package com.ai.developer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ToolCallResponse {
    private String name;
    private Map<String, Object> arguments;
    private String result;
    
    @JsonProperty("aiDeveloperAgentSessionId")
    private String aiDeveloperAgentSessionId;
    
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
