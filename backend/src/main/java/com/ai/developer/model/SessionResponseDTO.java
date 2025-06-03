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
    @JsonProperty("sessionId")
    private String sessionId;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    /**
     * Convert from domain model to DTO
     */
    public static SessionResponseDTO fromSessionResponse(SessionResponse response) {
        return SessionResponseDTO.builder()
                .sessionId(response.getSessionId())
                .createdAt(response.getCreatedAt())
                .build();
    }
}
