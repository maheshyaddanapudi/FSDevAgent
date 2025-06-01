// frontend/src/components/UnifiedEmulator/tools/GitOutput.js
import React, { useState, useEffect } from 'react';
import { ReactDiffViewer } from 'react-diff-viewer';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './GitOutput.css';

/**
 * Git output visualization component
 * Renders git command outputs with specialized formatting for different git operations
 */
const GitOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [activeTab, setActiveTab] = useState('output');
  const [diffView, setDiffView] = useState(null);
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process git output to detect operation type and format accordingly
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    const outputStr = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    
    // Check for diff output
    if (outputStr.includes('diff --git') || 
        (outputStr.includes('+++') && outputStr.includes('---'))) {
      try {
        // Extract old and new content for diff viewer
        const diffParts = parseDiff(outputStr);
        if (diffParts) {
          setDiffView(diffParts);
          setActiveTab('diff');
        }
      } catch (error) {
        console.error('Error parsing git diff:', error);
      }
    }
  }, [data]);
  
  // Parse git diff output
  const parseDiff = (diffText) => {
    // Simple diff parser - can be enhanced for more complex diffs
    const lines = diffText.split('\n');
    let oldCode = '';
    let newCode = '';
    let currentFile = '';
    
    // Find file name from diff header
    const fileMatch = diffText.match(/diff --git a\/(.*?) b\/(.*?)$/m);
    if (fileMatch) {
      currentFile = fileMatch[1];
    }
    
    // Extract old and new content
    let inOldSection = false;
    let inNewSection = false;
    
    for (const line of lines) {
      if (line.startsWith('---')) {
        inOldSection = true;
        inNewSection = false;
        continue;
      }
      
      if (line.startsWith('+++')) {
        inOldSection = false;
        inNewSection = true;
        continue;
      }
      
      if (line.startsWith('-') && !line.startsWith('---')) {
        oldCode += line.substring(1) + '\n';
      } else if (line.startsWith('+') && !line.startsWith('+++')) {
        newCode += line.substring(1) + '\n';
      } else if (line.startsWith(' ')) {
        oldCode += line.substring(1) + '\n';
        newCode += line.substring(1) + '\n';
      }
    }
    
    return {
      fileName: currentFile,
      oldCode,
      newCode
    };
  };
  
  // Render git status output
  const renderStatusOutput = (output) => {
    if (!output) return null;
    
    const outputStr = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    
    // Check if it's a status output
    if (outputStr.includes('On branch') || outputStr.includes('Changes not staged')) {
      const lines = outputStr.split('\n');
      
      return (
        <div className="git-status-output">
          {lines.map((line, index) => {
            let className = 'git-status-line';
            
            if (line.includes('On branch')) {
              className += ' git-branch-info';
            } else if (line.includes('Changes to be committed')) {
              className += ' git-section-header';
            } else if (line.includes('Changes not staged')) {
              className += ' git-section-header';
            } else if (line.includes('Untracked files')) {
              className += ' git-section-header';
            } else if (line.trim().startsWith('modified:')) {
              className += ' git-modified';
            } else if (line.trim().startsWith('new file:')) {
              className += ' git-new-file';
            } else if (line.trim().startsWith('deleted:')) {
              className += ' git-deleted';
            }
            
            return (
              <div key={index} className={className}>
                {line}
              </div>
            );
          })}
        </div>
      );
    }
    
    // Default output rendering
    return <pre className="git-raw-output">{outputStr}</pre>;
  };
  
  // Render git log output
  const renderLogOutput = (output) => {
    if (!output) return null;
    
    const outputStr = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    
    // Check if it's a log output
    if (outputStr.includes('commit ') && (outputStr.includes('Author:') || outputStr.includes('Date:'))) {
      const commits = parseGitLog(outputStr);
      
      return (
        <div className="git-log-output">
          {commits.map((commit, index) => (
            <div key={index} className="git-commit">
              <div className="git-commit-header">
                <div className="git-commit-hash">{commit.hash}</div>
                <div className="git-commit-date">{commit.date}</div>
              </div>
              <div className="git-commit-author">{commit.author}</div>
              <div className="git-commit-message">{commit.message}</div>
            </div>
          ))}
        </div>
      );
    }
    
    // Default output rendering
    return <pre className="git-raw-output">{outputStr}</pre>;
  };
  
  // Parse git log output
  const parseGitLog = (logText) => {
    const commits = [];
    const commitChunks = logText.split(/commit [0-9a-f]{7,40}/);
    const hashMatches = logText.match(/commit [0-9a-f]{7,40}/g) || [];
    
    // Skip the first empty chunk
    for (let i = 1; i < commitChunks.length; i++) {
      const chunk = commitChunks[i];
      const hash = hashMatches[i - 1]?.replace('commit ', '') || '';
      
      const authorMatch = chunk.match(/Author: (.*?)$/m);
      const dateMatch = chunk.match(/Date: (.*?)$/m);
      
      // Extract message - everything after the date line
      let message = '';
      if (dateMatch) {
        const dateLineIndex = chunk.indexOf(dateMatch[0]);
        if (dateLineIndex !== -1) {
          const afterDate = chunk.substring(dateLineIndex + dateMatch[0].length);
          message = afterDate.trim();
        }
      }
      
      commits.push({
        hash,
        author: authorMatch ? authorMatch[1] : 'Unknown author',
        date: dateMatch ? dateMatch[1] : 'Unknown date',
        message
      });
    }
    
    return commits;
  };
  
  // Render diff view
  const renderDiffView = () => {
    if (!diffView) return <div className="git-no-diff">No diff available</div>;
    
    return (
      <div className="git-diff-view">
        <div className="git-diff-header">
          <div className="git-diff-filename">{diffView.fileName || 'Unknown file'}</div>
        </div>
        <ReactDiffViewer
          oldValue={diffView.oldCode}
          newValue={diffView.newCode}
          splitView={true}
          useDarkTheme={true}
          showDiffOnly={false}
        />
      </div>
    );
  };
  
  // Render main output based on data
  const renderOutput = () => {
    if (!data) {
      return (
        <div className="git-empty-state">
          <div className="git-empty-icon">🔄</div>
          <div className="git-empty-message">Waiting for git command output...</div>
        </div>
      );
    }
    
    const output = data.output || data.data || data.content || '';
    
    // First try to render as status
    const statusOutput = renderStatusOutput(output);
    if (statusOutput.type !== 'pre') {
      return statusOutput;
    }
    
    // Then try to render as log
    const logOutput = renderLogOutput(output);
    if (logOutput.type !== 'pre') {
      return logOutput;
    }
    
    // Default to raw output
    return <pre className="git-raw-output">{typeof output === 'string' ? output : JSON.stringify(output, null, 2)}</pre>;
  };
  
  return (
    <div className="git-output">
      <div className="git-toolbar">
        <div className="git-tabs">
          <button 
            className={`git-tab ${activeTab === 'output' ? 'active' : ''}`}
            onClick={() => setActiveTab('output')}
          >
            Output
          </button>
          
          <button 
            className={`git-tab ${activeTab === 'diff' ? 'active' : ''}`}
            onClick={() => setActiveTab('diff')}
            disabled={!diffView}
          >
            Diff
          </button>
        </div>
        
        <div className="git-info">
          <span className="git-status">
            {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
          </span>
          <span className="git-count">
            {allOutputs ? `${allOutputs.length} outputs` : '0 outputs'}
          </span>
        </div>
      </div>
      
      <div className="git-content">
        {activeTab === 'output' ? renderOutput() : renderDiffView()}
      </div>
    </div>
  );
};

export default GitOutput;
