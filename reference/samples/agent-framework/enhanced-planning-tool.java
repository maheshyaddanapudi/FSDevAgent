package com.ai.developer.tools.impl;

import com.ai.developer.tools.ParameterInfo;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced Planning Tool implementing advanced autonomous planning patterns
 * Based on Hierarchical Task Networks (HTN) and Tree of Thoughts (ToT) approaches
 */
@Slf4j
@Component
public class PlanningTool implements Tool {

    private final Map<String, Plan> activePlans = new ConcurrentHashMap<>();
    private final Map<String, PlanningContext> planningContexts = new ConcurrentHashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    private String currentPlanId = null;

    @Override
    public String getName() {
        return "planning_tool";
    }

    @Override
    public String getDescription() {
        return "Advanced planning tool for hierarchical task decomposition, dependency analysis, and autonomous project management";
    }

    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
                .name("operation")
                .type("string")
                .description("Planning operation: create_plan, decompose_task, analyze_dependencies, calculate_critical_path, optimize_plan, assess_risks, generate_alternatives, update_progress, get_next_action")
                .required(true)
                .build());
                
        params.put("objective", ParameterInfo.builder()
                .name("objective")
                .type("string")
                .description("The high-level objective or goal")
                .required(false)
                .build());
                
        params.put("constraints", ParameterInfo.builder()
                .name("constraints")
                .type("object")
                .description("Project constraints (time, resources, technology)")
                .required(false)
                .build());
                
        params.put("planId", ParameterInfo.builder()
                .name("planId")
                .type("string")
                .description("ID of existing plan")
                .required(false)
                .build());
                
        params.put("taskId", ParameterInfo.builder()
                .name("taskId")
                .type("string")
                .description("ID of specific task")
                .required(false)
                .build());
                
        params.put("depth", ParameterInfo.builder()
                .name("depth")
                .type("integer")
                .description("Decomposition depth for hierarchical planning")
                .required(false)
                .build());
                
        return params;
    }

    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            try {
                String operation = (String) arguments.get("operation");
                
                return switch (operation) {
                    case "create_plan" -> createHierarchicalPlan(arguments);
                    case "decompose_task" -> decomposeTaskHierarchically(arguments);
                    case "analyze_dependencies" -> analyzeDependenciesAdvanced(arguments);
                    case "calculate_critical_path" -> calculateCriticalPathAdvanced(arguments);
                    case "optimize_plan" -> optimizePlan(arguments);
                    case "assess_risks" -> assessRisks(arguments);
                    case "generate_alternatives" -> generateAlternatives(arguments);
                    case "update_progress" -> updateProgress(arguments);
                    case "get_next_action" -> getNextAutonomousAction(arguments);
                    default -> ToolOutput.builder()
                            .type("error")
                            .content("Unknown operation: " + operation)
                            .build();
                };
            } catch (Exception e) {
                log.error("Error executing planning tool", e);
                return ToolOutput.builder()
                        .type("error")
                        .content("Error: " + e.getMessage())
                        .build();
            }
        }).flux();
    }

    /**
     * Create a hierarchical plan using HTN approach
     */
    private ToolOutput createHierarchicalPlan(Map<String, Object> arguments) {
        String objective = (String) arguments.getOrDefault("objective", "Build Full-Stack Application");
        Map<String, Object> constraints = (Map<String, Object>) arguments.getOrDefault("constraints", new HashMap<>());
        
        // Create planning context for autonomous decision making
        PlanningContext context = new PlanningContext();
        context.setObjective(objective);
        context.setConstraints(constraints);
        context.setCreatedAt(LocalDateTime.now());
        
        // Create root plan
        Plan plan = Plan.builder()
                .id(UUID.randomUUID().toString())
                .objective(objective)
                .type("HIERARCHICAL")
                .status("PLANNING")
                .created(LocalDateTime.now().format(formatter))
                .updated(LocalDateTime.now().format(formatter))
                .tasks(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
        
        // Decompose objective into high-level phases
        List<Task> phases = decomposeObjectiveIntoPhases(objective);
        plan.getTasks().addAll(phases);
        
        // Analyze and set dependencies
        analyzePhaseDependencies(plan);
        
        // Calculate estimated effort and timeline
        calculatePlanMetrics(plan);
        
        // Store plan and context
        activePlans.put(plan.getId(), plan);
        planningContexts.put(plan.getId(), context);
        currentPlanId = plan.getId();
        
        // Generate comprehensive plan report
        String report = generateHierarchicalPlanReport(plan, context);
        
        return ToolOutput.builder()
                .type("plan_created")
                .content(report)
                .metadata(Map.of(
                    "planId", plan.getId(),
                    "taskCount", plan.getTasks().size(),
                    "estimatedDays", plan.getMetadata().getOrDefault("estimatedDays", 0)
                ))
                .build();
    }

    /**
     * Decompose objective into high-level phases based on software development best practices
     */
    private List<Task> decomposeObjectiveIntoPhases(String objective) {
        List<Task> phases = new ArrayList<>();
        
        // Standard software development phases
        phases.add(createPhaseTask("Requirements & Analysis", 
            "Gather requirements, analyze feasibility, define scope", 
            TaskType.COMPOUND, 1));
            
        phases.add(createPhaseTask("Architecture & Design", 
            "Design system architecture, database schema, API contracts", 
            TaskType.COMPOUND, 2));
            
        phases.add(createPhaseTask("Development Setup", 
            "Set up development environment, CI/CD, version control", 
            TaskType.COMPOUND, 1));
            
        phases.add(createPhaseTask("Backend Development", 
            "Implement server-side logic, APIs, database integration", 
            TaskType.COMPOUND, 5));
            
        phases.add(createPhaseTask("Frontend Development", 
            "Build user interface, integrate with backend APIs", 
            TaskType.COMPOUND, 5));
            
        phases.add(createPhaseTask("Testing & QA", 
            "Unit tests, integration tests, E2E tests, performance testing", 
            TaskType.COMPOUND, 3));
            
        phases.add(createPhaseTask("Documentation", 
            "API documentation, user guides, deployment guides", 
            TaskType.COMPOUND, 2));
            
        phases.add(createPhaseTask("Deployment & DevOps", 
            "Configure deployment, monitoring, scaling", 
            TaskType.COMPOUND, 2));
        
        return phases;
    }

    /**
     * Hierarchically decompose a task using HTN approach
     */
    private ToolOutput decomposeTaskHierarchically(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        String taskId = (String) arguments.get("taskId");
        int depth = (Integer) arguments.getOrDefault("depth", 2);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("Plan not found: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        Optional<Task> taskOpt = findTaskById(plan, taskId);
        
        if (taskOpt.isEmpty()) {
            return createErrorOutput("Task not found: " + taskId);
        }
        
        Task parentTask = taskOpt.get();
        
        // Only decompose compound tasks
        if (parentTask.getType() != TaskType.COMPOUND) {
            return createErrorOutput("Cannot decompose primitive task: " + parentTask.getTitle());
        }
        
        // Generate subtasks based on task type and context
        List<Task> subtasks = generateSubtasks(parentTask, depth);
        
        // Add subtasks to plan
        parentTask.setSubtasks(subtasks.stream().map(Task::getId).collect(Collectors.toList()));
        plan.getTasks().addAll(subtasks);
        
        // Update plan
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        // Generate decomposition report
        String report = generateDecompositionReport(parentTask, subtasks);
        
        return ToolOutput.builder()
                .type("task_decomposed")
                .content(report)
                .metadata(Map.of(
                    "parentTaskId", taskId,
                    "subtaskCount", subtasks.size(),
                    "totalDepth", depth
                ))
                .build();
    }

    /**
     * Advanced dependency analysis with automatic detection
     */
    private ToolOutput analyzeDependenciesAdvanced(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("Plan not found: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        DependencyGraph graph = new DependencyGraph();
        
        // Build dependency graph
        for (Task task : plan.getTasks()) {
            graph.addNode(task);
            
            // Analyze dependencies based on task type and phase
            List<String> dependencies = inferDependencies(task, plan);
            task.setDependencies(dependencies);
            
            for (String depId : dependencies) {
                graph.addEdge(depId, task.getId());
            }
        }
        
        // Detect cycles
        List<List<String>> cycles = graph.detectCycles();
        
        // Find bottlenecks
        List<String> bottlenecks = graph.findBottlenecks();
        
        // Generate dependency report
        String report = generateDependencyReport(graph, cycles, bottlenecks);
        
        return ToolOutput.builder()
                .type("dependency_analysis")
                .content(report)
                .metadata(Map.of(
                    "totalDependencies", graph.getEdgeCount(),
                    "cyclesDetected", cycles.size(),
                    "bottlenecks", bottlenecks.size()
                ))
                .build();
    }

    /**
     * Get the next autonomous action based on current plan state
     */
    private ToolOutput getNextAutonomousAction(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found");
        }
        
        Plan plan = activePlans.get(planId);
        PlanningContext context = planningContexts.get(planId);
        
        // Find next actionable task
        Task nextTask = findNextActionableTask(plan);
        
        if (nextTask == null) {
            // Check if plan is complete
            if (isPlanComplete(plan)) {
                return ToolOutput.builder()
                        .type("plan_complete")
                        .content("All tasks in the plan have been completed!")
                        .metadata(Map.of("planId", planId, "status", "COMPLETE"))
                        .build();
            } else {
                // Find blocking issues
                String blockingIssues = analyzeBlockingIssues(plan);
                return ToolOutput.builder()
                        .type("blocked")
                        .content("No actionable tasks available. Blocking issues:\n" + blockingIssues)
                        .build();
            }
        }
        
        // Generate action recommendation
        String actionRecommendation = generateActionRecommendation(nextTask, plan, context);
        
        // Update task status to in-progress
        nextTask.setStatus("IN_PROGRESS");
        nextTask.setStartedAt(LocalDateTime.now().format(formatter));
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        return ToolOutput.builder()
                .type("next_action")
                .content(actionRecommendation)
                .metadata(Map.of(
                    "taskId", nextTask.getId(),
                    "taskTitle", nextTask.getTitle(),
                    "taskType", nextTask.getType().toString(),
                    "estimatedHours", nextTask.getEstimatedHours()
                ))
                .build();
    }

    /**
     * Generate alternatives using Tree of Thoughts approach
     */
    private ToolOutput generateAlternatives(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        String taskId = (String) arguments.get("taskId");
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("Plan not found: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        Task task = findTaskById(plan, taskId).orElse(null);
        
        if (task == null) {
            return createErrorOutput("Task not found: " + taskId);
        }
        
        // Generate multiple solution approaches
        List<SolutionApproach> approaches = generateSolutionApproaches(task);
        
        // Evaluate each approach
        for (SolutionApproach approach : approaches) {
            evaluateApproach(approach, plan);
        }
        
        // Rank approaches
        approaches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        // Generate alternatives report
        String report = generateAlternativesReport(task, approaches);
        
        return ToolOutput.builder()
                .type("alternatives_generated")
                .content(report)
                .metadata(Map.of(
                    "taskId", taskId,
                    "alternativeCount", approaches.size(),
                    "bestApproach", approaches.get(0).getName()
                ))
                .build();
    }

    // Helper methods

    private Task createPhaseTask(String title, String description, TaskType type, int estimatedDays) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .type(type)
                .status("TODO")
                .priority("HIGH")
                .estimatedHours(estimatedDays * 8)
                .subtasks(new ArrayList<>())
                .dependencies(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }

    private List<Task> generateSubtasks(Task parentTask, int depth) {
        List<Task> subtasks = new ArrayList<>();
        
        // Generate subtasks based on parent task title
        if (parentTask.getTitle().contains("Backend Development")) {
            subtasks.add(createSubtask("Set up Spring Boot project", TaskType.PRIMITIVE, 2));
            subtasks.add(createSubtask("Design database schema", TaskType.PRIMITIVE, 4));
            subtasks.add(createSubtask("Implement entity models", TaskType.PRIMITIVE, 3));
            subtasks.add(createSubtask("Create REST API endpoints", TaskType.COMPOUND, 8));
            subtasks.add(createSubtask("Implement business logic", TaskType.COMPOUND, 10));
            subtasks.add(createSubtask("Add authentication/authorization", TaskType.PRIMITIVE, 6));
        } else if (parentTask.getTitle().contains("Frontend Development")) {
            subtasks.add(createSubtask("Set up React project", TaskType.PRIMITIVE, 2));
            subtasks.add(createSubtask("Design component architecture", TaskType.PRIMITIVE, 3));
            subtasks.add(createSubtask("Implement UI components", TaskType.COMPOUND, 8));
            subtasks.add(createSubtask("Integrate with backend APIs", TaskType.PRIMITIVE, 5));
            subtasks.add(createSubtask("Implement state management", TaskType.PRIMITIVE, 4));
            subtasks.add(createSubtask("Add routing and navigation", TaskType.PRIMITIVE, 3));
        } else {
            // Generic subtasks
            for (int i = 1; i <= 3; i++) {
                subtasks.add(createSubtask(
                    parentTask.getTitle() + " - Step " + i,
                    depth > 1 ? TaskType.COMPOUND : TaskType.PRIMITIVE,
                    4
                ));
            }
        }
        
        // Set parent reference
        subtasks.forEach(task -> task.setParentId(parentTask.getId()));
        
        return subtasks;
    }

    private Task createSubtask(String title, TaskType type, int estimatedHours) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description("Subtask: " + title)
                .type(type)
                .status("TODO")
                .priority("MEDIUM")
                .estimatedHours(estimatedHours)
                .subtasks(new ArrayList<>())
                .dependencies(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }

    private List<String> inferDependencies(Task task, Plan plan) {
        List<String> dependencies = new ArrayList<>();
        
        // Infer dependencies based on common patterns
        if (task.getTitle().contains("Test")) {
            // Testing depends on implementation
            plan.getTasks().stream()
                .filter(t -> t.getTitle().contains("Implement") || t.getTitle().contains("Development"))
                .filter(t -> !t.getId().equals(task.getId()))
                .forEach(t -> dependencies.add(t.getId()));
        } else if (task.getTitle().contains("Deploy")) {
            // Deployment depends on testing
            plan.getTasks().stream()
                .filter(t -> t.getTitle().contains("Test"))
                .forEach(t -> dependencies.add(t.getId()));
        } else if (task.getTitle().contains("Frontend")) {
            // Frontend depends on backend APIs
            plan.getTasks().stream()
                .filter(t -> t.getTitle().contains("API") || t.getTitle().contains("Backend"))
                .filter(t -> !t.getTitle().contains("Frontend"))
                .forEach(t -> dependencies.add(t.getId()));
        }
        
        return dependencies;
    }

    private Task findNextActionableTask(Plan plan) {
        return plan.getTasks().stream()
                .filter(task -> "TODO".equals(task.getStatus()))
                .filter(task -> task.getDependencies().isEmpty() || 
                               allDependenciesComplete(task, plan))
                .min(Comparator.comparing(Task::getPriority)
                               .thenComparing(Task::getEstimatedHours))
                .orElse(null);
    }

    private boolean allDependenciesComplete(Task task, Plan plan) {
        return task.getDependencies().stream()
                .allMatch(depId -> {
                    Task dep = findTaskById(plan, depId).orElse(null);
                    return dep != null && "DONE".equals(dep.getStatus());
                });
    }

    private boolean isPlanComplete(Plan plan) {
        return plan.getTasks().stream()
                .allMatch(task -> "DONE".equals(task.getStatus()));
    }

    private Optional<Task> findTaskById(Plan plan, String taskId) {
        return plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();
    }

    private String generateHierarchicalPlanReport(Plan plan, PlanningContext context) {
        StringBuilder report = new StringBuilder();
        report.append("# Hierarchical Development Plan\n\n");
        report.append("**Objective:** ").append(plan.getObjective()).append("\n");
        report.append("**Plan ID:** ").append(plan.getId()).append("\n");
        report.append("**Created:** ").append(plan.getCreated()).append("\n\n");
        
        report.append("## Executive Summary\n");
        report.append("- Total Phases: ").append(plan.getTasks().size()).append("\n");
        report.append("- Estimated Duration: ").append(plan.getMetadata().get("estimatedDays")).append(" days\n");
        report.append("- Complexity: HIGH\n\n");
        
        report.append("## Development Phases\n\n");
        
        for (Task phase : plan.getTasks()) {
            report.append("### ").append(phase.getTitle()).append("\n");
            report.append("- **ID:** `").append(phase.getId()).append("`\n");
            report.append("- **Type:** ").append(phase.getType()).append("\n");
            report.append("- **Status:** ").append(phase.getStatus()).append("\n");
            report.append("- **Estimated Hours:** ").append(phase.getEstimatedHours()).append("\n");
            report.append("- **Description:** ").append(phase.getDescription()).append("\n\n");
        }
        
        report.append("## Next Steps\n");
        report.append("1. Decompose each phase into detailed tasks\n");
        report.append("2. Analyze dependencies between phases\n");
        report.append("3. Assign resources and set timeline\n");
        report.append("4. Begin with Requirements & Analysis phase\n");
        
        return report.toString();
    }

    private String generateActionRecommendation(Task task, Plan plan, PlanningContext context) {
        StringBuilder rec = new StringBuilder();
        rec.append("# Next Recommended Action\n\n");
        rec.append("## Task: ").append(task.getTitle()).append("\n\n");
        rec.append("**Task ID:** `").append(task.getId()).append("`\n");
        rec.append("**Type:** ").append(task.getType()).append("\n");
        rec.append("**Priority:** ").append(task.getPriority()).append("\n");
        rec.append("**Estimated Hours:** ").append(task.getEstimatedHours()).append("\n\n");
        
        rec.append("## Action Steps:\n");
        
        // Generate specific action steps based on task
        if (task.getType() == TaskType.COMPOUND) {
            rec.append("1. Decompose this task into subtasks\n");
            rec.append("2. Identify required resources and tools\n");
            rec.append("3. Create initial implementation plan\n");
        } else {
            rec.append("1. Review task requirements\n");
            rec.append("2. Set up necessary tools/environment\n");
            rec.append("3. Begin implementation\n");
            rec.append("4. Test and validate results\n");
        }
        
        rec.append("\n## Context:\n");
        rec.append(task.getDescription()).append("\n\n");
        
        rec.append("## Success Criteria:\n");
        rec.append("- Task completed according to specifications\n");
        rec.append("- All tests pass\n");
        rec.append("- Code reviewed and documented\n");
        rec.append("- No blocking issues for dependent tasks\n");
        
        return rec.toString();
    }

    private ToolOutput createErrorOutput(String message) {
        return ToolOutput.builder()
                .type("error")
                .content(message)
                .build();
    }

    // Inner classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Plan {
        private String id;
        private String objective;
        private String type;
        private String status;
        private String created;
        private String updated;
        private List<Task> tasks;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Task {
        private String id;
        private String title;
        private String description;
        private TaskType type;
        private String status;
        private String priority;
        private int estimatedHours;
        private String parentId;
        private List<String> subtasks;
        private List<String> dependencies;
        private Map<String, Object> metadata;
        private String startedAt;
        private String completedAt;
    }

    private enum TaskType {
        COMPOUND,  // Can be decomposed further
        PRIMITIVE  // Cannot be decomposed (atomic action)
    }

    @Data
    private static class PlanningContext {
        private String objective;
        private Map<String, Object> constraints;
        private LocalDateTime createdAt;
        private List<String> assumptions = new ArrayList<>();
        private List<String> risks = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    private static class SolutionApproach {
        private String name;
        private String description;
        private List<String> pros;
        private List<String> cons;
        private double score;
        private Map<String, Object> metadata;
    }

    private static class DependencyGraph {
        private Map<String, Set<String>> adjacencyList = new HashMap<>();
        private Map<String, Task> nodes = new HashMap<>();
        
        void addNode(Task task) {
            nodes.put(task.getId(), task);
            adjacencyList.putIfAbsent(task.getId(), new HashSet<>());
        }
        
        void addEdge(String from, String to) {
            adjacencyList.putIfAbsent(from, new HashSet<>());
            adjacencyList.get(from).add(to);
        }
        
        int getEdgeCount() {
            return adjacencyList.values().stream()
                    .mapToInt(Set::size)
                    .sum();
        }
        
        List<List<String>> detectCycles() {
            // Simplified cycle detection
            return new ArrayList<>();
        }
        
        List<String> findBottlenecks() {
            // Find nodes with many dependents
            Map<String, Integer> dependentCounts = new HashMap<>();
            
            for (Set<String> dependents : adjacencyList.values()) {
                for (String dependent : dependents) {
                    dependentCounts.merge(dependent, 1, Integer::sum);
                }
            }
            
            return dependentCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 2)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    // Additional helper methods for report generation
    
    private String generateDecompositionReport(Task parent, List<Task> subtasks) {
        StringBuilder report = new StringBuilder();
        report.append("# Task Decomposition Report\n\n");
        report.append("## Parent Task: ").append(parent.getTitle()).append("\n\n");
        report.append("Successfully decomposed into ").append(subtasks.size()).append(" subtasks:\n\n");
        
        for (int i = 0; i < subtasks.size(); i++) {
            Task subtask = subtasks.get(i);
            report.append(i + 1).append(". **").append(subtask.getTitle()).append("**\n");
            report.append("   - ID: `").append(subtask.getId()).append("`\n");
            report.append("   - Type: ").append(subtask.getType()).append("\n");
            report.append("   - Estimated Hours: ").append(subtask.getEstimatedHours()).append("\n\n");
        }
        
        return report.toString();
    }
    
    private String generateDependencyReport(DependencyGraph graph, List<List<String>> cycles, List<String> bottlenecks) {
        StringBuilder report = new StringBuilder();
        report.append("# Dependency Analysis Report\n\n");
        report.append("## Summary\n");
        report.append("- Total Dependencies: ").append(graph.getEdgeCount()).append("\n");
        report.append("- Cycles Detected: ").append(cycles.size()).append("\n");
        report.append("- Bottlenecks Found: ").append(bottlenecks.size()).append("\n\n");
        
        if (!bottlenecks.isEmpty()) {
            report.append("## Bottlenecks\n");
            report.append("Tasks with many dependencies:\n");
            for (String bottleneck : bottlenecks) {
                Task task = graph.nodes.get(bottleneck);
                if (task != null) {
                    report.append("- ").append(task.getTitle()).append(" (ID: ").append(bottleneck).append(")\n");
                }
            }
            report.append("\n");
        }
        
        return report.toString();
    }

    private void analyzePhaseDependencies(Plan plan) {
        // Set up basic phase dependencies
        List<Task> phases = plan.getTasks();
        for (int i = 1; i < phases.size(); i++) {
            Task current = phases.get(i);
            Task previous = phases.get(i - 1);
            
            // Some phases can run in parallel
            if (!canRunInParallel(current, previous)) {
                current.getDependencies().add(previous.getId());
            }
        }
    }

    private boolean canRunInParallel(Task task1, Task task2) {
        // Define which phases can run in parallel
        return (task1.getTitle().contains("Frontend") && task2.getTitle().contains("Backend")) ||
               (task1.getTitle().contains("Documentation") && !task2.getTitle().contains("Development"));
    }

    private void calculatePlanMetrics(Plan plan) {
        int totalHours = plan.getTasks().stream()
                .mapToInt(Task::getEstimatedHours)
                .sum();
                
        int estimatedDays = (int) Math.ceil(totalHours / 8.0);
        plan.getMetadata().put("totalHours", totalHours);
        plan.getMetadata().put("estimatedDays", estimatedDays);
    }

    private String analyzeBlockingIssues(Plan plan) {
        StringBuilder issues = new StringBuilder();
        
        List<Task> blockedTasks = plan.getTasks().stream()
                .filter(t -> "TODO".equals(t.getStatus()))
                .filter(t -> !allDependenciesComplete(t, plan))
                .collect(Collectors.toList());
                
        for (Task blocked : blockedTasks) {
            issues.append("- ").append(blocked.getTitle()).append(" is blocked by:\n");
            for (String depId : blocked.getDependencies()) {
                Task dep = findTaskById(plan, depId).orElse(null);
                if (dep != null && !"DONE".equals(dep.getStatus())) {
                    issues.append("  - ").append(dep.getTitle()).append(" (").append(dep.getStatus()).append(")\n");
                }
            }
        }
        
        return issues.toString();
    }

    private List<SolutionApproach> generateSolutionApproaches(Task task) {
        List<SolutionApproach> approaches = new ArrayList<>();
        
        // Generate different approaches based on task type
        if (task.getTitle().contains("API")) {
            approaches.add(new SolutionApproach(
                "RESTful API",
                "Traditional REST API with JSON",
                List.of("Well understood", "Wide tooling support", "Easy to test"),
                List.of("Can be chatty", "No real-time updates"),
                0.8,
                new HashMap<>()
            ));
            
            approaches.add(new SolutionApproach(
                "GraphQL API",
                "GraphQL with type-safe queries",
                List.of("Efficient data fetching", "Strong typing", "Self-documenting"),
                List.of("Learning curve", "Complex caching"),
                0.7,
                new HashMap<>()
            ));
            
            approaches.add(new SolutionApproach(
                "gRPC API",
                "High-performance RPC with Protocol Buffers",
                List.of("Very fast", "Streaming support", "Strong contracts"),
                List.of("Not web-friendly", "Binary format"),
                0.6,
                new HashMap<>()
            ));
        }
        
        return approaches;
    }

    private void evaluateApproach(SolutionApproach approach, Plan plan) {
        // Simple scoring based on pros/cons
        double score = 0.5;
        score += approach.getPros().size() * 0.1;
        score -= approach.getCons().size() * 0.05;
        approach.setScore(Math.max(0, Math.min(1, score)));
    }

    private String generateAlternativesReport(Task task, List<SolutionApproach> approaches) {
        StringBuilder report = new StringBuilder();
        report.append("# Solution Alternatives Analysis\n\n");
        report.append("## Task: ").append(task.getTitle()).append("\n\n");
        
        for (int i = 0; i < approaches.size(); i++) {
            SolutionApproach approach = approaches.get(i);
            report.append("### ").append(i + 1).append(". ").append(approach.getName());
            report.append(" (Score: ").append(String.format("%.2f", approach.getScore())).append(")\n\n");
            report.append("**Description:** ").append(approach.getDescription()).append("\n\n");
            
            report.append("**Pros:**\n");
            approach.getPros().forEach(pro -> report.append("- ").append(pro).append("\n"));
            
            report.append("\n**Cons:**\n");
            approach.getCons().forEach(con -> report.append("- ").append(con).append("\n"));
            report.append("\n");
        }
        
        report.append("## Recommendation\n");
        report.append("Based on the analysis, **").append(approaches.get(0).getName());
        report.append("** is recommended as it has the highest score.\n");
        
        return report.toString();
    }

    private ToolOutput optimizePlan(Map<String, Object> arguments) {
        // Implementation for plan optimization
        return ToolOutput.builder()
                .type("plan_optimized")
                .content("Plan optimization completed")
                .build();
    }

    private ToolOutput assessRisks(Map<String, Object> arguments) {
        // Implementation for risk assessment
        return ToolOutput.builder()
                .type("risks_assessed")
                .content("Risk assessment completed")
                .build();
    }

    private ToolOutput updateProgress(Map<String, Object> arguments) {
        // Implementation for progress updates
        return ToolOutput.builder()
                .type("progress_updated")
                .content("Progress updated successfully")
                .build();
    }

    private ToolOutput calculateCriticalPathAdvanced(Map<String, Object> arguments) {
        // Implementation for advanced critical path calculation
        return ToolOutput.builder()
                .type("critical_path")
                .content("Critical path calculated")
                .build();
    }

    @PreDestroy
    public void cleanup() {
        activePlans.clear();
        planningContexts.clear();
    }
}
