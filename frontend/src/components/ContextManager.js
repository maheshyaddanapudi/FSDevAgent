import React, { useState, useRef, useEffect } from 'react';
import useChatStore from '../hooks/useChatStore';
import '../styles/ContextManager.css';

/**
 * ContextManager component for managing conversation context
 * Provides UI for viewing and managing conversation context
 */
const ContextManager = () => {
  const { messages, sessionId } = useChatStore();
  const [isExpanded, setIsExpanded] = useState(false);
  const [contextSummary, setContextSummary] = useState('');
  const [referencesFound, setReferencesFound] = useState([]);
  const contextRef = useRef(null);

  // Calculate context summary whenever messages change
  useEffect(() => {
    if (messages.length === 0) {
      setContextSummary('No conversation history');
      setReferencesFound([]);
      return;
    }

    // Count messages by role
    const userMessages = messages.filter(msg => msg.role === 'user').length;
    const assistantMessages = messages.filter(msg => msg.role === 'assistant').length;
    const toolMessages = messages.filter(msg => msg.role === 'tool').length;

    // Generate summary
    setContextSummary(`${messages.length} messages (${userMessages} user, ${assistantMessages} assistant, ${toolMessages} tool)`);

    // Find potential references in the conversation
    const references = findReferences(messages);
    setReferencesFound(references);
  }, [messages]);

  // Find potential references in the conversation
  const findReferences = (messages) => {
    const references = [];
    const mentionedEntities = new Map();

    // Regular expressions for finding entities
    const codeRegex = /```([a-z]*)\n([\s\S]*?)```/g;
    const fileRegex = /([a-zA-Z0-9_\-]+\.[a-zA-Z0-9]+)/g;
    const conceptRegex = /\b([A-Z][a-z]+(?:[A-Z][a-z]+)+)\b/g;

    // Process each message to find entities
    messages.forEach((message, index) => {
      if (!message.content) return;

      // Find code blocks
      let match;
      while ((match = codeRegex.exec(message.content)) !== null) {
        const language = match[1] || 'code';
        const code = match[2];
        const key = `code-${language}-${code.substring(0, 20)}`;
        
        if (!mentionedEntities.has(key)) {
          mentionedEntities.set(key, {
            type: 'code',
            language,
            snippet: code.length > 50 ? code.substring(0, 50) + '...' : code,
            messageIndex: index
          });
        }
      }

      // Find file references
      while ((match = fileRegex.exec(message.content)) !== null) {
        const filename = match[1];
        if (!mentionedEntities.has(filename)) {
          mentionedEntities.set(filename, {
            type: 'file',
            name: filename,
            messageIndex: index
          });
        }
      }

      // Find concept references (CamelCase words)
      while ((match = conceptRegex.exec(message.content)) !== null) {
        const concept = match[1];
        if (!mentionedEntities.has(concept)) {
          mentionedEntities.set(concept, {
            type: 'concept',
            name: concept,
            messageIndex: index
          });
        }
      }
    });

    // Convert map to array
    return Array.from(mentionedEntities.values());
  };

  // Toggle expanded state
  const toggleExpanded = () => {
    setIsExpanded(!isExpanded);
  };

  // Jump to reference in conversation
  const jumpToReference = (messageIndex) => {
    const messageElement = document.querySelector(`[data-message-index="${messageIndex}"]`);
    if (messageElement) {
      messageElement.scrollIntoView({ behavior: 'smooth' });
      messageElement.classList.add('highlight-reference');
      setTimeout(() => {
        messageElement.classList.remove('highlight-reference');
      }, 2000);
    }
  };

  return (
    <div className={`context-manager ${isExpanded ? 'expanded' : 'collapsed'}`}>
      <div className="context-header" onClick={toggleExpanded}>
        <h3>Conversation Context</h3>
        <div className="context-summary">{contextSummary}</div>
        <button className="toggle-button">
          {isExpanded ? '▼' : '▲'}
        </button>
      </div>
      
      {isExpanded && (
        <div className="context-content" ref={contextRef}>
          <div className="session-info">
            <div className="session-id">
              <strong>Session ID:</strong> {sessionId || 'No active session'}
            </div>
          </div>
          
          {referencesFound.length > 0 && (
            <div className="references-section">
              <h4>References Found</h4>
              <ul className="references-list">
                {referencesFound.map((ref, index) => (
                  <li key={index} className={`reference-item ${ref.type}`}>
                    <span className="reference-type">{ref.type}</span>
                    <span className="reference-name">
                      {ref.type === 'code' ? `${ref.language}: ${ref.snippet}` : ref.name}
                    </span>
                    <button 
                      className="jump-button"
                      onClick={() => jumpToReference(ref.messageIndex)}
                    >
                      Jump
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          
          <div className="message-count">
            <strong>Message Count:</strong> {messages.length}
          </div>
        </div>
      )}
    </div>
  );
};

export default ContextManager;
