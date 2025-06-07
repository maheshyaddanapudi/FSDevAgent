package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a chat message in a conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /**
     * The role of the message sender (e.g., "user", "assistant", "system").
     */
    private String role;
    
    /**
     * The content of the message.
     */
    private String content;
    
    /**
     * The timestamp when the message was created.
     */
    private String timestamp;
}
