/**
 * REST client for the Card Management backend (S-03: CCLI, CCDL, CCUP).
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy).
 *
 * Every non-2xx body carries the verbatim 3270 ERRMSG text in `message`, so the
 * thrown Error can be shown on the screen's message line unchanged.
 */

async function readBody(res) {
  try {
    return await res.json();
  } catch (e) {
    return null;
  }
}

/**
 * CCLI — one page of the card list (COCRDLIC GET /api/cards). `state` is the
 * page state echoed back from the previous response (pageNumber, firstCardNum,
 * lastCardNum, nextPageExists, lastPageShown) and `dir` is the AID: '' for
 * ENTER, 'F' for PF8, 'B' for PF7.
 */
export async function listCards({
  acctId, cardNum, dir, pageNumber, firstCardNum, lastCardNum, nextPageExists, lastPageShown,
} = {}) {
  const params = new URLSearchParams();
  if (acctId) {
    params.set('acctId', acctId);
  }
  if (cardNum) {
    params.set('cardNum', cardNum);
  }
  if (dir) {
    params.set('dir', dir);
  }
  if (pageNumber) {
    params.set('pageNumber', String(pageNumber));
  }
  if (firstCardNum) {
    params.set('firstCardNum', firstCardNum);
  }
  if (lastCardNum) {
    params.set('lastCardNum', lastCardNum);
  }
  if (nextPageExists !== undefined) {
    params.set('nextPageExists', String(Boolean(nextPageExists)));
  }
  if (lastPageShown !== undefined) {
    params.set('lastPageShown', String(Boolean(lastPageShown)));
  }
  const qs = params.toString();
  const res = await fetch(`/api/cards${qs ? `?${qs}` : ''}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'NO MORE RECORDS TO SHOW');
  }
  return body;
}

/**
 * CCLI — the row selection flags (COCRDLIC POST /api/cards/selection). Resolves
 * to the XCTL target {program, tranId, mapset, map, acctId, cardNum}, or null
 * when ENTER was pressed with no row selected.
 */
export async function selectCard({ flags, rows }) {
  const res = await fetch('/api/cards/selection', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ flags, rows }),
  });
  if (res.status === 204) {
    return null;
  }
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'INVALID ACTION CODE');
  }
  return body;
}

/** CCDL — card detail (COCRDSLC GET /api/cards/detail?acctId&cardNum). */
export async function getCardDetail({ acctId, cardNum }) {
  const params = new URLSearchParams();
  params.set('acctId', acctId || '');
  params.set('cardNum', cardNum || '');
  const res = await fetch(`/api/cards/detail?${params.toString()}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Did not find cards for this search condition');
  }
  return body;
}

/** CCUP — fetch a card for editing (COCRDUPC GET /api/cards/update). */
export async function loadCardForUpdate({ acctId, cardNum }) {
  const params = new URLSearchParams();
  params.set('acctId', acctId || '');
  params.set('cardNum', cardNum || '');
  const res = await fetch(`/api/cards/update?${params.toString()}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Did not find cards for this search condition');
  }
  return body;
}

/**
 * CCUP — submit the edited card (COCRDUPC POST /api/cards/update). `confirmed`
 * is PF5. On a rejected submit the thrown Error carries the verbatim message and,
 * for "Record changed by some one else. Please review", the refreshed screen
 * values on `refreshed`.
 */
export async function updateCard(request) {
  const res = await fetch('/api/cards/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  const body = await readBody(res);
  if (!res.ok) {
    const error = new Error((body && body.message) || 'Changes unsuccessful. Please try again');
    error.refreshed = body && body.refreshed;
    throw error;
  }
  return body;
}
