// The web app and the API share one origin. The session is an HttpOnly cookie that scripts cannot read,
// and every request carries X-Requested-With, which the server requires for any change (CSRF defence).
const BASE = import.meta.env.VITE_API_URL || '';

// Free hosting puts the API to sleep when idle and needs up to about a minute to wake up.
// While that happens we retry quietly and show a "waking up" banner (see components/WakeBanner.jsx).
export const wake = { delay: 4000, max: 18 };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const announce = (on) => window.dispatchEvent(new CustomEvent('api-waking', { detail: on }));

async function request(path, { method = 'GET', body, json = false } = {}) {
  const headers = { 'X-Requested-With': 'XMLHttpRequest', ...(json ? { 'Content-Type': 'application/json' } : {}) };
  let waiting = false;
  try {
    for (let attempt = 0; ; attempt++) {
      let res = null;
      try { res = await fetch(BASE + path, { method, headers, body, credentials: 'same-origin' }); } catch { res = null; }
      const asleep = res === null || [502, 503, 504].includes(res.status);
      if (!asleep) return res;
      if (attempt >= wake.max) throw new Error('The server did not respond. Please try again in a minute.');
      if (!waiting) { waiting = true; announce(true); }
      await sleep(wake.delay);
    }
  } finally {
    if (waiting) announce(false);
  }
}

const NO_REDIRECT = ['/api/auth/login', '/api/auth/me', '/api/auth/logout'];

async function check(res, path) {
  if (res.status === 401 && !NO_REDIRECT.includes(path)) {
    window.dispatchEvent(new CustomEvent('auth-expired'));
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (!res.ok) {
    let data = null;
    try { data = await res.json(); } catch { /* no body */ }
    throw new Error((data && data.message) || `Request failed (${res.status})`);
  }
  return res;
}

export async function api(path, { method = 'GET', body } = {}) {
  const res = await check(await request(path, { method, json: body !== undefined, body: body !== undefined ? JSON.stringify(body) : undefined }), path);
  try { return await res.json(); } catch { return null; }
}

export async function upload(path, file) {
  const fd = new FormData();
  fd.append('file', file);
  const res = await check(await request(path, { method: 'POST', body: fd }), path);
  return res.json();
}

export async function download(path, fallbackName) {
  const res = await check(await request(path), path);
  const blob = await res.blob();
  const m = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') || '');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = m ? m[1] : fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
