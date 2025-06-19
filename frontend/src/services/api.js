import axios from 'axios';

const API_BASE_URL = '/api';

export const createSession = async () => {
  try {
    const response = await axios.post(`${API_BASE_URL}/sessions`);
    return response.data;
  } catch (error) {
    console.error('Error creating session:', error);
    throw error;
  }
};

export const getSessionHistory = async (aiDeveloperAgentSessionId) => {
  try {
    const response = await axios.get(`${API_BASE_URL}/sessions/${aiDeveloperAgentSessionId}/history`);
    return response.data;
  } catch (error) {
    console.error('Error getting session history:', error);
    throw error;
  }
};
export const sendMessage = (aiDeveloperAgentSessionId, message) => {
  return new EventSource(`${API_BASE_URL}/chat?aiDeveloperAgentSessionId=${aiDeveloperAgentSessionId}&message=${encodeURIComponent(message)}`);
};
export const executeTool = async (aiDeveloperAgentSessionId, toolName, args) => {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/tools/${toolName}?aiDeveloperAgentSessionId=${aiDeveloperAgentSessionId}`,
      args,
      { responseType: 'stream' }
    );
    return response.data;
  } catch (error) {
    console.error(`Error executing tool ${toolName}:`, error);
    throw error;
  }
};
