package com.ai.developer.service.autonomous;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.Message;
import com.ai.developer.model.AgentState;
import com.ai.developer.model.ChatRequest;
import com.ai.developer.model.ConversationMode;
import com.ai.developer.model.DevelopmentPhase;
import com.ai.developer.model.TaskMemory;
import com.ai.developer.service.AgentControlService;
import com.ai.developer.service.AgentPromptService;
import com.ai.developer.service.EnhancedChatService;
import com.ai.developer.service.TaskExecutorService;
import com.ai.developer.service.ProjectTemplateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing autonomous agent execution with multi-step planning
 * and pause/resume capabilities.
 */
@Service
@Slf4j
public class AutonomousAgentService {

    private final EnhancedChatService enhancedChatService;
    private final AgentPromptService agentPromptService;
    private final AgentControlService agentControlService;
    private final TaskExecutorService taskExecutorService;
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final ConcurrentHashMap<String, AgentState> agentStates;
    private final ProjectTemplateManager projectTemplateManager;
    
    // Track sessions with completed execution to prevent redundant processing
    private final ConcurrentHashMap<String, AtomicBoolean> completedSessions = new ConcurrentHashMap<>();
    
    @Value("${agent.max.iterations:50}")
    private int maxIterations;
    
    @Value("${agent.iteration.delay.ms:1000}")
    private int iterationDelayMs;

    public AutonomousAgentService(EnhancedChatService enhancedChatService,
                                 AgentPromptService agentPromptService,
                                 AgentControlService agentControlService,
                                 TaskExecutorService taskExecutorService,
                                 EnhancedToolOutputWebSocketHandler webSocketHandler,
                                 ConcurrentHashMap<String, AgentState> agentStates,
                                 ProjectTemplateManager projectTemplateManager) {
        this.enhancedChatService = enhancedChatService;
        this.agentPromptService = agentPromptService;
        this.agentControlService = agentControlService;
        this.taskExecutorService = taskExecutorService;
        this.webSocketHandler = webSocketHandler;
        this.agentStates = agentStates;
        this.projectTemplateManager = projectTemplateManager;
        // Note: maxIterations will be injected after construction
    }
    
    @PostConstruct
    public void init() {
        log.info("AutonomousAgentService initialized with max iterations: {}", maxIterations);
    }

