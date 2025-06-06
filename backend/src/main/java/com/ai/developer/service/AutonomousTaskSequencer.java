package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.model.DevelopmentPhase;
import com.ai.developer.model.TaskMemory;
import com.ai.developer.service.DevelopmentPhaseManager.PhaseCompletionStatus;
import com.ai.developer.service.ErrorRecoveryService.ErrorSeverity;
import com.ai.developer.service.ErrorRecoveryService.RecoveryResult;
import com.ai.developer.service.ErrorRecoveryService.NextAction;
import com.ai.developer.service.TaskMemoryService.TaskMemoryUpdate;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core execution framework for autonomous task sequencing and coordination.
 * This service enables the agent to break down complex tasks into ordered steps,
 * track dependencies, and execute steps in the correct sequence.
 */
@Slf4j
@Service
public class AutonomousTaskSequencer {
    
    @Autowired
    private TaskMemoryService taskMemoryService;
    
    @Autowired
    private DevelopmentPhaseManager phaseManager;
    
    @Autowired
    private ErrorRecoveryService errorRecoveryService;
    
    @Autowired
    private EnhancedToolOutputWebSocketHandler webSocketHandler;
    
    private final Map<String, ExecutionContext> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();
    
    /**
     * Initialize a new task execution
     */
    public ExecutionPlan initializeExecution(String sessionId, String taskId, String objective) {
        // Create a unique execution ID
        String executionId = sessionId + ":" + taskId;
        
        // Initialize step counter
        stepCounters.putIfAbsent(executionId, new AtomicInteger(0));
        
        // Create execution context
        ExecutionContext context = ExecutionContext.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .objective(objective)
                .status(ExecutionStatus.INITIALIZED)
                .currentPhase(DevelopmentPhase.ANALYSIS)
                .startTime(Instant.now())
                .build();
        
        activeExecutions.put(executionId, context);
        
        // Initialize task memory
        TaskMemoryUpdate memoryUpdate = TaskMemoryUpdate.builder()
                .objective(objective)
                .build();
        
        taskMemoryService.createOrUpdateMemory(sessionId, taskId, memoryUpdate);
        
        // Initialize phase tracking
        phaseManager.initializeSession(sessionId);
        
        log.info("Initialized execution for session {}, task {}: {}", sessionId, taskId, objective);
        
        // Create initial execution plan (empty)
        ExecutionPlan plan = ExecutionPlan.builder()
                .executionId(executionId)
                .objective(objective)
                .steps(new ArrayList<>())
                .build();
        
        return plan;
    }
    
    /**
     * Create or update an execution plan
     */
    public ExecutionPlan createOrUpdatePlan(String sessionId, String taskId, List<ExecutionStep> steps) {
        String executionId = sessionId + ":" + taskId;
        ExecutionContext context = activeExecutions.get(executionId);
        
        if (context == null) {
            throw new IllegalStateException("Execution not initialized for session " + sessionId + ", task " + taskId);
        }
        
        // Update execution context
        context.setStatus(ExecutionStatus.PLANNING);
        context.setLastUpdated(Instant.now());
        
        // Create execution plan
        ExecutionPlan plan = ExecutionPlan.builder()
                .executionId(executionId)
                .objective(context.getObjective())
                .steps(steps)
                .build();
        
        // Validate plan
        validatePlan(plan);
        
        // Update task memory with pending steps
        List<String> pendingStepDescriptions = steps.stream()
                .map(ExecutionStep::getDescription)
                .collect(Collectors.toList());
        
        TaskMemoryUpdate memoryUpdate = TaskMemoryUpdate.builder()
                .pendingSteps(pendingStepDescriptions)
                .build();
        
        taskMemoryService.createOrUpdateMemory(sessionId, taskId, memoryUpdate);
        
        // Broadcast plan creation via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> planData = new HashMap<>();
            planData.put("sessionId", sessionId);
            planData.put("executionId", executionId);
            planData.put("objective", context.getObjective());
            planData.put("stepCount", steps.size());
            planData.put("timestamp", Instant.now().toString());
            webSocketHandler.broadcastPlanningUpdate(planData);
        }
        
