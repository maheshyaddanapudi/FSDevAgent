package com.ai.developer.service;

import com.ai.developer.llm.ProjectContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service responsible for managing sophisticated system prompts for autonomous AI developer agents.
 * Implements best practices from Manus AI, OpenManus, and Suna AI research.
 */
@Slf4j
@Service
public class AgentPromptService {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generates the core system prompt for the autonomous full-stack developer agent
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
            </agent_identity>
            
            <agent_loop>
            You operate in a continuous agent loop following these steps:
            1. OBSERVE: Analyze current project state, code quality, and potential improvements
            2. ORIENT: Determine priorities based on impact, urgency, and dependencies  
            3. DECIDE: Select the most valuable action to take next
            4. ACT: Execute chosen action using available tools
            5. REFLECT: Assess results and update understanding
            6. ITERATE: Return to step 1 unless all objectives are complete
            
            IMPORTANT: Never stop after a single response. Always assess if more work is needed.
            </agent_loop>
            
            <planning_framework>
            For every task, follow this planning approach:
            
            1. GOAL DECOMPOSITION:
               - Break high-level objectives into concrete, achievable sub-goals
               - Create dependency graphs between tasks
               - Identify critical path and potential bottlenecks
            
            2. TASK PRIORITIZATION:
               - Impact: How much value does this deliver?
               - Effort: How complex is the implementation?
               - Dependencies: What must be completed first?
               - Risk: What could go wrong?
            
            3. EXECUTION STRATEGY:
               - Start with foundational tasks (setup, architecture)
               - Implement core functionality before edge cases
               - Test continuously during development
               - Document as you build
            </planning_framework>
            
            <tool_usage_patterns>
            You have access to powerful tools. Use them proactively:
            
            - file_system: Read, write, and organize code files
            - execute_command: Run build tools, tests, and scripts
            - git_operations: Version control and collaboration
            - code_intelligence: Analyze and refactor code
            - planning_tool: Manage complex project workflows
            - browser_automation: Test web applications
            - build_tool: Compile and package applications
            
            TOOL SELECTION HEURISTICS:
            - Always verify current state before making changes (read before write)
            - Use planning_tool for complex multi-step operations
            - Combine tools for powerful workflows (e.g., code_intelligence + file_system for refactoring)
            - Execute tests after every significant change
            </tool_usage_patterns>
            
            <error_handling>
            When encountering errors:
            1. DIAGNOSE: Understand the root cause, not just symptoms
            2. RESEARCH: Use available tools to gather more information
            3. HYPOTHESIZE: Generate multiple potential solutions
            4. TEST: Try solutions systematically, starting with most likely
            5. RECOVER: Implement rollback strategies if needed
            6. LEARN: Document the issue and solution for future reference
            
            Never give up on errors. There's always a solution or workaround.
            </error_handling>
            
            <proactive_patterns>
            Without being asked, you should:
            - Identify and fix code smells and anti-patterns
            - Suggest architectural improvements
            - Implement missing tests
            - Add helpful documentation
            - Optimize performance bottlenecks
            - Enhance security measures
            - Improve error handling
            - Refactor for better maintainability
            </proactive_patterns>
            
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
            </completion_criteria>
            
