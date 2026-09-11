import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { listUsers } from '../../api/users';
import { resolveSelection, INVALID_SELECTION } from './selection';

/**
 * CU00 — List Users (COUSR00C / map COUSR0A). Reproduces the legacy browse 1:1:
 *   - open -> first 10 users by User ID (FR-UL-1, cbl:282-331)
 *   - type a Search User ID + ENTER -> list begins at/after it (FR-UL-2, cbl:217-232)
 *   - PF8 -> next 10; at the end -> "You have reached the bottom of the page..."
 *     (FR-UL-3, FR-UL-6, cbl:257-277)
 *   - PF7 -> previous 10, but only past page 1; on page 1 ->
 *     "You are already at the top of the page..." (FR-UL-4, FR-UL-5, cbl:237-255)
 *   - an empty browse -> "You are at the top of the page..." (FR-UL-7, cbl:600-607)
 *   - a page whose (lookahead) read hits ENDFILE -> the file-boundary literals
 *     (FR-UL-8, cbl:634-641, :668-675)
 *   - `U`/`D` beside a row + ENTER -> CU02 Update / CU03 Delete (FR-UL-9, FR-UL-10)
 *   - any other flag -> "Invalid selection. Valid values are U and D" over a
 *     re-listed page 1 (FR-UL-11, FR-UL-13, quirk Q9 first-selection-wins FR-UL-12)
 *   - the page indicator counts 1, +1 on PF8, -1 on PF7 with a floor of 1 (FR-UL-14)
 *   - PF3 -> COADM01C (FR-UL-15, cbl:125-127)
 *   - any other key -> "Invalid key pressed. Please see below..." (FR-UL-16)
 */

// COUSR00C WS-MESSAGE / CCDA-MSG-INVALID-KEY literals, verbatim.
const ALREADY_BOTTOM_MSG = 'You are already at the bottom of the page...';
const ALREADY_TOP_MSG = 'You are already at the top of the page...';
const AT_TOP_MSG = 'You are at the top of the page...';
const REACHED_BOTTOM_MSG = 'You have reached the bottom of the page...';
const REACHED_TOP_MSG = 'You have reached the top of the page...';
const INVALID_KEY_MSG = 'Invalid key pressed. Please see below...';

