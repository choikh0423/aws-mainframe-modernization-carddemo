import React from 'react';
import { NavLink } from 'react-router-dom';
import Header from './Header';

/**
 * Shared page shell: legacy header + simple screen navigation. Each screen
 * passes its Tran ID / program name so the header matches the legacy program.
 */
const nav = {
  display: 'flex',
  gap: '12px',
  padding: '8px 16px',
  background: '#12321a',
  fontFamily: "'Courier New', Courier, monospace",
  fontSize: '13px',
};

const linkBase = {
  color: '#cfe8cf',
  textDecoration: 'none',
  padding: '4px 10px',
  border: '1px solid #2c5d34',
  borderRadius: '3px',
};

const linkActive = {
  ...linkBase,
  background: '#1e5128',
  color: '#fff',
};

function navStyle({ isActive }) {
  return isActive ? linkActive : linkBase;
}

export default function Layout({ tranId, progName, title, children }) {
  return (
    <div style={{ minHeight: '100vh' }}>
      <Header tranId={tranId} progName={progName} title={title} />
      <nav style={nav}>
        <NavLink to="/transactions" end style={navStyle}>CT00 List</NavLink>
        <NavLink to="/transactions/view" style={navStyle}>CT01 View</NavLink>
        <NavLink to="/transactions/add" style={navStyle}>CT02 Add</NavLink>
      </nav>
      <main style={{ padding: '24px' }}>{children}</main>
    </div>
  );
}
