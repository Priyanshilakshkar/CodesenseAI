package com.codeSense_Ai.Exceptions;

public class RateLimitRetryException extends RuntimeException{
    private final long retryAfterSeconds;

    public RateLimitRetryException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
