import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import '../styles/MessageList.css';

const MessageList = ({ messages }) => {
  const scrollRef = useRef(null);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

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
      <div ref={scrollRef} />
    </div>
  );
};

// Individual message component with collapsible sections
const MessageItem = ({ message, renderMarkdown }) => {
  const [expandedSections, setExpandedSections] = useState({
    thinking: false,
    toolCalls: {}
  });

  const toggleSection = (section, id = null) => {
    if (id) {
      // For tool calls with specific IDs
      setExpandedSections(prev => ({
        ...prev,
        toolCalls: {
          ...prev.toolCalls,
          [id]: !prev.toolCalls[id]
        }
      }));
    } else {
      // For other sections like thinking
      setExpandedSections(prev => ({
        ...prev,
        [section]: !prev[section]
      }));
    }
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
      'data_visualization': '📊',
      'web_search': '🔍',
      'code_execution': '💻',
      'file_access': '📁',
      'terminal': '⌨️'
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

  // Determine if we have multiple tool calls
  const hasMultipleToolCalls = Array.isArray(message.toolCalls) && message.toolCalls.length > 0;
  
  // Determine if we have a single tool call (legacy format)
  const hasSingleToolCall = message.toolCall && !hasMultipleToolCalls;

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
              <span className="claude-section-icon">🤔</span>
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
                <pre className="thinking-content">{message.thinking}</pre>
              </div>
            )}
          </div>
        )}
        
        {/* Multiple Tool Calls - New Format */}
        {hasMultipleToolCalls && message.toolCalls.map((toolCall, index) => {
          const toolCallId = toolCall.id || `tool-call-${index}`;
          const isExpanded = expandedSections.toolCalls[toolCallId] || false;
          const toolResult = message.toolResults && message.toolResults[index];
          const hasError = toolResult && (
            typeof toolResult === 'string' && 
            (toolResult.includes('error') || toolResult.includes('Error'))
          );
          
          return (
            <div 
              key={toolCallId} 
              className={`claude-section tool-call-section ${hasError ? 'error' : ''}`}
            >
              <button 
                className="claude-section-header"
                onClick={() => toggleSection('toolCalls', toolCallId)}
                aria-expanded={isExpanded}
              >
                <span className="claude-section-icon">{getToolIcon(toolCall.name)}</span>
                <span className="claude-section-title">Using {toolCall.name}</span>
                <span className="claude-section-preview">
                  {!isExpanded && getPreviewText(
                    toolCall.parameters || toolCall.arguments || {}
                  )}
                </span>
                <span className="claude-section-chevron">
                  {isExpanded ? '▼' : '▶'}
                </span>
              </button>
              {isExpanded && (
                <div className="claude-section-content">
                  <div className="tool-details">
                    <div className="tool-input">
                      <div className="code-block-header">
                        <span>Input:</span>
                        <button 
                          className="copy-button"
                          onClick={() => copyToClipboard(
                            JSON.stringify(toolCall.parameters || toolCall.arguments || {}, null, 2)
                          )}
                          title="Copy to clipboard"
                        >
                          📋 Copy
                        </button>
                      </div>
                      <pre className="code-block">
                        {JSON.stringify(toolCall.parameters || toolCall.arguments || {}, null, 2)}
                      </pre>
                    </div>
                    
                    {toolResult && (
                      <div className="tool-output">
                        <div className="code-block-header">
                          <span>Result:</span>
                          <button 
                            className="copy-button"
                            onClick={() => copyToClipboard(
                              typeof toolResult === 'string' 
                                ? toolResult 
                                : JSON.stringify(toolResult, null, 2)
                            )}
                            title="Copy to clipboard"
                          >
                            📋 Copy
                          </button>
                        </div>
                        {typeof toolResult === 'string' ? (
                          toolResult.startsWith('```') ? (
                            renderMarkdown(toolResult)
                          ) : (
                            <pre className="execution-log">{toolResult}</pre>
                          )
                        ) : (
                          <pre className="code-block">
                            {JSON.stringify(toolResult, null, 2)}
                          </pre>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}
        
        {/* Single Tool Call - Legacy Format */}
        {hasSingleToolCall && (
          <div className="claude-section tool-call-section">
            <button 
              className="claude-section-header"
              onClick={() => toggleSection('toolCall')}
              aria-expanded={expandedSections.toolCall}
            >
              <span className="claude-section-icon">{getToolIcon(message.toolCall.name)}</span>
              <span className="claude-section-title">Using {message.toolCall.name}</span>
              <span className="claude-section-preview">
                {!expandedSections.toolCall && getPreviewText(JSON.stringify(message.toolCall.arguments))}
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
                        onClick={() => copyToClipboard(JSON.stringify(message.toolCall.arguments, null, 2))}
                        title="Copy to clipboard"
                      >
                        📋 Copy
                      </button>
                    </div>
                    <pre className="code-block">{JSON.stringify(message.toolCall.arguments, null, 2)}</pre>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
        
        {/* Tool Result section - For legacy format */}
        {message.toolResult && !hasMultipleToolCalls && (
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
