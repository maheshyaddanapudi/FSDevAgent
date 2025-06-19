package com.ai.developer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatResponse {
    @JsonProperty("aiDeveloperAgentSessionId")
    private String aiDeveloperAgentSessionId;
    private String message;
    private String role;
    private String toolCallId;
    private ToolCallResponse toolCall;
    private Instant timestamp;
    
    // Message type for UI rendering (thinking, analysis, reflection, planning, answer, tool_result, text)
    @Builder.Default
    private String messageType = "text";
    
    /**
     * @deprecated Use getAiDeveloperAgentSessionId() instead.
     * This method is kept for backward compatibility during migration.
     */
    @Deprecated
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
