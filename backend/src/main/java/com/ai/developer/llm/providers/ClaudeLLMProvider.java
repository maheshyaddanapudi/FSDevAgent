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
                                            
                                            // Store the current tool use block and reset JSON accumulator
                                            currentToolUseBlock.set(toolUseBlock);
                                            toolInputJson.set(new StringBuilder());
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        } 
                                        else if ("content_block_delta".equals(streamingResponse.getType()) && 
                                                 streamingResponse.getDelta() != null &&
                                                 streamingResponse.getDelta().getType() != null &&
                                                 "input_json_delta".equals(streamingResponse.getDelta().getType())) {
                                            
                                            // This is a delta update to the tool use block (input parameters)
                                            log.debug("Detected tool use delta: {}", rawChunk);
                                            
                                            // Accumulate the partial JSON
                                            if (streamingResponse.getDelta().getPartialJson() != null) {
                                                toolInputJson.get().append(streamingResponse.getDelta().getPartialJson());
                                                log.debug("Accumulated JSON so far: {}", toolInputJson.get().toString());
                                            }
                                            
                                            // Don't emit anything yet, wait for the complete tool use block
                                            return Mono.empty();
                                        }
                                        else if ("content_block_stop".equals(streamingResponse.getType())) {
                                            
                                            // This is the end of a content block
                                            ToolUseBlock toolUseBlock = currentToolUseBlock.get();
                                            if (toolUseBlock != null) {
                                                try {
                                                    // Parse the accumulated JSON input
                                                    String jsonInput = toolInputJson.get().toString();
                                                    if (!jsonInput.isEmpty()) {
                                                        @SuppressWarnings("unchecked")
                                                        Map<String, Object> inputMap = objectMapper.readValue(jsonInput, Map.class);
                                                        toolUseBlock.setInput(inputMap);
                                                    }
                                                    
                                                    log.info("Tool use block completed: {} with input: {}", 
                                                            toolUseBlock.getName(), toolUseBlock.getInput());
                                                    
                                                    // Convert the tool use block to a JSON string for the tool execution handler
                                                    String toolUseJson = objectMapper.writeValueAsString(toolUseBlock);
                                                    
                                                    // Reset the current tool use block and JSON accumulator
                                                    currentToolUseBlock.set(null);
                                                    toolInputJson.set(new StringBuilder());
                                                    
                                                    // Return a special marker with the tool use information
                                                    return Mono.just("<tool_use>" + toolUseJson + "</tool_use>");
                                                } catch (Exception e) {
                                                    log.error("Error parsing tool input JSON: {}", e.getMessage());
                                                    currentToolUseBlock.set(null);
                                                    toolInputJson.set(new StringBuilder());
                                                    return Mono.just("Error parsing tool input: " + e.getMessage());
                                                }
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
                                        else if ("content_block_start".equals(streamingResponse.getType()) && 
                                                 streamingResponse.getContentBlock() != null &&
                                                 "text".equals(streamingResponse.getContentBlock().getType())) {
                                            
                                            // This is the start of a text block
                                            return Mono.empty();
                                        }
                                        else if ("message_start".equals(streamingResponse.getType())) {
                                            // This is the start of a message
                                            return Mono.empty();
                                        }
                                        else if ("message_delta".equals(streamingResponse.getType())) {
                                            // This is a message delta (usually the end)
                                            return Mono.empty();
                                        }
                                        else if ("message_stop".equals(streamingResponse.getType())) {
                                            // This is the end of a message
                                            return Mono.empty();
                                        }
                                        else if ("ping".equals(streamingResponse.getType())) {
                                            // This is a ping event to keep connection alive
                                            return Mono.empty();
                                        }
                                        else {
                                            log.warn("Unknown streaming response type: {}", streamingResponse.getType());
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
                                    log.error("Claude API error: {} - {}", response.statusCode(), errorBody);
                                })
                                .flatMapMany(errorBody -> Flux.just("Error from Claude API: " + response.statusCode() + " - " + errorBody));
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
        List<ClaudeMessage> messages = new ArrayList<>();
        String systemPrompt = "You are an AI Developer Agent, designed to help with coding, debugging, and development tasks. Use tools when appropriate to help solve problems.";
        
        // Convert the context messages to Claude format
        if (context != null && context.getMessages() != null) {
            for (Message message : context.getMessages()) {
                switch (message.getRole()) {
                    case "system":
                        // System messages go into the top-level system field, not in messages array
                        systemPrompt = message.getContent();
                        // Don't add to messages array - this was the bug
                        break;
                    case "user":
                        ClaudeMessage userMessage = new ClaudeMessage();
                        userMessage.setRole("user");
                        userMessage.setContent(List.of(new ClaudeContent("text", message.getContent())));
                        messages.add(userMessage);
                        break;
                    case "assistant":
                        ClaudeMessage assistantMessage = new ClaudeMessage();
                        assistantMessage.setRole("assistant");
                        
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
                            toolUse.put("id", message.getToolCall().getId());
                            toolUse.put("name", message.getToolCall().getName());
                            toolUse.put("input", message.getToolCall().getArguments());
                            
                            contents.add(ClaudeContent.toolUse("tool_use", toolUse));
                            assistantMessage.setContent(contents);
                        } else {
                            // Regular assistant message
                            assistantMessage.setContent(List.of(new ClaudeContent("text", message.getContent())));
                        }
                        
                        messages.add(assistantMessage);
                        break;
                    case "tool":
                        // Tool results are sent as user messages with tool_result content
                        ClaudeMessage toolResultMessage = new ClaudeMessage();
                        toolResultMessage.setRole("user");
                        
                        Map<String, Object> toolResult = new HashMap<>();
                        toolResult.put("tool_use_id", message.getToolCallId());
                        toolResult.put("content", message.getContent());
                        
                        List<ClaudeContent> contents = new ArrayList<>();
                        contents.add(ClaudeContent.toolResult("tool_result", toolResult));
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
        
        // Ensure messages array starts with a user message (Claude API requirement)
        if (!messages.isEmpty() && !"user".equals(messages.get(0).getRole())) {
            log.warn("First message is not from user, this may cause API errors");
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
    
    @Override
    public String getProviderName() {
        return "Claude (Anthropic API)";
    }
    
    // Claude API request/response models
    
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClaudeContent {
        private String type;
        private String text;
        @JsonProperty("tool_use")
        private Map<String, Object> toolUse;
        @JsonProperty("tool_result")
        private Map<String, Object> toolResult;
        
        // Constructor for text content
        public ClaudeContent(String type, String text) {
            this.type = type;
            this.text = text;
        }
        
        // Constructor for tool_use content
        public static ClaudeContent toolUse(String type, Map<String, Object> toolUse) {
            ClaudeContent content = new ClaudeContent();
            content.type = type;
            content.toolUse = toolUse;
            return content;
        }
        
        // Constructor for tool_result content  
        public static ClaudeContent toolResult(String type, Map<String, Object> toolResult) {
            ClaudeContent content = new ClaudeContent();
            content.type = type;
            content.toolResult = toolResult;
            return content;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
