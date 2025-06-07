package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class SessionResponse {
    private String sessionId;
    private Instant createdAt;
    private Instant created;
    private String workspacePath;
    
    /**
     * Builder method alias for created to support both naming conventions
     */
    public static class SessionResponseBuilder {
        public SessionResponseBuilder created(Instant created) {
            this.created = created;
            this.createdAt = created; // Keep both fields in sync
            return this;
        }
        
        public SessionResponseBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            this.created = createdAt; // Keep both fields in sync
            return this;
        }
    }
}
