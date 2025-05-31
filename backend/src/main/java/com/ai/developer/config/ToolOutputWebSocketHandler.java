package com.ai.developer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ToolOutputWebSocketHandler extends TextWebSocketHandler {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
    
    public void sendToolOutput(String sessionId, Object output) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String jsonOutput = objectMapper.writeValueAsString(output);
                session.sendMessage(new TextMessage(jsonOutput));
                log.info("Tool output sent to session {}: {}", sessionId, jsonOutput);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", sessionId, e.getMessage());
            }
        } else {
            log.warn("Cannot send tool output - session {} not found or closed", sessionId);
        }
    }
}
