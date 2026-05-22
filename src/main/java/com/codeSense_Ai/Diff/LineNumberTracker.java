package com.codeSense_Ai.Diff;

import com.codeSense_Ai.model.DiffLine;
import lombok.Setter;

@Setter
public class LineNumberTracker {

    private int oldLine ;
    private int newLine;

    private int  diffPosition ;

    public LineNumberTracker(int oldLine, int newLine, int deffPosition) {
        this.oldLine = oldLine;
        this.newLine = newLine;
        this.diffPosition = deffPosition;
    }

    public int getOldLine() {
        return oldLine;
    }

    public int getNewLine() {
        return newLine;
    }

    public int getDeffPosition() {
        return diffPosition;
    }

    public int incrementoldLine(){
        return oldLine++;
    }

    public  int incrementnewLine(){
        return newLine++;
    }
    public int incrementDiffPosition(){
        return diffPosition++;
    }

}
