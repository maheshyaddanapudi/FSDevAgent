#!/bin/bash
cd frontend
npm install --legacy-peer-deps
PORT=3001 NODE_OPTIONS=--openssl-legacy-provider npm start > frontend.log 2>&1 &
echo $! > frontend.pid
echo "Frontend started with PID $(cat frontend.pid)"
