// Common wrapper component to handle undefined toolId and other safety checks
// frontend/src/components/UnifiedEmulator/tools/ToolOutputWrapper.js

import React from 'react';
import { useEmulatorStore } from '../../../store/emulatorStore';

/**
 * Higher-order component that provides safety checks for tool output components
 */
export const withToolSafety = (Component) => {
  return ({ data, allOutputs, wsConnected, toolId, ...props }) => {
    // Provide default values for potentially undefined props
    const safeProps = {
      data: data || {},
      allOutputs: allOutputs || [],
      wsConnected: Boolean(wsConnected),
      toolId: toolId || `tool-${Date.now()}`, // Generate fallback ID if needed
      ...props
    };

    // Wrap in error boundary
    return (
      <ToolErrorBoundary toolName={Component.name}>
        <Component {...safeProps} />
      </ToolErrorBoundary>
    );
  };
};

/**
 * Error boundary for tool components
 */
class ToolErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error(`Error in ${this.props.toolName}:`, error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="tool-error-container">
          <div className="error-icon">⚠️</div>
          <h4>Error in {this.props.toolName}</h4>
          <p>{this.state.error?.message || 'An unexpected error occurred'}</p>
          <button 
            onClick={() => this.setState({ hasError: false, error: null })}
            className="retry-button"
          >
            Retry
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

// Updated hook for safer emulator store access
export const useSafeEmulatorStore = () => {
  try {
    const store = useEmulatorStore();
    return {
      updateToolActivity: (toolId) => {
        if (toolId && store?.updateToolActivity) {
          store.updateToolActivity(toolId);
        }
      },
      settings: store?.settings || { fontSize: 14, theme: 'dark', autoScroll: true }
    };
  } catch (error) {
    console.error('Error accessing emulator store:', error);
    return {
      updateToolActivity: () => {},
      settings: { fontSize: 14, theme: 'dark', autoScroll: true }
    };
  }
};
