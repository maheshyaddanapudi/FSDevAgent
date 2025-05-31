package com.ai.developer.llm.providers;

import com.ai.developer.config.LLMConfig;
import com.ai.developer.llm.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
// Removed @ConditionalOnProperty to avoid conflicts with CustomClaudeLLMProvider
// This provider is now disabled in favor of the custom implementation
public class ClaudeLLMProvider implements LLMProvider {
    
    private final LLMConfig config;
    private AnthropicChatModel chatModel;
    private AnthropicStreamingChatModel streamingModel;
    
    // Constructor is now private to prevent Spring from autowiring this bean
    private ClaudeLLMProvider(LLMConfig config) {
        this.config = config;
        log.info("LangChain4j Claude LLM Provider is disabled in favor of custom implementation");
    }
    
    @PostConstruct
    public void init() {
        // No initialization to prevent this bean from being used
    }
    
    @Override
    public Mono<String> generateResponse(String prompt, ChatContext context) {
        return Mono.error(new UnsupportedOperationException("This provider is disabled"));
    }
    
    @Override
    public Flux<String> streamResponse(String prompt, ChatContext context) {
        return Flux.error(new UnsupportedOperationException("This provider is disabled"));
    }
    
    @Override
    public String getProviderName() {
        return "Claude (Disabled LangChain4j)";
    }
    
    private List<ChatMessage> convertMessages(ChatContext context) {
        return new ArrayList<>();
    }
}
