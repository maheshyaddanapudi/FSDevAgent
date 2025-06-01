// frontend/src/components/UnifiedEmulator/ToolSkeleton.js
import React from 'react';
import './ToolSkeleton.css';

/**
 * Loading skeleton for tool components
 * Displays a placeholder UI while the actual tool component is loading
 */
const ToolSkeleton = ({ type }) => {
  // Different skeleton layouts based on tool type
  const renderSkeletonContent = () => {
    switch (type) {
      case 'terminal':
        return (
          <div className="terminal-skeleton">
            <div className="skeleton-line"></div>
            <div className="skeleton-line"></div>
            <div className="skeleton-line"></div>
            <div className="skeleton-line" style={{ width: '75%' }}></div>
            <div className="skeleton-line" style={{ width: '60%' }}></div>
          </div>
        );
        
      case 'browser':
        return (
          <div className="browser-skeleton">
            <div className="skeleton-header"></div>
            <div className="skeleton-body">
              <div className="skeleton-image"></div>
              <div className="skeleton-text">
                <div className="skeleton-line"></div>
                <div className="skeleton-line"></div>
                <div className="skeleton-line" style={{ width: '80%' }}></div>
              </div>
            </div>
          </div>
        );
        
      case 'filesystem':
        return (
          <div className="filesystem-skeleton">
            <div className="skeleton-sidebar">
              <div className="skeleton-tree-item"></div>
              <div className="skeleton-tree-item"></div>
              <div className="skeleton-tree-item"></div>
            </div>
            <div className="skeleton-content">
              <div className="skeleton-line"></div>
              <div className="skeleton-line"></div>
              <div className="skeleton-line"></div>
              <div className="skeleton-line" style={{ width: '70%' }}></div>
            </div>
          </div>
        );
        
      case 'git':
        return (
          <div className="git-skeleton">
            <div className="skeleton-header"></div>
            <div className="skeleton-commit">
              <div className="skeleton-commit-info"></div>
              <div className="skeleton-commit-message"></div>
            </div>
            <div className="skeleton-commit">
              <div className="skeleton-commit-info"></div>
              <div className="skeleton-commit-message"></div>
            </div>
          </div>
        );
        
      case 'build':
        return (
          <div className="build-skeleton">
            <div className="skeleton-header"></div>
            <div className="skeleton-log">
              <div className="skeleton-line"></div>
              <div className="skeleton-line"></div>
              <div className="skeleton-line"></div>
              <div className="skeleton-line" style={{ width: '65%' }}></div>
            </div>
          </div>
        );
        
      case 'code':
        return (
          <div className="code-skeleton">
            <div className="skeleton-header"></div>
            <div className="skeleton-editor">
              <div className="skeleton-line"></div>
              <div className="skeleton-line"></div>
              <div className="skeleton-line" style={{ width: '80%' }}></div>
              <div className="skeleton-line" style={{ width: '60%' }}></div>
              <div className="skeleton-line" style={{ width: '70%' }}></div>
            </div>
          </div>
        );
        
      case 'dataviz':
        return (
          <div className="dataviz-skeleton">
            <div className="skeleton-header"></div>
            <div className="skeleton-chart"></div>
            <div className="skeleton-legend">
              <div className="skeleton-legend-item"></div>
              <div className="skeleton-legend-item"></div>
              <div className="skeleton-legend-item"></div>
            </div>
          </div>
        );
        
      default:
        return (
          <div className="default-skeleton">
            <div className="skeleton-line"></div>
            <div className="skeleton-line"></div>
            <div className="skeleton-line"></div>
          </div>
        );
    }
  };

  return (
    <div className={`tool-skeleton ${type}-skeleton-container`}>
      <div className="skeleton-loading-indicator">
        <div className="skeleton-spinner"></div>
        <div className="skeleton-loading-text">Loading {type} visualization...</div>
      </div>
      {renderSkeletonContent()}
    </div>
  );
};

export default ToolSkeleton;
