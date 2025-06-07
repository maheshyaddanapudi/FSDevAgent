package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.config.ToolOutputWebSocketHandler;
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
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class EnhancedChatService {
    private static final int MAX_AUTONOMOUS_ITERATIONS = 10;
    
    // Updated regex pattern to properly capture the complete tool call structure
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile(
        "<function_calls>\\s*" +
        "<invoke\\s+name=\"([^\"]+)\">\\s*" +
        "(?:<parameter\\s+name=\"([^\"]+)\">([^<]+)</parameter>\\s*)*" +
        "</invoke>\\s*" +
        "</function_calls>",
        Pattern.DOTALL
    );
    
    // Pattern to extract individual parameters from a tool call
    private static final Pattern PARAMETER_PATTERN = Pattern.compile(
        "<parameter\\s+name=\"([^\"]+)\">([^<]+)</parameter>",
        Pattern.DOTALL
    );
    
    private final Map<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    private final Map<String, String> sessionWorkspaces = new ConcurrentHashMap<>();
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    
    /**
     * Process a chat message and return a response
     */
    public Flux<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for session {}: {}", sessionId, message);
        
        // Always ensure session exists before proceeding
        ensureSessionExists(sessionId);
        
        // Add user message to history
        chatHistories.get(sessionId).add(ChatMessage.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now().toString())
                .build());
        
        // Get agent state or create new one
        AgentState agentState = agentStates.computeIfAbsent(sessionId, id -> new AgentState());
        
        // Create response flux
        return llmProvider.streamResponse(message, createChatContext(chatHistories.get(sessionId)))
                .map(chunk -> {
                    // Process chunk for tool calls
                    return processResponseChunk(chunk, sessionId, agentState);
                });
    }
    
    /**
     * Ensure a session exists, creating it if necessary
     */
    private void ensureSessionExists(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        
        if (!chatHistories.containsKey(sessionId)) {
            log.info("Creating new session on demand: {}", sessionId);
            createSession(sessionId);
        }
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
        log.debug("Raw response chunk for session {}: {}", sessionId, chunk);
        
        // Check for tool use blocks in the chunk
        if (chunk.contains("<function_calls>") && chunk.contains("</function_calls>")) {
            log.info("Found tool use block in response for session {}", sessionId);
            
            // Log the entire chunk containing tool calls for debugging
            log.debug("Tool use block detected in chunk: {}", chunk);
            
            try {
                // Extract and process tool calls
                return processAutonomousResponse(chunk, sessionId, agentState);
            } catch (Exception e) {
                log.error("Error processing tool call for session {}: {}", sessionId, e.getMessage(), e);
                return ChatResponse.builder()
                        .content("Error processing tool call: " + e.getMessage())
                        .build();
            }
        }
        
        // Regular response chunk
        return ChatResponse.builder()
                .content(chunk)
                .build();
    }
    
    /**
     * Process a response containing tool calls
     */
    private ChatResponse processAutonomousResponse(String response, String sessionId, AgentState agentState) {
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Log the entire response containing tool calls for debugging
        log.info("Processing autonomous response for session {}", sessionId);
        log.debug("Full response with tool calls: {}", response);
        
        // Extract tool calls using regex
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        
        if (matcher.find()) {
            // Extract the entire tool call block for logging
            String fullToolCallBlock = matcher.group(0);
            log.info("Extracted tool call block for session {}: {}", sessionId, fullToolCallBlock);
            
            try {
                // Extract tool name
                String toolName = matcher.group(1);
                log.info("Extracted tool name for session {}: {}", sessionId, toolName);
                
                // Extract parameters using separate pattern
                Map<String, Object> arguments = new HashMap<>();
                Matcher paramMatcher = PARAMETER_PATTERN.matcher(fullToolCallBlock);
                
                while (paramMatcher.find()) {
                    String paramName = paramMatcher.group(1);
                    String paramValue = paramMatcher.group(2);
                    log.debug("Extracted parameter for session {}: {} = {}", sessionId, paramName, paramValue);
                    arguments.put(paramName, paramValue);
                }
                
                // Log the extracted arguments for debugging
                log.info("Extracted arguments for tool {} in session {}: {}", toolName, sessionId, arguments);
                
                // Check if arguments map is empty
                if (arguments.isEmpty()) {
                    log.warn("No arguments extracted for tool {} in session {}", toolName, sessionId);
                }
                
                // Create tool call object
                ToolCall toolCall = ToolCall.builder()
                        .name(toolName)
                        .arguments(arguments)
                        .build();
                
                // Execute tool
                return executeToolCall(toolCall, sessionId, agentState);
                
            } catch (Exception e) {
                log.error("Error parsing tool call for session {}: {}", sessionId, e.getMessage(), e);
                return ChatResponse.builder()
                        .content("Error parsing tool call: " + e.getMessage())
                        .build();
            }
        } else {
            log.warn("No tool call found in response for session {} despite function_calls tags", sessionId);
            return ChatResponse.builder()
                    .content(response)
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
        
        // Log the tool execution attempt
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        // Add null check for arguments
        if (arguments == null) {
            log.error("Tool arguments are null for tool {} in session {}", toolName, sessionId);
            return ChatResponse.builder()
                    .content("Error executing tool: Arguments are null")
                    .build();
        }
        
        // Add session ID to arguments if not present
        if (!arguments.containsKey("sessionId")) {
            arguments.put("sessionId", sessionId);
        }
        
        try {
            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("Tool not found: {} for session {}", toolName, sessionId);
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
                        log.info("Tool {} output for session {}: {}", toolName, sessionId, output.getContent());
                    })
                    .doOnError(error -> {
                        log.error("Error executing tool {} for session {}: {}", toolName, sessionId, error.getMessage(), error);
                        resultBuilder.append("Error: ").append(error.getMessage());
                        
                        // Explicitly ensure the agent continues after tool execution error
                        agentState.setShouldContinue(true);
                    })
                    .doOnComplete(() -> {
                        log.info("Tool execution complete for session {}", sessionId);
                        
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
            log.error("Error executing tool {} for session {}: {}", toolName, sessionId, e.getMessage(), e);
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
        
        // Add user message to history
        chatHistories.get(sessionId).add(ChatMessage.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now().toString())
                .build());
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setRunning(true);
        agentState.setShouldContinue(true);
        agentState.setIterationCount(0);
        agentStates.put(sessionId, agentState);
        
        // Create response sink
        Sinks.Many<ChatResponse> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        // Start autonomous loop in separate thread
        Thread autonomousThread = new Thread(() -> {
            try {
                // Loop until completion or max iterations
                while (agentState.isRunning() && agentState.getShouldContinue() && 
                       agentState.getIterationCount() < MAX_AUTONOMOUS_ITERATIONS) {
                    
                    // Reset continuation flag
                    agentState.setShouldContinue(false);
                    
                    // Increment iteration count
                    agentState.setIterationCount(agentState.getIterationCount() + 1);
                    
                    log.info("Autonomous iteration {} for session {}", agentState.getIterationCount(), sessionId);
                    
                    // Generate LLM response
                    llmProvider.streamResponse(message, createChatContext(chatHistories.get(sessionId)))
                            .doOnNext(chunk -> {
                                // Process chunk
                                ChatResponse response = processResponseChunk(chunk, sessionId, agentState);
                                
                                // Emit response
                                sink.tryEmitNext(response);
                            })
                            .doOnComplete(() -> {
                                log.info("LLM response complete for session {}", sessionId);
                                
                                // Continue loop if no tool calls were found
                                if (!agentState.getShouldContinue()) {
                                    log.info("No tool calls found, completing autonomous loop for session {}", sessionId);
                                    agentState.setRunning(false);
                                }
                            })
                            .doOnError(error -> {
                                log.error("Error in autonomous loop for session {}: {}", sessionId, error.getMessage(), error);
                                agentState.setRunning(false);
                                sink.tryEmitError(error);
                            })
                            .blockLast();
                    
                    // Check if max iterations reached
                    if (agentState.getIterationCount() >= MAX_AUTONOMOUS_ITERATIONS) {
                        log.warn("Max iterations reached for session {}", sessionId);
                        agentState.setRunning(false);
                        sink.tryEmitNext(ChatResponse.builder()
                                .content("Max iterations reached, stopping autonomous execution")
                                .build());
                    }
                }
                
                // Complete sink when done
                log.info("Autonomous loop complete for session {}", sessionId);
                sink.tryEmitComplete();
                
            } catch (Exception e) {
                log.error("Error in autonomous thread for session {}: {}", sessionId, e.getMessage(), e);
                sink.tryEmitError(e);
            }
        });
        
        // Start thread
        autonomousThread.start();
        
        // Return flux from sink
        return sink.asFlux();
    }
    
    /**
     * Create a new session with provided ID
     */
    public void createSession(String sessionId) {
        log.info("Created new session: {}", sessionId);
        chatHistories.put(sessionId, new ArrayList<>());
        
        // Create workspace directory
        String workspacePath = "/tmp/ai-developer-agent/" + sessionId;
        sessionWorkspaces.put(sessionId, workspacePath);
        
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(workspacePath));
            log.info("Created workspace directory for session {}: {}", workspacePath, sessionId);
        } catch (Exception e) {
            log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * Create a new session with generated ID
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        createSession(sessionId);
        
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .created(Instant.now())
                .build());
    }
    
    /**
     * Get all sessions
     */
    public Mono<List<SessionResponse>> getSessions() {
        List<SessionResponse> sessions = new ArrayList<>();
        
        for (String sessionId : chatHistories.keySet()) {
            sessions.add(SessionResponse.builder()
                    .sessionId(sessionId)
                    .created(Instant.now()) // Using current time as placeholder
                    .build());
        }
        
        return Mono.just(sessions);
    }
    
    /**
     * Delete a session
     */
    public Mono<Void> deleteSession(String sessionId) {
        log.info("Deleting session: {}", sessionId);
        chatHistories.remove(sessionId);
        agentStates.remove(sessionId);
        sessionWorkspaces.remove(sessionId);
        
        return Mono.empty();
    }
    
    /**
     * Register an existing session
     */
    public void registerSession(String sessionId, List<ChatMessage> history) {
        log.info("Registering existing session: {}", sessionId);
        chatHistories.put(sessionId, history);
        
        // Create workspace directory if it doesn't exist
        String workspacePath = "/tmp/ai-developer-agent/" + sessionId;
        sessionWorkspaces.put(sessionId, workspacePath);
        
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(workspacePath));
            log.info("Created workspace directory for session {}: {}", workspacePath, sessionId);
        } catch (Exception e) {
            log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * Get chat history for a session
     */
    public List<ChatMessage> getChatHistory(String sessionId) {
        ensureSessionExists(sessionId);
        return chatHistories.getOrDefault(sessionId, new ArrayList<>());
    }
    
    /**
     * Get session history as ChatResponse list
     */
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        ensureSessionExists(sessionId);
        List<ChatMessage> history = getChatHistory(sessionId);
        List<ChatResponse> responses = history.stream()
                .map(msg -> ChatResponse.builder()
                        .content(msg.getContent())
                        .role(msg.getRole())
                        .timestamp(Instant.now()) // Using current time as placeholder
                        .sessionId(sessionId)
                        .build())
                .collect(Collectors.toList());
        
        return Mono.just(responses);
    }
    
    /**
     * Execute a tool directly
     */
    public Flux<ToolCallResponse> executeTool(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Add null check for arguments
        if (arguments == null) {
            log.error("Tool arguments are null for tool {} in session {}", toolName, sessionId);
            return Flux.error(new IllegalArgumentException("Arguments cannot be null"));
        }
        
        // Add session ID to arguments if not present
        if (!arguments.containsKey("sessionId")) {
            arguments.put("sessionId", sessionId);
        }
        
        try {
            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("Tool not found: {} for session {}", toolName, sessionId);
                return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
            }
            
            // Execute tool and map results to ToolCallResponse
            return tool.execute(arguments)
                    .map(output -> {
                        // Send tool output to WebSocket
                        webSocketHandler.sendToolOutput(sessionId, output);
                        
                        // Log tool output
                        log.info("Tool {} output for session {}: {}", toolName, sessionId, output.getContent());
                        
                        // Map to ToolCallResponse
                        return ToolCallResponse.builder()
                                .toolName(toolName)
                                .result(output.getContent())
                                .metadata(output.getMetadata())
                                .build();
                    });
            
        } catch (Exception e) {
            log.error("Error executing tool {} for session {}: {}", toolName, sessionId, e.getMessage(), e);
            return Flux.error(e);
        }
    }
    
    /**
     * Get workspace path for a session
     */
    public String getSessionWorkspace(String sessionId) {
        ensureSessionExists(sessionId);
        return sessionWorkspaces.getOrDefault(sessionId, "/tmp/ai-developer-agent/" + sessionId);
    }
}
