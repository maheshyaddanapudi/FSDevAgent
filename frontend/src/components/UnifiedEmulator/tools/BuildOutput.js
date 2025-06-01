// frontend/src/components/UnifiedEmulator/tools/BuildOutput.js
import React, { useState, useEffect, useRef } from 'react';
import { FixedSizeList as List } from 'react-window';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './BuildOutput.css';

/**
 * Build output visualization component
 * Renders build logs with virtualization for performance
 */
const BuildOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [logLines, setLogLines] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [filteredLines, setFilteredLines] = useState([]);
  const [autoScroll, setAutoScroll] = useState(true);
  const [filterType, setFilterType] = useState('all');
  
  const listRef = useRef(null);
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process build output
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    const outputStr = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    
    // Split output into lines and add metadata
    const lines = outputStr.split('\n').map((line, index) => {
      const type = getLineType(line);
      return {
        id: `${Date.now()}-${index}`,
        text: line,
        type
      };
    });
    
    setLogLines(prevLines => [...prevLines, ...lines]);
  }, [data]);
  
  // Apply filtering
  useEffect(() => {
    let filtered = [...logLines];
    
    // Apply type filter
    if (filterType !== 'all') {
      filtered = filtered.filter(line => line.type === filterType);
    }
    
    // Apply search filter
    if (searchTerm) {
      filtered = filtered.filter(line => 
        line.text.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }
    
    setFilteredLines(filtered);
    
    // Auto-scroll to bottom if enabled
    if (autoScroll && listRef.current && filtered.length > 0) {
      listRef.current.scrollToItem(filtered.length - 1);
    }
  }, [logLines, searchTerm, filterType, autoScroll]);
  
  // Determine line type based on content
  const getLineType = (line) => {
    const lowerLine = line.toLowerCase();
    
    if (lowerLine.includes('error') || lowerLine.includes('exception') || lowerLine.includes('fail')) {
      return 'error';
    } else if (lowerLine.includes('warn')) {
      return 'warning';
    } else if (lowerLine.includes('info')) {
      return 'info';
    } else if (lowerLine.includes('success') || lowerLine.includes('completed')) {
      return 'success';
    } else {
      return 'normal';
    }
  };
  
  // Handle search
  const handleSearch = (e) => {
    setSearchTerm(e.target.value);
  };
  
  // Handle filter change
  const handleFilterChange = (type) => {
    setFilterType(type);
  };
  
  // Clear logs
  const handleClear = () => {
    setLogLines([]);
    setFilteredLines([]);
  };
  
  // Toggle auto-scroll
  const toggleAutoScroll = () => {
    setAutoScroll(!autoScroll);
  };
  
  // Render individual log line
  const renderLogLine = ({ index, style }) => {
    const line = filteredLines[index];
    
    return (
      <div 
        style={style} 
        className={`build-log-line build-log-${line.type}`}
      >
        <span className="build-log-number">{index + 1}</span>
        <span className="build-log-text">{line.text}</span>
      </div>
    );
  };
  
  return (
    <div className="build-output">
      <div className="build-toolbar">
        <div className="build-filters">
          <div className="build-search">
            <input
              type="text"
              placeholder="Search logs..."
              value={searchTerm}
              onChange={handleSearch}
            />
          </div>
          
          <div className="build-filter-buttons">
            <button 
              className={`build-filter-button ${filterType === 'all' ? 'active' : ''}`}
              onClick={() => handleFilterChange('all')}
            >
              All
            </button>
            <button 
              className={`build-filter-button ${filterType === 'error' ? 'active' : ''}`}
              onClick={() => handleFilterChange('error')}
            >
              Errors
            </button>
            <button 
              className={`build-filter-button ${filterType === 'warning' ? 'active' : ''}`}
              onClick={() => handleFilterChange('warning')}
            >
              Warnings
            </button>
            <button 
              className={`build-filter-button ${filterType === 'info' ? 'active' : ''}`}
              onClick={() => handleFilterChange('info')}
            >
              Info
            </button>
          </div>
        </div>
        
        <div className="build-actions">
          <button 
            className={`build-action-button ${autoScroll ? 'active' : ''}`}
            onClick={toggleAutoScroll}
            title={autoScroll ? 'Disable auto-scroll' : 'Enable auto-scroll'}
          >
            {autoScroll ? '📌 Auto-scroll ON' : '📌 Auto-scroll OFF'}
          </button>
          
          <button 
            className="build-action-button"
            onClick={handleClear}
            title="Clear logs"
          >
            🗑️ Clear
          </button>
        </div>
      </div>
      
      <div className="build-status-bar">
        <div className="build-connection-status">
          {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
        </div>
        
        <div className="build-stats">
          <span className="build-line-count">
            {filteredLines.length} lines {searchTerm && `(filtered from ${logLines.length})`}
          </span>
          
          <span className="build-error-count">
            {logLines.filter(line => line.type === 'error').length} errors
          </span>
          
          <span className="build-warning-count">
            {logLines.filter(line => line.type === 'warning').length} warnings
          </span>
        </div>
      </div>
      
      <div className="build-log-container">
        {filteredLines.length > 0 ? (
          <List
            ref={listRef}
            height={500} // This will be overridden by CSS
            width="100%"
            itemCount={filteredLines.length}
            itemSize={24} // Line height
            className="build-log-list"
          >
            {renderLogLine}
          </List>
        ) : (
          <div className="build-empty-state">
            {logLines.length > 0 ? (
              <div className="build-no-matches">No log lines match the current filters</div>
            ) : (
              <div className="build-no-logs">
                <div className="build-empty-icon">🔄</div>
                <div className="build-empty-message">Waiting for build output...</div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default BuildOutput;
