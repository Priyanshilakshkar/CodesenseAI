package com.codeSense_Ai.Exceptions;

public class WebhookValidationException extends RuntimeException{
    public WebhookValidationException(String message) {
        super(message);
    }
}
