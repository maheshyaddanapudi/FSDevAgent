package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ChatResponse {
    private String sessionId;
    private String message;
    private String content;
    private String role;
    private String toolCallId;
    private ToolCallResponse toolCall;
    private Instant timestamp;
    private boolean streaming;
    private String toolName;
    private Map<String, Object> toolArgs;
    
    /**
     * Builder method alias for content to support both naming conventions
     */
    public static class ChatResponseBuilder {
        public ChatResponseBuilder content(String content) {
            this.content = content;
            return this;
        }
    }
}