        log.info("Created execution plan for session {}, task {} with {} steps", 
                sessionId, taskId, steps.size());
        
        return plan;
    }
    
    /**
     * Execute the next step in the plan
     */
    public StepExecutionResult executeNextStep(String sessionId, String taskId) {
        String executionId = sessionId + ":" + taskId;
        ExecutionContext context = activeExecutions.get(executionId);
        
        if (context == null) {
            throw new IllegalStateException("Execution not initialized for session " + sessionId + ", task " + taskId);
        }
        
        // Get next pending step from task memory
        Optional<String> nextStepOpt = taskMemoryService.getNextPendingStep(sessionId, taskId);
        if (nextStepOpt.isEmpty()) {
            // No more steps to execute
            context.setStatus(ExecutionStatus.COMPLETED);
            context.setEndTime(Instant.now());
            
            return StepExecutionResult.builder()
                    .executionId(executionId)
                    .success(true)
                    .status(StepStatus.COMPLETED)
                    .message("All steps completed")
                    .build();
        }
        
        String nextStep = nextStepOpt.get();
        int stepNumber = stepCounters.get(executionId).incrementAndGet();
        
        // Update execution context
        context.setStatus(ExecutionStatus.EXECUTING);
        context.setCurrentStep(nextStep);
        context.setLastUpdated(Instant.now());
        
        // Broadcast step execution via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("sessionId", sessionId);
            stepData.put("executionId", executionId);
            stepData.put("step", nextStep);
            stepData.put("stepNumber", stepNumber);
            stepData.put("timestamp", Instant.now().toString());
            stepData.put("status", "started");
            webSocketHandler.broadcastToolExecution(stepData);
        }
        
        log.info("Executing step {} for session {}, task {}: {}", 
                stepNumber, sessionId, taskId, nextStep);
        
        // Simulate step execution (in a real implementation, this would execute the actual step)
        // For now, we'll just mark it as completed
        Map<String, Object> result = new HashMap<>();
        result.put("stepNumber", stepNumber);
        result.put("executionTime", System.currentTimeMillis());
        
        // Mark step as completed in task memory
        taskMemoryService.completeNextPendingStep(sessionId, taskId, result);
        
        // Check if this completes the current phase
        List<String> completedTasks = taskMemoryService.getMemory(sessionId, taskId)
                .map(memory -> memory.getCompletedSteps().stream()
                        .map(TaskMemory.TaskStep::getDescription)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
        
        PhaseCompletionStatus phaseStatus = phaseManager.checkPhaseCompletion(sessionId, completedTasks);
        if (phaseStatus.isComplete() && phaseStatus.getSuggestedNextPhase() != null) {
            // Transition to next phase
            phaseManager.transitionPhase(sessionId, phaseStatus.getSuggestedNextPhase());
            context.setCurrentPhase(phaseStatus.getSuggestedNextPhase());
            
            // Create checkpoint at phase transition
            taskMemoryService.createCheckpoint(sessionId, taskId, 
                    "Completed phase: " + context.getCurrentPhase());
        }
        
        // Broadcast step completion via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> completionData = new HashMap<>();
            completionData.put("sessionId", sessionId);
            completionData.put("executionId", executionId);
            completionData.put("step", nextStep);
            completionData.put("stepNumber", stepNumber);
            completionData.put("success", true);
            completionData.put("timestamp", Instant.now().toString());
            completionData.put("status", "completed");
            webSocketHandler.broadcastToolResult(completionData);
        }
        
        return StepExecutionResult.builder()
                .executionId(executionId)
                .success(true)
                .status(StepStatus.COMPLETED)
                .message("Step completed: " + nextStep)
                .result(result)
                .build();
    }
    
    /**
     * Handle step execution error
     */
    public StepExecutionResult handleStepError(String sessionId, String taskId, String errorMessage) {
        String executionId = sessionId + ":" + taskId;
        ExecutionContext context = activeExecutions.get(executionId);
        
        if (context == null) {
            throw new IllegalStateException("Execution not initialized for session " + sessionId + ", task " + taskId);
        }
        
        // Update execution context
        context.setStatus(ExecutionStatus.ERROR);
        context.setLastUpdated(Instant.now());
        
        log.error("Error executing step for session {}, task {}: {}", 
                sessionId, taskId, errorMessage);
        
        // Use error recovery service
        RecoveryResult recovery = errorRecoveryService.handleError(
                sessionId, 
                taskId, 
                errorMessage, 
                "Executing step: " + context.getCurrentStep(), 
                ErrorSeverity.MEDIUM
        );
        
        // Handle recovery based on next action
        StepExecutionResult result = StepExecutionResult.builder()
                .executionId(executionId)
                .success(recovery.isSuccess())
                .errorMessage(errorMessage)
                .recoveryAction(recovery.getNextAction().toString())
                .build();
        
        switch (recovery.getNextAction()) {
            case RETRY -> {
                result.setStatus(StepStatus.RETRY);
                result.setMessage("Retrying step: " + context.getCurrentStep());
            }
            case RETRY_AFTER_DELAY -> {
                result.setStatus(StepStatus.RETRY_AFTER_DELAY);
                result.setMessage("Retrying step after delay: " + context.getCurrentStep());
                result.setDelayMs(recovery.getDelayMs());
            }
            case RETRY_WITH_MODIFICATION -> {
                result.setStatus(StepStatus.RETRY_WITH_MODIFICATION);
                result.setMessage("Retrying step with modification: " + context.getCurrentStep());
                result.setModification(recovery.getSolution());
            }
            case ROLLBACK -> {
                result.setStatus(StepStatus.ROLLBACK);
                result.setMessage("Rolling back to checkpoint: " + recovery.getCheckpointId());
                result.setCheckpointId(recovery.getCheckpointId());
            }
            case CONTINUE -> {
                result.setStatus(StepStatus.SKIPPED);
                result.setMessage("Skipping step due to non-critical error: " + context.getCurrentStep());
                
                // Mark step as completed in task memory
                Map<String, Object> stepResult = new HashMap<>();
                stepResult.put("skipped", true);
                stepResult.put("reason", errorMessage);
                taskMemoryService.completeNextPendingStep(sessionId, taskId, stepResult);
            }
            case ABORT -> {
                result.setStatus(StepStatus.FAILED);
                result.setMessage("Execution aborted due to critical error: " + errorMessage);
                context.setStatus(ExecutionStatus.FAILED);
                context.setEndTime(Instant.now());
            }
        }
        
        // Broadcast step error via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("sessionId", sessionId);
            errorData.put("executionId", executionId);
            errorData.put("step", context.getCurrentStep());
            errorData.put("error", errorMessage);
            errorData.put("recovery", recovery.getNextAction().toString());
            errorData.put("timestamp", Instant.now().toString());
            errorData.put("severity", "error");
            webSocketHandler.broadcastErrorEvent(errorData);
        }
        
        return result;
    }
    
    /**
     * Get execution status
     */
    public ExecutionStatus getExecutionStatus(String sessionId, String taskId) {
        String executionId = sessionId + ":" + taskId;
        ExecutionContext context = activeExecutions.get(executionId);
        
        return context != null ? context.getStatus() : null;
    }
    
    /**
     * Get execution progress
     */
    public ExecutionProgress getExecutionProgress(String sessionId, String taskId) {
        String executionId = sessionId + ":" + taskId;
        ExecutionContext context = activeExecutions.get(executionId);
        
        if (context == null) {
            return null;
        }
        
        int progress = taskMemoryService.calculateProgress(sessionId, taskId);
        
        return ExecutionProgress.builder()
                .executionId(executionId)
                .objective(context.getObjective())
                .status(context.getStatus())
                .currentPhase(context.getCurrentPhase())
                .currentStep(context.getCurrentStep())
                .progress(progress)
                .startTime(context.getStartTime())
                .lastUpdated(context.getLastUpdated())
                .endTime(context.getEndTime())
                .build();
    }
    
    /**
     * Validate execution plan
     */
    private void validatePlan(ExecutionPlan plan) {
        // Check for duplicate step IDs
        Set<String> stepIds = new HashSet<>();
        for (ExecutionStep step : plan.getSteps()) {
            if (step.getId() != null && !stepIds.add(step.getId())) {
                throw new IllegalArgumentException("Duplicate step ID: " + step.getId());
            }
        }
        
        // Check for circular dependencies
        for (ExecutionStep step : plan.getSteps()) {
            if (step.getDependencies() != null) {
                for (String dependency : step.getDependencies()) {
                    if (hasCyclicDependency(plan, step.getId(), dependency, new HashSet<>())) {
                        throw new IllegalArgumentException("Cyclic dependency detected for step: " + step.getId());
                    }
                }
            }
        }
    }
    
    /**
     * Check for cyclic dependencies
     */
    private boolean hasCyclicDependency(ExecutionPlan plan, String stepId, String dependencyId, Set<String> visited) {
        if (stepId.equals(dependencyId)) {
            return true;
        }
        
        if (visited.contains(dependencyId)) {
            return false;
        }
        
        visited.add(dependencyId);
        
        for (ExecutionStep step : plan.getSteps()) {
            if (step.getId() != null && step.getId().equals(dependencyId) && step.getDependencies() != null) {
                for (String dependency : step.getDependencies()) {
                    if (hasCyclicDependency(plan, stepId, dependency, visited)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Execution status
     */
    public enum ExecutionStatus {
        INITIALIZED,
        PLANNING,
        EXECUTING,
        PAUSED,
        ERROR,
        COMPLETED,
        FAILED
    }
    
    /**
     * Step status
     */
    public enum StepStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        RETRY,
        RETRY_AFTER_DELAY,
        RETRY_WITH_MODIFICATION,
        ROLLBACK,
        SKIPPED
    }
    
    /**
     * Execution context
     */
    @Data
    @Builder
    private static class ExecutionContext {
        private String sessionId;
        private String taskId;
        private String objective;
        private ExecutionStatus status;
        private DevelopmentPhase currentPhase;
        private String currentStep;
        private Instant startTime;
        private Instant lastUpdated;
        private Instant endTime;
    }
    
    /**
     * Execution plan
     */
    @Data
    @Builder
    public static class ExecutionPlan {
        private String executionId;
        private String objective;
        private List<ExecutionStep> steps;
    }
    
    /**
     * Execution step
     */
    @Data
    @Builder
    public static class ExecutionStep {
        private String id;
        private String description;
        private List<String> dependencies;
        private Map<String, Object> parameters;
        private String toolName;
    }
    
    /**
     * Step execution result
     */
    @Data
    @Builder
    public static class StepExecutionResult {
        private String executionId;
        private boolean success;
        private StepStatus status;
        private String message;
        private String errorMessage;
        private String recoveryAction;
        private long delayMs;
        private String modification;
        private String checkpointId;
        private Map<String, Object> result;
    }
    
    /**
     * Execution progress
     */
    @Data
    @Builder
    public static class ExecutionProgress {
        private String executionId;
        private String objective;
        private ExecutionStatus status;
        private DevelopmentPhase currentPhase;
        private String currentStep;
        private int progress;
        private Instant startTime;
        private Instant lastUpdated;
        private Instant endTime;
    }
}
