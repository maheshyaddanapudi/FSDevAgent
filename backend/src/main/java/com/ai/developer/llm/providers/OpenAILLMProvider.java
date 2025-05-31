package com.ai.developer.llm.providers;

import com.ai.developer.config.LLMConfig;
import com.ai.developer.llm.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "llm.type", havingValue = "openai")
public class OpenAILLMProvider implements LLMProvider {
    
    private final LLMConfig config;
    private OpenAiChatModel chatModel;
    private OpenAiStreamingChatModel streamingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel() != null ? config.getModel() : "gpt-4-turbo")
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
                
        this.streamingModel = OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModel() != null ? config.getModel() : "gpt-4-turbo")
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
    }
    
    @Override
    public Mono<String> generateResponse(String prompt, ChatContext context) {
        return Mono.fromCallable(() -> {
            List<ChatMessage> messages = convertMessages(context);
            messages.add(UserMessage.from(prompt));
            
            Response<AiMessage> response = chatModel.generate(messages);
            return response.content().text();
        });
    }
    
    @Override
    public Flux<String> streamResponse(String prompt, ChatContext context) {
        return Flux.create(sink -> {
            List<ChatMessage> messages = convertMessages(context);
            messages.add(UserMessage.from(prompt));
            
            streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    sink.next(token);
                }
                
                @Override
                public void onComplete(Response<AiMessage> response) {
                    sink.complete();
                }
                
                @Override
                public void onError(Throwable error) {
                    log.error("Error streaming response from OpenAI", error);
                    sink.error(error);
                }
            });
        });
    }
    
    @Override
    public String getProviderName() {
        return "OpenAI";
    }
    
    private List<ChatMessage> convertMessages(ChatContext context) {
        List<ChatMessage> result = new ArrayList<>();
        
        if (context.getMessages() == null || context.getMessages().isEmpty()) {
            return result;
        }
        
        for (Message msg : context.getMessages()) {
            switch (msg.getRole()) {
                case "user":
                    result.add(UserMessage.from(msg.getContent()));
                    break;
                case "assistant":
                    result.add(AiMessage.from(msg.getContent()));
                    break;
                case "system":
                    result.add(SystemMessage.from(msg.getContent()));
                    break;
                default:
                    result.add(UserMessage.from(msg.getContent()));
            }
        }
        
        return result;
    }
    
    private String mapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Error converting map to JSON", e);
            return "{}";
        }
    }
}
