package com.ai.developer.config;

import com.ai.developer.model.AgentState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for Agent Control components
 * Provides necessary beans for agent state management
 */
@Configuration
public class AgentControlConfig {

    /**
     * Creates a ConcurrentHashMap bean for storing agent states by session ID
     * This is required by AgentControlService for managing agent execution states
     */
    @Bean
    public ConcurrentHashMap<String, AgentState> agentStates() {
        return new ConcurrentHashMap<>();
    }
}
