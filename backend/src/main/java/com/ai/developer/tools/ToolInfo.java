package com.ai.developer.tools;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Map;

@Data
@AllArgsConstructor
public class ToolInfo {
    private String name;
    private String description;
    private Map<String, ParameterInfo> parameters;
}
