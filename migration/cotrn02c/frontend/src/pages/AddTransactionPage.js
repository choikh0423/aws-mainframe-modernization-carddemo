import React, { useState } from 'react';

const FIELD_DEFS = [
  { key: 'acctId', label: 'Account ID', maxLen: 11 },
  { key: 'cardNum', label: 'Card Number', maxLen: 16 },
  { key: 'typeCd', label: 'Type CD', maxLen: 2 },
  { key: 'catCd', label: 'Category CD', maxLen: 4 },
  { key: 'source', label: 'Source', maxLen: 10 },
  { key: 'description', label: 'Description', maxLen: 100 },
  { key: 'amount', label: 'Amount (+99999999.99)', maxLen: 12 },
  { key: 'origDate', label: 'Orig Date (YYYY-MM-DD)', maxLen: 10 },
  { key: 'procDate', label: 'Proc Date (YYYY-MM-DD)', maxLen: 10 },
  { key: 'merchantId', label: 'Merchant ID', maxLen: 9 },
  { key: 'merchantName', label: 'Merchant Name', maxLen: 50 },
  { key: 'merchantCity', label: 'Merchant City', maxLen: 50 },
  { key: 'merchantZip', label: 'Merchant Zip', maxLen: 10 },
];

const INITIAL_FORM = {};
FIELD_DEFS.forEach(f => { INITIAL_FORM[f.key] = ''; });

const API_BASE = '/api/transactions';

