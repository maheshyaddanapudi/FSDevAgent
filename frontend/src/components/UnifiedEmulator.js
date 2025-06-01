import React, { useState, useEffect, useRef, useCallback } from 'react';
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
  const [lastToolOutputsLength, setLastToolOutputsLength] = useState(0);
  const [terminalReady, setTerminalReady] = useState(false);
  
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

  // Callback ref for terminal container to ensure DOM node exists
  const terminalContainerRef = useCallback(node => {
    console.log('Terminal container ref callback triggered with node:', !!node);
    terminalRef.current = node;
    
    if (node) {
      console.log('Terminal DOM node is available, setting terminal ready');
      setTerminalReady(true);
    }
  }, []);
  
  // Initialize terminal when component mounts or when WebSocket connection changes
  useEffect(() => {
    console.log('Terminal initialization effect triggered');
    console.log('Terminal ref exists:', !!terminalRef.current);
    console.log('Terminal ready state:', terminalReady);
    console.log('WebSocket connected:', wsConnected);
    
    // Only initialize if DOM node is ready
    if (terminalReady && terminalRef.current) {
      try {
        console.log('Creating new terminal instance');
        
        // Dispose of existing terminal if it exists
        if (terminalInstanceRef.current && terminalInstanceRef.current.terminal) {
          console.log('Disposing existing terminal instance');
          terminalInstanceRef.current.terminal.dispose();
        }
        
        // Create terminal instance with fit addon
        const terminal = new Terminal({
          cursorBlink: true,
          fontSize: 14,
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
          theme: {
            background: '#1e1e1e',
            foreground: '#f0f0f0'
          },
          scrollback: 5000, // Increase scrollback buffer
          disableStdin: true // Disable input since this is output-only
        });
        
        const fitAddon = new FitAddon();
        terminal.loadAddon(fitAddon);
        
        // Open terminal in the container
        terminal.open(terminalRef.current);
        fitAddon.fit();
        
        // Write initial message to confirm terminal is working
        terminal.write('\r\n\x1b[32m[SYSTEM] Terminal initialized successfully\x1b[0m\r\n');
        terminal.write('\r\n\x1b[33m[STATUS] WebSocket ' + (wsConnected ? 'connected' : 'disconnected') + '\x1b[0m\r\n');
        
        // Store terminal instance in ref
        terminalInstanceRef.current = {
          terminal,
          fitAddon
        };
        
        setTerminalInitialized(true);
        console.log('Terminal initialization complete');
        
        // Handle window resize
        const handleResize = () => {
          if (terminalInstanceRef.current) {
            terminalInstanceRef.current.fitAddon.fit();
          }
        };
        
        window.addEventListener('resize', handleResize);
        
        // Immediately process any existing tool outputs
        if (toolOutputs && toolOutputs.length > 0) {
          console.log(`Processing ${toolOutputs.length} existing tool outputs after initialization`);
          processToolOutputs(toolOutputs);
        }
        
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
    } else {
      console.log('Terminal DOM node not ready yet, waiting for ref callback');
    }
  }, [terminalReady, wsConnected, toolOutputs]); // Re-initialize when terminal is ready or WebSocket connection changes
  
  // Process tool outputs and write to terminal
  const processToolOutputs = (outputs) => {
    if (!terminalInstanceRef.current || !terminalInstanceRef.current.terminal) {
      console.error('Cannot process tool outputs: terminal instance not available');
      return;
    }
    
    const terminal = terminalInstanceRef.current.terminal;
    
    // Clear terminal to avoid duplication
    terminal.clear();
    terminal.write('\x1b[32m[SYSTEM] Terminal initialized and ready\x1b[0m\r\n\r\n');
    terminal.write('\x1b[33m[STATUS] WebSocket ' + (wsConnected ? 'connected' : 'disconnected') + '\x1b[0m\r\n\r\n');
    
    // Debug info
    terminal.write(`\x1b[36m[DEBUG] Processing ${outputs.length} tool outputs\x1b[0m\r\n\r\n`);
    
    // Process each output
    outputs.forEach((output, index) => {
      try {
        console.log(`Processing output ${index}:`, output);
        
        // Add timestamp and index for better context
        const timestamp = new Date().toLocaleTimeString();
        terminal.write(`\r\n[${timestamp}] Output #${index}\r\n`);
        
        // Add tool name/type as context
        const toolName = output.toolName || output.type || 'unknown';
        terminal.write(`\x1b[36m[${toolName}]\x1b[0m\r\n`);
        
        // Extract output content from any possible location
        let outputText = '';
        
        if (output.output !== undefined) {
          outputText = typeof output.output === 'object' 
            ? JSON.stringify(output.output, null, 2) 
            : String(output.output);
        } else if (output.data !== undefined) {
          outputText = typeof output.data === 'object' 
            ? JSON.stringify(output.data, null, 2) 
            : String(output.data);
        } else if (output.content !== undefined) {
          outputText = typeof output.content === 'object' 
            ? JSON.stringify(output.content, null, 2) 
            : String(output.content);
        } else {
          // If no recognized output field, stringify the entire object
          outputText = JSON.stringify(output, null, 2);
        }
        
        // Write to terminal with proper formatting
        terminal.write(outputText + '\r\n');
        
        // Add visual separator
        terminal.write('\x1b[90m' + '-'.repeat(40) + '\x1b[0m\r\n');
      } catch (error) {
        console.error('Error processing output:', error);
        terminal.write(`\x1b[31mError displaying output ${index}: ${error.message}\x1b[0m\r\n`);
      }
    });
    
    // Force terminal to scroll to bottom
    terminal.scrollToBottom();
  };

  // Update terminal with new outputs - completely rewritten for reliability
  useEffect(() => {
    console.log('Terminal update effect triggered, terminalInitialized:', terminalInitialized);
    console.log('WebSocket connected:', wsConnected);
    console.log('toolOutputs length:', toolOutputs?.length);
    console.log('lastToolOutputsLength:', lastToolOutputsLength);
    
    // Ensure terminal is initialized before attempting to write
    if (!terminalInitialized || !terminalInstanceRef.current || !terminalInstanceRef.current.terminal) {
      console.log('Terminal not initialized yet, skipping update');
      return;
    }
    
    // Check if there are new outputs to process
    if (toolOutputs && Array.isArray(toolOutputs)) {
      console.log(`Processing tool outputs: ${toolOutputs.length} items`);
      setLastToolOutputsLength(toolOutputs.length);
      processToolOutputs(toolOutputs);
    } else if (!toolOutputs || !Array.isArray(toolOutputs)) {
      console.log('No valid toolOutputs array available');
      terminalInstanceRef.current.terminal.write('\r\n\x1b[33m[INFO] Waiting for valid tool outputs...\x1b[0m\r\n');
    }
    
  }, [toolOutputs, terminalInitialized]);
  
  // Direct DOM access to force terminal rendering with enhanced debugging
  const forceTerminalUpdate = () => {
    console.log('Attempting to force terminal update...');
    console.log('terminalRef current:', !!terminalRef.current);
    console.log('terminalInstanceRef current:', !!terminalInstanceRef.current);
    
    if (terminalInstanceRef.current && terminalInstanceRef.current.terminal) {
      console.log('Terminal instance found, forcing update via direct DOM access');
      
      try {
        // Force terminal to refresh by triggering a resize event
        window.dispatchEvent(new Event('resize'));
        console.log('Resize event dispatched');
        
        // Write a test message to confirm terminal is responsive
        terminalInstanceRef.current.terminal.write('\r\n\x1b[33m[SYSTEM] Terminal refresh triggered at ' + new Date().toISOString() + '\x1b[0m\r\n');
        console.log('Test message written to terminal');
        
        // Force terminal to focus to ensure it's active
        terminalInstanceRef.current.terminal.focus();
        console.log('Terminal focus forced');
        
        // Process tool outputs if available
        if (toolOutputs && toolOutputs.length > 0) {
          processToolOutputs(toolOutputs);
          return true;
        } else {
          terminalInstanceRef.current.terminal.write('\r\n\x1b[31m[WARNING] No tool outputs available\x1b[0m\r\n');
        }
        
        return true;
      } catch (error) {
        console.error('Error during terminal update:', error);
        return false;
      }
    }
    console.error('Terminal instance not available for update');
    return false;
  };
  
  // Render browser content
  const renderBrowserContent = () => {
    const browserOutputs = toolOutputs?.filter(output => 
      ['browser_automation', 'data_visualization'].includes(output.toolName)
    ) || [];
    
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
    const codeOutputs = toolOutputs?.filter(output => 
      ['code_intelligence'].includes(output.toolName)
    ) || [];
    
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
  
  // Render fallback content when terminal is not available
  const renderFallbackTerminal = () => {
    if (!toolOutputs || toolOutputs.length === 0) {
      return (
        <div className="fallback-terminal">
          <p>No tool outputs available yet.</p>
        </div>
      );
    }
    
    // Group outputs by tool type for better organization
    const groupedOutputs = toolOutputs.reduce((acc, output) => {
      const toolName = output.toolName || 'unknown';
      if (!acc[toolName]) {
        acc[toolName] = [];
      }
      acc[toolName].push(output);
      return acc;
    }, {});
    
    return (
      <div className="fallback-terminal">
        <h4>Tool Outputs</h4>
        {Object.entries(groupedOutputs).map(([toolName, outputs], groupIndex) => (
          <div key={groupIndex} className="tool-group">
            <div className="tool-group-header">
              <span className="tool-name">{toolName}</span>
              <span className="output-count">{outputs.length} outputs</span>
            </div>
            
            {outputs.map((output, index) => {
              // Determine output content based on tool type
              let formattedOutput = '';
              let outputType = '';
              
              try {
                if (toolName === 'execute_command') {
                  outputType = 'terminal';
                  if (typeof output.output === 'string') {
                    formattedOutput = output.output;
                  } else if (output.output && output.output.content) {
                    formattedOutput = output.output.content;
                  } else {
                    formattedOutput = JSON.stringify(output.output, null, 2);
                  }
                } else if (toolName === 'file_system') {
                  outputType = 'file';
                  if (output.output && output.output.type === 'file_content') {
                    formattedOutput = output.output.content;
                  } else if (output.output && output.output.type === 'directory_listing') {
                    formattedOutput = formatDirectoryListing(output.output.metadata?.entries);
                  } else {
                    formattedOutput = JSON.stringify(output.output, null, 2);
                  }
                } else if (toolName === 'git_operations') {
                  outputType = 'git';
                  if (output.output && output.output.type === 'git_status') {
                    formattedOutput = formatGitStatus(output.output.metadata);
                  } else if (output.output && output.output.type === 'git_log') {
                    formattedOutput = formatGitLog(output.output.metadata?.commits);
                  } else if (output.output && output.output.content) {
                    formattedOutput = output.output.content;
                  } else {
                    formattedOutput = JSON.stringify(output.output, null, 2);
                  }
                } else if (toolName === 'build_tool') {
                  outputType = 'build';
                  if (Array.isArray(output.output)) {
                    formattedOutput = output.output.map(line => line.content).join('\n');
                  } else if (output.output && output.output.content) {
                    formattedOutput = output.output.content;
                  } else {
                    formattedOutput = JSON.stringify(output.output, null, 2);
                  }
                } else if (toolName === 'code_intelligence') {
                  outputType = 'code';
                  if (output.output && output.output.type === 'analysis_result') {
                    formattedOutput = formatCodeAnalysis(output.output.metadata);
                  } else if (output.output && output.output.type === 'method_search_result') {
                    formattedOutput = formatMethodSearch(output.output.metadata?.methods);
                  } else {
                    formattedOutput = JSON.stringify(output.output, null, 2);
                  }
                } else {
                  // Default handling for other tool types
                  if (output.output !== undefined) {
                    formattedOutput = typeof output.output === 'object' 
                      ? JSON.stringify(output.output, null, 2) 
                      : String(output.output);
                  } else if (output.data !== undefined) {
                    formattedOutput = typeof output.data === 'object' 
                      ? JSON.stringify(output.data, null, 2) 
                      : String(output.data);
                  } else {
                    formattedOutput = JSON.stringify(output, null, 2);
                  }
                }
              } catch (error) {
                formattedOutput = `Error parsing output: ${error.message}`;
              }
              
              return (
                <div key={index} className={`fallback-output ${outputType}`}>
                  <div className="fallback-header">
                    <span className="fallback-tool">{output.toolName || 'unknown'}</span>
                    <span className="fallback-index">#{index + 1}</span>
                    <span className="fallback-timestamp">
                      {output.timestamp ? new Date(output.timestamp).toLocaleTimeString() : ''}
                    </span>
                  </div>
                  <pre className="fallback-content">{formattedOutput}</pre>
                </div>
              );
            })}
          </div>
        ))}
      </div>
    );
  };
  
  // Helper function to format file size
  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Helper function to format directory listing
  const formatDirectoryListing = (entries) => {
    if (!entries || !Array.isArray(entries)) {
      return 'No entries available';
    }
    
    return entries.map(entry => {
      const size = entry.isDirectory ? '-' : formatFileSize(entry.size);
      return `${entry.isDirectory ? 'd' : '-'} ${entry.name.padEnd(30)} ${size}`;
    }).join('\n');
  };
  
  // Helper function to format git status
  const formatGitStatus = (metadata) => {
    if (!metadata) {
      return 'No git status information available';
    }
    
    let result = '';
    
    if (metadata.modified && metadata.modified.length > 0) {
      result += 'Modified files:\n';
      result += metadata.modified.map(file => `  M ${file}`).join('\n');
      result += '\n\n';
    }
    
    if (metadata.untracked && metadata.untracked.length > 0) {
      result += 'Untracked files:\n';
      result += metadata.untracked.map(file => `  ? ${file}`).join('\n');
    }
    
    return result || 'Working directory clean';
  };
  
  // Helper function to format git log
  const formatGitLog = (commits) => {
    if (!commits || !Array.isArray(commits)) {
      return 'No commit history available';
    }
    
    return commits.map(commit => {
      return `commit ${commit.id.substring(0, 7)}\nAuthor: ${commit.author}\nDate: ${commit.date}\n\n    ${commit.message}`;
    }).join('\n\n');
  };
  
  // Helper function to format code analysis
  const formatCodeAnalysis = (metadata) => {
    if (!metadata) {
      return 'No code analysis information available';
    }
    
    let result = '';
    
    if (metadata.classes && metadata.classes.length > 0) {
      result += 'Classes:\n';
      result += metadata.classes.map(cls => `  - ${cls}`).join('\n');
      result += '\n\n';
    }
    
    if (metadata.methods && metadata.methods.length > 0) {
      result += 'Methods:\n';
      result += metadata.methods.map(method => `  - ${method}`).join('\n');
    }
    
    return result || 'No classes or methods found';
  };
  
  // Helper function to format method search
  const formatMethodSearch = (methods) => {
    if (!methods || !Array.isArray(methods)) {
      return 'No methods found';
    }
    
    return methods.map(method => {
      return `${method.returnType} ${method.name}\n  ${method.file}:${method.line}`;
    }).join('\n\n');
  };
  
  // Render appropriate content based on tool type
  const renderContent = () => {
    // Debug: Log tool outputs and state to console
    console.log('UnifiedEmulator state:', {
      toolOutputs: toolOutputs ? toolOutputs.length : 0,
      wsConnected,
      terminalInitialized,
      terminalReady,
      currentToolType
    });
    
    if (toolOutputs && toolOutputs.length > 0) {
      console.log('Tool outputs available:', toolOutputs);
    }
    
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
          <button 
            className="retry-button"
            onClick={() => {
              // Force terminal reinitialization
              setTerminalInitialized(false);
              setTerminalReady(false);
              setTimeout(() => {
                if (terminalRef.current) {
                  // This will trigger the initialization useEffect
                  setTerminalReady(true);
                }
              }, 500);
            }}
          >
            Retry Connection
          </button>
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
    
    // If there are tool outputs, always render them in the fallback terminal
    // This is the key fix - we always render the fallback terminal when tool outputs exist
    if (toolOutputs && toolOutputs.length > 0) {
      console.log('Rendering fallback terminal with tool outputs:', toolOutputs);
      return renderFallbackTerminal();
    }
    
    // Default fallback if no other conditions are met
    return renderFallbackTerminal();
  };
  
  // Debug button to force terminal refresh
  const renderDebugControls = () => {
    return (
      <div className="debug-controls">
        <button 
          className="refresh-button"
          onClick={() => {
            console.log('Manual refresh triggered');
            if (terminalInitialized) {
              forceTerminalUpdate();
            } else {
              // Reinitialize terminal
              setTerminalReady(false);
              setTimeout(() => setTerminalReady(true), 100);
            }
          }}
        >
          Refresh Terminal
        </button>
        <div className="debug-status">
          <div>Terminal Ready: {terminalReady ? 'Yes' : 'No'}</div>
          <div>Terminal Initialized: {terminalInitialized ? 'Yes' : 'No'}</div>
          <div>Tool Outputs: {toolOutputs?.length || 0}</div>
        </div>
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
        {renderContent()}
      </div>
      {renderDebugControls()}
    </div>
  );
};

export default UnifiedEmulator;
