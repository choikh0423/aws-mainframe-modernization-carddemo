import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import { submitTransactionReport } from '../../api/reports';

/**
 * CR00 — Transaction Reports (CORPT00C / map CORPT0A). Reproduces the legacy
 * screen 1:1:
 *   - the row-4 title, the header and the row-24 PF line, which the shared
 *     Layout/Header render from the tranId/progName/title/pfKeys given here.
 *   - the three report choices in map order, each selected by typing any
 *     non-blank character in its one-byte field (FR-R6, CORPT00C.cbl:212-256).
 *   - the Custom start/end MM / DD / YYYY components, redisplayed as the
 *     program's NUMVAL-C normalisation leaves them (FR-R18).
 *   - the (Y/N) confirmation gate; blank asks, N clears the screen, anything
 *     else is quoted back (FR-R29..FR-R32, cbl:462-495).
 *   - ERRMSG on row 23 and the row-24 PF-key line, verbatim from the map.
 *   - PF3 back to the main menu, as XCTL COMEN01C did (FR-R3, cbl:171-174).
 *
 * Every edit and every message string comes from the backend, so the screen
 * cannot drift from the COBOL text.
 */

// Map order: MONTHLY (7,10), YEARLY (9,10), CUSTOM (11,10).
const REPORT_CHOICES = [
  { key: 'monthly', label: 'Monthly (Current Month)' },
  { key: 'yearly', label: 'Yearly (Current Year)' },
  { key: 'custom', label: 'Custom (Date Range)' },
];

// Map order within each date row: MM / DD / YYYY.
const DATE_ROWS = [
  {
    label: 'Start Date :',
    parts: [
      { key: 'startMonth', field: 'SDTMM', length: 2 },
      { key: 'startDay', field: 'SDTDD', length: 2 },
      { key: 'startYear', field: 'SDTYYYY', length: 4 },
    ],
  },
  {
    label: '  End Date :',
    parts: [
      { key: 'endMonth', field: 'EDTMM', length: 2 },
      { key: 'endDay', field: 'EDTDD', length: 2 },
      { key: 'endYear', field: 'EDTYYYY', length: 4 },
    ],
  },
];

const EMPTY_FORM = {
  monthly: '',
  yearly: '',
  custom: '',
  startMonth: '',
  startDay: '',
  startYear: '',
  endMonth: '',
  endDay: '',
  endYear: '',
  confirm: '',
};

const mono = "'Courier New', Courier, monospace";

const styles = {
  form: { fontFamily: mono, marginBottom: '16px' },
  choiceRow: { marginBottom: '12px' },
  choiceInput: {
    fontFamily: mono,
    fontSize: '14px',
    padding: '4px 6px',
    width: '28px',
    textAlign: 'center',
    marginRight: '10px',
  },
  dateRow: { marginBottom: '6px', whiteSpace: 'pre' },
  dateLabel: { display: 'inline-block' },
  datePart: {
    fontFamily: mono,
    fontSize: '14px',
    padding: '4px 6px',
    margin: '0 2px',
    textAlign: 'center',
  },
  hint: { marginLeft: '8px' },
  confirmRow: { marginTop: '24px' },
  confirmInput: {
    fontFamily: mono,
    fontSize: '14px',
    padding: '4px 6px',
    width: '28px',
    textAlign: 'center',
    margin: '0 6px',
  },
  keys: { marginTop: '20px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: mono,
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  message: {
    fontFamily: mono,
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

export default function TransactionReportsPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const [cursor, setCursor] = useState('MONTHLY');
  const inputs = useRef({});

  const setField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  // MOVE -1 TO <field>L: the program positions the cursor on the field it complains about.
  useEffect(() => {
    const target = inputs.current[cursor];
    if (target) {
      target.focus();
    }
  }, [cursor, message]);

  // ENTER (cbl:208-443): the backend runs every edit and returns the legacy text.
  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const result = await submitTransactionReport(form);
      // Y submitted the job (green success line); N cleared the screen silently.
      setForm(EMPTY_FORM);
      setMessage(result.message || '');
      setIsError(false);
      setCursor('MONTHLY');
    } catch (err) {
      // The screen comes back with the normalised date components (FR-R18).
      if (err.dateFields) {
        setForm((prev) => ({
          ...prev,
          startMonth: err.dateFields.startMonth,
          startDay: err.dateFields.startDay,
          startYear: err.dateFields.startYear,
          endMonth: err.dateFields.endMonth,
          endDay: err.dateFields.endDay,
          endYear: err.dateFields.endYear,
        }));
      }
      setMessage(err.message);
      setIsError(true);
      setCursor(err.cursor || 'MONTHLY');
    }
  };

  // PF3 — XCTL to COMEN01C (cbl:171-174).
  const backToMenu = useCallback(() => {
    navigate('/menu');
  }, [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        backToMenu();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToMenu]);

  const registerInput = (field) => (element) => {
    inputs.current[field] = element;
  };

  return (
    <Layout
      tranId="CR00"
      progName="CORPT00C"
      title="Transaction Reports"
      pfKeys="ENTER=Continue  F3=Back"
    >
      <form style={styles.form} onSubmit={handleSubmit}>
        {REPORT_CHOICES.map((choice) => (
          <div style={styles.choiceRow} key={choice.key}>
            <input
              aria-label={choice.label}
              ref={registerInput(choice.key.toUpperCase())}
              style={styles.choiceInput}
              value={form[choice.key]}
              maxLength={1}
              onChange={(e) => setField(choice.key, e.target.value)}
            />
            {choice.label}
          </div>
        ))}

        {DATE_ROWS.map((row) => (
          <div style={styles.dateRow} key={row.label}>
            <span style={styles.dateLabel}>{row.label}</span>
            {row.parts.map((part, index) => (
              <React.Fragment key={part.key}>
                {index > 0 ? '/' : ' '}
                <input
                  aria-label={`${row.label.trim()} ${part.field}`}
                  ref={registerInput(part.field)}
                  style={{ ...styles.datePart, width: part.length === 4 ? '52px' : '34px' }}
                  value={form[part.key]}
                  maxLength={part.length}
                  onChange={(e) => setField(part.key, e.target.value)}
                />
              </React.Fragment>
            ))}
            <span style={styles.hint}>(MM/DD/YYYY)</span>
          </div>
        ))}

        <div style={styles.confirmRow}>
          <span>The Report will be submitted for printing. Please confirm: </span>
          <input
            aria-label="Please confirm"
            ref={registerInput('CONFIRM')}
            style={styles.confirmInput}
            value={form.confirm}
            maxLength={1}
            onChange={(e) => setField('confirm', e.target.value)}
          />
          <span>(Y/N)</span>
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Continue</button>
          <button type="button" style={styles.button} onClick={backToMenu}>PF3 — Back</button>
        </div>
      </form>

      <div
        style={{ ...styles.message, color: isError ? '#b00020' : '#0b6b0b' }}
        role="alert"
      >
        {message}
      </div>
    </Layout>
  );
}
