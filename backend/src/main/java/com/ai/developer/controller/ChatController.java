package com.ai.developer.controller;

import com.ai.developer.model.*;
import com.ai.developer.service.EnhancedChatService;
import com.ai.developer.tools.ToolOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {
    
    private final EnhancedChatService chatService;
    
    // Session creation endpoint moved to SessionController to avoid mapping conflicts
    // and to leverage enhanced session management capabilities
    
    @GetMapping("/sessions/{aiDeveloperAgentSessionId}/history")
    public Mono<List<ChatResponse>> getSessionHistory(@PathVariable String aiDeveloperAgentSessionId) {
        log.info("Getting history for session: {}", aiDeveloperAgentSessionId);
        return chatService.getSessionHistory(aiDeveloperAgentSessionId)
            .doOnSuccess(history -> log.info("Retrieved history for session {}: {} messages", aiDeveloperAgentSessionId, history.size()))
            .doOnError(error -> log.error("Error retrieving history for session: {}", aiDeveloperAgentSessionId, error));
    }
    
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request for session {}: {}", request.getAiDeveloperAgentSessionId(), request.getMessage());
        return chatService.processMessage(request)
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", request.getAiDeveloperAgentSessionId()))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", request.getAiDeveloperAgentSessionId()))
            .doOnError(error -> log.error("Error processing message for session {}", request.getAiDeveloperAgentSessionId(), error));
    }
    
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatGet(@RequestParam String aiDeveloperAgentSessionId, @RequestParam String message) {
        log.info("Received GET chat request for session {}: {}", aiDeveloperAgentSessionId, message);
        ChatRequest request = ChatRequest.builder()
            .aiDeveloperAgentSessionId(aiDeveloperAgentSessionId)
            .message(message)
            .build();
            
        return chatService.processMessage(request)
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", aiDeveloperAgentSessionId))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", aiDeveloperAgentSessionId))
            .doOnError(error -> log.error("Error processing message for session {}", aiDeveloperAgentSessionId, error));
    }
    
    @PostMapping(value = "/tools/{toolName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ToolCallResponse> executeTool(
            @PathVariable String toolName,
            @RequestParam String aiDeveloperAgentSessionId,
            @RequestBody Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, aiDeveloperAgentSessionId, arguments);
        return chatService.executeTool(aiDeveloperAgentSessionId, toolName, arguments)
            .map(toolOutput -> convertToToolCallResponse(toolName, aiDeveloperAgentSessionId, arguments, toolOutput))
            .doOnNext(output -> log.info("Tool {} execution output for session {}: {}", toolName, aiDeveloperAgentSessionId, output))
            .doOnError(error -> log.error("Error executing tool {} for session {}", toolName, aiDeveloperAgentSessionId, error));
    }
    
    /**
     * Convert ToolOutput to ToolCallResponse for API compatibility
     */
    private ToolCallResponse convertToToolCallResponse(String toolName, String aiDeveloperAgentSessionId, Map<String, Object> arguments, ToolOutput toolOutput) {
        return ToolCallResponse.builder()
                .name(toolName)
                .arguments(arguments)
                .result(toolOutput.getContent())
                .aiDeveloperAgentSessionId(aiDeveloperAgentSessionId)
                .build();
    }
}
