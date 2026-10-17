export const LEDGER = 'http://127.0.0.1:8081/api/v1';
export const INGESTION = 'http://127.0.0.1:8082/api/v1';
export const FRAUD = 'http://127.0.0.1:8010/api/v1';
export const NOTIFY = 'http://127.0.0.1:8003/api/v1';

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
