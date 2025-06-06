package com.ai.developer.llm;

import lombok.Data;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents the context of a project for agent operations
 */
@Data
public class ProjectContext {
    private String projectType;
    private String buildTool;
    private String frameworkType;
    private String projectPath;
    private String description;
    private List<String> frameworks = new ArrayList<>();
    private List<String> languages = new ArrayList<>();
    private List<String> databases = new ArrayList<>();
}
