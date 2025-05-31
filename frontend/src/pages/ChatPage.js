import React, { useState, useEffect, useRef } from 'react';
import useChatStore from '../hooks/useChatStore';
import useWebSocket from '../hooks/useWebSocket';
import ChatInput from '../components/ChatInput';
import MessageList from '../components/MessageList';
import ToolOutput from '../components/ToolOutput';
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
  
  const [activeView, setActiveView] = useState('chat'); // 'chat', 'terminal', 'browser', 'code'
  const messagesEndRef = useRef(null);
  
  // Connect to WebSocket for real-time tool outputs
  const { 
    isConnected: wsConnected, 
    error: wsError, 
    messages: wsMessages 
  } = useWebSocket('tools', sessionId);

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
      
      // Add to tool outputs in store
      useChatStore.setState(state => ({
        toolOutputs: [...state.toolOutputs, latestMessage]
      }));
    }
  }, [wsMessages]);

  const handleSendMessage = (message) => {
    sendMessage(message);
  };

  const handleToolExecution = (toolName, args) => {
    executeTool(toolName, args);
  };

  const renderToolView = () => {
    // Show WebSocket connection status
    if (wsError) {
      console.error('WebSocket error:', wsError);
    }
    
    if (toolOutputs.length === 0) {
      return (
        <div className="empty-state">
          <div>No tool output yet</div>
          <div className="ws-status">
            WebSocket: {wsConnected ? 'Connected ✅' : 'Disconnected ❌'}
          </div>
        </div>
      );
    }

    const latestOutput = toolOutputs[toolOutputs.length - 1];
    return <ToolOutput output={latestOutput} />;
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
          {renderToolView()}
        </div>
      </div>
    </div>
  );
};

export default ChatPage;
