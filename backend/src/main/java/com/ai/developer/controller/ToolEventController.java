package com.ai.developer.controller;

import com.ai.developer.model.ToolEventResponse;
import com.ai.developer.service.ToolEventStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for streaming tool events via Server-Sent Events (SSE)
 * Replaces WebSocket functionality with SSE for tool output streaming
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ToolEventController {
    
    private final ToolEventStreamService toolEventStreamService;
    
    /**
     * Stream tool events for a specific session via SSE
     */
    @GetMapping(value = "/sessions/{aiDeveloperAgentSessionId}/tool-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ToolEventResponse> streamToolEvents(@PathVariable String aiDeveloperAgentSessionId) {
        log.info("Starting tool event stream for session: {}", aiDeveloperAgentSessionId);
        
        return toolEventStreamService.getEventStream(aiDeveloperAgentSessionId)
            .doOnSubscribe(subscription -> {
                log.info("Client subscribed to tool events for session: {}", aiDeveloperAgentSessionId);
            })
            .doOnCancel(() -> {
                log.info("Client cancelled tool event subscription for session: {}", aiDeveloperAgentSessionId);
            })
            .doOnError(error -> {
                log.error("Error in tool event stream for session {}: {}", aiDeveloperAgentSessionId, error.getMessage());
            })
            .onErrorResume(error -> {
                // Send error event and continue stream
                return Flux.just(ToolEventResponse.builder()
                    .type("error")
                    .sessionId(aiDeveloperAgentSessionId)
                    .data(Map.of(
                        "message", "Stream error: " + error.getMessage(),
                        "severity", "error"
                    ))
                    .timestamp(Instant.now())
                    .build());
            });
    }
    
    /**
     * Health check endpoint for tool event streaming
     */
    @GetMapping("/tool-events/health")
    public Mono<Map<String, Object>> toolEventHealth() {
        return Mono.just(Map.of(
            "status", "healthy",
            "service", "tool-event-streaming",
            "timestamp", Instant.now()
        ));
    }
}

