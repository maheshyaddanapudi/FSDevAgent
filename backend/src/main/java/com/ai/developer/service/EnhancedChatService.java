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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Chat Service with multi-turn conversation support and autonomous agent capabilities.
 * This service extends the original ChatService functionality while preserving the working
 * Claude tool call communication.
 */
@Service
@Slf4j
public class EnhancedChatService {
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolOutputWebSocketHandler webSocketHandler;
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
    
    public EnhancedChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      ToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
        log.info("EnhancedChatService initialized with autonomous agent capabilities and multi-turn support");
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
        
        // Create chat context
        ChatContext context = new ChatContext();
        // Updated system prompt with explicit instructions for tool use blocks
        context.setSystemPrompt("You are an AI Developer Agent, designed to help with coding, debugging, and using various development tools. " +
                "Your workspace directory is: " + workspacePath + "\n\n" +
                "IMPORTANT: When using tools, you MUST wrap your tool calls in <tool_use> tags like this:\n" +
                "<tool_use>\n" +
                "{\n" +
                "  \"name\": \"tool_name\",\n" +
                "  \"args\": {\n" +
                "    \"arg1\": \"value1\",\n" +
                "    \"arg2\": \"value2\"\n" +
                "  }\n" +
                "}\n" +
                "</tool_use>\n\n" +
                "Always use this format for tool calls. Available tools: " + String.join(", ", toolRegistry.getToolNames()));
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", workspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setProjectContext(new ProjectContext());
        agentState.getProjectContext().setProjectPath(workspacePath);
        agentState.setMode(ConversationMode.CONVERSATIONAL); // Start in conversational mode
        
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
                return executeAgentLoop(sessionId, context, agentState);
            
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
                return executeAgentLoop(sessionId, context, agentState);
            
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
                return executeAgentLoop(sessionId, context, agentState);
            
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
     * Provide an explanation based on the current state and context
     */
    private Flux<ChatResponse> provideExplanation(String sessionId, ChatContext context, AgentState agentState, String message) {
        // Add explanation prompt to context
        String explanationPrompt = "Please explain " + message + " based on the current project state and your actions so far.";
        
        // Use the LLM to generate an explanation
        return llmProvider.streamResponse(explanationPrompt, context)
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
     * Modify approach based on user feedback and continue execution
     */
    private Flux<ChatResponse> modifyApproachAndContinue(String sessionId, ChatContext context, AgentState agentState, String message) {
        // Add modification prompt to context
        String modificationPrompt = "Based on the user's feedback: \"" + message + "\", adjust your approach and continue working on the objective.";
        
        // Use the LLM to acknowledge the modification
        return llmProvider.streamResponse(modificationPrompt, context)
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
                
                // Continue execution if in autonomous mode
                if (agentState.getMode() == ConversationMode.AUTONOMOUS && agentState.isShouldContinue()) {
                    return Flux.just(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(responseText)
                            .timestamp(Instant.now())
                            .build())
                            .concatWith(executeAgentLoop(sessionId, context, agentState));
                } else {
                    return Flux.just(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(responseText)
                            .timestamp(Instant.now())
                            .build());
                }
            });
    }
    
    /**
     * Provide a status update on the current task
     */
    private Flux<ChatResponse> provideStatusUpdate(String sessionId, AgentState agentState) {
        String statusUpdate = generateProgressSummary(agentState);
        
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message("Here's the current status of your task:\n\n" + statusUpdate)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Handle a general conversational response
     */
    private Flux<ChatResponse> handleConversationalResponse(String sessionId, ChatContext context, AgentState agentState, String message) {
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
                
                // Check for tool use blocks in the response
                List<ToolUseBlock> toolUseBlocks = extractToolUseBlocks(responseText);
                
                if (!toolUseBlocks.isEmpty()) {
                    // Process each tool use block
                    return Flux.fromIterable(toolUseBlocks)
                            .concatMap(toolUseBlock -> processToolUseBlock(sessionId, context, agentState, toolUseBlock))
                            .concatWith(Flux.just(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .message(responseText)
                                    .timestamp(Instant.now())
                                    .build()));
                } else {
                    // No tool use blocks, just return the assistant's response
                    return Flux.just(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(responseText)
                            .timestamp(Instant.now())
                            .build());
                }
            });
    }
    
    /**
     * Execute the autonomous agent loop with streaming support
     */
    private Flux<ChatResponse> executeAgentLoop(String sessionId, ChatContext context, AgentState agentState) {
        // Create a sink for this session if it doesn't exist
        Sinks.Many<ChatResponse> sink = sessionSinks.computeIfAbsent(sessionId, 
            k -> Sinks.many().multicast().onBackpressureBuffer());
        
        // Start the agent loop in a separate thread to allow streaming
        executeAgentIteration(sessionId, context, agentState, sink);
        
        return sink.asFlux();
    }
    
    /**
     * Execute a single iteration of the agent loop with full streaming
     */
    private void executeAgentIteration(String sessionId, ChatContext context, AgentState agentState, 
                                      Sinks.Many<ChatResponse> sink) {
        
        agentState.setIterationCount(agentState.getIterationCount() + 1);
        log.info("Agent iteration {} for session {} in {} mode", 
                agentState.getIterationCount(), sessionId, agentState.getMode());
        
        // Check iteration limit
        if (agentState.getIterationCount() > 50) {
            log.warn("Agent reached maximum iteration limit for session {}", sessionId);
            sink.tryEmitNext(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("I've reached my iteration limit. The task might be too complex or require human intervention.")
                    .timestamp(Instant.now())
                    .build());
            sink.tryEmitComplete();
            return;
        }
        
        // Add continuation prompt for subsequent iterations
        if (agentState.getIterationCount() > 1 && !agentState.isWaitingForUserInput()) {
            String continuationPrompt = agentPromptService.generateContinuationPrompt(
                    agentState.getLastAction(), 
                    "Iteration " + agentState.getIterationCount()
            );
            
            context.getMessages().add(Message.builder()
                    .role("system")
                    .content(continuationPrompt)
                    .timestamp(Instant.now())
                    .build());
        }
        
        // Stream the LLM response
        final StringBuilder responseBuilder = new StringBuilder();
        final AtomicBoolean hasQuestion = new AtomicBoolean(false);
        
        llmProvider.streamResponse("Continue working on: " + agentState.getCurrentObjective(), context)
            .doOnNext(chunk -> {
                responseBuilder.append(chunk);
                
                // STREAM EACH CHUNK TO UI IMMEDIATELY
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(chunk)
                        .timestamp(Instant.now())
                        .build());
                
                // Check if chunk contains a question
                if (QUESTION_PATTERN.matcher(chunk).find()) {
                    hasQuestion.set(true);
                }
            })
            .doOnComplete(() -> {
                String completeResponse = responseBuilder.toString();
                log.info("Agent iteration {} complete for session {}", agentState.getIterationCount(), sessionId);
                
                // Update context
                Message assistantMessage = Message.builder()
                        .role("assistant")
                        .content(completeResponse)
                        .timestamp(Instant.now())
                        .build();
                context.getMessages().add(assistantMessage);
                agentState.getConversationHistory().add("Assistant: " + completeResponse);
                
                // Check if agent is asking a question
                if (hasQuestion.get() && agentState.getMode() != ConversationMode.AUTONOMOUS) {
                    agentState.setWaitingForUserInput(true);
                    agentState.setPendingQuestion(completeResponse);
                    log.info("Agent waiting for user input after question");
                    return;
                }
                
                // Check for tool use blocks
                List<ToolUseBlock> toolUseBlocks = extractToolUseBlocks(completeResponse);
                
                if (!toolUseBlocks.isEmpty()) {
                    processToolUseAndContinue(sessionId, context, agentState, toolUseBlocks, sink);
                } else if (isTaskComplete(completeResponse)) {
                    // Task complete
                    agentState.setShouldContinue(false);
                    sink.tryEmitNext(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message("\n\n✅ Task completed successfully! All objectives have been met.\n\n" +
                                    "Would you like me to:\n" +
                                    "- Explain any part of the implementation?\n" +
                                    "- Make any modifications?\n" +
                                    "- Start a new task?")
                            .timestamp(Instant.now())
                            .build());
                    // Don't complete the sink - keep it open for conversation
                } else if (agentState.isShouldContinue() && !agentState.isWaitingForUserInput()) {
                    // Continue to next iteration
                    agentState.setLastAction(extractActionSummary(completeResponse));
                    
                    // Small delay between iterations
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    executeAgentIteration(sessionId, context, agentState, sink);
                }
            })
            .subscribe();
    }
    
    /**
     * Process tool use blocks and continue agent loop
     */
    private void processToolUseAndContinue(String sessionId, ChatContext context, AgentState agentState, 
                                         List<ToolUseBlock> toolUseBlocks, Sinks.Many<ChatResponse> sink) {
        
        // Process each tool use block sequentially
        Flux.fromIterable(toolUseBlocks)
            .concatMap(toolUseBlock -> processToolUseBlock(sessionId, context, agentState, toolUseBlock))
            .doOnComplete(() -> {
                // Continue agent loop if in autonomous mode
                if (agentState.getMode() == ConversationMode.AUTONOMOUS && agentState.isShouldContinue()) {
                    executeAgentIteration(sessionId, context, agentState, sink);
                }
            })
            .subscribe();
    }
    
    /**
     * Process a single tool use block
     */
    private Flux<ChatResponse> processToolUseBlock(String sessionId, ChatContext context, AgentState agentState, ToolUseBlock toolUseBlock) {
        log.info("Processing tool use block for session {}: {}", sessionId, toolUseBlock);
        
        // Get the tool
        Tool tool = toolRegistry.getTool(toolUseBlock.getName());
        if (tool == null) {
            log.warn("Tool not found: {}", toolUseBlock.getName());
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("tool")
                    .message("Error: Tool not found: " + toolUseBlock.getName())
                    .timestamp(Instant.now())
                    .build());
        }
        
        // Get workspace path from agent state
        String workspacePath = agentState.getProjectContext() != null && agentState.getProjectContext().getProjectPath() != null ?
                agentState.getProjectContext().getProjectPath() :
                DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        
        // Add workspace path to arguments if needed
        Map<String, Object> args = new HashMap<>(toolUseBlock.getArgs());
        if (!args.containsKey("workspacePath")) {
            args.put("workspacePath", workspacePath);
        }
                 // Create tool call response
        ToolCallResponse toolCallResponse = ToolCallResponse.builder()
                .sessionId(sessionId)
                .name(toolUseBlock.getName())
                .arguments(args)
                .build();
        
        // Execute the tool
        return tool.execute(args)
                .doOnNext(output -> {
                    // Stream tool output via WebSocket
                    webSocketHandler.broadcastToolOutput(output);
                })
                .reduceWith(() -> new StringBuilder(), (sb, output) -> sb.append(output.getContent()).append("\n"))
                .flatMapMany(result -> {
                    String resultText = result.toString();
                    
                    // Add tool result to context
                    Message toolResultMessage = Message.builder()
                            .role("assistant")
                            .content("Tool result: " + resultText)
                            .timestamp(Instant.now())
                            .build();
                    context.getMessages().add(toolResultMessage);
                    
                    // Add tool result prompt to context
                    String toolResultPrompt = agentPromptService.generateToolResultPrompt(
                            toolUseBlock.getName(),
                            resultText,
                            !resultText.contains("Error")
                    );
                    
                    Message toolResultPromptMessage = Message.builder()
                            .role("system")
                            .content(toolResultPrompt)
                            .timestamp(Instant.now())
                            .build();
                    context.getMessages().add(toolResultPromptMessage);
                    
                    // Return tool result response
                    return Flux.just(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("tool")
                            .message(resultText)
                            .toolCall(toolCallResponse)
                            .timestamp(Instant.now())
                            .build());
                });
    }
    
    /**
     * Extract tool use blocks from a response
     */
    private List<ToolUseBlock> extractToolUseBlocks(String response) {
        List<ToolUseBlock> toolUseBlocks = new ArrayList<>();
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        
        while (matcher.find()) {
            String toolUseJson = matcher.group(1);
            if (toolUseJson == null) {
                // This might be a content block format
                toolUseJson = matcher.group(0);
            }
            
            try {
                ToolUseBlock toolUseBlock = parseToolUseBlock(toolUseJson);
                if (toolUseBlock != null) {
                    toolUseBlocks.add(toolUseBlock);
                }
            } catch (Exception e) {
                log.error("Error parsing tool use block: {}", e.getMessage(), e);
            }
        }
        
        return toolUseBlocks;
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
                args = (Map<String, Object>) toolUseMap.get("input");
            }
            
            // Initialize empty map if args is null
            if (args == null) {
                args = new HashMap<>();
            }
            
            ToolUseBlock block = new ToolUseBlock();
            block.setName(name);
            block.setArgs(args);
            return block;
        } catch (Exception e) {
            log.warn("Failed to parse direct tool use block: {}", e.getMessage());
            
            try {
                // Try to extract from content block format
                Map<String, Object> contentBlock = objectMapper.readValue(json, Map.class);
                
                if (contentBlock.containsKey("content") && contentBlock.get("content") instanceof Map) {
                    Map<String, Object> content = (Map<String, Object>) contentBlock.get("content");
                    
                    if (content.containsKey("tool_use")) {
                        Map<String, Object> toolUse = (Map<String, Object>) content.get("tool_use");
                        String name = (String) toolUse.get("name");
                        
                        // Check for arguments field
                        Map<String, Object> args = null;
                        if (toolUse.containsKey("arguments")) {
                            args = (Map<String, Object>) toolUse.get("arguments");
                        } else if (toolUse.containsKey("input")) {
                            args = (Map<String, Object>) toolUse.get("input");
                        }
                        
                        // Initialize empty map if args is null
                        if (args == null) {
                            args = new HashMap<>();
                        }
                        
                        ToolUseBlock block = new ToolUseBlock();
                        block.setName(name);
                        block.setArgs(args);
                        return block;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to parse content block format: {}", ex.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Check if a response indicates task completion
     */
    private boolean isTaskComplete(String response) {
        return COMPLETION_PATTERN.matcher(response).find();
    }
    
    /**
     * Extract a summary of the action taken from a response
     */
    private String extractActionSummary(String response) {
        // Extract the first sentence or first 100 characters
        int endIndex = response.indexOf(". ");
        if (endIndex > 0) {
            return response.substring(0, endIndex + 1);
        } else {
            return response.length() > 100 ? response.substring(0, 100) + "..." : response;
        }
    }
}
