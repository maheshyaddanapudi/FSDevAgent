import React from 'react';
import { Routes, Route } from 'react-router-dom';
import ChatPage from './pages/ChatPage';
import './styles/App.css';

function App() {
  return (
    <div className="app">
      <Routes>
        <Route path="/" element={<ChatPage />} />
      </Routes>
    </div>
  );
}

export default App;
