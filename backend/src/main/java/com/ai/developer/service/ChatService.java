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
     * Create a new session
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        
        ChatContext context = new ChatContext();
        context.setSystemPrompt("You are an AI Developer Agent, designed to help with coding, debugging, and using various development tools.");
        context.setMessages(new ArrayList<>());
        
        sessions.put(sessionId, context);
        
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
                
                // Replace the tool use marker with a cleaner message
                String cleanedResponse = response.replace(matcher.group(0), 
                        "\n\nI'll use the " + toolUseBlock.getName() + " tool to help with this.\n\n");
                
                // Create the initial response
                ChatResponse initialResponse = ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(cleanedResponse)
                        .timestamp(Instant.now())
                        .build();
                
                // ADDED DEBUG LOGGING: Log that we're handling the tool use
                log.info("Handling tool use for tool {} in session {}", toolUseBlock.getName(), sessionId);
                
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
                                    .message("\n\n**Tool Result:**\n```\n" + toolResult + "\n```\n\n")
                                    .toolCallId(toolUseBlock.getId())
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
                                    .message(cleanedResponse + "\n\nError executing tool: " + e.getMessage())
                                    .timestamp(Instant.now())
                                    .build());
                        });
                
            } catch (JsonProcessingException e) {
                log.error("Error parsing tool use JSON: {}", e.getMessage());
                return Flux.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("assistant")
                        .message(response)
                        .timestamp(Instant.now())
                        .build());
            }
        } else {
            // ADDED DEBUG LOGGING: Log that no tool use blocks were found
            log.info("No tool use blocks found in response for session {}", sessionId);
            
            // No tool use blocks, just return the response
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message(response)
                    .timestamp(Instant.now())
                    .build());
        }
    }
    
    /**
     * Process a user message and get a response (legacy method signature)
     */
    public Flux<String> processMessage(String sessionId, String message) {
        log.info("Processing message for session {} (legacy method): {}", sessionId, message);
        
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .message(message)
                .build();
                
        return processMessage(request)
                .map(ChatResponse::getMessage);
    }
    
    /**
     * Update the assistant's message in the context
     */
    private void updateAssistantMessage(ChatContext context, String chunk) {
        List<Message> messages = context.getMessages();
        
        // Check if we already have an assistant message as the last message
        if (!messages.isEmpty() && "assistant".equals(messages.get(messages.size() - 1).getRole())) {
            // Update existing message
            Message lastMessage = messages.get(messages.size() - 1);
            String updatedContent = lastMessage.getContent();
            if (updatedContent == null) {
                updatedContent = chunk;
            } else {
                updatedContent += chunk;
            }
            
            messages.set(messages.size() - 1, Message.builder()
                    .role("assistant")
                    .content(updatedContent)
                    .timestamp(lastMessage.getTimestamp())
                    .build());
        } else {
            // Add new assistant message
            messages.add(Message.builder()
                    .role("assistant")
                    .content(chunk)
                    .timestamp(Instant.now())
                    .build());
        }
    }
    
    /**
     * Handle a tool use request synchronously (deprecated - use handleToolUse instead)
     * @deprecated This method uses blocking operations which are not compatible with reactive contexts
     */
    @Deprecated
    private String handleToolUseSync(String sessionId, ToolUseBlock toolUseBlock) {
        log.warn("Using deprecated synchronous tool handling for session {}: {}", sessionId, toolUseBlock);
        return handleToolUse(sessionId, toolUseBlock)
                .block(); // Only use in non-reactive contexts
    }
    
    /**
     * Handle a tool use request in a non-blocking way
     */
    private Mono<String> handleToolUse(String sessionId, ToolUseBlock toolUseBlock) {
        log.info("Handling tool use reactively for session {}: {}", sessionId, toolUseBlock);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.error("Session not found: {}", sessionId);
            return Mono.just("Error: Session not found");
        }
        
        // Create a tool call message to add to the context
        String toolCallId = toolUseBlock.getId();
        
        try {
            // Convert input to Map if it's a string
            Map<String, Object> inputMap = new HashMap<>();
            Object input = toolUseBlock.getInput();
            
            if (input instanceof String) {
                // Parse the string as JSON to a Map
                try {
                    inputMap = objectMapper.readValue((String)input, Map.class);
                } catch (Exception e) {
                    log.error("Error parsing tool input as JSON: {}", e.getMessage());
                    inputMap.put("input", input);
                }
            } else if (input instanceof Map) {
                // Already a Map, just cast it
                inputMap = (Map<String, Object>) input;
            } else if (input != null) {
                // Not a string or map, but not null - add as generic input
                inputMap.put("input", input.toString());
            }
            
            String argumentsJson = objectMapper.writeValueAsString(inputMap);
            
            // Add tool call message to context
            Message toolCallMessage = Message.builder()
                    .role("assistant")
                    .toolCallId(toolCallId)
                    .toolCall(ToolCall.builder()
                            .id(toolCallId)
                            .name(toolUseBlock.getName())
                            .arguments(argumentsJson)
                            .build())
                    .timestamp(Instant.now())
                    .build();
                    
            context.getMessages().add(toolCallMessage);
            log.info("Added tool call message to context for tool: {}", toolUseBlock.getName());
            
            // ADDED DEBUG LOGGING: Log that we're about to execute the tool
            log.info("Executing tool {} for session {} with arguments: {}", toolUseBlock.getName(), sessionId, inputMap);
            
            // Execute the tool and collect results in a non-blocking way
            return executeToolCall(sessionId, toolUseBlock.getName(), inputMap)
                    .collectList()
                    .flatMap(outputs -> {
                        // ADDED DEBUG LOGGING: Log that tool execution is complete
                        log.info("Tool {} execution completed for session {} with {} outputs", toolUseBlock.getName(), sessionId, outputs != null ? outputs.size() : 0);
                        
                        // Combine all outputs into a single string
                        StringBuilder result = new StringBuilder();
                        if (outputs != null) {
                            for (com.ai.developer.tools.ToolOutput output : outputs) {
                                result.append(output.getContent()).append("\n");
                            }
                        } else {
                            result.append("No output from tool execution");
                        }                 
                        // Add tool result message to context
                        Message toolResultMessage = Message.builder()
                                .role("tool")
                                .toolCallId(toolCallId)
                                .content(result.toString())
                                .timestamp(Instant.now())
                                .build();
                                
                        context.getMessages().add(toolResultMessage);
                        log.info("Added tool result message to context for tool: {}", toolUseBlock.getName());
                        
                        return Mono.just(result.toString());
                    })
                    .onErrorResume(e -> {
                        log.error("Error executing tool: {}", e.getMessage(), e);
                        return Mono.just("Error executing tool: " + e.getMessage());
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing tool arguments: {}", e.getMessage());
            return Mono.just("Error executing tool: " + e.getMessage());
        }
    }
    
    /**
     * Execute a tool call and return the results
     */
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.error("Session not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        // Create a tool call message to add to the context
        String toolCallId = UUID.randomUUID().toString();
        
        try {
            // ADDED DEBUG LOGGING: Log that we're about to execute the tool
            log.info("Executing tool {} with registry for session {}", toolName, sessionId);
            
            // Execute the tool and collect results
            return toolRegistry.executeTool(toolName, arguments)
                    .doOnNext(output -> {
                        // ADDED DEBUG LOGGING: Log each tool output
                        log.debug("Tool {} output for session {}: {}", toolName, sessionId, output);
                        
                        // Send tool output to WebSocket
                        try {
                            Map<String, Object> toolOutput = new HashMap<>();
                            toolOutput.put("sessionId", sessionId);
                            toolOutput.put("toolName", toolName);
                            toolOutput.put("toolCallId", toolCallId);
                            toolOutput.put("args", arguments);
                            toolOutput.put("output", output);
                            toolOutput.put("timestamp", Instant.now());
                            
                            log.info("Broadcasting tool output: {}", toolOutput);
                            webSocketHandler.broadcastToolOutput(toolOutput);
                            log.info("Broadcasted tool output for tool: {}", toolName);
                        } catch (Exception e) {
                            log.error("Error broadcasting tool output: {}", e.getMessage(), e);
                        }
                    })
                    .doOnComplete(() -> {
                        // ADDED DEBUG LOGGING: Log completion of tool execution
                        log.info("Tool {} execution completed for session {}", toolName, sessionId);
                    })
                    .doOnError(error -> {
                        // ADDED DEBUG LOGGING: Log any errors during tool execution
                        log.error("Error during tool {} execution for session {}: {}", toolName, sessionId, error.getMessage(), error);
                    });
        } catch (Exception e) {
            log.error("Error executing tool: {}", e.getMessage(), e);
            return Flux.error(e);
        }
    }
}
