package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequest {
    private String sessionId;
    private String message;
}
