package com.ai.developer.llm.exceptions;

/**
 * Base exception for Claude API errors
 */
public class ClaudeApiException extends RuntimeException {
    private final int statusCode;
    private final String errorType;
    private final String errorDetails;
    
    public ClaudeApiException(int statusCode, String errorType, String message, String errorDetails) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorDetails = errorDetails;
    }
    
    public ClaudeApiException(int statusCode, String errorType, String message) {
        this(statusCode, errorType, message, null);
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public String getErrorDetails() {
        return errorDetails;
    }
    
    public boolean shouldPauseExecution() {
        // Pause execution for rate limits, server errors, and client errors
        return statusCode == 429 || statusCode >= 400;
    }
    
    public String getUserFriendlyMessage() {
        switch (statusCode) {
            case 429:
                return "Rate limit reached. The Claude API is temporarily limiting requests.";
            case 500:
            case 502:
            case 503:
            case 504:
                return "Claude API server error. The service is temporarily unavailable.";
            case 400:
                return "Invalid request to Claude API. There may be an issue with the request format.";
            case 401:
                return "Authentication failed with Claude API. Please check the API key.";
            case 403:
                return "Access forbidden by Claude API. The API key may not have sufficient permissions.";
            case 404:
                return "Claude API endpoint not found. This may indicate a configuration issue.";
            default:
                return "Claude API error: " + getMessage();
        }
    }
    
    public String getRecoveryInstructions() {
        switch (statusCode) {
            case 429:
                return "Wait a few minutes for the rate limit to reset, then choose 'Resume' to continue.";
            case 500:
            case 502:
            case 503:
            case 504:
                return "Wait a moment for the server issue to resolve, then choose 'Resume' to retry.";
            case 400:
                return "This may require adjusting the request. Choose 'Resume' to retry or 'Stop' to end execution.";
            case 401:
            case 403:
                return "Check your Claude API key configuration. Choose 'Stop' to end execution.";
            default:
                return "Choose 'Resume' to retry, or 'Stop' to end execution.";
        }
    }
}

