package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a workspace context that can be passed between tools
 * to maintain consistent workspace location regardless of sessionId propagation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceContext {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    private String sessionId;
    private String workspacePath;
    private boolean initialized;
    
    /**
     * Create a workspace context from sessionId
     */
    public static WorkspaceContext fromSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("SessionId cannot be null or empty");
        }
        
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        return WorkspaceContext.builder()
                .sessionId(sessionId)
                .workspacePath(workspacePath)
                .initialized(false)
                .build();
    }
    
    /**
     * Create a workspace context from explicit path
     */
    public static WorkspaceContext fromPath(String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace path cannot be null or empty");
        }
        
        return WorkspaceContext.builder()
                .workspacePath(workspacePath)
                .initialized(false)
                .build();
    }
    
    /**
     * Get the workspace path, ensuring it's properly formatted
     */
    public String getWorkspacePath() {
        if (workspacePath == null || workspacePath.trim().isEmpty()) {
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
            } else {
                throw new IllegalStateException("Cannot determine workspace path: both path and sessionId are missing");
            }
        }
        return workspacePath;
    }
    
    /**
     * Resolve a relative path within this workspace
     */
    public String resolvePath(String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        
        if (relativePath.startsWith("/")) {
            return relativePath; // Absolute path, use as is
        }
        
        return getWorkspacePath() + "/" + relativePath;
    }
}
