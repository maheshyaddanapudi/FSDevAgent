import React, { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { ChevronRight, ChevronDown, Copy, Check, Zap, Code, Terminal, AlertCircle, CheckCircle } from 'lucide-react';

const EnhancedMessage = ({ message, isStreaming }) => {
  const [expandedSections, setExpandedSections] = useState({});
  const [copiedStates, setCopiedStates] = useState({});
  
  // Auto-expand errors and important results
  useEffect(() => {
    if (message.toolResults) {
      message.toolResults.forEach((result, idx) => {
        if (result.error || result.important) {
          setExpandedSections(prev => ({ ...prev, [`result-${idx}`]: true }));
        }
      });
    }
  }, [message.toolResults]);

  const toggleSection = (sectionId) => {
    setExpandedSections(prev => ({ ...prev, [sectionId]: !prev[sectionId] }));
  };

  const copyToClipboard = async (text, id) => {
    await navigator.clipboard.writeText(text);
    setCopiedStates(prev => ({ ...prev, [id]: true }));
    setTimeout(() => {
      setCopiedStates(prev => ({ ...prev, [id]: false }));
    }, 2000);
  };

  const renderMarkdown = (content) => (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        code({node, inline, className, children, ...props}) {
          const match = /language-(\w+)/.exec(className || '');
          return !inline && match ? (
            <div className="relative group">
              <SyntaxHighlighter
                style={vscDarkPlus}
                language={match[1]}
                PreTag="div"
                {...props}
              >
                {String(children).replace(/\n$/, '')}
              </SyntaxHighlighter>
              <button
                onClick={() => copyToClipboard(String(children), `code-${Math.random()}`)}
                className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
              >
                {copiedStates[`code-${Math.random()}`] ? 
                  <Check className="w-4 h-4 text-green-500" /> : 
                  <Copy className="w-4 h-4 text-gray-400" />
                }
              </button>
            </div>
          ) : (
            <code className="bg-gray-100 dark:bg-gray-800 px-1 py-0.5 rounded text-sm" {...props}>
              {children}
            </code>
          );
        }
      }}
    >
      {content}
    </ReactMarkdown>
  );

  return (
    <div className={`message-container ${message.role} ${isStreaming ? 'streaming' : ''}`}>
      {/* Message Header */}
      <div className="message-header">
        <div className="flex items-center gap-2">
          {message.role === 'assistant' ? (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-purple-500 to-purple-700 flex items-center justify-center">
              <Zap className="w-4 h-4 text-white" />
            </div>
          ) : (
            <div className="w-8 h-8 rounded-full bg-gray-300 dark:bg-gray-600" />
          )}
          <span className="font-medium">
            {message.role === 'assistant' ? 'Claude' : 'You'}
          </span>
          <span className="text-xs text-gray-500">
            {new Date(message.timestamp).toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* Main Content */}
      <div className="message-content">
        {message.content && (
          <div className="prose dark:prose-invert max-w-none">
            {renderMarkdown(message.content)}
          </div>
        )}

        {/* Thinking Section */}
        {message.thinking && (
          <CollapsibleSection
            id="thinking"
            title="Thinking"
            icon={<div className="animate-pulse">💭</div>}
            expanded={expandedSections.thinking}
            onToggle={() => toggleSection('thinking')}
            preview={!expandedSections.thinking ? message.thinking.substring(0, 100) + '...' : ''}
          >
            <div className="text-gray-600 dark:text-gray-400 italic">
              {renderMarkdown(message.thinking)}
            </div>
          </CollapsibleSection>
        )}

        {/* Tool Calls */}
        {message.toolCalls && message.toolCalls.map((toolCall, idx) => (
          <CollapsibleSection
            key={`tool-${idx}`}
            id={`tool-${idx}`}
            title={`Using ${toolCall.name}`}
            icon={<Terminal className="w-4 h-4" />}
            expanded={expandedSections[`tool-${idx}`]}
            onToggle={() => toggleSection(`tool-${idx}`)}
            status={toolCall.status}
            preview={!expandedSections[`tool-${idx}`] ? JSON.stringify(toolCall.args).substring(0, 80) + '...' : ''}
          >
            <div className="space-y-2">
              <div className="bg-gray-900 rounded-lg p-3 relative">
                <div className="flex justify-between items-center mb-2">
                  <span className="text-xs text-gray-400">Arguments</span>
                  <button
                    onClick={() => copyToClipboard(JSON.stringify(toolCall.args, null, 2), `args-${idx}`)}
                    className="text-xs text-gray-400 hover:text-white transition-colors"
                  >
                    {copiedStates[`args-${idx}`] ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                  </button>
                </div>
                <pre className="text-xs overflow-x-auto">
                  <code>{JSON.stringify(toolCall.args, null, 2)}</code>
                </pre>
              </div>
            </div>
          </CollapsibleSection>
        ))}

        {/* Tool Results */}
        {message.toolResults && message.toolResults.map((result, idx) => (
          <CollapsibleSection
            key={`result-${idx}`}
            id={`result-${idx}`}
            title={`${result.toolName} Result`}
            icon={result.error ? <AlertCircle className="w-4 h-4 text-red-500" /> : <CheckCircle className="w-4 h-4 text-green-500" />}
            expanded={expandedSections[`result-${idx}`]}
            onToggle={() => toggleSection(`result-${idx}`)}
            status={result.error ? 'error' : 'success'}
            preview={!expandedSections[`result-${idx}`] ? (result.output || '').substring(0, 80) + '...' : ''}
          >
            <div className="space-y-2">
              {result.error ? (
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
                  <pre className="text-sm text-red-700 dark:text-red-300 whitespace-pre-wrap">
                    {result.error}
                  </pre>
                </div>
              ) : (
                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-3">
                  {result.outputType === 'code' ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={result.language || 'text'}
                      PreTag="div"
                    >
                      {result.output}
                    </SyntaxHighlighter>
                  ) : (
                    <pre className="text-sm whitespace-pre-wrap">{result.output}</pre>
                  )}
                </div>
              )}
            </div>
          </CollapsibleSection>
        ))}

        {/* Streaming Indicator */}
        {isStreaming && (
          <div className="flex items-center gap-2 mt-3 text-gray-500">
            <div className="flex gap-1">
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
            <span className="text-sm">Claude is thinking...</span>
          </div>
        )}
      </div>
    </div>
  );
};

