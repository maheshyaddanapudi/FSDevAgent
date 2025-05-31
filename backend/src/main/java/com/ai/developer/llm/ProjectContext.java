package com.ai.developer.llm;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectContext {
    private String projectPath;
    private String projectType; // java, javascript, python, etc.
    private String buildTool; // maven, gradle, npm, etc.
    private String frameworkType; // spring, react, angular, etc.
}
