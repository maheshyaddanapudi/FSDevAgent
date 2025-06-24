package com.ai.developer.exception;

/**
 * Exception thrown when Claude API returns a 429 (Too Many Requests) error.
 * This indicates rate limiting and should trigger automatic retry with backoff.
 */
public class ClaudeRateLimitException extends ClaudeApiException {
    private final long retryAfterSeconds;

    public ClaudeRateLimitException(String message, long retryAfterSeconds) {
        super(message, 429, "rate_limit_error", message, true, true);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

/**
 * Exception thrown when Claude API returns a 5xx server error.
 * This indicates a server-side issue and should trigger automatic pause.
 */
class ClaudeServerException extends ClaudeApiException {
    public ClaudeServerException(String message, int statusCode, String errorType, String errorMessage) {
        super(message, statusCode, errorType, errorMessage, true, true);
    }
}

/**
 * Exception thrown when Claude API returns a 4xx client error (except 429).
 * This indicates a client-side issue and may require user intervention.
 */
class ClaudeClientException extends ClaudeApiException {
    public ClaudeClientException(String message, int statusCode, String errorType, String errorMessage) {
        super(message, statusCode, errorType, errorMessage, false, true);
    }
}

/**
 * Exception thrown when Claude API returns an authentication error.
 * This requires immediate user intervention to fix API key issues.
 */
class ClaudeAuthenticationException extends ClaudeApiException {
    public ClaudeAuthenticationException(String message, String errorMessage) {
        super(message, 401, "authentication_error", errorMessage, false, true);
    }
}

/**
 * Exception thrown when Claude API returns an invalid request error.
 * This indicates a problem with the request format or parameters.
 */
class ClaudeInvalidRequestException extends ClaudeApiException {
    public ClaudeInvalidRequestException(String message, String errorMessage) {
        super(message, 400, "invalid_request_error", errorMessage, false, true);
    }
}

