package com.ai.developer.controller;

import com.ai.developer.service.autonomous.EventStreamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Controller for Server-Sent Events (SSE) streaming
 * Provides endpoints for real-time event streaming to the chat window
 */
@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
@Slf4j
public class EventStreamController {

    private final EventStreamingService eventStreamingService;

    public EventStreamController(EventStreamingService eventStreamingService) {
        this.eventStreamingService = eventStreamingService;
        log.info("EventStreamController initialized");
    }

    /**
     * Stream events for a specific session
     */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents(@PathVariable String sessionId) {
        log.info("Client connected to event stream for session: {}", sessionId);
        return eventStreamingService.createEventStream(sessionId);
    }
}
