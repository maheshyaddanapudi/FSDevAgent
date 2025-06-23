// Emulator Store for managing tool instances and state
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useEmulatorStore = create(
  persist(
    (set, get) => ({
      // Tool registration state
      registeredTools: new Map(),
      activeTools: new Set(),
      
      // Tool output state
      toolHistory: [],
      currentToolType: 'initializing',
      
      // UI state
      emulatorMinimized: false,
      selectedToolTab: 'terminal',
      
      // Actions
      registerTool: (toolId, toolType) => {
        set(state => {
          const newRegisteredTools = new Map(state.registeredTools);
          newRegisteredTools.set(toolId, {
            id: toolId,
            type: toolType,
            registeredAt: new Date().toISOString(),
            active: true
          });
          
          const newActiveTools = new Set(state.activeTools);
          newActiveTools.add(toolId);
          
          return {
            registeredTools: newRegisteredTools,
            activeTools: newActiveTools
          };
        });
      },
      
      unregisterTool: (toolId) => {
        set(state => {
          const newRegisteredTools = new Map(state.registeredTools);
          newRegisteredTools.delete(toolId);
          
          const newActiveTools = new Set(state.activeTools);
          newActiveTools.delete(toolId);
          
          return {
            registeredTools: newRegisteredTools,
            activeTools: newActiveTools
          };
        });
      },
      
      setCurrentToolType: (toolType) => {
        set({ currentToolType: toolType });
      },
      
      addToToolHistory: (toolOutput) => {
        set(state => ({
          toolHistory: [...state.toolHistory, {
            ...toolOutput,
            timestamp: toolOutput.timestamp || new Date().toISOString(),
            id: toolOutput.id || `tool-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
          }]
        }));
      },
      
      clearToolHistory: () => {
        set({ toolHistory: [] });
      },
      
      setEmulatorMinimized: (minimized) => {
        set({ emulatorMinimized: minimized });
      },
      
      setSelectedToolTab: (tab) => {
        set({ selectedToolTab: tab });
      },
      
      // Getters
      getRegisteredTools: () => {
        return Array.from(get().registeredTools.values());
      },
      
      getActiveTools: () => {
        const { registeredTools, activeTools } = get();
        return Array.from(activeTools).map(toolId => registeredTools.get(toolId)).filter(Boolean);
      },
      
      getToolHistory: (toolType = null) => {
        const { toolHistory } = get();
        if (!toolType) return toolHistory;
        return toolHistory.filter(item => item.toolType === toolType);
      }
    }),
    {
      name: 'fsdev-emulator-store',
      // Only persist UI state, not tool instances
      partialize: (state) => ({
        emulatorMinimized: state.emulatorMinimized,
        selectedToolTab: state.selectedToolTab,
        currentToolType: state.currentToolType
      })
    }
  )
);

export { useEmulatorStore };
export default useEmulatorStore;

