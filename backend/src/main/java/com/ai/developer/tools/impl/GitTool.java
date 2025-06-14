package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
public class GitTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
    // Logging prefix for file operations to enable easy grepping
    private static final String FILE_OP_LOG_PREFIX = "FILE_OPERATION";
    
    @Override
    public String getName() {
        return "git_operations";
    }
    
    @Override
    public String getDescription() {
        return "Perform Git operations on repositories within session-specific workspaces";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .name("operation")
            .type("string")
            .description("Operation: init, clone, add, commit, push, pull, status, log, branch")
            .required(true)
            .build());
            
        params.put("path", ParameterInfo.builder()
            .name("path")
            .type("string")
            .description("Repository path (absolute or relative to session workspace)")
            .required(true)
            .build());
            
        params.put("message", ParameterInfo.builder()
            .name("message")
            .type("string")
            .description("Commit message")
            .required(false)
            .build());
            
        params.put("url", ParameterInfo.builder()
            .name("url")
            .type("string")
            .description("Remote repository URL")
            .required(false)
            .build());
            
        params.put("branch", ParameterInfo.builder()
            .name("branch")
            .type("string")
            .description("Branch name")
            .required(false)
            .build());
            
        params.put("sessionId", ParameterInfo.builder()
            .name("sessionId")
            .type("string")
            .description("Chat session ID for Claude's internal tracking")
            .required(false)
            .build());
            
        params.put(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, ParameterInfo.builder()
            .name(SESSION_WORKSPACE_ROOT_FOLDER_PARAM)
            .type("string")
            .description("Session workspace root folder for workspace management")
            .required(false)
            .build());
            
        params.put("aiDeveloperAgentSessionId", ParameterInfo.builder()
            .name("aiDeveloperAgentSessionId")
            .type("string")
            .description("AI Developer Agent session ID for workspace management")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Enhanced logging for debugging argument structure
        log.info("GitTool executing with arguments: {}", arguments);
        
        // Extract operation with alternative key checking
        String operationParam = (String) arguments.get("operation");
        if (operationParam == null) {
            // Check for alternative keys that might contain operation
            if (arguments.containsKey("op")) {
                operationParam = (String) arguments.get("op");
                log.warn("Using 'op' instead of 'operation' for GitTool");
            } else if (arguments.containsKey("action")) {
                operationParam = (String) arguments.get("action");
                log.warn("Using 'action' instead of 'operation' for GitTool");
            } else if (arguments.containsKey("command")) {
                operationParam = (String) arguments.get("command");
                log.warn("Using 'command' instead of 'operation' for GitTool");
            } else {
                log.error("Operation parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("Operation parameter is required"));
            }
        }
        
        // Create final copy of operation for lambda
        final String operation = operationParam;
        
        // Extract path with alternative key checking
        String pathParam = (String) arguments.get("path");
        if (pathParam == null) {
            // Check for alternative keys that might contain path
            if (arguments.containsKey("directory")) {
                pathParam = (String) arguments.get("directory");
                log.warn("Using 'directory' instead of 'path' for GitTool");
            } else if (arguments.containsKey("repo")) {
                pathParam = (String) arguments.get("repo");
                log.warn("Using 'repo' instead of 'path' for GitTool");
            } else if (arguments.containsKey("repoPath")) {
                pathParam = (String) arguments.get("repoPath");
                log.warn("Using 'repoPath' instead of 'path' for GitTool");
            } else {
                log.error("Path parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("Path parameter is required"));
            }
        }
        
        // Create final copy of path for lambda
        final String path = pathParam;
        
        // Use aiDeveloperAgentSessionId if available, otherwise fall back to sessionId
        String aiDeveloperAgentSessionIdParam = (String) arguments.getOrDefault("aiDeveloperAgentSessionId", 
                                                arguments.getOrDefault("sessionId", UUID.randomUUID().toString()));
        final String aiDeveloperAgentSessionId = aiDeveloperAgentSessionIdParam;
        
        // Use sessionWorkspaceRootFolder for workspace management if provided, otherwise fall back to aiDeveloperAgentSessionId
        String sessionWorkspaceRootFolderParam = (String) arguments.getOrDefault(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, aiDeveloperAgentSessionId);
        final String sessionWorkspaceRootFolder = sessionWorkspaceRootFolderParam;
        
        // Resolve path within session workspace
        String resolvedPathParam = resolvePath(path, sessionWorkspaceRootFolder);
        final String resolvedPath = resolvedPathParam;
        
        // Create final copy of arguments for lambda
        final Map<String, Object> finalArguments = arguments;
        
        return Mono.fromCallable(() -> {
            // Validate operation
            if (operation == null) {
                throw new IllegalArgumentException("Operation parameter is required");
            }
            
            switch (operation.toLowerCase()) {
                case "init":
                    return initRepository(resolvedPath, aiDeveloperAgentSessionId);
                case "clone":
                    return cloneRepository((String) finalArguments.get("url"), resolvedPath, aiDeveloperAgentSessionId);
                case "add":
                    return addFiles(resolvedPath, aiDeveloperAgentSessionId);
                case "commit":
                    return commitChanges(resolvedPath, (String) finalArguments.get("message"), aiDeveloperAgentSessionId);
                case "status":
                    return getStatus(resolvedPath, aiDeveloperAgentSessionId);
                case "log":
                    return getLog(resolvedPath, aiDeveloperAgentSessionId);
                case "branch":
                    return manageBranch(resolvedPath, (String) finalArguments.get("branch"), aiDeveloperAgentSessionId);
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
        }).flux();
    }
    
    /**
     * Resolve a path within the session workspace
     * If the path is absolute, return it as is
     * If the path is relative, resolve it within the session workspace
     */
    private String resolvePath(String path, String sessionWorkspaceRootFolder) {
        if (path.startsWith("/")) {
            return path; // Absolute path, use as is
        }
        
        // Create session workspace directory if it doesn't exist
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionWorkspaceRootFolder;
        try {
            Files.createDirectories(Path.of(workspacePath));
            log.info("{}: [{}] Creating workspace directory at path: {}", FILE_OP_LOG_PREFIX, getName(), workspacePath);
        } catch (Exception e) {
            log.error("{}: [{}] Error creating workspace directory: {}", FILE_OP_LOG_PREFIX, getName(), workspacePath, e);
        }
        
        // Resolve relative path within workspace
        return workspacePath + "/" + path;
    }
    
    private ToolOutput initRepository(String path, String aiDeveloperAgentSessionId) throws GitAPIException {
        // Ensure directory exists
        try {
            Files.createDirectories(Path.of(path));
            log.info("{}: [{}] Creating directory for Git repository at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
        } catch (Exception e) {
            log.error("{}: [{}] Error creating directory: {}", FILE_OP_LOG_PREFIX, getName(), path, e);
            throw new GitAPIException("Error creating directory: " + e.getMessage()) {};
        }
        
        Git.init().setDirectory(new File(path)).call();
        log.info("{}: [{}] Initialized Git repository at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
        
        return ToolOutput.builder()
                .type("git_init")
                .content("Initialized empty Git repository in " + path)
                .metadata(Map.of(
                    "path", path,
                    "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                    "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                ))
                .build();
    }
    
    private ToolOutput cloneRepository(String url, String path, String aiDeveloperAgentSessionId) throws GitAPIException {
        // Ensure parent directory exists
        try {
            Files.createDirectories(Path.of(path).getParent());
            log.info("{}: [{}] Creating parent directory for Git clone at path: {}", FILE_OP_LOG_PREFIX, getName(), Path.of(path).getParent());
        } catch (Exception e) {
            log.error("{}: [{}] Error creating parent directory for: {}", FILE_OP_LOG_PREFIX, getName(), path, e);
            throw new GitAPIException("Error creating parent directory: " + e.getMessage()) {};
        }
        
        Git.cloneRepository()
                .setURI(url)
                .setDirectory(new File(path))
                .call();
        
        log.info("{}: [{}] Cloned Git repository from {} to path: {}", FILE_OP_LOG_PREFIX, getName(), url, path);
        
        return ToolOutput.builder()
                .type("git_clone")
                .content("Cloned repository from " + url)
                .metadata(Map.of(
                    "url", url, 
                    "path", path,
                    "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                    "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                ))
                .build();
    }
    
    private ToolOutput addFiles(String path, String aiDeveloperAgentSessionId) throws Exception {
        try (Git git = Git.open(new File(path))) {
            git.add().addFilepattern(".").call();
            log.info("{}: [{}] Added all files to Git staging area at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
            
            return ToolOutput.builder()
                    .type("git_add")
                    .content("Added all files to staging area")
                    .metadata(Map.of(
                        "path", path,
                        "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                        "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                    ))
                    .build();
        }
    }
    
    private ToolOutput commitChanges(String path, String message, String aiDeveloperAgentSessionId) throws Exception {
        try (Git git = Git.open(new File(path))) {
            RevCommit commit = git.commit()
                    .setMessage(message != null ? message : "Auto-commit by AI Agent")
                    .call();
            
            log.info("{}: [{}] Committed changes in Git repository at path: {}, commit ID: {}", 
                    FILE_OP_LOG_PREFIX, getName(), path, commit.getId().getName());
            
            return ToolOutput.builder()
                    .type("git_commit")
                    .content("Committed changes: " + commit.getId().getName())
                    .metadata(Map.of(
                        "commitId", commit.getId().getName(),
                        "message", commit.getFullMessage(),
                        "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                        "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                    ))
                    .build();
        }
    }
    
    private ToolOutput getStatus(String path, String aiDeveloperAgentSessionId) throws Exception {
        try (Git git = Git.open(new File(path))) {
            Status status = git.status().call();
            
            log.info("{}: [{}] Retrieved Git status for repository at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
            
            Map<String, Object> statusInfo = new HashMap<>();
            statusInfo.put("added", status.getAdded());
            statusInfo.put("changed", status.getChanged());
            statusInfo.put("removed", status.getRemoved());
            statusInfo.put("untracked", status.getUntracked());
            statusInfo.put("modified", status.getModified());
            statusInfo.put("aiDeveloperAgentSessionId", aiDeveloperAgentSessionId);
            statusInfo.put("workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId);
            
            return ToolOutput.builder()
                    .type("git_status")
                    .content("Repository status retrieved")
                    .metadata(statusInfo)
                    .build();
        }
    }
    
    private ToolOutput getLog(String path, String aiDeveloperAgentSessionId) throws Exception {
        try (Git git = Git.open(new File(path))) {
            List<Map<String, String>> commits = new ArrayList<>();
            
            log.info("{}: [{}] Retrieving Git log for repository at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
            
            Iterable<RevCommit> log = git.log().setMaxCount(10).call();
            for (RevCommit commit : log) {
                commits.add(Map.of(
                    "id", commit.getId().getName(),
                    "message", commit.getShortMessage(),
                    "author", commit.getAuthorIdent().getName(),
                    "date", new Date(commit.getCommitTime() * 1000L).toString()
                ));
            }
            
            return ToolOutput.builder()
                    .type("git_log")
                    .content("Recent commits retrieved")
                    .metadata(Map.of(
                        "commits", commits,
                        "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                        "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                    ))
                    .build();
        }
    }
    
    private ToolOutput manageBranch(String path, String branchName, String aiDeveloperAgentSessionId) throws Exception {
        try (Git git = Git.open(new File(path))) {
            if (branchName != null) {
                git.checkout().setName(branchName).setCreateBranch(true).call();
                log.info("{}: [{}] Created and switched to branch '{}' in repository at path: {}", 
                        FILE_OP_LOG_PREFIX, getName(), branchName, path);
                
                return ToolOutput.builder()
                        .type("git_branch")
                        .content("Created and switched to branch: " + branchName)
                        .metadata(Map.of(
                            "branch", branchName,
                            "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                            "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                        ))
                        .build();
            } else {
                List<String> branches = new ArrayList<>();
                git.branchList().call().forEach(ref -> 
                    branches.add(ref.getName().replace("refs/heads/", "")));
                
                log.info("{}: [{}] Listed branches in repository at path: {}", FILE_OP_LOG_PREFIX, getName(), path);
                
                return ToolOutput.builder()
                        .type("git_branch_list")
                        .content("Available branches")
                        .metadata(Map.of(
                            "branches", branches,
                            "aiDeveloperAgentSessionId", aiDeveloperAgentSessionId,
                            "workspacePath", DEFAULT_WORKSPACE_PATH + "/" + aiDeveloperAgentSessionId
                        ))
                        .build();
            }
        }
    }
}
