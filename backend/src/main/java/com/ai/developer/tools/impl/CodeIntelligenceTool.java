package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CodeIntelligenceTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    @Override
    public String getName() {
        return "code_intelligence";
    }
    
    @Override
    public String getDescription() {
        return "Analyze and manipulate code with AST operations within session-specific workspaces";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .name("operation")
            .type("string")
            .description("Operation: analyze, find_methods, find_classes, extract_javadoc")
            .required(true)
            .build());
            
        params.put("path", ParameterInfo.builder()
            .name("path")
            .type("string")
            .description("File or directory path (absolute or relative to session workspace)")
            .required(true)
            .build());
            
        params.put("query", ParameterInfo.builder()
            .name("query")
            .type("string")
            .description("Search query or pattern")
            .required(false)
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
        String operation = (String) arguments.get("operation");
        String path = (String) arguments.get("path");
        String query = (String) arguments.getOrDefault("query", "");
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        String taskDir = (String) arguments.getOrDefault("taskDir", "");
        
        // Resolve path within session workspace
        String resolvedPath = resolvePath(path, sessionId, taskDir);
        
        return switch (operation.toLowerCase()) {
            case "analyze" -> analyzeCode(resolvedPath, sessionId, taskDir);
            case "find_methods" -> findMethods(resolvedPath, query, sessionId, taskDir);
            case "find_classes" -> findClasses(resolvedPath, query, sessionId, taskDir);
            case "extract_javadoc" -> extractJavadoc(resolvedPath, sessionId, taskDir);
            default -> Flux.error(new IllegalArgumentException("Unknown operation: " + operation));
        };
    }
    
    /**
     * Resolve a path within the session workspace
     * If the path is absolute, return it as is
     * If the path is relative, resolve it within the session workspace and task directory
     */
    private String resolvePath(String path, String sessionId, String taskDir) {
        if (path.startsWith("/")) {
            return path; // Absolute path, use as is
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
        return sessionWorkspace + "/" + path;
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
    
    private Flux<ToolOutput> analyzeCode(String path, String sessionId, String taskDir) {
        return Mono.fromCallable(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    throw new IllegalArgumentException("File does not exist: " + path);
                }
                
                if (file.isDirectory()) {
                    return analyzeDirectory(file, sessionId, taskDir);
                } else {
                    return analyzeFile(file, sessionId, taskDir);
                }
            } catch (Exception e) {
                log.error("Error analyzing code: {}", path, e);
                throw new RuntimeException("Error analyzing code: " + e.getMessage());
            }
        }).flux();
    }
    
    private ToolOutput analyzeFile(File file, String sessionId, String taskDir) throws Exception {
        if (!file.getName().endsWith(".java")) {
            return ToolOutput.builder()
                    .type("analysis_result")
                    .content("Not a Java file: " + file.getName())
                    .metadata(Map.of(
                        "path", file.getPath(),
                        "sessionId", sessionId,
                        "workspacePath", getWorkspacePath(sessionId, taskDir)
                    ))
                    .build();
        }
        
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
            
            List<String> classes = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .collect(Collectors.toList());
                    
            List<String> methods = cu.findAll(MethodDeclaration.class).stream()
                    .map(MethodDeclaration::getNameAsString)
                    .collect(Collectors.toList());
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("path", file.getPath());
            metadata.put("classes", classes);
            metadata.put("methods", methods);
            metadata.put("imports", cu.getImports().size());
            metadata.put("sessionId", sessionId);
            metadata.put("workspacePath", getWorkspacePath(sessionId, taskDir));
            
            return ToolOutput.builder()
                    .type("analysis_result")
                    .content("Code analysis completed for: " + file.getName())
                    .metadata(metadata)
                    .build();
        }
    }
    
    private ToolOutput analyzeDirectory(File directory, String sessionId, String taskDir) throws Exception {
        List<Map<String, Object>> fileResults = new ArrayList<>();
        int totalFiles = 0;
        int totalClasses = 0;
        int totalMethods = 0;
        
        for (File file : directory.listFiles((dir, name) -> name.endsWith(".java"))) {
            try (FileInputStream in = new FileInputStream(file)) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                
                if (cu != null) {
                    List<String> classes = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                            .map(ClassOrInterfaceDeclaration::getNameAsString)
                            .collect(Collectors.toList());
                            
                    List<String> methods = cu.findAll(MethodDeclaration.class).stream()
                            .map(MethodDeclaration::getNameAsString)
                            .collect(Collectors.toList());
                    
                    totalFiles++;
                    totalClasses += classes.size();
                    totalMethods += methods.size();
                    
                    fileResults.add(Map.of(
                        "file", file.getName(),
                        "classes", classes,
                        "methods", methods
                    ));
                }
            } catch (Exception e) {
                log.warn("Error parsing file: {}", file.getName(), e);
            }
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("path", directory.getPath());
        metadata.put("totalFiles", totalFiles);
        metadata.put("totalClasses", totalClasses);
        metadata.put("totalMethods", totalMethods);
        metadata.put("fileResults", fileResults);
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", getWorkspacePath(sessionId, taskDir));
        
        return ToolOutput.builder()
                .type("analysis_result")
                .content("Directory analysis completed for: " + directory.getName())
                .metadata(metadata)
                .build();
    }
    
    private Flux<ToolOutput> findMethods(String path, String query, String sessionId, String taskDir) {
        return Mono.fromCallable(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    throw new IllegalArgumentException("File does not exist: " + path);
                }
                
                List<Map<String, Object>> methods = new ArrayList<>();
                
                if (file.isDirectory()) {
                    for (File javaFile : Files.walk(file.toPath())
                            .filter(p -> p.toString().endsWith(".java"))
                            .map(Path::toFile)
                            .collect(Collectors.toList())) {
                        methods.addAll(findMethodsInFile(javaFile, query));
                    }
                } else {
                    methods.addAll(findMethodsInFile(file, query));
                }
                
                return ToolOutput.builder()
                        .type("method_search_result")
                        .content("Found " + methods.size() + " methods matching: " + query)
                        .metadata(Map.of(
                            "query", query,
                            "methods", methods,
                            "sessionId", sessionId,
                            "workspacePath", getWorkspacePath(sessionId, taskDir)
                        ))
                        .build();
            } catch (Exception e) {
                log.error("Error finding methods: {}", path, e);
                throw new RuntimeException("Error finding methods: " + e.getMessage());
            }
        }).flux();
    }
    
    private List<Map<String, Object>> findMethodsInFile(File file, String query) throws Exception {
        if (!file.getName().endsWith(".java")) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> methods = new ArrayList<>();
        
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            
            if (cu != null) {
                cu.findAll(MethodDeclaration.class).stream()
                        .filter(method -> query.isEmpty() || method.getNameAsString().contains(query))
                        .forEach(method -> {
                            Map<String, Object> methodInfo = new HashMap<>();
                            methodInfo.put("name", method.getNameAsString());
                            methodInfo.put("returnType", method.getTypeAsString());
                            methodInfo.put("parameters", method.getParameters().toString());
                            methodInfo.put("file", file.getName());
                            methodInfo.put("line", method.getBegin().map(p -> p.line).orElse(-1));
                            
                            methods.add(methodInfo);
                        });
            }
        } catch (Exception e) {
            log.warn("Error parsing file: {}", file.getName(), e);
        }
        
        return methods;
    }
    
    private Flux<ToolOutput> findClasses(String path, String query, String sessionId, String taskDir) {
        return Mono.fromCallable(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    throw new IllegalArgumentException("File does not exist: " + path);
                }
                
                List<Map<String, Object>> classes = new ArrayList<>();
                
                if (file.isDirectory()) {
                    for (File javaFile : Files.walk(file.toPath())
                            .filter(p -> p.toString().endsWith(".java"))
                            .map(Path::toFile)
                            .collect(Collectors.toList())) {
                        classes.addAll(findClassesInFile(javaFile, query));
                    }
                } else {
                    classes.addAll(findClassesInFile(file, query));
                }
                
                return ToolOutput.builder()
                        .type("class_search_result")
                        .content("Found " + classes.size() + " classes matching: " + query)
                        .metadata(Map.of(
                            "query", query,
                            "classes", classes,
                            "sessionId", sessionId,
                            "workspacePath", getWorkspacePath(sessionId, taskDir)
                        ))
                        .build();
            } catch (Exception e) {
                log.error("Error finding classes: {}", path, e);
                throw new RuntimeException("Error finding classes: " + e.getMessage());
            }
        }).flux();
    }
    
    private List<Map<String, Object>> findClassesInFile(File file, String query) throws Exception {
        if (!file.getName().endsWith(".java")) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> classes = new ArrayList<>();
        
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElse(null);
            
            if (cu != null) {
                cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                        .filter(cls -> query.isEmpty() || cls.getNameAsString().contains(query))
                        .forEach(cls -> {
                            Map<String, Object> classInfo = new HashMap<>();
                            classInfo.put("name", cls.getNameAsString());
                            classInfo.put("isInterface", cls.isInterface());
                            classInfo.put("methods", cls.getMethods().size());
                            classInfo.put("file", file.getName());
                            classInfo.put("line", cls.getBegin().map(p -> p.line).orElse(-1));
                            
                            classes.add(classInfo);
                        });
            }
        } catch (Exception e) {
            log.warn("Error parsing file: {}", file.getName(), e);
        }
        
        return classes;
    }
    
    private Flux<ToolOutput> extractJavadoc(String path, String sessionId, String taskDir) {
        return Mono.fromCallable(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    throw new IllegalArgumentException("File does not exist: " + path);
                }
                
                if (!file.getName().endsWith(".java")) {
                    return ToolOutput.builder()
                            .type("javadoc_result")
                            .content("Not a Java file: " + file.getName())
                            .metadata(Map.of(
                                "path", file.getPath(),
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir)
                            ))
                            .build();
                }
                
                try (FileInputStream in = new FileInputStream(file)) {
                    JavaParser parser = new JavaParser();
                    CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
                    
                    List<Map<String, Object>> javadocs = new ArrayList<>();
                    
                    // Extract class javadocs
                    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                        cls.getJavadoc().ifPresent(javadoc -> {
                            javadocs.add(Map.of(
                                "type", "class",
                                "name", cls.getNameAsString(),
                                "javadoc", javadoc.getDescription().toText()
                            ));
                        });
                    });
                    
                    // Extract method javadocs
                    cu.findAll(MethodDeclaration.class).forEach(method -> {
                        method.getJavadoc().ifPresent(javadoc -> {
                            javadocs.add(Map.of(
                                "type", "method",
                                "name", method.getNameAsString(),
                                "javadoc", javadoc.getDescription().toText()
                            ));
                        });
                    });
                    
                    return ToolOutput.builder()
                            .type("javadoc_result")
                            .content("Extracted " + javadocs.size() + " javadoc comments from: " + file.getName())
                            .metadata(Map.of(
                                "path", file.getPath(),
                                "javadocs", javadocs,
                                "sessionId", sessionId,
                                "workspacePath", getWorkspacePath(sessionId, taskDir)
                            ))
                            .build();
                }
            } catch (Exception e) {
                log.error("Error extracting javadoc: {}", path, e);
                throw new RuntimeException("Error extracting javadoc: " + e.getMessage());
            }
        }).flux();
    }
}
