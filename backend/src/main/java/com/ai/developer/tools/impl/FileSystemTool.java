package com.ai.developer.tools.impl;

import com.ai.developer.llm.ChatContext;
import com.ai.developer.service.ChatService;
import com.ai.developer.tools.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.file.*;
import java.util.*;
import java.io.*;

@Slf4j
@Component
public class FileSystemTool implements Tool {
    
    @Autowired
    private ChatService chatService;
    
    @Override
    public String getName() {
        return "file_system";
    }
    
    @Override
    public String getDescription() {
        return "Perform file system operations like read, write, list, and delete";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .name("operation")
            .type("string")
            .description("Operation: read, write, append, list, delete")
            .required(true)
            .build());
            
        params.put("path", ParameterInfo.builder()
            .name("path")
            .type("string")
            .description("File or directory path")
            .required(true)
            .build());
            
        params.put("content", ParameterInfo.builder()
            .name("content")
            .type("string")
            .description("Content to write (for write/append operations)")
            .required(false)
            .build());
            
        params.put("sessionId", ParameterInfo.builder()
            .name("sessionId")
            .type("string")
            .description("Session ID for workspace resolution (optional)")
            .required(false)
            .build());
            
        params.put("taskName", ParameterInfo.builder()
            .name("taskName")
            .type("string")
            .description("Task name for subdirectory organization (optional)")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String operation = (String) arguments.get("operation");
        String path = (String) arguments.get("path");
        String sessionId = (String) arguments.getOrDefault("sessionId", null);
        String taskName = (String) arguments.getOrDefault("taskName", null);
        
        // Fix for NullPointerException: Add null check for operation
        if (operation == null) {
            log.error("Operation cannot be null");
            return Flux.error(new IllegalArgumentException("Operation parameter is required"));
        }
        
        // Fix for NullPointerException: Add null check for path
        if (path == null) {
            log.error("Path cannot be null");
            return Flux.error(new IllegalArgumentException("Path parameter is required"));
        }
        
        // Resolve path using session workspace if sessionId is provided
        String resolvedPath = path;
        if (sessionId != null && !sessionId.isEmpty()) {
            try {
                ChatContext context = chatService.getSession(sessionId);
                if (context != null) {
                    // If task name is provided, create or get task directory
                    if (taskName != null && !taskName.isEmpty()) {
                        String taskDir = context.getTaskDirectory(taskName);
                        if (taskDir == null) {
                            // Create task directory if it doesn't exist
                            taskDir = context.createTaskDirectory(taskName);
                            log.debug("Created task directory: {} for task: {}", taskDir, taskName);
                        }
                        
                        // If path is not absolute, resolve it against task directory
                        if (!new File(path).isAbsolute()) {
                            resolvedPath = Paths.get(taskDir, path).toString();
                            log.debug("Resolved path against task directory: {} -> {}", path, resolvedPath);
                        }
                    } else {
                        // No task name, resolve against workspace
                        resolvedPath = context.resolvePath(path);
                        log.debug("Resolved path against workspace: {} -> {}", path, resolvedPath);
                    }
                    
                    // Ensure parent directories exist
                    File file = new File(resolvedPath);
                    File parentDir = file.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                        log.debug("Created parent directories for: {}", resolvedPath);
                    }
                } else {
                    log.warn("Session not found: {}. Using original path.", sessionId);
                }
            } catch (Exception e) {
                // If any error occurs during path resolution, fall back to original path
                log.warn("Error resolving path: {}. Using original path. Error: {}", path, e.getMessage());
            }
        }
        
        final String finalPath = resolvedPath;
        
