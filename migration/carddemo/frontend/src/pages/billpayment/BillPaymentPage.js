import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';

/**
 * CB00 — Bill Payment (COBIL00C / map COBIL0A). The screen is reproduced field
 * for field from app/bms/COBIL00.bms:
 *   - row 6  'Enter Acct ID:' + ACTIDIN (11, unprotected)
 *   - row 8  'Your current balance is: ' + CURBAL (14, protected)
 *   - row 12 'Do you want to pay your balance now. Please confirm: ' +
 *            CONFIRM (1, unprotected) + '(Y/N)'
 *   - row 23 ERRMSG (78, red; green after a successful payment)
 *   - row 24 'ENTER=Continue  F3=Back  F4=Clear'
 *
 * ENTER posts the two unprotected fields to the CB00 endpoint, which is a 1:1
 * port of PROCESS-ENTER-KEY, and repaints whatever the backend returns — every
 * message string comes from the backend so it stays verbatim COBOL text
 * (FR-BP-10..FR-BP-39). PF3 returns to the caller (the main menu, COBIL00C.cbl
 * :135-142) and PF4 blanks the map (CLEAR-CURRENT-SCREEN, cbl:552-566).
 */

// COBIL00C only ever puts the cursor on one of these two fields.
const CURSOR_ACCT_ID = 'ACTIDIN';
const CURSOR_CONFIRM = 'CONFIRM';

const INVALID_CONFIRM = 'Invalid value. Valid values are (Y/N)...';

const EMPTY_SCREEN = {
  accountId: '',
  currentBalance: '',
  confirm: '',
  message: '',
  messageColour: 'RED',
  cursor: CURSOR_ACCT_ID,
};

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  row: { marginBottom: '10px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    textTransform: 'uppercase',
  },
  balance: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    marginLeft: '4px',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  message: {
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

/** POST one ENTER turn; a non-2xx body carries the verbatim ERRMSG text. */
async function submitScreen(accountId, confirm) {
  const res = await fetch('/api/billpay/screen', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accountId, confirm }),
  });
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const error = new Error((body && body.message) || 'Unable to lookup Account...');
    error.legacy = true;
    throw error;
  }
  return body;
}

export default function BillPaymentPage() {
  const navigate = useNavigate();
  const [screen, setScreen] = useState(EMPTY_SCREEN);
  const acctRef = useRef(null);
  const confirmRef = useRef(null);

  const setField = (key, value) => setScreen((prev) => ({ ...prev, [key]: value }));

  // MOVE -1 TO ACTIDINL / CONFIRML: the cursor the program asked for.
  useEffect(() => {
    const target = screen.cursor === CURSOR_CONFIRM ? confirmRef.current : acctRef.current;
    if (target) {
      target.focus();
    }
  }, [screen]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const next = await submitScreen(screen.accountId, screen.confirm);
      setScreen({
        accountId: next.accountId,
        currentBalance: next.currentBalance,
        confirm: next.confirm,
        message: next.message,
        messageColour: next.messageColour,
        cursor: next.cursor,
      });
    } catch (err) {
      // The rejected turns leave the typed fields on the screen; only the
      // confirm edit moves the cursor (cbl:185-190).
      setScreen((prev) => ({
        ...prev,
        message: err.message,
        messageColour: 'RED',
        cursor: err.message === INVALID_CONFIRM ? CURSOR_CONFIRM : CURSOR_ACCT_ID,
      }));
    }
  };

  // PF4 — CLEAR-CURRENT-SCREEN (cbl:552-566): every field blanked, no message.
  const clearScreen = useCallback(() => setScreen(EMPTY_SCREEN), []);

  // PF3 — XCTL back to CDEMO-FROM-PROGRAM, COMEN01C when it is blank (cbl:135-142).
  const backToCaller = useCallback(() => navigate('/menu'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToCaller();
      } else if (e.key === 'F4') {
        e.preventDefault();
        clearScreen();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToCaller, clearScreen]);

  return (
    <Layout
      tranId="CB00"
      progName="COBIL00C"
      title="Bill Payment"
      pfKeys="ENTER=Continue  F3=Back  F4=Clear"
    >
      <form style={styles.form} onSubmit={handleSubmit}>
        <div style={styles.row}>
          <label htmlFor="accountId">Enter Acct ID:</label>
          <input
            id="accountId"
            ref={acctRef}
            style={{ ...styles.input, width: '130px', marginLeft: '8px' }}
            value={screen.accountId}
            maxLength={11}
            onChange={(e) => setField('accountId', e.target.value)}
          />
        </div>

        <div style={styles.row}>
          <span>Your current balance is: </span>
          <span style={styles.balance}>{screen.currentBalance}</span>
        </div>

        <div style={styles.row}>
          <label htmlFor="confirm">Do you want to pay your balance now. Please confirm: </label>
          <input
            id="confirm"
            ref={confirmRef}
            style={{ ...styles.input, width: '30px' }}
            value={screen.confirm}
            maxLength={1}
            onChange={(e) => setField('confirm', e.target.value)}
          />
          <span style={{ marginLeft: '8px' }}>(Y/N)</span>
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Continue</button>
          <button type="button" style={styles.button} onClick={backToCaller}>F3 — Back</button>
          <button type="button" style={styles.button} onClick={clearScreen}>F4 — Clear</button>
        </div>
      </form>

      <div
        style={{ ...styles.message, color: screen.messageColour === 'GREEN' ? '#0b6b0b' : '#b00020' }}
        role="alert"
      >
        {screen.message}
      </div>
    </Layout>
  );
}
