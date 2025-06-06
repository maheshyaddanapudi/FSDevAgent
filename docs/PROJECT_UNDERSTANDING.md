# FSDevAgent Project Understanding

## Overview

FSDevAgent is an AI-powered developer agent platform designed to assist with coding, debugging, and using various development tools. The system integrates with Large Language Models (LLMs) like Claude to provide an interactive, tool-augmented development experience through a web interface.

The project follows a client-server architecture with a React frontend and Spring Boot backend, enabling real-time communication through WebSockets and RESTful APIs. The agent can execute terminal commands, perform git operations, create development plans, and potentially execute many other development-related tasks.

## Architecture

### High-Level Components

1. **Frontend**: React-based web application
   - User interface for interacting with the AI agent
   - Real-time message streaming via WebSockets
   - Tool execution visualization
   - Terminal emulator for command output display

2. **Backend**: Spring Boot Java application
   - REST API endpoints for session management
   - WebSocket handlers for real-time communication
   - Tool registry and implementation
   - LLM provider integration (Claude)
   - Workspace management for session isolation

3. **LLM Integration**: Claude API
   - Tool use capability for executing commands
   - Streaming responses for real-time feedback
   - Context management for multi-turn conversations

4. **Tool Framework**:
   - Extensible tool registry
   - Terminal command execution
   - Git operations
   - Planning capabilities
   - Workspace-aware operations

### Component Interactions

```
┌─────────────┐      WebSocket      ┌─────────────┐       HTTP       ┌─────────────┐
│             │◄─────────────────►  │             │◄───────────────► │             │
│   Frontend  │      REST API       │   Backend   │                  │  Claude API │
│             │◄─────────────────►  │             │                  │             │
└─────────────┘                     └─────────────┘                  └─────────────┘
                                          ▲
                                          │
                                          ▼
                                    ┌─────────────┐
                                    │    Tools    │
                                    │  Execution  │
                                    └─────────────┘
```

## Technical Stack

### Frontend
- **Framework**: React
- **State Management**: Zustand
- **UI Components**: Custom components
- **Real-time Communication**: WebSocket
- **Code Editor**: Monaco Editor
- **Terminal Emulator**: xterm.js
- **Build Tool**: npm/yarn

### Backend
- **Framework**: Spring Boot
- **Language**: Java 17
- **API**: REST + WebSockets
- **JSON Processing**: Jackson
- **Reactive Programming**: Project Reactor
- **Build Tool**: Maven

### External Services
- **LLM Provider**: Claude API (Anthropic)

## Key Features

1. **Multi-turn Conversation**: Support for ongoing conversations with context preservation
2. **Tool Use Integration**: Ability to detect and execute tool calls from LLM responses
3. **Real-time Streaming**: Streaming of LLM responses and tool outputs
4. **Session Management**: Creation and management of isolated user sessions
5. **Workspace Isolation**: Session-specific workspaces for file operations
6. **Planning Capabilities**: Autonomous planning for complex development tasks
7. **Terminal Integration**: Execution and visualization of terminal commands
8. **Git Operations**: Support for version control operations

## Project Structure

### Frontend Structure

```
frontend/
├── public/              # Static assets
├── src/
│   ├── components/      # React components
│   │   ├── MessageList.js       # Chat message display
│   │   └── ...
│   ├── hooks/           # Custom React hooks
│   │   ├── useChatStore.js      # Chat state management
│   │   ├── useWebSocket.js      # WebSocket connection
│   │   └── ...
│   ├── App.js           # Main application component
│   └── index.js         # Application entry point
├── .env                 # Environment variables
├── .env.development     # Development environment variables
└── package.json         # Dependencies and scripts
```

