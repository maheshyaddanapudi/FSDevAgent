package com.ai.developer.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ToolCallResponse {
    private String name;
    private Map<String, Object> arguments;
    private String result;
}
