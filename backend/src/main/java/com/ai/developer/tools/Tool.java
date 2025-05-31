package com.ai.developer.tools;

import reactor.core.publisher.Flux;
import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, ParameterInfo> getParameters();
    Flux<ToolOutput> execute(Map<String, Object> arguments);
}
