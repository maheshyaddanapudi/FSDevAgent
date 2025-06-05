package com.ai.developer.tools.impl;

import com.ai.developer.llm.ChatContext;
import com.ai.developer.service.ChatService;
import com.ai.developer.tools.*;
import com.pty4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class TerminalTool implements Tool {
    
    @Autowired
    private ChatService chatService;
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "Execute shell commands in the terminal";
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
            .description("Working directory for command execution")
            .required(false)
            .build());
            
        params.put("sessionId", ParameterInfo.builder()
            .name("sessionId")
            .type("string")
            .description("Session ID for workspace resolution (optional)")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        String workingDir = (String) arguments.getOrDefault("workingDirectory", ".");
        String sessionId = (String) arguments.getOrDefault("sessionId", null);
        
        // Only attempt workspace resolution if sessionId is provided
        if (sessionId != null && !sessionId.isEmpty()) {
            try {
                ChatContext context = chatService.getSession(sessionId);
                if (context != null && context.getWorkspaceDirectory() != null) {
                    // If working directory is ".", use session workspace
                    if (workingDir.equals(".")) {
                        workingDir = context.getWorkspaceDirectory();
                        log.debug("Using session workspace as working directory: {}", workingDir);
                    } 
                    // If working directory is relative, resolve it against workspace
                    else if (!new File(workingDir).isAbsolute()) {
                        workingDir = context.resolvePath(workingDir);
                        log.debug("Resolved working directory: {}", workingDir);
                    }
                    
                    // Ensure the directory exists
                    File dir = new File(workingDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                        log.debug("Created working directory: {}", workingDir);
                    }
                } else {
                    log.debug("Session not found or has no workspace: {}. Using provided working directory.", sessionId);
                }
            } catch (Exception e) {
                // If any error occurs during workspace resolution, fall back to provided directory
                log.warn("Error resolving workspace directory: {}. Using provided working directory.", e.getMessage());
            }
        }
        
        final String finalWorkingDir = workingDir;
        
        return Flux.create(sink -> {
            try {
                // Add null check for command parameter
                if (command == null) {
                    log.error("Command parameter is null");
                    sink.error(new IllegalArgumentException("Command parameter cannot be null"));
                    return;
                }
                
                log.info("Executing command: {} in directory: {}", command, finalWorkingDir);
                
                Map<String, String> env = new HashMap<>(System.getenv());
                String[] cmd = command.split(" ");
                
                PtyProcessBuilder builder = new PtyProcessBuilder()
                        .setCommand(cmd)
                        .setEnvironment(env)
                        .setDirectory(finalWorkingDir)
                        .setRedirectErrorStream(true);
                
                PtyProcess process = builder.start();
                
                // Read output in separate thread
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("command", command);
                            metadata.put("workingDirectory", finalWorkingDir);
                            if (sessionId != null) {
                                metadata.put("sessionId", sessionId);
                            }
                            
                            sink.next(ToolOutput.builder()
                                    .type("stdout")
                                    .content(line)
                                    .metadata(metadata)
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
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("command", command);
                    metadata.put("exitCode", exitCode);
                    metadata.put("workingDirectory", finalWorkingDir);
                    if (sessionId != null) {
                        metadata.put("sessionId", sessionId);
                    }
                    
                    sink.next(ToolOutput.builder()
                            .type("exit")
                            .content(String.valueOf(exitCode))
                            .metadata(metadata)
                            .build());
                    sink.complete();
                }
                
            } catch (Exception e) {
                log.error("Error executing command: {} in directory: {}", command, finalWorkingDir, e);
                sink.error(e);
            }
        });
    }
}
