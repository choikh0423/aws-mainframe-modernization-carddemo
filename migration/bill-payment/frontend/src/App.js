import React, { useCallback, useEffect, useRef, useState } from 'react';
import { initialScreen, submitKey } from './api';

/*
 * Bill Payment (CB00 / COBIL00C, map COBIL0A in app/bms/COBIL00.bms).
 * Labels, messages and key legend are the BMS/COBOL strings verbatim.
 * ENTER / F3 / F4 map to the AID keys the program evaluates; any other
 * function key is sent too so the "Invalid key pressed" path is reachable.
 */
const TITLE01 = 'AWS Mainframe Modernization';
const TITLE02 = 'CardDemo';
const FROM_PROGRAM = 'COMEN01C';

const s = {
  page: { maxWidth: 820, margin: '0 auto', padding: 16 },
  card: { background: '#fff', border: '1px solid #c8d3c8', borderRadius: 6, overflow: 'hidden' },
  header: { background: '#0b3d0b', color: '#e8f5e8', padding: '8px 16px', fontSize: 14, lineHeight: 1.5 },
  row: { display: 'grid', gridTemplateColumns: '1fr 2fr 1fr', alignItems: 'center' },
  body: { padding: '20px 28px', minHeight: 330 },
  title: { textAlign: 'center', fontWeight: 'bold', fontSize: 18, margin: '4px 0 20px' },
  line: { display: 'flex', alignItems: 'center', gap: 10, margin: '12px 0' },
  input: { fontFamily: 'inherit', fontSize: 15, padding: '4px 6px', border: '1px solid #7a9a7a', borderRadius: 3 },
  value: { fontWeight: 'bold', minWidth: 150, color: '#1b3f8b' },
  rule: { border: 0, borderTop: '1px dashed #b5a000', margin: '18px 0' },
  message: (color) => ({
    minHeight: 24, padding: '6px 10px', fontWeight: 'bold', borderRadius: 3,
    color: color === 'GREEN' ? '#0a6b0a' : '#b00020',
    background: color === 'GREEN' ? '#e3f5e3' : 'transparent',
  }),
  keys: { display: 'flex', gap: 8, padding: '10px 16px', background: '#f4f7f4', borderTop: '1px solid #dde5dd', alignItems: 'center' },
  button: { fontFamily: 'inherit', padding: '5px 12px', cursor: 'pointer' },
};

const AIDS = { Enter: 'ENTER', F3: 'PF3', F4: 'PF4' };

