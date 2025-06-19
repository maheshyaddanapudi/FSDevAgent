// Configuration constants for the AI Developer Agent frontend
export const API_BASE_URL = '/api';
export const WS_BASE_URL = 'ws://localhost:8080';

export const CONFIG = {
  API_BASE_URL,
  WS_BASE_URL,
  SESSION_TIMEOUT: 300000, // 5 minutes
  RECONNECT_ATTEMPTS: 5,
  RECONNECT_DELAY: 1000 // 1 second
};

