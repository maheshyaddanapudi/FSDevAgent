// frontend/src/components/UnifiedEmulator/UnifiedEmulator.js
import React, { useState, useEffect, useCallback, useMemo, Suspense } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { useEmulatorStore } from '../../store/emulatorStore';
import useToolEventStream from '../../hooks/useToolEventStream';
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
  
  // Get session ID from chat store
  const { aiDeveloperAgentSessionId } = useChatStore();
  
  // Use SSE hook for tool events
  const { 
    connected: sseConnected,
    lastEvent,
    error: sseError,
    reconnect: sseReconnect,
    isReconnecting
  } = useToolEventStream(aiDeveloperAgentSessionId);
  
  // Enhanced logging for debugging
  useEffect(() => {
    debugLog.emulator('UnifiedEmulator initialized', { 
      toolOutputsCount: toolOutputs.length,
      sseConnected,
      activeToolType,
      sessionId: aiDeveloperAgentSessionId
    });
  }, [toolOutputs.length, sseConnected, activeToolType, aiDeveloperAgentSessionId]);
  
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

  // Process SSE events
  useEffect(() => {
    if (!lastEvent) return;
    
    debugLog.emulator('Processing SSE event', { 
      type: lastEvent.type, 
      toolName: lastEvent.toolName,
      sessionId: lastEvent.sessionId 
    });
    
    // Only process events for our session
    if (lastEvent.sessionId !== aiDeveloperAgentSessionId) {
      debugLog.emulator('Ignoring event for different session', { 
        eventSessionId: lastEvent.sessionId,
        ourSessionId: aiDeveloperAgentSessionId 
      });
      return;
    }
    
    switch (lastEvent.type) {
      case 'tool_execution':
        handleToolExecution(lastEvent);
        break;
        
      case 'tool_result':
        handleToolResult(lastEvent);
        break;
        
      case 'phase_transition':
        handlePhaseTransition(lastEvent);
        break;
        
      case 'planning':
        handlePlanningUpdate(lastEvent);
        break;
        
      case 'agent_state_update':
        handleAgentStateUpdate(lastEvent);
        break;
        
      case 'error':
        handleError(lastEvent);
        break;
        
      case 'connection':
        debugLog.emulator('SSE connection established', { data: lastEvent.data });
        break;
        
      case 'heartbeat':
        // Ignore heartbeats
        break;
        
      default:
        debugLog.emulator('Unknown event type', { type: lastEvent.type });
    }
  }, [lastEvent, aiDeveloperAgentSessionId]);

  // Handle tool execution events
  const handleToolExecution = useCallback((event) => {
    const toolOutput = {
      id: `exec-${Date.now()}`,
      type: 'tool_execution',
      toolName: event.toolName,
      args: event.data.args,
      status: event.data.status,
      timestamp: event.timestamp || new Date().toISOString(),
      output: {
        type: 'execution',
        content: `Executing ${event.toolName}...`,
        metadata: event.data
      }
    };
    
    setToolOutputs(prev => [...prev, toolOutput]);
    setHasReceivedOutput(true);
    
    // Auto-switch to appropriate tool type
    const detectedType = mapToolNameToType(event.toolName);
    if (detectedType !== activeToolType) {
      setActiveToolType(detectedType);
    }
  }, [activeToolType]);

  // Handle tool result events
  const handleToolResult = useCallback((event) => {
    const toolOutput = {
      id: `result-${Date.now()}`,
      type: 'tool_result',
      toolName: event.toolName,
      result: event.data.result,
      success: event.data.success,
      timestamp: event.timestamp || new Date().toISOString(),
      output: event.data.result
    };
    
    setToolOutputs(prev => [...prev, toolOutput]);
  }, []);

  // Handle phase transition events
  const handlePhaseTransition = useCallback((event) => {
    const toolOutput = {
      id: `phase-${Date.now()}`,
      type: 'phase_transition',
      toolName: 'system',
      fromPhase: event.data.fromPhase,
      toPhase: event.data.toPhase,
      timestamp: event.timestamp || new Date().toISOString(),
      output: {
        type: 'phase_transition',
        content: `Phase transition: ${event.data.fromPhase || 'Start'} → ${event.data.toPhase}`,
        metadata: event.data
      }
    };
    
    setToolOutputs(prev => [...prev, toolOutput]);
  }, []);

  // Handle planning update events
  const handlePlanningUpdate = useCallback((event) => {
    const toolOutput = {
      id: `plan-${Date.now()}`,
      type: 'planning',
      toolName: 'planning_tool',
      step: event.data.step,
      totalSteps: event.data.totalSteps,
      description: event.data.description,
      timestamp: event.timestamp || new Date().toISOString(),
      output: {
        type: 'planning',
        content: `Planning step ${event.data.step}/${event.data.totalSteps}: ${event.data.description}`,
        metadata: event.data
      }
    };
    
    setToolOutputs(prev => [...prev, toolOutput]);
  }, []);

  // Handle agent state update events
  const handleAgentStateUpdate = useCallback((event) => {
    debugLog.emulator('Agent state update', { action: event.data.action, state: event.data.state });
    // Could update UI to show agent state if needed
  }, []);

  // Handle error events
  const handleError = useCallback((event) => {
    const toolOutput = {
      id: `error-${Date.now()}`,
      type: 'error',
      toolName: 'system',
      message: event.data.message,
      severity: event.data.severity,
      timestamp: event.timestamp || new Date().toISOString(),
      output: {
        type: 'error',
        content: `Error: ${event.data.message}`,
        metadata: event.data
      }
    };
    
    setToolOutputs(prev => [...prev, toolOutput]);
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
  }, [toolOutputs]);

  // Filter outputs for current tool type
  const filteredOutputs = useMemo(() => {
    if (!toolOutputs || activeToolType === 'initializing') return [];
    
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
    <div className={`connection-status ${sseConnected ? 'connected' : 'disconnected'}`}>
      <span className="status-indicator"></span>
      <span className="status-text">
        {isReconnecting ? 'Reconnecting...' : sseConnected ? 'Connected' : 'Disconnected'}
      </span>
      {sseError && (
        <button 
          className="reconnect-btn" 
          onClick={sseReconnect}
          style={{ marginLeft: '8px', fontSize: '12px' }}
        >
          Retry
        </button>
      )}
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
                wsConnected={sseConnected} // Keep prop name for compatibility
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

