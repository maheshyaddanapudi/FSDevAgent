package com.ai.developer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolOutputWebSocketHandler extends TextWebSocketHandler {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    public ToolOutputWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle Java 8 date/time types
        this.objectMapper.registerModule(new JavaTimeModule());
        log.info("ToolOutputWebSocketHandler initialized with JavaTimeModule for Java 8 date/time support");
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket connection established: {}", sessionId);
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Handle incoming messages if needed
        log.debug("Received message from client: {}", message.getPayload());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket connection closed: {}", sessionId);
    }
    
    public void broadcastToolOutput(Object output) {
        try {
            String jsonOutput = objectMapper.writeValueAsString(output);
            TextMessage message = new TextMessage(jsonOutput);
            log.info("Broadcasting tool output to {} sessions: {}", sessions.size(), jsonOutput);
            
            sessions.forEach((id, session) -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        log.debug("Tool output sent to session: {}", id);
                    }
                } catch (IOException e) {
                    log.error("Error sending message to session {}: {}", id, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error serializing tool output to JSON: {}", e.getMessage());
        }
    }
    
    // Enhanced method to broadcast tool usage events specifically
    public void broadcastToolUsage(String aiDeveloperAgentSessionId, String toolName, Object arguments, String toolCallId) {
        try {
            // Create a specialized tool usage event
            ToolUsageEvent event = new ToolUsageEvent();
            event.setType("tool_usage");
            event.setAiDeveloperAgentSessionId(aiDeveloperAgentSessionId);
            event.setToolName(toolName);
            event.setArguments(arguments);
            event.setToolCallId(toolCallId);
            event.setTimestamp(System.currentTimeMillis());
            
            String jsonOutput = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(jsonOutput);
            log.info("Broadcasting tool usage event for tool {}: {}", toolName, jsonOutput);
            
            sessions.forEach((id, session) -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        log.debug("Tool usage event sent to session: {}", id);
                    }
                } catch (IOException e) {
                    log.error("Error sending tool usage event to session {}: {}", id, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error serializing tool usage event to JSON: {}", e.getMessage());
        }
    }
    
    // Enhanced method to broadcast tool results specifically
    public void broadcastToolResult(String aiDeveloperAgentSessionId, String toolName, String result, String toolCallId) {
        try {
            // Create a specialized tool result event
            ToolResultEvent event = new ToolResultEvent();
            event.setType("tool_result");
            event.setAiDeveloperAgentSessionId(aiDeveloperAgentSessionId);
            event.setToolName(toolName);
            event.setResult(result);
            event.setToolCallId(toolCallId);
            event.setTimestamp(System.currentTimeMillis());
            
            String jsonOutput = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(jsonOutput);
            log.info("Broadcasting tool result event for tool {}: {}", toolName, jsonOutput);
            
            sessions.forEach((id, session) -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        log.debug("Tool result event sent to session: {}", id);
                    }
                } catch (IOException e) {
                    log.error("Error sending tool result event to session {}: {}", id, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Error serializing tool result event to JSON: {}", e.getMessage());
        }
    }
    
    public void sendToolOutput(String aiDeveloperAgentSessionId, Object output) {
        WebSocketSession session = sessions.get(aiDeveloperAgentSessionId);
        if (session != null && session.isOpen()) {
            try {
                String jsonOutput = objectMapper.writeValueAsString(output);
                session.sendMessage(new TextMessage(jsonOutput));
                log.info("Tool output sent to session {}: {}", aiDeveloperAgentSessionId, jsonOutput);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", aiDeveloperAgentSessionId, e.getMessage());
            }
        } else {
            log.warn("Cannot send tool output - session {} not found or closed", aiDeveloperAgentSessionId);
        }
    }
    
    // Inner class for tool usage events
    private static class ToolUsageEvent {
        private String type;
        private String aiDeveloperAgentSessionId;
        private String toolName;
        private Object arguments;
        private String toolCallId;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getAiDeveloperAgentSessionId() { return aiDeveloperAgentSessionId; }
        public void setAiDeveloperAgentSessionId(String aiDeveloperAgentSessionId) { this.aiDeveloperAgentSessionId = aiDeveloperAgentSessionId; }
        
        // For backward compatibility
        public String getSessionId() { return aiDeveloperAgentSessionId; }
        public void setSessionId(String sessionId) { this.aiDeveloperAgentSessionId = sessionId; }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Object getArguments() { return arguments; }
        public void setArguments(Object arguments) { this.arguments = arguments; }
        
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for tool result events
    private static class ToolResultEvent {
        private String type;
        private String aiDeveloperAgentSessionId;
        private String toolName;
        private String result;
        private String toolCallId;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getAiDeveloperAgentSessionId() { return aiDeveloperAgentSessionId; }
        public void setAiDeveloperAgentSessionId(String aiDeveloperAgentSessionId) { this.aiDeveloperAgentSessionId = aiDeveloperAgentSessionId; }
        
        // For backward compatibility
        public String getSessionId() { return aiDeveloperAgentSessionId; }
        public void setSessionId(String sessionId) { this.aiDeveloperAgentSessionId = sessionId; }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        
        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
