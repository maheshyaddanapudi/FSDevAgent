// Debug Logger Utility for FSDevAgent
// Provides structured logging for debugging emulator and chat functionality

class DebugLogger {
  constructor() {
    this.logs = [];
    this.maxLogs = 1000; // Keep last 1000 logs
    this.enabled = process.env.NODE_ENV === 'development';
    
    // Make logs available globally for error reporting
    if (typeof window !== 'undefined') {
      window.fsdevDebugLogs = this.logs;
    }
  }

  log(category, message, data = {}) {
    if (!this.enabled) return;

    const logEntry = {
      timestamp: new Date().toISOString(),
      category,
      message,
      data,
      level: 'info'
    };

    this.logs.push(logEntry);
    
    // Keep only the last maxLogs entries
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }

    // Console output with formatting
    console.log(
      `%c[${category.toUpperCase()}]%c ${message}`,
      'color: #60a5fa; font-weight: bold;',
      'color: inherit;',
      data
    );
  }

  error(category, message, data = {}) {
    const logEntry = {
      timestamp: new Date().toISOString(),
      category,
      message,
      data,
      level: 'error'
    };

    this.logs.push(logEntry);
    
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }

    console.error(
      `%c[${category.toUpperCase()} ERROR]%c ${message}`,
      'color: #ef4444; font-weight: bold;',
      'color: inherit;',
      data
    );
  }

  warn(category, message, data = {}) {
    const logEntry = {
      timestamp: new Date().toISOString(),
      category,
      message,
      data,
      level: 'warn'
    };

    this.logs.push(logEntry);
    
    if (this.logs.length > this.maxLogs) {
      this.logs.shift();
    }

    console.warn(
      `%c[${category.toUpperCase()} WARN]%c ${message}`,
      'color: #f59e0b; font-weight: bold;',
      'color: inherit;',
      data
    );
  }

  // Specific category loggers
  emulator(message, data = {}) {
    this.log('emulator', message, data);
  }

  sse(message, data = {}) {
    this.log('sse', message, data);
  }

  chat(message, data = {}) {
    this.log('chat', message, data);
  }

  ui(message, data = {}) {
    this.log('ui', message, data);
  }

  // Get logs for export
  getLogs(category = null, level = null) {
    let filteredLogs = this.logs;

    if (category) {
      filteredLogs = filteredLogs.filter(log => log.category === category);
    }

    if (level) {
      filteredLogs = filteredLogs.filter(log => log.level === level);
    }

    return filteredLogs;
  }

  // Clear logs
  clear() {
    this.logs = [];
    if (typeof window !== 'undefined') {
      window.fsdevDebugLogs = this.logs;
    }
  }

  // Export logs as JSON
  export() {
    const exportData = {
      timestamp: new Date().toISOString(),
      totalLogs: this.logs.length,
      logs: this.logs
    };

    const blob = new Blob([JSON.stringify(exportData, null, 2)], {
      type: 'application/json'
    });
    
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `fsdev-debug-logs-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

// Create singleton instance
const debugLog = new DebugLogger();

export { debugLog };
export default debugLog;

