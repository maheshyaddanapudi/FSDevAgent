import React from 'react';
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
        <div 
          key={index} 
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
            
            {/* Thinking section - AI reasoning */}
            {message.thinking && (
              <div className="thinking-block">
                <div className="thinking-header">
                  <span className="thinking-icon">💭</span> Thinking Process
                </div>
                <div className="thinking-content">
                  {renderMarkdown(message.thinking)}
                </div>
              </div>
            )}
            
            {/* Tool Call section - what tool is being used */}
            {message.toolCall && (
              <div className="tool-call">
                <div className="tool-call-header">
                  <span className="tool-icon">🛠️</span> Using Tool: {message.toolCall.name}
                </div>
                <div className="tool-call-args">
                  <pre>{JSON.stringify(message.toolCall.arguments, null, 2)}</pre>
                </div>
              </div>
            )}
            
            {/* Tool Execution section - showing the execution process */}
            {message.toolExecution && (
              <div className="tool-execution">
                <div className="tool-execution-header">
                  <span className="tool-execution-icon">⚙️</span> Tool Execution
                </div>
                <div className="tool-execution-content">
                  {typeof message.toolExecution === 'string' ? (
                    <pre>{message.toolExecution}</pre>
                  ) : (
                    <pre>{JSON.stringify(message.toolExecution, null, 2)}</pre>
                  )}
                </div>
              </div>
            )}
            
            {/* Tool Result section - output from the tool */}
            {message.toolResult && (
              <div className="tool-result">
                <div className="tool-result-header">
                  <span className="tool-result-icon">✅</span> Tool Result
                </div>
                <div className="tool-result-content">
                  {typeof message.toolResult === 'string' ? (
                    renderMarkdown(message.toolResult)
                  ) : (
                    <pre>{JSON.stringify(message.toolResult, null, 2)}</pre>
                  )}
                </div>
              </div>
            )}
            
            {/* Loading indicator for in-progress tool operations */}
            {message.loading && (
              <div className="loading-indicator">
                Processing request...
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

export default MessageList;
