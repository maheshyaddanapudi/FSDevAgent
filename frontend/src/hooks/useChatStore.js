// Enhanced Chat Store with Human-in-the-loop Support and Comprehensive Error Handling
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { API_BASE_URL } from '../config';
import { debugLog } from '../utils/debugLogger';

const useChatStore = create(
  persist(
    (set, get) => ({
      // State
      aiDeveloperAgentSessionId: null,
      messages: [],
      toolOutputs: [],
      isLoading: false,
      isProcessing: false,
      error: null,
      
      // NEW: Human-in-the-loop state
      waitingForHumanInput: false,
      humanInputRequest: null,
      
      // Actions
      clearError: () => set({ error: null }),
      
      // Add missing setIsProcessing function
      setIsProcessing: (processing) => set({ isProcessing: processing }),
      
      // Issue #2 Fix: Enhanced addMessage with validation
      addMessage: (message) => {
        try {
          if (!message || !message.role) {
            console.warn('Invalid message format:', message);
            return;
          }
          
          set(state => ({
            messages: [...state.messages, {
              id: message.id || uuidv4(),
              role: message.role,
              content: message.content || '',
              toolCall: message.toolCall || null,
              isComplete: message.isComplete !== undefined ? message.isComplete : true,
              timestamp: message.timestamp || new Date().toISOString()
            }]
          }));
        } catch (error) {
          debugLog.error('ChatStore', 'Error adding message', { error: error.message, message });
          console.error('Error adding message:', error);
        }
      },
      
      // Issue #2 Fix: Enhanced addToolOutput with validation
      addToolOutput: (toolOutput) => {
        try {
          if (!toolOutput) {
            console.warn('Invalid tool output format:', toolOutput);
            return;
          }
          
          set(state => ({
            toolOutputs: [...state.toolOutputs, {
              id: toolOutput.id || uuidv4(),
              toolName: toolOutput.toolName || 'unknown',
              type: toolOutput.type || 'output',
              content: toolOutput.content || '',
              timestamp: toolOutput.timestamp || new Date().toISOString(),
              ...toolOutput
            }]
          }));
        } catch (error) {
          debugLog.error('ChatStore', 'Error adding tool output', { error: error.message, toolOutput });
          console.error('Error adding tool output:', error);
        }
      },
      
      // Issue #2 Fix: Enhanced updateMessage with validation
      updateMessage: (messageId, updates) => {
        try {
          if (!messageId || !updates) {
            console.warn('Invalid update parameters:', { messageId, updates });
            return;
          }
          
          set(state => {
            const messages = [...state.messages];
            const messageIndex = messages.findIndex(m => m.id === messageId);
            
            if (messageIndex !== -1) {
              messages[messageIndex] = {
                ...messages[messageIndex],
                ...updates,
                timestamp: new Date().toISOString()
              };
            }
            
            return { messages };
          });
        } catch (error) {
          debugLog.error('ChatStore', 'Error updating message', { error: error.message, messageId, updates });
          console.error('Error updating message:', error);
        }
      },
      
      // Initialize session
      initializeSession: async () => {
        try {
          set({ isLoading: true, error: null });
          
          const response = await axios.post(`${API_BASE_URL}/sessions`, {});
          const sessionId = response.data.aiDeveloperAgentSessionId;
          
          set({ 
            aiDeveloperAgentSessionId: sessionId,
            isLoading: false 
          });
          
          return sessionId;
        } catch (error) {
          debugLog.error('ChatStore', 'Error initializing session', { error: error.message });
          console.error('Error initializing session:', error);
          set({ 
            error: error.message || 'Failed to initialize session',
            isLoading: false 
          });
          throw error;
        }
      },
      
      // Clear chat
      clearChat: () => {
        try {
          set({ 
            messages: [], 
            toolOutputs: [], 
            error: null,
            waitingForHumanInput: false,
            humanInputRequest: null
          });
        } catch (error) {
          debugLog.error('ChatStore', 'Error clearing chat', { error: error.message });
          console.error('Error clearing chat:', error);
        }
      },
      
      // Set session ID
      setAiDeveloperAgentSessionId: (sessionId) => {
        try {
          set({ aiDeveloperAgentSessionId: sessionId });
        } catch (error) {
          debugLog.error('ChatStore', 'Error setting session ID', { error: error.message, sessionId });
          console.error('Error setting session ID:', error);
        }
      },
      
      // Send message with SSE
      sendMessage: async (messageText) => {
        try {
          const { aiDeveloperAgentSessionId } = get();
          
          if (!aiDeveloperAgentSessionId) {
            throw new Error('No active session. Please initialize a session first.');
          }
          
          if (!messageText || messageText.trim() === '') {
            throw new Error('Message cannot be empty');
          }
          
          // Clear any previous error and set processing state
          set({ 
            error: null, 
            isProcessing: true,
            waitingForHumanInput: false,
            humanInputRequest: null
          });
          
          // Add user message
          get().addMessage({
            id: uuidv4(),
            role: 'user',
            content: messageText.trim(),
            timestamp: new Date().toISOString()
          });
          
          try {
            // Add initial assistant message that will be updated
            const assistantMessageId = uuidv4();
            get().addMessage({
              id: assistantMessageId,
              role: 'assistant',
              content: '',
              isComplete: false,
              timestamp: new Date().toISOString()
            });
            
            // Setup for SSE with comprehensive error handling
            const sseUrl = `${API_BASE_URL}/chat?aiDeveloperAgentSessionId=${aiDeveloperAgentSessionId}&message=${encodeURIComponent(messageText)}`;
            debugLog.sse('Connecting to SSE', { url: sseUrl, sessionId: aiDeveloperAgentSessionId, message: messageText });
            
            let eventSource;
            let messageComplete = false;
            
            // Cleanup function
            const cleanup = () => {
              try {
                debugLog.sse('Cleaning up SSE connection', { sessionId: aiDeveloperAgentSessionId });
                if (eventSource) {
                  eventSource.close();
                }
                set({ isProcessing: false });
              } catch (cleanupError) {
                debugLog.error('SSE', 'Error during cleanup', { error: cleanupError.message });
                console.error('Error during SSE cleanup:', cleanupError);
              }
            };
            
            try {
              eventSource = new EventSource(sseUrl);
              
              eventSource.onopen = (event) => {
                try {
                  debugLog.sse('SSE connection opened', { sessionId: aiDeveloperAgentSessionId, readyState: eventSource.readyState });
                } catch (error) {
                  debugLog.error('SSE', 'Error in onopen handler', { error: error.message });
                  console.error('Error in SSE onopen handler:', error);
                }
              };
              
              eventSource.onmessage = (event) => {
                try {
                  debugLog.sse('Received SSE message', { sessionId: aiDeveloperAgentSessionId, rawData: event.data });
                  
                  try {
                    const data = JSON.parse(event.data);
                    debugLog.sse('Parsed SSE message', { sessionId: aiDeveloperAgentSessionId, parsedData: data });
                    
                    // NEW: Check for human input request
                    if (data.type === 'tool_call' && data.tool === 'message_ask_user') {
                      // Close the event source as we need to pause for human input
                      cleanup();
                      
                      // Set the human input request state
                      set({
                        waitingForHumanInput: true,
                        humanInputRequest: {
                          id: data.id || uuidv4(),
                          text: data.text || 'Please provide additional information:',
                          attachments: data.attachments || [],
                          timestamp: new Date().toISOString()
                        }
                      });
                      
                      return;
                    }
                    
                    // Handle normal message content
                    if (data.message) {
                      set(state => {
                        const messages = [...state.messages];
                        const lastMessageIndex = messages.findIndex(m => m.id === assistantMessageId);
                        
                        if (lastMessageIndex !== -1) {
                          const currentContent = messages[lastMessageIndex].content || '';
                          messages[lastMessageIndex] = {
                            ...messages[lastMessageIndex],
                            content: currentContent + data.message,
                            isComplete: data.isComplete || false
                          };
                        }
                        
                        return { messages };
                      });
                    }
                    
                    // Handle tool calls
                    if (data.toolCall) {
                      set(state => {
                        const messages = [...state.messages];
                        const lastMessageIndex = messages.findIndex(m => m.id === assistantMessageId);
                        
                        if (lastMessageIndex !== -1) {
                          messages[lastMessageIndex] = {
                            ...messages[lastMessageIndex],
                            toolCall: data.toolCall,
                            isComplete: data.isComplete || false
                          };
                        }
                        
                        return { messages };
                      });
                    }
                    
                    // ✅ NEW: Handle tool outputs for emulator
                    if (data.role === 'tool' || data.messageType === 'tool_result') {
                      debugLog.sse('Received tool output', { 
                        toolCallId: data.toolCallId, 
                        messageType: data.messageType,
                        contentLength: data.message?.length || 0
                      });
                      
                      get().addToolOutput({
                        id: data.toolCallId || uuidv4(),
                        toolName: 'unknown', // Will be updated when we have tool call context
                        type: 'output',
                        content: data.message || '',
                        timestamp: data.timestamp || new Date().toISOString(),
                        sessionId: aiDeveloperAgentSessionId
                      });
                    }
                  } catch (parseError) {
                    debugLog.error('SSE', 'Error parsing SSE message', { 
                      error: parseError.message, 
                      rawData: event.data,
                      sessionId: aiDeveloperAgentSessionId 
                    });
                    console.error('Error parsing SSE message:', parseError, event.data);
                    
                    // Continue processing instead of crashing
                    return;
                  }
                } catch (messageHandlingError) {
                  debugLog.error('SSE', 'Error handling SSE message', { 
                    error: messageHandlingError.message, 
                    stack: messageHandlingError.stack,
                    sessionId: aiDeveloperAgentSessionId 
                  });
                  console.error('Error handling SSE message:', messageHandlingError);
                  
                  // Continue processing instead of crashing
                  return;
                }
              };
              
              eventSource.onerror = (error) => {
                try {
                  debugLog.sse('SSE connection error or ended', { sessionId: aiDeveloperAgentSessionId, error });
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
                } catch (errorHandlingError) {
                  debugLog.error('SSE', 'Error in onerror handler', { error: errorHandlingError.message });
                  console.error('Error in SSE onerror handler:', errorHandlingError);
                  cleanup();
                }
              };
              
              // Return cleanup function
              return cleanup;
              
            } catch (eventSourceError) {
              debugLog.error('SSE', 'Error creating EventSource', { error: eventSourceError.message, url: sseUrl });
              console.error('Error creating EventSource:', eventSourceError);
              cleanup();
              throw eventSourceError;
            }
            
          } catch (sseSetupError) {
            debugLog.error('SSE', 'Error setting up SSE', { error: sseSetupError.message });
            console.error('Error setting up SSE:', sseSetupError);
            set({ 
              error: sseSetupError.message || 'Failed to setup streaming connection',
              isLoading: false,
              isProcessing: false
            });
            throw sseSetupError;
          }
          
        } catch (error) {
          debugLog.error('ChatStore', 'Error sending message', { error: error.message, stack: error.stack });
          console.error('Error sending message:', error);
          set({ 
            error: error.message || 'Failed to send message',
            isLoading: false,
            isProcessing: false
          });
          return null;
        }
      },
      
      // NEW: Submit human input response
      submitHumanInput: async (responseText) => {
        try {
          const { aiDeveloperAgentSessionId, humanInputRequest } = get();
          
          if (!aiDeveloperAgentSessionId) {
            throw new Error('No active session');
          }
          
          if (!humanInputRequest) {
            throw new Error('No pending human input request');
          }
          
          if (!responseText || responseText.trim() === '') {
            throw new Error('Response cannot be empty');
          }
          
          // Clear the human input request state
          set({
            waitingForHumanInput: false,
            humanInputRequest: null
          });
          
          // Add user response message
          get().addMessage({
            id: uuidv4(),
            role: 'user',
            content: responseText.trim(),
            timestamp: new Date().toISOString()
          });
          
          // Continue with the conversation
          return get().sendMessage(responseText);
          
        } catch (error) {
          debugLog.error('ChatStore', 'Error submitting human input', { error: error.message });
          console.error('Error submitting human input:', error);
          set({ 
            error: error.message || 'Failed to submit response',
            waitingForHumanInput: false,
            humanInputRequest: null
          });
          throw error;
        }
      }
    }),
    {
      name: 'chat-store',
      partialize: (state) => ({
        aiDeveloperAgentSessionId: state.aiDeveloperAgentSessionId,
        messages: state.messages,
        toolOutputs: state.toolOutputs
      })
    }
  )
);

export default useChatStore;

