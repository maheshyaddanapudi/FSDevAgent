package com.ai.developer.controller;

import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.Message;
import com.ai.developer.model.*;
import com.ai.developer.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Controller for chat-related endpoints.
 * Enhanced to support multi-turn conversations and session management.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    
    /**
     * Create a new chat session
     */
    @PostMapping("/sessions")
    public Mono<SessionResponseDTO> createSession() {
        log.info("Creating new session");
        return chatService.createSession()
            .map(SessionResponseDTO::fromSessionResponse)
            .doOnSuccess(session -> log.info("Session created successfully: {}", session.getSessionId()))
            .doOnError(error -> log.error("Error creating session", error));
    }
    
    /**
     * Get all active sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions() {
        log.info("Getting all sessions");
        List<String> sessions = chatService.getSessions();
        log.info("Retrieved {} sessions", sessions.size());
        return ResponseEntity.ok(sessions);
    }
    
    /**
     * Get a specific session
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatContext> getSession(@PathVariable String sessionId) {
        log.info("Getting session: {}", sessionId);
        ChatContext context = chatService.getSession(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();
        }
        log.info("Retrieved session: {}", sessionId);
        return ResponseEntity.ok(context);
    }
    
    /**
     * Delete a session
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        log.info("Deleting session: {}", sessionId);
        boolean deleted = chatService.deleteSession(sessionId);
        if (!deleted) {
            log.warn("Session not found for deletion: {}", sessionId);
            return ResponseEntity.notFound().build();
        }
        log.info("Deleted session: {}", sessionId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get session history
     */
    @GetMapping("/sessions/{sessionId}/history")
    public Mono<List<ChatResponse>> getSessionHistory(@PathVariable String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        return chatService.getSessionHistory(sessionId)
            .doOnSuccess(history -> log.info("Retrieved history for session {}: {} messages", sessionId, history.size()))
            .doOnError(error -> log.error("Error retrieving history for session: {}", sessionId, error));
    }
    
    /**
     * Get session context metadata
     */
    @GetMapping("/sessions/{sessionId}/metadata")
    public ResponseEntity<Map<String, Object>> getSessionMetadata(@PathVariable String sessionId) {
        log.info("Getting metadata for session: {}", sessionId);
        ChatContext context = chatService.getSession(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> metadata = context.getMetadata();
        log.info("Retrieved metadata for session: {}", sessionId);
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * Process a chat message (POST)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request for session {}: {}", request.getSessionId(), request.getMessage());
        return chatService.processMessage(request)
            .doOnNext(response -> log.info("Sending chat response chunk for session {}", request.getSessionId()))
            .doOnComplete(() -> log.info("Completed sending chat response for session {}", request.getSessionId()))
            .doOnError(error -> log.error("Error processing message for session {}", request.getSessionId(), error));
    }
    
    /**
     * Process a chat message (GET)
     */
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
    
    /**
     * Execute a tool directly
     */
    @PostMapping(value = "/tools/{toolName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<com.ai.developer.tools.ToolOutput> executeTool(
            @PathVariable String toolName,
            @RequestParam String sessionId,
            @RequestBody Map<String, Object> arguments) {
        log.info("Executing tool {} for session {} with arguments: {}", toolName, sessionId, arguments);
        return chatService.executeToolCall(sessionId, toolName, arguments)
            .doOnNext(output -> log.info("Tool {} execution output for session {}: {}", toolName, sessionId, output))
            .doOnComplete(() -> log.info("Completed tool {} execution for session {}", toolName, sessionId))
            .doOnError(error -> log.error("Error executing tool {} for session {}", toolName, sessionId, error));
    }
    
    /**
     * Find references in conversation history
     */
    @GetMapping("/sessions/{sessionId}/references")
    public ResponseEntity<List<Message>> findReferences(
            @PathVariable String sessionId,
            @RequestParam String query) {
        log.info("Finding references for query '{}' in session: {}", query, sessionId);
        ChatContext context = chatService.getSession(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();
        }
        List<Message> references = context.findReferences(query);
        log.info("Found {} references for query '{}' in session: {}", references.size(), query, sessionId);
        return ResponseEntity.ok(references);
    }
}
