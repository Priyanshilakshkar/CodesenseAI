package com.codeSense_Ai.Diff;

import com.codeSense_Ai.Enums.DiffLineType;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.DiffHunks;
import com.codeSense_Ai.model.DiffLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class UnifiedDiffParser {

    private final DiffEdgeCaseHandler edgeCaseHandler;

    /*
     * Examples:
     *
     * @@ -10,5 +10,7 @@
     * @@ -10 +10 @@
     * @@ -10,5 +10 @@
     * @@ -10 +10,7 @@ public void test()
     */
    private static final Pattern HUNK_PATTERN =
            Pattern.compile(
                    "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*"
            );

    public DiffFile diffParser(String diff , String filename) {

        if (diff == null || diff.isBlank()) {
            return new DiffFile();
        }

        String[] lines = diff.split("\\R");

        DiffFile diffFile = new DiffFile();

        if (filename != null && !filename.isBlank()) {

            diffFile.setOldPath(filename);
            diffFile.setNewPath(filename);
            diffFile.setExtension(extractExtension(filename));
        }

        DiffHunks currentHunk = null;

        LineNumberTracker tracker = null;

        for (String str : lines) {

            /*
             * Ignore special "No newline at end of file" marker
             */
            if (edgeCaseHandler.isNoNewlineEOF(str)) {
                continue;
            }

            /*
             * BINARY FILE
             */
            if (edgeCaseHandler.isBinaryFile(str)) {
                diffFile.setBinaryFile(true);
                continue;
            }

            /*
             * DELETED FILE
             */
            if (edgeCaseHandler.isDeletedFile(str)) {
                diffFile.setDeletedFile(true);
                continue;
            }

            /*
             * RENAMED FILE
             */
            if (edgeCaseHandler.isRenameFrom(str)) {

                diffFile.setRenamedFile(true);

                String oldName = str
                        .replace("rename from ", "")
                        .trim();

                diffFile.setOldPath(oldName);

                continue;
            }

            if (edgeCaseHandler.isRenameTo(str)) {

                diffFile.setRenamedFile(true);

                String newName = str
                        .replace("rename to ", "")
                        .trim();

                diffFile.setNewPath(newName);

                diffFile.setExtension(extractExtension(newName));

                continue;
            }

            /*
             * diff --git a/file b/file
             */
            if (str.startsWith("diff --git")) {

                parseDiffHeader(str, diffFile);

                continue;
            }

            /*
             * --- a/file
             *
             * This is metadata, NOT a removed source line.
             */
            if (str.startsWith("--- ")) {

                if (diffFile.getOldPath() == null) {

                    String oldPath = str.substring(4).trim();

                    if (oldPath.startsWith("a/")) {
                        oldPath = oldPath.substring(2);
                    }

                    if (!oldPath.equals("/dev/null")) {
                        diffFile.setOldPath(oldPath);
                    }
                }

                continue;
            }

            /*
             * +++ b/file
             *
             * This is metadata, NOT an added source line.
             */
            if (str.startsWith("+++ ")) {

                if (diffFile.getNewPath() == null) {

                    String newPath = str.substring(4).trim();

                    if (newPath.startsWith("b/")) {
                        newPath = newPath.substring(2);
                    }

                    if (!newPath.equals("/dev/null")) {
                        diffFile.setNewPath(newPath);
                        diffFile.setExtension(extractExtension(newPath));
                    }
                }

                continue;
            }

            /*
             * HUNK HEADER
             *
             * Example:
             *
             * @@ -10,5 +10,7 @@
             */
            if (str.startsWith("@@")) {

                Matcher matcher = HUNK_PATTERN.matcher(str);

                if (!matcher.matches()) {

                    System.out.println(
                            "Unable to parse hunk header: " + str
                    );

                    continue;
                }

                int oldStart =
                        Integer.parseInt(matcher.group(1));

                int oldLines =
                        matcher.group(2) != null
                                ? Integer.parseInt(matcher.group(2))
                                : 1;

                int newStart =
                        Integer.parseInt(matcher.group(3));

                int newLines =
                        matcher.group(4) != null
                                ? Integer.parseInt(matcher.group(4))
                                : 1;

                /*
                 * Create new hunk
                 */
                currentHunk = new DiffHunks();

                currentHunk.setOldStart(oldStart);
                currentHunk.setOldLines(oldLines);

                currentHunk.setNewStart(newStart);
                currentHunk.setNewLines(newLines);

                diffFile.addHunk(currentHunk);

                /*
                 * Initialize line tracker
                 */
                tracker = new LineNumberTracker(
                        oldStart,
                        newStart,
                        0
                );

                continue;
            }

            /*
             * Ignore anything before the first hunk.
             */
            if (currentHunk == null || tracker == null) {
                continue;
            }

            /*
             * ADDED LINE
             *
             * Example:
             * +System.out.println("Hello");
             */
            if (str.startsWith("+")) {

                tracker.incrementDiffPosition();

                DiffLine line = new DiffLine(
                        DiffLineType.ADDED,
                        str.substring(1),
                        null,
                        tracker.getNewLine(),
                        tracker.getDeffPosition()
                );

                currentHunk.addLines(line);

                tracker.incrementnewLine();

                continue;
            }

            /*
             * REMOVED LINE
             *
             * Example:
             * -System.out.println("Old");
             */
            if (str.startsWith("-")) {

                tracker.incrementDiffPosition();

                DiffLine line = new DiffLine(
                        DiffLineType.REMOVED,
                        str.substring(1),
                        tracker.getOldLine(),
                        null,
                        tracker.getDeffPosition()
                );

                currentHunk.addLines(line);

                tracker.incrementoldLine();

                continue;
            }

            /*
             * CONTEXT LINE
             *
             * Example:
             *  System.out.println("Existing");
             */
            if (str.startsWith(" ")) {

                tracker.incrementDiffPosition();

                DiffLine line = new DiffLine(
                        DiffLineType.CONTEXT,
                        str.substring(1),
                        tracker.getOldLine(),
                        tracker.getNewLine(),
                        tracker.getDeffPosition()
                );

                currentHunk.addLines(line);

                tracker.incrementoldLine();
                tracker.incrementnewLine();

                continue;
            }
        }

        /*
         * Set extension if it wasn't already set.
         */
        if (diffFile.getExtension() == null &&
                diffFile.getNewPath() != null) {

            diffFile.setExtension(
                    extractExtension(diffFile.getNewPath())
            );
        }

        return diffFile;
    }

    private void parseDiffHeader(
            String str,
            DiffFile diffFile
    ) {

        /*
         * Expected:
         *
         * diff --git a/TestCode1.java b/TestCode1.java
         */

        String prefix = "diff --git ";

        if (!str.startsWith(prefix)) {
            return;
        }

        String paths = str.substring(prefix.length());

        int separator = paths.indexOf(" b/");

        if (separator == -1) {
            return;
        }

        String oldPath = paths.substring(0, separator);

        String newPath = paths.substring(separator + 1);

        if (oldPath.startsWith("a/")) {
            oldPath = oldPath.substring(2);
        }

        if (newPath.startsWith("b/")) {
            newPath = newPath.substring(2);
        }

        diffFile.setOldPath(oldPath);
        diffFile.setNewPath(newPath);

        diffFile.setExtension(
                extractExtension(newPath)
        );
    }

    private String extractExtension(String path) {

        if (path == null || !path.contains(".")) {
            return "";
        }

        return path.substring(
                path.lastIndexOf('.') + 1
        );
    }



 }
