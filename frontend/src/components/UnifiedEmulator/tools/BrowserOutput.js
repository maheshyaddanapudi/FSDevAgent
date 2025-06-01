// frontend/src/components/UnifiedEmulator/tools/BrowserOutput.js
import React, { useState, useEffect } from 'react';
import Frame from 'react-frame-component';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './BrowserOutput.css';

/**
 * Browser output visualization component
 * Renders browser automation results, screenshots, and HTML content
 */
const BrowserOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [activeTab, setActiveTab] = useState('content');
  const [htmlContent, setHtmlContent] = useState('');
  const [screenshots, setScreenshots] = useState([]);
  const [selectedScreenshot, setSelectedScreenshot] = useState(0);
  const [zoomLevel, setZoomLevel] = useState(1);
  
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process browser output
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    
    // Check if output contains HTML content
    if (typeof output === 'string' && (
        output.includes('<!DOCTYPE html>') || 
        output.includes('<html') || 
        output.includes('<body')
      )) {
      setHtmlContent(output);
      setActiveTab('content');
    } 
    // Check if output contains screenshot URLs
    else if (typeof output === 'object' && output.screenshots) {
      setScreenshots(Array.isArray(output.screenshots) ? output.screenshots : [output.screenshots]);
      setActiveTab('screenshots');
    }
    // Check if output is a screenshot URL
    else if (typeof output === 'string' && (
        output.startsWith('http') && 
        (output.includes('.png') || output.includes('.jpg') || output.includes('.jpeg'))
      )) {
      setScreenshots([output]);
      setActiveTab('screenshots');
    }
    // Default to showing as raw output
    else {
      setHtmlContent(typeof output === 'string' ? output : JSON.stringify(output, null, 2));
      setActiveTab('raw');
    }
  }, [data]);
  
  // Handle tab change
  const handleTabChange = (tab) => {
    setActiveTab(tab);
  };
  
  // Handle screenshot navigation
  const navigateScreenshots = (direction) => {
    if (direction === 'next' && selectedScreenshot < screenshots.length - 1) {
      setSelectedScreenshot(selectedScreenshot + 1);
    } else if (direction === 'prev' && selectedScreenshot > 0) {
      setSelectedScreenshot(selectedScreenshot - 1);
    }
  };
  
  // Handle zoom controls
  const handleZoom = (action) => {
    if (action === 'in') {
      setZoomLevel(Math.min(zoomLevel + 0.25, 3));
    } else if (action === 'out') {
      setZoomLevel(Math.max(zoomLevel - 0.25, 0.5));
    } else if (action === 'reset') {
      setZoomLevel(1);
    }
  };
  
  // Render HTML content in sandbox iframe
  const renderHtmlContent = () => {
    if (!htmlContent) {
      return (
        <div className="browser-empty-content">
          No HTML content to display
        </div>
      );
    }
    
    return (
      <div className="browser-html-container">
        <Frame 
          className="browser-iframe"
          initialContent={htmlContent}
          sandbox="allow-same-origin"
        />
      </div>
    );
  };
  
  // Render screenshots
  const renderScreenshots = () => {
    if (!screenshots.length) {
      return (
        <div className="browser-empty-content">
          No screenshots to display
        </div>
      );
    }
    
    const currentScreenshot = screenshots[selectedScreenshot];
    
    return (
      <div className="browser-screenshot-container">
        <div className="browser-screenshot-controls">
          <div className="browser-screenshot-navigation">
            <button 
              onClick={() => navigateScreenshots('prev')}
              disabled={selectedScreenshot === 0}
              className="browser-nav-button"
            >
              ←
            </button>
            
            <span className="browser-screenshot-counter">
              {selectedScreenshot + 1} / {screenshots.length}
            </span>
            
            <button 
              onClick={() => navigateScreenshots('next')}
              disabled={selectedScreenshot === screenshots.length - 1}
              className="browser-nav-button"
            >
              →
            </button>
          </div>
          
          <div className="browser-zoom-controls">
            <button 
              onClick={() => handleZoom('out')}
              className="browser-zoom-button"
              title="Zoom out"
            >
              -
            </button>
            
            <span className="browser-zoom-level">
              {Math.round(zoomLevel * 100)}%
            </span>
            
            <button 
              onClick={() => handleZoom('in')}
              className="browser-zoom-button"
              title="Zoom in"
            >
              +
            </button>
            
            <button 
              onClick={() => handleZoom('reset')}
              className="browser-zoom-button"
              title="Reset zoom"
            >
              ↺
            </button>
          </div>
        </div>
        
        <div className="browser-screenshot-viewer" style={{ transform: `scale(${zoomLevel})` }}>
          <img 
            src={currentScreenshot} 
            alt={`Screenshot ${selectedScreenshot + 1}`}
            className="browser-screenshot"
          />
        </div>
      </div>
    );
  };
  
  // Render raw output
  const renderRawOutput = () => {
    return (
      <pre className="browser-raw-output">
        {htmlContent}
      </pre>
    );
  };
  
  return (
    <div className="browser-output">
      <div className="browser-toolbar">
        <div className="browser-tabs">
          <button 
            className={`browser-tab ${activeTab === 'content' ? 'active' : ''}`}
            onClick={() => handleTabChange('content')}
          >
            HTML
          </button>
          
          <button 
            className={`browser-tab ${activeTab === 'screenshots' ? 'active' : ''}`}
            onClick={() => handleTabChange('screenshots')}
          >
            Screenshots
          </button>
          
          <button 
            className={`browser-tab ${activeTab === 'raw' ? 'active' : ''}`}
            onClick={() => handleTabChange('raw')}
          >
            Raw
          </button>
        </div>
        
        <div className="browser-status">
          {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
        </div>
      </div>
      
      <div className="browser-content">
        {activeTab === 'content' && renderHtmlContent()}
        {activeTab === 'screenshots' && renderScreenshots()}
        {activeTab === 'raw' && renderRawOutput()}
      </div>
    </div>
  );
};

export default BrowserOutput;
