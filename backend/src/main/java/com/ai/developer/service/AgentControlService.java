package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.model.AgentState;
import com.ai.developer.model.ConversationMode;
import com.ai.developer.model.DevelopmentPhase;
import com.ai.developer.model.TaskMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for controlling agent execution and streaming detailed events
 * This service provides APIs for pausing, resuming, and stepping through
 * autonomous agent execution, as well as enhanced event streaming.
 */
@Service
@Slf4j
public class AgentControlService {

    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final ConcurrentHashMap<String, AgentState> agentStates;
    private final ConcurrentHashMap<String, AtomicBoolean> pauseFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> stepFlags = new ConcurrentHashMap<>();

    public AgentControlService(EnhancedToolOutputWebSocketHandler webSocketHandler, 
                              ConcurrentHashMap<String, AgentState> agentStates) {
        this.webSocketHandler = webSocketHandler;
        this.agentStates = agentStates;
        log.info("AgentControlService initialized with enhanced control capabilities");
    }

    /**
     * Pause agent execution for a session
     */
    public Mono<Boolean> pauseExecution(String sessionId) {
        log.info("Pausing execution for session: {}", sessionId);
        
        AgentState agentState = agentStates.get(sessionId);
        if (agentState == null) {
            log.warn("Session not found for pause: {}", sessionId);
            return Mono.just(false);
        }
        
        // Set pause flag
        pauseFlags.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);
        
        // Update agent state
        agentState.setShouldContinue(false);
        agentState.setMode(ConversationMode.CONVERSATIONAL);
        
        // Broadcast pause event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "agent_control");
        eventData.put("action", "pause");
        eventData.put("timestamp", Instant.now().toString());
        eventData.put("state", agentState.toTaskMemory());
        
        webSocketHandler.broadcastAgentStateUpdate(eventData);
        
        return Mono.just(true);
    }
    
    /**
     * Resume agent execution for a session
     */
    public Mono<Boolean> resumeExecution(String sessionId) {
        log.info("Resuming execution for session: {}", sessionId);
        
        AgentState agentState = agentStates.get(sessionId);
        if (agentState == null) {
            log.warn("Session not found for resume: {}", sessionId);
            return Mono.just(false);
        }
        
        // Clear pause flag
        pauseFlags.computeIfAbsent(sessionId, k -> new AtomicBoolean(true)).set(false);
        
        // Update agent state
        agentState.setShouldContinue(true);
        agentState.setMode(ConversationMode.AUTONOMOUS);
        agentState.setWaitingForUserInput(false);
        
        // Broadcast resume event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "agent_control");
        eventData.put("action", "resume");
        eventData.put("timestamp", Instant.now().toString());
        eventData.put("state", agentState.toTaskMemory());
        
        webSocketHandler.broadcastAgentStateUpdate(eventData);
        
        return Mono.just(true);
    }
    
    /**
     * Step through agent execution for a session
     */
    public Mono<Boolean> stepExecution(String sessionId) {
        log.info("Stepping execution for session: {}", sessionId);
        
        AgentState agentState = agentStates.get(sessionId);
        if (agentState == null) {
            log.warn("Session not found for step: {}", sessionId);
            return Mono.just(false);
        }
        
        // Set step flag
        stepFlags.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);
        
        // Update agent state for a single step
        agentState.setShouldContinue(true);
        agentState.setMode(ConversationMode.AUTONOMOUS);
        agentState.setWaitingForUserInput(false);
        
        // Broadcast step event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "agent_control");
        eventData.put("action", "step");
        eventData.put("timestamp", Instant.now().toString());
        eventData.put("state", agentState.toTaskMemory());
        
        webSocketHandler.broadcastAgentStateUpdate(eventData);
        
        return Mono.just(true);
    }
    
    /**
     * Check if execution is paused for a session
     */
    public boolean isExecutionPaused(String sessionId) {
        AtomicBoolean pauseFlag = pauseFlags.get(sessionId);
        return pauseFlag != null && pauseFlag.get();
    }
    
    /**
     * Check if execution should step for a session
     * This also consumes the step flag if it's set
     */
    public boolean shouldStep(String sessionId) {
        AtomicBoolean stepFlag = stepFlags.get(sessionId);
        if (stepFlag != null && stepFlag.get()) {
            // Consume the step flag
            stepFlag.set(false);
            return true;
        }
        return false;
    }
    
    /**
     * Broadcast planning event
     */
    public void broadcastPlanningEvent(String sessionId, int step, int totalSteps, String description, Map<String, Object> details) {
        log.debug("Broadcasting planning event for session {}: step {}/{}", sessionId, step, totalSteps);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "planning");
        eventData.put("step", step);
        eventData.put("totalSteps", totalSteps);
        eventData.put("description", description);
        eventData.put("timestamp", Instant.now().toString());
        
        if (details != null) {
            eventData.put("details", details);
        }
        
        webSocketHandler.broadcastPlanningUpdate(eventData);
    }
    
    /**
     * Broadcast phase transition event
     */
    public void broadcastPhaseTransition(String sessionId, DevelopmentPhase fromPhase, DevelopmentPhase toPhase, int progress) {
        log.info("Broadcasting phase transition for session {}: {} -> {}", sessionId, fromPhase, toPhase);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "phase_transition");
        eventData.put("fromPhase", fromPhase != null ? fromPhase.toString() : null);
        eventData.put("toPhase", toPhase.toString());
        eventData.put("progress", progress);
        eventData.put("timestamp", Instant.now().toString());
        
        webSocketHandler.broadcastPhaseTransition(eventData);
    }
    
    /**
     * Broadcast tool execution event
     */
    public void broadcastToolExecution(String sessionId, String toolName, Map<String, Object> args, String status) {
        log.debug("Broadcasting tool execution for session {}: {}", sessionId, toolName);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "tool_execution");
        eventData.put("toolName", toolName);
        eventData.put("args", args);
        eventData.put("status", status);
        eventData.put("timestamp", Instant.now().toString());
        
        webSocketHandler.broadcastToolExecution(eventData);
    }
    
    /**
     * Broadcast tool result event
     */
    public void broadcastToolResult(String sessionId, String toolName, Map<String, Object> args, Object result, boolean success) {
        log.debug("Broadcasting tool result for session {}: {}", sessionId, toolName);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "tool_result");
        eventData.put("toolName", toolName);
        eventData.put("args", args);
        eventData.put("result", result);
        eventData.put("success", success);
        eventData.put("timestamp", Instant.now().toString());
        
        webSocketHandler.broadcastToolResult(eventData);
    }
    
    /**
     * Broadcast error event
     */
    public void broadcastErrorEvent(String sessionId, String message, String severity, Map<String, Object> details) {
        log.warn("Broadcasting error event for session {}: {}", sessionId, message);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("sessionId", sessionId);
        eventData.put("type", "error");
        eventData.put("message", message);
        eventData.put("severity", severity);
        eventData.put("timestamp", Instant.now().toString());
        
        if (details != null) {
            eventData.put("details", details);
        }
        
        webSocketHandler.broadcastErrorEvent(eventData);
    }
    
    /**
     * Get current agent state as TaskMemory
     */
    public TaskMemory getAgentState(String sessionId) {
        AgentState agentState = agentStates.get(sessionId);
        if (agentState == null) {
            log.warn("Session not found for state query: {}", sessionId);
            return null;
        }
        
        return agentState.toTaskMemory();
    }
}
