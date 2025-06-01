package com.ai.developer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
// Removed @Configuration to prevent duplicate bean creation
// since @EnableConfigurationProperties(LLMConfig.class) in main application class already creates a bean

@Data
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {
    private String type = "claude";
    private String apiKey;
    private String model = "claude-3-7-sonnet-latest";
    private Double temperature = 0.7;
    private Integer maxTokens = 4000;
    private String endpoint;
}