    /**
     * Start autonomous execution for a session
     */
    public Flux<Map<String, Object>> startAutonomousExecution(String sessionId, String objective) {
        log.info("Starting autonomous execution for session {} with objective: {}", sessionId, objective);
        
        // Reset completion status for this session
        completedSessions.remove(sessionId);
        
        // Initialize agent state if not exists
        AgentState agentState = agentStates.computeIfAbsent(sessionId, k -> {
            AgentState newState = new AgentState();
            newState.setAiDeveloperAgentSessionId(sessionId);
            newState.setCurrentObjective(objective);
            newState.setCurrentPhase(DevelopmentPhase.ANALYSIS);
            newState.setMode(ConversationMode.AUTONOMOUS);
            newState.setShouldContinue(true);
            newState.setProgress(0);
            newState.setIterationCount(0);
            newState.setCompletedTasks(new ArrayList<>());
            newState.setPendingTasks(new ArrayList<>());
            newState.setMemory(new HashMap<>());
            newState.setCreatedAt(Instant.now());
            newState.setUpdatedAt(Instant.now());
            return newState;
        });
        
        // Reset iteration count and progress
        agentState.setIterationCount(0);
        agentState.setProgress(0);
        agentState.setShouldContinue(true);
        agentState.setMode(ConversationMode.AUTONOMOUS);
        
        // Broadcast initial state
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("sessionId", sessionId);
        initialState.put("action", "start");
        initialState.put("state", agentState.toTaskMemory());
        webSocketHandler.broadcastAgentStateUpdate(initialState);
        
        // Create planning event for initialization
        agentControlService.broadcastPlanningEvent(
            sessionId, 
            0, 
            maxIterations, 
            "Initializing autonomous execution", 
            Map.of("objective", objective)
        );
        
        // Start the autonomous execution loop
        AtomicInteger iterationCounter = new AtomicInteger(0);
        
        return Flux.<Map<String, Object>>generate(sink -> {
            int iteration = iterationCounter.get();
            
            // Check if session is already marked as completed
            if (isSessionCompleted(sessionId)) {
                log.info("Session {} already marked as completed. Terminating loop.", sessionId);
                sink.complete();
                return;
            }
            
            // Check if we should continue
            if (!agentState.isShouldContinue() || iteration >= maxIterations) {
                log.info("Autonomous execution stopping for session {}: shouldContinue={}, iteration={}/{}",
                        sessionId, agentState.isShouldContinue(), iteration, maxIterations);
                
                // Mark session as completed to prevent redundant processing
                markSessionAsCompleted(sessionId);
                
                sink.complete();
                return;
            }
            
            // Check if all tasks are complete
            if (areAllTasksComplete(agentState)) {
                log.info("All tasks completed for session {}. Terminating autonomous execution loop.", sessionId);
                
                // Update progress to 100%
                updateAgentState(agentState.getAiDeveloperAgentSessionId(), "PROGRESS", "100");
                
                // Set shouldContinue to false to stop the loop
                agentState.setShouldContinue(false);
                
                // Mark session as completed to prevent redundant processing
                markSessionAsCompleted(sessionId);
                
                // Notify UI that execution is complete
                agentControlService.broadcastExecutionComplete(
                    agentState.getAiDeveloperAgentSessionId(), 
                    "All tasks completed successfully", 
                    true
                );
                
                // Create completion event
                Map<String, Object> completionEvent = new HashMap<>();
                completionEvent.put("sessionId", sessionId);
                completionEvent.put("status", "completed");
                completionEvent.put("message", "All tasks completed successfully");
                sink.next(completionEvent);
                sink.complete();
                return;
            }
            
            // Check if execution is paused
            if (agentControlService.isExecutionPaused(sessionId) && !agentControlService.shouldStep(sessionId)) {
                log.debug("Autonomous execution paused for session {}", sessionId);
                Map<String, Object> pauseEvent = new HashMap<>();
                pauseEvent.put("sessionId", sessionId);
                pauseEvent.put("status", "paused");
                pauseEvent.put("iteration", iteration);
                sink.next(pauseEvent);
                return;
            }
            
            // Execute one iteration
            log.info("Executing autonomous iteration {} for session {}", iteration, sessionId);
            
            try {
                // Update agent state
                agentState.setIterationCount(iteration);
                agentState.setUpdatedAt(Instant.now());
                
                // Generate the appropriate prompt for this iteration
                String prompt = generateIterationPrompt(agentState);
                
                // Broadcast planning event
                agentControlService.broadcastPlanningEvent(
                    sessionId,
                    iteration + 1,
                    maxIterations,
                    "Executing autonomous iteration " + (iteration + 1),
                    Map.of("prompt", prompt.substring(0, Math.min(100, prompt.length())) + "...")
                );
                
                // Process the prompt through the chat service
                // This will trigger tool use and other actions
                ChatRequest chatRequest = ChatRequest.builder()
                    .aiDeveloperAgentSessionId(sessionId)
                    .message(prompt)
                    .build();
                
                enhancedChatService.processMessage(chatRequest)
                    .collectList()
                    .block();
                
                // Update progress based on iteration
                int progress = Math.min(100, (int)(((double)(iteration + 1) / maxIterations) * 100));
                agentState.setProgress(progress);
                
                // Create event for this iteration
                Map<String, Object> iterationEvent = new HashMap<>();
                iterationEvent.put("sessionId", sessionId);
                iterationEvent.put("status", "running");
                iterationEvent.put("iteration", iteration);
                iterationEvent.put("progress", progress);
                
                // Emit the event
                sink.next(iterationEvent);
                
                // Check if we should transition to the next phase
                if (shouldTransitionPhase(agentState)) {
                    DevelopmentPhase nextPhase = getNextPhase(agentState.getCurrentPhase());
                    agentControlService.broadcastPhaseTransition(
                        sessionId,
                        agentState.getCurrentPhase(),
                        nextPhase,
                        progress
                    );
                    agentState.setCurrentPhase(nextPhase);
                }
                
                // Increment iteration counter
                iterationCounter.incrementAndGet();
                
            } catch (Exception e) {
                log.error("Error during autonomous iteration {} for session {}: {}", 
                        iteration, sessionId, e.getMessage(), e);
                
                // Broadcast error event
                agentControlService.broadcastErrorEvent(
                    sessionId,
                    "Error during autonomous iteration: " + e.getMessage(),
                    "error",
                    Map.of("iteration", iteration)
                );
                
                // Create error event
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("sessionId", sessionId);
                errorEvent.put("status", "error");
                errorEvent.put("iteration", iteration);
                errorEvent.put("error", e.getMessage());
                
                // Emit the error event
                sink.next(errorEvent);
                
                // Increment iteration counter
                iterationCounter.incrementAndGet();
            }
        })
        .delayElements(Duration.ofMillis(iterationDelayMs))
        .doOnComplete(() -> {
            log.info("Autonomous execution completed for session {}", sessionId);
            
            // Update agent state
            agentState.setShouldContinue(false);
            agentState.setMode(ConversationMode.CONVERSATIONAL);
            
            // Broadcast completion event
            Map<String, Object> completionEvent = new HashMap<>();
            completionEvent.put("sessionId", sessionId);
            completionEvent.put("action", "complete");
            completionEvent.put("state", agentState.toTaskMemory());
            webSocketHandler.broadcastAgentStateUpdate(completionEvent);
            
            // Create final planning event
            agentControlService.broadcastPlanningEvent(
                sessionId,
                maxIterations,
                maxIterations,
                "Autonomous execution completed",
                Map.of(
                    "totalIterations", iterationCounter.get(),
                    "finalPhase", agentState.getCurrentPhase().toString(),
                    "completedTasks", agentState.getCompletedTasks().size()
                )
            );
            
            // Clean up resources
            cleanupResources(sessionId);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * Check if a session is marked as completed
     */
    private boolean isSessionCompleted(String sessionId) {
        AtomicBoolean completionFlag = completedSessions.get(sessionId);
        return completionFlag != null && completionFlag.get();
    }
    
    /**
     * Mark a session as completed
     */
    private void markSessionAsCompleted(String sessionId) {
        completedSessions.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);
        log.info("Session {} marked as completed", sessionId);
    }
    
    /**
     * Clean up resources for a completed session
     */
    private void cleanupResources(String sessionId) {
        log.info("Cleaning up resources for completed session {}", sessionId);
        
        // Note: We don't remove the agent state as it might be needed for reference,
        // but we do clean up any background tasks or temporary resources
        
        // Mark as completed to prevent redundant processing
        markSessionAsCompleted(sessionId);
    }
    
    /**
     * Generate the appropriate prompt for the current iteration
     */
    private String generateIterationPrompt(AgentState agentState) {
        // For the first iteration, use a planning prompt
        if (agentState.getIterationCount() == 0) {
            return "Let's create a detailed plan for: " + agentState.getCurrentObjective() + 
                   "\n\nPlease use the planning_tool to create a hierarchical plan with tasks, dependencies, and estimated effort.";
        }
        
        // For subsequent iterations, check if we have a task to execute
        if (agentState.getPendingTasks() != null && !agentState.getPendingTasks().isEmpty()) {
            String nextTask = agentState.getPendingTasks().get(0);
            
            // Execute the task using TaskExecutorService
            try {
                Map<String, Object> context = new HashMap<>();
                context.put("sessionId", agentState.getAiDeveloperAgentSessionId());
                context.put("objective", agentState.getCurrentObjective());
                context.put("phase", agentState.getCurrentPhase().toString());
                context.put("workspacePath", DEFAULT_WORKSPACE_PATH + "/" + agentState.getAiDeveloperAgentSessionId());
                
                // Determine task type and description
                String taskType = extractTaskType(nextTask);
                String taskDescription = nextTask;
                
                log.info("Executing task via TaskExecutorService: {} - {}", taskType, taskDescription);
                
                // Execute task asynchronously
                taskExecutorService.executeTask(taskType, taskDescription, context)
                    .subscribe(
                        output -> {
                            log.info("Task execution output: {}", output);
                            // Update agent state with task progress
                            updateAgentState(agentState.getAiDeveloperAgentSessionId(), "TASK_PROGRESS", output.toString());
                        },
                        error -> {
                            log.error("Error executing task: {}", error.getMessage());
                            // Update agent state with error
                            updateAgentState(agentState.getAiDeveloperAgentSessionId(), "TASK_ERROR", error.getMessage());
                        },
                        () -> {
                            log.info("Task execution completed");
                            // Mark task as complete
                            updateAgentState(agentState.getAiDeveloperAgentSessionId(), "TASK_COMPLETE", nextTask);
                            
                            // Check if all tasks are complete after this update
                            AgentState updatedState = agentStates.get(agentState.getAiDeveloperAgentSessionId());
                            if (updatedState != null && areAllTasksComplete(updatedState)) {
                                log.info("All tasks completed after task execution. Setting progress to 100%.");
                                updateAgentState(updatedState.getSessionId(), "PROGRESS", "100");
                            }
                        }
                    );
            } catch (Exception e) {
                log.error("Failed to execute task: {}", e.getMessage());
            }
        }
        
        // For subsequent iterations, use the ReAct prompt
        return agentPromptService.generateReActPrompt(agentState);
    }
    
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    /**
     * Check if all tasks are complete for the given agent state
     */
    private boolean areAllTasksComplete(AgentState agentState) {
        // If there are no pending tasks and we have at least one completed task, consider all tasks complete
        boolean allComplete = agentState.getPendingTasks().isEmpty() && 
                             !agentState.getCompletedTasks().isEmpty() &&
                             agentState.getIterationCount() > 1; // Ensure we've done at least one iteration
        
        if (allComplete) {
            log.info("All tasks are complete for session {}. Completed tasks: {}, Pending tasks: {}", 
                    agentState.getAiDeveloperAgentSessionId(), 
                    agentState.getCompletedTasks().size(),
                    agentState.getPendingTasks().size());
        }
        
        return allComplete;
    }
    
    /**
     * Extract task type from task description
     */
    private String extractTaskType(String taskDescription) {
        // Simple heuristic to determine task type from description
        taskDescription = taskDescription.toLowerCase();
        
        if (taskDescription.contains("setup") || taskDescription.contains("initialize")) {
            return "setup_project";
        } else if (taskDescription.contains("backend") || taskDescription.contains("api")) {
            return "create_backend";
        } else if (taskDescription.contains("frontend") || taskDescription.contains("ui")) {
            return "create_frontend";
        } else if (taskDescription.contains("database") || taskDescription.contains("db")) {
            return "create_database";
        } else if (taskDescription.contains("component") || taskDescription.contains("react")) {
            return "create_component";
        } else if (taskDescription.contains("test")) {
            return "run_tests";
        } else if (taskDescription.contains("deploy")) {
            return "deploy";
        }
        
        // Default to generic task
        return "generic_task";
    }
    
    /**
     * Determine if we should transition to the next phase
     */
    private boolean shouldTransitionPhase(AgentState agentState) {
        // Simple heuristic: transition phases every 10 iterations
        // In a real implementation, this would be based on task completion
        return agentState.getIterationCount() > 0 && 
               agentState.getIterationCount() % 10 == 0 && 
               agentState.getCurrentPhase() != DevelopmentPhase.DEPLOYMENT;
    }
    
    /**
     * Get the next development phase
     */
    private DevelopmentPhase getNextPhase(DevelopmentPhase currentPhase) {
        return switch (currentPhase) {
            case ANALYSIS -> DevelopmentPhase.DESIGN;
            case DESIGN -> DevelopmentPhase.IMPLEMENTATION;
            case IMPLEMENTATION -> DevelopmentPhase.TESTING;
            case TESTING -> DevelopmentPhase.DEPLOYMENT;
            case DEPLOYMENT -> DevelopmentPhase.DEPLOYMENT; // No next phase
        };
    }
    
    /**
     * Update agent state based on events from the chat service
     */
    public void updateAgentState(String sessionId, String eventType, String eventData) {
        AgentState agentState = agentStates.get(sessionId);
        if (agentState == null) {
            log.warn("Cannot update state for unknown session: {}", sessionId);
            return;
        }
        
        // Skip updates for completed sessions
        if (isSessionCompleted(sessionId)) {
            log.debug("Skipping state update for completed session: {}", sessionId);
            return;
        }
        
        switch (eventType) {
            case "TASK_COMPLETE" -> {
                agentState.getCompletedTasks().add(eventData);
                agentState.getPendingTasks().remove(eventData);
                
                // Check if all tasks are complete after this update
                if (areAllTasksComplete(agentState)) {
                    log.info("All tasks completed after TASK_COMPLETE event. Setting progress to 100%.");
                    agentState.setProgress(100);
                }
            }
            case "TASK_PENDING" -> {
                if (!agentState.getPendingTasks().contains(eventData)) {
                    agentState.getPendingTasks().add(eventData);
                }
            }
            case "PHASE_TRANSITION" -> {
                try {
                    DevelopmentPhase newPhase = DevelopmentPhase.valueOf(eventData);
                    agentControlService.broadcastPhaseTransition(
                        sessionId,
                        agentState.getCurrentPhase(),
                        newPhase,
                        agentState.getProgress()
                    );
                    agentState.setCurrentPhase(newPhase);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid phase transition value: {}", eventData);
                }
            }
            case "PROGRESS" -> {
                try {
                    int progress = Integer.parseInt(eventData);
                    agentState.setProgress(Math.min(100, Math.max(0, progress)));
                } catch (NumberFormatException e) {
                    log.warn("Invalid progress value: {}", eventData);
                }
            }
            case "ACTION" -> {
                agentState.setLastAction(eventData);
            }
            default -> log.debug("Unhandled event type: {}", eventType);
        }
        
        // Update timestamp
        agentState.setUpdatedAt(Instant.now());
    }
    
    /**
     * Get the current agent state for a session
     */
    public AgentState getAgentState(String sessionId) {
        return agentStates.get(sessionId);
    }
}
