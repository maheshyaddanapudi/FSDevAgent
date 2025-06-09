package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.pty4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class TerminalTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "Execute shell commands in the terminal within session-specific workspace";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("command", ParameterInfo.builder()
            .name("command")
            .type("string")
            .description("The command to execute")
            .required(true)
            .build());
            
        params.put("workingDirectory", ParameterInfo.builder()
            .name("workingDirectory")
            .type("string")
            .description("Working directory for command execution (absolute path or relative to session workspace)")
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
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Enhanced logging for debugging argument structure
        log.info("TerminalTool executing with arguments: {}", arguments);
        
        // Extract command with alternative key checking
        String command = (String) arguments.get("command");
        if (command == null) {
            // Check for alternative keys that might contain command
            if (arguments.containsKey("cmd")) {
                command = (String) arguments.get("cmd");
                log.warn("Using 'cmd' instead of 'command' for TerminalTool");
            } else if (arguments.containsKey("script")) {
                command = (String) arguments.get("script");
                log.warn("Using 'script' instead of 'command' for TerminalTool");
            } else if (arguments.containsKey("shellCommand")) {
                command = (String) arguments.get("shellCommand");
                log.warn("Using 'shellCommand' instead of 'command' for TerminalTool");
            } else {
                log.error("Command parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("Command parameter cannot be null"));
            }
        }
        
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        
        // Use sessionWorkspaceRootFolder for workspace management if provided, otherwise fall back to sessionId
        String sessionWorkspaceRootFolder = (String) arguments.getOrDefault(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, sessionId);
        
        // Extract working directory with alternative key checking
        String workingDir = (String) arguments.get("workingDirectory");
        if (workingDir == null) {
            // Check for alternative keys that might contain working directory
            if (arguments.containsKey("workingDir")) {
                workingDir = (String) arguments.get("workingDir");
                log.warn("Using 'workingDir' instead of 'workingDirectory' for TerminalTool");
            } else if (arguments.containsKey("cwd")) {
                workingDir = (String) arguments.get("cwd");
                log.warn("Using 'cwd' instead of 'workingDirectory' for TerminalTool");
            } else if (arguments.containsKey("directory")) {
                workingDir = (String) arguments.get("directory");
                log.warn("Using 'directory' instead of 'workingDirectory' for TerminalTool");
            }
            // Working directory is optional, so no error if not found
        }
        
        // Resolve working directory within session workspace
        String resolvedWorkingDir = resolveWorkingDirectory(workingDir, sessionWorkspaceRootFolder);
        
        // Create final copies of all variables used in lambda
        final String finalCommand = command;
        final String finalResolvedWorkingDir = resolvedWorkingDir;
        final String finalSessionId = sessionId;
        
        return Flux.create(sink -> {
            try {
                // Command validation already done above with detailed logging
                
                // Ensure the working directory exists
                try {
                    Files.createDirectories(Path.of(finalResolvedWorkingDir));
                } catch (IOException e) {
                    log.error("Error creating working directory: {}", finalResolvedWorkingDir, e);
                    sink.error(new IOException("Error creating working directory: " + e.getMessage()));
                    return;
                }
                
                Map<String, String> env = new HashMap<>(System.getenv());
                // Add session workspace to environment variables
                env.put("WORKSPACE_PATH", DEFAULT_WORKSPACE_PATH + "/" + finalSessionId);
                env.put("SESSION_ID", finalSessionId);
                
                String[] cmd = finalCommand.split(" ");
                
                PtyProcessBuilder builder = new PtyProcessBuilder()
                        .setCommand(cmd)
                        .setEnvironment(env)
                        .setDirectory(resolvedWorkingDir)
                        .setRedirectErrorStream(true);
                
                PtyProcess process = builder.start();
                
                // Read output in separate thread
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sink.next(ToolOutput.builder()
                                    .type("stdout")
                                    .content(line)
                                    .metadata(Map.of(
                                        "command", finalCommand,
                                        "workingDirectory", finalResolvedWorkingDir,
                                        "sessionId", finalSessionId
                                    ))
                                    .build());
                        }
                    } catch (IOException e) {
                        sink.error(e);
                    }
                });
                
                outputReader.start();
                
                // Wait for process completion
                boolean completed = process.waitFor(30, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    sink.error(new TimeoutException("Command timed out after 30 seconds"));
                } else {
                    int exitCode = process.exitValue();
                    sink.next(ToolOutput.builder()
                            .type("exit")
                            .content(String.valueOf(exitCode))
                            .metadata(Map.of(
                                "command", finalCommand, 
                                "exitCode", exitCode,
                                "workingDirectory", finalResolvedWorkingDir,
                                "sessionId", finalSessionId
                            ))
                            .build());
                    sink.complete();
                }
                
            } catch (Exception e) {
                log.error("Error executing command: {}", finalCommand, e);
                sink.error(e);
            }
        });
    }
    
    /**
     * Resolve working directory within session workspace
     * If the path is absolute, return it as is
     * If the path is relative or null, resolve it within the session workspace
     */
    private String resolveWorkingDirectory(String workingDir, String sessionWorkspaceRootFolder) {
        // Create session workspace directory
        String sessionWorkspace = DEFAULT_WORKSPACE_PATH + "/" + sessionWorkspaceRootFolder;
        
        // If working directory is null or empty, use session workspace
        if (workingDir == null || workingDir.isEmpty()) {
            return sessionWorkspace;
        }
        
        // If working directory is absolute, use it as is
        if (workingDir.startsWith("/")) {
            return workingDir;
        }
        
        // Otherwise, resolve relative path within session workspace
        return sessionWorkspace + "/" + workingDir;
    }
}
