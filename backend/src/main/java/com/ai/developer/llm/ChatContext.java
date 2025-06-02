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
    private String systemPrompt;
    
    public ChatContext() {
    }
    
    public List<Message> getMessages() {
        return messages;
    }
    
    public void setMessages(List<Message> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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
        
        public Builder systemPrompt(String systemPrompt) {
            context.setSystemPrompt(systemPrompt);
            return this;
        }
        
        public ChatContext build() {
            return context;
        }
    }
}
