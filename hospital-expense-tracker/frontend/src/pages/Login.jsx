import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import Ecg from '../components/Ecg.jsx';
import { useAuth } from '../auth.jsx';

const DEMO = [['admin', 'Super Admin'], ['finance', 'Finance Admin'], ['manager', 'Dept Manager'], ['accountant', 'Accountant'], ['staff', 'Staff'], ['auditor', 'Auditor']];
const POINTS = ['Every rupee tracked from request to payment', 'Approvals with a full audit trail', 'Live budget alerts before a department overspends'];
const SHOW_DEMO = import.meta.env.VITE_SHOW_DEMO === 'true';
const SSO_MESSAGES = { failed: 'Single sign-on did not complete. Please try again.', denied: 'Your single sign-on account is not linked to an active user here. Ask an administrator to add your work email address.' };

export default function Login() {
  const { login, verifyMfa } = useAuth();
  const [params] = useSearchParams();
  const [username, setU] = useState(SHOW_DEMO ? 'finance' : '');
  const [password, setP] = useState('');
  const [mfaToken, setMfaToken] = useState('');
  const [code, setCode] = useState('');
  const [err, setErr] = useState(SSO_MESSAGES[params.get('sso')] || '');
  const [busy, setBusy] = useState(false);
  const [cfg, setCfg] = useState({ ssoEnabled: false, passwordLogin: true });
  useEffect(() => { api('/api/auth/config').then(setCfg).catch(() => {}); }, []);

  async function submit(e) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      if (mfaToken) await verifyMfa(mfaToken, code);
      else { const r = await login(username.trim(), password); if (r.mfaToken) setMfaToken(r.mfaToken); }
    } catch (ex) {
      setErr(ex.message);
      if (mfaToken && /expired/i.test(ex.message)) { setMfaToken(''); setCode(''); }
    } finally { setBusy(false); }
  }
  return (
    <div className="login">
      <section className="stage">
        <div className="blob b1" /><div className="blob b2" /><Ecg />
        <h1>Hospital Financial<br />Control Center</h1>
        <p>One calm place to record, approve and understand every operational expense.</p>
        <ul>{POINTS.map((p, i) => <li key={p} style={{ '--i': i }}>✓ {p}</li>)}</ul>
      </section>
      <section className="signin">
        <form className="card rise" onSubmit={submit}>
          <h1>{mfaToken ? 'Two-step verification' : 'Welcome back'}</h1>
          {mfaToken ? (
            <>
              <p className="mute" style={{ margin: 0 }}>Enter the 6-digit code from your authenticator app, or one of your recovery codes.</p>
              <label>Code<input value={code} onChange={(e) => setCode(e.target.value)} inputMode="text" autoComplete="one-time-code" autoFocus maxLength={20} required /></label>
            </>
          ) : (
            <>
              <p className="mute" style={{ margin: 0 }}>Sign in to continue.</p>
              {cfg.ssoEnabled && <a className="btn s center" href="/api/auth/sso/login">🔐 Sign in with single sign-on</a>}
              {(cfg.passwordLogin || !cfg.ssoEnabled) && (
                <>
                  {cfg.ssoEnabled && <div className="or"><span>or</span></div>}
                  <label>Username<input value={username} onChange={(e) => setU(e.target.value)} autoComplete="username" required /></label>
                  <label>Password<input type="password" value={password} onChange={(e) => setP(e.target.value)} autoComplete="current-password" required /></label>
                </>
              )}
              {!cfg.passwordLogin && cfg.ssoEnabled && <details><summary className="mute">Administrator sign-in</summary>
                <label>Username<input value={username} onChange={(e) => setU(e.target.value)} autoComplete="username" /></label>
                <label>Password<input type="password" value={password} onChange={(e) => setP(e.target.value)} autoComplete="current-password" /></label></details>}
            </>
          )}
          {err && <div className="err" role="alert">{err}</div>}
          {(!cfg.ssoEnabled || cfg.passwordLogin || mfaToken || username) && <button className="btn" disabled={busy}>{busy ? 'Checking…' : mfaToken ? 'Verify' : 'Sign in'}</button>}
          {mfaToken && <button type="button" className="btn s" onClick={() => { setMfaToken(''); setCode(''); setErr(''); }}>Back</button>}
          {SHOW_DEMO && !mfaToken && (
            <div className="demo">
              <span className="mute">Demo accounts (password: SEED_PASSWORD, default Password@123):</span>
              <div>{DEMO.map(([u, l]) => <button type="button" key={u} className="btn s sm" onClick={() => setU(u)}>{l}</button>)}</div>
            </div>
          )}
        </form>
      </section>
    </div>
  );
}
