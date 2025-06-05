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
 * Updated to support workspace directory strategy for session isolation.
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
    
    // Base workspace directory
    private static final String BASE_WORKSPACE_DIR = "/tmp/ai-developer-agent/";
    
    public ChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, ToolOutputWebSocketHandler webSocketHandler) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        
        log.info("ChatService initialized with LLM provider: {}", llmProvider.getClass().getSimpleName());
        log.info("Available tools: {}", toolRegistry.getToolNames());
        
        // Create base workspace directory if it doesn't exist
        createBaseWorkspaceDirectory();
        
        // Start session cleanup task
        startSessionCleanupTask();
    }
    
    /**
     * Create the base workspace directory if it doesn't exist
     */
    private void createBaseWorkspaceDirectory() {
        File baseDir = new File(BASE_WORKSPACE_DIR);
        if (!baseDir.exists()) {
            if (baseDir.mkdirs()) {
                log.info("Created base workspace directory: {}", BASE_WORKSPACE_DIR);
            } else {
                log.error("Failed to create base workspace directory: {}", BASE_WORKSPACE_DIR);
            }
        }
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
        copy.setWorkspaceDirectory(context.getWorkspaceDirectory());
        
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
        
        ChatContext context = new ChatContext(sessionId);
        context.setSystemPrompt("You are an AI Developer Agent, designed to help with coding, debugging, and using various development tools. " +
                "You have access to a workspace directory at " + context.getWorkspaceDirectory() + " where you can create and organize files. " +
                "You can create task-specific subdirectories within this workspace for better organization. " +
                "Use the workspace for all file operations and command executions to maintain session isolation.");
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
        metadata.put("workspace", context.getWorkspaceDirectory());
        context.setMetadata(metadata);
        
        // Store the session
        sessions.put(sessionId, context);
        updateSessionLastAccessed(sessionId);
        
        return Mono.just(SessionResponse.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .workspace(context.getWorkspaceDirectory())
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
                // Deserialize arguments from JSON string back to Map for response
                Map<String, Object> argumentsMap = new HashMap<>();
                if (message.getToolCall().getArguments() != null) {
                    try {
                        argumentsMap = objectMapper.readValue(message.getToolCall().getArguments(), Map.class);
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing tool call arguments: {}", e.getMessage());
                    }
                }
                
                builder.toolCall(ToolCallResponse.builder()
                        .name(message.getToolCall().getName())
                        .arguments(argumentsMap)
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
        
        // Add workspace information to the message if not already present
        if (!processedMessage.contains("workspace") && context.getWorkspaceDirectory() != null) {
            processedMessage = processedMessage + "\n\nYou have access to a workspace directory at " + 
                context.getWorkspaceDirectory() + " for file operations and command executions.";
        }
        
        // Add user message to context
        context.addMessage(Message.builder()
                .role("user")
                .content(processedMessage)
                .timestamp(Instant.now())
                .build());
        
        // Get response from LLM provider
        return llmProvider.streamResponse(processedMessage, context)
                .map(chunk -> {
                    // Check for tool use blocks
                    Matcher matcher = TOOL_USE_PATTERN.matcher(chunk);
                    if (matcher.find()) {
                        // Extract tool use block
                        String toolUseBlock = matcher.group(1);
                        if (toolUseBlock == null) {
                            toolUseBlock = matcher.group(0);
                        }
                        
                        try {
                            // Parse tool use block
                            ToolUseBlock toolUse = objectMapper.readValue(toolUseBlock, ToolUseBlock.class);
                            
                            // Execute tool
                            ChatResponse toolResponse = executeTool(toolUse.getName(), 
                                                                  (Map<String, Object>)toolUse.getInput(), 
                                                                  sessionId, context);
                            
                            // Return tool response
                            return toolResponse;
                        } catch (Exception e) {
                            log.error("Error parsing tool use block: {}", e.getMessage());
                            return ChatResponse.builder()
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .message("Error parsing tool use block: " + e.getMessage())
                                    .timestamp(Instant.now())
                                    .build();
                        }
                    } else {
                        // Update assistant message in context
                        updateAssistantMessage(context, chunk);
                        
                        // Return response
                        return ChatResponse.builder()
                                .sessionId(sessionId)
                                .role("assistant")
                                .message(chunk)
                                .timestamp(Instant.now())
                                .build();
                    }
                });
    }
    
    /**
     * Execute a tool
     */
    public ChatResponse executeTool(String toolName, Map<String, Object> arguments, String sessionId, ChatContext context) {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);
        
        // Get the tool
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("Error: Tool not found: " + toolName)
                    .timestamp(Instant.now())
                    .build();
        }
        
        try {
            // Add workspace directory to arguments if not present
            if (arguments != null && !arguments.containsKey("workspace") && context.getWorkspaceDirectory() != null) {
                arguments.put("workspace", context.getWorkspaceDirectory());
            }
            
            // Add sessionId to arguments if not present
            if (arguments != null && !arguments.containsKey("sessionId") && sessionId != null) {
                arguments.put("sessionId", sessionId);
            }
            
            // Process arguments to resolve paths relative to workspace
            processToolArguments(arguments, context);
            
            // Execute the tool - serialize arguments to JSON string
            String argumentsJson;
            try {
                argumentsJson = objectMapper.writeValueAsString(arguments);
            } catch (JsonProcessingException e) {
                log.error("Error serializing tool arguments: {}", e.getMessage());
                argumentsJson = "{}"; // fallback to empty JSON
            }
            
            ToolCall toolCall = ToolCall.builder()
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build();
            
            // Add tool call to context
            String toolCallId = UUID.randomUUID().toString();
            
            // Add message with tool call but WITHOUT content
            context.addMessage(Message.builder()
                    .role("assistant")
                    .toolCall(toolCall)
                    .toolCallId(toolCallId)
                    .timestamp(Instant.now())
                    .build());
            
            // Execute tool and collect results
            Flux<com.ai.developer.tools.ToolOutput> resultFlux = tool.execute(arguments);
            
            // Convert to a list for easier handling
            List<com.ai.developer.tools.ToolOutput> results = resultFlux.collectList().block();
            
            // Convert result to string for storage in context
            String resultStr;
            try {
                resultStr = objectMapper.writeValueAsString(results);
                
                // Add tool result to context - use 'user' role instead of 'tool' for Claude API compatibility
                context.addMessage(Message.builder()
                        .role("user")
                        .content(resultStr)  // Using serialized string for content
                        .toolCallId(toolCallId)
                        .timestamp(Instant.now())
                        .build());
            } catch (JsonProcessingException e) {
                log.error("Error serializing results: {}", e.getMessage());
            }
            
            // Send tool output to WebSocket
            if (results != null && !results.isEmpty()) {
                for (com.ai.developer.tools.ToolOutput result : results) {
                    webSocketHandler.sendToolOutput(sessionId, result);
                }
            }
            
            // Serialize results to JSON string for ToolCallResponse
            String resultJson;
            try {
                resultJson = objectMapper.writeValueAsString(results);
            } catch (JsonProcessingException e) {
                log.error("Error serializing tool results: {}", e.getMessage());
                resultJson = "[]"; // fallback to empty array
            }
            
            // Return tool call response
            return ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .toolCall(ToolCallResponse.builder()
                            .name(toolName)
                            .arguments(arguments)
                            .result(resultJson)
                            .build())
                    .timestamp(Instant.now())
                    .build();
        } catch (Exception e) {
            log.error("Error executing tool: {}", e.getMessage());
            return ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message("Error executing tool: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * Execute a tool call directly (for API endpoints)
     */
    public Flux<com.ai.developer.tools.ToolOutput> executeToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool call: {} for session {} with arguments: {}", toolName, sessionId, arguments);
        
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Update last accessed time
        updateSessionLastAccessed(sessionId);
        
        // Get the tool
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        try {
            // Add workspace directory to arguments if not present
            if (arguments != null && !arguments.containsKey("workspace") && context.getWorkspaceDirectory() != null) {
                arguments.put("workspace", context.getWorkspaceDirectory());
            }
            
            // Add sessionId to arguments if not present
            if (arguments != null && !arguments.containsKey("sessionId") && sessionId != null) {
                arguments.put("sessionId", sessionId);
            }
            
            // Process arguments to resolve paths relative to workspace
            processToolArguments(arguments, context);
            
            // Execute the tool - serialize arguments to JSON string
            String argumentsJson;
            try {
                argumentsJson = objectMapper.writeValueAsString(arguments);
            } catch (JsonProcessingException e) {
                log.error("Error serializing tool arguments: {}", e.getMessage());
                argumentsJson = "{}"; // fallback to empty JSON
            }
            
            ToolCall toolCall = ToolCall.builder()
                    .name(toolName)
                    .arguments(argumentsJson)
                    .build();
            
            // Add tool call to context
            String toolCallId = UUID.randomUUID().toString();
            
            // Add message with tool call but WITHOUT content
            context.addMessage(Message.builder()
                    .role("assistant")
                    .toolCall(toolCall)
                    .toolCallId(toolCallId)
                    .timestamp(Instant.now())
                    .build());
            
            // Execute tool and return results
            return tool.execute(arguments)
                    .doOnNext(result -> {
                        try {
                            // Send tool output to WebSocket
                            webSocketHandler.sendToolOutput(sessionId, result);
                            
                            // Add tool result to context - serialize to String
                            String resultStr = objectMapper.writeValueAsString(result);
                            context.addMessage(Message.builder()
                                    .role("tool")
                                    .content(resultStr)  // Using serialized string for content
                                    .toolCallId(toolCallId)
                                    .timestamp(Instant.now())
                                    .build());
                        } catch (JsonProcessingException e) {
                            log.error("Error serializing tool result: {}", e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error executing tool call: {}", e.getMessage());
            return Flux.error(e);
        }
    }
    
    /**
     * Process tool arguments to resolve paths relative to workspace
     */
    private void processToolArguments(Map<String, Object> arguments, ChatContext context) {
        if (arguments == null || context == null || context.getWorkspaceDirectory() == null) {
            return;
        }
        
        // Look for path-like arguments and resolve them relative to workspace
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Process string values that might be paths
            if (value instanceof String && (key.contains("path") || key.contains("dir") || key.contains("file"))) {
                String pathStr = (String) value;
                // Resolve path relative to workspace if it's not absolute
                if (!new File(pathStr).isAbsolute()) {
                    String resolvedPath = context.resolvePath(pathStr);
                    arguments.put(key, resolvedPath);
                }
            }
            
            // Handle task-specific directories if taskName is provided
            if (key.equals("taskName") && value instanceof String && !((String) value).isEmpty()) {
                String taskName = (String) value;
                String taskDir = context.getTaskDirectory(taskName);
                
                // Create task directory if it doesn't exist
                if (taskDir == null) {
                    taskDir = context.createTaskDirectory(taskName);
                    log.debug("Created task directory: {} for task: {}", taskDir, taskName);
                }
                
                // Add task directory to arguments
                arguments.put("taskDirectory", taskDir);
            }
        }
    }
}
