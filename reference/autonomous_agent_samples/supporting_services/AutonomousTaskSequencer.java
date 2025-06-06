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
 * Service for sequencing and managing autonomous tasks
 */
@Slf4j
@Service
public class AutonomousTaskSequencer {
    
    private final Map<String, TaskSequence> activeSequences = new ConcurrentHashMap<>();
    private final Map<String, TaskDependencyGraph> dependencyGraphs = new ConcurrentHashMap<>();
    
    /**
     * Create a new task sequence
     */
    public TaskSequence createSequence(String sessionId, String objective) {
        String sequenceId = UUID.randomUUID().toString();
        
        TaskSequence sequence = TaskSequence.builder()
                .id(sequenceId)
                .sessionId(sessionId)
                .objective(objective)
                .status(SequenceStatus.CREATED)
                .tasks(new ArrayList<>())
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
        
        activeSequences.put(sequenceId, sequence);
        log.info("Created new task sequence: {} for session: {}", sequenceId, sessionId);
        
        return sequence;
    }
    
    /**
     * Add task to sequence
     */
    public SequencedTask addTask(String sequenceId, TaskDefinition taskDefinition) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        String taskId = UUID.randomUUID().toString();
        
        SequencedTask task = SequencedTask.builder()
                .id(taskId)
                .sequenceId(sequenceId)
                .definition(taskDefinition)
                .status(TaskStatus.PENDING)
                .position(sequence.getTasks().size())
                .createdAt(Instant.now())
                .build();
        
        sequence.getTasks().add(task);
        sequence.setLastUpdated(Instant.now());
        
        // Update dependency graph
        updateDependencyGraph(sequenceId, task);
        
