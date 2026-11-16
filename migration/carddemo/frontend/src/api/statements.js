/**
 * REST client for the S-21 OnlineStatements backend (CMD-4, com.carddemo.statements).
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy).
 *
 * A non-2xx body is the shared ErrorResponse, whose `message` is thrown unchanged
 * so the screen can show the API's validation text verbatim on its message line.
 */

export const STATEMENTS_PAGE_SIZE = 10;

async function readBody(res) {
  try {
    return await res.json();
  } catch (e) {
    return null;
  }
}

/**
 * Normalises one page of GET /api/statements?acctId=&page= (0-based page,
 * newest cycle first) into {rows, page, hasNext}. Accepts the API's own
 * envelope ({statements, page, totalPages, hasNext}) or a Spring Page
 * ({content, number, totalPages, last}).
 */
function toPage(body, requestedPage) {
  const data = body || {};
  let rows = [];
  if (Array.isArray(data)) {
    rows = data;
  } else if (Array.isArray(data.statements)) {
    rows = data.statements;
  } else if (Array.isArray(data.content)) {
    rows = data.content;
  }
  let page = requestedPage;
  if (Number.isInteger(data.page)) {
    page = data.page;
  } else if (Number.isInteger(data.number)) {
    page = data.number;
  }
  let hasNext;
  if (typeof data.hasNext === 'boolean') {
    hasNext = data.hasNext;
  } else if (typeof data.last === 'boolean') {
    hasNext = !data.last;
  } else if (Number.isInteger(data.totalPages)) {
    hasNext = page + 1 < data.totalPages;
  } else {
    hasNext = rows.length >= STATEMENTS_PAGE_SIZE;
  }
  return { rows, page, hasNext };
}

/** COSTM00C — one page of an account's archived statements. */
export async function listStatements({ acctId, page = 0 }) {
  const params = new URLSearchParams();
  params.set('acctId', acctId);
  params.set('page', String(page));
  const res = await fetch(`/api/statements?${params.toString()}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to lookup statements...');
  }
  return toPage(body, page);
}

/** COSTM01C — the stored HTML statement, served text/html under a no-script CSP. */
export function statementHtmlUrl(stmtId) {
  return `/api/statements/${encodeURIComponent(stmtId)}/html`;
}

/** COSTM01C — the text statement rendered to PDF (CMD-5, boundary B-13). */
export function statementPdfUrl(stmtId) {
  return `/api/statements/${encodeURIComponent(stmtId)}/pdf`;
}

/**
 * COSTM01C — GET /api/statements/{stmtId}: one statement plus its account and
 * customer header fields. A 404 (the statement no longer exists) throws the
 * API's ErrorResponse `message`, or `notFoundMessage` when there is none.
 */
export async function getStatement(stmtId, { notFoundMessage } = {}) {
  const res = await fetch(`/api/statements/${encodeURIComponent(stmtId)}`);
  const body = await readBody(res);
  if (!res.ok) {
    const fallback = res.status === 404 && notFoundMessage
      ? notFoundMessage
      : 'Unable to lookup statement...';
    throw new Error((body && body.message) || fallback);
  }
  return body || {};
}
