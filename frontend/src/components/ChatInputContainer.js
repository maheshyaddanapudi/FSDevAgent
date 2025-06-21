import React, { useState, useRef, useCallback, useLayoutEffect } from 'react';
import '../styles/ChatInput.css';

/**
 * Isolated Chat Input Container - Fix for UI Flickering Issue
 * 
 * This component isolates input state management to prevent parent re-renders
 * that cause UI flickering throughout the application.
 * 
 * Key improvements:
 * - Local state management prevents parent component re-renders
 * - useLayoutEffect prevents visual flicker during height adjustments
 * - useCallback ensures stable function references
 * - Proper cleanup and state reset
 */
function ChatInputContainer({ onSendMessage, disabled, placeholder = "Type your message..." }) {
  const [message, setMessage] = useState('');
  const [inputHeight, setInputHeight] = useState('auto');
  const textareaRef = useRef(null);

  // Use useLayoutEffect to prevent flicker by running before paint
  useLayoutEffect(() => {
    if (textareaRef.current) {
      // Reset height to auto to get the correct scrollHeight
      textareaRef.current.style.height = 'auto';
      const scrollHeight = textareaRef.current.scrollHeight;
      const newHeight = Math.min(scrollHeight, 120); // Max height of 120px
      setInputHeight(`${newHeight}px`);
    }
  }, [message]);

  const handleChange = useCallback((e) => {
    setMessage(e.target.value);
  }, []);

  const handleSubmit = useCallback(() => {
    if (message.trim() && !disabled) {
      onSendMessage(message.trim());
      setMessage('');
      setInputHeight('auto');
    }
  }, [message, disabled, onSendMessage]);

  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  }, [handleSubmit]);

  return (
    <div className="chat-input-container">
      <div className="input-wrapper">
        <textarea
          ref={textareaRef}
          value={message}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          style={{ height: inputHeight }}
          className="chat-input"
          placeholder={placeholder}
          disabled={disabled}
          rows={1}
        />
        <button 
          onClick={handleSubmit}
          disabled={!message.trim() || disabled}
          className="send-button"
          type="button"
        >
          {disabled ? '⏳' : 'Send'}
        </button>
      </div>
    </div>
  );
}

export default React.memo(ChatInputContainer);

