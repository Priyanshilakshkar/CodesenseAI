package com.codeSense_Ai.model;

import com.codeSense_Ai.Enums.DiffLineType;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@RequiredArgsConstructor
public class DiffLine {
    DiffLineType type;

    String content;

    Integer oldLineNum;
    Integer newLineNum;

    int diffPosition;

    public DiffLine(DiffLineType type, String content, Integer oldLineNum, Integer newLineNum, int diffPosition) {
        this.type = type;
        this.content = content;
        this.oldLineNum = oldLineNum;
        this.newLineNum = newLineNum;
        this.diffPosition = diffPosition;
    }
    public String content(){
        return content;
    }

    public boolean isAdded() {
        return this.type == DiffLineType.ADDED;
    }
    public boolean isChanged() {
        return type == DiffLineType.ADDED || type == DiffLineType.REMOVED;
    }
    public boolean isContext(){
        return type == DiffLineType.CONTEXT;
    }
    public int displayLineNumber() {
        if (newLineNum != null) return newLineNum;
        if (oldLineNum != null) return oldLineNum;
        return 0;
    }
}
