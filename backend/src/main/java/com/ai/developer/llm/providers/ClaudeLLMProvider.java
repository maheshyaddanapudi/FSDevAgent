package com.ai.developer.llm.providers;

import com.ai.developer.config.LLMConfig;
import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.Message;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.llm.ToolUseBlock;
import com.ai.developer.tools.ParameterInfo;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.type", havingValue = "claude")
public class ClaudeLLMProvider implements LLMProvider {
    
    private final LLMConfig config;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private WebClient webClient;
    
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_API_VERSION = "2023-06-01";
    
    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @PostConstruct
    public void init() {
        // Register JavaTimeModule and configure snake_case naming for proper Claude API compatibility
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        
        this.webClient = WebClient.builder()
                .baseUrl(CLAUDE_API_URL)
                .defaultHeader("x-api-key", config.getApiKey())
                .defaultHeader("anthropic-version", CLAUDE_API_VERSION)
                .defaultHeader("content-type", "application/json")
                .filter(logRequest())
                .filter(logResponse())
                .build();        
        // Fix for StringIndexOutOfBoundsException - safely handle null or empty API key
        String apiKeyDisplay = "not set";
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            apiKeyDisplay = config.getApiKey().substring(0, Math.min(4, config.getApiKey().length())) + "...";
        }
        
