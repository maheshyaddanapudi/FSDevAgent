/**
 * ToolActivityTimeline.js
 * 
 * This component displays a timeline of agent activities including tool calls,
 * planning steps, and phase transitions in a visually rich format.
 */

import React, { useState, useEffect, useRef } from 'react';
import '../styles/ToolActivityTimeline.css';

// Tool type icons mapping
const TOOL_ICONS = {
  file_system: '📄',
  execute_command: '💻',
  browser_automation: '🌐',
  git_operations: '🔄',
  build_tool: '🔨',
  planning_tool: '🧠',
  data_visualization: '📊',
  code_intelligence: '🔍',
  default: '🛠️'
};

// Tool operation icons mapping
const OPERATION_ICONS = {
  read: '👁️',
  write: '✏️',
  create: '➕',
  delete: '❌',
  execute: '▶️',
  build: '🏗️',
  analyze: '🔬',
  search: '🔎',
  default: '⚙️'
};

const ToolActivityTimeline = ({ className = '' }) => {
  const [activities, setActivities] = useState([]);
  const [expandedActivity, setExpandedActivity] = useState(null);
  const [filter, setFilter] = useState('all');
  const timelineRef = useRef(null);
  const autoScrollRef = useRef(true);
  
  const {
    connected,
    planningData,
    toolCallData,
    agentStateData,
    phaseData,
    errorData

  // Process new tool call data
  useEffect(() => {
    if (!toolCallData) return;
    
    const newActivity = {
      id: `tool-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type: 'tool_call',
      timestamp: new Date(),
      toolName: toolCallData.toolName || toolCallData.type || 'unknown',
      operation: toolCallData.args?.operation || toolCallData.args?.action || 'default',
      args: toolCallData.args || {},
      result: toolCallData.result || toolCallData.output || null,
      status: toolCallData.status || 'unknown'
    };
    
    setActivities(prev => [...prev, newActivity]);
  }, [toolCallData]);

  // Process new planning data
  useEffect(() => {
    if (!planningData) return;
    
    const newActivity = {
      id: `plan-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type: 'planning',
      timestamp: new Date(),
      planStep: planningData.step || planningData.currentStep || 0,
      planTotal: planningData.totalSteps || planningData.total || 0,
      description: planningData.description || planningData.message || '',
      details: planningData.details || {}
    };
    
    setActivities(prev => [...prev, newActivity]);
  }, [planningData]);

  // Process new phase transition data
  useEffect(() => {
    if (!phaseData) return;
    
    const newActivity = {
      id: `phase-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type: 'phase_transition',
      timestamp: new Date(),
      fromPhase: phaseData.fromPhase || null,
      toPhase: phaseData.currentPhase || phaseData.toPhase || null,
      progress: phaseData.progress || 0
    };
    
    setActivities(prev => [...prev, newActivity]);
  }, [phaseData]);

  // Process new error data
  useEffect(() => {
    if (!errorData) return;
    
    const newActivity = {
      id: `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type: 'error',
      timestamp: new Date(),
      message: errorData.message || 'Unknown error',
      details: errorData.details || {},
      severity: errorData.severity || 'error'
    };
    
    setActivities(prev => [...prev, newActivity]);
  }, [errorData]);

  // Auto-scroll to bottom when new activities arrive
  useEffect(() => {
    if (autoScrollRef.current && timelineRef.current && activities.length > 0) {
      timelineRef.current.scrollTop = timelineRef.current.scrollHeight;
    }
  }, [activities]);

  // Handle activity click to expand/collapse
  const handleActivityClick = (activityId) => {
    setExpandedActivity(expandedActivity === activityId ? null : activityId);
  };

  // Handle filter change
  const handleFilterChange = (newFilter) => {
    setFilter(newFilter);
  };

  // Handle auto-scroll toggle
  const handleAutoScrollToggle = () => {
    autoScrollRef.current = !autoScrollRef.current;
  };

  // Filter activities based on selected filter
  const filteredActivities = activities.filter(activity => {
    if (filter === 'all') return true;
    return activity.type === filter;
  });

  // Format timestamp
  const formatTimestamp = (timestamp) => {
    if (!timestamp) return '';
    
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  // Get tool icon
  const getToolIcon = (toolName) => {
    return TOOL_ICONS[toolName] || TOOL_ICONS.default;
  };

  // Get operation icon
  const getOperationIcon = (operation) => {
    return OPERATION_ICONS[operation] || OPERATION_ICONS.default;
  };

  // Get activity class based on type
  const getActivityClass = (activity) => {
    switch (activity.type) {
      case 'tool_call':
        return `activity-item tool-call ${activity.status}`;
      case 'planning':
        return 'activity-item planning';
      case 'phase_transition':
        return 'activity-item phase-transition';
      case 'error':
        return `activity-item error ${activity.severity}`;
      default:
        return 'activity-item';
    }
  };

  // Render tool call activity
  const renderToolCallActivity = (activity) => {
    const isExpanded = expandedActivity === activity.id;
    const toolIcon = getToolIcon(activity.toolName);
    const operationIcon = getOperationIcon(activity.operation);
    
    return (
      <>
        <div className="activity-header" onClick={() => handleActivityClick(activity.id)}>
          <div className="activity-icon tool-icon">{toolIcon}</div>
          <div className="activity-icon operation-icon">{operationIcon}</div>
          <div className="activity-title">
            <span className="tool-name">{activity.toolName}</span>
            {activity.operation !== 'default' && (
              <span className="operation-name">{activity.operation}</span>
            )}
          </div>
          <div className="activity-timestamp">{formatTimestamp(activity.timestamp)}</div>
          <div className={`activity-status ${activity.status}`}></div>
        </div>
        
        {isExpanded && (
          <div className="activity-details">
            <div className="activity-args">
              <h4>Arguments</h4>
              <pre>{JSON.stringify(activity.args, null, 2)}</pre>
            </div>
            
            {activity.result && (
              <div className="activity-result">
                <h4>Result</h4>
                <pre>{typeof activity.result === 'string' 
                  ? activity.result 
                  : JSON.stringify(activity.result, null, 2)}</pre>
              </div>
            )}
          </div>
        )}
      </>
    );
  };

  // Render planning activity
  const renderPlanningActivity = (activity) => {
    const isExpanded = expandedActivity === activity.id;
    
    return (
      <>
        <div className="activity-header" onClick={() => handleActivityClick(activity.id)}>
          <div className="activity-icon planning-icon">🧠</div>
          <div className="activity-title">
            <span className="planning-step">
              Step {activity.planStep}{activity.planTotal ? `/${activity.planTotal}` : ''}
            </span>
            <span className="planning-description">{activity.description}</span>
          </div>
          <div className="activity-timestamp">{formatTimestamp(activity.timestamp)}</div>
        </div>
        
        {isExpanded && activity.details && (
          <div className="activity-details">
            <pre>{JSON.stringify(activity.details, null, 2)}</pre>
          </div>
        )}
      </>
    );
  };

  // Render phase transition activity
  const renderPhaseTransitionActivity = (activity) => {
    return (
      <div className="activity-header phase-header">
        <div className="activity-icon phase-icon">📍</div>
        <div className="activity-title">
          <span className="phase-transition">
            {activity.fromPhase 
              ? `Phase transition: ${activity.fromPhase} → ${activity.toPhase}` 
              : `Entered phase: ${activity.toPhase}`}
          </span>
          {activity.progress > 0 && (
            <div className="phase-progress">
              <div className="progress-bar">
                <div 
                  className="progress-fill" 
                  style={{ width: `${activity.progress}%` }}
                ></div>
              </div>
              <span className="progress-text">{activity.progress}%</span>
            </div>
          )}
        </div>
        <div className="activity-timestamp">{formatTimestamp(activity.timestamp)}</div>
      </div>
    );
  };

  // Render error activity
  const renderErrorActivity = (activity) => {
    const isExpanded = expandedActivity === activity.id;
    
    return (
      <>
        <div className="activity-header" onClick={() => handleActivityClick(activity.id)}>
          <div className="activity-icon error-icon">⚠️</div>
          <div className="activity-title">
            <span className={`error-severity ${activity.severity}`}>{activity.severity}</span>
            <span className="error-message">{activity.message}</span>
          </div>
          <div className="activity-timestamp">{formatTimestamp(activity.timestamp)}</div>
        </div>
        
        {isExpanded && activity.details && (
          <div className="activity-details">
            <pre>{JSON.stringify(activity.details, null, 2)}</pre>
          </div>
        )}
      </>
    );
  };

  // Render activity based on type
  const renderActivity = (activity) => {
    switch (activity.type) {
      case 'tool_call':
        return renderToolCallActivity(activity);
      case 'planning':
        return renderPlanningActivity(activity);
      case 'phase_transition':
        return renderPhaseTransitionActivity(activity);
      case 'error':
        return renderErrorActivity(activity);
      default:
        return null;
    }
  };

  return (
    <div className={`tool-activity-timeline ${className}`}>
      <div className="timeline-header">
        <h3>Agent Activity</h3>
        
        <div className="timeline-controls">
          <div className="filter-controls">
            <button 
              className={`filter-button ${filter === 'all' ? 'active' : ''}`}
              onClick={() => handleFilterChange('all')}
            >
              All
            </button>
            <button 
              className={`filter-button ${filter === 'tool_call' ? 'active' : ''}`}
              onClick={() => handleFilterChange('tool_call')}
            >
              Tools
            </button>
            <button 
              className={`filter-button ${filter === 'planning' ? 'active' : ''}`}
              onClick={() => handleFilterChange('planning')}
            >
              Planning
            </button>
            <button 
              className={`filter-button ${filter === 'phase_transition' ? 'active' : ''}`}
              onClick={() => handleFilterChange('phase_transition')}
            >
              Phases
            </button>
            <button 
              className={`filter-button ${filter === 'error' ? 'active' : ''}`}
              onClick={() => handleFilterChange('error')}
            >
              Errors
            </button>
          </div>
          
          <button 
            className={`auto-scroll-button ${autoScrollRef.current ? 'active' : ''}`}
            onClick={handleAutoScrollToggle}
            title={autoScrollRef.current ? 'Auto-scroll enabled' : 'Auto-scroll disabled'}
          >
            {autoScrollRef.current ? '📜' : '🛑'}
          </button>
        </div>
      </div>
      
      <div className="timeline-content" ref={timelineRef}>
        {filteredActivities.length === 0 ? (
          <div className="empty-timeline">
            <p>No activities yet</p>
          </div>
        ) : (
          filteredActivities.map(activity => (
            <div 
              key={activity.id} 
              className={getActivityClass(activity)}
            >
              {renderActivity(activity)}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default ToolActivityTimeline;
