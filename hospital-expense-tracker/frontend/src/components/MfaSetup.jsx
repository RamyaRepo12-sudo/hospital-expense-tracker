import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { api } from '../api.js';
import Modal from './Modal.jsx';

/** Three steps: confirm password, scan the QR code and enter a code, then save the recovery codes. */
export default function MfaSetup({ force = false, onClose, onDone }) {
  const [step, setStep] = useState(1);
  const [password, setPassword] = useState('');
  const [info, setInfo] = useState(null);
  const [qr, setQr] = useState('');
  const [code, setCode] = useState('');
  const [codes, setCodes] = useState([]);
  const [saved, setSaved] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (info) QRCode.toDataURL(info.otpauthUri, { width: 200, margin: 1 }).then(setQr).catch(() => setQr('')); }, [info]);

  async function start() {
    setErr(''); setBusy(true);
    try { setInfo(await api('/api/account/mfa/setup', { method: 'POST', body: { password } })); setStep(2); }
    catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  async function enable() {
    setErr(''); setBusy(true);
    try { const r = await api('/api/account/mfa/enable', { method: 'POST', body: { code } }); setCodes(r.recoveryCodes); setStep(3); }
    catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  function saveFile() {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob(['Hospital Finance recovery codes (each works once)\n\n' + codes.join('\n') + '\n'], { type: 'text/plain' }));
    a.download = 'recovery-codes.txt';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  return (
    <Modal title={force ? 'Set up two-factor authentication' : 'Turn on two-factor authentication'} onClose={force || step === 3 ? () => {} : onClose}>
      {force && step === 1 && <p className="mute" style={{ marginTop: 0 }}>Your role requires a second step at sign-in. It takes about two minutes.</p>}
      {step === 1 && (
        <div className="form">
          <p className="w2" style={{ margin: 0 }}>Install an authenticator app (Google Authenticator, Microsoft Authenticator, Authy or 1Password) on your phone. Then confirm your password.</p>
          <label className="w2">Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
          {err && <div className="err w2" role="alert">{err}</div>}
          <div className="w2 actions">{!force && <button className="btn s" onClick={onClose}>Cancel</button>}<button className="btn" disabled={busy || !password} onClick={start}>Continue</button></div>
        </div>
      )}
      {step === 2 && info && (
        <div className="form">
          <p className="w2" style={{ margin: 0 }}>Scan this code with your app, then type the 6-digit number it shows.</p>
          <div className="w2 qr">{qr ? <img src={qr} alt="QR code for your authenticator app" width="200" height="200" /> : <div className="skel" style={{ width: 200, height: 200 }} />}</div>
          <p className="w2 mute" style={{ margin: 0, wordBreak: 'break-all' }}>Cannot scan? Enter this key by hand: <b>{info.secret}</b></p>
          <label className="w2">6-digit code<input value={code} onChange={(e) => setCode(e.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} /></label>
          {err && <div className="err w2" role="alert">{err}</div>}
          <div className="w2 actions"><button className="btn" disabled={busy || code.length !== 6} onClick={enable}>Turn on</button></div>
        </div>
      )}
      {step === 3 && (
        <div className="form">
          <p className="w2" style={{ margin: 0 }}><b>Two-factor authentication is on.</b> Save these recovery codes now. Each works once if you lose your phone, and they will not be shown again.</p>
          <pre className="w2 codes">{codes.join('\n')}</pre>
          <div className="w2 actions" style={{ justifyContent: 'flex-start' }}>
            <button className="btn s" onClick={() => navigator.clipboard && navigator.clipboard.writeText(codes.join('\n'))}>Copy</button>
            <button className="btn s" onClick={saveFile}>Download</button>
          </div>
          <label className="w2" style={{ display: 'flex', gap: 8, alignItems: 'center' }}><input type="checkbox" style={{ width: 'auto' }} checked={saved} onChange={(e) => setSaved(e.target.checked)} />I have saved my recovery codes</label>
          <div className="w2 actions"><button className="btn" disabled={!saved} onClick={onDone}>Done</button></div>
        </div>
      )}
    </Modal>
  );
}
