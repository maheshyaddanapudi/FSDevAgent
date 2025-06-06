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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    
    // Pattern to match tool use blocks in LLM responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    
    // Pattern to detect completion signals
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("(?i)(task complete|objectives? (?:met|achieved|completed)|all (?:done|finished)|nothing (?:more|else) to do)");
    
    public ChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      ToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
        log.info("ChatService initialized with autonomous agent capabilities");
        log.info("Available tools: {}", toolRegistry.getToolNames());
    }
    
    /**
     * Agent state for managing continuous operation
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
     * Create a new session with enhanced autonomous agent capabilities
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Creating new autonomous agent session: {}", sessionId);
        
        // Initialize project context (could be enhanced to detect from environment)
        ProjectContext projectContext = ProjectContext.builder()
                .projectPath(".")
                .projectType("fullstack")
                .buildTool("maven")
                .frameworkType("spring-react")
                .build();
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.projectContext = projectContext;
        agentStates.put(sessionId, agentState);
        
        // Generate sophisticated system prompt
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        
        ChatContext context = new ChatContext();
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add the system prompt as the first message
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
    
    /**
     * Process a user message with autonomous agent loop
     */
    public Flux<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for autonomous agent session {}: {}", sessionId, message);
        
        ChatContext context = sessions.get(sessionId);
        AgentState agentState = agentStates.get(sessionId);
        
        if (context == null || agentState == null) {
            log.warn("Session or agent state not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Update agent objective from user message
        agentState.currentObjective = message;
        agentState.shouldContinue = true;
        agentState.iterationCount = 0;
        
        // Add user message to context
        context.getMessages().add(Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build());
        
        // Start the autonomous agent loop
        return executeAgentLoop(sessionId, context, agentState);
    }
    
    /**
     * Execute the autonomous agent loop
     */
    private Flux<ChatResponse> executeAgentLoop(String sessionId, ChatContext context, AgentState agentState) {
        return Flux.create(sink -> {
            executeAgentIteration(sessionId, context, agentState, sink);
        });
    }
    
    /**
     * Execute a single iteration of the agent loop
     */
    private void executeAgentIteration(String sessionId, ChatContext context, AgentState agentState, 
                                      reactor.core.publisher.FluxSink<ChatResponse> sink) {
        
        agentState.iterationCount++;
        log.info("Agent iteration {} for session {}", agentState.iterationCount, sessionId);
        
        // Safety check to prevent infinite loops
        if (agentState.iterationCount > 50) {
            log.warn("Agent reached maximum iteration limit for session {}", sessionId);
            sink.next(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("I've reached my iteration limit. The task might be too complex or require human intervention.")
                    .timestamp(Instant.now())
                    .build());
            sink.complete();
            return;
        }
        
        // Generate continuation prompt if this isn't the first iteration
        if (agentState.iterationCount > 1) {
            String continuationPrompt = agentPromptService.generateContinuationPrompt(
                    agentState.lastAction, 
                    "Iteration " + agentState.iterationCount
            );
            
            // Add as a system message to guide the agent
            context.getMessages().add(Message.builder()
                    .role("system")
                    .content(continuationPrompt)
                    .timestamp(Instant.now())
                    .build());
        }
        
        // Add memory context for complex tasks
        if (agentState.iterationCount > 3) {
            String memoryPrompt = agentPromptService.generateMemoryPrompt(agentState.toTaskMemory());
            context.getMessages().add(Message.builder()
                    .role("system")
                    .content(memoryPrompt)
                    .timestamp(Instant.now())
                    .build());
        }
        
        // Get LLM response
        final StringBuilder responseBuilder = new StringBuilder();
        final List<String> collectedChunks = new ArrayList<>();
        
        llmProvider.streamResponse("Continue working on: " + agentState.currentObjective, context)
            .doOnNext(chunk -> {
                responseBuilder.append(chunk);
                collectedChunks.add(chunk);
                
                // Send chunk to UI
                sink.next(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(chunk)
                        .timestamp(Instant.now())
                        .build());
            })
            .doOnComplete(() -> {
                String completeResponse = responseBuilder.toString();
                log.info("Agent iteration {} complete for session {}", agentState.iterationCount, sessionId);
                
                // Update context with assistant's response
                updateAssistantMessage(context, completeResponse);
                
                // Check for tool use
                Matcher toolMatcher = TOOL_USE_PATTERN.matcher(completeResponse);
                if (toolMatcher.find()) {
                    // Process tool use
                    processToolUseAndContinue(sessionId, context, agentState, completeResponse, sink);
                } else {
                    // Check if task is complete
                    if (isTaskComplete(completeResponse)) {
                        log.info("Agent detected task completion for session {}", sessionId);
                        agentState.shouldContinue = false;
                        
                        sink.next(ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message("\n\n✅ Task completed successfully! All objectives have been met.")
                                .timestamp(Instant.now())
                                .build());
                        sink.complete();
                    } else if (agentState.shouldContinue) {
                        // Continue to next iteration after a brief pause
                        try {
                            Thread.sleep(1000); // Brief pause between iterations
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Record this iteration
                        agentState.lastAction = extractActionSummary(completeResponse);
                        
                        // Continue the loop
                        executeAgentIteration(sessionId, context, agentState, sink);
                    } else {
                        sink.complete();
                    }
                }
            })
            .doOnError(error -> {
                log.error("Error in agent iteration for session {}: {}", sessionId, error.getMessage());
                
                // Try to recover from error
                String errorRecoveryPrompt = agentPromptService.generateToolResultPrompt(
                        "error", error.getMessage(), false);
                
                context.getMessages().add(Message.builder()
                        .role("system")
                        .content(errorRecoveryPrompt)
                        .timestamp(Instant.now())
                        .build());
                
                // Continue with error recovery
                if (agentState.iterationCount < 50) {
                    executeAgentIteration(sessionId, context, agentState, sink);
                } else {
                    sink.error(error);
                }
            })
            .subscribe();
    }
    
    /**
     * Process tool use and continue agent loop
     */
    private void processToolUseAndContinue(String sessionId, ChatContext context, AgentState agentState,
                                         String response, reactor.core.publisher.FluxSink<ChatResponse> sink) {
        
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
                            // Send tool result to UI
                            sink.next(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("tool")
                                    .message(toolResult)
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Add tool result to context
                            context.getMessages().add(Message.builder()
                                    .role("tool")
                                    .content(toolResult)
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Generate tool result prompt
                            String toolResultPrompt = agentPromptService.generateToolResultPrompt(
                                    toolUseBlock.getName(), toolResult, true);
                            
                            context.getMessages().add(Message.builder()
                                    .role("system")
                                    .content(toolResultPrompt)
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Update agent state
                            agentState.lastAction = "Used tool: " + toolUseBlock.getName();
                            agentState.completedTasks.add(agentState.lastAction);
                            
                            // Continue to next iteration
                            executeAgentIteration(sessionId, context, agentState, sink);
                        },
                        error -> {
                            log.error("Tool execution error: {}", error.getMessage());
                            
                            // Add error to context
                            String errorPrompt = agentPromptService.generateToolResultPrompt(
                                    toolUseBlock.getName(), error.getMessage(), false);
                            
                            context.getMessages().add(Message.builder()
                                    .role("system")
                                    .content(errorPrompt)
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Continue despite error
                            executeAgentIteration(sessionId, context, agentState, sink);
                        }
                    );
            } catch (Exception e) {
                log.error("Error processing tool use: {}", e.getMessage());
                // Continue anyway
                executeAgentIteration(sessionId, context, agentState, sink);
            }
        }
    }
    
    /**
     * Check if the task is complete based on response content
     */
    private boolean isTaskComplete(String response) {
        // Check for explicit completion signals
        Matcher matcher = COMPLETION_PATTERN.matcher(response);
        return matcher.find();
    }
    
    /**
     * Extract a summary of the action from the response
     */
    private String extractActionSummary(String response) {
        // Simple extraction - take first sentence or line
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
    
    /**
     * Update the assistant's message in the context
     */
    private void updateAssistantMessage(ChatContext context, String content) {
        List<Message> messages = context.getMessages();
        
        // Add as new assistant message
        messages.add(Message.builder()
                .role("assistant")
                .content(content)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Handle a tool use request in a non-blocking way
     */
    private Mono<String> handleToolUse(String sessionId, ToolUseBlock toolUseBlock) {
        log.info("Handling tool use for session {}: {}", sessionId, toolUseBlock);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.error("Session not found: {}", sessionId);
            return Mono.just("Error: Session not found");
        }
        
        try {
            // Convert input to Map
            Map<String, Object> inputMap = new HashMap<>();
            Object input = toolUseBlock.getInput();
            
            if (input instanceof String) {
                try {
                    inputMap = objectMapper.readValue((String)input, Map.class);
                } catch (Exception e) {
                    log.error("Error parsing tool input as JSON: {}", e.getMessage());
                    inputMap.put("input", input);
                }
            } else if (input instanceof Map) {
                inputMap = (Map<String, Object>) input;
            } else if (input != null) {
                inputMap.put("input", input.toString());
            }
            
            // Execute the tool
            return executeToolCall(sessionId, toolUseBlock.getName(), inputMap)
                    .collectList()
                    .flatMap(outputs -> {
                        StringBuilder result = new StringBuilder();
                        if (outputs != null) {
                            for (com.ai.developer.tools.ToolOutput output : outputs) {
                                result.append(output.getContent()).append("\n");
                            }
                        } else {
                            result.append("No output from tool execution");
                        }
                        return Mono.just(result.toString());
                    })
                    .onErrorResume(e -> {
                        log.error("Error executing tool: {}", e.getMessage(), e);
                        return Mono.just("Error executing tool: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error preparing tool execution: {}", e.getMessage());
            return Mono.just("Error executing tool: " + e.getMessage());
        }
    }
    
    /**
     * Execute a tool call and return the results
     */
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        return toolRegistry.executeTool(toolName, arguments)
                .doOnNext(output -> {
                    // Send tool output to WebSocket
                    try {
                        Map<String, Object> toolOutput = new HashMap<>();
                        toolOutput.put("sessionId", sessionId);
                        toolOutput.put("toolName", toolName);
                        toolOutput.put("output", output);
                        toolOutput.put("timestamp", Instant.now());
                        
                        webSocketHandler.broadcastToolOutput(toolOutput);
                    } catch (Exception e) {
                        log.error("Error broadcasting tool output: {}", e.getMessage(), e);
                    }
                });
    }
    
    // Keep existing methods for backward compatibility
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        List<ChatResponse> history = new ArrayList<>();
        for (Message message : context.getMessages()) {
            // Skip system messages in history
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
    
    // Other existing methods remain unchanged...
}
