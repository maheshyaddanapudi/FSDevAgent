package com.ai.developer.llm.providers;

import com.ai.developer.config.LLMConfig;
import com.ai.developer.llm.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gemini LLM Provider - Stubbed for v1
 * Google AI integration is deferred to v2 due to dependency availability issues
 */
@Slf4j
// Removed @Component annotation to prevent bean registration
// @Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.type", havingValue = "gemini")
public class GeminiLLMProvider {
    
    private final LLMConfig config;
    
    @PostConstruct
    public void init() {
        log.info("Gemini LLM Provider is stubbed in v1 and will not be functional");
        log.info("Google AI integration is planned for v2");
    }
    
    public String getCompletion(ChatContext context) {
        throw new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers.");
    }
    
    public String streamingCompletion(ChatContext context, Consumer<String> onPartialResponse) {
        throw new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers.");
    }
    
    public List<ToolCall> extractToolCalls(String response) {
        throw new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers.");
    }
    
    public List<ToolUseBlock> extractToolUseBlocks(String response) {
        throw new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers.");
    }
    
    // Legacy methods - kept for backward compatibility
    public Mono<String> generateResponse(String prompt, ChatContext context) {
        return Mono.error(new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers."));
    }
    
    public Flux<String> streamResponse(String prompt, ChatContext context) {
        return Flux.error(new UnsupportedOperationException(
            "Gemini integration is not available in v1. Please use Claude or OpenAI providers."));
    }
    
    public String getProviderName() {
        return "Google Gemini (Planned for v2)";
    }
}
