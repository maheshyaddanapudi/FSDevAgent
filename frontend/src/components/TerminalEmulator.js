import React, { useEffect, useRef } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';

const TerminalEmulator = ({ sessionId, toolOutput }) => {
  const terminalRef = useRef(null);
  const terminalInstanceRef = useRef(null);
  const fitAddonRef = useRef(null);

  useEffect(() => {
    // Initialize terminal if it doesn't exist
    if (!terminalInstanceRef.current && terminalRef.current) {
      // Create terminal instance
      terminalInstanceRef.current = new Terminal({
        cursorBlink: true,
        fontSize: 14,
        fontFamily: 'Menlo, Monaco, "Courier New", monospace',
        theme: {
          background: '#1e1e1e',
          foreground: '#f8f8f8',
          cursor: '#f8f8f8',
          black: '#000000',
          red: '#e06c75',
          green: '#98c379',
          yellow: '#e5c07b',
          blue: '#61afef',
          magenta: '#c678dd',
          cyan: '#56b6c2',
          white: '#d0d0d0',
          brightBlack: '#808080',
          brightRed: '#ff5370',
          brightGreen: '#c3e88d',
          brightYellow: '#ffcb6b',
          brightBlue: '#82aaff',
          brightMagenta: '#c792ea',
          brightCyan: '#89ddff',
          brightWhite: '#ffffff'
        }
      });

      // Create fit addon
      fitAddonRef.current = new FitAddon();
      terminalInstanceRef.current.loadAddon(fitAddonRef.current);

      // Open terminal in the container
      terminalInstanceRef.current.open(terminalRef.current);
      
      // Fit terminal to container
      setTimeout(() => {
        if (fitAddonRef.current) {
          fitAddonRef.current.fit();
        }
      }, 100);

      // Add welcome message
      terminalInstanceRef.current.writeln('\x1b[1;34m=== AI Developer Agent Terminal ===\x1b[0m');
      terminalInstanceRef.current.writeln('\x1b[90mSession ID: ' + sessionId + '\x1b[0m');
      terminalInstanceRef.current.writeln('\x1b[90mTerminal ready for tool output streaming\x1b[0m');
      terminalInstanceRef.current.writeln('');
    }

    // Handle window resize
    const handleResize = () => {
      if (fitAddonRef.current) {
        fitAddonRef.current.fit();
      }
    };

    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, [sessionId]);

  // Process tool output and write to terminal
  useEffect(() => {
    if (terminalInstanceRef.current && toolOutput) {
      // Process ANSI color codes and special characters
      terminalInstanceRef.current.writeln(toolOutput);
      
      // Scroll to bottom
      terminalInstanceRef.current.scrollToBottom();
    }
  }, [toolOutput]);

  return (
    <div className="terminal-container" style={{ height: '300px', width: '100%', border: '1px solid #333', borderRadius: '4px' }}>
      <div ref={terminalRef} style={{ height: '100%', width: '100%' }} />
    </div>
  );
};

export default TerminalEmulator;
