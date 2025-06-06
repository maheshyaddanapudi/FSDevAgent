# Component Mapping for Autonomous Agent Integration

## Overview

This document maps the reference supporting services to the existing FSDevAgent architecture, identifying how each component will be integrated, what existing code will be extended, and what new components need to be created.

## Component Mapping

### 1. EnhancedToolOutputWebSocketHandler

**Existing Component**: `com.ai.developer.config.ToolOutputWebSocketHandler`

**Integration Approach**:
- Extend the existing `ToolOutputWebSocketHandler` class
- Add new methods for broadcasting agent state, phase transitions, and tool events
- Preserve all existing functionality for backward compatibility

**New Methods to Add**:
- `broadcastToolUsage(String sessionId, String toolName, Map<String, Object> args, String toolId)`
- `broadcastToolResult(String sessionId, String toolName, String result, String toolId)`
- `broadcastAgentState(String sessionId, String phase, int progress, String currentTask)`
- `broadcastPhaseTransition(String sessionId, String fromPhase, String toPhase)`
- `broadcastError(String sessionId, String error, String context)`

**Code Reuse**:
- Reuse the session management code from the existing handler
- Reuse the message formatting and sending logic

### 2. DevelopmentPhaseManager

**New Component**: `com.ai.developer.service.DevelopmentPhaseManager`

**Integration Approach**:
- Create as a new service with Spring `@Service` annotation
- Implement phase tracking, transitions, and completion criteria
- Add session-based phase management

**Dependencies**:
- `AgentPromptService` for phase-specific prompts
- `EnhancedToolOutputWebSocketHandler` for phase transition broadcasts

**Model Classes**:
- `DevelopmentPhase` enum (already exists in `AgentPromptService`)
- `PhaseTracker` for tracking phase state
- `PhaseMetric` for recording phase metrics
- `PhaseTransitionResult` for reporting phase transitions
- `PhaseCompletionStatus` for checking phase completion
- `SessionPhaseMetrics` for reporting session metrics

### 3. TaskMemoryService

**New Component**: `com.ai.developer.service.TaskMemoryService`

**Integration Approach**:
- Create as a new service with Spring `@Service` annotation
- Implement file-based persistence for task memory
- Add methods for context retrieval and update

**Dependencies**:
- `ObjectMapper` for JSON serialization/deserialization

**Model Classes**:
- `TaskMemory` for storing task memory
- `TaskStep` for recording task steps
- `TaskCheckpoint` for creating checkpoints
- `TaskMemoryUpdate` for updating task memory
- `PatternMatch` for finding similar patterns

### 4. ErrorRecoveryService

**New Component**: `com.ai.developer.service.ErrorRecoveryService`

**Integration Approach**:
- Create as a new service with Spring `@Service` annotation
- Implement error pattern recognition and recovery strategies
- Add learning from successful recoveries

**Dependencies**:
- `TaskMemoryService` for storing error patterns
- `EnhancedToolOutputWebSocketHandler` for error broadcasts

**Model Classes**:
- `ErrorOccurrence` for recording error occurrences
- `ErrorPattern` for identifying error patterns
- `RecoveryStrategy` for defining recovery strategies
- `RecoveryAction` for executing recovery actions
- `ErrorAnalysis` for analyzing errors
- `RecoveryResult` for reporting recovery results
- `ErrorStatistics` for reporting error statistics

### 5. AutonomousTaskSequencer

**New Component**: `com.ai.developer.service.AutonomousTaskSequencer`

**Integration Approach**:
- Create as a new service with Spring `@Service` annotation
- Implement task sequencing with dependency management
- Add task status tracking and progress reporting

**Dependencies**:
- `TaskExecutorService` for executing tasks
- `EnhancedToolOutputWebSocketHandler` for progress updates