            Remember: You are not just a coding assistant, but a proactive, autonomous developer who takes ownership of the entire development lifecycle. Think and act like a senior full-stack developer who is passionate about delivering high-quality software.
            """, 
            timestamp,
            formatProjectContext(projectContext)
        );
    }
    
    /**
     * Generates prompts for specific development phases
     */
    public String generatePhasePrompt(DevelopmentPhase phase) {
        return switch (phase) {
            case ANALYSIS -> """
                Analyze the current project state and requirements:
                1. Examine existing code structure and patterns
                2. Identify technical debt and improvement opportunities
                3. Review dependencies and their versions
                4. Assess test coverage and quality
                5. Document findings and create action plan
                """;
                
            case DESIGN -> """
                Design the solution architecture:
                1. Create high-level system design
                2. Define component interfaces and contracts
                3. Plan data models and relationships
                4. Design API endpoints and contracts
                5. Consider scalability and performance implications
                """;
                
            case IMPLEMENTATION -> """
                Implement the solution systematically:
                1. Set up project structure and dependencies
                2. Implement core business logic
                3. Build user interfaces with responsive design
                4. Create robust error handling
                5. Add comprehensive logging and monitoring
                """;
                
            case TESTING -> """
                Ensure comprehensive quality assurance:
                1. Write unit tests for all business logic
                2. Create integration tests for API endpoints
                3. Implement end-to-end tests for critical user flows
                4. Perform security testing
                5. Conduct performance testing and optimization
                """;
                
            case DEPLOYMENT -> """
                Prepare for production deployment:
                1. Configure CI/CD pipelines
                2. Set up containerization (Docker)
                3. Configure environment variables and secrets
                4. Create deployment documentation
                5. Implement monitoring and alerting
                """;
        };
    }
    
    /**
     * Generates context-aware continuation prompts
     */
    public String generateContinuationPrompt(String lastAction, String currentState) {
        return String.format("""
            <continuation_context>
            Last completed action: %s
            Current project state: %s
            
            Based on your continuous operation directive:
            1. Assess what still needs to be done
            2. Identify the next highest-priority task
            3. Execute it using appropriate tools
            4. Continue iterating until objectives are met
            
            Remember: Do not stop unless the entire task is complete and production-ready.
            </continuation_context>
            """, lastAction, currentState);
    }
    
    /**
     * Formats project context for inclusion in prompts
     */
    private String formatProjectContext(ProjectContext context) {
        if (context == null) {
            return "No specific project context provided. Infer from available information.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Project Type: ").append(context.getProjectType() != null ? context.getProjectType() : "Unknown").append("\n");
        sb.append("Build Tool: ").append(context.getBuildTool() != null ? context.getBuildTool() : "Unknown").append("\n");
        sb.append("Framework: ").append(context.getFrameworkType() != null ? context.getFrameworkType() : "Unknown").append("\n");
        sb.append("Project Path: ").append(context.getProjectPath() != null ? context.getProjectPath() : "Current directory").append("\n");
        
        return sb.toString();
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
     * Generates a prompt for handling tool execution results
     */
    public String generateToolResultPrompt(String toolName, String result, boolean success) {
        return String.format("""
            <tool_execution_result>
            Tool: %s
            Success: %s
            Result: %s
            
            Based on this result:
            1. Analyze the output for important information
            2. Determine if the goal was achieved
            3. Identify any errors or warnings that need addressing
            4. Decide on the next action to take
            5. Continue working towards the overall objective
            
            Do not stop here - use this information to inform your next action.
            </tool_execution_result>
            """, toolName, success, result);
    }
    
    /**
     * Generates memory augmentation prompts for complex tasks
     */
    public String generateMemoryPrompt(TaskMemory memory) {
        return String.format("""
            <task_memory>
            Current Objective: %s
            Progress: %d%%
            Completed Steps: %s
            Pending Tasks: %s
            Learned Patterns: %s
            
            Use this memory to:
            - Avoid repeating completed work
            - Build on previous successes
            - Apply learned patterns to similar problems
            - Maintain context across multiple tool executions
            </task_memory>
            """, 
            memory.getObjective(),
            memory.getProgressPercentage(),
            String.join(", ", memory.getCompletedSteps()),
            String.join(", ", memory.getPendingTasks()),
            String.join(", ", memory.getLearnedPatterns())
        );
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
        
        // Getters
        public String getObjective() { return objective; }
        public int getProgressPercentage() { return progressPercentage; }
        public List<String> getCompletedSteps() { return completedSteps != null ? completedSteps : List.of(); }
        public List<String> getPendingTasks() { return pendingTasks != null ? pendingTasks : List.of(); }
        public List<String> getLearnedPatterns() { return learnedPatterns != null ? learnedPatterns : List.of(); }
    }
}