        return switch (operation.toLowerCase()) {
            case "read" -> readFile(finalPath, sessionId);
            case "write" -> writeFile(finalPath, (String) arguments.get("content"), sessionId);
            case "append" -> appendFile(finalPath, (String) arguments.get("content"), sessionId);
            case "list" -> listDirectory(finalPath, sessionId);
            case "delete" -> deleteFile(finalPath, sessionId);
            default -> Flux.error(new IllegalArgumentException("Unknown operation: " + operation));
        };
    }
    
    private Flux<ToolOutput> readFile(String path, String sessionId) {
        return Mono.fromCallable(() -> {
            try {
                String content = Files.readString(Path.of(path));
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("path", path);
                metadata.put("size", content.length());
                if (sessionId != null) {
                    metadata.put("sessionId", sessionId);
                }
                
                return ToolOutput.builder()
                        .type("file_content")
                        .content(content)
                        .metadata(metadata)
                        .build();
            } catch (IOException e) {
                log.error("Error reading file: {}", path, e);
                throw new RuntimeException("Error reading file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> writeFile(String path, String content, String sessionId) {
        return Mono.fromCallable(() -> {
            try {
                // Ensure parent directories exist
                File file = new File(path);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                    log.debug("Created parent directories for: {}", path);
                }
                
                Files.writeString(Path.of(path), content != null ? content : "");
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("path", path);
                metadata.put("size", content != null ? content.length() : 0);
                if (sessionId != null) {
                    metadata.put("sessionId", sessionId);
                }
                
                return ToolOutput.builder()
                        .type("file_written")
                        .content("File written successfully")
                        .metadata(metadata)
                        .build();
            } catch (IOException e) {
                log.error("Error writing file: {}", path, e);
                throw new RuntimeException("Error writing file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> appendFile(String path, String content, String sessionId) {
        return Mono.fromCallable(() -> {
            try {
                // Ensure parent directories exist
                File file = new File(path);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                    log.debug("Created parent directories for: {}", path);
                }
                
                Files.writeString(Path.of(path), content != null ? content : "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("path", path);
                metadata.put("appended_size", content != null ? content.length() : 0);
                if (sessionId != null) {
                    metadata.put("sessionId", sessionId);
                }
                
                return ToolOutput.builder()
                        .type("file_appended")
                        .content("Content appended successfully")
                        .metadata(metadata)
                        .build();
            } catch (IOException e) {
                log.error("Error appending to file: {}", path, e);
                throw new RuntimeException("Error appending to file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> listDirectory(String path, String sessionId) {
        return Mono.fromCallable(() -> {
            try {
                List<Map<String, Object>> entries = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(path))) {
                    for (Path entry : stream) {
                        boolean isDirectory = Files.isDirectory(entry);
                        entries.add(Map.of(
                            "name", entry.getFileName().toString(),
                            "path", entry.toString(),
                            "isDirectory", isDirectory,
                            "size", isDirectory ? 0 : Files.size(entry),
                            "lastModified", Files.getLastModifiedTime(entry).toMillis()
                        ));
                    }
                }
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("path", path);
                metadata.put("entries", entries);
                if (sessionId != null) {
                    metadata.put("sessionId", sessionId);
                }
                
                return ToolOutput.builder()
                        .type("directory_listing")
                        .content("Directory listed successfully")
                        .metadata(metadata)
                        .build();
            } catch (IOException e) {
                log.error("Error listing directory: {}", path, e);
                throw new RuntimeException("Error listing directory: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> deleteFile(String path, String sessionId) {
        return Mono.fromCallable(() -> {
            try {
                boolean deleted = Files.deleteIfExists(Path.of(path));
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("path", path);
                metadata.put("deleted", deleted);
                if (sessionId != null) {
                    metadata.put("sessionId", sessionId);
                }
                
                return ToolOutput.builder()
                        .type("file_deleted")
                        .content(deleted ? "File deleted successfully" : "File does not exist")
                        .metadata(metadata)
                        .build();
            } catch (IOException e) {
                log.error("Error deleting file: {}", path, e);
                throw new RuntimeException("Error deleting file: " + e.getMessage());
            }
        }).flux();
    }
}
