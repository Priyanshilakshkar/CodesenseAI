package com.codeSense_Ai.Exceptions;

public class OpenAIRateLimitException extends RuntimeException{
    public OpenAIRateLimitException(String message){
        super(message);
    }
}
