package com.ai.developer.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LLMProvider {
    Mono<String> generateResponse(String prompt, ChatContext context);
    Flux<String> streamResponse(String prompt, ChatContext context);
    String getProviderName();
}
