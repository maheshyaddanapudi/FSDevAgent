package com.ai.developer.service;

import com.ai.developer.model.ToolInvocation;

import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that maps high-level tasks to specific tool invocations.
 * This is the bridge between planning and execution.
 */
@Slf4j
@Service
public class TaskExecutorService {
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolOutputWebSocketHandler webSocketHandler;
    private final CodeGenerationService codeGenerationService;
    private final ProjectTemplateManager projectTemplateManager;
    
    public TaskExecutorService(ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                             ToolOutputWebSocketHandler webSocketHandler,
                             CodeGenerationService codeGenerationService,
                             ProjectTemplateManager projectTemplateManager) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.codeGenerationService = codeGenerationService;
        this.projectTemplateManager = projectTemplateManager;
    }
    
    /**
     * Execute a high-level task by mapping it to tool invocations
     */
    public Flux<ToolOutput> executeTask(String taskType, String taskDescription, Map<String, Object> context) {
        log.info("Executing task: {} - {}", taskType, taskDescription);
        
        // Map task types to execution strategies
        return switch (taskType.toLowerCase()) {
            case "setup_project" -> executeProjectSetup(context);
            case "create_backend" -> executeBackendCreation(context);
            case "create_frontend" -> executeFrontendCreation(context);
            case "create_database" -> executeDatabaseSetup(context);
            case "create_api" -> executeApiCreation(context);
            case "create_component" -> executeComponentCreation(context);
            case "run_tests" -> executeTestRun(context);
            case "deploy" -> executeDeployment(context);
            default -> executeGenericTask(taskDescription, context);
        };
    }
    
    /**
     * Execute project setup tasks
     */
    private Flux<ToolOutput> executeProjectSetup(Map<String, Object> context) {
        String projectName = (String) context.getOrDefault("projectName", "myapp");
        String projectType = (String) context.getOrDefault("projectType", "fullstack");
        String workspacePath = (String) context.get("workspacePath");
        String templateId = (String) context.getOrDefault("templateId", projectType);
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        // Use ProjectTemplateManager to apply template if available
        try {
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("projectName", projectName);
            templateVariables.put("projectType", projectType);
            templateVariables.putAll(context);
            
            // Apply template using ProjectTemplateManager
            String sessionId = (String) context.get("sessionId");
            String targetDir = workspacePath + "/" + projectName;
            
            log.info("Applying project template '{}' to directory: {}", templateId, targetDir);
            
            // Ensure target directory exists
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "mkdir",
                "path", targetDir
            )));
            
            // Convert context map to string-only variables for template
            Map<String, String> stringTemplateVariables = new HashMap<>();
            for (Map.Entry<String, Object> entry : templateVariables.entrySet()) {
                if (entry.getValue() != null) {
                    stringTemplateVariables.put(entry.getKey(), entry.getValue().toString());
                }
            }
            
            // Apply template via ProjectTemplateManager
            projectTemplateManager.applyTemplate(sessionId, templateId, targetDir, stringTemplateVariables);
            
        } catch (Exception e) {
            log.warn("Failed to apply template '{}', falling back to manual setup: {}", templateId, e.getMessage());
            
            // Fallback to manual project structure creation
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "mkdir",
                "path", projectName
            )));
            
            // Create subdirectories based on project type
            if ("fullstack".equals(projectType) || "backend".equals(projectType)) {
                toolSequence.add(new ToolInvocation("file_system", Map.of(
                    "operation", "mkdir",
                    "path", projectName + "/backend"
                )));
                toolSequence.add(new ToolInvocation("file_system", Map.of(
                    "operation", "mkdir",
                    "path", projectName + "/backend/src"
                )));
            }
            
            if ("fullstack".equals(projectType) || "frontend".equals(projectType)) {
                toolSequence.add(new ToolInvocation("file_system", Map.of(
                    "operation", "mkdir",
                    "path", projectName + "/frontend"
                )));
                toolSequence.add(new ToolInvocation("file_system", Map.of(
                    "operation", "mkdir",
                    "path", projectName + "/frontend/src"
                )));
            }
            
            // Initialize git repository
            toolSequence.add(new ToolInvocation("git_operations", Map.of(
                "operation", "init",
                "path", projectName
            )));
            
            // Create README
            String readmeContent = generateReadme(projectName, projectType);
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectName + "/README.md",
                "content", readmeContent
            )));
        }
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute backend creation tasks
     */
    private Flux<ToolOutput> executeBackendCreation(Map<String, Object> context) {
        String framework = (String) context.getOrDefault("framework", "spring-boot");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        if ("spring-boot".equals(framework)) {
            // Create Spring Boot project structure
            toolSequence.addAll(createSpringBootStructure(projectPath));
            
            // Generate pom.xml
            String pomContent = generateSpringBootPom(context);
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/backend/pom.xml",
                "content", pomContent
            )));
            
            // Generate main application class
            String mainClass = generateSpringBootMainClass(context);
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/backend/src/main/java/com/example/Application.java",
                "content", mainClass
            )));
            
            // Generate application.properties
            String properties = generateApplicationProperties(context);
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/backend/src/main/resources/application.properties",
                "content", properties
            )));
        }
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute frontend creation tasks
     */
    private Flux<ToolOutput> executeFrontendCreation(Map<String, Object> context) {
        String framework = (String) context.getOrDefault("framework", "react");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        if ("react".equals(framework)) {
            // Initialize React app
            toolSequence.add(new ToolInvocation("execute_command", Map.of(
                "command", "npx create-react-app frontend --template typescript",
                "workingDirectory", projectPath
            )));
            
            // Install additional dependencies
            toolSequence.add(new ToolInvocation("execute_command", Map.of(
                "command", "npm install axios react-router-dom @types/react-router-dom",
                "workingDirectory", projectPath + "/frontend"
            )));
            
            // Create components directory
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "mkdir",
                "path", projectPath + "/frontend/src/components"
            )));
            
            // Generate App component
            String appComponent = generateReactAppComponent(context);
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/frontend/src/App.tsx",
                "content", appComponent
            )));
        }
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute database setup tasks
     */
    private Flux<ToolOutput> executeDatabaseSetup(Map<String, Object> context) {
        String dbType = (String) context.getOrDefault("database", "postgresql");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        // Create database directory
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/database"
        )));
        
        // Generate schema
        String schema = generateDatabaseSchema(context);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/database/schema.sql",
            "content", schema
        )));
        
        // Generate docker-compose for database
        String dockerCompose = generateDatabaseDockerCompose(dbType);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/docker-compose.yml",
            "content", dockerCompose
        )));
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute API creation tasks
     */
    private Flux<ToolOutput> executeApiCreation(Map<String, Object> context) {
        String apiType = (String) context.getOrDefault("apiType", "rest");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        // Create API directories
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/main/java/com/example/controller"
        )));
        
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/main/java/com/example/service"
        )));
        
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/main/java/com/example/model"
        )));
        
        // Generate controller
        String controller = generateRestController(context);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/backend/src/main/java/com/example/controller/ApiController.java",
            "content", controller
        )));
        
        // Generate service
        String service = generateService(context);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/backend/src/main/java/com/example/service/ApiService.java",
            "content", service
        )));
        
        // Generate model
        String model = generateModel(context);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/backend/src/main/java/com/example/model/Entity.java",
            "content", model
        )));
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute component creation tasks
     */
    private Flux<ToolOutput> executeComponentCreation(Map<String, Object> context) {
        String componentName = (String) context.getOrDefault("componentName", "Component");
        String componentType = (String) context.getOrDefault("componentType", "functional");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        // Use CodeGenerationService to generate component
        Map<String, Object> codeContext = new HashMap<>();
        codeContext.put("name", componentName);
        codeContext.put("type", componentType);
        codeContext.putAll(context);
        
        String component = codeGenerationService.generateCode("react-component", codeContext);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/frontend/src/components/" + componentName + ".tsx",
            "content", component
        )));
        
        // Generate component styles using CodeGenerationService
        String styles = codeGenerationService.generateCode("react-styles", codeContext);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/frontend/src/components/" + componentName + ".css",
            "content", styles
        )));
        
        // Generate component test using CodeGenerationService
        String test = codeGenerationService.generateCode("react-test", codeContext);
        toolSequence.add(new ToolInvocation("file_system", Map.of(
            "operation", "write",
            "path", projectPath + "/frontend/src/components/" + componentName + ".test.tsx",
            "content", test
        )));
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute test run tasks
     */
    private Flux<ToolOutput> executeTestRun(Map<String, Object> context) {
        String projectPath = (String) context.get("projectPath");
        String testType = (String) context.getOrDefault("testType", "all");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        // Run backend tests
        if ("all".equals(testType) || "backend".equals(testType)) {
            toolSequence.add(new ToolInvocation("build_tool", Map.of(
                "tool", "maven",
                "projectPath", projectPath + "/backend",
                "goals", List.of("test")
            )));
        }
        
        // Run frontend tests
        if ("all".equals(testType) || "frontend".equals(testType)) {
            toolSequence.add(new ToolInvocation("execute_command", Map.of(
                "command", "npm test",
                "workingDirectory", projectPath + "/frontend"
            )));
        }
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute deployment tasks
     */
    private Flux<ToolOutput> executeDeployment(Map<String, Object> context) {
        String deploymentType = (String) context.getOrDefault("deploymentType", "docker");
        String projectPath = (String) context.get("projectPath");
        
        List<ToolInvocation> toolSequence = new ArrayList<>();
        
        if ("docker".equals(deploymentType)) {
            // Generate Dockerfile for backend
            String backendDockerfile = generateBackendDockerfile();
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/backend/Dockerfile",
                "content", backendDockerfile
            )));
            
            // Generate Dockerfile for frontend
            String frontendDockerfile = generateFrontendDockerfile();
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/frontend/Dockerfile",
                "content", frontendDockerfile
            )));
            
            // Generate docker-compose
            String dockerCompose = generateFullStackDockerCompose();
            toolSequence.add(new ToolInvocation("file_system", Map.of(
                "operation", "write",
                "path", projectPath + "/docker-compose.yml",
                "content", dockerCompose
            )));
            
            // Build images
            toolSequence.add(new ToolInvocation("execute_command", Map.of(
                "command", "docker-compose build",
                "workingDirectory", projectPath
            )));
        }
        
        return executeToolSequence(toolSequence, context);
    }
    
    /**
     * Execute a generic task using planning
     */
    private Flux<ToolOutput> executeGenericTask(String taskDescription, Map<String, Object> context) {
        // Use planning tool to break down the task
        Tool planningTool = toolRegistry.getTool("planning_tool");
        if (planningTool != null) {
            Map<String, Object> planningArgs = new HashMap<>();
            planningArgs.put("operation", "create_plan");
            planningArgs.put("objective", taskDescription);
            planningArgs.putAll(context);
            
            return planningTool.execute(planningArgs);
        }
        
        return Flux.just(ToolOutput.builder()
                .type("error")
                .content("Unable to execute generic task: " + taskDescription)
                .build());
    }
    
    /**
     * Execute a sequence of tool invocations
     */    private Flux<ToolOutput> executeToolSequence(List<ToolInvocation> toolSequence, Map<String, Object> context) {
        return Flux.fromIterable(toolSequence)
            .concatMap(invocation -> {
                Tool tool = toolRegistry.getTool(invocation.getName());
                if (tool == null) {
                    return Flux.just(ToolOutput.builder()
                            .type("error")
                            .content("Tool not found: " + invocation.getName())
                            .build());
                }
                
                // Add context to arguments
                Map<String, Object> args = new HashMap<>(invocation.getArgs());
                args.putAll(context);
                
                // Extract JSON content from tool_use blocks if needed
                Object rawArgsObj = invocation.getArgs();
                
                if (rawArgsObj instanceof String rawArgs && rawArgs.contains("<tool_use>")) {
                    Pattern pattern = Pattern.compile("<tool_use>(.*?)</tool_use>", Pattern.DOTALL);
                    Matcher matcher = pattern.matcher(rawArgs);
                    if (matcher.find()) {
                        String jsonContent = matcher.group(1);
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> extractedArgs = objectMapper.readValue(jsonContent, Map.class);
                            args = new HashMap<>(extractedArgs);
                            args.putAll(context);
                        } catch (Exception e) {
                            log.error("Error parsing tool_use JSON content: {}", e.getMessage());
                            return Flux.error(e);
                        }
                    } else {
                        log.warn("No valid <tool_use>...</tool_use> block found in: {}", rawArgs);
                    }
                }
                
                // Add sessionWorkspaceRootFolder parameter for workspace management
                // Use sessionId as sessionWorkspaceRootFolder if not explicitly provided
                String sessionId = (String) context.get("sessionId");
                String sessionWorkspaceRootFolder = sessionId;
                if (context.containsKey(SESSION_WORKSPACE_ROOT_FOLDER_PARAM)) {
                    sessionWorkspaceRootFolder = (String) context.get(SESSION_WORKSPACE_ROOT_FOLDER_PARAM);
                }
                
                // Ensure sessionId is preserved for Claude's internal tracking
                args.put("sessionId", sessionId);
                // Add sessionWorkspaceRootFolder for workspace management
                args.put(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, sessionWorkspaceRootFolder);
                
                // Ensure we're passing a non-null map with the correct parameter name
                Map<String, Object> arguments = args;
                
                return tool.execute(arguments);
            });
    }
    
    // Using the standalone ToolInvocation model class from com.ai.developer.model package
    
    // Code generation methods (simplified - in real implementation these would be more sophisticated)
    
    private String generateReadme(String projectName, String projectType) {
        return String.format("""
            # %s
            
            A %s application built with modern technologies.
            
            ## Project Structure
            
            ```
            %s/
            ├── backend/         # Backend API
            ├── frontend/        # Frontend application
            ├── database/        # Database schemas and migrations
            └── docker-compose.yml
            ```
            
            ## Getting Started
            
            ### Prerequisites
            - Java 17+
            - Node.js 16+
            - Docker (optional)
            
            ### Running the Application
            
            1. Start the backend:
               ```bash
               cd backend
               mvn spring-boot:run
               ```
            
            2. Start the frontend:
               ```bash
               cd frontend
               npm install
               npm start
               ```
            
            ## Development
            
            This project was generated by AI Developer Agent.
            """, projectName, projectType, projectName);
    }
    
    private List<ToolInvocation> createSpringBootStructure(String projectPath) {
        List<ToolInvocation> structure = new ArrayList<>();
        
        // Maven structure
        structure.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/main/java/com/example"
        )));
        structure.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/main/resources"
        )));
        structure.add(new ToolInvocation("file_system", Map.of(
            "operation", "mkdir",
            "path", projectPath + "/backend/src/test/java/com/example"
        )));
        
        return structure;
    }
    
    /**
     * Helper method to safely create a Map for file content operations
     * This ensures proper serialization of content with special characters
     */
    private Map<String, Object> createFileContentMap(String operation, String path, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("operation", operation);
        map.put("path", path);
        if (content != null) {
            map.put("content", content);
        }
        return map;
    }
    
    private String generateSpringBootPom(Map<String, Object> context) {
        String appName = (String) context.getOrDefault("appName", "myapp");
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                    <relativePath/>
                </parent>
                
                <groupId>com.example</groupId>
                <artifactId>%s</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <name>%s</name>
                <description>Backend API for %s</description>
                
                <properties>
                    <java.version>17</java.version>
                </properties>
                
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-test</artifactId>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <optional>true</optional>
                    </dependency>
                </dependencies>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """, appName, appName, appName);
    }
    
    private String generateSpringBootMainClass(Map<String, Object> context) {
        return """
            package com.example;
            
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            
            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            }
            """;
    }
    
    private String generateApplicationProperties(Map<String, Object> context) {
        return """
            # Server configuration
            server.port=8080
            
            # Database configuration
            spring.datasource.url=jdbc:postgresql://localhost:5432/myapp
            spring.datasource.username=postgres
            spring.datasource.password=postgres
            
            # JPA configuration
            spring.jpa.hibernate.ddl-auto=update
            spring.jpa.show-sql=true
            spring.jpa.properties.hibernate.format_sql=true
            
            # Logging
            logging.level.root=INFO
            logging.level.com.example=DEBUG
            """;
    }
    
    private String generateReactAppComponent(Map<String, Object> context) {
        return """
            import React from 'react';
            import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
            import './App.css';
            
            function App() {
              return (
                <Router>
                  <div className="App">
                    <header className="App-header">
                      <nav>
                        <ul>
                          <li><Link to="/">Home</Link></li>
                          <li><Link to="/about">About</Link></li>
                          <li><Link to="/items">Items</Link></li>
                        </ul>
                      </nav>
                    </header>
                    
                    <main>
                      <Routes>
                        <Route path="/" element={<Home />} />
                        <Route path="/about" element={<About />} />
                        <Route path="/items" element={<Items />} />
                      </Routes>
                    </main>
                  </div>
                </Router>
              );
            }
            
            function Home() {
              return <h1>Welcome to My App</h1>;
            }
            
            function About() {
              return <h1>About Us</h1>;
            }
            
            function Items() {
              return <h1>Items List</h1>;
            }
            
            export default App;
            """;
    }
    
    private String generateDatabaseSchema(Map<String, Object> context) {
        String appType = (String) context.getOrDefault("appType", "generic");
        
        if ("todo".equals(appType)) {
            return """
                -- Todo application schema
                
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                
                CREATE TABLE IF NOT EXISTS todos (
                    id SERIAL PRIMARY KEY,
                    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
                    title VARCHAR(200) NOT NULL,
                    description TEXT,
                    completed BOOLEAN DEFAULT FALSE,
                    due_date DATE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                
                CREATE INDEX idx_todos_user_id ON todos(user_id);
                CREATE INDEX idx_todos_completed ON todos(completed);
                """;
        }
        
        return """
            -- Generic application schema
            
            CREATE TABLE IF NOT EXISTS entities (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
    }
    
    private String generateDatabaseDockerCompose(String dbType) {
        if ("postgresql".equals(dbType)) {
            return """
                version: '3.8'
                
                services:
                  postgres:
                    image: postgres:15-alpine
                    container_name: myapp-postgres
                    environment:
                      POSTGRES_DB: myapp
                      POSTGRES_USER: postgres
                      POSTGRES_PASSWORD: postgres
                    ports:
                      - "5432:5432"
                    volumes:
                      - postgres_data:/var/lib/postgresql/data
                      - ./database:/docker-entrypoint-initdb.d
                
                volumes:
                  postgres_data:
                """;
        }
        
        return "";
    }
    
    private String generateRestController(Map<String, Object> context) {
        return """
            package com.example.controller;
            
            import com.example.model.Entity;
            import com.example.service.ApiService;
            import lombok.RequiredArgsConstructor;
            import org.springframework.http.HttpStatus;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;
            
            import java.util.List;
            
            @RestController
            @RequestMapping("/api/entities")
            @RequiredArgsConstructor
            @CrossOrigin(origins = "*")
            public class ApiController {
                
                private final ApiService apiService;
                
                @GetMapping
                public ResponseEntity<List<Entity>> getAllEntities() {
                    return ResponseEntity.ok(apiService.getAllEntities());
                }
                
                @GetMapping("/{id}")
                public ResponseEntity<Entity> getEntityById(@PathVariable Long id) {
                    return apiService.getEntityById(id)
                            .map(ResponseEntity::ok)
                            .orElse(ResponseEntity.notFound().build());
                }
                
                @PostMapping
                public ResponseEntity<Entity> createEntity(@RequestBody Entity entity) {
                    Entity created = apiService.createEntity(entity);
                    return ResponseEntity.status(HttpStatus.CREATED).body(created);
                }
                
                @PutMapping("/{id}")
                public ResponseEntity<Entity> updateEntity(@PathVariable Long id, @RequestBody Entity entity) {
                    return apiService.updateEntity(id, entity)
                            .map(ResponseEntity::ok)
                            .orElse(ResponseEntity.notFound().build());
                }
                
                @DeleteMapping("/{id}")
                public ResponseEntity<Void> deleteEntity(@PathVariable Long id) {
                    if (apiService.deleteEntity(id)) {
                        return ResponseEntity.noContent().build();
                    }
                    return ResponseEntity.notFound().build();
                }
            }
            """;
    }
    
    private String generateService(Map<String, Object> context) {
        return """
            package com.example.service;
            
            import com.example.model.Entity;
            import com.example.repository.EntityRepository;
            import lombok.RequiredArgsConstructor;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            
            import java.util.List;
            import java.util.Optional;
            
            @Service
            @Transactional
            @RequiredArgsConstructor
            public class ApiService {
                
                private final EntityRepository entityRepository;
                
                public List<Entity> getAllEntities() {
                    return entityRepository.findAll();
                }
                
                public Optional<Entity> getEntityById(Long id) {
                    return entityRepository.findById(id);
                }
                
                public Entity createEntity(Entity entity) {
                    return entityRepository.save(entity);
                }
                
                public Optional<Entity> updateEntity(Long id, Entity entity) {
                    return entityRepository.findById(id)
                            .map(existing -> {
                                existing.setName(entity.getName());
                                existing.setDescription(entity.getDescription());
                                return entityRepository.save(existing);
                            });
                }
                
                public boolean deleteEntity(Long id) {
                    if (entityRepository.existsById(id)) {
                        entityRepository.deleteById(id);
                        return true;
                    }
                    return false;
                }
            }
            """;
    }
    
    private String generateModel(Map<String, Object> context) {
        return """
            package com.example.model;
            
            import jakarta.persistence.*;
            import lombok.Data;
            import lombok.NoArgsConstructor;
            import lombok.AllArgsConstructor;
            
            import java.time.LocalDateTime;
            
            @Entity
            @Table(name = "entities")
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public class Entity {
                
                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                
                @Column(nullable = false)
                private String name;
                
                @Column(columnDefinition = "TEXT")
                private String description;
                
                @Column(name = "created_at", updatable = false)
                private LocalDateTime createdAt;
                
                @Column(name = "updated_at")
                private LocalDateTime updatedAt;
                
                @PrePersist
                protected void onCreate() {
                    createdAt = LocalDateTime.now();
                    updatedAt = LocalDateTime.now();
                }
                
                @PreUpdate
                protected void onUpdate() {
                    updatedAt = LocalDateTime.now();
                }
            }
            """;
    }
    
    private String generateReactComponent(String componentName, String componentType, Map<String, Object> context) {
        if ("functional".equals(componentType)) {
            return String.format("""
                import React, { useState, useEffect } from 'react';
                import './`$s.css';
                
                interface %sProps {
                  title?: string;
                }
                
                const %s: React.FC<%sProps> = ({ title = '%s' }) => {
                  const [data, setData] = useState<any[]>([]);
                  const [loading, setLoading] = useState(false);
                  
                  useEffect(() => {
                    fetchData();
                  }, []);
                  
                  const fetchData = async () => {
                    setLoading(true);
                    try {
                      const response = await fetch('/api/entities');
                      const result = await response.json();
                      setData(result);
                    } catch (error) {
                      console.error('Error fetching data:', error);
                    } finally {
                      setLoading(false);
                    }
                  };
                  
                  if (loading) {
                    return <div className="%s-loading">Loading...</div>;
                  }
                  
                  return (
                    <div className="%s">
                      <h2>{title}</h2>
                      <div className="%s-content">
                        {data.map((item, index) => (
                          <div key={index} className="%s-item">
                            {JSON.stringify(item)}
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                };
                
                export default %s;
                """, componentName, componentName, componentName, componentName, componentName,
                componentName.toLowerCase(), componentName.toLowerCase(), 
                componentName.toLowerCase(), componentName.toLowerCase(), componentName);
        }
        
        return "";
    }
    
    private String generateComponentStyles(String componentName) {
        String className = componentName.toLowerCase();
        return String.format("""
            .%s {
              padding: 20px;
              margin: 10px;
              border: 1px solid #ddd;
              border-radius: 8px;
            }
            
            .%s h2 {
              color: #333;
              margin-bottom: 15px;
            }
            
            .%s-content {
              display: flex;
              flex-direction: column;
              gap: 10px;
            }
            
            .%s-item {
              padding: 10px;
              background: #f5f5f5;
              border-radius: 4px;
            }
            
            .%s-loading {
              text-align: center;
              padding: 20px;
              color: #666;
            }
            """, className, className, className, className, className);
    }
    
    private String generateComponentTest(String componentName) {
        return String.format("""
            import React from 'react';
            import { render, screen, waitFor } from '@testing-library/react';
            import %s from './%s';
            
            describe('%s', () => {
              it('renders with default title', () => {
                render(<%s />);
                const titleElement = screen.getByText('%s');
                expect(titleElement).toBeInTheDocument();
              });
              
              it('renders with custom title', () => {
                render(<%s title="Custom Title" />);
                const titleElement = screen.getByText('Custom Title');
                expect(titleElement).toBeInTheDocument();
              });
              
              it('shows loading state initially', async () => {
                render(<%s />);
                const loadingElement = screen.getByText('Loading...');
                expect(loadingElement).toBeInTheDocument();
                
                await waitFor(() => {
                  expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
                });
              });
            });
            """, componentName, componentName, componentName, componentName, 
            componentName, componentName, componentName);
    }
    
    private String generateBackendDockerfile() {
        return """
            FROM maven:3.8-openjdk-17-slim AS build
            WORKDIR /app
            COPY pom.xml .
            COPY src ./src
            RUN mvn clean package -DskipTests
            
            FROM openjdk:17-jdk-slim
            WORKDIR /app
            COPY --from=build /app/target/*.jar app.jar
            EXPOSE 8080
            ENTRYPOINT ["java", "-jar", "app.jar"]
            """;
    }
    
    private String generateFrontendDockerfile() {
        return """
            FROM node:16-alpine AS build
            WORKDIR /app
            COPY package*.json ./
            RUN npm ci
            COPY . .
            RUN npm run build
            
            FROM nginx:alpine
            COPY --from=build /app/build /usr/share/nginx/html
            COPY nginx.conf /etc/nginx/conf.d/default.conf
            EXPOSE 80
            CMD ["nginx", "-g", "daemon off;"]
            """;
    }
    
    private String generateFullStackDockerCompose() {
        return """
            version: '3.8'
            
            services:
              postgres:
                image: postgres:15-alpine
                environment:
                  POSTGRES_DB: myapp
                  POSTGRES_USER: postgres
                  POSTGRES_PASSWORD: postgres
                ports:
                  - "5432:5432"
                volumes:
                  - postgres_data:/var/lib/postgresql/data
                  - ./database:/docker-entrypoint-initdb.d
              
              backend:
                build: ./backend
                ports:
                  - "8080:8080"
                environment:
                  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/myapp
                  SPRING_DATASOURCE_USERNAME: postgres
                  SPRING_DATASOURCE_PASSWORD: postgres
                depends_on:
                  - postgres
              
              frontend:
                build: ./frontend
                ports:
                  - "3000:80"
                depends_on:
                  - backend
            
            volumes:
              postgres_data:
            """;
    }
}