import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { useAuth } from '../auth/AuthContext';
import { pathForProgram } from '../routes/registry';
import { selectMenuOption } from '../api/auth';

/**
 * The menu screen shared by COMEN01C and COADM01C: both maps are the same
 * layout — a title, the numbered option list BUILD-MENU-OPTIONS writes into the
 * twelve 40-character OPTN fields ("NN. Name", COMEN01C.cbl:262-301), the
 * 2-byte OPTION field behind "Please select an option :", the ERRMSG line and
 * 'ENTER=Continue  F3=Exit'.
 *
 * Every literal here comes from the backend's BMS transcription. ENTER posts
 * the typed option, which the backend validates exactly as PROCESS-ENTER-KEY
 * does, answering with either the program to transfer to or the message and the
 * ERRMSG colour the map used — red for a refusal, green for the "coming soon" /
 * "not installed" answers. PF3 signs off and returns to COSGN00C.
 */
const styles = {
  options: {
    listStyle: 'none',
    margin: '0 0 24px',
    padding: 0,
    lineHeight: 1.6,
    fontFamily: "'Courier New', Courier, monospace",
    whiteSpace: 'pre',
  },
  row: { display: 'flex', alignItems: 'center', gap: '8px' },
  input: {
    font: 'inherit',
    width: '4ch',
    padding: '2px 4px',
    border: '1px solid #2c5d34',
    textAlign: 'right',
  },
  keys: { display: 'flex', gap: '12px', marginTop: '16px' },
  button: {
    font: 'inherit',
    padding: '4px 12px',
    border: '1px solid #2c5d34',
    background: '#e8f5e8',
    cursor: 'pointer',
  },
  message: { marginTop: '16px', minHeight: '1.5em' },
};

const MESSAGE_COLOURS = { RED: '#b00020', GREEN: '#1b7a2f' };

export default function MenuScreen({ tranId, progName, title, menu, loadScreen }) {
  const { signOff } = useAuth();
  const navigate = useNavigate();
  const [screen, setScreen] = useState(null);
  const [typedOption, setTypedOption] = useState('');
  const [message, setMessage] = useState('');
  const [messageColour, setMessageColour] = useState('RED');

  useEffect(() => {
    let cancelled = false;
    loadScreen().then((loaded) => {
      if (!cancelled) {
        setScreen(loaded);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [loadScreen]);

  const show = useCallback((selection) => {
    setMessage(selection.message || '');
    setMessageColour(selection.messageColour || 'RED');
    if (selection.optionEcho) {
      setTypedOption(selection.optionEcho);
    }
  }, []);

  const handleSubmit = useCallback(
    async (event) => {
      event.preventDefault();
      const selection = await selectMenuOption(menu, typedOption);
      show(selection);
      if (!selection.accepted) {
        return;
      }
      const path = pathForProgram(selection.programName);
      if (path) {
        navigate(path);
      }
    },
    [menu, typedOption, navigate, show]
  );

  const exit = useCallback(async () => {
    await signOff();
    navigate('/');
  }, [signOff, navigate]);

  const options = screen ? screen.options : [];

  return (
    <Layout
      tranId={tranId}
      progName={progName}
      title={title}
      pfKeys={screen ? screen.pfKeys : ''}
    >
      <ul style={styles.options}>
        {options.map((option) => (
          <li key={option.number}>{option.displayText}</li>
        ))}
      </ul>
      <form onSubmit={handleSubmit}>
        <div style={styles.row}>
          <label htmlFor="option">{screen ? screen.prompt : ''}</label>
          <input
            id="option"
            style={styles.input}
            maxLength={screen ? screen.optionLength : 2}
            value={typedOption}
            onChange={(e) => setTypedOption(e.target.value)}
            autoFocus
          />
        </div>
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Continue</button>
          <button type="button" style={styles.button} onClick={exit}>PF3 — Exit</button>
        </div>
      </form>
      <div style={{ ...styles.message, color: MESSAGE_COLOURS[messageColour] }} role="alert">
        {message}
      </div>
    </Layout>
  );
}
