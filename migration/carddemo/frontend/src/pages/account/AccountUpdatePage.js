import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';

/**
 * CAUP — Update Account (COACTUPC / map COACTUP). Reproduces the legacy turn
 * sequence, which the backend carries as an explicit state instead of the
 * COMMAREA flags CDEMO-ACUP-*:
 *   - opens with "Enter or update id of account to update"
 *   - ENTER on an account id fetches the record: "Update account details presented above."
 *   - ENTER again edits the screen: the first failing edit is shown, an untouched
 *     screen gives "No change detected with respect to values fetched.", a clean
 *     change gives "Changes validated.Press F5 to save"
 *   - F5 re-runs the edits, re-reads both records under a lock and rewrites them:
 *     "Changes committed to database"
 *   - once validated the fields are protected until F5 or F12, as COACTUPC protects them
 *   - F12 abandons the changes and re-presents the stored record
 *   - F3 leaves the screen
 * F5 is only accepted once the changes are validated and F12 only once the
 * details are on the screen, exactly as COACTUPC's AID handling allows.
 */

const PROMPT = 'Enter or update id of account to update';

// Map COACTUP in map order (COACTUP.bms:93-473). `parts` splits a field the way
// the map does, so the screen keeps the legacy sub-field boundaries.
const ACCOUNT_FIELDS = [
  { label: 'Active Y/N: ', parts: [{ key: 'activeStatus', size: 1 }] },
  {
    label: 'Opened :',
    parts: [{ key: 'openYear', size: 4 }, { key: 'openMonth', size: 2 }, { key: 'openDay', size: 2 }],
  },
  { label: 'Credit Limit        :', parts: [{ key: 'creditLimit', size: 15 }] },
  {
    label: 'Expiry :',
    parts: [
      { key: 'expiryYear', size: 4 },
      { key: 'expiryMonth', size: 2 },
      { key: 'expiryDay', size: 2 },
    ],
  },
  { label: 'Cash credit Limit   :', parts: [{ key: 'cashCreditLimit', size: 15 }] },
  {
    label: 'Reissue:',
    parts: [
      { key: 'reissueYear', size: 4 },
      { key: 'reissueMonth', size: 2 },
      { key: 'reissueDay', size: 2 },
    ],
  },
  { label: 'Current Balance     :', parts: [{ key: 'currBal', size: 15 }] },
  { label: 'Current Cycle Credit:', parts: [{ key: 'currCycCredit', size: 15 }] },
  { label: 'Account Group:', parts: [{ key: 'groupId', size: 10 }] },
  { label: 'Current Cycle Debit :', parts: [{ key: 'currCycDebit', size: 15 }] },
];

const CUSTOMER_FIELDS = [
  { label: 'Customer id  :', parts: [{ key: 'custId', size: 9, readOnly: true }] },
  {
    label: 'SSN:',
    parts: [{ key: 'ssnPart1', size: 3 }, { key: 'ssnPart2', size: 2 }, { key: 'ssnPart3', size: 4 }],
  },
  {
    label: 'Date of birth:',
    parts: [{ key: 'dobYear', size: 4 }, { key: 'dobMonth', size: 2 }, { key: 'dobDay', size: 2 }],
  },
  { label: 'FICO Score:', parts: [{ key: 'ficoScore', size: 3 }] },
  { label: 'First Name', parts: [{ key: 'firstName', size: 25 }] },
  { label: 'Middle Name: ', parts: [{ key: 'middleName', size: 25 }] },
  { label: 'Last Name : ', parts: [{ key: 'lastName', size: 25 }] },
  { label: 'Address:', parts: [{ key: 'addrLine1', size: 50 }] },
  { label: 'Address:', parts: [{ key: 'addrLine2', size: 50 }] },
  { label: 'State ', parts: [{ key: 'state', size: 2 }] },
  { label: 'Zip', parts: [{ key: 'zip', size: 5 }] },
  { label: 'City ', parts: [{ key: 'city', size: 50 }] },
  { label: 'Country', parts: [{ key: 'country', size: 3 }] },
  {
    label: 'Phone 1:',
    parts: [
      { key: 'phone1Area', size: 3 },
      { key: 'phone1Prefix', size: 3 },
      { key: 'phone1Line', size: 4 },
    ],
  },
  { label: 'Government Issued Id Ref    : ', parts: [{ key: 'govtIssuedId', size: 20 }] },
  {
    label: 'Phone 2:',
    parts: [
      { key: 'phone2Area', size: 3 },
      { key: 'phone2Prefix', size: 3 },
      { key: 'phone2Line', size: 4 },
    ],
  },
  { label: 'EFT Account Id: ', parts: [{ key: 'eftAccountId', size: 10 }] },
  { label: 'Primary Card Holder Y/N:', parts: [{ key: 'priCardHolderInd', size: 1 }] },
];

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px' },
  label: { display: 'inline-block', width: '160px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '160px',
  },
  keys: { marginTop: '12px', display: 'flex', gap: '8px' },
  button: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '13px',
    padding: '6px 12px',
    cursor: 'pointer',
  },
  info: {
    color: '#0b3d0b',
    fontFamily: "'Courier New', Courier, monospace",
    minHeight: '20px',
    marginBottom: '4px',
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
    maxWidth: '760px',
  },
  th: {
    textAlign: 'left',
    padding: '4px 10px',
    width: '260px',
    color: '#0b3d0b',
    borderBottom: '1px solid #cfe8cf',
    verticalAlign: 'top',
  },
  td: { padding: '4px 10px', borderBottom: '1px solid #cfe8cf' },
  part: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '2px 4px',
    marginRight: '6px',
  },
};

