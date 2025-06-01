import React, { useState, useEffect } from 'react';
import '../styles/UnifiedEmulator.css';

/**
 * Simplified UnifiedEmulator component - Minimal version to isolate browser crash
 * Removes terminal initialization and complex rendering logic
 */
const UnifiedEmulator = ({ toolOutputs, wsConnected }) => {
  // State for the current tool type
  const [currentToolType, setCurrentToolType] = useState('terminal');
  const [lastToolOutputsLength, setLastToolOutputsLength] = useState(0);
  
  // Update tool type based on latest output
  useEffect(() => {
    if (toolOutputs && toolOutputs.length > 0) {
      const latestOutput = toolOutputs[toolOutputs.length - 1];
      if (latestOutput && latestOutput.toolName) {
        // Map backend tool names to frontend tool types
        let detectedType = 'terminal'; // Default fallback
        
        switch (latestOutput.toolName) {
          case 'execute_command':
            detectedType = 'terminal';
            break;
          case 'browser_automation':
            detectedType = 'browser';
            break;
          case 'code_intelligence':
            detectedType = 'code';
            break;
          default:
            detectedType = 'terminal';
        }
        
        setCurrentToolType(detectedType);
      }
    }
  }, [toolOutputs]);
  
  // Render connection status
  const renderConnectionStatus = () => {
    return (
      <div className={`connection-status ${wsConnected ? 'connected' : 'disconnected'}`}>
        <span className="status-indicator"></span>
        <span className="status-text">
          {wsConnected ? 'Connected' : 'Disconnected'}
        </span>
      </div>
    );
  };
  
  // Render fallback content for all tool outputs
  const renderFallbackContent = () => {
    if (!toolOutputs || toolOutputs.length === 0) {
      return (
        <div className="fallback-terminal">
          <p>No tool outputs available yet.</p>
        </div>
      );
    }
    
    return (
      <div className="fallback-terminal">
        <h4>Tool Outputs (Fallback View)</h4>
        {toolOutputs.map((output, index) => {
          let outputContent = '';
          
          try {
            if (output.output !== undefined) {
              outputContent = typeof output.output === 'object' 
                ? JSON.stringify(output.output, null, 2) 
                : String(output.output);
            } else if (output.data !== undefined) {
              outputContent = typeof output.data === 'object' 
                ? JSON.stringify(output.data, null, 2) 
                : String(output.data);
            } else {
              outputContent = JSON.stringify(output, null, 2);
            }
          } catch (error) {
            outputContent = `Error parsing output: ${error.message}`;
          }
          
          return (
            <div key={index} className="fallback-output">
              <div className="fallback-header">
                <span className="fallback-tool">{output.toolName || 'unknown'}</span>
                <span className="fallback-index">#{index}</span>
              </div>
              <pre className="fallback-content">{outputContent}</pre>
            </div>
          );
        })}
      </div>
    );
  };
  
  // Debug status display
  const renderDebugStatus = () => {
    return (
      <div className="debug-status">
        <div>WebSocket Connected: {wsConnected ? 'Yes' : 'No'}</div>
        <div>Tool Outputs: {toolOutputs?.length || 0}</div>
        <div>Current Tool Type: {currentToolType}</div>
      </div>
    );
  };
  
  return (
    <div className="unified-emulator">
      <div className="emulator-header">
        <h3>Tool Output: {currentToolType.charAt(0).toUpperCase() + currentToolType.slice(1)}</h3>
        {renderConnectionStatus()}
      </div>
      <div className="emulator-content">
        {renderFallbackContent()}
      </div>
      {renderDebugStatus()}
    </div>
  );
};

export default UnifiedEmulator;
