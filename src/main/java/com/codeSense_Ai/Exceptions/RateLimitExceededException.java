package com.codeSense_Ai.Exceptions;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException{
    private final  long retryAfterSeconds;
    public RateLimitExceededException(String message , Long retryAfterSeconds){
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
