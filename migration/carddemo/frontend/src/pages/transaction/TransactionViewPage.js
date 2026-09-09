import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { getTransaction } from '../../api/transactions';

/**
 * CT01 — Transaction View (COTRN01C). Reproduces the legacy screen 1:1:
 *   - enter a Tran ID + ENTER -> read-only detail of every CVTRA05Y field (FR-V1)
 *   - not found -> "Transaction ID NOT found..." (FR-V2, COTRN01C.cbl:283-288)
 *   - empty Tran ID -> "Tran ID can NOT be empty..." (FR-V3, COTRN01C.cbl:147-152)
 *   - PF3 back to menu, PF4 clear fields (FR-V4), PF5 go to the list (FR-V5)
 *   - arrive with ?id=... (from the list's `S` select) -> auto-load (FR-V7)
 */

const EMPTY_MSG = 'Tran ID can NOT be empty...';

// Display fields in the same order COTRN01C moves them onto map COTRN1A
// (COTRN01C.cbl:178-190).
const FIELDS = [
  { key: 'id', label: 'Tran ID' },
  { key: 'cardNum', label: 'Card Number' },
  { key: 'typeCd', label: 'Type CD' },
  { key: 'catCd', label: 'Category CD' },
  { key: 'source', label: 'Source' },
  { key: 'amountDisplay', label: 'Amount' },
  { key: 'description', label: 'Description' },
  { key: 'origTs', label: 'Orig Timestamp' },
  { key: 'procTs', label: 'Proc Timestamp' },
  { key: 'merchantId', label: 'Merchant ID' },
  { key: 'merchantName', label: 'Merchant Name' },
  { key: 'merchantCity', label: 'Merchant City' },
  { key: 'merchantZip', label: 'Merchant Zip' },
];

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '260px',
    textTransform: 'uppercase',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  error: {
    color: '#b00020',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  detail: {
    fontFamily: "'Courier New', Courier, monospace",
    borderCollapse: 'collapse',
    width: '100%',
    maxWidth: '640px',
  },
  th: {
    textAlign: 'left',
    padding: '4px 10px',
    width: '180px',
    color: '#0b3d0b',
    borderBottom: '1px solid #cfe8cf',
    verticalAlign: 'top',
  },
  td: {
    padding: '4px 10px',
    borderBottom: '1px solid #cfe8cf',
    whiteSpace: 'pre-wrap',
    color: '#12321a',
  },
};

export default function TransactionViewPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tranId, setTranId] = useState('');
  const [transaction, setTransaction] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async (rawId) => {
    const id = (rawId || '').trim();
    // COTRN01C PROCESS-ENTER-KEY empty guard (COTRN01C.cbl:147-152) — checked
    // before the READ, so no lookup is attempted on a blank key.
    if (id === '') {
      setTransaction(null);
      setError(EMPTY_MSG);
      return;
    }
    try {
      const data = await getTransaction(id);
      setTransaction(data);
      setError('');
    } catch (e) {
      setTransaction(null);
      setError(e.message);
    }
  }, []);

  // FR-V7: arrive from the list via `S` select with ?id=... -> auto-load once.
  const didAutoLoad = useRef(false);
  useEffect(() => {
    if (didAutoLoad.current) {
      return;
    }
    const preselected = searchParams.get('id');
    if (preselected) {
      didAutoLoad.current = true;
      setTranId(preselected);
      load(preselected);
    }
  }, [searchParams, load]);

  const handleSubmit = (e) => {
    e.preventDefault();
    load(tranId);
  };

  const clearScreen = useCallback(() => {
    // FR-V4 / CLEAR-CURRENT-SCREEN (COTRN01C.cbl:301-326).
    setTranId('');
    setTransaction(null);
    setError('');
  }, []);

  const backToMenu = useCallback(() => {
    // FR-V6 PF3 -> caller/menu (COTRN01C.cbl:115-122).
    navigate('/menu');
  }, [navigate]);

  const goToList = useCallback(() => {
    // FR-V5 PF5 -> transaction list CT00 (COTRN01C.cbl:125-127).
    navigate('/transactions');
  }, [navigate]);

  // Mirror the legacy PF keys on the physical function keys.
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToMenu();
      } else if (e.key === 'F4') {
        e.preventDefault();
        clearScreen();
      } else if (e.key === 'F5') {
        e.preventDefault();
        goToList();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToMenu, clearScreen, goToList]);

  return (
    <Layout tranId="CT01" progName="COTRN01C" title="View Transaction">
      <form style={styles.form} onSubmit={handleSubmit}>
        <label style={styles.label} htmlFor="tranId">Tran ID:</label>
        <input
          id="tranId"
          style={styles.input}
          value={tranId}
          maxLength={16}
          onChange={(e) => setTranId(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — View</button>
          <button type="button" style={styles.button} onClick={backToMenu}>PF3 — Back</button>
          <button type="button" style={styles.button} onClick={clearScreen}>PF4 — Clear</button>
          <button type="button" style={styles.button} onClick={goToList}>PF5 — List</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>

      {transaction ? (
        <table style={styles.detail}>
          <tbody>
            {FIELDS.map((f) => (
              <tr key={f.key}>
                <th scope="row" style={styles.th}>{f.label}</th>
                <td style={styles.td}>
                  {transaction[f.key] === null || transaction[f.key] === undefined
                    ? ''
                    : String(transaction[f.key])}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </Layout>
  );
}
