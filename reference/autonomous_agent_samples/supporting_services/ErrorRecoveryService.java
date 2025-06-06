package com.ai.developer.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for error recovery and learning from failures
 */
@Slf4j
@Service
public class ErrorRecoveryService {
    
    private final Map<String, ErrorPattern> errorPatterns = new ConcurrentHashMap<>();
    private final Map<String, RecoveryStrategy> recoveryStrategies = new ConcurrentHashMap<>();
    private final Map<String, List<ErrorOccurrence>> sessionErrors = new ConcurrentHashMap<>();
    
    public ErrorRecoveryService() {
        initializeCommonPatterns();
    }
    
    /**
     * Record an error occurrence
     */
    public ErrorAnalysis recordError(String sessionId, String context, Exception error) {
        String errorType = error.getClass().getSimpleName();
        String errorMessage = error.getMessage();
        String stackTrace = getStackTraceAsString(error);
        
        // Create error occurrence
        ErrorOccurrence occurrence = ErrorOccurrence.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .timestamp(Instant.now())
                .errorType(errorType)
                .errorMessage(errorMessage)
                .context(context)
                .stackTrace(stackTrace)
                .build();
        
        // Store in session errors
        sessionErrors.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(occurrence);
        
        // Analyze error pattern
        ErrorPattern pattern = identifyPattern(error, context);
        if (pattern != null) {
            pattern.incrementOccurrences();
            errorPatterns.put(pattern.getPatternId(), pattern);
        }
        
        // Find recovery strategy
        RecoveryStrategy strategy = findRecoveryStrategy(errorType, errorMessage, context);
        
        return ErrorAnalysis.builder()
                .errorOccurrence(occurrence)
                .identifiedPattern(pattern)
                .suggestedStrategy(strategy)
                .similarErrors(findSimilarErrors(errorMessage, 3))
                .build();
    }
    
    /**
     * Apply recovery strategy
     */
    public RecoveryResult applyRecovery(String sessionId, ErrorAnalysis analysis) {
        if (analysis.getSuggestedStrategy() == null) {
            return RecoveryResult.builder()
                    .success(false)
                    .message("No recovery strategy available")
                    .build();
        }
        
        RecoveryStrategy strategy = analysis.getSuggestedStrategy();
        log.info("Applying recovery strategy: {} for session: {}", strategy.getStrategyName(), sessionId);
        
        // Execute recovery actions
        List<RecoveryAction> executedActions = new ArrayList<>();
        boolean allSuccess = true;
        
        for (RecoveryAction action : strategy.getActions()) {
            boolean actionSuccess = executeRecoveryAction(sessionId, action);
            action.setExecuted(true);
            action.setSuccess(actionSuccess);
            executedActions.add(action);
            
            if (!actionSuccess && action.isCritical()) {
                allSuccess = false;
                break;
            }
        }
        
        // Update strategy effectiveness
        if (allSuccess) {
            strategy.incrementSuccessCount();
        } else {
            strategy.incrementFailureCount();
        }
        
        return RecoveryResult.builder()
                .success(allSuccess)
                .strategyUsed(strategy.getStrategyName())
                .executedActions(executedActions)
                .message(allSuccess ? "Recovery successful" : "Recovery partially failed")
                .build();
    }
    
    /**
     * Learn from successful recovery
     */
    public void learnFromRecovery(String errorPattern, RecoveryStrategy successfulStrategy) {
        // Store successful recovery strategy
        recoveryStrategies.put(errorPattern, successfulStrategy);
        
        // Update pattern with successful recovery
        ErrorPattern pattern = errorPatterns.get(errorPattern);
        if (pattern != null) {
            pattern.getSuccessfulRecoveries().add(successfulStrategy.getStrategyName());
        }
        
        log.info("Learned new recovery strategy: {} for pattern: {}", 
                successfulStrategy.getStrategyName(), errorPattern);
    }
    
    /**
     * Get error statistics for a session
     */
    public ErrorStatistics getSessionStatistics(String sessionId) {
        List<ErrorOccurrence> errors = sessionErrors.getOrDefault(sessionId, new ArrayList<>());
        
        Map<String, Long> errorTypeCount = errors.stream()
                .collect(Collectors.groupingBy(ErrorOccurrence::getErrorType, Collectors.counting()));
        
        Map<String, Long> contextCount = errors.stream()
                .collect(Collectors.groupingBy(ErrorOccurrence::getContext, Collectors.counting()));
        
        return ErrorStatistics.builder()
                .sessionId(sessionId)
                .totalErrors(errors.size())
                .errorTypeDistribution(errorTypeCount)
                .contextDistribution(contextCount)
                .mostCommonError(findMostCommonError(errorTypeCount))
                .errorTimeline(createErrorTimeline(errors))
                .build();
    }
    
