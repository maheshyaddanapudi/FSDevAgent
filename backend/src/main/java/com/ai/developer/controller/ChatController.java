package com.ai.developer.controller;

import com.ai.developer.model.*;
import com.ai.developer.service.ChatService;
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
    
    private final ChatService chatService;
    
    @PostMapping("/sessions")
    public Mono<SessionResponse> createSession() {
        log.info("Creating new session");
        return chatService.createSession()
            .doOnSuccess(session -> log.info("Session created successfully: {}", session.getSessionId()))
            .doOnError(error -> log.error("Error creating session", error));
    }
    
    @GetMapping("/sessions/{sessionId}/history")
    public Mono<List<ChatResponse>> getSessionHistory(@PathVariable String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        return chatService.getSessionHistory(sessionId)
            .doOnSuccess(history -> log.info("Retrieved history for session {}: {} messages", sessionId, history.size()))
            .doOnError(error -> log.error("Error retrieving history for session: {}", sessionId, error));
    }
    
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request for session {}: {}", request.getSessionId(), request.getMessage());
        return chatService.processMessage(request)
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", request.getSessionId()))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", request.getSessionId()))
            .doOnError(error -> log.error("Error processing message for session {}", request.getSessionId(), error));
    }
    
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatGet(@RequestParam String sessionId, @RequestParam String message) {
        log.info("Received GET chat request for session {}: {}", sessionId, message);
        ChatRequest request = ChatRequest.builder()
            .sessionId(sessionId)
            .message(message)
            .build();
            
        return chatService.processMessage(request)
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", sessionId))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", sessionId))
            .doOnError(error -> log.error("Error processing message for session {}", sessionId, error));
    }
    
    @PostMapping(value = "/tools/{toolName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ToolOutput> executeTool(
            @PathVariable String toolName,
            @RequestParam String sessionId,
            @RequestBody Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        return chatService.executeToolCall(sessionId, toolName, arguments)
            .doOnNext(output -> log.info("Tool {} execution output for session {}: {}", toolName, sessionId, output))
            .doOnComplete(() -> log.info("Completed tool {} execution for session {}", toolName, sessionId))
            .doOnError(error -> log.error("Error executing tool {} for session {}", toolName, sessionId, error));
    }
}
