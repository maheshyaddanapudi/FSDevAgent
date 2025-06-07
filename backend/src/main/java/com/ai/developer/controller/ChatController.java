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
    
    @GetMapping("/sessions/{sessionId}/history")
    public Mono<List<ChatResponse>> getSessionHistory(@PathVariable String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        return chatService.getSessionHistory(sessionId)
            .doOnSuccess(history -> log.info("Retrieved history for session {}: {} messages", sessionId, history.size()))
            .doOnError(error -> log.error("Error retrieving history for session: {}", sessionId, error));
    }
    
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> processMessage(@RequestParam String sessionId, @RequestParam String message) {
        log.info("Received chat request for session {}: {}", sessionId, message);
        
        // CRITICAL FIX: Always trigger autonomous execution loop for all requests
        // This ensures the backend drives agent autonomy regardless of endpoint
        return chatService.executeAutonomousLoop(sessionId, message)
                .doOnNext(response -> log.info("Autonomous execution response for session {}", sessionId))
                .doOnComplete(() -> log.info("Completed autonomous execution for session {}", sessionId))
                .doOnError(error -> log.error("Error in autonomous execution for session {}", sessionId, error));
    }
    
    @PostMapping(value = "/pause", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> pauseExecution(@RequestParam String sessionId) {
        log.info("Received pause request for session {}", sessionId);
        return chatService.pauseExecution(sessionId)
                .map(result -> Map.of(
                    "success", true,
                    "message", "Execution paused for session " + sessionId,
                    "sessionId", sessionId
                ))
                .onErrorResume(error -> {
                    log.error("Error pausing execution for session {}", sessionId, error);
                    return Mono.just(Map.of(
                        "success", false,
                        "message", "Failed to pause execution: " + error.getMessage(),
                        "sessionId", sessionId
                    ));
                });
    }
    
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> resumeExecution(@RequestParam String sessionId) {
        log.info("Received resume request for session {}", sessionId);
        return chatService.resumeExecution(sessionId)
                .doOnNext(response -> log.info("Resume response for session {}", sessionId))
                .doOnComplete(() -> log.info("Completed resume for session {}", sessionId))
                .doOnError(error -> log.error("Error in resume for session {}", sessionId, error));
    }e for session {}", request.getSessionId(), error));
    }
    
    @PostMapping(value = "/autonomous", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> autonomousExecution(@RequestBody ChatRequest request) {
        log.info("Received autonomous execution request for session {}: {}", 
                 request.getSessionId(), request.getMessage());
        return chatService.executeAutonomousLoop(request)
            .doOnNext(response -> log.info("Autonomous execution response for session {}", 
                                          request.getSessionId()))
            .doOnComplete(() -> log.info("Completed autonomous execution for session {}", 
                                        request.getSessionId()))
            .doOnError(error -> log.error("Error in autonomous execution for session {}", 
                                         request.getSessionId(), error));
    }
    
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatGet(@RequestParam String sessionId, @RequestParam String message) {
        log.info("Received GET chat request for session {}: {}", sessionId, message);
        ChatRequest request = ChatRequest.builder()
            .sessionId(sessionId)
            .message(message)
            .build();
            
        return Flux.from(chatService.processMessage(request))
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", sessionId))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", sessionId))
            .doOnError(error -> log.error("Error processing message for session {}", sessionId, error));
    }
    
    @PostMapping(value = "/tools/{toolName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ToolCallResponse> executeTool(
            @PathVariable String toolName,
            @RequestParam String sessionId,
            @RequestBody Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        return chatService.executeTool(sessionId, toolName, arguments)
            .doOnNext(response -> log.info("Tool {} execution output for session {}: {}", toolName, sessionId, response.getResult()))
            .doOnComplete(() -> log.info("Completed tool {} execution for session {}", toolName, sessionId))
            .doOnError(error -> log.error("Error executing tool {} for session {}", toolName, sessionId, error));
    }
}
