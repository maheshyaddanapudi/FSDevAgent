package com.ai.developer.service;

import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.llm.*;
import com.ai.developer.model.*;
import com.ai.developer.service.AgentPromptService.DevelopmentPhase;
import com.ai.developer.service.AgentPromptService.TaskMemory;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ChatService {
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolOutputWebSocketHandler webSocketHandler;
    private final AgentPromptService agentPromptService;
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<ChatResponse>> sessionSinks = new ConcurrentHashMap<>();
    
    // Patterns for parsing responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("(?i)(task complete|objectives? (?:met|achieved|completed)|all (?:done|finished)|nothing (?:more|else) to do)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("(?i)(\\?|would you like|should i|do you want|can you clarify|need more information|what about)");
    
    public ChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      ToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
        log.info("ChatService initialized with autonomous agent capabilities and multi-turn support");
    }
    
    /**
     * Enhanced agent state with conversation tracking
     */
    private static class AgentState {
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
            memory.objective = currentObjective;
            memory.progressPercentage = calculateProgress();
            memory.completedSteps = new ArrayList<>(completedTasks);
            memory.pendingTasks = new ArrayList<>(pendingTasks);
            memory.learnedPatterns = new ArrayList<>(learnedPatterns.values());
            return memory;
        }
        
        private int calculateProgress() {
            if (completedTasks.isEmpty() && pendingTasks.isEmpty()) return 0;
            int total = completedTasks.size() + pendingTasks.size();
            return (completedTasks.size() * 100) / total;
        }
    }
    
    /**
     * Conversation modes for flexible interaction
     */
    private enum ConversationMode {
        AUTONOMOUS,      // Full autonomous operation
        INTERACTIVE,     // Asks for confirmation at key points
        CONVERSATIONAL,  // Traditional back-and-forth
        GUIDED          // User guides each step
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
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Add user message to context
        context.getMessages().add(Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation
        agentState.conversationHistory.add("User: " + message);
        
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
        
        if (agentState.waitingForUserInput && agentState.pendingQuestion != null) {
            return UserIntent.ANSWER_QUESTION;
        }
        
        if (lowerMessage.contains("status") || lowerMessage.contains("progress")) {
            return UserIntent.CHECK_STATUS;
        }
        
        // Check if this is a new task or continuation
        if (agentState.currentObjective == null || agentState.iterationCount == 0) {
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
        
        return switch (intent) {
            case NEW_TASK -> {
                // Start autonomous execution for new task
                agentState.currentObjective = message;
                agentState.shouldContinue = true;
                agentState.iterationCount = 0;
                agentState.mode = ConversationMode.AUTONOMOUS;
                yield executeAgentLoop(sessionId, context, agentState);
            }
            
            case PAUSE_EXECUTION -> {
                // Pause autonomous execution
                agentState.shouldContinue = false;
                agentState.mode = ConversationMode.CONVERSATIONAL;
                yield Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I've paused the execution. Here's what I've completed so far:\n\n" + 
                                generateProgressSummary(agentState) + 
                                "\n\nWhat would you like me to do next?")
                        .timestamp(Instant.now())
                        .build());
            }
            
            case CONTINUE_EXECUTION -> {
                // Resume autonomous execution
                agentState.shouldContinue = true;
                agentState.mode = ConversationMode.AUTONOMOUS;
                agentState.waitingForUserInput = false;
                yield executeAgentLoop(sessionId, context, agentState);
            }
            
            case REQUEST_EXPLANATION -> {
                // Provide explanation without stopping execution
                yield provideExplanation(sessionId, context, agentState, message);
            }
            
            case MODIFY_APPROACH -> {
                // Modify approach and continue
                yield modifyApproachAndContinue(sessionId, context, agentState, message);
            }
            
            case ANSWER_QUESTION -> {
                // Process answer to agent's question
                agentState.waitingForUserInput = false;
                agentState.pendingQuestion = null;
                yield executeAgentLoop(sessionId, context, agentState);
            }
            
            case CHECK_STATUS -> {
                // Provide status without interrupting
                yield provideStatusUpdate(sessionId, agentState);
            }
            
            case GENERAL_CONVERSATION -> {
                // Handle as regular conversation
                yield handleConversationalResponse(sessionId, context, agentState, message);
            }
        };
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
        
        agentState.iterationCount++;
        log.info("Agent iteration {} for session {} in {} mode", 
                agentState.iterationCount, sessionId, agentState.mode);
        
        // Check iteration limit
        if (agentState.iterationCount > 50) {
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
        if (agentState.iterationCount > 1 && !agentState.waitingForUserInput) {
            String continuationPrompt = agentPromptService.generateContinuationPrompt(
                    agentState.lastAction, 
                    "Iteration " + agentState.iterationCount
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
        
        llmProvider.streamResponse("Continue working on: " + agentState.currentObjective, context)
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
                log.info("Agent iteration {} complete for session {}", agentState.iterationCount, sessionId);
                
                // Update context
                updateAssistantMessage(context, completeResponse);
                agentState.conversationHistory.add("Assistant: " + completeResponse);
                
                // Check if agent is asking a question
                if (hasQuestion.get() && agentState.mode != ConversationMode.AUTONOMOUS) {
                    agentState.waitingForUserInput = true;
                    agentState.pendingQuestion = completeResponse;
                    log.info("Agent waiting for user input after question");
                    return;
                }
                
                // Check for tool use
                Matcher toolMatcher = TOOL_USE_PATTERN.matcher(completeResponse);
                if (toolMatcher.find()) {
                    processToolUseAndContinue(sessionId, context, agentState, completeResponse, sink);
                } else if (isTaskComplete(completeResponse)) {
                    // Task complete
                    agentState.shouldContinue = false;
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
                } else if (agentState.shouldContinue && !agentState.waitingForUserInput) {
                    // Continue to next iteration
                    agentState.lastAction = extractActionSummary(completeResponse);
                    
                    // Small delay between iterations
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    executeAgentIteration(sessionId, context, agentState, sink);
                }
            })
            .doOnError(error -> {
                log.error("Error in agent iteration: {}", error.getMessage());
                sink.tryEmitNext(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + error.getMessage() + "\n\nWould you like me to try a different approach?")
                        .timestamp(Instant.now())
                        .build());
            })
            .subscribe();
    }
    
    /**
     * Process tool use and continue with streaming
     */
    private void processToolUseAndContinue(String sessionId, ChatContext context, AgentState agentState,
                                         String response, Sinks.Many<ChatResponse> sink) {
        
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                String toolUseJson = matcher.group(1);
                ToolUseBlock toolUseBlock = objectMapper.readValue(toolUseJson, ToolUseBlock.class);
                
                log.info("Agent using tool {} in iteration {}", toolUseBlock.getName(), agentState.iterationCount);
                
                // Execute tool
                handleToolUse(sessionId, toolUseBlock)
                    .subscribe(
                        toolResult -> {
                            // STREAM TOOL RESULT TO UI
                            sink.tryEmitNext(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("tool")
                                    .message("\n\n📊 **Tool Result** (`" + toolUseBlock.getName() + "`):\n```\n" + toolResult + "\n```\n")
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Add to context
                            context.getMessages().add(Message.builder()
                                    .role("tool")
                                    .content(toolResult)
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Update state
                            agentState.lastAction = "Used tool: " + toolUseBlock.getName();
                            agentState.completedTasks.add(agentState.lastAction);
                            
                            // Continue iteration
                            if (agentState.shouldContinue && !agentState.waitingForUserInput) {
                                executeAgentIteration(sessionId, context, agentState, sink);
                            }
                        },
                        error -> {
                            log.error("Tool execution error: {}", error.getMessage());
                            
                            // Stream error to UI
                            sink.tryEmitNext(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .message("⚠️ Tool execution failed: " + error.getMessage() + "\nTrying alternative approach...")
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Continue despite error
                            executeAgentIteration(sessionId, context, agentState, sink);
                        }
                    );
            } catch (Exception e) {
                log.error("Error processing tool use: {}", e.getMessage());
                executeAgentIteration(sessionId, context, agentState, sink);
            }
        }
    }
    
    /**
     * Provide explanation without stopping execution
     */
    private Flux<ChatResponse> provideExplanation(String sessionId, ChatContext context, 
                                                  AgentState agentState, String question) {
        return Flux.create(sink -> {
            // Add explanation request to context
            context.getMessages().add(Message.builder()
                    .role("system")
                    .content("User is asking for explanation: " + question + 
                            "\nProvide a clear explanation while continuing your work.")
                    .timestamp(Instant.now())
                    .build());
            
            // Stream explanation
            llmProvider.streamResponse(question, context)
                .doOnNext(chunk -> {
                    sink.next(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(chunk)
                            .timestamp(Instant.now())
                            .build());
                })
                .doOnComplete(() -> {
                    // After explanation, continue with autonomous execution if active
                    if (agentState.shouldContinue) {
                        sink.next(ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message("\n\nNow continuing with the implementation...\n")
                                .timestamp(Instant.now())
                                .build());
                        executeAgentLoop(sessionId, context, agentState)
                            .subscribe(sink::next, sink::error, sink::complete);
                    } else {
                        sink.complete();
                    }
                })
                .subscribe();
        });
    }
    
    /**
     * Handle conversational responses
     */
    private Flux<ChatResponse> handleConversationalResponse(String sessionId, ChatContext context,
                                                           AgentState agentState, String message) {
        return Flux.create(sink -> {
            llmProvider.streamResponse(message, context)
                .doOnNext(chunk -> {
                    sink.next(ChatResponse.builder()
                            .sessionId(sessionId)
                            .role("assistant")
                            .message(chunk)
                            .timestamp(Instant.now())
                            .build());
                })
                .doOnComplete(() -> {
                    sink.complete();
                })
                .subscribe();
        });
    }
    
    /**
     * Generate progress summary
     */
    private String generateProgressSummary(AgentState agentState) {
        StringBuilder summary = new StringBuilder();
        summary.append("**Progress: ").append(agentState.calculateProgress()).append("%**\n\n");
        
        if (!agentState.completedTasks.isEmpty()) {
            summary.append("**Completed:**\n");
            agentState.completedTasks.forEach(task -> 
                summary.append("- ✓ ").append(task).append("\n"));
        }
        
        if (!agentState.pendingTasks.isEmpty()) {
            summary.append("\n**Pending:**\n");
            agentState.pendingTasks.forEach(task -> 
                summary.append("- ○ ").append(task).append("\n"));
        }
        
        return summary.toString();
    }
    
    /**
     * Provide status update
     */
    private Flux<ChatResponse> provideStatusUpdate(String sessionId, AgentState agentState) {
        String status = String.format(
            "## Current Status\n\n" +
            "**Objective:** %s\n" +
            "**Progress:** %d%%\n" +
            "**Current Phase:** %s\n" +
            "**Iteration:** %d\n" +
            "**Mode:** %s\n\n" +
            "%s",
            agentState.currentObjective,
            agentState.calculateProgress(),
            agentState.currentPhase,
            agentState.iterationCount,
            agentState.mode,
            generateProgressSummary(agentState)
        );
        
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(status)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Modify approach based on user input
     */
    private Flux<ChatResponse> modifyApproachAndContinue(String sessionId, ChatContext context,
                                                        AgentState agentState, String modification) {
        // Add modification instruction
        context.getMessages().add(Message.builder()
                .role("system")
                .content("User has requested modification: " + modification + 
                        "\nAdjust your approach accordingly and continue.")
                .timestamp(Instant.now())
                .build());
        
        // Acknowledge and continue
        return Flux.concat(
            Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("I understand. I'll modify my approach to: " + modification + 
                            "\n\nAdjusting and continuing...")
                    .timestamp(Instant.now())
                    .build()),
            executeAgentLoop(sessionId, context, agentState)
        );
    }
    
    // Helper enums and classes
    
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
    
    // Keep all other existing methods unchanged...
    
    private boolean isTaskComplete(String response) {
        Matcher matcher = COMPLETION_PATTERN.matcher(response);
        return matcher.find();
    }
    
    private String extractActionSummary(String response) {
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                return line.trim().length() > 100 ? 
                       line.trim().substring(0, 100) + "..." : 
                       line.trim();
            }
        }
        return "Continued analysis";
    }
    
    private void updateAssistantMessage(ChatContext context, String content) {
        context.getMessages().add(Message.builder()
                .role("assistant")
                .content(content)
                .timestamp(Instant.now())
                .build());
    }
    
    private Mono<String> handleToolUse(String sessionId, ToolUseBlock toolUseBlock) {
        try {
            Map<String, Object> inputMap = new HashMap<>();
            Object input = toolUseBlock.getInput();
            
            if (input instanceof String) {
                try {
                    inputMap = objectMapper.readValue((String)input, Map.class);
                } catch (Exception e) {
                    inputMap.put("input", input);
                }
            } else if (input instanceof Map) {
                inputMap = (Map<String, Object>) input;
            } else if (input != null) {
                inputMap.put("input", input.toString());
            }
            
            return executeToolCall(sessionId, toolUseBlock.getName(), inputMap)
                    .collectList()
                    .flatMap(outputs -> {
                        StringBuilder result = new StringBuilder();
                        if (outputs != null) {
                            for (com.ai.developer.tools.ToolOutput output : outputs) {
                                result.append(output.getContent()).append("\n");
                            }
                        }
                        return Mono.just(result.toString());
                    });
        } catch (Exception e) {
            return Mono.just("Error executing tool: " + e.getMessage());
        }
    }
    
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {}", toolName, sessionId);
        
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        return toolRegistry.executeTool(toolName, arguments)
                .doOnNext(output -> {
                    try {
                        Map<String, Object> toolOutput = new HashMap<>();
                        toolOutput.put("sessionId", sessionId);
                        toolOutput.put("toolName", toolName);
                        toolOutput.put("output", output);
                        toolOutput.put("timestamp", Instant.now());
                        
                        webSocketHandler.broadcastToolOutput(toolOutput);
                    } catch (Exception e) {
                        log.error("Error broadcasting tool output: {}", e.getMessage());
                    }
                });
    }
    
    // Session management methods
    
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Creating new autonomous agent session with multi-turn support: {}", sessionId);
        
        ProjectContext projectContext = ProjectContext.builder()
                .projectPath(".")
                .projectType("fullstack")
                .buildTool("maven")
                .frameworkType("spring-react")
                .build();
        
        AgentState agentState = new AgentState();
        agentState.projectContext = projectContext;
        agentStates.put(sessionId, agentState);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        
        ChatContext context = new ChatContext();
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        context.getMessages().add(Message.builder()
                .role("system")
                .content(systemPrompt)
                .timestamp(Instant.now())
                .build());
        
        sessions.put(sessionId, context);
        
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .build());
    }
    
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        List<ChatResponse> history = new ArrayList<>();
        for (Message message : context.getMessages()) {
            if (!"system".equals(message.getRole())) {
                history.add(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role(message.getRole())
                        .message(message.getContent())
                        .timestamp(message.getTimestamp())
                        .build());
            }
        }
        
        return Mono.just(history);
    }
    
    /**
     * Create a defensive copy of a chat context
     */
    private ChatContext createDefensiveCopy(ChatContext context) {
        ChatContext copy = new ChatContext();
        copy.setSystemPrompt(context.getSystemPrompt());
        
        List<Message> messagesCopy = new ArrayList<>();
        for (Message message : context.getMessages()) {
            messagesCopy.add(Message.builder()
                    .role(message.getRole())
                    .content(message.getContent())
                    .toolCallId(message.getToolCallId())
                    .toolCall(message.getToolCall())
                    .timestamp(message.getTimestamp())
                    .build());
        }
        
        copy.setMessages(messagesCopy);
        return copy;
    }
}
