import React, { useState, useEffect } from 'react';
import { parseAgentContent } from '../utils/contentParser';
import '../styles/PhaseComponent.css';

/**
 * Collapsible Phase Component based on wireframe specifications
 * Groups related agent activities into expandable/collapsible phases
 * with progress tracking and metadata display
 */
const PhaseComponent = ({ 
  phase, 
  tasks = [], 
  isActive = false,
  knowledgeRecallCount = 0,
  progress = 0,
  onToggle
}) => {
  const [isExpanded, setIsExpanded] = useState(isActive);

  useEffect(() => {
    setIsExpanded(isActive);
  }, [isActive]);

  const handleToggle = () => {
    const newExpanded = !isExpanded;
    setIsExpanded(newExpanded);
    if (onToggle) {
      onToggle(phase.id, newExpanded);
    }
  };

  const getPhaseIcon = () => {
    switch (phase.type) {
      case 'analysis':
        return '🔍';
      case 'planning':
        return '📋';
      case 'implementation':
        return '⚙️';
      case 'testing':
        return '🧪';
      case 'completion':
        return '✅';
      default:
        return '📄';
    }
  };

  const getTaskIcon = (task) => {
    switch (task.type) {
      case 'file_read':
        return '📄';
      case 'file_write':
        return '✏️';
      case 'file_edit':
        return '📝';
      case 'thinking':
        return '🔵';
      case 'tool_execution':
        return '🔧';
      case 'completed':
        return '✅';
      case 'error':
        return '❌';
      default:
        return '•';
    }
  };

  const getTaskStatusClass = (task) => {
    switch (task.status) {
      case 'completed':
        return 'task-completed';
      case 'active':
        return 'task-active';
      case 'error':
        return 'task-error';
      default:
        return 'task-pending';
    }
  };

  return (
    <div className={`phase-component ${isActive ? 'phase-active' : ''} ${isExpanded ? 'phase-expanded' : ''}`}>
      <div className="phase-header" onClick={handleToggle}>
        <div className="phase-header-left">
          <span className="expand-icon">
            {isExpanded ? '▼' : '▶'}
          </span>
          <span className="phase-icon">{getPhaseIcon()}</span>
          <div className="phase-info">
            <span className="phase-title">{phase.title}</span>
            <div className="phase-meta">
              {knowledgeRecallCount > 0 && (
                <span className="knowledge-recall">
                  Knowledge recalled({knowledgeRecallCount})
                </span>
              )}
              {progress > 0 && (
                <span className="phase-progress">
                  {progress}% complete
                </span>
              )}
            </div>
          </div>
        </div>
        
        <div className="phase-header-right">
          {tasks.length > 0 && (
            <span className="task-count">
              {tasks.filter(t => t.status === 'completed').length}/{tasks.length}
            </span>
          )}
          {progress > 0 && (
            <div className="progress-bar-mini">
              <div 
                className="progress-fill-mini" 
                style={{ width: `${progress}%` }}
              ></div>
            </div>
          )}
        </div>
      </div>
      
      {isExpanded && (
        <div className="phase-content">
          {tasks.length > 0 ? (
            <div className="phase-tasks">
              {tasks.map((task, index) => (
                <div 
                  key={task.id || index} 
                  className={`task-item ${getTaskStatusClass(task)}`}
                >
                  <span className="task-icon">{getTaskIcon(task)}</span>
                  <span className="task-text">{task.description}</span>
                  {task.timestamp && (
                    <span className="task-timestamp">
                      {new Date(task.timestamp).toLocaleTimeString()}
                    </span>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="phase-empty">
              <span className="empty-icon">📝</span>
              <span className="empty-text">No tasks in this phase yet</span>
            </div>
          )}
          
          {progress > 0 && (
            <div className="phase-progress-section">
              <div className="progress-bar">
                <div 
                  className="progress-fill" 
                  style={{ width: `${progress}%` }}
                ></div>
              </div>
              <span className="progress-text">{progress}% Complete</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

/**
 * Phase Manager Component - Organizes messages into phases
 * Analyzes message stream to create phase-based organization
 */
export const PhaseManager = ({ messages = [], onPhaseToggle }) => {
  const [phases, setPhases] = useState([]);
  const [activePhaseId, setActivePhaseId] = useState(null);

  useEffect(() => {
    // Analyze messages and organize into phases
    const organizedPhases = organizeMessagesIntoPhases(messages);
    setPhases(organizedPhases);
    
    // Set the last phase as active
    if (organizedPhases.length > 0) {
      const lastPhase = organizedPhases[organizedPhases.length - 1];
      setActivePhaseId(lastPhase.id);
    }
  }, [messages]);

  const handlePhaseToggle = (phaseId, isExpanded) => {
    if (onPhaseToggle) {
      onPhaseToggle(phaseId, isExpanded);
    }
  };

  return (
    <div className="phase-manager">
      {phases.map((phase) => (
        <PhaseComponent
          key={phase.id}
          phase={phase}
          tasks={phase.tasks}
          isActive={phase.id === activePhaseId}
          knowledgeRecallCount={phase.knowledgeRecallCount}
          progress={phase.progress}
          onToggle={handlePhaseToggle}
        />
      ))}
    </div>
  );
};

/**
 * Organize messages into phases based on content analysis
 * This function analyzes the message stream to create logical phases
 */
function organizeMessagesIntoPhases(messages) {
  const phases = [];
  let currentPhase = null;
  let phaseCounter = 1;

  messages.forEach((message, index) => {
    // Skip user messages for phase organization
    if (message.role === 'user') return;
    
    // Use message.content instead of message.message
    const content = message.content || '';
    if (!content.trim()) return;
    
    const phaseType = detectPhaseType(message);
    
    // Start a new phase if type changes or if it's the first assistant message
    if (!currentPhase || currentPhase.type !== phaseType) {
      if (currentPhase) {
        phases.push(currentPhase);
      }
      
      currentPhase = {
        id: `phase-${phaseCounter++}`,
        type: phaseType,
        title: getPhaseTitle(phaseType, phaseCounter - 1),
        tasks: [],
        knowledgeRecallCount: 0,
        progress: 0,
        startIndex: index
      };
    }
    
    // Parse the message content to extract sections
    const parsedContent = parseAgentContent(content);
    
    // Add main response as a task if it exists
    if (parsedContent.mainResponse && parsedContent.mainResponse.trim()) {
      const mainTask = {
        id: `task-${index}-main`,
        type: 'general',
        status: 'completed',
        description: parsedContent.mainResponse.length > 100 
          ? parsedContent.mainResponse.substring(0, 100) + '...'
          : parsedContent.mainResponse,
        timestamp: new Date().toISOString()
      };
      currentPhase.tasks.push(mainTask);
    }
    
    // Add tasks from parsed sections
    if (parsedContent.sections && Array.isArray(parsedContent.sections)) {
      parsedContent.sections.forEach((section, sectionIndex) => {
        const task = sectionToTask(section, index, sectionIndex);
        if (task) {
          currentPhase.tasks.push(task);
        }
      });
    }
    
    // Update phase metadata
    if (content.includes('Knowledge recalled') || content.includes('knowledge recall')) {
      currentPhase.knowledgeRecallCount++;
    }
  });
  
  // Add the last phase
  if (currentPhase) {
    phases.push(currentPhase);
  }
  
  // Calculate progress for each phase
  phases.forEach(phase => {
    const completedTasks = phase.tasks.filter(task => task.status === 'completed').length;
    phase.progress = phase.tasks.length > 0 ? Math.round((completedTasks / phase.tasks.length) * 100) : 0;
  });
  
  return phases;
}

/**
 * Detect phase type based on message content
 */
function detectPhaseType(message) {
  const content = (message.content || '').toLowerCase();
  
  if (content.includes('think') || content.includes('analyzing') || content.includes('analysis') || content.includes('understanding')) {
    return 'analysis';
  }
  if (content.includes('reason') || content.includes('planning') || content.includes('plan') || content.includes('strategy')) {
    return 'planning';
  }
  if (content.includes('act') || content.includes('implementing') || content.includes('creating') || content.includes('building') || content.includes('tool_use')) {
    return 'implementation';
  }
  if (content.includes('testing') || content.includes('validating') || content.includes('checking')) {
    return 'testing';
  }
  if (content.includes('task_complete') || content.includes('completed') || content.includes('finished') || content.includes('done')) {
    return 'completion';
  }
  
  return 'general';
}

/**
 * Get phase title based on type and number
 */
function getPhaseTitle(type, number) {
  const titles = {
    analysis: `Phase ${number}: Analysis & Thinking`,
    planning: `Phase ${number}: Planning & Reasoning`,
    implementation: `Phase ${number}: Implementation & Actions`,
    testing: `Phase ${number}: Testing & Validation`,
    completion: `Phase ${number}: Task Completion`,
    general: `Phase ${number}: General Tasks`
  };
  
  return titles[type] || `Phase ${number}: Tasks`;
}

/**
 * Convert parsed section to task object
 */
function sectionToTask(section, messageIndex, sectionIndex) {
  if (!section || !section.content) return null;
  
  let type = 'general';
  let status = 'completed';
  let description = section.content;
  
  // Detect task type from section type
  switch (section.type) {
    case 'thinking':
      type = 'thinking';
      status = 'completed';
      description = `💭 ${section.content.substring(0, 80)}...`;
      break;
    case 'reasoning':
      type = 'planning';
      status = 'completed';
      description = `🧠 ${section.content.substring(0, 80)}...`;
      break;
    case 'action':
      type = 'tool_execution';
      status = 'completed';
      description = `⚙️ ${section.content.substring(0, 80)}...`;
      break;
    case 'tool_use':
      type = 'tool_execution';
      status = 'completed';
      description = `🔧 Tool: ${section.toolName || 'Unknown'}`;
      break;
    case 'observation':
      type = 'completed';
      status = 'completed';
      description = `👁️ ${section.content.substring(0, 80)}...`;
      break;
    case 'event':
      if (section.content && typeof section.content === 'object') {
        const eventType = section.content.type;
        const eventData = section.content.data || section.content.title || 'Event';
        const eventIcon = section.content.icon || '📋';
        
        if (eventType === 'TASK_COMPLETE') {
          type = 'completed';
          status = 'completed';
          description = `✅ ${eventData}`;
        } else if (eventType === 'PROGRESS') {
          type = 'general';
          status = 'active';
          description = `📊 Progress: ${eventData}`;
        } else if (eventType === 'PHASE_TRANSITION') {
          type = 'general';
          status = 'completed';
          description = `🔄 Phase: ${eventData}`;
        } else {
          type = 'general';
          status = 'completed';
          description = `${eventIcon} ${eventData}`;
        }
      } else {
        // Fallback for string content
        type = 'general';
        status = 'completed';
        description = `📋 ${section.content || 'Event'}`;
      }
      break;
    default:
      if (description.length > 100) {
        description = description.substring(0, 100) + '...';
      }
  }
  
  return {
    id: `task-${messageIndex}-${sectionIndex}`,
    type,
    status,
    description,
    timestamp: new Date().toISOString()
  };
}

export default PhaseComponent;

