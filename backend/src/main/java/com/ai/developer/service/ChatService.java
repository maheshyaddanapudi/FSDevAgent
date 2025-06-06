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
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
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
            context.setMetadata(new HashMap<>());
        }
        
        if (!context.getMetadata().containsKey("workspacePath")) {
            String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
            context.getMetadata().put("workspacePath", workspacePath);
            log.info("Added missing workspace path for session {}: {}", sessionId, workspacePath);
        }
        
        // Add user message to context
        Message userMessage = Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build();
        context.getMessages().add(userMessage);
        
        // Create a response flux that will emit the assistant's response
        return llmProvider.streamResponse(message, context)
                .doOnNext(chunk -> {
                    log.debug("Received chunk from LLM: {}", chunk);
                })
                .reduceWith(() -> new StringBuilder(), StringBuilder::append)
                .flatMapMany(completeResponse -> {
                    String responseText = completeResponse.toString();
                    log.info("LLM response completed for session {}", sessionId);
                    log.info("Complete LLM response for session {}: {}", sessionId, responseText);
                    
                    // Add assistant message to context
                    Message assistantMessage = Message.builder()
                            .role("assistant")
                            .content(responseText)
                            .timestamp(Instant.now())
                            .build();
                    context.getMessages().add(assistantMessage);
                    
                    // Check for tool use blocks in the response
                    log.info("Checking for tool use blocks in response for session {}", sessionId);
                    List<ToolUseBlock> toolUseBlocks = processResponseForToolUse(sessionId, responseText);
                    
                    if (!toolUseBlocks.isEmpty()) {
                        log.info("Found {} tool use blocks in response for session {}", toolUseBlocks.size(), sessionId);
                        
                        // Process each tool use block
                        return Flux.fromIterable(toolUseBlocks)
                                .concatMap(toolUseBlock -> processToolUseBlock(sessionId, toolUseBlock))
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
     * Process a response for tool use blocks
     */
    private List<ToolUseBlock> processResponseForToolUse(String sessionId, String response) {
        List<ToolUseBlock> toolUseBlocks = new ArrayList<>();
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        
        while (matcher.find()) {
            String toolUseJson = matcher.group(1);
            if (toolUseJson == null) {
                // This might be a content block format
                toolUseJson = matcher.group(0);
            }
            
            log.info("Found tool use block in response for session {}: {}", sessionId, toolUseJson);
            
            try {
                // Notify about tool use detection via WebSocket
                webSocketHandler.broadcastToolOutput("Tool use detected: " + toolUseJson);
                
                ToolUseBlock toolUseBlock = parseToolUseBlock(toolUseJson);
                if (toolUseBlock != null) {
                    toolUseBlocks.add(toolUseBlock);
                }
            } catch (Exception e) {
                log.error("Error parsing tool use block for session {}: {}", sessionId, e.getMessage(), e);
            }
        }
        
        return toolUseBlocks;
    }
    
    /**
     * Parse a tool use block from JSON
     */
    private ToolUseBlock parseToolUseBlock(String json) {
        log.info("Parsing tool use block from JSON: {}", json);
        try {
            // Try to parse as a direct tool use block
            Map<String, Object> toolUseMap = objectMapper.readValue(json, Map.class);
            String name = (String) toolUseMap.get("name");
            
            // Debug logging for args field
            log.info("Tool use map: {}", toolUseMap);
            log.info("Tool name: {}", name);
            log.info("Args field in map: {}", toolUseMap.get("args"));
            
            // Check for args or arguments field
            Map<String, Object> args = null;
            if (toolUseMap.containsKey("args")) {
                args = (Map<String, Object>) toolUseMap.get("args");
                log.info("Found args field: {}", args);
            } else if (toolUseMap.containsKey("arguments")) {
                args = (Map<String, Object>) toolUseMap.get("arguments");
                log.info("Found arguments field: {}", args);
            } else {
                log.warn("No args or arguments field found in tool use block");
            }
            
            // Initialize empty map if args is null
            if (args == null) {
                args = new HashMap<>();
                log.warn("Initializing empty args map for tool: {}", name);
            }
            
            ToolUseBlock block = new ToolUseBlock();
            block.setName(name);
            block.setArgs(args);
            log.info("Created ToolUseBlock: {}", block);
            return block;
        } catch (Exception e) {
            log.warn("Failed to parse direct tool use block: {}", e.getMessage());
            
            try {
                // Try to extract from content block format
                Map<String, Object> contentBlock = objectMapper.readValue(json, Map.class);
                log.info("Content block: {}", contentBlock);
                
                if (contentBlock.containsKey("content") && contentBlock.get("content") instanceof Map) {
                    Map<String, Object> content = (Map<String, Object>) contentBlock.get("content");
                    log.info("Content field: {}", content);
                    
                    if (content.containsKey("tool_use")) {
                        Map<String, Object> toolUse = (Map<String, Object>) content.get("tool_use");
                        String name = (String) toolUse.get("name");
                        
                        // Debug logging for arguments field
                        log.info("Tool use in content block: {}", toolUse);
                        log.info("Tool name in content block: {}", name);
                        log.info("Arguments field in content block: {}", toolUse.get("arguments"));
                        
                        // Check for arguments field
                        Map<String, Object> args = null;
                        if (toolUse.containsKey("arguments")) {
                            args = (Map<String, Object>) toolUse.get("arguments");
                            log.info("Found arguments field in content block: {}", args);
                        } else if (toolUse.containsKey("args")) {
                            args = (Map<String, Object>) toolUse.get("args");
                            log.info("Found args field in content block: {}", args);
                        } else {
                            log.warn("No arguments or args field found in content block tool use");
                        }
                        
                        // Initialize empty map if args is null
                        if (args == null) {
                            args = new HashMap<>();
                            log.warn("Initializing empty args map for tool in content block: {}", name);
                        }
                        
                        ToolUseBlock block = new ToolUseBlock();
                        block.setName(name);
                        block.setArgs(args);
                        log.info("Created ToolUseBlock from content block: {}", block);
                        return block;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to parse content block format: {}", ex.getMessage(), ex);
            }
        }
        
        log.error("Failed to parse tool use block, returning null");
        return null;
    }
    
    /**
     * Process a tool use block
     */
    private Flux<ChatResponse> processToolUseBlock(String sessionId, ToolUseBlock toolUseBlock) {
        String toolName = toolUseBlock.getName();
        Map<String, Object> args = toolUseBlock.getArgs();
        
        // Enhanced debug logging for tool use block
        log.info("Raw tool use block for session {}: {}", sessionId, toolUseBlock);
        log.info("Tool name: {}, Args object type: {}", toolName, args != null ? args.getClass().getName() : "null");
        
        // Ensure args is never null to prevent NullPointerException
        if (args == null) {
            args = new HashMap<>();
            log.warn("Null arguments detected in tool use block for session {}: tool={}, initializing empty map", sessionId, toolName);
        } else {
            // Log detailed argument information
            log.info("Arguments keys: {}", args.keySet());
            if (toolName.equals("planning_tool")) {
                log.info("Planning tool operation: {}", args.get("operation"));
                log.info("Planning tool objective: {}", args.get("objective"));
                log.info("Planning tool title: {}", args.get("title"));
                log.info("Planning tool type: {}", args.get("type"));
            }
        }
        
        // Create a final reference to args for use in lambda expressions
        final Map<String, Object> finalArgs = args;
        
        log.info("Processing tool use block for session {}: tool={}, arguments={}", sessionId, toolName, finalArgs);
        
        // Notify about tool execution start via WebSocket
        webSocketHandler.broadcastToolOutput("Executing tool: " + toolName);
        
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.warn("Tool not found for session {}: {}", sessionId, toolName);
            
            String errorMessage = "Tool not found: " + toolName;
            
            // Notify about tool execution error via WebSocket
            webSocketHandler.broadcastToolOutput(errorMessage);
            
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("tool")
                    .message(errorMessage)
                    .timestamp(Instant.now())
                    .build());
        }
        
        try {
            // Get workspace path from session metadata
            ChatContext context = sessions.get(sessionId);
            String workspacePath = (String) context.getMetadata().getOrDefault("workspacePath", DEFAULT_WORKSPACE_PATH + "/" + sessionId);
            
            // Execute tool with guaranteed non-null arguments
            Flux<ToolOutput> outputFlux = tool.execute(finalArgs);
            
            return outputFlux.flatMap(output -> {
                // Add tool response to context
                Message toolMessage = Message.builder()
                        .role("tool")
                        .content(output.getContent())
                        .timestamp(Instant.now())
                        .build();
                context.getMessages().add(toolMessage);
                
                // Notify about tool execution result via WebSocket
                webSocketHandler.broadcastToolOutput(output);
                
                // Return tool response
                return Mono.just(ChatResponse.builder()
                        .sessionId(sessionId)
                        .role("tool")
                        .message(output.getContent())
                        .timestamp(Instant.now())
                        .build());
            }).switchIfEmpty(Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("tool")
                    .message("Tool execution completed with no output")
                    .timestamp(Instant.now())
                    .build()));
        } catch (Exception e) {
            log.error("Error executing tool for session {}: {}", sessionId, e.getMessage(), e);
            
            String errorMessage = "Error executing tool " + toolName + ": " + e.getMessage();
            
            // Notify about tool execution error via WebSocket
            webSocketHandler.broadcastToolOutput(errorMessage);
            
            return Flux.just(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("tool")
                    .message(errorMessage)
                    .timestamp(Instant.now())
                    .build());
        }
    }
    
    /**
     * Execute a tool directly
     */
    public Mono<ToolCallResponse> executeTool(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool directly for session {}: tool={}, arguments={}", sessionId, toolName, arguments);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.warn("Tool not found: {}", toolName);
            return Mono.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        try {
            // Get workspace path from session metadata
            String workspacePath = (String) context.getMetadata().getOrDefault("workspacePath", DEFAULT_WORKSPACE_PATH + "/" + sessionId);
            
            // Execute tool
            return tool.execute(arguments)
                .next()
                .map(output -> {
                    // Add tool response to context
                    Message toolMessage = Message.builder()
                            .role("tool")
                            .content(output.getContent())
                            .timestamp(Instant.now())
                            .build();
                    context.getMessages().add(toolMessage);
                    
                    // Return tool response
                    return ToolCallResponse.builder()
                            .name(toolName)
                            .arguments(arguments)
                            .result(output.getContent())
                            .build();
                });
        } catch (Exception e) {
            log.error("Error executing tool: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
}
