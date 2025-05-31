package com.ai.developer.llm;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatContext {
    private String sessionId;
    private List<Message> messages;
    private Map<String, Object> metadata;
    private List<Map<String, Object>> availableTools;
    private ProjectContext projectContext;
}
