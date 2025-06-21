/**
 * StreamingService.js
 * 
 * including planning and tool calls. It provides a unified interface for
 * consuming real-time updates from the autonomous agent.
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { useEmulatorStore } from '../store/emulatorStore';

/**
 * Custom hook for consuming SSE streams from the backend
 * @param {string} endpoint - The SSE endpoint to connect to
 * @param {Object} options - Configuration options
 * @returns {Object} SSE connection state and data
 */
export const useSSEStream = (endpoint, options = {}) => {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('disconnected');
  const [error, setError] = useState(null);
  const eventSourceRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const [connectionAttempts, setConnectionAttempts] = useState(0);
  
  const {
    onMessage,
    onOpen,
    onError,
    eventTypes = ['message'],
    reconnectDelay = 1000,
    maxReconnectAttempts = 5,
    autoReconnect = true
  } = options;

  // Construct the SSE URL based on environment
  const getSSEUrl = useCallback(() => {
    const protocol = window.location.protocol;
    const host = window.location.hostname;
    
    // Check if we're using the proxied domain
    if (host.includes('manusvm.computer')) {
      // For proxied domains, use the 8080 subdomain for backend
      const baseHost = host.split('-').slice(1).join('-'); // Remove port prefix
      return `${protocol}//8080-${baseHost}/${endpoint}`;
    } else {
      // Default behavior for local development
      const port = process.env.REACT_APP_API_PORT || '8080';
      return `${protocol}//${host}:${port}/${endpoint}`;
    }
  }, [endpoint]);

  // Initialize SSE connection
  const initializeSSE = useCallback(() => {
    // Clear any existing reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    // Close existing EventSource if open
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }

    try {
      const sseUrl = getSSEUrl();
      console.log('Connecting to SSE:', sseUrl);
      
      const eventSource = new EventSource(sseUrl);
      eventSourceRef.current = eventSource;

      // Set up connection event handler
      eventSource.onopen = (event) => {
        console.log('SSE connected successfully');
        setStatus('connected');
        setConnectionAttempts(0);
        
        if (onOpen && typeof onOpen === 'function') {
          onOpen(event);
        }
      };

      // Set up error event handler
      eventSource.onerror = (event) => {
        console.error('SSE connection error:', event);
        setStatus('error');
        setError(event);
        
        if (onError && typeof onError === 'function') {
          onError(event);
        }
        
        // Handle reconnection
        eventSource.close();
        
        if (autoReconnect && connectionAttempts < maxReconnectAttempts) {
          const delay = reconnectDelay * Math.pow(2, connectionAttempts);
          console.log(`Scheduling SSE reconnection attempt ${connectionAttempts + 1} in ${delay}ms`);
          
          setStatus('reconnecting');
          setConnectionAttempts(prev => prev + 1);
          
          reconnectTimeoutRef.current = setTimeout(() => {
            initializeSSE();
          }, delay);
        } else if (connectionAttempts >= maxReconnectAttempts) {
          console.error('SSE connection failed after maximum attempts');
          setStatus('failed');
        }
      };

      // Set up event listeners for specified event types
      eventTypes.forEach(eventType => {
        eventSource.addEventListener(eventType, (event) => {
          try {
            const parsedData = event.data ? JSON.parse(event.data) : null;
            setData(parsedData);
            
            if (onMessage && typeof onMessage === 'function') {
              onMessage(parsedData, eventType);
            }
          } catch (error) {
            console.error(`Error parsing SSE ${eventType} event data:`, error);
            console.error('Raw event data:', event.data);
          }
        });
      });

    } catch (error) {
      console.error('Error creating SSE connection:', error);
      setStatus('error');
      setError(error);
      
      // Schedule retry for connection creation errors
      if (autoReconnect && connectionAttempts < maxReconnectAttempts) {
        const delay = reconnectDelay * Math.pow(2, connectionAttempts);
        setConnectionAttempts(prev => prev + 1);
        
        reconnectTimeoutRef.current = setTimeout(() => {
          initializeSSE();
        }, delay);
      }
    }
  }, [
    getSSEUrl, 
    onMessage, 
    onOpen, 
    onError, 
    eventTypes, 
    reconnectDelay, 
    maxReconnectAttempts, 
    autoReconnect, 
    connectionAttempts
  ]);

  // Initialize SSE on component mount
  useEffect(() => {
    initializeSSE();

    // Clean up on unmount
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, [initializeSSE]);

  // Manual reconnect function
  const reconnect = useCallback(() => {
    console.log('Manual SSE reconnection requested');
    setConnectionAttempts(0); // Reset attempts for manual reconnection
    setStatus('reconnecting');
    initializeSSE();
  }, [initializeSSE]);

  // Manual disconnect function
  const disconnect = useCallback(() => {
    console.log('Manual SSE disconnection requested');
    
    // Clear reconnection timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    // Close EventSource
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    
    setStatus('disconnected');
    setConnectionAttempts(0);
  }, []);

  return {
    data,
    status,
    error,
    reconnect,
    disconnect,
    connectionAttempts,
    maxReconnectAttempts
  };
};

