/**
 * Enhanced debug logger with SSE support
 * Provides consistent logging across the application
 */
class DebugLogger {
  constructor() {
    this.enabled = process.env.NODE_ENV === 'development';
    this.logBuffer = [];
    this.maxBufferSize = 1000;
  }

  log(category, message, data = {}) {
    if (!this.enabled) return;

    const logEntry = {
      timestamp: new Date().toISOString(),
      category,
      message,
      data
    };

    // Add to buffer
    this.logBuffer.push(logEntry);
    if (this.logBuffer.length > this.maxBufferSize) {
      this.logBuffer = this.logBuffer.slice(-this.maxBufferSize);
    }

    // Store in window for error reporting
    if (typeof window !== 'undefined') {
      window.fsdevDebugLogs = this.logBuffer;
    }

    // Console output
    console.log(`[${category}] ${message}`, data);
  }

  error(category, message, data = {}) {
    const logEntry = {
      timestamp: new Date().toISOString(),
      category,
      message,
      data,
      level: 'error'
    };

    this.logBuffer.push(logEntry);
    if (this.logBuffer.length > this.maxBufferSize) {
      this.logBuffer = this.logBuffer.slice(-this.maxBufferSize);
    }

    if (typeof window !== 'undefined') {
      window.fsdevDebugLogs = this.logBuffer;
    }

    console.error(`[${category}] ${message}`, data);
  }

  // Category-specific methods
  emulator(message, data) {
    this.log('Emulator', message, data);
  }

  sse(message, data) {
    this.log('SSE', message, data);
  }

  chat(message, data) {
    this.log('Chat', message, data);
  }

  tool(message, data) {
    this.log('Tool', message, data);
  }

  exportLogs() {
    const blob = new Blob([JSON.stringify(this.logBuffer, null, 2)], {
      type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `fsdev-debug-logs-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  clearLogs() {
    this.logBuffer = [];
    if (typeof window !== 'undefined') {
      window.fsdevDebugLogs = [];
    }
  }
}

export const debugLog = new DebugLogger();

