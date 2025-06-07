package com.ai.developer.controller;

import com.ai.developer.model.AgentControlRequest;
import com.ai.developer.model.AgentState;
import com.ai.developer.model.TaskMemory;
import com.ai.developer.service.AgentControlService;
import com.ai.developer.service.autonomous.AutonomousAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for agent control operations
 * Provides endpoints for pausing, resuming, and monitoring autonomous agent execution
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentControlController {

    private final AgentControlService agentControlService;
    private final AutonomousAgentService autonomousAgentService;

    public AgentControlController(AgentControlService agentControlService,
                                AutonomousAgentService autonomousAgentService) {
        this.agentControlService = agentControlService;
        this.autonomousAgentService = autonomousAgentService;
        log.info("AgentControlController initialized");
    }

    /**
     * Start autonomous execution for a session
     */
    @PostMapping("/start/{sessionId}")
    public Flux<Map<String, Object>> startAutonomousExecution(
            @PathVariable String sessionId,
            @RequestBody AgentControlRequest request) {
        log.info("Starting autonomous execution for session {} with objective: {}", 
                sessionId, request.getObjective());
        return autonomousAgentService.startAutonomousExecution(sessionId, request.getObjective());
    }

    /**
     * Pause agent execution
     */
    @PostMapping("/pause/{sessionId}")
    public Mono<ResponseEntity<Boolean>> pauseExecution(@PathVariable String sessionId) {
        log.info("Pausing execution for session: {}", sessionId);
        return agentControlService.pauseExecution(sessionId)
                .map(result -> ResponseEntity.ok(result));
    }

    /**
     * Resume agent execution
     */
    @PostMapping("/resume/{sessionId}")
    public Mono<ResponseEntity<Boolean>> resumeExecution(@PathVariable String sessionId) {
        log.info("Resuming execution for session: {}", sessionId);
        return agentControlService.resumeExecution(sessionId)
                .map(result -> ResponseEntity.ok(result));
    }

    /**
     * Step through agent execution
     */
    @PostMapping("/step/{sessionId}")
    public Mono<ResponseEntity<Boolean>> stepExecution(@PathVariable String sessionId) {
        log.info("Stepping execution for session: {}", sessionId);
        return agentControlService.stepExecution(sessionId)
                .map(result -> ResponseEntity.ok(result));
    }

    /**
     * Get agent state
     */
    @GetMapping("/state/{sessionId}")
    public ResponseEntity<TaskMemory> getAgentState(@PathVariable String sessionId) {
        log.info("Getting agent state for session: {}", sessionId);
        TaskMemory state = agentControlService.getAgentState(sessionId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }
}
