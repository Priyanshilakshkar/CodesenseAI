package com.codeSense_Ai.Diff;

import com.codeSense_Ai.model.DiffFile;
import org.springframework.stereotype.Component;

@Component
public class DiffEdgeCaseHandler {
    public boolean isBinaryFile(String line) {
        return line.startsWith("Binary files");
    }

    public boolean isDeletedFile(String line) {
        return line.startsWith("deleted file mode");
    }

    public boolean isRenameFrom(String line) {
        return line.startsWith("rename from");
    }

    public boolean isRenameTo(String line) {
        return line.startsWith("rename to");
    }

    public boolean isNoNewlineEOF(String line) {
        return line.equals("\\ No newline at end of file");
    }

    public boolean isEmptyDiff(DiffFile diffFile) {
        return diffFile.getHunksList().isEmpty();
    }
}
