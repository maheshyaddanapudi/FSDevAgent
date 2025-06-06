package com.ai.developer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced WebSocket handler for tool output and agent state events
 * Extends the base ToolOutputWebSocketHandler with additional event types
 */
@Slf4j
@Component
public class EnhancedToolOutputWebSocketHandler extends ToolOutputWebSocketHandler {
    
    /**
     * Broadcast phase transition events to all connected clients
     * Used to notify UI of agent development phase changes
     */
    public void broadcastPhaseTransition(Map<String, Object> phaseData) {
        try {
            // Create a specialized phase transition event
            PhaseTransitionEvent event = new PhaseTransitionEvent();
            event.setType("phase_transition");
            event.setSessionId((String) phaseData.get("sessionId"));
            event.setFromPhase((String) phaseData.get("fromPhase"));
            event.setToPhase((String) phaseData.get("toPhase"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.info("Broadcasting phase transition event: {} -> {}", 
                    event.getFromPhase(), event.getToPhase());
        } catch (Exception e) {
            log.error("Error broadcasting phase transition event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast agent state updates to all connected clients
     * Used to notify UI of agent state changes
     */
    public void broadcastAgentStateUpdate(Map<String, Object> stateData) {
        try {
            // Create a specialized agent state update event
            AgentStateEvent event = new AgentStateEvent();
            event.setType("agent_state_update");
            event.setSessionId((String) stateData.get("sessionId"));
            event.setAction((String) stateData.get("action"));
            event.setStateData(stateData.get("state"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.info("Broadcasting agent state update event: {}", stateData.get("action"));
        } catch (Exception e) {
            log.error("Error broadcasting agent state update event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast planning updates to all connected clients
     * Used to notify UI of planning progress
     */
    public void broadcastPlanningUpdate(Map<String, Object> planningData) {
        try {
            // Create a specialized planning update event
            PlanningEvent event = new PlanningEvent();
            event.setType("planning");
            event.setSessionId((String) planningData.get("sessionId"));
            event.setStep((Integer) planningData.get("step"));
            event.setTotalSteps((Integer) planningData.get("totalSteps"));
            event.setDescription((String) planningData.get("description"));
            event.setDetails(planningData.get("details"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.info("Broadcasting planning update event: step {}/{}", 
                    event.getStep(), event.getTotalSteps());
        } catch (Exception e) {
            log.error("Error broadcasting planning update event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast tool execution events to all connected clients
     * Used to notify UI of tool execution
     */
    public void broadcastToolExecution(Map<String, Object> executionData) {
        try {
            // Create a specialized tool execution event
            ToolExecutionEvent event = new ToolExecutionEvent();
            event.setType("tool_execution");
            event.setSessionId((String) executionData.get("sessionId"));
            event.setToolName((String) executionData.get("toolName"));
            event.setArgs(executionData.get("args"));
            event.setStatus((String) executionData.get("status"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.info("Broadcasting tool execution event: {}", event.getToolName());
        } catch (Exception e) {
            log.error("Error broadcasting tool execution event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast tool result events to all connected clients
     * Used to notify UI of tool execution results
     */
    public void broadcastToolResult(Map<String, Object> resultData) {
        try {
            // Create a specialized tool result event
            ToolResultEvent event = new ToolResultEvent();
            event.setType("tool_result");
            event.setSessionId((String) resultData.get("sessionId"));
            event.setToolName((String) resultData.get("toolName"));
            event.setArgs(resultData.get("args"));
            event.setResult(resultData.get("result"));
            event.setSuccess((Boolean) resultData.get("success"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.info("Broadcasting tool result event: {} (success={})", 
                    event.getToolName(), event.isSuccess());
        } catch (Exception e) {
            log.error("Error broadcasting tool result event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast error events to all connected clients
     * Used to notify UI of errors
     */
    public void broadcastErrorEvent(Map<String, Object> errorData) {
        try {
            // Create a specialized error event
            ErrorEvent event = new ErrorEvent();
            event.setType("error");
            event.setSessionId((String) errorData.get("sessionId"));
            event.setMessage((String) errorData.get("message"));
            event.setSeverity((String) errorData.get("severity"));
            event.setDetails(errorData.get("details"));
            event.setTimestamp(System.currentTimeMillis());
            
            // Broadcast the event to all connected clients
            broadcastToolOutput(event);
            
            log.warn("Broadcasting error event: {} (severity={})", 
                    event.getMessage(), event.getSeverity());
        } catch (Exception e) {
            log.error("Error broadcasting error event: {}", e.getMessage(), e);
        }
    }
    
    // Inner class for phase transition events
    private static class PhaseTransitionEvent {
        private String type;
        private String sessionId;
        private String fromPhase;
        private String toPhase;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getFromPhase() { return fromPhase; }
        public void setFromPhase(String fromPhase) { this.fromPhase = fromPhase; }
        
        public String getToPhase() { return toPhase; }
        public void setToPhase(String toPhase) { this.toPhase = toPhase; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for agent state update events
    private static class AgentStateEvent {
        private String type;
        private String sessionId;
        private String action;
        private Object stateData;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public Object getStateData() { return stateData; }
        public void setStateData(Object stateData) { this.stateData = stateData; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for planning events
    private static class PlanningEvent {
        private String type;
        private String sessionId;
        private int step;
        private int totalSteps;
        private String description;
        private Object details;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public int getStep() { return step; }
        public void setStep(int step) { this.step = step; }
        
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for tool execution events
    private static class ToolExecutionEvent {
        private String type;
        private String sessionId;
        private String toolName;
        private Object args;
        private String status;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Object getArgs() { return args; }
        public void setArgs(Object args) { this.args = args; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for tool result events
    private static class ToolResultEvent {
        private String type;
        private String sessionId;
        private String toolName;
        private Object args;
        private Object result;
        private boolean success;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Object getArgs() { return args; }
        public void setArgs(Object args) { this.args = args; }
        
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    // Inner class for error events
    private static class ErrorEvent {
        private String type;
        private String sessionId;
        private String message;
        private String severity;
        private Object details;
        private long timestamp;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
