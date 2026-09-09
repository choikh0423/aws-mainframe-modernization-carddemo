import React from 'react';
import { useNavigate } from 'react-router-dom';
import Header from './Header';
import { useAuth } from '../auth/AuthContext';

/**
 * Shared page shell: the legacy header, the signed-on user, and the row-24
 * PF-key line. Each screen passes its Tran ID / program name so the header
 * matches the legacy program, and its own {@code pfKeys} text taken verbatim
 * from the screen's BMS map (e.g. 'ENTER=Continue  F3=Exit' on COMEN01).
 */
const bar = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: '12px',
  padding: '6px 16px',
  background: '#12321a',
  color: '#cfe8cf',
  fontFamily: "'Courier New', Courier, monospace",
  fontSize: '13px',
};

const button = {
  background: 'none',
  color: '#cfe8cf',
  border: '1px solid #2c5d34',
  borderRadius: '3px',
  padding: '4px 10px',
  cursor: 'pointer',
  font: 'inherit',
};

const pfLine = {
  padding: '8px 16px',
  color: '#8a6d00',
  fontFamily: "'Courier New', Courier, monospace",
  fontSize: '13px',
};

export default function Layout({ tranId, progName, title, pfKeys, children }) {
  const { user, isSignedOn, isAdmin, signOff } = useAuth();
  const navigate = useNavigate();

  const handleSignOff = async () => {
    await signOff();
    navigate('/');
  };

  return (
    <div style={{ minHeight: '100vh' }}>
      <Header tranId={tranId} progName={progName} title={title} />
      {isSignedOn ? (
        <div style={bar}>
          <span>{`User: ${user.userId}  Type: ${user.userType}`}</span>
          <span style={{ display: 'flex', gap: '8px' }}>
            <button type="button" style={button} onClick={() => navigate('/menu')}>
              Main Menu
            </button>
            {isAdmin ? (
              <button type="button" style={button} onClick={() => navigate('/admin')}>
                Admin Menu
              </button>
            ) : null}
            <button type="button" style={button} onClick={handleSignOff}>
              Sign Off
            </button>
          </span>
        </div>
      ) : null}
      <main style={{ padding: '24px' }}>{children}</main>
      {pfKeys ? <div style={pfLine}>{pfKeys}</div> : null}
    </div>
  );
}
