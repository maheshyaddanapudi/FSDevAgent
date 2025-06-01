import React from 'react';
import '../styles/Header.css';

const Header = ({ activeView, setActiveView }) => {
  return (
    <header className="header">
      <div className="logo">AI Developer Agent</div>
      <nav className="nav">
        <button 
          className={`nav-button ${activeView === 'chat' ? 'active' : ''}`}
          onClick={() => setActiveView('chat')}
        >
          Chat
        </button>
        <div className="tool-status-indicator">
          <span className="tool-status-text">Unified Emulator</span>
          <span className="tool-status-dot"></span>
        </div>
      </nav>
    </header>
  );
};

export default Header;
