package com.ai.developer.service;

import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.Message;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.llm.ToolUseBlock;
import com.ai.developer.model.ChatRequest;
import com.ai.developer.model.ChatResponse;
import com.ai.developer.model.SessionResponse;
import com.ai.developer.model.ToolCallResponse;
import com.ai.developer.model.ToolOutput;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Pattern to match tool use blocks in LLM responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    
    public ChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, ToolOutputWebSocketHandler webSocketHandler) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        
        log.info("ChatService initialized with LLM provider: {}", llmProvider.getClass().getSimpleName());
        log.info("Available tools: {}", toolRegistry.getToolNames());
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
    
    /**
     * Create a new session with workspace initialization
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
        
        ChatContext context = new ChatContext();
        context.setSystemPrompt("You are an AI Developer Agent, designed to help with coding, debugging, and using various development tools. Your workspace directory is: " + workspacePath);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", workspacePath);
        context.setMetadata(metadata);
        
        sessions.put(sessionId, context);
        
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
        if (removed != null) {
            log.info("Deleted session: {}", sessionId);
            return true;
        }
        
        log.warn("Session not found for deletion: {}", sessionId);
        return false;
    }
    
    /**
     * Process a user message and get a response
     */
    public Flux<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for session {}: {}", sessionId, message);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Ensure session has metadata with workspace path
        if (context.getMetadata() == null) {
            context.setMetadata(new HashMap<String, Object>());
        }
        
        // Create or update workspace path in metadata
        if (!context.getMetadata().containsKey("workspacePath")) {
            String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
            try {
                Files.createDirectories(Path.of(workspacePath));
                log.info("Created workspace directory for existing session {}: {}", sessionId, workspacePath);
                context.getMetadata().put("workspacePath", workspacePath);
            } catch (Exception e) {
                log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
            }
        }
        
        // Ensure sessionId is in metadata
        if (!context.getMetadata().containsKey("sessionId")) {
            context.getMetadata().put("sessionId", sessionId);
        }
        
        // Add user message to context
        context.getMessages().add(Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build());
        
        // Log the current context
        log.debug("Current context for session {}: {} messages", sessionId, context.getMessages().size());
        
        // ADDED DEBUG LOGGING: Log that we're about to call the LLM provider
        log.info("Calling LLM provider for session {}", sessionId);
        
        // Get streaming response from LLM using reactive API
        final StringBuilder responseBuilder = new StringBuilder();
        
        // Process the streaming response
        return llmProvider.streamResponse(message, context)
            .doOnNext(chunk -> {
                responseBuilder.append(chunk);
                // ADDED DEBUG LOGGING: Log each chunk received from LLM
                log.debug("Received chunk from LLM: {}", chunk);
                
                // Update the assistant's message in the context
                updateAssistantMessage(context, chunk);
            })
            .doOnComplete(() -> {
                // ADDED DEBUG LOGGING: Log completion of LLM response
                log.info("LLM response completed for session {}", sessionId);
            })
            .doOnError(error -> {
                // ADDED DEBUG LOGGING: Log any errors during LLM invocation
                log.error("Error during LLM invocation for session {}: {}", sessionId, error.getMessage(), error);
            })
            .collectList()
            .flatMapMany(chunks -> {
                // Combine all chunks into a single response
                String llmResponse = responseBuilder.toString();
                
                // ADDED DEBUG LOGGING: Log the complete LLM response
                log.info("Complete LLM response for session {}: {}", sessionId, llmResponse);
                
                // Process the response for tool use blocks
                return processResponseForToolUse(sessionId, context, llmResponse);
            });
    }
    
    /**
     * Process a response for tool use blocks
     */
    private Flux<ChatResponse> processResponseForToolUse(String sessionId, ChatContext context, String response) {
        // ADDED DEBUG LOGGING: Log that we're checking for tool use blocks
        log.info("Checking for tool use blocks in response for session {}", sessionId);
        
        // Check for special EVENT: prefixed messages
        if (response.startsWith("EVENT:")) {
            log.info("Found special event in response: {}", response.substring(0, Math.min(50, response.length())));
            
            // Extract event type and payload
            String[] parts = response.split(":", 3);
            if (parts.length >= 3) {
                String eventType = parts[1];
                String eventPayload = parts[2];
                
                // Handle different event types
                if ("toolCall".equals(eventType)) {
                    log.info("Processing toolCall event: {}", eventPayload);
                    try {
                        // Parse the tool call JSON
                        ToolCall toolCall = objectMapper.readValue(eventPayload, ToolCall.class);
                        
                        // Add session information to tool call arguments
                        Map<String, Object> arguments = new HashMap<>(toolCall.getArguments());
                        if (!arguments.containsKey("sessionId")) {
                            arguments.put("sessionId", sessionId);
                        }
                        
                        // Add workspace path if not present
                        if (!arguments.containsKey("workspacePath") && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
                            String workspacePath = (String) context.getMetadata().get("workspacePath");
                            arguments.put("workspacePath", workspacePath);
                        }
                        
                        // Update the tool call with session information
                        toolCall = ToolCall.builder()
                                .id(toolCall.getId())
                                .name(toolCall.getName())
                                .arguments(arguments)
                                .build();
                        
                        // Create the initial response with the tool call
                        final ChatResponse initialResponse = ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message("I'll use the " + toolCall.getName() + " tool to help with this.")
                                .toolCall(ToolCallResponse.builder()
                                        .name(toolCall.getName())
                                        .arguments(objectMapper.convertValue(toolCall.getArguments(), Map.class))
                                        .build())
                                .timestamp(Instant.now())
                                .build();
                        
                        // Extract the tool use block from the remaining response
                        String remainingResponse = response.substring(response.indexOf("<tool_use>"));
                        Matcher matcher = TOOL_USE_PATTERN.matcher(remainingResponse);
                        if (matcher.find()) {
                            // Extract the tool use JSON from the marker
                            String toolUseJson = matcher.group(1);
                            log.info("Extracted tool use JSON: {}", toolUseJson);
                            
                            // Parse the tool use block
                            ToolUseBlock toolUseBlock = objectMapper.readValue(toolUseJson, ToolUseBlock.class);
                            log.info("Parsed tool use block: {}", toolUseBlock);
                            
                            // Add session information to tool use block
                            // Cast the input to Map<String, Object> if it's not null
                            Map<String, Object> input = new HashMap<>();
                            if (toolUseBlock.getInput() != null) {
                                if (toolUseBlock.getInput() instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> inputMap = (Map<String, Object>) toolUseBlock.getInput();
                                    input.putAll(inputMap);
                                }
                            }
                            
                            if (!input.containsKey("sessionId")) {
                                input.put("sessionId", sessionId);
                            }
                            
                            // Add workspace path if not present
                            if (!input.containsKey("workspacePath") && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
                                String workspacePath = (String) context.getMetadata().get("workspacePath");
                                input.put("workspacePath", workspacePath);
                            }
                            
                            // Update the tool use block with session information
                            final ToolUseBlock finalToolUseBlock = ToolUseBlock.builder()
                                    .id(toolUseBlock.getId())
                                    .name(toolUseBlock.getName())
                                    .input(input)
                                    .build();
                            
                            // Handle the tool use in a non-blocking way and chain the result
                            return handleToolUse(sessionId, finalToolUseBlock)
                                    .flatMapMany(toolResult -> {
                                        // ADDED DEBUG LOGGING: Log the tool result
                                        log.info("Tool {} execution completed for session {}: {}", finalToolUseBlock.getName(), sessionId, toolResult);
                                        
                                        // Send tool result to WebSocket for emulator visualization
                                        try {
                                            ToolOutput toolOutput = ToolOutput.builder()
                                                .type("terminal")
                                                .output(toolResult)
                                                .mimeType("text/plain")
                                                .build();
                                            String toolOutputJson = objectMapper.writeValueAsString(toolOutput);
                                            webSocketHandler.broadcastToolOutput(toolOutputJson);
                                            log.info("Broadcasted tool output to WebSocket: {}", toolOutputJson);
                                        } catch (Exception e) {
                                            log.error("Error broadcasting tool output to WebSocket: {}", e.getMessage());
                                        }
                                        
                                        // Create the tool result response
                                        ChatResponse toolResponse = ChatResponse.builder()
                                                .sessionId(sessionId)
                                                .role("tool")
                                                .message(toolResult)
                                                .toolCallId(finalToolUseBlock.getId())
                                                .timestamp(Instant.now())
                                                .build();
                                        
                                        // Return both responses as a flux
                                        return Flux.just(initialResponse, toolResponse);
                                    })
                                    .onErrorResume(e -> {
                                        log.error("Error handling tool use: {}", e.getMessage());
                                        return Flux.just(ChatResponse.builder()
                                                .sessionId(sessionId)
                                                .role("assistant")
                                                .message("Error executing tool: " + e.getMessage())
                                                .timestamp(Instant.now())
                                                .build());
                                    });
                        } else {
                            log.error("Found toolCall event but no tool_use block in response");
                            return Flux.just(initialResponse);
                        }
                    } catch (JsonProcessingException e) {
                        log.error("Error parsing toolCall event: {}", e.getMessage());
                        return Flux.just(ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message(response.replaceAll("EVENT:.*?\\n", ""))
                                .timestamp(Instant.now())
                                .build());
                    }
                } else {
                    log.warn("Unknown event type: {}", eventType);
                }
            }
            
            // Remove the EVENT: prefix and continue with normal processing
            response = response.replaceAll("EVENT:.*?\\n", "");
        }
        
        // Check if the response contains tool use blocks
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                // Extract the tool use JSON from the marker
                String toolUseJson = matcher.group(1);
                log.info("Extracted tool use JSON: {}", toolUseJson);
                
                // Parse the tool use block
                ToolUseBlock toolUseBlock = objectMapper.readValue(toolUseJson, ToolUseBlock.class);
                log.info("Parsed tool use block: {}", toolUseBlock);
                
                // Add session information to tool use block
                // Cast the input to Map<String, Object> if it's not null
                Map<String, Object> input = new HashMap<>();
                if (toolUseBlock.getInput() != null) {
                    if (toolUseBlock.getInput() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inputMap = (Map<String, Object>) toolUseBlock.getInput();
                        input.putAll(inputMap);
                    }
                }
                
                if (!input.containsKey("sessionId")) {
                    input.put("sessionId", sessionId);
                }
                
                // Add workspace path if not present
                if (!input.containsKey("workspacePath") && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
                    String workspacePath = (String) context.getMetadata().get("workspacePath");
                    input.put("workspacePath", workspacePath);
                }
                
                // Replace the tool use marker with a cleaner message
                String cleanedResponse = response.replace(matcher.group(0), 
                        "\n\nI'll use the " + toolUseBlock.getName() + " tool to help with this.");
                
                // Add the assistant's message to the context
                Message assistantMessage = Message.builder()
                        .role("assistant")
                        .content(cleanedResponse)
                        .toolCall(ToolCall.builder()
                                .id(toolUseBlock.getId())
                                .name(toolUseBlock.getName())
                                .arguments(input)
                                .build())
                        .timestamp(Instant.now())
                        .build();
                
                context.getMessages().add(assistantMessage);
                
                // Create the initial response with the tool call
                final ChatResponse initialResponse = ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(cleanedResponse)
                        .toolCall(ToolCallResponse.builder()
                                .name(toolUseBlock.getName())
                                .arguments(objectMapper.convertValue(input, Map.class))
                                .build())
                        .timestamp(Instant.now())
                        .build();
                
                // Update the tool use block with session information
                final ToolUseBlock finalToolUseBlock = ToolUseBlock.builder()
                        .id(toolUseBlock.getId())
                        .name(toolUseBlock.getName())
                        .input(input)
                        .build();
                
                // Handle the tool use in a non-blocking way and chain the result
                return handleToolUse(sessionId, finalToolUseBlock)
                        .flatMapMany(toolResult -> {
                            // ADDED DEBUG LOGGING: Log the tool result
                            log.info("Tool {} execution completed for session {}: {}", finalToolUseBlock.getName(), sessionId, toolResult);
                            
                            // Send tool result to WebSocket for emulator visualization
                            try {
                                ToolOutput toolOutput = ToolOutput.builder()
                                    .type("terminal")
                                    .output(toolResult)
                                    .mimeType("text/plain")
                                    .build();
                                String toolOutputJson = objectMapper.writeValueAsString(toolOutput);
                                webSocketHandler.broadcastToolOutput(toolOutputJson);
                                log.info("Broadcasted tool output to WebSocket: {}", toolOutputJson);
                            } catch (Exception e) {
                                log.error("Error broadcasting tool output to WebSocket: {}", e.getMessage());
                            }
                            
                            // Add the tool result to the context
                            Message toolMessage = Message.builder()
                                    .role("tool")
                                    .content(toolResult)
                                    .toolCallId(finalToolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build();
                            
                            context.getMessages().add(toolMessage);
                            
                            // Create the tool result response
                            ChatResponse toolResponse = ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("tool")
                                    .message(toolResult)
                                    .toolCallId(finalToolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build();
                            
                            // Return both responses as a flux
                            return Flux.just(initialResponse, toolResponse);
                        })
                        .onErrorResume(e -> {
                            log.error("Error handling tool use: {}", e.getMessage());
                            return Flux.just(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .message("Error executing tool: " + e.getMessage())
                                    .timestamp(Instant.now())
                                    .build());
                        });
            } catch (JsonProcessingException e) {
                log.error("Error parsing tool use block: {}", e.getMessage());
            }
        }
        
        // If no tool use blocks, just return the response as is
        Message assistantMessage = Message.builder()
                .role("assistant")
                .content(response)
                .timestamp(Instant.now())
                .build();
        
        context.getMessages().add(assistantMessage);
        
        return Flux.just(ChatResponse.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(response)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Update the assistant's message in the context with streaming chunks
     */
    private void updateAssistantMessage(ChatContext context, String chunk) {
        List<Message> messages = context.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        
        // Find the last assistant message, or create a new one if none exists
        Message lastAssistantMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).getRole())) {
                lastAssistantMessage = messages.get(i);
                break;
            }
        }
        
        if (lastAssistantMessage == null) {
            // Create a new assistant message
            lastAssistantMessage = Message.builder()
                    .role("assistant")
                    .content(chunk)
                    .timestamp(Instant.now())
                    .build();
            messages.add(lastAssistantMessage);
        } else {
            // Update the existing message
            String updatedContent = lastAssistantMessage.getContent() != null ? 
                    lastAssistantMessage.getContent() + chunk : chunk;
            
            // Replace the message with an updated copy
            int index = messages.indexOf(lastAssistantMessage);
            messages.set(index, Message.builder()
                    .role("assistant")
                    .content(updatedContent)
                    .toolCall(lastAssistantMessage.getToolCall())
                    .toolCallId(lastAssistantMessage.getToolCallId())
                    .timestamp(lastAssistantMessage.getTimestamp())
                    .build());
        }
    }
    
    /**
     * Handle a tool use block
     */
    private Mono<String> handleToolUse(String sessionId, ToolUseBlock toolUseBlock) {
        log.info("Handling tool use for session {}: {}", sessionId, toolUseBlock);
        
        // Get the tool from the registry
        Tool tool = toolRegistry.getTool(toolUseBlock.getName());
        if (tool == null) {
            log.error("Tool not found: {}", toolUseBlock.getName());
            return Mono.just("Error: Tool not found: " + toolUseBlock.getName());
        }
        
        // Ensure the tool input has sessionId
        Map<String, Object> input = new HashMap<>();
        if (toolUseBlock.getInput() != null) {
            if (toolUseBlock.getInput() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> inputMap = (Map<String, Object>) toolUseBlock.getInput();
                input.putAll(inputMap);
            }
        }
        
        if (!input.containsKey("sessionId")) {
            input.put("sessionId", sessionId);
        }
        
        // Get the workspace path from context metadata
        ChatContext context = sessions.get(sessionId);
        if (context != null && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
            String workspacePath = (String) context.getMetadata().get("workspacePath");
            
            // Ensure workspace directory exists
            try {
                Files.createDirectories(Path.of(workspacePath));
            } catch (Exception e) {
                log.error("Error creating workspace directory: {}", workspacePath, e);
            }
            
            // Add workspace path to tool input if not present
            if (!input.containsKey("workspacePath")) {
                input.put("workspacePath", workspacePath);
            }
        }
        
        // Create a final copy of the input map for use in lambda
        final Map<String, Object> finalInput = new HashMap<>(input);
        
        // Execute the tool
        return tool.execute(finalInput)
                .collectList()
                .map(outputs -> {
                    StringBuilder result = new StringBuilder();
                    for (com.ai.developer.tools.ToolOutput output : outputs) {
                        result.append(output.getContent()).append("\n");
                    }
                    return result.toString().trim();
                })
                .onErrorResume(e -> {
                    log.error("Error executing tool: {}", e.getMessage(), e);
                    return Mono.just("Error executing tool: " + e.getMessage());
                });
    }
    
    /**
     * Get the workspace path for a session
     */
    public String getWorkspacePath(String sessionId) {
        ChatContext context = sessions.get(sessionId);
        if (context != null && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
            return (String) context.getMetadata().get("workspacePath");
        }
        
        // Return default workspace path if not found in context
        return DEFAULT_WORKSPACE_PATH + "/" + sessionId;
    }
    
    /**
     * Create a task-specific subdirectory within the session workspace
     */
    public String createTaskDirectory(String sessionId, String taskDir) {
        String workspacePath = getWorkspacePath(sessionId);
        String taskPath = workspacePath + "/" + taskDir;
        
        try {
            Files.createDirectories(Path.of(taskPath));
            log.info("Created task directory for session {}: {}", sessionId, taskPath);
            return taskPath;
        } catch (Exception e) {
            log.error("Error creating task directory for session {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Execute a tool call directly
     */
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        // Get the tool from the registry
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        // Ensure the arguments have sessionId
        Map<String, Object> enhancedArgs = new HashMap<>(arguments);
        if (!enhancedArgs.containsKey("sessionId")) {
            enhancedArgs.put("sessionId", sessionId);
        }
        
        // Get the workspace path from context metadata
        ChatContext context = sessions.get(sessionId);
        if (context != null && context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
            String workspacePath = (String) context.getMetadata().get("workspacePath");
            
            // Ensure workspace directory exists
            try {
                Files.createDirectories(Path.of(workspacePath));
            } catch (Exception e) {
                log.error("Error creating workspace directory: {}", workspacePath, e);
            }
            
            // Add workspace path to arguments if not present
            if (!enhancedArgs.containsKey("workspacePath")) {
                enhancedArgs.put("workspacePath", workspacePath);
            }
        }
        
        // Create a final copy of the enhanced arguments for use in lambda
        final Map<String, Object> finalArgs = new HashMap<>(enhancedArgs);
        
        // Execute the tool
        return tool.execute(finalArgs)
                .doOnNext(output -> log.info("Tool {} output: {}", toolName, output.getContent()))
                .doOnError(e -> log.error("Error executing tool {}: {}", toolName, e.getMessage(), e));
    }
}
