# FSDevAgent Setup Instructions

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Node.js 16 or higher
- npm 8 or higher
- Git

## Environment Variables

The following environment variables are required:

- `LLM_API_KEY`: Claude API key for LLM integration

## Backend Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/maheshyaddanapudi/FSDevAgent.git
   cd FSDevAgent
   ```

2. Switch to the test-1 branch:
   ```bash
   git checkout test-1
   ```

3. Build the backend:
   ```bash
   cd backend
   mvn clean install -DskipTests
   ```

4. Run the backend:
   ```bash
   export LLM_API_KEY="your_claude_api_key"
   java -DLLM_API_KEY="$LLM_API_KEY" -Xms128m -Xmx200m -jar target/ai-developer-agent-1.0.0.jar
   ```

## Frontend Setup

1. Install dependencies:
   ```bash
   cd ../frontend
   npm install
   ```

2. Run the frontend:
   ```bash
   npm start
   ```

3. Access the application:
   Open your browser and navigate to `http://localhost:3001`

## Docker Setup (Optional)

1. Build the Docker images:
   ```bash
   docker-compose build
   ```

2. Run the Docker containers:
   ```bash
   docker-compose up -d
   ```

3. Access the application:
   Open your browser and navigate to `http://localhost:3001`

## Configuration

### Backend Configuration

The backend configuration is located in `backend/src/main/resources/application.properties`. Key properties include:

- `server.port`: The port on which the backend server runs (default: 8080)
- `logging.level.root`: The logging level (default: INFO)

### Frontend Configuration

The frontend configuration is located in `.env` and `.env.development` files. Key properties include:

- `REACT_APP_API_URL`: The URL of the backend API (default: http://localhost:8080)
- `PORT`: The port on which the frontend server runs (default: 3001)

## Project Structure

### Backend

- `src/main/java/com/ai/developer/config`: Configuration classes
- `src/main/java/com/ai/developer/controller`: REST controllers
- `src/main/java/com/ai/developer/llm`: LLM integration classes
- `src/main/java/com/ai/developer/service`: Service classes
- `src/main/java/com/ai/developer/tools`: Tool implementation classes

### Frontend

- `src/components`: React components
- `src/hooks`: Custom React hooks
- `src/App.js`: Main application component
- `src/index.js`: Entry point

## Testing

### Backend Testing

```bash
cd backend
mvn test
```

### Frontend Testing

```bash
cd frontend
npm test
```

## Troubleshooting

### Backend Issues

- If the backend fails to start, check the logs for errors
- Ensure the Claude API key is correctly set
- Verify that port 8080 is not in use by another application

### Frontend Issues

- If the frontend fails to start, check the console for errors
- Ensure all dependencies are installed
- Verify that port 3001 is not in use by another application

## Additional Resources

- [Claude API Documentation](https://docs.anthropic.com/claude/reference/getting-started-with-the-api)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [React Documentation](https://reactjs.org/docs/getting-started.html)

---

Last Updated: June 6, 2025
