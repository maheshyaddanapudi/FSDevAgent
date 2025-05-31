package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the output from a tool execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutput {
    private String output;
    private String type;
    private String mimeType;
}
