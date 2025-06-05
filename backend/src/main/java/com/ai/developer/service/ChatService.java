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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for handling chat interactions.
 * Enhanced to support multi-turn conversations and agentic framework.
 */
@Service
@Slf4j
public class ChatService {
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ToolOutputWebSocketHandler webSocketHandler;
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> sessionLastAccessed = new ConcurrentHashMap<>();
    
    // Pattern to match tool use blocks in LLM responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    
    // Pattern to match references to previous messages or entities
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\[([^\\]]+)\\]|\"([^\"]+)\"");
    
    // Default session timeout (30 minutes)
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);
    
    // Default max context size
    private static final int DEFAULT_MAX_CONTEXT_SIZE = 20;
    
    public ChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, ToolOutputWebSocketHandler webSocketHandler) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        
        log.info("ChatService initialized with LLM provider: {}", llmProvider.getClass().getSimpleName());
        log.info("Available tools: {}", toolRegistry.getToolNames());
        
        // Start session cleanup task
        startSessionCleanupTask();
    }
    
    /**
     * Start a background task to clean up expired sessions
     */
    private void startSessionCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    // Sleep for 5 minutes
                    Thread.sleep(5 * 60 * 1000);
                    
                    // Get current time
                    Instant now = Instant.now();
                    
                    // Find expired sessions
                    List<String> expiredSessions = sessionLastAccessed.entrySet().stream()
                            .filter(entry -> Duration.between(entry.getValue(), now).compareTo(SESSION_TIMEOUT) > 0)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    
                    // Remove expired sessions
                    for (String sessionId : expiredSessions) {
                        log.info("Removing expired session: {}", sessionId);
                        sessions.remove(sessionId);
                        sessionLastAccessed.remove(sessionId);
                    }
                    
                    log.debug("Session cleanup completed. Removed {} expired sessions. Current session count: {}", 
                            expiredSessions.size(), sessions.size());
                } catch (InterruptedException e) {
                    log.error("Session cleanup task interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in session cleanup task", e);
                }
            }
        });
        
        cleanupThread.setDaemon(true);
        cleanupThread.setName("SessionCleanupThread");
        cleanupThread.start();
        
        log.info("Session cleanup task started");
    }
    
    /**
     * Create a defensive copy of a chat context
     */
    private ChatContext createDefensiveCopy(ChatContext context) {
        ChatContext copy = new ChatContext();
        copy.setSystemPrompt(context.getSystemPrompt());
        copy.setSessionId(context.getSessionId());
        copy.setMaxContextSize(context.getMaxContextSize());
        
        // Copy messages
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
        
        // Copy metadata
        if (context.getMetadata() != null) {
            Map<String, Object> metadataCopy = new HashMap<>(context.getMetadata());
            copy.setMetadata(metadataCopy);
        }
        
        return copy;
    }
    
    /**
     * Update the last accessed time for a session
     */
    private void updateSessionLastAccessed(String sessionId) {
        sessionLastAccessed.put(sessionId, Instant.now());
    }
    
    /**
     * Create a new session
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        
        ChatContext context = new ChatContext();
        context.setSessionId(sessionId);
        context.setSystemPrompt("You are an AI Developer Agent, designed to help with coding, debugging, and using various development tools.");
        context.setMessages(new ArrayList<>());
        context.setMaxContextSize(DEFAULT_MAX_CONTEXT_SIZE);
        
        // Add metadata for agent capabilities
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentCapabilities", List.of(
            "coding", "debugging", "tool_use", "planning", "documentation"
        ));
        metadata.put("preferredLanguages", List.of(
            "java", "javascript", "python", "typescript"
        ));
        context.setMetadata(metadata);
        
        // Store the session
        sessions.put(sessionId, context);
        updateSessionLastAccessed(sessionId);
        
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
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
        
        // Update last accessed time
        updateSessionLastAccessed(sessionId);
        
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
        
        // Update last accessed time
        updateSessionLastAccessed(sessionId);
        
        List<ChatResponse> history = new ArrayList<>();
        for (Message message : context.getMessages()) {
            ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                    .sessionId(sessionId)
                    .role(message.getRole())
                    .message(message.getContent())
                    .timestamp(message.getTimestamp());
            
            // Add tool call information if available
            if (message.getToolCall() != null) {
                builder.toolCall(ToolCallResponse.builder()
                        .name(message.getToolCall().getName())
                        .arguments(objectMapper.convertValue(message.getToolCall().getArguments(), Map.class))
                        .build());
            }
            
            // Add tool call ID if available
            if (message.getToolCallId() != null) {
                builder.toolCallId(message.getToolCallId());
            }
            
            history.add(builder.build());
        }
        
        return Mono.just(history);
    }
    
    /**
     * Delete a session
     */
    public boolean deleteSession(String sessionId) {
        ChatContext removed = sessions.remove(sessionId);
        if (removed != null) {
            sessionLastAccessed.remove(sessionId);
            log.info("Deleted session: {}", sessionId);
            return true;
        }
        
        log.warn("Session not found for deletion: {}", sessionId);
        return false;
    }
    
    /**
     * Process references in a message
     * Looks for references to previous messages or entities and resolves them
     */
    private String processReferences(String message, ChatContext context) {
        if (message == null || context == null) {
            return message;
        }
        
        // Find all potential references
        Matcher matcher = REFERENCE_PATTERN.matcher(message);
        StringBuffer processedMessage = new StringBuffer();
        
        while (matcher.find()) {
            String reference = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (reference == null) {
                continue;
            }
            
            // Find matching messages
            List<Message> matches = context.findReferences(reference);
            
            if (!matches.isEmpty()) {
                // Replace reference with a more explicit reference
                String replacement = "[Reference to: " + reference + "]";
                matcher.appendReplacement(processedMessage, replacement);
                
                // Add reference information to context metadata
                Map<String, Object> metadata = context.getMetadata();
                if (!metadata.containsKey("references")) {
                    metadata.put("references", new HashMap<String, List<String>>());
                }
                
                @SuppressWarnings("unchecked")
                Map<String, List<String>> references = (Map<String, List<String>>) metadata.get("references");
                
                if (!references.containsKey(reference)) {
                    references.put(reference, new ArrayList<>());
                }
                
                // Add message content snippets to references
                for (Message match : matches) {
                    String content = match.getContent();
                    if (content != null && content.length() > 100) {
                        content = content.substring(0, 97) + "...";
                    }
                    references.get(reference).add(content);
                }
            }
        }
        
        matcher.appendTail(processedMessage);
        return processedMessage.toString();
    }
    
    /**
     * Update the assistant's message in the context
     */
    private void updateAssistantMessage(ChatContext context, String chunk) {
        // Check if there's already an assistant message at the end
        List<Message> messages = context.getMessages();
        if (!messages.isEmpty() && "assistant".equals(messages.get(messages.size() - 1).getRole())) {
            // Update the existing message
            Message lastMessage = messages.get(messages.size() - 1);
            String updatedContent = lastMessage.getContent() + chunk;
            
            // Replace the message with an updated one
            messages.set(messages.size() - 1, Message.builder()
                    .role(lastMessage.getRole())
                    .content(updatedContent)
                    .toolCallId(lastMessage.getToolCallId())
                    .toolCall(lastMessage.getToolCall())
                    .timestamp(lastMessage.getTimestamp())
                    .build());
        } else {
            // Add a new assistant message
            context.addMessage(Message.builder()
                    .role("assistant")
                    .content(chunk)
                    .timestamp(Instant.now())
                    .build());
        }
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
        
        // Update last accessed time
        updateSessionLastAccessed(sessionId);
        
        // Process references in the message
        String processedMessage = processReferences(message, context);
        
        // Add user message to context
        context.addMessage(Message.builder()
                .role("user")
                .content(processedMessage)
                .timestamp(Instant.now())
                .build());
        
        // Log the current context
        log.debug("Current context for session {}: {} messages", sessionId, context.getMessages().size());
        
        // ADDED DEBUG LOGGING: Log that we're about to call the LLM provider
        log.info("Calling LLM provider for session {}", sessionId);
        
        // Get streaming response from LLM using reactive API
        final StringBuilder responseBuilder = new StringBuilder();
        
        // Process the streaming response
        return llmProvider.streamResponse(processedMessage, context)
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
                        
                        // Create the initial response with the tool call
                        ChatResponse initialResponse = ChatResponse.builder()
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
                            
                            // Handle the tool use in a non-blocking way and chain the result
                            return handleToolUse(sessionId, toolUseBlock)
                                    .flatMapMany(toolResult -> {
                                        // ADDED DEBUG LOGGING: Log the tool result
                                        log.info("Tool {} execution completed for session {}: {}", toolUseBlock.getName(), sessionId, toolResult);
                                        
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
                                                .toolCallId(toolUseBlock.getId())
                                                .timestamp(Instant.now())
                                                .build();
                                        
                                        // Add tool response to context
                                        context.addMessage(Message.builder()
                                                .role("tool")
                                                .content(toolResult)
                                                .toolCallId(toolUseBlock.getId())
                                                .timestamp(Instant.now())
                                                .build());
                                        
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
                
                // Clean the response by removing the tool use block
                String cleanedResponse = response.replaceAll("<tool_use>.*?</tool_use>", "").trim();
                
                // Create the initial response with the tool call
                ChatResponse initialResponse = ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(cleanedResponse)
                        .toolCall(ToolCallResponse.builder()
                                .name(toolUseBlock.getName())
                                .arguments(objectMapper.convertValue(toolUseBlock.getInput(), Map.class))
                                .build())
                        .timestamp(Instant.now())
                        .build();
                
                // Add assistant message to context
                context.addMessage(Message.builder()
                        .role("assistant")
                        .content(cleanedResponse)
                        .toolCall(ToolCall.builder()
                                .name(toolUseBlock.getName())
                                .arguments(objectMapper.writeValueAsString(convertInputToMap(toolUseBlock.getInput())))
                                .build())
                        .timestamp(Instant.now())
                        .build());
                
                // Handle the tool use in a non-blocking way and chain the result
                return handleToolUse(sessionId, toolUseBlock)
                        .flatMapMany(toolResult -> {
                            // ADDED DEBUG LOGGING: Log the tool result
                            log.info("Tool {} execution completed for session {}: {}", toolUseBlock.getName(), sessionId, toolResult);
                            
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
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build();
                            
                            // Add tool response to context
                            context.addMessage(Message.builder()
                                    .role("tool")
                                    .content(toolResult)
                                    .toolCallId(toolUseBlock.getId())
                                    .timestamp(Instant.now())
                                    .build());
                            
                            // Return both responses as a flux
                            return Flux.just(initialResponse, toolResponse);
                        })
                        .onErrorResume(e -> {
                            log.error("Error handling tool use: {}", e.getMessage());
                            return Flux.just(ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .message(cleanedResponse + "\n\nError executing tool: " + e.getMessage())
                                    .timestamp(Instant.now())
                                    .build());
                        });
            } catch (JsonProcessingException e) {
                log.error("Error parsing tool use block: {}", e.getMessage());
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(response)
                        .timestamp(Instant.now())
                        .build());
            }
        } else {
            // No tool use blocks found, return the response as is
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message(response)
                    .timestamp(Instant.now())
                    .build());
        }
    }
    
    /**
     * Handle a tool use block
     */
    private Mono<String> handleToolUse(String sessionId, ToolUseBlock toolUseBlock) {
        log.info("Handling tool use for session {}: {}", sessionId, toolUseBlock.getName());
        
        // Get the tool from the registry
        Tool tool = toolRegistry.getTool(toolUseBlock.getName());
        if (tool == null) {
            log.error("Tool not found: {}", toolUseBlock.getName());
            return Mono.just("Error: Tool not found: " + toolUseBlock.getName());
        }
        
        // Execute the tool
        try {
            // Convert input to arguments map - FIX: Use objectMapper to convert Object to Map
            Map<String, Object> arguments;
            Object input = toolUseBlock.getInput();
            
            if (input instanceof Map) {
                // If input is already a Map, use it directly
                @SuppressWarnings("unchecked")
                Map<String, Object> inputMap = (Map<String, Object>) input;
                arguments = inputMap;
            } else if (input instanceof String) {
                // If input is a String, try to parse it as JSON
                String inputStr = (String) input;
                try {
                    arguments = objectMapper.readValue(inputStr, Map.class);
                } catch (Exception e) {
                    // If parsing fails, create a simple map with the string as content
                    arguments = new HashMap<>();
                    arguments.put("content", inputStr);
                }
            } else {
                // For any other type, convert using objectMapper
                arguments = objectMapper.convertValue(input, Map.class);
            }
            
            // Execute the tool and collect the results
            return tool.execute(arguments)
                    .collectList()
                    .map(outputs -> {
                        // Combine all outputs into a single string
                        StringBuilder result = new StringBuilder();
                        for (com.ai.developer.tools.ToolOutput output : outputs) {
                            if (output.getContent() != null) {
                                result.append(output.getContent()).append("\n");
                            }
                        }
                        return result.toString().trim();
                    })
                    .onErrorResume(e -> {
                        log.error("Error executing tool {}: {}", toolUseBlock.getName(), e.getMessage());
                        return Mono.just("Error executing tool: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolUseBlock.getName(), e.getMessage());
            return Mono.just("Error executing tool: " + e.getMessage());
        }
    }
    
    /**
     * Execute a tool call directly
     * This method is used by the controller to execute a tool directly
     */
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool call for session {}: {} with arguments: {}", sessionId, toolName, arguments);
        
        // Get the tool from the registry
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        // Update last accessed time for the session
        updateSessionLastAccessed(sessionId);
        
        // Execute the tool
        return tool.execute(arguments)
                .doOnNext(output -> {
                    // Send tool output to WebSocket for emulator visualization
                    try {
                        String toolOutputJson = objectMapper.writeValueAsString(output);
                        webSocketHandler.broadcastToolOutput(toolOutputJson);
                    } catch (Exception e) {
                        log.error("Error broadcasting tool output to WebSocket: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> log.info("Tool execution completed for session {}: {}", sessionId, toolName))
                .doOnError(error -> log.error("Error executing tool {} for session {}: {}", toolName, sessionId, error.getMessage()));
    }
    
    /**
     * Find references in conversation history
     */
    public Mono<List<Message>> findReferences(String sessionId, String query) {
        log.info("Finding references for session {} with query: {}", sessionId, query);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Update last accessed time
        updateSessionLastAccessed(sessionId);
        
        // Find references
        List<Message> references = context.findReferences(query);
        
        return Mono.just(references);
    }
    
    /**
     * Helper method to convert input object to Map<String, Object>
     * Handles different input types safely
     */
    private Map<String, Object> convertInputToMap(Object input) {
        if (input == null) {
            return new HashMap<>();
        }
        
        if (input instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) input;
            return inputMap;
        } else if (input instanceof String) {
            // If input is a String, try to parse it as JSON
            String inputStr = (String) input;
            try {
                return objectMapper.readValue(inputStr, Map.class);
            } catch (Exception e) {
                // If parsing fails, create a simple map with the string as content
                Map<String, Object> result = new HashMap<>();
                result.put("content", inputStr);
                return result;
            }
        } else {
            // For any other type, convert using objectMapper
            return objectMapper.convertValue(input, Map.class);
        }
    }
}
