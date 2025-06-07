package com.ai.developer.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String name;
    private Map<String, Object> arguments;
    
    /**
     * Alias for arguments to support both naming conventions
     */
    public Map<String, Object> getArgs() {
        return arguments;
    }
    
    /**
     * Alias for arguments to support both naming conventions
     */
    public void setArgs(Map<String, Object> args) {
        this.arguments = args;
    }
    
    /**
     * Builder method alias for arguments to support both naming conventions
     */
    public static class ToolCallBuilder {
        public ToolCallBuilder args(Map<String, Object> args) {
            this.arguments = args;
            return this;
        }
    }
}