    /**
     * Initialize common error patterns
     */
    private void initializeCommonPatterns() {
        // File not found pattern
        errorPatterns.put("FILE_NOT_FOUND", ErrorPattern.builder()
                .patternId("FILE_NOT_FOUND")
                .errorType("FileNotFoundException")
                .description("File or directory not found")
                .commonCauses(List.of("Missing file", "Wrong path", "Permissions issue"))
                .successfulRecoveries(new ArrayList<>())
                .build());
        
        recoveryStrategies.put("FILE_NOT_FOUND", RecoveryStrategy.builder()
                .strategyName("Create Missing File")
                .description("Create the missing file or directory")
                .actions(List.of(
                        RecoveryAction.builder()
                                .actionType("CREATE_DIRECTORY")
                                .description("Create parent directories")
                                .critical(true)
                                .build(),
                        RecoveryAction.builder()
                                .actionType("CREATE_FILE")
                                .description("Create the missing file")
                                .critical(true)
                                .build()
                ))
                .build());
        
        // Compilation error pattern
        errorPatterns.put("COMPILATION_ERROR", ErrorPattern.builder()
                .patternId("COMPILATION_ERROR")
                .errorType("CompilationException")
                .description("Code compilation failed")
                .commonCauses(List.of("Syntax error", "Missing imports", "Type mismatch"))
                .successfulRecoveries(new ArrayList<>())
                .build());
        
        recoveryStrategies.put("COMPILATION_ERROR", RecoveryStrategy.builder()
                .strategyName("Fix Compilation Errors")
                .description("Analyze and fix compilation errors")
                .actions(List.of(
                        RecoveryAction.builder()
                                .actionType("ANALYZE_SYNTAX")
                                .description("Parse and identify syntax errors")
                                .critical(true)
                                .build(),
                        RecoveryAction.builder()
                                .actionType("ADD_IMPORTS")
                                .description("Add missing import statements")
                                .critical(false)
                                .build(),
                        RecoveryAction.builder()
                                .actionType("FIX_TYPES")
                                .description("Resolve type mismatches")
                                .critical(false)
                                .build()
                ))
                .build());
        
        // Network/API error pattern
        errorPatterns.put("NETWORK_ERROR", ErrorPattern.builder()
                .patternId("NETWORK_ERROR")
                .errorType("NetworkException")
                .description("Network or API call failed")
                .commonCauses(List.of("Connection timeout", "Service unavailable", "Rate limit"))
                .successfulRecoveries(new ArrayList<>())
                .build());
        
        recoveryStrategies.put("NETWORK_ERROR", RecoveryStrategy.builder()
                .strategyName("Retry with Backoff")
                .description("Retry the operation with exponential backoff")
                .actions(List.of(
                        RecoveryAction.builder()
                                .actionType("WAIT")
                                .description("Wait before retry")
                                .metadata(Map.of("duration", "2000"))
                                .critical(false)
                                .build(),
                        RecoveryAction.builder()
                                .actionType("RETRY")
                                .description("Retry the failed operation")
                                .metadata(Map.of("maxAttempts", "3"))
                                .critical(true)
                                .build()
                ))
                .build());
    }
    
    /**
     * Identify error pattern
     */
    private ErrorPattern identifyPattern(Exception error, String context) {
        String errorType = error.getClass().getSimpleName();
        String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        
        // Check known patterns
        if (errorMessage.contains("file not found") || errorMessage.contains("no such file")) {
            return errorPatterns.get("FILE_NOT_FOUND");
        }
        
        if (errorMessage.contains("compilation") || errorMessage.contains("syntax error")) {
            return errorPatterns.get("COMPILATION_ERROR");
        }
        
        if (errorMessage.contains("connection") || errorMessage.contains("timeout") || 
            errorMessage.contains("network")) {
            return errorPatterns.get("NETWORK_ERROR");
        }
        
        // Create new pattern if unknown
        String patternId = errorType + "_" + context.hashCode();
        return errorPatterns.computeIfAbsent(patternId, k -> ErrorPattern.builder()
                .patternId(patternId)
                .errorType(errorType)
                .description("Unknown error pattern")
                .commonCauses(new ArrayList<>())
                .successfulRecoveries(new ArrayList<>())
                .build());
    }
    
    /**
     * Find recovery strategy
     */
    private RecoveryStrategy findRecoveryStrategy(String errorType, String errorMessage, String context) {
        // Try exact match first
        String key = errorType + "_" + context.hashCode();
        RecoveryStrategy strategy = recoveryStrategies.get(key);
        
        if (strategy != null) {
            return strategy;
        }
        
        // Try pattern-based match
        if (errorMessage != null) {
            String lowerMessage = errorMessage.toLowerCase();
            if (lowerMessage.contains("file not found") || lowerMessage.contains("no such file")) {
                return recoveryStrategies.get("FILE_NOT_FOUND");
            }
            if (lowerMessage.contains("compilation") || lowerMessage.contains("syntax")) {
                return recoveryStrategies.get("COMPILATION_ERROR");
            }
            if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
                return recoveryStrategies.get("NETWORK_ERROR");
            }
        }
        
