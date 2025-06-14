package com.ai.developer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for streaming agent state updates to clients
 */
@Component
@Slf4j
public class AgentStateWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection for agent state: {}", session.getId());
        sessions.put(session.getId(), session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket connection closed for agent state: {}", sessionId);
    }

    /**
     * Broadcast agent state update to all connected clients
     */
    public void broadcastAgentState(String aiDeveloperAgentSessionId, String state) {
        String message = String.format("{\"aiDeveloperAgentSessionId\":\"%s\",\"state\":%s}", aiDeveloperAgentSessionId, state);
        broadcast(message);
    }

    /**
     * Broadcast agent progress update to all connected clients
     */
    public void broadcastProgress(String aiDeveloperAgentSessionId, String phase, int progress, String currentTask) {
        String message = String.format(
                "{\"aiDeveloperAgentSessionId\":\"%s\",\"type\":\"progress\",\"phase\":\"%s\",\"progress\":%d,\"currentTask\":\"%s\"}",
                aiDeveloperAgentSessionId, phase, progress, currentTask.replace("\"", "\\\""));
        broadcast(message);
    }
    
    /**
     * Broadcast message to all connected sessions
     */
    private void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        sessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                    log.debug("Message sent to session: {}", id);
                }
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", id, e.getMessage());
            }
        });
    }
}
