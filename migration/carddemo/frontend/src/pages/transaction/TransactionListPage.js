import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { listTransactions } from '../../api/transactions';

/**
 * CT00 — Transaction List (COTRN00C). Reproduces the legacy paged browse 1:1:
 *   - open -> first 10 transactions by Tran ID (FR-L1, COTRN00C.cbl:279-326)
 *   - type a start Tran ID + ENTER -> list begins at/after it (FR-L2, cbl:206-225)
 *   - PF8 -> next 10; already at the end -> "You are already at the bottom of the
 *     page..." (FR-L3, cbl:257-274); a browse whose (lookahead) READNEXT hits
 *     ENDFILE -> "You have reached the bottom of the page..." (cbl:639-645)
 *   - PF7 -> previous 10; already at the top -> "You are already at the top of the
 *     page..." (FR-L4, cbl:234-252); a backward browse whose (lookahead)
 *     READPREV hits ENDFILE -> "You have reached the top of the page..."
 *     (cbl:673-678)
 *   - a browse that finds nothing at or after the key -> "You are at the top of
 *     the page..." (cbl:605-610)
 *   - type `S` beside a row + ENTER -> open it in CT01 View (FR-L5, cbl:183-195)
 *   - a non-`S` flag -> "Invalid selection. Valid value is S" (FR-L6, cbl:196-203)
 *   - a non-numeric filter -> "Tran ID must be Numeric ..." (FR-L7, cbl:209-218)
 *   - PF3 -> back to the menu placeholder (FR-L8, cbl:122-124)
 */

// Legacy boundary / validation ERRMSG text (COTRN00C WS-MESSAGE literals + the
// FR acceptance oracle). Kept verbatim so the screen matches the 3270 display.
const ALREADY_BOTTOM_MSG = 'You are already at the bottom of the page...';
const REACHED_BOTTOM_MSG = 'You have reached the bottom of the page...';
const ALREADY_TOP_MSG = 'You are already at the top of the page...';
const REACHED_TOP_MSG = 'You have reached the top of the page...';
const AT_TOP_MSG = 'You are at the top of the page...';
const INVALID_SEL_MSG = 'Invalid selection. Valid value is S';

const EMPTY_PAGE = {
  rows: [],
  firstId: null,
  lastId: null,
  hasNextPage: false,
  hasPrevPage: false,
  count: 0,
};

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  label: { display: 'inline-block', width: '140px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
    textTransform: 'uppercase',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  message: {
    color: '#b00020',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  table: {
    fontFamily: "'Courier New', Courier, monospace",
    borderCollapse: 'collapse',
    width: '100%',
    maxWidth: '820px',
  },
  th: {
    textAlign: 'left',
    padding: '6px 10px',
    color: '#0b3d0b',
    borderBottom: '2px solid #cfe8cf',
    background: '#eef7ee',
  },
  td: {
    padding: '4px 10px',
    borderBottom: '1px solid #cfe8cf',
    color: '#12321a',
  },
  amtCell: { textAlign: 'right' },
  selInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '28px',
    textAlign: 'center',
    textTransform: 'uppercase',
  },
  idLink: {
    background: 'none',
    border: 'none',
    padding: 0,
    color: '#0b5cc0',
    cursor: 'pointer',
    fontFamily: "'Courier New', Courier, monospace",
    textDecoration: 'underline',
  },
  footer: {
    marginTop: '8px',
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    color: '#12321a',
  },
};

