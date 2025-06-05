package com.ai.developer.tools.impl;

import com.ai.developer.tools.ParameterInfo;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
 * Planning Tool for creating and managing project plans, tasks, and related planning activities.
 */
@Slf4j
@Component
public class PlanningTool implements Tool {

    private final Map<String, Plan> activePlans = new ConcurrentHashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");
    private String currentPlanId = null;

    @Override
    public String getName() {
        return "planning_tool";
    }

    @Override
    public String getDescription() {
        return "A tool for creating and managing project plans, tasks, and related planning activities.";
    }

    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("operation", ParameterInfo.builder()
                .name("operation")
                .type("string")
                .description("The planning operation to perform (create_plan, decompose_task, analyze_dependencies, calculate_critical_path, plan_sprint, track_technical_debt, create_architecture_decision, get_plan, get_current_plan, get_task_ids, update_task, add_task, generate_report)")
                .required(true)
                .build());
                
        params.put("objective", ParameterInfo.builder()
                .name("objective")
                .type("string")
                .description("The objective or description for the plan or task")
                .required(false)
                .build());
                
        params.put("type", ParameterInfo.builder()
                .name("type")
                .type("string")
                .description("The type of plan or task (FEATURE, BUG, SPIKE, TECHNICAL_DEBT, DOCUMENTATION)")
                .required(false)
                .build());
                
        params.put("format", ParameterInfo.builder()
                .name("format")
                .type("string")
                .description("The output format (markdown, json, csv)")
                .required(false)
                .build());
                
        params.put("planId", ParameterInfo.builder()
                .name("planId")
                .type("string")
                .description("The ID of the plan to operate on")
                .required(false)
                .build());
                
        params.put("taskId", ParameterInfo.builder()
                .name("taskId")
                .type("string")
                .description("The ID of the task to operate on")
                .required(false)
                .build());
                
        params.put("status", ParameterInfo.builder()
                .name("status")
                .type("string")
                .description("The status to set for a task (TODO, IN_PROGRESS, REVIEW, DONE, BLOCKED)")
                .required(false)
                .build());
                
        params.put("title", ParameterInfo.builder()
                .name("title")
                .type("string")
                .description("The title for a new task")
                .required(false)
                .build());
                
        params.put("description", ParameterInfo.builder()
                .name("description")
                .type("string")
                .description("The description for a new task")
                .required(false)
                .build());
                
        params.put("position", ParameterInfo.builder()
                .name("position")
                .type("string")
                .description("The position to add a new task (START, END, AFTER_TASK)")
                .required(false)
                .build());
                
        params.put("afterTaskId", ParameterInfo.builder()
                .name("afterTaskId")
                .type("string")
                .description("The ID of the task after which to add a new task")
                .required(false)
                .build());
                
