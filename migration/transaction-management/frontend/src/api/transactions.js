/**
 * REST client for the Transaction Management backend.
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy).
 */

/**
 * CT01 — fetch a single transaction by Tran ID (COTRN01C GET /api/transactions/{id}).
 * Resolves to the transaction detail on 200; on a non-2xx response it throws an
 * Error whose {@code message} is the exact legacy ERRMSG text from the backend
 * body (e.g. "Transaction ID NOT found...").
 */
export async function getTransaction(id) {
  const res = await fetch(`/api/transactions/${encodeURIComponent(id)}`);
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const message = (body && body.message) || 'Unable to lookup Transaction...';
    throw new Error(message);
  }
  return body;
}

/**
 * CT00 — fetch a page of transactions (COTRN00C GET /api/transactions?startId&dir).
 * {@code startId} is the start-from Tran ID filter (ENTER) or the paging cursor
 * (the current page's first/last id for PF7/PF8); {@code dir} is
 * {@code 'next'} (PF8), {@code 'prev'} (PF7) or omitted (ENTER / initial open).
 * Resolves to the page payload ({rows, firstId, lastId, hasNextPage,
 * hasPrevPage, count}); on a non-2xx response (e.g. a non-numeric filter) it
 * throws an Error whose {@code message} is the exact legacy ERRMSG text.
 */
export async function listTransactions({ startId, dir } = {}) {
  const params = new URLSearchParams();
  if (startId) {
    params.set('startId', startId);
  }
  if (dir) {
    params.set('dir', dir);
  }
  const qs = params.toString();
  const res = await fetch(`/api/transactions${qs ? `?${qs}` : ''}`);
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const message = (body && body.message) || 'Unable to lookup transaction...';
    throw new Error(message);
  }
  return body;
}

/**
 * CT02 — resolve the account/card cross-reference (COTRN02C
 * VALIDATE-INPUT-KEY-FIELDS). Pass {@code accountId} to resolve the card (FR-A1)
 * or {@code cardNumber} to resolve the account (FR-A2). Resolves to
 * {accountId, cardNumber}; on a non-2xx response it throws an Error whose
 * {@code message} is the exact legacy ERRMSG ("Account ID NOT found...", etc.).
 */
export async function resolveCardXref({ accountId, cardNumber } = {}) {
  const params = new URLSearchParams();
  if (accountId) {
    params.set('accountId', accountId);
  }
  if (cardNumber) {
    params.set('cardNumber', cardNumber);
  }
  const res = await fetch(`/api/cardxref/resolve?${params.toString()}`);
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const message = (body && body.message) || 'Unable to lookup Card/Acct in XREF file...';
    throw new Error(message);
  }
  return body;
}

/**
 * CT02 — add a transaction (COTRN02C POST /api/transactions). {@code request}
 * carries every CT02 screen field as a raw string. Resolves to
 * {tranId, message} with the exact legacy green success text on 201; on a
 * non-2xx response it throws an Error whose {@code message} is the exact legacy
 * ERRMSG (validation, not-found or "Tran ID already exist...").
 */
export async function addTransaction(request) {
  const res = await fetch('/api/transactions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const message = (body && body.message) || 'Unable to Add Transaction...';
    throw new Error(message);
  }
  return body;
}

/**
 * CT02 — most recent transaction for PF5 copy-last (COTRN02C
 * COPY-LAST-TRAN-DATA, FR-A12). Resolves to the transaction detail on 200 or
 * {@code null} on 204 (no transactions yet).
 */
export async function getLatestTransaction() {
  const res = await fetch('/api/transactions/latest');
  if (res.status === 204) {
    return null;
  }
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const message = (body && body.message) || 'Unable to lookup Transaction...';
    throw new Error(message);
  }
  return body;
}
