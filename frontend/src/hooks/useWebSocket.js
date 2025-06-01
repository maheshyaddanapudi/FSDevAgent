import { useState, useEffect, useCallback } from 'react';

const WS_BASE_URL = process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080';

// Modified to be session-independent and more robust with browser compatibility
const useWebSocket = (endpoint) => {
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

    // Fix: Check if endpoint already includes 'ws/' prefix to avoid double prefixing
    const wsEndpoint = endpoint.startsWith('ws/') ? endpoint : `ws/${endpoint}`;
    
    // Fix: Use the correct endpoint without double prefixing
    const wsUrl = `${WS_BASE_URL}/${endpoint}`;
      
    console.log(`Connecting to WebSocket: ${wsUrl}`);
    
    let ws = null;
    
    // Add a small delay before creating the WebSocket to ensure any previous connections are fully closed
    const connectionTimer = setTimeout(() => {
      try {
        ws = new WebSocket(wsUrl);
        
        // Add specific protocols or headers if needed
        // ws = new WebSocket(wsUrl, ['protocol1', 'protocol2']);
        
        ws.onopen = () => {
          console.log(`WebSocket connected: ${wsUrl}`);
          setIsConnected(true);
          setError(null);
          setReconnectAttempts(0); // Reset reconnect attempts on successful connection
          
          // Send a simple ping message to keep the connection alive
          // This can help with some proxy/firewall configurations
          const keepAliveInterval = setInterval(() => {
            if (ws && ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: 'ping' }));
            } else {
              clearInterval(keepAliveInterval);
            }
          }, 30000); // Send ping every 30 seconds
        };
        
        ws.onclose = (event) => {
          console.log(`WebSocket disconnected: ${wsUrl}`, event);
          setIsConnected(false);
          
          // Only attempt to reconnect if this wasn't a normal closure
          if (event.code !== 1000) {
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
          }
        };
        
        ws.onerror = (event) => {
          console.error(`WebSocket error: ${wsUrl}`, event);
          setError('WebSocket connection error');
          // Don't close the socket here, let the onclose handler deal with reconnection
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
            
            // Accept all messages regardless of sessionId
            setMessages((prevMessages) => [...prevMessages, data]);
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
    }, 500); // 500ms delay before creating new connection
    
    // Clean up on unmount
    return () => {
      clearTimeout(connectionTimer);
      if (ws) {
        console.log(`Closing WebSocket: ${wsUrl}`);
        // Use a clean close code to prevent reconnection attempts
        ws.close(1000, "Component unmounted");
      }
    };
  }, [endpoint, reconnectAttempts]); // Dependency only on endpoint and reconnect attempts
  
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
    clearMessages,
    reconnectAttempts
  };
};

export default useWebSocket;
