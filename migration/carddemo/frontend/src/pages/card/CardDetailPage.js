import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import { getCardDetail } from '../../api/cards';

/**
 * CCDL — View Credit Card Detail (COCRDSLC). The BMS map COCRDSL shows the two
 * search fields and, once a card is read, the name / active flag / expiry:
 *   - ENTER with both keys -> read CARDDAT by card number and display the card
 *     (FR-D6/FR-D7, COCRDSLC.cbl:726-805, 457-497)
 *   - a missing or malformed key -> the legacy edit message, nothing read
 *     (FR-D2..FR-D5, cbl:637-720)
 *   - arriving from CCLI with both keys already known -> the read happens on
 *     entry and the key fields are protected (cbl:502-512)
 *   - PF3 -> back to the caller, or the main menu (FR-D9, cbl:268-381)
 */

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
  detail: { fontFamily: "'Courier New', Courier, monospace", lineHeight: '1.8' },
  value: { fontWeight: 'bold' },
};

export default function CardDetailPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [acctId, setAcctId] = useState(params.get('acctId') || '');
  const [cardNum, setCardNum] = useState(params.get('cardNum') || '');
  const [card, setCard] = useState(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('Please enter Account and Card Number');

  const search = useCallback(async (acct, num) => {
    try {
      const data = await getCardDetail({ acctId: acct, cardNum: num });
      setCard(data);
      setError('');
      setInfo(data.infoMessage);
    } catch (e) {
      setCard(null);
      setError(e.message);
      setInfo('');
    }
  }, []);

  // Reached from CCLI with both keys in the COMMAREA: read straight away.
  useEffect(() => {
    const acct = params.get('acctId');
    const num = params.get('cardNum');
    if (acct && num) {
      search(acct, num);
    }
  }, [params, search]);

  const exit = useCallback(() => navigate('/cards'), [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        exit();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [exit]);

  return (
    <Layout
      tranId="CM00"
      progName="COCRDSLC"
      title="View Credit Card Detail"
      pfKeys="ENTER=Search Cards  F3=Exit"
    >
      <form
        style={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          search(acctId, cardNum);
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
        <div style={styles.keys}>
          <button type="submit" style={styles.button}>ENTER=Search Cards</button>
          <button type="button" style={styles.button} onClick={exit}>F3=Exit</button>
        </div>
      </form>

      <div style={styles.error} role="alert">{error}</div>
      <div style={styles.info}>{error ? '' : info}</div>

      {card ? (
        <div style={styles.detail}>
          <div>
            <span style={styles.label}>Name on card      :</span>
            <span style={styles.value}>{card.embossedName}</span>
          </div>
          <div>
            <span style={styles.label}>Card Active Y/N   : </span>
            <span style={styles.value}>{card.activeStatus}</span>
          </div>
          <div>
            <span style={styles.label}>Expiry Date       : </span>
            <span style={styles.value}>{`${card.expiryMonth}/${card.expiryYear}`}</span>
          </div>
        </div>
      ) : null}
    </Layout>
  );
}
