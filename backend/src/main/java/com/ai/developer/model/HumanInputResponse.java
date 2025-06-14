package com.ai.developer.model;

/**
 * Model class representing a human input response to the AI agent
 */
public class HumanInputResponse {
    private String aiDeveloperAgentSessionId;
    private String response;
    private String requestId;
    
    public String getAiDeveloperAgentSessionId() {
        return aiDeveloperAgentSessionId;
    }
    
    public void setAiDeveloperAgentSessionId(String aiDeveloperAgentSessionId) {
        this.aiDeveloperAgentSessionId = aiDeveloperAgentSessionId;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
