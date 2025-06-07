package com.ai.developer.service.autonomous;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.model.AgentState;
import com.ai.developer.service.AgentControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing Server-Sent Events (SSE) streaming to the chat window
 * Ensures real-time updates of thinking, reasoning, and action details
 */
@Service
@Slf4j
public class EventStreamingService {

    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final AgentControlService agentControlService;
    private final ConcurrentHashMap<String, AgentState> agentStates;
    private final ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sseEmitters = new ConcurrentHashMap<>();

    public EventStreamingService(EnhancedToolOutputWebSocketHandler webSocketHandler,
                               AgentControlService agentControlService,
                               ConcurrentHashMap<String, AgentState> agentStates) {
        this.webSocketHandler = webSocketHandler;
        this.agentControlService = agentControlService;
        this.agentStates = agentStates;
        log.info("EventStreamingService initialized for real-time SSE streaming");
    }

    /**
     * Create a new SSE stream for a session
     */
    public Flux<ServerSentEvent<String>> createEventStream(String sessionId) {
        log.info("Creating SSE event stream for session: {}", sessionId);
        
        // Create a new sink for this session if it doesn't exist
        Sinks.Many<ServerSentEvent<String>> sink = sseEmitters.computeIfAbsent(sessionId, 
            k -> Sinks.many().multicast().onBackpressureBuffer());
        
        // Send initial connection event
        sendEvent(sessionId, "connection", "SSE stream established for session: " + sessionId);
        
        // Return the flux from the sink
        return sink.asFlux()
            .doOnCancel(() -> {
                log.info("SSE stream cancelled for session: {}", sessionId);
                // Don't remove the sink as other subscribers might still be using it
            })
            .doOnError(e -> {
                log.error("Error in SSE stream for session {}: {}", sessionId, e.getMessage(), e);
            });
    }
    
    /**
     * Send an event to the SSE stream for a session
     */
    public void sendEvent(String sessionId, String eventType, String data) {
        Sinks.Many<ServerSentEvent<String>> sink = sseEmitters.get(sessionId);
        if (sink == null) {
            log.warn("No SSE sink found for session: {}", sessionId);
            return;
        }
        
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
            .id(UUID.randomUUID().toString())
            .event(eventType)
            .data(data)
            .build();
        
        sink.tryEmitNext(event);
        log.debug("Sent SSE event of type {} to session {}", eventType, sessionId);
    }
    
    /**
     * Send a thinking event to the SSE stream
     */
    public void sendThinkingEvent(String sessionId, String thought) {
        sendEvent(sessionId, "thinking", thought);
        
        // Also broadcast to WebSocket for unified emulator
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "thinking");
        eventData.put("content", thought);
        webSocketHandler.broadcastAgentStateUpdate(eventData);
    }
    
    /**
     * Send a reasoning event to the SSE stream
     */
    public void sendReasoningEvent(String sessionId, String reasoning) {
        sendEvent(sessionId, "reasoning", reasoning);
        
        // Also broadcast to WebSocket for unified emulator
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "reasoning");
        eventData.put("content", reasoning);
        webSocketHandler.broadcastAgentStateUpdate(eventData);
    }
    
    /**
     * Send an action event to the SSE stream
     */
    public void sendActionEvent(String sessionId, String action, Map<String, Object> details) {
        // Convert details to JSON string
        String detailsJson = details != null ? details.toString() : "{}";
        sendEvent(sessionId, "action", action + "\n" + detailsJson);
        
        // Also broadcast to WebSocket for unified emulator
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "action");
        eventData.put("content", action);
        eventData.put("details", details);
        webSocketHandler.broadcastAgentStateUpdate(eventData);
        
        // Update agent state
        AgentState agentState = agentStates.get(sessionId);
        if (agentState != null) {
            agentState.setLastAction(action);
        }
    }
    
    /**
     * Send a tool use event to the SSE stream
     */
    public void sendToolUseEvent(String sessionId, String toolName, Map<String, Object> args) {
        Map<String, Object> details = new HashMap<>();
        details.put("toolName", toolName);
        details.put("args", args);
        
        sendEvent(sessionId, "tool_use", toolName);
        
        // Also broadcast to WebSocket for unified emulator
        agentControlService.broadcastToolExecution(sessionId, toolName, args, "executing");
    }
    
    /**
     * Send a tool result event to the SSE stream
     */
    public void sendToolResultEvent(String sessionId, String toolName, Map<String, Object> args, Object result, boolean success) {
        Map<String, Object> details = new HashMap<>();
        details.put("toolName", toolName);
        details.put("args", args);
        details.put("result", result);
        details.put("success", success);
        
        sendEvent(sessionId, "tool_result", success ? "Success: " + toolName : "Failed: " + toolName);
        
        // Also broadcast to WebSocket for unified emulator
        agentControlService.broadcastToolResult(sessionId, toolName, args, result, success);
    }
    
    /**
     * Send a planning event to the SSE stream
     */
    public void sendPlanningEvent(String sessionId, int step, int totalSteps, String description) {
        String planningInfo = String.format("Planning step %d/%d: %s", step, totalSteps, description);
        sendEvent(sessionId, "planning", planningInfo);
        
        // Also broadcast to WebSocket for unified emulator
        agentControlService.broadcastPlanningEvent(sessionId, step, totalSteps, description, null);
    }
    
    /**
     * Send an error event to the SSE stream
     */
    public void sendErrorEvent(String sessionId, String message, String severity) {
        sendEvent(sessionId, "error", severity + ": " + message);
        
        // Also broadcast to WebSocket for unified emulator
        agentControlService.broadcastErrorEvent(sessionId, message, severity, null);
    }
    
    /**
     * Close the SSE stream for a session
     */
    public void closeEventStream(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> sink = sseEmitters.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Closed SSE stream for session: {}", sessionId);
        }
    }
}
