package com.ai.developer.llm.providers;

import com.ai.developer.config.LLMConfig;
import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.Message;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.service.ChatService;
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
public class CustomClaudeLLMProvider implements LLMProvider {
    
    private final LLMConfig config;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private WebClient webClient;
    
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    // Updated to the latest API version for Claude 3.7
    private static final String CLAUDE_API_VERSION = "2023-06-01"; // This is the current latest version as of May 2025
    
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
                
        log.info("Custom Claude LLM Provider initialized with model: {}, API key: {}", 
                config.getModel(), 
                config.getApiKey() != null ? (config.getApiKey().substring(0, 4) + "...") : "null");
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
                                        if (streamingResponse.getType().equals("content_block_start") && 
                                            streamingResponse.getContentBlock() != null &&
                                            "tool_use".equals(streamingResponse.getContentBlock().getType())) {
                                            
                                            // This is the start of a tool use block
                                            log.info("Detected tool use block start: {}", rawChunk);
                                            
                                            // Extract tool use information
                                            ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                                                .id(streamingResponse.getContentBlock().getId())
                                                .name(streamingResponse.getContentBlock().getName())
                                                .input(new HashMap<>())
                                                .build();
                                            
                                            // Store the current tool use block
                                            currentToolUseBlock.set(toolUseBlock);
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        } 
                                        else if (streamingResponse.getType().equals("content_block_delta") && 
                                                 streamingResponse.getDelta() != null &&
                                                 streamingResponse.getDelta().getType() != null &&
                                                 "tool_use_delta".equals(streamingResponse.getDelta().getType())) {
                                            
                                            // This is a delta update to the tool use block (input parameters)
                                            log.info("Detected tool use delta: {}", rawChunk);
                                            
                                            // Update the current tool use block with the input parameters
                                            ToolUseBlock toolUseBlock = currentToolUseBlock.get();
                                            if (toolUseBlock != null && streamingResponse.getDelta().getInput() != null) {
                                                // Merge the input parameters
                                                if (toolUseBlock.getInput() == null) {
                                                    toolUseBlock.setInput(new HashMap<>());
                                                }
                                                toolUseBlock.getInput().putAll(streamingResponse.getDelta().getInput());
                                                currentToolUseBlock.set(toolUseBlock);
                                            }
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        }
                                        else if (streamingResponse.getType().equals("message_delta") && 
                                                 streamingResponse.getDelta() != null &&
                                                 "tool_use".equals(streamingResponse.getDelta().getStopReason())) {
                                            
                                            // This is the end of a tool use block
                                            log.info("Detected tool use block end: {}", rawChunk);
                                            
                                            // Get the complete tool use block
                                            ToolUseBlock toolUseBlock = currentToolUseBlock.get();
                                            if (toolUseBlock != null) {
                                                // Execute the tool and return a special marker
                                                log.info("Executing tool: {} with input: {}", toolUseBlock.getName(), toolUseBlock.getInput());
                                                
                                                // Convert the tool use block to a JSON string for the tool execution handler
                                                String toolUseJson = objectMapper.writeValueAsString(toolUseBlock);
                                                
                                                // Reset the current tool use block
                                                currentToolUseBlock.set(null);
                                                
                                                // Return a special marker with the tool use information
                                                return Mono.just("[TOOL_USE:" + toolUseJson + "]");
                                            }
                                            
                                            return Mono.empty();
                                        }
                                        else if (streamingResponse.getType().equals("content_block_delta") && 
                                                 streamingResponse.getDelta() != null &&
                                                 streamingResponse.getDelta().getText() != null) {
                                            
                                            // Regular text response
                                            return Mono.just(streamingResponse.getDelta().getText());
                                        }
                                        
