package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing project templates to streamline autonomous agent workflows.
 * This service enables the agent to use, create, and customize reusable project templates.
 */
@Slf4j
@Service
public class ProjectTemplateManager {
    
    private static final String TEMPLATES_DIR = "/tmp/ai-developer-agent/templates";
    private final Map<String, ProjectTemplate> availableTemplates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private EnhancedToolOutputWebSocketHandler webSocketHandler;
    
    public ProjectTemplateManager() {
        // Ensure templates directory exists
        try {
            Files.createDirectories(Paths.get(TEMPLATES_DIR));
            log.info("ProjectTemplateManager initialized with templates directory: {}", TEMPLATES_DIR);
            
            // Load available templates
            loadAvailableTemplates();
        } catch (IOException e) {
            log.error("Failed to create templates directory", e);
        }
    }
    
    /**
     * Load available templates from disk
     */
    private void loadAvailableTemplates() {
        File templatesDir = new File(TEMPLATES_DIR);
        if (templatesDir.exists() && templatesDir.isDirectory()) {
            File[] templateDirs = templatesDir.listFiles(File::isDirectory);
            if (templateDirs != null) {
                for (File dir : templateDirs) {
                    File metadataFile = new File(dir, "template.json");
                    if (metadataFile.exists()) {
                        try {
                            ProjectTemplate template = objectMapper.readValue(metadataFile, ProjectTemplate.class);
                            availableTemplates.put(template.getId(), template);
                            log.info("Loaded template: {}", template.getName());
                        } catch (IOException e) {
                            log.error("Failed to load template metadata: {}", metadataFile, e);
                        }
                    }
                }
            }
        }
        
        // If no templates found, initialize with built-in templates
        if (availableTemplates.isEmpty()) {
            initializeBuiltInTemplates();
        }
    }
    
    /**
     * Initialize built-in templates
     */
    private void initializeBuiltInTemplates() {
        // Spring Boot REST API template
        createBuiltInTemplate(
                "spring-boot-rest-api",
                "Spring Boot REST API",
                "Basic Spring Boot REST API with JPA and H2 database",
                "java",
                Arrays.asList("spring-boot", "rest-api", "jpa", "h2"),
                createSpringBootTemplate()
        );
        
        // React SPA template
        createBuiltInTemplate(
                "react-spa",
                "React Single Page Application",
                "Basic React SPA with React Router and Material UI",
                "javascript",
                Arrays.asList("react", "spa", "material-ui", "react-router"),
                createReactTemplate()
        );
        
        // Node.js Express API template
        createBuiltInTemplate(
                "nodejs-express-api",
                "Node.js Express API",
                "Basic Node.js API with Express and MongoDB",
                "javascript",
                Arrays.asList("nodejs", "express", "mongodb", "rest-api"),
                createNodejsTemplate()
        );
        
        log.info("Initialized built-in templates: {}", availableTemplates.keySet());
    }
    
    /**
     * Create a built-in template
     */
    private void createBuiltInTemplate(String id, String name, String description, String language, 
                                      List<String> tags, Map<String, String> files) {
        ProjectTemplate template = ProjectTemplate.builder()
                .id(id)
                .name(name)
                .description(description)
                .language(language)
                .tags(tags)
                .files(files)
                .isBuiltIn(true)
                .createdAt(Instant.now())
                .build();
        
        availableTemplates.put(id, template);
        
        // Save template to disk
        try {
            File templateDir = new File(TEMPLATES_DIR, id);
            Files.createDirectories(templateDir.toPath());
            
            File metadataFile = new File(templateDir, "template.json");
            objectMapper.writeValue(metadataFile, template);
            
            // Save template files
            for (Map.Entry<String, String> entry : files.entrySet()) {
                File file = new File(templateDir, entry.getKey());
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), entry.getValue());
            }
            
