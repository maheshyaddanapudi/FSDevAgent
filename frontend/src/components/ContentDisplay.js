import React, { useState } from 'react';
import { formatToolArgs } from '../utils/contentParser';
import '../styles/ContentDisplay.css';

/**
 * Main Content Display Component
 * Renders parsed agent content with different sections
 */
export const ContentDisplay = ({ parsedContent }) => {
  if (!parsedContent) return null;

  const { mainResponse, sections } = parsedContent;

  return (
    <div className="content-display">
      {/* Main Response */}
      {mainResponse && (
        <div className="main-response">
          <div className="response-text">
            {mainResponse}
          </div>
        </div>
      )}

      {/* Additional Sections */}
      {sections && sections.length > 0 && (
        <div className="content-sections">
          {sections.map((section, index) => (
            <ContentSection key={index} section={section} />
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * Individual Content Section Component
 */
const ContentSection = ({ section }) => {
  const [isExpanded, setIsExpanded] = useState(section.defaultExpanded);

  const handleToggle = () => {
    if (section.collapsible) {
      setIsExpanded(!isExpanded);
    }
  };

  return (
    <div className={`content-section section-${section.type}`}>
      <div 
        className={`section-header ${section.collapsible ? 'clickable' : ''}`}
        onClick={handleToggle}
      >
        <span className="section-icon">{section.icon}</span>
        <span className="section-title">{section.title}</span>
        {section.collapsible && (
          <span className="section-toggle">
            {isExpanded ? '▼' : '▶'}
          </span>
        )}
      </div>

      {(!section.collapsible || isExpanded) && (
        <div className="section-content">
          {section.type === 'tool_use' ? (
            <ToolUseDisplay content={section.content} />
          ) : section.type === 'event' ? (
            <EventDisplay content={section.content} />
          ) : (
            <div className="text-content">
              {section.content}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

/**
 * Tool Use Display Component
 */
const ToolUseDisplay = ({ content }) => {
  const [showRaw, setShowRaw] = useState(false);

  return (
    <div className="tool-use-display">
      <div className="tool-info">
        <div className="tool-name">
          <strong>Tool:</strong> {content.name}
        </div>
        {content.args && content.args !== 'See details below' && (
          <div className="tool-args">
            <strong>Parameters:</strong>
            <pre className="args-display">
              {formatToolArgs(content.args)}
            </pre>
          </div>
        )}
      </div>
      
      {content.rawContent && (
        <div className="tool-raw">
          <button 
            className="toggle-raw"
            onClick={() => setShowRaw(!showRaw)}
          >
            {showRaw ? 'Hide' : 'Show'} Raw Details
          </button>
          {showRaw && (
            <pre className="raw-content">
              {content.rawContent}
            </pre>
          )}
        </div>
      )}
    </div>
  );
};

/**
 * Event Display Component
 */
const EventDisplay = ({ content }) => {
  return (
    <div className="event-display">
      <div className="event-info">
        <span className="event-type">{content.type}</span>
        {content.data && (
          <span className="event-data">{content.data}</span>
        )}
      </div>
      {content.timestamp && (
        <div className="event-timestamp">
          {new Date(content.timestamp).toLocaleTimeString()}
        </div>
      )}
    </div>
  );
};

export default ContentDisplay;

