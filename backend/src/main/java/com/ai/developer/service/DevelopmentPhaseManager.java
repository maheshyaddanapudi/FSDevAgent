package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.model.DevelopmentPhase;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages development phase transitions and tracks progress through the autonomous agent workflow.
 * This service enables structured progression through development phases and provides metrics
 * for phase completion status.
 */
@Slf4j
@Service
public class DevelopmentPhaseManager {
    
    private final Map<String, PhaseTracker> sessionPhases = new ConcurrentHashMap<>();
    
    @Autowired
    private EnhancedToolOutputWebSocketHandler webSocketHandler;
    
    /**
     * Initialize phase tracking for a session
     */
    public void initializeSession(String sessionId) {
        PhaseTracker tracker = PhaseTracker.builder()
                .currentPhase(DevelopmentPhase.ANALYSIS)
                .phaseHistory(new ArrayList<>())
                .phaseMetrics(new HashMap<>())
                .currentPhaseStart(Instant.now())
                .currentPhaseTasks(0)
                .build();
        
        sessionPhases.put(sessionId, tracker);
        log.info("Initialized phase tracking for session: {}", sessionId);
    }
    
    /**
     * Transition to a new phase
     */
    public PhaseTransitionResult transitionPhase(String sessionId, DevelopmentPhase newPhase) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        if (tracker == null) {
            initializeSession(sessionId);
            tracker = sessionPhases.get(sessionId);
        }
        
        DevelopmentPhase previousPhase = tracker.getCurrentPhase();
        
        // Record phase completion
        if (tracker.getCurrentPhaseStart() != null) {
            Duration phaseDuration = Duration.between(tracker.getCurrentPhaseStart(), Instant.now());
            PhaseMetric metric = PhaseMetric.builder()
                    .phase(previousPhase)
                    .startTime(tracker.getCurrentPhaseStart())
                    .endTime(Instant.now())
                    .duration(phaseDuration)
                    .tasksCompleted(tracker.getCurrentPhaseTasks())
                    .build();
            
            tracker.getPhaseHistory().add(metric);
            tracker.getPhaseMetrics().put(previousPhase, metric);
        }
        
        // Start new phase
        tracker.setCurrentPhase(newPhase);
        tracker.setCurrentPhaseStart(Instant.now());
        tracker.setCurrentPhaseTasks(0);
        
        log.info("Session {} transitioned from {} to {}", sessionId, previousPhase, newPhase);
        
        // Broadcast phase transition via WebSocket
        if (webSocketHandler != null) {
            webSocketHandler.broadcastPhaseTransition(sessionId, previousPhase, newPhase);
        }
        