export default function App() {
  const [screen, setScreen] = useState(null);
  const [acctId, setAcctId] = useState('');
  const [confirm, setConfirm] = useState('');
  const [menu, setMenu] = useState(null);
  const [busy, setBusy] = useState(false);
  const acctRef = useRef(null);
  const confirmRef = useRef(null);
  const state = useRef({ acctId: '', confirm: '', screen: null });
  state.current = { acctId, confirm, screen };

  const show = useCallback((next) => {
    setScreen(next);
    setAcctId(next.acctId);
    setConfirm(next.confirm);
    if (next.nextProgram) {
      setMenu(next.nextProgram);
    }
    if (window.parent !== window) {
      window.parent.postMessage({ type: 'cb00-java-screen', screen: next }, '*');
    }
  }, []);

  const send = useCallback(async (aid, values) => {
    const cur = { ...state.current, ...(values || {}) };
    setBusy(true);
    try {
      show(await submitKey({
        aid,
        acctId: cur.acctId,
        currentBalance: cur.screen ? cur.screen.currentBalance : '',
        confirm: cur.confirm,
        fromProgram: FROM_PROGRAM,
      }));
    } finally {
      setBusy(false);
    }
  }, [show]);

  const start = useCallback(async () => {
    setMenu(null);
    show(await initialScreen());
  }, [show]);

  useEffect(() => { start(); }, [start]);

  useEffect(() => {
    if (!screen || menu) return;
    (screen.cursor === 'CONFIRM' ? confirmRef : acctRef).current?.focus();
  }, [screen, menu]);

  // The comparison page drives both screens with the same keystrokes.
  useEffect(() => {
    const onMessage = (e) => {
      const d = e.data || {};
      if (d.type === 'cb00-drive') {
        if (d.aid === 'START') {
          start();
          return;
        }
        if (d.acctId !== undefined) setAcctId(d.acctId);
        if (d.confirm !== undefined) setConfirm(d.confirm);
        send(d.aid, { acctId: d.acctId ?? state.current.acctId, confirm: d.confirm ?? state.current.confirm });
      }
    };
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [send, start]);

  const onKeyDown = (e) => {
    const aid = AIDS[e.key] || (/^F([5-9]|1[0-2])$/.test(e.key) ? `PF${e.key.slice(1)}` : null);
    if (aid) {
      e.preventDefault();
      send(aid);
    }
  };

  if (menu) {
    return (
      <div style={s.page} data-testid="menu-stub">
        <div style={s.card}>
          <div style={s.header}>
            <div style={s.row}><span>Tran: CM00</span><span style={{ textAlign: 'center' }}>{TITLE01}</span><span /></div>
            <div style={s.row}><span>Prog: {menu}</span><span style={{ textAlign: 'center' }}>{TITLE02}</span><span /></div>
          </div>
          <div style={s.body}>
            <div style={s.title}>Main Menu</div>
            <p>F3=Back transferred control to <b>{menu}</b> (EXEC CICS XCTL). The menu is outside this migration.</p>
            <button style={s.button} onClick={start}>10. Bill Payment</button>
          </div>
        </div>
      </div>
    );
  }

  if (!screen) {
    return <div style={s.page}>Loading...</div>;
  }

  return (
    <div style={s.page} onKeyDown={onKeyDown}>
      <div style={s.card}>
        <div style={s.header}>
          <div style={s.row}>
            <span>Tran: {screen.tranId}</span>
            <span style={{ textAlign: 'center', color: '#fff27a' }}>{TITLE01}</span>
            <span style={{ textAlign: 'right' }}>Date: {screen.currentDate}</span>
          </div>
          <div style={s.row}>
            <span>Prog: {screen.programName}</span>
            <span style={{ textAlign: 'center', color: '#fff27a' }}>{TITLE02}</span>
            <span style={{ textAlign: 'right' }}>Time: {screen.currentTime}</span>
          </div>
        </div>
        <form style={s.body} onSubmit={(e) => { e.preventDefault(); send('ENTER'); }}>
          <div style={s.title}>Bill Payment</div>
          <label style={s.line}>
            <span>Enter Acct ID:</span>
            <input ref={acctRef} data-testid="acct-id" style={{ ...s.input, width: '14ch' }} maxLength={11}
              value={acctId} onChange={(e) => setAcctId(e.target.value)} />
          </label>
          <hr style={s.rule} />
          <div style={s.line}>
            <span>Your current balance is: </span>
            <span data-testid="current-balance" style={s.value}>{screen.currentBalance}</span>
          </div>
          <label style={{ ...s.line, marginTop: 28 }}>
            <span>Do you want to pay your balance now. Please confirm: </span>
            <input ref={confirmRef} data-testid="confirm" style={{ ...s.input, width: '4ch', textAlign: 'center' }} maxLength={1}
              value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            <span>(Y/N)</span>
          </label>
          <div data-testid="message" style={s.message(screen.messageColor)}>{screen.message}</div>
          <button type="submit" hidden aria-hidden="true" />
        </form>
        <div style={s.keys}>
          <span>ENTER=Continue  F3=Back  F4=Clear</span>
          <span style={{ flex: 1 }} />
          <button style={s.button} disabled={busy} onClick={() => send('ENTER')}>Enter</button>
          <button style={s.button} disabled={busy} onClick={() => send('PF3')}>F3 Back</button>
          <button style={s.button} disabled={busy} onClick={() => send('PF4')}>F4 Clear</button>
        </div>
      </div>
    </div>
  );
}
