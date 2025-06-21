import React, { useState, useEffect } from 'react';
import EnhancedMessageList from './EnhancedMessageList';
import TerminalEmulator from './TerminalEmulator';
import EmulatorErrorBoundary from './EmulatorErrorBoundary';
import { Send } from 'react-feather';
import { debugLog } from '../utils/debugLogger';

const ChatInterface = ({ sessionId }) => {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [terminalOutput, setTerminalOutput] = useState('');
  const [error, setError] = useState(null);
  
  
  
  
  // Process tool output messages with comprehensive error handling
  useEffect(() => {
    try {
      if (toolMessage) {
        try {
          
          const data = JSON.parse(toolMessage.data);
          debugLog.emulator('Processing tool output', { sessionId, parsedData: data });
          
          if (data.sessionId === sessionId) {
            setTerminalOutput(prev => {
              try {
                const newOutput = prev + data.content + '\\n';
                debugLog.emulator('Updated terminal output', { 
                  sessionId, 
                  oldLength: prev.length, 
                  newLength: newOutput.length 
                });
                return newOutput;
              } catch (updateError) {
                debugLog.error('ChatInterface', 'Error updating terminal output', {
                  sessionId,
                  error: updateError.message,
                  data
                });
                console.error('Error updating terminal output:', updateError);
                return prev; // Return previous state on error
              }
            });
          }
        } catch (parseError) {
          debugLog.error('ChatInterface', 'Error parsing tool message', {
            sessionId,
            error: parseError.message,
            rawData: toolMessage.data
          });
          console.error('Error parsing tool message:', parseError);
          setError('Error processing tool output');
        }
      }
    } catch (error) {
      debugLog.error('ChatInterface', 'Error in tool message effect', {
        sessionId,
        error: error.message
      });
      console.error('Error in tool message effect:', error);
      setError('Error handling tool messages');
    }
  }, [toolMessage, sessionId]);
  
  // Process agent state messages with comprehensive error handling
  useEffect(() => {
    try {
      if (agentStateMessage) {
        try {
          
          const data = JSON.parse(agentStateMessage.data);
          debugLog.emulator('Processing agent state', { sessionId, parsedData: data });
          
          if (data.sessionId === sessionId) {
            // Handle agent state updates here
            debugLog.emulator('Agent state updated', { sessionId, state: data.state });
          }
        } catch (parseError) {
          debugLog.error('ChatInterface', 'Error parsing agent state message', {
            sessionId,
            error: parseError.message,
            rawData: agentStateMessage.data
          });
          console.error('Error parsing agent state message:', parseError);
          setError('Error processing agent state');
        }
      }
    } catch (error) {
      debugLog.error('ChatInterface', 'Error in agent state effect', {
        sessionId,
        error: error.message
      });
      console.error('Error in agent state effect:', error);
      setError('Error handling agent state messages');
    }
  }, [agentStateMessage, sessionId]);
  
  useEffect(() => {
    try {
      if (toolWsError) {
        setError(`Tool connection error: ${toolWsError}`);
      }
      if (agentWsError) {
        setError(`Agent connection error: ${agentWsError}`);
      }
    } catch (error) {
        sessionId,
        error: error.message
      });
    }
  }, [toolWsError, agentWsError, sessionId]);
  
  // Handle input change with error handling
  const handleInputChange = (e) => {
    try {
      setInput(e.target.value);
      setError(null); // Clear any previous errors
    } catch (error) {
      debugLog.error('ChatInterface', 'Error handling input change', {
        sessionId,
        error: error.message
      });
      console.error('Error handling input change:', error);
    }
  };
  
  // Handle form submission with error handling
  const handleSubmit = async (e) => {
    try {
      e.preventDefault();
      
      if (!input.trim()) {
        return;
      }
      
      const messageText = input.trim();
      setInput('');
      setError(null);
      setIsStreaming(true);
      
      try {
        // Add user message
        const userMessage = {
          id: Date.now(),
          role: 'user',
          content: messageText,
          timestamp: new Date().toISOString()
        };
        
        setMessages(prev => [...prev, userMessage]);
        
        // NOTE: Actual message sending should be handled by useChatStore via SSE
        // This is just for UI state management
        debugLog.chat('Message submitted', { sessionId, message: messageText });
        
      } catch (messageError) {
        debugLog.error('ChatInterface', 'Error processing message submission', {
          sessionId,
          error: messageError.message,
          message: messageText
        });
        console.error('Error processing message submission:', messageError);
        setError('Error sending message');
      } finally {
        setIsStreaming(false);
      }
      
    } catch (error) {
      debugLog.error('ChatInterface', 'Error in form submission', {
        sessionId,
        error: error.message
      });
      console.error('Error in form submission:', error);
      setError('Error submitting form');
      setIsStreaming(false);
    }
  };
  
  // Clear error function
  const clearError = () => {
    try {
      setError(null);
    } catch (error) {
      debugLog.error('ChatInterface', 'Error clearing error state', {
        sessionId,
        error: error.message
      });
      console.error('Error clearing error state:', error);
    }
  };
  
  return (
    <EmulatorErrorBoundary>
      <div className="chat-interface">
        {/* Error Display */}
        {error && (
          <div className="error-banner" style={{
            backgroundColor: '#f8d7da',
            color: '#721c24',
            padding: '10px',
            margin: '10px 0',
            borderRadius: '4px',
            border: '1px solid #f5c6cb'
          }}>
            <strong>Error:</strong> {error}
            <button 
              onClick={clearError}
              style={{
                marginLeft: '10px',
                background: 'none',
                border: 'none',
                color: '#721c24',
                cursor: 'pointer',
                textDecoration: 'underline'
              }}
            >
              Dismiss
            </button>
          </div>
        )}
        
        {/* Chat Messages */}
        <div className="chat-messages">
            <EnhancedMessageList 
              messages={messages} 
              isStreaming={isStreaming}
              sessionId={sessionId}
            />
        </div>
        
        {/* Terminal Emulator */}
        <div className="terminal-section">
          <EmulatorErrorBoundary>
              <TerminalEmulator 
                output={terminalOutput}
                sessionId={sessionId}
              />
          </EmulatorErrorBoundary>
        </div>
        
        {/* Input Form */}
        <form onSubmit={handleSubmit} className="chat-input-form">
          <div className="input-container">
            <input
              type="text"
              value={input}
              onChange={handleInputChange}
              placeholder="Type your message..."
              disabled={isStreaming}
              className="chat-input"
            />
            <button 
              type="submit" 
              disabled={isStreaming || !input.trim()}
              className="send-button"
            >
              {isStreaming ? '⏳' : <Send size={20} />}
            </button>
          </div>
        </form>
      </div>
    </EmulatorErrorBoundary>
  );
};

export default ChatInterface;