        log.debug("Added task: {} to sequence: {}", taskId, sequenceId);
        return task;
    }
    
    /**
     * Add multiple tasks to sequence
     */
    public List<SequencedTask> addTasks(String sequenceId, List<TaskDefinition> taskDefinitions) {
        return taskDefinitions.stream()
                .map(def -> addTask(sequenceId, def))
                .collect(Collectors.toList());
    }
    
    /**
     * Get next task to execute
     */
    public Optional<SequencedTask> getNextTask(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            return Optional.empty();
        }
        
        // Check if sequence is active
        if (sequence.getStatus() != SequenceStatus.ACTIVE) {
            return Optional.empty();
        }
        
        // Get dependency graph
        TaskDependencyGraph graph = dependencyGraphs.get(sequenceId);
        if (graph == null) {
            // Fallback to position-based ordering if no graph
            return sequence.getTasks().stream()
                    .filter(t -> t.getStatus() == TaskStatus.PENDING)
                    .min(Comparator.comparing(SequencedTask::getPosition));
        }
        
        // Find tasks with no unresolved dependencies
        return sequence.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .filter(t -> {
                    Set<String> dependencies = graph.getDependencies().getOrDefault(t.getId(), Collections.emptySet());
                    return dependencies.stream()
                            .map(depId -> findTaskById(sequence, depId))
                            .allMatch(depTask -> depTask.getStatus() == TaskStatus.COMPLETED);
                })
                .min(Comparator.comparing(SequencedTask::getPosition));
    }
    
    /**
     * Update task status
     */
    public SequencedTask updateTaskStatus(String sequenceId, String taskId, TaskStatus newStatus) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        SequencedTask task = findTaskById(sequence, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        
        task.setStatus(newStatus);
        task.setLastUpdated(Instant.now());
        
        if (newStatus == TaskStatus.COMPLETED) {
            task.setCompletedAt(Instant.now());
        }
        
        sequence.setLastUpdated(Instant.now());
        
        // Check if sequence is complete
        boolean allCompleted = sequence.getTasks().stream()
                .allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);
        
        if (allCompleted && sequence.getStatus() == SequenceStatus.ACTIVE) {
            sequence.setStatus(SequenceStatus.COMPLETED);
            sequence.setCompletedAt(Instant.now());
            log.info("Sequence completed: {}", sequenceId);
        }
        
        log.debug("Updated task: {} status to: {}", taskId, newStatus);
        return task;
    }
    
    /**
     * Start sequence execution
     */
    public TaskSequence startSequence(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        sequence.setStatus(SequenceStatus.ACTIVE);
        sequence.setStartedAt(Instant.now());
        sequence.setLastUpdated(Instant.now());
        
        log.info("Started sequence execution: {}", sequenceId);
        return sequence;
    }
    
    /**
     * Pause sequence execution
     */
    public TaskSequence pauseSequence(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        sequence.setStatus(SequenceStatus.PAUSED);
        sequence.setLastUpdated(Instant.now());
        
        log.info("Paused sequence execution: {}", sequenceId);
        return sequence;
    }
    
    /**
     * Resume sequence execution
     */
    public TaskSequence resumeSequence(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        sequence.setStatus(SequenceStatus.ACTIVE);
        sequence.setLastUpdated(Instant.now());
        
        log.info("Resumed sequence execution: {}", sequenceId);
        return sequence;
    }
    
    /**
     * Cancel sequence execution
     */
    public TaskSequence cancelSequence(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        sequence.setStatus(SequenceStatus.CANCELLED);
        sequence.setLastUpdated(Instant.now());
        
        log.info("Cancelled sequence execution: {}", sequenceId);
        return sequence;
    }
    
    /**
     * Get sequence by ID
     */
    public Optional<TaskSequence> getSequence(String sequenceId) {
        return Optional.ofNullable(activeSequences.get(sequenceId));
    }
    
    /**
     * Get all sequences for a session
     */
    public List<TaskSequence> getSessionSequences(String sessionId) {
        return activeSequences.values().stream()
                .filter(seq -> seq.getSessionId().equals(sessionId))
                .collect(Collectors.toList());
    }
    
    /**
     * Get sequence progress
     */
    public SequenceProgress getSequenceProgress(String sequenceId) {
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence not found: " + sequenceId);
        }
        
        int totalTasks = sequence.getTasks().size();
        int completedTasks = (int) sequence.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count();
        int inProgressTasks = (int) sequence.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .count();
        int failedTasks = (int) sequence.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .count();
        
        int progressPercentage = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
        
        return SequenceProgress.builder()
                .sequenceId(sequenceId)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .failedTasks(failedTasks)
                .progressPercentage(progressPercentage)
                .status(sequence.getStatus())
                .build();
    }
    
    /**
     * Add dependency between tasks
     */
    public void addTaskDependency(String sequenceId, String taskId, String dependsOnTaskId) {
        TaskDependencyGraph graph = dependencyGraphs.computeIfAbsent(sequenceId, k -> new TaskDependencyGraph());
        
        // Add dependency
        graph.getDependencies()
                .computeIfAbsent(taskId, k -> new HashSet<>())
                .add(dependsOnTaskId);
        
        // Add reverse dependency
        graph.getDependents()
                .computeIfAbsent(dependsOnTaskId, k -> new HashSet<>())
                .add(taskId);
        
        log.debug("Added dependency: {} depends on {}", taskId, dependsOnTaskId);
    }
    
    /**
     * Get task dependencies
     */
    public Set<String> getTaskDependencies(String sequenceId, String taskId) {
        TaskDependencyGraph graph = dependencyGraphs.get(sequenceId);
        if (graph == null) {
            return Collections.emptySet();
        }
        
        return graph.getDependencies().getOrDefault(taskId, Collections.emptySet());
    }
    
    /**
     * Get task dependents
     */
    public Set<String> getTaskDependents(String sequenceId, String taskId) {
        TaskDependencyGraph graph = dependencyGraphs.get(sequenceId);
        if (graph == null) {
            return Collections.emptySet();
        }
        
        return graph.getDependents().getOrDefault(taskId, Collections.emptySet());
    }
    
    /**
     * Update dependency graph
     */
    private void updateDependencyGraph(String sequenceId, SequencedTask newTask) {
        TaskDependencyGraph graph = dependencyGraphs.computeIfAbsent(sequenceId, k -> new TaskDependencyGraph());
        
        // Initialize empty sets for the new task
        graph.getDependencies().putIfAbsent(newTask.getId(), new HashSet<>());
        graph.getDependents().putIfAbsent(newTask.getId(), new HashSet<>());
        
        // Add automatic dependencies based on task type
        TaskSequence sequence = activeSequences.get(sequenceId);
        if (sequence != null) {
            for (SequencedTask existingTask : sequence.getTasks()) {
                if (existingTask.getId().equals(newTask.getId())) {
                    continue;
                }
                
                // Check for automatic dependencies
                if (shouldDependOn(newTask, existingTask)) {
                    addTaskDependency(sequenceId, newTask.getId(), existingTask.getId());
                }
            }
        }
    }
    
    /**
     * Determine if a task should depend on another
     */
    private boolean shouldDependOn(SequencedTask task, SequencedTask potentialDependency) {
        // Example: Setup tasks should be completed before implementation tasks
        if (task.getDefinition().getType() == TaskType.IMPLEMENTATION && 
            potentialDependency.getDefinition().getType() == TaskType.SETUP) {
            return true;
        }
        
        // Example: Testing tasks should be completed after implementation tasks
        if (task.getDefinition().getType() == TaskType.TESTING && 
            potentialDependency.getDefinition().getType() == TaskType.IMPLEMENTATION) {
            return true;
        }
        
        // Example: Deployment tasks should be completed after testing tasks
        if (task.getDefinition().getType() == TaskType.DEPLOYMENT && 
            potentialDependency.getDefinition().getType() == TaskType.TESTING) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Find task by ID
     */
    private SequencedTask findTaskById(TaskSequence sequence, String taskId) {
        return sequence.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }
    
    @Data
    private static class TaskDependencyGraph {
        // Map of task ID to its dependencies
        private final Map<String, Set<String>> dependencies = new HashMap<>();
        
        // Map of task ID to tasks that depend on it
        private final Map<String, Set<String>> dependents = new HashMap<>();
    }
    
    public enum TaskType {
        SETUP,
        IMPLEMENTATION,
        TESTING,
        DEPLOYMENT,
        ANALYSIS,
        DESIGN,
        DOCUMENTATION,
        MAINTENANCE
    }
    
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        BLOCKED,
        SKIPPED
    }
    
    public enum SequenceStatus {
        CREATED,
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    
    @Data
    @Builder
    public static class TaskDefinition {
        private String name;
        private String description;
        private TaskType type;
        private Map<String, Object> parameters;
        private int estimatedDuration; // in seconds
        private int priority;
    }
    
    @Data
    @Builder
    public static class SequencedTask {
        private String id;
        private String sequenceId;
        private TaskDefinition definition;
        private TaskStatus status;
        private int position;
        private Map<String, Object> result;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private Instant lastUpdated;
    }
    
    @Data
    @Builder
    public static class TaskSequence {
        private String id;
        private String sessionId;
        private String objective;
        private SequenceStatus status;
        private List<SequencedTask> tasks;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private Instant lastUpdated;
    }
    
    @Data
    @Builder
    public static class SequenceProgress {
        private String sequenceId;
        private int totalTasks;
        private int completedTasks;
        private int inProgressTasks;
        private int failedTasks;
        private int progressPercentage;
        private SequenceStatus status;
    }
}
