# Multi-Turn Conversation Support Design

This document outlines the design for enhancing the multi-turn conversation support in the FSDevAgent project, focusing on implementing a true ReAct (Reason-Act-Observe) loop for autonomous agent functionality.

## 1. Core ReAct Loop Architecture

### 1.1 ReAct Loop Components

The enhanced multi-turn conversation support will follow the ReAct paradigm with these core components:

1. **Reason**: LLM processes context and plans next action
   - Enhanced prompt engineering to focus on execution
   - Context-aware continuation prompts
   - Task decomposition guidance

2. **Act**: Execute tool calls or generate responses
   - Real-time tool use detection and execution
   - Streaming response parsing
   - Parallel tool execution where appropriate

3. **Observe**: Update state and check completion
   - Comprehensive state management
   - Progress tracking
   - Phase transitions
   - Error detection and recovery

### 1.2 Flow Diagram

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│                 │     │                 │     │                 │
│     REASON      │────▶│      ACT        │────▶│    OBSERVE      │
│                 │     │                 │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         ▲                                              │
         │                                              │
         └──────────────────────────────────────────────┘
```

## 2. Enhanced Streaming Response Parsing

### 2.1 Real-Time Tool Use Detection

The current implementation already has a pattern for detecting tool use blocks, but it will be enhanced to:

- Process LLM responses in real-time as they stream
- Detect and parse tool use blocks as soon as they appear
- Execute tools immediately upon detection
- Continue parsing the remaining response

### 2.2 Implementation Approach

```java
llmProvider.streamResponse(prompt, context)
    .doOnNext(chunk -> {
        responseBuilder.append(chunk);
        
        // Check for complete tool use blocks in the accumulated response
        Matcher toolMatcher = TOOL_USE_PATTERN.matcher(responseBuilder.toString());
        while (toolMatcher.find()) {
            String toolUseJson = toolMatcher.group(1);
            if (!isToolUseProcessed(toolUseJson, processedToolUses)) {
                ToolUseBlock toolUse = parseToolUseBlock(toolUseJson);
                if (toolUse != null) {
                    processedToolUses.add(toolUse);
                    
                    // Execute tool immediately
                    executeToolUse(sessionId, toolUse, agentState)
                        .subscribe(output -> {
                            // Process tool output
                            // Update context with tool result
                            // Continue the conversation
                        });
                }
            }
        }
        
        // Stream non-tool chunks to UI
        if (!chunk.contains("<tool_use>") && !chunk.contains("</tool_use>")) {
            sink.tryEmitNext(ChatResponse.builder()
                    .sessionId(sessionId)
                    .role("assistant")
                    .message(chunk)
                    .timestamp(Instant.now())
                    .build());
        }
    })
```

## 3. Context Persistence and State Management

### 3.1 Enhanced AgentState

The AgentState class will be enhanced to track:

- Current development phase
- Task progress
- Completed and pending tasks
- Execution history
- Error states and recovery attempts
- Checkpoints for long-running tasks

### 3.2 TaskMemory Integration

A new TaskMemory service will be integrated to provide:

- Persistent memory across iterations
- Pattern recognition for similar tasks
- Learning from past executions
- Progress calculation based on task completion

### 3.3 Implementation Approach

```java
public class TaskMemory {
    private String taskId;
    private String objective;
    private List<String> completedSteps;
    private List<String> pendingSteps;
    private Map<String, Object> metadata;
    private int progressPercentage;
    private DevelopmentPhase currentPhase;
    private List<Checkpoint> checkpoints;
    
    // Methods for state management
    public void addCompletedStep(String step) { ... }
    public void addPendingStep(String step) { ... }
    public void updateProgress() { ... }
    public void createCheckpoint() { ... }
    public void restoreFromCheckpoint(String checkpointId) { ... }
}
```

## 4. Enhanced Prompt Engineering

### 4.1 Execution-Focused Prompts

The AgentPromptService will be enhanced with execution-focused prompts:

- System prompts that emphasize tool use
- Continuation prompts that guide next actions
- Tool result prompts that provide context for tool outputs
- Error recovery prompts that suggest alternative approaches

### 4.2 Implementation Approach

```java
public class AgentPromptService {
    // Existing methods
    
    /**
     * Generate a continuation prompt that encourages tool use
     */
    public String generateContinuationPrompt(String lastAction, String currentState) {
        return String.format("""
            <continuation_context>
            Last completed action: %s
            Current project state: %s
            
            CRITICAL DIRECTIVE: You MUST continue autonomous execution by using tools!
            
            Based on what you just did:
            1. Identify the immediate next step
            2. Execute it using the appropriate tool
            3. Use <tool_use> blocks - DO NOT just describe what to do
            
            Common next actions:
            - If you created a directory → Create files in it
            - If you created a file → Write code to it
            - If you wrote code → Test it
            - If tests pass → Move to next component
            - If tests fail → Fix the code
            
            Example tool usage:
            <tool_use>
            {
              "name": "file_system",
              "args": {
                "operation": "write",
                "path": "src/components/TodoList.tsx",
                "content": "import React from 'react';\\n\\nconst TodoList: React.FC = () => {\\n  return <div>Todo List</div>;\\n};\\n\\nexport default TodoList;"
              }
            }
            </tool_use>
            
            NOW EXECUTE THE NEXT STEP! Don't wait for permission!
            </continuation_context>
            """, lastAction, currentState);
    }
    
