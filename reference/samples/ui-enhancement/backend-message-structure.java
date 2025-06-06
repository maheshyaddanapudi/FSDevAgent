// Enhanced Message Structure for Claude-like UI
package com.ai.developer.llm.providers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnhancedMessage {
    private String id;
    private String role;
    private String content;
    private Instant timestamp;
    private MessageStatus status;
    
    // Claude-style sections
    private String thinking;
    private List<ToolCall> toolCalls;
    private List<ToolResult> toolResults;
    
    // Streaming support
    private boolean isComplete;
    private StreamingMetadata streaming;
    
    public enum MessageStatus {
        PENDING, STREAMING, COMPLETE, ERROR
    }
    
    @Data
    @Builder
    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> args;
        private ToolStatus status;
        private Instant startTime;
        private Instant endTime;
        
        public enum ToolStatus {
            PENDING, EXECUTING, COMPLETED, FAILED
        }
    }
    
    @Data
    @Builder
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private String output;
        private String outputType; // "text", "code", "json", "image"
        private String language; // for code outputs
        private boolean error;
        private String errorMessage;
        private Map<String, Object> metadata;
        private boolean important; // auto-expand in UI
    }
    
    @Data
    @Builder
    public static class StreamingMetadata {
        private String chunkType; // "content", "thinking", "tool_call", "tool_result"
        private int chunkIndex;
        private boolean isFirstChunk;
        private boolean isLastChunk;
    }
}

// Enhanced SSE Streaming Service
@Service
public class EnhancedStreamingService {
    private final SseEmitter.SseEventBuilder createEvent(String eventType, Object data) {
        return SseEmitter.event()
            .name(eventType)
            .data(data)
            .id(UUID.randomUUID().toString());
    }
    
    public void streamMessage(SseEmitter emitter, EnhancedMessage message) {
        try {
            // Stream thinking first (if available)
            if (message.getThinking() != null) {
                streamThinking(emitter, message.getId(), message.getThinking());
            }
            
            // Stream main content
            streamContent(emitter, message.getId(), message.getContent());
            
            // Stream tool calls
            if (message.getToolCalls() != null) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    streamToolCall(emitter, message.getId(), toolCall);
                }
            }
            
            // Send completion event
            emitter.send(createEvent("message_complete", Map.of(
                "messageId", message.getId(),
                "timestamp", Instant.now()
            )));
            
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
    
    private void streamThinking(SseEmitter emitter, String messageId, String thinking) throws IOException {
        // Stream thinking in chunks for smooth animation
        String[] chunks = splitIntoChunks(thinking, 50); // 50 chars per chunk
        
        for (int i = 0; i < chunks.length; i++) {
            emitter.send(createEvent("thinking_chunk", Map.of(
                "messageId", messageId,
                "content", chunks[i],
                "chunkIndex", i,
                "isFirst", i == 0,
                "isLast", i == chunks.length - 1
            )));
            
            // Small delay for smooth streaming effect
            Thread.sleep(20);
        }
    }
    
    private void streamContent(SseEmitter emitter, String messageId, String content) throws IOException {
        // Smart chunking based on content structure
        List<ContentChunk> chunks = intelligentChunking(content);
        
        for (ContentChunk chunk : chunks) {
            emitter.send(createEvent("content_chunk", Map.of(
                "messageId", messageId,
                "content", chunk.getText(),
                "type", chunk.getType(), // "text", "code_block", "list_item"
                "metadata", chunk.getMetadata()
            )));
            
            Thread.sleep(chunk.getDelay()); // Variable delay based on content
        }
    }
    
    private void streamToolCall(SseEmitter emitter, String messageId, ToolCall toolCall) throws IOException {
        // Send tool call start
        emitter.send(createEvent("tool_call_start", Map.of(
            "messageId", messageId,
            "toolCall", toolCall
        )));
        
        // Execute tool and stream results
        executeToolWithStreaming(toolCall, (result) -> {
            try {
                emitter.send(createEvent("tool_result_chunk", Map.of(
                    "messageId", messageId,
                    "toolCallId", toolCall.getId(),
                    "chunk", result
                )));
            } catch (IOException e) {
                log.error("Error streaming tool result", e);
            }
        });
        
        // Send tool call complete
        emitter.send(createEvent("tool_call_complete", Map.of(
            "messageId", messageId,
            "toolCallId", toolCall.getId()
        )));
    }
}

// Enhanced Chat Service with proper message handling
@Service
public class EnhancedChatService {
    
    @Autowired
    private ClaudeApiService claudeService;
    
    @Autowired
    private EnhancedStreamingService streamingService;
    
    @Autowired
    private ToolExecutor toolExecutor;
    
    public void processMessageWithStreaming(String sessionId, String userMessage, SseEmitter emitter) {
        try {
            // Create enhanced message structure
            EnhancedMessage userMsg = EnhancedMessage.builder()
                .id(generateMessageId())
                .role("user")
                .content(userMessage)
                .timestamp(Instant.now())
                .status(MessageStatus.COMPLETE)
                .build();
            
            // Send user message immediately
            emitter.send(SseEmitter.event()
                .name("user_message")
                .data(userMsg));
            
            // Process with Claude
            ClaudeResponse response = claudeService.processWithTools(userMessage);
            
            // Build enhanced assistant message
            EnhancedMessage assistantMsg = EnhancedMessage.builder()
                .id(generateMessageId())
                .role("assistant")
                .content(response.getContent())
                .thinking(response.getThinking())
                .toolCalls(convertToolCalls(response.getToolUse()))
                .timestamp(Instant.now())
                .status(MessageStatus.STREAMING)
                .build();
            
            // Stream the assistant message
            streamingService.streamMessage(emitter, assistantMsg);
            
        } catch (Exception e) {
            handleStreamingError(emitter, e);
        }
    }
}