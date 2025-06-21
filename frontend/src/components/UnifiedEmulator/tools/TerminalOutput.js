// frontend/src/components/UnifiedEmulator/tools/TerminalOutput.js
import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { SearchAddon } from 'xterm-addon-search';
import { WebLinksAddon } from 'xterm-addon-web-links';
import { useEmulatorStore, selectSettings } from '../../../store/emulatorStore';
import 'xterm/css/xterm.css';
import './TerminalOutput.css';

/**
 * Terminal output component using xterm.js
 * Provides full terminal emulation with ANSI support, search, and links
 */
const TerminalOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const terminalRef = useRef(null);
  const terminalInstanceRef = useRef(null);
  const fitAddonRef = useRef(null);
  const searchAddonRef = useRef(null);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [isTerminalReady, setIsTerminalReady] = useState(false);
  
  const { settings, updateToolActivity } = useEmulatorStore(
    useCallback(state => ({
      settings: selectSettings(state),
      updateToolActivity: state.updateToolActivity
    }), [])
  );

  // Initialize terminal
  useEffect(() => {
    if (!terminalRef.current) return;

    console.log('Initializing xterm.js terminal');
    
    // Small delay to ensure DOM is ready
    const initTimeout = setTimeout(() => {
      try {
        // Create terminal instance
        const terminal = new Terminal({
          cursorBlink: true,
          fontSize: settings?.fontSize || 14,
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
          theme: {
            background: settings?.theme === 'dark' ? '#1e1e1e' : '#ffffff',
            foreground: settings?.theme === 'dark' ? '#f0f0f0' : '#333333',
            cursor: '#f0f0f0',
            selection: 'rgba(255, 255, 255, 0.3)'
          },
          scrollback: 10000,
          convertEol: true,
          disableStdin: false // Enable input for interactive terminals
        });

        // Create addons
        const fitAddon = new FitAddon();
        const searchAddon = new SearchAddon();
        const webLinksAddon = new WebLinksAddon();

        // Load addons
        terminal.loadAddon(fitAddon);
        terminal.loadAddon(searchAddon);
        terminal.loadAddon(webLinksAddon);

        // Open terminal in DOM element
        terminal.open(terminalRef.current);

        // Fit terminal after a small delay to ensure dimensions are available
        setTimeout(() => {
          try {
            // Check if container has dimensions before fitting
            if (terminalRef.current && terminalRef.current.offsetWidth > 0 && terminalRef.current.offsetHeight > 0) {
              fitAddon.fit();
              setIsTerminalReady(true);
            } else {
              console.warn('Terminal container has no dimensions yet, retrying...');
              // Retry fitting after another delay
              setTimeout(() => {
                if (terminalRef.current && terminalRef.current.offsetWidth > 0 && terminalRef.current.offsetHeight > 0) {
                  fitAddon.fit();
                  setIsTerminalReady(true);
                }
              }, 500);
            }
          } catch (fitError) {
            console.error('Error fitting terminal:', fitError);
            // Terminal is still usable even if fit fails
            setIsTerminalReady(true);
          }
        }, 100);

        // Store references
        terminalInstanceRef.current = terminal;
        fitAddonRef.current = fitAddon;
        searchAddonRef.current = searchAddon;

        // Write initial status
        terminal.write('\r\n\x1b[32m● Terminal Ready\x1b[0m\r\n');

        // Handle resize with debouncing
        let resizeTimeout;
        const handleResize = () => {
          clearTimeout(resizeTimeout);
          resizeTimeout = setTimeout(() => {
            if (fitAddonRef.current && terminalRef.current) {
              try {
                // Check dimensions before fitting
                if (terminalRef.current.offsetWidth > 0 && terminalRef.current.offsetHeight > 0) {
                  fitAddonRef.current.fit();
                }
              } catch (error) {
                console.error('Error during resize fit:', error);
              }
            }
          }, 100);
        };

        window.addEventListener('resize', handleResize);

        // Process existing outputs after terminal is ready
        if (allOutputs && allOutputs.length > 0) {
          // Wait for terminal to be fully ready
          setTimeout(() => {
            processOutputs(terminal, allOutputs);
          }, 200);
        }

        // Cleanup
        return () => {
          clearTimeout(resizeTimeout);
          window.removeEventListener('resize', handleResize);
          if (terminal) {
            terminal.dispose();
          }
        };
      } catch (error) {
        console.error('Error initializing terminal:', error);
      }
    }, 50); // Small initial delay

    return () => {
      clearTimeout(initTimeout);
    };
  }, []); // Only run once on mount

  // Update terminal when outputs change
  useEffect(() => {
    if (!terminalInstanceRef.current || !allOutputs || !isTerminalReady) return;

    const terminal = terminalInstanceRef.current;
    
    try {
      // Clear and rewrite all outputs
      terminal.clear();
      terminal.write('\r\n\x1b[32m● Terminal Output\x1b[0m\r\n\r\n');
      
      processOutputs(terminal, allOutputs);
      
      // Update activity timestamp
      if (updateToolActivity && toolId) {
        updateToolActivity(toolId);
      }
      
      // Auto-scroll if enabled
      if (settings?.autoScroll !== false) {
        terminal.scrollToBottom();
      }
    } catch (error) {
      console.error('Error updating terminal outputs:', error);
    }
  }, [allOutputs, toolId, settings?.autoScroll, updateToolActivity, isTerminalReady]);

  // Process and write outputs to terminal
  const processOutputs = (terminal, outputs) => {
    if (!terminal || !outputs) return;

    outputs.forEach((output, index) => {
      try {
        // Add timestamp
        const timestamp = output.timestamp 
          ? new Date(output.timestamp).toLocaleTimeString() 
          : new Date().toLocaleTimeString();
        
        terminal.write(`\x1b[90m[${timestamp}]\x1b[0m `);

        // Extract and write content
        let content = '';
        
        if (output.output) {
          if (typeof output.output === 'string') {
            content = output.output;
          } else if (output.output.content) {
            content = output.output.content;
          } else if (output.output.type === 'stdout' || output.output.type === 'stderr') {
            const color = output.output.type === 'stderr' ? '\x1b[31m' : '';
            content = `${color}${output.output.content || JSON.stringify(output.output)}\x1b[0m`;
          } else {
            content = JSON.stringify(output.output, null, 2);
          }
        } else {
          content = JSON.stringify(output, null, 2);
        }

        // Write content with proper line endings
        content.split('\n').forEach((line, i) => {
          if (i > 0) terminal.write('\r\n');
          terminal.write(line);
        });
        
        terminal.write('\r\n');
        
        // Add separator between outputs
        if (index < outputs.length - 1) {
          terminal.write('\x1b[90m' + '─'.repeat(80) + '\x1b[0m\r\n');
        }
      } catch (error) {
        console.error('Error processing terminal output:', error);
        terminal.write(`\x1b[31mError displaying output: ${error.message}\x1b[0m\r\n`);
      }
    });
  };

  // Handle search
  const handleSearch = useCallback(() => {
    setIsSearchOpen(!isSearchOpen);
  }, [isSearchOpen]);

  const performSearch = useCallback((query) => {
    if (searchAddonRef.current && query) {
      try {
        searchAddonRef.current.findNext(query);
      } catch (error) {
        console.error('Error performing search:', error);
      }
    }
  }, []);

  const handleSearchKeyDown = useCallback((e) => {
    if (e.key === 'Enter') {
      performSearch(searchQuery);
    } else if (e.key === 'Escape') {
      setIsSearchOpen(false);
      setSearchQuery('');
    }
  }, [searchQuery, performSearch]);

  // Handle clear
  const handleClear = useCallback(() => {
    if (terminalInstanceRef.current && isTerminalReady) {
      try {
        terminalInstanceRef.current.clear();
        terminalInstanceRef.current.write('\r\n\x1b[32m● Terminal Cleared\x1b[0m\r\n\r\n');
      } catch (error) {
        console.error('Error clearing terminal:', error);
      }
    }
  }, [isTerminalReady]);

  return (
    <div className="terminal-output-container">
      <div className="terminal-toolbar">
        <button 
          className="toolbar-button"
          onClick={handleSearch}
          title="Search (Ctrl+F)"
          disabled={!isTerminalReady}
        >
          🔍
        </button>
        <button 
          className="toolbar-button"
          onClick={handleClear}
          title="Clear Terminal"
          disabled={!isTerminalReady}
        >
          🗑️
        </button>
        <div className="terminal-status">
          <span className={`status-dot ${wsConnected ? 'connected' : 'disconnected'}`}></span>
          <span className="output-count">{allOutputs?.length || 0} outputs</span>
        </div>
      </div>
      
      {isSearchOpen && (
        <div className="terminal-search">
          <input
            type="text"
            placeholder="Search..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={handleSearchKeyDown}
            autoFocus
          />
          <button onClick={() => performSearch(searchQuery)}>Find</button>
          <button onClick={() => setIsSearchOpen(false)}>✕</button>
        </div>
      )}
      
      <div 
        ref={terminalRef} 
        className="terminal-container"
        style={{ 
          height: isSearchOpen ? 'calc(100% - 80px)' : 'calc(100% - 40px)',
          minHeight: '200px' // Ensure minimum height
        }}
      />
    </div>
  );
};

export default TerminalOutput;