            log.info("Created built-in template: {}", name);
        } catch (IOException e) {
            log.error("Failed to save built-in template: {}", name, e);
        }
    }
    
    /**
     * Get all available templates
     */
    public List<ProjectTemplate> getAllTemplates() {
        return new ArrayList<>(availableTemplates.values());
    }
    
    /**
     * Get template by ID
     */
    public Optional<ProjectTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(availableTemplates.get(templateId));
    }
    
    /**
     * Search templates by criteria
     */
    public List<ProjectTemplate> searchTemplates(TemplateSearchCriteria criteria) {
        return availableTemplates.values().stream()
                .filter(template -> {
                    if (criteria.getLanguage() != null && !criteria.getLanguage().equals(template.getLanguage())) {
                        return false;
                    }
                    
                    if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
                        return template.getTags().stream().anyMatch(criteria.getTags()::contains);
                    }
                    
                    if (criteria.getQuery() != null && !criteria.getQuery().isEmpty()) {
                        String query = criteria.getQuery().toLowerCase();
                        return template.getName().toLowerCase().contains(query) ||
                               template.getDescription().toLowerCase().contains(query);
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Create a new template from existing project
     */
    public ProjectTemplate createTemplate(String sessionId, String projectPath, TemplateCreationRequest request) {
        // Validate project path
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid project path: " + projectPath);
        }
        
        // Generate template ID
        String templateId = request.getId() != null ? request.getId() : UUID.randomUUID().toString();
        
        // Create template directory
        File templateDir = new File(TEMPLATES_DIR, templateId);
        try {
            Files.createDirectories(templateDir.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create template directory", e);
        }
        
        // Collect template files
        Map<String, String> templateFiles = new HashMap<>();
        try {
            Files.walk(projectDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> !isExcludedFile(path, request.getExcludePatterns()))
                    .forEach(path -> {
                        try {
                            String relativePath = projectDir.toPath().relativize(path).toString();
                            String content = Files.readString(path);
                            templateFiles.put(relativePath, content);
                        } catch (IOException e) {
                            log.error("Failed to read file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to collect template files", e);
        }
        
        // Create template
        ProjectTemplate template = ProjectTemplate.builder()
                .id(templateId)
                .name(request.getName())
                .description(request.getDescription())
                .language(request.getLanguage())
                .tags(request.getTags())
                .files(templateFiles)
                .isBuiltIn(false)
                .createdAt(Instant.now())
                .createdBy(sessionId)
                .build();
        
        // Save template metadata
        try {
            File metadataFile = new File(templateDir, "template.json");
            objectMapper.writeValue(metadataFile, template);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save template metadata", e);
        }
        
        // Save template files
        for (Map.Entry<String, String> entry : templateFiles.entrySet()) {
            try {
                File file = new File(templateDir, entry.getKey());
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), entry.getValue());
            } catch (IOException e) {
                log.error("Failed to save template file: {}", entry.getKey(), e);
            }
        }
        
        // Add to available templates
        availableTemplates.put(templateId, template);
        
        // Broadcast template creation via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("sessionId", sessionId);
            templateData.put("id", template.getId());
            templateData.put("name", template.getName());
            templateData.put("fileCount", template.getFiles().size());
            templateData.put("timestamp", Instant.now().toString());
            templateData.put("type", "template_created");
            webSocketHandler.broadcastToolOutput(templateData);
        }
        
        log.info("Created template {} with {} files", template.getName(), templateFiles.size());
        
        return template;
    }
    
    /**
     * Apply template to create a new project
     */
    public ProjectCreationResult applyTemplate(String sessionId, String templateId, String targetDir, Map<String, String> variables) {
        // Get template
        ProjectTemplate template = availableTemplates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        
        // Create target directory
        File targetDirectory = new File(targetDir);
        try {
            Files.createDirectories(targetDirectory.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create target directory", e);
        }
        
        // Apply template files
        List<String> createdFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : template.getFiles().entrySet()) {
            try {
                String filePath = entry.getKey();
                String content = entry.getValue();
                
                // Apply variables
                if (variables != null) {
                    for (Map.Entry<String, String> var : variables.entrySet()) {
                        content = content.replace("${" + var.getKey() + "}", var.getValue());
                    }
                }
                
                // Create file
                File file = new File(targetDirectory, filePath);
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), content);
                
                createdFiles.add(filePath);
            } catch (IOException e) {
                log.error("Failed to create file: {}", entry.getKey(), e);
            }
        }
        
        // Broadcast template application via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> applicationData = new HashMap<>();
            applicationData.put("sessionId", sessionId);
            applicationData.put("templateId", templateId);
            applicationData.put("templateName", template.getName());
            applicationData.put("targetDir", targetDir);
            applicationData.put("fileCount", createdFiles.size());
            applicationData.put("timestamp", Instant.now().toString());
            applicationData.put("type", "template_applied");
            webSocketHandler.broadcastToolOutput(applicationData);
        }
        
        log.info("Applied template {} to {} with {} files", template.getName(), targetDir, createdFiles.size());
        
        return ProjectCreationResult.builder()
                .templateId(templateId)
                .templateName(template.getName())
                .targetDirectory(targetDir)
                .createdFiles(createdFiles)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Delete a template
     */
    public boolean deleteTemplate(String templateId) {
        ProjectTemplate template = availableTemplates.get(templateId);
        if (template == null) {
            return false;
        }
        
        // Cannot delete built-in templates
        if (template.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot delete built-in template: " + templateId);
        }
        
        // Remove from available templates
        availableTemplates.remove(templateId);
        
        // Delete template directory
        try {
            File templateDir = new File(TEMPLATES_DIR, templateId);
            if (templateDir.exists()) {
                Files.walk(templateDir.toPath())
                        .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Failed to delete: {}", path, e);
                            }
                        });
            }
            
            log.info("Deleted template: {}", template.getName());
            return true;
        } catch (IOException e) {
            log.error("Failed to delete template directory: {}", templateId, e);
            return false;
        }
    }
    
    /**
     * Check if a file should be excluded from template
     */
    private boolean isExcludedFile(Path path, List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            // Default exclusions
            String fileName = path.getFileName().toString();
            return fileName.startsWith(".") || 
                   fileName.equals("node_modules") || 
                   fileName.equals("target") || 
                   fileName.equals("build");
        }
        
        String pathStr = path.toString();
        for (String pattern : excludePatterns) {
            if (pathStr.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create Spring Boot template files
     */
    private Map<String, String> createSpringBootTemplate() {
        Map<String, String> files = new HashMap<>();
        
        // pom.xml
        files.put("pom.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.1.0</version>
                        <relativePath/>
                    </parent>
                    <groupId>${groupId}</groupId>
                    <artifactId>${artifactId}</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>${name}</name>
                    <description>${description}</description>
                    
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>com.h2database</groupId>
                            <artifactId>h2</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <configuration>
                                    <excludes>
                                        <exclude>
                                            <groupId>org.projectlombok</groupId>
                                            <artifactId>lombok</artifactId>
                                        </exclude>
                                    </excludes>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        
        // Application class
        files.put("src/main/java/${packagePath}/Application.java", """
                package ${packageName};
                
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """);
        
        // Entity
        files.put("src/main/java/${packagePath}/model/User.java", """
                package ${packageName}.model;
                
                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                
                @Entity
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class User {
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    
                    private String name;
                    private String email;
                }
                """);
        
        // Repository
        files.put("src/main/java/${packagePath}/repository/UserRepository.java", """
                package ${packageName}.repository;
                
                import ${packageName}.model.User;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;
                
                @Repository
                public interface UserRepository extends JpaRepository<User, Long> {
                }
                """);
        
        // Controller
        files.put("src/main/java/${packagePath}/controller/UserController.java", """
                package ${packageName}.controller;
                
                import ${packageName}.model.User;
                import ${packageName}.repository.UserRepository;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                import java.util.List;
                
                @RestController
                @RequestMapping("/api/users")
                public class UserController {
                    
                    @Autowired
                    private UserRepository userRepository;
                    
                    @GetMapping
                    public List<User> getAllUsers() {
                        return userRepository.findAll();
                    }
                    
                    @GetMapping("/{id}")
                    public ResponseEntity<User> getUserById(@PathVariable Long id) {
                        return userRepository.findById(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
                    }
                    
                    @PostMapping
                    public User createUser(@RequestBody User user) {
                        return userRepository.save(user);
                    }
                    
                    @PutMapping("/{id}")
                    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
                        return userRepository.findById(id)
                                .map(existingUser -> {
                                    existingUser.setName(user.getName());
                                    existingUser.setEmail(user.getEmail());
                                    return ResponseEntity.ok(userRepository.save(existingUser));
                                })
                                .orElse(ResponseEntity.notFound().build());
                    }
                    
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
                        return userRepository.findById(id)
                                .map(user -> {
                                    userRepository.delete(user);
                                    return ResponseEntity.ok().<Void>build();
                                })
                                .orElse(ResponseEntity.notFound().build());
                    }
                }
                """);
        
        // Application properties
        files.put("src/main/resources/application.properties", """
                # H2 Database Configuration
                spring.datasource.url=jdbc:h2:mem:testdb
                spring.datasource.driverClassName=org.h2.Driver
                spring.datasource.username=sa
                spring.datasource.password=
                spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
                
                # H2 Console
                spring.h2.console.enabled=true
                spring.h2.console.path=/h2-console
                
                # JPA Configuration
                spring.jpa.hibernate.ddl-auto=update
                spring.jpa.show-sql=true
                
                # Server Configuration
                server.port=8080
                """);
        
        return files;
    }
    
    /**
     * Create React template files
     */
    private Map<String, String> createReactTemplate() {
        Map<String, String> files = new HashMap<>();
        
        // package.json
        files.put("package.json", """
                {
                  "name": "${name}",
                  "version": "0.1.0",
                  "private": true,
                  "dependencies": {
                    "@emotion/react": "^11.11.0",
                    "@emotion/styled": "^11.11.0",
                    "@mui/icons-material": "^5.11.16",
                    "@mui/material": "^5.13.0",
                    "@testing-library/jest-dom": "^5.16.5",
                    "@testing-library/react": "^13.4.0",
                    "@testing-library/user-event": "^13.5.0",
                    "axios": "^1.4.0",
                    "react": "^18.2.0",
                    "react-dom": "^18.2.0",
                    "react-router-dom": "^6.11.1",
                    "react-scripts": "5.0.1",
                    "web-vitals": "^2.1.4"
                  },
                  "scripts": {
                    "start": "react-scripts start",
                    "build": "react-scripts build",
                    "test": "react-scripts test",
                    "eject": "react-scripts eject"
                  },
                  "eslintConfig": {
                    "extends": [
                      "react-app",
                      "react-app/jest"
                    ]
                  },
                  "browserslist": {
                    "production": [
                      ">0.2%",
                      "not dead",
                      "not op_mini all"
                    ],
                    "development": [
                      "last 1 chrome version",
                      "last 1 firefox version",
                      "last 1 safari version"
                    ]
                  }
                }
                """);
        
        // index.html
        files.put("public/index.html", """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <link rel="icon" href="%PUBLIC_URL%/favicon.ico" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <meta name="theme-color" content="#000000" />
                    <meta name="description" content="${description}" />
                    <link rel="apple-touch-icon" href="%PUBLIC_URL%/logo192.png" />
                    <link rel="manifest" href="%PUBLIC_URL%/manifest.json" />
                    <title>${name}</title>
                  </head>
                  <body>
                    <noscript>You need to enable JavaScript to run this app.</noscript>
                    <div id="root"></div>
                  </body>
                </html>
                """);
        
        // App.js
        files.put("src/App.js", """
                import React from 'react';
                import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
                import { ThemeProvider, createTheme } from '@mui/material/styles';
                import CssBaseline from '@mui/material/CssBaseline';
                
                import Header from './components/Header';
                import Home from './pages/Home';
                import About from './pages/About';
                import NotFound from './pages/NotFound';
                
                const theme = createTheme({
                  palette: {
                    mode: 'light',
                    primary: {
                      main: '#1976d2',
                    },
                    secondary: {
                      main: '#dc004e',
                    },
                  },
                });
                
                function App() {
                  return (
                    <ThemeProvider theme={theme}>
                      <CssBaseline />
                      <Router>
                        <Header />
                        <Routes>
                          <Route path="/" element={<Home />} />
                          <Route path="/about" element={<About />} />
                          <Route path="*" element={<NotFound />} />
                        </Routes>
                      </Router>
                    </ThemeProvider>
                  );
                }
                
                export default App;
                """);
        
        // Header component
        files.put("src/components/Header.js", """
                import React from 'react';
                import { Link as RouterLink } from 'react-router-dom';
                import AppBar from '@mui/material/AppBar';
                import Toolbar from '@mui/material/Toolbar';
                import Typography from '@mui/material/Typography';
                import Button from '@mui/material/Button';
                import Container from '@mui/material/Container';
                
                function Header() {
                  return (
                    <AppBar position="static">
                      <Container maxWidth="lg">
                        <Toolbar>
                          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                            ${name}
                          </Typography>
                          <Button color="inherit" component={RouterLink} to="/">Home</Button>
                          <Button color="inherit" component={RouterLink} to="/about">About</Button>
                        </Toolbar>
                      </Container>
                    </AppBar>
                  );
                }
                
                export default Header;
                """);
        
        // Home page
        files.put("src/pages/Home.js", """
                import React from 'react';
                import Container from '@mui/material/Container';
                import Typography from '@mui/material/Typography';
                import Box from '@mui/material/Box';
                import Paper from '@mui/material/Paper';
                
                function Home() {
                  return (
                    <Container maxWidth="lg">
                      <Box sx={{ my: 4 }}>
                        <Typography variant="h4" component="h1" gutterBottom>
                          Welcome to ${name}
                        </Typography>
                        <Paper elevation={3} sx={{ p: 3, mt: 3 }}>
                          <Typography variant="body1">
                            This is a starter template for your React application.
                          </Typography>
                        </Paper>
                      </Box>
                    </Container>
                  );
                }
                
                export default Home;
                """);
        
        // About page
        files.put("src/pages/About.js", """
                import React from 'react';
                import Container from '@mui/material/Container';
                import Typography from '@mui/material/Typography';
                import Box from '@mui/material/Box';
                import Paper from '@mui/material/Paper';
                
                function About() {
                  return (
                    <Container maxWidth="lg">
                      <Box sx={{ my: 4 }}>
                        <Typography variant="h4" component="h1" gutterBottom>
                          About
                        </Typography>
                        <Paper elevation={3} sx={{ p: 3, mt: 3 }}>
                          <Typography variant="body1">
                            This is the about page for ${name}.
                          </Typography>
                          <Typography variant="body1" sx={{ mt: 2 }}>
                            ${description}
                          </Typography>
                        </Paper>
                      </Box>
                    </Container>
                  );
                }
                
                export default About;
                """);
        
        // NotFound page
        files.put("src/pages/NotFound.js", """
                import React from 'react';
                import { Link as RouterLink } from 'react-router-dom';
                import Container from '@mui/material/Container';
                import Typography from '@mui/material/Typography';
                import Box from '@mui/material/Box';
                import Button from '@mui/material/Button';
                
                function NotFound() {
                  return (
                    <Container maxWidth="lg">
                      <Box sx={{ my: 4, textAlign: 'center' }}>
                        <Typography variant="h1" component="h1" gutterBottom>
                          404
                        </Typography>
                        <Typography variant="h4" component="h2" gutterBottom>
                          Page Not Found
                        </Typography>
                        <Typography variant="body1" sx={{ mb: 4 }}>
                          The page you are looking for does not exist.
                        </Typography>
                        <Button variant="contained" component={RouterLink} to="/">
                          Go Home
                        </Button>
                      </Box>
                    </Container>
                  );
                }
                
                export default NotFound;
                """);
        
        // index.js
        files.put("src/index.js", """
                import React from 'react';
                import ReactDOM from 'react-dom/client';
                import App from './App';
                import reportWebVitals from './reportWebVitals';
                
                const root = ReactDOM.createRoot(document.getElementById('root'));
                root.render(
                  <React.StrictMode>
                    <App />
                  </React.StrictMode>
                );
                
                reportWebVitals();
                """);
        
        return files;
    }
    
    /**
     * Create Node.js template files
     */
    private Map<String, String> createNodejsTemplate() {
        Map<String, String> files = new HashMap<>();
        
        // package.json
        files.put("package.json", """
                {
                  "name": "${name}",
                  "version": "1.0.0",
                  "description": "${description}",
                  "main": "src/index.js",
                  "scripts": {
                    "start": "node src/index.js",
                    "dev": "nodemon src/index.js",
                    "test": "jest"
                  },
                  "dependencies": {
                    "express": "^4.18.2",
                    "mongoose": "^7.1.1",
                    "cors": "^2.8.5",
                    "dotenv": "^16.0.3",
                    "helmet": "^7.0.0",
                    "morgan": "^1.10.0"
                  },
                  "devDependencies": {
                    "nodemon": "^2.0.22",
                    "jest": "^29.5.0",
                    "supertest": "^6.3.3"
                  }
                }
                """);
        
        // index.js
        files.put("src/index.js", """
                const express = require('express');
                const cors = require('cors');
                const helmet = require('helmet');
                const morgan = require('morgan');
                const mongoose = require('mongoose');
                require('dotenv').config();
                
                const userRoutes = require('./routes/userRoutes');
                
                const app = express();
                const PORT = process.env.PORT || 3000;
                
                // Middleware
                app.use(cors());
                app.use(helmet());
                app.use(morgan('dev'));
                app.use(express.json());
                
                // Routes
                app.use('/api/users', userRoutes);
                
                // Root route
                app.get('/', (req, res) => {
                  res.json({ message: 'Welcome to ${name} API' });
                });
                
                // Connect to MongoDB
                mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/${name}')
                  .then(() => {
                    console.log('Connected to MongoDB');
                    app.listen(PORT, () => {
                      console.log(`Server running on port ${PORT}`);
                    });
                  })
                  .catch(err => {
                    console.error('Failed to connect to MongoDB', err);
                  });
                
                module.exports = app; // For testing
                """);
        
        // User model
        files.put("src/models/User.js", """
                const mongoose = require('mongoose');
                
                const userSchema = new mongoose.Schema({
                  name: {
                    type: String,
                    required: true,
                    trim: true
                  },
                  email: {
                    type: String,
                    required: true,
                    unique: true,
                    trim: true,
                    lowercase: true
                  },
                  createdAt: {
                    type: Date,
                    default: Date.now
                  }
                });
                
                module.exports = mongoose.model('User', userSchema);
                """);
        
        // User routes
        files.put("src/routes/userRoutes.js", """
                const express = require('express');
                const router = express.Router();
                const User = require('../models/User');
                
                // Get all users
                router.get('/', async (req, res) => {
                  try {
                    const users = await User.find();
                    res.json(users);
                  } catch (err) {
                    res.status(500).json({ message: err.message });
                  }
                });
                
                // Get one user
                router.get('/:id', getUser, (req, res) => {
                  res.json(res.user);
                });
                
                // Create user
                router.post('/', async (req, res) => {
                  const user = new User({
                    name: req.body.name,
                    email: req.body.email
                  });
                
                  try {
                    const newUser = await user.save();
                    res.status(201).json(newUser);
                  } catch (err) {
                    res.status(400).json({ message: err.message });
                  }
                });
                
                // Update user
                router.patch('/:id', getUser, async (req, res) => {
                  if (req.body.name != null) {
                    res.user.name = req.body.name;
                  }
                  if (req.body.email != null) {
                    res.user.email = req.body.email;
                  }
                
                  try {
                    const updatedUser = await res.user.save();
                    res.json(updatedUser);
                  } catch (err) {
                    res.status(400).json({ message: err.message });
                  }
                });
                
                // Delete user
                router.delete('/:id', getUser, async (req, res) => {
                  try {
                    await res.user.deleteOne();
                    res.json({ message: 'User deleted' });
                  } catch (err) {
                    res.status(500).json({ message: err.message });
                  }
                });
                
                // Middleware to get user by ID
                async function getUser(req, res, next) {
                  let user;
                  try {
                    user = await User.findById(req.params.id);
                    if (user == null) {
                      return res.status(404).json({ message: 'User not found' });
                    }
                  } catch (err) {
                    return res.status(500).json({ message: err.message });
                  }
                
                  res.user = user;
                  next();
                }
                
                module.exports = router;
                """);
        
        // .env file
        files.put(".env", """
                PORT=3000
                MONGODB_URI=mongodb://localhost:27017/${name}
                NODE_ENV=development
                """);
        
        // .gitignore
        files.put(".gitignore", """
                node_modules/
                .env
                coverage/
                .DS_Store
                npm-debug.log
                """);
        
        return files;
    }
    
    /**
     * Template search criteria
     */
    @Data
    @Builder
    public static class TemplateSearchCriteria {
        private String query;
        private String language;
        private List<String> tags;
    }
    
    /**
     * Template creation request
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateCreationRequest {
        private String id;
        private String name;
        private String description;
        private String language;
        private List<String> tags;
        private List<String> excludePatterns;
    }
    
    /**
     * Project template
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectTemplate {
        private String id;
        private String name;
        private String description;
        private String language;
        private List<String> tags;
        private Map<String, String> files;
        private boolean isBuiltIn;
        private Instant createdAt;
        private String createdBy;
    }
    
    /**
     * Project creation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectCreationResult {
        private String templateId;
        private String templateName;
        private String targetDirectory;
        private List<String> createdFiles;
        private Instant timestamp;
    }
}
