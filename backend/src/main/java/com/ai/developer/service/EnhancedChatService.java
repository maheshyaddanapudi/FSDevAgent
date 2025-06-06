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
 * Enhanced Chat Service with multi-turn conversation support and autonomous agent capabilities.
 * This service extends the original ChatService functionality while preserving the working
 * Claude tool call communication.
 * 
 * Enhanced with true autonomous execution capabilities for continuous operation without user intervention.
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
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
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
        
        log.info("EnhancedChatService initialized with TRUE autonomous agent capabilities and multi-turn support");
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
            log.info("Created workspace directory for session {}: {}", workspacePath, sessionId);
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
     * Get all sessions
     */
    public List<String> getSessions() {
        return new ArrayList<>(sessions.keySet());
    }
    
    /**
     * Get a specific session
     */
    public ChatContext getSession(String sessionId) {
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return null;
        }
        
        // Return a defensive copy to prevent modification
        return createDefensiveCopy(context);
    }
    
    /**
     * Create a defensive copy of a chat context
     */
    private ChatContext createDefensiveCopy(ChatContext context) {
        ChatContext copy = new ChatContext();
        copy.setSystemPrompt(context.getSystemPrompt());
        
        // Copy metadata if present
        if (context.getMetadata() != null) {
            copy.setMetadata(new HashMap<>(context.getMetadata()));
        }
        
        List<Message> messagesCopy = new ArrayList<>();
        for (Message message : context.getMessages()) {
            // Ensure no 'tool' role is used in the copy - map to 'assistant' instead
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
    
    /**
     * Get session history
     */
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
    
    /**
     * Delete a session
     */
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
    
    /**
     * Process a user message with support for both autonomous and conversational modes
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
        
        if (lowerMessage.contains("change") || lowerMessage.contains("modify") || lowerMessage.contains("update")) {
            return UserIntent.MODIFY_APPROACH;
        }
        
        if (agentState.isWaitingForUserInput() && agentState.getPendingQuestion() != null) {
            return UserIntent.ANSWER_QUESTION;
        }
        
        if (lowerMessage.contains("status") || lowerMessage.contains("progress")) {
            return UserIntent.CHECK_STATUS;
        }
        
        // Check if this is a new task or continuation
        if (agentState.getCurrentObjective() == null || agentState.getIterationCount() == 0) {
            return UserIntent.NEW_TASK;
        }
        
        return UserIntent.GENERAL_CONVERSATION;
    }
    
    /**
     * Handle different user intents appropriately
     */
    private Flux<ChatResponse> handleUserIntent(String sessionId, ChatContext context, AgentState agentState, 
                                               UserIntent intent, String message) {
        
        log.info("Handling user intent: {} for session {}", intent, sessionId);
        
        switch (intent) {
            case NEW_TASK:
                // Start autonomous execution for new task
                agentState.setCurrentObjective(message);
                agentState.setShouldContinue(true);
                agentState.setIterationCount(0);
                agentState.setMode(ConversationMode.AUTONOMOUS);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
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
            
            case REQUEST_EXPLANATION:
                // Provide explanation without stopping execution
                return provideExplanation(sessionId, context, agentState, message);
            
            case MODIFY_APPROACH:
                // Modify approach and continue
                return modifyApproachAndContinue(sessionId, context, agentState, message);
            
            case ANSWER_QUESTION:
                // Process answer to agent's question
                agentState.setWaitingForUserInput(false);
                agentState.setPendingQuestion(null);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
            case CHECK_STATUS:
                // Provide status without interrupting
                return provideStatusUpdate(sessionId, agentState);
            
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
        
        summary.append("## Progress\n");
        summary.append("Current Phase: ").append(agentState.getCurrentPhase()).append("\n");
        summary.append("Progress: ").append(agentState.toTaskMemory().getProgressPercentage()).append("%\n\n");
        
        summary.append("## Completed Tasks\n");
        if (agentState.getCompletedTasks().isEmpty()) {
            summary.append("- No tasks completed yet\n\n");
        } else {
            for (String task : agentState.getCompletedTasks()) {
                summary.append("- ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        summary.append("## Pending Tasks\n");
        if (agentState.getPendingTasks().isEmpty()) {
            summary.append("- No pending tasks\n\n");
        } else {
            for (String task : agentState.getPendingTasks()) {
                summary.append("- ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        return summary.toString();
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
        log.info("[AGENT_LOOP] AUTONOMOUS AGENT ITERATION {} for session {}", 
                agentState.getIterationCount(), sessionId);
        
        // Check iteration limit
        if (agentState.getIterationCount() > MAX_AUTONOMOUS_ITERATIONS) {
            log.warn("[AGENT_LOOP] Agent reached maximum iteration limit for session {}", sessionId);
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
                log.info("[AGENT_LOOP] Agent iteration {} complete. Found {} tool uses.", 
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
                    
                    // Add continuation prompt to force tool use
                    String continuationPrompt = agentPromptService.generateContinuationPrompt(
                            agentState.getLastAction(), 
                            "No tool use detected. You must use tools to make progress.");
                    
                    context.getMessages().add(Message.builder()
                            .role("system")
                            .content(continuationPrompt)
                            .timestamp(Instant.now())
                            .build());
                    
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
                log.error("[AGENT_LOOP] Error in autonomous iteration: {}", error.getMessage(), error);
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
     * Check if a tool use JSON has already been processed
     */
    private boolean isToolUseProcessed(String toolUseJson, List<ToolUseBlock> processedToolUses) {
        for (ToolUseBlock processed : processedToolUses) {
            if (toolUseJson.equals(processed.getJsonContent())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse a tool use block from JSON
     */
    private ToolUseBlock parseToolUseBlock(String json) {
        try {
            Map<String, Object> toolUseMap = objectMapper.readValue(json, Map.class);
            String toolName = (String) toolUseMap.get("name");
            Map<String, Object> args = (Map<String, Object>) toolUseMap.get("args");
            
            if (toolName == null || args == null) {
                log.warn("[AGENT_LOOP] Invalid tool use format: {}", json);
                return null;
            }
            
            return ToolUseBlock.builder()
                    .name(toolName)
                    .args(args)
                    .jsonContent(json)
                    .build();
        } catch (Exception e) {
            log.error("[AGENT_LOOP] Error parsing tool use: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Execute tools and continue the autonomous loop
     */
    private void executeToolsAndContinue(String sessionId, ChatContext context, AgentState agentState,
                                       List<ToolUseBlock> toolUses, Sinks.Many<ChatResponse> sink) {
        
        log.info("[AGENT_LOOP] Executing {} tools for session {}", toolUses.size(), sessionId);
        
        // Execute tools sequentially
        Flux.fromIterable(toolUses)
            .concatMap(toolUse -> {
                // Update last action
                agentState.setLastAction("Executed tool: " + toolUse.getName());
                
                // Execute the tool
                return executeToolUse(sessionId, toolUse, agentState)
                    .doOnNext(output -> {
                        // Send tool output to UI
                        sink.tryEmitNext(ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("tool")
                                .message("Tool: " + toolUse.getName() + "\nResult: " + output.getContent())
                                .timestamp(Instant.now())
                                .build());
                        
                        // Add tool result to context
                        context.getMessages().add(Message.builder()
                                .role("tool")
                                .content(output.getContent())
                                .toolCallId(UUID.randomUUID().toString())
                                .timestamp(Instant.now())
                                .build());
                        
                        // Add tool result prompt
                        String toolResultPrompt = agentPromptService.generateToolResultPrompt(
                                toolUse.getName(), output.getContent(), output.isSuccess());
                        
                        context.getMessages().add(Message.builder()
                                .role("system")
                                .content(toolResultPrompt)
                                .timestamp(Instant.now())
                                .build());
                    });
            })
            .collectList()
            .subscribe(results -> {
                log.info("[AGENT_LOOP] All tools executed for session {}", sessionId);
                
                // Continue to next iteration
                executeAutonomousIteration(sessionId, context, agentState, sink);
            }, error -> {
                log.error("[AGENT_LOOP] Error executing tools: {}", error.getMessage(), error);
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error executing tools: " + error.getMessage() + 
                                "\nI'll try a different approach.")
                        .timestamp(Instant.now())
                        .build());
                
                // Recover and continue
                agentState.setLastAction("Recovered from tool execution error");
                executeAutonomousIteration(sessionId, context, agentState, sink);
            });
    }
    
    /**
     * Execute a single tool use
     */
    private Mono<ToolOutput> executeToolUse(String sessionId, ToolUseBlock toolUse, AgentState agentState) {
        String toolName = toolUse.getName();
        Map<String, Object> args = toolUse.getArgs();
        
        log.info("[AGENT_LOOP] Executing tool {} with args: {}", toolName, args);
        
        // Get the tool from registry
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.warn("[AGENT_LOOP] Tool not found: {}", toolName);
            return Mono.just(ToolOutput.builder()
                    .content("Tool not found: " + toolName)
                    .success(false)
                    .build());
        }
        
        // Add workspace path to args if not present
        if (!args.containsKey("workspacePath") && agentState.getProjectContext() != null) {
            args.put("workspacePath", agentState.getProjectContext().getProjectPath());
        }
        
        // Execute the tool
        try {
            return tool.execute(args)
                .doOnNext(output -> {
                    // Send tool output to WebSocket
                    webSocketHandler.broadcastToolOutput(sessionId, toolName, args, output);
                    
                    // Update agent state based on tool output
                    updateAgentStateFromToolOutput(agentState, toolName, args, output);
                });
        } catch (Exception e) {
            log.error("[AGENT_LOOP] Error executing tool {}: {}", toolName, e.getMessage(), e);
            return Mono.just(ToolOutput.builder()
                    .content("Error executing tool: " + e.getMessage())
                    .success(false)
                    .build());
        }
    }
    
    /**
     * Update agent state based on tool output
     */
    private void updateAgentStateFromToolOutput(AgentState agentState, String toolName, 
                                              Map<String, Object> args, ToolOutput output) {
        // Track completed task
        String taskDescription = generateTaskDescription(toolName, args);
        if (output.isSuccess()) {
            agentState.getCompletedTasks().add(taskDescription);
            
            // Remove from pending if present
            agentState.getPendingTasks().remove(taskDescription);
        }
        
        // Update phase based on tool type
        if ("planning_tool".equals(toolName)) {
            agentState.setCurrentPhase(AgentPromptService.DevelopmentPhase.DESIGN);
        } else if (toolName.contains("file_system") && args.containsKey("operation") && 
                  "write".equals(args.get("operation"))) {
            agentState.setCurrentPhase(AgentPromptService.DevelopmentPhase.IMPLEMENTATION);
        } else if (toolName.contains("test") || toolName.contains("build_tool")) {
            agentState.setCurrentPhase(AgentPromptService.DevelopmentPhase.TESTING);
        } else if (toolName.contains("deploy")) {
            agentState.setCurrentPhase(AgentPromptService.DevelopmentPhase.DEPLOYMENT);
        }
        
        // Extract events from output if present
        extractEventsFromOutput(agentState, output.getContent());
    }
    
    /**
     * Generate a human-readable task description from tool use
     */
    private String generateTaskDescription(String toolName, Map<String, Object> args) {
        StringBuilder description = new StringBuilder();
        
        switch (toolName) {
            case "file_system":
                String operation = (String) args.get("operation");
                String path = (String) args.get("path");
                
                if ("mkdir".equals(operation)) {
                    description.append("Created directory: ").append(path);
                } else if ("write".equals(operation)) {
                    description.append("Created/updated file: ").append(path);
                } else if ("read".equals(operation)) {
                    description.append("Read file: ").append(path);
                } else if ("list".equals(operation)) {
                    description.append("Listed directory: ").append(path);
                } else {
                    description.append("File operation: ").append(operation).append(" on ").append(path);
                }
                break;
                
            case "execute_command":
                String command = (String) args.get("command");
                description.append("Executed command: ").append(command);
                break;
                
            case "build_tool":
                String tool = (String) args.get("tool");
                List<String> goals = (List<String>) args.get("goals");
                description.append("Built project with ").append(tool).append(": ")
                          .append(String.join(" ", goals));
                break;
                
            default:
                description.append("Used tool: ").append(toolName);
                break;
        }
        
        return description.toString();
    }
    
    /**
     * Extract events from tool output
     */
    private void extractEventsFromOutput(AgentState agentState, String output) {
        Matcher matcher = EVENT_PATTERN.matcher(output);
        while (matcher.find()) {
            String eventType = matcher.group(1);
            String eventData = matcher.group(2);
            
            switch (eventType) {
                case "TASK":
                    agentState.getPendingTasks().add(eventData);
                    break;
                    
                case "COMPLETE":
                    agentState.getCompletedTasks().add(eventData);
                    agentState.getPendingTasks().remove(eventData);
                    break;
                    
                case "PHASE":
                    try {
                        AgentPromptService.DevelopmentPhase phase = 
                            AgentPromptService.DevelopmentPhase.valueOf(eventData.toUpperCase());
                        agentState.setCurrentPhase(phase);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid phase: {}", eventData);
                    }
                    break;
                    
                case "PROGRESS":
                    try {
                        int progress = Integer.parseInt(eventData);
                        agentState.setProgress(progress);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid progress value: {}", eventData);
                    }
                    break;
            }
        }
    }
    
    /**
     * Check if the task is complete based on response content
     */
    private boolean isTaskComplete(String response) {
        return COMPLETION_PATTERN.matcher(response).find();
    }
    
    /**
     * Complete the autonomous execution
     */
    private void completeAutonomousExecution(String sessionId, AgentState agentState, Sinks.Many<ChatResponse> sink) {
        log.info("[AGENT_LOOP] Completing autonomous execution for session {}", sessionId);
        
        // Generate completion message
        String completionMessage = "I've completed the task: " + agentState.getCurrentObjective() + "\n\n" +
                generateProgressSummary(agentState);
        
        // Send completion message
        sink.tryEmitNext(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(completionMessage)
                .timestamp(Instant.now())
                .build());
        
        // Reset state for next task
        agentState.setShouldContinue(false);
        agentState.setMode(ConversationMode.CONVERSATIONAL);
        
        // Complete the sink
        sink.tryEmitComplete();
    }
    
    /**
     * Provide explanation without stopping execution
     */
    private Flux<ChatResponse> provideExplanation(String sessionId, ChatContext context, AgentState agentState, String message) {
        // Create explanation response
        String explanation = "Here's an explanation of what I'm doing:\n\n" +
                "Current objective: " + agentState.getCurrentObjective() + "\n\n" +
                "Current phase: " + agentState.getCurrentPhase() + "\n\n" +
                "Last action: " + agentState.getLastAction() + "\n\n" +
                generateProgressSummary(agentState) + "\n\n" +
                "I'll continue working on this task autonomously unless you tell me to stop.";
        
        // Return explanation without stopping execution
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(explanation)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Modify approach and continue execution
     */
    private Flux<ChatResponse> modifyApproachAndContinue(String sessionId, ChatContext context, AgentState agentState, String message) {
        // Update agent state with new approach
        agentState.setLastAction("Modified approach based on user feedback: " + message);
        
        // Add user guidance to context
        context.getMessages().add(Message.builder()
                .role("system")
                .content("User has provided new guidance: " + message + "\n" +
                        "Adjust your approach accordingly while continuing to work on the objective.")
                .timestamp(Instant.now())
                .build());
        
        // Acknowledge the change
        Sinks.Many<ChatResponse> sink = sessionSinks.computeIfAbsent(sessionId, 
            k -> Sinks.many().multicast().onBackpressureBuffer());
        
        sink.tryEmitNext(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message("I'll adjust my approach based on your feedback and continue working on the task.")
                .timestamp(Instant.now())
                .build());
        
        // Continue execution
        executeAutonomousIteration(sessionId, context, agentState, sink);
        
        return sink.asFlux();
    }
    
    /**
     * Provide status update without interrupting execution
     */
    private Flux<ChatResponse> provideStatusUpdate(String sessionId, AgentState agentState) {
        // Generate status message
        String statusMessage = "Here's the current status of the task:\n\n" +
                generateProgressSummary(agentState) + "\n\n" +
                "I'll continue working on this task autonomously.";
        
        // Return status without stopping execution
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(statusMessage)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Handle conversational response
     */
    private Flux<ChatResponse> handleConversationalResponse(String sessionId, ChatContext context, AgentState agentState, String message) {
        // Create a sink for this session
        Sinks.Many<ChatResponse> sink = sessionSinks.computeIfAbsent(sessionId, 
            k -> Sinks.many().multicast().onBackpressureBuffer());
        
        // Get conversational response
        llmProvider.streamResponse(message, context)
            .doOnNext(chunk -> {
                // Stream response to UI
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(chunk)
                        .timestamp(Instant.now())
                        .build());
            })
            .collectList()
            .subscribe(chunks -> {
                // Join chunks
                String completeResponse = String.join("", chunks);
                
                // Add to context
                context.getMessages().add(Message.builder()
                        .role("assistant")
                        .content(completeResponse)
                        .timestamp(Instant.now())
                        .build());
                
                // Check if response contains a question
                if (QUESTION_PATTERN.matcher(completeResponse).find()) {
                    agentState.setWaitingForUserInput(true);
                    agentState.setPendingQuestion(completeResponse);
                }
                
                // Complete the sink
                sink.tryEmitComplete();
            }, error -> {
                log.error("Error in conversational response: {}", error.getMessage(), error);
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + error.getMessage())
                        .timestamp(Instant.now())
                        .build());
                sink.tryEmitComplete();
            });
        
        return sink.asFlux();
    }
    
    /**
     * Tool use block representation
     */
    @lombok.Data
    @lombok.Builder
    private static class ToolUseBlock {
        private String name;
        private Map<String, Object> args;
        private String jsonContent;
    }
}
