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
import com.ai.developer.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing autonomous agent execution with multi-step planning
 * and pause/resume capabilities.
 */
@Service
@Slf4j
public class AutonomousAgentService {

    private final ChatService chatService;
    private final AgentPromptService agentPromptService;
    private final AgentControlService agentControlService;
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final ConcurrentHashMap<String, AgentState> agentStates;
    
    @Value("${agent.max.iterations:50}")
    private int maxIterations;
    
    @Value("${agent.iteration.delay.ms:1000}")
    private int iterationDelayMs;

    public AutonomousAgentService(ChatService chatService,
                                 AgentPromptService agentPromptService,
                                 AgentControlService agentControlService,
                                 EnhancedToolOutputWebSocketHandler webSocketHandler,
                                 ConcurrentHashMap<String, AgentState> agentStates) {
        this.chatService = chatService;
        this.agentPromptService = agentPromptService;
        this.agentControlService = agentControlService;
        this.webSocketHandler = webSocketHandler;
        this.agentStates = agentStates;
        log.info("AutonomousAgentService initialized with max iterations: {}", maxIterations);
    }

    /**
     * Start autonomous execution for a session
     */
    public Flux<Map<String, Object>> startAutonomousExecution(String sessionId, String objective) {
        log.info("Starting autonomous execution for session {} with objective: {}", sessionId, objective);
        
        // Initialize agent state if not exists
        AgentState agentState = agentStates.computeIfAbsent(sessionId, k -> {
            AgentState newState = new AgentState();
            newState.setSessionId(sessionId);
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
            
            // Check if we should continue
            if (!agentState.isShouldContinue() || iteration >= maxIterations) {
                log.info("Autonomous execution stopping for session {}: shouldContinue={}, iteration={}/{}",
                        sessionId, agentState.isShouldContinue(), iteration, maxIterations);
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
                    .sessionId(sessionId)
                    .message(prompt)
                    .build();
                
                chatService.processMessage(chatRequest)
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
        })
        .subscribeOn(Schedulers.boundedElastic());
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
        
        // For subsequent iterations, use the ReAct prompt
        return agentPromptService.generateReActPrompt(agentState);
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
        
        switch (eventType) {
            case "TASK_COMPLETE" -> {
                agentState.getCompletedTasks().add(eventData);
                agentState.getPendingTasks().remove(eventData);
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
