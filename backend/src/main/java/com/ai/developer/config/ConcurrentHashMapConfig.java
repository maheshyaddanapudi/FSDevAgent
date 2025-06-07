package com.ai.developer.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for shared ConcurrentHashMap instances
 * Note: The agentStates bean is now defined in AgentControlConfig
 */
@Configuration
public class ConcurrentHashMapConfig {
    // Bean definition moved to AgentControlConfig to avoid conflicts
}
