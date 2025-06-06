import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import '../styles/MessageList.css';

const MessageList = ({ messages }) => {
  if (!messages || messages.length === 0) {
    return (
      <div className="message-list empty">
        <div className="empty-state">
          <h2>Welcome to AI Developer Agent</h2>
          <p>Ask me anything about development, and I'll help you with coding, debugging, and using various tools.</p>
        </div>
      </div>
    );
  }

  // Helper function to render markdown content
  const renderMarkdown = (content) => {
    return (
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({node, inline, className, children, ...props}) {
            const match = /language-(\w+)/.exec(className || '');
            return !inline && match ? (
              <SyntaxHighlighter
                style={vscDarkPlus}
                language={match[1]}
                PreTag="div"
                {...props}
              >
                {String(children).replace(/\n$/, '')}
              </SyntaxHighlighter>
            ) : (
              <code className={className} {...props}>
                {children}
              </code>
            );
          }
        }}
      >
        {content}
      </ReactMarkdown>
    );
  };

  return (
    <div className="message-list">
      {messages.map((message, index) => (
        <MessageItem 
          key={index}
          message={message}
          renderMarkdown={renderMarkdown}
        />
      ))}
    </div>
  );
};

// Individual message component with collapsible sections
const MessageItem = ({ message, renderMarkdown }) => {
  const [expandedSections, setExpandedSections] = useState({
    thinking: false,
    toolCall: false,
    toolExecution: false,
    toolResult: message.toolResult?.includes('error') || message.toolResult?.includes('Error') || false
  });

  const toggleSection = (section) => {
    setExpandedSections(prev => ({
      ...prev,
      [section]: !prev[section]
    }));
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    // You could add a toast notification here
  };

  // Helper to get tool icon based on tool name
  const getToolIcon = (toolName) => {
    const icons = {
      'file_system': '📁',
      'execute_command': '⌨️',
      'browser_automation': '🌐',
      'git_operations': '🔧',
      'build_tool': '🏗️',
      'code_intelligence': '🧠',
      'data_visualization': '📊'
    };
    return icons[toolName] || '🛠️';
  };

  // Helper to get preview text
  const getPreviewText = (content, maxLength = 100) => {
    if (!content) return '';
    const text = typeof content === 'string' ? content : JSON.stringify(content);
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
  };

  return (
    <div 
      className={`message ${message.role === 'user' ? 'user-message' : 
                          message.role === 'tool' ? 'tool-message' : 
                          message.role === 'system' ? 'system-message' : 
                          'assistant-message'}`}
    >
      <div className="message-header">
        <div className="message-role">
          {message.role === 'user' ? 'You' : 
           message.role === 'tool' ? 'Tool Result' : 
           message.role === 'system' ? 'System' : 
           'AI Developer'}
        </div>
      </div>
      <div className="message-content">
        {/* Main message content */}
        {message.content && renderMarkdown(message.content)}
        
        {/* Thinking section - Collapsible */}
        {message.thinking && (
          <div className="claude-section thinking-section">
            <button 
              className="claude-section-header"
              onClick={() => toggleSection('thinking')}
              aria-expanded={expandedSections.thinking}
            >
              <span className="claude-section-icon">💭</span>
              <span className="claude-section-title">Thinking</span>
              <span className="claude-section-preview">
                {!expandedSections.thinking && getPreviewText(message.thinking)}
              </span>
              <span className="claude-section-chevron">
                {expandedSections.thinking ? '▼' : '▶'}
              </span>
            </button>
            {expandedSections.thinking && (
              <div className="claude-section-content">
                {renderMarkdown(message.thinking)}
              </div>
            )}
          </div>
        )}
        
        {/* Tool Call section - Collapsible */}
        {message.toolCall && (
          <div className="claude-section tool-call-section">
            <button 
              className="claude-section-header"
              onClick={() => toggleSection('toolCall')}
              aria-expanded={expandedSections.toolCall}
            >
              <span className="claude-section-icon">{getToolIcon(message.toolCall.name)}</span>
              <span className="claude-section-title">Using {message.toolCall.name}</span>
              <span className="claude-section-preview">
                {!expandedSections.toolCall && getPreviewText(JSON.stringify(message.toolCall.args))}
              </span>
              <span className="claude-section-chevron">
                {expandedSections.toolCall ? '▼' : '▶'}
              </span>
            </button>
            {expandedSections.toolCall && (
              <div className="claude-section-content">
                <div className="tool-call-details">
                  <div className="tool-call-arguments">
                    <div className="code-block-header">
                      <span>Arguments</span>
                      <button 
                        className="copy-button"
                        onClick={() => copyToClipboard(JSON.stringify(message.toolCall.args, null, 2))}
                        title="Copy to clipboard"
                      >
                        📋 Copy
                      </button>
                    </div>
                    <pre className="code-block">{JSON.stringify(message.toolCall.args, null, 2)}</pre>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
        
        {/* Tool Execution section - Shows progress/status */}
        {message.toolExecution && (
          <div className="claude-section tool-execution-section">
            <button 
              className="claude-section-header"
              onClick={() => toggleSection('toolExecution')}
              aria-expanded={expandedSections.toolExecution}
            >
              <span className="claude-section-icon">⚙️</span>
              <span className="claude-section-title">Executing Tool</span>
              <span className="claude-section-status executing">
                <span className="status-dot"></span>
                Running
              </span>
              <span className="claude-section-chevron">
                {expandedSections.toolExecution ? '▼' : '▶'}
              </span>
            </button>
            {expandedSections.toolExecution && (
              <div className="claude-section-content">
                <div className="execution-output">
                  {typeof message.toolExecution === 'string' ? (
                    <pre className="execution-log">{message.toolExecution}</pre>
                  ) : (
                    <pre className="execution-log">{JSON.stringify(message.toolExecution, null, 2)}</pre>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
        
        {/* Tool Result section - Auto-expanded for errors */}
        {message.toolResult && (
          <div className={`claude-section tool-result-section ${
            message.toolResult.includes('error') || message.toolResult.includes('Error') ? 'error' : 'success'
          }`}>
            <button 
              className="claude-section-header"
              onClick={() => toggleSection('toolResult')}
              aria-expanded={expandedSections.toolResult}
            >
              <span className="claude-section-icon">
                {message.toolResult.includes('error') || message.toolResult.includes('Error') ? '❌' : '✅'}
              </span>
              <span className="claude-section-title">Tool Result</span>
              <span className="claude-section-preview">
                {!expandedSections.toolResult && getPreviewText(message.toolResult)}
              </span>
              <span className="claude-section-chevron">
                {expandedSections.toolResult ? '▼' : '▶'}
              </span>
            </button>
            {expandedSections.toolResult && (
              <div className="claude-section-content">
                <div className="tool-result-content">
                  {/* Check if result contains code or structured data */}
                  {(message.toolResult.includes('{') || 
                    message.toolResult.includes('[') || 
                    message.toolResult.includes('```')) ? (
                    <div className="code-result">
                      <div className="code-block-header">
                        <span>Output</span>
                        <button 
                          className="copy-button"
                          onClick={() => copyToClipboard(message.toolResult)}
                          title="Copy to clipboard"
                        >
                          📋 Copy
                        </button>
                      </div>
                      {renderMarkdown(message.toolResult)}
                    </div>
                  ) : (
                    renderMarkdown(message.toolResult)
                  )}
                </div>
              </div>
            )}
          </div>
        )}
        
        {/* Loading indicator for in-progress operations */}
        {message.loading && (
          <div className="claude-loading">
            <div className="loading-spinner"></div>
            <span>Processing request...</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default MessageList;
