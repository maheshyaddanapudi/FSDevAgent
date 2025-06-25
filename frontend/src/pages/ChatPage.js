import React, { useState, useEffect, useRef, useCallback } from 'react';
import useChatStore from '../hooks/useChatStore';
import UnifiedEmulator from '../components/UnifiedEmulator/UnifiedEmulator';
import MessageList from '../components/MessageList';
import EnhancedHeader from '../components/EnhancedHeader';
import EnhancedMessageList from '../components/EnhancedMessageList';
import '../styles/ChatPage.css';
import '../styles/enhanced-error-handling.css';

// Enhanced Error Alert Component
const ErrorAlert = ({ error, onRetry, onDismiss, showDetails, onToggleDetails }) => {
  if (!error) return null;
  
  return (
    <div className="error-alert">
      <div className="error-header">
        <span className="error-icon">⚠️</span>
        <span className="error-title">Connection Issue</span>
        <button className="error-dismiss" onClick={onDismiss}>✕</button>
      </div>
      <div className="error-message">
        {error.userMessage || 'Unable to connect to the AI Developer Agent service'}
      </div>
      <div className="error-actions">
        <button className="retry-button" onClick={onRetry}>
          🔄 Retry Connection
        </button>
        <button className="details-button" onClick={onToggleDetails}>
          {showDetails ? '📄 Hide Details' : '🔍 Show Details'}
        </button>
      </div>
      {showDetails && (
        <div className="error-details">
          <strong>Technical Details:</strong>
          <pre>{error.technicalDetails || error.message || 'Unknown error'}</pre>
        </div>
      )}
    </div>
  );
};

