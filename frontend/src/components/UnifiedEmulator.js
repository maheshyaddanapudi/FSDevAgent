// Fixed UnifiedEmulator.js - Issue #4: Terminal Visualization Issues Fix
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { WebLinksAddon } from 'xterm-addon-web-links';
import 'xterm/css/xterm.css';
import '../styles/UnifiedEmulator.css';

/**
 * UnifiedEmulator - Issue #4 Fix: Robust terminal with proper initialization and error handling
 */
const UnifiedEmulator = ({ toolOutputs, wsConnected }) => {
  // State management
  const [terminalReady, setTerminalReady] = useState(false);
  const [terminalError, setTerminalError] = useState(null);
  const [outputCount, setOutputCount] = useState(0);
  const [lastProcessedIndex, setLastProcessedIndex] = useState(-1);
  
  // Refs for terminal management
  const terminalRef = useRef(null);
  const terminalInstanceRef = useRef(null);
  const fitAddonRef = useRef(null);
  const webLinksAddonRef = useRef(null);
  const resizeObserverRef = useRef(null);
  const initTimeoutRef = useRef(null);
  
  // Issue #4 Fix: Memoized terminal configuration
  const terminalConfig = useMemo(() => ({
    cursorBlink: true,
    fontSize: 14,
    fontFamily: '"Cascadia Code", "Fira Code", "Consolas", "Monaco", "Courier New", monospace',
    theme: {
      background: '#1e1e1e',
      foreground: '#f0f0f0',
      cursor: '#f0f0f0',
      selection: 'rgba(255, 255, 255, 0.3)',
      black: '#000000',
      red: '#cd3131',
      green: '#0dbc79',
      yellow: '#e5e510',
      blue: '#2472c8',
      magenta: '#bc3fbc',
      cyan: '#11a8cd',
      white: '#e5e5e5',
      brightBlack: '#666666',
      brightRed: '#f14c4c',
      brightGreen: '#23d18b',
      brightYellow: '#f5f543',
      brightBlue: '#3b8eea',
      brightMagenta: '#d670d6',
      brightCyan: '#29b8db',
      brightWhite: '#e5e5e5'
    },
    scrollback: 10000,
    convertEol: true,
    allowTransparency: false,
    disableStdin: true, // Read-only terminal
    cursorStyle: 'block',
    allowProposedApi: true
  }), []);

  // Utility function for debouncing
  const debounce = (func, wait) => {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  };

  // Issue #4 Fix: Robust terminal initialization with comprehensive error handling
  const initializeTerminal = useCallback(() => {
    // Clear any existing initialization timeout
    if (initTimeoutRef.current) {
      clearTimeout(initTimeoutRef.current);
      initTimeoutRef.current = null;
    }

    // Clean up existing terminal
    if (terminalInstanceRef.current) {
      try {
        terminalInstanceRef.current.dispose();
      } catch (error) {
        console.warn('Error disposing existing terminal:', error);
      }
      terminalInstanceRef.current = null;
    }

    // Validate terminal container
    if (!terminalRef.current) {
      console.error('Terminal container ref not available');
      setTerminalError('Terminal container not found');
      return;
    }

    // Check container dimensions
    const container = terminalRef.current;
    if (!container.offsetWidth || !container.offsetHeight) {
      console.warn('Terminal container has no dimensions, retrying...');
      initTimeoutRef.current = setTimeout(initializeTerminal, 100);
      return;
    }

    try {
      console.log('Initializing xterm.js terminal...');
      
      // Create terminal instance
      const terminal = new Terminal(terminalConfig);
      
      // Create and load addons with error handling
      try {
        const fitAddon = new FitAddon();
        const webLinksAddon = new WebLinksAddon();
        
        terminal.loadAddon(fitAddon);
        terminal.loadAddon(webLinksAddon);
        
        fitAddonRef.current = fitAddon;
        webLinksAddonRef.current = webLinksAddon;
      } catch (addonError) {
        console.error('Error loading terminal addons:', addonError);
        // Continue without addons if they fail
      }

      // Open terminal in container
      terminal.open(container);
      
      // Store terminal reference
      terminalInstanceRef.current = terminal;

      // Issue #4 Fix: Improved terminal fitting with error handling
      const performFit = () => {
        try {
          if (fitAddonRef.current && container.offsetWidth > 0 && container.offsetHeight > 0) {
            fitAddonRef.current.fit();
            return true;
          }
        } catch (fitError) {
          console.warn('Error fitting terminal:', fitError);
        }
        return false;
      };

      // Initial fit after a short delay
      setTimeout(() => {
        if (performFit()) {
          setTerminalReady(true);
          setTerminalError(null);
          console.log('Terminal initialized successfully');
          
          // Write welcome message
          writeWelcomeMessage(terminal);
          
          // Process existing outputs
          if (toolOutputs && toolOutputs.length > 0) {
            processToolOutputs(terminal, toolOutputs);
            setLastProcessedIndex(toolOutputs.length - 1);
          }
        } else {
          console.warn('Initial terminal fit failed, terminal may not display correctly');
          setTerminalReady(true); // Still mark as ready to allow usage
          setTerminalError('Terminal display may be incorrect');
        }
      }, 100);

      // Issue #4 Fix: Setup resize observer for responsive terminal
      if (window.ResizeObserver) {
        resizeObserverRef.current = new ResizeObserver(
          debounce(() => {
            if (terminalReady && performFit()) {
              console.log('Terminal resized successfully');
            }
          }, 250)
        );
        resizeObserverRef.current.observe(container);
      } else {
        // Fallback for browsers without ResizeObserver
        const handleResize = debounce(() => {
          if (terminalReady) performFit();
        }, 250);
        window.addEventListener('resize', handleResize);
        
        // Store cleanup function
        terminalInstanceRef.current._cleanupResize = () => {
          window.removeEventListener('resize', handleResize);
        };
      }

    } catch (error) {
      console.error('Error initializing terminal:', error);
      setTerminalError(`Terminal initialization failed: ${error.message}`);
      setTerminalReady(false);
    }
  }, [terminalConfig, terminalReady, toolOutputs]);

  // Issue #4 Fix: Initialize terminal when component mounts or container becomes available
  useEffect(() => {
    if (terminalRef.current && !terminalInstanceRef.current) {
      // Small delay to ensure DOM is fully ready
      initTimeoutRef.current = setTimeout(initializeTerminal, 50);
    }

    return () => {
      if (initTimeoutRef.current) {
        clearTimeout(initTimeoutRef.current);
      }
    };
  }, [initializeTerminal]);

  // Issue #4 Fix: Process new tool outputs when they arrive
  useEffect(() => {
    if (!terminalInstanceRef.current || !terminalReady || !toolOutputs) {
      return;
    }

    const newOutputs = toolOutputs.slice(lastProcessedIndex + 1);
    if (newOutputs.length > 0) {
      processToolOutputs(terminalInstanceRef.current, newOutputs);
      setLastProcessedIndex(toolOutputs.length - 1);
      setOutputCount(toolOutputs.length);
    }
  }, [toolOutputs, terminalReady, lastProcessedIndex]);

  // Issue #4 Fix: Update connection status in terminal
  useEffect(() => {
    if (terminalInstanceRef.current && terminalReady) {
      const terminal = terminalInstanceRef.current;
      const statusColor = wsConnected ? '\x1b[32m' : '\x1b[31m';
      const statusText = wsConnected ? 'Connected' : 'Disconnected';
      
      // Only write status update, don't clear everything
      terminal.write(`\r\n${statusColor}● WebSocket: ${statusText}\x1b[0m\r\n`);
    }
  }, [wsConnected, terminalReady]);

  // Issue #4 Fix: Clean up resources on unmount
  useEffect(() => {
    return () => {
      // Clean up resize observer
      if (resizeObserverRef.current) {
        resizeObserverRef.current.disconnect();
        resizeObserverRef.current = null;
      }

      // Clean up terminal
      if (terminalInstanceRef.current) {
        try {
          if (terminalInstanceRef.current._cleanupResize) {
            terminalInstanceRef.current._cleanupResize();
          }
          terminalInstanceRef.current.dispose();
        } catch (error) {
          console.warn('Error during terminal cleanup:', error);
        }
        terminalInstanceRef.current = null;
      }

      // Clear timeout
      if (initTimeoutRef.current) {
        clearTimeout(initTimeoutRef.current);
        initTimeoutRef.current = null;
      }
    };
  }, []);

  // Issue #4 Fix: Welcome message with better formatting
  const writeWelcomeMessage = (terminal) => {
    try {
      const timestamp = new Date().toLocaleTimeString();
      terminal.write('\r\n\x1b[36m╭─ AI Developer Agent Terminal ─╮\x1b[0m\r\n');
      terminal.write('\x1b[36m│\x1b[0m \x1b[32m●\x1b[0m Terminal Ready               \x1b[36m│\x1b[0m\r\n');
      terminal.write(`\x1b[36m│\x1b[0m \x1b[33m○\x1b[0m ${timestamp}                   \x1b[36m│\x1b[0m\r\n`);
      terminal.write('\x1b[36m╰─────────────────────────────────╯\x1b[0m\r\n\r\n');
    } catch (error) {
      console.warn('Error writing welcome message:', error);
    }
  };

  // Issue #4 Fix: Robust tool output processing with better formatting
  const processToolOutputs = (terminal, outputs) => {
    if (!terminal || !outputs || !Array.isArray(outputs)) {
      return;
    }

    outputs.forEach((output, index) => {
      try {
        const timestamp = new Date().toLocaleTimeString();
        const toolName = output.toolName || output.type || 'unknown';
        
        // Write header with tool information
        terminal.write(`\r\n\x1b[90m[${timestamp}]\x1b[0m `);
        terminal.write(`\x1b[36m${toolName}\x1b[0m`);
        
        if (output.args) {
          const operation = output.args.operation || output.args.action || '';
          if (operation) {
            terminal.write(` \x1b[90m→\x1b[0m \x1b[33m${operation}\x1b[0m`);
          }
        }
        
        terminal.write('\r\n');
        
        // Process output content
        let content = extractOutputContent(output);
        if (content) {
          // Apply syntax highlighting for different content types
          const formattedContent = formatOutputContent(content, toolName);
          writeFormattedContent(terminal, formattedContent);
        } else {
          terminal.write('\x1b[90m(no output)\x1b[0m\r\n');
        }
        
        // Add separator between outputs
        if (index < outputs.length - 1) {
          terminal.write('\x1b[90m' + '─'.repeat(60) + '\x1b[0m\r\n');
        }
        
      } catch (error) {
        console.error('Error processing tool output:', error);
        terminal.write(`\x1b[31mError displaying output: ${error.message}\x1b[0m\r\n`);
      }
    });
    
    // Auto-scroll to bottom
    terminal.scrollToBottom();
  };

  // Issue #4 Fix: Extract content from various output formats
  const extractOutputContent = (output) => {
    if (output.output !== undefined) {
      if (typeof output.output === 'string') {
        return output.output;
      } else if (output.output && output.output.content) {
        return output.output.content;
      } else if (typeof output.output === 'object') {
        return JSON.stringify(output.output, null, 2);
      }
    }
    
    if (output.content !== undefined) {
      return typeof output.content === 'string' ? output.content : JSON.stringify(output.content, null, 2);
    }
    
    if (output.data !== undefined) {
      return typeof output.data === 'string' ? output.data : JSON.stringify(output.data, null, 2);
    }
    
    // Fallback: stringify the entire output
    return JSON.stringify(output, null, 2);
  };

  // Issue #4 Fix: Format content based on tool type and content
  const formatOutputContent = (content, toolName) => {
    if (!content || typeof content !== 'string') {
      return String(content || '');
    }

    switch (toolName) {
      case 'execute_command':
        return formatCommandOutput(content);
      case 'file_system':
        return formatFileSystemOutput(content);
      case 'git_operations':
        return formatGitOutput(content);
      default:
        return content;
    }
  };

  // Issue #4 Fix: Format command output with ANSI colors
  const formatCommandOutput = (content) => {
    return content
      .replace(/ERROR/g, '\x1b[31mERROR\x1b[0m')
      .replace(/WARN/g, '\x1b[33mWARN\x1b[0m')
      .replace(/INFO/g, '\x1b[36mINFO\x1b[0m')
      .replace(/SUCCESS/g, '\x1b[32mSUCCESS\x1b[0m');
  };

  // Issue #4 Fix: Format file system output
  const formatFileSystemOutput = (content) => {
    return content
      .replace(/^(drwx)/gm, '\x1b[34m$1\x1b[0m') // Directories in blue
      .replace(/^(-rw)/gm, '\x1b[37m$1\x1b[0m')  // Files in white
      .replace(/(\d{4}-\d{2}-\d{2} \d{2}:\d{2})/g, '\x1b[90m$1\x1b[0m'); // Timestamps in gray
  };

  // Issue #4 Fix: Format git output
  const formatGitOutput = (content) => {
    return content
      .replace(/^(commit [a-f0-9]{7,})/gm, '\x1b[33m$1\x1b[0m') // Commit hashes in yellow
      .replace(/^(Author:)/gm, '\x1b[36m$1\x1b[0m')            // Author in cyan
      .replace(/^(Date:)/gm, '\x1b[36m$1\x1b[0m')              // Date in cyan
      .replace(/^(\+.*)/gm, '\x1b[32m$1\x1b[0m')               // Additions in green
      .replace(/^(-.*)/gm, '\x1b[31m$1\x1b[0m');               // Deletions in red
  };

  // Issue #4 Fix: Write formatted content to terminal with proper line handling
  const writeFormattedContent = (terminal, content) => {
    if (!content) return;
    
    const lines = content.split('\n');
    lines.forEach((line, index) => {
      terminal.write(line);
      if (index < lines.length - 1) {
        terminal.write('\r\n');
      }
    });
    terminal.write('\r\n');
  };

  // Issue #4 Fix: Manual terminal refresh function
  const handleRefresh = useCallback(() => {
    if (terminalInstanceRef.current) {
      try {
        terminalInstanceRef.current.clear();
        writeWelcomeMessage(terminalInstanceRef.current);
        
        if (toolOutputs && toolOutputs.length > 0) {
          processToolOutputs(terminalInstanceRef.current, toolOutputs);
        }
        
        console.log('Terminal refreshed successfully');
      } catch (error) {
        console.error('Error refreshing terminal:', error);
        setTerminalError(`Refresh failed: ${error.message}`);
      }
    } else {
      // Reinitialize terminal if not available
      setTerminalReady(false);
      initializeTerminal();
    }
  }, [toolOutputs, initializeTerminal]);

  // Issue #4 Fix: Clear terminal function
  const handleClear = useCallback(() => {
    if (terminalInstanceRef.current) {
      try {
        terminalInstanceRef.current.clear();
        writeWelcomeMessage(terminalInstanceRef.current);
        setLastProcessedIndex(-1);
        setOutputCount(0);
        console.log('Terminal cleared successfully');
      } catch (error) {
        console.error('Error clearing terminal:', error);
      }
    }
  }, []);

  // Issue #4 Fix: Render error state
  const renderErrorState = () => (
    <div className="terminal-error">
      <div className="error-icon">⚠️</div>
      <h3>Terminal Error</h3>
      <p>{terminalError}</p>
      <button onClick={initializeTerminal} className="retry-button">
        Retry
      </button>
    </div>
  );

  // Issue #4 Fix: Render loading state
  const renderLoadingState = () => (
    <div className="terminal-loading">
      <div className="loading-spinner"></div>
      <p>Initializing terminal...</p>
    </div>
  );

  return (
    <div className="unified-emulator">
      <div className="emulator-header">
        <h3>Terminal Output</h3>
        <div className="header-controls">
          <button onClick={handleClear} className="control-button" title="Clear Terminal">
            🗑️
          </button>
          <button onClick={handleRefresh} className="control-button" title="Refresh Terminal">
            🔄
          </button>
          <div className="connection-status">
            <span className={`status-indicator ${wsConnected ? 'connected' : 'disconnected'}`}></span>
            <span className="status-text">{wsConnected ? 'Connected' : 'Disconnected'}</span>
          </div>
        </div>
      </div>
      
      <div className="emulator-content">
        {terminalError ? (
          renderErrorState()
        ) : !terminalReady ? (
          renderLoadingState()
        ) : (
          <div className="terminal-container" ref={terminalRef}></div>
        )}
      </div>
      
      <div className="emulator-footer">
        <div className="output-count">
          {outputCount} {outputCount === 1 ? 'output' : 'outputs'}
        </div>
        {!wsConnected && (
          <button onClick={handleRefresh} className="reconnect-button">
            Retry Connection
          </button>
        )}
      </div>
    </div>
  );
};

export default UnifiedEmulator;
