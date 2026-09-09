/**
 * REST client for the Reporting backend (S-07, transaction CR00).
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy).
 */

/**
 * CR00 — ENTER on the Transaction Reports screen (CORPT00C
 * POST /api/reports/transactions). The screen fields are sent exactly as typed;
 * every edit lives in the backend, as it did in the COBOL.
 *
 * Resolves to the submit result ({submitted, reportName, startDate, endDate,
 * message, cursor, fieldsCleared}). On a non-2xx response it throws an Error
 * carrying the exact legacy ERRMSG text plus the field CORPT00C put the cursor
 * on and the NUMVAL-C normalised date components to redisplay.
 */
export async function submitTransactionReport(fields) {
  const res = await fetch('/api/reports/transactions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(fields),
  });
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    const error = new Error((body && body.message) || 'Unable to Write TDQ (JOBS)...');
    error.cursor = body && body.cursor;
    error.dateFields = body && body.dateFields;
    throw error;
  }
  return body;
}
