package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.pty4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class TerminalTool implements Tool {
    
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
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        String workingDir = (String) arguments.getOrDefault("workingDirectory", ".");
        
        return Flux.create(sink -> {
            try {
                Map<String, String> env = new HashMap<>(System.getenv());
                String[] cmd = command.split(" ");
                
                PtyProcessBuilder builder = new PtyProcessBuilder()
                        .setCommand(cmd)
                        .setEnvironment(env)
                        .setDirectory(workingDir)
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
                                    .metadata(Map.of("command", command))
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
                            .metadata(Map.of("command", command, "exitCode", exitCode))
                            .build());
                    sink.complete();
                }
                
            } catch (Exception e) {
                log.error("Error executing command: {}", command, e);
                sink.error(e);
            }
        });
    }
}
