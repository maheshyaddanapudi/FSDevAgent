import { useEffect, useState, useRef, useCallback } from 'react';
import { useEmulatorStore } from '../store/emulatorStore';
import { debugLog } from '../utils/debugLogger';

export const useWebSocket = () => {
  const [connected, setConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState(null);
  const [connectionAttempts, setConnectionAttempts] = useState(0);
  const [isReconnecting, setIsReconnecting] = useState(false);
  const [error, setError] = useState(null);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const maxReconnectAttempts = 5;
  const baseReconnectDelay = 1000; // 1 second base delay
  
  // Get emulator store functions with error handling
  const { setWebSocket, processWebSocketMessage } = useEmulatorStore();

  // Updated WebSocket URL construction to work with proxied domains
  const getWebSocketUrl = useCallback(() => {
    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.hostname;
      
      // Check if we're using the proxied domain
      if (host.includes('manusvm.computer')) {
        // For proxied domains, use the 8080 subdomain for backend
        const baseHost = host.split('-').slice(1).join('-'); // Remove port prefix
        return `${protocol}//8080-${baseHost}/ws/tool-output`;
      } else {
        // Default behavior for local development
        const port = process.env.REACT_APP_WS_PORT || '8080';
        return `${protocol}//${host}:${port}/ws/tool-output`;
      }
    } catch (error) {
      debugLog.error('WebSocket', 'Error constructing WebSocket URL', { error: error.message });
      console.error('Error constructing WebSocket URL:', error);
      return 'ws://localhost:8080/ws/tool-output'; // Fallback URL
    }
  }, []);

  // Initialize WebSocket connection with comprehensive error handling
  const initializeWebSocket = useCallback(() => {
    try {
      // Clear any existing reconnect timeout
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }

      // Close existing socket if open
      if (socketRef.current) {
        try {
          if (socketRef.current.readyState === WebSocket.OPEN || 
              socketRef.current.readyState === WebSocket.CONNECTING) {
            socketRef.current.close();
          }
        } catch (closeError) {
          debugLog.error('WebSocket', 'Error closing existing socket', { error: closeError.message });
          console.error('Error closing existing WebSocket:', closeError);
        }
        socketRef.current = null;
      }

      const wsUrl = getWebSocketUrl();
      debugLog.websocket('Attempting WebSocket connection', { url: wsUrl, attempt: connectionAttempts + 1 });
      
      try {
        const socket = new WebSocket(wsUrl);
        socketRef.current = socket;

        socket.onopen = (event) => {
          try {
            debugLog.websocket('WebSocket connected successfully', { url: wsUrl });
            console.log('WebSocket connected:', wsUrl);
            
            setConnected(true);
            setConnectionAttempts(0);
            setIsReconnecting(false);
            setError(null);
            
            // Register socket with emulator store
            if (setWebSocket) {
              setWebSocket(socket);
            }
          } catch (error) {
            debugLog.error('WebSocket', 'Error in onopen handler', { error: error.message });
            console.error('Error in WebSocket onopen handler:', error);
          }
        };

        socket.onmessage = (event) => {
          try {
            debugLog.websocket('Received WebSocket message', { rawData: event.data });
            
            try {
              const data = JSON.parse(event.data);
              debugLog.websocket('Parsed WebSocket message', { parsedData: data });
              
              setLastMessage(data);
              
              // Process message through emulator store
              if (processWebSocketMessage) {
                processWebSocketMessage(data);
              }
            } catch (parseError) {
              debugLog.error('WebSocket', 'Error parsing WebSocket message', { 
                error: parseError.message, 
                rawData: event.data 
              });
              console.error('Error parsing WebSocket message:', parseError, event.data);
              
              // Continue processing instead of crashing
              return;
            }
          } catch (messageHandlingError) {
            debugLog.error('WebSocket', 'Error handling WebSocket message', { 
              error: messageHandlingError.message, 
              stack: messageHandlingError.stack 
            });
            console.error('Error handling WebSocket message:', messageHandlingError);
            
            // Continue processing instead of crashing
            return;
          }
        };

        socket.onerror = (error) => {
          try {
            debugLog.error('WebSocket', 'WebSocket error occurred', { error: error.message || 'Unknown error' });
            console.error('WebSocket error:', error);
            
            setError(error.message || 'WebSocket connection error');
            setConnected(false);
          } catch (errorHandlingError) {
            debugLog.error('WebSocket', 'Error in onerror handler', { error: errorHandlingError.message });
            console.error('Error in WebSocket onerror handler:', errorHandlingError);
          }
        };

        socket.onclose = (event) => {
          try {
            debugLog.websocket('WebSocket connection closed', { 
              code: event.code, 
              reason: event.reason, 
              wasClean: event.wasClean 
            });
            console.log('WebSocket closed:', event.code, event.reason);
            
            setConnected(false);
            socketRef.current = null;
            
            // Clear socket from emulator store
            if (setWebSocket) {
              setWebSocket(null);
            }
            
            // Attempt reconnection if not a clean close and we haven't exceeded max attempts
            if (!event.wasClean && connectionAttempts < maxReconnectAttempts) {
              const delay = baseReconnectDelay * Math.pow(2, connectionAttempts); // Exponential backoff
              debugLog.websocket('Scheduling reconnection', { delay, attempt: connectionAttempts + 1 });
              
              setIsReconnecting(true);
              setConnectionAttempts(prev => prev + 1);
              
              reconnectTimeoutRef.current = setTimeout(() => {
                initializeWebSocket();
              }, delay);
            } else if (connectionAttempts >= maxReconnectAttempts) {
              debugLog.error('WebSocket', 'Max reconnection attempts reached', { maxAttempts: maxReconnectAttempts });
              setError('Failed to reconnect after multiple attempts');
              setIsReconnecting(false);
            }
          } catch (closeHandlingError) {
            debugLog.error('WebSocket', 'Error in onclose handler', { error: closeHandlingError.message });
            console.error('Error in WebSocket onclose handler:', closeHandlingError);
          }
        };

      } catch (socketCreationError) {
        debugLog.error('WebSocket', 'Error creating WebSocket', { error: socketCreationError.message, url: wsUrl });
        console.error('Error creating WebSocket:', socketCreationError);
        setError(socketCreationError.message || 'Failed to create WebSocket connection');
        setConnected(false);
        throw socketCreationError;
      }

    } catch (initializationError) {
      debugLog.error('WebSocket', 'Error initializing WebSocket', { error: initializationError.message });
      console.error('Error initializing WebSocket:', initializationError);
      setError(initializationError.message || 'Failed to initialize WebSocket');
      setConnected(false);
    }
  }, [getWebSocketUrl, connectionAttempts, setWebSocket, processWebSocketMessage]);

  // Send message with error handling
  const sendMessage = useCallback((message) => {
    try {
      if (!socketRef.current || socketRef.current.readyState !== WebSocket.OPEN) {
        const errorMsg = 'WebSocket is not connected';
        debugLog.error('WebSocket', errorMsg, { readyState: socketRef.current?.readyState });
        console.error(errorMsg);
        setError(errorMsg);
        return false;
      }

      try {
        const messageStr = typeof message === 'string' ? message : JSON.stringify(message);
        debugLog.websocket('Sending WebSocket message', { message: messageStr });
        
        socketRef.current.send(messageStr);
        return true;
      } catch (sendError) {
        debugLog.error('WebSocket', 'Error sending message', { error: sendError.message, message });
        console.error('Error sending WebSocket message:', sendError);
        setError(sendError.message || 'Failed to send message');
        return false;
      }
    } catch (error) {
      debugLog.error('WebSocket', 'Error in sendMessage function', { error: error.message });
      console.error('Error in sendMessage function:', error);
      setError(error.message || 'Failed to send message');
      return false;
    }
  }, []);

  // Disconnect with error handling
  const disconnect = useCallback(() => {
    try {
      debugLog.websocket('Manually disconnecting WebSocket');
      
      // Clear reconnect timeout
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }

      // Close socket
      if (socketRef.current) {
        try {
          socketRef.current.close(1000, 'Manual disconnect');
        } catch (closeError) {
          debugLog.error('WebSocket', 'Error during manual disconnect', { error: closeError.message });
          console.error('Error during manual WebSocket disconnect:', closeError);
        }
      }

      // Reset state
      setConnected(false);
      setConnectionAttempts(0);
      setIsReconnecting(false);
      setError(null);
      socketRef.current = null;
      
      // Clear socket from emulator store
      if (setWebSocket) {
        setWebSocket(null);
      }
    } catch (error) {
      debugLog.error('WebSocket', 'Error in disconnect function', { error: error.message });
      console.error('Error in disconnect function:', error);
    }
  }, [setWebSocket]);

  // Manual reconnect with error handling
  const reconnect = useCallback(() => {
    try {
      debugLog.websocket('Manual reconnection requested');
      setConnectionAttempts(0);
      setError(null);
      disconnect();
      setTimeout(() => {
        initializeWebSocket();
      }, 1000);
    } catch (error) {
      debugLog.error('WebSocket', 'Error in reconnect function', { error: error.message });
      console.error('Error in reconnect function:', error);
    }
  }, [disconnect, initializeWebSocket]);

  // Initialize connection on mount
  useEffect(() => {
    try {
      initializeWebSocket();

      // Cleanup on unmount
      return () => {
        try {
          disconnect();
        } catch (error) {
          debugLog.error('WebSocket', 'Error during cleanup', { error: error.message });
          console.error('Error during WebSocket cleanup:', error);
        }
      };
    } catch (error) {
      debugLog.error('WebSocket', 'Error in useEffect', { error: error.message });
      console.error('Error in WebSocket useEffect:', error);
    }
  }, [initializeWebSocket, disconnect]);

  return {
    connected,
    lastMessage,
    isReconnecting,
    connectionAttempts,
    error,
    sendMessage,
    disconnect,
    reconnect
  };
};

export default useWebSocket;

