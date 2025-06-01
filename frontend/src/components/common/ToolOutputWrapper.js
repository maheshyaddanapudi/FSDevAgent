// ToolOutputWrapper.js - Issue #6: Error Handling Improvements
import React, { Component, createContext, useContext, useState, useEffect, useRef } from 'react';
import './ToolOutputWrapper.css';

// Context for error reporting
const ErrorReportingContext = createContext({
  reportError: () => {},
  clearError: () => {},
  errors: []
});

// Global error reporter hook
export const useErrorReporting = () => {
  const context = useContext(ErrorReportingContext);
  if (!context) {
    console.warn('useErrorReporting used outside of ErrorReportingProvider');
    return {
      reportError: console.error,
      clearError: () => {},
      errors: []
    };
  }
  return context;
};

// Error Reporting Provider Component
export const ErrorReportingProvider = ({ children }) => {
  const [errors, setErrors] = useState([]);

  const reportError = (error, component, context = {}) => {
    const errorEntry = {
      id: Date.now() + Math.random(),
      error,
      component,
      context,
      timestamp: new Date().toISOString(),
      stack: error?.stack || new Error().stack
    };

    setErrors(prev => [...prev.slice(-9), errorEntry]); // Keep last 10 errors
    
    // Log to console for debugging
    console.error(`[${component}] Error:`, error, context);
  };

  const clearError = (errorId) => {
    setErrors(prev => prev.filter(e => e.id !== errorId));
  };

  const clearAllErrors = () => {
    setErrors([]);
  };

  return (
    <ErrorReportingContext.Provider value={{ reportError, clearError, clearAllErrors, errors }}>
      {children}
    </ErrorReportingContext.Provider>
  );
};

