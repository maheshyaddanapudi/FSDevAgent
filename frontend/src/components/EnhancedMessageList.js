import React, { useState, useEffect, useRef } from 'react';
import { PhaseManager } from './PhaseComponent';
import '../styles/EnhancedMessageList.css';

/**
 * Enhanced Message List Component based on wireframe specifications
 * Provides phase-based organization with fallback to traditional message display
 */
const EnhancedMessageList = ({ 
  messages = [], 
  usePhaseOrganization = true,
  showTimestamps = true,
  autoScroll = true 
}) => {
  const [viewMode, setViewMode] = useState(usePhaseOrganization ? 'phases' : 'messages');
  const [phaseStates, setPhaseStates] = useState({});
  const messagesEndRef = useRef(null);
  const containerRef = useRef(null);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (autoScroll && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, autoScroll]);

  const handlePhaseToggle = (phaseId, isExpanded) => {
    setPhaseStates(prev => ({
      ...prev,
      [phaseId]: isExpanded
    }));
  };

  const handleViewModeToggle = () => {
    setViewMode(prev => prev === 'phases' ? 'messages' : 'phases');
  };

  const formatTimestamp = (timestamp) => {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const getMessageTypeClass = (messageType) => {
    switch (messageType) {
      case 'error':
        return 'message-error';
      case 'system':
        return 'message-system';
      case 'thinking':
        return 'message-thinking';
      case 'tool_result':
        return 'message-tool-result';
      case 'agent_paused':
        return 'message-agent-paused';
      case 'agent_resumed':
        return 'message-agent-resumed';
      case 'resume_guidance':
        return 'message-resume-guidance';
      default:
        return 'message-default';
    }
  };

  const getMessageIcon = (role, messageType) => {
    if (messageType === 'error') return '❌';
    if (messageType === 'system') return '⚙️';
    if (messageType === 'thinking') return '🔵';
    if (messageType === 'tool_result') return '🔧';
    if (messageType === 'agent_paused') return '🛑';
    if (messageType === 'agent_resumed') return '✅';
    if (messageType === 'resume_guidance') return '💡';
    
    switch (role) {
      case 'user':
        return '👤';
      case 'assistant':
        return '🤖';
      case 'system':
        return '⚙️';
      default:
        return '💬';
    }
  };

  const renderTraditionalMessages = () => (
    <div className="traditional-messages">
      {messages.map((message, index) => (
        <div 
          key={message.id || index} 
          className={`message-item ${getMessageTypeClass(message.messageType)} role-${message.role}`}
        >
          <div className="message-header">
            <div className="message-avatar">
              <span className="message-icon">
                {getMessageIcon(message.role, message.messageType)}
              </span>
            </div>
            <div className="message-meta">
              <span className="message-role">
                {message.role === 'user' ? 'You' : 
                 message.role === 'assistant' ? 'AI Agent' : 
                 'System'}
              </span>
              {showTimestamps && message.timestamp && (
                <span className="message-timestamp">
                  {formatTimestamp(message.timestamp)}
                </span>
              )}
            </div>
          </div>
          
          <div className="message-content">
            {message.message && (
              <div className="message-text">
                {message.message}
              </div>
            )}
            
            {message.messageType === 'error_details' && (
              <details className="error-details">
                <summary>Technical Details</summary>
                <pre className="error-details-content">{message.message}</pre>
              </details>
            )}
          </div>
        </div>
      ))}
    </div>
  );

  return (
    <div className="enhanced-message-list" ref={containerRef}>
      <div className="message-list-header">
        <div className="view-controls">
          <button 
            className={`view-toggle ${viewMode === 'phases' ? 'active' : ''}`}
            onClick={handleViewModeToggle}
            title="Toggle between phase view and message view"
          >
            <span className="toggle-icon">
              {viewMode === 'phases' ? '📋' : '💬'}
            </span>
            <span className="toggle-text">
              {viewMode === 'phases' ? 'Phase View' : 'Message View'}
            </span>
          </button>
        </div>
        
        <div className="message-stats">
          <span className="message-count">
            {messages.length} message{messages.length !== 1 ? 's' : ''}
          </span>
        </div>
      </div>
      
      <div className="message-list-content">
        {viewMode === 'phases' ? (
          <div className="phase-view">
            <PhaseManager 
              messages={messages}
              onPhaseToggle={handlePhaseToggle}
            />
            
            {/* Show recent messages that don't fit into phases */}
            {messages.length > 0 && (
              <div className="recent-messages">
                {messages.slice(-3).map((message, index) => (
                  <div 
                    key={`recent-${index}`} 
                    className={`recent-message ${getMessageTypeClass(message.messageType)}`}
                  >
                    <span className="recent-icon">
                      {getMessageIcon(message.role, message.messageType)}
                    </span>
                    <span className="recent-text">
                      {message.message && message.message.length > 100 
                        ? message.message.substring(0, 100) + '...'
                        : message.message}
                    </span>
                    {showTimestamps && message.timestamp && (
                      <span className="recent-timestamp">
                        {formatTimestamp(message.timestamp)}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          renderTraditionalMessages()
        )}
        
        <div ref={messagesEndRef} />
      </div>
      
      {messages.length === 0 && (
        <div className="empty-state">
          <div className="empty-icon">💬</div>
          <div className="empty-title">No messages yet</div>
          <div className="empty-subtitle">
            Start a conversation with the AI Developer Agent
          </div>
        </div>
      )}
    </div>
  );
};

export default EnhancedMessageList;

