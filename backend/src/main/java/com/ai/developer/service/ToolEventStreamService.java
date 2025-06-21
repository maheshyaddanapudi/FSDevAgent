package com.ai.developer.service;

import com.ai.developer.model.ToolEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Server-Sent Event streams for tool outputs
 * Replaces WebSocket functionality with SSE
 * Uses Map<String, Object> format for consistency with existing services
 */
@Service
@Slf4j
public class ToolEventStreamService {
    
    // Session ID -> Event Sink mapping
    private final ConcurrentHashMap<String, Sinks.Many<ToolEventResponse>> sessionSinks = new ConcurrentHashMap<>();
    
    /**
     * Get or create an event stream for a session
     */
    public Flux<ToolEventResponse> getEventStream(String sessionId) {
        Sinks.Many<ToolEventResponse> sink = sessionSinks.computeIfAbsent(sessionId, id -> {
            log.info("Creating new tool event sink for session: {}", id);
            return Sinks.many().multicast().onBackpressureBuffer(1000);
        });
        
        // Send initial connection event
        sink.tryEmitNext(ToolEventResponse.builder()
            .type("connection")
            .sessionId(sessionId)
            .data(Map.of("status", "connected"))
            .timestamp(Instant.now())
            .build());
        
        return sink.asFlux()
            // Add heartbeat to keep connection alive
            .mergeWith(Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ToolEventResponse.builder()
                    .type("heartbeat")
                    .sessionId(sessionId)
                    .data(Map.of("tick", tick))
                    .timestamp(Instant.now())
                    .build()))
            // Clean up on completion
            .doOnCancel(() -> {
                log.info("Tool event stream cancelled for session: {}", sessionId);
                // Don't remove sink immediately - allow reconnection
            })
            .doOnError(error -> {
                log.error("Error in tool event stream for session {}: {}", sessionId, error.getMessage());
            });
    }
    
    /**
     * Broadcast tool execution event using Map format
     */
    public void broadcastToolExecution(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        String toolName = (String) eventData.get("toolName");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("tool_execution")
            .sessionId(sessionId)
            .toolName(toolName)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    /**
     * Broadcast tool result event using Map format
     */
    public void broadcastToolResult(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        String toolName = (String) eventData.get("toolName");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("tool_result")
            .sessionId(sessionId)
            .toolName(toolName)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    /**
     * Broadcast phase transition event using Map format
     */
    public void broadcastPhaseTransition(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("phase_transition")
            .sessionId(sessionId)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    /**
     * Broadcast agent state update using Map format
     */
    public void broadcastAgentStateUpdate(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("agent_state_update")
            .sessionId(sessionId)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    /**
     * Broadcast planning update using Map format
     */
    public void broadcastPlanningUpdate(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("planning")
            .sessionId(sessionId)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    /**
     * Broadcast error event using Map format
     */
    public void broadcastError(Map<String, Object> eventData) {
        String sessionId = (String) eventData.get("sessionId");
        
        ToolEventResponse event = ToolEventResponse.builder()
            .type("error")
            .sessionId(sessionId)
            .data(eventData)
            .timestamp(Instant.now())
            .build();
        
        emitToSession(sessionId, event);
    }
    
    // Convenience methods for individual parameters (for new code)
    
    /**
     * Broadcast tool result with individual parameters (for EnhancedChatService)
     */
    public void broadcastToolResult(String sessionId, String toolName, Object result, String toolCallId, boolean success) {
        Map<String, Object> eventData = Map.of(
            "sessionId", sessionId,
            "toolName", toolName,
            "result", result,
            "toolCallId", toolCallId != null ? toolCallId : "",
            "success", success,
            "timestamp", Instant.now().toString()
        );
        broadcastToolResult(eventData);
    }
    
    /**
     * Emit event to a specific session
     */
    private void emitToSession(String sessionId, ToolEventResponse event) {
        Sinks.Many<ToolEventResponse> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit tool event to session {}: {}", sessionId, result);
            } else {
                log.debug("Emitted tool event to session {}: type={}, tool={}", 
                    sessionId, event.getType(), event.getToolName());
            }
        } else {
            log.warn("No sink found for session: {}", sessionId);
        }
    }
    
    /**
     * Clean up resources for a session
     */
    public void cleanupSession(String sessionId) {
        Sinks.Many<ToolEventResponse> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Cleaned up tool event sink for session: {}", sessionId);
        }
    }
}

