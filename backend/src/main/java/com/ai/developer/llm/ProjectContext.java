package com.ai.developer.llm;

import lombok.Data;

/**
 * Represents the context of a project for agent operations
 */
@Data
public class ProjectContext {
    private String projectType;
    private String buildTool;
    private String frameworkType;
    private String projectPath;
}
