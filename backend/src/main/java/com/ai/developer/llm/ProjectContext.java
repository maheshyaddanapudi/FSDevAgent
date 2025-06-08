package com.ai.developer.llm;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the context of a project, including its path and other metadata.
 */
@Data
public class ProjectContext {
    private String projectPath;
    private String language;
    private String framework;
    private String projectType;
    private String description;
    private List<String> frameworks = new ArrayList<>();
    private List<String> languages = new ArrayList<>();
    private List<String> databases = new ArrayList<>();
    private Map<String, Object> workspaceState;
    
    /**
     * Set the workspace state with directory structure information
     */
    public void setWorkspaceState(Map<String, Object> workspaceState) {
        this.workspaceState = workspaceState;
    }
    
    /**
     * Get the list of frameworks
     */
    public List<String> getFrameworks() {
        return frameworks != null ? frameworks : new ArrayList<>();
    }
    
    /**
     * Get the list of languages
     */
    public List<String> getLanguages() {
        return languages != null ? languages : new ArrayList<>();
    }
    
    /**
     * Get the list of databases
     */
    public List<String> getDatabases() {
        return databases != null ? databases : new ArrayList<>();
    }
    
    /**
     * Get the project description
     */
    public String getDescription() {
        return description != null ? description : "";
    }
}
