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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    @PostConstruct
    public void init() {
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
                                            return claudeResponse.getContent().get(0).getText();
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
                                        if (rawChunk.startsWith("data: ")) {
                                            rawChunk = rawChunk.substring(6);
                                        }
                                        if (rawChunk.equals("[DONE]")) {
                                            return Mono.empty();
                                        }
                                        
                                        ClaudeStreamingResponse streamingResponse = objectMapper.readValue(rawChunk, ClaudeStreamingResponse.class);
                                        
                                        // Handle different types of streaming responses
                                        if ("content_block_start".equals(streamingResponse.getType()) && 
                                            streamingResponse.getContentBlock() != null &&
                                            "tool_use".equals(streamingResponse.getContentBlock().getType())) {
                                            
                                            // This is the start of a tool use block
                                            log.info("Detected tool use block start: {}", rawChunk);
                                            
                                            // Extract tool use information
                                            ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                                                .id(streamingResponse.getContentBlock().getId())
                                                .name(streamingResponse.getContentBlock().getName())
                                                .input(new HashMap<String, Object>())
                                                .build();
                                            
                                            // Store the current tool use block
                                            currentToolUseBlock.set(toolUseBlock);
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        } 
                                        else if ("content_block_delta".equals(streamingResponse.getType()) && 
                                                 streamingResponse.getDelta() != null &&
                                                 streamingResponse.getDelta().getType() != null &&
                                                 "input_json_delta".equals(streamingResponse.getDelta().getType())) {
                                            
                                            // This is a delta update to the tool use block (input parameters)
                                            log.info("Detected tool use delta: {}", rawChunk);
                                            
                                            // Update the current tool use block with the input parameters
                                            ToolUseBlock toolUseBlock = currentToolUseBlock.get();
                                            if (toolUseBlock != null && streamingResponse.getDelta().getPartialJson() != null) {
                                                // For now, we'll accumulate the partial JSON and parse at the end
                                                // This is a simplified approach - production code might need more sophisticated JSON streaming
                                                log.debug("Accumulating partial JSON: {}", streamingResponse.getDelta().getPartialJson());
                                            }
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        }
                                        else if ("content_block_stop".equals(streamingResponse.getType())) {
                                            
                                            // This is the end of a content block
                                            ToolUseBlock toolUseBlock = currentToolUseBlock.get();
                                            if (toolUseBlock != null) {
                                                log.info("Tool use block completed: {}", toolUseBlock);
                                                
                                                // Convert the tool use block to a JSON string for the tool execution handler
                                                String toolUseJson = objectMapper.writeValueAsString(toolUseBlock);
                                                
                                                // Reset the current tool use block
                                                currentToolUseBlock.set(null);
                                                
                                                // Return a special marker with the tool use information
                                                return Mono.just("<tool_use>" + toolUseJson + "</tool_use>");
                                            }
                                            
                                            return Mono.empty();
                                        }
                                        else if ("content_block_delta".equals(streamingResponse.getType()) && 
                                                 streamingResponse.getDelta() != null &&
                                                 "text_delta".equals(streamingResponse.getDelta().getType()) &&
                                                 streamingResponse.getDelta().getText() != null) {
                                            
                                            // This is a regular text delta
                                            return Mono.just(streamingResponse.getDelta().getText());
                                        }
                                        else if ("ping".equals(streamingResponse.getType())) {
                                            // Ignore ping events
                                            return Mono.empty();
                                        }
                                        else {
                                            log.debug("Unhandled streaming response type: {}", streamingResponse.getType());
                                            return Mono.empty();
                                        }
                                    } catch (Exception e) {
                                        log.error("Error processing streaming chunk: {}", e.getMessage());
                                        return Mono.just("Error processing streaming chunk: " + e.getMessage());
                                    }
                                });
                        } else {
                            return response.bodyToMono(String.class)
                                .doOnNext(errorBody -> {
                                    log.error("Claude API streaming error: {} - {}", response.statusCode(), errorBody);
                                })
                                .flatMapMany(errorBody -> Flux.just("Error from Claude API: " + response.statusCode() + " - " + errorBody));
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("Error in streaming response: {}", error.getMessage(), error);
                        return Flux.just("Error in streaming response: " + error.getMessage());
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing streaming request: {}", e.getMessage());
            return Flux.just("Error serializing streaming request: " + e.getMessage());
        }
    }
    
    private ClaudeRequest buildClaudeRequest(String prompt, ChatContext context, boolean stream) {
        List<ClaudeMessage> messages = new ArrayList<>();
        
        // Process existing messages from context
        if (context != null && context.getMessages() != null) {
            for (Message message : context.getMessages()) {
                ClaudeMessage claudeMessage = new ClaudeMessage();
                
                switch (message.getRole()) {
                    case "system":
                        // Skip system messages in the messages array
                        // System prompt is set as a top-level parameter
                        log.debug("Skipping system message in messages array: {}", message.getContent());
                        break;
                    case "user":
                        claudeMessage.setRole("user");
                        claudeMessage.setContent(List.of(new ClaudeContent("text", message.getContent())));
                        messages.add(claudeMessage);
                        break;
                    case "assistant":
                        claudeMessage.setRole("assistant");
                        
                        // Handle tool calls in assistant messages
                        if (message.getToolCall() != null) {
                            // This is a tool call message
                            List<ClaudeContent> contents = new ArrayList<>();
                            
                            // Add text content if present
                            if (message.getContent() != null && !message.getContent().isEmpty()) {
                                contents.add(new ClaudeContent("text", message.getContent()));
                            }
                            
                            // Add tool use content
                            Map<String, Object> toolUse = new HashMap<>();
                            toolUse.put("name", message.getToolCall().getName());
                            toolUse.put("input", message.getToolCall().getArguments());
                            toolUse.put("id", message.getToolCall().getId());
                            
                            ClaudeContent toolUseContent = new ClaudeContent("tool_use", null);
                            toolUseContent.setToolUse(toolUse);
                            contents.add(toolUseContent);
                            
                            claudeMessage.setContent(contents);
                        } else {
                            // Regular assistant message
                            claudeMessage.setContent(List.of(new ClaudeContent("text", message.getContent())));
                        }
                        
                        messages.add(claudeMessage);
                        break;
                    case "tool":
                        // Tool messages are added as user messages with tool_result content
                        ClaudeMessage toolResultMessage = new ClaudeMessage();
                        toolResultMessage.setRole("user");
                        
                        List<ClaudeContent> contents = new ArrayList<>();
                        
                        // Add tool result content
                        Map<String, Object> toolResult = new HashMap<>();
                        toolResult.put("content", message.getContent());
                        toolResult.put("tool_use_id", message.getToolCallId());
                        
                        ClaudeContent toolResultContent = new ClaudeContent("tool_result", null);
                        toolResultContent.setToolResult(toolResult);
                        contents.add(toolResultContent);
                        
                        toolResultMessage.setContent(contents);
                        messages.add(toolResultMessage);
                        break;
                    default:
                        log.warn("Unknown message role: {}", message.getRole());
                        break;
                }
            }
        }
        
        // Add the prompt as a user message if provided
        if (prompt != null && !prompt.isEmpty()) {
            ClaudeMessage promptMessage = new ClaudeMessage();
            promptMessage.setRole("user");
            promptMessage.setContent(List.of(new ClaudeContent("text", prompt)));
            messages.add(promptMessage);
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
                
                // Skip parameters with null names
                if (param.getName() == null) {
                    log.warn("Skipping parameter with null name in tool: {}", tool.getName());
                    continue;
                }
                
                ClaudePropertySchema propertySchema = new ClaudePropertySchema();
                propertySchema.setType(param.getType());
                propertySchema.setDescription(param.getDescription());
                
                properties.put(param.getName(), propertySchema);
                
                if (param.isRequired()) {
                    required.add(param.getName());
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
        request.setSystem("You are an AI Developer Agent, designed to help with coding, debugging, and development tasks. Use tools when appropriate to help solve problems.");
        request.setMaxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 4000);
        request.setTemperature(config.getTemperature() != null ? config.getTemperature() : 0.7);
        request.setStream(stream);
        request.setTools(tools);
        
        return request;
    }
    
    @Override
    public String getProviderName() {
        return "Claude 3.5 (Custom Implementation)";
    }
    
    // Claude API request/response models
    
    @Data
    @NoArgsConstructor
    public static class ClaudeRequest {
        private String model;
        private List<ClaudeMessage> messages;
        private String system;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Double temperature;
        private Boolean stream;
        private List<ClaudeTool> tools;
    }
    
    @Data
    @NoArgsConstructor
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
        @JsonProperty("tool_use")
        private Map<String, Object> toolUse;
        @JsonProperty("tool_result")
        private Map<String, Object> toolResult;
        
        public ClaudeContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
        
        public void setToolUse(Map<String, Object> toolUse) {
            this.toolUse = toolUse;
        }
        
        public void setToolResult(Map<String, Object> toolResult) {
            this.toolResult = toolResult;
        }
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudeTool {
        private String name;
        private String description;
        @JsonProperty("input_schema")
        private ClaudeInputSchema inputSchema;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudeInputSchema {
        private String type;
        private Map<String, ClaudePropertySchema> properties;
        private List<String> required;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudePropertySchema {
        private String type;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudeResponse {
        private String id;
        private String type;
        private String model;
        private String role;
        private List<ClaudeResponseContent> content;
        @JsonProperty("stop_reason")
        private String stopReason;
        private Usage usage;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudeResponseContent {
        private String type;
        private String text;
        private String id;
        private String name;
        private Map<String, Object> input;
    }
    
    @Data
    @NoArgsConstructor
    public static class ClaudeStreamingResponse {
        private String type;
        private String message;
        @JsonProperty("content_block")
        private ContentBlock contentBlock;
        private Delta delta;
        private Integer index;
        private Usage usage;
    }
    
    @Data
    @NoArgsConstructor
    public static class ContentBlock {
        private String type;
        private String text;
        private String id;
        private String name;
        private Map<String, Object> input;
    }
    
    @Data
    @NoArgsConstructor
    public static class Delta {
        private String type;
        private String text;
        @JsonProperty("stop_reason")
        private String stopReason;
        @JsonProperty("partial_json")
        private String partialJson;
    }
    
    @Data
    @NoArgsConstructor
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        @JsonProperty("output_tokens")
        private Integer outputTokens;
        @JsonProperty("cache_creation_input_tokens")
        private Integer cacheCreationInputTokens;
        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;
    }
}
