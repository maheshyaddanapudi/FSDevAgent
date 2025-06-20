import React, { useState, useEffect } from 'react';
import { debugLog } from '../utils/debugLogger';

const SafeWebSocketComponent = ({ children, componentName = 'WebSocket Component' }) => {
  const [error, setError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const maxRetries = 3;

  useEffect(() => {
    // Track WebSocket connections globally for debugging
    if (!window.fsdevWebSocketConnections) {
      window.fsdevWebSocketConnections = [];
    }

    // Set up global error handler for WebSocket errors
    const originalWebSocket = window.WebSocket;
    
    if (originalWebSocket && !window.fsdevWebSocketPatched) {
      window.WebSocket = function(url, protocols) {
        debugLog.websocket('Creating WebSocket connection', { url, protocols });
        
        const ws = new originalWebSocket(url, protocols);
        
        // Track this connection
        const connectionInfo = {
          url,
          protocols,
          created: new Date().toISOString(),
          id: Math.random().toString(36).substr(2, 9)
        };
        
        window.fsdevWebSocketConnections.push(connectionInfo);
        
        // Enhanced error handling
        const originalOnError = ws.onerror;
        ws.onerror = function(event) {
          debugLog.error('WebSocket', 'WebSocket error occurred', {
            url,
            event,
            readyState: ws.readyState,
            connectionInfo
          });
          
          setError({
            type: 'websocket_error',
            message: `WebSocket connection failed for ${url}`,
            details: {
              url,
              readyState: ws.readyState,
              timestamp: new Date().toISOString(),
              connectionInfo
            }
          });
          
          if (originalOnError) {
            originalOnError.call(this, event);
          }
        };
        
        // Enhanced close handling
        const originalOnClose = ws.onclose;
        ws.onclose = function(event) {
          debugLog.websocket('WebSocket connection closed', {
            url,
            code: event.code,
            reason: event.reason,
            wasClean: event.wasClean
          });
          
          if (!event.wasClean) {
            setError({
              type: 'websocket_close_error',
              message: `WebSocket connection closed unexpectedly: ${event.reason || 'Unknown reason'}`,
              details: {
                url,
                code: event.code,
                reason: event.reason,
                wasClean: event.wasClean,
                timestamp: new Date().toISOString()
              }
            });
          }
          
          if (originalOnClose) {
            originalOnClose.call(this, event);
          }
        };
        
        return ws;
      };
      
      // Copy static properties
      Object.setPrototypeOf(window.WebSocket, originalWebSocket);
      Object.defineProperty(window.WebSocket, 'prototype', {
        value: originalWebSocket.prototype,
        writable: false
      });
      
      window.fsdevWebSocketPatched = true;
      debugLog.websocket('WebSocket patched for enhanced error handling', {});
    }

    return () => {
      // Cleanup if needed
    };
  }, []);

  const handleRetry = () => {
    if (retryCount < maxRetries) {
      debugLog.websocket('Retrying WebSocket component', { 
        componentName, 
        retryCount: retryCount + 1 
      });
      
      setError(null);
      setRetryCount(prev => prev + 1);
    }
  };

  const handleReset = () => {
    debugLog.websocket('Resetting WebSocket component', { componentName });
    setError(null);
    setRetryCount(0);
  };

  if (error) {
    return (
      <div className="websocket-error-container" style={{
        padding: '15px',
        backgroundColor: '#fff3cd',
        border: '1px solid #ffeaa7',
        borderRadius: '6px',
        margin: '10px 0'
      }}>
        <h4 style={{ color: '#856404', marginTop: 0 }}>
          ⚠️ {componentName} Error
        </h4>
        
        <div style={{ marginBottom: '10px' }}>
          <strong>Error Type:</strong> {error.type}
        </div>
        
        <div style={{ marginBottom: '10px' }}>
          <strong>Message:</strong> {error.message}
        </div>
        
        <details style={{ marginBottom: '15px' }}>
          <summary style={{ cursor: 'pointer', fontWeight: 'bold' }}>
            Error Details
          </summary>
          <pre style={{ 
            backgroundColor: '#f8f9fa', 
            padding: '10px', 
            borderRadius: '4px',
            overflow: 'auto',
            maxHeight: '200px',
            fontSize: '12px',
            marginTop: '5px'
          }}>
            {JSON.stringify(error.details, null, 2)}
          </pre>
        </details>

        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          {retryCount < maxRetries && (
            <button 
              onClick={handleRetry}
              style={{
                padding: '6px 12px',
                backgroundColor: '#007bff',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              🔄 Retry ({retryCount + 1}/{maxRetries})
            </button>
          )}
          
          <button 
            onClick={handleReset}
            style={{
              padding: '6px 12px',
              backgroundColor: '#28a745',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            🔄 Reset
          </button>
        </div>

        {retryCount >= maxRetries && (
          <div style={{ 
            marginTop: '10px', 
            padding: '8px', 
            backgroundColor: '#f8d7da', 
            borderRadius: '4px',
            color: '#721c24'
          }}>
            <strong>Max retries reached.</strong> Please check your connection and refresh the page.
          </div>
        )}
      </div>
    );
  }

  try {
    return children;
  } catch (renderError) {
    debugLog.error('SafeWebSocketComponent', 'Render error in WebSocket component', {
      componentName,
      error: renderError.message,
      stack: renderError.stack
    });

    setError({
      type: 'render_error',
      message: `Render error in ${componentName}: ${renderError.message}`,
      details: {
        componentName,
        error: renderError.message,
        stack: renderError.stack,
        timestamp: new Date().toISOString()
      }
    });

    return null;
  }
};

export default SafeWebSocketComponent;

