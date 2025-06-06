package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a tool invocation with name and arguments.
 * Used by TaskExecutorService to sequence tool operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocation {
    private String name;
    private Map<String, Object> args;
}
