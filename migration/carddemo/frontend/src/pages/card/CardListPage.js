import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { listCards, selectCard } from '../../api/cards';

/**
 * CCLI — List Credit Cards (COCRDLIC). The BMS map COCRDLI shows seven detail
 * lines under the two filter fields, with the paging state carried between
 * screens:
 *   - ENTER -> apply the Account Number / Credit Card Number filters and browse
 *     from the top (FR-L1/FR-L7, COCRDLIC.cbl:1003-1066, 1382-1405)
 *   - PF8 -> next seven; past the end -> "NO MORE PAGES TO DISPLAY"
 *     (FR-L8/FR-L9, cbl:486-497, 906-912)
 *   - PF7 -> previous seven; on page 1 -> "NO PREVIOUS PAGES TO DISPLAY"
 *     (FR-L10, cbl:444-454, 901-905)
 *   - `S` beside a row -> CCDL detail, `U` -> CCUP update (FR-L12/FR-L13,
 *     cbl:517-569)
 *   - two selections -> "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE",
 *     any other flag -> "INVALID ACTION CODE" (FR-L14/FR-L15, cbl:1075-1121)
 *   - PF3 -> the main menu (FR-L16, cbl:406-417)
 */

const ROWS_PER_PAGE = 7;

const EMPTY_PAGE = {
  rows: [],
  pageNumber: 1,
  firstCardNum: null,
  lastCardNum: null,
  nextPageExists: false,
  lastPageShown: false,
  infoMessage: '',
  errorMessage: '',
};

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  label: { display: 'inline-block', width: '190px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
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
  },
  info: {
    color: '#12321a',
    fontFamily: "'Courier New', Courier, monospace",
    minHeight: '20px',
    marginBottom: '12px',
  },
  page: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '8px' },
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
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' },
  selInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '28px',
    textAlign: 'center',
    textTransform: 'uppercase',
  },
};

export default function CardListPage() {
  const navigate = useNavigate();
  const [acctFilter, setAcctFilter] = useState('');
  const [cardFilter, setCardFilter] = useState('');
  const [page, setPage] = useState(EMPTY_PAGE);
  const [flags, setFlags] = useState({});
  const [error, setError] = useState('');
  const pageRef = useRef(EMPTY_PAGE);
  const filtersRef = useRef({ acctFilter: '', cardFilter: '' });

  filtersRef.current = { acctFilter, cardFilter };

  const load = useCallback(async (dir) => {
    const current = pageRef.current;
    const { acctFilter: acct, cardFilter: card } = filtersRef.current;
    try {
      const data = await listCards({
        acctId: acct.trim(),
        cardNum: card.trim(),
        dir,
        pageNumber: current.pageNumber,
        firstCardNum: current.firstCardNum,
        lastCardNum: current.lastCardNum,
        nextPageExists: current.nextPageExists,
        lastPageShown: current.lastPageShown,
      });
      pageRef.current = data;
      setPage(data);
      // The map is re-sent with the CRDSELnn flags blank every screen.
      setFlags({});
      setError(data.errorMessage || '');
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load('');
  }, [load]);

  const handleSubmit = useCallback(async (e) => {
    if (e) {
      e.preventDefault();
    }
    const rows = page.rows;
    const flagList = [];
    for (let i = 0; i < ROWS_PER_PAGE; i += 1) {
      flagList.push(rows[i] ? (flags[rows[i].cardNum] || '') : '');
    }
    if (flagList.some((flag) => flag.trim() !== '')) {
      try {
        const target = await selectCard({ flags: flagList, rows });
        if (target) {
          const path = target.program === 'COCRDSLC' ? '/cards/view' : '/cards/update';
          navigate(`${path}?acctId=${encodeURIComponent(target.acctId)}`
            + `&cardNum=${encodeURIComponent(target.cardNum)}`);
          return;
        }
      } catch (selectionError) {
        setError(selectionError.message);
        return;
      }
    }
    await load('');
  }, [page.rows, flags, load, navigate]);

  const pageForward = useCallback(() => load('F'), [load]);
  const pageBackward = useCallback(() => load('B'), [load]);
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
      progName="COCRDLIC"
      title="List Credit Cards"
      pfKeys="  F3=Exit F7=Backward  F8=Forward"
    >
      <div style={styles.page}>{`Page ${page.pageNumber}`}</div>
      <form style={styles.form} onSubmit={handleSubmit}>
        <div>
          <label style={styles.label} htmlFor="acctFilter">Account Number    :</label>
          <input
            id="acctFilter"
            style={styles.input}
            value={acctFilter}
            maxLength={11}
            onChange={(e) => setAcctFilter(e.target.value)}
            autoFocus
          />
        </div>
        <div>
          <label style={styles.label} htmlFor="cardFilter">Credit Card Number:</label>
          <input
            id="cardFilter"
            style={styles.input}
            value={cardFilter}
            maxLength={16}
            onChange={(e) => setCardFilter(e.target.value)}
          />
        </div>
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER</button>
          <button type="button" style={styles.button} onClick={exit}>F3=Exit</button>
          <button type="button" style={styles.button} onClick={pageBackward}>F7=Backward</button>
          <button type="button" style={styles.button} onClick={pageForward}>F8=Forward</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.info}>{error ? '' : (page.infoMessage || '')}</div>

      <table style={styles.table}>
        <thead>
          <tr>
            <th style={styles.th} scope="col">Select</th>
            <th style={styles.th} scope="col">Account Number</th>
            <th style={styles.th} scope="col"> Card Number </th>
            <th style={styles.th} scope="col">Active</th>
          </tr>
        </thead>
        <tbody>
          {page.rows.map((row) => (
            <tr key={row.cardNum}>
              <td style={styles.td}>
                <input
                  aria-label={`select ${row.cardNum}`}
                  style={styles.selInput}
                  maxLength={1}
                  value={flags[row.cardNum] || ''}
                  onChange={(e) => setFlags((prev) => ({ ...prev, [row.cardNum]: e.target.value }))}
                />
              </td>
              <td style={styles.td}>{row.acctId}</td>
              <td style={styles.td}>{row.cardNum}</td>
              <td style={styles.td}>{row.activeStatus}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Layout>
  );
}
