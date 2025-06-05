/**
 * AgentOrchestrator.js
 * 
 * Core orchestration service for the AI Agent that coordinates between
 * the planning module, tool registry, context manager, and execution engine.
 * Enhances the existing architecture with a more structured agentic framework.
 */

import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

class AgentOrchestrator {
  constructor() {
    this.sessionId = null;
    this.context = [];
    this.listeners = [];
    this.isProcessing = false;
  }

  /**
   * Initialize the orchestrator with a session
   */
  async initialize() {
    try {
      const response = await axios.post(`${API_BASE_URL}/sessions`, {}, {
        timeout: 10000 // 10 second timeout
      });
      
      if (!response.data || !response.data.sessionId) {
        throw new Error('Invalid response from server: missing sessionId');
      }
      
      this.sessionId = response.data.sessionId;
      this.context = [];
      
      console.log('Agent Orchestrator initialized with session:', this.sessionId);
      this.notifyListeners('initialized', { sessionId: this.sessionId });
      
      return this.sessionId;
    } catch (error) {
      console.error('Failed to initialize agent orchestrator:', error);
      this.notifyListeners('error', { 
        error: error.message || 'Failed to initialize session',
        phase: 'initialization'
      });
      throw error;
    }
  }

  /**
   * Process a user message and generate a response with enhanced thinking and planning
   */
  async processUserMessage(message) {
    if (!this.sessionId) {
      await this.initialize();
    }
    
    if (!message || typeof message !== 'string' || message.trim() === '') {
      this.notifyListeners('error', { error: 'Message cannot be empty' });
      return null;
    }
    
    try {
      this.isProcessing = true;
      this.notifyListeners('processingStart', { message });
      
      // Add user message to context
      this.addToContext({
        role: 'user',
        content: message.trim(),
        timestamp: new Date().toISOString()
      });
      
      // Notify about thinking start
      this.notifyListeners('thinkingStart', {});
      
      // Start streaming response
      const encodedMessage = encodeURIComponent(message.trim());
      const eventSource = new EventSource(
        `${API_BASE_URL}/chat?sessionId=${this.sessionId}&message=${encodedMessage}`
      );
      
      let assistantMessage = '';
      let thinking = '';
      let toolCall = null;
      let toolExecution = null;
      let toolResult = null;
      let messageComplete = false;
      
      const cleanup = () => {
        if (eventSource.readyState !== EventSource.CLOSED) {
          eventSource.close();
        }
        this.isProcessing = false;
        this.notifyListeners('processingEnd', {});
      };
      
      eventSource.onmessage = (event) => {
        try {
          const chunk = JSON.parse(event.data);
          
          // Handle different chunk types
          if (chunk.message) {
            assistantMessage += chunk.message;
            this.notifyListeners('contentChunk', { content: chunk.message });
          }
          
          if (chunk.thinking) {
            thinking += chunk.thinking;
            this.notifyListeners('thinkingChunk', { content: chunk.thinking });
          }
          
          if (chunk.toolCall && !toolCall) {
            toolCall = chunk.toolCall;
            this.notifyListeners('toolCallStart', { 
              tool: toolCall.name, 
              parameters: toolCall.arguments 
            });
          }
          
          if (chunk.toolExecution) {
            toolExecution = chunk.toolExecution;
            this.notifyListeners('toolExecution', { execution: toolExecution });
          }
          
          if (chunk.toolResult) {
            toolResult = chunk.toolResult;
            this.notifyListeners('toolCallResult', { 
              tool: toolCall?.name, 
              parameters: toolCall?.arguments,
              result: toolResult 
            });
          }
          
          // Update context with current state
          this.updateAssistantMessageInContext({
            content: assistantMessage,
            thinking: thinking || undefined,
            toolCall: toolCall || undefined,
            toolExecution: toolExecution || undefined,
            toolResult: toolResult || undefined,
            isComplete: false
          });
        } catch (parseError) {
          console.error('Error parsing SSE message:', parseError, event.data);
        }
      };
      
      eventSource.onerror = (error) => {
        console.log('SSE connection ended or error occurred:', error);
        
        if (!messageComplete) {
          // Mark the message as complete
          this.updateAssistantMessageInContext({
            content: assistantMessage,
            thinking: thinking || undefined,
            toolCall: toolCall || undefined,
            toolExecution: toolExecution || undefined,
            toolResult: toolResult || undefined,
            isComplete: true
          });
          
          messageComplete = true;
          this.notifyListeners('messageComplete', {});
        }
        
        cleanup();
      };
      
      // Return cleanup function
      return cleanup;
      
    } catch (error) {
      console.error('Error processing message:', error);
      this.isProcessing = false;
      this.notifyListeners('error', { 
        error: error.message || 'Failed to process message',
        phase: 'processing'
      });
      return null;
    }
  }

