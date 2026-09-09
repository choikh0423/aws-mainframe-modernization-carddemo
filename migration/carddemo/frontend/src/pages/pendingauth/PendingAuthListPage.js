import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import {
  listPendingAuthorizations,
  selectPendingAuthorization,
} from '../../api/pendingAuthorizations';

/**
 * CPVS — View Authorizations (COPAUS0C / map COPAU0A). Reproduces the legacy
 * screen 1:1:
 *   - blank account id + ENTER -> "Please enter Acct Id..." (FR-S2, cbl:266-271)
 *   - non-numeric account id -> "Acct Id must be Numeric ..." (FR-S3, cbl:273-278)
 *   - a valid account -> header from ACCTDAT/CUSTDAT plus the summary counters
 *     and up to five authorizations (FR-S5..FR-S8, cbl:414-451, 749-807)
 *   - PF8 -> next five; at the end -> "You are already at the bottom of the
 *     page..." (FR-S9, cbl:359-390)
 *   - PF7 -> previous five; at the top -> "You are already at the top of the
 *     page..." (FR-S10, cbl:392-412)
 *   - `S`/`s` beside a row + ENTER -> XCTL COPAUS1C/CPVD (FR-S11, cbl:308-341)
 *   - any other flag -> "Invalid selection. Valid value is S" (FR-S12)
 *   - PF3 -> back to the menu (FR-S13, cbl:236-247)
 *
 * The five selection fields are scanned in row order and the first non-blank
 * one wins, exactly as PROCESS-ENTER-KEY does (quirk Q-11), and the Acct Status
 * field is displayed empty because COPAUS0C never moves anything to ACCSTATO
 * (quirk Q-12).
 */

const EMPTY_SCREEN = {
  acctId: '',
  custId: '',
  customerName: '',
  addressLine1: '',
  addressLine2: '',
  phone: '',
  creditLimit: '',
  cashLimit: '',
  creditBalance: '',
  cashBalance: '',
  approvedCount: '',
  declinedCount: '',
  approvedAmount: '',
  declinedAmount: '',
  rows: [],
  pageNum: 0,
  firstKey: null,
  lastKey: null,
  nextPage: false,
  message: '',
};

const mono = "'Courier New', Courier, monospace";

