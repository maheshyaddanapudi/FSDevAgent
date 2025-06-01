// frontend/src/components/UnifiedEmulator/tools/CodeOutput.js
import React, { useState, useEffect } from 'react';
import { useEmulatorStore } from '../../../store/emulatorStore';
import './CodeOutput.css';

/**
 * Code output visualization component
 * Renders code analysis results with syntax highlighting
 */
const CodeOutput = ({ data, allOutputs, wsConnected, toolId }) => {
  const [codeContent, setCodeContent] = useState('');
  const [language, setLanguage] = useState('javascript');
  const [analysisResults, setAnalysisResults] = useState([]);
  const [activeTab, setActiveTab] = useState('code');
  
  const { updateToolActivity } = useEmulatorStore();
  
  // Update tool activity
  useEffect(() => {
    if (data) {
      updateToolActivity(toolId);
    }
  }, [data, toolId, updateToolActivity]);
  
  // Process code output
  useEffect(() => {
    if (!data) return;
    
    const output = data.output || data.data || data.content || '';
    
    // Try to detect if this is code content or analysis results
    if (typeof output === 'object') {
      // This might be analysis results
      if (output.code) {
        setCodeContent(output.code);
        setLanguage(detectLanguage(output.code));
      }
      
      if (output.analysis || output.results) {
        setAnalysisResults(output.analysis || output.results);
        setActiveTab('analysis');
      }
    } else if (typeof output === 'string') {
      // Try to detect if this is code
      if (isLikelyCode(output)) {
        setCodeContent(output);
        setLanguage(detectLanguage(output));
        setActiveTab('code');
      } else {
        // Treat as analysis text
        setAnalysisResults([{ message: output, type: 'info' }]);
        setActiveTab('analysis');
      }
    }
  }, [data]);
  
  // Detect if string is likely code
  const isLikelyCode = (text) => {
    // Check for common code patterns
    return (
      text.includes('function') ||
      text.includes('class') ||
      text.includes('import ') ||
      text.includes('export ') ||
      text.includes('const ') ||
      text.includes('let ') ||
      text.includes('var ') ||
      text.includes('if (') ||
      text.includes('for (') ||
      text.includes('while (') ||
      text.includes('{') && text.includes('}') ||
      text.includes('<') && text.includes('>')
    );
  };
  
  // Detect language from code content
  const detectLanguage = (code) => {
    if (code.includes('import React') || code.includes('const [') || code.includes('useState')) {
      return 'javascript';
    } else if (code.includes('public class') || code.includes('private void')) {
      return 'java';
    } else if (code.includes('def ') && code.includes(':')) {
      return 'python';
    } else if (code.includes('#include')) {
      return 'cpp';
    } else if (code.includes('<?php')) {
      return 'php';
    } else if (code.includes('<html') || code.includes('<!DOCTYPE')) {
      return 'html';
    } else if (code.includes('@media') || code.includes('.class {')) {
      return 'css';
    } else {
      return 'javascript'; // Default
    }
  };
  
  // Handle tab change
  const handleTabChange = (tab) => {
    setActiveTab(tab);
  };
  
  // Render code with syntax highlighting
  const renderCode = () => {
    if (!codeContent) {
      return (
        <div className="code-empty-content">
          No code to display
        </div>
      );
    }
    
    return (
      <pre className={`code-block language-${language}`}>
        <code>{codeContent}</code>
      </pre>
    );
  };
  
  // Render analysis results
  const renderAnalysis = () => {
    if (!analysisResults || analysisResults.length === 0) {
      return (
        <div className="code-empty-content">
          No analysis results to display
        </div>
      );
    }
    
    if (Array.isArray(analysisResults)) {
      return (
        <div className="code-analysis-list">
          {analysisResults.map((result, index) => (
            <div 
              key={index} 
              className={`code-analysis-item code-analysis-${result.type || 'info'}`}
            >
              <div className="code-analysis-header">
                <span className="code-analysis-type">
                  {result.type || 'Info'}
                </span>
                {result.location && (
                  <span className="code-analysis-location">
                    {result.location}
                  </span>
                )}
              </div>
              <div className="code-analysis-message">
                {result.message}
              </div>
              {result.suggestion && (
                <div className="code-analysis-suggestion">
                  Suggestion: {result.suggestion}
                </div>
              )}
            </div>
          ))}
        </div>
      );
    } else {
      // If it's not an array, just display as text
      return (
        <pre className="code-analysis-raw">
          {typeof analysisResults === 'string' 
            ? analysisResults 
            : JSON.stringify(analysisResults, null, 2)}
        </pre>
      );
    }
  };
  
  return (
    <div className="code-output">
      <div className="code-toolbar">
        <div className="code-tabs">
          <button 
            className={`code-tab ${activeTab === 'code' ? 'active' : ''}`}
            onClick={() => handleTabChange('code')}
          >
            Code
          </button>
          
          <button 
            className={`code-tab ${activeTab === 'analysis' ? 'active' : ''}`}
            onClick={() => handleTabChange('analysis')}
          >
            Analysis
          </button>
        </div>
        
        <div className="code-info">
          <span className="code-language">
            {language.charAt(0).toUpperCase() + language.slice(1)}
          </span>
          
          <span className="code-status">
            {wsConnected ? '🟢 Connected' : '🔴 Disconnected'}
          </span>
        </div>
      </div>
      
      <div className="code-content">
        {activeTab === 'code' ? renderCode() : renderAnalysis()}
      </div>
    </div>
  );
};

export default CodeOutput;
