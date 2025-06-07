package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequest {
    private String sessionId;
    private String message;
    private boolean autonomous;
    
    /**
     * Check if request is for autonomous execution
     */
    public boolean isAutonomous() {
        return autonomous;
    }
}
