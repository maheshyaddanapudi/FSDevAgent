package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Comprehensive planning tool for software development projects.
 * Supports hierarchical task decomposition, sprint planning, architecture decisions,
 * technical debt tracking, and more.
 */
@Slf4j
@Component
public class PlanningTool implements Tool {
    
    private final ObjectMapper objectMapper;
    private final Map<String, Plan> activePlans = new ConcurrentHashMap<>();
    private final Map<String, TaskTemplate> taskTemplates = new HashMap<>();
    
    public PlanningTool() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initializeTaskTemplates();
    }
    
    private void initializeTaskTemplates() {
        // Initialize common task templates
        taskTemplates.put("feature", TaskTemplate.builder()
            .id("feature")
            .name("Feature Development")
            .description("Template for feature development tasks")
            .defaultSubtasks(Arrays.asList(
                "Requirements Analysis",
                "Design",
                "Implementation",
                "Testing",
                "Documentation"
            ))
            .build());
            
        taskTemplates.put("bug_fix", TaskTemplate.builder()
            .id("bug_fix")
            .name("Bug Fix")
            .description("Template for bug fixing tasks")
            .defaultSubtasks(Arrays.asList(
                "Reproduce Issue",
                "Root Cause Analysis",
                "Fix Implementation",
                "Regression Testing",
                "Documentation Update"
            ))
            .build());
            
        taskTemplates.put("refactoring", TaskTemplate.builder()
            .id("refactoring")
            .name("Code Refactoring")
            .description("Template for code refactoring tasks")
            .defaultSubtasks(Arrays.asList(
                "Code Analysis",
                "Design Improvements",
                "Implementation",
                "Testing",
                "Documentation Update"
            ))
            .build());
    }
    
    @Override
    public String getName() {
        return "planning_tool";
    }
    
    @Override
    public String getDescription() {
        return "Comprehensive planning tool for software development projects. " +
               "Supports task decomposition, sprint planning, architecture decisions, " +
               "technical debt tracking, dependency analysis, and resource allocation.";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
            .name("operation")
            .type("string")
            .description("Operation: create_plan, decompose_task, analyze_dependencies, " +
                        "calculate_critical_path, plan_sprint, track_technical_debt, " +
                        "create_architecture_decision, get_plan, update_task, generate_report")
            .required(true)
            .enumValues(Arrays.asList("create_plan", "decompose_task", "analyze_dependencies",
                        "calculate_critical_path", "plan_sprint", "track_technical_debt",
                        "create_architecture_decision", "get_plan", "update_task", "generate_report"))
            .build());
            
        params.put("objective", ParameterInfo.builder()
            .name("objective")
            .type("string")
            .description("The main objective or goal (for create_plan, decompose_task)")
            .required(false)
            .build());
            
        params.put("type", ParameterInfo.builder()
            .name("type")
            .type("string")
            .description("Plan type: feature, architecture, refactoring, sprint, release")
            .required(false)
            .enumValues(Arrays.asList("feature", "architecture", "refactoring", "sprint", "release"))
            .build());
            
        params.put("planId", ParameterInfo.builder()
            .name("planId")
            .type("string")
            .description("ID of existing plan (for operations on existing plans)")
            .required(false)
            .build());
            
        params.put("taskId", ParameterInfo.builder()
            .name("taskId")
            .type("string")
            .description("ID of specific task (for task operations)")
            .required(false)
            .build());
            
        params.put("dependencies", ParameterInfo.builder()
            .name("dependencies")
            .type("string")
            .description("Comma-separated list of task dependencies")
            .required(false)
            .build());
            
        params.put("timeline", ParameterInfo.builder()
            .name("timeline")
            .type("string")
            .description("Expected timeline (e.g., '2 weeks', '1 sprint', '3 days')")
            .required(false)
            .build());
            
        params.put("resources", ParameterInfo.builder()
            .name("resources")
            .type("string")
            .description("Available resources (developers, tools, etc.)")
            .required(false)
            .build());
            
        params.put("format", ParameterInfo.builder()
            .name("format")
            .type("string")
            .description("Output format: json, markdown, gantt")
            .required(false)
            .enumValues(Arrays.asList("json", "markdown", "gantt"))
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String operation = (String) arguments.get("operation");
        
        if (operation == null) {
            return Flux.error(new IllegalArgumentException("Operation parameter is required"));
        }
        
        return switch (operation.toLowerCase()) {
            case "create_plan" -> createPlan(arguments);
            case "decompose_task" -> decomposeTask(arguments);
            case "analyze_dependencies" -> analyzeDependencies(arguments);
            case "calculate_critical_path" -> calculateCriticalPath(arguments);
            case "plan_sprint" -> planSprint(arguments);
            case "track_technical_debt" -> trackTechnicalDebt(arguments);
            case "create_architecture_decision" -> createArchitectureDecision(arguments);
            case "get_plan" -> getPlan(arguments);
            case "update_task" -> updateTask(arguments);
            case "generate_report" -> generateReport(arguments);
            default -> Flux.error(new IllegalArgumentException("Unknown operation: " + operation));
        };
    }
    
    private Flux<ToolOutput> createPlan(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String objective = (String) arguments.get("objective");
            String type = (String) arguments.getOrDefault("type", "feature");
            String timeline = (String) arguments.get("timeline");
            
            if (objective == null) {
                throw new IllegalArgumentException("Objective is required for creating a plan");
            }
            
            Plan plan = Plan.builder()
                .id(UUID.randomUUID().toString())
                .objective(objective)
                .type(PlanType.valueOf(type.toUpperCase()))
                .status(PlanStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tasks(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
                
            if (timeline != null) {
                plan.getMetadata().put("timeline", timeline);
            }
            
            // Store the plan
            activePlans.put(plan.getId(), plan);
            
            // Create initial tasks based on plan type
            List<Task> initialTasks = createInitialTasks(plan);
            plan.setTasks(initialTasks);
            
            return ToolOutput.builder()
                .type("plan_created")
                .content("Plan created: " + plan.getId())
                .metadata(Map.of(
                    "plan_id", plan.getId(),
                    "objective", plan.getObjective(),
                    "type", plan.getType().toString(),
                    "status", plan.getStatus().toString(),
                    "created_at", plan.getCreatedAt().toString(),
                    "task_count", String.valueOf(plan.getTasks().size())
                ))
                .build();
        }).flux();
    }
    
    private List<Task> createInitialTasks(Plan plan) {
        List<Task> tasks = new ArrayList<>();
        
        switch (plan.getType()) {
            case FEATURE -> {
                TaskTemplate template = taskTemplates.get("feature");
                if (template != null) {
                    int order = 0;
                    for (String subtaskName : template.getDefaultSubtasks()) {
                        tasks.add(Task.builder()
                            .id(UUID.randomUUID().toString())
                            .planId(plan.getId())
                            .title(subtaskName)
                            .description("Task for " + subtaskName)
                            .status(TaskStatus.TODO)
                            .priority(TaskPriority.MEDIUM)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .dependencies(new ArrayList<>())
                            .subtasks(new ArrayList<>())
                            .metadata(Map.of("order", String.valueOf(order++)))
                            .build());
                    }
                }
            }
            case ARCHITECTURE -> {
                tasks.add(Task.builder()
                    .id(UUID.randomUUID().toString())
                    .planId(plan.getId())
                    .title("Architecture Analysis")
                    .description("Analyze current architecture")
                    .status(TaskStatus.TODO)
                    .priority(TaskPriority.HIGH)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .dependencies(new ArrayList<>())
                    .subtasks(new ArrayList<>())
                    .build());
                    
                tasks.add(Task.builder()
                    .id(UUID.randomUUID().toString())
                    .planId(plan.getId())
                    .title("Architecture Design")
                    .description("Design new architecture")
                    .status(TaskStatus.TODO)
                    .priority(TaskPriority.HIGH)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .dependencies(Arrays.asList(tasks.get(0).getId()))
                    .subtasks(new ArrayList<>())
                    .build());
            }
            default -> {
                tasks.add(Task.builder()
                    .id(UUID.randomUUID().toString())
                    .planId(plan.getId())
                    .title("Initial Planning")
                    .description("Initial planning task")
                    .status(TaskStatus.TODO)
                    .priority(TaskPriority.MEDIUM)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .dependencies(new ArrayList<>())
                    .subtasks(new ArrayList<>())
                    .build());
            }
        }
        
        return tasks;
    }
    
    private Flux<ToolOutput> decomposeTask(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String taskId = (String) arguments.get("taskId");
            String objective = (String) arguments.get("objective");
            
            if (planId == null || taskId == null) {
                throw new IllegalArgumentException("Plan ID and Task ID are required for task decomposition");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            Task parentTask = findTaskById(plan, taskId);
            if (parentTask == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }
            
            // Create subtasks
            List<Task> subtasks = new ArrayList<>();
            String[] subtaskTitles;
            
            if (objective != null) {
                // Use objective to generate custom subtasks
                subtaskTitles = generateSubtasksFromObjective(objective);
            } else {
                // Use default decomposition based on task title
                subtaskTitles = generateDefaultSubtasks(parentTask.getTitle());
            }
            
            for (int i = 0; i < subtaskTitles.length; i++) {
                Task subtask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .planId(planId)
                    .title(subtaskTitles[i])
                    .description("Subtask for " + parentTask.getTitle())
                    .status(TaskStatus.TODO)
                    .priority(parentTask.getPriority())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .dependencies(new ArrayList<>())
                    .subtasks(new ArrayList<>())
                    .metadata(Map.of("order", String.valueOf(i)))
                    .build();
                    
                subtasks.add(subtask);
                
                // If not the first subtask, add dependency on previous subtask
                if (i > 0) {
                    subtask.getDependencies().add(subtasks.get(i-1).getId());
                }
            }
            
            // Add subtasks to parent task
            parentTask.setSubtasks(subtasks);
            
            return ToolOutput.builder()
                .type("task_decomposed")
                .content("Task decomposed: " + taskId)
                .metadata(Map.of(
                    "plan_id", planId,
                    "task_id", taskId,
                    "subtask_count", String.valueOf(subtasks.size()),
                    "subtasks", subtasks.stream().map(Task::getTitle).collect(Collectors.joining(", "))
                ))
                .build();
        }).flux();
    }
    
    private String[] generateSubtasksFromObjective(String objective) {
        // In a real implementation, this could use NLP or predefined patterns
        // For now, use a simple approach
        if (objective.toLowerCase().contains("feature")) {
            return new String[] {
                "Requirements Analysis",
                "Design",
                "Implementation",
                "Testing",
                "Documentation"
            };
        } else if (objective.toLowerCase().contains("bug")) {
            return new String[] {
                "Reproduce Issue",
                "Root Cause Analysis",
                "Fix Implementation",
                "Regression Testing"
            };
        } else {
            return new String[] {
                "Research",
                "Planning",
                "Implementation",
                "Verification"
            };
        }
    }
    
    private String[] generateDefaultSubtasks(String taskTitle) {
        // Generate default subtasks based on task title
        if (taskTitle.toLowerCase().contains("design")) {
            return new String[] {
                "Research",
                "Draft Design",
                "Review",
                "Finalize Design"
            };
        } else if (taskTitle.toLowerCase().contains("implement")) {
            return new String[] {
                "Setup Environment",
                "Code Implementation",
                "Unit Testing",
                "Code Review"
            };
        } else if (taskTitle.toLowerCase().contains("test")) {
            return new String[] {
                "Test Plan",
                "Test Case Development",
                "Test Execution",
                "Bug Reporting"
            };
        } else {
            return new String[] {
                "Research",
                "Planning",
                "Execution",
                "Verification"
            };
        }
    }
    
    private Flux<ToolOutput> analyzeDependencies(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            
            if (planId == null) {
                throw new IllegalArgumentException("Plan ID is required for dependency analysis");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            // Flatten all tasks including subtasks
            List<Task> allTasks = flattenTasks(plan);
            
            // Build dependency graph
            Map<String, List<String>> dependencyGraph = new HashMap<>();
            Map<String, List<String>> reverseDependencyGraph = new HashMap<>();
            
            for (Task task : allTasks) {
                dependencyGraph.put(task.getId(), new ArrayList<>(task.getDependencies()));
                
                // Build reverse dependency graph
                for (String depId : task.getDependencies()) {
                    reverseDependencyGraph.computeIfAbsent(depId, k -> new ArrayList<>()).add(task.getId());
                }
            }
            
            // Find tasks with no dependencies (entry points)
            List<String> entryPoints = allTasks.stream()
                .filter(task -> task.getDependencies().isEmpty())
                .map(Task::getId)
                .collect(Collectors.toList());
                
            // Find tasks with no dependents (exit points)
            List<String> exitPoints = allTasks.stream()
                .filter(task -> !reverseDependencyGraph.containsKey(task.getId()) || 
                               reverseDependencyGraph.get(task.getId()).isEmpty())
                .map(Task::getId)
                .collect(Collectors.toList());
                
            // Check for cycles
            boolean hasCycles = detectCycles(dependencyGraph);
            
            // Generate dependency report
            Map<String, Object> report = new HashMap<>();
            report.put("total_tasks", allTasks.size());
            report.put("entry_points", entryPoints.size());
            report.put("exit_points", exitPoints.size());
            report.put("has_cycles", hasCycles);
            
            // Task details with dependencies
            List<Map<String, Object>> taskDetails = new ArrayList<>();
            for (Task task : allTasks) {
                Map<String, Object> taskDetail = new HashMap<>();
                taskDetail.put("id", task.getId());
                taskDetail.put("title", task.getTitle());
                taskDetail.put("dependencies", task.getDependencies());
                taskDetail.put("dependents", 
                    reverseDependencyGraph.getOrDefault(task.getId(), Collections.emptyList()));
                taskDetails.add(taskDetail);
            }
            report.put("tasks", taskDetails);
            
            return ToolOutput.builder()
                .type("dependency_analysis")
                .content("Dependency analysis for plan: " + planId)
                .metadata(report)
                .build();
        }).flux();
    }
    
    private boolean detectCycles(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (hasCycle(node, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycle(String node, Map<String, List<String>> graph, 
                            Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        List<String> dependencies = graph.getOrDefault(node, Collections.emptyList());
        for (String dependency : dependencies) {
            if (hasCycle(dependency, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    private Flux<ToolOutput> calculateCriticalPath(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            
            if (planId == null) {
                throw new IllegalArgumentException("Plan ID is required for critical path calculation");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            // Flatten all tasks including subtasks
            List<Task> allTasks = flattenTasks(plan);
            
            // For simplicity, assume each task takes 1 day
            // In a real implementation, task duration would be a property of the task
            Map<String, Integer> taskDurations = new HashMap<>();
            for (Task task : allTasks) {
                taskDurations.put(task.getId(), 1);
            }
            
            // Build dependency graph
            Map<String, List<String>> dependencyGraph = new HashMap<>();
            for (Task task : allTasks) {
                dependencyGraph.put(task.getId(), new ArrayList<>(task.getDependencies()));
            }
            
            // Calculate earliest start and finish times
            Map<String, Integer> earliestStart = new HashMap<>();
            Map<String, Integer> earliestFinish = new HashMap<>();
            
            // Topological sort
            List<String> sortedTasks = topologicalSort(dependencyGraph);
            
            // Forward pass
            for (String taskId : sortedTasks) {
                int maxDependencyFinish = 0;
                for (String depId : dependencyGraph.getOrDefault(taskId, Collections.emptyList())) {
                    maxDependencyFinish = Math.max(maxDependencyFinish, 
                                                earliestFinish.getOrDefault(depId, 0));
                }
                
                earliestStart.put(taskId, maxDependencyFinish);
                earliestFinish.put(taskId, maxDependencyFinish + taskDurations.get(taskId));
            }
            
            // Calculate latest start and finish times
            Map<String, Integer> latestStart = new HashMap<>();
            Map<String, Integer> latestFinish = new HashMap<>();
            
            // Find project duration
            int projectDuration = 0;
            for (int finish : earliestFinish.values()) {
                projectDuration = Math.max(projectDuration, finish);
            }
            
            // Build reverse dependency graph
            Map<String, List<String>> reverseDependencyGraph = new HashMap<>();
            for (Task task : allTasks) {
                for (String depId : task.getDependencies()) {
                    reverseDependencyGraph.computeIfAbsent(depId, k -> new ArrayList<>()).add(task.getId());
                }
            }
            
            // Backward pass
            List<String> reverseSortedTasks = new ArrayList<>(sortedTasks);
            Collections.reverse(reverseSortedTasks);
            
            for (String taskId : reverseSortedTasks) {
                List<String> dependents = reverseDependencyGraph.getOrDefault(taskId, Collections.emptyList());
                
                if (dependents.isEmpty()) {
                    // Exit task
                    latestFinish.put(taskId, projectDuration);
                } else {
                    int minDependentStart = Integer.MAX_VALUE;
                    for (String depId : dependents) {
                        minDependentStart = Math.min(minDependentStart, 
                                                  latestStart.getOrDefault(depId, Integer.MAX_VALUE));
                    }
                    latestFinish.put(taskId, minDependentStart);
                }
                
                latestStart.put(taskId, latestFinish.get(taskId) - taskDurations.get(taskId));
            }
            
            // Calculate slack and identify critical path
            List<String> criticalPath = new ArrayList<>();
            List<Map<String, Object>> taskDetails = new ArrayList<>();
            
            for (Task task : allTasks) {
                String taskId = task.getId();
                int slack = latestStart.get(taskId) - earliestStart.get(taskId);
                
                if (slack == 0) {
                    criticalPath.add(taskId);
                }
                
                Map<String, Object> taskDetail = new HashMap<>();
                taskDetail.put("id", taskId);
                taskDetail.put("title", task.getTitle());
                taskDetail.put("earliest_start", earliestStart.get(taskId));
                taskDetail.put("earliest_finish", earliestFinish.get(taskId));
                taskDetail.put("latest_start", latestStart.get(taskId));
                taskDetail.put("latest_finish", latestFinish.get(taskId));
                taskDetail.put("slack", slack);
                taskDetail.put("is_critical", slack == 0);
                
                taskDetails.add(taskDetail);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("project_duration", projectDuration);
            result.put("critical_path", criticalPath);
            result.put("tasks", taskDetails);
            
            return ToolOutput.builder()
                .type("critical_path")
                .content("Critical path calculated for plan: " + planId)
                .metadata(result)
                .build();
        }).flux();
    }
    
    private List<String> topologicalSort(Map<String, List<String>> graph) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> temp = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                topologicalSortUtil(node, graph, visited, temp, result);
            }
        }
        
        Collections.reverse(result);
        return result;
    }
    
    private void topologicalSortUtil(String node, Map<String, List<String>> graph, 
                                   Set<String> visited, Set<String> temp, List<String> result) {
        if (temp.contains(node)) {
            // Cycle detected, but continue with best effort
            return;
        }
        
        if (visited.contains(node)) {
            return;
        }
        
        temp.add(node);
        
        for (String dependency : graph.getOrDefault(node, Collections.emptyList())) {
            topologicalSortUtil(dependency, graph, visited, temp, result);
        }
        
        temp.remove(node);
        visited.add(node);
        result.add(node);
    }
    
    private Flux<ToolOutput> planSprint(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String timeline = (String) arguments.get("timeline");
            String resources = (String) arguments.get("resources");
            
            if (planId == null) {
                throw new IllegalArgumentException("Plan ID is required for sprint planning");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            // Create a new sprint plan
            Plan sprintPlan = Plan.builder()
                .id(UUID.randomUUID().toString())
                .objective("Sprint plan for " + plan.getObjective())
                .type(PlanType.SPRINT)
                .status(PlanStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .tasks(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
                
            if (timeline != null) {
                sprintPlan.getMetadata().put("timeline", timeline);
            }
            
            if (resources != null) {
                sprintPlan.getMetadata().put("resources", resources);
            }
            
            // Link to parent plan
            sprintPlan.getMetadata().put("parent_plan_id", planId);
            
            // Get all tasks from the parent plan
            List<Task> allTasks = flattenTasks(plan);
            
            // Filter tasks that are not yet completed
            List<Task> availableTasks = allTasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .collect(Collectors.toList());
                
            // Sort tasks by priority
            availableTasks.sort((t1, t2) -> t2.getPriority().compareTo(t1.getPriority()));
            
            // For simplicity, select top N tasks for the sprint
            // In a real implementation, this would consider dependencies, capacity, etc.
            int sprintCapacity = 5; // Default capacity
            if (resources != null) {
                // Simple heuristic: 3 tasks per resource
                String[] resourceList = resources.split(",");
                sprintCapacity = resourceList.length * 3;
            }
            
            List<Task> sprintTasks = availableTasks.stream()
                .limit(sprintCapacity)
                .map(task -> {
                    // Create a copy of the task for the sprint plan
                    return Task.builder()
                        .id(UUID.randomUUID().toString())
                        .planId(sprintPlan.getId())
                        .title(task.getTitle())
                        .description(task.getDescription())
                        .status(TaskStatus.TODO)
                        .priority(task.getPriority())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .dependencies(new ArrayList<>())
                        .subtasks(new ArrayList<>())
                        .metadata(Map.of("original_task_id", task.getId()))
                        .build();
                })
                .collect(Collectors.toList());
                
            sprintPlan.setTasks(sprintTasks);
            
            // Store the sprint plan
            activePlans.put(sprintPlan.getId(), sprintPlan);
            
            return ToolOutput.builder()
                .type("sprint_planned")
                .content("Sprint plan created: " + sprintPlan.getId())
                .metadata(Map.of(
                    "sprint_plan_id", sprintPlan.getId(),
                    "parent_plan_id", planId,
                    "task_count", String.valueOf(sprintTasks.size()),
                    "timeline", timeline != null ? timeline : "Not specified",
                    "resources", resources != null ? resources : "Not specified"
                ))
                .build();
        }).flux();
    }
    
    private Flux<ToolOutput> trackTechnicalDebt(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String title = (String) arguments.get("objective"); // Reuse objective parameter
            String description = (String) arguments.get("text"); // Additional parameter
            String impact = (String) arguments.get("impact"); // Additional parameter
            String severity = (String) arguments.getOrDefault("severity", "MEDIUM"); // Additional parameter
            
            if (planId == null || title == null) {
                throw new IllegalArgumentException("Plan ID and title are required for technical debt tracking");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            // Create technical debt item
            TechnicalDebt debt = TechnicalDebt.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .title(title)
                .description(description != null ? description : "")
                .impact(impact != null ? impact : "Unknown impact")
                .remediationPlan("")
                .severity(TechnicalDebtSeverity.valueOf(severity.toUpperCase()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
                
            // Store technical debt in plan metadata
            if (!plan.getMetadata().containsKey("technical_debt")) {
                plan.getMetadata().put("technical_debt", new ArrayList<TechnicalDebt>());
            }
            
            @SuppressWarnings("unchecked")
            List<TechnicalDebt> debtItems = (List<TechnicalDebt>) plan.getMetadata().get("technical_debt");
            debtItems.add(debt);
            
            return ToolOutput.builder()
                .type("technical_debt_tracked")
                .content("Technical debt item added: " + debt.getId())
                .metadata(Map.of(
                    "debt_id", debt.getId(),
                    "plan_id", planId,
                    "title", debt.getTitle(),
                    "severity", debt.getSeverity().toString(),
                    "created_at", debt.getCreatedAt().toString()
                ))
                .build();
        }).flux();
    }
    
    private Flux<ToolOutput> createArchitectureDecision(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String title = (String) arguments.get("objective"); // Reuse objective parameter
            String context = (String) arguments.get("context"); // Additional parameter
            String decision = (String) arguments.get("decision"); // Additional parameter
            String rationale = (String) arguments.get("rationale"); // Additional parameter
            String alternatives = (String) arguments.get("alternatives"); // Additional parameter
            String consequences = (String) arguments.get("consequences"); // Additional parameter
            
            if (planId == null || title == null || decision == null) {
                throw new IllegalArgumentException("Plan ID, title, and decision are required for architecture decisions");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            // Parse alternatives and consequences
            List<String> alternativesList = alternatives != null ? 
                Arrays.asList(alternatives.split(",")) : new ArrayList<>();
                
            List<String> consequencesList = consequences != null ? 
                Arrays.asList(consequences.split(",")) : new ArrayList<>();
                
            // Create architecture decision
            ArchitectureDecision decision_record = ArchitectureDecision.builder()
                .id(UUID.randomUUID().toString())
                .planId(planId)
                .title(title)
                .context(context != null ? context : "")
                .decision(decision)
                .rationale(rationale != null ? rationale : "")
                .alternatives(alternativesList)
                .consequences(consequencesList)
                .createdAt(LocalDateTime.now())
                .build();
                
            // Store architecture decision in plan metadata
            if (!plan.getMetadata().containsKey("architecture_decisions")) {
                plan.getMetadata().put("architecture_decisions", new ArrayList<ArchitectureDecision>());
            }
            
            @SuppressWarnings("unchecked")
            List<ArchitectureDecision> decisions = (List<ArchitectureDecision>) plan.getMetadata().get("architecture_decisions");
            decisions.add(decision_record);
            
            return ToolOutput.builder()
                .type("architecture_decision_created")
                .content("Architecture decision recorded: " + decision_record.getId())
                .metadata(Map.of(
                    "decision_id", decision_record.getId(),
                    "plan_id", planId,
                    "title", decision_record.getTitle(),
                    "created_at", decision_record.getCreatedAt().toString()
                ))
                .build();
        }).flux();
    }
    
    private Flux<ToolOutput> getPlan(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String format = (String) arguments.getOrDefault("format", "json");
            
            if (planId == null) {
                throw new IllegalArgumentException("Plan ID is required to get a plan");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            String content;
            if ("markdown".equalsIgnoreCase(format)) {
                content = generateMarkdownReport(plan);
            } else if ("gantt".equalsIgnoreCase(format)) {
                content = generateGanttChart(plan);
            } else {
                // Default to JSON
                content = objectMapper.writeValueAsString(plan);
            }
            
            return ToolOutput.builder()
                .type("plan_details")
                .content(content)
                .metadata(Map.of(
                    "plan_id", plan.getId(),
                    "objective", plan.getObjective(),
                    "type", plan.getType().toString(),
                    "status", plan.getStatus().toString(),
                    "format", format,
                    "task_count", String.valueOf(plan.getTasks().size())
                ))
                .build();
        }).flux();
    }
    
    private String generateMarkdownReport(Plan plan) {
        StringBuilder markdown = new StringBuilder();
        
        markdown.append("# ").append(plan.getObjective()).append("\n\n");
        markdown.append("**Plan ID:** ").append(plan.getId()).append("\n");
        markdown.append("**Type:** ").append(plan.getType()).append("\n");
        markdown.append("**Status:** ").append(plan.getStatus()).append("\n");
        markdown.append("**Created:** ").append(plan.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        markdown.append("**Updated:** ").append(plan.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        
        // Add metadata
        if (!plan.getMetadata().isEmpty()) {
            markdown.append("## Metadata\n\n");
            for (Map.Entry<String, Object> entry : plan.getMetadata().entrySet()) {
                if (!entry.getKey().equals("technical_debt") && !entry.getKey().equals("architecture_decisions")) {
                    markdown.append("- **").append(entry.getKey()).append(":** ").append(entry.getValue()).append("\n");
                }
            }
            markdown.append("\n");
        }
        
        // Add tasks
        markdown.append("## Tasks\n\n");
        for (Task task : plan.getTasks()) {
            appendTaskToMarkdown(markdown, task, 0);
        }
        
        // Add technical debt if any
        if (plan.getMetadata().containsKey("technical_debt")) {
            markdown.append("## Technical Debt\n\n");
            
            @SuppressWarnings("unchecked")
            List<TechnicalDebt> debtItems = (List<TechnicalDebt>) plan.getMetadata().get("technical_debt");
            
            for (TechnicalDebt debt : debtItems) {
                markdown.append("### ").append(debt.getTitle()).append("\n\n");
                markdown.append("**ID:** ").append(debt.getId()).append("\n");
                markdown.append("**Severity:** ").append(debt.getSeverity()).append("\n");
                markdown.append("**Impact:** ").append(debt.getImpact()).append("\n\n");
                
                if (!debt.getDescription().isEmpty()) {
                    markdown.append(debt.getDescription()).append("\n\n");
                }
                
                if (!debt.getRemediationPlan().isEmpty()) {
                    markdown.append("**Remediation Plan:**\n\n").append(debt.getRemediationPlan()).append("\n\n");
                }
            }
        }
        
        // Add architecture decisions if any
        if (plan.getMetadata().containsKey("architecture_decisions")) {
            markdown.append("## Architecture Decisions\n\n");
            
            @SuppressWarnings("unchecked")
            List<ArchitectureDecision> decisions = (List<ArchitectureDecision>) plan.getMetadata().get("architecture_decisions");
            
            for (ArchitectureDecision decision : decisions) {
                markdown.append("### ").append(decision.getTitle()).append("\n\n");
                
                if (!decision.getContext().isEmpty()) {
                    markdown.append("**Context:**\n\n").append(decision.getContext()).append("\n\n");
                }
                
                markdown.append("**Decision:**\n\n").append(decision.getDecision()).append("\n\n");
                
                if (!decision.getRationale().isEmpty()) {
                    markdown.append("**Rationale:**\n\n").append(decision.getRationale()).append("\n\n");
                }
                
                if (!decision.getAlternatives().isEmpty()) {
                    markdown.append("**Alternatives Considered:**\n\n");
                    for (String alternative : decision.getAlternatives()) {
                        markdown.append("- ").append(alternative).append("\n");
                    }
                    markdown.append("\n");
                }
                
                if (!decision.getConsequences().isEmpty()) {
                    markdown.append("**Consequences:**\n\n");
                    for (String consequence : decision.getConsequences()) {
                        markdown.append("- ").append(consequence).append("\n");
                    }
                    markdown.append("\n");
                }
            }
        }
        
        return markdown.toString();
    }
    
    private void appendTaskToMarkdown(StringBuilder markdown, Task task, int level) {
        String indent = "  ".repeat(level);
        String statusEmoji = switch (task.getStatus()) {
            case TODO -> "🔲";
            case IN_PROGRESS -> "🔄";
            case REVIEW -> "👀";
            case DONE -> "✅";
            case BLOCKED -> "🚫";
        };
        
        markdown.append(indent).append("- ").append(statusEmoji).append(" **")
                .append(task.getTitle()).append("**");
                
        if (task.getPriority() == TaskPriority.HIGH || task.getPriority() == TaskPriority.CRITICAL) {
            markdown.append(" (").append(task.getPriority()).append(")");
        }
        
        markdown.append("\n");
        
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            markdown.append(indent).append("  ").append(task.getDescription()).append("\n");
        }
        
        if (!task.getDependencies().isEmpty()) {
            markdown.append(indent).append("  Dependencies: ");
            markdown.append(String.join(", ", task.getDependencies()));
            markdown.append("\n");
        }
        
        if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
            markdown.append("\n");
            for (Task subtask : task.getSubtasks()) {
                appendTaskToMarkdown(markdown, subtask, level + 1);
            }
        }
    }
    
    private String generateGanttChart(Plan plan) {
        StringBuilder gantt = new StringBuilder();
        
        gantt.append("```mermaid\n");
        gantt.append("gantt\n");
        gantt.append("    title ").append(plan.getObjective()).append("\n");
        gantt.append("    dateFormat  YYYY-MM-DD\n");
        gantt.append("    axisFormat %d/%m\n\n");
        
        // For simplicity, use today as the start date
        LocalDateTime today = LocalDateTime.now();
        String startDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        // Flatten all tasks
        List<Task> allTasks = flattenTasks(plan);
        
        // Group tasks by status
        Map<TaskStatus, List<Task>> tasksByStatus = allTasks.stream()
            .collect(Collectors.groupingBy(Task::getStatus));
            
        // Add sections for each status
        for (TaskStatus status : TaskStatus.values()) {
            if (tasksByStatus.containsKey(status)) {
                gantt.append("    section ").append(status).append("\n");
                
                for (Task task : tasksByStatus.get(status)) {
                    String taskId = task.getId().substring(0, 8); // Use shortened ID
                    String taskTitle = task.getTitle().replace(":", "-"); // Escape colons
                    
                    gantt.append("    ").append(taskTitle).append(" :").append(taskId);
                    
                    // Add task duration (1-5 days based on priority)
                    int duration = switch (task.getPriority()) {
                        case LOW -> 1;
                        case MEDIUM -> 2;
                        case HIGH -> 3;
                        case CRITICAL -> 5;
                    };
                    
                    // If task is in progress or done, make it active
                    if (task.getStatus() == TaskStatus.IN_PROGRESS || 
                        task.getStatus() == TaskStatus.REVIEW) {
                        gantt.append(", active, ").append(startDate).append(", ").append(duration).append("d\n");
                    } else if (task.getStatus() == TaskStatus.DONE) {
                        gantt.append(", done, ").append(startDate).append(", ").append(duration).append("d\n");
                    } else {
                        gantt.append(", ").append(startDate).append(", ").append(duration).append("d\n");
                    }
                    
                    // Add dependencies if any
                    if (!task.getDependencies().isEmpty()) {
                        for (String depId : task.getDependencies()) {
                            gantt.append("    ").append(taskTitle).append(" after ")
                                 .append(depId.substring(0, 8)).append("\n");
                        }
                    }
                }
            }
        }
        
        gantt.append("```\n");
        return gantt.toString();
    }
    
    private Flux<ToolOutput> updateTask(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String taskId = (String) arguments.get("taskId");
            String status = (String) arguments.get("status");
            String title = (String) arguments.get("title");
            String description = (String) arguments.get("description");
            String priority = (String) arguments.get("priority");
            
            if (planId == null || taskId == null) {
                throw new IllegalArgumentException("Plan ID and Task ID are required for task update");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            Task task = findTaskById(plan, taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }
            
            // Update task properties
            boolean updated = false;
            
            if (status != null) {
                task.setStatus(TaskStatus.valueOf(status.toUpperCase()));
                updated = true;
            }
            
            if (title != null) {
                task.setTitle(title);
                updated = true;
            }
            
            if (description != null) {
                task.setDescription(description);
                updated = true;
            }
            
            if (priority != null) {
                task.setPriority(TaskPriority.valueOf(priority.toUpperCase()));
                updated = true;
            }
            
            if (updated) {
                task.setUpdatedAt(LocalDateTime.now());
            }
            
            return ToolOutput.builder()
                .type("task_updated")
                .content("Task updated: " + taskId)
                .metadata(Map.of(
                    "plan_id", planId,
                    "task_id", taskId,
                    "title", task.getTitle(),
                    "status", task.getStatus().toString(),
                    "updated_at", task.getUpdatedAt().toString()
                ))
                .build();
        }).flux();
    }
    
    private Flux<ToolOutput> generateReport(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String planId = (String) arguments.get("planId");
            String format = (String) arguments.getOrDefault("format", "markdown");
            
            if (planId == null) {
                throw new IllegalArgumentException("Plan ID is required for report generation");
            }
            
            Plan plan = activePlans.get(planId);
            if (plan == null) {
                throw new IllegalArgumentException("Plan not found: " + planId);
            }
            
            String report;
            if ("json".equalsIgnoreCase(format)) {
                report = objectMapper.writeValueAsString(plan);
            } else if ("gantt".equalsIgnoreCase(format)) {
                report = generateGanttChart(plan);
            } else {
                // Default to markdown
                report = generateMarkdownReport(plan);
            }
            
            return ToolOutput.builder()
                .type("report")
                .content(report)
                .metadata(Map.of(
                    "plan_id", plan.getId(),
                    "format", format,
                    "generated_at", LocalDateTime.now().toString()
                ))
                .build();
        }).flux();
    }
    
    private Task findTaskById(Plan plan, String taskId) {
        // Search in top-level tasks
        for (Task task : plan.getTasks()) {
            if (task.getId().equals(taskId)) {
                return task;
            }
            
            // Search in subtasks
            Task subtask = findTaskInSubtasks(task, taskId);
            if (subtask != null) {
                return subtask;
            }
        }
        
        return null;
    }
    
    private Task findTaskInSubtasks(Task parentTask, String taskId) {
        if (parentTask.getSubtasks() == null || parentTask.getSubtasks().isEmpty()) {
            return null;
        }
        
        for (Task subtask : parentTask.getSubtasks()) {
            if (subtask.getId().equals(taskId)) {
                return subtask;
            }
            
            // Recursive search
            Task found = findTaskInSubtasks(subtask, taskId);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    private List<Task> flattenTasks(Plan plan) {
        List<Task> result = new ArrayList<>();
        
        for (Task task : plan.getTasks()) {
            result.add(task);
            flattenSubtasks(task, result);
        }
        
        return result;
    }
    
    private void flattenSubtasks(Task task, List<Task> result) {
        if (task.getSubtasks() == null || task.getSubtasks().isEmpty()) {
            return;
        }
        
        for (Task subtask : task.getSubtasks()) {
            result.add(subtask);
            flattenSubtasks(subtask, result);
        }
    }
    
    // Data models
    
    @Data
    @Builder
    public static class Plan {
        private String id;
        private String objective;
        private PlanType type;
        private PlanStatus status;
        private List<Task> tasks;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    
    @Data
    @Builder
    public static class Task {
        private String id;
        private String planId;
        private String title;
        private String description;
        private TaskStatus status;
        private TaskPriority priority;
        private List<String> dependencies;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<Task> subtasks;
        private Map<String, String> metadata;
    }
    
    @Data
    @Builder
    public static class TaskTemplate {
        private String id;
        private String name;
        private String description;
        private List<String> defaultSubtasks;
        private Map<String, String> metadata;
    }
    
    @Data
    @Builder
    public static class ArchitectureDecision {
        private String id;
        private String planId;
        private String title;
        private String context;
        private String decision;
        private String rationale;
        private List<String> alternatives;
        private List<String> consequences;
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    public static class TechnicalDebt {
        private String id;
        private String planId;
        private String title;
        private String description;
        private String impact;
        private String remediationPlan;
        private TechnicalDebtSeverity severity;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    
    public enum PlanType {
        FEATURE, ARCHITECTURE, REFACTORING, SPRINT, RELEASE
    }
    
    public enum PlanStatus {
        DRAFT, IN_PROGRESS, COMPLETED, ARCHIVED
    }
    
    public enum TaskStatus {
        TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED
    }
    
    public enum TaskPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum TechnicalDebtSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
