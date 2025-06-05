package com.ai.developer.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface for LLM providers.
 * Enhanced to support multi-turn conversations and agentic framework.
 */
public interface LLMProvider {
    /**
     * Generate a complete response for a prompt
     * @param prompt The user prompt
     * @param context The chat context
     * @return A Mono containing the complete response
     */
    Mono<String> generateResponse(String prompt, ChatContext context);
    
    /**
     * Stream a response for a prompt
     * @param prompt The user prompt
     * @param context The chat context
     * @return A Flux of response chunks
     */
    Flux<String> streamResponse(String prompt, ChatContext context);
    
    /**
     * Get the provider name
     * @return The provider name
     */
    String getProviderName();
    
    /**
     * Get the maximum context size supported by this provider
     * @return The maximum context size in tokens
     */
    default int getMaxContextSize() {
        return 4000; // Default value
    }
    
    /**
     * Check if the provider supports streaming
     * @return true if streaming is supported, false otherwise
     */
    default boolean supportsStreaming() {
        return true; // Default to true
    }
    
    /**
     * Check if the provider supports tool use
     * @return true if tool use is supported, false otherwise
     */
    default boolean supportsToolUse() {
        return true; // Default to true
    }
    
    /**
     * Estimate token count for a message
     * @param message The message to estimate
     * @return The estimated token count
     */
    default int estimateTokenCount(String message) {
        if (message == null) {
            return 0;
        }
        // Very rough estimate: 4 characters per token
        return message.length() / 4;
    }
}
