package com.ai.developer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * DTO for session response with explicit property naming to match frontend expectations
 */
@Data
@Builder
public class SessionResponseDTO {
    @JsonProperty("aiDeveloperAgentSessionId")
    private String aiDeveloperAgentSessionId;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    /**
     * Convert from domain model to DTO
     */
    public static SessionResponseDTO fromSessionResponse(SessionResponse response) {
        return SessionResponseDTO.builder()
                .aiDeveloperAgentSessionId(response.getAiDeveloperAgentSessionId())
                .createdAt(response.getCreatedAt())
                .build();
    }
    
    /**
     * @deprecated Use getAiDeveloperAgentSessionId() instead.
     * This method is kept for backward compatibility during migration.
     */
    @Deprecated
    @JsonProperty("sessionId")
    public String getSessionId() {
        return aiDeveloperAgentSessionId;
    }
    
    /**
     * @deprecated Use setAiDeveloperAgentSessionId(String) instead.
     * This method is kept for backward compatibility during migration.
     */
    @Deprecated
    public void setSessionId(String sessionId) {
        this.aiDeveloperAgentSessionId = sessionId;
    }
}
