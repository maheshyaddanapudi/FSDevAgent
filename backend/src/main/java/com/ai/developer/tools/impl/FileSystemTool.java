package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.file.*;
import java.util.*;
import java.io.*;

@Slf4j
@Component
public class FileSystemTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    @Override
    public String getName() {
        return "file_system";
    }
    
    @Override
    public String getDescription() {
        return "Perform file system operations like read, write, list, and delete within workspace directories";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .name("operation")
            .type("string")
            .description("Operation: read, write, append, list, delete, mkdir")
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
            .description("Chat session ID for workspace management")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String operation = (String) arguments.get("operation");
        String path = (String) arguments.get("path");
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        
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
        
        // Resolve path within session workspace if it's not absolute
        String resolvedPath = resolvePath(path, sessionId);
        
        return switch (operation.toLowerCase()) {
            case "read" -> readFile(resolvedPath);
            case "write" -> writeFile(resolvedPath, (String) arguments.get("content"));
            case "append" -> appendFile(resolvedPath, (String) arguments.get("content"));
            case "list" -> listDirectory(resolvedPath);
            case "delete" -> deleteFile(resolvedPath);
            case "mkdir" -> createDirectory(resolvedPath);
            default -> Flux.error(new IllegalArgumentException("Unknown operation: " + operation));
        };
    }
    
    /**
     * Resolve a path within the session workspace
     * If the path is absolute, return it as is
     * If the path is relative, resolve it within the session workspace
     */
    private String resolvePath(String path, String sessionId) {
        if (path.startsWith("/")) {
            return path; // Absolute path, use as is
        }
        
        // Create session workspace directory if it doesn't exist
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        try {
            Files.createDirectories(Path.of(workspacePath));
        } catch (IOException e) {
            log.error("Error creating workspace directory: {}", workspacePath, e);
        }
        
        // Resolve relative path within workspace
        return workspacePath + "/" + path;
    }
    
    private Flux<ToolOutput> readFile(String path) {
        return Mono.fromCallable(() -> {
            try {
                String content = Files.readString(Path.of(path));
                return ToolOutput.builder()
                        .type("file_content")
                        .content(content)
                        .metadata(Map.of(
                            "path", path,
                            "size", content.length()
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error reading file: {}", path, e);
                throw new RuntimeException("Error reading file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> writeFile(String path, String content) {
        return Mono.fromCallable(() -> {
            try {
                // Ensure parent directories exist
                Path filePath = Path.of(path);
                Files.createDirectories(filePath.getParent());
                
                Files.writeString(filePath, content);
                return ToolOutput.builder()
                        .type("file_written")
                        .content("File written successfully")
                        .metadata(Map.of(
                            "path", path,
                            "size", content.length()
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error writing file: {}", path, e);
                throw new RuntimeException("Error writing file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> appendFile(String path, String content) {
        return Mono.fromCallable(() -> {
            try {
                // Ensure parent directories exist
                Path filePath = Path.of(path);
                Files.createDirectories(filePath.getParent());
                
                Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return ToolOutput.builder()
                        .type("file_appended")
                        .content("Content appended successfully")
                        .metadata(Map.of(
                            "path", path,
                            "appended_size", content.length()
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error appending to file: {}", path, e);
                throw new RuntimeException("Error appending to file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> listDirectory(String path) {
        return Mono.fromCallable(() -> {
            try {
                // Create directory if it doesn't exist
                Path dirPath = Path.of(path);
                Files.createDirectories(dirPath);
                
                List<Map<String, Object>> entries = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
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
                
                return ToolOutput.builder()
                        .type("directory_listing")
                        .content("Directory listed successfully")
                        .metadata(Map.of(
                            "path", path,
                            "entries", entries
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error listing directory: {}", path, e);
                throw new RuntimeException("Error listing directory: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> deleteFile(String path) {
        return Mono.fromCallable(() -> {
            try {
                boolean deleted = Files.deleteIfExists(Path.of(path));
                return ToolOutput.builder()
                        .type("file_deleted")
                        .content(deleted ? "File deleted successfully" : "File does not exist")
                        .metadata(Map.of(
                            "path", path,
                            "deleted", deleted
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error deleting file: {}", path, e);
                throw new RuntimeException("Error deleting file: " + e.getMessage());
            }
        }).flux();
    }
    
    private Flux<ToolOutput> createDirectory(String path) {
        return Mono.fromCallable(() -> {
            try {
                Files.createDirectories(Path.of(path));
                return ToolOutput.builder()
                        .type("directory_created")
                        .content("Directory created successfully")
                        .metadata(Map.of(
                            "path", path
                        ))
                        .build();
            } catch (IOException e) {
                log.error("Error creating directory: {}", path, e);
                throw new RuntimeException("Error creating directory: " + e.getMessage());
            }
        }).flux();
    }
}
