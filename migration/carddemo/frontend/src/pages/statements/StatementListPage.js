import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { listStatements } from '../../api/statements';

/**
 * COSTM00C — Statement List (S-21 OnlineStatements, Main Menu option 12). A
 * modern screen with no legacy COBOL behind it, laid out like the other list
 * screens (CT00/CCLI):
 *   - type an 11 digit account number + ENTER -> the newest 10 statement cycles
 *     from GET /api/statements?acctId=&page=0
 *   - the API's validation / not-found text is shown verbatim on the message line;
 *     an account with no archived statements -> "No statements found for this account"
 *   - PF8 / PF7 -> next / previous 10 cycles, with the CT00 boundary messages
 *   - `S` beside a row + ENTER -> COSTM01C Statement View for that statement;
 *     any other flag -> "Invalid selection. Valid value is S"
 *   - PF3 -> back to the Main Menu
 * Arriving with ?acctId=... lists that account straight away (how the Statement
 * View returns here).
 */

export const PROMPT_MSG = 'Enter an account number and press ENTER to list its statements';
export const EMPTY_MSG = 'No statements found for this account';
export const ALREADY_TOP_MSG = 'You are already at the top of the page...';
export const ALREADY_BOTTOM_MSG = 'You are already at the bottom of the page...';
export const INVALID_SEL_MSG = 'Invalid selection. Valid value is S';
const LOOKUP_FAILED_MSG = 'Unable to lookup statements...';

const EMPTY_LIST = { acctId: null, rows: [], page: 0, hasNext: false };

const MONO = "'Courier New', Courier, monospace";

