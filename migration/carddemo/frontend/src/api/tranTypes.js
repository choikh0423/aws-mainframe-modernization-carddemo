/**
 * REST client for S-08 TransactionTypeManagement (CTLI COTRTLIC, CTTU COTRTUPC).
 *
 * Both endpoints are one round trip per keystroke, the way a CICS pseudo
 * conversation is: the screen posts the AID key, the unprotected fields and the
 * COMMAREA (`state`) it was last handed, and the backend returns the next map
 * plus the new COMMAREA. The screens never interpret the state; they only carry
 * it back, exactly as DFHCOMMAREA does.
 */

async function post(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`Unable to reach ${url} (HTTP ${res.status})`);
  }
  return res.json();
}

/** CTLI — one keystroke on the transaction-type list map (CTRTLIA). */
export async function sendTranTypeList({ aid, typeFilter, descFilter, rows, state }) {
  return post('/api/admin/transaction-types/list', {
    aid,
    typeFilter,
    descFilter,
    rows,
    state,
  });
}

/** CTTU — one keystroke on the transaction-type maintenance map (CTRTUPA). */
export async function sendTranTypeUpdate({ aid, typeCode, description, state }) {
  return post('/api/admin/transaction-types/update', {
    aid,
    typeCode,
    description,
    state,
  });
}
