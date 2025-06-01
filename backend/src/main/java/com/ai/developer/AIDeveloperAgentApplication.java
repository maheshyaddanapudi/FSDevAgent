package com.ai.developer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ai.developer.config.LLMConfig;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(LLMConfig.class)
public class AIDeveloperAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AIDeveloperAgentApplication.class, args);
    }
}
