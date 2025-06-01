// frontend/src/store/emulatorStore.js
import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';

/**
 * Global state management for the unified emulator system
 * Handles tool registration, WebSocket coordination, and shared state
 */
export const useEmulatorStore = create(
  subscribeWithSelector((set, get) => ({
    // Active tools registry
    activeTools: new Map(),
    
    // WebSocket connection reference
    webSocket: null,
    
    // Tool outputs cache
    outputCache: new Map(),
    
    // Global settings
    settings: {
      autoScroll: true,
      theme: 'dark',
      fontSize: 14,
      maxOutputHistory: 1000
    },
    
    // Register a new tool instance
    registerTool: (id, type) => {
      set(state => {
        const newTools = new Map(state.activeTools);
        newTools.set(id, { 
          id, 
          type, 
          registeredAt: Date.now(),
          lastActivity: Date.now()
        });
        return { activeTools: newTools };
      });
    },
    
    // Unregister a tool instance
    unregisterTool: (id) => {
      set(state => {
        const newTools = new Map(state.activeTools);
        newTools.delete(id);
        return { activeTools: newTools };
      });
    },
    
    // Update tool activity timestamp
    updateToolActivity: (id) => {
      set(state => {
        const tool = state.activeTools.get(id);
        if (tool) {
          const newTools = new Map(state.activeTools);
          newTools.set(id, { ...tool, lastActivity: Date.now() });
          return { activeTools: newTools };
        }
        return state;
      });
    },
    
    // Set WebSocket connection
    setWebSocket: (ws) => {
      set({ webSocket: ws });
    },
    
    // Process WebSocket message
    processWebSocketMessage: (message) => {
      const { toolName, data, sessionId } = message;
      
      // Update output cache
      set(state => {
        const cacheKey = `${toolName}-${sessionId || 'global'}`;
        const existingOutputs = state.outputCache.get(cacheKey) || [];
        
        // Limit cache size
        const newOutputs = [...existingOutputs, { ...data, timestamp: Date.now() }];
        if (newOutputs.length > state.settings.maxOutputHistory) {
          newOutputs.splice(0, newOutputs.length - state.settings.maxOutputHistory);
        }
        
        const newCache = new Map(state.outputCache);
        newCache.set(cacheKey, newOutputs);
        
        return { outputCache: newCache };
      });
    },
    
    // Send command through WebSocket
    sendCommand: (toolId, command) => {
      const ws = get().webSocket;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          toolId,
          command,
          timestamp: Date.now()
        }));
      }
    },
    
    // Get cached outputs for a specific tool
    getCachedOutputs: (toolName, sessionId) => {
      const cacheKey = `${toolName}-${sessionId || 'global'}`;
      return get().outputCache.get(cacheKey) || [];
    },
    
    // Update settings
    updateSettings: (newSettings) => {
      set(state => ({
        settings: { ...state.settings, ...newSettings }
      }));
    },
    
    // Clear output cache
    clearOutputCache: (toolName = null) => {
      set(state => {
        if (toolName) {
          const newCache = new Map(state.outputCache);
          // Clear specific tool outputs
          for (const [key] of newCache) {
            if (key.startsWith(toolName)) {
              newCache.delete(key);
            }
          }
          return { outputCache: newCache };
        } else {
          // Clear all
          return { outputCache: new Map() };
        }
      });
    },
    
    // Get active tool count by type
    getToolCountByType: () => {
      const counts = {};
      get().activeTools.forEach(tool => {
        counts[tool.type] = (counts[tool.type] || 0) + 1;
      });
      return counts;
    }
  }))
);

// Selectors for common queries
export const selectActiveToolsByType = (type) => (state) => 
  Array.from(state.activeTools.values()).filter(tool => tool.type === type);

export const selectSettings = (state) => state.settings;

export const selectIsWebSocketConnected = (state) => 
  state.webSocket && state.webSocket.readyState === WebSocket.OPEN;
