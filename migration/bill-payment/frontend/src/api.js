const BASE = process.env.REACT_APP_API_BASE || '';

async function call(method, path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    throw new Error(`${method} ${path} failed: ${res.status}`);
  }
  return res.json();
}

export const initialScreen = () => call('GET', '/api/bill-payment');
export const submitKey = (request) => call('POST', '/api/bill-payment', request);
