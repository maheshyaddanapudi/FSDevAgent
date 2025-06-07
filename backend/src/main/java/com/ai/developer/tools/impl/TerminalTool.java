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
            .description("Chat session ID for workspace management")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Log the raw arguments for debugging
        log.info("TerminalTool executing with arguments: {}", arguments);
        
        // Fix for NullPointerException: Add null check for arguments
        if (arguments == null) {
            log.error("Arguments map is null");
            return Flux.error(new IllegalArgumentException("Arguments map is null"));
        }
        
        String command = (String) arguments.get("command");
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        String workingDir = (String) arguments.getOrDefault("workingDirectory", null);
        
        // Log the specific operation being attempted
        log.info("TerminalTool executing command: {} in directory: {} for session: {}", 
                command, workingDir, sessionId);
        
        // Resolve working directory within session workspace
        String resolvedWorkingDir = resolveWorkingDirectory(workingDir, sessionId);
        
        return Flux.create(sink -> {
            try {
                // Add null check for command parameter
                if (command == null) {
                    log.error("Command parameter is null");
                    sink.error(new IllegalArgumentException("Command parameter cannot be null"));
                    return;
                }
                
                // Ensure the working directory exists
                try {
                    Files.createDirectories(Path.of(resolvedWorkingDir));
                } catch (IOException e) {
                    log.error("Error creating working directory: {}", resolvedWorkingDir, e);
                    sink.error(new IOException("Error creating working directory: " + e.getMessage()));
                    return;
                }
                
                Map<String, String> env = new HashMap<>(System.getenv());
                // Add session workspace to environment variables
                env.put("WORKSPACE_PATH", DEFAULT_WORKSPACE_PATH + "/" + sessionId);
                env.put("SESSION_ID", sessionId);
                
                String[] cmd = command.split(" ");
                
                log.info("Starting process with command: {} in directory: {}", command, resolvedWorkingDir);
                
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
                            log.debug("Command output: {}", line);
                            sink.next(ToolOutput.builder()
                                    .type("stdout")
                                    .content(line)
                                    .metadata(Map.of(
                                        "command", command,
                                        "workingDirectory", resolvedWorkingDir,
                                        "sessionId", sessionId
                                    ))
                                    .build());
                        }
                    } catch (IOException e) {
                        log.error("Error reading process output: {}", e.getMessage(), e);
                        sink.error(e);
                    }
                });
                
                outputReader.start();
                
                // Wait for process completion
                boolean completed = process.waitFor(30, TimeUnit.SECONDS);
                if (!completed) {
                    log.warn("Command timed out after 30 seconds: {}", command);
                    process.destroyForcibly();
                    sink.error(new TimeoutException("Command timed out after 30 seconds"));
                } else {
                    int exitCode = process.exitValue();
                    log.info("Command completed with exit code {}: {}", exitCode, command);
                    sink.next(ToolOutput.builder()
                            .type("exit")
                            .content(String.valueOf(exitCode))
                            .metadata(Map.of(
                                "command", command, 
                                "exitCode", exitCode,
                                "workingDirectory", resolvedWorkingDir,
                                "sessionId", sessionId
                            ))
                            .build());
                    sink.complete();
                }
                
            } catch (Exception e) {
                log.error("Error executing command: {}", command, e);
                sink.error(e);
            }
        });
    }
    
    /**
     * Resolve working directory within session workspace
     * If the path is absolute, return it as is
     * If the path is relative or null, resolve it within the session workspace
     */
    private String resolveWorkingDirectory(String workingDir, String sessionId) {
        // Create session workspace directory
        String sessionWorkspace = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        
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
