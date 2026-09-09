import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';

/**
 * CAVW — View Account (COACTVWC / map COACTVW). Reproduces the legacy screen 1:1:
 *   - opens with "Enter or update id of account to display" (COACTVWC.cbl:333-341)
 *   - ENTER on an 11 digit non zero account id reads CXACAIX, ACCTDAT then CUSTDAT
 *     and shows the detail with "Displaying details of given Account"
 *   - a blank id gives "No input received", any other bad id gives
 *     "Account Filter must  be a non-zero 11 digit number" (COACTVWC.cbl:566-604)
 *   - a missing record gives the file specific "not found" text from the backend
 *   - F3 leaves the screen (COACTVWC.cbl:295-306)
 * Arriving with ?accountId=... loads that account straight away, which is how the
 * legacy program behaves when the caller passes CDEMO-ACCT-ID in the COMMAREA.
 */

const PROMPT = 'Enter or update id of account to display';

// The account half of map COACTVW, in map order (COACTVW.bms:83-200).
const ACCOUNT_FIELDS = [
  { key: 'activeStatus', label: 'Active Y/N:' },
  { key: 'openDate', label: 'Opened:' },
  { key: 'creditLimit', label: 'Credit Limit        :' },
  { key: 'expirationDate', label: 'Expiry:' },
  { key: 'cashCreditLimit', label: 'Cash credit Limit   :' },
  { key: 'reissueDate', label: 'Reissue:' },
  { key: 'currBal', label: 'Current Balance     :' },
  { key: 'currCycCredit', label: 'Current Cycle Credit:' },
  { key: 'groupId', label: 'Account Group:' },
  { key: 'currCycDebit', label: 'Current Cycle Debit :' },
];

// The customer half of map COACTVW, in map order (COACTVW.bms:206-350).
const CUSTOMER_FIELDS = [
  { key: 'custId', label: 'Customer id  :' },
  { key: 'ssn', label: 'SSN:' },
  { key: 'dateOfBirth', label: 'Date of birth:' },
  { key: 'ficoScore', label: 'FICO Score:' },
  { key: 'firstName', label: 'First Name' },
  { key: 'middleName', label: 'Middle Name: ' },
  { key: 'lastName', label: 'Last Name : ' },
  { key: 'addrLine1', label: 'Address:' },
  { key: 'addrLine2', label: 'Address:' },
  { key: 'state', label: 'State ' },
  { key: 'zip', label: 'Zip' },
  { key: 'city', label: 'City ' },
  { key: 'country', label: 'Country' },
  { key: 'phone1', label: 'Phone 1:' },
  { key: 'govtIssuedId', label: 'Government Issued Id Ref    : ' },
  { key: 'phone2', label: 'Phone 2:' },
  { key: 'eftAccountId', label: 'EFT Account Id: ' },
  { key: 'priCardHolderInd', label: 'Primary Card Holder Y/N:' },
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
  section: {
    fontFamily: "'Courier New', Courier, monospace",
    color: '#0b3d0b',
    margin: '12px 0 4px',
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
    width: '260px',
    color: '#0b3d0b',
    borderBottom: '1px solid #cfe8cf',
    verticalAlign: 'top',
  },
  td: {
    padding: '4px 10px',
    borderBottom: '1px solid #cfe8cf',
    whiteSpace: 'pre',
    color: '#12321a',
  },
};

function DetailRows({ fields, account }) {
  return fields.map((field) => (
    <tr key={field.label + field.key}>
      <th scope="row" style={styles.th}>{field.label}</th>
      <td style={styles.td}>{account[field.key] === null || account[field.key] === undefined
        ? ''
        : String(account[field.key])}
      </td>
    </tr>
  ));
}

export default function AccountViewPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [accountId, setAccountId] = useState('');
  const [account, setAccount] = useState(null);
  const [info, setInfo] = useState(PROMPT);
  const [error, setError] = useState('');

  const load = useCallback(async (rawId) => {
    const id = (rawId || '').trim();
    let res;
    let body = null;
    try {
      res = await fetch(`/api/accounts/${encodeURIComponent(id === '' ? ' ' : id)}`);
      body = await res.json();
    } catch (e) {
      body = null;
    }
    if (!res || !res.ok) {
      setAccount(null);
      setInfo('');
      setError((body && body.message) || 'Unable to lookup Account...');
      return;
    }
    setAccount(body);
    setError('');
    setInfo(body.infoMessage);
  }, []);

  // The caller's CDEMO-ACCT-ID: COACTVWC displays that account without a further ENTER.
  const didAutoLoad = useRef(false);
  useEffect(() => {
    if (didAutoLoad.current) {
      return;
    }
    const preselected = searchParams.get('accountId');
    if (preselected) {
      didAutoLoad.current = true;
      setAccountId(preselected);
      load(preselected);
    }
  }, [searchParams, load]);

  const handleSubmit = (e) => {
    e.preventDefault();
    load(accountId);
  };

  const exitScreen = useCallback(() => {
    // PF03 pressed.Exiting — back to the caller, the main menu here.
    navigate('/menu');
  }, [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        exitScreen();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [exitScreen]);

  return (
    <Layout tranId="CAVW" progName="COACTVWC" title="View Account" pfKeys="  F3=Exit ">
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
          <button type="submit" style={styles.button}>ENTER — View</button>
          <button type="button" style={styles.button} onClick={exitScreen}>F3 — Exit</button>
        </div>
      </form>

      <div style={styles.info}>{info}</div>
      <div style={styles.error} role="alert">{error}</div>

      {account ? (
        <table style={styles.detail}>
          <tbody>
            <DetailRows fields={ACCOUNT_FIELDS} account={account} />
            <tr>
              <th scope="row" style={styles.th}>Customer Details</th>
              <td style={styles.td} />
            </tr>
            <DetailRows fields={CUSTOMER_FIELDS} account={account} />
          </tbody>
        </table>
      ) : null}
    </Layout>
  );
}
