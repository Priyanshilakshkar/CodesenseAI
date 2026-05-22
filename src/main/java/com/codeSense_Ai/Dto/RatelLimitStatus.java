package com.codeSense_Ai.Dto;

import java.time.Instant;

public record RatelLimitStatus(
        int remaining ,
        int limit ,
        Instant resetAt
) {
    public Long SecondsUntilResese(){
        Long seconds = resetAt.getEpochSecond()  - Instant.now().getEpochSecond();
        return Math.max(0 , seconds);
    }

    public double percentageRemaining(){
        return limit > 0 ? (remaining * 100.0)/limit  : 0;
    }
}
