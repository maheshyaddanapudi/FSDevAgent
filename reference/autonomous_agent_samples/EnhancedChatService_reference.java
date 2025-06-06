package com.ai.developer.service;

import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.llm.*;
import com.ai.developer.model.*;
import com.ai.developer.service.AgentPromptService.AgentState;
import com.ai.developer.service.AgentPromptService.ConversationMode;
import com.ai.developer.service.AgentPromptService.TaskMemory;
import com.ai.developer.service.AgentPromptService.UserIntent;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Chat Service with true autonomous agent capabilities.
 * This service implements a complete execution loop that translates plans into actions.
 */
@Service
@Slf4j
public class EnhancedChatService {
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolOutputWebSocketHandler webSocketHandler;
    private final AgentPromptService agentPromptService;
    private final TaskExecutorService taskExecutorService;
    private final CodeGenerationService codeGenerationService;
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<ChatResponse>> sessionSinks = new ConcurrentHashMap<>();
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Patterns for parsing responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>", Pattern.DOTALL);
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("(?i)(task complete|objectives? (?:met|achieved|completed)|all (?:done|finished)|nothing (?:more|else) to do)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("(?i)(\\?|would you like|should i|do you want|can you clarify|need more information|what about)");
    private static final Pattern EVENT_PATTERN = Pattern.compile("EVENT:([^:]+):(.+)");
    
    // Maximum iterations to prevent infinite loops
    private static final int MAX_AUTONOMOUS_ITERATIONS = 100;
    
    public EnhancedChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      ToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
        // Initialize auxiliary services
        this.taskExecutorService = new TaskExecutorService(toolRegistry, objectMapper, webSocketHandler);
        this.codeGenerationService = new CodeGenerationService();
        
        log.info("EnhancedChatService initialized with TRUE autonomous agent capabilities");
    }
    
    /**
     * Create a new session with workspace initialization and agent state
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        
        // Create session workspace directory
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        try {
            Files.createDirectories(Path.of(workspacePath));
            log.info("Created workspace directory for session {}: {}", sessionId, workspacePath);
        } catch (Exception e) {
            log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
        
        // Create chat context with autonomous agent prompt
        ChatContext context = new ChatContext();
        ProjectContext projectContext = new ProjectContext();
        projectContext.setProjectPath(workspacePath);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", workspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setProjectContext(projectContext);
        agentState.setMode(ConversationMode.AUTONOMOUS); // Start in autonomous mode for true autonomy
        
        // Store context and state
        sessions.put(sessionId, context);
        agentStates.put(sessionId, agentState);
        
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .workspacePath(workspacePath)
                .build());
    }
    
    /**
     * Process a user message with true autonomous execution
     */
    public Flux<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for session {}: {}", sessionId, message);
        
        ChatContext context = sessions.get(sessionId);
        AgentState agentState = agentStates.get(sessionId);
        
