// Update useWebSocket.js to work with the new emulator architecture
import { useEffect, useState, useRef, useCallback } from 'react';
import { useEmulatorStore } from '../store/emulatorStore';

export const useWebSocket = () => {
  const [connected, setConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState(null);
  const socketRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  
  // Get emulator store functions
  const { setWebSocket, processWebSocketMessage } = useEmulatorStore();

  // Initialize WebSocket connection
  const initializeWebSocket = useCallback(() => {
    // Clear any existing reconnect timeout
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    // Close existing socket if open
    if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
      socketRef.current.close();
    }

    // Create new WebSocket connection
    // Using hardcoded URL to avoid the double prefix issue
    const socket = new WebSocket('ws://localhost:8080/ws/tools');
    socketRef.current = socket;

    // Set up event handlers
    socket.onopen = () => {
      console.log('WebSocket connected');
      setConnected(true);
      setWebSocket(socket);
    };

    socket.onmessage = (event) => {
      setLastMessage(event);
      
      // Process message in emulator store
      try {
        const data = JSON.parse(event.data);
        processWebSocketMessage(data);
      } catch (error) {
        console.error('Error processing WebSocket message:', error);
      }
    };

    socket.onclose = () => {
      console.log('WebSocket disconnected');
      setConnected(false);
      setWebSocket(null);
      
      // Schedule reconnect
      reconnectTimeoutRef.current = setTimeout(() => {
        initializeWebSocket();
      }, 3000);
    };

    socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      socket.close();
    };
  }, [setWebSocket, processWebSocketMessage]);

  // Initialize WebSocket on component mount
  useEffect(() => {
    initializeWebSocket();

    // Clean up on unmount
    return () => {
      if (socketRef.current) {
        socketRef.current.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [initializeWebSocket]);

  // Send message through WebSocket
  const sendMessage = useCallback((message) => {
    if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
      socketRef.current.send(message);
      return true;
    }
    return false;
  }, []);

  // Manually reconnect
  const reconnect = useCallback(() => {
    initializeWebSocket();
  }, [initializeWebSocket]);

  return {
    connected,
    sendMessage,
    lastMessage,
    reconnect
  };
};