export default function TransactionListPage() {
  const navigate = useNavigate();
  const [filterId, setFilterId] = useState('');
  const [page, setPage] = useState(EMPTY_PAGE);
  const [selections, setSelections] = useState({});
  const [message, setMessage] = useState('');

  // COTRN00C PROCESS-PAGE-FORWARD/BACKWARD -> fetch a page and reset the row
  // select flags (the map SELnnnn fields are re-sent blank each screen).
  const load = useCallback(async ({ startId, dir } = {}) => {
    try {
      const data = await listTransactions({ startId, dir });
      setPage(data);
      setSelections({});
      if (data.count === 0) {
        // STARTBR found nothing at or after the key (cbl:605-610).
        setMessage(AT_TOP_MSG);
      } else if (dir === 'prev' ? !data.hasPrevPage : !data.hasNextPage) {
        // READNEXT/READPREV hitting ENDFILE (cbl:639-645, :673-678), either
        // while filling the page or on the lookahead read that sets NEXT-PAGE
        // (cbl:305-312) — so a full last page carries the message too, since
        // SEND-TRNLST-SCREEN (:531) never clears WS-MESSAGE. A different event
        // from the PF7/PF8 guards.
        setMessage(dir === 'prev' ? REACHED_TOP_MSG : REACHED_BOTTOM_MSG);
      } else {
        setMessage('');
      }
      return data;
    } catch (e) {
      setMessage(e.message);
      return null;
    }
  }, []);

  // FR-L1: initial open -> first page from the top.
  useEffect(() => {
    load();
  }, [load]);

  const openRow = useCallback((id) => {
    // FR-L5: `S` select -> hand off to the CT01 View page (XCTL COTRN01C).
    navigate(`/transactions/view?id=${encodeURIComponent(id)}`);
  }, [navigate]);

  const handleSubmit = useCallback(async (e) => {
    if (e) {
      e.preventDefault();
    }
    // COTRN00C PROCESS-ENTER-KEY scans the SELnnnn flags first (cbl:148-204).
    const selected = page.rows.find((row) => (selections[row.id] || '').trim() !== '');
    if (selected) {
      const flag = (selections[selected.id] || '').trim().toUpperCase();
      if (flag === 'S') {
        openRow(selected.id);
        return;
      }
      // FR-L6: any non-`S` flag -> invalid-selection message, page stays put.
      setMessage(INVALID_SEL_MSG);
      return;
    }
    // FR-L2 / FR-L7: apply the start-from Tran ID filter (numeric guard is on
    // the backend, which returns "Tran ID must be Numeric ...").
    await load({ startId: filterId.trim() });
  }, [page.rows, selections, filterId, load, openRow]);

  const pageForward = useCallback(async () => {
    // FR-L3 / PF8. NEXT-PAGE flag guards the advance (cbl:267-274).
    if (!page.hasNextPage) {
      setMessage(ALREADY_BOTTOM_MSG);
      return;
    }
    await load({ startId: page.lastId, dir: 'next' });
  }, [page.hasNextPage, page.lastId, load]);

  const pageBackward = useCallback(async () => {
    // FR-L4 / PF7. PAGE-NUM > 1 guard (cbl:245-252).
    if (!page.hasPrevPage) {
      setMessage(ALREADY_TOP_MSG);
      return;
    }
    await load({ startId: page.firstId, dir: 'prev' });
  }, [page.hasPrevPage, page.firstId, load]);

  const backToMenu = useCallback(() => {
    // FR-L8: PF3 -> menu (COMEN01C).
    navigate('/menu');
  }, [navigate]);

  // Mirror the legacy PF keys on the physical function keys.
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

  const setSel = (id, value) => {
    setSelections((prev) => ({ ...prev, [id]: value }));
  };

  return (
    <Layout tranId="CT00" progName="COTRN00C" title="List Transactions">
      <form style={styles.form} onSubmit={handleSubmit}>
        <label style={styles.label} htmlFor="filterId">Search Tran ID:</label>
        <input
          id="filterId"
          style={styles.input}
          value={filterId}
          maxLength={16}
          onChange={(e) => setFilterId(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Filter / Select</button>
          <button type="button" style={styles.button} onClick={backToMenu}>PF3 — Back</button>
          <button
            type="button"
            style={styles.button}
            onClick={pageBackward}
            disabled={!page.hasPrevPage}
          >
            PF7 — Prev
          </button>
          <button
            type="button"
            style={styles.button}
            onClick={pageForward}
            disabled={!page.hasNextPage}
          >
            PF8 — Next
          </button>
        </div>
      </form>

      <div style={styles.message} role="alert">{message}</div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th} scope="col">Sel</th>
            <th style={styles.th} scope="col">Tran ID</th>
            <th style={styles.th} scope="col">Date</th>
            <th style={styles.th} scope="col">Description</th>
            <th style={{ ...styles.th, ...styles.amtCell }} scope="col">Amount</th>
          </tr>
        </thead>
        <tbody>
          {page.rows.map((row) => (
            <tr key={row.id}>
              <td style={styles.td}>
                <input
                  aria-label={`select ${row.id}`}
                  style={styles.selInput}
                  maxLength={1}
                  value={selections[row.id] || ''}
                  onChange={(e) => setSel(row.id, e.target.value)}
                />
              </td>
              <td style={styles.td}>
                <button type="button" style={styles.idLink} onClick={() => openRow(row.id)}>
                  {row.id}
                </button>
              </td>
              <td style={styles.td}>{row.date}</td>
              <td style={styles.td}>{row.description}</td>
              <td style={{ ...styles.td, ...styles.amtCell }}>{row.amountDisplay}</td>
            </tr>
          ))}
          {page.rows.length === 0 ? (
            <tr>
              <td style={styles.td} colSpan={5}>No transactions to display.</td>
            </tr>
          ) : null}
        </tbody>
      </table>

      <div style={styles.footer}>
        {page.count > 0
          ? `Showing ${page.count} transaction(s): ${page.firstId} — ${page.lastId}`
          : ''}
      </div>
    </Layout>
  );
}
