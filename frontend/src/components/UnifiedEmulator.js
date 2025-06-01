import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';
import '../styles/UnifiedEmulator.css';

/**
 * UnifiedEmulator component - Enhanced version with improved visualization
 * Handles different tool types and provides real-time visualization of tool outputs
 */
const UnifiedEmulator = ({ toolOutputs, wsConnected }) => {
  // Tool types supported by the backend
  const toolTypes = ['terminal', 'browser', 'file_system', 'code_intelligence', 'git_operations', 'build_tool', 'data_visualization'];
  
  // State for the current tool type and terminal instance
  const [currentToolType, setCurrentToolType] = useState('terminal');
  const [terminalInitialized, setTerminalInitialized] = useState(false);
  const [errorState, setErrorState] = useState(null);
  
  // Refs for DOM elements
  const terminalRef = useRef(null);
  const terminalInstanceRef = useRef(null);
  const browserRef = useRef(null);
  const codeEditorRef = useRef(null);
  
  // Auto-detect tool type from the latest output
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
          case 'file_system':
            detectedType = 'terminal';
            break;
          case 'git_operations':
            detectedType = 'terminal';
            break;
          case 'build_tool':
            detectedType = 'terminal';
            break;
          case 'data_visualization':
            detectedType = 'browser';
            break;
          default:
            detectedType = 'terminal';
        }
        
        setCurrentToolType(detectedType);
      }
    }
  }, [toolOutputs]);
  
  // Initialize terminal when component mounts
  useEffect(() => {
    if (terminalRef.current && !terminalInitialized) {
      try {
        // Create terminal instance with fit addon
        const terminal = new Terminal({
          cursorBlink: true,
          fontSize: 14,
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
          theme: {
            background: '#1e1e1e',
            foreground: '#f0f0f0'
          }
        });
        
        const fitAddon = new FitAddon();
        terminal.loadAddon(fitAddon);
        
        // Open terminal in the container
        terminal.open(terminalRef.current);
        fitAddon.fit();
        
        // Store terminal instance in ref
        terminalInstanceRef.current = {
          terminal,
          fitAddon
        };
        
        setTerminalInitialized(true);
        
        // Handle window resize
        const handleResize = () => {
          if (terminalInstanceRef.current) {
            terminalInstanceRef.current.fitAddon.fit();
          }
        };
        
        window.addEventListener('resize', handleResize);
        
        // Cleanup on unmount
        return () => {
          window.removeEventListener('resize', handleResize);
          if (terminalInstanceRef.current) {
            terminalInstanceRef.current.terminal.dispose();
          }
        };
      } catch (error) {
        console.error('Error initializing terminal:', error);
        setErrorState({
          component: 'terminal',
          error: error.message || 'Failed to initialize terminal'
        });
      }
    }
  }, [terminalInitialized]);
  
  // Update terminal with new outputs - enhanced for better streaming visualization
  useEffect(() => {
    if (terminalInstanceRef.current && toolOutputs && toolOutputs.length > 0) {
      const terminalOutputs = toolOutputs.filter(output => 
        ['execute_command', 'file_system', 'git_operations', 'build_tool'].includes(output.toolName)
      );
      
      if (terminalOutputs.length > 0) {
        const latestOutput = terminalOutputs[terminalOutputs.length - 1];
        if (latestOutput && latestOutput.output) {
          try {
            // Handle different output formats with improved visualization
            let outputText = '';
            
            // Add timestamp for better context
            const timestamp = new Date().toLocaleTimeString();
            terminalInstanceRef.current.terminal.write(`\r\n[${timestamp}] `);
            
            // Format the output based on type
            if (typeof latestOutput.output === 'string') {
              outputText = latestOutput.output;
            } else if (typeof latestOutput.output === 'object') {
              // Pretty print JSON with colors
              outputText = JSON.stringify(latestOutput.output, null, 2);
              
              // Add tool name as context if available
              if (latestOutput.toolName) {
                terminalInstanceRef.current.terminal.write(`\x1b[36m[${latestOutput.toolName}]\x1b[0m\r\n`);
              }
            }
            
            // Write to terminal with proper formatting
            terminalInstanceRef.current.terminal.write(outputText + '\r\n');
            
            // Add visual separator for better readability
            terminalInstanceRef.current.terminal.write('\x1b[90m' + '-'.repeat(40) + '\x1b[0m\r\n');
          } catch (error) {
            console.error('Error updating terminal:', error);
            terminalInstanceRef.current.terminal.write(`\x1b[31mError displaying output: ${error.message}\x1b[0m\r\n`);
          }
        }
      }
    }
  }, [toolOutputs]);
  
  // Render browser content
  const renderBrowserContent = () => {
    const browserOutputs = toolOutputs.filter(output => 
      ['browser_automation', 'data_visualization'].includes(output.toolName)
    );
    
    if (browserOutputs.length === 0) {
      return (
        <div className="browser-placeholder">
          <p>No browser content to display</p>
        </div>
      );
    }
    
    const latestOutput = browserOutputs[browserOutputs.length - 1];
    
    try {
      // Handle different output formats
      if (latestOutput.output && typeof latestOutput.output === 'string') {
        if (latestOutput.output.startsWith('<html') || latestOutput.output.includes('<!DOCTYPE html>')) {
          // Render HTML content in iframe
          const blob = new Blob([latestOutput.output], { type: 'text/html' });
          const url = URL.createObjectURL(blob);
          
          return (
            <iframe 
              src={url} 
              className="browser-iframe" 
              title="Browser Content"
              sandbox="allow-same-origin allow-scripts"
            />
          );
        } else {
          // Render text content
          return (
            <div className="browser-content">
              <pre>{latestOutput.output}</pre>
            </div>
          );
        }
      } else if (latestOutput.output && latestOutput.output.imageUrl) {
        // Render image
        return (
          <div className="browser-content">
            <img 
              src={latestOutput.output.imageUrl} 
              alt="Browser Output" 
              className="browser-image"
            />
          </div>
        );
      } else {
        // Fallback for other content types
        return (
          <div className="browser-content">
            <pre>{JSON.stringify(latestOutput.output, null, 2)}</pre>
          </div>
        );
      }
    } catch (error) {
      console.error('Error rendering browser content:', error);
      return (
        <div className="browser-error">
          <p>Error rendering browser content: {error.message}</p>
        </div>
      );
    }
  };
  
  // Render code editor content
  const renderCodeContent = () => {
    const codeOutputs = toolOutputs.filter(output => 
      ['code_intelligence'].includes(output.toolName)
    );
    
    if (codeOutputs.length === 0) {
      return (
        <div className="code-placeholder">
          <p>No code content to display</p>
        </div>
      );
    }
    
    const latestOutput = codeOutputs[codeOutputs.length - 1];
    
    try {
      // Handle different output formats
      if (latestOutput.output && typeof latestOutput.output === 'string') {
        return (
          <div className="code-content">
            <pre className="code-block">{latestOutput.output}</pre>
          </div>
        );
      } else if (latestOutput.output && latestOutput.output.code) {
        return (
          <div className="code-content">
            <pre className="code-block">{latestOutput.output.code}</pre>
          </div>
        );
      } else {
        // Fallback for other content types
        return (
          <div className="code-content">
            <pre>{JSON.stringify(latestOutput.output, null, 2)}</pre>
          </div>
        );
      }
    } catch (error) {
      console.error('Error rendering code content:', error);
      return (
        <div className="code-error">
          <p>Error rendering code content: {error.message}</p>
        </div>
      );
    }
  };
  
  // Render error state
  const renderErrorState = () => {
    if (!errorState) return null;
    
    return (
      <div className="error-container">
        <h3>Error in {errorState.component}</h3>
        <p>{errorState.error}</p>
      </div>
    );
  };
  
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
  
  // Render appropriate content based on tool type
  const renderContent = () => {
    // If there's an error, show error state
    if (errorState) {
      return renderErrorState();
    }
    
    // If no tool outputs and not connected, show connection error
    if (!wsConnected && (!toolOutputs || toolOutputs.length === 0)) {
      return (
        <div className="connection-error">
          <h3>WebSocket Connection Error</h3>
          <p>Unable to connect to the backend. Please check your connection and try again.</p>
        </div>
      );
    }
    
    // If no tool outputs but connected, show waiting message
    if ((!toolOutputs || toolOutputs.length === 0) && wsConnected) {
      return (
        <div className="waiting-message">
          <p>Waiting for tool output...</p>
        </div>
      );
    }
    
    // Render based on current tool type
    switch (currentToolType) {
      case 'terminal':
        return (
          <div className="terminal-container" ref={terminalRef}></div>
        );
      case 'browser':
        return (
          <div className="browser-container" ref={browserRef}>
            {renderBrowserContent()}
          </div>
        );
      case 'code':
        return (
          <div className="code-container" ref={codeEditorRef}>
            {renderCodeContent()}
          </div>
        );
      default:
        return (
          <div className="terminal-container" ref={terminalRef}></div>
        );
    }
  };
  
  return (
    <div className="unified-emulator">
      <div className="emulator-header">
        <h3>Tool Output</h3>
        {renderConnectionStatus()}
      </div>
      <div className="emulator-content">
        {renderContent()}
      </div>
    </div>
  );
};

export default UnifiedEmulator;
