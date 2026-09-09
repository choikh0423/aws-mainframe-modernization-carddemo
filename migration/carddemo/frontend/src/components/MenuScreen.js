import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from './Layout';
import { useAuth } from '../auth/AuthContext';
import { pathForProgram } from '../routes/registry';
import { selectMenuOption } from '../api/auth';

/**
 * The menu screen shared by COMEN01C and COADM01C: both maps are the same
 * layout — a title, the numbered option list built by BUILD-MENU-OPTIONS
 * ("NN. Name", COMEN01C.cbl:262-301), a "Please select an option :" input,
 * the ERRMSG line and 'ENTER=Continue  F3=Exit'.
 *
 * ENTER posts the typed option to the backend, which applies the legacy
 * validation and the 'U'/'A' user-type rule, and answers with either the
 * CDEMO-TO-PROGRAM to transfer to or the exact message to display.
 * PF3 signs off and returns to COSGN00C.
 */
const PROMPT = 'Please select an option :';
const PF_KEYS = 'ENTER=Continue  F3=Exit';

const styles = {
  options: { listStyle: 'none', margin: '0 0 24px', padding: 0, lineHeight: 1.6 },
  row: { display: 'flex', alignItems: 'center', gap: '8px' },
  input: {
    font: 'inherit',
    width: '4ch',
    padding: '2px 4px',
    border: '1px solid #2c5d34',
  },
  keys: { display: 'flex', gap: '12px', marginTop: '16px' },
  button: {
    font: 'inherit',
    padding: '4px 12px',
    border: '1px solid #2c5d34',
    background: '#e8f5e8',
    cursor: 'pointer',
  },
  message: { color: '#b00020', marginTop: '16px', minHeight: '1.5em' },
};

function pad2(n) {
  return String(n).padStart(2, '0');
}

export default function MenuScreen({ tranId, progName, title, menu, loadOptions }) {
  const { signOff } = useAuth();
  const navigate = useNavigate();
  const [options, setOptions] = useState([]);
  const [typedOption, setTypedOption] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    let cancelled = false;
    loadOptions().then((loaded) => {
      if (!cancelled) {
        setOptions(loaded);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [loadOptions]);

  const handleSubmit = useCallback(
    async (event) => {
      event.preventDefault();
      const selection = await selectMenuOption(menu, typedOption);
      if (!selection.accepted) {
        setMessage(selection.message);
        return;
      }
      setMessage('');
      setTypedOption('');
      const path = pathForProgram(selection.programName);
      if (path) {
        navigate(path);
      }
    },
    [menu, typedOption, navigate]
  );

  const exit = useCallback(async () => {
    await signOff();
    navigate('/');
  }, [signOff, navigate]);

  return (
    <Layout tranId={tranId} progName={progName} title={title} pfKeys={PF_KEYS}>
      <ul style={styles.options}>
        {options.map((option) => (
          <li key={option.number}>{`${pad2(option.number)}. ${option.name}`}</li>
        ))}
      </ul>
      <form onSubmit={handleSubmit}>
        <div style={styles.row}>
          <label htmlFor="option">{PROMPT}</label>
          <input
            id="option"
            style={styles.input}
            maxLength={2}
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
      <div style={styles.message} role="alert">{message}</div>
    </Layout>
  );
}
