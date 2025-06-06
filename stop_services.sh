#!/bin/bash
if [ -f backend/backend.pid ]; then
  echo "Stopping backend with PID $(cat backend/backend.pid)"
  kill $(cat backend/backend.pid)
  rm backend/backend.pid
else
  echo "No backend PID file found"
fi

if [ -f frontend/frontend.pid ]; then
  echo "Stopping frontend with PID $(cat frontend/frontend.pid)"
  kill $(cat frontend/frontend.pid)
  rm frontend/frontend.pid
else
  echo "No frontend PID file found"
fi
