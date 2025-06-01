// Fixed useChatStore.js - Issue #2: Runtime Errors Fix
import { create } from 'zustand';
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const useChatStore = create((set, get) => ({
  sessionId: null,
  messages: [],
  isLoading: false,
  isProcessing: false,
  error: null,
  toolOutputs: [],
  
  // Issue #2 Fix: Add missing addMessage function with proper error handling
  addMessage: (message) => {
    try {
      if (!message || typeof message !== 'object') {
        console.error('Invalid message object provided to addMessage');
        return;
      }
      
      set(state => ({
        messages: [...state.messages, {
          ...message,
          id: message.id || `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          timestamp: message.timestamp || new Date().toISOString()
        }]
      }));
    } catch (error) {
      console.error('Error adding message:', error);
      set(state => ({
        error: `Failed to add message: ${error.message}`
      }));
    }
  },
  
  // Issue #2 Fix: Add missing addToolOutput function with proper error handling
  addToolOutput: (toolOutput) => {
    try {
      if (!toolOutput) {
        console.error('No tool output provided to addToolOutput');
        return;
      }
      
      set(state => ({
        toolOutputs: [...state.toolOutputs, {
          ...toolOutput,
          id: toolOutput.id || `tool_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          timestamp: toolOutput.timestamp || new Date().toISOString()
        }]
      }));
    } catch (error) {
      console.error('Error adding tool output:', error);
      set(state => ({
        error: `Failed to add tool output: ${error.message}`
      }));
    }
  },
  
  // Issue #2 Fix: Add setter for isProcessing state with validation
  setIsProcessing: (isProcessing) => {
    if (typeof isProcessing === 'boolean') {
      set({ isProcessing });
    } else {
      console.error('setIsProcessing expects a boolean value');
    }
  },
  
  // Issue #2 Fix: Add clearError function
  clearError: () => {
    set({ error: null });
  },
  
  // Issue #2 Fix: Improved session initialization with better error handling
  initializeSession: async () => {
    set({ isLoading: true, error: null });
    try {
      const response = await axios.post(`${API_BASE_URL}/sessions`, {}, {
        timeout: 10000 // 10 second timeout
      });
      
      if (!response.data || !response.data.sessionId) {
        throw new Error('Invalid response from server: missing sessionId');
      }
      
      set({ 
        sessionId: response.data.sessionId,
        isLoading: false,
        messages: [],
        error: null
      });
      
      console.log('Session initialized successfully:', response.data.sessionId);
      return response.data.sessionId;
    } catch (error) {
      console.error('Session initialization failed:', error);
      const errorMessage = error.response?.data?.message || error.message || 'Failed to initialize session';
      set({ 
        error: errorMessage,
        isLoading: false,
        sessionId: null
      });
      return null;
    }
  },
  
  // Issue #2 Fix: Improved sendMessage with better error handling and validation
  sendMessage: async (message) => {
    const { sessionId } = get();
    
    if (!message || typeof message !== 'string' || message.trim() === '') {
      set({ error: 'Message cannot be empty' });
      return null;
    }
    
    if (!sessionId) {
      set({ error: 'No active session. Please refresh the page.' });
      return null;
    }
    
    // Add user message to state first
    const userMessage = {
      role: 'user',
      content: message.trim(),
      timestamp: new Date().toISOString()
    };
    
    try {
      get().addMessage(userMessage);
      set({ isLoading: true, error: null, isProcessing: true });
      
      const encodedMessage = encodeURIComponent(message.trim());
      const eventSource = new EventSource(`${API_BASE_URL}/chat?sessionId=${sessionId}&message=${encodedMessage}`);
      
      let assistantMessage = '';
      let messageComplete = false;
      
      const cleanup = () => {
        if (eventSource.readyState !== EventSource.CLOSED) {
          eventSource.close();
        }
        set({ isLoading: false, isProcessing: false });
      };
      
      eventSource.onmessage = (event) => {
        try {
          const chunk = JSON.parse(event.data);
          if (chunk && chunk.message) {
            assistantMessage += chunk.message;
            
            // Update or create assistant message
            set(state => {
              const messages = [...state.messages];
              const lastMessage = messages[messages.length - 1];
              
              if (lastMessage && lastMessage.role === 'assistant' && !lastMessage.isComplete) {
                // Update existing assistant message
                messages[messages.length - 1] = {
                  ...lastMessage,
                  content: assistantMessage,
                  isComplete: false
                };
              } else {
                // Add new assistant message
                messages.push({
                  role: 'assistant',
                  content: assistantMessage,
                  isComplete: false,
                  timestamp: new Date().toISOString()
                });
              }
              
              return { messages };
            });
          }
        } catch (parseError) {
          console.error('Error parsing SSE message:', parseError, event.data);
        }
      };
      
      eventSource.onerror = (error) => {
        console.log('SSE connection ended or error occurred:', error);
        
        if (!messageComplete) {
          // Mark the last assistant message as complete
          set(state => {
            const messages = [...state.messages];
            const lastMessage = messages[messages.length - 1];
            
            if (lastMessage && lastMessage.role === 'assistant' && !lastMessage.isComplete) {
              messages[messages.length - 1] = {
                ...lastMessage,
                isComplete: true
              };
            }
            
            return { messages };
          });
          messageComplete = true;
        }
        
        cleanup();
      };
      
      // Return cleanup function
      return cleanup;
      
    } catch (error) {
      console.error('Error sending message:', error);
      set({ 
        error: error.message || 'Failed to send message',
        isLoading: false,
        isProcessing: false
      });
      return null;
    }
  },
  
  // Issue #2 Fix: Improved tool execution with better error handling
  executeTool: async (toolName, args) => {
    const { sessionId } = get();
    
    if (!sessionId) {
      set({ error: 'No active session' });
      return null;
    }
    
    if (!toolName || typeof toolName !== 'string') {
      set({ error: 'Invalid tool name provided' });
      return null;
    }
    
    set({ isLoading: true, error: null });
    
    try {
      const response = await axios.post(
        `${API_BASE_URL}/tools/${toolName}?sessionId=${sessionId}`,
        args || {},
        { 
          responseType: 'stream',
          timeout: 30000 // 30 second timeout for tool execution
        }
      );
      
      const toolOutput = {
        toolName,
        args: args || {},
        output: response.data,
        timestamp: new Date().toISOString(),
        sessionId
      };
      
      get().addToolOutput(toolOutput);
      set({ isLoading: false });
      
      return response.data;
    } catch (error) {
      console.error(`Error executing tool ${toolName}:`, error);
      const errorMessage = error.response?.data?.message || error.message || 'Failed to execute tool';
      set({ 
        error: errorMessage,
        isLoading: false
      });
      return null;
    }
  },
  
  // Issue #2 Fix: Improved session history with better error handling
  getSessionHistory: async () => {
    const { sessionId } = get();
    
    if (!sessionId) {
      set({ error: 'No active session' });
      return;
    }
    
    set({ isLoading: true, error: null });
    
    try {
      const response = await axios.get(`${API_BASE_URL}/sessions/${sessionId}/history`, {
        timeout: 10000 // 10 second timeout
      });
      
      if (!response.data || !Array.isArray(response.data)) {
        throw new Error('Invalid response format from server');
      }
      
      const messages = response.data.map((msg, index) => ({
        id: msg.id || `history_${index}`,
        role: msg.role || 'unknown',
        content: msg.message || msg.content || '',
        toolCall: msg.toolCall || null,
        isComplete: true,
        timestamp: msg.timestamp || new Date().toISOString()
      }));
      
      set({ 
        messages,
        isLoading: false,
        error: null
      });
    } catch (error) {
      console.error('Error getting session history:', error);
      const errorMessage = error.response?.data?.message || error.message || 'Failed to get session history';
      set({ 
        error: errorMessage,
        isLoading: false
      });
    }
  },
  
  // Issue #2 Fix: Enhanced clearMessages with validation
  clearMessages: () => {
    try {
      set({ messages: [], error: null });
    } catch (error) {
      console.error('Error clearing messages:', error);
      set({ error: 'Failed to clear messages' });
    }
  },
  
  // Issue #2 Fix: Add clearToolOutputs function
  clearToolOutputs: () => {
    try {
      set({ toolOutputs: [], error: null });
    } catch (error) {
      console.error('Error clearing tool outputs:', error);
      set({ error: 'Failed to clear tool outputs' });
    }
  },
  
  // Issue #2 Fix: Add resetSession function
  resetSession: () => {
    try {
      set({
        sessionId: null,
        messages: [],
        toolOutputs: [],
        isLoading: false,
        isProcessing: false,
        error: null
      });
    } catch (error) {
      console.error('Error resetting session:', error);
    }
  }
}));

export default useChatStore;
