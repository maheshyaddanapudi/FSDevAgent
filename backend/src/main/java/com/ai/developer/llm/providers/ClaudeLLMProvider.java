package com.ai.developer.llm.providers;

import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.Message;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.llm.ToolUseBlock;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ClaudeLLMProvider implements LLMProvider {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    
    @Value("${llm.api.key}")
    private String apiKey;
    
    @Value("${llm.api.model:claude-3-opus-20240229}")
    private String model;
    
    @Value("${llm.api.max-tokens:4000}")
    private Integer maxTokens;
    
    @Value("${llm.api.temperature:0.7}")
    private Double temperature;
    
    // Map to track active tool use blocks by session
    private final ConcurrentHashMap<String, ToolUseBlock> activeToolUseBlocks = new ConcurrentHashMap<>();
    
    // Pattern to match tool use blocks in LLM responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>", Pattern.DOTALL);
    
    public ClaudeLLMProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, ToolRegistry toolRegistry) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", "dummy") // Will be overridden in actual requests
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        
        log.info("Initialized Claude LLM provider with model: {}", model);
    }
    
    @Override
    public String getProviderName() {
        return "Claude";
    }
    
    @Override
    public Mono<String> generateResponse(String prompt, ChatContext context) {
        // For non-streaming responses, we'll just collect the streaming response
        return streamResponse(prompt, context)
                .collectList()
                .map(chunks -> String.join("", chunks));
    }
    
    @Override
    public Flux<String> streamResponse(String message, ChatContext context) {
        log.info("Streaming response for message: {}", message);
        
        // Convert our messages to Claude format
        List<ClaudeMessage> claudeMessages = convertToClaude(context.getMessages());
        
        // Add the new user message
        ClaudeMessage userMessage = new ClaudeMessage();
        userMessage.setRole("user");
        List<ClaudeContent> userContent = new ArrayList<>();
        userContent.add(new ClaudeContent("text", message));
        userMessage.setContent(userContent);
        claudeMessages.add(userMessage);
        
        // Create the request
        ClaudeRequest request = new ClaudeRequest();
        request.setModel(model);
        request.setMessages(claudeMessages);
        request.setSystem(context.getSystemPrompt());
        request.setMaxTokens(maxTokens);
        request.setTemperature(temperature);
        request.setStream(true);
        
        // Add tools
        List<ClaudeTool> tools = new ArrayList<>();
        for (Tool tool : toolRegistry.getAllTools()) {
            ClaudeTool claudeTool = new ClaudeTool();
            claudeTool.setName(tool.getName());
            claudeTool.setDescription(tool.getDescription());
            
            // Add input schema if available - using parameters as schema
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            inputSchema.put("properties", tool.getParameters());
            claudeTool.setInputSchema(inputSchema);
            
            tools.add(claudeTool);
        }
        request.setTools(tools);
        
        // Add metadata with session ID if available
        if (context.getSessionId() != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sessionId", context.getSessionId());
            
            // Add workspace path if available
            if (context.getMetadata() != null && context.getMetadata().containsKey("workspacePath")) {
                String workspacePath = (String) context.getMetadata().get("workspacePath");
                metadata.put("workspacePath", workspacePath);
            }
            
            request.setMetadata(metadata);
        }
        
        // Thread-local variables to track state during streaming
        ThreadLocal<StringBuilder> currentText = ThreadLocal.withInitial(StringBuilder::new);
        ThreadLocal<StringBuilder> toolInputJson = ThreadLocal.withInitial(StringBuilder::new);
        ThreadLocal<AtomicReference<ToolUseBlock>> currentToolUseBlock = ThreadLocal.withInitial(() -> new AtomicReference<>(null));
        ThreadLocal<AtomicReference<String>> toolUseMarker = ThreadLocal.withInitial(() -> new AtomicReference<>(null));
        
        // Get session ID for tracking
        String sessionId = context.getSessionId();
        
        return webClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(chunk -> {
                    try {
                        // Skip empty lines
                        if (chunk.trim().isEmpty()) {
                            return Mono.empty();
                        }
                        
                        // Handle data chunks
                        if (chunk.startsWith("data: ")) {
                            String data = chunk.substring(6);
                            
                            // Handle stream end
                            if ("[DONE]".equals(data)) {
                                log.info("Stream completed");
                                
                                // Check if we have a pending tool use marker to emit
                                String marker = toolUseMarker.get().get();
                                if (marker != null) {
                                    log.info("Emitting pending tool use marker at stream end");
                                    return Mono.just(marker);
                                }
                                
                                return Mono.empty();
                            }
                            
                            // Parse the JSON data
                            JsonNode jsonNode = objectMapper.readTree(data);
                            
                            // Check for content blocks
                            if (jsonNode.has("type")) {
                                String type = jsonNode.get("type").asText();
                                
                                if ("content_block_start".equals(type)) {
                                    // Content block start
                                    JsonNode contentBlock = jsonNode.get("content_block");
                                    String blockType = contentBlock.get("type").asText();
                                    
                                    if ("text".equals(blockType)) {
                                        // Text block started, reset current text
                                        currentText.get().setLength(0);
                                    } else if ("tool_use".equals(blockType)) {
                                        // Tool use block started
                                        String id = contentBlock.get("id").asText();
                                        String name = contentBlock.get("name").asText();
                                        
                                        log.info("Tool use block started: {} ({})", name, id);
                                        
                                        // Create a new tool use block
                                        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                                                .id(id)
                                                .name(name)
                                                .input(new HashMap<String, Object>())
                                                .build();
                                        
                                        // Store it for later use
                                        currentToolUseBlock.get().set(toolUseBlock);
                                        
                                        // Also store in session map
                                        if (sessionId != null) {
                                            activeToolUseBlocks.put(sessionId, toolUseBlock);
                                        }
                                        
                                        // Reset tool input JSON
                                        toolInputJson.get().setLength(0);
                                    }
                                } else if ("content_block_delta".equals(type)) {
                                    // Content block delta
                                    JsonNode delta = jsonNode.get("delta");
                                    String blockType = jsonNode.get("content_block_type").asText();
                                    
                                    if ("text".equals(blockType)) {
                                        // Text delta
                                        if (delta.has("text")) {
                                            String text = delta.get("text").asText();
                                            currentText.get().append(text);
                                            return Mono.just(text);
                                        }
                                    } else if ("tool_use".equals(blockType)) {
                                        // Tool use delta
                                        if (delta.has("input")) {
                                            String inputDelta = delta.get("input").asText();
                                            toolInputJson.get().append(inputDelta);
                                            log.debug("Tool use input delta: {}", inputDelta);
                                        }
                                    }
                                } else if ("content_block_stop".equals(type)) {
                                    // Content block stop
                                    return handleContentBlockStop(jsonNode, sessionId, currentToolUseBlock, toolInputJson, toolUseMarker);
                                }
                            } else if (jsonNode.has("message")) {
                                // Regular message
                                JsonNode messageNode = jsonNode.get("message");
                                if (messageNode.has("content")) {
                                    JsonNode content = messageNode.get("content").get(0);
                                    if (content.has("text")) {
                                        String text = content.get("text").asText();
                                        return Mono.just(text);
                                    }
                                }
                            }
                        }
                        
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Error processing chunk: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .timeout(Duration.ofMinutes(5))
                .doOnError(e -> log.error("Error in stream: {}", e.getMessage(), e));
    }
    
    /**
     * Handle content block stop events
     */
    private Mono<String> handleContentBlockStop(JsonNode response, String sessionId, 
            ThreadLocal<AtomicReference<ToolUseBlock>> currentToolUseBlock,
            ThreadLocal<StringBuilder> toolInputJson,
            ThreadLocal<AtomicReference<String>> toolUseMarker) {
        
        String blockType = response.get("content_block").get("type").asText();
        if (blockType == null) {
            log.warn("Content block stop event missing block type");
            return Mono.empty();
        }
        
        if ("tool_use".equals(blockType)) {
            // Get the current tool use block
            ToolUseBlock toolUseBlock = currentToolUseBlock.get().get();
            if (toolUseBlock == null) {
                // Try to get from session map
                toolUseBlock = activeToolUseBlocks.get(sessionId);
                if (toolUseBlock == null) {
                    log.error("Received tool use stop but no active tool use block found");
                    return Mono.empty();
                }
            }
            
            try {
                // Parse the accumulated JSON input
                String inputJson = toolInputJson.get().toString();
                if (!inputJson.isEmpty()) {
                    Map<String, Object> inputMap = objectMapper.readValue(inputJson, Map.class);
                    toolUseBlock.setInput(inputMap);
                    
                    log.info("Completed tool use block: {} with input keys: {}", 
                            toolUseBlock.getName(), inputMap.keySet());
                }
                
                // Create tool call event
                ToolCall toolCall = ToolCall.builder()
                        .id(toolUseBlock.getId())
                        .name(toolUseBlock.getName())
                        .arguments(toolUseBlock.getInput() instanceof Map ? 
                                   (Map<String, Object>)toolUseBlock.getInput() : 
                                   new HashMap<String, Object>())
                        .build();
                
                // Serialize to JSON
                String toolCallJson = objectMapper.writeValueAsString(toolCall);
                String marker = "EVENT:toolCall:" + toolCallJson;
                
                log.info("Emitting tool call event: {}", toolCallJson);
                
                // Store the marker for later emission if needed
                toolUseMarker.get().set(marker);
                
                // Return the marker immediately
                return Mono.just(marker);
            } catch (Exception e) {
                log.error("Error processing tool use block: {}", e.getMessage(), e);
                return Mono.empty();
            }
        } else if ("text".equals(blockType)) {
            // Text block stopped, nothing to do
            return Mono.empty();
        } else {
            log.warn("Unknown content block type in stop event: {}", blockType);
            return Mono.empty();
        }
    }
    
    // Claude API request/response models
    
    @Data
    private static class ClaudeRequest {
        private String model;
        private List<ClaudeMessage> messages;
        private String system;
        private Integer maxTokens;
        private Double temperature;
        private Boolean stream;
        private List<ClaudeTool> tools;
        private Map<String, Object> metadata;
    }
    
    @Data
    private static class ClaudeMessage {
        private String role;
        private List<ClaudeContent> content;
    }
    
    @Data
    private static class ClaudeContent {
        private String type;
        private String text;
        
        @JsonProperty("tool_use")
        private Map<String, Object> toolUse;
        
        @JsonProperty("tool_result")
        private Map<String, Object> toolResult;
        
        public ClaudeContent() {
        }
        
        public ClaudeContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    
    @Data
    private static class ClaudeTool {
        private String name;
        private String description;
        
        @JsonProperty("input_schema")
        private Map<String, Object> inputSchema;
    }
    
    /**
     * Convert our message format to Claude's format
     */
    private List<ClaudeMessage> convertToClaude(List<Message> messages) {
        List<ClaudeMessage> claudeMessages = new ArrayList<>();
        
        for (Message message : messages) {
            ClaudeMessage claudeMessage = new ClaudeMessage();
            claudeMessage.setRole(message.getRole());
            
            List<ClaudeContent> content = new ArrayList<>();
            
            if (message.getToolCall() != null) {
                // This is a message with a tool call
                ClaudeContent textContent = new ClaudeContent();
                textContent.setType("text");
                textContent.setText(message.getContent());
                content.add(textContent);
                
                // Add tool use content
                ClaudeContent toolUseContent = new ClaudeContent();
                toolUseContent.setType("tool_use");
                
                // Convert tool call to Claude format
                Map<String, Object> toolUseMap = new HashMap<>();
                toolUseMap.put("id", message.getToolCall().getId());
                toolUseMap.put("name", message.getToolCall().getName());
                toolUseMap.put("input", message.getToolCall().getArguments());
                toolUseContent.setToolUse(toolUseMap);
                
                content.add(toolUseContent);
            } else if (message.getToolCallId() != null) {
                // This is a tool result message
                ClaudeContent toolResultContent = new ClaudeContent();
                toolResultContent.setType("tool_result");
                
                // Convert tool result to Claude format
                Map<String, Object> toolResultMap = new HashMap<>();
                toolResultMap.put("tool_call_id", message.getToolCallId());
                toolResultMap.put("content", message.getContent());
                toolResultContent.setToolResult(toolResultMap);
                
                content.add(toolResultContent);
            } else {
                // Regular text message
                ClaudeContent textContent = new ClaudeContent();
                textContent.setType("text");
                textContent.setText(message.getContent());
                content.add(textContent);
            }
            
            claudeMessage.setContent(content);
            claudeMessages.add(claudeMessage);
        }
        
        return claudeMessages;
    }
    
    /**
     * Extract tool use blocks from a response
     */
    private List<ToolUseBlock> extractToolUseBlocks(String response) {
        List<ToolUseBlock> blocks = new ArrayList<>();
        
        Matcher matcher = TOOL_USE_PATTERN.matcher(response);
        while (matcher.find()) {
            try {
                String json = matcher.group(1);
                ToolUseBlock block = objectMapper.readValue(json, ToolUseBlock.class);
                blocks.add(block);
            } catch (JsonProcessingException e) {
                log.error("Error parsing tool use block: {}", e.getMessage(), e);
            }
        }
        
        return blocks;
    }
    
    /**
     * Generate a unique ID
     */
    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
