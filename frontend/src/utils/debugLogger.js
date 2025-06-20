// Enhanced logging utility for FSDevAgent debugging
const DEBUG_ENABLED = true;
const LOG_PREFIX = '[FSDevAgent-Frontend]';

export const debugLog = {
  sse: (action, data) => {
    if (DEBUG_ENABLED) {
      console.log(`${LOG_PREFIX}[SSE] ${action}:`, data);
      // Also log to a file-like structure for persistence
      if (window.fsdevDebugLogs) {
        window.fsdevDebugLogs.push({
          timestamp: new Date().toISOString(),
          type: 'SSE',
          action,
          data: JSON.stringify(data, null, 2)
        });
      }
    }
  },
  
  websocket: (action, data) => {
    if (DEBUG_ENABLED) {
      console.log(`${LOG_PREFIX}[WebSocket] ${action}:`, data);
      if (window.fsdevDebugLogs) {
        window.fsdevDebugLogs.push({
          timestamp: new Date().toISOString(),
          type: 'WebSocket',
          action,
          data: JSON.stringify(data, null, 2)
        });
      }
    }
  },
  
  chat: (action, data) => {
    if (DEBUG_ENABLED) {
      console.log(`${LOG_PREFIX}[Chat] ${action}:`, data);
      if (window.fsdevDebugLogs) {
        window.fsdevDebugLogs.push({
          timestamp: new Date().toISOString(),
          type: 'Chat',
          action,
          data: JSON.stringify(data, null, 2)
        });
      }
    }
  },
  
  emulator: (action, data) => {
    if (DEBUG_ENABLED) {
      console.log(`${LOG_PREFIX}[Emulator] ${action}:`, data);
      if (window.fsdevDebugLogs) {
        window.fsdevDebugLogs.push({
          timestamp: new Date().toISOString(),
          type: 'Emulator',
          action,
          data: JSON.stringify(data, null, 2)
        });
      }
    }
  },
  
  error: (component, error, context) => {
    console.error(`${LOG_PREFIX}[ERROR][${component}]`, error, context);
    if (window.fsdevDebugLogs) {
      window.fsdevDebugLogs.push({
        timestamp: new Date().toISOString(),
        type: 'ERROR',
        component,
        error: error.toString(),
        context: JSON.stringify(context, null, 2)
      });
    }
  },
  
  exportLogs: () => {
    if (window.fsdevDebugLogs) {
      const logs = window.fsdevDebugLogs;
      const blob = new Blob([JSON.stringify(logs, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `fsdev-debug-logs-${new Date().toISOString()}.json`;
      a.click();
      URL.revokeObjectURL(url);
    }
  }
};

// Initialize debug logs array
if (typeof window !== 'undefined') {
  window.fsdevDebugLogs = window.fsdevDebugLogs || [];
}

