import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import MfaSetup from '../components/MfaSetup.jsx';
import Modal from '../components/Modal.jsx';

export default function Security() {
  const { user, refresh } = useAuth();
  const [setup, setSetup] = useState(false);
  const [action, setAction] = useState(null); // 'disable' | 'codes'
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [codes, setCodes] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  async function run() {
    setErr('');
    try {
      if (action === 'disable') { await api('/api/account/mfa/disable', { method: 'POST', body: { password, code } }); setMsg('Two-factor authentication is off.'); setAction(null); await refresh(); }
      else { const r = await api('/api/account/mfa/recovery-codes', { method: 'POST', body: { password, code } }); setCodes(r.recoveryCodes); setAction(null); }
      setPassword(''); setCode('');
    } catch (ex) { setErr(ex.message); }
  }
  return (
    <>
      <div className="top"><h1>Security</h1></div>
      {msg && <div className="alert" role="status">{msg}</div>}
      <div className="card rise">
        <div className="between"><h2 style={{ margin: 0 }}>Two-factor authentication</h2>
          <span className={'badge ' + (user.mfaEnabled ? 'Approved' : 'Draft')}>{user.mfaEnabled ? '✓ On' : '○ Off'}</span></div>
        <p className="mute">After your password, sign-in asks for a 6-digit code from an authenticator app on your phone. This protects your account even if your password is stolen.</p>
        {user.mfaEnabled ? (
          <div className="actions" style={{ justifyContent: 'flex-start' }}>
            <button className="btn s" onClick={() => { setAction('codes'); setErr(''); }}>New recovery codes</button>
            <button className="btn s" onClick={() => { setAction('disable'); setErr(''); }}>Turn off</button>
          </div>
        ) : <button className="btn" onClick={() => setSetup(true)}>Turn on two-factor authentication</button>}
      </div>
      {setup && <MfaSetup onClose={() => setSetup(false)} onDone={async () => { setSetup(false); setMsg('Two-factor authentication is on.'); await refresh(); }} />}
      {action && (
        <Modal title={action === 'disable' ? 'Turn off two-factor authentication' : 'Generate new recovery codes'} onClose={() => setAction(null)}>
          <div className="form">
            <label className="w2">Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
            <label className="w2">Code from your app (or a recovery code)<input value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" maxLength={20} /></label>
            {action === 'codes' && <p className="w2 mute" style={{ margin: 0 }}>Your old recovery codes stop working.</p>}
            {err && <div className="err w2" role="alert">{err}</div>}
            <div className="w2 actions"><button className="btn s" onClick={() => setAction(null)}>Cancel</button><button className={'btn' + (action === 'disable' ? ' bad' : '')} onClick={run}>{action === 'disable' ? 'Turn off' : 'Generate'}</button></div>
          </div>
        </Modal>
      )}
      {codes && (
        <Modal title="Your new recovery codes" onClose={() => setCodes(null)}>
          <p style={{ marginTop: 0 }}>Save these now. Each works once and they will not be shown again.</p>
          <pre className="codes">{codes.join('\n')}</pre>
          <div className="actions"><button className="btn s" onClick={() => navigator.clipboard && navigator.clipboard.writeText(codes.join('\n'))}>Copy</button><button className="btn" onClick={() => setCodes(null)}>I have saved them</button></div>
        </Modal>
      )}
    </>
  );
}