    /**
     * Generate a tool result prompt that provides context for tool outputs
     */
    public String generateToolResultPrompt(String toolName, String result, String status) {
        return String.format("""
            <tool_result>
            Tool: %s
            Status: %s
            Result: %s
            
            Based on this result, determine the next appropriate action.
            If successful, proceed to the next logical step.
            If failed, diagnose the issue and try an alternative approach.
            
            ALWAYS use tools to make progress - do not just describe what to do next.
            </tool_result>
            """, toolName, status, result);
    }
    
    /**
     * Generate an error recovery prompt
     */
    public String generateErrorRecoveryPrompt(String error, String context) {
        return String.format("""
            <error_recovery>
            Error encountered: %s
            Context: %s
            
            Please analyze this error and determine a recovery strategy.
            Consider:
            1. Is this a syntax error?
            2. Is this a logical error?
            3. Is this a configuration issue?
            4. Is this a dependency issue?
            
            Then, implement a solution using the appropriate tool.
            </error_recovery>
            """, error, context);
    }
}
```

## 5. Error Recovery and Self-Correction

### 5.1 Error Detection and Analysis

The system will be enhanced to:

- Detect errors in tool execution
- Analyze error patterns
- Categorize errors by type
- Suggest recovery strategies

### 5.2 Recovery Strategies

Different recovery strategies will be implemented:

- Retry with modified parameters
- Alternative approach selection
- Dependency resolution
- Configuration correction
- User assistance request (as a last resort)

### 5.3 Implementation Approach

```java
public class ErrorRecoveryService {
    /**
     * Analyze an error and suggest recovery strategies
     */
    public RecoveryStrategy analyzeError(String error, String context) {
        // Analyze error pattern
        if (error.contains("syntax error") || error.contains("unexpected token")) {
            return new SyntaxErrorRecovery(error, context);
        } else if (error.contains("not found") || error.contains("undefined")) {
            return new ReferenceErrorRecovery(error, context);
        } else if (error.contains("permission denied")) {
            return new PermissionErrorRecovery(error, context);
        } else {
            return new GenericErrorRecovery(error, context);
        }
    }
    
    /**
     * Apply a recovery strategy
     */
    public Mono<ToolOutput> applyRecoveryStrategy(RecoveryStrategy strategy, AgentState agentState) {
        return strategy.apply(agentState);
    }
}
```

## 6. Integration with Supporting Services

### 6.1 EnhancedToolOutputWebSocketHandler

This service will be enhanced to provide real-time updates to the UI:

- Tool execution events
- Progress updates
- Phase transitions
- Error notifications

### 6.2 DevelopmentPhaseManager

This service will manage development phase transitions:

- Phase detection
- Phase completion criteria
- Phase-specific tool recommendations
- Progress tracking within phases

### 6.3 TaskMemoryService

This service will provide persistent memory across iterations:

- Task history storage
- Pattern recognition
- Learning from past executions
- Progress calculation

## 7. Implementation Plan

### 7.1 Phase 1: Core ReAct Loop Enhancement

1. Update EnhancedChatService with true ReAct loop
2. Implement real-time tool use detection and execution
3. Enhance AgentPromptService with execution-focused prompts

### 7.2 Phase 2: State Management and Context Persistence

1. Enhance AgentState with comprehensive state tracking
2. Implement TaskMemory for persistent memory
3. Add checkpointing for long-running tasks

### 7.3 Phase 3: Error Recovery and Self-Correction

1. Implement ErrorRecoveryService
2. Add error analysis and categorization
3. Implement recovery strategies

### 7.4 Phase 4: Supporting Services Integration

1. Implement EnhancedToolOutputWebSocketHandler
2. Implement DevelopmentPhaseManager
3. Implement TaskMemoryService

### 7.5 Phase 5: UI Updates and Testing

1. Update UI for tool output visualization
2. Add progress indicators
3. Add phase transition visualization
4. Comprehensive testing of autonomous agent functionality

## 8. Conclusion

This design document outlines the approach for enhancing the multi-turn conversation support in the FSDevAgent project. By implementing a true ReAct loop, improving streaming tool execution, enhancing state management, and adding robust error recovery, the autonomous agent will be able to execute complex tasks without user intervention.

The implementation will follow a phased approach, starting with the core ReAct loop enhancement and progressing through state management, error recovery, and supporting services integration. Each phase will be tested thoroughly before proceeding to the next.
