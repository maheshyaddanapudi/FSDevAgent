import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { ChevronRight, ChevronDown, Copy, Check, Zap, Code, Terminal, AlertCircle, CheckCircle } from 'react-feather';

const EnhancedMessageList = ({ messages, isStreaming }) => {
  return (
    <div className="message-list">
      {messages.map((message, index) => (
        <EnhancedMessage 
          key={index} 
          message={message} 
          isStreaming={isStreaming && index === messages.length - 1} 
        />
      ))}
    </div>
  );
};

const EnhancedMessage = ({ message, isStreaming }) => {
  const [expandedSections, setExpandedSections] = useState({});
  const [copiedStates, setCopiedStates] = useState({});
  
  // Generate stable IDs using useMemo to prevent re-renders
  const stableIds = useMemo(() => ({
    toolId: `tool-${message.id || 'unknown'}`,
    resultId: `result-${message.id || 'unknown'}`,
    codeId: `code-${message.id || 'unknown'}`,
    argsId: `args-${message.id || 'unknown'}`
  }), [message.id]);
  
  // Auto-expand errors and important results
  useEffect(() => {
    if (message.toolCall) {
      setExpandedSections(prev => ({ ...prev, [stableIds.toolId]: true }));
    }
  }, [message.toolCall, stableIds.toolId]);

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
                onClick={() => copyToClipboard(String(children), stableIds.codeId)}
                className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
              >
                {copiedStates[stableIds.codeId] ? 
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

  // Detect if message contains tool call
  const hasToolCall = message.toolCall && Object.keys(message.toolCall).length > 0;
  
  // Extract tool name and args if available
  const toolName = hasToolCall ? message.toolCall.toolName : null;
  const toolArgs = hasToolCall ? message.toolCall.args : null;

  return (
    <div className={`message-container ${message.role} ${isStreaming ? 'streaming' : ''}`}>
      {/* Message Header */}
      <div className="message-header">
        <div className="flex items-center gap-2">
          {message.role === 'assistant' ? (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-purple-500 to-purple-700 flex items-center justify-center">
              <Zap className="w-4 h-4 text-white" />
            </div>
          ) : message.role === 'tool' ? (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center">
              <Terminal className="w-4 h-4 text-white" />
            </div>
          ) : (
            <div className="w-8 h-8 rounded-full bg-gray-300 dark:bg-gray-600" />
          )}
          <span className="font-medium">
            {message.role === 'assistant' ? 'Claude' : 
             message.role === 'tool' ? (toolName || 'Tool') : 'You'}
          </span>
          <span className="text-xs text-gray-500">
            {new Date(message.timestamp).toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* Main Content */}
      <div className="message-content">
        {/* Regular message content */}
        {message.message && message.role !== 'tool' && (
          <div className="prose dark:prose-invert max-w-none">
            {renderMarkdown(message.message)}
          </div>
        )}

        {/* Tool Call Section */}
        {hasToolCall && (
          <CollapsibleSection
            id={stableIds.toolId}
            title={`Using ${toolName}`}
            icon={<Terminal className="w-4 h-4" />}
            expanded={expandedSections[stableIds.toolId]}
            onToggle={() => toggleSection(stableIds.toolId)}
            preview={!expandedSections[stableIds.toolId] ? 
              JSON.stringify(toolArgs).substring(0, 80) + '...' : ''}
          >
            <div className="space-y-2">
              <div className="bg-gray-900 rounded-lg p-3 relative">
                <div className="flex justify-between items-center mb-2">
                  <span className="text-xs text-gray-400">Arguments</span>
                  <button
                    onClick={() => copyToClipboard(JSON.stringify(toolArgs, null, 2), stableIds.argsId)}
                    className="text-xs text-gray-400 hover:text-white transition-colors"
                  >
                    {copiedStates[stableIds.argsId] ? 
                      <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                  </button>
                </div>
                <pre className="text-xs overflow-x-auto">
                  <code>{JSON.stringify(toolArgs, null, 2)}</code>
                </pre>
              </div>
            </div>
          </CollapsibleSection>
        )}

        {/* Tool Result Section */}
        {message.role === 'tool' && (
          <CollapsibleSection
            id={stableIds.resultId}
            title={`${toolName || 'Tool'} Result`}
            icon={message.message && message.message.includes('Error') ? 
              <AlertCircle className="w-4 h-4 text-red-500" /> : 
              <CheckCircle className="w-4 h-4 text-green-500" />}
            expanded={expandedSections[stableIds.resultId]}
            onToggle={() => toggleSection(stableIds.resultId)}
            status={message.message && message.message.includes('Error') ? 'error' : 'success'}
            preview={!expandedSections[stableIds.resultId] ? 
              (message.message || '').substring(0, 80) + '...' : ''}
          >
            <div className="space-y-2">
              {message.message && message.message.includes('Error') ? (
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3">
                  <pre className="text-sm text-red-700 dark:text-red-300 whitespace-pre-wrap">
                    {message.message}
                  </pre>
                </div>
              ) : (
                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-3">
                  {/* Detect if output is code and apply syntax highlighting */}
                  {message.message && (message.message.startsWith('```') || 
                     /^\s*[{[]/.test(message.message) || 
                     /^\s*<\?xml/.test(message.message) ||
                     /^\s*<!DOCTYPE/.test(message.message)) ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={detectLanguage(message.message)}
                      PreTag="div"
                    >
                      {cleanCodeBlock(message.message)}
                    </SyntaxHighlighter>
                  ) : (
                    <pre className="text-sm whitespace-pre-wrap">{message.message}</pre>
                  )}
                </div>
              )}
            </div>
          </CollapsibleSection>
        )}

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

// Helper function to detect language for syntax highlighting
const detectLanguage = (content) => {
  if (!content) return 'text';
  
  // Check for markdown code blocks with language specification
  const codeBlockMatch = content.match(/```(\w+)/);
  if (codeBlockMatch && codeBlockMatch[1]) {
    return codeBlockMatch[1];
  }
  
  // Try to detect based on content patterns
  if (/^\s*[{[]/.test(content)) {
    // Check if it's valid JSON
    try {
      JSON.parse(content.trim());
      return 'json';
    } catch (e) {
      // Not valid JSON
    }
  }
  
  if (/^\s*<\?xml/.test(content)) return 'xml';
  if (/^\s*<!DOCTYPE html>|<html>|<head>|<body>/.test(content)) return 'html';
  if (/^\s*(import|package|public class|@Override)/.test(content)) return 'java';
  if (/^\s*(def|import|from|class|if __name__)/.test(content)) return 'python';
  if (/^\s*(function|const|let|var|import|export)/.test(content)) return 'javascript';
  if (/^\s*(#include|int main|void|namespace|std::)/.test(content)) return 'cpp';
  
  // Terminal output often has file listings, paths, etc.
  if (/^\s*(total|drwx|ls:|cd |cat |mkdir|touch)/.test(content)) return 'bash';
  
  return 'text';
};

// Helper function to clean code blocks for syntax highlighting
const cleanCodeBlock = (content) => {
  if (!content) return '';
  
  // Remove markdown code block markers
  return content.replace(/```\w*\n?/, '').replace(/```$/, '');
};

export default EnhancedMessageList;
