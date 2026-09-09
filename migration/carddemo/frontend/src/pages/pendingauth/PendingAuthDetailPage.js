import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Layout from '../../components/Layout';
import {
  getNextPendingAuthorizationDetail,
  getPendingAuthorizationDetail,
  markPendingAuthorizationFraud,
} from '../../api/pendingAuthorizations';

/**
 * CPVD — View Authorization Details (COPAUS1C / map COPAU1A):
 *   - entry from CPVS shows the selected authorization (FR-D1, cbl:291-357)
 *   - an unusable account id or key leaves the detail area blank with no
 *     message, exactly as the legacy program does (quirk Q-2, FR-D3)
 *   - PF8 -> the next authorization of the account; at the end ->
 *     "Already at the last Authorization..." (FR-D5, cbl:225-249)
 *   - PF5 -> toggle fraud: 'F' becomes 'R' with "AUTH FRAUD REMOVED...",
 *     anything else becomes 'F' with "AUTH MARKED FRAUD...", both after the
 *     AUTHFRDS write COPAUS2C performs (FR-D6/FR-D7, cbl:196-223)
 *   - PF3 -> back to CPVS (FR-D8)
 *
 * "Auth Code:" shows PA-PROCESSING-CODE rather than PA-AUTH-ID-CODE, which is
 * what the source moves to AUTHCDO (quirk Q-3).
 */

const EMPTY_DETAIL = {
  authKey: null,
  cardNumber: '',
  authDate: '',
  authTime: '',
  authAmount: '',
  authResponse: '',
  authReason: '',
  authCode: '',
  posEntryMode: '',
  authSource: '',
  mccCode: '',
  cardExpiry: '',
  authType: '',
  transactionId: '',
  matchStatus: '',
  merchantName: '',
  merchantId: '',
  merchantCity: '',
  merchantState: '',
  merchantZip: '',
  fraud: '',
  found: false,
  message: '',
};

const mono = "'Courier New', Courier, monospace";

const styles = {
  keys: { marginBottom: '12px', display: 'flex', gap: '8px' },
  button: { fontFamily: mono, fontSize: '13px', padding: '6px 12px', cursor: 'pointer' },
  message: {
    color: '#b00020',
    fontFamily: mono,
    fontWeight: 'bold',
    minHeight: '20px',
    marginBottom: '12px',
  },
  panel: { fontFamily: mono, fontSize: '13px', color: '#12321a', maxWidth: '820px' },
  row: { display: 'flex', gap: '24px', padding: '2px 0' },
  cell: { minWidth: '300px' },
  section: {
    marginTop: '12px',
    paddingTop: '8px',
    borderTop: '1px solid #cfe8cf',
    fontWeight: 'bold',
  },
};

function Field({ label, value }) {
  return (
    <div style={styles.cell}>
      {label} {value}
    </div>
  );
}

export default function PendingAuthDetailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const acctId = searchParams.get('acctId') || '';
  const initialKey = searchParams.get('authKey') || '';

  const [detail, setDetail] = useState(EMPTY_DETAIL);
  const [authKey, setAuthKey] = useState(initialKey);
  const [message, setMessage] = useState('');

  const apply = useCallback((data) => {
    setDetail(data);
    setMessage(data.message || '');
    if (data.authKey) {
      setAuthKey(data.authKey);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    getPendingAuthorizationDetail({ acctId, authKey: initialKey })
      .then((data) => {
        if (!cancelled) {
          apply(data);
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setMessage(e.message);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [acctId, initialKey, apply]);

  const nextAuth = useCallback(async () => {
    try {
      apply(await getNextPendingAuthorizationDetail({ acctId, authKey }));
    } catch (e) {
      setMessage(e.message);
    }
  }, [acctId, authKey, apply]);

  const toggleFraud = useCallback(async () => {
    try {
      apply(await markPendingAuthorizationFraud({ acctId, authKey }));
    } catch (e) {
      setMessage(e.message);
    }
  }, [acctId, authKey, apply]);

  const back = useCallback(() => {
    navigate('/pending-authorizations');
  }, [navigate]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'F3') {
        e.preventDefault();
        back();
      } else if (e.key === 'F5') {
        e.preventDefault();
        toggleFraud();
      } else if (e.key === 'F8') {
        e.preventDefault();
        nextAuth();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [back, toggleFraud, nextAuth]);

  return (
    <Layout tranId="CPVD" progName="COPAUS1C" title="View Authorization Details">
      <div style={styles.keys}>
        <button type="button" style={styles.button} onClick={back}>F3=Back</button>
        <button type="button" style={styles.button} onClick={toggleFraud}>
          F5=Mark/Remove Fraud
        </button>
        <button type="button" style={styles.button} onClick={nextAuth}>F8=Next Auth</button>
      </div>

      <div style={styles.message} role="alert">{message}</div>

      <div style={styles.panel}>
        <div style={styles.row}>
          <Field label="Card #:" value={detail.cardNumber} />
        </div>
        <div style={styles.row}>
          <Field label="Auth Date:" value={detail.authDate} />
          <Field label="Auth Time:" value={detail.authTime} />
        </div>
        <div style={styles.row}>
          <Field label="Auth Resp:" value={detail.authResponse} />
          <Field label="Resp Reason:" value={detail.authReason} />
        </div>
        <div style={styles.row}>
          <Field label="Auth Code:" value={detail.authCode} />
          <Field label="Amount:" value={detail.authAmount} />
        </div>
        <div style={styles.row}>
          <Field label="POS Entry Mode:" value={detail.posEntryMode} />
          <Field label="Source   :" value={detail.authSource} />
        </div>
        <div style={styles.row}>
          <Field label="MCC Code:" value={detail.mccCode} />
          <Field label="Card Exp. Date:" value={detail.cardExpiry} />
        </div>
        <div style={styles.row}>
          <Field label="Auth Type:" value={detail.authType} />
          <Field label="Tran Id:" value={detail.transactionId} />
        </div>
        <div style={styles.row}>
          <Field label="Match Status:" value={detail.matchStatus} />
          <Field label="Fraud Status:" value={detail.fraud} />
        </div>

        <div style={styles.section}>Merchant Details</div>
        <div style={styles.row}>
          <Field label="Name:" value={detail.merchantName} />
          <Field label="Merchant ID:" value={detail.merchantId} />
        </div>
        <div style={styles.row}>
          <Field label="City:" value={detail.merchantCity} />
          <Field label="State:" value={detail.merchantState} />
        </div>
        <div style={styles.row}>
          <Field label="Zip:" value={detail.merchantZip} />
        </div>
      </div>
    </Layout>
  );
}
