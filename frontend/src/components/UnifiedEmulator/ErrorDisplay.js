// frontend/src/components/UnifiedEmulator/ErrorDisplay.js
import React from 'react';
import './ErrorDisplay.css';

/**
 * Error display component for the UnifiedEmulator
 * Shows user-friendly error messages with recovery options
 */
const ErrorDisplay = ({ error, resetErrorBoundary }) => {
  const errorMessage = error?.message || 'An unexpected error occurred';
  const errorStack = error?.stack || '';
  
  return (
    <div className="error-display">
      <div className="error-icon">⚠️</div>
      <h3 className="error-title">Something went wrong</h3>
      
      <div className="error-message">
        {errorMessage}
      </div>
      
      <div className="error-actions">
        <button 
          className="error-retry-button"
          onClick={resetErrorBoundary}
        >
          Try Again
        </button>
        
        <button 
          className="error-refresh-button"
          onClick={() => window.location.reload()}
        >
          Refresh Page
        </button>
      </div>
      
      <details className="error-details">
        <summary>Technical Details</summary>
        <pre className="error-stack">{errorStack}</pre>
      </details>
    </div>
  );
};

export default ErrorDisplay;