// Issue #6 Fix: Enhanced Error Boundary with comprehensive error handling
class ToolErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { 
      hasError: false, 
      error: null, 
      errorInfo: null,
      retryCount: 0
    };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    const { toolName, onError } = this.props;
    
    console.error(`Error in ${toolName}:`, error, errorInfo);
    
    this.setState({ errorInfo });
    
    // Report to parent if callback provided
    if (onError) {
      onError(error, errorInfo, toolName);
    }
    
    // Report to global error tracking if available
    if (window.reportError) {
      window.reportError(error, toolName, errorInfo);
    }
  }

  handleRetry = () => {
    this.setState(prevState => ({ 
      hasError: false, 
      error: null, 
      errorInfo: null,
      retryCount: prevState.retryCount + 1
    }));
  };

  handleReport = () => {
    const { error, errorInfo } = this.state;
    const { toolName } = this.props;
    
    const reportData = {
      component: toolName,
      error: {
        message: error?.message,
        stack: error?.stack,
        name: error?.name
      },
      errorInfo,
      userAgent: navigator.userAgent,
      timestamp: new Date().toISOString(),
      url: window.location.href
    };
    
    // Copy to clipboard for easy reporting
    navigator.clipboard?.writeText(JSON.stringify(reportData, null, 2))
      .then(() => alert('Error details copied to clipboard'))
      .catch(() => console.log('Error details:', reportData));
  };

  render() {
    if (this.state.hasError) {
      const { toolName, fallback } = this.props;
      const { error, retryCount } = this.state;
      
      // Use custom fallback if provided
      if (fallback) {
        return fallback(error, this.handleRetry, toolName);
      }
      
      return (
        <div className="tool-error-container">
          <div className="tool-error-header">
            <div className="error-icon">⚠️</div>
            <h3>Error in {toolName}</h3>
          </div>
          
          <div className="tool-error-content">
            <div className="error-message">
              {error?.message || 'An unexpected error occurred'}
            </div>
            
            {retryCount > 0 && (
              <div className="error-retry-info">
                Retry attempts: {retryCount}
              </div>
            )}
            
            <div className="error-actions">
              <button 
                onClick={this.handleRetry}
                className="error-retry-button"
              >
                Try Again
              </button>
              
              <button 
                onClick={this.handleReport}
                className="error-report-button"
              >
                Report Issue
              </button>
            </div>
            
            {process.env.NODE_ENV === 'development' && (
              <details className="error-details">
                <summary>Technical Details</summary>
                <pre className="error-stack">
                  {error?.stack || 'No stack trace available'}
                </pre>
              </details>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

// Issue #6 Fix: Safe component wrapper with validation and error handling
export const withToolSafety = (Component, options = {}) => {
  const {
    defaultProps = {},
    validateProps = () => true,
    errorFallback = null,
    onError = null
  } = options;

  const SafeComponent = ({ toolId, ...props }) => {
    const [componentError, setComponentError] = useState(null);
    const { reportError } = useErrorReporting();
    
    // Issue #6 Fix: Provide safe default values for potentially undefined props
    const safeProps = {
      data: props.data || {},
      allOutputs: props.allOutputs || [],
      wsConnected: Boolean(props.wsConnected),
      toolId: toolId || `tool-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      ...defaultProps,
      ...props
    };

    // Validate props
    useEffect(() => {
      try {
        if (!validateProps(safeProps)) {
          const error = new Error('Invalid props provided to component');
          setComponentError(error);
          reportError(error, Component.name, { props: safeProps });
        }
      } catch (validationError) {
        setComponentError(validationError);
        reportError(validationError, Component.name, { props: safeProps });
      }
    }, [safeProps, reportError]);

    // Clear error when props change significantly
    useEffect(() => {
      if (componentError && props.data !== safeProps.data) {
        setComponentError(null);
      }
    }, [props.data, componentError, safeProps.data]);

    if (componentError) {
      return (
        <div className="component-error">
          <div className="error-icon">❌</div>
          <h4>Component Error</h4>
          <p>{componentError.message}</p>
          <button onClick={() => setComponentError(null)}>
            Reset
          </button>
        </div>
      );
    }

    return (
      <ToolErrorBoundary 
        toolName={Component.displayName || Component.name}
        fallback={errorFallback}
        onError={(error, errorInfo, toolName) => {
          if (onError) onError(error, errorInfo, toolName);
          reportError(error, toolName, { props: safeProps, errorInfo });
        }}
      >
        <Component {...safeProps} />
      </ToolErrorBoundary>
    );
  };

  SafeComponent.displayName = `withToolSafety(${Component.displayName || Component.name})`;
  return SafeComponent;
};

// Issue #6 Fix: Safe store access hook with fallbacks
export const useSafeEmulatorStore = () => {
  const [storeError, setStoreError] = useState(null);
  
  try {
    // Dynamic import to handle missing store
    const useEmulatorStore = require('../../store/emulatorStore').useEmulatorStore;
    const store = useEmulatorStore();
    
    return {
      updateToolActivity: (toolId) => {
        try {
          if (toolId && store?.updateToolActivity) {
            store.updateToolActivity(toolId);
          }
        } catch (error) {
          console.warn('Error updating tool activity:', error);
          setStoreError(error);
        }
      },
      settings: store?.settings || { 
        fontSize: 14, 
        theme: 'dark', 
        autoScroll: true,
        maxOutputHistory: 1000
      },
      storeError
    };
  } catch (error) {
    console.error('Error accessing emulator store:', error);
    setStoreError(error);
    
    return {
      updateToolActivity: () => {},
      settings: { 
        fontSize: 14, 
        theme: 'dark', 
        autoScroll: true,
        maxOutputHistory: 1000
      },
      storeError
    };
  }
};

// Issue #6 Fix: Generic error fallback component
export const GenericErrorFallback = ({ error, retry, componentName }) => (
  <div className="generic-error-fallback">
    <div className="error-illustration">
      <div className="broken-robot">🤖💥</div>
    </div>
    <h3>Oops! Something went wrong</h3>
    <p>The {componentName} component encountered an unexpected error.</p>
    <div className="error-message-box">
      {error?.message || 'Unknown error occurred'}
    </div>
    <div className="error-actions">
      <button onClick={retry} className="primary-button">
        Try Again
      </button>
      <button 
        onClick={() => window.location.reload()} 
        className="secondary-button"
      >
        Refresh Page
      </button>
    </div>
  </div>
);

// Issue #6 Fix: Performance monitoring hook
export const usePerformanceMonitor = (componentName) => {
  const [performanceData, setPerformanceData] = useState({
    renderCount: 0,
    lastRenderTime: null,
    averageRenderTime: 0
  });

  useEffect(() => {
    const startTime = performance.now();
    
    return () => {
      const endTime = performance.now();
      const renderTime = endTime - startTime;
      
      setPerformanceData(prev => {
        const newRenderCount = prev.renderCount + 1;
        const newAverageRenderTime = (prev.averageRenderTime * prev.renderCount + renderTime) / newRenderCount;
        
        return {
          renderCount: newRenderCount,
          lastRenderTime: renderTime,
          averageRenderTime: newAverageRenderTime
        };
      });
      
      // Log slow renders in development
      if (process.env.NODE_ENV === 'development' && renderTime > 100) {
        console.warn(`Slow render in ${componentName}: ${renderTime.toFixed(2)}ms`);
      }
    };
  });

  return performanceData;
};

// Issue #6 Fix: Cleanup manager for preventing memory leaks
export const useCleanupManager = () => {
  const cleanupFunctions = useRef([]);
  
  const addCleanup = (fn) => {
    if (typeof fn === 'function') {
      cleanupFunctions.current.push(fn);
    }
  };
  
  const runCleanup = () => {
    cleanupFunctions.current.forEach(fn => {
      try {
        fn();
      } catch (error) {
        console.error('Error during cleanup:', error);
      }
    });
    cleanupFunctions.current = [];
  };
  
  useEffect(() => {
    return runCleanup;
  }, []);
  
  return { addCleanup, runCleanup };
};

// Export default error boundary
export default ToolErrorBoundary;