const styles = {
  form: { fontFamily: MONO },
  label: { display: 'inline-block', width: '160px' },
  input: { fontFamily: MONO, fontSize: '14px', padding: '4px 6px', width: '160px' },
  keys: { marginTop: '12px', marginBottom: '16px', display: 'flex', gap: '8px' },
  button: { fontFamily: MONO, fontSize: '13px', padding: '6px 12px', cursor: 'pointer' },
  info: { color: '#0b3d0b', fontFamily: MONO, minHeight: '20px', marginBottom: '4px' },
  error: {
    color: '#b00020',
    fontFamily: MONO,
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  page: { fontFamily: MONO, marginBottom: '8px' },
  table: { fontFamily: MONO, borderCollapse: 'collapse', width: '100%', maxWidth: '820px' },
  th: {
    textAlign: 'left',
    padding: '6px 10px',
    color: '#0b3d0b',
    borderBottom: '2px solid #cfe8cf',
    background: '#eef7ee',
  },
  thNum: {
    textAlign: 'right',
    padding: '6px 10px',
    color: '#0b3d0b',
    borderBottom: '2px solid #cfe8cf',
    background: '#eef7ee',
  },
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' },
  tdNum: {
    padding: '4px 10px',
    borderBottom: '1px solid #cfe8cf',
    color: '#12321a',
    textAlign: 'right',
  },
  selInput: { fontFamily: MONO, width: '28px', textAlign: 'center', textTransform: 'uppercase' },
};

/** The card as the list shows it: `****` plus the last four digits. */
export function maskCard(cardNum) {
  const digits = String(cardNum || '').replace(/\D/g, '');
  return `****${digits.slice(-4)}`;
}

const AMOUNT_FORMAT = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatAmount(amount) {
  if (amount === null || amount === undefined || amount === '') {
    return '';
  }
  const value = Number(amount);
  return Number.isFinite(value) ? AMOUNT_FORMAT.format(value) : String(amount);
}

/** ISO `2026-11-01T02:15:30.123` -> `2026-11-01 02:15:30`. */
export function formatTimestamp(ts) {
  if (!ts) {
    return '';
  }
  return String(ts).replace('T', ' ').replace(/\.\d+$/, '').replace(/Z$/, '');
}

export default function StatementListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [acctInput, setAcctInput] = useState('');
  const [list, setList] = useState(EMPTY_LIST);
  const [flags, setFlags] = useState({});
  const [info, setInfo] = useState(PROMPT_MSG);
  const [error, setError] = useState('');
  const listRef = useRef(EMPTY_LIST);

  const load = useCallback(async (acctId, page) => {
    try {
      const data = await listStatements({ acctId, page });
      const next = { acctId, rows: data.rows, page: data.page, hasNext: data.hasNext };
      listRef.current = next;
      setList(next);
      setFlags({});
      setError('');
      setInfo(data.rows.length === 0 ? EMPTY_MSG : '');
    } catch (e) {
      listRef.current = EMPTY_LIST;
      setList(EMPTY_LIST);
      setFlags({});
      setInfo('');
      setError(e.message || LOOKUP_FAILED_MSG);
    }
  }, []);

  const didAutoLoad = useRef(false);
  useEffect(() => {
    if (didAutoLoad.current) {
      return;
    }
    didAutoLoad.current = true;
    const preselected = searchParams.get('acctId');
    if (preselected) {
      setAcctInput(preselected);
      load(preselected.trim(), 0);
    }
  }, [searchParams, load]);

  const handleSubmit = (e) => {
    e.preventDefault();
    const selected = list.rows.find((row) => (flags[row.stmtId] || '').trim() !== '');
    if (selected) {
      if (flags[selected.stmtId].trim().toUpperCase() === 'S') {
        navigate(`/statements/view?stmtId=${encodeURIComponent(selected.stmtId)}`
          + `&acctId=${encodeURIComponent(list.acctId)}`);
      } else {
        setInfo('');
        setError(INVALID_SEL_MSG);
      }
      return;
    }
    load(acctInput.trim(), 0);
  };

  const pageBackward = useCallback(() => {
    const current = listRef.current;
    if (current.acctId === null || current.page <= 0) {
      setInfo('');
      setError(ALREADY_TOP_MSG);
      return;
    }
    load(current.acctId, current.page - 1);
  }, [load]);

  const pageForward = useCallback(() => {
    const current = listRef.current;
    if (current.acctId === null || !current.hasNext) {
      setInfo('');
      setError(ALREADY_BOTTOM_MSG);
      return;
    }
    load(current.acctId, current.page + 1);
  }, [load]);

  const exit = useCallback(() => navigate('/menu'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        exit();
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
  }, [exit, pageBackward, pageForward]);

  return (
    <Layout
      tranId="CM00"
      progName="COSTM00C"
      title="Statement List"
      pfKeys="ENTER=Continue  F3=Back  F7=Backward  F8=Forward"
    >
      <form style={styles.form} onSubmit={handleSubmit}>
        <label style={styles.label} htmlFor="acctId">Account Number :</label>
        <input
          id="acctId"
          style={styles.input}
          value={acctInput}
          maxLength={11}
          inputMode="numeric"
          onChange={(e) => setAcctInput(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER</button>
          <button type="button" style={styles.button} onClick={exit}>F3=Back</button>
          <button type="button" style={styles.button} onClick={pageBackward}>F7=Backward</button>
          <button type="button" style={styles.button} onClick={pageForward}>F8=Forward</button>
        </div>
        <div style={styles.info} role="status">{info}</div>
        <div style={styles.error} role="alert">{error}</div>

        {list.rows.length > 0 ? (
          <>
            <div style={styles.page}>{`Page ${list.page + 1}`}</div>
            <table style={styles.table}>
              <thead>
                <tr>
                  <th style={styles.th} scope="col">Sel</th>
                  <th style={styles.th} scope="col">Cycle</th>
                  <th style={styles.th} scope="col">Card</th>
                  <th style={styles.thNum} scope="col">Transactions</th>
                  <th style={styles.thNum} scope="col">Total</th>
                  <th style={styles.th} scope="col">Generated</th>
                </tr>
              </thead>
              <tbody>
                {list.rows.map((row) => (
                  <tr key={row.stmtId}>
                    <td style={styles.td}>
                      <input
                        aria-label={`select statement ${row.cycle}`}
                        style={styles.selInput}
                        maxLength={1}
                        value={flags[row.stmtId] || ''}
                        onChange={(e) => {
                          const { value } = e.target;
                          setFlags((prev) => ({ ...prev, [row.stmtId]: value }));
                        }}
                      />
                    </td>
                    <td style={styles.td}>{row.cycle}</td>
                    <td style={styles.td}>{maskCard(row.cardNum)}</td>
                    <td style={styles.tdNum}>{row.txnCount}</td>
                    <td style={styles.tdNum}>{formatAmount(row.totalAmount)}</td>
                    <td style={styles.td}>{formatTimestamp(row.generatedTs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : null}
      </form>
    </Layout>
  );
}