const styles = {
  form: { fontFamily: mono, marginBottom: '12px' },
  label: { display: 'inline-block', width: '150px' },
  input: {
    fontFamily: mono,
    fontSize: '14px',
    padding: '4px 6px',
    width: '160px',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px' },
  button: { fontFamily: mono, fontSize: '13px', padding: '6px 12px', cursor: 'pointer' },
  message: {
    color: '#b00020',
    fontFamily: mono,
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  header: { fontFamily: mono, fontSize: '13px', color: '#12321a', marginBottom: '12px' },
  headerRow: { display: 'flex', gap: '24px' },
  headerCell: { minWidth: '260px' },
  table: { fontFamily: mono, borderCollapse: 'collapse', width: '100%', maxWidth: '820px' },
  th: {
    textAlign: 'left',
    padding: '6px 10px',
    color: '#0b3d0b',
    borderBottom: '2px solid #cfe8cf',
    background: '#eef7ee',
  },
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' },
  amtCell: { textAlign: 'right' },
  selInput: { fontFamily: mono, width: '28px', textAlign: 'center', textTransform: 'uppercase' },
  hint: { marginTop: '8px', fontFamily: mono, fontSize: '13px', color: '#12321a' },
};

export default function PendingAuthListPage() {
  const navigate = useNavigate();
  const [acctIdInput, setAcctIdInput] = useState('');
  const [screen, setScreen] = useState(EMPTY_SCREEN);
  const [selections, setSelections] = useState({});
  const [message, setMessage] = useState('');

  const load = useCallback(async ({ acctId, dir, startKey, pageNum } = {}) => {
    try {
      const data = await listPendingAuthorizations({ acctId, dir, startKey, pageNum });
      setScreen(data);
      setSelections({});
      setMessage(data.message || '');
      return data;
    } catch (e) {
      setMessage(e.message);
      return null;
    }
  }, []);

  const openDetail = useCallback((authKey) => {
    const params = new URLSearchParams({ acctId: acctIdInput.trim(), authKey });
    navigate(`/pending-authorizations/detail?${params.toString()}`);
  }, [acctIdInput, navigate]);

  const handleSubmit = useCallback(async (e) => {
    if (e) {
      e.preventDefault();
    }
    // PROCESS-ENTER-KEY evaluates the SEL0001I..SEL0005I flags before anything
    // else; only when they are all blank does it re-read the account.
    const flagged = screen.rows.some((row) => (selections[row.authKey] || '').trim() !== '');
    if (flagged) {
      const rows = screen.rows.map((row) => ({
        flag: selections[row.authKey] || '',
        authKey: row.authKey,
      }));
      try {
        const outcome = await selectPendingAuthorization({ acctId: acctIdInput, rows });
        if (outcome.selected) {
          openDetail(outcome.authKey);
          return;
        }
        setMessage(outcome.message || '');
      } catch (err) {
        setMessage(err.message);
      }
      return;
    }
    await load({ acctId: acctIdInput });
  }, [screen.rows, selections, acctIdInput, load, openDetail]);

  const pageForward = useCallback(async () => {
    // PF8: reposition on the last key of the page (cbl:359-390).
    await load({
      acctId: acctIdInput,
      dir: 'next',
      startKey: screen.lastKey,
      pageNum: screen.pageNum,
    });
  }, [acctIdInput, screen.lastKey, screen.pageNum, load]);

  const pageBackward = useCallback(async () => {
    // PF7: reposition on the first key of the page (cbl:392-412).
    await load({
      acctId: acctIdInput,
      dir: 'prev',
      startKey: screen.firstKey,
      pageNum: screen.pageNum,
    });
  }, [acctIdInput, screen.firstKey, screen.pageNum, load]);

  const backToMenu = useCallback(() => {
    navigate('/menu');
  }, [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToMenu();
      } else if (e.key === 'F7') {
        e.preventDefault();
        pageBackward();
      } else if (e.key === 'F8') {
        e.preventDefault();
        pageForward();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToMenu, pageBackward, pageForward]);

  const setSel = (authKey, value) => {
    setSelections((prev) => ({ ...prev, [authKey]: value }));
  };

  return (
    <Layout tranId="CPVS" progName="COPAUS0C" title="View Authorizations">
      <form style={styles.form} onSubmit={handleSubmit}>
        <label style={styles.label} htmlFor="acctId">Search Acct Id:</label>
        <input
          id="acctId"
          style={styles.input}
          value={acctIdInput}
          maxLength={11}
          onChange={(e) => setAcctIdInput(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER=Continue</button>
          <button type="button" style={styles.button} onClick={backToMenu}>F3=Back</button>
          <button type="button" style={styles.button} onClick={pageBackward}>F7=Backward</button>
          <button type="button" style={styles.button} onClick={pageForward}>F8=Forward</button>
        </div>
      </form>

      <div style={styles.message} role="alert">{message}</div>

      <div style={styles.header}>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>Name: {screen.customerName}</div>
          <div>Customer Id: {screen.custId}</div>
        </div>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>{screen.addressLine1}</div>
          <div>Acct Status: </div>
        </div>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>{screen.addressLine2}</div>
        </div>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>PH: {screen.phone}</div>
          <div>Approval # : {screen.approvedCount}</div>
          <div>Decline #: {screen.declinedCount}</div>
        </div>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>Credit Lim: {screen.creditLimit}</div>
          <div>Cash Lim: {screen.cashLimit}</div>
          <div>Appr Amt: {screen.approvedAmount}</div>
        </div>
        <div style={styles.headerRow}>
          <div style={styles.headerCell}>Credit Bal: {screen.creditBalance}</div>
          <div>Cash Bal: {screen.cashBalance}</div>
          <div>Decl Amt: {screen.declinedAmount}</div>
        </div>
      </div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th} scope="col">Sel</th>
            <th style={styles.th} scope="col">Transaction ID</th>
            <th style={styles.th} scope="col">Date</th>
            <th style={styles.th} scope="col">Time</th>
            <th style={styles.th} scope="col">Type</th>
            <th style={styles.th} scope="col">A/D</th>
            <th style={styles.th} scope="col">STS</th>
            <th style={{ ...styles.th, ...styles.amtCell }} scope="col">Amount</th>
          </tr>
        </thead>
        <tbody>
          {screen.rows.map((row) => (
            <tr key={row.authKey}>
              <td style={styles.td}>
                <input
                  aria-label={`select ${row.transactionId}`}
                  style={styles.selInput}
                  maxLength={1}
                  value={selections[row.authKey] || ''}
                  onChange={(e) => setSel(row.authKey, e.target.value)}
                />
              </td>
              <td style={styles.td}>{row.transactionId}</td>
              <td style={styles.td}>{row.date}</td>
              <td style={styles.td}>{row.time}</td>
              <td style={styles.td}>{row.authType}</td>
              <td style={styles.td}>{row.approvalStatus}</td>
              <td style={styles.td}>{row.matchStatus}</td>
              <td style={{ ...styles.td, ...styles.amtCell }}>{row.amountDisplay}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div style={styles.hint}>Type &apos;S&apos; to View Authorization details from the list</div>
    </Layout>
  );
}
