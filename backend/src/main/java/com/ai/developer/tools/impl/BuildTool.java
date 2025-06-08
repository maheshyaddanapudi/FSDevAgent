package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import org.apache.maven.shared.invoker.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
public class BuildTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    @Override
    public String getName() {
        return "build_tool";
    }
    
    @Override
    public String getDescription() {
        return "Execute Maven or Gradle build commands within session-specific workspaces";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("tool", ParameterInfo.builder()
            .name("tool")
            .type("string")
            .description("Build tool: maven or gradle")
            .required(true)
            .build());
            
        params.put("projectPath", ParameterInfo.builder()
            .name("projectPath")
            .type("string")
            .description("Path to project (absolute or relative to session workspace)")
            .required(true)
            .build());
            
        params.put("goals", ParameterInfo.builder()
            .name("goals")
            .type("array")
            .description("Build goals/tasks to execute")
            .required(true)
            .build());
            
        params.put("sessionId", ParameterInfo.builder()
            .name("sessionId")
            .type("string")
            .description("Chat session ID for workspace management")
            .required(false)
            .build());
            
        params.put("taskDir", ParameterInfo.builder()
            .name("taskDir")
            .type("string")
            .description("Task-specific subdirectory within the session workspace")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Enhanced logging for debugging argument structure
        log.info("BuildTool executing with arguments: {}", arguments);
        
        // Extract build tool with alternative key checking
        String tool = (String) arguments.get("tool");
        if (tool == null) {
            // Check for alternative keys that might contain tool
            if (arguments.containsKey("buildTool")) {
                tool = (String) arguments.get("buildTool");
                log.warn("Using 'buildTool' instead of 'tool' for BuildTool");
            } else if (arguments.containsKey("type")) {
                tool = (String) arguments.get("type");
                log.warn("Using 'type' instead of 'tool' for BuildTool");
            } else if (arguments.containsKey("framework")) {
                tool = (String) arguments.get("framework");
                log.warn("Using 'framework' instead of 'tool' for BuildTool");
            } else {
                log.error("Tool parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("Tool parameter is required"));
            }
        }
        
        // Extract project path with alternative key checking
        String projectPath = (String) arguments.get("projectPath");
        if (projectPath == null) {
            // Check for alternative keys that might contain project path
            if (arguments.containsKey("path")) {
                projectPath = (String) arguments.get("path");
                log.warn("Using 'path' instead of 'projectPath' for BuildTool");
            } else if (arguments.containsKey("directory")) {
                projectPath = (String) arguments.get("directory");
                log.warn("Using 'directory' instead of 'projectPath' for BuildTool");
            } else if (arguments.containsKey("project")) {
                projectPath = (String) arguments.get("project");
                log.warn("Using 'project' instead of 'projectPath' for BuildTool");
            } else {
                log.error("ProjectPath parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("ProjectPath parameter is required"));
            }
        }
        
        // Extract goals with alternative key checking
        List<String> goals = (List<String>) arguments.get("goals");
        if (goals == null) {
            // Check for alternative keys that might contain goals
            if (arguments.containsKey("tasks")) {
                goals = (List<String>) arguments.get("tasks");
                log.warn("Using 'tasks' instead of 'goals' for BuildTool");
            } else if (arguments.containsKey("commands")) {
                goals = (List<String>) arguments.get("commands");
                log.warn("Using 'commands' instead of 'goals' for BuildTool");
            } else if (arguments.containsKey("targets")) {
                goals = (List<String>) arguments.get("targets");
                log.warn("Using 'targets' instead of 'goals' for BuildTool");
            } else {
                log.warn("Goals parameter is null, using default empty list");
                goals = new ArrayList<>();
            }
        }
        
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        String taskDir = (String) arguments.getOrDefault("taskDir", "");
        
        // Resolve project path within session workspace
        String resolvedProjectPath = resolveProjectPath(projectPath, sessionId, taskDir);
        
        // Log the final parameters being used
        log.info("BuildTool executing with tool: {}, projectPath: {}, goals: {}", tool, resolvedProjectPath, goals);
        
        if ("maven".equalsIgnoreCase(tool)) {
            return executeMaven(resolvedProjectPath, goals, sessionId, taskDir);
        } else if ("gradle".equalsIgnoreCase(tool)) {
            return executeGradle(resolvedProjectPath, goals, sessionId, taskDir);
        } else {
            log.error("Unknown build tool: {}. Supported tools: maven, gradle", tool);
            return Flux.error(new IllegalArgumentException("Unknown build tool: " + tool));
        }
    }
    
    /**
     * Resolve project path within session workspace
     * If the path is absolute, return it as is
     * If the path is relative, resolve it within the session workspace and task directory
     */
    private String resolveProjectPath(String projectPath, String sessionId, String taskDir) {
        if (projectPath.startsWith("/")) {
            return projectPath; // Absolute path, use as is
        }
        
        // Create session workspace directory
        String sessionWorkspace = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        
        // If task directory is specified, include it in the path
        if (taskDir != null && !taskDir.isEmpty()) {
            sessionWorkspace = sessionWorkspace + "/" + taskDir;
        }
        
        try {
            Files.createDirectories(Path.of(sessionWorkspace));
        } catch (Exception e) {
            log.error("Error creating workspace directory: {}", sessionWorkspace, e);
        }
        
        // Resolve relative path within workspace
        return sessionWorkspace + "/" + projectPath;
    }
    
    private Flux<ToolOutput> executeMaven(String projectPath, List<String> goals, String sessionId, String taskDir) {
        // Create final copies of variables for lambda
        final String finalProjectPath = projectPath;
        final List<String> finalGoals = goals;
        final String finalSessionId = sessionId;
        final String finalTaskDir = taskDir;
        
        return Flux.create(sink -> {
            try {
                // Ensure project directory exists
                Files.createDirectories(Path.of(finalProjectPath));
                
                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(new File(finalProjectPath, "pom.xml"));
                request.setGoals(finalGoals);
                request.setOutputHandler(line -> {
                    sink.next(ToolOutput.builder()
                            .type("build_output")
                            .content(line)
                            .metadata(Map.of(
                                "tool", "maven",
                                "sessionId", finalSessionId,
                                "workspacePath", getWorkspacePath(finalSessionId, finalTaskDir),
                                "projectPath", finalProjectPath
                            ))
                            .build());
                });
                
                Invoker invoker = new DefaultInvoker();
                InvocationResult result = invoker.execute(request);
                
                if (result.getExitCode() == 0) {
                    sink.next(ToolOutput.builder()
                            .type("build_complete")
                            .content("Maven build completed successfully")
                            .metadata(Map.of(
                                "exitCode", result.getExitCode(),
                                "sessionId", finalSessionId,
                                "workspacePath", getWorkspacePath(finalSessionId, finalTaskDir),
                                "projectPath", finalProjectPath
                            ))
                            .build());
                } else {
                    sink.next(ToolOutput.builder()
                            .type("build_error")
                            .content("Maven build failed with exit code: " + result.getExitCode())
                            .metadata(Map.of(
                                "exitCode", result.getExitCode(),
                                "sessionId", finalSessionId,
                                "workspacePath", getWorkspacePath(finalSessionId, finalTaskDir),
                                "projectPath", finalProjectPath
                            ))
                            .build());
                }
                
                sink.complete();
            } catch (Exception e) {
                log.error("Error executing Maven build", e);
                sink.error(e);
            }
        });
    }
    
    private Flux<ToolOutput> executeGradle(String projectPath, List<String> goals, String sessionId, String taskDir) {
        return Flux.create(sink -> {
            try {
                // Ensure project directory exists
                Files.createDirectories(Path.of(projectPath));
                
                ProcessBuilder processBuilder = new ProcessBuilder();
                List<String> command = new ArrayList<>();
                
                // Use gradlew if available, otherwise use gradle
                File gradlew = new File(projectPath, "gradlew");
                if (gradlew.exists() && gradlew.canExecute()) {
                    command.add("./gradlew");
                } else {
                    command.add("gradle");
                }
                
                command.addAll(goals);
                processBuilder.command(command);
                processBuilder.directory(new File(projectPath));
                processBuilder.redirectErrorStream(true);
                
                // Add workspace information to environment
                Map<String, String> env = processBuilder.environment();
                env.put("WORKSPACE_PATH", getWorkspacePath(sessionId, taskDir));
                env.put("SESSION_ID", sessionId);
                
                Process process = processBuilder.start();
                
                // Read output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sink.next(ToolOutput.builder()
                                .type("build_output")
                                .content(line)
                                .metadata(Map.of(
                                    "tool", "gradle",
                                    "sessionId", sessionId,
                                    "workspacePath", getWorkspacePath(sessionId, taskDir),
                                    "projectPath", projectPath
                                ))
                                .build());
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    sink.next(ToolOutput.builder()
                            .type("build_complete")
                            .content("Gradle build completed successfully")
                            .metadata(Map.of(
                                "exitCode", exitCode,
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir),
                                "projectPath", projectPath
                            ))
                            .build());
                } else {
                    sink.next(ToolOutput.builder()
                            .type("build_error")
                            .content("Gradle build failed with exit code: " + exitCode)
                            .metadata(Map.of(
                                "exitCode", exitCode,
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir),
                                "projectPath", projectPath
                            ))
                            .build());
                }
                
                sink.complete();
            } catch (Exception e) {
                log.error("Error executing Gradle build", e);
                sink.error(e);
            }
        });
    }
    
    /**
     * Get the full workspace path including session ID and optional task directory
     */
    private String getWorkspacePath(String sessionId, String taskDir) {
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        if (taskDir != null && !taskDir.isEmpty()) {
            workspacePath = workspacePath + "/" + taskDir;
        }
        return workspacePath;
    }
}
