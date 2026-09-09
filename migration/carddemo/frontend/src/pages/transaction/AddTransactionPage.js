import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Layout from '../../components/Layout';
import {
  addTransaction,
  resolveCardXref,
  getLatestTransaction,
} from '../../api/transactions';

/**
 * CT02 — Add Transaction (COTRN02C). Reproduces the legacy screen 1:1:
 *   - Account ID or Card Number keys the record; entering one auto-resolves the
 *     other via CARDXREF (FR-A1/FR-A2, COTRN02C.cbl:193-230).
 *   - every CVTRA05Y input field with the legacy edit rules (empty / numeric /
 *     amount format / date format+real-date) surfaced as exact ERRMSG text
 *     (FR-A3..FR-A10, COTRN02C.cbl:251-436).
 *   - Confirm (Y/N) gate before the write (FR-A11); on Y a new Tran ID =
 *     max existing + 1 is written and the green success text is shown (FR-A6).
 *   - PF3 back to menu (FR-A14), PF4 clear (FR-A13), PF5 copy-last (FR-A12).
 */

// Field order mirrors COTRN02C's screen / VALIDATE-INPUT-DATA-FIELDS order.
const KEY_FIELDS = [
  { key: 'accountId', label: 'Account ID', maxLength: 11, placeholder: '11-digit account' },
  { key: 'cardNumber', label: 'Card Number', maxLength: 16, placeholder: '16-digit card' },
];

const DATA_FIELDS = [
  { key: 'typeCd', label: 'Type CD', maxLength: 2 },
  { key: 'categoryCd', label: 'Category CD', maxLength: 4 },
  { key: 'source', label: 'Source', maxLength: 10 },
  { key: 'description', label: 'Description', maxLength: 60 },
  { key: 'amount', label: 'Amount', maxLength: 12, placeholder: '-99999999.99' },
  { key: 'origDate', label: 'Orig Date', maxLength: 10, placeholder: 'YYYY-MM-DD' },
  { key: 'procDate', label: 'Proc Date', maxLength: 10, placeholder: 'YYYY-MM-DD' },
  { key: 'merchantId', label: 'Merchant ID', maxLength: 9 },
  { key: 'merchantName', label: 'Merchant Name', maxLength: 30 },
  { key: 'merchantCity', label: 'Merchant City', maxLength: 25 },
  { key: 'merchantZip', label: 'Merchant Zip', maxLength: 10 },
];

const EMPTY_FORM = {
  accountId: '',
  cardNumber: '',
  typeCd: '',
  categoryCd: '',
  source: '',
  description: '',
  amount: '',
  origDate: '',
  procDate: '',
  merchantId: '',
  merchantName: '',
  merchantCity: '',
  merchantZip: '',
  confirm: '',
};

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  row: { marginBottom: '6px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '280px',
    textTransform: 'uppercase',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  message: {
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
};

export default function AddTransactionPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY_FORM);
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);

  const setField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const showError = (text) => {
    setMessage(text);
    setIsError(true);
  };
  const showSuccess = (text) => {
    setMessage(text);
    setIsError(false);
  };

  // FR-A1: entering an Account ID resolves and fills the Card Number.
  const resolveByAccount = async () => {
    const accountId = form.accountId.trim();
    if (accountId === '') {
      return;
    }
    try {
      const { cardNumber } = await resolveCardXref({ accountId });
      setField('cardNumber', cardNumber);
      setMessage('');
    } catch (e) {
      showError(e.message);
    }
  };

  // FR-A2: entering a Card Number (with no Account ID) resolves the Account ID.
  const resolveByCard = async () => {
    const cardNumber = form.cardNumber.trim();
    if (cardNumber === '' || form.accountId.trim() !== '') {
      return;
    }
    try {
      const { accountId } = await resolveCardXref({ cardNumber });
      setField('accountId', accountId);
      setMessage('');
    } catch (e) {
      showError(e.message);
    }
  };

  // ENTER: validate + write (FR-A6); the backend returns the exact legacy text.
  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const { message: successText } = await addTransaction(form);
      showSuccess(successText);
      setForm(EMPTY_FORM);
    } catch (err) {
      showError(err.message);
    }
  };

  // FR-A13 / PF4: CLEAR-CURRENT-SCREEN (COTRN02C.cbl:754-757).
  const clearScreen = useCallback(() => {
    setForm(EMPTY_FORM);
    setMessage('');
    setIsError(false);
  }, []);

  // FR-A14 / PF3: return to caller/menu (COTRN02C.cbl:135-140).
  const backToMenu = useCallback(() => {
    navigate('/menu');
  }, [navigate]);

  // FR-A12 / PF5: COPY-LAST-TRAN-DATA (COTRN02C.cbl:471-495) — pre-fill the data
  // fields from the most recent transaction (keys are left as entered).
  const copyLast = useCallback(async () => {
    try {
      const last = await getLatestTransaction();
      if (!last) {
        showError('Unable to lookup Transaction...');
        return;
      }
      setForm((prev) => ({
        ...prev,
        typeCd: last.typeCd == null ? '' : String(last.typeCd),
        categoryCd: last.catCd == null ? '' : String(last.catCd),
        source: last.source || '',
        description: last.description || '',
        amount: last.amountDisplay || '',
        origDate: last.origTs || '',
        procDate: last.procTs || '',
        merchantId: last.merchantId == null ? '' : String(last.merchantId),
        merchantName: last.merchantName || '',
        merchantCity: last.merchantCity || '',
        merchantZip: last.merchantZip || '',
      }));
      setMessage('');
      setIsError(false);
    } catch (e) {
      showError(e.message);
    }
  }, []);

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
        copyLast();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [backToMenu, clearScreen, copyLast]);

  const onBlurFor = (key) => {
    if (key === 'accountId') {
      return resolveByAccount;
    }
    if (key === 'cardNumber') {
      return resolveByCard;
    }
    return undefined;
  };

  const renderField = (f) => (
    <div style={styles.row} key={f.key}>
      <label style={styles.label} htmlFor={f.key}>{f.label}:</label>
      <input
        id={f.key}
        style={styles.input}
        value={form[f.key]}
        maxLength={f.maxLength}
        placeholder={f.placeholder || ''}
        onChange={(e) => setField(f.key, e.target.value)}
        onBlur={onBlurFor(f.key)}
      />
    </div>
  );

  return (
    <Layout tranId="CT02" progName="COTRN02C" title="Add Transaction">
      <form style={styles.form} onSubmit={handleSubmit}>
        {KEY_FIELDS.map(renderField)}
        {DATA_FIELDS.map(renderField)}

        <div style={styles.row}>
          <label style={styles.label} htmlFor="confirm">Confirm (Y/N):</label>
          <input
            id="confirm"
            style={{ ...styles.input, width: '60px' }}
            value={form.confirm}
            maxLength={1}
            onChange={(e) => setField('confirm', e.target.value)}
          />
        </div>

        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Add</button>
          <button type="button" style={styles.button} onClick={backToMenu}>PF3 — Back</button>
          <button type="button" style={styles.button} onClick={clearScreen}>PF4 — Clear</button>
          <button type="button" style={styles.button} onClick={copyLast}>PF5 — Copy Last</button>
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
