import { createContext, useContext, useEffect, useState } from 'react';
import { api } from './api.js';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

const toUser = (d) => ({ username: d.username, name: d.name, role: d.role, mustChange: !!d.mustChangePassword, mfaEnabled: !!d.mfaEnabled, mfaSetupRequired: !!d.mfaSetupRequired });

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const refresh = () => api('/api/auth/me').then((d) => setUser(toUser(d))).catch(() => setUser(null));

  // The session cookie is HttpOnly, so the page asks the server who is signed in.
  useEffect(() => {
    refresh().finally(() => setReady(true));
    const expired = () => setUser(null);
    window.addEventListener('auth-expired', expired);
    return () => window.removeEventListener('auth-expired', expired);
  }, []);

  /** Returns { mfaToken } when a second step is needed, otherwise signs in. */
  async function login(username, password) {
    const d = await api('/api/auth/login', { method: 'POST', body: { username, password } });
    if (d.mfaRequired) return { mfaToken: d.mfaToken };
    setUser(toUser(d));
    return {};
  }
  async function verifyMfa(mfaToken, code) { setUser(toUser(await api('/api/auth/mfa', { method: 'POST', body: { mfaToken, code } }))); }
  async function logout() {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* cookie is cleared server-side when reachable */ }
    setUser(null);
  }
  const passwordChanged = () => setUser((u) => ({ ...u, mustChange: false }));

  return <Ctx.Provider value={{ user, ready, login, verifyMfa, logout, passwordChanged, refresh }}>{children}</Ctx.Provider>;
}
