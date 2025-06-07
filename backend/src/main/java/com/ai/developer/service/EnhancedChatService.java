package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.model.*;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class EnhancedChatService {
    private static final int MAX_AUTONOMOUS_ITERATIONS = 10;
    
    private final Map<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    private final Map<String, List<SessionResponse>> sessions = new ConcurrentHashMap<>();
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final ToolCallExtractor toolCallExtractor;
    
    /**
     * Process a message and return a response
     */
    public Mono<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for session {}: {}", sessionId, message);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Add user message to history
        chatHistories.get(sessionId).add(ChatMessage.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now().toString())
                .build());
        
        // Generate LLM response
        return llmProvider.streamResponse(message, createChatContext(chatHistories.get(sessionId)))
                .last()
                .map(response -> {
                    // Add assistant message to history
                    chatHistories.get(sessionId).add(ChatMessage.builder()
                            .role("assistant")
                            .content(response)
                            .timestamp(Instant.now().toString())
                            .build());
                    
                    // Return response
                    return ChatResponse.builder()
                            .content(response)
                            .build();
                });
    }
    
    /**
     * Create a chat context from chat history
     */
    private com.ai.developer.llm.ChatContext createChatContext(List<ChatMessage> chatHistory) {
        com.ai.developer.llm.ChatContext context = new com.ai.developer.llm.ChatContext();
        List<com.ai.developer.llm.Message> messages = chatHistory.stream()
                .map(msg -> com.ai.developer.llm.Message.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build())
                .collect(Collectors.toList());
        context.setMessages(messages);
        return context;
    }
    
    /**
     * Process a response chunk and check for tool calls
     */
    private ChatResponse processResponseChunk(String chunk, String sessionId, AgentState agentState) {
        // Log the raw response chunk for debugging
        log.debug("[BREAKPOINT_CHUNK] Raw response chunk for session {}: {}", sessionId, chunk);
        
        try {
            // Use the unified ToolCallExtractor to extract tool calls from any format
            List<ToolCall> toolCalls = toolCallExtractor.extractToolCalls(chunk);
            
            if (!toolCalls.isEmpty()) {
                // Found at least one tool call, execute the first one
                ToolCall toolCall = toolCalls.get(0);
                log.info("[BREAKPOINT_FOUND_TOOL] Found tool call in chunk for session {}: {} with arguments: {}", 
                        sessionId, toolCall.getName(), toolCall.getArguments());
                
                // Execute the tool call
                return executeToolCall(toolCall, sessionId, agentState);
            }
            
            // No tool calls found, return the chunk as is
            return ChatResponse.builder()
                    .content(chunk)
                    .build();
        } catch (Exception e) {
            log.error("[BREAKPOINT_ERROR] Error processing response chunk for session {}: {}", sessionId, e.getMessage(), e);
            return ChatResponse.builder()
                    .content("Error processing response: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Execute a tool call and return the result
     */
    private ChatResponse executeToolCall(ToolCall toolCall, String sessionId, AgentState agentState) {
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        String toolName = toolCall.getName();
        Map<String, Object> arguments = toolCall.getArguments();
        
        // Log the tool execution attempt with breakpoint
        log.info("[BREAKPOINT_EXECUTE_1] Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        // Add null check for arguments
        if (arguments == null) {
            log.error("[BREAKPOINT_EXECUTE_ERROR] Tool arguments are null for tool {} in session {}", toolName, sessionId);
            return ChatResponse.builder()
                    .content("Error executing tool: Arguments are null")
                    .build();
        }
        
        // CRITICAL FIX: Always ensure sessionId is present in arguments
        // This ensures consistent workspace context across all tool executions
        arguments.put("sessionId", sessionId);
        log.info("[BREAKPOINT_EXECUTE_2] Ensured sessionId {} is in arguments. Final arguments: {}", sessionId, arguments);
        
        try {
            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("[BREAKPOINT_EXECUTE_ERROR] Tool not found: {} for session {}", toolName, sessionId);
                return ChatResponse.builder()
                        .content("Error: Tool not found: " + toolName)
                        .build();
            }
            
            // Execute tool and collect results
            StringBuilder resultBuilder = new StringBuilder();
            tool.execute(arguments)
                    .doOnNext(output -> {
                        // Send tool output to WebSocket
                        webSocketHandler.sendToolOutput(sessionId, output);
                        
                        // Append to result
                        resultBuilder.append(output.getContent()).append("\n");
                        
                        // Log tool output
                        log.info("[BREAKPOINT_EXECUTE_3] Tool {} output for session {}: {}", toolName, sessionId, output.getContent());
                    })
                    .doOnError(error -> {
                        log.error("[BREAKPOINT_EXECUTE_ERROR] Error executing tool {} for session {}: {}", toolName, sessionId, error.getMessage(), error);
                        resultBuilder.append("Error: ").append(error.getMessage());
                        
                        // Explicitly ensure the agent continues after tool execution error
                        agentState.setShouldContinue(true);
                    })
                    .doOnComplete(() -> {
                        log.info("[BREAKPOINT_EXECUTE_4] Tool execution complete for session {}", sessionId);
                        
                        // Explicitly ensure the agent continues after tool execution
                        agentState.setShouldContinue(true);
                    })
                    .blockLast();
            
            // Add tool result to chat history
            chatHistories.get(sessionId).add(ChatMessage.builder()
                    .role("assistant")
                    .content(resultBuilder.toString())
                    .timestamp(Instant.now().toString())
                    .build());
            
            // Return tool result
            return ChatResponse.builder()
                    .content(resultBuilder.toString())
                    .toolName(toolName)
                    .toolArgs(arguments)
                    .build();
            
        } catch (Exception e) {
            log.error("[BREAKPOINT_EXECUTE_ERROR] Error executing tool {} for session {}: {}", toolName, sessionId, e.getMessage(), e);
            return ChatResponse.builder()
                    .content("Error executing tool: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Execute autonomous agent loop
     */
    public Flux<ChatResponse> executeAutonomousLoop(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Starting autonomous execution for session {}: {}", sessionId, message);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Create agent state if not exists
        if (!agentStates.containsKey(sessionId)) {
            agentStates.put(sessionId, new AgentState());
        }
        
        // Reset agent state
        AgentState agentState = agentStates.get(sessionId);
        agentState.setShouldContinue(true);
        agentState.setIterationCount(0);
        
        // Add user message to history
        chatHistories.get(sessionId).add(ChatMessage.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now().toString())
                .build());
        
        // Create a flux for the autonomous loop
        return Flux.create(sink -> {
            // Start the autonomous loop
            executeAutonomousStep(sessionId, message, agentState, sink);
        });
    }
    
    /**
     * Execute a single step in the autonomous loop
     */
    private void executeAutonomousStep(String sessionId, String message, AgentState agentState, reactor.core.publisher.FluxSink<ChatResponse> sink) {
        // Check if we should continue
        if (!agentState.getShouldContinue() || agentState.getIterationCount() >= MAX_AUTONOMOUS_ITERATIONS) {
            log.info("Autonomous execution complete for session {}", sessionId);
            sink.complete();
            return;
        }
        
        // Increment iteration count
        agentState.setIterationCount(agentState.getIterationCount() + 1);
        log.info("[AUTONOMOUS_LOOP] Starting iteration {} for session {}", agentState.getIterationCount(), sessionId);
        
        // Add explicit continuation prompt if this is not the first iteration
        String promptMessage = message;
        if (agentState.getIterationCount() > 1) {
            // Add explicit continuation prompt to encourage the agent to continue
            promptMessage = "Continue with the task. If you executed a tool in the previous step, " +
                    "use the tool result to make progress. If you need to execute another tool, do so. " +
                    "If the task is complete, summarize what you've done.\n\n" + message;
            log.info("[AUTONOMOUS_LOOP] Added continuation prompt for session {}", sessionId);
        }
        
        // Generate LLM response
        llmProvider.streamResponse(promptMessage, createChatContext(chatHistories.get(sessionId)))
                .subscribe(
                        chunk -> {
                            // Process the chunk
                            ChatResponse response = processResponseChunk(chunk, sessionId, agentState);
                            
                            // Send the response to the client
                            sink.next(response);
                            
                            // Add assistant message to history if not a tool call
                            if (response.getToolName() == null) {
                                chatHistories.get(sessionId).add(ChatMessage.builder()
                                        .role("assistant")
                                        .content(response.getContent())
                                        .timestamp(Instant.now().toString())
                                        .build());
                            }
                            
                            // CRITICAL FIX: If this was a tool call response, explicitly set shouldContinue to true
                            // to ensure the autonomous loop continues after tool execution
                            if (response.getToolName() != null) {
                                log.info("[AUTONOMOUS_LOOP] Tool {} executed, explicitly setting shouldContinue=true for session {}", 
                                        response.getToolName(), sessionId);
                                agentState.setShouldContinue(true);
                            }
                        },
                        error -> {
                            log.error("Error in autonomous execution for session {}: {}", sessionId, error.getMessage(), error);
                            sink.error(error);
                        },
                        () -> {
                            // CRITICAL FIX: Add a small delay before continuing to ensure all processing is complete
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                log.warn("Sleep interrupted in autonomous loop for session {}", sessionId);
                            }
                            
                            // Continue the autonomous loop if needed
                            if (agentState.getShouldContinue()) {
                                log.info("[AUTONOMOUS_LOOP] Continuing autonomous loop for session {}, iteration {}", 
                                        sessionId, agentState.getIterationCount() + 1);
                                
                                // Get the last message from the chat history
                                ChatMessage lastMessage = chatHistories.get(sessionId).get(chatHistories.get(sessionId).size() - 1);
                                
                                // Add tool result to the message if the last response was a tool call
                                String nextMessage = lastMessage.getContent();
                                
                                // Continue the autonomous loop with the last message
                                executeAutonomousStep(sessionId, nextMessage, agentState, sink);
                            } else {
                                log.info("[AUTONOMOUS_LOOP] Autonomous execution complete for session {}", sessionId);
                                sink.complete();
                            }
                        }
                );
    }
    
    /**
     * Create a new session
     */
    public Mono<SessionResponse> createSession() {
        // Generate a random session ID
        String sessionId = UUID.randomUUID().toString();
        
        log.info("Creating new session: {}", sessionId);
        
        // Initialize chat history
        chatHistories.put(sessionId, new ArrayList<>());
        
        // Initialize sessions map if not exists
        if (!sessions.containsKey("all")) {
            sessions.put("all", new ArrayList<>());
        }
        
        // Add session to list
        sessions.get("all").add(SessionResponse.builder()
                .sessionId(sessionId)
                .created(Instant.now())
                .build());
        
        // Return session
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .created(Instant.now())
                .build());
    }
    
    /**
     * Get all sessions
     */
    public Mono<List<SessionResponse>> getSessions() {
        // Initialize sessions map if not exists
        if (!sessions.containsKey("all")) {
            sessions.put("all", new ArrayList<>());
        }
        
        // Return all sessions
        return Mono.just(sessions.get("all"));
    }
    
    /**
     * Delete a session
     */
    public Mono<Void> deleteSession(String sessionId) {
        log.info("Deleting session: {}", sessionId);
        
        // Remove chat history
        chatHistories.remove(sessionId);
        
        // Remove agent state
        agentStates.remove(sessionId);
        
        // Remove session from list
        if (sessions.containsKey("all")) {
            sessions.get("all").removeIf(s -> s.getSessionId().equals(sessionId));
        }
        
        // Return empty mono
        return Mono.empty();
    }
    
    /**
     * Get session history
     * This method was restored to maintain API compatibility with ChatController
     */
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        log.info("[API] Getting history for session: {}", sessionId);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Convert chat history to chat responses
        List<ChatResponse> history = new ArrayList<>();
        
        for (ChatMessage msg : chatHistories.get(sessionId)) {
            ChatResponse response = ChatResponse.builder()
                    .content(msg.getContent())
                    .role(msg.getRole())
                    // Skip timestamp to avoid type conversion issues
                    .build();
            history.add(response);
        }
        
        return Mono.just(history);
    }
    
    /**
     * Execute a specific tool
     * This method was restored to maintain API compatibility with ChatController
     */
    public Flux<ToolCallResponse> executeTool(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("[API] Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Add session ID to arguments if not present
        if (!arguments.containsKey("sessionId")) {
            arguments.put("sessionId", sessionId);
        }
        
        try {
            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("[API] Tool not found: {} for session {}", toolName, sessionId);
                return Flux.error(new RuntimeException("Tool not found: " + toolName));
            }
            
            // Execute tool and map results to ToolCallResponse
            return tool.execute(arguments)
                    .map(output -> {
                        // Send tool output to WebSocket
                        webSocketHandler.sendToolOutput(sessionId, output);
                        
                        // Log tool output
                        log.info("[API] Tool {} output for session {}: {}", toolName, sessionId, output.getContent());
                        
                        // Return tool call response
                        return ToolCallResponse.builder()
                                .toolName(toolName)
                                .result(output.getContent())
                                .metadata(output.getMetadata())
                                .build();
                    })
                    .doOnError(error -> {
                        log.error("[API] Error executing tool {} for session {}: {}", toolName, sessionId, error.getMessage(), error);
                    });
            
        } catch (Exception e) {
            log.error("[API] Error executing tool {} for session {}: {}", toolName, sessionId, e.getMessage(), e);
            return Flux.error(e);
        }
    }
    
    /**
     * Ensure a session exists
     */
    private void ensureSessionExists(String sessionId) {
        // Initialize chat history if not exists
        if (!chatHistories.containsKey(sessionId)) {
            chatHistories.put(sessionId, new ArrayList<>());
        }
        
        // Initialize sessions map if not exists
        if (!sessions.containsKey("all")) {
            sessions.put("all", new ArrayList<>());
        }
        
        // Add session to list if not exists
        boolean sessionExists = sessions.get("all").stream()
                .anyMatch(s -> s.getSessionId().equals(sessionId));
        
        if (!sessionExists) {
            sessions.get("all").add(SessionResponse.builder()
                    .sessionId(sessionId)
                    .created(Instant.now())
                    .build());
        }
    }
}
