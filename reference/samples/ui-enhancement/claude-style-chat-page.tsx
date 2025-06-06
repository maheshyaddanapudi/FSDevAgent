import React, { useState, useRef, useEffect } from 'react';
import { Send, Paperclip, Settings, Command, Sparkles } from 'lucide-react';
import EnhancedMessage from './EnhancedMessage';

const ClaudeStyleChatPage = () => {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [showToolPanel, setShowToolPanel] = useState(true);
  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [input]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!input.trim() || isProcessing) return;

    const userMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: input.trim(),
      timestamp: new Date().toISOString(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsProcessing(true);

    // Create assistant message placeholder
    const assistantMessageId = `assistant-${Date.now()}`;
    const assistantMessage = {
      id: assistantMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date().toISOString(),
      thinking: '',
      toolCalls: [],
      toolResults: [],
      isStreaming: true,
    };

    setMessages(prev => [...prev, assistantMessage]);

    // Set up SSE connection
    const eventSource = new EventSource(`/api/chat?sessionId=${sessionId}&message=${encodeURIComponent(input)}`);

    eventSource.addEventListener('thinking_chunk', (event) => {
      const data = JSON.parse(event.data);
      setMessages(prev => prev.map(msg => 
        msg.id === assistantMessageId 
          ? { ...msg, thinking: msg.thinking + data.content }
          : msg
      ));
    });

    eventSource.addEventListener('content_chunk', (event) => {
      const data = JSON.parse(event.data);
      setMessages(prev => prev.map(msg => 
        msg.id === assistantMessageId 
          ? { ...msg, content: msg.content + data.content }
          : msg
      ));
    });

    eventSource.addEventListener('tool_call_start', (event) => {
      const data = JSON.parse(event.data);
      setMessages(prev => prev.map(msg => 
        msg.id === assistantMessageId 
          ? { ...msg, toolCalls: [...msg.toolCalls, data.toolCall] }
          : msg
      ));
    });

    eventSource.addEventListener('tool_result_chunk', (event) => {
      const data = JSON.parse(event.data);
      setMessages(prev => prev.map(msg => {
        if (msg.id === assistantMessageId) {
          const resultIndex = msg.toolResults.findIndex(r => r.toolCallId === data.toolCallId);
          if (resultIndex >= 0) {
            const newResults = [...msg.toolResults];
            newResults[resultIndex] = {
              ...newResults[resultIndex],
              output: newResults[resultIndex].output + data.chunk
            };
            return { ...msg, toolResults: newResults };
          } else {
            return {
              ...msg,
              toolResults: [...msg.toolResults, {
                toolCallId: data.toolCallId,
                output: data.chunk
              }]
            };
          }
        }
        return msg;
      }));
    });

    eventSource.addEventListener('message_complete', () => {
      setMessages(prev => prev.map(msg => 
        msg.id === assistantMessageId 
          ? { ...msg, isStreaming: false }
          : msg
      ));
      setIsProcessing(false);
      eventSource.close();
    });

    eventSource.onerror = () => {
      setIsProcessing(false);
      eventSource.close();
    };
  };

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-900">
      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col">
        {/* Header */}
        <header className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-purple-500 to-purple-700 flex items-center justify-center">
                <Sparkles className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="font-semibold text-gray-900 dark:text-white">Claude</h1>
                <p className="text-xs text-gray-500">AI Developer Agent</p>
              </div>
            </div>
            <button
              onClick={() => setShowToolPanel(!showToolPanel)}
              className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              <Command className="w-5 h-5 text-gray-600 dark:text-gray-400" />
            </button>
          </div>
        </header>

        {/* Messages Area */}
        <div className="flex-1 overflow-y-auto">
          <div className="max-w-4xl mx-auto py-8 px-4">
            {messages.length === 0 ? (
              <div className="text-center py-12">
                <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
                  <Sparkles className="w-8 h-8 text-gray-400" />
                </div>
                <h2 className="text-xl font-medium text-gray-900 dark:text-white mb-2">
                  How can I help you today?
                </h2>
                <p className="text-gray-500">
                  I can write code, debug issues, and help with your development tasks.
                </p>
              </div>
            ) : (
              <>
                {messages.map((message) => (
                  <EnhancedMessage 
                    key={message.id} 
                    message={message} 
                    isStreaming={message.isStreaming}
                  />
                ))}
                <div ref={messagesEndRef} />
              </>
            )}
          </div>
        </div>

        {/* Input Area */}
        <div className="border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <form onSubmit={handleSubmit} className="max-w-4xl mx-auto p-4">
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSubmit(e);
                  }
                }}
                placeholder="Message Claude..."
                className="w-full px-4 py-3 pr-24 rounded-lg border border-gray-300 dark:border-gray-600 
                         bg-white dark:bg-gray-900 text-gray-900 dark:text-white 
                         placeholder-gray-500 dark:placeholder-gray-400 resize-none
                         focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                rows="1"
                disabled={isProcessing}
              />
              <div className="absolute bottom-3 right-3 flex items-center gap-2">
                <button
                  type="button"
                  className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <Paperclip className="w-4 h-4 text-gray-500" />
                </button>
                <button
                  type="submit"
                  disabled={!input.trim() || isProcessing}
                  className="p-2 rounded-lg bg-purple-600 text-white hover:bg-purple-700 
                           disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>
            </div>
            <div className="mt-2 text-xs text-gray-500 text-center">
              Claude can make mistakes. Please double-check responses.
            </div>
          </form>
        </div>
      </div>

      {/* Tool Panel */}
      {showToolPanel && (
        <div className="w-96 border-l border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <ToolPanel />
        </div>
      )}
    </div>
  );
};

// Tool Panel Component
const ToolPanel = () => {
  const [activeTools, setActiveTools] = useState([]);
  const [toolOutputs, setToolOutputs] = useState({});

  return (
    <div className="h-full flex flex-col">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <h2 className="font-semibold text-gray-900 dark:text-white">Active Tools</h2>
      </div>
      
      <div className="flex-1 overflow-y-auto">
        {activeTools.length === 0 ? (
          <div className="p-4 text-center text-gray-500">
            No tools active yet
          </div>
        ) : (
          <div className="p-4 space-y-4">
            {activeTools.map(tool => (
              <ToolVisualization 
                key={tool.id} 
                tool={tool} 
                output={toolOutputs[tool.id]}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

// Enhanced Tool Visualization
const ToolVisualization = ({ tool, output }) => {
  const [expanded, setExpanded] = useState(true);

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full p-3 bg-gray-50 dark:bg-gray-900 hover:bg-gray-100 dark:hover:bg-gray-800 
                   transition-colors flex items-center justify-between"
      >
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4" />
          <span className="font-medium">{tool.name}</span>
        </div>
        <ChevronRight 
          className={`w-4 h-4 transition-transform ${expanded ? 'rotate-90' : ''}`}
        />
      </button>
      
      {expanded && (
        <div className="p-3 bg-gray-900 text-gray-100 font-mono text-sm">
          <pre className="whitespace-pre-wrap">{output || 'Waiting for output...'}</pre>
        </div>
      )}
    </div>
  );
};

export default ClaudeStyleChatPage;