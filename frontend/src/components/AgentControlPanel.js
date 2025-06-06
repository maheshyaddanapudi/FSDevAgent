/**
 * AgentControlPanel.js
 * 
 * This component provides controls for the autonomous agent execution,
 * including pause, resume, step, and other execution controls.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useEnhancedWebSocket } from '../services/StreamingService';
import '../styles/AgentControlPanel.css';

const AgentControlPanel = ({ className = '' }) => {
  // Agent execution state
  const [executionState, setExecutionState] = useState('idle'); // idle, running, paused, completed, error
  const [currentPhase, setCurrentPhase] = useState(null);
  const [progress, setProgress] = useState(0);
  const [isControlEnabled, setIsControlEnabled] = useState(true);
  
  // Get WebSocket controls and data streams
  const {
    connected,
    planningData,
    agentStateData,
    phaseData,
    errorData,
    pauseExecution,
    resumeExecution,
    stepExecution,
    stopExecution,
    skipStep,
    jumpToPhase
  } = useEnhancedWebSocket();

  // Update execution state based on agent state data
  useEffect(() => {
    if (!agentStateData) return;
    
    if (agentStateData.executionState) {
      setExecutionState(agentStateData.executionState);
    }
    
    if (agentStateData.progress !== undefined) {
      setProgress(agentStateData.progress);
    }
  }, [agentStateData]);

  // Update current phase based on phase data
  useEffect(() => {
    if (!phaseData) return;
    
    if (phaseData.currentPhase) {
      setCurrentPhase(phaseData.currentPhase);
    }
  }, [phaseData]);

  // Disable controls when disconnected
  useEffect(() => {
    setIsControlEnabled(connected);
  }, [connected]);

  // Handle pause button click
  const handlePause = useCallback(() => {
    if (!isControlEnabled) return;
    
    pauseExecution();
    // Optimistic UI update
    setExecutionState('pausing');
  }, [isControlEnabled, pauseExecution]);

  // Handle resume button click
  const handleResume = useCallback(() => {
    if (!isControlEnabled) return;
    
    resumeExecution();
    // Optimistic UI update
    setExecutionState('resuming');
  }, [isControlEnabled, resumeExecution]);

  // Handle step button click
  const handleStep = useCallback(() => {
    if (!isControlEnabled) return;
    
    stepExecution();
    // No optimistic UI update for step as it's immediate
  }, [isControlEnabled, stepExecution]);

  // Handle stop button click
  const handleStop = useCallback(() => {
    if (!isControlEnabled) return;
    
    stopExecution();
    // Optimistic UI update
    setExecutionState('stopping');
  }, [isControlEnabled, stopExecution]);

  // Handle skip button click
  const handleSkip = useCallback(() => {
    if (!isControlEnabled) return;
    
    skipStep();
    // No optimistic UI update for skip as it's immediate
  }, [isControlEnabled, skipStep]);

  // Get button state based on execution state
  const getButtonState = (buttonType) => {
    if (!isControlEnabled) return 'disabled';
    
    switch (buttonType) {
      case 'pause':
        return executionState === 'running' ? 'enabled' : 'disabled';
      case 'resume':
        return executionState === 'paused' ? 'enabled' : 'disabled';
      case 'step':
        return executionState === 'paused' ? 'enabled' : 'disabled';
      case 'stop':
        return ['running', 'paused'].includes(executionState) ? 'enabled' : 'disabled';
      case 'skip':
        return executionState === 'paused' ? 'enabled' : 'disabled';
      default:
        return 'disabled';
    }
  };

  // Get phase display name
  const getPhaseDisplayName = () => {
    if (!currentPhase) return 'Not Started';
    
    // Convert SNAKE_CASE to Title Case
    return currentPhase
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  };

  // Get status indicator class
  const getStatusIndicatorClass = () => {
    switch (executionState) {
      case 'running':
        return 'status-indicator running';
      case 'paused':
        return 'status-indicator paused';
      case 'completed':
        return 'status-indicator completed';
      case 'error':
        return 'status-indicator error';
      default:
        return 'status-indicator idle';
    }
  };

  return (
    <div className={`agent-control-panel ${className}`}>
      <div className="control-panel-header">
        <div className={getStatusIndicatorClass()}></div>
        <h3>Agent Control</h3>
        <div className="connection-status">
          {connected ? (
            <span className="connected">Connected</span>
          ) : (
            <span className="disconnected">Disconnected</span>
          )}
        </div>
      </div>
      
      <div className="control-panel-body">
        <div className="phase-indicator">
          <span className="phase-label">Phase:</span>
          <span className="phase-value">{getPhaseDisplayName()}</span>
        </div>
        
        <div className="progress-tracker">
          <div className="progress-bar">
            <div 
              className="progress-fill" 
              style={{ width: `${progress}%` }}
            ></div>
          </div>
          <span className="progress-text">{progress}% Complete</span>
        </div>
        
        <div className="control-buttons">
          {executionState === 'running' ? (
            <button 
              className={`control-button pause ${getButtonState('pause')}`}
              onClick={handlePause}
              disabled={getButtonState('pause') === 'disabled'}
              title="Pause Execution"
            >
              <span className="button-icon">⏸</span>
              <span className="button-text">Pause</span>
            </button>
          ) : (
            <button 
              className={`control-button resume ${getButtonState('resume')}`}
              onClick={handleResume}
              disabled={getButtonState('resume') === 'disabled'}
              title="Resume Execution"
            >
              <span className="button-icon">▶️</span>
              <span className="button-text">Resume</span>
            </button>
          )}
          
          <button 
            className={`control-button step ${getButtonState('step')}`}
            onClick={handleStep}
            disabled={getButtonState('step') === 'disabled'}
            title="Step Execution"
          >
            <span className="button-icon">⏭</span>
            <span className="button-text">Step</span>
          </button>
          
          <button 
            className={`control-button skip ${getButtonState('skip')}`}
            onClick={handleSkip}
            disabled={getButtonState('skip') === 'disabled'}
            title="Skip Current Step"
          >
            <span className="button-icon">⏩</span>
            <span className="button-text">Skip</span>
          </button>
          
          <button 
            className={`control-button stop ${getButtonState('stop')}`}
            onClick={handleStop}
            disabled={getButtonState('stop') === 'disabled'}
            title="Stop Execution"
          >
            <span className="button-icon">⏹</span>
            <span className="button-text">Stop</span>
          </button>
        </div>
      </div>
      
      {executionState === 'error' && errorData && (
        <div className="error-notification">
          <div className="error-header">
            <span className="error-icon">⚠️</span>
            <span className="error-title">Error</span>
          </div>
          <div className="error-message">
            {errorData.message || 'An unknown error occurred'}
          </div>
        </div>
      )}
    </div>
  );
};

export default AgentControlPanel;
