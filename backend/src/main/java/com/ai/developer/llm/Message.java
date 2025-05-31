package com.ai.developer.llm;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class Message {
    private String role; // user, assistant, system, tool
    private String content;
    private String toolCallId;
    private ToolCall toolCall;
    private Instant timestamp;
}
