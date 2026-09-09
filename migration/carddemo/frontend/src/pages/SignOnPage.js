import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../components/Layout';
import { useAuth } from '../auth/AuthContext';
import { pathForProgram } from '../routes/registry';
import { getSignOnScreen } from '../api/auth';

/**
 * CC00 — Sign On (COSGN00C). Reproduces the legacy screen 1:1:
 *   - the COSGN00 map's banner, banknote artwork, prompt, the two 8-character
 *     fields and their "(8 Char)" hints, all served from the map transcription
 *     (COSGN00.bms POS=(5,6), (7,21)…(15,21), (17,16), (19,29), (20,29))
 *   - ENTER -> read USRSEC and XCTL to COADM01C for a type 'A' user or
 *     COMEN01C otherwise (COSGN00C.cbl:180-215)
 *   - a blank field or a failed lookup redisplays the screen with the exact
 *     ERRMSG text and the cursor on the offending field (cbl:139-168)
 *   - PF3 -> CCDA-MSG-THANK-YOU (cbl:120-127)
 */
const styles = {
  banner: { textAlign: 'center', marginBottom: '16px' },
  art: {
    fontFamily: "'Courier New', Courier, monospace",
    whiteSpace: 'pre',
    textAlign: 'center',
    color: '#2a4d8f',
    margin: '0 0 24px',
  },
  prompt: { marginBottom: '16px' },
  row: { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' },
  label: { width: '13ch' },
  input: {
    font: 'inherit',
    padding: '2px 4px',
    width: '10ch',
    textTransform: 'uppercase',
    border: '1px solid #2c5d34',
  },
  hint: { color: '#2a4d8f' },
  keys: { display: 'flex', gap: '12px', marginTop: '16px' },
  button: {
    font: 'inherit',
    padding: '4px 12px',
    border: '1px solid #2c5d34',
    background: '#e8f5e8',
    cursor: 'pointer',
  },
  message: { color: '#b00020', marginTop: '16px', minHeight: '1.5em' },
  sysInfo: { display: 'flex', justifyContent: 'space-between', marginBottom: '16px' },
};

export default function SignOnPage() {
  const { signOn, signOff, isSignedOn, user } = useAuth();
  const navigate = useNavigate();
  const [screen, setScreen] = useState(null);
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [errorField, setErrorField] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getSignOnScreen().then((loaded) => {
      if (!cancelled) {
        setScreen(loaded);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // A signed-on user who navigates back to CC00 goes on to their menu, the way
  // the legacy COMMAREA-carrying re-entry does (COSGN00C.cbl:98-112).
  useEffect(() => {
    if (isSignedOn && user.nextProgram) {
      const path = pathForProgram(user.nextProgram);
      if (path) {
        navigate(path, { replace: true });
      }
    }
  }, [isSignedOn, user, navigate]);

  const handleSubmit = useCallback(
    async (event) => {
      event.preventDefault();
      const result = await signOn(userId, password);
      if (!result.signedOn) {
        setMessage(result.message);
        setErrorField(result.errorField);
        return;
      }
      setMessage('');
      setErrorField(null);
      const path = pathForProgram(result.nextProgram);
      navigate(path || '/menu');
    },
    [signOn, userId, password, navigate]
  );

  // PF3: CCDA-MSG-THANK-YOU on a cleared screen.
  const exit = useCallback(async () => {
    const result = await signOff();
    setUserId('');
    setPassword('');
    setErrorField(null);
    setMessage(result.message);
  }, [signOff]);

  const fieldLength = screen ? screen.fieldLength : 8;

  return (
    <Layout tranId="CC00" progName="COSGN00C" pfKeys={screen ? screen.pfKeys : ''}>
      {screen ? (
        <div style={styles.sysInfo}>
          <span>{`${screen.applidLabel} ${screen.applid}`}</span>
          <span>{`${screen.sysidLabel} ${screen.sysid}`}</span>
        </div>
      ) : null}
      <div style={styles.banner}>{screen ? screen.banner : ''}</div>
      <pre style={styles.art}>{screen ? screen.art.join('\n') : ''}</pre>
      <div style={styles.prompt}>{screen ? screen.prompt : ''}</div>
      <form onSubmit={handleSubmit}>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="userId">{screen ? screen.userIdLabel : ''}</label>
          <input
            id="userId"
            style={styles.input}
            maxLength={fieldLength}
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            autoFocus={errorField !== 'password'}
          />
          <span style={styles.hint}>{screen ? screen.fieldHint : ''}</span>
        </div>
        <div style={styles.row}>
          <label style={styles.label} htmlFor="password">
            {screen ? screen.passwordLabel : ''}
          </label>
          <input
            id="password"
            type="password"
            style={styles.input}
            maxLength={fieldLength}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoFocus={errorField === 'password'}
          />
          <span style={styles.hint}>{screen ? screen.fieldHint : ''}</span>
        </div>
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Sign-on</button>
          <button type="button" style={styles.button} onClick={exit}>PF3 — Exit</button>
        </div>
      </form>
      <div style={styles.message} role="alert">{message}</div>
    </Layout>
  );
}
