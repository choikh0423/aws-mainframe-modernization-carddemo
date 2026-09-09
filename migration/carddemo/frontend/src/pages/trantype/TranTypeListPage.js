import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { sendTranTypeList } from '../../api/tranTypes';

/**
 * CTLI — Maintain Transaction Type, list map (COTRTLIC / bms/COTRTLI.bms).
 *
 * Field order, labels and PF-key line come from CTRTLIA verbatim: "Type
 * Filter:" (6,30), "Description Filter:" (8,4), the Select/Type/Description
 * column heads (10,4..42), seven detail lines (rows 12-18), INFOMSG (21,19),
 * ERRMSG (23,1) and "F2=Add F3=Exit F7=Page Up F8=Page Dn F10=Save" (row 24).
 *
 * All behaviour lives in COTRTLIC on the backend: this screen only sends the
 * AID key, the two filters, the seven Select/Description pairs and the COMMAREA
 * it was handed, then renders whatever comes back. That keeps the pending
 * update/delete confirmations (F10) and the paging quirks in one place, as they
 * are in the legacy program.
 */
const MAX_SCREEN_LINES = 7;

const EMPTY_ROW = { selection: '', description: '' };

const styles = {
  screen: { fontFamily: "'Courier New', Courier, monospace" },
  subHeader: { display: 'flex', justifyContent: 'space-between', maxWidth: '820px' },
  filters: { marginBottom: '16px' },
  filterLine: { marginBottom: '4px' },
  label: { display: 'inline-block', width: '170px' },
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
  table: { borderCollapse: 'collapse', width: '100%', maxWidth: '820px' },
  th: {
    textAlign: 'left',
    padding: '6px 10px',
    color: '#0b3d0b',
    borderBottom: '2px solid #cfe8cf',
    background: '#eef7ee',
  },
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf', color: '#12321a' },
  highlighted: { background: '#ffe9e9', color: '#b00020', fontWeight: 'bold' },
  selInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '28px',
    textAlign: 'center',
    textTransform: 'uppercase',
  },
  rowDescInput: {
    fontFamily: "'Courier New', Courier, monospace",
    width: '420px',
    textTransform: 'uppercase',
  },
  info: {
    textAlign: 'center',
    maxWidth: '820px',
    minHeight: '20px',
    marginTop: '12px',
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

function blankRows() {
  return Array.from({ length: MAX_SCREEN_LINES }, () => ({ ...EMPTY_ROW }));
}

export default function TranTypeListPage() {
  const navigate = useNavigate();
  const [typeFilter, setTypeFilter] = useState('');
  const [descFilter, setDescFilter] = useState('');
  const [inputs, setInputs] = useState(blankRows);
  const [screen, setScreen] = useState(null);
  const [transportError, setTransportError] = useState('');
  const stateRef = useRef(null);
  const enteredRef = useRef(false);

  const send = useCallback(async (aid, rows) => {
    try {
      const response = await sendTranTypeList({
        aid,
        typeFilter,
        descFilter,
        rows: rows || inputs,
        state: stateRef.current,
      });
      stateRef.current = response.state;
      setScreen(response);
      setTransportError('');
      // The map re-sends TRTSELO blank and TRTYPDO from the fetched row, so the
      // unprotected fields always come from the response, never from the DOM.
      setTypeFilter(response.typeFilter || '');
      setDescFilter(response.descFilter || '');
      setInputs(response.rows.map((row) => ({
        selection: row.selection || '',
        description: row.description || '',
      })));
      // CCARD-NEXT-PROG: XCTL leaves this screen (COTRTLIC.cbl:640-700).
      if (response.nextProgram === 'COADM01C') {
        navigate('/admin');
      } else if (response.nextProgram === 'COTRTUPC') {
        navigate('/admin/transaction-types/update');
      }
      return response;
    } catch (e) {
      setTransportError(e.message);
      return null;
    }
  }, [typeFilter, descFilter, inputs, navigate]);

  // EIBCALEN = 0: first entry runs the browse from the top (FR-L1).
  useEffect(() => {
    if (enteredRef.current) {
      return;
    }
    enteredRef.current = true;
    send('ENTER', blankRows());
  }, [send]);

  const setRow = (index, field, value) => {
    setInputs((prev) => prev.map((row, i) => (
      i === index ? { ...row, [field]: value } : row
    )));
  };

  const onSubmit = (e) => {
    e.preventDefault();
    send('ENTER');
  };

  // The physical PF keys, as the 3270 keyboard drives them.
  useEffect(() => {
    const onKeyDown = (e) => {
      const key = e.key;
      if (key === 'F2' || key === 'F3' || key === 'F7' || key === 'F8' || key === 'F10') {
        e.preventDefault();
        send(`PF${key.substring(1)}`);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [send]);

  const rows = screen ? screen.rows : blankRows().map((row) => ({
    ...row, typeCode: '', highlighted: false, inError: false,
  }));

  return (
    <Layout tranId="CA00" progName="COTRTLIC" title="Maintain Transaction Type">
      <div style={styles.screen}>
        <div style={styles.subHeader}>
          <span />
          <span>{`Page ${screen ? screen.pageNumber : ''}`}</span>
        </div>

        <form style={styles.filters} onSubmit={onSubmit}>
          <div style={styles.filterLine}>
            <label style={styles.label} htmlFor="trtype">Type Filter:</label>
            <input
              id="trtype"
              style={styles.typeInput}
              maxLength={2}
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              autoFocus
            />
          </div>
          <div style={styles.filterLine}>
            <label style={styles.label} htmlFor="trdesc">Description Filter:</label>
            <input
              id="trdesc"
              style={styles.descInput}
              maxLength={50}
              value={descFilter}
              onChange={(e) => setDescFilter(e.target.value)}
            />
          </div>
          <button type="submit" style={{ ...styles.button, display: 'none' }} aria-hidden="true">
            ENTER
          </button>
        </form>

        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th} scope="col">Select</th>
              <th style={styles.th} scope="col">Type</th>
              <th style={styles.th} scope="col">Description</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const cell = row.highlighted ? { ...styles.td, ...styles.highlighted } : styles.td;
              return (
                <tr key={`line-${index + 1}`}>
                  <td style={cell}>
                    <input
                      aria-label={`select line ${index + 1}`}
                      style={styles.selInput}
                      maxLength={1}
                      value={inputs[index].selection}
                      disabled={Boolean(screen && screen.protectSelectRows)}
                      onChange={(e) => setRow(index, 'selection', e.target.value)}
                    />
                  </td>
                  <td style={cell}>{row.typeCode}</td>
                  <td style={cell}>
                    <input
                      aria-label={`description line ${index + 1}`}
                      style={styles.rowDescInput}
                      maxLength={50}
                      value={inputs[index].description}
                      onChange={(e) => setRow(index, 'description', e.target.value)}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        <div style={styles.info}>{screen ? screen.infoMessage : ''}</div>
        <div style={styles.error} role="alert">
          {transportError || (screen ? screen.errorMessage : '')}
        </div>

        <div style={styles.keys}>
          <button type="button" style={styles.button} onClick={() => send('ENTER')}>
            ENTER=Process
          </button>
          <button type="button" style={styles.button} onClick={() => send('PF2')}>F2=Add</button>
          <button type="button" style={styles.button} onClick={() => send('PF3')}>F3=Exit</button>
          <button type="button" style={styles.button} onClick={() => send('PF7')}>F7=Page Up</button>
          <button type="button" style={styles.button} onClick={() => send('PF8')}>F8=Page Dn</button>
          <button type="button" style={styles.button} onClick={() => send('PF10')}>F10=Save</button>
        </div>
      </div>
    </Layout>
  );
}
