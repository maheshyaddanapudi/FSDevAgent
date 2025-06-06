package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.model.TaskMemory;
import com.ai.developer.model.TaskMemory.TaskCheckpoint;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service for handling errors and providing recovery mechanisms during autonomous execution.
 * This service enables the agent to detect errors, attempt recovery strategies, and learn from failures.
 */
@Slf4j
@Service
public class ErrorRecoveryService {
    
    @Autowired
    private TaskMemoryService taskMemoryService;
    
    @Autowired
    private EnhancedToolOutputWebSocketHandler webSocketHandler;
    
    private final Map<String, ErrorPattern> knownErrorPatterns = new ConcurrentHashMap<>();
    private final Map<String, List<ErrorOccurrence>> sessionErrors = new ConcurrentHashMap<>();
    
    public ErrorRecoveryService() {
        initializeCommonErrorPatterns();
        log.info("ErrorRecoveryService initialized with common error patterns");
    }
    
    /**
     * Handle an error during autonomous execution
     */
    public RecoveryResult handleError(String sessionId, String taskId, String errorMessage, String context, ErrorSeverity severity) {
        // Record error occurrence
        ErrorOccurrence occurrence = ErrorOccurrence.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .errorMessage(errorMessage)
                .context(context)
                .severity(severity)
                .timestamp(Instant.now())
                .build();
        
        sessionErrors.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(occurrence);
        
        log.warn("Error in session {}, task {}: {} ({})", sessionId, taskId, errorMessage, severity);
        
        // Broadcast error via WebSocket
        if (webSocketHandler != null) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("message", errorMessage);
            errorData.put("context", context);
            errorData.put("severity", severity.toString());
            errorData.put("timestamp", Instant.now().toString());
            webSocketHandler.broadcastError(sessionId, errorMessage, context);
        }
        
        // Determine recovery strategy
        RecoveryStrategy strategy = determineRecoveryStrategy(errorMessage, severity);
        
        // Apply recovery strategy
        RecoveryResult result = applyRecoveryStrategy(sessionId, taskId, occurrence, strategy);
        
        // Learn from this error if recovery was successful
        if (result.isSuccess() && !isKnownError(errorMessage)) {
            learnErrorPattern(errorMessage, strategy, result.getSolution());
        }
        
