/**
 * PlanningModuleEnhancement.js
 * 
 * Enhances the existing planning tool with additional capabilities
 * for dynamic plan updates, step tracking, reflection, and hierarchical planning.
 */

import axios from 'axios';
import AgentOrchestrator from './AgentOrchestrator';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

class PlanningModuleEnhancement {
  constructor() {
    this.currentPlan = null;
    this.planHistory = [];
    this.listeners = [];
    
    // Register with AgentOrchestrator to receive events
    AgentOrchestrator.on('toolCallStart', this.handleToolCall.bind(this));
    AgentOrchestrator.on('toolCallResult', this.handleToolResult.bind(this));
  }

  /**
   * Handle tool calls to detect planning tool usage
   */
  handleToolCall(data) {
    if (data.tool === 'planning_tool') {
      console.log('Planning tool call detected:', data);
      
      // Track planning tool usage
      const operation = data.parameters?.operation;
      if (operation) {
        this.notifyListeners('planningOperationStart', { 
          operation, 
          parameters: data.parameters 
        });
      }
    }
  }

  /**
   * Handle tool results to process planning tool outputs
   */
  handleToolResult(data) {
    if (data.tool === 'planning_tool') {
      console.log('Planning tool result received:', data);
      
      // Process planning tool result
      const operation = data.parameters?.operation;
      const result = data.result;
      
      if (operation === 'create_plan' && result) {
        this.handleNewPlan(result);
      } else if (operation === 'update_task' && result) {
        this.handleTaskUpdate(result);
      } else if (operation === 'get_current_plan' && result) {
        this.handlePlanRetrieval(result);
      } else if (operation === 'add_task' && result) {
        this.handleTaskAddition(result);
      }
      
      this.notifyListeners('planningOperationComplete', { 
        operation, 
        parameters: data.parameters,
        result
      });
    }
  }

  /**
   * Handle new plan creation
   */
  handleNewPlan(planResult) {
    try {
      // Parse plan result if needed
      const plan = typeof planResult === 'string' ? JSON.parse(planResult) : planResult;
      
      // Store current plan in history
      if (this.currentPlan) {
        this.planHistory.push({
          ...this.currentPlan,
          archivedAt: new Date().toISOString()
        });
      }
      
      // Set new current plan
      this.currentPlan = {
        ...plan,
        createdAt: new Date().toISOString(),
        lastUpdatedAt: new Date().toISOString(),
        reflections: [],
        stepProgress: {}
      };
      
      // Initialize step progress tracking
      if (plan.tasks) {
        plan.tasks.forEach(task => {
          this.currentPlan.stepProgress[task.id] = {
            startTime: null,
            endTime: null,
            status: task.status,
            reflections: []
          };
        });
      }
      
      this.notifyListeners('planCreated', { plan: this.currentPlan });
    } catch (error) {
      console.error('Error handling new plan:', error);
    }
  }

  /**
   * Handle task update
   */
  handleTaskUpdate(updateResult) {
    try {
      // Parse update result if needed
      const update = typeof updateResult === 'string' ? JSON.parse(updateResult) : updateResult;
      
      if (!this.currentPlan || !update.taskId) {
        return;
      }
      
      // Update task in current plan
      if (this.currentPlan.tasks) {
        const taskIndex = this.currentPlan.tasks.findIndex(task => task.id === update.taskId);
        
        if (taskIndex !== -1) {
          // Update task
          this.currentPlan.tasks[taskIndex] = {
            ...this.currentPlan.tasks[taskIndex],
            ...update,
            lastUpdatedAt: new Date().toISOString()
          };
          
          // Update step progress
          if (update.status) {
            const now = new Date().toISOString();
            
            if (update.status === 'IN_PROGRESS' && !this.currentPlan.stepProgress[update.taskId].startTime) {
              this.currentPlan.stepProgress[update.taskId].startTime = now;
            } else if (['COMPLETED', 'SKIPPED', 'BLOCKED'].includes(update.status)) {
              this.currentPlan.stepProgress[update.taskId].endTime = now;
            }
            
            this.currentPlan.stepProgress[update.taskId].status = update.status;
          }
          
          this.currentPlan.lastUpdatedAt = new Date().toISOString();
          this.notifyListeners('taskUpdated', { 
            taskId: update.taskId, 
            update, 
            plan: this.currentPlan 
          });
        }
      }
    } catch (error) {
      console.error('Error handling task update:', error);
    }
  }

