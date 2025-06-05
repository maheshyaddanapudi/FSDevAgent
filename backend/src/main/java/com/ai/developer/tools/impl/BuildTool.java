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
        String tool = (String) arguments.get("tool");
        String projectPath = (String) arguments.get("projectPath");
        List<String> goals = (List<String>) arguments.get("goals");
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        String taskDir = (String) arguments.getOrDefault("taskDir", "");
        
        // Resolve project path within session workspace
        String resolvedProjectPath = resolveProjectPath(projectPath, sessionId, taskDir);
        
        if ("maven".equalsIgnoreCase(tool)) {
            return executeMaven(resolvedProjectPath, goals, sessionId, taskDir);
        } else if ("gradle".equalsIgnoreCase(tool)) {
            return executeGradle(resolvedProjectPath, goals, sessionId, taskDir);
        } else {
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
        return Flux.create(sink -> {
            try {
                // Ensure project directory exists
                Files.createDirectories(Path.of(projectPath));
                
                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(new File(projectPath, "pom.xml"));
                request.setGoals(goals);
                request.setOutputHandler(line -> {
                    sink.next(ToolOutput.builder()
                            .type("build_output")
                            .content(line)
                            .metadata(Map.of(
                                "tool", "maven",
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir),
                                "projectPath", projectPath
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
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir),
                                "projectPath", projectPath
                            ))
                            .build());
                } else {
                    sink.next(ToolOutput.builder()
                            .type("build_error")
                            .content("Maven build failed with exit code: " + result.getExitCode())
                            .metadata(Map.of(
                                "exitCode", result.getExitCode(),
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir),
                                "projectPath", projectPath
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
