import { create } from 'zustand';
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';
const WS_BASE_URL = process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080/ws';

const useChatStore = create((set, get) => ({
  sessionId: null,
  messages: [],
  isLoading: false,
  error: null,
  toolOutputs: [],
  
  initializeSession: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await axios.post(`${API_BASE_URL}/sessions`);
      set({ 
        sessionId: response.data.sessionId,
        isLoading: false,
        messages: []
      });
      return response.data.sessionId;
    } catch (error) {
      set({ 
        error: error.message || 'Failed to initialize session',
        isLoading: false
      });
      return null;
    }
  },
  
  sendMessage: async (message) => {
    const { sessionId } = get();
    if (!sessionId) {
      set({ error: 'No active session' });
      return;
    }
    
    // Add user message to state
    set(state => ({
      messages: [...state.messages, { role: 'user', content: message }],
      isLoading: true,
      error: null
    }));
    
    try {
      const source = new EventSource(`${API_BASE_URL}/chat?sessionId=${sessionId}&message=${encodeURIComponent(message)}`);
      
      let assistantMessage = '';
      
      source.onmessage = (event) => {
        const chunk = JSON.parse(event.data);
        assistantMessage += chunk.message;
        
        set(state => ({
          messages: [
            ...state.messages.filter(m => m.role !== 'assistant' || m.isComplete),
            { role: 'assistant', content: assistantMessage, isComplete: false }
          ]
        }));
      };
      
      source.onerror = (error) => {
        source.close();
        set(state => ({
          isLoading: false,
          messages: [
            ...state.messages.filter(m => m.role !== 'assistant' || m.isComplete),
            { role: 'assistant', content: assistantMessage, isComplete: true }
          ]
        }));
      };
      
      return () => {
        source.close();
      };
    } catch (error) {
      set({ 
        error: error.message || 'Failed to send message',
        isLoading: false
      });
    }
  },
  
  executeTool: async (toolName, args) => {
    const { sessionId } = get();
    if (!sessionId) {
      set({ error: 'No active session' });
      return;
    }
    
    set({ isLoading: true, error: null });
    
    try {
      const response = await axios.post(
        `${API_BASE_URL}/tools/${toolName}?sessionId=${sessionId}`,
        args,
        { responseType: 'stream' }
      );
      
      set(state => ({
        toolOutputs: [...state.toolOutputs, {
          toolName,
          args,
          output: response.data,
          timestamp: new Date()
        }],
        isLoading: false
      }));
      
      return response.data;
    } catch (error) {
      set({ 
        error: error.message || 'Failed to execute tool',
        isLoading: false
      });
      return null;
    }
  },
  
  getSessionHistory: async () => {
    const { sessionId } = get();
    if (!sessionId) {
      set({ error: 'No active session' });
      return;
    }
    
    set({ isLoading: true, error: null });
    
    try {
      const response = await axios.get(`${API_BASE_URL}/sessions/${sessionId}/history`);
      set({ 
        messages: response.data.map(msg => ({
          role: msg.role,
          content: msg.message,
          toolCall: msg.toolCall,
          isComplete: true
        })),
        isLoading: false
      });
    } catch (error) {
      set({ 
        error: error.message || 'Failed to get session history',
        isLoading: false
      });
    }
  },
  
  clearMessages: () => {
    set({ messages: [] });
  }
}));

export default useChatStore;
