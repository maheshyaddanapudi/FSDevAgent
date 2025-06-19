import { create } from 'zustand';

// Emulator store for managing terminal and tool output state
export const useEmulatorStore = create((set, get) => ({
  // Settings
  settings: {
    theme: 'dark',
    fontSize: 14,
    fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
    cursorBlink: true,
    scrollback: 1000,
    allowTransparency: false,
  },
  
  // Registered tools
  registeredTools: new Map(),
  
  // Active tool outputs
  toolOutputs: [],
  
  // Terminal state
  terminalReady: false,
  terminalError: null,
  
  // Actions
  updateSettings: (newSettings) => set((state) => ({
    settings: { ...state.settings, ...newSettings }
  })),
  
  registerTool: (toolType, component) => set((state) => {
    const newRegisteredTools = new Map(state.registeredTools);
    newRegisteredTools.set(toolType, component);
    return { registeredTools: newRegisteredTools };
  }),
  
  unregisterTool: (toolType) => set((state) => {
    const newRegisteredTools = new Map(state.registeredTools);
    newRegisteredTools.delete(toolType);
    return { registeredTools: newRegisteredTools };
  }),
  
  addToolOutput: (output) => set((state) => ({
    toolOutputs: [...state.toolOutputs, output]
  })),
  
  clearToolOutputs: () => set({ toolOutputs: [] }),
  
  setTerminalReady: (ready) => set({ terminalReady: ready }),
  
  setTerminalError: (error) => set({ terminalError: error }),
}));

// Selector for settings
export const selectSettings = (state) => state.settings;

// Selector for registered tools
export const selectRegisteredTools = (state) => state.registeredTools;

// Selector for tool outputs
export const selectToolOutputs = (state) => state.toolOutputs;

// Selector for terminal state
export const selectTerminalState = (state) => ({
  ready: state.terminalReady,
  error: state.terminalError,
});

export default useEmulatorStore;

