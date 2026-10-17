export const LEDGER = '/svc/ledger/api/v1';
export const INGESTION = '/svc/ingestion/api/v1';
export const FRAUD = '/svc/fraud/api/v1';
export const NOTIFY = '/svc/notify/api/v1';

export async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
  }
  if (!response.ok) {
    const error = new Error(body?.message || body?.error || `Request failed (${response.status})`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

export async function ping(url) {
  try {
    const response = await fetch(url, { cache: 'no-store' });
    return response.ok;
  } catch {
    return false;
  }
}