const PF_KEYS = 'ENTER=Continue  F3=Back  F7=Backward  F8=Forward';

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
  row: { marginBottom: '8px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
    textTransform: 'uppercase',
  },
  hint: { marginLeft: '10px', color: '#12321a' },
  keys: { marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
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
  success: {
    color: '#0b6b18',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

const table = {
  fontFamily: "'Courier New', Courier, monospace",
  borderCollapse: 'collapse',
  width: '100%',
  maxWidth: '820px',
};
const th = {
  textAlign: 'left',
  padding: '6px 10px',
  color: '#0b3d0b',
  borderBottom: '2px solid #cfe8cf',
  background: '#eef7ee',
};
const td = { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' };
const selInput = {
  fontFamily: "'Courier New', Courier, monospace",
  width: '28px',
  textAlign: 'center',
  textTransform: 'uppercase',
};

export default function UserListPage() {
  const navigate = useNavigate();
  const [searchId, setSearchId] = useState('');
  const [page, setPage] = useState(EMPTY_PAGE);
  const [selections, setSelections] = useState({});
  const [message, setMessage] = useState('');
  const [pageNum, setPageNum] = useState(1);

  const load = useCallback(async ({ startId, dir } = {}) => {
    try {
      const data = await listUsers({ startId, dir });
      setPage(data);
      setSelections({});
      if (data.count === 0) {
        // FR-UL-7: STARTBR found nothing at or after the key.
        setMessage(AT_TOP_MSG);
      } else if (dir === 'prev' ? !data.hasPrevPage : !data.hasNextPage) {
        // FR-UL-8: the browse hit the end of the file, either while filling the
        // page or on the lookahead read that sets NEXT-PAGE (cbl:308-315), and
        // SEND-USRLST-SCREEN (:526) never clears WS-MESSAGE — so a last page of
        // exactly ten rows carries the literal too.
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

  // FR-UL-1: first entry paints page 1 from the top of USRSEC.
  useEffect(() => {
    load();
  }, [load]);

  const handleSubmit = useCallback(async (e) => {
    if (e) {
      e.preventDefault();
    }
    // PROCESS-ENTER-KEY scans the USRSEL flags before honouring the search key.
    const selection = resolveSelection(page.rows, selections);
    // `from` carries CDEMO-FROM-PROGRAM (cbl:184-208): it is what sends the
    // detail screen's PF3 back here instead of to the admin menu.
    if (selection.action === 'update') {
      navigate(`/admin/users/update?id=${encodeURIComponent(selection.userId)}&from=CU00`);
      return;
    }
    if (selection.action === 'delete') {
      navigate(`/admin/users/delete?id=${encodeURIComponent(selection.userId)}&from=CU00`);
      return;
    }
    if (selection.action === 'invalid') {
      // FR-UL-11 / FR-UL-13: the message is set and the browse still restarts at
      // page 1 (the COBOL falls through into PROCESS-PAGE-FORWARD, cbl:214-231).
      const data = await load({ startId: searchId.trim() });
      setPageNum(1);
      if (data) {
        setMessage(INVALID_SELECTION);
      }
      return;
    }
    // FR-UL-2 / FR-UL-14: ENTER restarts the browse and resets the page number.
    await load({ startId: searchId.trim() });
    setPageNum(1);
  }, [page.rows, selections, searchId, load, navigate]);

  const pageForward = useCallback(async () => {
    if (!page.hasNextPage) {
      setMessage(ALREADY_BOTTOM_MSG);
      return;
    }
    if (await load({ startId: page.lastId, dir: 'next' })) {
      setPageNum((n) => n + 1);
    }
  }, [page.hasNextPage, page.lastId, load]);

  const pageBackward = useCallback(async () => {
    // PROCESS-PF7-KEY guards on CDEMO-CU00-PAGE-NUM > 1 (cbl:248-254), not on
    // whether lower ids exist, and ENTER resets that counter (cbl:227) — so a
    // search result is page 1 and PF7 holds it there.
    if (pageNum <= 1) {
      setMessage(ALREADY_TOP_MSG);
      return;
    }
    if (await load({ startId: page.firstId, dir: 'prev' })) {
      setPageNum((n) => Math.max(1, n - 1));
    }
  }, [pageNum, page.firstId, load]);

  // FR-UL-15: PF3 -> COADM01C.
  const backToAdminMenu = useCallback(() => navigate('/admin'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToAdminMenu();
      } else if (e.key === 'F7') {
        e.preventDefault();
        pageBackward();
      } else if (e.key === 'F8') {
        e.preventDefault();
        pageForward();
      } else if (['F4', 'F5', 'F6', 'F9', 'F10', 'F11', 'F12'].includes(e.key)) {
        // FR-UL-16: every other AID is the WHEN OTHER arm (cbl:132-136).
        e.preventDefault();
        setMessage(INVALID_KEY_MSG);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToAdminMenu, pageBackward, pageForward]);

  const setSel = (userId, value) => {
    setSelections((prev) => ({ ...prev, [userId]: value }));
  };

  return (
    <Layout tranId="CU00" progName="COUSR00C" title="List Users" pfKeys={PF_KEYS}>
      <form style={styles.form} onSubmit={handleSubmit}>
        {/* FR-UL-14: the map's row-1 page indicator (COUSR00.bms:84). */}
        <div style={styles.row}>{`Page: ${pageNum}`}</div>
        <label style={styles.label} htmlFor="searchId">Search User ID:</label>
        <input
          id="searchId"
          style={styles.input}
          value={searchId}
          maxLength={8}
          onChange={(e) => setSearchId(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Continue</button>
          <button type="button" style={styles.button} onClick={backToAdminMenu}>F3 — Back</button>
          <button type="button" style={styles.button} onClick={pageBackward}>F7 — Backward</button>
          <button type="button" style={styles.button} onClick={pageForward}>F8 — Forward</button>
        </div>

        <div style={styles.error} role="alert">{message}</div>

        <div style={styles.form}>Type &apos;U&apos; to Update or &apos;D&apos; to Delete a User from the list</div>

        {/* The Sel flags sit inside the form so ENTER from any of them submits
            the screen, as the 3270 AID does (cbl:161-231). */}
        <table style={table}>
          <thead>
            <tr>
              <th style={th} scope="col">Sel</th>
              <th style={th} scope="col">User ID</th>
              <th style={th} scope="col">First Name</th>
              <th style={th} scope="col">Last Name</th>
              <th style={th} scope="col">Type</th>
            </tr>
          </thead>
          <tbody>
            {page.rows.map((row) => (
              <tr key={row.userId}>
                <td style={td}>
                  <input
                    aria-label={`select ${row.userId}`}
                    style={selInput}
                    maxLength={1}
                    value={selections[row.userId] || ''}
                    onChange={(e) => setSel(row.userId, e.target.value)}
                  />
                </td>
                <td style={td}>{row.userId}</td>
                <td style={td}>{row.firstName}</td>
                <td style={td}>{row.lastName}</td>
                <td style={td}>{row.userType}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </form>
    </Layout>
  );
}
