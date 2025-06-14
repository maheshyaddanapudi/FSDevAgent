package com.ai.developer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ai.developer.llm.ProjectContext;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the current state of the autonomous agent.
 * Extracted from AgentPromptService to be used across services.
 */
@Data
public class AgentState {
    @JsonProperty("aiDeveloperAgentSessionId")
    private String aiDeveloperAgentSessionId;
    private ProjectContext projectContext;
    private String currentObjective;
    private DevelopmentPhase currentPhase = DevelopmentPhase.ANALYSIS;
    private ConversationMode mode = ConversationMode.AUTONOMOUS;
    private boolean shouldContinue = true;
    private boolean waitingForUserInput = false;
    private String pendingQuestion;
    private String lastAction;
    private int iterationCount = 0;
    private int progress = 0;
    private List<String> completedTasks = new ArrayList<>();
    private List<String> pendingTasks = new ArrayList<>();
    private List<String> conversationHistory = new ArrayList<>();
    private Map<String, Object> memory = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;
    private String canonicalWorkspacePath;
    
    /**
     * Convert to TaskMemory for prompt generation
     */
    public TaskMemory toTaskMemory() {
        TaskMemory memory = new TaskMemory();
        memory.setObjective(currentObjective);
        memory.setPhase(currentPhase);
        memory.setCompletedTasks(new ArrayList<>(completedTasks));
        memory.setPendingTasks(new ArrayList<>(pendingTasks));
        memory.setProgressPercentage(progress);
        memory.setLastAction(lastAction);
        return memory;
    }
    
    /**
     * Set the canonical workspace path for this agent session
     */
    public void setCanonicalWorkspacePath(String path) {
        this.canonicalWorkspacePath = path;
    }
    
    /**
     * Get the canonical workspace path for this agent session
     */
    public String getCanonicalWorkspacePath() {
        return this.canonicalWorkspacePath;
    }
    
    /**
     * Update workspace state with directory structure information
     */
    public void updateWorkspaceState(String workspacePath, Map<String, Object> workspaceState) {
        this.memory.put("workspaceState", workspaceState);
        this.canonicalWorkspacePath = workspacePath;
    }
    
    /**
     * @deprecated Use getAiDeveloperAgentSessionId() instead.
     * This method is kept for backward compatibility during migration.
     */
    @Deprecated
    public String getSessionId() {
        return aiDeveloperAgentSessionId;
    }
    
    /**
     * @deprecated Use setAiDeveloperAgentSessionId(String) instead.
     * This method is kept for backward compatibility during migration.
     */
    @Deprecated
    public void setSessionId(String sessionId) {
        this.aiDeveloperAgentSessionId = sessionId;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
