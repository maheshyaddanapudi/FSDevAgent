import React from 'react';

class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { 
      hasError: false, 
      error: null, 
      errorInfo: null 
    };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    console.error('[AppErrorBoundary] Application crashed:', error);
    console.error('[AppErrorBoundary] Error info:', errorInfo);
    
    this.setState({
      error,
      errorInfo
    });

    // Log to our debug system
    if (window.fsdevDebugLogs) {
      window.fsdevDebugLogs.push({
        timestamp: new Date().toISOString(),
        type: 'APP_CRASH',
        error: error.toString(),
        stack: error.stack,
        componentStack: errorInfo.componentStack
      });
    }
  }

  handleReload = () => {
    window.location.reload();
  };

  handleExportLogs = () => {
    const logs = window.fsdevDebugLogs || [];
    const errorReport = {
      timestamp: new Date().toISOString(),
      error: this.state.error?.toString(),
      errorInfo: this.state.errorInfo,
      debugLogs: logs,
      userAgent: navigator.userAgent,
      url: window.location.href
    };

    const blob = new Blob([JSON.stringify(errorReport, null, 2)], { 
      type: 'application/json' 
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `app-crash-report-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          padding: '40px',
          textAlign: 'center',
          backgroundColor: '#f8f9fa',
          minHeight: '100vh',
          fontFamily: 'Arial, sans-serif'
        }}>
          <div style={{
            maxWidth: '600px',
            margin: '0 auto',
            backgroundColor: 'white',
            padding: '30px',
            borderRadius: '8px',
            boxShadow: '0 2px 10px rgba(0,0,0,0.1)'
          }}>
            <h1 style={{ color: '#dc3545', marginBottom: '20px' }}>
              🚨 Application Error
            </h1>
            
            <p style={{ fontSize: '18px', marginBottom: '20px', color: '#6c757d' }}>
              The FSDevAgent application encountered an error and needs to be reloaded.
            </p>

            <div style={{ 
              backgroundColor: '#f8f9fa', 
              padding: '15px', 
              borderRadius: '4px',
              marginBottom: '20px',
              textAlign: 'left'
            }}>
              <strong>Error Details:</strong>
              <pre style={{ 
                fontSize: '12px', 
                overflow: 'auto',
                maxHeight: '150px',
                margin: '10px 0 0 0'
              }}>
                {this.state.error?.message || 'Unknown error'}
              </pre>
            </div>

            <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
              <button 
                onClick={this.handleReload}
                style={{
                  padding: '12px 24px',
                  backgroundColor: '#007bff',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '16px'
                }}
              >
                🔄 Reload Application
              </button>
              
              <button 
                onClick={this.handleExportLogs}
                style={{
                  padding: '12px 24px',
                  backgroundColor: '#28a745',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '16px'
                }}
              >
                📥 Export Debug Logs
              </button>
            </div>

            <div style={{ 
              marginTop: '20px', 
              fontSize: '14px', 
              color: '#6c757d' 
            }}>
              If this error persists, please export the debug logs and report the issue.
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default AppErrorBoundary;

