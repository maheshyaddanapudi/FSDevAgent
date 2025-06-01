import { useState, useEffect, useCallback } from 'react';

const WS_BASE_URL = process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080/ws';

const useWebSocket = (endpoint, sessionId) => {
  const [socket, setSocket] = useState(null);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState(null);
  const [messages, setMessages] = useState([]);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  const MAX_RECONNECT_ATTEMPTS = 5;
  const RECONNECT_DELAY_BASE = 1000; // Start with 1 second delay

  // Initialize WebSocket connection
  useEffect(() => {
    // Don't attempt to connect if we've reached max reconnect attempts
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      setError(`Maximum reconnection attempts (${MAX_RECONNECT_ATTEMPTS}) reached. Please refresh the page.`);
      return;
    }

    // Modified to use a simpler URL without sessionId parameter
    // This matches the successful wscat connection format
    const wsUrl = `${WS_BASE_URL}/${endpoint}`;
      
    console.log(`Connecting to WebSocket: ${wsUrl}`);
    
    let ws;
    try {
      ws = new WebSocket(wsUrl);
      
      ws.onopen = () => {
        console.log(`WebSocket connected: ${wsUrl}`);
        setIsConnected(true);
        setError(null);
        setReconnectAttempts(0); // Reset reconnect attempts on successful connection
      };
      
      ws.onclose = (event) => {
        console.log(`WebSocket disconnected: ${wsUrl}`, event);
        setIsConnected(false);
        
        // Implement exponential backoff for reconnection
        const nextReconnectAttempt = reconnectAttempts + 1;
        setReconnectAttempts(nextReconnectAttempt);
        
        // Calculate delay with exponential backoff and some jitter
        const delay = Math.min(
          RECONNECT_DELAY_BASE * Math.pow(2, nextReconnectAttempt) + Math.random() * 1000,
          30000 // Max 30 seconds
        );
        
        // Attempt to reconnect after delay
        setTimeout(() => {
          console.log(`Attempting to reconnect WebSocket (attempt ${nextReconnectAttempt})...`);
          setSocket(null); // This will trigger the useEffect to run again
        }, delay);
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
          
          // Filter messages by sessionId if provided and if message has sessionId
          if (!sessionId || !data.sessionId || data.sessionId === sessionId) {
            setMessages((prevMessages) => [...prevMessages, data]);
          }
        } catch (err) {
          console.error('Error processing WebSocket message:', err);
        }
      };
      
      setSocket(ws);
    } catch (err) {
      console.error('Error creating WebSocket connection:', err);
      setError(`Failed to create WebSocket connection: ${err.message}`);
      
      // Attempt to reconnect after delay
      const nextReconnectAttempt = reconnectAttempts + 1;
      setReconnectAttempts(nextReconnectAttempt);
      
      const delay = Math.min(
        RECONNECT_DELAY_BASE * Math.pow(2, nextReconnectAttempt) + Math.random() * 1000,
        30000 // Max 30 seconds
      );
      
      setTimeout(() => {
        setSocket(null); // This will trigger the useEffect to run again
      }, delay);
    }
    
    // Clean up on unmount
    return () => {
      if (ws) {
        console.log(`Closing WebSocket: ${wsUrl}`);
        ws.close();
      }
    };
  }, [endpoint, sessionId, reconnectAttempts]);
  
  // Send message through WebSocket
  const sendMessage = useCallback((data) => {
    if (socket && isConnected) {
      // If sessionId is provided, include it in the message
      if (sessionId && typeof data === 'object') {
        data.sessionId = sessionId;
      }
      const message = typeof data === 'string' ? data : JSON.stringify(data);
      socket.send(message);
      return true;
    }
    return false;
  }, [socket, isConnected, sessionId]);
  
  // Clear messages
  const clearMessages = useCallback(() => {
    setMessages([]);
  }, []);
  
  return {
    isConnected,
    error,
    messages,
    sendMessage,
    clearMessages,
    reconnectAttempts
  };
};

export default useWebSocket;
