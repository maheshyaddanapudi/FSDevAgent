package com.ai.developer.config;

import com.ai.developer.tools.ToolOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 */
@Slf4j
@Component
public class EnhancedToolOutputWebSocketHandler extends TextWebSocketHandler {
    
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
    public void broadcastToolOutput(ToolOutput output) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "tool_output");
        message.put("toolType", output.getType());
        message.put("content", output.getContent());
        message.put("metadata", output.getMetadata());
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
    public void broadcastAgentState(String sessionId, String phase, int progress, String currentTask) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "agent_state");
        message.put("sessionId", sessionId);
        message.put("phase", phase);
        message.put("progress", progress);
        message.put("currentTask", currentTask);
        message.put("timestamp", Instant.now().toString());
        
        broadcast(message);
    }
    
    /**
     * Broadcast phase transition
     */
    public void broadcastPhaseTransition(String sessionId, String fromPhase, String toPhase) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "phase_transition");
        message.put("sessionId", sessionId);
        message.put("fromPhase", fromPhase);
        message.put("toPhase", toPhase);
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
}