        return params;
    }

    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            try {
                String operation = (String) arguments.get("operation");
                
                switch (operation) {
                    case "create_plan":
                        return createPlan(arguments);
                    case "decompose_task":
                        return decomposeTask(arguments);
                    case "analyze_dependencies":
                        return analyzeDependencies(arguments);
                    case "calculate_critical_path":
                        return calculateCriticalPath(arguments);
                    case "plan_sprint":
                        return planSprint(arguments);
                    case "track_technical_debt":
                        return trackTechnicalDebt(arguments);
                    case "create_architecture_decision":
                        return createArchitectureDecision(arguments);
                    case "get_plan":
                        return getPlan(arguments);
                    case "get_current_plan":
                        return getCurrentPlan();
                    case "get_task_ids":
                        return getTaskIds(arguments);
                    case "update_task":
                        return updateTask(arguments);
                    case "add_task":
                        return addTask(arguments);
                    case "generate_report":
                        return generateReport(arguments);
                    default:
                        return ToolOutput.builder()
                                .type("error")
                                .content("Unsupported operation: " + operation)
                                .build();
                }
            } catch (Exception e) {
                log.error("Error executing planning tool", e);
                return ToolOutput.builder()
                        .type("error")
                        .content("Error executing planning tool: " + e.getMessage())
                        .build();
            }
        }).flux();
    }

    private ToolOutput createPlan(Map<String, Object> arguments) {
        String objective = arguments.containsKey("objective") ? (String) arguments.get("objective") : "New Plan";
        String type = arguments.containsKey("type") ? (String) arguments.get("type") : "FEATURE";
        String format = arguments.containsKey("format") ? (String) arguments.get("format") : "markdown";

        // Create a new plan with default tasks
        Plan plan = Plan.builder()
                .id(UUID.randomUUID().toString())
                .objective(objective)
                .type(type)
                .status("DRAFT")
                .created(LocalDateTime.now().format(formatter))
                .updated(LocalDateTime.now().format(formatter))
                .tasks(new ArrayList<>())
                .build();

        // Add default tasks
        plan.getTasks().add(Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Requirements Analysis")
                .description("Task for Requirements Analysis")
                .status("TODO")
                .build());

        plan.getTasks().add(Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Design")
                .description("Task for Design")
                .status("TODO")
                .build());

        plan.getTasks().add(Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Implementation")
                .description("Task for Implementation")
                .status("TODO")
                .build());

        plan.getTasks().add(Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Testing")
                .description("Task for Testing")
                .status("TODO")
                .build());

        plan.getTasks().add(Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Documentation")
                .description("Task for Documentation")
                .status("TODO")
                .build());

        // Store the plan
        activePlans.put(plan.getId(), plan);
        currentPlanId = plan.getId();

        // Return the plan in the requested format
        String result = formatPlanAsMarkdown(plan);

        return ToolOutput.builder()
                .type("stdout")
                .content("Plan created: " + plan.getId())
                .metadata(Map.of("planId", plan.getId()))
                .build();
    }

    private ToolOutput decomposeTask(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String taskId = arguments.containsKey("taskId") ? (String) arguments.get("taskId") : "";

        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }

        Plan plan = activePlans.get(planId);
        Optional<Task> taskOpt = plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();

        if (taskOpt.isEmpty()) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Task not found: " + taskId)
                    .build();
        }

        Task parentTask = taskOpt.get();
        
        // Create subtasks
        List<Task> subtasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Task subtask = Task.builder()
                    .id(UUID.randomUUID().toString())
                    .title(parentTask.getTitle() + " - Subtask " + i)
                    .description("Subtask " + i + " for " + parentTask.getTitle())
                    .status("TODO")
                    .parentId(parentTask.getId())
                    .build();
            subtasks.add(subtask);
        }
        
        // Add subtasks to the plan
        plan.getTasks().addAll(subtasks);
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        return ToolOutput.builder()
                .type("stdout")
                .content("Task decomposed into " + subtasks.size() + " subtasks")
                .build();
    }

    private ToolOutput analyzeDependencies(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Simple dependency analysis - just create some sample dependencies
        StringBuilder result = new StringBuilder("# Dependency Analysis\n\n");
        result.append("## Task Dependencies\n\n");
        
        List<Task> tasks = plan.getTasks();
        for (int i = 0; i < tasks.size() - 1; i++) {
            result.append("- ").append(tasks.get(i).getTitle())
                  .append(" -> ")
                  .append(tasks.get(i + 1).getTitle())
                  .append("\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .build();
    }

    private ToolOutput calculateCriticalPath(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Simple critical path calculation - just return all tasks in sequence
        StringBuilder result = new StringBuilder("# Critical Path\n\n");
        
        List<Task> tasks = plan.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            result.append(i + 1).append(". ").append(tasks.get(i).getTitle()).append("\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .build();
    }

    private ToolOutput planSprint(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Simple sprint planning - just assign tasks to sprints
        StringBuilder result = new StringBuilder("# Sprint Plan\n\n");
        result.append("## Sprint 1\n\n");
        
        List<Task> tasks = plan.getTasks();
        int tasksPerSprint = 2;
        int sprintCount = (int) Math.ceil((double) tasks.size() / tasksPerSprint);
        
        for (int sprint = 0; sprint < sprintCount; sprint++) {
            result.append("## Sprint ").append(sprint + 1).append("\n\n");
            
            for (int i = sprint * tasksPerSprint; i < (sprint + 1) * tasksPerSprint && i < tasks.size(); i++) {
                result.append("- ").append(tasks.get(i).getTitle()).append("\n");
            }
            
            result.append("\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .build();
    }

    private ToolOutput trackTechnicalDebt(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String description = arguments.containsKey("objective") ? (String) arguments.get("objective") : "Technical Debt Item";
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Create a technical debt task
        Task techDebtTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .title("Technical Debt: " + description)
                .description(description)
                .status("TODO")
                .type("TECHNICAL_DEBT")
                .build();
        
        plan.getTasks().add(techDebtTask);
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        return ToolOutput.builder()
                .type("stdout")
                .content("Technical debt item added: " + techDebtTask.getId())
                .build();
    }

    private ToolOutput createArchitectureDecision(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String decision = arguments.containsKey("objective") ? (String) arguments.get("objective") : "Architecture Decision";
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Create an architecture decision record
        StringBuilder result = new StringBuilder("# Architecture Decision Record\n\n");
        result.append("## Decision\n\n").append(decision).append("\n\n");
        result.append("## Status\n\nAccepted\n\n");
        result.append("## Context\n\nContext for this decision...\n\n");
        result.append("## Consequences\n\nConsequences of this decision...\n\n");
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .build();
    }

    private ToolOutput getPlan(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String format = arguments.containsKey("format") ? (String) arguments.get("format") : "markdown";
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        currentPlanId = planId; // Update current plan
        
        // Return the plan in the requested format
        String result = formatPlanAsMarkdown(plan);
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result)
                .build();
    }

    private ToolOutput getCurrentPlan() {
        if (currentPlanId == null || !activePlans.containsKey(currentPlanId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("No current plan available")
                    .build();
        }
        
        Plan plan = activePlans.get(currentPlanId);
        
        // Return the current plan with task IDs
        StringBuilder result = new StringBuilder();
        
        result.append("# ").append(plan.getObjective()).append("\n\n");
        
        result.append("**Plan ID:** ").append(plan.getId()).append("\n");
        result.append("**Type:** ").append(plan.getType()).append("\n");
        result.append("**Status:** ").append(plan.getStatus()).append("\n");
        result.append("**Created:** ").append(plan.getCreated()).append("\n");
        result.append("**Updated:** ").append(plan.getUpdated()).append("\n\n");
        
        result.append("## Tasks\n\n");
        
        for (Task task : plan.getTasks()) {
            result.append("- [ ] **").append(task.getTitle()).append("** (ID: ").append(task.getId()).append(")\n");
            result.append("  ").append(task.getDescription()).append("\n");
            result.append("  Status: ").append(task.getStatus()).append("\n\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .metadata(Map.of("planId", plan.getId()))
                .build();
    }

    private ToolOutput getTaskIds(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Create a mapping of task titles to IDs
        StringBuilder result = new StringBuilder("# Task IDs for Plan: " + plan.getObjective() + "\n\n");
        result.append("Plan ID: ").append(plan.getId()).append("\n\n");
        result.append("## Task Title to ID Mapping\n\n");
        
        for (Task task : plan.getTasks()) {
            result.append("- **").append(task.getTitle()).append("**: `").append(task.getId()).append("`\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .metadata(Map.of("planId", plan.getId()))
                .build();
    }

    private ToolOutput updateTask(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String taskId = arguments.containsKey("taskId") ? (String) arguments.get("taskId") : "";
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        Optional<Task> taskOpt = plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst();
        
        if (taskOpt.isEmpty()) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Task not found: " + taskId)
                    .build();
        }
        
        Task task = taskOpt.get();
        
        // Update task properties if provided
        if (arguments.containsKey("title")) {
            task.setTitle((String) arguments.get("title"));
        }
        
        if (arguments.containsKey("description")) {
            task.setDescription((String) arguments.get("description"));
        }
        
        if (arguments.containsKey("status")) {
            task.setStatus((String) arguments.get("status"));
        }
        
        if (arguments.containsKey("priority")) {
            task.setPriority((String) arguments.get("priority"));
        }
        
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        return ToolOutput.builder()
                .type("stdout")
                .content("Task updated: " + taskId + " (" + task.getTitle() + ")")
                .metadata(Map.of("taskId", taskId, "planId", planId))
                .build();
    }

    private ToolOutput addTask(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String title = arguments.containsKey("title") ? (String) arguments.get("title") : "New Task";
        String description = arguments.containsKey("description") ? (String) arguments.get("description") : "Description for " + title;
        String position = arguments.containsKey("position") ? (String) arguments.get("position") : "END";
        String afterTaskId = arguments.containsKey("afterTaskId") ? (String) arguments.get("afterTaskId") : null;
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Create new task
        Task newTask = Task.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .status("TODO")
                .build();
        
        // Add task at the specified position
        if ("START".equalsIgnoreCase(position)) {
            plan.getTasks().add(0, newTask);
        } else if ("AFTER_TASK".equalsIgnoreCase(position) && afterTaskId != null) {
            int index = -1;
            for (int i = 0; i < plan.getTasks().size(); i++) {
                if (plan.getTasks().get(i).getId().equals(afterTaskId)) {
                    index = i;
                    break;
                }
            }
            
            if (index >= 0) {
                plan.getTasks().add(index + 1, newTask);
            } else {
                plan.getTasks().add(newTask); // Add to end if task not found
            }
        } else {
            // Default to END
            plan.getTasks().add(newTask);
        }
        
        plan.setUpdated(LocalDateTime.now().format(formatter));
        
        return ToolOutput.builder()
                .type("stdout")
                .content("Task added: " + newTask.getId() + " (" + newTask.getTitle() + ")")
                .metadata(Map.of("taskId", newTask.getId(), "planId", planId))
                .build();
    }

    private ToolOutput generateReport(Map<String, Object> arguments) {
        String planId = arguments.containsKey("planId") ? (String) arguments.get("planId") : currentPlanId;
        String format = arguments.containsKey("format") ? (String) arguments.get("format") : "markdown";
        
        if (planId == null || !activePlans.containsKey(planId)) {
            return ToolOutput.builder()
                    .type("error")
                    .content("Plan not found: " + planId)
                    .build();
        }
        
        Plan plan = activePlans.get(planId);
        
        // Generate a report based on the plan
        StringBuilder result = new StringBuilder("# Project Report: " + plan.getObjective() + "\n\n");
        
        result.append("## Overview\n\n");
        result.append("- **Status**: ").append(plan.getStatus()).append("\n");
        result.append("- **Type**: ").append(plan.getType()).append("\n");
        result.append("- **Created**: ").append(plan.getCreated()).append("\n");
        result.append("- **Last Updated**: ").append(plan.getUpdated()).append("\n\n");
        
        result.append("## Task Summary\n\n");
        
        Map<String, Long> statusCounts = plan.getTasks().stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            result.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append("\n");
        }
        
        result.append("\n## Tasks\n\n");
        
        for (Task task : plan.getTasks()) {
            result.append("### ").append(task.getTitle()).append(" (ID: ").append(task.getId()).append(")\n\n");
            result.append("- **Status**: ").append(task.getStatus()).append("\n");
            result.append("- **Description**: ").append(task.getDescription()).append("\n\n");
        }
        
        return ToolOutput.builder()
                .type("stdout")
                .content(result.toString())
                .build();
    }

    private String formatPlanAsMarkdown(Plan plan) {
        StringBuilder markdown = new StringBuilder();
        
        markdown.append("# ").append(plan.getObjective()).append("\n\n");
        
        markdown.append("**Plan ID:** ").append(plan.getId()).append("\n");
        markdown.append("**Type:** ").append(plan.getType()).append("\n");
        markdown.append("**Status:** ").append(plan.getStatus()).append("\n");
        markdown.append("**Created:** ").append(plan.getCreated()).append("\n");
        markdown.append("**Updated:** ").append(plan.getUpdated()).append("\n\n");
        
        markdown.append("## Tasks\n\n");
        
        for (Task task : plan.getTasks()) {
            markdown.append("- [ ] **").append(task.getTitle()).append("** (ID: ").append(task.getId()).append(")\n");
            markdown.append("  ").append(task.getDescription()).append("\n");
        }
        
        return markdown.toString();
    }

    @PreDestroy
    public void cleanup() {
        // Cleanup resources if needed
        activePlans.clear();
    }

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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Task {
        private String id;
        private String title;
        private String description;
        private String status;
        private String priority;
        private String type;
        private String parentId;
        private List<String> dependencies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TaskTemplate {
        private String name;
        private String description;
        private List<String> subtasks;
    }
}
