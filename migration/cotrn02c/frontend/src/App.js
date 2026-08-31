import React, { useState } from 'react';
import AddTransactionPage from './pages/AddTransactionPage';
import TestRunnerPage from './pages/TestRunnerPage';

const NAV_STYLE = {
  display: 'flex',
  gap: '16px',
  padding: '12px 24px',
  background: '#1a1a2e',
  color: '#fff',
  fontFamily: 'monospace',
  fontSize: '14px',
  alignItems: 'center',
};

const TAB_STYLE = {
  cursor: 'pointer',
  padding: '6px 16px',
  borderRadius: '4px',
  border: '1px solid #444',
  background: 'transparent',
  color: '#ccc',
  fontFamily: 'monospace',
};

const TAB_ACTIVE_STYLE = {
  ...TAB_STYLE,
  background: '#16213e',
  color: '#fff',
  borderColor: '#0f3460',
};

function App() {
  const [page, setPage] = useState('form');

  return (
    <div style={{ fontFamily: 'monospace', minHeight: '100vh', background: '#f5f5f5' }}>
      <nav style={NAV_STYLE}>
        <span style={{ fontWeight: 'bold', marginRight: '16px' }}>
          COTRN02C Migration
        </span>
        <button
          style={page === 'form' ? TAB_ACTIVE_STYLE : TAB_STYLE}
          onClick={() => setPage('form')}
        >
          Add Transaction
        </button>
        <button
          style={page === 'test-runner' ? TAB_ACTIVE_STYLE : TAB_STYLE}
          onClick={() => setPage('test-runner')}
        >
          Test Runner
        </button>
      </nav>
      <main style={{ padding: '24px' }}>
        {page === 'form' && <AddTransactionPage />}
        {page === 'test-runner' && <TestRunnerPage />}
      </main>
    </div>
  );
}

export default App;
