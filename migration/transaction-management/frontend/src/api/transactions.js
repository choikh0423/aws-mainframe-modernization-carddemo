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
