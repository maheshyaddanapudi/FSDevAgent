import React from 'react';

class EmulatorErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { 
      hasError: false, 
      error: null, 
      errorInfo: null,
      errorDetails: {}
    };
  }

  static getDerivedStateFromError(error) {
    // Update state so the next render will show the fallback UI
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    // Log error details
    console.error('[EmulatorErrorBoundary] Caught error:', error);
    console.error('[EmulatorErrorBoundary] Error info:', errorInfo);
    
    // Capture additional debugging information
    const errorDetails = {
      message: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
      localStorageData: this.getLocalStorageDebugInfo()
    };

    this.setState({
      error,
      errorInfo,
      errorDetails
    });

    // Also log to our debug system if available
    if (window.fsdevDebugLogs) {
      window.fsdevDebugLogs.push({
        timestamp: new Date().toISOString(),
        type: 'EMULATOR_ERROR',
        error: error.toString(),
        details: errorDetails
      });
    }
  }

    try {
      return {
      };
    } catch (e) {
      return { error: e.message };
    }
  }

  getLocalStorageDebugInfo() {
    try {
      const chatStore = localStorage.getItem('chat-store');
      return {
        chatStoreExists: !!chatStore,
        chatStoreSize: chatStore ? chatStore.length : 0
      };
    } catch (e) {
      return { error: e.message };
    }
  }

  handleRetry = () => {
    this.setState({ 
      hasError: false, 
      error: null, 
      errorInfo: null,
      errorDetails: {}
    });
  };

  handleExportError = () => {
    const errorReport = {
      timestamp: new Date().toISOString(),
      error: this.state.error?.toString(),
      errorDetails: this.state.errorDetails,
      debugLogs: window.fsdevDebugLogs || []
    };

    const blob = new Blob([JSON.stringify(errorReport, null, 2)], { 
      type: 'application/json' 
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `emulator-error-report-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="emulator-error-container" style={{
          padding: '20px',
          backgroundColor: '#fee',
          border: '2px solid #f00',
          borderRadius: '8px',
          margin: '10px',
          fontFamily: 'monospace'
        }}>
          <h3 style={{ color: '#d00', marginTop: 0 }}>
            🚨 Emulator Error Detected
          </h3>
          
          <div style={{ marginBottom: '15px' }}>
            <strong>Error Message:</strong>
            <pre style={{ 
              backgroundColor: '#f5f5f5', 
              padding: '10px', 
              borderRadius: '4px',
              overflow: 'auto',
              maxHeight: '100px'
            }}>
              {this.state.error?.message || 'Unknown error'}
            </pre>
          </div>

          <div style={{ marginBottom: '15px' }}>
            <strong>Error Details:</strong>
            <pre style={{ 
              backgroundColor: '#f5f5f5', 
              padding: '10px', 
              borderRadius: '4px',
              overflow: 'auto',
              maxHeight: '200px',
              fontSize: '12px'
            }}>
              {JSON.stringify(this.state.errorDetails, null, 2)}
            </pre>
          </div>

          <div style={{ marginBottom: '15px' }}>
            <strong>Component Stack:</strong>
            <pre style={{ 
              backgroundColor: '#f5f5f5', 
              padding: '10px', 
              borderRadius: '4px',
              overflow: 'auto',
              maxHeight: '150px',
              fontSize: '11px'
            }}>
              {this.state.errorInfo?.componentStack}
            </pre>
          </div>

          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <button 
              onClick={this.handleRetry}
              style={{
                padding: '8px 16px',
                backgroundColor: '#007bff',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              🔄 Retry Emulator
            </button>
            
            <button 
              onClick={this.handleExportError}
              style={{
                padding: '8px 16px',
                backgroundColor: '#28a745',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              📥 Export Error Report
            </button>
            
            <button 
              onClick={() => window.location.reload()}
              style={{
                padding: '8px 16px',
                backgroundColor: '#ffc107',
                color: 'black',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              🔄 Reload Page
            </button>
          </div>

          <div style={{ 
            marginTop: '15px', 
            padding: '10px', 
            backgroundColor: '#e7f3ff', 
            borderRadius: '4px',
            fontSize: '14px'
          }}>
            <strong>Debug Info:</strong>
            <ul style={{ margin: '5px 0', paddingLeft: '20px' }}>
              <li>Timestamp: {this.state.errorDetails.timestamp}</li>
              <li>Chat Store: {JSON.stringify(this.state.errorDetails.localStorageData)}</li>
            </ul>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default EmulatorErrorBoundary;

