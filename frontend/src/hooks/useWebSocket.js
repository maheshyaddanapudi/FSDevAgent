import { useState, useEffect, useCallback } from 'react';

const WS_BASE_URL = process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080/ws';

const useWebSocket = (endpoint, sessionId) => {
  const [socket, setSocket] = useState(null);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState(null);
  const [messages, setMessages] = useState([]);

  // Initialize WebSocket connection
  useEffect(() => {
    // Include sessionId in the WebSocket URL if provided
    const wsUrl = sessionId 
      ? `${WS_BASE_URL}/${endpoint}?sessionId=${sessionId}`
      : `${WS_BASE_URL}/${endpoint}`;
      
    console.log(`Connecting to WebSocket: ${wsUrl}`);
    
    const ws = new WebSocket(wsUrl);
    
    ws.onopen = () => {
      console.log(`WebSocket connected: ${wsUrl}`);
      setIsConnected(true);
      setError(null);
    };
    
    ws.onclose = (event) => {
      console.log(`WebSocket disconnected: ${wsUrl}`, event);
      setIsConnected(false);
      
      // Attempt to reconnect after 3 seconds
      setTimeout(() => {
        console.log('Attempting to reconnect WebSocket...');
        setSocket(null); // This will trigger the useEffect to run again
      }, 3000);
    };
    
    ws.onerror = (event) => {
      console.error(`WebSocket error: ${wsUrl}`, event);
      setError('WebSocket connection error');
    };
    
    ws.onmessage = (event) => {
      try {
        // Enhanced error handling for WebSocket message parsing
        if (!event || !event.data) {
          console.warn('Empty WebSocket message received');
          return;
        }
        
        let data;
        try {
          data = JSON.parse(event.data);
        } catch (parseError) {
          console.error('Failed to parse WebSocket message:', parseError);
          console.log('Raw message:', event.data);
          // Try to salvage the message if it's a string
          if (typeof event.data === 'string') {
            data = { 
              toolName: 'unknown',
              timestamp: new Date().toISOString(),
              output: event.data
            };
          } else {
            return; // Can't salvage, skip this message
          }
        }
        
        console.log('WebSocket message received:', data);
        
        // Validate message structure
        if (!data) {
          console.warn('Invalid WebSocket message structure');
          return;
        }
        
        // Ensure required fields exist
        if (!data.toolName) {
          data.toolName = 'unknown';
        }
        
        if (!data.timestamp) {
          data.timestamp = new Date().toISOString();
        }
        
        // Filter messages by sessionId if provided
        if (!sessionId || data.sessionId === sessionId) {
          setMessages((prevMessages) => [...prevMessages, data]);
        }
      } catch (err) {
        console.error('Error processing WebSocket message:', err);
      }
    };
    
    setSocket(ws);
    
    // Clean up on unmount
    return () => {
      if (ws) {
        console.log(`Closing WebSocket: ${wsUrl}`);
        ws.close();
      }
    };
  }, [endpoint, sessionId]);
  
  // Send message through WebSocket
  const sendMessage = useCallback((data) => {
    if (socket && isConnected) {
      const message = typeof data === 'string' ? data : JSON.stringify(data);
      socket.send(message);
      return true;
    }
    return false;
  }, [socket, isConnected]);
  
  // Clear messages
  const clearMessages = useCallback(() => {
    setMessages([]);
  }, []);
  
  return {
    isConnected,
    error,
    messages,
    sendMessage,
    clearMessages
  };
};

export default useWebSocket;
