package com.ai.developer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for persisting and retrieving task memory across agent iterations
 */
@Slf4j
@Service
public class TaskMemoryService {
    
    private static final String MEMORY_DIR = "/tmp/ai-developer-agent/memory";
    private final Map<String, TaskMemory> activeMemories = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public TaskMemoryService() {
        // Ensure memory directory exists
        try {
            Files.createDirectories(Paths.get(MEMORY_DIR));
        } catch (IOException e) {
            log.error("Failed to create memory directory", e);
        }
    }
    
    /**
     * Create or update task memory
     */
    public TaskMemory createOrUpdateMemory(String sessionId, String taskId, TaskMemoryUpdate update) {
        String memoryKey = sessionId + ":" + taskId;
        
        TaskMemory memory = activeMemories.computeIfAbsent(memoryKey, k -> {
            // Try to load from disk first
            TaskMemory loaded = loadFromDisk(sessionId, taskId);
            return loaded != null ? loaded : TaskMemory.builder()
                    .sessionId(sessionId)
                    .taskId(taskId)
                    .createdAt(Instant.now())
                    .completedSteps(new ArrayList<>())
                    .pendingSteps(new ArrayList<>())
                    .learnedPatterns(new HashMap<>())
                    .checkpoints(new ArrayList<>())
                    .metadata(new HashMap<>())
                    .build();
        });
        
        // Update memory
        if (update.getObjective() != null) {
            memory.setObjective(update.getObjective());
        }
        
        if (update.getCompletedStep() != null) {
            memory.getCompletedSteps().add(TaskStep.builder()
                    .description(update.getCompletedStep())
                    .timestamp(Instant.now())
                    .build());
        }
        
        if (update.getPendingSteps() != null) {
            memory.setPendingSteps(update.getPendingSteps());
        }
        
        if (update.getLearnedPattern() != null) {
            memory.getLearnedPatterns().put(
                update.getLearnedPattern().getKey(),
                update.getLearnedPattern().getValue()
            );
        }
        
        if (update.getCheckpoint() != null) {
            memory.getCheckpoints().add(update.getCheckpoint());
        }
        
        if (update.getMetadata() != null) {
            memory.getMetadata().putAll(update.getMetadata());
        }
        
        memory.setLastUpdated(Instant.now());
        
        // Persist to disk
        persistToDisk(memory);
        
        return memory;
    }
    
    /**
     * Get task memory
     */
    public Optional<TaskMemory> getMemory(String sessionId, String taskId) {
        String memoryKey = sessionId + ":" + taskId;
        TaskMemory memory = activeMemories.get(memoryKey);
        
        if (memory == null) {
            memory = loadFromDisk(sessionId, taskId);
            if (memory != null) {
                activeMemories.put(memoryKey, memory);
            }
        }
        
        return Optional.ofNullable(memory);
    }
    
    /**
     * Get all memories for a session
     */
    public List<TaskMemory> getSessionMemories(String sessionId) {
        List<TaskMemory> memories = new ArrayList<>();
        
        // Get from active memories
        activeMemories.forEach((key, memory) -> {
            if (key.startsWith(sessionId + ":")) {
                memories.add(memory);
            }
        });
        
        // Load from disk
        File sessionDir = new File(MEMORY_DIR, sessionId);
        if (sessionDir.exists() && sessionDir.isDirectory()) {
            File[] memoryFiles = sessionDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (memoryFiles != null) {
                for (File file : memoryFiles) {
                    try {
                        TaskMemory memory = objectMapper.readValue(file, TaskMemory.class);
                        String memoryKey = memory.getSessionId() + ":" + memory.getTaskId();
                        if (!activeMemories.containsKey(memoryKey)) {
                            memories.add(memory);
                        }
                    } catch (IOException e) {
                        log.error("Failed to load memory from file: {}", file, e);
                    }
                }
            }
        }
        
        return memories;
    }
    