async function readJson(res) {
  try {
    return await res.json();
  } catch (e) {
    return null;
  }
}

export default function AccountUpdatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [accountId, setAccountId] = useState('');
  const [original, setOriginal] = useState(null);
  const [fields, setFields] = useState(null);
  const [state, setState] = useState('DETAILS_NOT_FETCHED');
  const [info, setInfo] = useState(PROMPT);
  const [error, setError] = useState('');

  const apply = useCallback((body) => {
    setState(body.state);
    setInfo(body.infoMessage || '');
    setError(body.errorMessage || '');
    if (body.details) {
      setFields(body.details);
    }
  }, []);

  const fetchDetails = useCallback(async (rawId) => {
    const id = (rawId || '').trim();
    let res = null;
    let body = null;
    try {
      res = await fetch(`/api/accounts/${encodeURIComponent(id === '' ? ' ' : id)}/update`);
      body = await readJson(res);
    } catch (e) {
      body = null;
    }
    if (!res || !res.ok) {
      setOriginal(null);
      setFields(null);
      setState('DETAILS_NOT_FETCHED');
      setInfo('');
      setError((body && body.message) || 'Unable to lookup Account...');
      return;
    }
    setOriginal(body.details);
    apply(body);
  }, [apply]);

  const post = useCallback(async (action) => {
    if (!fields || !original) {
      return;
    }
    let res = null;
    let body = null;
    try {
      res = await fetch(`/api/accounts/${encodeURIComponent(accountId.trim())}/update/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ original, updated: fields }),
      });
      body = await readJson(res);
    } catch (e) {
      body = null;
    }
    if (!res || !res.ok) {
      setInfo('');
      setError((body && body.message) || 'Unable to update Account...');
      return;
    }
    if (action === 'save' && body.details) {
      setOriginal(body.details);
    }
    apply(body);
  }, [accountId, apply, fields, original]);

  const didAutoLoad = useRef(false);
  useEffect(() => {
    if (didAutoLoad.current) {
      return;
    }
    const preselected = searchParams.get('accountId');
    if (preselected) {
      didAutoLoad.current = true;
      setAccountId(preselected);
      fetchDetails(preselected);
    }
  }, [searchParams, fetchDetails]);

  const detailsShown = fields !== null;
  const canSave = state === 'CHANGES_OK_NOT_CONFIRMED';

  const handleSubmit = (e) => {
    e.preventDefault();
    // ENTER: fetch the record the first time, edit the screen afterwards.
    if (detailsShown) {
      post('validate');
    } else {
      fetchDetails(accountId);
    }
  };

  const save = useCallback(() => {
    if (canSave) {
      post('save');
    }
  }, [canSave, post]);

  const cancel = useCallback(() => {
    // F12: throw the typed changes away and re-present the stored record.
    if (detailsShown) {
      fetchDetails(accountId);
    }
  }, [accountId, detailsShown, fetchDetails]);

  const exitScreen = useCallback(() => {
    navigate('/menu');
  }, [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        exitScreen();
      } else if (e.key === 'F5') {
        e.preventDefault();
        save();
      } else if (e.key === 'F12') {
        e.preventDefault();
        cancel();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [cancel, exitScreen, save]);

  const setPart = (key, value) => {
    setFields((current) => ({ ...current, [key]: value }));
  };

  const renderRows = (rows) => rows.map((row) => (
    <tr key={row.label + row.parts[0].key}>
      <th scope="row" style={styles.th}>{row.label}</th>
      <td style={styles.td}>
        {row.parts.map((part) => (
          <input
            key={part.key}
            aria-label={`${row.label} ${part.key}`}
            style={{ ...styles.part, width: `${Math.min(part.size, 30) * 9 + 12}px` }}
            value={fields[part.key] === null || fields[part.key] === undefined ? '' : fields[part.key]}
            maxLength={part.size}
            readOnly={Boolean(part.readOnly) || canSave}
            onChange={(e) => setPart(part.key, e.target.value)}
          />
        ))}
      </td>
    </tr>
  ));

  return (
    <Layout
      tranId="CAUP"
      progName="COACTUPC"
      title="Update Account"
      pfKeys="ENTER=Process F3=Exit  F5=Save  F12=Cancel"
    >
      <form style={styles.form} onSubmit={handleSubmit}>
        <label style={styles.label} htmlFor="accountId">Account Number :</label>
        <input
          id="accountId"
          style={styles.input}
          value={accountId}
          maxLength={11}
          onChange={(e) => setAccountId(e.target.value)}
          autoFocus
        />
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER — Process</button>
          <button type="button" style={styles.button} onClick={exitScreen}>F3 — Exit</button>
          <button type="button" style={styles.button} onClick={save} disabled={!canSave}>F5 — Save</button>
          <button type="button" style={styles.button} onClick={cancel} disabled={!detailsShown}>
            F12 — Cancel
          </button>
        </div>

        <div style={{ marginTop: '12px' }}>
          <div style={styles.info}>{info}</div>
          <div style={styles.error} role="alert">{error}</div>
        </div>

        {detailsShown ? (
          <table style={styles.detail}>
            <tbody>
              {renderRows(ACCOUNT_FIELDS)}
              <tr>
                <th scope="row" style={styles.th}>Customer Details</th>
                <td style={styles.td} />
              </tr>
              {renderRows(CUSTOMER_FIELDS)}
            </tbody>
          </table>
        ) : null}
      </form>
    </Layout>
  );
}
