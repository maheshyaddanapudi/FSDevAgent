package com.ai.developer.controller;

import com.ai.developer.model.SessionResponse;
import com.ai.developer.model.SessionResponseDTO;
import com.ai.developer.service.ChatService;
import com.ai.developer.service.EnhancedChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST controller for session management operations
 * Provides endpoints for creating, listing, retrieving, and deleting sessions
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SessionController {

    private final ChatService chatService;
    private final EnhancedChatService enhancedChatService;

    /**
     * Create a new session
     */
    @PostMapping
    public Mono<SessionResponseDTO> createSession() {
        log.info("REST request to create new session");
        return chatService.createSession()
            .map(SessionResponseDTO::fromSessionResponse)
            .doOnSuccess(session -> {
                log.info("Session created successfully: {}", session.getSessionId());
                // Ensure the session is also registered with EnhancedChatService
                enhancedChatService.registerExistingSession(session.getSessionId());
            })
            .doOnError(error -> log.error("Error creating session", error));
    }

    /**
     * Get all active sessions
     */
    @GetMapping
    public List<String> getAllSessions() {
        log.info("REST request to get all sessions");
        return enhancedChatService.getSessions();
    }

    /**
     * Delete a session
     */
    @DeleteMapping("/{sessionId}")
    public boolean deleteSession(@PathVariable String sessionId) {
        log.info("REST request to delete session: {}", sessionId);
        boolean enhancedResult = enhancedChatService.deleteSession(sessionId);
        boolean chatResult = chatService.deleteSession(sessionId);
        return enhancedResult || chatResult;
    }
}
