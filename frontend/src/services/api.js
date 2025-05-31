import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

export const createSession = async () => {
  try {
    const response = await axios.post(`${API_BASE_URL}/sessions`);
    return response.data;
  } catch (error) {
    console.error('Error creating session:', error);
    throw error;
  }
};

export const getSessionHistory = async (sessionId) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/sessions/${sessionId}/history`);
    return response.data;
  } catch (error) {
    console.error('Error getting session history:', error);
    throw error;
  }
};

export const sendMessage = (sessionId, message) => {
  return new EventSource(`${API_BASE_URL}/chat?sessionId=${sessionId}&message=${encodeURIComponent(message)}`);
};

export const executeTool = async (sessionId, toolName, args) => {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/tools/${toolName}?sessionId=${sessionId}`,
      args,
      { responseType: 'stream' }
    );
    return response.data;
  } catch (error) {
    console.error(`Error executing tool ${toolName}:`, error);
    throw error;
  }
};
