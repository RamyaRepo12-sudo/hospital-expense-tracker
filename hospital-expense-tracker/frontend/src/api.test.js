import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, wake } from './api.js';

const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => { wake.delay = 1; wake.max = 3; });
afterEach(() => { vi.restoreAllMocks(); });

describe('api client', () => {
  it('sends the CSRF header and the session cookie, never a token it can read', async () => {
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue(ok({ hello: 'world' }));
    expect(await api('/api/x')).toEqual({ hello: 'world' });
    const init = f.mock.calls[0][1];
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(init.headers.Authorization).toBeUndefined();
    expect(init.credentials).toBe('same-origin');
  });

  it('retries while the server is waking up and announces it', async () => {
    const events = [];
    window.addEventListener('api-waking', (e) => events.push(e.detail));
    const f = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(new Response('', { status: 503 }))
      .mockResolvedValueOnce(ok({ up: true }));
    expect(await api('/api/x')).toEqual({ up: true });
    expect(f).toHaveBeenCalledTimes(3);
    expect(events).toEqual([true, false]);
  });

  it('gives up after the retry limit', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(api('/api/x')).rejects.toThrow(/did not respond/);
  });

  it('surfaces the server error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'You cannot approve your own expense' }), { status: 403 }));
    await expect(api('/api/x', { method: 'POST' })).rejects.toThrow('You cannot approve your own expense');
  });

  it('signals an expired session on 401 but not for the sign-in call itself', async () => {
    const seen = [];
    window.addEventListener('auth-expired', () => seen.push(1));
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }));
    await expect(api('/api/expenses')).rejects.toThrow(/session has expired/);
    expect(seen).toHaveLength(1);
    await expect(api('/api/auth/login', { method: 'POST', body: {} })).rejects.toThrow(/Request failed/);
    expect(seen).toHaveLength(1);
  });
});
