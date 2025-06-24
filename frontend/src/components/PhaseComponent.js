import React, { useState, useEffect } from 'react';
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
    const phaseType = detectPhaseType(message);
    
    // Start a new phase if type changes or if it's the first message
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
    
    // Add message as a task to current phase
    const task = messageToTask(message, index);
    if (task) {
      currentPhase.tasks.push(task);
    }
    
    // Update phase metadata
    if (message.message && message.message.includes('Knowledge recalled')) {
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
  const content = message.message || '';
  
  if (content.includes('analyzing') || content.includes('analysis') || content.includes('understanding')) {
    return 'analysis';
  }
  if (content.includes('planning') || content.includes('plan') || content.includes('strategy')) {
    return 'planning';
  }
  if (content.includes('implementing') || content.includes('creating') || content.includes('building')) {
    return 'implementation';
  }
  if (content.includes('testing') || content.includes('validating') || content.includes('checking')) {
    return 'testing';
  }
  if (content.includes('completed') || content.includes('finished') || content.includes('done')) {
    return 'completion';
  }
  
  return 'general';
}

/**
 * Get phase title based on type and number
 */
function getPhaseTitle(type, number) {
  const titles = {
    analysis: `Phase ${number}: Analysis & Understanding`,
    planning: `Phase ${number}: Planning & Strategy`,
    implementation: `Phase ${number}: Implementation`,
    testing: `Phase ${number}: Testing & Validation`,
    completion: `Phase ${number}: Completion`,
    general: `Phase ${number}: General Tasks`
  };
  
  return titles[type] || `Phase ${number}: Tasks`;
}

/**
 * Convert message to task object
 */
function messageToTask(message, index) {
  if (!message.message) return null;
  
  const content = message.message;
  let type = 'general';
  let status = 'completed';
  
  // Detect task type from content
  if (content.includes('Reading file') || content.includes('📄')) {
    type = 'file_read';
  } else if (content.includes('Writing file') || content.includes('Creating file')) {
    type = 'file_write';
  } else if (content.includes('Editing file') || content.includes('✏️')) {
    type = 'file_edit';
  } else if (content.includes('thinking') || content.includes('🔵')) {
    type = 'thinking';
    status = 'active';
  } else if (content.includes('error') || content.includes('❌')) {
    type = 'error';
    status = 'error';
  } else if (content.includes('tool') || content.includes('executing')) {
    type = 'tool_execution';
  }
  
  return {
    id: `task-${index}`,
    type,
    status,
    description: content.length > 100 ? content.substring(0, 100) + '...' : content,
    timestamp: message.timestamp
  };
}

export default PhaseComponent;