**Model Classes**:
- `TaskType` enum for categorizing tasks
- `TaskStatus` enum for tracking task status
- `SequenceStatus` enum for tracking sequence status
- `TaskDefinition` for defining tasks
- `SequencedTask` for tracking task execution
- `TaskSequence` for managing task sequences
- `SequenceProgress` for reporting sequence progress
- `TaskDependencyGraph` for managing task dependencies

### 6. ProjectTemplateManager

**New Component**: `com.ai.developer.service.ProjectTemplateManager`

**Integration Approach**:
- Create as a new service with Spring `@Service` annotation
- Implement template storage and retrieval
- Add code generation from templates

**Dependencies**:
- `CodeGenerationService` for code generation

**Model Classes**:
- `ProjectTemplate` for defining project templates
- `TemplateCriteria` for searching templates
- `Dependency` for managing dependencies
- `ConfigOption` for configuring templates
- `ProjectStructure` for generating project structures
- `GeneratedFile` for generating files
- `TemplateMatch` for matching templates
- `ProjectType` enum for categorizing projects

## Integration with Existing Services

### EnhancedChatService

**Existing Component**: `com.ai.developer.service.EnhancedChatService`

**Integration Points**:
- Add autonomous execution loop
- Integrate with `DevelopmentPhaseManager` for phase transitions
- Use `TaskMemoryService` for context persistence
- Handle errors with `ErrorRecoveryService`
- Sequence tasks with `AutonomousTaskSequencer`

**Code Changes**:
- Update `executeAutonomousAgent` method to use the new supporting services
- Add phase transition logic
- Enhance error handling
- Add task sequencing

### AgentPromptService

**Existing Component**: `com.ai.developer.service.AgentPromptService`

**Integration Points**:
- Add phase-specific prompts
- Include context from `TaskMemoryService`
- Add error recovery prompts

**Code Changes**:
- Update `generateContinuationPrompt` to include phase-specific instructions
- Add methods for generating error recovery prompts
- Include task memory context in prompts

### TaskExecutorService

**Existing Component**: `com.ai.developer.service.TaskExecutorService`

**Integration Points**:
- Execute tool sequences from `AutonomousTaskSequencer`
- Report results to `TaskMemoryService`
- Handle errors with `ErrorRecoveryService`

**Code Changes**:
- Update `executeToolSequence` to report results to `TaskMemoryService`
- Add error handling with `ErrorRecoveryService`
- Integrate with `AutonomousTaskSequencer` for task execution

### CodeGenerationService

**Existing Component**: `com.ai.developer.service.CodeGenerationService`

**Integration Points**:
- Use templates from `ProjectTemplateManager`
- Generate code based on project requirements

**Code Changes**:
- Update code generation methods to use templates from `ProjectTemplateManager`
- Add methods for generating project structures

## Dependency Injection

```java
@Configuration
public class AutonomousAgentConfig {

    @Bean
    public DevelopmentPhaseManager developmentPhaseManager() {
        return new DevelopmentPhaseManager();
    }

    @Bean
    public TaskMemoryService taskMemoryService() {
        return new TaskMemoryService();
    }

    @Bean
    public ErrorRecoveryService errorRecoveryService() {
        return new ErrorRecoveryService();
    }

    @Bean
    public AutonomousTaskSequencer autonomousTaskSequencer() {
        return new AutonomousTaskSequencer();
    }

    @Bean
    public ProjectTemplateManager projectTemplateManager() {
        return new ProjectTemplateManager();
    }
}
```

## Configuration Properties

```properties
# Autonomous Agent Configuration
autonomous.agent.enabled=true
autonomous.agent.memory.dir=/tmp/ai-developer-agent/memory
autonomous.agent.templates.dir=/tmp/ai-developer-agent/templates
autonomous.agent.error.recovery.enabled=true
autonomous.agent.task.sequencer.enabled=true
```

## Conclusion

This mapping provides a clear path for integrating the reference supporting services into the FSDevAgent codebase. By following this mapping, we can ensure that each component is properly adapted to fit the existing architecture while enabling seamless autonomous operation.
