package com.ai.developer.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    
    // Claude uses "input" field, so we map it to "arguments" for our tools
    @JsonProperty("input")
    private Map<String, Object> arguments;
    
    // Getter for arguments (used by tools)
    public Map<String, Object> getArguments() {
        return arguments;
    }
    
    // Setter for arguments
    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
