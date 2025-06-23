import React, { useState, useEffect, useCallback, useMemo, Suspense } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import PlanDisplay from '../PlanDisplay/PlanDisplay';
import useChatStore from '../../hooks/useChatStore';
import ToolOutputFactory from './ToolOutputFactory';
import ToolSkeleton from './ToolSkeleton';
import ErrorDisplay from './ErrorDisplay';
import { debugLog } from '../../utils/debugLogger';
import './EnhancedUnifiedEmulator.css';

// Error Fallback Component
const EmulatorErrorFallback = ({ error, resetErrorBoundary }) => {
  const handleExportError = () => {
    const errorReport = {
      timestamp: new Date().toISOString(),
      component: 'EnhancedUnifiedEmulator',
      error: error.toString(),
      stack: error.stack,
      debugLogs: window.fsdevDebugLogs || []
    };

    const blob = new Blob([JSON.stringify(errorReport, null, 2)], { 
      type: 'application/json' 
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `enhanced-emulator-error-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="emulator-error-fallback">
      <h3>🚨 Emulator Error</h3>
      <p>{error.message}</p>
      <div style={{ display: 'flex', gap: '10px', marginTop: '15px' }}>
        <button onClick={resetErrorBoundary} className="retry-button">
          🔄 Retry
        </button>
        <button onClick={handleExportError} className="export-button">
          📥 Export Error
        </button>
      </div>
    </div>
  );
};

// Terminal Output Component with dynamic tool support
const DynamicTerminalOutput = ({ toolOutputs = [], activeToolType, currentOutput, filteredOutputs, isMinimized = false }) => {
  if (isMinimized) {
    return (
      <div className="terminal-minimized">
        <div className="terminal-icon">💻</div>
        <div className="output-count">{toolOutputs.length} outputs</div>
        <div className="active-tool">{activeToolType}</div>
      </div>
    );
  }

  return (
    <div className="terminal-output">
      <div className="terminal-header">
        <span className="terminal-title">Tool Output - {activeToolType}</span>
        <div className="terminal-controls">
          <span className="output-count">{filteredOutputs.length} outputs</span>
        </div>
      </div>
      <div className="terminal-content">
        {activeToolType === 'initializing' ? (
          <div className="terminal-empty">
            <div className="empty-icon">🚀</div>
            <p>Agent Computer Initializing</p>
            <p className="empty-subtitle">Tool outputs will appear here when the agent starts working</p>
          </div>
        ) : currentOutput ? (
          <ErrorBoundary fallback={<ErrorDisplay />}>
            <Suspense fallback={<ToolSkeleton type={activeToolType} />}>
              <ToolOutputFactory
                type={activeToolType}
                data={currentOutput}
                allOutputs={filteredOutputs}
                wsConnected={toolOutputs.length > 0}
                toolId={`enhanced-${Date.now()}`}
              />
            </Suspense>
          </ErrorBoundary>
        ) : (
          <div className="terminal-empty">
            <div className="empty-icon">📟</div>
            <p>No {activeToolType} outputs yet</p>
            <p className="empty-subtitle">Outputs for this tool type will appear here</p>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Enhanced UnifiedEmulator with minimize/expand functionality and dynamic tool support
 */
const EnhancedUnifiedEmulator = ({ 
  toolOutputs = [], 
  currentPlan = null,
  isAgentWorking = false,
  onMinimizeToggle = () => {}
}) => {
  const [isMinimized, setIsMinimized] = useState(false);
  const [activeToolType, setActiveToolType] = useState('initializing');
  const [selectedOutputIndex, setSelectedOutputIndex] = useState(0);
  const [hasReceivedOutput, setHasReceivedOutput] = useState(false);
  
  // Get tool outputs from chat store
  const { 
    aiDeveloperAgentSessionId,
    toolOutputs: chatToolOutputs 
  } = useChatStore();
  
  // Use chat store tool outputs if available
  const displayToolOutputs = chatToolOutputs.length > 0 ? chatToolOutputs : toolOutputs;

  // Enhanced logging for debugging
  useEffect(() => {
    if (typeof debugLog !== 'undefined') {
      debugLog.emulator('EnhancedUnifiedEmulator initialized', { 
        toolOutputsCount: displayToolOutputs.length,
        activeToolType,
        sessionId: aiDeveloperAgentSessionId
      });
    }
  }, [displayToolOutputs.length, activeToolType, aiDeveloperAgentSessionId]);

  // Map tool names to emulator types
  const mapToolNameToType = useCallback((toolName) => {
    if (!toolName) return 'terminal';
    
    const toolTypeMap = {
      'file_system': 'filesystem',
      'planning_tool': 'terminal',
      'shell': 'terminal',
      'browser': 'browser',
      'code_execution': 'code',
      'execute_command': 'terminal',
      'browser_automation': 'browser',
      'git_operations': 'git',
      'build_tool': 'build',
      'data_visualization': 'dataviz',
      'system': 'terminal'
    };
    
    return toolTypeMap[toolName] || 'terminal';
  }, []);

  // Process tool outputs to determine available types
  const availableToolTypes = useMemo(() => {
    if (!displayToolOutputs || displayToolOutputs.length === 0) {
      return new Set(['initializing']);
    }
    
    const types = new Set(['initializing']); // Always include initializing
    displayToolOutputs.forEach(output => {
      const toolName = output.toolName || output.type;
      types.add(mapToolNameToType(toolName));
    });
    return types;
  }, [displayToolOutputs, mapToolNameToType]);

  // Filter outputs for current tool type
  const filteredOutputs = useMemo(() => {
    if (!displayToolOutputs || activeToolType === 'initializing') return [];
    
    return displayToolOutputs.filter(output => {
      const outputType = mapToolNameToType(output.toolName || output.type);
      return outputType === activeToolType;
    });
  }, [displayToolOutputs, activeToolType, mapToolNameToType]);

  // Get current output data
  const currentOutput = useMemo(() => {
    if (filteredOutputs.length === 0) return null;
    const index = Math.min(selectedOutputIndex, filteredOutputs.length - 1);
    return filteredOutputs[index];
  }, [filteredOutputs, selectedOutputIndex]);

  // Update active tool type when new outputs arrive
  useEffect(() => {
    if (displayToolOutputs.length > 0 && !hasReceivedOutput) {
      setHasReceivedOutput(true);
      const latestOutput = displayToolOutputs[displayToolOutputs.length - 1];
      if (latestOutput && latestOutput.toolName) {
        const newToolType = mapToolNameToType(latestOutput.toolName);
        setActiveToolType(newToolType);
      }
    }
  }, [displayToolOutputs, hasReceivedOutput, mapToolNameToType]);

  // Handle minimize/expand toggle
  const handleMinimizeToggle = useCallback(() => {
    const newMinimizedState = !isMinimized;
    setIsMinimized(newMinimizedState);
    onMinimizeToggle(newMinimizedState);
  }, [isMinimized, onMinimizeToggle]);

  // Handle tool type switching
  const handleToolTypeChange = useCallback((newType) => {
    setActiveToolType(newType);
    setSelectedOutputIndex(0);
  }, []);

  // Handle output navigation
  const handleOutputNavigation = useCallback((direction) => {
    if (direction === 'prev' && selectedOutputIndex > 0) {
      setSelectedOutputIndex(selectedOutputIndex - 1);
    } else if (direction === 'next' && selectedOutputIndex < filteredOutputs.length - 1) {
      setSelectedOutputIndex(selectedOutputIndex + 1);
    }
  }, [selectedOutputIndex, filteredOutputs.length]);

  // Connection status based on tool outputs
  const connectionStatus = displayToolOutputs.length > 0 ? 'connected' : 'disconnected';

  // Available tabs with icons
  const getToolIcon = (type) => {
    const icons = {
      'initializing': '🚀',
      'terminal': '💻',
      'browser': '🌐',
      'filesystem': '📁',
      'git': '🔀',
      'build': '🔨',
      'code': '💻',
      'dataviz': '📊'
    };
    return icons[type] || '🔧';
  };

  // Render output navigation
  const renderOutputNavigation = () => {
    if (filteredOutputs.length <= 1) return null;
    
    return (
      <div className="output-navigation">
        <button 
          className="nav-button" 
          onClick={() => handleOutputNavigation('prev')}
          disabled={selectedOutputIndex === 0}
        >
          ←
        </button>
        <span className="output-counter">
          {selectedOutputIndex + 1} / {filteredOutputs.length}
        </span>
        <button 
          className="nav-button" 
          onClick={() => handleOutputNavigation('next')}
          disabled={selectedOutputIndex === filteredOutputs.length - 1}
        >
          →
        </button>
      </div>
    );
  };

  if (isMinimized) {
    return (
      <div className="emulator-container minimized">
        <div className="emulator-thumbnail" onClick={handleMinimizeToggle}>
          <div className="thumbnail-header">
            <span className="thumbnail-title">Agent Computer</span>
            <div className={`connection-indicator ${connectionStatus}`}></div>
          </div>
          <div className="thumbnail-content">
            <DynamicTerminalOutput 
              toolOutputs={displayToolOutputs} 
              activeToolType={activeToolType}
              currentOutput={currentOutput}
              filteredOutputs={filteredOutputs}
              isMinimized={true} 
            />
          </div>
          <div className="thumbnail-expand">
            <span className="expand-icon">⤢</span>
          </div>
        </div>
        
        {/* Plan Display - Always visible */}
        <PlanDisplay 
          plan={currentPlan}
          isMinimized={true}
          isAgentWorking={isAgentWorking}
        />
      </div>
    );
  }

  return (
    <div className="emulator-container expanded">
      <ErrorBoundary FallbackComponent={EmulatorErrorFallback}>
        <div className="unified-emulator">
          {/* Emulator Header */}
          <div className="emulator-header">
            <div className="header-left">
              <h3 className="emulator-title">Agent Computer</h3>
              {renderOutputNavigation()}
              <div className={`connection-status ${connectionStatus}`}>
                <span className="status-indicator"></span>
                <span className="status-text">
                  {connectionStatus === 'connected' ? 'Connected' : 'Disconnected'}
                </span>
              </div>
            </div>
            <div className="header-right">
              <div className="emulator-controls">
                <button 
                  className="control-btn minimize-btn"
                  onClick={handleMinimizeToggle}
                  title="Minimize emulator"
                >
                  ⤡
                </button>
              </div>
            </div>
          </div>

          {/* Dynamic Tool Tabs */}
          <div className="emulator-tabs">
            {Array.from(availableToolTypes).map(type => (
              <button
                key={type}
                className={`tab-button ${activeToolType === type ? 'active' : ''}`}
                onClick={() => handleToolTypeChange(type)}
              >
                <span className="tab-icon">{getToolIcon(type)}</span>
                <span className="tab-label">{type.charAt(0).toUpperCase() + type.slice(1)}</span>
              </button>
            ))}
          </div>

          {/* Dynamic Emulator Content */}
          <div className="emulator-content">
            <DynamicTerminalOutput 
              toolOutputs={displayToolOutputs} 
              activeToolType={activeToolType}
              currentOutput={currentOutput}
              filteredOutputs={filteredOutputs}
              isMinimized={false} 
            />
          </div>
        </div>
      </ErrorBoundary>

      {/* Plan Display - Always visible below emulator */}
      <PlanDisplay 
        plan={currentPlan}
        isMinimized={false}
        isAgentWorking={isAgentWorking}
      />
    </div>
  );
};

export default EnhancedUnifiedEmulator;

