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
    List<DiffHunks> hunksList;


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

    public List<DiffLine> getAllLines() {

        List<DiffLine> allLines = new ArrayList<>();

        for (DiffHunks hunk : hunksList) {

            allLines.addAll(hunk.getLines());
        }

        return allLines;
    }

    public int totalAdditions() {

        int count = 0;

        for (DiffLine line : getAllLines()) {

            if (line.getType() == DiffLineType.ADDED) {
                count++;
            }
        }

        return count;
    }
    public int totalRemovals() {

        int count = 0;

        for (DiffLine line : getAllLines()) {

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

                /*
                 * Only ADDED or CONTEXT lines belong to the NEW file.
                 * Deleted lines do not exist in the new file.
                 */
                if (line.getNewLineNum() == targetLine) {
                    return position;
                }
            }
        }

        // Line not found in diff
        return null;
    }



}