                                        return Mono.empty();
                                    } catch (JsonProcessingException e) {
                                        log.error("Error parsing streaming chunk: {}", e.getMessage());
                                        return Mono.empty();
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
                    .doOnSubscribe(s -> log.info("Starting Claude API streaming request"))
                    .doOnComplete(() -> log.info("Completed Claude API streaming request"))
                    .doOnError(error -> log.error("Error streaming from Claude API: {}", error.getMessage(), error));
        } catch (JsonProcessingException e) {
            log.error("Error serializing streaming request: {}", e.getMessage());
            return Flux.just("Error serializing streaming request: " + e.getMessage());
        }
    }
    
    @Override
    public String getProviderName() {
        return "Claude (Custom Anthropic)";
    }
    
    private ClaudeRequest buildClaudeRequest(String prompt, ChatContext context, boolean stream) {
        // Extract system message if present (now as top-level parameter)
        Optional<String> systemMessage = context.getMessages().stream()
                .filter(msg -> "system".equals(msg.getRole()))
                .map(Message::getContent)
                .findFirst();
        
        // Log if system message is found
        systemMessage.ifPresent(sysMsg -> {
            log.info("Found system message: {}", 
                    sysMsg.length() > 50 ? sysMsg.substring(0, 50) + "..." : sysMsg);
        });
        
        // Add only user and assistant messages to the messages array
        List<ClaudeMessage> messages = new ArrayList<>();
        
        // Add user and assistant messages in order (excluding system messages)
        List<Message> conversationMessages = context.getMessages().stream()
                .filter(msg -> {
                    // Strictly filter to only include user and assistant roles
                    // Claude API does not accept system role in messages array
                    String role = msg.getRole();
                    boolean isValidRole = "user".equals(role) || "assistant".equals(role);
                    if (!isValidRole && !"system".equals(role)) {
                        log.warn("Ignoring message with unsupported role: {}", role);
                    }
                    return isValidRole;
                })
                .toList();
        
        for (Message msg : conversationMessages) {
            log.info("Adding {} message: {}", msg.getRole(),
                    msg.getContent().length() > 50 ? 
                    msg.getContent().substring(0, 50) + "..." : 
                    msg.getContent());
            
            // Validate role is strictly "user" or "assistant" before adding
            if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                messages.add(ClaudeMessage.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build());
            }
        }
        
        // Add the current prompt as a user message if it's not empty
        if (prompt != null && !prompt.trim().isEmpty()) {
            log.info("Adding current prompt as user message: {}", 
                    prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
            
            messages.add(ClaudeMessage.builder()
                    .role("user")
                    .content(prompt)
                    .build());
        }
        
        // Ensure we have at least one message - Claude API requires at least one message
        if (messages.isEmpty()) {
            log.warn("No messages found in context, adding default user message");
            messages.add(ClaudeMessage.builder()
                    .role("user")
                    .content(prompt != null && !prompt.trim().isEmpty() ? 
                            prompt : "Hello, I need help with development.")
                    .build());
        }
        
        // Use default values if config values are null
        String modelName = config.getModel() != null ? config.getModel() : "claude-3-sonnet-20240229";
        Integer maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4000;
        Double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        
        log.info("Using model: {}, maxTokens: {}, temperature: {}, messageCount: {}", 
                modelName, maxTokens, temperature, messages.size());
        
        // Build the request with system as a top-level parameter
        ClaudeRequest.ClaudeRequestBuilder requestBuilder = ClaudeRequest.builder()
                .model(modelName)
                .messages(messages)
                .max_tokens(maxTokens)  // Using snake_case field name to match API expectation
                .temperature(temperature)
                .stream(stream);
        
        // Add system message as top-level parameter if present
        if (systemMessage.isPresent()) {
            requestBuilder.system(systemMessage.get());
        } else {
            // Add default system message if none exists
            String defaultSystemMessage = "You are a helpful AI developer assistant. You can help with coding, debugging, and using various development tools.";
            log.info("No system message found, using default: {}", defaultSystemMessage);
            requestBuilder.system(defaultSystemMessage);
        }
        
        // Add tools to the request if available
        List<ClaudeTool> tools = buildToolDefinitions();
        if (!tools.isEmpty()) {
            log.info("Adding {} tools to Claude API request", tools.size());
            requestBuilder.tools(tools);
        }
        
        return requestBuilder.build();
    }
    
    /**
     * Build tool definitions for Claude API request
     */
    private List<ClaudeTool> buildToolDefinitions() {
        List<ClaudeTool> tools = new ArrayList<>();
        
        // Get all registered tools from the registry
        List<Tool> registeredTools = toolRegistry.getAllTools();
        
        for (Tool tool : registeredTools) {
            // Create input schema for the tool
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            
            // Add properties to the schema
            Map<String, Object> properties = new HashMap<>();
            List<String> required = new ArrayList<>();
            
            // Add each parameter to the properties
            for (Map.Entry<String, ParameterInfo> entry : tool.getParameters().entrySet()) {
                String paramName = entry.getKey();
                ParameterInfo param = entry.getValue();
                
                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("type", param.getType());
                paramSchema.put("description", param.getDescription());
                
                // Add enum values if available
                if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                    paramSchema.put("enum", param.getEnumValues());
                }
                
                properties.put(paramName, paramSchema);
                
                // Add to required list if parameter is required
                if (param.isRequired()) {
                    required.add(paramName);
                }
            }
            
            inputSchema.put("properties", properties);
            inputSchema.put("required", required);
            
            // Create the tool definition
            ClaudeTool claudeTool = ClaudeTool.builder()
                    .name(tool.getName())
                    .description(tool.getDescription())
                    .inputSchema(inputSchema)
                    .build();
            
            tools.add(claudeTool);
            log.info("Added tool definition for: {}", tool.getName());
        }
        
        return tools;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolUseBlock {
        private String id;
        private String name;
        private Map<String, Object> input;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeRequest {
        private String model;
        private List<ClaudeMessage> messages;
        private String system;
        private Double temperature;
        
        @JsonProperty("max_tokens")  // Use snake_case for API compatibility
        private Integer max_tokens;
        
        private Boolean stream;
        private List<ClaudeTool> tools;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMessage {
        private String role;
        private String content;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeTool {
        private String name;
        private String description;
        
        @JsonProperty("input_schema")
        private Map<String, Object> inputSchema;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeResponse {
        private String id;
        private String type;
        private String role;
        private List<ContentBlock> content;
        private String model;
        private String stopReason;
        private String stopSequence;
        private Usage usage;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlock {
        private String type;
        private String text;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;
        
        @JsonProperty("output_tokens")
        private Integer outputTokens;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeStreamingResponse {
        private String type;
        private Integer index;
        
        @JsonProperty("content_block")
        private ContentBlockInfo contentBlock;
        
        private Delta delta;
        private Usage usage;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlockInfo {
        private String type;
        private String id;
        private String name;
        private Map<String, Object> input;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delta {
        private String type;
        private String text;
        private Map<String, Object> input;
        
        @JsonProperty("stop_reason")
        private String stopReason;
        
        @JsonProperty("stop_sequence")
        private String stopSequence;
    }
}
