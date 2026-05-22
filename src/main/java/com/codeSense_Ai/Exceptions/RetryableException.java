package com.codeSense_Ai.Exceptions;

public class RetryableException extends RuntimeException{
    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
