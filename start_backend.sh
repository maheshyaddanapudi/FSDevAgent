#!/bin/bash
# Export API key as environment variable
# IMPORTANT: Set the CLAUDE_API_KEY environment variable before running this script
# Example: export CLAUDE_API_KEY="your-api-key-here"

# Check if API key is set
if [ -z "${CLAUDE_API_KEY}" ]; then
  echo "Error: CLAUDE_API_KEY environment variable is not set"
  echo "Please set it before running this script:"
  echo "export CLAUDE_API_KEY=\"your-api-key-here\""
  exit 1
fi

# Build and start backend
cd /home/ubuntu/FSDevAgent/backend
mvn clean install

# Pass the API key as a system property with the correct name
java -DCLAUDE_API_KEY="${CLAUDE_API_KEY}" -jar target/ai-developer-agent-1.0.0.jar > /home/ubuntu/backend_logs.txt 2>&1 &
echo $! > /home/ubuntu/FSDevAgent/backend.pid
echo "Backend started with PID $(cat /home/ubuntu/FSDevAgent/backend.pid)"