/**
 * for handling planning and tool call events
 */
  const [planningData, setPlanningData] = useState(null);
  const [toolCallData, setToolCallData] = useState(null);
  const [agentStateData, setAgentStateData] = useState(null);
  const [phaseData, setPhaseData] = useState(null);
  const [errorData, setErrorData] = useState(null);
  
  // Get emulator store functions
  
  // Custom message handler for different event types
  const handleMessage = useCallback((data) => {
    if (!data || !data.type) return;
    
    // Process message based on type
    switch (data.type) {
      case 'planning':
        setPlanningData(data);
        break;
      case 'tool_call':
        setToolCallData(data);
        break;
      case 'agent_state':
        setAgentStateData(data);
        break;
      case 'phase_transition':
        setPhaseData(data);
        break;
      case 'error':
        setErrorData(data);
        break;
      default:
        // For other types, just pass to the emulator store
        }
    }
  
  // This is a placeholder - in the actual implementation, you would import and use
  const {
    connected,
    sendMessage,
    lastMessage,
    reconnect,
    disconnect,
    isReconnecting,
    connectionAttempts,
    maxReconnectAttempts,
    getConnectionStatus
  
  // Send a command to the agent
  const sendAgentCommand = useCallback((command, args = {}) => {
    if (!connected) {
      return false;
    }
    
    const message = {
      type: 'agent_command',
      command,
      args,
      requestId: `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    };
    
    return sendMessage(message);
  }, [connected, sendMessage]);
  
  // Pause agent execution
  const pauseExecution = useCallback(() => {
    return sendAgentCommand('pause');
  }, [sendAgentCommand]);
  
  // Resume agent execution
  const resumeExecution = useCallback(() => {
    return sendAgentCommand('resume');
  }, [sendAgentCommand]);
  
  // Step through agent execution
  const stepExecution = useCallback(() => {
    return sendAgentCommand('step');
  }, [sendAgentCommand]);
  
  // Stop agent execution
  const stopExecution = useCallback(() => {
    return sendAgentCommand('stop');
  }, [sendAgentCommand]);
  
  // Skip current step
  const skipStep = useCallback(() => {
    return sendAgentCommand('skipStep');
  }, [sendAgentCommand]);
  
  // Jump to a specific phase
  const jumpToPhase = useCallback((phase) => {
    return sendAgentCommand('jumpToPhase', { phase });
  }, [sendAgentCommand]);
  
  return {
    // Connection state
    connected,
    reconnect,
    disconnect,
    isReconnecting,
    connectionAttempts,
    maxReconnectAttempts,
    getConnectionStatus,
    
    // Data streams
    planningData,
    toolCallData,
    agentStateData,
    phaseData,
    errorData,
    lastMessage,
    
    // Agent control commands
    sendAgentCommand,
    pauseExecution,
    resumeExecution,
    stepExecution,
    stopExecution,
    skipStep,
    jumpToPhase
  };
};

// This would be replaced with the actual import in the implementation
  // This is just a placeholder to make the code compile
  // In the actual implementation, you would import and use the existing hook
  return {
    connected: false,
    sendMessage: () => false,
    lastMessage: null,
    reconnect: () => {},
    disconnect: () => {},
    isReconnecting: false,
    connectionAttempts: 0,
    maxReconnectAttempts: 5,
    getConnectionStatus: () => ({ status: 'disconnected' })
  };
}
