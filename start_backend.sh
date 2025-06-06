#!/bin/bash
# Use environment variable for API key instead of hardcoding
# Export LLM_API_KEY before running this script
cd backend
mvn clean install -DskipTests
java -DLLM_API_KEY="$LLM_API_KEY" -Xms128m -Xmx200m -jar target/ai-developer-agent-1.0.0.jar > backend.log 2>&1 &
echo $! > backend.pid
echo "Backend started with PID $(cat backend.pid)"
