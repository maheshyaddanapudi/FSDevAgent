package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.llm.*;
import com.ai.developer.model.*;
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
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final AgentPromptService agentPromptService;
    
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
                      EnhancedToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
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
     * Register an existing session created by another service
     * This ensures session state consistency across services
     */
    public void registerExistingSession(String sessionId) {
        log.info("Registering existing session in EnhancedChatService: {}", sessionId);
        
        if (sessions.containsKey(sessionId) || agentStates.containsKey(sessionId)) {
            log.info("Session already registered: {}", sessionId);
            return;
        }
        
        // Create session workspace directory
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        try {
            Files.createDirectories(Path.of(workspacePath));
            log.info("Created workspace directory for existing session {}: {}", workspacePath, sessionId);
        } catch (Exception e) {
            log.error("Error creating workspace directory for existing session {}: {}", sessionId, e.getMessage(), e);
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
        
        log.info("Successfully registered existing session: {}", sessionId);
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
        
        // Track conversation in agent state memory
        List<String> conversationHistory = (List<String>) agentState.getMemory()
                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
        conversationHistory.add("User: " + message);
        
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
        
        if (agentState.isWaitingForUserInput() && agentState.getMemory().containsKey("pendingQuestion")) {
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
                // Modify approach based on user feedback
                return modifyApproachAndContinue(sessionId, context, agentState, message);
            
            case ANSWER_QUESTION:
                // Process user answer to a pending question
                return processUserAnswer(sessionId, context, agentState, message);
            
            case CHECK_STATUS:
                // Provide status update
                return provideStatusUpdate(sessionId, agentState);
            
            case GENERAL_CONVERSATION:
            default:
                // Handle as general conversation
                return handleConversationalResponse(sessionId, context, agentState, message);
        }
    }
    
    /**
     * Generate a progress summary for the current state
     */
    private String generateProgressSummary(AgentState agentState) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Current Objective: ").append(agentState.getCurrentObjective()).append("\n\n");
        summary.append("Current Phase: ").append(agentState.getCurrentPhase()).append("\n");
        summary.append("Progress: ").append(agentState.getProgress()).append("%\n\n");
        
        if (!agentState.getCompletedTasks().isEmpty()) {
            summary.append("Completed Tasks:\n");
            for (String task : agentState.getCompletedTasks()) {
                summary.append("✅ ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        if (!agentState.getPendingTasks().isEmpty()) {
            summary.append("Pending Tasks:\n");
            for (String task : agentState.getPendingTasks()) {
                summary.append("⏳ ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        if (agentState.getLastAction() != null) {
            summary.append("Last Action: ").append(agentState.getLastAction()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Provide explanation based on user request
     */
    private Flux<ChatResponse> provideExplanation(String sessionId, ChatContext context, AgentState agentState, String message) {
        log.info("Providing explanation for session {}: {}", sessionId, message);
        
        // Generate explanation based on current state
        String explanation = "Here's an explanation of what I'm doing:\n\n";
        explanation += "Current Objective: " + agentState.getCurrentObjective() + "\n\n";
        explanation += "Current Phase: " + agentState.getCurrentPhase() + "\n";
        explanation += "Progress: " + agentState.getProgress() + "%\n\n";
        
        if (agentState.getLastAction() != null) {
            explanation += "Last Action: " + agentState.getLastAction() + "\n\n";
        }
        
        explanation += "My approach is to break down the task into smaller steps, execute them sequentially, and adapt based on the results. ";
        explanation += "I'm using a variety of tools to accomplish this, including file operations, code generation, and execution.\n\n";
        
        if (!agentState.getCompletedTasks().isEmpty()) {
            explanation += "So far, I've completed:\n";
            for (String task : agentState.getCompletedTasks()) {
                explanation += "- " + task + "\n";
            }
            explanation += "\n";
        }
        
        if (!agentState.getPendingTasks().isEmpty()) {
            explanation += "Next, I plan to:\n";
            for (String task : agentState.getPendingTasks()) {
                explanation += "- " + task + "\n";
            }
        }
        
        // Add explanation to context
        context.getMessages().add(Message.builder()
                .role("assistant")
                .content(explanation)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation in agent state memory
        List<String> conversationHistory = (List<String>) agentState.getMemory()
                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
        conversationHistory.add("Assistant: " + explanation);
        
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(explanation)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Process user answer to a pending question
     */
    private Flux<ChatResponse> processUserAnswer(String sessionId, ChatContext context, AgentState agentState, String message) {
        log.info("Processing user answer for session {}: {}", sessionId, message);
        
        // Get the pending question
        String pendingQuestion = (String) agentState.getMemory().get("pendingQuestion");
        agentState.getMemory().remove("pendingQuestion");
        
        // Store the answer in memory
        agentState.getMemory().put("userAnswer", message);
        
        // Acknowledge the answer
        String acknowledgment = "Thank you for your answer. I'll continue with the task based on your input.";
        
        // Add assistant message to context
        context.getMessages().add(Message.builder()
                .role("assistant")
                .content(acknowledgment)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation in agent state memory
        List<String> conversationHistory = (List<String>) agentState.getMemory()
                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
        conversationHistory.add("Assistant: " + acknowledgment);
        
        // Resume autonomous execution
        agentState.setShouldContinue(true);
        agentState.setMode(ConversationMode.AUTONOMOUS);
        agentState.setWaitingForUserInput(false);
        
        return Flux.concat(
                Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(acknowledgment)
                        .timestamp(Instant.now())
                        .build()),
                executeAutonomousAgentLoop(sessionId, context, agentState)
        );
    }
    
    /**
     * Modify approach based on user feedback
     */
    private Flux<ChatResponse> modifyApproachAndContinue(String sessionId, ChatContext context, AgentState agentState, String message) {
        log.info("Modifying approach for session {}: {}", sessionId, message);
        
        // Update agent state based on user feedback
        // Store feedback in memory map
        agentState.getMemory().put("userFeedback", message);
        
        // Acknowledge the modification
        String acknowledgment = "I'll adjust my approach based on your feedback: \"" + message + "\". Let me continue with the updated approach.";
        
        // Add assistant message to context
        context.getMessages().add(Message.builder()
                .role("assistant")
                .content(acknowledgment)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation in agent state memory
        List<String> conversationHistory = (List<String>) agentState.getMemory()
                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
        conversationHistory.add("Assistant: " + acknowledgment);
        
        // Resume autonomous execution with modified approach
        agentState.setShouldContinue(true);
        agentState.setMode(ConversationMode.AUTONOMOUS);
        
        return Flux.concat(
                Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(acknowledgment)
                        .timestamp(Instant.now())
                        .build()),
                executeAutonomousAgentLoop(sessionId, context, agentState)
        );
    }
    
    /**
     * Provide status update without interrupting execution
     */
    private Flux<ChatResponse> provideStatusUpdate(String sessionId, AgentState agentState) {
        log.info("Providing status update for session {}", sessionId);
        
        String statusUpdate = generateProgressSummary(agentState);
        
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(statusUpdate)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Handle conversational response in non-autonomous mode
     */
    private Flux<ChatResponse> handleConversationalResponse(String sessionId, ChatContext context, AgentState agentState, String message) {
        log.info("Handling conversational response for session {}", sessionId);
        
        // Generate LLM response
        return generateLLMResponse(sessionId, context, agentState, message);
    }
    
    /**
     * Generate LLM response for conversational mode
     */
    private Flux<ChatResponse> generateLLMResponse(String sessionId, ChatContext context, AgentState agentState, String message) {
        log.info("Generating LLM response for session {}", sessionId);
        
        // Create a copy of the context for the LLM request
        ChatContext requestContext = createDefensiveCopy(context);
        
        // Get LLM response - convert Mono to Flux
        return llmProvider.generateResponse(message, requestContext)
                .map(response -> {
                    // Add assistant message to context
                    context.getMessages().add(Message.builder()
                            .role("assistant")
                            .content(response)
                            .timestamp(Instant.now())
                            .build());
                    
                    // Track conversation in agent state memory
                    List<String> conversationHistory = (List<String>) agentState.getMemory()
                            .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
                    conversationHistory.add("Assistant: " + response);
                    
                    return ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(response)
                            .timestamp(Instant.now())
                            .build();
                })
                .flux(); // Convert Mono<ChatResponse> to Flux<ChatResponse>
    }
    
    /**
     * Execute the autonomous agent loop for continuous operation
     */
    private Flux<ChatResponse> executeAutonomousAgentLoop(String sessionId, ChatContext context, AgentState agentState) {
        log.info("Starting autonomous agent loop for session {}", sessionId);
        
        // Create a sink for streaming responses
        Sinks.Many<ChatResponse> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        
        // Start the autonomous loop in a separate thread
        Thread autonomousThread = new Thread(() -> {
            try {
                executeAutonomousLoop(sessionId, context, agentState, sink);
            } catch (Exception e) {
                log.error("Error in autonomous loop for session {}: {}", sessionId, e.getMessage(), e);
                sink.tryEmitError(e);
            } finally {
                sink.tryEmitComplete();
            }
        });
        
        autonomousThread.setName("autonomous-" + sessionId);
        autonomousThread.start();
        
        return sink.asFlux();
    }
    
    /**
     * Execute the autonomous loop with ReAct paradigm
     */
    private void executeAutonomousLoop(String sessionId, ChatContext context, AgentState agentState, Sinks.Many<ChatResponse> sink) {
        log.info("Executing autonomous loop for session {}", sessionId);
        
        AtomicInteger iterationCount = new AtomicInteger(agentState.getIterationCount());
        
        while (agentState.isShouldContinue() && iterationCount.get() < MAX_AUTONOMOUS_ITERATIONS) {
            // Check if execution is paused
            if (!agentState.isShouldContinue()) {
                log.info("Autonomous execution paused for session {}", sessionId);
                break;
            }
            
            // Update iteration count
            agentState.setIterationCount(iterationCount.incrementAndGet());
            log.info("Autonomous iteration {} for session {}", iterationCount.get(), sessionId);
            
            // Generate next step with ReAct paradigm
            try {
                // Create a copy of the context for the LLM request
                ChatContext requestContext = createDefensiveCopy(context);
                
                // Add ReAct prompt
                String reactPrompt = agentPromptService.generateReActPrompt(agentState);
                
                // Stream LLM response with real-time processing
                final StringBuilder responseBuilder = new StringBuilder();
                final AtomicBoolean hasToolUse = new AtomicBoolean(false);
                
                llmProvider.streamResponse(reactPrompt, requestContext)
                    .doOnNext(chunk -> {
                        // Append chunk to response builder
                        responseBuilder.append(chunk);
                        
                        // Check for tool use pattern
                        if (chunk.contains("<tool_use>") || chunk.contains("</tool_use>")) {
                            hasToolUse.set(true);
                        } else {
                            // Stream non-tool chunks to user in real-time
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
                        log.info("Completed LLM response for iteration {}", iterationCount.get());
                        
                        // Add assistant message to context
                        context.getMessages().add(Message.builder()
                                .role("assistant")
                                .content(completeResponse)
                                .timestamp(Instant.now())
                                .build());
                        
                        // Track conversation in agent state memory
                        List<String> conversationHistory = (List<String>) agentState.getMemory()
                                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
                        conversationHistory.add("Assistant: " + completeResponse);
                        
                        // Process the response
                        processAutonomousResponse(sessionId, context, agentState, completeResponse, sink);
                        
                        // Check if task is complete
                        if (isTaskComplete(completeResponse)) {
                            log.info("Task complete for session {}", sessionId);
                            agentState.setShouldContinue(false);
                            return;
                        }
                        
                        // Check if user input is needed
                        if (needsUserInput(completeResponse)) {
                            log.info("User input needed for session {}", sessionId);
                            agentState.setWaitingForUserInput(true);
                            agentState.setShouldContinue(false);
                            
                            // Extract question for future reference
                            String question = extractQuestion(completeResponse);
                            agentState.getMemory().put("pendingQuestion", question);
                            
                            return;
                        }
                        
                        // Continue to next iteration if execution should continue
                        if (agentState.isShouldContinue()) {
                            // Small delay to prevent tight loops
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            // Continue to next iteration
                            executeAutonomousLoop(sessionId, context, agentState, sink);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Error in LLM response for session {}: {}", sessionId, error.getMessage(), error);
                        
                        // Emit error to user
                        sink.tryEmitNext(ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message("I encountered an error: " + error.getMessage() + "\nI'll try a different approach.")
                                .timestamp(Instant.now())
                                .build());
                        
                        // Continue to next iteration with error recovery
                        agentState.setLastAction("Recovered from error: " + error.getMessage());
                        
                        // Small delay to prevent tight loops
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Continue to next iteration
                        executeAutonomousLoop(sessionId, context, agentState, sink);
                    })
                    .subscribe();
                
                // Wait for the streaming to complete before continuing
                // This is necessary to prevent multiple iterations from running concurrently
                return;
                
            } catch (Exception e) {
                log.error("Error in autonomous iteration for session {}: {}", sessionId, e.getMessage(), e);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + e.getMessage() + "\nI'll try a different approach.")
                        .timestamp(Instant.now())
                        .build());
                
                // Continue to next iteration with error recovery
                agentState.setLastAction("Recovered from error: " + e.getMessage());
                
                // Small delay to prevent tight loops
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Check if max iterations reached
        if (iterationCount.get() >= MAX_AUTONOMOUS_ITERATIONS) {
            log.warn("Max iterations reached for session {}", sessionId);
            
            // Emit warning to user
            sink.tryEmitNext(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("I've reached the maximum number of iterations. Here's what I've accomplished so far:\n\n" + 
                            generateProgressSummary(agentState) + 
                            "\n\nWould you like me to continue?")
                    .timestamp(Instant.now())
                    .build());
            
            // Pause execution
            agentState.setShouldContinue(false);
        }
    }
    
    /**
     * Process autonomous response with ReAct paradigm
     */
    private void processAutonomousResponse(String sessionId, ChatContext context, AgentState agentState, 
                                          String llmResponse, Sinks.Many<ChatResponse> sink) {
        log.info("Processing autonomous response for session {}", sessionId);
        
        // Extract tool calls
        List<String> toolCalls = extractToolCalls(llmResponse);
        
        if (toolCalls.isEmpty()) {
            // No tool calls, treat as regular response
            log.info("No tool calls found in response for session {}", sessionId);
            
            // Add assistant message to context
            context.getMessages().add(Message.builder()
                    .role("assistant")
                    .content(llmResponse)
                    .timestamp(Instant.now())
                    .build());
            
            // Track conversation in agent state memory
            List<String> conversationHistory = (List<String>) agentState.getMemory()
                    .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
            conversationHistory.add("Assistant: " + llmResponse);
            
            // Emit response to user
            sink.tryEmitNext(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message(llmResponse)
                    .timestamp(Instant.now())
                    .build());
            
            // Extract and process events
            processEvents(sessionId, llmResponse, agentState);
            
            return;
        }
        
        // Process each tool call
        for (String toolCall : toolCalls) {
            try {
                // Parse tool call
                ToolCall parsedToolCall = objectMapper.readValue(toolCall, ToolCall.class);
                
                // Add assistant message with tool call to context
                context.getMessages().add(Message.builder()
                        .role("assistant")
                        .toolCall(parsedToolCall)
                        .timestamp(Instant.now())
                        .build());
                
                // Track conversation in agent state memory
                List<String> conversationHistory = (List<String>) agentState.getMemory()
                        .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
                conversationHistory.add("Assistant: [Tool Call] " + parsedToolCall.getName());
                
                // Execute tool
                Tool tool = toolRegistry.getTool(parsedToolCall.getName());
                if (tool == null) {
                    throw new IllegalArgumentException("Tool not found: " + parsedToolCall.getName());
                }
                
                // Execute the tool - handle reactively for compatibility
                // Convert Flux<ToolOutput> to ToolOutput by blocking on the first element
                ToolOutput toolOutput = tool.execute(parsedToolCall.getArguments())
                    .blockFirst(); // Block and get the first result from the Flux
                
                // Add tool message to context
                context.getMessages().add(Message.builder()
                        .role("tool")
                        .toolCallId(parsedToolCall.getId())
                        .content(toolOutput.toString())
                        .timestamp(Instant.now())
                        .build());
                
                // Track conversation in agent state memory
                conversationHistory.add("Tool: " + toolOutput.toString());
                
                // Emit tool output to user
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("tool")
                        .message(toolOutput.toString())
                        .timestamp(Instant.now())
                        .build());
            } catch (Exception e) {
                log.error("Error processing tool call for session {}: {}", sessionId, e.getMessage(), e);
                
                // Add error message to context
                String errorMessage = "Error executing tool: " + e.getMessage();
                context.getMessages().add(Message.builder()
                        .role("tool")
                        .content(errorMessage)
                        .timestamp(Instant.now())
                        .build());
                
                // Track conversation in agent state memory
                List<String> conversationHistory = (List<String>) agentState.getMemory()
                        .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
                conversationHistory.add("Tool Error: " + errorMessage);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("tool")
                        .message(errorMessage)
                        .timestamp(Instant.now())
                        .build());
            }
        }
    }
    
    /**
     * Extract tool calls from LLM response
     */
    private List<String> extractToolCalls(String response) {
        List<String> toolCalls = new ArrayList<>();
        
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        while (matcher.find()) {
            String toolCall = matcher.group(1);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        
        return toolCalls;
    }
    
    /**
     * Process events from LLM response
     */
    private void processEvents(String sessionId, String response, AgentState agentState) {
        Matcher matcher = EVENT_PATTERN.matcher(response);
        while (matcher.find()) {
            String eventType = matcher.group(1);
            String eventData = matcher.group(2);
            
            log.info("Processing event for session {}: {} - {}", sessionId, eventType, eventData);
            
            switch (eventType) {
                case "TASK_COMPLETE":
                    agentState.getCompletedTasks().add(eventData);
                    break;
                    
                case "TASK_PENDING":
                    agentState.getPendingTasks().add(eventData);
                    break;
                    
                case "PHASE_TRANSITION":
                    try {
                        DevelopmentPhase newPhase = DevelopmentPhase.valueOf(eventData);
                        DevelopmentPhase oldPhase = agentState.getCurrentPhase();
                        agentState.setCurrentPhase(newPhase);
                        
                        // Broadcast phase transition
                        Map<String, Object> phaseData = new HashMap<>();
                        phaseData.put("sessionId", sessionId);
                        phaseData.put("fromPhase", oldPhase.name());
                        phaseData.put("toPhase", newPhase.name());
                        phaseData.put("timestamp", Instant.now().toString());
                        webSocketHandler.broadcastPhaseTransition(phaseData);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid phase: {}", eventData);
                    }
                    break;
                    
                case "PROGRESS":
                    try {
                        int progress = Integer.parseInt(eventData);
                        agentState.setProgress(progress);
                    } catch (NumberFormatException e) {
                        log.error("Invalid progress value: {}", eventData);
                    }
                    break;
                    
                case "ACTION":
                    agentState.setLastAction(eventData);
                    break;
                    
                default:
                    log.warn("Unknown event type: {}", eventType);
                    break;
            }
        }
    }
    
    /**
     * Check if task is complete based on response
     */
    private boolean isTaskComplete(String response) {
        return COMPLETION_PATTERN.matcher(response).find();
    }
    
    /**
     * Check if user input is needed based on response
     */
    private boolean needsUserInput(String response) {
        return QUESTION_PATTERN.matcher(response).find();
    }
    
    /**
     * Extract question from response
     */
    private String extractQuestion(String response) {
        // Simple extraction - get the last sentence with a question mark
        String[] sentences = response.split("[.!?]");
        for (int i = sentences.length - 1; i >= 0; i--) {
            if (sentences[i].contains("?")) {
                return sentences[i].trim() + "?";
            }
        }
        
        // Fallback - return the last sentence
        return sentences.length > 0 ? sentences[sentences.length - 1].trim() : "What would you like me to do?";
    }
    
    /**
     * User intent enum for intent classification
     */
    private enum UserIntent {
        NEW_TASK,
        PAUSE_EXECUTION,
        CONTINUE_EXECUTION,
        REQUEST_EXPLANATION,
        MODIFY_APPROACH,
        ANSWER_QUESTION,
        CHECK_STATUS,
        GENERAL_CONVERSATION
    }
}
