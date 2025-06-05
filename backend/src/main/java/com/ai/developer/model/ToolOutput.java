package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents the output from a tool execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutput {
    private String sessionId;
    private String toolName;
    private Map<String, Object> arguments;
    private Object result;
    private Instant timestamp;
    private String output;
    private String type;
    private String mimeType;
    private Map<String, Object> metadata;
}
