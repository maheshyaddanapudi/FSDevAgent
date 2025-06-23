import React from 'react';
import './PlanDisplay.css';

const PlanDisplay = ({ 
  plan = null, 
  currentStep = 0, 
  isMinimized = false,
  isAgentWorking = false 
}) => {
  // Default plan structure if none provided
  const defaultPlan = {
    title: "Agent Planning",
    steps: [
      { id: 1, text: "Analyzing request", status: "completed" },
      { id: 2, text: "Planning approach", status: "current" },
      { id: 3, text: "Executing tasks", status: "pending" },
      { id: 4, text: "Delivering results", status: "pending" }
    ]
  };

  const activePlan = plan || defaultPlan;
  const completedSteps = activePlan.steps.filter(step => step.status === 'completed').length;
  const totalSteps = activePlan.steps.length;

  const getStepStatus = (step, index) => {
    if (step.status) return step.status;
    if (index < currentStep) return 'completed';
    if (index === currentStep) return 'current';
    return 'pending';
  };

  const getStepIcon = (status) => {
    switch (status) {
      case 'completed':
        return '✓';
      case 'current':
        return isAgentWorking ? '⚡' : '●';
      case 'pending':
        return '○';
      default:
        return '○';
    }
  };

  return (
    <div className={`plan-display ${isMinimized ? 'minimized' : 'expanded'}`}>
      <div className="plan-header">
        <div className="plan-title-section">
          <h3 className="plan-title">{activePlan.title}</h3>
          {isAgentWorking && (
            <span className="working-indicator">
              <span className="pulse-dot"></span>
              Working...
            </span>
          )}
        </div>
        <div className="plan-progress">
          {completedSteps}/{totalSteps} completed
        </div>
      </div>

      {!isMinimized && (
        <div className="plan-content">
          <ul className="plan-steps">
            {activePlan.steps.map((step, index) => {
              const status = getStepStatus(step, index);
              return (
                <li key={step.id || index} className={`plan-step ${status}`}>
                  <div className={`step-status ${status}`}>
                    {getStepIcon(status)}
                  </div>
                  <span className={`step-text ${status}`}>
                    {step.text}
                  </span>
                  {status === 'current' && isAgentWorking && (
                    <div className="step-progress">
                      <div className="progress-bar">
                        <div className="progress-fill"></div>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>

          {isAgentWorking && (
            <div className="plan-footer">
              <div className="agent-status">
                <span className="status-icon">🤖</span>
                <span className="status-text">Agent is actively working on your request</span>
              </div>
            </div>
          )}
        </div>
      )}

      {isMinimized && (
        <div className="plan-summary">
          <div className="summary-progress">
            <div className="progress-circle">
              <span className="progress-text">{completedSteps}/{totalSteps}</span>
            </div>
          </div>
          {isAgentWorking && (
            <div className="mini-status">
              <span className="mini-pulse"></span>
              Working
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default PlanDisplay;

