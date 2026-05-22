package com.codeSense_Ai.model;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@RequiredArgsConstructor
@Builder
public class DiffHunks {
    int oldStart;
    int newStart;
    int oldLines;
    int newLines;

    List<DiffLine> lines;

    public void addLines(DiffLine line){
        this.lines.add(line);
    }

    public DiffHunks(int oldStart, int newStart, int oldLines, int newLines, List<DiffLine> lines) {
        this.oldStart = oldStart;
        this.newStart = newStart;
        this.oldLines = oldLines;
        this.newLines = newLines;
        this.lines = lines;
    }
}