        if (context == null || agentState == null) {
            log.warn("Session not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Add user message to context
        context.getMessages().add(Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation
        agentState.getConversationHistory().add("User: " + message);
        
        // Analyze user intent
        UserIntent intent = analyzeUserIntent(message, agentState);
        
        // For new tasks, set objective and start autonomous execution
        if (intent == UserIntent.NEW_TASK) {
            agentState.setCurrentObjective(message);
            agentState.setShouldContinue(true);
            agentState.setIterationCount(0);
            agentState.setMode(ConversationMode.AUTONOMOUS);
            
            // Start the autonomous execution loop
            return executeAutonomousAgentLoop(sessionId, context, agentState);
        }
        
        return handleUserIntent(sessionId, context, agentState, intent, message);
    }
    
    /**
     * Execute the TRUE autonomous agent loop with actual tool execution
     */
    private Flux<ChatResponse> executeAutonomousAgentLoop(String sessionId, ChatContext context, AgentState agentState) {
        // Create a sink for this session
        Sinks.Many<ChatResponse> sink = sessionSinks.computeIfAbsent(sessionId, 
            k -> Sinks.many().multicast().onBackpressureBuffer());
        
        // Start the autonomous execution in a separate thread
        executeAutonomousIteration(sessionId, context, agentState, sink);
        
        return sink.asFlux();
    }
    
    /**
     * Execute a single iteration of the autonomous agent loop with REAL execution
     */
    private void executeAutonomousIteration(String sessionId, ChatContext context, AgentState agentState, 
                                           Sinks.Many<ChatResponse> sink) {
        
        agentState.setIterationCount(agentState.getIterationCount() + 1);
        log.info("AUTONOMOUS AGENT ITERATION {} for session {}", 
                agentState.getIterationCount(), sessionId);
        
        // Check iteration limit
        if (agentState.getIterationCount() > MAX_AUTONOMOUS_ITERATIONS) {
            log.warn("Agent reached maximum iteration limit for session {}", sessionId);
            sink.tryEmitNext(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("I've reached my iteration limit. The task might be too complex or I may need human intervention.")
                    .timestamp(Instant.now())
                    .build());
            sink.tryEmitComplete();
            return;
        }
        
        // Build the prompt for the next action
        String prompt = buildAutonomousPrompt(agentState);
        
        // Add the prompt to context
        context.getMessages().add(Message.builder()
                .role("system")
                .content(prompt)
                .timestamp(Instant.now())
                .build());
        
        // Stream the LLM response and process it
        final StringBuilder responseBuilder = new StringBuilder();
        final AtomicBoolean hasToolUse = new AtomicBoolean(false);
        final List<ToolUseBlock> pendingToolUses = new ArrayList<>();
        
        llmProvider.streamResponse("Continue working autonomously on the objective.", context)
            .doOnNext(chunk -> {
                responseBuilder.append(chunk);
                
                // Check for tool use patterns in the chunk
                Matcher toolMatcher = TOOL_USE_PATTERN.matcher(responseBuilder.toString());
                while (toolMatcher.find()) {
                    String toolUseJson = toolMatcher.group(1);
                    if (!isToolUseProcessed(toolUseJson, pendingToolUses)) {
                        ToolUseBlock toolUse = parseToolUseBlock(toolUseJson);
                        if (toolUse != null) {
                            pendingToolUses.add(toolUse);
                            hasToolUse.set(true);
                        }
                    }
                }
                
                // Stream non-tool chunks to UI
                if (!chunk.contains("<tool_use>") && !chunk.contains("</tool_use>")) {
                    sink.tryEmitNext(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(chunk)
                            .timestamp(Instant.now())
                            .build());
                }
            })
            .doOnComplete(() -> {
                String completeResponse = responseBuilder.toString();
                log.info("Agent iteration {} complete. Found {} tool uses.", 
                        agentState.getIterationCount(), pendingToolUses.size());
                
                // Update context
                Message assistantMessage = Message.builder()
                        .role("assistant")
                        .content(completeResponse)
                        .timestamp(Instant.now())
                        .build();
                context.getMessages().add(assistantMessage);
                
                // If there are tool uses, execute them
                if (!pendingToolUses.isEmpty()) {
                    executeToolsAndContinue(sessionId, context, agentState, pendingToolUses, sink);
                } else if (isTaskComplete(completeResponse) || !agentState.isShouldContinue()) {
                    // Task complete
                    completeAutonomousExecution(sessionId, agentState, sink);
                } else {
                    // No tool use but task not complete - prompt for next action
                    agentState.setLastAction("Analyzed current state");
                    
                    // Small delay to prevent tight loops
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Continue to next iteration
                    executeAutonomousIteration(sessionId, context, agentState, sink);
                }
            })
            .doOnError(error -> {
                log.error("Error in autonomous iteration: {}", error.getMessage(), error);
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + error.getMessage() + "\nI'll try a different approach.")
                        .timestamp(Instant.now())
                        .build());
                
                // Recover and continue
                agentState.setLastAction("Recovered from error");
                executeAutonomousIteration(sessionId, context, agentState, sink);
            })
            .subscribe();
    }
    
    /**
     * Build a prompt that encourages autonomous action
     */
    private String buildAutonomousPrompt(AgentState agentState) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("AUTONOMOUS EXECUTION DIRECTIVE\n\n");
        prompt.append("Current Objective: ").append(agentState.getCurrentObjective()).append("\n");
        prompt.append("Iteration: ").append(agentState.getIterationCount()).append("\n");
        
        if (agentState.getLastAction() != null && !agentState.getLastAction().isEmpty()) {
            prompt.append("Last Action: ").append(agentState.getLastAction()).append("\n");
        }
        
        // Add task progress if available
        if (!agentState.getCompletedTasks().isEmpty()) {
            prompt.append("\nCompleted Tasks:\n");
            for (String task : agentState.getCompletedTasks()) {
                prompt.append("- ").append(task).append("\n");
            }
        }
        
        if (!agentState.getPendingTasks().isEmpty()) {
            prompt.append("\nPending Tasks:\n");
            for (String task : agentState.getPendingTasks()) {
                prompt.append("- ").append(task).append("\n");
            }
        }
        
