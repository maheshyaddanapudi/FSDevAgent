package com.ai.developer.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a chat context containing messages and other information.
 * This class is used to store the conversation history and context for LLM interactions.
 */
public class ChatContext {
    private final List<Message> messages = new ArrayList<>();
    private String sessionId;
    
    public ChatContext() {
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final ChatContext context = new ChatContext();
        
        public Builder messages(List<Message> messages) {
            if (messages != null) {
                context.getMessages().addAll(messages);
            }
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            context.setSessionId(sessionId);
            return this;
        }
        
        public ChatContext build() {
            return context;
        }
    }
}
