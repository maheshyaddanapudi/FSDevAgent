package com.ai.developer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for WebSocket handlers
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public SimpleUrlHandlerMapping webSocketHandlerMapping(ToolOutputWebSocketHandler toolOutputHandler,
                                                          AgentStateWebSocketHandler agentStateHandler) {
        Map<String, WebSocketHandler> urlMap = new HashMap<>();
        urlMap.put("/ws/tool-output", toolOutputHandler);
        urlMap.put("/ws/agent-state", agentStateHandler);
        
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(1);
        mapping.setUrlMap(urlMap);
        return mapping;
    }
}