const ChatPage = () => {
  // State management
  const [message, setMessage] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [sessionInitialized, setSessionInitialized] = useState(false);
  const [connectionError, setConnectionError] = useState(null);
  const [showErrorDetails, setShowErrorDetails] = useState(false);
  
  // Enhanced UI state
  const [useEnhancedUI, setUseEnhancedUI] = useState(true);
  const [currentTask, setCurrentTask] = useState('AI Developer Agent Session');
  
  // Refs for auto-scroll
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  
  // Issue #5 Fix: Enhanced chat state management with error handling
  const { 
    aiDeveloperAgentSessionId,
    setAiDeveloperAgentSessionId,
    messages, 
    addMessage, 
    toolOutputs,
    addToolOutput,
    isProcessing, 
    setIsProcessing,
    isLoading,
    error: storeError,
    clearError,
    clearChat,
    initializeSession,
    sendMessage: sendChatMessage,
    // NEW: Human-in-the-loop state and actions
    waitingForHumanInput,
    humanInputRequest,
    submitHumanInput,
    cancelHumanInputRequest
  } = useChatStore();

  // Enhanced session initialization with better error handling
  const handleSessionInitialization = useCallback(async () => {
    // Check if we already have a persisted session
    if (aiDeveloperAgentSessionId) {
      console.log('Using persisted session:', aiDeveloperAgentSessionId);
      setSessionInitialized(true);
      setConnectionError(null);
      return;
    }
    
    if (sessionInitialized || isLoading) {
      return;
    }

    try {
      console.log('Attempting to initialize new session...');
      const newSessionId = await initializeSession();
      
      if (newSessionId) {
        setSessionInitialized(true);
        setConnectionError(null);
        console.log('Session initialized successfully:', newSessionId);
      } else {
        throw new Error('Session initialization returned empty response');
      }
    } catch (err) {
      console.error('Session initialization failed:', err);
      
      // Create user-friendly error message
      const userError = {
        userMessage: 'Failed to connect to AI Developer Agent service',
        technicalDetails: `${err.message}\n\nThis could be due to:\n- Backend service not running\n- Network connectivity issues\n- API endpoint configuration problems`,
        originalError: err
      };
      
      setConnectionError(userError);
      setSessionInitialized(false); // Allow retry
    }
  }, [aiDeveloperAgentSessionId, sessionInitialized, isLoading, initializeSession]);

  // Retry connection handler
  const handleRetryConnection = useCallback(() => {
    setConnectionError(null);
    setSessionInitialized(false);
    clearError();
    handleSessionInitialization();
  }, [handleSessionInitialization, clearError]);

  // Initialize session on component mount
  useEffect(() => {
    handleSessionInitialization();
  }, [handleSessionInitialization]);

  // WebSocket message processing removed - now using SSE only
  useEffect(() => {
    // Tool events now handled by UnifiedEmulator via SSE
    // Chat messages already use SSE via useChatStore
  }, [addMessage, addToolOutput, setIsProcessing]);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, toolOutputs]);

  // Enhanced message sending with better error handling
  const handleSendMessage = useCallback(async () => {
    if (!message.trim()) return;
    
    // Check if session is initialized
    if (!sessionInitialized || !aiDeveloperAgentSessionId) {
      setConnectionError({
        userMessage: 'Session not initialized. Please retry connection.',
        technicalDetails: 'No active session ID available for sending messages.'
      });
      return;
    }

    const userMessage = message.trim();
    setMessage('');
    setIsProcessing(true);
    setIsTyping(true);

    try {
      // Send message via chat service (sendChatMessage handles all message state management)
      await sendChatMessage(userMessage);
    } catch (error) {
      console.error('Error sending message:', error);
      
      setConnectionError({
        userMessage: 'Failed to send message to AI Developer Agent',
        technicalDetails: `${error.message}\n\nThe message could not be delivered. Please check your connection and try again.`
      });
      
      setIsProcessing(false);
      setIsTyping(false);
    }
  }, [message, sessionInitialized, aiDeveloperAgentSessionId, addMessage, sendChatMessage, setIsProcessing]);

  // Handle Enter key press
  const handleKeyPress = useCallback((e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  }, [handleSendMessage]);

  // Clear messages handler - clears Zustand store and refreshes page
  const handleClearMessages = useCallback(() => {
    try {
      console.log('Clearing chat store and local storage...');
      
      // Clear the Zustand chat store
      clearChat();
      
      // Reset session ID to null
      setAiDeveloperAgentSessionId(null);
      
      // Clear all local storage (including Zustand persistence)
      localStorage.clear();
      
      // Clear session storage as well
      sessionStorage.clear();
      
      console.log('Store and storage cleared, refreshing page...');
      
      // Small delay to ensure state is cleared before refresh
      setTimeout(() => {
        window.location.reload();
      }, 100);
      
    } catch (error) {
      console.error('Error clearing store and storage:', error);
      // Still try to refresh even if clearing fails
      setTimeout(() => {
        window.location.reload();
      }, 100);
    }
  }, [clearChat]);

  // Refresh handler
  const handleRefresh = useCallback(() => {
    window.location.reload();
  }, []);

  // Human input submission handler
  const handleHumanInputSubmit = useCallback((inputText) => {
    if (!inputText.trim()) return;
    
    try {
      submitHumanInput(inputText);
    } catch (error) {
      console.error('Error submitting human input:', error);
      setConnectionError({
        userMessage: 'Failed to submit human input response',
        technicalDetails: error.message
      });
    }
  }, [submitHumanInput]);

  // Determine if the interface should be disabled
  const isInterfaceDisabled = !sessionInitialized || isLoading || !!connectionError;

  return (
    <div className="chat-page">
      {/* Enhanced Header */}
      <EnhancedHeader
        sessionInitialized={sessionInitialized}
        isLoading={isLoading}
        connectionError={connectionError}
        onClearMessages={handleClearMessages}
        onRefresh={handleRefresh}
        currentTask={currentTask}
      />

      {/* Enhanced Error Alert */}
      <ErrorAlert 
        error={connectionError || storeError}
        onRetry={handleRetryConnection}
        onDismiss={() => {
          setConnectionError(null);
          clearError();
        }}
        showDetails={showErrorDetails}
        onToggleDetails={() => setShowErrorDetails(!showErrorDetails)}
      />

      {/* Main Content - 50/50 Split */}
      <div className="main-content">
        {/* Left Half - Chat Interface */}
        <div className="chat-section">
          <div className="messages-container">
            {!sessionInitialized && !connectionError && (
              <div className="welcome-message">
                <h2>Welcome to AI Developer Agent</h2>
                <p>Ask me anything about development, and I'll help you with coding, debugging, and using various tools.</p>
              </div>
            )}
            
            {sessionInitialized && (
              <>
                {useEnhancedUI ? (
                  <EnhancedMessageList 
                    messages={messages}
                    usePhaseOrganization={true}
                    showTimestamps={true}
                    autoScroll={true}
                  />
                ) : (
                  <MessageList messages={messages} />
                )}
                
                {/* Human-in-the-loop UI */}
                {waitingForHumanInput && humanInputRequest && (
                  <div className="human-input-request">
                    <div className="human-input-header">
                      <h3>🤖 AI Agent needs your input</h3>
                      <p>{humanInputRequest.message}</p>
                    </div>
                    
                    {humanInputRequest.attachments && humanInputRequest.attachments.length > 0 && (
                      <div className="human-input-attachments">
                        <h4>Related files:</h4>
                        <ul>
                          {humanInputRequest.attachments.map((attachment, index) => (
                            <li key={index}>
                              <a href={attachment.url} target="_blank" rel="noopener noreferrer">
                                {attachment.name}
                              </a>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
                
                <div ref={messagesEndRef} />
              </>
            )}
          </div>

          {/* Chat Input Section */}
          <div className="chat-input-section">
            <div className="input-controls">
              <button 
                className={`control-button ui-toggle ${useEnhancedUI ? 'enhanced' : 'traditional'}`}
                onClick={() => setUseEnhancedUI(!useEnhancedUI)}
                title={`Switch to ${useEnhancedUI ? 'traditional' : 'enhanced'} UI`}
              >
                {useEnhancedUI ? '📋' : '💬'}
              </button>
            </div>
            
            <div className={`chat-input ${waitingForHumanInput ? 'human-input-mode' : ''}`}>
              <input
                ref={inputRef}
                type="text"
                value={waitingForHumanInput ? '' : message}
                onChange={(e) => !waitingForHumanInput && setMessage(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder={
                  waitingForHumanInput ? 'Waiting for your response above...' :
                  isInterfaceDisabled ? 'Please retry connection to send messages...' :
                  'Type your message...'
                }
                disabled={isInterfaceDisabled}
                className={waitingForHumanInput ? 'human-input' : ''}
              />
              <button 
                onClick={waitingForHumanInput ? 
                  () => handleHumanInputSubmit(message) : 
                  handleSendMessage
                }
                disabled={isInterfaceDisabled || (!message.trim() && !waitingForHumanInput)}
                className={`send-button ${isProcessing && !waitingForHumanInput ? 'processing' : ''} ${waitingForHumanInput ? 'human-input-button' : ''}`}
              >
                {isProcessing && !waitingForHumanInput ? '⏳' : 
                 waitingForHumanInput ? '📤' : 'Send'}
              </button>
            </div>
            
            {waitingForHumanInput && (
              <button 
                className="cancel-human-input"
                onClick={cancelHumanInputRequest}
              >
                Cancel Request
              </button>
            )}
          </div>
        </div>

        {/* Right Half - Enhanced Emulator */}
        <div className="emulator-section">
          <UnifiedEmulator 
            toolOutputs={toolOutputs} 
          />
        </div>
      </div>
    </div>
  );
};

export default ChatPage;

