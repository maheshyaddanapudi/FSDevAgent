import React, { useState, useEffect, useRef } from 'react';
import useChatStore from '../hooks/useChatStore';
import useWebSocket from '../hooks/useWebSocket';
import ChatInput from '../components/ChatInput';
import MessageList from '../components/MessageList';
import MinimalEmulator from '../components/MinimalEmulator';
import Header from '../components/Header';
import '../styles/ChatPage.css';

const ChatPage = () => {
  const { 
    sessionId, 
    messages, 
    isLoading, 
    error, 
    toolOutputs,
    initializeSession, 
    sendMessage, 
    executeTool 
  } = useChatStore();
  
  const messagesEndRef = useRef(null);
  
  // Add state for active view
  const [activeView, setActiveView] = useState('chat');
  
  // Connect to WebSocket for real-time tool outputs
  // Removed sessionId parameter to make connection session-independent
  const { 
    isConnected: wsConnected, 
    error: wsError, 
    messages: wsMessages 
  } = useWebSocket('tools');

  useEffect(() => {
    if (!sessionId) {
      initializeSession();
    }
  }, [sessionId, initializeSession]);

  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);
  
  // Process WebSocket messages for tool outputs
  useEffect(() => {
    if (wsMessages.length > 0) {
      const latestMessage = wsMessages[wsMessages.length - 1];
      console.log('Received tool output via WebSocket:', latestMessage);
      
      // Validate and sanitize the WebSocket message before adding to store
      try {
        // Ensure the message has the required structure
        if (!latestMessage) {
          console.warn('Empty WebSocket message received');
          return;
        }
        
        // Ensure output property exists
        if (!latestMessage.output && latestMessage.data) {
          latestMessage.output = latestMessage.data;
        }
        
        // Ensure toolName property exists
        if (!latestMessage.toolName && latestMessage.type) {
          latestMessage.toolName = latestMessage.type;
        }
        
        console.log('Adding validated message to toolOutputs:', latestMessage);
        
        // Add validated message to tool outputs in store
        useChatStore.setState(state => ({
          toolOutputs: [...state.toolOutputs, latestMessage]
        }));
        
        // Find existing assistant message to update with tool result
        // instead of creating a new tool message
        useChatStore.setState(state => {
          const outputContent = latestMessage.output || latestMessage.data;
          const toolCallId = latestMessage.toolCallId;
          
          // Find the last assistant message
          const lastAssistantIndex = [...state.messages].reverse()
            .findIndex(msg => msg.role === 'assistant');
          
          if (lastAssistantIndex !== -1) {
            // Convert from reverse index to actual index
            const assistantIndex = state.messages.length - 1 - lastAssistantIndex;
            const assistantMessage = state.messages[assistantIndex];
            
            // Create a copy of the messages array
            const updatedMessages = [...state.messages];
            
            // Update the assistant message with tool result
            updatedMessages[assistantIndex] = {
              ...assistantMessage,
              toolResult: typeof outputContent === 'string' 
                ? outputContent 
                : JSON.stringify(outputContent, null, 2),
              toolName: latestMessage.toolName
            };
            
            return { messages: updatedMessages };
          }
          
          // Fallback: If no assistant message found, create a tool message
          return {
            messages: [...state.messages, {
              role: 'tool',
              content: `Tool Result (${latestMessage.toolName})`,
              toolResult: typeof outputContent === 'string' 
                ? outputContent 
                : JSON.stringify(outputContent, null, 2),
              timestamp: new Date().toISOString()
            }]
          };
        });
      } catch (error) {
        console.error('Error processing WebSocket message:', error);
      }
    }
  }, [wsMessages]);

  const handleSendMessage = (message) => {
    sendMessage(message);
  };

  return (
    <div className="chat-page">
      <Header activeView={activeView} setActiveView={setActiveView} />
      
      <div className="split-view">
        <div className="chat-container">
          <MessageList messages={messages} />
          <div ref={messagesEndRef} />
          <ChatInput onSendMessage={handleSendMessage} isLoading={isLoading} />
          {error && <div className="error-message">{error}</div>}
        </div>
        
        <div className="tool-container">
          <MinimalEmulator 
            toolOutputs={toolOutputs} 
            wsConnected={wsConnected}
            currentToolType={activeView !== 'chat' ? activeView : 'terminal'}
          />
          {wsError && <div className="ws-error">WebSocket Error: {wsError}</div>}
        </div>
      </div>
    </div>
  );
};

export default ChatPage;