        // Default fallback strategy
        return RecoveryStrategy.builder()
                .strategyName("Generic Retry")
                .description("Generic retry strategy")
                .actions(List.of(
                        RecoveryAction.builder()
                                .actionType("LOG_ERROR")
                                .description("Log error details")
                                .critical(false)
                                .build(),
                        RecoveryAction.builder()
                                .actionType("RETRY")
                                .description("Retry the operation")
                                .critical(true)
                                .build()
                ))
                .build();
    }
    
    /**
     * Find similar errors
     */
    private List<ErrorOccurrence> findSimilarErrors(String errorMessage, int limit) {
        if (errorMessage == null) {
            return new ArrayList<>();
        }
        
        List<ErrorOccurrence> allErrors = sessionErrors.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.getErrorMessage() != null)
                .filter(e -> calculateSimilarity(errorMessage, e.getErrorMessage()) > 0.7)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        
        return allErrors;
    }
    
    /**
     * Execute recovery action
     */
    private boolean executeRecoveryAction(String sessionId, RecoveryAction action) {
        try {
            log.info("Executing recovery action: {} for session: {}", action.getActionType(), sessionId);
            
            switch (action.getActionType()) {
                case "CREATE_DIRECTORY":
                case "CREATE_FILE":
                case "WAIT":
                case "RETRY":
                case "LOG_ERROR":
                    // These would be implemented with actual tool calls
                    return true;
                    
                default:
                    log.warn("Unknown recovery action type: {}", action.getActionType());
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute recovery action", e);
            return false;
        }
    }
    
    /**
     * Calculate string similarity
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        
        String longer = s1.length() > s2.length() ? s1 : s2;
        String shorter = s1.length() > s2.length() ? s2 : s1;
        
        if (longer.length() == 0) {
            return 1.0;
        }
        
        int editDistance = computeLevenshteinDistance(longer, shorter);
        return (longer.length() - editDistance) / (double) longer.length();
    }
    
    /**
     * Compute Levenshtein distance
     */
    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] distance = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            distance[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            distance[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(Math.min(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + cost);
            }
        }
        
        return distance[s1.length()][s2.length()];
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Find most common error
     */
    private String findMostCommonError(Map<String, Long> errorTypeCount) {
        return errorTypeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }
    
    /**
     * Create error timeline
     */
    private List<ErrorTimelineEntry> createErrorTimeline(List<ErrorOccurrence> errors) {
        return errors.stream()
                .map(e -> ErrorTimelineEntry.builder()
                        .timestamp(e.getTimestamp())
                        .errorType(e.getErrorType())
                        .context(e.getContext())
                        .build())
                .sorted(Comparator.comparing(ErrorTimelineEntry::getTimestamp))
                .collect(Collectors.toList());
    }
    
    @Data
    @Builder
    public static class ErrorOccurrence {
        private String id;
        private String sessionId;
        private Instant timestamp;
        private String errorType;
        private String errorMessage;
        private String context;
        private String stackTrace;
    }
    
    @Data
    @Builder
    public static class ErrorPattern {
        private String patternId;
        private String errorType;
        private String description;
        private List<String> commonCauses;
        private List<String> successfulRecoveries;
        private int occurrences;
        
        public void incrementOccurrences() {
            this.occurrences++;
        }
    }
    
    @Data
    @Builder
    public static class RecoveryStrategy {
        private String strategyName;
        private String description;
        private List<RecoveryAction> actions;
        private int successCount;
        private int failureCount;
        
        public void incrementSuccessCount() {
            this.successCount++;
        }
        
        public void incrementFailureCount() {
            this.failureCount++;
        }
        
        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 0.0;
        }
    }
    
    @Data
    @Builder
    public static class RecoveryAction {
        private String actionType;
        private String description;
        private Map<String, Object> metadata;
        private boolean critical;
        private boolean executed;
        private boolean success;
    }
    
    @Data
    @Builder
    public static class ErrorAnalysis {
        private ErrorOccurrence errorOccurrence;
        private ErrorPattern identifiedPattern;
        private RecoveryStrategy suggestedStrategy;
        private List<ErrorOccurrence> similarErrors;
    }
    
    @Data
    @Builder
    public static class RecoveryResult {
        private boolean success;
        private String strategyUsed;
        private List<RecoveryAction> executedActions;
        private String message;
    }
    
    @Data
    @Builder
    public static class ErrorStatistics {
        private String sessionId;
        private int totalErrors;
        private Map<String, Long> errorTypeDistribution;
        private Map<String, Long> contextDistribution;
        private String mostCommonError;
        private List<ErrorTimelineEntry> errorTimeline;
    }
    
    @Data
    @Builder
    public static class ErrorTimelineEntry {
        private Instant timestamp;
        private String errorType;
        private String context;
    }
}