        prompt.append("\nIMPORTANT: You MUST take concrete action to progress toward the objective. ");
        prompt.append("Use the available tools to:\n");
        prompt.append("1. Create files and directories\n");
        prompt.append("2. Write code\n");
        prompt.append("3. Execute commands\n");
        prompt.append("4. Test your implementation\n");
        prompt.append("5. Make corrections as needed\n\n");
        
        prompt.append("Do not just plan or describe - EXECUTE the next concrete step using <tool_use> blocks.\n");
        prompt.append("If you've completed a phase, move to the next phase immediately.\n");
        
        return prompt.toString();
    }
    
    /**
     * Execute tool uses and continue the autonomous loop
     */
    private void executeToolsAndContinue(String sessionId, ChatContext context, AgentState agentState,
                                       List<ToolUseBlock> toolUses, Sinks.Many<ChatResponse> sink) {
        
        log.info("Executing {} tools for session {}", toolUses.size(), sessionId);
        
        // Execute tools sequentially
        Flux.fromIterable(toolUses)
            .concatMap(toolUse -> {
                // Update last action
                agentState.setLastAction("Executed tool: " + toolUse.getName());
                
                // Execute the tool
                return executeToolUse(sessionId, context, agentState, toolUse)
                    .doOnNext(response -> sink.tryEmitNext(response));
            })
            .doOnComplete(() -> {
                log.info("All tools executed. Continuing autonomous execution.");
                
                // Continue to next iteration
                executeAutonomousIteration(sessionId, context, agentState, sink);
            })
            .doOnError(error -> {
                log.error("Error executing tools: {}", error.getMessage(), error);
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("Tool execution error: " + error.getMessage())
                        .timestamp(Instant.now())
                        .build());
                        
                // Continue despite error
                executeAutonomousIteration(sessionId, context, agentState, sink);
            })
            .subscribe();
    }
    
    /**
     * Execute a single tool use block
     */
    private Flux<ChatResponse> executeToolUse(String sessionId, ChatContext context, AgentState agentState, 
                                            ToolUseBlock toolUse) {
        log.info("Executing tool: {} with args: {}", toolUse.getName(), toolUse.getArgs());
        
        // Get the tool
        Tool tool = toolRegistry.getTool(toolUse.getName());
        if (tool == null) {
            log.warn("Tool not found: {}", toolUse.getName());
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("tool")
                    .message("Error: Tool not found: " + toolUse.getName())
                    .timestamp(Instant.now())
                    .build());
        }
        
        // Add session context to tool arguments
        Map<String, Object> args = new HashMap<>(toolUse.getArgs());
        args.put("sessionId", sessionId);
        
        if (agentState.getProjectContext() != null && agentState.getProjectContext().getProjectPath() != null) {
            args.put("workspacePath", agentState.getProjectContext().getProjectPath());
        }
        
        // Broadcast tool usage
        webSocketHandler.broadcastToolUsage(sessionId, toolUse.getName(), args, toolUse.getId());
        
        // Execute the tool
        return tool.execute(args)
            .map(output -> {
                // Broadcast tool result
                webSocketHandler.broadcastToolResult(sessionId, toolUse.getName(), output.getContent(), toolUse.getId());
                
                // Add to context
                context.getMessages().add(Message.builder()
                        .role("tool")
                        .content("Tool: " + toolUse.getName() + "\nResult: " + output.getContent())
                        .timestamp(Instant.now())
                        .build());
                
                // Update completed tasks if this was a significant action
                if (isSignificantAction(toolUse.getName())) {
                    String taskDescription = describeToolAction(toolUse);
                    agentState.getCompletedTasks().add(taskDescription);
                }
                
                return ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("tool")
                        .message(output.getContent())
                        .timestamp(Instant.now())
                        .build();
            })
            .onErrorResume(error -> {
                log.error("Error executing tool {}: {}", toolUse.getName(), error.getMessage());
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("tool")
                        .message("Error executing " + toolUse.getName() + ": " + error.getMessage())
                        .timestamp(Instant.now())
                        .build());
            });
    }
    
    /**
     * Check if a tool use has already been processed
     */
    private boolean isToolUseProcessed(String toolUseJson, List<ToolUseBlock> processedTools) {
        for (ToolUseBlock processed : processedTools) {
            // Simple check - could be enhanced with better comparison
            if (processed.getName() != null && toolUseJson.contains(processed.getName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if a tool action is significant enough to track
     */
    private boolean isSignificantAction(String toolName) {
        return Arrays.asList("file_system", "execute_command", "git_operations", 
                           "build_tool", "planning_tool").contains(toolName);
    }
    
    /**
     * Generate a description of what the tool action accomplished
     */
    private String describeToolAction(ToolUseBlock toolUse) {
        String toolName = toolUse.getName();
        Map<String, Object> args = toolUse.getArgs();
        
        switch (toolName) {
            case "file_system":
                String operation = (String) args.get("operation");
                String path = (String) args.get("path");
                return String.format("%s file/directory: %s", operation, path);
                
            case "execute_command":
                String command = (String) args.get("command");
                return String.format("Executed command: %s", command);
                
            case "planning_tool":
                String planOp = (String) args.get("operation");
                return String.format("Planning operation: %s", planOp);
                
            default:
                return String.format("Used tool: %s", toolName);
        }
    }
    
    /**
     * Complete the autonomous execution
     */
    private void completeAutonomousExecution(String sessionId, AgentState agentState, Sinks.Many<ChatResponse> sink) {
        agentState.setShouldContinue(false);
        
        StringBuilder completionMessage = new StringBuilder();
        completionMessage.append("\n✅ Task completed successfully!\n\n");
        completionMessage.append("**Summary of work done:**\n");
        
        for (String task : agentState.getCompletedTasks()) {
            completionMessage.append("- ").append(task).append("\n");
        }
        
        completionMessage.append("\nThe ").append(agentState.getCurrentObjective())
                        .append(" has been implemented.\n\n");
        completionMessage.append("Would you like me to:\n");
        completionMessage.append("- Run the application?\n");
        completionMessage.append("- Explain the implementation?\n");
        completionMessage.append("- Make modifications?\n");
        completionMessage.append("- Start a new task?");
        
        sink.tryEmitNext(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(completionMessage.toString())
                .timestamp(Instant.now())
                .build());
        
        // Don't complete the sink - keep it open for further interaction
    }
    
    /**
     * Parse a tool use block from JSON
     */
    private ToolUseBlock parseToolUseBlock(String json) {
        try {
            // Try to parse as a direct tool use block
            Map<String, Object> toolUseMap = objectMapper.readValue(json, Map.class);
            String name = (String) toolUseMap.get("name");
            
            // Check for args or arguments field
            Map<String, Object> args = null;
            if (toolUseMap.containsKey("args")) {
                args = (Map<String, Object>) toolUseMap.get("args");
            } else if (toolUseMap.containsKey("arguments")) {
                args = (Map<String, Object>) toolUseMap.get("arguments");
            } else if (toolUseMap.containsKey("input")) {
                Object input = toolUseMap.get("input");
                if (input instanceof Map) {
                    args = (Map<String, Object>) input;
                }
            }
            
            // Initialize empty map if args is null
            if (args == null) {
                args = new HashMap<>();
            }
            
            ToolUseBlock block = new ToolUseBlock();
            block.setName(name);
            block.setArgs(args);
            block.setId(UUID.randomUUID().toString());
            return block;
        } catch (Exception e) {
            log.warn("Failed to parse tool use block: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Analyze user intent to determine response mode
     */
    private UserIntent analyzeUserIntent(String message, AgentState agentState) {
        String lowerMessage = message.toLowerCase();
        
        // Check for mode switches
        if (lowerMessage.contains("stop") || lowerMessage.contains("pause") || lowerMessage.contains("wait")) {
            return UserIntent.PAUSE_EXECUTION;
        }
        
        if (lowerMessage.contains("continue") || lowerMessage.contains("proceed") || lowerMessage.contains("go ahead")) {
            return UserIntent.CONTINUE_EXECUTION;
        }
        
        if (lowerMessage.contains("explain") || lowerMessage.contains("why") || lowerMessage.contains("how")) {
            return UserIntent.REQUEST_EXPLANATION;
        }
        
        if (lowerMessage.contains("status") || lowerMessage.contains("progress")) {
            return UserIntent.CHECK_STATUS;
        }
        
        // Check if this looks like a development task
        if (containsDevelopmentKeywords(lowerMessage)) {
            return UserIntent.NEW_TASK;
        }
        
        return UserIntent.GENERAL_CONVERSATION;
    }
    
    /**
     * Check if message contains development-related keywords
     */
    private boolean containsDevelopmentKeywords(String message) {
        String[] keywords = {
            "build", "create", "implement", "develop", "make", "code", "write",
            "app", "application", "website", "api", "service", "component",
            "todo", "crud", "blog", "dashboard", "backend", "frontend"
        };
        
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Handle different user intents appropriately
     */
    private Flux<ChatResponse> handleUserIntent(String sessionId, ChatContext context, AgentState agentState, 
                                               UserIntent intent, String message) {
        
        log.info("Handling user intent: {} for session {}", intent, sessionId);
        
        switch (intent) {
            case PAUSE_EXECUTION:
                // Pause autonomous execution
                agentState.setShouldContinue(false);
                agentState.setMode(ConversationMode.CONVERSATIONAL);
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I've paused the execution. Here's what I've completed so far:\n\n" + 
                                generateProgressSummary(agentState) + 
                                "\n\nWhat would you like me to do next?")
                        .timestamp(Instant.now())
                        .build());
            
            case CONTINUE_EXECUTION:
                // Resume autonomous execution
                agentState.setShouldContinue(true);
                agentState.setMode(ConversationMode.AUTONOMOUS);
                agentState.setWaitingForUserInput(false);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
            case CHECK_STATUS:
                // Provide status without interrupting
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("Here's the current status:\n\n" + generateProgressSummary(agentState))
                        .timestamp(Instant.now())
                        .build());
            
            case REQUEST_EXPLANATION:
            case GENERAL_CONVERSATION:
            default:
                // Handle as regular conversation
                return handleConversationalResponse(sessionId, context, agentState, message);
        }
    }
    
    /**
     * Generate a progress summary for the current agent state
     */
    private String generateProgressSummary(AgentState agentState) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("## Current Objective\n");
        summary.append(agentState.getCurrentObjective()).append("\n\n");
        
        summary.append("## Completed Tasks\n");
        if (agentState.getCompletedTasks().isEmpty()) {
            summary.append("- No tasks completed yet\n\n");
        } else {
            for (String task : agentState.getCompletedTasks()) {
                summary.append("- ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        summary.append("## Iterations\n");
        summary.append("- Total iterations: ").append(agentState.getIterationCount()).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Handle a general conversational response
     */
    private Flux<ChatResponse> handleConversationalResponse(String sessionId, ChatContext context, 
                                                          AgentState agentState, String message) {
        // Use the LLM to generate a response
        return llmProvider.streamResponse(message, context)
            .reduceWith(() -> new StringBuilder(), StringBuilder::append)
            .flatMapMany(completeResponse -> {
                String responseText = completeResponse.toString();
                
                // Add assistant message to context
                Message assistantMessage = Message.builder()
                        .role("assistant")
                        .content(responseText)
                        .timestamp(Instant.now())
                        .build();
                context.getMessages().add(assistantMessage);
                
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(responseText)
                        .timestamp(Instant.now())
                        .build());
            });
    }
    
    /**
     * Check if a response indicates task completion
     */
    private boolean isTaskComplete(String response) {
        return COMPLETION_PATTERN.matcher(response).find();
    }
    
    // Other existing methods remain the same...
    
    public List<String> getSessions() {
        return new ArrayList<>(sessions.keySet());
    }
    
    public ChatContext getSession(String sessionId) {
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return null;
        }
        
        // Return a defensive copy to prevent modification
        return createDefensiveCopy(context);
    }
    
    private ChatContext createDefensiveCopy(ChatContext context) {
        ChatContext copy = new ChatContext();
        copy.setSystemPrompt(context.getSystemPrompt());
        
        if (context.getMetadata() != null) {
            copy.setMetadata(new HashMap<>(context.getMetadata()));
        }
        
        List<Message> messagesCopy = new ArrayList<>();
        for (Message message : context.getMessages()) {
            String role = "tool".equals(message.getRole()) ? "assistant" : message.getRole();
            
            messagesCopy.add(Message.builder()
                    .role(role)
                    .content(message.getContent())
                    .toolCallId(message.getToolCallId())
                    .toolCall(message.getToolCall())
                    .timestamp(message.getTimestamp())
                    .build());
        }
        
        copy.setMessages(messagesCopy);
        return copy;
    }
    
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        List<ChatResponse> history = new ArrayList<>();
        for (Message message : context.getMessages()) {
            history.add(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role(message.getRole())
                    .message(message.getContent())
                    .timestamp(message.getTimestamp())
                    .build());
        }
        
        return Mono.just(history);
    }
    
    public boolean deleteSession(String sessionId) {
        ChatContext removed = sessions.remove(sessionId);
        AgentState removedState = agentStates.remove(sessionId);
        Sinks.Many<ChatResponse> removedSink = sessionSinks.remove(sessionId);
        
        if (removed != null) {
            log.info("Deleted session: {}", sessionId);
            return true;
        }
        
        log.warn("Session not found for deletion: {}", sessionId);
        return false;
    }
}