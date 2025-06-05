package com.ai.developer.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a chat context containing messages and other information.
 * This class is used to store the conversation history and context for LLM interactions.
 */
public class ChatContext {
    private final List<Message> messages = new ArrayList<>();
    private String sessionId;
    private String systemPrompt;
    private Map<String, Object> metadata;
    
    public ChatContext() {
        this.metadata = new HashMap<>();
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
        // Also store in metadata for tool propagation
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("sessionId", sessionId);
    }
    
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Get the workspace path for this chat context
     * @return The workspace path or null if not set
     */
    public String getWorkspacePath() {
        if (metadata != null && metadata.containsKey("workspacePath")) {
            return (String) metadata.get("workspacePath");
        }
        return null;
    }
    
    /**
     * Set the workspace path for this chat context
     * @param workspacePath The workspace path to set
     */
    public void setWorkspacePath(String workspacePath) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("workspacePath", workspacePath);
    }
    
    /**
     * Get the current task directory for this chat context
     * @return The task directory or null if not set
     */
    public String getTaskDir() {
        if (metadata != null && metadata.containsKey("taskDir")) {
            return (String) metadata.get("taskDir");
        }
        return null;
    }
    
    /**
     * Set the current task directory for this chat context
     * @param taskDir The task directory to set
     */
    public void setTaskDir(String taskDir) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("taskDir", taskDir);
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
        
        public Builder metadata(Map<String, Object> metadata) {
            context.setMetadata(metadata);
            return this;
        }
        
        public Builder workspacePath(String workspacePath) {
            context.setWorkspacePath(workspacePath);
            return this;
        }
        
        public Builder taskDir(String taskDir) {
            context.setTaskDir(taskDir);
            return this;
        }
        
        public ChatContext build() {
            return context;
        }
    }
}
