import React, { useState } from 'react';
import '../styles/ChatInput.css';

const ChatInput = ({ onSendMessage, isLoading }) => {
  const [message, setMessage] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (message.trim() && !isLoading) {
      onSendMessage(message);
      setMessage('');
    }
  };

  return (
    <div className="chat-input-container">
      <form onSubmit={handleSubmit}>
        <div className="input-wrapper">
          <textarea
            className="chat-input"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Ask me anything about development..."
            disabled={isLoading}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSubmit(e);
              }
            }}
          />
          <button 
            type="submit" 
            className="send-button"
            disabled={!message.trim() || isLoading}
          >
            {isLoading ? 'Processing...' : 'Send'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ChatInput;
