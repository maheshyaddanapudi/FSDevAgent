import React, { useState, useEffect, useRef } from 'react';
import useChatStore from '../hooks/useChatStore';
import useWebSocket from '../hooks/useWebSocket';
import ChatInput from '../components/ChatInput';
import MessageList from '../components/MessageList';
import UnifiedEmulator from '../components/UnifiedEmulator';
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
        
        // Add validated message to tool outputs in store
        useChatStore.setState(state => ({
          toolOutputs: [...state.toolOutputs, latestMessage]
        }));
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
          <UnifiedEmulator 
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
