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
            .type("string")
            .description("Operation: read, write, append, list, delete")
            .required(true)
            .build());
            
        params.put("path", ParameterInfo.builder()
            .type("string")
            .description("File or directory path")
            .required(true)
            .build());
            
        params.put("content", ParameterInfo.builder()
            .type("string")
            .description("Content to write (for write/append operations)")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String operation = (String) arguments.get("operation");
        String path = (String) arguments.get("path");
        
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
        
        return switch (operation.toLowerCase()) {
            case "read" -> readFile(path);
            case "write" -> writeFile(path, (String) arguments.get("content"));
            case "append" -> appendFile(path, (String) arguments.get("content"));
            case "list" -> listDirectory(path);
            case "delete" -> deleteFile(path);
            default -> Flux.error(new IllegalArgumentException("Unknown operation: " + operation));
        };
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
                Files.writeString(Path.of(path), content);
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
                Files.writeString(Path.of(path), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
}
