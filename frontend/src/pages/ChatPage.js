// Update ChatPage.js to use the new UnifiedEmulator component and enhanced MessageList
import React, { useState, useEffect, useRef } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import useChatStore from '../hooks/useChatStore';
import UnifiedEmulator from '../components/UnifiedEmulator/UnifiedEmulator';
import MessageList from '../components/MessageList';
import '../styles/ChatPage.css';

const ChatPage = () => {
  // State and refs
  const [message, setMessage] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef(null);
  
  // Get chat state from store
  const { 
    messages, 
    addMessage, 
    toolOutputs,
    addToolOutput,
    isProcessing, 
    setIsProcessing 
  } = useChatStore();
  
  // WebSocket connection
  const { 
    connected: wsConnected, 
    sendMessage: wsSendMessage,
    lastMessage: wsLastMessage
  } = useWebSocket();
  
  // Handle WebSocket messages
  useEffect(() => {
    if (wsLastMessage) {
      try {
        const data = JSON.parse(wsLastMessage.data);
        
        if (data.type === 'message') {
          // Handle chat message
          addMessage({
            role: 'assistant',
            content: data.content
          });
          setIsProcessing(false);
        } else if (data.type === 'tool_output') {
          // Handle tool output
          addToolOutput(data);
        } else if (data.type === 'typing') {
          // Handle typing indicator
          setIsTyping(data.isTyping);
        }
      } catch (error) {
        console.error('Error parsing WebSocket message:', error);
      }
    }
  }, [wsLastMessage, addMessage, addToolOutput, setIsProcessing]);
  
  // Auto-scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);
  
  // Handle message submission
  const handleSubmit = (e) => {
    e.preventDefault();
    
    if (!message.trim() || !wsConnected) return;
    
    // Add user message to chat
    addMessage({
      role: 'user',
      content: message
    });
    
    // Send message to backend
    wsSendMessage(JSON.stringify({
      type: 'message',
      content: message
    }));
    
    // Clear input and set processing state
    setMessage('');
    setIsProcessing(true);
  };
  
  return (
    <div className="chat-page">
      <div className="chat-container">
        <div className="chat-messages">
          {/* Use the enhanced MessageList component */}
          <MessageList messages={messages} />
          
          {isTyping && (
            <div className="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          )}
          
          <div ref={messagesEndRef} />
        </div>
        
        <form className="chat-input" onSubmit={handleSubmit}>
          <input
            type="text"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Type your message..."
            disabled={isProcessing || !wsConnected}
          />
          
          <button 
            type="submit" 
            disabled={isProcessing || !wsConnected || !message.trim()}
          >
            Send
          </button>
        </form>
      </div>
      
      <div className="emulator-container">
        <UnifiedEmulator 
          toolOutputs={toolOutputs}
          wsConnected={wsConnected}
        />
      </div>
    </div>
  );
};

export default ChatPage;
