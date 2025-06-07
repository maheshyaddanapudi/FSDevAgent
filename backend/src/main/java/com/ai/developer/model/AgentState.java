package com.ai.developer.model;

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
    private String sessionId;
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
    
    // Explicit setters to ensure compatibility
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
