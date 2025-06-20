import React from 'react';
import { Routes, Route } from 'react-router-dom';
import ChatPage from './pages/ChatPage';
import AppErrorBoundary from './components/AppErrorBoundary';
import './styles/App.css';

function App() {
  return (
    <AppErrorBoundary>
      <div className="app">
        <Routes>
          <Route path="/" element={<ChatPage />} />
        </Routes>
      </div>
    </AppErrorBoundary>
  );
}

export default App;
