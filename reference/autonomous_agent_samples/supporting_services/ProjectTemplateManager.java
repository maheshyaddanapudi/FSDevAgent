package com.ai.developer.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing project templates and patterns
 */
@Slf4j
@Service
public class ProjectTemplateManager {
    
    private static final String TEMPLATES_DIR = "/tmp/ai-developer-agent/templates";
    private final Map<String, ProjectTemplate> templates = new ConcurrentHashMap<>();
    
    public ProjectTemplateManager() {
        // Ensure templates directory exists
        try {
            Files.createDirectories(Paths.get(TEMPLATES_DIR));
            loadBuiltInTemplates();
        } catch (IOException e) {
            log.error("Failed to create templates directory", e);
        }
    }
    
    /**
     * Get all available templates
     */
    public List<ProjectTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }
    
    /**
     * Get template by ID
     */
    public Optional<ProjectTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }
    
    /**
     * Find templates matching criteria
     */
    public List<TemplateMatch> findTemplates(TemplateCriteria criteria) {
        return templates.values().stream()
                .map(template -> {
                    double score = calculateMatchScore(template, criteria);
                    return TemplateMatch.builder()
                            .template(template)
                            .score(score)
                            .build();
                })
                .filter(match -> match.getScore() > 0.5) // 50% match threshold
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * Create a new template
     */
    public ProjectTemplate createTemplate(ProjectTemplate template) {
        if (template.getId() == null) {
            template.setId(UUID.randomUUID().toString());
        }
        
        template.setCreatedAt(Instant.now());
        template.setLastUpdated(Instant.now());
        
        templates.put(template.getId(), template);
        persistTemplate(template);
        
        log.info("Created new template: {}", template.getId());
        return template;
    }
    
    /**
     * Update an existing template
     */
    public ProjectTemplate updateTemplate(String templateId, ProjectTemplate updatedTemplate) {
        ProjectTemplate existing = templates.get(templateId);
        if (existing == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        
        // Update fields
        existing.setName(updatedTemplate.getName());
        existing.setDescription(updatedTemplate.getDescription());
        existing.setType(updatedTemplate.getType());
        existing.setTags(updatedTemplate.getTags());
        existing.setFrameworks(updatedTemplate.getFrameworks());
        existing.setLanguages(updatedTemplate.getLanguages());
        existing.setStructure(updatedTemplate.getStructure());
        existing.setFiles(updatedTemplate.getFiles());
        existing.setDependencies(updatedTemplate.getDependencies());
        existing.setConfigOptions(updatedTemplate.getConfigOptions());
        existing.setLastUpdated(Instant.now());
        
        templates.put(templateId, existing);
        persistTemplate(existing);
        
        log.info("Updated template: {}", templateId);
        return existing;
    }
    
    /**
     * Delete a template
     */
    public boolean deleteTemplate(String templateId) {
        ProjectTemplate template = templates.remove(templateId);
        if (template != null) {
            File templateFile = new File(TEMPLATES_DIR, templateId + ".json");
            boolean deleted = templateFile.delete();
            log.info("Deleted template: {} (file deleted: {})", templateId, deleted);
            return true;
        }
        return false;
    }
    
    /**
     * Generate project structure from template
     */
    public ProjectStructure generateProjectStructure(String templateId, Map<String, Object> config) {
        ProjectTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        
        // Validate required config options
        for (ConfigOption option : template.getConfigOptions()) {
            if (option.isRequired() && !config.containsKey(option.getName())) {
                throw new IllegalArgumentException("Missing required config option: " + option.getName());
            }
        }
        
        // Apply default values for missing options
        for (ConfigOption option : template.getConfigOptions()) {
            if (!config.containsKey(option.getName()) && option.getDefaultValue() != null) {
                config.put(option.getName(), option.getDefaultValue());
            }
        }
        
        // Generate project name if not provided
        String projectName = (String) config.getOrDefault("projectName", 
                template.getName().toLowerCase().replaceAll("\\s+", "-") + "-" + UUID.randomUUID().toString().substring(0, 8));
        
        // Generate directories
        List<String> directories = generateDirectories(template, config);
        
        // Generate files
        List<GeneratedFile> files = generateFiles(template, config);
        
        // Collect dependencies
        Map<String, List<Dependency>> dependencies = new HashMap<>(template.getDependencies());
        
        return ProjectStructure.builder()
                .templateId(templateId)
                .name(projectName)
                .directories(directories)
                .files(files)
                .dependencies(dependencies)
                .configuration(config)
                .build();
    }
    
    /**
     * Load built-in templates
     */
    private void loadBuiltInTemplates() {
        // React frontend template
        ProjectTemplate reactTemplate = ProjectTemplate.builder()
                .id("react-frontend")
                .name("React Frontend")
                .description("Modern React frontend application with TypeScript")
                .type(ProjectType.FRONTEND)
                .tags(List.of("frontend", "react", "typescript"))
                .frameworks(List.of("react", "react-router", "redux"))
                .languages(List.of("typescript", "javascript", "css"))
                .structure(Map.of(
                        "src", List.of("components", "pages", "services", "utils", "hooks", "store"),
                        "public", List.of("assets", "images")
                ))
                .files(Map.of(
                        "package.json", "{\n  \"name\": \"${projectName}\",\n  \"version\": \"0.1.0\",\n  \"private\": true,\n  \"dependencies\": {\n    \"react\": \"^18.2.0\",\n    \"react-dom\": \"^18.2.0\",\n    \"react-router-dom\": \"^6.4.0\",\n    \"@reduxjs/toolkit\": \"^1.8.5\",\n    \"react-redux\": \"^8.0.4\"\n  },\n  \"scripts\": {\n    \"start\": \"react-scripts start\",\n    \"build\": \"react-scripts build\",\n    \"test\": \"react-scripts test\",\n    \"eject\": \"react-scripts eject\"\n  }\n}",
                        "tsconfig.json", "{\n  \"compilerOptions\": {\n    \"target\": \"es5\",\n    \"lib\": [\"dom\", \"dom.iterable\", \"esnext\"],\n    \"allowJs\": true,\n    \"skipLibCheck\": true,\n    \"esModuleInterop\": true,\n    \"allowSyntheticDefaultImports\": true,\n    \"strict\": true,\n    \"forceConsistentCasingInFileNames\": true,\n    \"noFallthroughCasesInSwitch\": true,\n    \"module\": \"esnext\",\n    \"moduleResolution\": \"node\",\n    \"resolveJsonModule\": true,\n    \"isolatedModules\": true,\n    \"noEmit\": true,\n    \"jsx\": \"react-jsx\"\n  },\n  \"include\": [\"src\"]\n}",
                        "src/index.tsx", "import React from 'react';\nimport ReactDOM from 'react-dom/client';\nimport './index.css';\nimport App from './App';\n\nconst root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);\nroot.render(\n  <React.StrictMode>\n    <App />\n  </React.StrictMode>\n);\n",
                        "src/App.tsx", "import React from 'react';\nimport './App.css';\n\nfunction App() {\n  return (\n    <div className=\"App\">\n      <header className=\"App-header\">\n        <h1>${projectName}</h1>\n        <p>Welcome to your new React application!</p>\n      </header>\n    </div>\n  );\n}\n\nexport default App;\n"
                ))
                .dependencies(Map.of(
                        "npm", List.of(
                                Dependency.builder().name("react").version("^18.2.0").build(),
                                Dependency.builder().name("react-dom").version("^18.2.0").build(),
                                Dependency.builder().name("react-router-dom").version("^6.4.0").build(),
                                Dependency.builder().name("@reduxjs/toolkit").version("^1.8.5").build(),
                                Dependency.builder().name("react-redux").version("^8.0.4").build()
                        )
                ))
                .configOptions(List.of(
                        ConfigOption.builder()
                                .name("projectName")
                                .type("string")
                                .required(true)
                                .description("Name of the project")
                                .build(),
                        ConfigOption.builder()
                                .name("useTypescript")
                                .type("boolean")
                                .required(false)
                                .defaultValue(true)
                                .description("Use TypeScript")
                                .build(),
                        ConfigOption.builder()
                                .name("cssFramework")
                                .type("string")
                                .required(false)
                                .defaultValue("none")
                                .enumValues(List.of("none", "bootstrap", "tailwind", "material-ui"))
                                .description("CSS framework to use")
                                .build()
                ))
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
        
        // Spring Boot backend template
        ProjectTemplate springBootTemplate = ProjectTemplate.builder()
                .id("spring-boot-backend")
                .name("Spring Boot Backend")
                .description("Spring Boot REST API with JPA and PostgreSQL")
                .type(ProjectType.BACKEND)
                .tags(List.of("backend", "spring-boot", "java", "rest-api"))
                .frameworks(List.of("spring-boot", "spring-data-jpa", "spring-web"))
                .languages(List.of("java"))
                .structure(Map.of(
                        "src/main/java/${packagePath}", List.of("controller", "service", "repository", "model", "config", "exception"),
                        "src/main/resources", List.of("static", "templates"),
                        "src/test/java/${packagePath}", List.of("controller", "service", "repository")
                ))
                .files(Map.of(
                        "pom.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n\txsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n\t<modelVersion>4.0.0</modelVersion>\n\t<parent>\n\t\t<groupId>org.springframework.boot</groupId>\n\t\t<artifactId>spring-boot-starter-parent</artifactId>\n\t\t<version>2.7.3</version>\n\t\t<relativePath/>\n\t</parent>\n\t<groupId>${groupId}</groupId>\n\t<artifactId>${artifactId}</artifactId>\n\t<version>0.0.1-SNAPSHOT</version>\n\t<name>${projectName}</name>\n\t<description>${projectDescription}</description>\n\t<properties>\n\t\t<java.version>17</java.version>\n\t</properties>\n\t<dependencies>\n\t\t<dependency>\n\t\t\t<groupId>org.springframework.boot</groupId>\n\t\t\t<artifactId>spring-boot-starter-data-jpa</artifactId>\n\t\t</dependency>\n\t\t<dependency>\n\t\t\t<groupId>org.springframework.boot</groupId>\n\t\t\t<artifactId>spring-boot-starter-web</artifactId>\n\t\t</dependency>\n\t\t<dependency>\n\t\t\t<groupId>org.postgresql</groupId>\n\t\t\t<artifactId>postgresql</artifactId>\n\t\t\t<scope>runtime</scope>\n\t\t</dependency>\n\t\t<dependency>\n\t\t\t<groupId>org.projectlombok</groupId>\n\t\t\t<artifactId>lombok</artifactId>\n\t\t\t<optional>true</optional>\n\t\t</dependency>\n\t\t<dependency>\n\t\t\t<groupId>org.springframework.boot</groupId>\n\t\t\t<artifactId>spring-boot-starter-test</artifactId>\n\t\t\t<scope>test</scope>\n\t\t</dependency>\n\t</dependencies>\n\t<build>\n\t\t<plugins>\n\t\t\t<plugin>\n\t\t\t\t<groupId>org.springframework.boot</groupId>\n\t\t\t\t<artifactId>spring-boot-maven-plugin</artifactId>\n\t\t\t\t<configuration>\n\t\t\t\t\t<excludes>\n\t\t\t\t\t\t<exclude>\n\t\t\t\t\t\t\t<groupId>org.projectlombok</groupId>\n\t\t\t\t\t\t\t<artifactId>lombok</artifactId>\n\t\t\t\t\t\t</exclude>\n\t\t\t\t\t</excludes>\n\t\t\t\t</configuration>\n\t\t\t</plugin>\n\t\t</plugins>\n\t</build>\n</project>\n",
                        "src/main/resources/application.properties", "spring.datasource.url=jdbc:postgresql://localhost:5432/${dbName}\nspring.datasource.username=${dbUsername}\nspring.datasource.password=${dbPassword}\nspring.jpa.hibernate.ddl-auto=update\nspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect\nserver.port=${serverPort}\n",
                        "src/main/java/${packagePath}/Application.java", "package ${packageName};\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class Application {\n\n\tpublic static void main(String[] args) {\n\t\tSpringApplication.run(Application.class, args);\n\t}\n\n}\n"
                ))
                .dependencies(Map.of(
                        "maven", List.of(
                                Dependency.builder().groupId("org.springframework.boot").artifactId("spring-boot-starter-data-jpa").build(),
                                Dependency.builder().groupId("org.springframework.boot").artifactId("spring-boot-starter-web").build(),
                                Dependency.builder().groupId("org.postgresql").artifactId("postgresql").scope("runtime").build(),
                                Dependency.builder().groupId("org.projectlombok").artifactId("lombok").build(),
                                Dependency.builder().groupId("org.springframework.boot").artifactId("spring-boot-starter-test").scope("test").build()
                        )
                ))
                .configOptions(List.of(
                        ConfigOption.builder()
                                .name("projectName")
                                .type("string")
                                .required(true)
                                .description("Name of the project")
                                .build(),
                        ConfigOption.builder()
                                .name("packageName")
                                .type("string")
                                .required(true)
                                .description("Base package name (e.g., com.example.project)")
                                .build(),
                        ConfigOption.builder()
                                .name("packagePath")
                                .type("string")
                                .required(false)
                                .description("Package path with slashes instead of dots (derived from packageName)")
                                .build(),
                        ConfigOption.builder()
                                .name("groupId")
                                .type("string")
                                .required(false)
                                .description("Maven group ID")
                                .build(),
                        ConfigOption.builder()
                                .name("artifactId")
                                .type("string")
                                .required(false)
                                .description("Maven artifact ID")
                                .build(),
                        ConfigOption.builder()
                                .name("projectDescription")
                                .type("string")
                                .required(false)
                                .defaultValue("Spring Boot REST API")
                                .description("Project description")
                                .build(),
                        ConfigOption.builder()
                                .name("dbName")
                                .type("string")
                                .required(false)
                                .defaultValue("postgres")
                                .description("Database name")
                                .build(),
                        ConfigOption.builder()
                                .name("dbUsername")
                                .type("string")
                                .required(false)
                                .defaultValue("postgres")
                                .description("Database username")
                                .build(),
                        ConfigOption.builder()
                                .name("dbPassword")
                                .type("string")
                                .required(false)
                                .defaultValue("postgres")
                                .description("Database password")
                                .build(),
                        ConfigOption.builder()
                                .name("serverPort")
                                .type("string")
                                .required(false)
                                .defaultValue("8080")
                                .description("Server port")
                                .build()
                ))
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
        
        // Add templates
        templates.put(reactTemplate.getId(), reactTemplate);
        templates.put(springBootTemplate.getId(), springBootTemplate);
        
        // Persist templates
        persistTemplate(reactTemplate);
        persistTemplate(springBootTemplate);
        
        log.info("Loaded built-in templates: {}", templates.size());
    }
    
    /**
     * Generate directories from template
     */
    private List<String> generateDirectories(ProjectTemplate template, Map<String, Object> config) {
        List<String> directories = new ArrayList<>();
        
        // Process variables in structure
        for (Map.Entry<String, List<String>> entry : template.getStructure().entrySet()) {
            String baseDir = processVariables(entry.getKey(), config);
            directories.add(baseDir);
            
            for (String subDir : entry.getValue()) {
                directories.add(baseDir + "/" + processVariables(subDir, config));
            }
        }
        
        return directories;
    }
    
    /**
     * Generate files from template
     */
    private List<GeneratedFile> generateFiles(ProjectTemplate template, Map<String, Object> config) {
        List<GeneratedFile> files = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : template.getFiles().entrySet()) {
            String path = processVariables(entry.getKey(), config);
            String content = processVariables(entry.getValue(), config);
            
            files.add(GeneratedFile.builder()
                    .path(path)
                    .content(content)
                    .build());
        }
        
        return files;
    }
    
    /**
     * Process variables in a string
     */
    private String processVariables(String input, Map<String, Object> config) {
        String result = input;
        
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, entry.getValue().toString());
            }
        }
        
        // Special case for packagePath
        if (result.contains("${packagePath}") && config.containsKey("packageName")) {
            String packagePath = ((String) config.get("packageName")).replace('.', '/');
            result = result.replace("${packagePath}", packagePath);
        }
        
        return result;
    }
    
    /**
     * Calculate match score between template and criteria
     */
    private double calculateMatchScore(ProjectTemplate template, TemplateCriteria criteria) {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // Match type (highest weight)
        if (criteria.getType() != null && criteria.getType() == template.getType()) {
            score += 3.0;
        }
        totalWeight += 3.0;
        
        // Match frameworks
        if (criteria.getFrameworks() != null && !criteria.getFrameworks().isEmpty()) {
            double frameworkMatches = criteria.getFrameworks().stream()
                    .filter(f -> template.getFrameworks().contains(f))
                    .count();
            score += (frameworkMatches / criteria.getFrameworks().size()) * 2.0;
            totalWeight += 2.0;
        }
        
        // Match languages
        if (criteria.getLanguages() != null && !criteria.getLanguages().isEmpty()) {
            double languageMatches = criteria.getLanguages().stream()
                    .filter(l -> template.getLanguages().contains(l))
                    .count();
            score += (languageMatches / criteria.getLanguages().size()) * 2.0;
            totalWeight += 2.0;
        }
        
        // Match tags
        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            double tagMatches = criteria.getTags().stream()
                    .filter(t -> template.getTags().contains(t))
                    .count();
            score += (tagMatches / criteria.getTags().size()) * 1.0;
            totalWeight += 1.0;
        }
        
        // Normalize score
        return score / totalWeight;
    }
    
    /**
     * Persist template to disk
     */
    private void persistTemplate(ProjectTemplate template) {
        try {
            Path templatePath = Paths.get(TEMPLATES_DIR, template.getId() + ".json");
            Files.writeString(templatePath, template.toString());
        } catch (IOException e) {
            log.error("Failed to persist template: {}", template.getId(), e);
        }
    }
    
    @Data
    @Builder
    public static class ProjectTemplate {
        private String id;
        private String name;
        private String description;
        private ProjectType type;
        private List<String> tags;
        private List<String> frameworks;
        private List<String> languages;
        private Map<String, List<String>> structure;
        private Map<String, String> files;
        private Map<String, List<Dependency>> dependencies;
        private List<ConfigOption> configOptions;
        private Instant createdAt;
        private Instant lastUpdated;
    }
    
    @Data
    @Builder
    public static class TemplateCriteria {
        private ProjectType type;
        private List<String> frameworks;
        private List<String> languages;
        private List<String> tags;
    }
    
    @Data
    @Builder
    public static class Dependency {
        // Maven/Gradle fields
        private String groupId;
        private String artifactId;
        private String version;
        private String scope;
        
        // NPM fields
        private String name;
        private boolean dev;
        private boolean peerDependency;
    }
    
    @Data
    @Builder
    public static class ConfigOption {
        private String name;
        private String type;
        private boolean required;
        private Object defaultValue;
        private String description;
        private List<String> enumValues;
    }
    
    @Data
    @Builder
    public static class ProjectStructure {
        private String templateId;
        private String name;
        private List<String> directories;
        private List<GeneratedFile> files;
        private Map<String, List<Dependency>> dependencies;
        private Map<String, Object> configuration;
    }
    
    @Data
    @Builder
    public static class GeneratedFile {
        private String path;
        private String content;
    }
    
    @Data
    @Builder
    public static class TemplateMatch {
        private ProjectTemplate template;
        private double score;
    }
    
    public enum ProjectType {
        FRONTEND,
        BACKEND,
        FULLSTACK,
        MICROSERVICE,
        LIBRARY,
        CLI,
        OTHER
    }
}