  /**
   * Add a message to the context
   */
  addToContext(message) {
    if (!message || typeof message !== 'object') {
      console.error('Invalid message object provided to addToContext');
      return;
    }
    
    this.context.push({
      ...message,
      id: message.id || `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: message.timestamp || new Date().toISOString()
    });
    
    this.notifyListeners('contextUpdated', { context: this.context });
  }

  /**
   * Update the assistant message in the context
   */
  updateAssistantMessageInContext(messageUpdate) {
    const lastMessage = this.context[this.context.length - 1];
    
    if (lastMessage && lastMessage.role === 'assistant' && !lastMessage.isComplete) {
      // Update existing assistant message
      this.context[this.context.length - 1] = {
        ...lastMessage,
        ...messageUpdate
      };
    } else {
      // Add new assistant message
      this.context.push({
        role: 'assistant',
        ...messageUpdate,
        id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        timestamp: new Date().toISOString()
      });
    }
    
    this.notifyListeners('contextUpdated', { context: this.context });
  }

  /**
   * Execute a tool directly
   */
  async executeTool(toolName, args) {
    if (!this.sessionId) {
      await this.initialize();
    }
    
    if (!toolName || typeof toolName !== 'string') {
      this.notifyListeners('error', { error: 'Invalid tool name provided' });
      return null;
    }
    
    try {
      this.notifyListeners('toolCallStart', { 
        tool: toolName, 
        parameters: args || {} 
      });
      
      const response = await axios.post(
        `${API_BASE_URL}/tools/${toolName}?sessionId=${this.sessionId}`,
        args || {},
        { 
          timeout: 30000 // 30 second timeout for tool execution
        }
      );
      
      const result = response.data;
      
      this.notifyListeners('toolCallResult', { 
        tool: toolName, 
        parameters: args || {},
        result 
      });
      
      // Add tool execution to context
      this.addToContext({
        role: 'tool',
        toolName,
        args: args || {},
        result,
        isComplete: true
      });
      
      return result;
    } catch (error) {
      console.error(`Error executing tool ${toolName}:`, error);
      this.notifyListeners('error', { 
        error: error.message || 'Failed to execute tool',
        phase: 'tool_execution',
        tool: toolName
      });
      return null;
    }
  }

  /**
   * Get the current context
   */
  getContext() {
    return [...this.context];
  }

  /**
   * Clear the context
   */
  clearContext() {
    this.context = [];
    this.notifyListeners('contextUpdated', { context: this.context });
  }

  /**
   * Register a listener for orchestrator events
   */
  on(eventType, callback) {
    if (typeof callback !== 'function') {
      console.error('Callback must be a function');
      return;
    }
    
    this.listeners.push({ eventType, callback });
    
    // Return unsubscribe function
    return () => {
      this.listeners = this.listeners.filter(
        listener => !(listener.eventType === eventType && listener.callback === callback)
      );
    };
  }

  /**
   * Notify all listeners of an event
   */
  notifyListeners(eventType, data) {
    this.listeners
      .filter(listener => listener.eventType === eventType)
      .forEach(listener => {
        try {
          listener.callback(data);
        } catch (error) {
          console.error(`Error in listener for ${eventType}:`, error);
        }
      });
  }
}

export default new AgentOrchestrator();
