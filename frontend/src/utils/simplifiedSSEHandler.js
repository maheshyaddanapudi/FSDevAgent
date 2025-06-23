// Simplified SSE message handler to fix browser crashes
// This replaces the complex nested try-catch structure in useChatStore.js

export const createSimplifiedSSEHandler = (assistantMessageId, get, set, debugLog, cleanup) => {
  return (event) => {
    try {
      // Basic validation
      if (!event || !event.data) {
        console.warn('Invalid SSE event received:', event);
        return;
      }

      // Parse JSON data
      let data;
      try {
        data = JSON.parse(event.data);
      } catch (parseError) {
        console.warn('Failed to parse SSE data:', event.data);
        return;
      }

      // Log received data for debugging
      console.log('SSE message received:', data);

      // Handle different message types
      if (data.type === 'tool_call' && data.tool === 'message_ask_user') {
        // Human input request - close SSE and set state
        cleanup();
        set({
          waitingForHumanInput: true,
          humanInputRequest: {
            id: data.id || Date.now().toString(),
            text: data.text || 'Please provide additional information:',
            attachments: data.attachments || [],
            timestamp: new Date().toISOString()
          }
        });
        return;
      }

      // Handle message content updates
      if (data.message) {
        set(state => {
          const messages = [...state.messages];
          const messageIndex = messages.findIndex(m => m.id === assistantMessageId);
          
          if (messageIndex !== -1) {
            messages[messageIndex] = {
              ...messages[messageIndex],
              content: (messages[messageIndex].content || '') + data.message,
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
          const messageIndex = messages.findIndex(m => m.id === assistantMessageId);
          
          if (messageIndex !== -1) {
            messages[messageIndex] = {
              ...messages[messageIndex],
              toolCall: data.toolCall,
              isComplete: data.isComplete || false
            };
          }
          
          return { messages };
        });
      }

      // Handle tool outputs for emulator
      if (data.role === 'tool' || data.messageType === 'tool_result') {
        console.log('Adding tool output:', data);
        
        get().addToolOutput({
          id: data.toolCallId || Date.now().toString(),
          toolName: data.toolName || 'unknown',
          type: 'output',
          content: data.message || '',
          timestamp: data.timestamp || new Date().toISOString()
        });
      }

    } catch (error) {
      console.error('Error in SSE message handler:', error);
      // Don't crash - just log and continue
    }
  };
};

