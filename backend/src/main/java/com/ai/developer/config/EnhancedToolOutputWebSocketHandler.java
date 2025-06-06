package com.ai.developer.config;

import com.ai.developer.model.DevelopmentPhase;
import com.ai.developer.tools.ToolOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced WebSocket handler for real-time tool output and execution events
 * Provides richer visualization and event broadcasting capabilities
 * to support the autonomous agent's execution loop
 */
@Slf4j
@Component
public class EnhancedToolOutputWebSocketHandler extends TextWebSocketHandler {
    
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    
    public EnhancedToolOutputWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle Java 8 date/time types
        this.objectMapper.registerModule(new JavaTimeModule());
        log.info("EnhancedToolOutputWebSocketHandler initialized with JavaTimeModule for Java 8 date/time support");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
        
        // Send welcome message
        Map<String, Object> welcome = new HashMap<>();
        welcome.put("type", "connection");
        welcome.put("message", "Connected to AI Developer Agent");
        welcome.put("timestamp", Instant.now().toString());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket connection closed: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Handle client messages if needed
        log.debug("Received message from client: {}", message.getPayload());
    }
    
    /**
     * Broadcast tool output to all connected clients
     */
    public void broadcastToolOutput(Object output) {
        try {
            String jsonOutput = objectMapper.writeValueAsString(output);
            TextMessage message = new TextMessage(jsonOutput);
            log.info("Broadcasting tool output to {} sessions: {}", sessions.size(), jsonOutput);
            
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        log.debug("Tool output sent to session: {}", session.getId());
                    }
                } catch (IOException e) {
                    log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error serializing tool output to JSON: {}", e.getMessage());
        }
    }
    
    /**
     * Broadcast tool output with specific type information
     */
    public void broadcastToolOutput(ToolOutput output) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_output");
        message.put("toolType", output.getType());
        message.put("content", output.getContent());
        message.put("metadata", output.getMetadata());
        message.put("success", output.isSuccess());
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast tool output as string
     */
    public void broadcastToolOutput(String output) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_output");
        message.put("content", output);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast tool output with event type and data
     */
    public void broadcastToolOutput(String eventType, String data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", eventType);
        message.put("content", data);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast tool usage event
     */
    public void broadcastToolUsage(String sessionId, String toolName, Map<String, Object> args, String toolId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_usage");
        message.put("sessionId", sessionId);
        message.put("toolName", toolName);
        message.put("toolId", toolId);
        message.put("arguments", args);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast tool result event
     */
    public void broadcastToolResult(String sessionId, String toolName, String result, String toolId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_result");
        message.put("sessionId", sessionId);
        message.put("toolName", toolName);
        message.put("toolId", toolId);
        message.put("result", result);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Send tool output to a specific tool type handler
     */
    public void sendToolOutput(String toolType, String data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_specific");
        message.put("toolType", toolType);
        message.put("data", data);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast agent state update
     */
    public void broadcastAgentState(String sessionId, DevelopmentPhase phase, int progress, String currentTask) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "agent_state");
        message.put("sessionId", sessionId);
        message.put("phase", phase.name());
        message.put("progress", progress);
        message.put("currentTask", currentTask);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast phase transition
     */
    public void broadcastPhaseTransition(String sessionId, DevelopmentPhase fromPhase, DevelopmentPhase toPhase) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "phase_transition");
        message.put("sessionId", sessionId);
        message.put("fromPhase", fromPhase.name());
        message.put("toPhase", toPhase.name());
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast error event
     */
    public void broadcastError(String sessionId, String error, String context) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "error");
        message.put("sessionId", sessionId);
        message.put("error", error);
        message.put("context", context);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Send tool output to a specific session
     */
    public void sendToolOutput(String sessionId, Object output) {
        for (WebSocketSession session : sessions) {
            if (session.getId().equals(sessionId) && session.isOpen()) {
                try {
                    String jsonOutput = objectMapper.writeValueAsString(output);
                    session.sendMessage(new TextMessage(jsonOutput));
                    log.info("Tool output sent to session {}: {}", sessionId, jsonOutput);
                } catch (IOException e) {
                    log.error("Error sending message to session {}: {}", sessionId, e.getMessage());
                }
                return;
            }
        }
        log.warn("Cannot send tool output - session {} not found or closed", sessionId);
    }
    
    /**
     * Internal broadcast method
     */
    private void broadcast(Map<String, Object> message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Error serializing message", e);
            return;
        }
        
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            } catch (IOException e) {
                log.error("Error sending message to session {}", session.getId(), e);
            }
        });
    }
    
    /**
     * Get the number of connected sessions
     */
    public int getConnectionCount() {
        return sessions.size();
    }
    
    /**
     * Broadcast agent state update
     */
    public void broadcastAgentStateUpdate(Map<String, Object> eventData) {
        log.debug("Broadcasting agent state update: {}", eventData);
        broadcast(eventData);
    }
    
    /**
     * Broadcast planning update
     */
    public void broadcastPlanningUpdate(Map<String, Object> eventData) {
        log.debug("Broadcasting planning update: {}", eventData);
        broadcast(eventData);
    }
    
    /**
     * Broadcast tool execution
     */
    public void broadcastToolExecution(Map<String, Object> eventData) {
        log.debug("Broadcasting tool execution: {}", eventData);
        broadcast(eventData);
    }
    
    /**
     * Broadcast tool result with map data
     */
    public void broadcastToolResult(Map<String, Object> eventData) {
        log.debug("Broadcasting tool result: {}", eventData);
        broadcast(eventData);
    }
    
    /**
     * Broadcast phase transition with map data
     */
    public void broadcastPhaseTransition(Map<String, Object> eventData) {
        log.debug("Broadcasting phase transition: {}", eventData);
        broadcast(eventData);
    }
    
    /**
     * Broadcast error event
     */
    public void broadcastErrorEvent(Map<String, Object> eventData) {
        log.debug("Broadcasting error event: {}", eventData);
        broadcast(eventData);
    }
}
