import React, { useState } from 'react';
import { TEST_CASES } from '../data/testCases';

const API_BASE = '/api/transactions';

function TestRunnerPage() {
  const [results, setResults] = useState({});
  const [running, setRunning] = useState(false);
  const [currentTest, setCurrentTest] = useState('');

  const runSingleTest = async (tc) => {
    try {
      let resp;
      if (tc.method === 'GET') {
        const params = new URLSearchParams(tc.queryParams || {});
        resp = await fetch(`${API_BASE}${tc.path}?${params.toString()}`);
      } else {
        resp = await fetch(`${API_BASE}${tc.path}`, {
          method: tc.method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(tc.body),
        });
      }
      const data = await resp.json();
      const status = resp.status;

      let pass = true;
      const checks = [];

      if (tc.expectedStatus && status !== tc.expectedStatus) {
        pass = false;
        checks.push(`Status: expected ${tc.expectedStatus}, got ${status}`);
      }
      if (tc.expectedMessage) {
        if (data.message !== tc.expectedMessage) {
          pass = false;
          checks.push(`Message: expected "${tc.expectedMessage}", got "${data.message}"`);
        }
      }
      if (tc.expectedField) {
        if (data.errorField !== tc.expectedField) {
          pass = false;
          checks.push(`Field: expected "${tc.expectedField}", got "${data.errorField}"`);
        }
      }
      if (tc.expectedSuccess !== undefined) {
        if (data.success !== tc.expectedSuccess) {
          pass = false;
          checks.push(`Success: expected ${tc.expectedSuccess}, got ${data.success}`);
        }
      }
      if (tc.customCheck) {
        const customResult = tc.customCheck(data, status);
        if (!customResult.pass) {
          pass = false;
          checks.push(customResult.detail);
        }
      }

      return {
        pass,
        status,
        actualMessage: data.message || '',
        actualField: data.errorField || '',
        actualSuccess: data.success,
        checks,
        raw: data,
      };
    } catch (err) {
      return {
        pass: false,
        status: 0,
        actualMessage: 'Network error: ' + err.message,
        actualField: '',
        actualSuccess: false,
        checks: ['Network error'],
        raw: null,
      };
    }
  };

  const runAllTests = async () => {
    setRunning(true);
    setResults({});
    for (const tc of TEST_CASES) {
      setCurrentTest(tc.id);
      const result = await runSingleTest(tc);
      setResults(prev => ({ ...prev, [tc.id]: result }));
      // Small delay to avoid overwhelming the server
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    setCurrentTest('');
    setRunning(false);
  };

  const runOne = async (tc) => {
    setRunning(true);
    setCurrentTest(tc.id);
    const result = await runSingleTest(tc);
    setResults(prev => ({ ...prev, [tc.id]: result }));
    setCurrentTest('');
    setRunning(false);
  };

  const passCount = Object.values(results).filter(r => r.pass).length;
  const failCount = Object.values(results).filter(r => !r.pass).length;
  const pendingCount = TEST_CASES.length - passCount - failCount;

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <h2 style={{ fontFamily: 'monospace', borderBottom: '2px solid #333', paddingBottom: '8px' }}>
        COTRN02C Parity Test Runner
      </h2>

      <div style={{ display: 'flex', gap: '16px', marginBottom: '16px', alignItems: 'center' }}>
        <button
          onClick={runAllTests}
          disabled={running}
          style={{
            fontFamily: 'monospace', fontSize: '14px', padding: '10px 24px',
            background: running ? '#999' : '#1565c0', color: '#fff',
            border: 'none', borderRadius: '4px', cursor: running ? 'default' : 'pointer',
          }}
        >
          {running ? `Running ${currentTest}...` : 'Run All Tests'}
        </button>

        <div style={{ fontFamily: 'monospace', fontSize: '14px', display: 'flex', gap: '16px' }}>
          <span style={{ color: '#2e7d32', fontWeight: 'bold' }}>PASS: {passCount}</span>
          <span style={{ color: '#c62828', fontWeight: 'bold' }}>FAIL: {failCount}</span>
          <span style={{ color: '#999' }}>PENDING: {pendingCount}</span>
          <span style={{ color: '#333' }}>TOTAL: {TEST_CASES.length}</span>
        </div>
      </div>

      {/* Progress bar */}
      <div style={{ height: '6px', background: '#eee', borderRadius: '3px', marginBottom: '16px', overflow: 'hidden' }}>
        <div style={{
          height: '100%',
          width: `${((passCount + failCount) / TEST_CASES.length) * 100}%`,
          background: failCount > 0 ? '#c62828' : '#2e7d32',
          transition: 'width 0.3s ease',
        }} />
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', fontFamily: 'monospace', fontSize: '12px' }}>
        <thead>
          <tr style={{ background: '#1a1a2e', color: '#fff' }}>
            <th style={thStyle}>ID</th>
            <th style={thStyle}>Phase</th>
            <th style={thStyle}>Description</th>
            <th style={thStyle}>Expected (COBOL)</th>
            <th style={thStyle}>Actual (Java)</th>
            <th style={thStyle}>Result</th>
            <th style={thStyle}>Run</th>
          </tr>
        </thead>
        <tbody>
          {TEST_CASES.map((tc, i) => {
            const result = results[tc.id];
            const rowBg = result ? (result.pass ? '#e8f5e9' : '#ffebee') : (i % 2 === 0 ? '#fff' : '#fafafa');
            return (
              <tr key={tc.id} style={{ background: rowBg }}>
                <td style={tdStyle}>{tc.id}</td>
                <td style={tdStyle}>{tc.phase}</td>
                <td style={{ ...tdStyle, maxWidth: '250px' }}>{tc.description}</td>
                <td style={{ ...tdStyle, maxWidth: '200px', fontSize: '11px' }}>
                  {tc.expectedMessage || (tc.expectedSuccess ? 'success' : '')}
                </td>
                <td style={{ ...tdStyle, maxWidth: '200px', fontSize: '11px' }}>
                  {result ? (result.actualMessage || (result.actualSuccess ? 'success' : 'N/A')) : '—'}
                </td>
                <td style={{ ...tdStyle, fontWeight: 'bold', textAlign: 'center' }}>
                  {!result ? <span style={{ color: '#999' }}>PENDING</span>
                    : result.pass ? <span style={{ color: '#2e7d32' }}>PASS</span>
                    : <span style={{ color: '#c62828' }}>FAIL</span>}
                  {result && !result.pass && result.checks.length > 0 && (
                    <div style={{ fontSize: '10px', color: '#c62828', fontWeight: 'normal', marginTop: '2px' }}>
                      {result.checks.join('; ')}
                    </div>
                  )}
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <button
                    onClick={() => runOne(tc)}
                    disabled={running}
                    style={{
                      fontFamily: 'monospace', fontSize: '11px', padding: '2px 8px',
                      background: '#eee', border: '1px solid #ccc', borderRadius: '3px',
                      cursor: running ? 'default' : 'pointer',
                    }}
                  >
                    Run
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

const thStyle = {
  padding: '8px 10px',
  textAlign: 'left',
  borderBottom: '2px solid #333',
  fontSize: '11px',
};

const tdStyle = {
  padding: '6px 10px',
  borderBottom: '1px solid #ddd',
  verticalAlign: 'top',
};

export default TestRunnerPage;
