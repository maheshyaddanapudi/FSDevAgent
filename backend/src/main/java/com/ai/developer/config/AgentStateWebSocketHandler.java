package com.ai.developer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for streaming agent state updates to clients
 */
@Component
@Slf4j
public class AgentStateWebSocketHandler implements WebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("New WebSocket connection for agent state: {}", session.getId());
        sessions.put(session.getId(), session);

        // Send messages to this client
        Mono<Void> output = session.send(
                sink.asFlux()
                        .map(session::textMessage)
        );

        // Remove session when connection is closed
        return output.doFinally(signalType -> {
            sessions.remove(session.getId());
            log.info("WebSocket connection closed for agent state: {}", session.getId());
        });
    }

    /**
     * Broadcast agent state update to all connected clients
     */
    public void broadcastAgentState(String sessionId, String state) {
        String message = String.format("{\"sessionId\":\"%s\",\"state\":%s}", sessionId, state);
        sink.tryEmitNext(message);
    }

    /**
     * Broadcast agent progress update to all connected clients
     */
    public void broadcastProgress(String sessionId, String phase, int progress, String currentTask) {
        String message = String.format(
                "{\"sessionId\":\"%s\",\"type\":\"progress\",\"phase\":\"%s\",\"progress\":%d,\"currentTask\":\"%s\"}",
                sessionId, phase, progress, currentTask.replace("\"", "\\\""));
        sink.tryEmitNext(message);
    }
}
