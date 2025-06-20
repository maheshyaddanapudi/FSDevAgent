import React, { useState, useEffect } from 'react';
import useWebSocket from '../hooks/useWebSocket';
import EnhancedMessageList from './EnhancedMessageList';
import TerminalEmulator from './TerminalEmulator';
import { Send } from 'react-feather';

const ChatInterface = ({ sessionId }) => {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [terminalOutput, setTerminalOutput] = useState('');
  
  // WebSocket for chat messages
  const { lastMessage: chatMessage } = useWebSocket(`/ws/chat/${sessionId}`);
  
  // WebSocket for tool output
  const { lastMessage: toolMessage } = useWebSocket(`/ws/tool-output`);
  
  // WebSocket for agent state
  const { lastMessage: agentStateMessage } = useWebSocket(`/ws/agent-state`);
  
  // Process chat messages
  useEffect(() => {
    if (chatMessage) {
      try {
        const data = JSON.parse(chatMessage.data);
        
        if (data.messageType === 'stream-start') {
          setIsStreaming(true);
        } else if (data.messageType === 'stream-end') {
          setIsStreaming(false);
        } else if (data.messageType === 'message') {
          // Handle new message
          setMessages(prev => {
            // Check if this is a continuation of a streaming message
            if (isStreaming && data.role === 'assistant' && prev.length > 0 && prev[prev.length - 1].role === 'assistant') {
              const updatedMessages = [...prev];
              updatedMessages[updatedMessages.length - 1] = {
                ...updatedMessages[updatedMessages.length - 1],
                message: (updatedMessages[updatedMessages.length - 1].message || '') + data.message
              };
              return updatedMessages;
            } else {
              // New message
              return [...prev, data];
            }
          });
        }
      } catch (error) {
        console.error('Error parsing chat message:', error);
      }
    }
  }, [chatMessage, isStreaming]);
  
  // Process tool output
  useEffect(() => {
    if (toolMessage) {
      try {
        const data = JSON.parse(toolMessage.data);
        if (data.sessionId === sessionId) {
          setTerminalOutput(prev => prev + data.content + '\n');
        }
      } catch (error) {
        console.error('Error parsing tool message:', error);
      }
    }
  }, [toolMessage, sessionId]);
  
  // Process agent state updates
  useEffect(() => {
    if (agentStateMessage) {
      try {
        const data = JSON.parse(agentStateMessage.data);
        if (data.sessionId === sessionId) {
          // Update UI based on agent state
          console.log('Agent state update:', data);
          // Could update progress indicators, mode displays, etc.
        }
      } catch (error) {
        console.error('Error parsing agent state message:', error);
      }
    }
  }, [agentStateMessage, sessionId]);
  
  // Handle sending messages
  const handleSendMessage = () => {
    if (!input.trim()) return;
    
    // Add user message to local state immediately
    const userMessage = {
      role: 'user',
      message: input,
      timestamp: new Date().toISOString()
    };
    setMessages(prev => [...prev, userMessage]);
    
    // Send message to backend
    fetch(`/api/chat/${sessionId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ message: input })
    }).catch(error => {
      console.error('Error sending message:', error);
    });
    
    // Clear input
    setInput('');
  };
  
  return (
    <div className="chat-interface flex flex-col h-full">
      <div className="flex-1 overflow-y-auto p-4">
        <EnhancedMessageList messages={messages} isStreaming={isStreaming} />
      </div>
      
      {/* Terminal section */}
      <div className="terminal-section bg-gray-900 p-4">
        <h3 className="text-white text-sm font-medium mb-2">Terminal Output</h3>
        <TerminalEmulator sessionId={sessionId} toolOutput={terminalOutput} />
      </div>
      
      {/* Input section */}
      <div className="input-section p-4 border-t border-gray-200 dark:border-gray-700">
        <div className="flex items-center">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
            placeholder="Type your message..."
            className="flex-1 p-2 border border-gray-300 dark:border-gray-600 rounded-l-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-white"
          />
          <button
            onClick={handleSendMessage}
            className="bg-blue-600 hover:bg-blue-700 text-white p-2 rounded-r-md"
          >
            <Send size={20} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default ChatInterface;
