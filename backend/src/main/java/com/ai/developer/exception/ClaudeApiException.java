package com.ai.developer.exception;

/**
 * Base exception for Claude API related errors.
 * This exception hierarchy allows for specific handling of different Claude API error conditions.
 */
public class ClaudeApiException extends RuntimeException {
    private final int statusCode;
    private final String errorType;
    private final String errorMessage;
    private final boolean retryable;
    private final boolean pauseAgent;

    public ClaudeApiException(String message, int statusCode, String errorType, String errorMessage, 
                             boolean retryable, boolean pauseAgent) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
        this.pauseAgent = pauseAgent;
    }

    public ClaudeApiException(String message, Throwable cause, int statusCode, String errorType, 
                             String errorMessage, boolean retryable, boolean pauseAgent) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
        this.pauseAgent = pauseAgent;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean shouldPauseAgent() {
        return pauseAgent;
    }
}