function AddTransactionPage() {
  const [form, setForm] = useState({ ...INITIAL_FORM });
  const [confirm, setConfirm] = useState('');
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState(''); // 'success' | 'error' | 'info'
  const [errorField, setErrorField] = useState('');
  const [resolvedCard, setResolvedCard] = useState('');
  const [resolvedAcct, setResolvedAcct] = useState('');
  const [validated, setValidated] = useState(false);

  const handleChange = (key, value) => {
    setForm(prev => ({ ...prev, [key]: value }));
    if (errorField === key) {
      setErrorField('');
      setMessage('');
    }
  };

  const handleValidate = async () => {
    setMessage('');
    setErrorField('');
    try {
      const resp = await fetch(`${API_BASE}/validate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      const data = await resp.json();
      if (data.success) {
        setResolvedCard(data.resolvedCardNum || '');
        setResolvedAcct(data.resolvedAcctId || '');
        setMessage(data.message);
        setMessageType('info');
        setValidated(true);
      } else {
        setMessage(data.message);
        setMessageType('error');
        setErrorField(data.errorField || '');
        setValidated(false);
      }
    } catch (err) {
      setMessage('Network error: ' + err.message);
      setMessageType('error');
    }
  };

  const handleSubmit = async () => {
    setMessage('');
    setErrorField('');
    try {
      const body = { ...form, confirm };
      const resp = await fetch(API_BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await resp.json();
      if (data.success) {
        setMessage(data.message);
        setMessageType('success');
        setForm({ ...INITIAL_FORM });
        setConfirm('');
        setResolvedCard('');
        setResolvedAcct('');
        setValidated(false);
      } else {
        setMessage(data.message);
        setMessageType('error');
        setErrorField(data.errorField || '');
      }
    } catch (err) {
      setMessage('Network error: ' + err.message);
      setMessageType('error');
    }
  };

  const handleCopyLast = async () => {
    setMessage('');
    setErrorField('');
    try {
      const params = new URLSearchParams();
      if (form.acctId) params.set('acctId', form.acctId);
      if (form.cardNum) params.set('cardNum', form.cardNum);
      const resp = await fetch(`${API_BASE}/last?${params.toString()}`);
      const data = await resp.json();
      if (data.success && data.typeCd) {
        setForm(prev => ({
          ...prev,
          typeCd: data.typeCd || '',
          catCd: data.catCd || '',
          source: data.source || '',
          description: data.description || '',
          amount: data.amount || '',
          origDate: data.origDate || '',
          procDate: data.procDate || '',
          merchantId: data.merchantId || '',
          merchantName: data.merchantName || '',
          merchantCity: data.merchantCity || '',
          merchantZip: data.merchantZip || '',
        }));
        setMessage('Last transaction data copied. Press ENTER to validate.');
        setMessageType('info');
      } else {
        setMessage(data.message || 'No transaction data to copy.');
        setMessageType(data.success ? 'info' : 'error');
        if (data.errorField) setErrorField(data.errorField);
      }
    } catch (err) {
      setMessage('Network error: ' + err.message);
      setMessageType('error');
    }
  };

  const msgColor = messageType === 'success' ? '#2e7d32'
    : messageType === 'error' ? '#c62828'
    : '#1565c0';

  return (
    <div style={{ maxWidth: '700px', margin: '0 auto' }}>
      <h2 style={{ fontFamily: 'monospace', borderBottom: '2px solid #333', paddingBottom: '8px' }}>
        Add Transaction (CT02)
      </h2>

      {message && (
        <div style={{
          padding: '10px 16px', marginBottom: '16px', borderRadius: '4px',
          background: messageType === 'success' ? '#e8f5e9' : messageType === 'error' ? '#ffebee' : '#e3f2fd',
          color: msgColor, fontFamily: 'monospace', fontSize: '13px', border: `1px solid ${msgColor}`,
        }}>
          {message}
        </div>
      )}

      {resolvedCard && (
        <div style={{ fontFamily: 'monospace', fontSize: '12px', color: '#555', marginBottom: '8px' }}>
          Resolved Card: {resolvedCard} | Resolved Acct: {resolvedAcct}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '200px 1fr', gap: '8px 12px', alignItems: 'center' }}>
        {FIELD_DEFS.map(f => (
          <React.Fragment key={f.key}>
            <label style={{
              fontFamily: 'monospace', fontSize: '13px', textAlign: 'right',
              color: errorField === f.key ? '#c62828' : '#333', fontWeight: errorField === f.key ? 'bold' : 'normal',
            }}>
              {f.label}:
            </label>
            <input
              type="text"
              value={form[f.key]}
              onChange={e => handleChange(f.key, e.target.value)}
              maxLength={f.maxLen}
              style={{
                fontFamily: 'monospace', fontSize: '13px', padding: '4px 8px',
                border: errorField === f.key ? '2px solid #c62828' : '1px solid #999',
                borderRadius: '3px', width: '100%', boxSizing: 'border-box',
              }}
            />
          </React.Fragment>
        ))}

        {validated && (
          <>
            <label style={{ fontFamily: 'monospace', fontSize: '13px', textAlign: 'right',
              color: errorField === 'confirm' ? '#c62828' : '#333',
              fontWeight: errorField === 'confirm' ? 'bold' : 'normal',
            }}>
              Confirm (Y/N):
            </label>
            <input
              type="text"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              maxLength={1}
              style={{
                fontFamily: 'monospace', fontSize: '13px', padding: '4px 8px',
                border: errorField === 'confirm' ? '2px solid #c62828' : '1px solid #999',
                borderRadius: '3px', width: '60px',
              }}
            />
          </>
        )}
      </div>

      <div style={{ marginTop: '16px', display: 'flex', gap: '12px' }}>
        <button onClick={handleValidate} style={btnStyle('#1565c0')}>
          ENTER (Validate)
        </button>
        {validated && (
          <button onClick={handleSubmit} style={btnStyle('#2e7d32')}>
            ENTER (Submit)
          </button>
        )}
        <button onClick={handleCopyLast} style={btnStyle('#6a1b9a')}>
          PF5 (Copy Last)
        </button>
        <button onClick={() => { setForm({ ...INITIAL_FORM }); setConfirm(''); setMessage(''); setErrorField(''); setResolvedCard(''); setResolvedAcct(''); setValidated(false); }} style={btnStyle('#757575')}>
          PF3 (Clear/Exit)
        </button>
      </div>

      <div style={{ marginTop: '24px', fontFamily: 'monospace', fontSize: '11px', color: '#999' }}>
        Maps to CICS transaction CT02 (COTRN02C.cbl). ENTER=validate, PF3=exit, PF5=copy last.
      </div>
    </div>
  );
}

function btnStyle(bg) {
  return {
    fontFamily: 'monospace', fontSize: '13px', padding: '8px 20px',
    background: bg, color: '#fff', border: 'none', borderRadius: '4px',
    cursor: 'pointer',
  };
}

export default AddTransactionPage;
