# FSDevAgent - Agent Framework Architecture

## Overview

This document outlines the architecture for the FSDevAgent autonomous agent framework, designed to support multi-turn conversations, context memory, and advanced UI features. The architecture is based on reference implementations and best practices from modern AI agent systems.

## Core Components

### 1. Agent State Management

The agent framework maintains state across conversation turns through the `AgentState` class:

```java
private static class AgentState {
    // Task context
    private String currentObjective;
    private DevelopmentPhase currentPhase = DevelopmentPhase.ANALYSIS;
    private List<String> completedTasks = new ArrayList<>();
    private List<String> pendingTasks = new ArrayList<>();
    private Map<String, String> learnedPatterns = new HashMap<>();
    private int iterationCount = 0;
    private boolean shouldContinue = true;
    private String lastAction = "";
    private ProjectContext projectContext;
    
    // Multi-turn conversation support
    private boolean waitingForUserInput = false;
    private String pendingQuestion = null;
    private ConversationMode mode = ConversationMode.AUTONOMOUS;
    private List<String> conversationHistory = new ArrayList<>();
    
    // Methods for state manipulation and conversion
    public TaskMemory toTaskMemory() { ... }
    private int calculateProgress() { ... }
}
```

### 2. Conversation Modes

The framework supports multiple conversation modes to accommodate different interaction patterns:

```java
private enum ConversationMode {
    AUTONOMOUS,      // Full autonomous operation
    INTERACTIVE,     // Asks for confirmation at key points
    CONVERSATIONAL,  // Traditional back-and-forth
    GUIDED           // User guides each step
}
```

### 3. Agent Prompt Service

The `AgentPromptService` manages sophisticated system prompts for the agent:

- `generateSystemPrompt(ProjectContext)`: Creates the core system prompt
- `generatePhasePrompt(DevelopmentPhase)`: Generates phase-specific prompts
- `generateContinuationPrompt(String, String)`: Creates context-aware continuation prompts
- `generateToolResultPrompt(String, String, boolean)`: Handles tool execution results
- `generateMemoryPrompt(TaskMemory)`: Generates memory augmentation prompts

### 4. Enhanced Chat Service

The `ChatService` orchestrates the agent's operation:

- `processMessage(ChatRequest)`: Handles user messages with multi-turn support
- `analyzeUserIntent(String, AgentState)`: Determines user intent from messages
- `handleUserIntent(String, ChatContext, AgentState, UserIntent, String)`: Routes to appropriate handlers
- `executeAgentLoop(String, ChatContext, AgentState)`: Manages the autonomous agent loop
- `executeAgentIteration(String, ChatContext, AgentState, Sinks.Many<ChatResponse>)`: Executes a single iteration

### 5. User Intent Analysis

The framework analyzes user messages to determine intent:

```java
private UserIntent analyzeUserIntent(String message, AgentState agentState) {
    String lowerMessage = message.toLowerCase();
    
    // Check for mode switches
    if (lowerMessage.contains("stop") || lowerMessage.contains("pause") || lowerMessage.contains("wait")) {
        return UserIntent.PAUSE_EXECUTION;
    }
    
    if (lowerMessage.contains("continue") || lowerMessage.contains("proceed") || lowerMessage.contains("go ahead")) {
        return UserIntent.CONTINUE_EXECUTION;
    }
    
    // Additional intent detection logic...
    
    return UserIntent.GENERAL_CONVERSATION;
}
```

### 6. Enhanced UI Components

The UI layer features advanced components for rich interaction:

- `EnhancedMessage`: Renders messages with collapsible sections
- `CollapsibleSection`: Manages expandable/collapsible content areas
- Syntax highlighting for code and command outputs
- Streaming indicators for real-time feedback

## Integration Points

### 1. Tool Execution Pipeline

The agent framework integrates with the existing tool execution pipeline:

```java
private void processToolUseAndContinue(String sessionId, ChatContext context, AgentState agentState, 
                                     String completeResponse, Sinks.Many<ChatResponse> sink) {
    // Extract tool use blocks
    List<ToolUseBlock> toolUseBlocks = extractToolUseBlocks(completeResponse);
    
    for (ToolUseBlock toolUseBlock : toolUseBlocks) {
        // Execute tool and handle results
        Tool tool = toolRegistry.getTool(toolUseBlock.getName());
        if (tool != null) {
            // Execute tool with workspace context
            String workspacePath = agentState.projectContext != null ? 
                agentState.projectContext.getProjectPath() : 
                "/tmp/ai-developer-agent/" + sessionId;
                
            ToolResult result = tool.execute(toolUseBlock.getArgs(), workspacePath);
            
            // Process result and continue agent loop
            // ...
        }
    }
}
```

### 2. WebSocket Communication

Real-time updates are streamed via WebSockets:

```java
private void streamToolOutput(String sessionId, String toolOutput) {
    webSocketHandler.broadcastToolOutput(sessionId, toolOutput);
}
```

### 3. Frontend Message Rendering

The frontend renders messages with collapsible tool calls:

```jsx
{message.toolCalls && message.toolCalls.map((toolCall, idx) => (
  <CollapsibleSection
    key={`tool-${idx}`}
    id={`tool-${idx}`}
    title={`Using ${toolCall.name}`}
    icon={<Terminal className="w-4 h-4" />}
    expanded={expandedSections[`tool-${idx}`]}
    onToggle={() => toggleSection(`tool-${idx}`)}
    status={toolCall.status}
    preview={!expandedSections[`tool-${idx}`] ? JSON.stringify(toolCall.args).substring(0, 80) + '...' : ''}
  >
    {/* Tool call content */}
  </CollapsibleSection>
))}
```

## Data Flow

1. User sends a message via the chat interface
2. `ChatService.processMessage()` receives the request
3. User intent is analyzed and routed to appropriate handler
4. For autonomous operation, the agent loop executes:
   - LLM generates a response with potential tool calls
   - Tool calls are extracted and executed
   - Results are streamed back to the frontend
   - The agent continues iterating until completion
5. UI renders the response with collapsible tool calls and results

## Implementation Strategy

1. First implement the core agent state management and prompt service
2. Refactor the chat service to support multi-turn conversations
3. Integrate with the existing tool execution pipeline
4. Implement the enhanced UI components
5. Test end-to-end with complex multi-turn scenarios

## Technical Constraints

- Must maintain compatibility with Claude API
- Must support real-time streaming of responses
- Must handle tool execution in a non-blocking manner
- Must preserve conversation context across multiple turns
