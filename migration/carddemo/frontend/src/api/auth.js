/**
 * REST client for the sign-on/menu shell (COSGN00C, COMEN01C, COADM01C).
 * Requests are proxied to the Spring Boot app on :8080 (see package.json proxy)
 * and carry the session cookie that holds the COMMAREA.
 */

async function readBody(res) {
  try {
    return await res.json();
  } catch (e) {
    return null;
  }
}

/**
 * COSGN00C — sign on with a USRSEC user ID and password. Resolves to
 * {signedOn, userId, userType, nextProgram, message, errorField}; a rejected
 * sign-on resolves with {@code signedOn: false} and the exact legacy ERRMSG
 * ("Wrong Password. Try again ...", etc.) rather than throwing, because the
 * legacy screen redisplays itself with that message.
 */
export async function signOn(userId, password) {
  const res = await fetch('/api/auth/signon', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, password }),
  });
  const body = await readBody(res);
  if (!body) {
    return { signedOn: false, message: 'Unable to verify the User ...', errorField: 'userId' };
  }
  return { ...body, signedOn: res.ok };
}

/** COSGN00C PF3 / menu PF3 — sign off and clear the COMMAREA. */
export async function signOff() {
  const res = await fetch('/api/auth/signoff', { method: 'POST' });
  const body = await readBody(res);
  return body || { message: 'Thank you for using CardDemo application...' };
}

/**
 * Current COMMAREA-backed session, or {@code null} when nobody is signed on.
 * Used to restore the auth context after a browser refresh.
 */
export async function getSession() {
  const res = await fetch('/api/auth/session');
  if (res.status === 401 || res.status === 204) {
    return null;
  }
  const body = await readBody(res);
  if (!res.ok || !body || !body.userId) {
    return null;
  }
  return body;
}

/** COMEN01C — the 11 main-menu options from COMEN02Y, in copybook order. */
export async function getMainMenu() {
  const res = await fetch('/api/menu/main');
  return (await readBody(res)) || [];
}

/** COADM01C — the 6 admin-menu options from COADM02Y, in copybook order. */
export async function getAdminMenu() {
  const res = await fetch('/api/menu/admin');
  return (await readBody(res)) || [];
}

/**
 * COMEN01C/COADM01C option entry. {@code menu} is {@code 'main'} or
 * {@code 'admin'}. Resolves to {accepted, optionNumber, optionName,
 * programName, message}; a rejected option carries the exact legacy text
 * ("Please enter a valid option number...", "No access - Admin Only option... ").
 */
export async function selectMenuOption(menu, option) {
  const res = await fetch(`/api/menu/${menu}/select`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ option }),
  });
  const body = await readBody(res);
  if (!body) {
    return { accepted: false, message: 'Please enter a valid option number...' };
  }
  return body;
}
