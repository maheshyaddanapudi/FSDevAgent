package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatResponse {
    private String sessionId;
    private String message;
    private String role;
    private String toolCallId;
    private ToolCallResponse toolCall;
    private Instant timestamp;
}
