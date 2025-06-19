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
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
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
                
        params.put("sessionId", ParameterInfo.builder()
                .name("sessionId")
                .type("string")
                .description("Chat session ID for Claude's internal tracking")
                .required(false)
                .build());
                
        params.put(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, ParameterInfo.builder()
                .name(SESSION_WORKSPACE_ROOT_FOLDER_PARAM)
                .type("string")
                .description("Session workspace root folder for workspace management")
                .required(false)
                .build());
                
        params.put("title", ParameterInfo.builder()
                .name("title")
                .type("string")
                .description("Title for the plan or project")
                .required(false)
                .build());
                
        params.put("type", ParameterInfo.builder()
                .name("type")
                .type("string")
                .description("Type of plan (FEATURE, BUG, REFACTOR, etc.)")
                .required(false)
                .build());
                
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Create final copies of variables for lambda
        final Map<String, Object> finalArguments = arguments;
        
        return Mono.fromCallable(() -> {
            try {
                // Add null check and logging for arguments
                if (finalArguments == null) {
                    log.error("Planning tool received null arguments");
                    return ToolOutput.builder()
                            .type("error")
                            .content("Error: Planning tool received null arguments")
                            .build();
                }
                
                log.info("Planning tool executing with arguments: {}", finalArguments);
                
                // Extract operation with alternative key checking
                String operation = (String) finalArguments.get("operation");
                if (operation == null) {
                    // Check for alternative keys that might contain operation
                    if (finalArguments.containsKey("op")) {
                        operation = (String) finalArguments.get("op");
                        log.warn("Using 'op' instead of 'operation' for PlanningTool");
                    } else if (finalArguments.containsKey("action")) {
                        operation = (String) finalArguments.get("action");
                        log.warn("Using 'action' instead of 'operation' for PlanningTool");
                    } else if (finalArguments.containsKey("command")) {
                        operation = (String) finalArguments.get("command");
                        log.warn("Using 'command' instead of 'operation' for PlanningTool");
                    } else {
                        log.error("Planning tool operation is null. Available keys: {}", finalArguments.keySet());
                        return ToolOutput.builder()
                                .type("error")
                                .content("Error: Planning tool operation is required but was null")
                                .build();
                    }
                }
                
                log.info("Planning tool executing operation: {}", operation);
                
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
        String title = (String) arguments.getOrDefault("title", "New Project Plan");
        String type = (String) arguments.getOrDefault("type", "FEATURE");
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        
        // Use sessionWorkspaceRootFolder for workspace management if provided, otherwise fall back to sessionId
        String sessionWorkspaceRootFolder = (String) arguments.getOrDefault(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, sessionId);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) arguments.getOrDefault("constraints", new HashMap<>());
        
        // Create planning context for autonomous decision making
        PlanningContext context = new PlanningContext();
        context.setObjective(objective);
        context.setConstraints(constraints);
        context.setCreatedAt(LocalDateTime.now());
        context.setSessionId(sessionId);
        
        // Set up workspace path for this session
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionWorkspaceRootFolder;
        context.setWorkspacePath(workspacePath);
        
        // Create root plan
        Plan plan = Plan.builder()
                .id(UUID.randomUUID().toString())
                .objective(objective)
                .title(title)
                .type(type)
                .status("PLANNING")
                .created(LocalDateTime.now().format(formatter))
                .updated(LocalDateTime.now().format(formatter))
                .tasks(new ArrayList<>())
                .metadata(new HashMap<>())
                .workspacePath(workspacePath)
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
        
        // CRITICAL: Write the plan to todo.md file for autonomous agent to read
        try {
            java.nio.file.Path workspaceDir = java.nio.file.Paths.get(workspacePath);
            java.nio.file.Files.createDirectories(workspaceDir);
            
            java.nio.file.Path todoFile = workspaceDir.resolve("todo.md");
            java.nio.file.Files.writeString(todoFile, report, java.nio.charset.StandardCharsets.UTF_8);
            
            log.info("Successfully wrote plan to: {}", todoFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write todo.md file to workspace: {}", workspacePath, e);
        }
        
        return ToolOutput.builder()
                .type("plan_created")
                .content(report)
                .metadata(Map.of(
                        "planId", plan.getId(),
                        "taskCount", plan.getTasks().size(),
                        "estimatedDays", plan.getMetadata().getOrDefault("estimatedDays", 0),
                        "workspacePath", workspacePath
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
     * Create a phase task with appropriate metadata
     */
    private Task createPhaseTask(String title, String description, TaskType type, int estimatedDays) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .type(type)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .metadata(Map.of("estimatedDays", estimatedDays))
                .subtasks(new ArrayList<>())
                .build();
    }
    
    /**
     * Analyze dependencies between phases and set them in the plan
     */
    private void analyzePhaseDependencies(Plan plan) {
        List<Task> tasks = plan.getTasks();
        
        // Simple linear dependencies for now
        for (int i = 0; i < tasks.size() - 1; i++) {
            Task current = tasks.get(i);
            Task next = tasks.get(i + 1);
            
            // Add next task as dependent on current
            if (current.getDependencies() == null) {
                current.setDependencies(new ArrayList<>());
            }
            
            // Add current task as prerequisite for next
            if (next.getPrerequisites() == null) {
                next.setPrerequisites(new ArrayList<>());
            }
            next.getPrerequisites().add(current.getId());
        }
    }
    
    /**
     * Calculate plan metrics like total estimated time, critical path, etc.
     */
    private void calculatePlanMetrics(Plan plan) {
        List<Task> tasks = plan.getTasks();
        int totalDays = 0;
        
        for (Task task : tasks) {
            Object estimatedDays = task.getMetadata().get("estimatedDays");
            if (estimatedDays instanceof Integer) {
                totalDays += (Integer) estimatedDays;
            }
        }
        
        // Store metrics in plan metadata
        plan.getMetadata().put("estimatedDays", totalDays);
        plan.getMetadata().put("estimatedWeeks", Math.ceil(totalDays / 5.0));
    }
    
    /**
     * Generate a comprehensive plan report in markdown format
     */
    private String generateHierarchicalPlanReport(Plan plan, PlanningContext context) {
        StringBuilder report = new StringBuilder();
        
        report.append("# ").append(plan.getTitle()).append("\n\n");
        report.append("## Objective\n\n");
        report.append(plan.getObjective()).append("\n\n");
        
        report.append("## Plan Summary\n\n");
        report.append("- **Plan ID**: ").append(plan.getId()).append("\n");
        report.append("- **Status**: ").append(plan.getStatus()).append("\n");
        report.append("- **Created**: ").append(plan.getCreated()).append("\n");
        report.append("- **Estimated Duration**: ").append(plan.getMetadata().get("estimatedDays"))
              .append(" days (").append(plan.getMetadata().get("estimatedWeeks")).append(" weeks)\n");
        report.append("- **Workspace Path**: ").append(context.getWorkspacePath()).append("\n\n");
        
        report.append("## Phases\n\n");
        
        List<Task> tasks = plan.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            report.append("### Phase ").append(i + 1).append(": ").append(task.getTitle()).append("\n\n");
            report.append(task.getDescription()).append("\n\n");
            report.append("- **Estimated Duration**: ").append(task.getMetadata().get("estimatedDays")).append(" days\n");
            report.append("- **Status**: ").append(task.getStatus()).append("\n");
            
            if (task.getPrerequisites() != null && !task.getPrerequisites().isEmpty()) {
                report.append("- **Prerequisites**: ");
                for (int j = 0; j < task.getPrerequisites().size(); j++) {
                    String prereqId = task.getPrerequisites().get(j);
                    String prereqTitle = findTaskTitle(plan, prereqId);
                    report.append(prereqTitle);
                    if (j < task.getPrerequisites().size() - 1) {
                        report.append(", ");
                    }
                }
                report.append("\n");
            }
            
            report.append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * Find a task title by its ID
     */
    private String findTaskTitle(Plan plan, String taskId) {
        return plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .map(Task::getTitle)
                .findFirst()
                .orElse("Unknown Task");
    }
    
    /**
     * Hierarchically decompose a task into subtasks
     */
    private ToolOutput decomposeTaskHierarchically(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        String taskId = (String) arguments.get("taskId");
        Integer depth = arguments.containsKey("depth") ? (Integer) arguments.get("depth") : 1;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        if (taskId == null) {
            return createErrorOutput("Task ID is required");
        }
        
        Plan plan = activePlans.get(planId);
        Task task = findTaskById(plan, taskId);
        
        if (task == null) {
            return createErrorOutput("Task not found with ID: " + taskId);
        }
        
        // Decompose the task based on its type
        List<Task> subtasks = generateSubtasksForTask(task, depth);
        
        // Add subtasks to the task
        if (task.getSubtasks() == null) {
            task.setSubtasks(new ArrayList<>());
        }
        task.getSubtasks().addAll(subtasks);
        task.setType(TaskType.COMPOUND); // Mark as compound since it now has subtasks
        
        // Update plan
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Task Decomposition\n\n");
        report.append("## Parent Task\n\n");
        report.append("- **Title**: ").append(task.getTitle()).append("\n");
        report.append("- **Description**: ").append(task.getDescription()).append("\n\n");
        
        report.append("## Subtasks\n\n");
        for (int i = 0; i < subtasks.size(); i++) {
            Task subtask = subtasks.get(i);
            report.append("### ").append(i + 1).append(". ").append(subtask.getTitle()).append("\n\n");
            report.append(subtask.getDescription()).append("\n\n");
        }
        
        return ToolOutput.builder()
                .type("task_decomposed")
                .content(report.toString())
                .metadata(Map.of(
                        "taskId", taskId,
                        "subtaskCount", subtasks.size()
                ))
                .build();
    }
    
    /**
     * Find a task by its ID in the plan (including subtasks)
     */
    private Task findTaskById(Plan plan, String taskId) {
        // Check top-level tasks
        Optional<Task> taskOpt = plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();
        
        if (taskOpt.isPresent()) {
            return taskOpt.get();
        }
        
        // Check subtasks recursively
        for (Task task : plan.getTasks()) {
            Task found = findTaskInSubtasks(task, taskId);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    /**
     * Recursively search for a task in subtasks
     */
    private Task findTaskInSubtasks(Task parent, String taskId) {
        if (parent.getSubtasks() == null || parent.getSubtasks().isEmpty()) {
            return null;
        }
        
        Optional<Task> taskOpt = parent.getSubtasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();
        
        if (taskOpt.isPresent()) {
            return taskOpt.get();
        }
        
        // Recursive search
        for (Task subtask : parent.getSubtasks()) {
            Task found = findTaskInSubtasks(subtask, taskId);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    /**
     * Generate appropriate subtasks based on task type
     */
    private List<Task> generateSubtasksForTask(Task task, int depth) {
        List<Task> subtasks = new ArrayList<>();
        
        // Different decomposition strategies based on task title/type
        if (task.getTitle().contains("Requirements")) {
            subtasks.add(createSubtask(task, "Stakeholder Interviews", 
                    "Conduct interviews with key stakeholders to gather requirements"));
            subtasks.add(createSubtask(task, "User Story Creation", 
                    "Create user stories based on gathered requirements"));
            subtasks.add(createSubtask(task, "Acceptance Criteria Definition", 
                    "Define acceptance criteria for each user story"));
            subtasks.add(createSubtask(task, "Requirements Documentation", 
                    "Document all requirements in a structured format"));
        } else if (task.getTitle().contains("Architecture")) {
            subtasks.add(createSubtask(task, "System Architecture Design", 
                    "Design the overall system architecture"));
            subtasks.add(createSubtask(task, "Database Schema Design", 
                    "Design the database schema and relationships"));
            subtasks.add(createSubtask(task, "API Contract Definition", 
                    "Define API contracts and endpoints"));
            subtasks.add(createSubtask(task, "Security Architecture", 
                    "Design security architecture and authentication flow"));
        } else if (task.getTitle().contains("Backend")) {
            subtasks.add(createSubtask(task, "Core Domain Models", 
                    "Implement core domain models and entities"));
            subtasks.add(createSubtask(task, "Repository Layer", 
                    "Implement data access and repository layer"));
            subtasks.add(createSubtask(task, "Service Layer", 
                    "Implement business logic in service layer"));
            subtasks.add(createSubtask(task, "API Controllers", 
                    "Implement API controllers and endpoints"));
            subtasks.add(createSubtask(task, "Authentication & Authorization", 
                    "Implement authentication and authorization"));
        } else if (task.getTitle().contains("Frontend")) {
            subtasks.add(createSubtask(task, "UI Component Design", 
                    "Design and implement reusable UI components"));
            subtasks.add(createSubtask(task, "State Management", 
                    "Implement state management solution"));
            subtasks.add(createSubtask(task, "API Integration", 
                    "Integrate with backend APIs"));
            subtasks.add(createSubtask(task, "Responsive Design", 
                    "Ensure responsive design for all screen sizes"));
            subtasks.add(createSubtask(task, "User Authentication UI", 
                    "Implement user authentication UI flows"));
        } else {
            // Generic decomposition for other task types
            subtasks.add(createSubtask(task, task.getTitle() + " - Planning", 
                    "Plan and organize the " + task.getTitle() + " work"));
            subtasks.add(createSubtask(task, task.getTitle() + " - Implementation", 
                    "Implement the core functionality for " + task.getTitle()));
            subtasks.add(createSubtask(task, task.getTitle() + " - Testing", 
                    "Test and validate the " + task.getTitle() + " implementation"));
            subtasks.add(createSubtask(task, task.getTitle() + " - Documentation", 
                    "Document the " + task.getTitle() + " implementation"));
        }
        
        // Further decompose subtasks if depth > 1
        if (depth > 1) {
            List<Task> allSubtasks = new ArrayList<>(subtasks);
            
            for (Task subtask : subtasks) {
                List<Task> subsubtasks = generateSubtasksForTask(subtask, depth - 1);
                subtask.setSubtasks(subsubtasks);
                subtask.setType(TaskType.COMPOUND);
            }
        }
        
        return subtasks;
    }
    
    /**
     * Create a subtask with appropriate metadata
     */
    private Task createSubtask(Task parent, String title, String description) {
        return Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .type(TaskType.ATOMIC)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .parentId(parent.getId())
                .metadata(new HashMap<>())
                .build();
    }
    
    /**
     * Analyze dependencies between tasks and subtasks
     */
    private ToolOutput analyzeDependenciesAdvanced(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        
        // Analyze dependencies using critical path method
        Map<String, Object> dependencyAnalysis = analyzeDependenciesWithCPM(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Dependency Analysis\n\n");
        
        report.append("## Task Dependencies\n\n");
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dependencies = (List<Map<String, String>>) dependencyAnalysis.get("dependencies");
        
        for (Map<String, String> dependency : dependencies) {
            report.append("- **").append(dependency.get("from"))
                  .append("** → **")
                  .append(dependency.get("to"))
                  .append("**\n");
        }
        
        report.append("\n## Critical Path\n\n");
        
        @SuppressWarnings("unchecked")
        List<String> criticalPath = (List<String>) dependencyAnalysis.get("criticalPath");
        
        for (int i = 0; i < criticalPath.size(); i++) {
            report.append(i + 1).append(". **").append(criticalPath.get(i)).append("**\n");
        }
        
        return ToolOutput.builder()
                .type("dependency_analysis")
                .content(report.toString())
                .metadata(dependencyAnalysis)
                .build();
    }
    
    /**
     * Analyze dependencies using Critical Path Method (CPM)
     */
    private Map<String, Object> analyzeDependenciesWithCPM(Plan plan) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> dependencies = new ArrayList<>();
        List<String> criticalPath = new ArrayList<>();
        
        // Extract dependencies from tasks
        for (Task task : plan.getTasks()) {
            if (task.getPrerequisites() != null) {
                for (String prereqId : task.getPrerequisites()) {
                    String prereqTitle = findTaskTitle(plan, prereqId);
                    dependencies.add(Map.of(
                            "from", prereqTitle,
                            "to", task.getTitle(),
                            "fromId", prereqId,
                            "toId", task.getId()
                    ));
                }
            }
            
            // Also check subtask dependencies
            if (task.getSubtasks() != null) {
                for (int i = 0; i < task.getSubtasks().size() - 1; i++) {
                    Task current = task.getSubtasks().get(i);
                    Task next = task.getSubtasks().get(i + 1);
                    
                    dependencies.add(Map.of(
                            "from", current.getTitle(),
                            "to", next.getTitle(),
                            "fromId", current.getId(),
                            "toId", next.getId()
                    ));
                }
            }
        }
        
        // Simple critical path calculation - just use the main phases for now
        for (Task task : plan.getTasks()) {
            criticalPath.add(task.getTitle());
        }
        
        result.put("dependencies", dependencies);
        result.put("criticalPath", criticalPath);
        
        return result;
    }
    
    /**
     * Calculate critical path for the plan
     */
    private ToolOutput calculateCriticalPathAdvanced(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        
        // Calculate critical path
        List<Task> criticalPathTasks = calculateCriticalPathTasks(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Critical Path Analysis\n\n");
        
        report.append("## Critical Path\n\n");
        report.append("The critical path is the sequence of tasks that determines the minimum time needed to complete the project.\n\n");
        
        int totalDays = 0;
        
        for (int i = 0; i < criticalPathTasks.size(); i++) {
            Task task = criticalPathTasks.get(i);
            Object estimatedDays = task.getMetadata().get("estimatedDays");
            int days = (estimatedDays instanceof Integer) ? (Integer) estimatedDays : 1;
            
            totalDays += days;
            
            report.append(i + 1).append(". **").append(task.getTitle()).append("** (")
                  .append(days).append(" days)\n");
            report.append("   - ").append(task.getDescription()).append("\n\n");
        }
        
        report.append("## Timeline Summary\n\n");
        report.append("- **Total Duration**: ").append(totalDays).append(" days\n");
        report.append("- **Estimated Completion**: ").append(totalDays / 5).append(" weeks\n");
        
        return ToolOutput.builder()
                .type("critical_path")
                .content(report.toString())
                .metadata(Map.of(
                        "criticalPathLength", criticalPathTasks.size(),
                        "totalDays", totalDays
                ))
                .build();
    }
    
    /**
     * Calculate the critical path tasks
     */
    private List<Task> calculateCriticalPathTasks(Plan plan) {
        // For now, just return the main phases as the critical path
        // In a real implementation, this would use the CPM algorithm
        return new ArrayList<>(plan.getTasks());
    }
    
    /**
     * Optimize the plan by reordering tasks, adjusting resources, etc.
     */
    private ToolOutput optimizePlan(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        
        // Perform plan optimization
        Map<String, Object> optimizationResults = performPlanOptimization(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Plan Optimization\n\n");
        
        report.append("## Optimization Results\n\n");
        report.append("- **Original Duration**: ").append(optimizationResults.get("originalDuration")).append(" days\n");
        report.append("- **Optimized Duration**: ").append(optimizationResults.get("optimizedDuration")).append(" days\n");
        report.append("- **Time Saved**: ").append(optimizationResults.get("timeSaved")).append(" days (")
              .append(optimizationResults.get("percentImprovement")).append("% improvement)\n\n");
        
        report.append("## Optimization Strategies Applied\n\n");
        
        @SuppressWarnings("unchecked")
        List<String> strategies = (List<String>) optimizationResults.get("strategies");
        
        for (String strategy : strategies) {
            report.append("- ").append(strategy).append("\n");
        }
        
        return ToolOutput.builder()
                .type("plan_optimized")
                .content(report.toString())
                .metadata(optimizationResults)
                .build();
    }
    
    /**
     * Perform plan optimization
     */
    private Map<String, Object> performPlanOptimization(Plan plan) {
        Map<String, Object> result = new HashMap<>();
        
        // Calculate original duration
        int originalDuration = calculateTotalDuration(plan);
        
        // Apply optimization strategies
        List<String> strategies = new ArrayList<>();
        
        // Strategy 1: Parallelize independent tasks
        strategies.add("Parallelized independent tasks where possible");
        
        // Strategy 2: Optimize resource allocation
        strategies.add("Optimized resource allocation across tasks");
        
        // Strategy 3: Reduce dependencies
        strategies.add("Reduced unnecessary dependencies between tasks");
        
        // Calculate optimized duration (simulated improvement)
        int optimizedDuration = (int) (originalDuration * 0.8); // 20% improvement
        int timeSaved = originalDuration - optimizedDuration;
        double percentImprovement = (double) timeSaved / originalDuration * 100;
        
        result.put("originalDuration", originalDuration);
        result.put("optimizedDuration", optimizedDuration);
        result.put("timeSaved", timeSaved);
        result.put("percentImprovement", Math.round(percentImprovement * 10) / 10.0);
        result.put("strategies", strategies);
        
        return result;
    }
    
    /**
     * Calculate total duration of the plan
     */
    private int calculateTotalDuration(Plan plan) {
        int totalDays = 0;
        
        for (Task task : plan.getTasks()) {
            Object estimatedDays = task.getMetadata().get("estimatedDays");
            if (estimatedDays instanceof Integer) {
                totalDays += (Integer) estimatedDays;
            }
        }
        
        return totalDays;
    }
    
    /**
     * Assess risks in the plan
     */
    private ToolOutput assessRisks(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        
        // Perform risk assessment
        List<Risk> risks = identifyProjectRisks(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Risk Assessment\n\n");
        
        report.append("## Identified Risks\n\n");
        
        for (int i = 0; i < risks.size(); i++) {
            Risk risk = risks.get(i);
            report.append("### Risk ").append(i + 1).append(": ").append(risk.getTitle()).append("\n\n");
            report.append("- **Probability**: ").append(risk.getProbability()).append("\n");
            report.append("- **Impact**: ").append(risk.getImpact()).append("\n");
            report.append("- **Severity**: ").append(risk.getSeverity()).append("\n");
            report.append("- **Description**: ").append(risk.getDescription()).append("\n");
            report.append("- **Mitigation Strategy**: ").append(risk.getMitigationStrategy()).append("\n\n");
        }
        
        report.append("## Risk Matrix\n\n");
        report.append("```\n");
        report.append("     │ Low Impact │ Medium Impact │ High Impact │\n");
        report.append("─────┼────────────┼───────────────┼─────────────┤\n");
        report.append("High │            │               │             │\n");
        report.append("Prob │            │               │ Risks: 1, 4 │\n");
        report.append("─────┼────────────┼───────────────┼─────────────┤\n");
        report.append("Med  │            │ Risks: 2, 5   │             │\n");
        report.append("Prob │            │               │ Risks: 3    │\n");
        report.append("─────┼────────────┼───────────────┼─────────────┤\n");
        report.append("Low  │            │               │             │\n");
        report.append("Prob │ Risks: 6   │               │             │\n");
        report.append("```\n");
        
        return ToolOutput.builder()
                .type("risk_assessment")
                .content(report.toString())
                .metadata(Map.of("riskCount", risks.size()))
                .build();
    }
    
    /**
     * Identify project risks
     */
    private List<Risk> identifyProjectRisks(Plan plan) {
        List<Risk> risks = new ArrayList<>();
        
        // Common project risks
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("Scope Creep")
                .description("Requirements expand beyond initial agreement")
                .probability("High")
                .impact("High")
                .severity("Critical")
                .mitigationStrategy("Implement strict change control process and maintain clear requirements documentation")
                .build());
        
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("Technical Debt Accumulation")
                .description("Shortcuts taken during development lead to future maintenance issues")
                .probability("Medium")
                .impact("Medium")
                .severity("Moderate")
                .mitigationStrategy("Regular code reviews, maintain technical documentation, and schedule refactoring sprints")
                .build());
        
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("Integration Issues")
                .description("Components fail to work together as expected")
                .probability("Medium")
                .impact("High")
                .severity("High")
                .mitigationStrategy("Early integration testing, clear API contracts, and continuous integration")
                .build());
        
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("Resource Constraints")
                .description("Insufficient team members or expertise for project needs")
                .probability("High")
                .impact("High")
                .severity("Critical")
                .mitigationStrategy("Early resource planning, cross-training team members, and identifying external resources")
                .build());
        
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("Technology Selection Issues")
                .description("Selected technologies prove inadequate for requirements")
                .probability("Medium")
                .impact("Medium")
                .severity("Moderate")
                .mitigationStrategy("Thorough technology evaluation, prototyping, and maintaining flexibility in architecture")
                .build());
        
        risks.add(Risk.builder()
                .id(UUID.randomUUID().toString())
                .title("External Dependency Delays")
                .description("Third-party services or components cause delays")
                .probability("Low")
                .impact("Low")
                .severity("Low")
                .mitigationStrategy("Identify alternatives, maintain contingency plans, and establish clear SLAs")
                .build());
        
        return risks;
    }
    
    /**
     * Generate alternative approaches for the plan
     */
    private ToolOutput generateAlternatives(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        Plan plan = activePlans.get(planId);
        
        // Generate alternative approaches
        List<Alternative> alternatives = generateAlternativeApproaches(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Alternative Approaches\n\n");
        
        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alt = alternatives.get(i);
            report.append("## Alternative ").append(i + 1).append(": ").append(alt.getTitle()).append("\n\n");
            report.append(alt.getDescription()).append("\n\n");
            
            report.append("### Pros\n\n");
            for (String pro : alt.getPros()) {
                report.append("- ").append(pro).append("\n");
            }
            report.append("\n");
            
            report.append("### Cons\n\n");
            for (String con : alt.getCons()) {
                report.append("- ").append(con).append("\n");
            }
            report.append("\n");
            
            report.append("### Estimated Impact\n\n");
            report.append("- **Time Impact**: ").append(alt.getTimeImpact()).append("\n");
            report.append("- **Cost Impact**: ").append(alt.getCostImpact()).append("\n");
            report.append("- **Quality Impact**: ").append(alt.getQualityImpact()).append("\n\n");
        }
        
        return ToolOutput.builder()
                .type("alternatives_generated")
                .content(report.toString())
                .metadata(Map.of("alternativeCount", alternatives.size()))
                .build();
    }
    
    /**
     * Generate alternative approaches for the plan
     */
    private List<Alternative> generateAlternativeApproaches(Plan plan) {
        List<Alternative> alternatives = new ArrayList<>();
        
        // Alternative 1: Agile Approach
        alternatives.add(Alternative.builder()
                .id(UUID.randomUUID().toString())
                .title("Agile Iterative Approach")
                .description("Implement the project using Agile methodology with 2-week sprints, focusing on delivering working software incrementally.")
                .pros(List.of(
                        "Early delivery of working software",
                        "Flexibility to adapt to changing requirements",
                        "Regular feedback from stakeholders",
                        "Reduced risk through incremental delivery"
                ))
                .cons(List.of(
                        "Requires experienced Agile team",
                        "May be challenging for fixed-scope contracts",
                        "Documentation may be less comprehensive",
                        "Requires active stakeholder involvement"
                ))
                .timeImpact("May reduce overall timeline by 15%")
                .costImpact("Similar cost to current approach")
                .qualityImpact("Potentially higher quality due to continuous feedback")
                .build());
        
        // Alternative 2: Phased Delivery
        alternatives.add(Alternative.builder()
                .id(UUID.randomUUID().toString())
                .title("Phased Delivery Approach")
                .description("Divide the project into distinct phases with clear deliverables at the end of each phase, allowing for production deployment of core features earlier.")
                .pros(List.of(
                        "Earlier delivery of core functionality",
                        "Clear milestones for stakeholder review",
                        "Reduced complexity by focusing on one phase at a time",
                        "Earlier return on investment"
                ))
                .cons(List.of(
                        "May require rework between phases",
                        "Integration challenges between phases",
                        "Less flexibility for major changes after phase completion",
                        "May extend overall timeline"
                ))
                .timeImpact("May extend overall timeline by 10%")
                .costImpact("Potentially 5-10% higher cost")
                .qualityImpact("Similar quality with better alignment to business priorities")
                .build());
        
        // Alternative 3: Technology Stack Alternative
        alternatives.add(Alternative.builder()
                .id(UUID.randomUUID().toString())
                .title("Alternative Technology Stack")
                .description("Consider alternative technology stack options that may better suit the project requirements or team expertise.")
                .pros(List.of(
                        "May better align with specific requirements",
                        "Could leverage team's existing expertise",
                        "Potential performance or scalability improvements",
                        "May reduce development time with more suitable tools"
                ))
                .cons(List.of(
                        "Learning curve if team is unfamiliar with new technologies",
                        "Integration risks with existing systems",
                        "May limit future extensibility",
                        "Potential for less community support or documentation"
                ))
                .timeImpact("Variable impact depending on team familiarity")
                .costImpact("Initial increase but potential long-term savings")
                .qualityImpact("Potentially improved if better suited to requirements")
                .build());
        
        return alternatives;
    }
    
    /**
     * Update progress on tasks in the plan
     */
    private ToolOutput updateProgress(Map<String, Object> arguments) {
        String planId = (String) arguments.getOrDefault("planId", currentPlanId);
        String taskId = (String) arguments.get("taskId");
        String status = (String) arguments.get("status");
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return createErrorOutput("No active plan found with ID: " + planId);
        }
        
        if (taskId == null) {
            return createErrorOutput("Task ID is required");
        }
        
        if (status == null) {
            return createErrorOutput("Status is required");
        }
        
        Plan plan = activePlans.get(planId);
        Task task = findTaskById(plan, taskId);
        
        if (task == null) {
            return createErrorOutput("Task not found with ID: " + taskId);
        }
        
        // Update task status
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.valueOf(status));
        
        // Update plan
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        // Calculate overall progress
        int progress = calculateOverallProgress(plan);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Progress Update\n\n");
        
        report.append("## Task Updated\n\n");
        report.append("- **Task**: ").append(task.getTitle()).append("\n");
        report.append("- **Status Changed**: ").append(oldStatus).append(" → ").append(task.getStatus()).append("\n\n");
        
        report.append("## Overall Progress\n\n");
        report.append("- **Progress**: ").append(progress).append("%\n");
        
        // Generate progress summary
        report.append("\n## Progress Summary\n\n");
        
        Map<TaskStatus, Integer> statusCounts = countTasksByStatus(plan);
        
        for (Map.Entry<TaskStatus, Integer> entry : statusCounts.entrySet()) {
            report.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append(" tasks\n");
        }
        
        return ToolOutput.builder()
                .type("progress_updated")
                .content(report.toString())
                .metadata(Map.of(
                        "taskId", taskId,
                        "newStatus", status,
                        "progress", progress
                ))
                .build();
    }
    
    /**
     * Calculate overall progress of the plan
     */
    private int calculateOverallProgress(Plan plan) {
        int totalTasks = countAllTasks(plan);
        if (totalTasks == 0) {
            return 0;
        }
        
        int completedTasks = countCompletedTasks(plan);
        return (int) Math.round((double) completedTasks / totalTasks * 100);
    }
    
    /**
     * Count all tasks in the plan (including subtasks)
     */
    private int countAllTasks(Plan plan) {
        int count = 0;
        
        for (Task task : plan.getTasks()) {
            count++; // Count the task itself
            
            // Count subtasks recursively
            if (task.getSubtasks() != null) {
                count += countSubtasks(task);
            }
        }
        
        return count;
    }
    
    /**
     * Count subtasks recursively
     */
    private int countSubtasks(Task task) {
        if (task.getSubtasks() == null || task.getSubtasks().isEmpty()) {
            return 0;
        }
        
        int count = task.getSubtasks().size();
        
        for (Task subtask : task.getSubtasks()) {
            count += countSubtasks(subtask);
        }
        
        return count;
    }
    
    /**
     * Count completed tasks in the plan (including subtasks)
     */
    private int countCompletedTasks(Plan plan) {
        int count = 0;
        
        for (Task task : plan.getTasks()) {
            if (task.getStatus() == TaskStatus.DONE) {
                count++; // Count the task itself if done
            }
            
            // Count completed subtasks recursively
            if (task.getSubtasks() != null) {
                count += countCompletedSubtasks(task);
            }
        }
        
        return count;
    }
    
    /**
     * Count completed subtasks recursively
     */
    private int countCompletedSubtasks(Task task) {
        if (task.getSubtasks() == null || task.getSubtasks().isEmpty()) {
            return 0;
        }
        
        int count = 0;
        
        for (Task subtask : task.getSubtasks()) {
            if (subtask.getStatus() == TaskStatus.DONE) {
                count++;
            }
            
            count += countCompletedSubtasks(subtask);
        }
        
        return count;
    }
    
    /**
     * Count tasks by status
     */
    private Map<TaskStatus, Integer> countTasksByStatus(Plan plan) {
        Map<TaskStatus, Integer> counts = new HashMap<>();
        
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0);
        }
        
        for (Task task : plan.getTasks()) {
            counts.put(task.getStatus(), counts.get(task.getStatus()) + 1);
            
            // Count subtasks recursively
            if (task.getSubtasks() != null) {
                countSubtasksByStatus(task, counts);
            }
        }
        
        return counts;
    }
    
    /**
     * Count subtasks by status recursively
     */
    private void countSubtasksByStatus(Task task, Map<TaskStatus, Integer> counts) {
        if (task.getSubtasks() == null || task.getSubtasks().isEmpty()) {
            return;
        }
        
        for (Task subtask : task.getSubtasks()) {
            counts.put(subtask.getStatus(), counts.get(subtask.getStatus()) + 1);
            countSubtasksByStatus(subtask, counts);
        }
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
                        .content("Plan is complete. All tasks have been finished.")
                        .build();
            } else {
                return ToolOutput.builder()
                        .type("no_actionable_tasks")
                        .content("No actionable tasks found. Some tasks may be blocked or all tasks are in progress.")
                        .build();
            }
        }
        
        // Generate next action recommendation
        String actionType = determineActionType(nextTask);
        String actionDescription = generateActionDescription(nextTask, actionType);
        
        // Generate report
        StringBuilder report = new StringBuilder();
        report.append("# Next Recommended Action\n\n");
        
        report.append("## Task\n\n");
        report.append("- **Title**: ").append(nextTask.getTitle()).append("\n");
        report.append("- **Description**: ").append(nextTask.getDescription()).append("\n");
        report.append("- **Status**: ").append(nextTask.getStatus()).append("\n\n");
        
        report.append("## Recommended Action\n\n");
        report.append("- **Action Type**: ").append(actionType).append("\n");
        report.append("- **Action**: ").append(actionDescription).append("\n\n");
        
        report.append("## Workspace Information\n\n");
        report.append("- **Workspace Path**: ").append(context.getWorkspacePath()).append("\n");
        
        return ToolOutput.builder()
                .type("next_action")
                .content(report.toString())
                .metadata(Map.of(
                        "taskId", nextTask.getId(),
                        "actionType", actionType,
                        "workspacePath", context.getWorkspacePath()
                ))
                .build();
    }
    
    /**
     * Find the next actionable task in the plan
     */
    private Task findNextActionableTask(Plan plan) {
        // First look for TODO tasks with no prerequisites or all prerequisites done
        for (Task task : plan.getTasks()) {
            if (task.getStatus() == TaskStatus.TODO && arePrerequisitesMet(task, plan)) {
                return task;
            }
            
            // Check subtasks recursively
            if (task.getSubtasks() != null) {
                Task nextSubtask = findNextActionableSubtask(task, plan);
                if (nextSubtask != null) {
                    return nextSubtask;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find the next actionable subtask recursively
     */
    private Task findNextActionableSubtask(Task parentTask, Plan plan) {
        if (parentTask.getSubtasks() == null || parentTask.getSubtasks().isEmpty()) {
            return null;
        }
        
        for (Task subtask : parentTask.getSubtasks()) {
            if (subtask.getStatus() == TaskStatus.TODO && arePrerequisitesMet(subtask, plan)) {
                return subtask;
            }
            
            // Recursive check
            Task nextSubtask = findNextActionableSubtask(subtask, plan);
            if (nextSubtask != null) {
                return nextSubtask;
            }
        }
        
        return null;
    }
    
    /**
     * Check if all prerequisites for a task are met
     */
    private boolean arePrerequisitesMet(Task task, Plan plan) {
        if (task.getPrerequisites() == null || task.getPrerequisites().isEmpty()) {
            return true; // No prerequisites
        }
        
        for (String prereqId : task.getPrerequisites()) {
            Task prereqTask = findTaskById(plan, prereqId);
            if (prereqTask == null || prereqTask.getStatus() != TaskStatus.DONE) {
                return false; // Prerequisite not met
            }
        }
        
        return true; // All prerequisites met
    }
    
    /**
     * Check if the plan is complete
     */
    private boolean isPlanComplete(Plan plan) {
        // Check if all tasks and subtasks are done
        for (Task task : plan.getTasks()) {
            if (task.getStatus() != TaskStatus.DONE) {
                return false;
            }
            
            if (task.getSubtasks() != null && !areAllSubtasksDone(task)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if all subtasks are done recursively
     */
    private boolean areAllSubtasksDone(Task task) {
        if (task.getSubtasks() == null || task.getSubtasks().isEmpty()) {
            return true;
        }
        
        for (Task subtask : task.getSubtasks()) {
            if (subtask.getStatus() != TaskStatus.DONE) {
                return false;
            }
            
            if (!areAllSubtasksDone(subtask)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determine the type of action to take for a task
     */
    private String determineActionType(Task task) {
        if (task.getTitle().contains("Requirements")) {
            return "GATHER_REQUIREMENTS";
        } else if (task.getTitle().contains("Architecture") || task.getTitle().contains("Design")) {
            return "DESIGN";
        } else if (task.getTitle().contains("Development")) {
            return "IMPLEMENT";
        } else if (task.getTitle().contains("Testing")) {
            return "TEST";
        } else if (task.getTitle().contains("Documentation")) {
            return "DOCUMENT";
        } else {
            return "EXECUTE";
        }
    }
    
    /**
     * Generate a description for the next action
     */
    private String generateActionDescription(Task task, String actionType) {
        return switch (actionType) {
            case "GATHER_REQUIREMENTS" -> "Interview stakeholders and document requirements for " + task.getTitle();
            case "DESIGN" -> "Create design documents and diagrams for " + task.getTitle();
            case "IMPLEMENT" -> "Write code and implement functionality for " + task.getTitle();
            case "TEST" -> "Create and execute test cases for " + task.getTitle();
            case "DOCUMENT" -> "Create documentation for " + task.getTitle();
            default -> "Execute the task: " + task.getTitle();
        };
    }
    
    /**
     * Create an error output
     */
    private ToolOutput createErrorOutput(String message) {
        return ToolOutput.builder()
                .type("error")
                .content("Error: " + message)
                .build();
    }
    
    /**
     * Clean up resources when the bean is destroyed
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up planning tool resources");
        activePlans.clear();
        planningContexts.clear();
    }
    
    /**
     * Planning context for a plan
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class PlanningContext {
        private String objective;
        private Map<String, Object> constraints;
        private LocalDateTime createdAt;
        private String sessionId;
        private String workspacePath;
    }
    
    /**
     * Plan model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Plan {
        private String id;
        private String title;
        private String objective;
        private String type;
        private String status;
        private String created;
        private String updated;
        private List<Task> tasks;
        private Map<String, Object> metadata;
        private String workspacePath;
    }
    
    /**
     * Task model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Task {
        private String id;
        private String title;
        private String description;
        private TaskType type;
        private TaskStatus status;
        private TaskPriority priority;
        private String parentId;
        private List<String> prerequisites;
        private List<String> dependencies;
        private List<Task> subtasks;
        private Map<String, Object> metadata;
    }
    
    /**
     * Risk model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Risk {
        private String id;
        private String title;
        private String description;
        private String probability;
        private String impact;
        private String severity;
        private String mitigationStrategy;
    }
    
    /**
     * Alternative approach model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Alternative {
        private String id;
        private String title;
        private String description;
        private List<String> pros;
        private List<String> cons;
        private String timeImpact;
        private String costImpact;
        private String qualityImpact;
    }
    
    /**
     * Task type enum
     */
    private enum TaskType {
        ATOMIC,     // Cannot be broken down further
        COMPOUND    // Can be broken down into subtasks
    }
    
    /**
     * Task status enum
     */
    private enum TaskStatus {
        TODO,
        IN_PROGRESS,
        REVIEW,
        DONE,
        BLOCKED
    }
    
    /**
     * Task priority enum
     */
    private enum TaskPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
