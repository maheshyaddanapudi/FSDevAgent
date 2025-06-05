// Enhanced useChatStore.js - Fixed data mapping for collapsible UI
import { create } from 'zustand';
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

// Debug helper for tracing data structure
const debugLog = (label, data) => {
  console.log(`[DEBUG] ${label}:`, JSON.stringify(data, null, 2));
};

const useChatStore = create((set, get) => ({
  sessionId: null,
  messages: [],
  isLoading: false,
  isProcessing: false,
  error: null,
  toolOutputs: [],
  
  addMessage: (message) => {
    try {
      if (!message || typeof message !== 'object') {
        console.error('Invalid message object provided to addMessage');
        return;
      }
      
      // Debug log for message structure
      debugLog('Adding message', message);
      
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
  
  addToolOutput: (toolOutput) => {
    try {
      if (!toolOutput) {
        console.error('No tool output provided to addToolOutput');
        return;
      }
      
      // Debug log for tool output structure
      debugLog('Adding tool output', toolOutput);
      
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
  
  setIsProcessing: (isProcessing) => {
    if (typeof isProcessing === 'boolean') {
      set({ isProcessing });
    } else {
      console.error('setIsProcessing expects a boolean value');
    }
  },
  
  clearError: () => {
    set({ error: null });
  },
  
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
  
  // Helper to parse tool calls from raw event data
  parseToolCalls: (eventData) => {
    try {
      // Check for EVENT:toolCall format
      if (typeof eventData === 'string' && eventData.includes('EVENT:toolCall')) {
        const toolCallMatch = eventData.match(/EVENT:toolCall\s*({.*})/);
        if (toolCallMatch && toolCallMatch[1]) {
          const toolCallData = JSON.parse(toolCallMatch[1]);
          debugLog('Parsed tool call from EVENT format', toolCallData);
          return [toolCallData]; // Return as array for consistent handling
        }
      }
      
      // Check for explicit toolCalls array
      if (eventData.toolCalls && Array.isArray(eventData.toolCalls)) {
        debugLog('Found explicit toolCalls array', eventData.toolCalls);
        return eventData.toolCalls;
      }
      
      // Check for single toolCall object
      if (eventData.toolCall && typeof eventData.toolCall === 'object') {
        debugLog('Found single toolCall object', eventData.toolCall);
        return [eventData.toolCall]; // Convert to array for consistent handling
      }
      
      // Check for raw JSON tool call in message content
      if (eventData.message && typeof eventData.message === 'string') {
        const content = eventData.message;
        if (content.includes('"name":') && content.includes('"arguments":')) {
          try {
            // Try to extract JSON from the message content
            const jsonMatch = content.match(/({[\s\S]*"name"[\s\S]*"arguments"[\s\S]*})/);
            if (jsonMatch && jsonMatch[1]) {
              const toolCallData = JSON.parse(jsonMatch[1]);
              debugLog('Extracted tool call from message content', toolCallData);
              return [toolCallData];
            }
          } catch (e) {
            console.warn('Failed to extract tool call from message content', e);
          }
        }
      }
      
      return null;
    } catch (error) {
      console.error('Error parsing tool calls:', error);
      return null;
    }
  },
  
  // Helper to parse thinking blocks from raw event data
  parseThinking: (eventData) => {
    try {
      // Check for explicit thinking property
      if (eventData.thinking && typeof eventData.thinking === 'string') {
        debugLog('Found explicit thinking block', eventData.thinking);
        return eventData.thinking;
      }
      
      // Check for EVENT:thinking format
      if (typeof eventData === 'string' && eventData.includes('EVENT:thinking')) {
        const thinkingMatch = eventData.match(/EVENT:thinking\s*([\s\S]*?)(?=EVENT:|$)/);
        if (thinkingMatch && thinkingMatch[1]) {
          debugLog('Parsed thinking from EVENT format', thinkingMatch[1]);
          return thinkingMatch[1].trim();
        }
      }
      
      // Check for thinking block in message content
      if (eventData.message && typeof eventData.message === 'string') {
        const content = eventData.message;
        if (content.includes('<thinking>') && content.includes('</thinking>')) {
          const thinkingMatch = content.match(/<thinking>([\s\S]*?)<\/thinking>/);
          if (thinkingMatch && thinkingMatch[1]) {
            debugLog('Extracted thinking from message content', thinkingMatch[1]);
            return thinkingMatch[1].trim();
          }
        }
      }
      
      return null;
    } catch (error) {
      console.error('Error parsing thinking block:', error);
      return null;
    }
  },
  
  // Helper to parse tool results from raw event data
  parseToolResults: (eventData) => {
    try {
      // Check for explicit toolResults array
      if (eventData.toolResults && Array.isArray(eventData.toolResults)) {
        debugLog('Found explicit toolResults array', eventData.toolResults);
        return eventData.toolResults;
      }
      
      // Check for single toolResult
      if (eventData.toolResult && (typeof eventData.toolResult === 'string' || typeof eventData.toolResult === 'object')) {
        debugLog('Found single toolResult', eventData.toolResult);
        return [eventData.toolResult]; // Convert to array for consistent handling
      }
      
      // Check for EVENT:toolResult format
      if (typeof eventData === 'string' && eventData.includes('EVENT:toolResult')) {
        const toolResultMatch = eventData.match(/EVENT:toolResult\s*({.*})/);
        if (toolResultMatch && toolResultMatch[1]) {
          const toolResultData = JSON.parse(toolResultMatch[1]);
          debugLog('Parsed tool result from EVENT format', toolResultData);
          return [toolResultData]; // Return as array for consistent handling
        }
      }
      
      return null;
    } catch (error) {
      console.error('Error parsing tool results:', error);
      return null;
    }
  },
  
  // Enhanced message sending with improved data structure parsing
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
      let currentToolCalls = null;
      let currentThinking = null;
      let currentToolResults = null;
      
      const cleanup = () => {
        if (eventSource.readyState !== EventSource.CLOSED) {
          eventSource.close();
        }
        set({ isLoading: false, isProcessing: false });
      };
      
      eventSource.onmessage = (event) => {
        try {
          debugLog('Raw SSE event data', event.data);
          
          const chunk = JSON.parse(event.data);
          debugLog('Parsed SSE chunk', chunk);
          
          if (chunk && chunk.message) {
            // Extract clean message content (without tool calls or thinking blocks)
            let cleanMessage = chunk.message;
            
            // Remove tool call blocks from display content
            cleanMessage = cleanMessage.replace(/<tool_use>[\s\S]*?<\/tool_use>/g, '');
            cleanMessage = cleanMessage.replace(/EVENT:toolCall[\s\S]*?(?=EVENT:|$)/g, '');
            
            // Remove thinking blocks from display content
            cleanMessage = cleanMessage.replace(/<thinking>[\s\S]*?<\/thinking>/g, '');
            cleanMessage = cleanMessage.replace(/EVENT:thinking[\s\S]*?(?=EVENT:|$)/g, '');
            
            // Append clean content to assistant message
            assistantMessage += cleanMessage;
            
            // Parse tool calls, thinking blocks, and tool results
            const toolCalls = get().parseToolCalls(chunk) || currentToolCalls;
            const thinking = get().parseThinking(chunk) || currentThinking;
            const toolResults = get().parseToolResults(chunk) || currentToolResults;
            
            // Update current state for next iteration
            if (toolCalls) currentToolCalls = toolCalls;
            if (thinking) currentThinking = thinking;
            if (toolResults) currentToolResults = toolResults;
            
            // Debug log the extracted components
            debugLog('Extracted components', {
              cleanMessage,
              toolCalls: currentToolCalls,
              thinking: currentThinking,
              toolResults: currentToolResults
            });
            
            // Update or create assistant message with properly structured data
            set(state => {
              const messages = [...state.messages];
              const lastMessage = messages[messages.length - 1];
              
              if (lastMessage && lastMessage.role === 'assistant' && !lastMessage.isComplete) {
                // Update existing assistant message
                messages[messages.length - 1] = {
                  ...lastMessage,
                  content: assistantMessage.trim(),
                  isComplete: false,
                  // Add properly structured tool-related properties
                  ...(currentToolCalls && { toolCalls: currentToolCalls }),
                  ...(currentThinking && { thinking: currentThinking }),
                  ...(currentToolResults && { toolResults: currentToolResults })
                };
              } else {
                // Add new assistant message
                messages.push({
                  role: 'assistant',
                  content: assistantMessage.trim(),
                  isComplete: false,
                  timestamp: new Date().toISOString(),
                  // Add properly structured tool-related properties
                  ...(currentToolCalls && { toolCalls: currentToolCalls }),
                  ...(currentThinking && { thinking: currentThinking }),
                  ...(currentToolResults && { toolResults: currentToolResults })
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
      
      const messages = response.data.map((msg, index) => {
        // Parse tool calls and thinking blocks from message content
        const toolCalls = get().parseToolCalls(msg);
        const thinking = get().parseThinking(msg);
        const toolResults = get().parseToolResults(msg);
        
        return {
          id: msg.id || `history_${index}`,
          role: msg.role || 'unknown',
          content: msg.message || msg.content || '',
          // Add properly structured tool-related properties
          ...(toolCalls && { toolCalls }),
          ...(thinking && { thinking }),
          ...(toolResults && { toolResults }),
          isComplete: true,
          timestamp: msg.timestamp || new Date().toISOString()
        };
      });
      
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
  
  clearMessages: () => {
    try {
      set({ messages: [], error: null });
    } catch (error) {
      console.error('Error clearing messages:', error);
      set({ error: 'Failed to clear messages' });
    }
  },
  
  clearToolOutputs: () => {
    try {
      set({ toolOutputs: [], error: null });
    } catch (error) {
      console.error('Error clearing tool outputs:', error);
      set({ error: 'Failed to clear tool outputs' });
    }
  },
  
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
