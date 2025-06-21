import { useState, useEffect, useCallback, useRef } from 'react';
import { debugLog } from '../utils/debugLogger';

/**
 * React hook for consuming tool events via Server-Sent Events
 */
const useToolEventStream = (sessionId) => {
  const [connected, setConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const [error, setError] = useState(null);
  const [reconnectAttempt, setReconnectAttempt] = useState(0);
  
  const eventSourceRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const eventQueueRef = useRef([]);
  
  // Configuration
  const MAX_RECONNECT_ATTEMPTS = 5;
  const RECONNECT_DELAY = 3000;
  
  // Cleanup function
  const cleanup = useCallback(() => {
    debugLog.sse('Cleaning up tool event stream', { sessionId });
    
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    
    setConnected(false);
  }, [sessionId]);
  
  // Connect to SSE endpoint
  const connect = useCallback(() => {
    if (!sessionId) {
      debugLog.sse('No session ID provided for tool event stream');
      return;
    }
    
    // Clean up any existing connection
    cleanup();
    
    try {
      const url = `/api/sessions/${sessionId}/tool-events`;
      debugLog.sse('Connecting to tool event stream', { url, sessionId });
      
      const eventSource = new EventSource(url);
      eventSourceRef.current = eventSource;
      
      // Connection opened
      eventSource.onopen = () => {
        debugLog.sse('Tool event stream connected', { sessionId });
        setConnected(true);
        setError(null);
        setReconnectAttempt(0);
      };
      
      // Handle messages
      eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Received tool event', { sessionId, type: data.type, data });
          
          // Update last event
          setLastEvent({
            ...data,
            receivedAt: new Date().toISOString()
          });
          
          // Add to event queue for buffering
          eventQueueRef.current.push(data);
          
          // Keep only last 100 events in queue
          if (eventQueueRef.current.length > 100) {
            eventQueueRef.current = eventQueueRef.current.slice(-100);
          }
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing tool event', { 
            sessionId, 
            error: err.message, 
            eventData: event.data 
          });
        }
      };
      
      // Handle specific event types
      eventSource.addEventListener('tool_execution', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Tool execution event', { sessionId, data });
          setLastEvent({ type: 'tool_execution', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing tool_execution event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('tool_result', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Tool result event', { sessionId, data });
          setLastEvent({ type: 'tool_result', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing tool_result event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('phase_transition', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Phase transition event', { sessionId, data });
          setLastEvent({ type: 'phase_transition', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing phase_transition event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('planning', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Planning event', { sessionId, data });
          setLastEvent({ type: 'planning', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing planning event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('agent_state_update', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Agent state update event', { sessionId, data });
          setLastEvent({ type: 'agent_state_update', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing agent_state_update event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('error', (event) => {
        try {
          const data = JSON.parse(event.data);
          debugLog.sse('Error event', { sessionId, data });
          setLastEvent({ type: 'error', ...data, receivedAt: new Date().toISOString() });
        } catch (err) {
          debugLog.error('useToolEventStream', 'Error parsing error event', { error: err.message });
        }
      });
      
      eventSource.addEventListener('heartbeat', (event) => {
        debugLog.sse('Heartbeat received', { sessionId });
        // Don't update lastEvent for heartbeats to avoid unnecessary re-renders
      });
      
      // Handle errors
      eventSource.onerror = (err) => {
        debugLog.error('useToolEventStream', 'Tool event stream error', { sessionId, error: err });
        setConnected(false);
        setError('Connection lost');
        
        // Attempt reconnection
        if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
          const delay = RECONNECT_DELAY * Math.pow(2, reconnectAttempt);
          debugLog.sse('Attempting reconnection', { 
            sessionId, 
            attempt: reconnectAttempt + 1, 
            delay 
          });
          
          reconnectTimeoutRef.current = setTimeout(() => {
            setReconnectAttempt(prev => prev + 1);
            connect();
          }, delay);
        } else {
          setError('Maximum reconnection attempts reached');
          cleanup();
        }
      };
      
    } catch (err) {
      debugLog.error('useToolEventStream', 'Error creating EventSource', { 
        sessionId, 
        error: err.message 
      });
      setError(err.message);
      setConnected(false);
    }
  }, [sessionId, reconnectAttempt, cleanup]);
  
  // Connect when session ID is available
  useEffect(() => {
    if (sessionId) {
      connect();
    }
    
    return cleanup;
  }, [sessionId, connect, cleanup]);
  
  // Get buffered events
  const getEventBuffer = useCallback(() => {
    return [...eventQueueRef.current];
  }, []);
  
  // Clear event buffer
  const clearEventBuffer = useCallback(() => {
    eventQueueRef.current = [];
  }, []);
  
  // Manual reconnect
  const reconnect = useCallback(() => {
    setReconnectAttempt(0);
    connect();
  }, [connect]);
  
  return {
    connected,
    lastEvent,
    error,
    reconnect,
    getEventBuffer,
    clearEventBuffer,
    isReconnecting: reconnectAttempt > 0 && reconnectAttempt < MAX_RECONNECT_ATTEMPTS
  };
};

export default useToolEventStream;