const CollapsibleSection = ({ 
  id, 
  title, 
  icon, 
  expanded, 
  onToggle, 
  children, 
  status, 
  preview 
}) => {
  const contentRef = useRef(null);
  const [height, setHeight] = useState(0);

  useEffect(() => {
    if (contentRef.current) {
      setHeight(expanded ? contentRef.current.scrollHeight : 0);
    }
  }, [expanded, children]);

  return (
    <div className="collapsible-section mt-3">
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-2 p-3 bg-gray-50 dark:bg-gray-800 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-all"
      >
        <span className="transition-transform duration-200" style={{ transform: expanded ? 'rotate(90deg)' : 'rotate(0deg)' }}>
          <ChevronRight className="w-4 h-4" />
        </span>
        {icon}
        <span className="font-medium text-sm">{title}</span>
        {status && (
          <span className={`ml-auto text-xs px-2 py-1 rounded-full ${
            status === 'executing' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' :
            status === 'error' ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300' :
            'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
          }`}>
            {status}
          </span>
        )}
        {preview && !expanded && (
          <span className="ml-2 text-xs text-gray-500 truncate flex-1 text-left">
            {preview}
          </span>
        )}
      </button>
      
      <div 
        className="overflow-hidden transition-all duration-300 ease-in-out"
        style={{ height: `${height}px` }}
      >
        <div ref={contentRef} className="p-3 pt-0">
          {children}
        </div>
      </div>
    </div>
  );
};

export default EnhancedMessage;