# FSDevAgent Workspace Directory Strategy - Handover Document

## Overview

This document provides a comprehensive guide to the workspace directory strategy implemented in the FSDevAgent project. The strategy enables session isolation and better organization of files and directories created during agent interactions.

## Table of Contents

1. [Introduction](#introduction)
2. [Architecture](#architecture)
3. [Implementation Details](#implementation-details)
4. [Usage Guide](#usage-guide)
5. [Testing](#testing)
6. [Future Enhancements](#future-enhancements)
7. [Troubleshooting](#troubleshooting)

## Introduction

The workspace directory strategy provides a structured approach to file and directory management in the FSDevAgent project. Each user session gets its own isolated workspace, and task-specific subdirectories can be created within each workspace for better organization.

### Key Benefits

- **Session Isolation**: Each session has its own workspace, preventing file conflicts between sessions
- **Dynamic Subdirectories**: Support for task-specific subdirectories within each workspace
- **Path Resolution**: Automatic resolution of relative paths against the workspace directory
- **Backward Compatibility**: All tools maintain backward compatibility with existing code

## Architecture

### Base Directory Structure

```
/tmp/ai-developer-agent/
├── {session_id_1}/
│   ├── {task_1}/
│   ├── {task_2}/
│   └── ...
├── {session_id_2}/
│   ├── {task_1}/
│   └── ...
└── ...
```

### Component Interactions

1. **ChatContext**: Manages workspace directory information and provides path resolution methods
2. **ChatService**: Propagates workspace context to tools and handles session management
3. **ToolRegistry**: Provides access to tools that operate within the workspace
4. **Tools**: Execute operations within the workspace context

## Implementation Details

### ChatContext Enhancements

The `ChatContext` class has been enhanced to support workspace directories:

- Added `workspaceDirectory` field to store the session's workspace path
- Added methods for path resolution and task directory management:
  - `resolvePath(String path)`: Resolves a relative path against the workspace directory
  - `getTaskDirectory(String taskName)`: Gets the path to a task-specific directory
  - `createTaskDirectory(String taskName)`: Creates a new task-specific directory

### Tool Implementations

#### TerminalTool

The `TerminalTool` has been updated to support workspace directories:

- Added optional `sessionId` parameter for workspace context
- Enhanced working directory resolution to use workspace when appropriate
- Added automatic directory creation for non-existent paths
- Maintained backward compatibility for existing code

#### FileSystemTool

The `FileSystemTool` has been updated with similar enhancements:

- Added optional `sessionId` parameter for workspace context
- Added support for resolving paths against the workspace directory
- Added automatic parent directory creation for file operations
- Maintained backward compatibility for existing code

### ChatService Integration

The `ChatService` class has been updated to:

- Create and manage workspace directories for each session
- Propagate workspace information to tools via arguments
- Process tool arguments to resolve paths relative to workspace
- Support task-specific subdirectories via the `taskName` parameter

### Serialization Fixes

Several serialization issues were fixed to ensure proper type handling:

- `ToolCall.arguments` is now properly serialized from `Map<String, Object>` to JSON String
- Tool results are properly serialized to JSON String for `ToolCallResponse`

## Usage Guide

### Creating a Session

When a new session is created, a workspace directory is automatically created at `/tmp/ai-developer-agent/{session_id}/`.

```java
Mono<SessionResponse> response = chatService.createSession();
// The workspace directory is included in the response
```

### Using Tools with Workspace Context

Tools can be used with workspace context by providing the `sessionId` parameter:

```java
Map<String, Object> arguments = new HashMap<>();
arguments.put("sessionId", sessionId);
arguments.put("command", "ls -la");
arguments.put("exec_dir", "."); // Will be resolved against the workspace directory

Flux<ToolOutput> result = toolRegistry.getTool("terminal").execute(arguments);
```

### Creating Task-Specific Subdirectories

Task-specific subdirectories can be created by providing the `taskName` parameter:

```java
Map<String, Object> arguments = new HashMap<>();
arguments.put("sessionId", sessionId);
arguments.put("taskName", "python-project");
arguments.put("command", "mkdir -p src tests");
arguments.put("exec_dir", "."); // Will be resolved against the task directory

Flux<ToolOutput> result = toolRegistry.getTool("terminal").execute(arguments);
```

## Testing

### Manual Testing

1. Start the backend server:
   ```bash
   cd /home/ubuntu/FSDevAgent/backend
   mvn spring-boot:run
   ```

2. Start the frontend server:
   ```bash
   cd /home/ubuntu/FSDevAgent/frontend
   npm start --legacy-peer-deps
   ```

3. Open the application in a browser and create a new session

4. Test file operations with the following prompts:
   - "Create a directory called 'test-project' in my workspace"
   - "Create a Python file in the test-project directory that prints 'Hello, World!'"
   - "Run the Python file you just created"

### Automated Testing

Unit tests for the workspace directory strategy can be run with:

```bash
cd /home/ubuntu/FSDevAgent/backend
mvn test -Dtest=ChatContextTest,TerminalToolTest,FileSystemToolTest
```

## Future Enhancements

1. **Workspace Cleanup**: Implement automatic cleanup of unused workspace directories
2. **Workspace Sharing**: Allow sharing of workspaces between sessions
3. **Workspace Templates**: Support for predefined workspace templates
4. **Workspace Persistence**: Option to persist workspaces across server restarts

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure the application has write permissions to `/tmp/ai-developer-agent/`
2. **Path Resolution Errors**: Check that relative paths are being properly resolved against the workspace directory
3. **Missing Directories**: Verify that parent directories are being created automatically when needed

### Debugging

1. Enable debug logging in `application.properties`:
   ```properties
   logging.level.com.ai.developer=DEBUG
   ```

2. Check the logs for workspace-related messages:
   ```bash
   grep "workspace" logs/application.log
   ```

3. Verify workspace directories are being created:
   ```bash
   ls -la /tmp/ai-developer-agent/
   ```
