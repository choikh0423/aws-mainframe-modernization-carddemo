import React, { useEffect, useState } from 'react';

/**
 * Shared header reproducing the legacy CardDemo 3270 screen header used by
 * COTRN00C/COTRN01C/COTRN02C (see COTTL01Y titles + POPULATE-HEADER-INFO,
 * COTRN02C.cbl:552-571):
 *   row 1: Tran ID (left) | TITLE01 "AWS Mainframe Modernization" | date MM/DD/YY
 *   row 2: Program name   | TITLE02 "CardDemo"                    | time HH:MM:SS
 * plus an optional per-screen sub-title line.
 */
const TITLE01 = 'AWS Mainframe Modernization';
const TITLE02 = 'CardDemo';

function pad2(n) {
  return String(n).padStart(2, '0');
}

function formatDate(d) {
  // CURDATE mask MM/DD/YY (COTRN02C.cbl:565)
  return `${pad2(d.getMonth() + 1)}/${pad2(d.getDate())}/${pad2(d.getFullYear() % 100)}`;
}

function formatTime(d) {
  // CURTIME mask HH:MM:SS (COTRN02C.cbl:571)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

const wrap = {
  background: '#0b3d0b',
  color: '#e8f5e8',
  padding: '8px 16px',
  fontFamily: "'Courier New', Courier, monospace",
  fontSize: '14px',
  lineHeight: 1.5,
};

const row = {
  display: 'grid',
  gridTemplateColumns: '1fr 2fr 1fr',
  alignItems: 'center',
};

export default function Header({ tranId = '', progName = '', title = '' }) {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  return (
    <header style={wrap}>
      <div style={row}>
        <span>{tranId}</span>
        <span style={{ textAlign: 'center', fontWeight: 'bold' }}>{TITLE01}</span>
        <span style={{ textAlign: 'right' }}>{formatDate(now)}</span>
      </div>
      <div style={row}>
        <span>{progName}</span>
        <span style={{ textAlign: 'center', fontWeight: 'bold' }}>{TITLE02}</span>
        <span style={{ textAlign: 'right' }}>{formatTime(now)}</span>
      </div>
      {title ? (
        <div style={{ textAlign: 'center', marginTop: '4px' }}>{title}</div>
      ) : null}
    </header>
  );
}
