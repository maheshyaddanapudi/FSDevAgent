package com.ai.developer.tools;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ToolOutput {
    private String type; // stdout, stderr, file, image, etc.
    private String content;
    private Map<String, Object> metadata;
}
