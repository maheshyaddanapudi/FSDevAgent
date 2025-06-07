package com.ai.developer.model;

import com.ai.developer.tools.ToolOutput;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ToolCallResponse {
    private String name;
    private Map<String, Object> arguments;
    private String result;
    private String sessionId;
    private String toolName;
    private ToolOutput output;
    private Instant timestamp;
    private Map<String, Object> metadata;
    
    /**
     * Get the output as ToolOutput
     */
    public ToolOutput getOutput() {
        return output;
    }
    
    /**
     * Builder method alias for metadata to support both naming conventions
     */
    public static class ToolCallResponseBuilder {
        public ToolCallResponseBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
    }
}
