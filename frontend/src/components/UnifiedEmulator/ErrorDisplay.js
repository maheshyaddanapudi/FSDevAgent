import React from 'react';

const ErrorDisplay = ({ error = null, message = "Something went wrong" }) => {
  return (
    <div style={{
      padding: '20px',
      textAlign: 'center',
      background: 'var(--bg-card, #1e1e1e)',
      border: '1px solid var(--error, #ef4444)',
      borderRadius: 'var(--radius-md, 0.5rem)',
      color: 'var(--text-primary, #ffffff)'
    }}>
      <div style={{ fontSize: '2rem', marginBottom: '10px' }}>⚠️</div>
      <h3 style={{ color: 'var(--error, #ef4444)', margin: '0 0 10px 0' }}>
        Error Loading Component
      </h3>
      <p style={{ margin: '0', color: 'var(--text-secondary, #b0b0b0)' }}>
        {error?.message || message}
      </p>
    </div>
  );
};

export default ErrorDisplay;

