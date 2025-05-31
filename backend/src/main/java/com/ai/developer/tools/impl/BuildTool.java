package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import org.apache.maven.shared.invoker.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.io.*;
import java.util.*;

@Slf4j
@Component
public class BuildTool implements Tool {
    
    @Override
    public String getName() {
        return "build_tool";
    }
    
    @Override
    public String getDescription() {
        return "Execute Maven or Gradle build commands";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("tool", ParameterInfo.builder()
            .type("string")
            .description("Build tool: maven or gradle")
            .required(true)
            .build());
            
        params.put("projectPath", ParameterInfo.builder()
            .type("string")
            .description("Path to project")
            .required(true)
            .build());
            
        params.put("goals", ParameterInfo.builder()
            .type("array")
            .description("Build goals/tasks to execute")
            .required(true)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String tool = (String) arguments.get("tool");
        String projectPath = (String) arguments.get("projectPath");
        List<String> goals = (List<String>) arguments.get("goals");
        
        if ("maven".equalsIgnoreCase(tool)) {
            return executeMaven(projectPath, goals);
        } else if ("gradle".equalsIgnoreCase(tool)) {
            return executeGradle(projectPath, goals);
        } else {
            return Flux.error(new IllegalArgumentException("Unknown build tool: " + tool));
        }
    }
    
    private Flux<ToolOutput> executeMaven(String projectPath, List<String> goals) {
        return Flux.create(sink -> {
            try {
                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(new File(projectPath, "pom.xml"));
                request.setGoals(goals);
                request.setOutputHandler(line -> {
                    sink.next(ToolOutput.builder()
                            .type("build_output")
                            .content(line)
                            .metadata(Map.of("tool", "maven"))
                            .build());
                });
                
                Invoker invoker = new DefaultInvoker();
                InvocationResult result = invoker.execute(request);
                
                if (result.getExitCode() == 0) {
                    sink.next(ToolOutput.builder()
                            .type("build_complete")
                            .content("Maven build completed successfully")
                            .metadata(Map.of("exitCode", result.getExitCode()))
                            .build());
                } else {
                    sink.next(ToolOutput.builder()
                            .type("build_error")
                            .content("Maven build failed with exit code: " + result.getExitCode())
                            .metadata(Map.of("exitCode", result.getExitCode()))
                            .build());
                }
                
                sink.complete();
            } catch (Exception e) {
                log.error("Error executing Maven build", e);
                sink.error(e);
            }
        });
    }
    
    private Flux<ToolOutput> executeGradle(String projectPath, List<String> goals) {
        return Flux.create(sink -> {
            try {
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
                
                Process process = processBuilder.start();
                
                // Read output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sink.next(ToolOutput.builder()
                                .type("build_output")
                                .content(line)
                                .metadata(Map.of("tool", "gradle"))
                                .build());
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    sink.next(ToolOutput.builder()
                            .type("build_complete")
                            .content("Gradle build completed successfully")
                            .metadata(Map.of("exitCode", exitCode))
                            .build());
                } else {
                    sink.next(ToolOutput.builder()
                            .type("build_error")
                            .content("Gradle build failed with exit code: " + exitCode)
                            .metadata(Map.of("exitCode", exitCode))
                            .build());
                }
                
                sink.complete();
            } catch (Exception e) {
                log.error("Error executing Gradle build", e);
                sink.error(e);
            }
        });
    }
}
