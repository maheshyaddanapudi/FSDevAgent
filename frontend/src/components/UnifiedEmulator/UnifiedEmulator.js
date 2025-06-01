// frontend/src/components/UnifiedEmulator/UnifiedEmulator.js
import React, { useState, useEffect, useCallback, useMemo, Suspense, lazy } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import useWebSocket from '../../hooks/useWebSocket';
import { useEmulatorStore } from '../../store/emulatorStore';
import ToolOutputFactory from './ToolOutputFactory';
import ToolSkeleton from './ToolSkeleton';
import ErrorDisplay from './ErrorDisplay';
import './UnifiedEmulator.css';

/**
 * UnifiedEmulator - Enhanced plugin-based tool output visualization system
 */
const UnifiedEmulator = ({ toolOutputs, wsConnected }) => {
  const [activeToolType, setActiveToolType] = useState('terminal');
  const [selectedOutputIndex, setSelectedOutputIndex] = useState(0);
  
  const { 
    registerTool, 
    unregisterTool, 
    activeTools,
    processWebSocketMessage 
  } = useEmulatorStore();

  // Generate unique tool ID
  const toolId = useMemo(() => `tool-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, []);

  // Register this tool instance
  useEffect(() => {
    registerTool(toolId, activeToolType);
    return () => unregisterTool(toolId);
  }, [toolId, activeToolType, registerTool, unregisterTool]);

  // Process tool outputs to determine available types
  const availableToolTypes = useMemo(() => {
    if (!toolOutputs || toolOutputs.length === 0) return new Set(['terminal']);
    
    const types = new Set();
    toolOutputs.forEach(output => {
      const toolName = output.toolName || output.type;
      types.add(mapToolNameToType(toolName));
    });
    return types;
  }, [toolOutputs]);

  // Auto-detect and switch to appropriate tool type
  useEffect(() => {
    if (toolOutputs && toolOutputs.length > 0) {
      const latestOutput = toolOutputs[toolOutputs.length - 1];
      const detectedType = mapToolNameToType(latestOutput.toolName || latestOutput.type);
      
      if (detectedType !== activeToolType && availableToolTypes.has(detectedType)) {
        setActiveToolType(detectedType);
        setSelectedOutputIndex(toolOutputs.length - 1);
      }
    }
  }, [toolOutputs, activeToolType, availableToolTypes]);

  // Filter outputs for current tool type
  const filteredOutputs = useMemo(() => {
    if (!toolOutputs) return [];
    
    return toolOutputs.filter(output => {
      const outputType = mapToolNameToType(output.toolName || output.type);
      return outputType === activeToolType;
    });
  }, [toolOutputs, activeToolType]);

  // Get current output data
  const currentOutput = useMemo(() => {
    if (filteredOutputs.length === 0) return null;
    const index = Math.min(selectedOutputIndex, filteredOutputs.length - 1);
    return filteredOutputs[index];
  }, [filteredOutputs, selectedOutputIndex]);

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

  // Render connection status
  const renderConnectionStatus = () => (
    <div className={`connection-status ${wsConnected ? 'connected' : 'disconnected'}`}>
      <span className="status-indicator"></span>
      <span className="status-text">
        {wsConnected ? 'Connected' : 'Disconnected'}
      </span>
    </div>
  );

  // Render tool type tabs
  const renderToolTabs = () => (
    <div className="tool-tabs">
      {Array.from(availableToolTypes).map(type => (
        <button
          key={type}
          className={`tool-tab ${activeToolType === type ? 'active' : ''}`}
          onClick={() => handleToolTypeChange(type)}
        >
          <span className={`tool-icon tool-icon-${type}`}></span>
          {type.charAt(0).toUpperCase() + type.slice(1)}
        </button>
      ))}
    </div>
  );

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

  return (
    <div className="unified-emulator">
      <div className="emulator-header">
        <div className="header-left">
          <h3>Tool Output</h3>
          {renderOutputNavigation()}
        </div>
        <div className="header-right">
          {renderConnectionStatus()}
        </div>
      </div>
      
      {renderToolTabs()}
      
      <div className="emulator-content">
        <ErrorBoundary fallback={<ErrorDisplay />}>
          <Suspense fallback={<ToolSkeleton type={activeToolType} />}>
            <ToolOutputFactory
              type={activeToolType}
              data={currentOutput}
              allOutputs={filteredOutputs}
              wsConnected={wsConnected}
              toolId={toolId}
            />
          </Suspense>
        </ErrorBoundary>
      </div>
    </div>
  );
};

// Helper function to map tool names to visualization types
function mapToolNameToType(toolName) {
  const mapping = {
    'execute_command': 'terminal',
    'browser_automation': 'browser',
    'file_system': 'filesystem',
    'git_operations': 'git',
    'build_tool': 'build',
    'code_intelligence': 'code',
    'data_visualization': 'dataviz'
  };
  
  return mapping[toolName] || 'terminal';
}

export default UnifiedEmulator;
