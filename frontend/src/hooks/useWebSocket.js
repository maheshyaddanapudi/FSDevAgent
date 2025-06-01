// Fixed useWebSocket.js - Issue #3: WebSocket Connection Issues Fix
import { useEffect, useState, useRef, useCallback } from 'react';
import { useEmulatorStore } from '../store/emulatorStore';

export const useWebSocket = () => {
  const [connected, setConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState(null);
  const [connectionAttempts, setConnectionAttempts] = useState(0);
  const [isReconnecting, setIsReconnecting] = useState(false);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const maxReconnectAttempts = 5;
  const baseReconnectDelay = 1000; // 1 second base delay
  
  // Get emulator store functions with error handling
  const { setWebSocket, processWebSocketMessage } = useEmulatorStore();

  // Issue #3 Fix: Proper WebSocket URL construction without double prefix
  const getWebSocketUrl = useCallback(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.hostname;
    const port = process.env.REACT_APP_WS_PORT || '8080';
    
    // Fix: Use correct WebSocket URL format without double prefix
    const wsUrl = `${protocol}//${host}:${port}/ws/tools`;
    console.log('WebSocket URL:', wsUrl);
    return wsUrl;
  }, []);

  // Initialize WebSocket connection with improved error handling
  const initializeWebSocket = useCallback(() => {
    // Clear any existing reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    // Close existing socket if open
    if (socketRef.current) {
      if (socketRef.current.readyState === WebSocket.OPEN || 
          socketRef.current.readyState === WebSocket.CONNECTING) {
        socketRef.current.close();
      }
      socketRef.current = null;
    }

    try {
      // Issue #3 Fix: Use the corrected URL without double prefix
      const wsUrl = getWebSocketUrl();
      console.log('Connecting to WebSocket:', wsUrl);
      
      const socket = new WebSocket(wsUrl);
      socketRef.current = socket;

      // Set up event handlers with improved error handling
      socket.onopen = () => {
        console.log('WebSocket connected successfully');
        setConnected(true);
        setIsReconnecting(false);
        setConnectionAttempts(0);
        
        // Update emulator store
        if (setWebSocket) {
          setWebSocket(socket);
        }
        
        // Send initial connection message
        try {
          socket.send(JSON.stringify({
            type: 'connection',
            timestamp: new Date().toISOString(),
            clientInfo: {
              userAgent: navigator.userAgent,
              url: window.location.href
            }
          }));
        } catch (sendError) {
          console.warn('Failed to send initial connection message:', sendError);
        }
      };

      socket.onmessage = (event) => {
        try {
          setLastMessage(event);
          
          // Issue #3 Fix: Improved message processing with error handling
          if (event.data && typeof event.data === 'string') {
            const data = JSON.parse(event.data);
            
            // Process message in emulator store if available
            if (processWebSocketMessage && typeof processWebSocketMessage === 'function') {
              processWebSocketMessage(data);
            }
            
            console.log('WebSocket message processed:', data.type || 'unknown type');
          }
        } catch (error) {
          console.error('Error processing WebSocket message:', error);
          console.error('Raw message data:', event.data);
        }
      };

      socket.onclose = (event) => {
        console.log('WebSocket disconnected:', event.code, event.reason);
        setConnected(false);
        
        // Update emulator store
        if (setWebSocket) {
          setWebSocket(null);
        }
        
        // Issue #3 Fix: Improved reconnection logic with exponential backoff
        if (!event.wasClean && connectionAttempts < maxReconnectAttempts) {
          const delay = baseReconnectDelay * Math.pow(2, connectionAttempts);
          console.log(`Scheduling reconnection attempt ${connectionAttempts + 1} in ${delay}ms`);
          
          setIsReconnecting(true);
          setConnectionAttempts(prev => prev + 1);
          
          reconnectTimeoutRef.current = setTimeout(() => {
            if (connectionAttempts < maxReconnectAttempts) {
              initializeWebSocket();
            } else {
              console.error('Max reconnection attempts reached. Please refresh the page.');
              setIsReconnecting(false);
            }
          }, delay);
        } else if (connectionAttempts >= maxReconnectAttempts) {
          console.error('WebSocket connection failed after maximum attempts');
          setIsReconnecting(false);
        }
      };

      socket.onerror = (error) => {
        console.error('WebSocket error:', error);
        
        // Issue #3 Fix: Better error handling for different error types
        if (socket.readyState === WebSocket.CONNECTING) {
          console.error('Failed to connect to WebSocket server. Check if the backend is running.');
        } else if (socket.readyState === WebSocket.OPEN) {
          console.error('WebSocket connection error during communication.');
        }
        
        // Don't close manually here, let onclose handle the reconnection
      };

    } catch (error) {
      console.error('Error creating WebSocket connection:', error);
      setConnected(false);
      setIsReconnecting(false);
      
      // Schedule retry for connection creation errors
      if (connectionAttempts < maxReconnectAttempts) {
        const delay = baseReconnectDelay * Math.pow(2, connectionAttempts);
        setConnectionAttempts(prev => prev + 1);
        
        reconnectTimeoutRef.current = setTimeout(() => {
          initializeWebSocket();
        }, delay);
      }
    }
  }, [getWebSocketUrl, setWebSocket, processWebSocketMessage, connectionAttempts]);

  // Initialize WebSocket on component mount
  useEffect(() => {
    initializeWebSocket();

    // Clean up on unmount
    return () => {
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, []); // Empty dependency array to run only once

  // Issue #3 Fix: Enhanced sendMessage with better error handling and validation
  const sendMessage = useCallback((message) => {
    if (!message) {
      console.warn('Cannot send empty message');
      return false;
    }

    if (!socketRef.current) {
      console.warn('WebSocket not initialized');
      return false;
    }

    if (socketRef.current.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket not connected. Current state:', socketRef.current.readyState);
      return false;
    }

    try {
      const messageToSend = typeof message === 'string' ? message : JSON.stringify(message);
      socketRef.current.send(messageToSend);
      console.log('Message sent successfully');
      return true;
    } catch (error) {
      console.error('Error sending WebSocket message:', error);
      return false;
    }
  }, []);

  // Issue #3 Fix: Manual reconnect function with reset
  const reconnect = useCallback(() => {
    console.log('Manual reconnection requested');
    setConnectionAttempts(0); // Reset attempts for manual reconnection
    setIsReconnecting(true);
    initializeWebSocket();
  }, [initializeWebSocket]);

  // Issue #3 Fix: Function to get connection status details
  const getConnectionStatus = useCallback(() => {
    if (!socketRef.current) {
      return { status: 'disconnected', readyState: null, attempts: connectionAttempts };
    }
    
    const readyStateMap = {
      [WebSocket.CONNECTING]: 'connecting',
      [WebSocket.OPEN]: 'connected',
      [WebSocket.CLOSING]: 'closing',
      [WebSocket.CLOSED]: 'disconnected'
    };
    
    return {
      status: readyStateMap[socketRef.current.readyState] || 'unknown',
      readyState: socketRef.current.readyState,
      attempts: connectionAttempts,
      isReconnecting
    };
  }, [connectionAttempts, isReconnecting]);

  // Issue #3 Fix: Disconnect function for clean shutdown
  const disconnect = useCallback(() => {
    console.log('Manual disconnection requested');
    
    // Clear reconnection timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    // Close socket
    if (socketRef.current) {
      socketRef.current.close(1000, 'Manual disconnect');
      socketRef.current = null;
    }
    
    setConnected(false);
    setIsReconnecting(false);
    setConnectionAttempts(0);
    
    // Update emulator store
    if (setWebSocket) {
      setWebSocket(null);
    }
  }, [setWebSocket]);

  return {
    connected,
    sendMessage,
    lastMessage,
    reconnect,
    disconnect,
    isReconnecting,
    connectionAttempts,
    maxReconnectAttempts,
    getConnectionStatus
  };
};
