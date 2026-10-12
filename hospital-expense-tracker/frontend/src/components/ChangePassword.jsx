import { useState } from 'react';
import { api } from '../api.js';
import Modal from './Modal.jsx';

export default function ChangePassword({ onClose, onDone, force = false }) {
  const [cur, setCur] = useState('');
  const [nw, setNw] = useState('');
  const [err, setErr] = useState('');
  async function save() {
    setErr('');
    try { await api('/api/account/password', { method: 'POST', body: { currentPassword: cur, newPassword: nw } }); onDone('Password changed'); }
    catch (ex) { setErr(ex.message); }
  }
  return (
    <Modal title={force ? 'Choose your own password' : 'Change password'} onClose={force ? () => {} : onClose}>
      <div className="form">
        {force && <p className="w2 mute" style={{ margin: 0 }}>Your account was created with a temporary password. Set a new one to continue.</p>}
        <label className="w2">Current password<input type="password" value={cur} onChange={(e) => setCur(e.target.value)} autoComplete="current-password" /></label>
        <label className="w2">New password (10+ characters, a letter and a number)<input type="password" value={nw} onChange={(e) => setNw(e.target.value)} autoComplete="new-password" /></label>
        {err && <div className="err w2" role="alert">{err}</div>}
        <div className="w2 actions">{!force && <button className="btn s" onClick={onClose}>Cancel</button>}<button className="btn" onClick={save}>Update password</button></div>
      </div>
    </Modal>
  );
}
