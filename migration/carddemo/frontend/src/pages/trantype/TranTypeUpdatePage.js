import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { sendTranTypeUpdate } from '../../api/tranTypes';

/**
 * CTTU — Maintain Transaction Type, add/update/delete map (COTRTUPC /
 * bms/COTRTUP.bms).
 *
 * The map has exactly two unprotected fields, "Transaction Type  :" (12,4) and
 * "Description       :" (14,4), the INFOMSG line (22,23), the ERRMSG line
 * (23,1) and the key line "ENTER=Process F3=Exit" with F4=Delete, F5=Save,
 * F6=Add and F12=Cancel defined DRK — dark until the program lights them up.
 *
 * COTRTUPC never lights F6 up: no paragraph moves DFHBMASB into FKEY06A and no
 * EVALUATE branch tests CCARD-AID-PFK06, so the map's "F6=Add" caption is
 * unreachable and adding is done with F5 from the not-found state instead. The
 * quirk is preserved here (the button is never rendered enabled) and recorded in
 * the FR rather than fixed.
 *
 * Every state transition lives in COTRTUPC on the backend; this screen posts the
 * AID key, the two fields and the COMMAREA, and renders the returned map.
 */
const styles = {
  screen: { fontFamily: "'Courier New', Courier, monospace" },
  line: { marginBottom: '8px' },
  label: { display: 'inline-block', width: '190px' },
  typeInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '40px',
    textTransform: 'uppercase',
  },
  descInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '420px',
    textTransform: 'uppercase',
  },
  info: {
    textAlign: 'center',
    maxWidth: '820px',
    minHeight: '20px',
    marginTop: '16px',
    color: '#12321a',
  },
  error: { color: '#b00020', fontWeight: 'bold', minHeight: '20px', marginTop: '4px' },
  keys: { marginTop: '12px', display: 'flex', gap: '12px' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
};

export default function TranTypeUpdatePage() {
  const navigate = useNavigate();
  const [typeCode, setTypeCode] = useState('');
  const [description, setDescription] = useState('');
  const [screen, setScreen] = useState(null);
  const [transportError, setTransportError] = useState('');
  const stateRef = useRef(null);
  const enteredRef = useRef(false);

  const send = useCallback(async (aid) => {
    try {
      const response = await sendTranTypeUpdate({
        aid,
        typeCode,
        description,
        state: stateRef.current,
      });
      stateRef.current = response.state;
      setScreen(response);
      setTransportError('');
      setTypeCode(response.typeCode || '');
      setDescription(response.description || '');
      // CCARD-NEXT-PROG on F3 / after the exit paths (COTRTUPC.cbl:430-445).
      if (response.nextProgram === 'COADM01C') {
        navigate('/admin');
      }
      return response;
    } catch (e) {
      setTransportError(e.message);
      return null;
    }
  }, [typeCode, description, navigate]);

  // EIBCALEN = 0: the first entry paints the empty map with its info line.
  useEffect(() => {
    if (enteredRef.current) {
      return;
    }
    enteredRef.current = true;
    send('ENTER');
  }, [send]);

  const onSubmit = (e) => {
    e.preventDefault();
    send('ENTER');
  };

  useEffect(() => {
    const onKeyDown = (e) => {
      const key = e.key;
      if (key === 'F3' || key === 'F4' || key === 'F5' || key === 'F12') {
        e.preventDefault();
        send(`PF${key.substring(1)}`);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [send]);

  const f4Enabled = Boolean(screen && screen.f4Enabled);
  const f5Enabled = Boolean(screen && screen.f5Enabled);
  const f12Enabled = Boolean(screen && screen.f12Enabled);

  return (
    <Layout tranId="CA00" progName="COTRTUPC" title="Maintain Transaction Type">
      <form style={styles.screen} onSubmit={onSubmit}>
        <div style={styles.line}>
          <label style={styles.label} htmlFor="trtypcd">Transaction Type  :</label>
          <input
            id="trtypcd"
            style={styles.typeInput}
            maxLength={2}
            value={typeCode}
            readOnly={Boolean(screen) && !screen.typeCodeEditable}
            onChange={(e) => setTypeCode(e.target.value)}
            autoFocus
          />
        </div>
        <div style={styles.line}>
          <label style={styles.label} htmlFor="trtydsc">Description       :</label>
          <input
            id="trtydsc"
            style={styles.descInput}
            maxLength={50}
            value={description}
            readOnly={Boolean(screen) && !screen.descriptionEditable}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <div style={styles.info}>{screen ? screen.infoMessage : ''}</div>
        <div style={styles.error} role="alert">
          {transportError || (screen ? screen.errorMessage : '')}
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER=Process</button>
          <button type="button" style={styles.button} onClick={() => send('PF3')}>F3=Exit</button>
          {f4Enabled ? (
            <button type="button" style={styles.button} onClick={() => send('PF4')}>F4=Delete</button>
          ) : null}
          {f5Enabled ? (
            <button type="button" style={styles.button} onClick={() => send('PF5')}>F5=Save</button>
          ) : null}
          {f12Enabled ? (
            <button type="button" style={styles.button} onClick={() => send('PF12')}>F12=Cancel</button>
          ) : null}
        </div>
      </form>
    </Layout>
  );
}
