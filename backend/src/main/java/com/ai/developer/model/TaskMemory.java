package com.ai.developer.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the memory of tasks for the autonomous agent.
 * Extracted from AgentPromptService to be used across services.
 */
@Data
public class TaskMemory {
    private String objective;
    private DevelopmentPhase phase;
    private List<String> completedTasks = new ArrayList<>();
    private List<String> pendingTasks = new ArrayList<>();
    private int progressPercentage;
    private String lastAction;
}
