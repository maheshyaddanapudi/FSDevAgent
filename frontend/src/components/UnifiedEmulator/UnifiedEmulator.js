// frontend/src/components/UnifiedEmulator/UnifiedEmulator.js
import React, { useState, useEffect, useCallback, useMemo, Suspense } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useEmulatorStore } from '../../store/emulatorStore';
import useChatStore from '../../hooks/useChatStore';
import ToolOutputFactory from './ToolOutputFactory';
import ToolSkeleton from './ToolSkeleton';
import ErrorDisplay from './ErrorDisplay';
import { debugLog } from '../../utils/debugLogger';
import './UnifiedEmulator.css';

// Enhanced Error Fallback Component
const EmulatorErrorFallback = ({ error, resetErrorBoundary }) => {
  const handleExportError = () => {
    const errorReport = {
      timestamp: new Date().toISOString(),
      component: 'UnifiedEmulator',
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
    a.download = `unified-emulator-error-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="emulator-error-fallback" style={{
      padding: '20px',
      backgroundColor: '#fee',
      border: '2px solid #f00',
      borderRadius: '8px',
      margin: '10px',
      fontFamily: 'monospace'
    }}>
      <h3 style={{ color: '#d00', marginTop: 0 }}>
        🚨 UnifiedEmulator Error
      </h3>
      
      <div style={{ marginBottom: '15px' }}>
        <strong>Error:</strong>
        <pre style={{ 
          backgroundColor: '#f5f5f5', 
          padding: '10px', 
          borderRadius: '4px',
          overflow: 'auto',
          maxHeight: '100px'
        }}>
          {error.message}
        </pre>
      </div>

      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
        <button 
          onClick={resetErrorBoundary}
          style={{
            padding: '8px 16px',
            backgroundColor: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          🔄 Retry Emulator
        </button>
        
        <button 
          onClick={handleExportError}
          style={{
            padding: '8px 16px',
            backgroundColor: '#28a745',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}
        >
          📥 Export Error Report
        </button>
      </div>
    </div>
  );
};

/**
 * UnifiedEmulator - Enhanced plugin-based tool output visualization system
 */
const UnifiedEmulator = () => {
  const [activeToolType, setActiveToolType] = useState('initializing');
  const [selectedOutputIndex, setSelectedOutputIndex] = useState(0);
  const [hasReceivedOutput, setHasReceivedOutput] = useState(false);
  const [toolOutputs, setToolOutputs] = useState([]);
  
  // Get session ID and tool outputs from chat store
  const { 
    aiDeveloperAgentSessionId, 
    toolOutputs: chatToolOutputs 
  } = useChatStore();
  
  // Enhanced logging for debugging
  useEffect(() => {
    debugLog.emulator('UnifiedEmulator initialized', { 
      toolOutputsCount: toolOutputs.length,
      chatToolOutputsCount: chatToolOutputs.length,
      activeToolType,
      sessionId: aiDeveloperAgentSessionId
    });
  }, [toolOutputs.length, chatToolOutputs.length, activeToolType, aiDeveloperAgentSessionId]);
  
  const { 
    registerTool, 
    unregisterTool
  } = useEmulatorStore();

  // Generate unique tool ID
  const toolId = useMemo(() => `tool-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, []);

  // Register this tool instance
  useEffect(() => {
    debugLog.emulator('Registering tool', { toolId, activeToolType });
    registerTool(toolId, activeToolType);
    return () => {
      debugLog.emulator('Unregistering tool', { toolId });
      unregisterTool(toolId);
    };
  }, [toolId, activeToolType, registerTool, unregisterTool]);

  // Process chat tool outputs instead of SSE events
  useEffect(() => {
    if (!chatToolOutputs || chatToolOutputs.length === 0) return;
    
    // Update local tool outputs from chat store
    setToolOutputs(chatToolOutputs);
    setHasReceivedOutput(true);
    
    // Get the latest tool output
    const latestOutput = chatToolOutputs[chatToolOutputs.length - 1];
    if (latestOutput) {
      debugLog.emulator('Processing tool output from chat store', { 
        toolName: latestOutput.toolName,
        type: latestOutput.type,
        outputId: latestOutput.id
      });
      
      // Update active tool type based on latest output
      if (latestOutput.toolName) {
        setActiveToolType(latestOutput.toolName);
      }
      
      // Handle different tool output types
      handleToolOutput(latestOutput);
    }
  }, [chatToolOutputs]);

  // Handle tool output processing
  const handleToolOutput = useCallback((toolOutput) => {
    try {
      debugLog.emulator('Handling tool output', { 
        toolName: toolOutput.toolName, 
        type: toolOutput.type,
        content: toolOutput.content?.substring(0, 100) + '...'
      });
      
      // Update emulator state based on tool output
      // This replaces the previous SSE event handling logic
      
    } catch (error) {
      debugLog.error('UnifiedEmulator', 'Error handling tool output', { 
        error: error.message, 
        toolOutput 
      });
    }
  }, []);

  // Map tool names to emulator types (simplified version)
  const mapToolNameToType = useCallback((toolName) => {
    if (!toolName) return 'initializing';
    
    const toolTypeMap = {
      'file_system': 'file_system',
      'planning_tool': 'planning',
      'shell': 'shell',
      'browser': 'browser',
      'code_execution': 'code_execution'
    };
    
    return toolTypeMap[toolName] || 'generic';
  }, []);

  // Process tool outputs to determine available types
  const availableToolTypes = useMemo(() => {
    if (!toolOutputs || toolOutputs.length === 0) {
      debugLog.emulator('No tool outputs available', { toolOutputs });
      return new Set(['initializing']);
    }
    
    const types = new Set(['initializing']); // Always include initializing
    toolOutputs.forEach(output => {
      const toolName = output.toolName || output.type;
      types.add(mapToolNameToType(toolName));
    });
    return types;
  }, [toolOutputs, mapToolNameToType]);

  // Filter outputs for current tool type
  const filteredOutputs = useMemo(() => {
    if (!toolOutputs || activeToolType === 'initializing') return [];
    
    return toolOutputs.filter(output => {
      const outputType = mapToolNameToType(output.toolName || output.type);
      return outputType === activeToolType;
    });
  }, [toolOutputs, activeToolType, mapToolNameToType]);

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

  // Render connection status (simplified for chat store)
  const renderConnectionStatus = () => (
    <div className={`connection-status ${chatToolOutputs.length > 0 ? 'connected' : 'disconnected'}`}>
      <span className="status-indicator"></span>
      <span className="status-text">
        {chatToolOutputs.length > 0 ? 'Connected' : 'Disconnected'}
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
    <ErrorBoundary 
      FallbackComponent={EmulatorErrorFallback}
      onError={(error, errorInfo) => {
        debugLog.error('UnifiedEmulator', 'Error boundary caught error', {
          error: error.message,
          stack: error.stack,
          componentStack: errorInfo.componentStack
        });
      }}
    >
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
                wsConnected={chatToolOutputs.length > 0} // Use chat store connection status
                toolId={toolId}
              />
            </Suspense>
          </ErrorBoundary>
        </div>
      </div>
    </ErrorBoundary>
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
    'data_visualization': 'dataviz',
    'planning_tool': 'terminal',
    'system': 'terminal' // For system messages
  };
  
  return mapping[toolName] || 'terminal';
}

export default UnifiedEmulator;

