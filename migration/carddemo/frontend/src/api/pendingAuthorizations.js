/**
 * REST client for the Pending Authorizations backend (S-09).
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy).
 *
 * CPVS and CPVD answer a bad account id, an empty list or a bad selection by
 * redisplaying the map with a message rather than failing, so every call here
 * resolves to the screen state and only a transport/server fault throws.
 */

async function getJson(url) {
  const res = await fetch(url);
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to lookup Authorizations...');
  }
  return body;
}

async function postJson(url, payload) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    body = null;
  }
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to lookup Authorizations...');
  }
  return body;
}

/**
 * CPVS — a page of pending authorizations (COPAUS0C
 * GET /api/pending-authorizations?acctId&dir&startKey&pageNum).
 * {@code dir} is 'next' (PF8), 'prev' (PF7) or omitted (ENTER / first display);
 * {@code startKey} is the paging cursor the previous page returned.
 */
export async function listPendingAuthorizations({ acctId, dir, startKey, pageNum } = {}) {
  const params = new URLSearchParams();
  if (acctId) {
    params.set('acctId', acctId);
  }
  if (dir) {
    params.set('dir', dir);
  }
  if (startKey) {
    params.set('startKey', startKey);
  }
  if (pageNum) {
    params.set('pageNum', String(pageNum));
  }
  const qs = params.toString();
  return getJson(`/api/pending-authorizations${qs ? `?${qs}` : ''}`);
}

/**
 * CPVS — the SEL0001I..SEL0005I evaluation (PROCESS-ENTER-KEY). Resolves to
 * {selected, nextProgram, nextTranId, authKey, message}.
 */
export async function selectPendingAuthorization(request) {
  return postJson('/api/pending-authorizations/selection', request);
}

/** CPVD — the selected authorization (COPAUS1C on entry). */
export async function getPendingAuthorizationDetail({ acctId, authKey } = {}) {
  const params = new URLSearchParams({ acctId: acctId || '', authKey: authKey || '' });
  return getJson(`/api/pending-authorizations/detail?${params.toString()}`);
}

/** CPVD PF8 — the next authorization in the account's chain. */
export async function getNextPendingAuthorizationDetail({ acctId, authKey } = {}) {
  const params = new URLSearchParams({ acctId: acctId || '', authKey: authKey || '' });
  return getJson(`/api/pending-authorizations/detail/next?${params.toString()}`);
}

/** CPVD PF5 — mark or remove fraud (LINK COPAUS2C + the AUTHFRDS write). */
export async function markPendingAuthorizationFraud({ acctId, authKey } = {}) {
  const params = new URLSearchParams({ acctId: acctId || '', authKey: authKey || '' });
  return postJson(`/api/pending-authorizations/detail/fraud?${params.toString()}`);
}
