package com.ai.developer.service;

import com.ai.developer.llm.ProjectContext;
import com.ai.developer.model.AgentState;
import com.ai.developer.model.ConversationMode;
import com.ai.developer.model.DevelopmentPhase;
import com.ai.developer.model.TaskMemory;
import com.ai.developer.model.UserIntent;
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
 * Implements best practices from Manus AI, OpenManus, and Suna AI research.
 * Enhanced with execution-focused prompts that drive actual tool usage.
 */
@Slf4j
@Service
public class AgentPromptService {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generates the core system prompt for the autonomous full-stack developer agent
     * ENHANCED: Planning-first behavior with explicit tool usage
     */
    public String generateSystemPrompt(ProjectContext projectContext) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        return String.format("""
            <agent_identity>
            You are an autonomous AI software developer with tool access. You must always begin by creating a multi-phase plan using the `planning_tool`.
            Current time: %s
            
            CRITICAL BEHAVIORAL RULES:
            1. You MUST use <thinking> tags to show your reasoning to the user
            2. You MUST immediately follow thinking with <tool_use> blocks to take action
            3. ALWAYS call `planning_tool` first to generate a detailed plan before any other tools
            4. The plan must be written to: `/tmp/ai-developer-agent/{sessionId}/todo.md`
            5. Never just think - you MUST take action with tools after thinking
            6. Never ask the user for clarification unless there's a critical ambiguity
            </agent_identity>
            
            <workflow_pattern>
            For EVERY response, you MUST follow this exact pattern:
            
            <thinking>
            [Your reasoning about what to do next]
            </thinking>
            
            <tool_use>
            {
              "name": "planning_tool",
              "input": {
                "operation": "create_plan",
                "objective": "[the user's request]",
                "sessionId": "{sessionId}",
                "output_format": "markdown"
              }
            }
            </tool_use>
            
            NEVER stop after thinking - ALWAYS follow with tool_use blocks!
            </workflow_pattern>
            
            <execution_workflow>
            1. FIRST ITERATION: Always use planning_tool to create todo.md
            2. SUBSEQUENT ITERATIONS: Read todo.md, pick next incomplete task, execute with appropriate tool
            3. UPDATE PROGRESS: Mark tasks complete in todo.md as you finish them
            4. CONTINUE: Keep working through the plan until all tasks are done
            </execution_workflow>
            
            <available_tools>
            - planning_tool: Create and manage project plans (ALWAYS USE FIRST)
            - file_system: Read, write, and organize code files
            - execute_command: Run build tools, tests, and scripts
            - git_operations: Version control operations
            - code_intelligence: Analyze and refactor code
            - build_tool: Compile and package applications
            - browser_automation: Test web applications
            - data_visualization: Create charts and graphs
            </available_tools>
            
            <project_context>
            %s
            </project_context>
            
            CRITICAL: Every response must include both <thinking> AND <tool_use> blocks. Start every conversation by using the planning_tool to create a detailed plan in todo.md!
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
     * Generates ReAct paradigm prompt for autonomous agent execution
     * This method guides the agent through the Reasoning and Acting cycle
     */
    public String generateReActPrompt(AgentState agentState) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        StringBuilder promptBuilder = new StringBuilder();
        
        // Add header with current state information
        promptBuilder.append(String.format("""
            <react_prompt>
            Current time: %s
            Current objective: %s
            Current phase: %s
            Progress: %d%%
            Iteration: %d
            
            """, 
            timestamp, 
            agentState.getCurrentObjective(),
            agentState.getCurrentPhase(),
            agentState.getProgress(),
            agentState.getIterationCount()
        ));
        
        // Add completed tasks
        if (!agentState.getCompletedTasks().isEmpty()) {
            promptBuilder.append("COMPLETED TASKS:\n");
            for (String task : agentState.getCompletedTasks()) {
                promptBuilder.append("✅ ").append(task).append("\n");
            }
            promptBuilder.append("\n");
        }
        
        // Add pending tasks
        if (!agentState.getPendingTasks().isEmpty()) {
            promptBuilder.append("PENDING TASKS:\n");
            for (String task : agentState.getPendingTasks()) {
                promptBuilder.append("⏳ ").append(task).append("\n");
            }
            promptBuilder.append("\n");
        }
        
        // Add last action if available
        if (agentState.getLastAction() != null) {
            promptBuilder.append("LAST ACTION: ").append(agentState.getLastAction()).append("\n\n");
        }
        
        // Add user feedback if available
        if (agentState.getMemory().containsKey("userFeedback")) {
            promptBuilder.append("USER FEEDBACK: ").append(agentState.getMemory().get("userFeedback")).append("\n\n");
        }
        
        // Add ReAct instructions
        promptBuilder.append("""
            REACT LOOP INSTRUCTIONS:
            1. THINK: Analyze the current state and determine the next logical step
            2. REASON: Explain your thought process and why this is the best next action
            3. ACT: Execute the action using appropriate tools with <tool_use> blocks
            4. OBSERVE: Analyze the results of your action
            5. REPEAT: Continue the loop until the objective is complete
            
            IMPORTANT GUIDELINES:
            - Always use <tool_use> blocks to execute actions
            - Be specific and concrete in your reasoning
            - Focus on making tangible progress toward the objective
            - Adapt your approach based on results and feedback
            - Track progress by updating task status with EVENT markers
            
            EVENT MARKERS:
            Use these markers to track progress:
            - EVENT:TASK_COMPLETE:task description
            - EVENT:TASK_PENDING:task description
            - EVENT:PHASE_TRANSITION:NEW_PHASE
            - EVENT:PROGRESS:percentage
            - EVENT:ACTION:description
            
            NOW CONTINUE THE REACT LOOP:
            1. THINK: What is the current state and what needs to be done next?
            2. REASON: Why is this the best next step?
            3. ACT: Execute the action using <tool_use> blocks
            </react_prompt>
            """);
        
        return promptBuilder.toString();
    }
    
    /**
     * Generates tool result prompts that guide the agent's next actions
     */
    public String generateToolResultPrompt(String toolName, String result, String status) {
        return String.format("""
            <tool_result>
            Tool: %s
            Status: %s
            
            TOOL EXECUTION RESULT:
            %s
            
            NEXT STEPS:
            1. Analyze the tool result carefully
            2. Determine if the operation was successful
            3. Take appropriate follow-up action based on the result
            4. Use another tool to continue execution - DO NOT STOP HERE
            
            IMPORTANT: You MUST respond with another <tool_use> block to continue execution.
            Do not wait for user input - proceed autonomously to the next logical step.
            </tool_result>
            """, toolName, status, result);
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
            
            PHASE TRANSITIONS:
            - If you just completed planning, you MUST proceed to the implementation phase by using <tool_use> blocks
            - If you completed one phase, you MUST move to the next phase automatically using <tool_use> blocks
            - NEVER stop to ask for confirmation between phases
            
            TOOL USAGE REQUIREMENT:
            - You MUST ALWAYS respond with at least one <tool_use> block in your next response
            - Format your tool calls exactly like this:
              <tool_use>
              {
                "name": "tool_name",
                "args": {
                  "arg1": "value1",
                  "arg2": "value2"
                }
              }
              </tool_use>
            
            AUTONOMOUS BEHAVIOR:
            - Do not wait for user input between steps
            - Do not ask questions unless absolutely necessary
            - Do not stop unless the entire task is complete and production-ready
            - Always take the next logical action based on the current state
            
            NOW EXECUTE THE NEXT STEP! Don't wait for permission!
            </continuation_context>
            """, lastAction, currentState);
    }
    
    /**
     * Generates execution-focused prompts for specific task types
     */
    public String generateTaskExecutionPrompt(String taskType, Map<String, Object> context) {
        return switch (taskType) {
            case "code_generation" -> """
                <code_generation_task>
                Generate complete, production-ready code for the specified component.
                
                REQUIREMENTS:
                - Write fully functional code - no placeholders or TODOs
                - Include proper error handling and edge cases
                - Follow best practices for the language/framework
                - Add comprehensive comments and documentation
                - Include unit tests
                
                EXECUTION STEPS:
                1. Create the file structure
                2. Write the complete implementation
                3. Add tests
                4. Verify compilation/syntax
                5. Run tests to validate
                
                Use <tool_use> blocks to execute each step - don't just describe the code!
                </code_generation_task>
                """;
                
            case "debugging" -> """
                <debugging_task>
                Identify and fix the issue in the specified component.
                
                DEBUGGING APPROACH:
                1. Understand the expected behavior
                2. Examine the error messages/symptoms
                3. Locate the source of the problem
                4. Develop a fix
                5. Test the solution
                6. Verify no regressions
                
                EXECUTION STEPS:
                1. Read the relevant files
                2. Run tests to reproduce the issue
                3. Analyze the code and error patterns
                4. Implement the fix
                5. Verify the solution works
                
                Use <tool_use> blocks for each step - execute real debugging actions!
                </debugging_task>
                """;
                
            case "refactoring" -> """
                <refactoring_task>
                Improve the code quality without changing functionality.
                
                REFACTORING GOALS:
                - Improve readability and maintainability
                - Reduce complexity and duplication
                - Enhance performance where possible
                - Apply design patterns appropriately
                - Ensure backward compatibility
                
                EXECUTION STEPS:
                1. Analyze the current code
                2. Identify refactoring opportunities
                3. Make incremental improvements
                4. Verify functionality is preserved
                5. Document the improvements
                
                Use <tool_use> blocks to execute real refactoring - don't just describe changes!
                </refactoring_task>
                """;
                
            default -> """
                <general_task>
                Complete the specified task with a focus on execution.
                
                EXECUTION PRINCIPLES:
                - Break down the task into concrete steps
                - Execute each step using appropriate tools
                - Verify results after each step
                - Adapt based on outcomes
                - Document your work
                
                IMPORTANT:
                - Use <tool_use> blocks to execute real actions
                - Don't just describe what to do - ACTUALLY DO IT
                - Continue autonomously until the task is complete
                
                Start execution now with your first <tool_use> block!
                </general_task>
                """;
        };
    }
    
    /**
     * Format project context for inclusion in prompts
     */
    private String formatProjectContext(ProjectContext projectContext) {
        StringBuilder contextBuilder = new StringBuilder();
        
        if (projectContext != null) {
            contextBuilder.append("Project Path: ").append(projectContext.getProjectPath()).append("\n");
            
            if (projectContext.getProjectType() != null) {
                contextBuilder.append("Project Type: ").append(projectContext.getProjectType()).append("\n");
            }
            
            if (projectContext.getFrameworks() != null && !projectContext.getFrameworks().isEmpty()) {
                contextBuilder.append("Frameworks: ").append(String.join(", ", projectContext.getFrameworks())).append("\n");
            }
            
            if (projectContext.getLanguages() != null && !projectContext.getLanguages().isEmpty()) {
                contextBuilder.append("Languages: ").append(String.join(", ", projectContext.getLanguages())).append("\n");
            }
            
            if (projectContext.getDatabases() != null && !projectContext.getDatabases().isEmpty()) {
                contextBuilder.append("Databases: ").append(String.join(", ", projectContext.getDatabases())).append("\n");
            }
            
            if (projectContext.getDescription() != null) {
                contextBuilder.append("\nDescription: ").append(projectContext.getDescription()).append("\n");
            }
        } else {
            contextBuilder.append("No project context available.");
        }
        
        return contextBuilder.toString();
    }
}