        return PhaseTransitionResult.builder()
                .sessionId(sessionId)
                .fromPhase(previousPhase)
                .toPhase(newPhase)
                .transitionTime(Instant.now())
                .build();
    }
    
    /**
     * Get suggested next phase based on current progress
     */
    public DevelopmentPhase suggestNextPhase(String sessionId) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        if (tracker == null) {
            return DevelopmentPhase.ANALYSIS;
        }
        
        DevelopmentPhase currentPhase = tracker.getCurrentPhase();
        
        // Standard phase progression
        return switch (currentPhase) {
            case ANALYSIS -> DevelopmentPhase.DESIGN;
            case DESIGN -> DevelopmentPhase.IMPLEMENTATION;
            case IMPLEMENTATION -> DevelopmentPhase.TESTING;
            case TESTING -> DevelopmentPhase.DEPLOYMENT;
            case DEPLOYMENT -> DevelopmentPhase.DEPLOYMENT; // Stay in deployment
        };
    }
    
    /**
     * Check if current phase is complete based on criteria
     */
    public PhaseCompletionStatus checkPhaseCompletion(String sessionId, List<String> completedTasks) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        if (tracker == null) {
            return PhaseCompletionStatus.builder()
                    .isComplete(false)
                    .completionPercentage(0)
                    .remainingTasks(Collections.emptyList())
                    .build();
        }
        
        DevelopmentPhase currentPhase = tracker.getCurrentPhase();
        Map<String, Boolean> phaseCriteria = getPhaseCompletionCriteria(currentPhase);
        
        // Calculate completion
        int totalCriteria = phaseCriteria.size();
        int metCriteria = 0;
        List<String> remainingTasks = new ArrayList<>();
        
        for (Map.Entry<String, Boolean> criterion : phaseCriteria.entrySet()) {
            boolean isMet = checkCriterion(criterion.getKey(), completedTasks);
            if (isMet) {
                metCriteria++;
            } else {
                remainingTasks.add(criterion.getKey());
            }
        }
        
        int completionPercentage = totalCriteria > 0 ? (metCriteria * 100) / totalCriteria : 0;
        boolean isComplete = completionPercentage >= 80; // 80% threshold for phase completion
        
        // Update agent state via WebSocket if significant progress
        if (webSocketHandler != null && completionPercentage > 0) {
            webSocketHandler.broadcastAgentState(sessionId, currentPhase, completionPercentage, 
                    "Working on " + currentPhase.name().toLowerCase() + " phase");
        }
        
        return PhaseCompletionStatus.builder()
                .isComplete(isComplete)
                .completionPercentage(completionPercentage)
                .remainingTasks(remainingTasks)
                .suggestedNextPhase(isComplete ? suggestNextPhase(sessionId) : null)
                .build();
    }
    
    /**
     * Record task completion in current phase
     */
    public void recordTaskCompletion(String sessionId, String taskDescription) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        if (tracker != null) {
            tracker.setCurrentPhaseTasks(tracker.getCurrentPhaseTasks() + 1);
            log.debug("Recorded task completion for session {}: {}", sessionId, taskDescription);
            
            // Update agent state via WebSocket
            if (webSocketHandler != null) {
                webSocketHandler.broadcastAgentState(sessionId, tracker.getCurrentPhase(), 
                        calculateProgressPercentage(tracker), 
                        "Completed: " + taskDescription);
            }
        }
    }
    
    /**
     * Calculate progress percentage for current phase
     */
    private int calculateProgressPercentage(PhaseTracker tracker) {
        // Simple calculation based on tasks completed
        // In a real implementation, this would be more sophisticated
        int baseTasks = 5; // Assume minimum 5 tasks per phase
        return Math.min(100, (tracker.getCurrentPhaseTasks() * 100) / (baseTasks + tracker.getCurrentPhaseTasks()));
    }
    
    /**
     * Get phase metrics for a session
     */
    public SessionPhaseMetrics getSessionMetrics(String sessionId) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        if (tracker == null) {
            return SessionPhaseMetrics.builder()
                    .sessionId(sessionId)
                    .phaseMetrics(Collections.emptyMap())
                    .totalDuration(Duration.ZERO)
                    .build();
        }
        
        Duration totalDuration = tracker.getPhaseHistory().stream()
                .map(PhaseMetric::getDuration)
                .reduce(Duration.ZERO, Duration::plus);
        
        return SessionPhaseMetrics.builder()
                .sessionId(sessionId)
                .currentPhase(tracker.getCurrentPhase())
                .phaseMetrics(new HashMap<>(tracker.getPhaseMetrics()))
                .phaseHistory(new ArrayList<>(tracker.getPhaseHistory()))
                .totalDuration(totalDuration)
                .build();
    }
    
    /**
     * Get current phase for a session
     */
    public DevelopmentPhase getCurrentPhase(String sessionId) {
        PhaseTracker tracker = sessionPhases.get(sessionId);
        return tracker != null ? tracker.getCurrentPhase() : DevelopmentPhase.ANALYSIS;
    }
    
    /**
     * Get completion criteria for a phase
     */
    private Map<String, Boolean> getPhaseCompletionCriteria(DevelopmentPhase phase) {
        Map<String, Boolean> criteria = new HashMap<>();
        
        switch (phase) {
            case ANALYSIS -> {
                criteria.put("Requirements documented", true);
                criteria.put("Technical stack decided", true);
                criteria.put("Project structure planned", true);
                criteria.put("Dependencies identified", false);
            }
            case DESIGN -> {
                criteria.put("Architecture designed", true);
                criteria.put("Database schema created", true);
                criteria.put("API contracts defined", true);
                criteria.put("UI mockups created", false);
            }
            case IMPLEMENTATION -> {
                criteria.put("Core entities created", true);
                criteria.put("API endpoints implemented", true);
                criteria.put("Frontend components built", true);
                criteria.put("Integration completed", true);
            }
            case TESTING -> {
                criteria.put("Unit tests written", true);
                criteria.put("Integration tests created", true);
                criteria.put("API tests passing", true);
                criteria.put("UI tests implemented", false);
            }
            case DEPLOYMENT -> {
                criteria.put("Docker files created", true);
                criteria.put("CI/CD configured", false);
                criteria.put("Environment variables set", true);
                criteria.put("Deployment scripts ready", true);
            }
        }
        
        return criteria;
    }
    
    /**
     * Check if a specific criterion is met
     */
    private boolean checkCriterion(String criterion, List<String> completedTasks) {
        String lowerCriterion = criterion.toLowerCase();
        return completedTasks.stream()
                .anyMatch(task -> task.toLowerCase().contains(lowerCriterion) ||
                                 criterionMatchesTask(lowerCriterion, task.toLowerCase()));
    }
    
    /**
     * Advanced criterion matching
     */
    private boolean criterionMatchesTask(String criterion, String task) {
        // Map criterion keywords to task patterns
        Map<String, List<String>> criterionPatterns = Map.of(
            "requirements", List.of("requirement", "analyze", "gather", "document"),
            "architecture", List.of("design", "architect", "structure", "pattern"),
            "database", List.of("schema", "table", "entity", "migration"),
            "api", List.of("endpoint", "controller", "rest", "service"),
            "test", List.of("test", "spec", "assertion", "coverage")
        );
        
        for (Map.Entry<String, List<String>> entry : criterionPatterns.entrySet()) {
            if (criterion.contains(entry.getKey())) {
                return entry.getValue().stream().anyMatch(task::contains);
            }
        }
        
        return false;
    }
    
    @Data
    @Builder
    private static class PhaseTracker {
        private DevelopmentPhase currentPhase;
        private Instant currentPhaseStart;
        private int currentPhaseTasks;
        private List<PhaseMetric> phaseHistory;
        private Map<DevelopmentPhase, PhaseMetric> phaseMetrics;
    }
    
    @Data
    @Builder
    public static class PhaseMetric {
        private DevelopmentPhase phase;
        private Instant startTime;
        private Instant endTime;
        private Duration duration;
        private int tasksCompleted;
    }
    
    @Data
    @Builder
    public static class PhaseTransitionResult {
        private String sessionId;
        private DevelopmentPhase fromPhase;
        private DevelopmentPhase toPhase;
        private Instant transitionTime;
    }
    
    @Data
    @Builder
    public static class PhaseCompletionStatus {
        private boolean isComplete;
        private int completionPercentage;
        private List<String> remainingTasks;
        private DevelopmentPhase suggestedNextPhase;
    }
    
    @Data
    @Builder
    public static class SessionPhaseMetrics {
        private String sessionId;
        private DevelopmentPhase currentPhase;
        private Map<DevelopmentPhase, PhaseMetric> phaseMetrics;
        private List<PhaseMetric> phaseHistory;
        private Duration totalDuration;
    }
}
