package com.ai.developer.service;

import com.ai.developer.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Service for classifying Claude API errors and creating appropriate exceptions.
 * This service analyzes error responses and determines the appropriate error handling strategy.
 */
@Component
@Slf4j
public class ClaudeErrorClassifier {
    
    private final ObjectMapper objectMapper;
    
    public ClaudeErrorClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Classify a Claude API error response and create an appropriate exception.
     * 
     * @param statusCode HTTP status code from the response
     * @param errorBody Error response body from Claude API
     * @param retryAfterHeader Retry-After header value if present
     * @return Appropriate ClaudeApiException subclass
     */
    public ClaudeApiException classifyError(int statusCode, String errorBody, String retryAfterHeader) {
        log.debug("Classifying Claude API error: status={}, body={}", statusCode, errorBody);
        
        // Parse error details from response body
        String errorType = "unknown";
        String errorMessage = "Unknown error occurred";
        
        try {
            JsonNode errorJson = objectMapper.readTree(errorBody);
            JsonNode error = errorJson.get("error");
            if (error != null) {
                errorType = error.get("type") != null ? error.get("type").asText() : "unknown";
                errorMessage = error.get("message") != null ? error.get("message").asText() : "No message provided";
            }
        } catch (Exception e) {
            log.warn("Could not parse Claude API error response: {}", e.getMessage());
            errorMessage = "Failed to parse error response: " + errorBody;
        }
        
        // Classify based on status code and error type
        switch (statusCode) {
            case 429:
                // Rate limiting - parse retry-after header
                long retryAfterSeconds = parseRetryAfter(retryAfterHeader);
                return new ClaudeRateLimitException(
                    "Claude API rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.",
                    retryAfterSeconds
                );
                
            case 401:
                return new ClaudeAuthenticationException(
                    "Claude API authentication failed. Please check your API key.",
                    errorMessage
                );
                
            case 400:
                return new ClaudeInvalidRequestException(
                    "Invalid request to Claude API: " + errorMessage,
                    errorMessage
                );
                
            case 403:
                return new ClaudeClientException(
                    "Access forbidden by Claude API: " + errorMessage,
                    statusCode, errorType, errorMessage
                );
                
            case 404:
                return new ClaudeClientException(
                    "Claude API endpoint not found: " + errorMessage,
                    statusCode, errorType, errorMessage
                );
                
            default:
                if (statusCode >= 500) {
                    // Server errors
                    return new ClaudeServerException(
                        "Claude API server error (status " + statusCode + "): " + errorMessage,
                        statusCode, errorType, errorMessage
                    );
                } else if (statusCode >= 400) {
                    // Other client errors
                    return new ClaudeClientException(
                        "Claude API client error (status " + statusCode + "): " + errorMessage,
                        statusCode, errorType, errorMessage
                    );
                } else {
                    // Unexpected status code
                    return new ClaudeApiException(
                        "Unexpected Claude API response (status " + statusCode + "): " + errorMessage,
                        statusCode, errorType, errorMessage, false, false
                    );
                }
        }
    }
    
    /**
     * Parse the Retry-After header value to determine retry delay.
     * 
     * @param retryAfterHeader Retry-After header value
     * @return Retry delay in seconds
     */
    private long parseRetryAfter(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.trim().isEmpty()) {
            return 60; // Default to 60 seconds if no header
        }
        
        try {
            return Long.parseLong(retryAfterHeader.trim());
        } catch (NumberFormatException e) {
            log.warn("Could not parse Retry-After header: {}", retryAfterHeader);
            return 60; // Default to 60 seconds if parsing fails
        }
    }
    
    /**
     * Determine if an error should trigger automatic retry.
     * 
     * @param exception The Claude API exception
     * @return true if the error should be retried automatically
     */
    public boolean shouldRetry(ClaudeApiException exception) {
        return exception.isRetryable();
    }
    
    /**
     * Determine if an error should trigger automatic agent pause.
     * 
     * @param exception The Claude API exception
     * @return true if the agent should be paused
     */
    public boolean shouldPauseAgent(ClaudeApiException exception) {
        return exception.shouldPauseAgent();
    }
    
    /**
     * Get user-friendly error message for display in UI.
     * 
     * @param exception The Claude API exception
     * @return User-friendly error message
     */
    public String getUserFriendlyMessage(ClaudeApiException exception) {
        switch (exception.getStatusCode()) {
            case 429:
                return "The AI service is currently experiencing high demand. The agent will automatically pause and retry in a moment.";
            case 401:
                return "There's an issue with the API authentication. Please check your API key configuration.";
            case 400:
                return "There was a problem with the request format. The agent will pause to prevent further errors.";
            case 403:
                return "Access to the AI service is currently restricted. Please check your account permissions.";
            case 500:
            case 502:
            case 503:
            case 504:
                return "The AI service is temporarily unavailable. The agent will pause and you can retry when the service is restored.";
            default:
                return "An unexpected error occurred with the AI service. The agent has been paused for safety.";
        }
    }
    
    /**
     * Get technical details for error display.
     * 
     * @param exception The Claude API exception
     * @return Technical error details
     */
    public String getTechnicalDetails(ClaudeApiException exception) {
        StringBuilder details = new StringBuilder();
        details.append("Status Code: ").append(exception.getStatusCode()).append("\n");
        details.append("Error Type: ").append(exception.getErrorType()).append("\n");
        details.append("Error Message: ").append(exception.getErrorMessage()).append("\n");
        details.append("Retryable: ").append(exception.isRetryable()).append("\n");
        details.append("Should Pause Agent: ").append(exception.shouldPauseAgent()).append("\n");
        
        if (exception instanceof ClaudeRateLimitException) {
            ClaudeRateLimitException rateLimitEx = (ClaudeRateLimitException) exception;
            details.append("Retry After: ").append(rateLimitEx.getRetryAfterSeconds()).append(" seconds\n");
        }
        
        return details.toString();
    }
}

