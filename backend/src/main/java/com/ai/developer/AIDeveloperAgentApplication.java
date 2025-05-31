package com.ai.developer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AIDeveloperAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AIDeveloperAgentApplication.class, args);
    }
}