### Backend Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/ai/developer/
│   │   │   ├── config/           # Configuration classes
│   │   │   │   └── ToolOutputWebSocketHandler.java  # WebSocket handler
│   │   │   ├── controller/       # REST controllers
│   │   │   ├── llm/              # LLM integration
│   │   │   │   ├── providers/    # LLM provider implementations
│   │   │   │   │   └── ClaudeLLMProvider.java  # Claude API integration
│   │   │   │   ├── ChatContext.java    # Conversation context
│   │   │   │   ├── LLMProvider.java    # LLM provider interface
│   │   │   │   ├── Message.java        # Message model
│   │   │   │   ├── ToolCall.java       # Tool call model
│   │   │   │   └── ToolUseBlock.java   # Tool use block model
│   │   │   ├── model/            # Data models
│   │   │   ├── service/          # Business logic
│   │   │   │   └── ChatService.java    # Main chat service
│   │   │   └── tools/            # Tool implementations
│   │   │       ├── impl/         # Tool implementations
│   │   │       │   ├── PlanningTool.java  # Planning tool
│   │   │       │   └── TerminalTool.java  # Terminal command tool
│   │   │       ├── Tool.java     # Tool interface
│   │   │       └── ToolRegistry.java  # Tool registry
│   │   └── resources/      # Application resources
│   │       └── application.properties  # Configuration properties
│   └── test/              # Test classes
└── pom.xml               # Maven configuration
```

## Core Workflows

### Chat Session Workflow

1. User creates a new chat session via REST API
2. Backend creates a session with unique ID and workspace
3. User sends a message to the backend
4. Backend forwards the message to the LLM provider
5. LLM generates a response, potentially with tool use blocks
6. Backend detects tool use blocks and executes corresponding tools
7. Tool results are sent back to the LLM for continuation
8. Final response and tool outputs are streamed to the frontend
9. Frontend displays the response and tool outputs in real-time

### Tool Execution Workflow

1. LLM response contains a tool use block
2. Backend parses the tool use block to extract tool name and arguments
3. Backend looks up the tool in the registry
4. Tool is executed with the provided arguments
5. Tool execution results are captured
6. Results are sent back to the LLM and/or streamed to the frontend
7. Frontend displays the tool execution and results

## Current Implementation Status

### Completed Features

1. **Phase 1: Tool Use Block Handoff**
   - Tool use block detection and parsing
   - Tool registry and basic tool implementations
   - Tool execution and result handling
   - WebSocket integration for real-time updates

2. **Phase 2: Enhanced Planning Tool**
   - PlanningTool implementation with autonomous planning patterns
   - Workspace directory support for all tools
   - Session-specific workspace in `/tmp/ai-developer-agent/{sessionId}`
   - Workspace path propagation to all tools
   - ToolCall refactoring to use Map<String, Object> for arguments

### In Progress Features

1. **Phase 3: Autonomous Agent Framework**
   - Multi-turn conversation support
   - Tool call visualization improvements
   - Real-time streaming enhancements
   - UI/UX improvements for tool execution display

### Known Issues

1. **Claude API Integration**:
   - Error with 'tool' role in messages: "Unexpected role 'tool'. Allowed roles are 'user' or 'assistant'."
   - Initial fix implemented in ChatService.java to remap 'tool' roles to 'assistant' in defensive copies
   - Further fixes needed in all code paths that assign or serialize the 'tool' role

2. **Frontend Rendering**:
   - Tool call blocks not properly collapsed/formatted as shown in reference screenshots
   - Terminal emulator initialization issues
   - Missing dependencies in package.json (fixed in latest commit)

3. **WebSocket Communication**:
   - Occasional disconnections requiring page refresh
   - Incomplete error handling for WebSocket failures

## Reference Samples

The project includes reference samples for enhancing the implementation:

### UI Enhancement Samples
Located in `/reference/samples/ui-enhancement/`:
- `backend-message-structure.java`: Backend message structure for improved UI rendering
- `claude-style-chat-page.tsx`: React component for Claude-style chat interface
- `claude-style-css.txt`: CSS styles for Claude-style UI
- `enhanced-message-component.tsx`: React component for enhanced message display
- `enhanced-websocket-handler.java`: Improved WebSocket handler for real-time updates

### Agent Framework Samples
Located in `/reference/samples/agent-framework/`:
- `agent-prompt-service.java`: Service for generating agent prompts
- `enhanced-chat-service-multiturn.java`: Enhanced chat service with multi-turn support
- `enhanced-planning-tool.java`: Advanced planning tool implementation
- `integration-readme.md`: Integration guidelines
- `updated-chat-service.java`: Updated chat service implementation
- `updated-application-properties.txt`: Configuration properties
- `usage-examples.md`: Usage examples for the agent framework
