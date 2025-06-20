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
    
    // Claude can use "args", "input", or "arguments" - we normalize to "arguments"
    @JsonProperty("arguments")
    private Map<String, Object> arguments;
}
