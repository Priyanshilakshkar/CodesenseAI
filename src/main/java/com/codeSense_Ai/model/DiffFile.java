package com.codeSense_Ai.model;

import com.codeSense_Ai.Enums.DiffLineType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DiffFile {
    private String oldPath;
    private String newPath;
    private String extension;
    private boolean deletedFile;

    private boolean binaryFile;

    private boolean renamedFile;
    private List<DiffHunks> hunksList = new ArrayList<>();


    public boolean isDeletedFile() {
        return deletedFile;
    }

    public void setDeletedFile(boolean deletedFile) {
        this.deletedFile = deletedFile;
    }

    public boolean isBinaryFile() {
        return binaryFile;
    }

    public void setBinaryFile(boolean binaryFile) {
        this.binaryFile = binaryFile;
    }

    public boolean isRenamedFile() {
        return renamedFile;
    }

    public void setRenamedFile(boolean renamedFile) {
        this.renamedFile = renamedFile;
    }

    public void addHunk(DiffHunks hunk) {
        this.hunksList.add(hunk);
    }

    public List<DiffLine> allLines() {
        return hunksList.stream()
                .flatMap(hunk -> hunk.getLines().stream())
                .toList();
    }

    public int totalAdditions() {

        int count = 0;

        for (DiffLine line : allLines()) {

            if (line.getType() == DiffLineType.ADDED) {
                count++;
            }
        }

        return count;
    }
    public int totalRemovals() {

        int count = 0;

        for (DiffLine line : allLines()) {

            if (line.getType() == DiffLineType.REMOVED) {
                count++;
            }
        }

        return count;
    }

    public Integer diffPositionForLine(int targetLine) {

        int position = 0;

        for (DiffHunks hunk : hunksList) {

            for (DiffLine line : hunk.getLines()) {

                // Every visible diff line increments GitHub position
                position++;

                Integer newLineNum = line.getNewLineNum();

                /*
                 * Only lines belonging to the new file
                 * can match the target line.
                 */
                if (newLineNum != null && newLineNum == targetLine) {
                    return position;
                }
            }
        }

        // Line not found in diff
        return null;
    }




}
