// Fixed ChatPage.js - Issue #5: Integration Challenges Fix
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import useChatStore from '../hooks/useChatStore';
import UnifiedEmulator from '../components/UnifiedEmulator';
import MessageList from '../components/MessageList';
import '../styles/ChatPage.css';

const ChatPage = () => {
  // State management
  const [message, setMessage] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [sessionInitialized, setSessionInitialized] = useState(false);
  const [error, setError] = useState(null);
  
  // Refs for auto-scroll
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  
  // Issue #5 Fix: Enhanced chat state management with error handling
  const { 
    sessionId,
    messages, 
    addMessage, 
    toolOutputs,
    addToolOutput,
    isProcessing, 
    setIsProcessing,
    isLoading,
    error: storeError,
    clearError,
    initializeSession,
    sendMessage: sendChatMessage
  } = useChatStore();
  
  // Issue #5 Fix: Enhanced WebSocket connection with comprehensive status
  const { 
    connected: wsConnected, 
    sendMessage: wsSendMessage,
    lastMessage: wsLastMessage,
    isReconnecting,
    reconnect: wsReconnect,
    getConnectionStatus
  } = useWebSocket();
  
  // Issue #5 Fix: Initialize session on component mount
  useEffect(() => {
    if (!sessionInitialized && !sessionId && !isLoading) {
      console.log('Initializing new session...');
      initializeSession()
        .then((newSessionId) => {
          if (newSessionId) {
            setSessionInitialized(true);
            console.log('Session initialized:', newSessionId);
          } else {
            setError('Failed to initialize session');
          }
        })
        .catch((err) => {
          console.error('Session initialization error:', err);
          setError('Failed to initialize session');
        });
    }
  }, [sessionInitialized, sessionId, isLoading, initializeSession]);

  // Issue #5 Fix: Enhanced WebSocket message handling with proper error handling
  // and real-time tool output streaming to chat window
  useEffect(() => {
    if (!wsLastMessage) return;

    try {
      const data = JSON.parse(wsLastMessage.data);
      console.log('Processing WebSocket message:', data);
      
      switch (data.type) {
        case 'message':
          if (data.content) {
            addMessage({
              role: 'assistant',
              content: data.content,
              timestamp: new Date().toISOString()
            });
            setIsProcessing(false);
            setIsTyping(false);
          }
          break;
          
        case 'tool_output':
          if (data.toolName || data.output) {
            // Add to tool outputs for terminal display
            addToolOutput({
              ...data,
              timestamp: data.timestamp || new Date().toISOString()
            });
            
            // ENHANCEMENT: Also add to message list for real-time streaming in chat window
            // Find the last assistant message to attach this tool output to
            const lastAssistantMessageIndex = [...messages].reverse().findIndex(m => m.role === 'assistant');
            
            if (lastAssistantMessageIndex !== -1) {
              const actualIndex = messages.length - 1 - lastAssistantMessageIndex;
              const updatedMessages = [...messages];
              const lastAssistantMessage = updatedMessages[actualIndex];
              
              // If message already has toolCalls array, add to it, otherwise create it
              if (!lastAssistantMessage.toolCalls) {
                lastAssistantMessage.toolCalls = [];
              }
              
              // Create a tool call object from the tool output
              const toolCall = {
                name: data.toolName || data.type || 'unknown_tool',
                id: data.id || `tool_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                arguments: data.args || {},
              };
              
              // Add tool call to the message
              lastAssistantMessage.toolCalls.push(toolCall);
              
              // Add tool result if available
              if (!lastAssistantMessage.toolResults) {
                lastAssistantMessage.toolResults = {};
              }
              
              // Format the output content
              const outputContent = typeof data.output === 'string' 
                ? data.output 
                : JSON.stringify(data.output || {});
              
              // Store the tool result
              lastAssistantMessage.toolResults[lastAssistantMessage.toolCalls.length - 1] = outputContent;
              
              // Update the message in state
              set({ messages: updatedMessages });
              console.log('Updated message with real-time tool output:', toolCall.name);
            } else {
              // If no assistant message found, create a new one with this tool output
              addMessage({
                role: 'assistant',
                content: 'Using tools to complete your request...',
                toolCalls: [{
                  name: data.toolName || data.type || 'unknown_tool',
                  id: data.id || `tool_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                  arguments: data.args || {},
                }],
                toolResults: {
                  0: typeof data.output === 'string' 
                    ? data.output 
                    : JSON.stringify(data.output || {})
                },
                timestamp: new Date().toISOString()
              });
              console.log('Created new message with tool output:', data.toolName || data.type);
            }
          }
          break;
          
        case 'typing':
          setIsTyping(Boolean(data.isTyping));
          break;
          
        case 'error':
          console.error('WebSocket error message:', data);
          setError(data.message || 'Unknown error occurred');
          setIsProcessing(false);
          setIsTyping(false);
          break;
          
        case 'status':
          // Handle status updates
          console.log('Status update:', data.status);
          break;
          
        default:
          console.warn('Unknown WebSocket message type:', data.type);
          // Try to process as tool output if it has relevant fields
          if (data.toolName || data.output) {
            addToolOutput({
              ...data,
              timestamp: data.timestamp || new Date().toISOString()
            });
          }
      }
    } catch (parseError) {
      console.error('Error parsing WebSocket message:', parseError);
      console.error('Raw message:', wsLastMessage.data);
    }
  }, [wsLastMessage, addMessage, addToolOutput, setIsProcessing, messages, set]);
  
  // Issue #5 Fix: Auto-scroll to bottom when messages change
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ 
        behavior: 'smooth',
        block: 'nearest'
      });
    }
  }, [messages, isTyping]);
  
  // Issue #5 Fix: Clear errors when they change
  useEffect(() => {
    if (storeError) {
      setError(storeError);
      // Auto-clear error after 5 seconds
      const timeout = setTimeout(() => {
        clearError();
        setError(null);
      }, 5000);
      return () => clearTimeout(timeout);
    }
  }, [storeError, clearError]);
  
  // FIXED: Modified message submission to prioritize REST API over WebSocket
  // This ensures all prompts are properly processed by the backend's ChatService
  const handleSubmit = useCallback(async (e) => {
    e.preventDefault();
    
    if (!message.trim()) {
      console.warn('Cannot send empty message');
      return;
    }
    
    if (isProcessing) {
      console.warn('Already processing a message');
      return;
    }
    
    if (!sessionId) {
      setError('No active session. Please refresh the page.');
      return;
    }
    
    const messageText = message.trim();
    console.log('Sending message:', messageText);
    
    // Clear input and errors
    setMessage('');
    setError(null);
    setIsProcessing(true);
    
    try {
      // FIXED: Always use HTTP REST API as primary method
      // This ensures the backend's ChatService.processMessage is always called
      console.log('Using HTTP REST API for message submission...');
      
      // REMOVED: Don't add user message here to prevent duplication
      // User message is already added in useChatStore.js sendMessage function
      
      // Send via REST API
      const cleanup = await sendChatMessage(messageText);
      
      if (cleanup) {
        // Store cleanup function for potential use
        window._currentChatCleanup = cleanup;
      }
      
      // REMOVED: WebSocket message sending for chat to prevent duplication
      // Only WebSocket should be used for emulator, not for chat messages
    } catch (error) {
      console.error('Error sending message:', error);
      setError(`Failed to send message: ${error.message}`);
      setIsProcessing(false);
      setIsTyping(false);
    }
  }, [message, isProcessing, sessionId, wsConnected, wsSendMessage, addMessage, sendChatMessage]);
  
  // Issue #5 Fix: Handle keyboard shortcuts
  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    } else if (e.key === 'Escape') {
      setMessage('');
      setError(null);
    }
  }, [handleSubmit]);
  
  // Issue #5 Fix: Focus input on component mount
  useEffect(() => {
    if (inputRef.current && sessionInitialized) {
      inputRef.current.focus();
    }
  }, [sessionInitialized]);
  
  // Issue #5 Fix: Connection retry function
  const handleConnectionRetry = useCallback(() => {
    console.log('Retrying connection...');
    setError(null);
    wsReconnect();
    
    // Also try to reinitialize session if needed
    if (!sessionId) {
      initializeSession();
    }
  }, [wsReconnect, sessionId, initializeSession]);
  
  // Issue #5 Fix: Render connection status
  const renderConnectionStatus = () => {
    const status = getConnectionStatus();
    
    if (!wsConnected && !isReconnecting) {
      return (
        <div className="connection-warning">
          <span>⚠️ WebSocket disconnected</span>
          <button onClick={handleConnectionRetry} className="retry-btn">
            Retry
          </button>
        </div>
      );
    }
    
    if (isReconnecting) {
      return (
        <div className="connection-warning">
          <span>🔄 Reconnecting...</span>
        </div>
      );
    }
    
    return null;
  };
  
  // Issue #5 Fix: Render error message
  const renderError = () => {
    if (!error) return null;
    
    return (
      <div className="error-message">
        <span>❌ {error}</span>
        <button onClick={() => setError(null)} className="close-btn">
          ✕
        </button>
      </div>
    );
  };
  
  // Issue #5 Fix: Loading state for session initialization
  if (!sessionInitialized && isLoading) {
    return (
      <div className="chat-page loading">
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>Initializing AI Developer Agent...</p>
        </div>
      </div>
    );
  }
  
  // Issue #5 Fix: Error state for failed session initialization
  if (!sessionInitialized && error) {
    return (
      <div className="chat-page error">
        <div className="error-container">
          <h2>Failed to Initialize</h2>
          <p>{error}</p>
          <button onClick={() => window.location.reload()} className="retry-button">
            Reload Page
          </button>
        </div>
      </div>
    );
  }
  
  return (
    <div className="chat-page">
      {/* Chat Section */}
      <div className="chat-container">
        {/* Connection and Error Status */}
        {renderConnectionStatus()}
        {renderError()}
        
        {/* Messages Area */}
        <div className="chat-messages">
          <MessageList messages={messages} />
          
          {/* Typing Indicator */}
          {isTyping && (
            <div className="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
              <div className="typing-text">AI is thinking...</div>
            </div>
          )}
          
          {/* Auto-scroll anchor */}
          <div ref={messagesEndRef} />
        </div>
        
        {/* Input Area */}
        <form className="chat-input" onSubmit={handleSubmit}>
          <input
            ref={inputRef}
            type="text"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={
              !sessionId 
                ? "Initializing session..." 
                : !wsConnected 
                  ? "Type your message (WebSocket disconnected)..."
                  : "Type your message..."
            }
            disabled={isProcessing || !sessionId}
            maxLength={4000}
          />
          
          <button 
            type="submit" 
            disabled={isProcessing || !sessionId || !message.trim()}
            className={isProcessing ? 'processing' : ''}
          >
            {isProcessing ? (
              <span className="button-spinner">⟳</span>
            ) : (
              'Send'
            )}
          </button>
        </form>
      </div>
      
      {/* Emulator Section */}
      <div className="emulator-container">
        <UnifiedEmulator 
          toolOutputs={toolOutputs}
          wsConnected={wsConnected}
          sessionId={sessionId}
        />
      </div>
    </div>
  );
};

export default ChatPage;
