import React, { useState, useEffect } from 'react';
import './InitializingOutput.css';

/**
 * InitializingOutput - Shows agent computer initializing screen
 */
const InitializingOutput = ({ wsConnected }) => {
  const [dots, setDots] = useState('');
  
  // Animate loading dots
  useEffect(() => {
    const interval = setInterval(() => {
      setDots(prev => {
        if (prev === '...') return '';
        return prev + '.';
      });
    }, 500);
    
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="initializing-output">
      <div className="initializing-container">
        <div className="initializing-icon">
          <div className="computer-icon">
            <div className="screen">
              <div className="screen-glow"></div>
            </div>
            <div className="base"></div>
          </div>
        </div>
        
        <div className="initializing-content">
          <h2 className="initializing-title">
            Agent Computer Initializing{dots}
          </h2>
          <p className="initializing-subtitle">
            Setting up autonomous development environment
          </p>
          
          <div className="status-indicators">
            <div className={`status-item ${wsConnected ? 'active' : ''}`}>
              <div className="status-dot"></div>
              <span>Connection {wsConnected ? 'Established' : 'Pending'}</span>
            </div>
            <div className="status-item active">
              <div className="status-dot"></div>
              <span>Workspace Ready</span>
            </div>
            <div className="status-item">
              <div className="status-dot"></div>
              <span>Awaiting Task</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default InitializingOutput;

