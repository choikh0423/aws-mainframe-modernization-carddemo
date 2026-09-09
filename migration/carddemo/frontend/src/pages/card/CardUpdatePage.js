import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { loadCardForUpdate, updateCard } from '../../api/cards';

/**
 * CCUP — Update Credit Card Details (COCRDUPC). The screen walks the legacy
 * CCUP-CHANGE-ACTION state machine (COCRDUPC.cbl:948-1031):
 *   - ENTER with the two keys -> fetch the card, "Details of selected card
 *     shown above" (FR-U3)
 *   - ENTER with edits -> validate; unchanged values give "No change detected
 *     with respect to values fetched." (FR-U9), valid edits give
 *     "Changes validated.Press F5 to save" (FR-U15)
 *   - F5 -> rewrite under the CARDDAT lock, "Changes committed to database"
 *     (FR-U16/FR-U19); if the record moved meanwhile, "Record changed by some
 *     one else. Please review" and the refreshed values are shown (FR-U18)
 *   - F12 -> discard the edits and re-display the stored details (FR-U20)
 *   - F3 -> back to the caller (FR-U21)
 */

const styles = {
  form: { fontFamily: "'Courier New', Courier, monospace", marginBottom: '16px', lineHeight: '1.9' },
  label: { display: 'inline-block', width: '190px' },
  input: {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: '14px',
    padding: '4px 6px',
    width: '220px',
  },
  short: { width: '60px' },
  tiny: { width: '40px' },
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
};

export default function CardUpdatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [acctId, setAcctId] = useState(params.get('acctId') || '');
  const [cardNum, setCardNum] = useState(params.get('cardNum') || '');
  const [name, setName] = useState('');
  const [status, setStatus] = useState('');
  const [expiryMonth, setExpiryMonth] = useState('');
  const [expiryYear, setExpiryYear] = useState('');
  // CCUP-OLD-DETAILS: the values fetched, sent back with every submit so the
  // backend can tell whether the record moved underneath.
  const [snapshot, setSnapshot] = useState(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('Please enter Account and Card Number');

  const applyScreen = useCallback((data) => {
    setName(data.embossedName);
    setStatus(data.activeStatus);
    setExpiryMonth(data.expiryMonth);
    setExpiryYear(data.expiryYear);
    setSnapshot(data);
  }, []);

  const fetchCard = useCallback(async (acct, num) => {
    try {
      const data = await loadCardForUpdate({ acctId: acct, cardNum: num });
      applyScreen(data);
      setError('');
      setInfo(data.message);
    } catch (e) {
      setSnapshot(null);
      setError(e.message);
      setInfo('');
    }
  }, [applyScreen]);

  useEffect(() => {
    const acct = params.get('acctId');
    const num = params.get('cardNum');
    if (acct && num) {
      fetchCard(acct, num);
    }
  }, [params, fetchCard]);

  const submit = useCallback(async (confirmed) => {
    if (!snapshot) {
      await fetchCard(acctId, cardNum);
      return;
    }
    try {
      const data = await updateCard({
        acctId,
        cardNum,
        newName: name,
        newStatus: status,
        newExpiryMonth: expiryMonth,
        newExpiryYear: expiryYear,
        oldName: snapshot.embossedName,
        oldStatus: snapshot.activeStatus,
        oldExpiryMonth: snapshot.expiryMonth,
        oldExpiryYear: snapshot.expiryYear,
        oldExpiryDay: snapshot.expiryDay,
        oldCvvCd: snapshot.cvvCd,
        confirmed,
      });
      if (data.state === 'CHANGES_OKAYED_AND_DONE' || data.state === 'NO_CHANGES'
          || data.state === 'SHOW_DETAILS') {
        applyScreen(data);
      }
      setError('');
      setInfo(data.message);
    } catch (e) {
      if (e.refreshed) {
        applyScreen(e.refreshed);
      }
      setError(e.message);
      setInfo('');
    }
  }, [snapshot, acctId, cardNum, name, status, expiryMonth, expiryYear, applyScreen, fetchCard]);

  // F12: discard the edits and re-read the stored details.
  const cancelEdits = useCallback(() => {
    if (snapshot) {
      fetchCard(acctId, cardNum);
    }
  }, [snapshot, acctId, cardNum, fetchCard]);

  const exit = useCallback(() => navigate('/cards'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        exit();
      } else if (e.key === 'F5') {
        e.preventDefault();
        submit(true);
      } else if (e.key === 'F12') {
        e.preventDefault();
        cancelEdits();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [exit, submit, cancelEdits]);

  return (
    <Layout
      tranId="CM00"
      progName="COCRDUPC"
      title="Update Credit Card Details"
      pfKeys="ENTER=Process F3=Exit    F5=Save F12=Cancel"
    >
      <form
        style={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          submit(false);
        }}
      >
        <div>
          <label style={styles.label} htmlFor="acctId">Account Number    :</label>
          <input
            id="acctId"
            style={styles.input}
            value={acctId}
            maxLength={11}
            onChange={(e) => setAcctId(e.target.value)}
            autoFocus
          />
        </div>
        <div>
          <label style={styles.label} htmlFor="cardNum">Card Number       :</label>
          <input
            id="cardNum"
            style={styles.input}
            value={cardNum}
            maxLength={16}
            onChange={(e) => setCardNum(e.target.value)}
          />
        </div>
        <div>
          <label style={styles.label} htmlFor="name">Name on card      :</label>
          <input
            id="name"
            style={styles.input}
            value={name}
            maxLength={50}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div>
          <label style={styles.label} htmlFor="status">Card Active Y/N   : </label>
          <input
            id="status"
            style={{ ...styles.input, ...styles.tiny }}
            value={status}
            maxLength={1}
            onChange={(e) => setStatus(e.target.value)}
          />
        </div>
        <div>
          <label style={styles.label} htmlFor="expiryMonth">Expiry Date       : </label>
          <input
            id="expiryMonth"
            style={{ ...styles.input, ...styles.tiny }}
            value={expiryMonth}
            maxLength={2}
            onChange={(e) => setExpiryMonth(e.target.value)}
          />
          <span>/</span>
          <input
            id="expiryYear"
            aria-label="Expiry year"
            style={{ ...styles.input, ...styles.short }}
            value={expiryYear}
            maxLength={4}
            onChange={(e) => setExpiryYear(e.target.value)}
          />
        </div>
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER=Process</button>
          <button type="button" style={styles.button} onClick={exit}>F3=Exit</button>
          <button type="button" style={styles.button} onClick={() => submit(true)}>F5=Save</button>
          <button type="button" style={styles.button} onClick={cancelEdits}>F12=Cancel</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.info}>{error ? '' : info}</div>
    </Layout>
  );
}
