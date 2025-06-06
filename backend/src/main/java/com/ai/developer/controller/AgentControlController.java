package com.ai.developer.controller;

import com.ai.developer.model.TaskMemory;
import com.ai.developer.service.AgentControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for agent control operations
 * Provides endpoints for pausing, resuming, and stepping through agent execution
 */
@RestController
@RequestMapping("/api/agent/control")
@Slf4j
public class AgentControlController {

    private final AgentControlService agentControlService;

    public AgentControlController(AgentControlService agentControlService) {
        this.agentControlService = agentControlService;
        log.info("AgentControlController initialized");
    }

    /**
     * Pause agent execution
     */
    @PostMapping("/{sessionId}/pause")
    public Mono<ResponseEntity<Map<String, Object>>> pauseExecution(@PathVariable String sessionId) {
        log.info("REST request to pause execution for session: {}", sessionId);
        
        return agentControlService.pauseExecution(sessionId)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Execution paused",
                            "sessionId", sessionId
                        ));
                    } else {
                        return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "Failed to pause execution",
                            "sessionId", sessionId
                        ));
                    }
                });
    }

    /**
     * Resume agent execution
     */
    @PostMapping("/{sessionId}/resume")
    public Mono<ResponseEntity<Map<String, Object>>> resumeExecution(@PathVariable String sessionId) {
        log.info("REST request to resume execution for session: {}", sessionId);
        
        return agentControlService.resumeExecution(sessionId)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Execution resumed",
                            "sessionId", sessionId
                        ));
                    } else {
                        return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "Failed to resume execution",
                            "sessionId", sessionId
                        ));
                    }
                });
    }

    /**
     * Step through agent execution
     */
    @PostMapping("/{sessionId}/step")
    public Mono<ResponseEntity<Map<String, Object>>> stepExecution(@PathVariable String sessionId) {
        log.info("REST request to step execution for session: {}", sessionId);
        
        return agentControlService.stepExecution(sessionId)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Execution stepped",
                            "sessionId", sessionId
                        ));
                    } else {
                        return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "Failed to step execution",
                            "sessionId", sessionId
                        ));
                    }
                });
    }

    /**
     * Get agent state
     */
    @GetMapping("/{sessionId}/state")
    public ResponseEntity<TaskMemory> getAgentState(@PathVariable String sessionId) {
        log.info("REST request to get agent state for session: {}", sessionId);
        
        TaskMemory state = agentControlService.getAgentState(sessionId);
        if (state != null) {
            return ResponseEntity.ok(state);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
