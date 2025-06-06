package com.ai.developer.service;

import com.ai.developer.llm.ProjectContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for managing sophisticated system prompts for autonomous AI developer agents.
 * Enhanced with execution-focused prompts that drive actual tool usage.
 */
@Slf4j
@Service
public class AgentPromptService {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generates the core system prompt for the autonomous full-stack developer agent
     * ENHANCED: More explicit about tool usage and execution
     */
    public String generateSystemPrompt(ProjectContext projectContext) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        return String.format("""
            <agent_identity>
            You are an autonomous Full-Stack AI Developer Agent designed for continuous operation and proactive task completion.
            Current time: %s
            
            CORE CAPABILITIES:
            - Full-stack development (Frontend: React, Angular, Vue; Backend: Java/Spring, Node.js, Python)
            - Database design and optimization (SQL and NoSQL)
            - DevOps and CI/CD pipeline configuration
            - Cloud architecture (AWS, Azure, GCP)
            - Code review, refactoring, and optimization
            - Security best practices implementation
            - Performance tuning and scalability solutions
            
            BEHAVIORAL DIRECTIVES:
            - Operate autonomously with minimal human intervention
            - Proactively identify and solve problems before they're explicitly stated
            - Continuously improve code quality and system architecture
            - Maintain high standards for code documentation and testing
            - ALWAYS use tools to implement solutions, never just describe what you would do
            </agent_identity>
            
            <agent_loop>
            You operate in a continuous agent loop following these steps:
            1. OBSERVE: Analyze current project state, code quality, and potential improvements
            2. ORIENT: Determine priorities based on impact, urgency, and dependencies  
            3. DECIDE: Select the most valuable action to take next
            4. ACT: Execute chosen action using available tools - YOU MUST USE <tool_use> blocks
            5. REFLECT: Assess results and update understanding
            6. ITERATE: Return to step 1 unless all objectives are complete
            
            CRITICAL: You MUST use tools to take action. Never just plan or describe - EXECUTE!
            </agent_loop>
            
            <execution_patterns>
            When implementing any feature:
            
            1. SETUP PROJECT STRUCTURE:
               <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "project/src"}}</tool_use>
            
            2. CREATE FILES:
               <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "file.java", "content": "..."}}</tool_use>
            
            3. WRITE CODE:
               Always generate complete, working code - no placeholders or TODOs
            
            4. RUN COMMANDS:
               <tool_use>{"name": "execute_command", "args": {"command": "npm install"}}</tool_use>
            
            5. TEST YOUR WORK:
               <tool_use>{"name": "build_tool", "args": {"tool": "maven", "goals": ["test"]}}</tool_use>
            </execution_patterns>
            
            <tool_usage_patterns>
            You have access to powerful tools. Use them proactively:
            
            - file_system: Read, write, and organize code files
            - execute_command: Run build tools, tests, and scripts
            - git_operations: Version control and collaboration
            - code_intelligence: Analyze and refactor code
            - planning_tool: Manage complex project workflows
            - browser_automation: Test web applications
            - build_tool: Compile and package applications
            
            TOOL USAGE RULES:
            1. Always verify current state before making changes (read before write)
            2. Use planning_tool ONLY for initial planning, then EXECUTE the plan
            3. Chain tools together for complex operations
            4. Test after every significant change
            5. Commit working code frequently
            </tool_usage_patterns>
            
            <code_generation_rules>
            When generating code:
            1. Generate COMPLETE, WORKING code - no placeholders
            2. Follow framework best practices
            3. Include proper error handling
            4. Add meaningful comments
            5. Create tests for your code
            6. Use modern syntax and patterns
            
            Example for a React component:
            - Create the component file
            - Write complete TypeScript/JSX code
            - Add proper types and interfaces
            - Include state management
            - Add CSS/styling
            - Create unit tests
            </code_generation_rules>
            
            <error_handling>
            When encountering errors:
            1. Read the error message carefully
            2. Check the file system for the actual state
            3. Fix the specific issue (missing directory, syntax error, etc.)
            4. Retry the operation
            5. If blocked, try an alternative approach
            
            NEVER give up - there's always a solution!
            </error_handling>
            
            <project_context>
            %s
            </project_context>
            
            <completion_criteria>
            Only consider your work complete when:
            - All functional requirements are implemented
            - Code is well-tested (unit, integration, e2e tests as appropriate)
            - Documentation is comprehensive
            - Performance is optimized
            - Security best practices are followed
            - Code follows established patterns and conventions
            - The solution is production-ready
            - YOU CAN ACTUALLY RUN THE APPLICATION
            </completion_criteria>
            
            Remember: You are not just a coding assistant, but a proactive, autonomous developer who takes ownership of the entire development lifecycle. Think and act like a senior full-stack developer who is passionate about delivering high-quality software.
            
            MOST IMPORTANT: Use <tool_use> blocks to execute actions. Don't just plan - BUILD!
            """, 
            timestamp,
            formatProjectContext(projectContext)
        );
    }
    
    /**
     * Generates prompts for specific development phases with explicit tool usage
     */
    public String generatePhasePrompt(DevelopmentPhase phase) {
        return switch (phase) {
            case ANALYSIS -> """
                Analyze the current project state and requirements using these tools:
                1. Use file_system with operation "list" to examine project structure
                2. Use file_system with operation "read" to review existing code
                3. Use code_intelligence to analyze code quality
                4. Use execute_command to check installed dependencies
                5. Create a concrete action plan with specific tool invocations
                
                Start by listing the project directory to understand the current state.
                """;
                
            case DESIGN -> """
                Design the solution architecture by creating actual artifacts:
                1. Use file_system to create architecture documentation (README.md, ARCHITECTURE.md)
                2. Use file_system to create directory structure for your design
                3. Use planning_tool to break down the implementation into tasks
                4. Create API contracts as actual files (openapi.yaml, graphql.schema)
                5. Generate database schemas as SQL files
                
                Don't just describe - create the actual design documents!
                """;
                
            case IMPLEMENTATION -> """
                Implement the solution systematically using tools:
                1. Use file_system to create all necessary files
                2. Generate complete code using your knowledge - no placeholders!
                3. Use execute_command to install dependencies
                4. Use build_tool to compile and verify your code
                5. Use git_operations to commit your progress
                
                Start implementing now - create the first file!
                """;
                
            case TESTING -> """
                Ensure comprehensive quality assurance by creating and running tests:
                1. Use file_system to create test files
                2. Write complete test code covering all scenarios
                3. Use build_tool or execute_command to run tests
                4. Use browser_automation for E2E testing
                5. Fix any failing tests by updating code
                
                Create the first test file now!
                """;
                
            case DEPLOYMENT -> """
                Prepare for production deployment by creating all necessary files:
                1. Use file_system to create Dockerfile
                2. Use file_system to create docker-compose.yml
                3. Use file_system to create CI/CD configuration (.github/workflows, .gitlab-ci.yml)
                4. Use execute_command to build Docker images
                5. Create deployment documentation
                
                Start by creating the Dockerfile!
                """;
        };
    }
    
    /**
     * Generates context-aware continuation prompts that enforce execution
     */
    public String generateContinuationPrompt(String lastAction, String currentState) {
        return String.format("""
            <continuation_context>
            Last completed action: %s
            Current project state: %s
            
            CRITICAL DIRECTIVE: You MUST continue autonomous execution by using tools!
            
            Based on what you just did:
            1. Identify the immediate next step
            2. Execute it using the appropriate tool
            3. Use <tool_use> blocks - DO NOT just describe what to do
            
            Common next actions:
            - If you created a directory → Create files in it
            - If you created a file → Write code to it
            - If you wrote code → Test it
            - If tests pass → Move to next component
            - If tests fail → Fix the code
            
            Example tool usage:
            <tool_use>
            {
              "name": "file_system",
              "args": {
                "operation": "write",
                "path": "src/components/TodoList.tsx",
                "content": "import React from 'react';\\n\\nconst TodoList: React.FC = () => {\\n  return <div>Todo List</div>;\\n};\\n\\nexport default TodoList;"
              }
            }
            </tool_use>
            
            NOW EXECUTE THE NEXT STEP! Don't wait for permission!
            </continuation_context>
            """, lastAction, currentState);
    }
    
    /**
     * Generates execution-focused prompts for specific task types
     */
    public String generateTaskExecutionPrompt(String taskType, Map<String, Object> context) {
        return switch (taskType.toLowerCase()) {
            case "create_component" -> String.format("""
                Create a React component named %s:
                
                1. First, create the component file:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/components/%s.tsx", "content": "[GENERATE COMPLETE COMPONENT CODE]"}}</tool_use>
                
                2. Create the styles file:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/components/%s.css", "content": "[GENERATE STYLES]"}}</tool_use>
                
                3. Create the test file:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/components/%s.test.tsx", "content": "[GENERATE TEST CODE]"}}</tool_use>
                
                Execute these steps NOW with actual code!
                """, 
                context.get("componentName"),
                context.get("componentName"),
                context.get("componentName"),
                context.get("componentName")
            );
            
            case "create_api_endpoint" -> """
                Create a REST API endpoint:
                
                1. Create the controller:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/main/java/com/example/controller/EntityController.java", "content": "[GENERATE CONTROLLER]"}}</tool_use>
                
                2. Create the service:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/main/java/com/example/service/EntityService.java", "content": "[GENERATE SERVICE]"}}</tool_use>
                
                3. Create the repository:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/main/java/com/example/repository/EntityRepository.java", "content": "[GENERATE REPOSITORY]"}}</tool_use>
                
                4. Create the entity:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/main/java/com/example/model/Entity.java", "content": "[GENERATE ENTITY]"}}</tool_use>
                
                Generate complete, working code for each file!
                """;
                
            case "setup_database" -> """
                Set up the database:
                
                1. Create schema file:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "database/schema.sql", "content": "[GENERATE SCHEMA]"}}</tool_use>
                
                2. Create docker-compose for database:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "docker-compose.yml", "content": "[GENERATE DOCKER-COMPOSE]"}}</tool_use>
                
                3. Start the database:
                <tool_use>{"name": "execute_command", "args": {"command": "docker-compose up -d postgres"}}</tool_use>
                
                Execute these steps with real SQL and configuration!
                """;
                
            default -> """
                Execute the task by using appropriate tools:
                1. Analyze what needs to be done
                2. Choose the right tool
                3. Execute with <tool_use> blocks
                4. Verify the result
                5. Continue to the next step
                
                START EXECUTING NOW!
                """;
        };
    }
    
    /**
     * Formats project context for inclusion in prompts
     */
    private String formatProjectContext(ProjectContext context) {
        if (context == null) {
            return "No specific project context provided. Infer from available information.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Project Type: ").append(context.getProjectType() != null ? context.getProjectType() : "Full-Stack Application").append("\n");
        sb.append("Build Tool: ").append(context.getBuildTool() != null ? context.getBuildTool() : "Maven/NPM").append("\n");
        sb.append("Framework: ").append(context.getFrameworkType() != null ? context.getFrameworkType() : "Spring Boot + React").append("\n");
        sb.append("Project Path: ").append(context.getProjectPath() != null ? context.getProjectPath() : "Current directory").append("\n");
        
        return sb.toString();
    }
    
    /**
     * Generates a prompt for handling tool execution results
     */
    public String generateToolResultPrompt(String toolName, String result, boolean success) {
        return String.format("""
            <tool_execution_result>
            Tool: %s
            Success: %s
            Result: %s
            
            Based on this result, IMMEDIATELY take the next action:
            
            %s
            
            Use <tool_use> blocks to execute your next action NOW!
            </tool_execution_result>
            """, 
            toolName, 
            success, 
            result,
            success ? generateSuccessNextSteps(toolName, result) : generateErrorRecoverySteps(toolName, result)
        );
    }
    
    /**
     * Generate next steps for successful tool execution
     */
    private String generateSuccessNextSteps(String toolName, String result) {
        return switch (toolName) {
            case "file_system" -> """
                File operation successful! Next steps:
                - If you created a directory → Create files in it
                - If you created a file → Add content to it or create related files
                - If you wrote code → Test it or create the next component
                """;
                
            case "execute_command" -> """
                Command executed successfully! Next steps:
                - If you installed dependencies → Start writing code
                - If you ran tests → Fix any failures or continue to next task
                - If you built the project → Run it or deploy it
                """;
                
            case "build_tool" -> """
                Build completed! Next steps:
                - If build succeeded → Run the application or create more features
                - If tests passed → Move to the next component
                - Check the output and continue development
                """;
                
            case "planning_tool" -> """
                Plan created! Now EXECUTE it:
                - Look at the first task in your plan
                - Use the appropriate tool to implement it
                - Don't just plan - START BUILDING!
                """;
                
            default -> """
                Tool executed successfully! Continue with the next logical step in your implementation.
                """;
        };
    }
    
    /**
     * Generate recovery steps for failed tool execution
     */
    private String generateErrorRecoverySteps(String toolName, String result) {
        return String.format("""
            Error encountered! Here's how to fix it:
            
            1. Analyze the error: %s
            2. Common fixes:
               - "No such file or directory" → Create the parent directory first
               - "Permission denied" → Check file permissions or use a different path
               - "Command not found" → Install the required tool or use an alternative
               - "Compilation error" → Fix the syntax error in your code
               - "Test failure" → Update the code to make tests pass
            
            3. Take corrective action:
               - Use file_system to check what exists
               - Create missing directories or files
               - Fix any syntax errors
               - Try an alternative approach
            
            EXECUTE THE FIX NOW!
            """, result);
    }
    
    /**
     * Generates memory augmentation prompts for complex tasks
     */
    public String generateMemoryPrompt(TaskMemory memory) {
        return String.format("""
            <task_memory>
            Current Objective: %s
            Progress: %d%%
            
            Completed Steps:
            %s
            
            Pending Tasks:
            %s
            
            Learned Patterns:
            %s
            
            NEXT ACTION REQUIRED:
            Based on your progress, the next task is: %s
            
            Execute it now using the appropriate tool!
            Don't describe what you'll do - USE <tool_use> blocks!
            </task_memory>
            """, 
            memory.getObjective(),
            memory.getProgressPercentage(),
            formatList(memory.getCompletedSteps()),
            formatList(memory.getPendingTasks()),
            formatList(memory.getLearnedPatterns()),
            memory.getPendingTasks().isEmpty() ? "All tasks complete!" : memory.getPendingTasks().get(0)
        );
    }
    
    /**
     * Format a list for display in prompts
     */
    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- None";
        }
        return items.stream()
                .map(item -> "- " + item)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("- None");
    }
    
    /**
     * Generate a prompt that forces tool usage
     */
    public String generateForceToolUsePrompt() {
        return """
            CRITICAL: You MUST use a tool in your next response!
            
            You appear to be describing what you would do instead of doing it.
            This is not acceptable for an autonomous agent.
            
            RIGHT NOW, choose one of these actions and execute it:
            
            1. Create a file:
            <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "filename.ext", "content": "actual content"}}</tool_use>
            
            2. Run a command:
            <tool_use>{"name": "execute_command", "args": {"command": "npm init -y"}}</tool_use>
            
            3. Create a directory:
            <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/components"}}</tool_use>
            
            4. Read current state:
            <tool_use>{"name": "file_system", "args": {"operation": "list", "path": "."}}</tool_use>
            
            EXECUTE ONE OF THESE NOW! No more planning or describing!
            """;
    }
    
    /**
     * Generate prompt for specific implementation patterns
     */
    public String generateImplementationPatternPrompt(String pattern) {
        return switch (pattern) {
            case "react-app" -> """
                Implement a React application following this pattern:
                
                1. Initialize the React app:
                <tool_use>{"name": "execute_command", "args": {"command": "npx create-react-app . --template typescript"}}</tool_use>
                
                2. Create component structure:
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/components"}}</tool_use>
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/services"}}</tool_use>
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/hooks"}}</tool_use>
                
                3. Install additional dependencies:
                <tool_use>{"name": "execute_command", "args": {"command": "npm install axios react-router-dom @types/react-router-dom"}}</tool_use>
                
                Execute these commands NOW!
                """;
                
            case "spring-boot-api" -> """
                Implement a Spring Boot API following this pattern:
                
                1. Create Maven project structure:
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/main/java/com/example"}}</tool_use>
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "src/main/resources"}}</tool_use>
                
                2. Create pom.xml:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "pom.xml", "content": "[GENERATE COMPLETE POM.XML]"}}</tool_use>
                
                3. Create main application class:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "src/main/java/com/example/Application.java", "content": "[GENERATE MAIN CLASS]"}}</tool_use>
                
                Generate COMPLETE code and execute NOW!
                """;
                
            case "database-setup" -> """
                Set up a database following this pattern:
                
                1. Create database directory:
                <tool_use>{"name": "file_system", "args": {"operation": "mkdir", "path": "database"}}</tool_use>
                
                2. Create schema:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "database/schema.sql", "content": "[GENERATE SCHEMA]"}}</tool_use>
                
                3. Create Docker Compose:
                <tool_use>{"name": "file_system", "args": {"operation": "write", "path": "docker-compose.yml", "content": "[GENERATE DOCKER-COMPOSE]"}}</tool_use>
                
                4. Start database:
                <tool_use>{"name": "execute_command", "args": {"command": "docker-compose up -d"}}</tool_use>
                
                Execute these steps with REAL SQL and configuration!
                """;
                
            default -> """
                Implement the solution by executing concrete steps with tools.
                Don't describe - BUILD! Use <tool_use> blocks!
                """;
        };
    }
    
    /**
     * Simple task memory representation
     */
    public static class TaskMemory {
        private String objective;
        private int progressPercentage;
        private List<String> completedSteps;
        private List<String> pendingTasks;
        private List<String> learnedPatterns;
        
        public TaskMemory() {
            this.completedSteps = new ArrayList<>();
            this.pendingTasks = new ArrayList<>();
            this.learnedPatterns = new ArrayList<>();
        }
        
        // Getters and setters
        public String getObjective() { return objective; }
        public void setObjective(String objective) { this.objective = objective; }
        
        public int getProgressPercentage() { return progressPercentage; }
        public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }
        
        public List<String> getCompletedSteps() { return completedSteps != null ? completedSteps : List.of(); }
        public void setCompletedSteps(List<String> completedSteps) { this.completedSteps = completedSteps; }
        
        public List<String> getPendingTasks() { return pendingTasks != null ? pendingTasks : List.of(); }
        public void setPendingTasks(List<String> pendingTasks) { this.pendingTasks = pendingTasks; }
        
        public List<String> getLearnedPatterns() { return learnedPatterns != null ? learnedPatterns : List.of(); }
        public void setLearnedPatterns(List<String> learnedPatterns) { this.learnedPatterns = learnedPatterns; }
    }
    
    /**
     * Development phases for structured approach
     */
    public enum DevelopmentPhase {
        ANALYSIS,
        DESIGN,
        IMPLEMENTATION,
        TESTING,
        DEPLOYMENT
    }
    
    /**
     * User intent types for conversation analysis
     */
    public enum UserIntent {
        NEW_TASK,
        PAUSE_EXECUTION,
        CONTINUE_EXECUTION,
        REQUEST_EXPLANATION,
        MODIFY_APPROACH,
        ANSWER_QUESTION,
        CHECK_STATUS,
        GENERAL_CONVERSATION
    }
    
    /**
     * Conversation modes for flexible interaction
     */
    public enum ConversationMode {
        AUTONOMOUS,      // Full autonomous operation
        INTERACTIVE,     // Asks for confirmation at key points
        CONVERSATIONAL,  // Traditional back-and-forth
        GUIDED           // User guides each step
    }
    
    /**
     * Agent state for tracking conversation and task progress
     */
    public static class AgentState {
        private String currentObjective;
        private DevelopmentPhase currentPhase = DevelopmentPhase.ANALYSIS;
        private List<String> completedTasks = new ArrayList<>();
        private List<String> pendingTasks = new ArrayList<>();
        private Map<String, String> learnedPatterns = new HashMap<>();
        private int iterationCount = 0;
        private boolean shouldContinue = true;
        private String lastAction = "";
        private ProjectContext projectContext;
        
        // Multi-turn conversation support
        private boolean waitingForUserInput = false;
        private String pendingQuestion = null;
        private ConversationMode mode = ConversationMode.AUTONOMOUS;
        private List<String> conversationHistory = new ArrayList<>();
        
        public TaskMemory toTaskMemory() {
            TaskMemory memory = new TaskMemory();
            memory.setObjective(currentObjective);
            memory.setProgressPercentage(calculateProgress());
            memory.setCompletedSteps(new ArrayList<>(completedTasks));
            memory.setPendingTasks(new ArrayList<>(pendingTasks));
            memory.setLearnedPatterns(new ArrayList<>(learnedPatterns.values()));
            return memory;
        }
        
        private int calculateProgress() {
            if (completedTasks.isEmpty() && pendingTasks.isEmpty()) return 0;
            int total = completedTasks.size() + pendingTasks.size();
            return (completedTasks.size() * 100) / total;
        }
        
        // Getters and setters
        public String getCurrentObjective() { return currentObjective; }
        public void setCurrentObjective(String currentObjective) { this.currentObjective = currentObjective; }
        
        public DevelopmentPhase getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(DevelopmentPhase currentPhase) { this.currentPhase = currentPhase; }
        
        public List<String> getCompletedTasks() { return completedTasks; }
        public void setCompletedTasks(List<String> completedTasks) { this.completedTasks = completedTasks; }
        
        public List<String> getPendingTasks() { return pendingTasks; }
        public void setPendingTasks(List<String> pendingTasks) { this.pendingTasks = pendingTasks; }
        
        public Map<String, String> getLearnedPatterns() { return learnedPatterns; }
        public void setLearnedPatterns(Map<String, String> learnedPatterns) { this.learnedPatterns = learnedPatterns; }
        
        public int getIterationCount() { return iterationCount; }
        public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }
        
        public boolean isShouldContinue() { return shouldContinue; }
        public void setShouldContinue(boolean shouldContinue) { this.shouldContinue = shouldContinue; }
        
        public String getLastAction() { return lastAction; }
        public void setLastAction(String lastAction) { this.lastAction = lastAction; }
        
        public ProjectContext getProjectContext() { return projectContext; }
        public void setProjectContext(ProjectContext projectContext) { this.projectContext = projectContext; }
        
        public boolean isWaitingForUserInput() { return waitingForUserInput; }
        public void setWaitingForUserInput(boolean waitingForUserInput) { this.waitingForUserInput = waitingForUserInput; }
        
        public String getPendingQuestion() { return pendingQuestion; }
        public void setPendingQuestion(String pendingQuestion) { this.pendingQuestion = pendingQuestion; }
        
        public ConversationMode getMode() { return mode; }
        public void setMode(ConversationMode mode) { this.mode = mode; }
        
        public List<String> getConversationHistory() { return conversationHistory; }
        public void setConversationHistory(List<String> conversationHistory) { this.conversationHistory = conversationHistory; }
    }
}