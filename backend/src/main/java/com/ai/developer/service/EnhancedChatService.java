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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
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
        
        log.info("ENHANCED_AGENT_STARTUP: Processing message for session {}: {}", sessionId, message);
        
        // Ensure session exists
        ensureSessionExists(sessionId);
        
        // Initialize workspace directory for this session
        initializeWorkspace(sessionId);
        
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
     * Initialize workspace directory for a session
     * This ensures the workspace exists before any tool execution
     */
    private void initializeWorkspace(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.error("[WORKSPACE_INIT] SessionId is null or empty, cannot initialize workspace");
            return;
        }
        
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        log.info("[WORKSPACE_INIT] Initializing workspace for session {}: {}", sessionId, workspacePath);
        
        try {
            // Create workspace directory if it doesn't exist
            Path dirPath = Path.of(workspacePath);
            Files.createDirectories(dirPath);
            
            // Create marker file to indicate initialization
            Path markerPath = dirPath.resolve(".initialized");
            if (!Files.exists(markerPath)) {
                Files.createFile(markerPath);
                log.info("[WORKSPACE_INIT] Created workspace marker file: {}", markerPath);
            }
            
            // Verify directory was actually created
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                log.info("[WORKSPACE_INIT] Successfully initialized workspace directory: {}", workspacePath);
            } else {
                log.error("[WORKSPACE_INIT] Failed to create workspace directory: {}", workspacePath);
            }
        } catch (IOException e) {
            log.error("[WORKSPACE_INIT] Error creating workspace directory: {}", workspacePath, e);
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
     * Enhanced with improved execution chain and workspace initialization
     */
    private ChatResponse processResponseChunk(String chunk, String sessionId, AgentState agentState) {
        // Log the raw response chunk for debugging
        log.debug("[BREAKPOINT_CHUNK] Raw response chunk for session {}: {}", sessionId, chunk);
        
        try {
            // CRITICAL FIX: Ensure workspace is initialized before processing tool calls
            initializeWorkspace(sessionId);
            
            // Extract tool calls from the response chunk
            List<ToolCall> toolCalls = toolCallExtractor.extractToolCalls(chunk);
            
            // If no tool calls found, return the chunk as is
            if (toolCalls.isEmpty()) {
                return ChatResponse.builder()
                        .content(chunk)
                        .build();
            }
            
            // Process each tool call
            StringBuilder responseBuilder = new StringBuilder();
            for (ToolCall toolCall : toolCalls) {
                // CRITICAL FIX: Ensure sessionId is included in tool arguments
                if (!toolCall.getArguments().containsKey("sessionId")) {
                    toolCall.getArguments().put("sessionId", sessionId);
                }
                
                // CRITICAL FIX: Add workspace path to tool arguments
                String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
                toolCall.getArguments().put("workspacePath", workspacePath);
                
                log.info("[TOOL_EXECUTION] Executing tool {} with sessionId {} and workspace {}", 
                        toolCall.getName(), sessionId, workspacePath);
                
                // Execute the tool and get the result
                String toolResult = executeToolCall(toolCall, sessionId, agentState);
                
                // Append the tool result to the response
                responseBuilder.append(toolResult);
            }
            
            // Return the combined response
            return ChatResponse.builder()
                    .content(responseBuilder.toString())
                    .build();
        } catch (Exception e) {
            log.error("[TOOL_EXECUTION_ERROR] Error processing response chunk: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .content("Error processing response: " + e.getMessage())
                    .build();
        }
    }
            // This ensures sessionId is present in logs and throughout the entire execution chain
            if (chunk.contains("\"name\":") && chunk.contains("\"arguments\":")) {
                log.info("[SESSIONID_FIX] Pre-processing chunk to inject sessionId for session {}", sessionId);
                // Only inject if sessionId is not already present
                if (!chunk.contains("\"sessionId\":")) {
                    // Simple string replacement to inject sessionId into arguments
                    // This is a basic approach; a more robust solution would use proper JSON parsing
                    chunk = chunk.replace("\"arguments\":{", "\"arguments\":{\"sessionId\":\"" + sessionId + "\",");
                    log.info("[SESSIONID_FIX] Injected sessionId into chunk for session {}", sessionId);
                }
            }
            
            // Use the unified ToolCallExtractor to extract tool calls from any format
            List<ToolCall> toolCalls = toolCallExtractor.extractToolCalls(chunk);
            
            if (!toolCalls.isEmpty()) {
                // Found at least one tool call
                ToolCall toolCall = toolCalls.get(0);
                
                // CRITICAL FIX: Ensure sessionId is in the arguments before logging
                Map<String, Object> arguments = toolCall.getArguments();
                if (arguments != null && !arguments.containsKey("sessionId")) {
                    arguments.put("sessionId", sessionId);
                    log.info("[SESSIONID_FIX] Added sessionId to tool call arguments before logging");
                }
                
                log.info("[BREAKPOINT_FOUND_TOOL] Found tool call in chunk for session {}: {} with arguments: {}", 
                        sessionId, toolCall.getName(), toolCall.getArguments());
                
                // CRITICAL FIX: Check if this is the first tool call in the session and if it's not a planning tool
                // If so, initialize the workspace first using the planning tool
                if (!isWorkspaceInitialized(sessionId) && !"planning_tool".equals(toolCall.getName())) {
                    log.info("[WORKSPACE_INIT] Initializing workspace for session {} before executing tool {}", 
                            sessionId, toolCall.getName());
                    
                    // Create a planning tool call to initialize the workspace
                    ToolCall planningToolCall = ToolCall.builder()
                            .name("planning_tool")
                            .arguments(Map.of(
                                "operation", "create_plan",
                                "sessionId", sessionId,
                                "title", "Workspace Initialization",
                                "objective", "Initialize workspace for session " + sessionId
                            ))
                            .build();
                    
                    // Execute the planning tool to initialize workspace
                    ChatResponse planningResponse = executeToolCall(planningToolCall, sessionId, agentState);
                    log.info("[WORKSPACE_INIT] Workspace initialized for session {}: {}", 
                            sessionId, planningResponse.getContent());
                    
                    // Broadcast workspace initialization event
                    try {
                        Map<String, Object> initData = new HashMap<>();
                        initData.put("sessionId", sessionId);
                        initData.put("action", "workspace_initialized");
                        initData.put("state", Map.of("status", "success"));
                        webSocketHandler.broadcastAgentStateUpdate(initData);
                    } catch (Exception e) {
                        log.warn("[WORKSPACE_INIT] Failed to broadcast workspace initialization: {}", e.getMessage());
                    }
                    
                    // Mark workspace as initialized
                    markWorkspaceInitialized(sessionId);
                }
                
                // Execute the original tool call
                ChatResponse toolResponse = executeToolCall(toolCall, sessionId, agentState);
                
                // CRITICAL FIX: Broadcast tool execution event to UI
                try {
                    Map<String, Object> toolData = new HashMap<>();
                    toolData.put("sessionId", sessionId);
                    toolData.put("action", "tool_executed");
                    toolData.put("toolName", toolCall.getName());
                    toolData.put("arguments", toolCall.getArguments());
                    toolData.put("result", toolResponse.getContent());
                    webSocketHandler.broadcastAgentStateUpdate(toolData);
                    log.info("[UI_STREAM] Broadcasted tool execution event for {} in session {}", 
                            toolCall.getName(), sessionId);
                } catch (Exception e) {
                    log.warn("[UI_STREAM] Failed to broadcast tool execution: {}", e.getMessage());
                }
                
                return toolResponse;
            }
            
            // No tool calls found, return the chunk as is
            // CRITICAL FIX: Broadcast thinking/reasoning to UI
            try {
                if (chunk != null && !chunk.trim().isEmpty()) {
                    Map<String, Object> thinkingData = new HashMap<>();
                    thinkingData.put("sessionId", sessionId);
                    thinkingData.put("action", "agent_thinking");
                    thinkingData.put("content", chunk);
                    webSocketHandler.broadcastAgentStateUpdate(thinkingData);
                    log.debug("[UI_STREAM] Broadcasted agent thinking for session {}", sessionId);
                }
            } catch (Exception e) {
                log.warn("[UI_STREAM] Failed to broadcast agent thinking: {}", e.getMessage());
            }
            
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
     * Check if workspace is initialized for a session
     */
    private boolean isWorkspaceInitialized(String sessionId) {
        // Check if workspace directory exists
        String workspacePath = "/tmp/ai-developer-agent/" + sessionId;
        boolean exists = Files.exists(Path.of(workspacePath));
        log.debug("[WORKSPACE_INIT] Checking if workspace exists for session {}: {}", sessionId, exists);
        return exists;
    }
    
    /**
     * Mark workspace as initialized for a session
     */
    private void markWorkspaceInitialized(String sessionId) {
        // Create a marker file to indicate workspace is initialized
        try {
            String markerPath = "/tmp/ai-developer-agent/" + sessionId + "/.initialized";
            Files.createFile(Path.of(markerPath));
            log.info("[WORKSPACE_INIT] Created workspace initialization marker for session {}", sessionId);
        } catch (IOException e) {
            log.warn("[WORKSPACE_INIT] Failed to create workspace marker: {}", e.getMessage());
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
        
        // CRITICAL FIX: Verify workspace directory exists before tool execution
        String workspacePath = "/tmp/ai-developer-agent/" + sessionId;
        try {
            Path dirPath = Path.of(workspacePath);
            if (!Files.exists(dirPath)) {
                log.info("[WORKSPACE_INIT] Creating workspace directory for session {}: {}", sessionId, workspacePath);
                Files.createDirectories(dirPath);
                
                // Create a marker file to indicate workspace is initialized
                Path markerPath = Path.of(workspacePath + "/.initialized");
                Files.createFile(markerPath);
                log.info("[WORKSPACE_INIT] Created workspace marker file: {}", markerPath);
            } else {
                log.info("[WORKSPACE_INIT] Workspace directory already exists for session {}: {}", sessionId, workspacePath);
            }
        } catch (IOException e) {
            log.error("[WORKSPACE_INIT] Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
        
        log.info("[BREAKPOINT_EXECUTE_2] Ensured sessionId {} is in arguments and workspace exists. Final arguments: {}", sessionId, arguments);
        
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
     * Enhanced with improved workspace initialization and event broadcasting
     */
    public Flux<ChatResponse> executeAutonomousLoop(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("ENHANCED_AGENT_AUTONOMOUS_STARTUP: Starting autonomous execution for session {}: {}", sessionId, message);
        
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
        
        // CRITICAL FIX: Broadcast autonomous execution start event
        try {
            Map<String, Object> startData = new HashMap<>();
            startData.put("sessionId", sessionId);
            startData.put("action", "autonomous_execution_start");
            startData.put("message", message);
            webSocketHandler.broadcastAgentStateUpdate(startData);
            log.info("[AUTONOMOUS_LOOP] Broadcasted autonomous execution start event for session {}", sessionId);
        } catch (Exception e) {
            log.warn("[AUTONOMOUS_LOOP] Failed to broadcast autonomous start event: {}", e.getMessage());
        }
        
        // Create a flux for the autonomous loop
        return Flux.create(sink -> {
            // Start the autonomous loop
            executeAutonomousStep(sessionId, message, agentState, sink);
        });
    }
    
    /**
     * Execute a single step in the autonomous loop
     * Enhanced with improved continuation logic and SSE event broadcasting
     */
    private void executeAutonomousStep(String sessionId, String message, AgentState agentState, reactor.core.publisher.FluxSink<ChatResponse> sink) {
        // Check if we should continue
        if (!agentState.getShouldContinue() || agentState.getIterationCount() >= MAX_AUTONOMOUS_ITERATIONS) {
            log.info("[AUTONOMOUS_LOOP] Autonomous execution complete for session {} after {} iterations", 
                    sessionId, agentState.getIterationCount());
            
            // CRITICAL FIX: Broadcast completion event to UI via SSE
            try {
                Map<String, Object> completionData = new HashMap<>();
                completionData.put("sessionId", sessionId);
                completionData.put("action", "autonomous_execution_complete");
                completionData.put("iterations", agentState.getIterationCount());
                webSocketHandler.broadcastAgentStateUpdate(completionData);
                log.info("[AUTONOMOUS_LOOP] Broadcasted completion event for session {}", sessionId);
            } catch (Exception e) {
                log.warn("[AUTONOMOUS_LOOP] Failed to broadcast completion event: {}", e.getMessage());
            }
            
            sink.complete();
            return;
        }
        
        // Increment iteration count
        agentState.setIterationCount(agentState.getIterationCount() + 1);
        log.info("[AUTONOMOUS_LOOP] Starting iteration {} for session {}", agentState.getIterationCount(), sessionId);
        
        // CRITICAL FIX: Add explicit continuation prompt with more specific guidance
        String promptMessage = message;
        if (agentState.getIterationCount() > 1) {
            // Enhanced continuation prompt with more specific guidance for the LLM
            promptMessage = "Continue with the task. You are in an autonomous execution loop where you can use tools to complete multi-step tasks.\n\n" +
                    "Previous step summary: " + message + "\n\n" +
                    "Please take the next logical action to make progress on the task. If you executed a tool in the previous step, " +
                    "use the tool result to determine what to do next. If you need to execute another tool, do so explicitly.\n\n" +
                    "Remember to maintain context across steps and work towards completing the overall task. " +
                    "If the task is complete, provide a final summary of what you've accomplished.";
            
            log.info("[AUTONOMOUS_LOOP] Added enhanced continuation prompt for session {}", sessionId);
            
            // CRITICAL FIX: Broadcast iteration start event to UI via SSE
            try {
                Map<String, Object> stateData = new HashMap<>();
                stateData.put("sessionId", sessionId);
                stateData.put("action", "iteration_start");
                stateData.put("state", Map.of(
                    "iterationCount", agentState.getIterationCount(),
                    "continuationPrompt", "Enhanced continuation prompt added"
                ));
                webSocketHandler.broadcastAgentStateUpdate(stateData);
                log.info("[AUTONOMOUS_LOOP] Broadcast iteration start event for session {}", sessionId);
            } catch (Exception e) {
                log.warn("[AUTONOMOUS_LOOP] Failed to broadcast agent state update: {}", e.getMessage());
            }
        }
        
        // CRITICAL FIX: Force shouldContinue to true at the beginning of each step
        agentState.setShouldContinue(true);
        
        // CRITICAL FIX: Add a small delay before continuing to ensure previous processing completes
        try {
            Thread.sleep(500); // 500ms delay
            log.debug("[AUTONOMOUS_LOOP] Added delay before continuing for session {}", sessionId);
        } catch (InterruptedException e) {
            log.warn("[AUTONOMOUS_LOOP] Delay interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        
        // Generate LLM response
        llmProvider.streamResponse(promptMessage, createChatContext(chatHistories.get(sessionId)))
                .subscribe(
                        chunk -> {
                            // Process the chunk
                            ChatResponse response = processResponseChunk(chunk, sessionId, agentState);
                            
                            // Send the response to the client
                            sink.next(response);
                            
                            // CRITICAL FIX: Broadcast chunk to UI via SSE
                            try {
                                Map<String, Object> chunkData = new HashMap<>();
                                chunkData.put("sessionId", sessionId);
                                chunkData.put("action", "chunk_received");
                                chunkData.put("content", chunk);
                                webSocketHandler.broadcastAgentStateUpdate(chunkData);
                            } catch (Exception e) {
                                log.warn("[AUTONOMOUS_LOOP] Failed to broadcast chunk: {}", e.getMessage());
                            }
                            
                            // Add assistant message to history if not a tool call
                            if (response.getToolName() == null) {
                                chatHistories.get(sessionId).add(ChatMessage.builder()
                                        .role("assistant")
                                        .content(response.getContent())
                                        .timestamp(Instant.now().toString())
                                        .build());
                            }
                            
                            // CRITICAL FIX: If this was a tool call response, explicitlyy set shouldContinue to true
                            // to ensure the autonomous loop continues after tool execution
                            if (response.getToolName() != null) {
                                log.info("[AUTONOMOUS_LOOP] Tool {} executed, explicitly setting shouldContinue=true for session {}", 
                                        response.getToolName(), sessionId);
                                agentState.setShouldContinue(true);
                                
                                // CRITICAL FIX: Broadcast tool execution event to UI
                                try {
                                    Map<String, Object> executionData = new HashMap<>();
                                    executionData.put("sessionId", sessionId);
                                    executionData.put("toolName", response.getToolName());
                                    executionData.put("args", response.getToolArgs());
                                    executionData.put("status", "executed");
                                    webSocketHandler.broadcastToolExecution(executionData);
                                    log.info("[AUTONOMOUS_LOOP] Broadcast tool execution event for {} in session {}", 
                                            response.getToolName(), sessionId);
                                } catch (Exception e) {
                                    log.warn("[AUTONOMOUS_LOOP] Failed to broadcast tool execution event: {}", e.getMessage());
                                }
                            }
                        },
                        error -> {
                            log.error("[AUTONOMOUS_LOOP] Error in autonomous execution for session {}: {}", 
                                    sessionId, error.getMessage(), error);
                            
                            // CRITICAL FIX: Broadcast error event to UI
                            try {
                                Map<String, Object> errorData = new HashMap<>();
                                errorData.put("sessionId", sessionId);
                                errorData.put("message", "Error in autonomous execution: " + error.getMessage());
                                errorData.put("severity", "error");
                                errorData.put("details", error.getClass().getName());
                                webSocketHandler.broadcastErrorEvent(errorData);
                            } catch (Exception e) {
                                log.warn("[AUTONOMOUS_LOOP] Failed to broadcast error event: {}", e.getMessage());
                            }
                            
                            sink.error(error);
                        },
                        () -> {
                            // CRITICAL FIX: Add a longer delay before continuing to ensure all processing is complete
                            try {
                                log.info("[AUTONOMOUS_LOOP] Adding delay before continuing autonomous loop for session {}", sessionId);
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                log.warn("[AUTONOMOUS_LOOP] Sleep interrupted in autonomous loop for session {}", sessionId);
                            }
                            
                            // Continue the autonomous loop if needed
                            if (agentState.getShouldContinue()) {
                                log.info("[AUTONOMOUS_LOOP] Continuing autonomous loop for session {}, iteration {}", 
                                        sessionId, agentState.getIterationCount() + 1);
                                
                                // CRITICAL FIX: Get the most relevant message for continuation
                                // This ensures the agent has proper context for the next step
                                String nextMessage;
                                List<ChatMessage> history = chatHistories.get(sessionId);
                                
                                // Find the last assistant message
                                ChatMessage lastAssistantMessage = null;
                                for (int i = history.size() - 1; i >= 0; i--) {
                                    if ("assistant".equals(history.get(i).getRole())) {
                                        lastAssistantMessage = history.get(i);
                                        break;
                                    }
                                }
                                
                                if (lastAssistantMessage != null) {
                                    nextMessage = lastAssistantMessage.getContent();
                                    log.info("[AUTONOMOUS_LOOP] Using last assistant message for continuation in session {}", sessionId);
                                } else {
                                    // Fallback to original message if no assistant message found
                                    nextMessage = message;
                                    log.warn("[AUTONOMOUS_LOOP] No assistant message found, using original message for session {}", sessionId);
                                }
                                
                                // Continue the autonomous loop with the appropriate message
                                executeAutonomousStep(sessionId, nextMessage, agentState, sink);
                            } else {
                                log.info("[AUTONOMOUS_LOOP] Autonomous execution complete for session {}", sessionId);
                                
                                // CRITICAL FIX: Broadcast completion event to UI
                                try {
                                    Map<String, Object> stateData = new HashMap<>();
                                    stateData.put("sessionId", sessionId);
                                    stateData.put("action", "execution_complete");
                                    stateData.put("state", Map.of(
                                        "iterationCount", agentState.getIterationCount(),
                                        "status", "completed"
                                    ));
                                    webSocketHandler.broadcastAgentStateUpdate(stateData);
                                } catch (Exception e) {
                                    log.warn("[AUTONOMOUS_LOOP] Failed to broadcast completion event: {}", e.getMessage());
                                }
                                
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