    /**
     * Create a checkpoint for rollback
     */
    public TaskCheckpoint createCheckpoint(String sessionId, String taskId, String description) {
        TaskCheckpoint checkpoint = TaskCheckpoint.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .description(description)
                .sessionState(captureSessionState(sessionId))
                .build();
        
        TaskMemoryUpdate update = TaskMemoryUpdate.builder()
                .checkpoint(checkpoint)
                .build();
        
        createOrUpdateMemory(sessionId, taskId, update);
        
        return checkpoint;
    }
    
    /**
     * Find similar patterns from past executions
     */
    public List<PatternMatch> findSimilarPatterns(String pattern, int limit) {
        List<PatternMatch> matches = new ArrayList<>();
        
        activeMemories.values().forEach(memory -> {
            memory.getLearnedPatterns().forEach((key, value) -> {
                double similarity = calculateSimilarity(pattern, key);
                if (similarity > 0.7) { // 70% similarity threshold
                    matches.add(PatternMatch.builder()
                            .pattern(key)
                            .solution(value)
                            .similarity(similarity)
                            .sourceTaskId(memory.getTaskId())
                            .build());
                }
            });
        });
        
        // Sort by similarity and limit
        return matches.stream()
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(limit)
                .toList();
    }
    
    /**
     * Calculate progress percentage
     */
    public int calculateProgress(String sessionId, String taskId) {
        return getMemory(sessionId, taskId)
                .map(memory -> {
                    int completed = memory.getCompletedSteps().size();
                    int pending = memory.getPendingSteps().size();
                    int total = completed + pending;
                    
                    if (total == 0) return 0;
                    return (completed * 100) / total;
                })
                .orElse(0);
    }
    
    /**
     * Persist memory to disk
     */
    private void persistToDisk(TaskMemory memory) {
        try {
            File sessionDir = new File(MEMORY_DIR, memory.getSessionId());
            Files.createDirectories(sessionDir.toPath());
            
            File memoryFile = new File(sessionDir, memory.getTaskId() + ".json");
            objectMapper.writeValue(memoryFile, memory);
            
            log.debug("Persisted memory to disk: {}/{}", memory.getSessionId(), memory.getTaskId());
        } catch (IOException e) {
            log.error("Failed to persist memory to disk", e);
        }
    }
    
    /**
     * Load memory from disk
     */
    private TaskMemory loadFromDisk(String sessionId, String taskId) {
        File memoryFile = new File(MEMORY_DIR, sessionId + "/" + taskId + ".json");
        if (memoryFile.exists()) {
            try {
                return objectMapper.readValue(memoryFile, TaskMemory.class);
            } catch (IOException e) {
                log.error("Failed to load memory from disk: {}", memoryFile, e);
            }
        }
        return null;
    }
    
    /**
     * Capture current session state
     */
    private Map<String, Object> captureSessionState(String sessionId) {
        Map<String, Object> state = new HashMap<>();
        state.put("timestamp", Instant.now());
        state.put("activeMemories", activeMemories.size());
        // Add more state as needed
        return state;
    }
    
    /**
     * Calculate similarity between two strings (simple implementation)
     */
    private double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        
        int editDistance = computeLevenshteinDistance(longer, shorter);
        return (longerLength - editDistance) / (double) longerLength;
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
    
    @Data
    @Builder
    public static class TaskMemory {
        private String sessionId;
        private String taskId;
        private String objective;
        private List<TaskStep> completedSteps;
        private List<String> pendingSteps;
        private Map<String, String> learnedPatterns;
        private List<TaskCheckpoint> checkpoints;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant lastUpdated;
    }
    
    @Data
    @Builder
    public static class TaskStep {
        private String description;
        private Instant timestamp;
        private Map<String, Object> result;
    }
    
    @Data
    @Builder
    public static class TaskCheckpoint {
        private String id;
        private Instant timestamp;
        private String description;
        private Map<String, Object> sessionState;
    }
    
    @Data
    @Builder
    public static class TaskMemoryUpdate {
        private String objective;
        private String completedStep;
        private List<String> pendingSteps;
        private Map.Entry<String, String> learnedPattern;
        private TaskCheckpoint checkpoint;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    public static class PatternMatch {
        private String pattern;
        private String solution;
        private double similarity;
        private String sourceTaskId;
    }
}
