/**
 * REST client for the S-05 UserManagement backend (CU00..CU03). Every endpoint
 * lives under /api/admin/**, the COADM01C boundary the backend restricts to the
 * ADMIN role. Requests are proxied to the Spring Boot app on :8080 (see
 * package.json proxy).
 *
 * On a non-2xx response each call throws an Error whose {@code message} is the
 * exact legacy ERRMSG text from the backend body, so the screens can paint it
 * unchanged.
 */

async function readBody(res) {
  try {
    return await res.json();
  } catch (e) {
    return null;
  }
}

/**
 * CU00 — a page of users (COUSR00C GET /api/admin/users?startId&dir).
 * {@code startId} is the Search User ID (ENTER) or the paging cursor for
 * PF7/PF8; {@code dir} is 'next' (PF8), 'prev' (PF7) or omitted.
 */
export async function listUsers({ startId, dir } = {}) {
  const params = new URLSearchParams();
  if (startId) {
    params.set('startId', startId);
  }
  if (dir) {
    params.set('dir', dir);
  }
  const qs = params.toString();
  const res = await fetch(`/api/admin/users${qs ? `?${qs}` : ''}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to lookup User...');
  }
  return body;
}

/** CU02/CU03 ENTER fetch — GET /api/admin/users/{id}. */
export async function getUser(userId) {
  const res = await fetch(`/api/admin/users/${encodeURIComponent(userId)}`);
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to lookup User...');
  }
  return body;
}

/** CU01 ENTER — POST /api/admin/users. Resolves to {userId, message}. */
export async function addUser(request) {
  const res = await fetch('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to Add User...');
  }
  return body;
}

/** CU02 PF5/PF3 — PUT /api/admin/users/{id}. Resolves to {userId, message}. */
export async function updateUser(userId, request) {
  const res = await fetch(`/api/admin/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to Update User...');
  }
  return body;
}

/**
 * CU03 PF5 — DELETE /api/admin/users/{id}. Resolves to {userId, message}. The
 * fallback text is COUSR03C's own WHEN OTHER literal, which is the *update*
 * wording (quirk Q3).
 */
export async function deleteUser(userId) {
  const res = await fetch(`/api/admin/users/${encodeURIComponent(userId)}`, { method: 'DELETE' });
  const body = await readBody(res);
  if (!res.ok) {
    throw new Error((body && body.message) || 'Unable to Update User...');
  }
  return body;
}
