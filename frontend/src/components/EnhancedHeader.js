import React from 'react';
import '../styles/EnhancedHeader.css';

/**
 * Enhanced Header Component based on wireframe specifications
 * Provides branding, task context, and action buttons while maintaining
 * compatibility with existing session management
 */
const EnhancedHeader = ({ 
  sessionInitialized, 
  isLoading, 
  connectionError, 
  onClearMessages, 
  onRefresh,
  currentTask = "AI Developer Agent Session"
}) => {
  const getStatusText = () => {
    if (isLoading) return 'Initializing...';
    if (sessionInitialized) return 'Ready';
    if (connectionError) return 'Connection Failed';
    return 'Connecting...';
  };

  const getStatusClass = () => {
    if (isLoading) return 'status-loading';
    if (sessionInitialized) return 'status-ready';
    if (connectionError) return 'status-error';
    return 'status-connecting';
  };

  return (
    <header className="enhanced-header">
      <div className="header-content">
        <div className="header-left">
          <div className="app-branding">
            <span className="app-icon">🤖</span>
            <div className="app-info">
              <h1 className="app-title">AI Developer Agent</h1>
              <span className="app-subtitle">Your AI-Powered Development Assistant</span>
            </div>
          </div>
          <div className="task-context">
            <span className="task-label">Task:</span>
            <span className="task-name">{currentTask}</span>
          </div>
        </div>
        
        <div className="header-right">
          <div className="header-actions">
            <button 
              className="header-action-btn share-btn" 
              title="Share Session"
              disabled={!sessionInitialized}
            >
              <span className="btn-icon">📤</span>
              <span className="btn-text">Share</span>
            </button>
            
            <button 
              className="header-action-btn save-btn" 
              title="Save Session"
              disabled={!sessionInitialized}
            >
              <span className="btn-icon">💾</span>
              <span className="btn-text">Save</span>
            </button>
            
            <button 
              className="header-action-btn settings-btn" 
              title="Settings"
            >
              <span className="btn-icon">⚙️</span>
              <span className="btn-text">Settings</span>
            </button>
            
            <button 
              className="header-action-btn refresh-btn" 
              onClick={onRefresh}
              title="Refresh"
            >
              <span className="btn-icon">🔄</span>
            </button>
            
            <button 
              className="header-action-btn clear-btn" 
              onClick={onClearMessages}
              disabled={!sessionInitialized}
              title="Clear Session"
            >
              <span className="btn-icon">🗑️</span>
            </button>
          </div>
          
          <div className="connection-status">
            <div className={`status-indicator ${getStatusClass()}`}>
              <span className="status-dot"></span>
              <span className="status-text">{getStatusText()}</span>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};

export default EnhancedHeader;

