import React, { useState, useEffect, useRef } from 'react';
import { PhaseManager } from './PhaseComponent';
import ContentDisplay from './ContentDisplay';
import { parseAgentContent } from '../utils/contentParser';
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
  const [viewMode, setViewMode] = useState('messages'); // Default to message view for proper conversation flow
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
            {message.content && (
              <div className="message-text">
                {message.role === 'assistant' ? (
                  <ContentDisplay parsedContent={parseAgentContent(message.content)} />
                ) : (
                  message.content
                )}
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
        {/* Main Chat Area - Always Visible */}
        <div className="chat-area">
          {renderTraditionalMessages()}
        </div>
        
        {/* Phase Panel - Collapsible Sidebar */}
        <div className={`phase-panel ${viewMode === 'phases' ? 'expanded' : 'minimized'}`}>
          <div className="phase-panel-header">
            <span className="phase-panel-title">📋 Project Phases</span>
            <button 
              className="phase-panel-toggle"
              onClick={handleViewModeToggle}
              title={viewMode === 'phases' ? 'Minimize phase panel' : 'Expand phase panel'}
            >
              {viewMode === 'phases' ? '▶' : '◀'}
            </button>
          </div>
          
          {viewMode === 'phases' && (
            <div className="phase-panel-content">
              <PhaseManager 
                messages={messages}
                onPhaseToggle={handlePhaseToggle}
                showAllPhases={true}
              />
            </div>
          )}
        </div>
      </div>
      
      <div ref={messagesEndRef} />
      
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

