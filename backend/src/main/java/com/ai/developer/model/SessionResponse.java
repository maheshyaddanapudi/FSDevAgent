package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class SessionResponse {
    private String sessionId;
    private Instant createdAt;
}
