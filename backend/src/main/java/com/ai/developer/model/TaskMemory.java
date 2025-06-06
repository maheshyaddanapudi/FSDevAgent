package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the memory of a task across multiple iterations.
 * This model is used to maintain context and state for multi-step planning and execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMemory {
    private String sessionId;
    private String taskId;
    private String objective;
    private DevelopmentPhase phase;
    private List<TaskStep> completedSteps;
    private List<String> pendingSteps;
    private Map<String, String> learnedPatterns;
    private List<TaskCheckpoint> checkpoints;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant lastUpdated;
    private String lastAction;
    private int progressPercentage;
    
    /**
     * Calculate the progress percentage based on completed vs total steps
     * @return percentage of task completion (0-100)
     */
    public int getProgressPercentage() {
        if (progressPercentage > 0) {
            return progressPercentage;
        }
        
        int completed = completedSteps != null ? completedSteps.size() : 0;
        int pending = pendingSteps != null ? pendingSteps.size() : 0;
        int total = completed + pending;
        
        if (total == 0) return 0;
        return (completed * 100) / total;
    }
    
    /**
     * Get completed tasks as list of strings for compatibility with AgentState
     */
    public ArrayList<String> getCompletedTasks() {
        ArrayList<String> tasks = new ArrayList<>();
        if (completedSteps != null) {
            for (TaskStep step : completedSteps) {
                tasks.add(step.getDescription());
            }
        }
        return tasks;
    }
    
    /**
     * Get pending tasks as list of strings for compatibility with AgentState
     */
    public ArrayList<String> getPendingTasks() {
        return pendingSteps != null ? new ArrayList<>(pendingSteps) : new ArrayList<>();
    }
    
    /**
     * Set completed tasks from list of strings
     */
    public void setCompletedTasks(ArrayList<String> tasks) {
        if (completedSteps == null) {
            completedSteps = new ArrayList<>();
        } else {
            completedSteps.clear();
        }
        
        if (tasks != null) {
            for (String task : tasks) {
                completedSteps.add(TaskStep.builder()
                    .description(task)
                    .timestamp(Instant.now())
                    .build());
            }
        }
    }
    
    /**
     * Set pending tasks from list of strings
     */
    public void setPendingTasks(ArrayList<String> tasks) {
        if (pendingSteps == null) {
            pendingSteps = new ArrayList<>();
        } else {
            pendingSteps.clear();
        }
        
        if (tasks != null) {
            pendingSteps.addAll(tasks);
        }
    }
    
    /**
     * Represents a completed step in a task
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStep {
        private String description;
        private Instant timestamp;
        private Map<String, Object> result;
    }
    
    /**
     * Represents a checkpoint for potential rollback
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskCheckpoint {
        private String id;
        private Instant timestamp;
        private String description;
        private Map<String, Object> sessionState;
    }
}
