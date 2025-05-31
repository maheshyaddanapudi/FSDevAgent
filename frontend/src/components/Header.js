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
        <button 
          className={`nav-button ${activeView === 'terminal' ? 'active' : ''}`}
          onClick={() => setActiveView('terminal')}
        >
          Terminal
        </button>
        <button 
          className={`nav-button ${activeView === 'browser' ? 'active' : ''}`}
          onClick={() => setActiveView('browser')}
        >
          Browser
        </button>
        <button 
          className={`nav-button ${activeView === 'code' ? 'active' : ''}`}
          onClick={() => setActiveView('code')}
        >
          Code Editor
        </button>
      </nav>
    </header>
  );
};

export default Header;
