package com.ai.developer.tools;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for all tools to enforce consistent workspace context handling
 */
@Slf4j
public abstract class AbstractTool implements Tool {

    // Default workspace path for tools
    protected static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";

    /**
     * Execute the tool with enforced workspace context validation
     * @param arguments Tool arguments
     * @return Tool execution result
     */
    @Override
    public final Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Validate arguments
        if (arguments == null) {
            log.error("[TOOL_EXECUTION] Null arguments provided to tool: {}", getName());
            return Flux.error(new IllegalArgumentException("Arguments cannot be null"));
        }

        // Extract and validate workspace context
        WorkspaceContext workspaceContext = resolveWorkspaceContext(arguments);
        
        // Log tool execution with workspace context
        log.info("[TOOL_EXECUTION] Executing tool {} with workspace {} (sessionId: {}) and arguments: {}", 
                getName(), workspaceContext.getWorkspacePath(), workspaceContext.getSessionId(), arguments);
        
        // Ensure workspace directory exists
        ensureWorkspaceExists(workspaceContext);
        
        // Call the actual tool implementation
        return executeWithWorkspace(arguments, workspaceContext)
                .doOnError(error -> {
                    log.error("[TOOL_EXECUTION] Error executing tool {} in workspace {}: {}", 
                            getName(), workspaceContext.getWorkspacePath(), error.getMessage(), error);
                })
                .doOnComplete(() -> {
                    log.info("[TOOL_EXECUTION] Completed execution of tool {} in workspace {}", 
                            getName(), workspaceContext.getWorkspacePath());
                });
    }
    
    /**
     * Resolve workspace context from arguments
     * @param arguments Tool arguments
     * @return Resolved workspace context
     */
    protected WorkspaceContext resolveWorkspaceContext(Map<String, Object> arguments) {
        // Check for explicit workspace path first
        String workspacePath = (String) arguments.get("workspacePath");
        String sessionId = (String) arguments.get("sessionId");
        
        WorkspaceContext context;
        
        if (workspacePath != null && !workspacePath.trim().isEmpty()) {
            // Use explicit workspace path if provided
            context = WorkspaceContext.fromPath(workspacePath);
            log.info("[WORKSPACE_CONTEXT] Using explicit workspace path: {}", workspacePath);
            
            // Set sessionId if available
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                context.setSessionId(sessionId);
            }
        } else if (sessionId != null && !sessionId.trim().isEmpty()) {
            // Fall back to sessionId-based workspace
            context = WorkspaceContext.fromSessionId(sessionId);
            log.info("[WORKSPACE_CONTEXT] Using sessionId-based workspace: {}", context.getWorkspacePath());
        } else {
            // Generate fallback sessionId if both are missing
            sessionId = "default-session-" + UUID.randomUUID().toString();
            context = WorkspaceContext.fromSessionId(sessionId);
            arguments.put("sessionId", sessionId);
            log.warn("[WORKSPACE_CONTEXT] Missing both workspacePath and sessionId, using fallback: {}", 
                    context.getWorkspacePath());
        }
        
        // Always ensure workspacePath is in arguments for other tools
        arguments.put("workspacePath", context.getWorkspacePath());
        
        return context;
    }
    
    /**
     * Ensure workspace directory exists
     * @param context Workspace context
     */
    protected void ensureWorkspaceExists(WorkspaceContext context) {
        try {
            Path dirPath = Path.of(context.getWorkspacePath());
            Files.createDirectories(dirPath);
            
            // Create marker file if not exists
            Path markerPath = dirPath.resolve(".initialized");
            if (!Files.exists(markerPath)) {
                Files.createFile(markerPath);
                log.info("[WORKSPACE_CONTEXT] Created workspace marker file: {}", markerPath);
            }
            
            context.setInitialized(true);
            log.info("[WORKSPACE_CONTEXT] Ensured workspace directory exists: {}", context.getWorkspacePath());
        } catch (IOException e) {
            log.error("[WORKSPACE_CONTEXT] Error creating workspace directory: {}", context.getWorkspacePath(), e);
        }
    }
    
    /**
     * Execute the tool implementation with validated workspace context
     * @param arguments Tool arguments
     * @param workspaceContext Validated workspace context
     * @return Tool execution result
     */
    protected Flux<ToolOutput> executeWithWorkspace(Map<String, Object> arguments, WorkspaceContext workspaceContext) {
        // Default implementation calls the legacy method for backward compatibility
        return executeWithSession(arguments, workspaceContext.getSessionId());
    }
    
    /**
     * Legacy method for backward compatibility
     * @param arguments Tool arguments
     * @param sessionId Validated sessionId
     * @return Tool execution result
     */
    protected Flux<ToolOutput> executeWithSession(Map<String, Object> arguments, String sessionId) {
        throw new UnsupportedOperationException("Tool must implement either executeWithWorkspace or executeWithSession");
    }
}
