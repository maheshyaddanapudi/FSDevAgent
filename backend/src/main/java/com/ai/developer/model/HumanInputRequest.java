package com.ai.developer.model;

import java.util.List;

/**
 * Model class representing a human input request from the AI agent
 */
public class HumanInputRequest {
    private String aiDeveloperAgentSessionId;
    private String text;
    private List<String> attachments;
    private String id;
    
    public String getAiDeveloperAgentSessionId() {
        return aiDeveloperAgentSessionId;
    }
    
    public void setAiDeveloperAgentSessionId(String aiDeveloperAgentSessionId) {
        this.aiDeveloperAgentSessionId = aiDeveloperAgentSessionId;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public List<String> getAttachments() {
        return attachments;
    }
    
    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
}
