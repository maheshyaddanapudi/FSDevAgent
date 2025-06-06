# FSDevAgent Setup Instructions

This document provides detailed instructions for setting up and running the FSDevAgent project.

## Prerequisites

- Java 17 or higher
- Node.js 16 or higher
- npm or yarn
- Git
- Claude API key (for LLM integration)

## Repository Setup

1. Clone the repository:
```bash
git clone https://github.com/maheshyaddanapudi/FSDevAgent.git
cd FSDevAgent
```

2. Switch to the test-1 branch:
```bash
git checkout test-1
```

## Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
```

2. Set the Claude API key as an environment variable:
```bash
# Linux/macOS
export LLM_API_KEY="your-claude-api-key"

# Windows
set LLM_API_KEY=your-claude-api-key
```

3. Build the project using Maven:
```bash
./mvnw clean install -DskipTests
```

4. Run the backend service:
```bash
java -DLLM_API_KEY="$LLM_API_KEY" -jar target/ai-developer-agent-1.0.0.jar
```

The backend service will start on port 8080 by default.

## Frontend Setup

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
# or
yarn install
```

3. Create or update the `.env` file with the following content:
```
REACT_APP_API_URL=http://localhost:8080
```

4. Create or update the `.env.development` file with the following content:
```
REACT_APP_API_URL=http://localhost:8080
```

5. Start the development server:
```bash
npm start
# or
yarn start
```

The frontend will be available at http://localhost:3001.

## Configuration Options

### Backend Configuration

The backend configuration can be modified in `backend/src/main/resources/application.properties`:

```properties
# Server configuration
server.port=8080

# Logging configuration
logging.level.com.ai.developer=DEBUG

# CORS configuration
spring.webmvc.cors.allowed-origins=*
spring.webmvc.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.webmvc.cors.allowed-headers=*

# WebSocket configuration
spring.websocket.enabled=true
```

### Frontend Configuration

The frontend configuration can be modified through environment variables:

- `REACT_APP_API_URL`: Backend API URL (default: http://localhost:8080)
- `PORT`: Frontend server port (default: 3001)

## Workspace Directory

The agent creates session-specific workspaces in the following directory:
```
/tmp/ai-developer-agent/{sessionId}
```

Each session gets its own isolated workspace for file operations and tool execution.

## Testing the Setup

1. Open the frontend application at http://localhost:3001
2. The application should connect to the backend automatically
3. Type a message like "Please run ls -la in the current directory" and send it
4. The agent should execute the command and display the results

## Troubleshooting

### Backend Issues

1. **API Key Issues**:
   - Ensure the Claude API key is correctly set as an environment variable
   - Check the logs for authentication errors

2. **Port Conflicts**:
   - If port 8080 is already in use, modify the `server.port` in `application.properties`

3. **Java Version**:
   - Ensure you're using Java 17 or higher: `java -version`

### Frontend Issues

1. **Dependency Issues**:
   - If you encounter missing dependencies, run `npm install` or `yarn install` again
   - Check that all required packages are listed in `package.json`

2. **Connection Issues**:
   - Verify that the backend is running and accessible
   - Check that the `REACT_APP_API_URL` is correctly set in the `.env` file
   - Look for CORS errors in the browser console

3. **WebSocket Issues**:
   - If real-time updates aren't working, check WebSocket connection in browser developer tools
   - Ensure the backend WebSocket endpoint is correctly configured

## Security Notes

- The Claude API key should be kept secure and not committed to version control
- For production deployment, implement proper authentication and authorization
- Consider using environment-specific configuration files for different deployment environments