  /**
   * Handle plan retrieval
   */
  handlePlanRetrieval(planResult) {
    try {
      // Parse plan result if needed
      const plan = typeof planResult === 'string' ? JSON.parse(planResult) : planResult;
      
      // Update current plan with latest from backend
      if (plan && plan.id) {
        // Preserve local enhancements
        const stepProgress = this.currentPlan?.stepProgress || {};
        const reflections = this.currentPlan?.reflections || [];
        
        this.currentPlan = {
          ...plan,
          lastUpdatedAt: new Date().toISOString(),
          reflections,
          stepProgress
        };
        
        // Ensure all tasks have step progress entries
        if (plan.tasks) {
          plan.tasks.forEach(task => {
            if (!this.currentPlan.stepProgress[task.id]) {
              this.currentPlan.stepProgress[task.id] = {
                startTime: null,
                endTime: null,
                status: task.status,
                reflections: []
              };
            }
          });
        }
        
        this.notifyListeners('planRetrieved', { plan: this.currentPlan });
      }
    } catch (error) {
      console.error('Error handling plan retrieval:', error);
    }
  }

  /**
   * Handle task addition
   */
  handleTaskAddition(addResult) {
    try {
      // Parse add result if needed
      const result = typeof addResult === 'string' ? JSON.parse(addResult) : addResult;
      
      if (!this.currentPlan || !result.task) {
        return;
      }
      
      // Add task to current plan
      if (!this.currentPlan.tasks) {
        this.currentPlan.tasks = [];
      }
      
      this.currentPlan.tasks.push(result.task);
      
      // Initialize step progress for new task
      this.currentPlan.stepProgress[result.task.id] = {
        startTime: null,
        endTime: null,
        status: result.task.status,
        reflections: []
      };
      
      this.currentPlan.lastUpdatedAt = new Date().toISOString();
      this.notifyListeners('taskAdded', { 
        task: result.task, 
        plan: this.currentPlan 
      });
    } catch (error) {
      console.error('Error handling task addition:', error);
    }
  }

  /**
   * Add a reflection to the current plan
   */
  async addReflection(reflection, taskId = null) {
    if (!this.currentPlan) {
      console.error('No active plan for reflection');
      return null;
    }
    
    try {
      const reflectionObj = {
        id: `reflection_${Date.now()}`,
        content: reflection,
        timestamp: new Date().toISOString()
      };
      
      if (taskId) {
        // Task-specific reflection
        if (!this.currentPlan.stepProgress[taskId]) {
          console.error(`Task ${taskId} not found in current plan`);
          return null;
        }
        
        if (!this.currentPlan.stepProgress[taskId].reflections) {
          this.currentPlan.stepProgress[taskId].reflections = [];
        }
        
        this.currentPlan.stepProgress[taskId].reflections.push(reflectionObj);
        this.notifyListeners('taskReflectionAdded', { 
          taskId, 
          reflection: reflectionObj 
        });
      } else {
        // Plan-level reflection
        this.currentPlan.reflections.push(reflectionObj);
        this.notifyListeners('planReflectionAdded', { 
          reflection: reflectionObj 
        });
      }
      
      // Optionally persist reflection to backend
      // This would require backend API support
      
      return reflectionObj;
    } catch (error) {
      console.error('Error adding reflection:', error);
      return null;
    }
  }

  /**
   * Get the current plan with enhancements
   */
  getCurrentPlan() {
    return this.currentPlan;
  }

  /**
   * Get plan history
   */
  getPlanHistory() {
    return [...this.planHistory];
  }

  /**
   * Calculate plan progress
   */
  calculatePlanProgress() {
    if (!this.currentPlan || !this.currentPlan.tasks || this.currentPlan.tasks.length === 0) {
      return { percent: 0, completed: 0, total: 0 };
    }
    
    const total = this.currentPlan.tasks.length;
    const completed = this.currentPlan.tasks.filter(
      task => task.status === 'COMPLETED' || task.status === 'SKIPPED'
    ).length;
    
    return {
      percent: Math.round((completed / total) * 100),
      completed,
      total
    };
  }

  /**
   * Calculate critical path
   */
  calculateCriticalPath() {
    // This would require task dependency information
    // For now, return a simple placeholder
    return {
      criticalPath: [],
      message: "Critical path calculation requires task dependency information"
    };
  }

  /**
   * Register a listener for planning module events
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

export default new PlanningModuleEnhancement();
