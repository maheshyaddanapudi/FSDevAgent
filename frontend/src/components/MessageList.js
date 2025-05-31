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

  return (
    <div className="message-list">
      {messages.map((message, index) => (
        <div 
          key={index} 
          className={`message ${message.role === 'user' ? 'user-message' : 'assistant-message'}`}
        >
          <div className="message-header">
            <div className="message-role">
              {message.role === 'user' ? 'You' : 'AI Developer'}
            </div>
          </div>
          <div className="message-content">
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
              {message.content}
            </ReactMarkdown>
            
            {message.toolCall && (
              <div className="tool-call">
                <div className="tool-call-header">
                  Tool Call: {message.toolCall.name}
                </div>
                <div className="tool-call-args">
                  <pre>{JSON.stringify(message.toolCall.arguments, null, 2)}</pre>
                </div>
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

export default MessageList;
