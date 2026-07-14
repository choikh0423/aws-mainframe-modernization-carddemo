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
