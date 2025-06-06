# Port and WebSocket Configuration

This document outlines the port and WebSocket configuration for the FSDevAgent project, ensuring consistent integration between frontend and backend components.

## Port Configuration

### Frontend
- **Port**: 3001
- **Configuration File**: `/frontend/.env.development`
- **Start Command**: `PORT=3001 npm start`

### Backend
- **Port**: 8080
- **Configuration File**: `/backend/src/main/resources/application.properties`
- **Start Command**: `java -DLLM_API_KEY="$LLM_API_KEY" -Xms128m -Xmx200m -jar target/ai-developer-agent-1.0.0.jar`

## WebSocket Configuration

### Frontend WebSocket Endpoints
- **Base URL**: `ws://localhost:8080/ws`
- **Tool Output Endpoint**: `/ws/tool-output`
- **Agent State Endpoint**: `/ws/agent-state`
- **Configuration File**: `/frontend/src/hooks/useWebSocket.js`

### Backend WebSocket Handlers
- **Tool Output Handler**: `ToolOutputWebSocketHandler.java` (endpoint: `/ws/tool-output`)
- **Agent State Handler**: `AgentStateWebSocketHandler.java` (endpoint: `/ws/agent-state`)
- **Configuration File**: `/backend/src/main/java/com/ai/developer/config/WebSocketConfig.java`

## Important Notes

1. **Port Consistency**: Always ensure the frontend runs on port 3001 and the backend on port 8080 to maintain integration.

2. **WebSocket Endpoint Matching**: The frontend WebSocket endpoints must match exactly with the backend's registered handlers:
   - Frontend connects to: `/ws/tool-output` and `/ws/agent-state`
   - Backend exposes: `/ws/tool-output` and `/ws/agent-state`

3. **API Key Configuration**: The Claude API key must be set as an environment variable and passed to the backend via the `-DLLM_API_KEY` JVM parameter.

## Troubleshooting

### WebSocket Connection Issues
- Check browser console for WebSocket connection errors
- Verify that frontend WebSocket URLs match backend endpoints
- Ensure both frontend and backend are running on the correct ports
- Check for CORS issues in the backend configuration

### API Authentication Issues
- Ensure the LLM_API_KEY environment variable is set before starting the backend
- Verify the API key is correctly passed to the backend via the JVM parameter
- Check backend logs for authentication errors when making API calls
