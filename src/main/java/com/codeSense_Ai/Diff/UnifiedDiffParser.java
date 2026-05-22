package com.codeSense_Ai.Diff;

import com.codeSense_Ai.Enums.DiffLineType;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.DiffHunks;
import com.codeSense_Ai.model.DiffLine;
import lombok.RequiredArgsConstructor;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class UnifiedDiffParser {

    private final DiffEdgeCaseHandler edgeCaseHandler;

    public DiffFile diffParser(String Diff){
        String[] diff = Diff.split("/n");
        DiffHunks hunks = null;
        DiffFile diffFile = new DiffFile();
        LineNumberTracker tracker = null;



        for(String str : diff){
            if (edgeCaseHandler.isNoNewlineEOF(str)) {
                continue;
            }

            // BINARY FILE
            if (edgeCaseHandler.isBinaryFile(str)) {
                diffFile.setBinaryFile(true);
                continue;
            }

            // DELETED FILE
            if (edgeCaseHandler.isDeletedFile(str)) {
                diffFile.setDeletedFile(true);
                continue;
            }
            if (edgeCaseHandler.isRenameFrom(str)) {

                diffFile.setRenamedFile(true);

                String oldName =
                        str.replace("rename from ", "");

                diffFile.setOldPath(oldName);

                continue;
            }

            if (edgeCaseHandler.isRenameTo(str)) {

                diffFile.setRenamedFile(true);

                String newName =
                        str.replace("rename to ", "");

                diffFile.setNewPath(newName);
                diffFile.setExtension(extractExtension(newName));

                continue;
            }



            if(str == "dif...git"){
                String[] parts = str.split(" ");
                diffFile.setOldPath(parts[2]);
                diffFile.setNewPath(parts[3]);




            }
            else if(str.startsWith("@@")){
                int oldLine = 0;
                int oldLength = 0;
                int newLine  = 0;
                int newLength  = 0;
                String regex = "@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(Diff);
                hunks = new DiffHunks();

                if(matcher.find()){
                    oldLine = Integer.parseInt(matcher.group(1));
                    oldLength = Integer.parseInt(matcher.group(2));
                    newLine = Integer.parseInt(matcher.group(3));
                    newLength = Integer.parseInt(matcher.group(4));
                }

                hunks.setNewStart(newLine);
                hunks.setNewLines(newLength);
                hunks.setOldStart(oldLine);
                hunks.setOldLines(oldLength);

                diffFile.addHunk(hunks);


            }
            else if(str.startsWith("+") || str.startsWith("+++")){
                 tracker.incrementDiffPosition();
                 DiffLine line = new DiffLine(
                         DiffLineType.ADDED,
                         str.substring(1),
                         null,
                         tracker.getNewLine(),
                         tracker.getDeffPosition()
                 );

                 hunks.addLines(line);
                 tracker.incrementnewLine();
            }
            else if(str.startsWith("-")|| str.startsWith("---")){
                tracker.incrementDiffPosition();
                DiffLine line = new DiffLine(
                      DiffLineType.REMOVED,
                      str.substring(1),
                        tracker.getOldLine(),
                        null ,
                        tracker.getDeffPosition()
                );
                hunks.addLines(line);
                tracker.incrementoldLine();
            }
            else if(str.startsWith(" ")){
                tracker.incrementDiffPosition();
                DiffLine line = new DiffLine(
                        DiffLineType.CONTEXT,
                        str.substring(1),
                        tracker.getOldLine(),
                        tracker.getNewLine(),
                        tracker.getDeffPosition()
                );
                hunks.addLines(line);
                tracker.incrementnewLine();
                tracker.incrementoldLine();
            }
        }






        return diffFile;



    }

    private String extractExtension(String path) {

        if (path == null || !path.contains(".")) {
            return "";
        }

        return path.substring(
                path.lastIndexOf(".") + 1
        );
    }



 }