        return result;
    }
    
    /**
     * Determine the appropriate recovery strategy for an error
     */
    private RecoveryStrategy determineRecoveryStrategy(String errorMessage, ErrorSeverity severity) {
        // Check for known error patterns first
        for (Map.Entry<String, ErrorPattern> entry : knownErrorPatterns.entrySet()) {
            if (Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE).matcher(errorMessage).find()) {
                return entry.getValue().getStrategy();
            }
        }
        
        // Default strategies based on severity
        return switch (severity) {
            case CRITICAL -> RecoveryStrategy.ROLLBACK_TO_CHECKPOINT;
            case HIGH -> RecoveryStrategy.RETRY_WITH_ALTERNATIVE;
            case MEDIUM -> RecoveryStrategy.RETRY_WITH_DELAY;
            case LOW -> RecoveryStrategy.IGNORE_AND_CONTINUE;
        };
    }
    
    /**
     * Apply a recovery strategy
     */
    private RecoveryResult applyRecoveryStrategy(String sessionId, String taskId, ErrorOccurrence error, RecoveryStrategy strategy) {
        RecoveryResult result = RecoveryResult.builder()
                .success(false)
                .errorOccurrence(error)
                .appliedStrategy(strategy)
                .build();
        
        switch (strategy) {
            case RETRY_SIMPLE -> {
                result.setSuccess(true);
                result.setSolution("Retry the operation without changes");
                result.setNextAction(NextAction.RETRY);
            }
            case RETRY_WITH_DELAY -> {
                result.setSuccess(true);
                result.setSolution("Retry the operation after a short delay");
                result.setNextAction(NextAction.RETRY_AFTER_DELAY);
                result.setDelayMs(2000); // 2 second delay
            }
            case RETRY_WITH_ALTERNATIVE -> {
                String alternativeSolution = findAlternativeSolution(error.getErrorMessage());
                result.setSuccess(alternativeSolution != null);
                result.setSolution(alternativeSolution);
                result.setNextAction(NextAction.RETRY_WITH_MODIFICATION);
            }
            case ROLLBACK_TO_CHECKPOINT -> {
                Optional<TaskMemory> memoryOpt = taskMemoryService.getMemory(sessionId, taskId);
                if (memoryOpt.isPresent() && !memoryOpt.get().getCheckpoints().isEmpty()) {
                    TaskCheckpoint checkpoint = memoryOpt.get().getCheckpoints().get(memoryOpt.get().getCheckpoints().size() - 1);
                    result.setSuccess(true);
                    result.setSolution("Rollback to checkpoint: " + checkpoint.getDescription());
                    result.setNextAction(NextAction.ROLLBACK);
                    result.setCheckpointId(checkpoint.getId());
                } else {
                    result.setSuccess(false);
                    result.setSolution("No checkpoint available for rollback");
                    result.setNextAction(NextAction.ABORT);
                }
            }
            case IGNORE_AND_CONTINUE -> {
                result.setSuccess(true);
                result.setSolution("Ignore error and continue execution");
                result.setNextAction(NextAction.CONTINUE);
            }
            case ABORT_EXECUTION -> {
                result.setSuccess(false);
                result.setSolution("Abort execution due to unrecoverable error");
                result.setNextAction(NextAction.ABORT);
            }
        }
        
        // Log recovery attempt
        log.info("Recovery attempt for session {}, task {}: Strategy={}, Success={}, NextAction={}",
                sessionId, taskId, strategy, result.isSuccess(), result.getNextAction());
        
        return result;
    }
    
    /**
     * Find an alternative solution for a known error
     */
    private String findAlternativeSolution(String errorMessage) {
        for (Map.Entry<String, ErrorPattern> entry : knownErrorPatterns.entrySet()) {
            if (Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE).matcher(errorMessage).find()) {
                return entry.getValue().getAlternativeSolution();
            }
        }
        return null;
    }
    
    /**
     * Check if an error matches a known pattern
     */
    private boolean isKnownError(String errorMessage) {
        for (String pattern : knownErrorPatterns.keySet()) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(errorMessage).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Learn a new error pattern
     */
    public void learnErrorPattern(String errorPattern, RecoveryStrategy strategy, String alternativeSolution) {
        ErrorPattern pattern = ErrorPattern.builder()
                .pattern(errorPattern)
                .strategy(strategy)
                .alternativeSolution(alternativeSolution)
                .occurrences(1)
                .firstSeen(Instant.now())
                .lastSeen(Instant.now())
                .build();
        
        knownErrorPatterns.put(errorPattern, pattern);
        log.info("Learned new error pattern: {}", errorPattern);
    }
    
    /**
     * Get error statistics for a session
     */
    public ErrorStatistics getSessionErrorStatistics(String sessionId) {
        List<ErrorOccurrence> errors = sessionErrors.getOrDefault(sessionId, Collections.emptyList());
        
        Map<ErrorSeverity, Integer> severityCounts = new EnumMap<>(ErrorSeverity.class);
        for (ErrorSeverity severity : ErrorSeverity.values()) {
            severityCounts.put(severity, 0);
        }
        
        for (ErrorOccurrence error : errors) {
            severityCounts.put(error.getSeverity(), severityCounts.get(error.getSeverity()) + 1);
        }
        
        return ErrorStatistics.builder()
                .sessionId(sessionId)
                .totalErrors(errors.size())
                .severityCounts(severityCounts)
                .errorOccurrences(new ArrayList<>(errors))
                .build();
    }
    
    /**
     * Initialize common error patterns
     */
    private void initializeCommonErrorPatterns() {
        // Compilation errors
        learnErrorPattern(
                "compilation failed|compiler error|syntax error",
                RecoveryStrategy.RETRY_WITH_ALTERNATIVE,
                "Fix syntax errors in the code"
        );
        
        // Dependency issues
        learnErrorPattern(
                "could not resolve|dependency not found|no such file",
                RecoveryStrategy.RETRY_WITH_ALTERNATIVE,
                "Add missing dependency or check import statements"
        );
        
        // Permission errors
        learnErrorPattern(
                "permission denied|access denied|not authorized",
                RecoveryStrategy.ABORT_EXECUTION,
                "Request appropriate permissions or use alternative approach"
        );
        
        // Network errors
        learnErrorPattern(
                "connection refused|network error|timeout|unreachable",
                RecoveryStrategy.RETRY_WITH_DELAY,
                "Retry after ensuring network connectivity"
        );
        
        // File system errors
        learnErrorPattern(
                "file not found|directory not found|no such file or directory",
                RecoveryStrategy.RETRY_WITH_ALTERNATIVE,
                "Create missing file or directory first"
        );
    }
    
    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        LOW,        // Minor issues that don't affect execution
        MEDIUM,     // Issues that may affect execution but can be worked around
        HIGH,       // Serious issues that significantly impact execution
        CRITICAL    // Fatal issues that prevent execution
    }
    
    /**
     * Recovery strategies
     */
    public enum RecoveryStrategy {
        RETRY_SIMPLE,           // Simple retry without changes
        RETRY_WITH_DELAY,       // Retry after a delay
        RETRY_WITH_ALTERNATIVE, // Retry with an alternative approach
        ROLLBACK_TO_CHECKPOINT, // Rollback to a previous checkpoint
        IGNORE_AND_CONTINUE,    // Ignore the error and continue
        ABORT_EXECUTION         // Abort execution
    }
    
    /**
     * Next actions after recovery attempt
     */
    public enum NextAction {
        RETRY,                  // Retry the operation
        RETRY_AFTER_DELAY,      // Retry after a delay
        RETRY_WITH_MODIFICATION, // Retry with modifications
        ROLLBACK,               // Rollback to checkpoint
        CONTINUE,               // Continue execution
        ABORT                   // Abort execution
    }
    
    /**
     * Error pattern
     */
    @Data
    @Builder
    public static class ErrorPattern {
        private String pattern;
        private RecoveryStrategy strategy;
        private String alternativeSolution;
        private int occurrences;
        private Instant firstSeen;
        private Instant lastSeen;
    }
    
    /**
     * Error occurrence
     */
    @Data
    @Builder
    public static class ErrorOccurrence {
        private String sessionId;
        private String taskId;
        private String errorMessage;
        private String context;
        private ErrorSeverity severity;
        private Instant timestamp;
    }
    
    /**
     * Recovery result
     */
    @Data
    @Builder
    public static class RecoveryResult {
        private boolean success;
        private ErrorOccurrence errorOccurrence;
        private RecoveryStrategy appliedStrategy;
        private String solution;
        private NextAction nextAction;
        private long delayMs;
        private String checkpointId;
    }
    
    /**
     * Error statistics
     */
    @Data
    @Builder
    public static class ErrorStatistics {
        private String sessionId;
        private int totalErrors;
        private Map<ErrorSeverity, Integer> severityCounts;
        private List<ErrorOccurrence> errorOccurrences;
    }
}
