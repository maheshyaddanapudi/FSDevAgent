package com.ai.developer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration for WebSocket handlers
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EnhancedToolOutputWebSocketHandler toolOutputHandler;
    private final AgentStateWebSocketHandler agentStateHandler;
    
    public WebSocketConfig(EnhancedToolOutputWebSocketHandler toolOutputHandler, 
                          AgentStateWebSocketHandler agentStateHandler) {
        this.toolOutputHandler = toolOutputHandler;
        this.agentStateHandler = agentStateHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(toolOutputHandler, "/ws/tool-output")
                .setAllowedOrigins("*");
        registry.addHandler(agentStateHandler, "/ws/agent-state")
                .setAllowedOrigins("*");
    }
}