        log.info("Claude LLM Provider initialized with model: {}, API key: {}", 
                config.getModel() != null ? config.getModel() : "not set", 
                apiKeyDisplay);
    }
    
    // Add request logging filter
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) -> 
                values.forEach(value -> log.info("{}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }
    
    // Add enhanced response logging filter with detailed error diagnostics
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("Response status: {}", clientResponse.statusCode());
            clientResponse.headers().asHttpHeaders().forEach((name, values) -> 
                values.forEach(value -> log.info("{}={}", name, value)));
            
            if (clientResponse.statusCode().isError()) {
                return clientResponse.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Claude API error response: Status={}, Body={}", 
                                clientResponse.statusCode(), body);
                        
                        // Try to parse error details for better diagnostics
                        try {
                            Map<String, Object> errorMap = objectMapper.readValue(body, Map.class);
                            if (errorMap.containsKey("error")) {
                                Map<String, Object> error = (Map<String, Object>) errorMap.get("error");
                                log.error("Claude API error details: type={}, message={}", 
                                        error.get("type"), error.get("message"));
                            }
                        } catch (Exception e) {
                            log.warn("Could not parse error details: {}", e.getMessage());
                        }
                        
                        return Mono.just(ClientResponse.create(clientResponse.statusCode())
                            .headers(headers -> headers.addAll(clientResponse.headers().asHttpHeaders()))
                            .body(body)
                            .build());
                    });
            }
            return Mono.just(clientResponse);
        });
    }
    
    @Override
    public Mono<String> generateResponse(String prompt, ChatContext context) {
        ClaudeRequest request = buildClaudeRequest(prompt, context, false);
        
        log.info("Sending non-streaming request to Claude API with {} messages", request.getMessages().size());
        
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("Request payload: {}", requestJson);
            
            // Use direct HTTP client approach for more control
            return webClient.post()
                    .body(BodyInserters.fromValue(requestJson))
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class)
                                .doOnNext(rawResponse -> {
                                    log.info("Raw response from Claude API: {}", rawResponse);
                                })
                                .map(rawResponse -> {
                                    try {
                                        ClaudeResponse claudeResponse = objectMapper.readValue(rawResponse, ClaudeResponse.class);
                                        if (claudeResponse.getContent() != null && !claudeResponse.getContent().isEmpty()) {
                                            StringBuilder responseBuilder = new StringBuilder();
                                            
                                            for (ClaudeResponseContent content : claudeResponse.getContent()) {
                                                if ("text".equals(content.getType()) && content.getText() != null) {
                                                    responseBuilder.append(content.getText());
                                                } else if ("tool_use".equals(content.getType())) {
                                                    // Handle tool use in non-streaming response
                                                    try {
                                                        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                                                            .id(content.getId())
                                                            .name(content.getName())
                                                            .input(content.getInput() != null ? content.getInput() : new HashMap<>())
                                                            .build();
                                                        
                                                        String toolUseJson = objectMapper.writeValueAsString(toolUseBlock);
                                                        responseBuilder.append("<tool_use>").append(toolUseJson).append("</tool_use>");
                                                    } catch (Exception e) {
                                                        log.error("Error processing tool use in response: {}", e.getMessage());
                                                        responseBuilder.append("Error processing tool use: ").append(e.getMessage());
                                                    }
                                                }
                                            }
                                            
                                            return responseBuilder.toString();
                                        } else {
                                            log.error("Empty content in Claude response");
                                            return "Error: Empty content in Claude response";
                                        }
                                    } catch (JsonProcessingException e) {
                                        log.error("Error parsing Claude response: {}", e.getMessage());
                                        return "Error parsing Claude response: " + e.getMessage();
                                    }
                                });
                        } else {
                            return response.bodyToMono(String.class)
                                .doOnNext(errorBody -> {
                                    log.error("Claude API error: {} - {}", response.statusCode(), errorBody);
                                })
                                .map(errorBody -> "Error from Claude API: " + response.statusCode() + " - " + errorBody);
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("Error calling Claude API: {}", error.getMessage(), error);
                        return Mono.just("Error calling Claude API: " + error.getMessage());
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing request: {}", e.getMessage());
            return Mono.just("Error serializing request: " + e.getMessage());
        }
    }
    
    @Override
    public Flux<String> streamResponse(String prompt, ChatContext context) {
        ClaudeRequest request = buildClaudeRequest(prompt, context, true);
        
        log.info("Sending streaming request to Claude API with {} messages", request.getMessages().size());
        
        // Log the messages being sent for debugging
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("Request payload: {}", requestJson);
            
            // Track the current tool use block being built
            AtomicReference<ToolUseBlock> currentToolUseBlock = new AtomicReference<>(null);
            AtomicReference<StringBuilder> toolInputJson = new AtomicReference<>(new StringBuilder());
            // Track tool call sequence for proper ordering
            AtomicInteger toolCallCounter = new AtomicInteger(0);
            
            return webClient.post()
                    .body(BodyInserters.fromValue(requestJson))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchangeToFlux(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToFlux(String.class)
                                .doOnNext(rawChunk -> {
                                    log.debug("Raw streaming chunk: {}", rawChunk);
                                })
                                .flatMap(rawChunk -> {
                                    try {
                                        // Clean up the chunk data
                                        if (rawChunk.startsWith("data: ")) {
                                            rawChunk = rawChunk.substring(6);
                                        }
                                        
                                        // Skip empty lines and done markers
                                        if (rawChunk.trim().isEmpty() || rawChunk.equals("[DONE]")) {
                                            return Mono.empty();
                                        }
                                        
                                        // Parse the streaming response with error handling
                                        ClaudeStreamingResponse streamingResponse;
                                        try {
                                            streamingResponse = objectMapper.readValue(rawChunk, ClaudeStreamingResponse.class);
                                        } catch (JsonProcessingException e) {
                                            log.error("Failed to parse streaming response: {}, Raw chunk: {}", e.getMessage(), rawChunk);
                                            return Mono.empty(); // Skip malformed chunks instead of failing
                                        }
                                        
                                        // Validate response has required type field
                                        if (streamingResponse.getType() == null) {
                                            log.warn("Streaming response missing type field: {}", rawChunk);
                                            return Mono.empty();
                                        }
                                        
                                        // Handle different event types with proper null checking
                                        switch (streamingResponse.getType()) {
                                            case "message_start":
                                                log.debug("Message start event received: {}", streamingResponse.getMessageData());
                                                // Handle message start event properly instead of ignoring it
                                                try {
                                                    // Log detailed message data for debugging
                                                    if (streamingResponse.getMessageData() != null) {
                                                        log.debug("Message ID: {}, Model: {}, Role: {}", 
                                                            streamingResponse.getMessageData().getId(),
                                                            streamingResponse.getMessageData().getModel(),
                                                            streamingResponse.getMessageData().getRole());
                                                    } else if (streamingResponse.getAdditionalProperties() != null && 
                                                               !streamingResponse.getAdditionalProperties().isEmpty()) {
                                                        log.debug("Message start with additional properties: {}", 
                                                            streamingResponse.getAdditionalProperties());
                                                    }
                                                } catch (Exception e) {
                                                    log.warn("Error processing message_start event: {}", e.getMessage());
                                                }
                                                return Mono.empty();
                                                
                                            case "content_block_start":
                                                return handleContentBlockStart(streamingResponse, currentToolUseBlock, toolInputJson);
                                                
                                            case "content_block_delta":
                                                return handleContentBlockDelta(streamingResponse, currentToolUseBlock, toolInputJson);
                                                
                                            case "content_block_stop":
                                                // Enhanced to track tool call sequence
                                                int toolCallSequence = toolCallCounter.incrementAndGet();
                                                log.info("Processing tool call #{}", toolCallSequence);
                                                return handleContentBlockStop(streamingResponse, currentToolUseBlock, toolInputJson, toolCallSequence);
                                                
                                            case "message_delta":
                                                // Handle message delta events
                                                if (streamingResponse.getDelta() != null && 
                                                    "text_delta".equals(streamingResponse.getDelta().getType()) && 
                                                    streamingResponse.getDelta().getText() != null) {
                                                    return Mono.just(streamingResponse.getDelta().getText());
                                                }
                                                return Mono.empty();
                                                
                                            case "message_stop":
                                                log.debug("Message stop event received");
                                                return Mono.empty();
                                                
                                            case "ping":
                                                log.debug("Ping event received");
                                                return Mono.empty();
                                                
                                            case "error":
                                                log.error("Error event received: {}", rawChunk);
                                                return Mono.just("Error from Claude API: " + rawChunk);
                                                
                                            default:
                                                log.warn("Unknown streaming response type: {} in chunk: {}", 
                                                        streamingResponse.getType(), rawChunk);
                                                return Mono.empty();
                                        }
                                    } catch (Exception e) {
                                        log.error("Error processing streaming chunk: {}", e.getMessage(), e);
                                        return Mono.empty(); // Skip problematic chunks instead of failing
                                    }
                                })
                                .onErrorResume(error -> {
                                    log.error("Error in streaming response: {}", error.getMessage(), error);
                                    return Flux.just("Error in streaming response: " + error.getMessage());
                                });
                        } else {
                            return response.bodyToMono(String.class)
                                .flatMapMany(errorBody -> {
                                    log.error("Claude API error: {} - {}", response.statusCode(), errorBody);
                                    return Flux.just("Error from Claude API: " + response.statusCode() + " - " + errorBody);
                                });
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("Error calling Claude API: {}", error.getMessage(), error);
                        return Flux.just("Error calling Claude API: " + error.getMessage());
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing request: {}", e.getMessage());
            return Flux.just("Error serializing request: " + e.getMessage());
        }
    }
    
    private ClaudeRequest buildClaudeRequest(String prompt, ChatContext context, boolean stream) {
        // Build system prompt with explicit tool use instructions
        String systemPrompt = "You are an AI Developer Agent, designed to help with coding, debugging, and development tasks. " +
                              "When appropriate, use tools to help solve problems. " +
                              "Always emit tool calls in the proper format using the tool_use content block type. " +
                              "When using tools, always provide the complete input parameters as required by the tool schema.";
        
        // Build messages from context
        List<ClaudeMessage> messages = new ArrayList<>();
        
        // Convert context messages to Claude format
        if (context != null && context.getMessages() != null) {
            for (Message message : context.getMessages()) {
                List<ClaudeContent> content = new ArrayList<>();
                String role = message.getRole();
                
                // Map 'tool' role to 'user' for Claude API compatibility
                if ("tool".equals(role)) {
                    role = "user";
                }
                
                // Ensure only valid roles are used
                if (!"user".equals(role) && !"assistant".equals(role)) {
                    log.warn("Skipping message with invalid role for Claude API: {}", role);
                    continue;
                }
                
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    content.add(new ClaudeContent("text", message.getContent()));
                }
                
                // Add tool use if present (only for assistant messages)
                if ("assistant".equals(role) && message.getToolCall() != null) {
                    try {
                        // For historical tool calls, we format them as text to avoid collision with current tool calls
                        StringBuilder toolCallText = new StringBuilder();
                        toolCallText.append("Previous tool call: ").append(message.getToolCall().getName()).append("\n");
                        toolCallText.append("Arguments: ").append(objectMapper.writeValueAsString(message.getToolCall().getArguments()));
                        
                        // Add as regular text content instead of tool_use to avoid API errors
                        ClaudeContent textContent = new ClaudeContent("text", toolCallText.toString());
                        content.add(textContent);
                    } catch (JsonProcessingException e) {
                        log.error("Error serializing tool arguments for Claude API: {}", e.getMessage());
                    }
                }
                
                // Add tool result if present (only for user messages)
                if ("user".equals(role) && message.getToolCallId() != null && message.getContent() != null) {
                    // For historical tool results, we format them as text
                    StringBuilder toolResultText = new StringBuilder();
                    toolResultText.append("Previous tool result for tool ID: ").append(message.getToolCallId()).append("\n");
                    toolResultText.append("Result: ").append(message.getContent());
                    
                    // Add as regular text content instead of tool_result to avoid API errors
                    ClaudeContent textContent = new ClaudeContent("text", toolResultText.toString());
                    content.add(textContent);
                }
                
                if (!content.isEmpty()) {
                    messages.add(new ClaudeMessage(role, content));
                }
            }
        }
        
        // CRITICAL FIX: Enhanced duplicate message detection and handling
        // This prevents duplicate prompts which can confuse the LLM
        boolean promptAlreadyInContext = false;
        if (context != null && context.getMessages() != null) {
            for (Message msg : context.getMessages()) {
                if ("user".equals(msg.getRole()) && prompt != null && prompt.equals(msg.getContent())) {
                    promptAlreadyInContext = true;
                    log.info("[DUPLICATE_FIX] Prompt already exists in context, skipping duplicate addition");
                    break;
                }
            }
        }
        
        if (prompt != null && !prompt.isEmpty() && !promptAlreadyInContext) {
            log.info("[DUPLICATE_FIX] Adding prompt to messages as it's not in context: {}", 
                    prompt.substring(0, Math.min(50, prompt.length())) + "...");
            List<ClaudeContent> promptContent = new ArrayList<>();
            promptContent.add(new ClaudeContent("text", prompt));
            messages.add(new ClaudeMessage("user", promptContent));
        } else if (prompt == null || prompt.isEmpty()) {
            log.warn("[DUPLICATE_FIX] Skipping empty or null prompt");
        }
        
        // Build the tools list
        List<ClaudeTool> tools = new ArrayList<>();
        for (Tool tool : toolRegistry.getAllTools()) {
            ClaudeTool claudeTool = new ClaudeTool();
            claudeTool.setName(tool.getName());
            claudeTool.setDescription(tool.getDescription());
            
            // Build input schema
            ClaudeInputSchema inputSchema = new ClaudeInputSchema();
            inputSchema.setType("object");
            
            Map<String, ClaudePropertySchema> properties = new HashMap<>();
            List<String> required = new ArrayList<>();
            
            for (Map.Entry<String, ParameterInfo> entry : tool.getParameters().entrySet()) {
                ParameterInfo param = entry.getValue();
                if (param.getName() != null) {
                    ClaudePropertySchema propertySchema = new ClaudePropertySchema();
                    propertySchema.setType(param.getType());
                    propertySchema.setDescription(param.getDescription());
                    
                    properties.put(param.getName(), propertySchema);
                    
                    if (param.isRequired()) {
                        required.add(param.getName());
                    }
                } else {
                    log.warn("Skipping parameter with null name in tool: {}", tool.getName());
                }
            }
            
            inputSchema.setProperties(properties);
            inputSchema.setRequired(required);
            claudeTool.setInputSchema(inputSchema);
            
            tools.add(claudeTool);
        }
        
        // Build the request
        ClaudeRequest request = new ClaudeRequest();
        request.setModel(config.getModel() != null ? config.getModel() : "claude-3-5-sonnet-20241022");
        request.setMessages(messages);
        request.setSystem(systemPrompt);
        request.setMaxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 4000);
        request.setTemperature(config.getTemperature() != null ? config.getTemperature() : 0.7);
        request.setStream(stream);
        if (!tools.isEmpty()) {
            request.setTools(tools);
        }
        
        return request;
    }
    
    // Helper methods for handling different streaming event types
    
    private Mono<String> handleContentBlockStart(ClaudeStreamingResponse response, 
                                               AtomicReference<ToolUseBlock> currentToolUseBlock,
                                               AtomicReference<StringBuilder> toolInputJson) {
        if (response.getContentBlock() == null) {
            log.warn("Content block start event missing content block data");
            return Mono.empty();
        }
        
        String blockType = response.getContentBlock().getType();
        if (blockType == null) {
            log.warn("Content block start event missing block type");
            return Mono.empty();
        }
        
        switch (blockType) {
            case "tool_use":
                log.info("Starting tool use block: id={}, name={}", 
                        response.getContentBlock().getId(), response.getContentBlock().getName());
                
                // Validate required fields
                if (response.getContentBlock().getId() == null || response.getContentBlock().getName() == null) {
                    log.error("Tool use block missing required id or name");
                    return Mono.empty();
                }
                
                // Create new tool use block
                ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                    .id(response.getContentBlock().getId())
                    .name(response.getContentBlock().getName())
                    .input(new HashMap<String, Object>())
                    .build();
                
                // Store the current tool use block and reset JSON accumulator
                currentToolUseBlock.set(toolUseBlock);
                toolInputJson.set(new StringBuilder());
                break;
                
            case "text":
                log.debug("Starting text block");
                break;
                
            default:
                log.warn("Unknown content block type: {}", blockType);
                break;
        }
        
        return Mono.empty();
    }
    
    private Mono<String> handleContentBlockDelta(ClaudeStreamingResponse response,
                                                AtomicReference<ToolUseBlock> currentToolUseBlock,
                                                AtomicReference<StringBuilder> toolInputJson) {
        if (response.getDelta() == null) {
            log.warn("Content block delta event missing delta data");
            return Mono.empty();
        }
        
        String deltaType = response.getDelta().getType();
        if (deltaType == null) {
            log.warn("Content block delta event missing delta type");
            return Mono.empty();
        }
        
        switch (deltaType) {
            case "text_delta":
                // Handle text content updates
                if (response.getDelta().getText() != null) {
                    return Mono.just(response.getDelta().getText());
                }
                break;
                
            case "input_json_delta":
                // Handle tool input JSON accumulation
                log.debug("Accumulating tool input JSON delta");
                if (response.getDelta().getPartialJson() != null) {
                    StringBuilder currentJson = toolInputJson.get();
                    if (currentJson != null) {
                        currentJson.append(response.getDelta().getPartialJson());
                        log.debug("Accumulated JSON length: {}", currentJson.length());
                    } else {
                        log.warn("Received input JSON delta but no accumulator found");
                    }
                }
                break;
                
            default:
                log.warn("Unknown delta type: {}", deltaType);
                break;
        }
        
        return Mono.empty();
    }
    
    private Mono<String> handleContentBlockStop(ClaudeStreamingResponse response,
                                              AtomicReference<ToolUseBlock> currentToolUseBlock,
                                              AtomicReference<StringBuilder> toolInputJson,
                                              int toolCallSequence) {
        ToolUseBlock toolUseBlock = currentToolUseBlock.get();
        if (toolUseBlock == null) {
            log.debug("Content block stop - no active tool use block");
            return Mono.empty();
        }
        
        try {
            // Parse the accumulated JSON input
            StringBuilder jsonBuilder = toolInputJson.get();
            if (jsonBuilder != null && jsonBuilder.length() > 0) {
                String jsonInput = jsonBuilder.toString();
                log.debug("Parsing tool input JSON: {}", jsonInput);
                
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputMap = objectMapper.readValue(jsonInput, Map.class);
                    toolUseBlock.setInput(inputMap);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse tool input JSON: {} - JSON: {}", e.getMessage(), jsonInput);
                    toolUseBlock.setInput(new HashMap<>()); // Use empty map as fallback
                }
            } else {
                log.debug("No JSON input accumulated for tool use block");
                toolUseBlock.setInput(new HashMap<>());
            }
            
            log.info("Completed tool use block #{}: {} with input keys: {}", 
                    toolCallSequence,
                    toolUseBlock.getName(), 
                    toolUseBlock.getInput() != null ? ((Map<String, Object>)toolUseBlock.getInput()).keySet() : "none");
            
            // Convert the tool use block to a JSON string for the tool execution handler
            String toolUseJson = objectMapper.writeValueAsString(toolUseBlock);
            
            // Reset state
            currentToolUseBlock.set(null);
            toolInputJson.set(new StringBuilder());
            
            // Create a special event for the frontend to display tool call
            try {
                Map<String, Object> toolCallEvent = new HashMap<>();
                toolCallEvent.put("id", toolUseBlock.getId());
                toolCallEvent.put("name", toolUseBlock.getName());
                toolCallEvent.put("arguments", toolUseBlock.getInput());
                toolCallEvent.put("sequence", toolCallSequence); // Add sequence number for ordering
                
                String toolCallEventJson = objectMapper.writeValueAsString(toolCallEvent);
                log.info("Emitting tool call event: {}", toolCallEventJson);
                
                // Return a special marker with the tool use information
                // The EVENT: prefix signals to the frontend this is a special event
                return Mono.just("EVENT:toolCall:" + toolCallEventJson + "\n<tool_use>" + toolUseJson + "</tool_use>");
            } catch (Exception e) {
                log.error("Error creating tool call event: {}", e.getMessage());
                // Fall back to just the tool use marker
                return Mono.just("<tool_use>" + toolUseJson + "</tool_use>");
            }
            
        } catch (Exception e) {
            log.error("Error processing completed tool use block: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }
    
    // Inner classes for Claude API request/response
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeRequest {
        private String model;
        private List<ClaudeMessage> messages;
        private String system;
        
        @JsonProperty("max_tokens")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Integer maxTokens;
        
        private Double temperature;
        private Boolean stream;
        private List<ClaudeTool> tools;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMessage {
        private String role;
        private List<ClaudeContent> content;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeContent {
        private String type;
        private String text;
        
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, Object> toolUse;
        
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, Object> toolResult;
        
        public ClaudeContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeTool {
        private String name;
        private String description;
        
        @JsonProperty("input_schema")
        private ClaudeInputSchema inputSchema;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeInputSchema {
        private String type;
        private Map<String, ClaudePropertySchema> properties;
        private List<String> required;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudePropertySchema {
        private String type;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeResponse {
        private String id;
        private String type;
        private String role;
        private String model;
        private List<ClaudeResponseContent> content;
        private String stopReason;
        private Map<String, Object> usage;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeResponseContent {
        private String id;
        private String type;
        private String text;
        private String name;
        private Map<String, Object> input;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeStreamingResponse {
        private String type;
        private ClaudeMessageData messageData;
        private ClaudeContentBlock contentBlock;
        private ClaudeDelta delta;
        private Map<String, Object> additionalProperties;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMessageData {
        private String id;
        private String model;
        private String role;
        private String stopReason;
        private Map<String, Object> usage;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeContentBlock {
        private String id;
        private String type;
        private String name;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeDelta {
        private String type;
        private String text;
        private String partialJson;
        private String stopReason;
    }
}
