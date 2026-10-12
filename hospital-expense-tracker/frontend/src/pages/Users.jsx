import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import Modal from '../components/Modal.jsx';
import Skeleton from '../components/Skeleton.jsx';

const ROLES = ['SUPER_ADMIN', 'FINANCE_ADMIN', 'DEPARTMENT_MANAGER', 'ACCOUNTANT', 'STAFF', 'AUDITOR'];
const EMPTY = { username: '', name: '', email: '', role: 'STAFF', password: '' };

export default function Users() {
  const { user } = useAuth();
  const [rows, setRows] = useState(null);
  const [f, setF] = useState(EMPTY);
  const [msg, setMsg] = useState({ t: '', bad: false });
  const [reset, setReset] = useState(null);
  const [pw, setPw] = useState('');
  const say = (t, bad = false) => setMsg({ t, bad });
  const load = () => api('/api/users').then(setRows).catch((e) => say(e.message, true));
  useEffect(() => { load(); }, []);

  async function create(e) {
    e.preventDefault();
    try { await api('/api/users', { method: 'POST', body: f }); say('User created. They will be asked to choose their own password at first sign-in.'); setF(EMPTY); load(); }
    catch (ex) { say(ex.message, true); }
  }
  async function toggle(u) {
    try { await api(`/api/users/${u.id}/active`, { method: 'PUT', body: { active: !u.active } }); say(u.active ? 'User deactivated' : 'User activated'); load(); }
    catch (ex) { say(ex.message, true); }
  }
  async function doReset() {
    try { await api(`/api/users/${reset.id}/password`, { method: 'PUT', body: { password: pw } }); setReset(null); setPw(''); say('Password reset. The user must choose a new one at next sign-in.'); load(); }
    catch (ex) { say(ex.message, true); }
  }
  async function resetMfa(u) {
    if (!window.confirm(`Turn off two-factor authentication for ${u.username}? Use this when they lost their phone and recovery codes. They sign in with their password and set it up again.`)) return;
    try { await api(`/api/users/${u.id}/mfa/reset`, { method: 'PUT' }); say('Two-factor authentication was reset for ' + u.username); load(); }
    catch (ex) { say(ex.message, true); }
  }
  async function backup() {
    try {
      const d = await api('/api/admin/backup');
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([JSON.stringify(d, null, 2)], { type: 'application/json' }));
      a.download = `hospital-backup-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(a.href);
      say('Backup downloaded. Store it somewhere private.');
    } catch (ex) { say(ex.message, true); }
  }
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <div className="top"><h1>Users</h1><button className="btn s" onClick={backup}>⬇ Download JSON backup</button></div>
      {msg.t && <div className={'alert ' + (msg.bad ? 'bad' : '')} role="status">{msg.t}</div>}
      <div className="card rise" style={{ marginBottom: 14 }}>
        <h2>Add a user</h2>
        <form className="form" onSubmit={create}>
          <label>Username<input value={f.username} onChange={set('username')} placeholder="e.g. anita.rao" required /></label>
          <label>Full name<input value={f.name} onChange={set('name')} required /></label>
          <label>Email (optional, for notifications)<input type="email" value={f.email} onChange={set('email')} /></label>
          <label>Role<select value={f.role} onChange={set('role')}>{ROLES.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}</select></label>
          <label className="w2">Temporary password<input type="password" value={f.password} onChange={set('password')} placeholder="10+ chars, letter and number" required autoComplete="new-password" /></label>
          <div className="w2 actions" style={{ margin: 0 }}><button className="btn">Create user</button></div>
        </form>
      </div>
      <div className="card rise" style={{ '--i': 2 }}><h2>All users</h2>
        {!rows ? <Skeleton rows={4} /> : (
          <div className="sc"><table>
            <thead><tr><th>Username</th><th>Name</th><th>Email</th><th>Role</th><th>2FA</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{rows.map((u) => (
              <tr key={u.id}><td>{u.username}</td><td>{u.name}</td><td>{u.email || '-'}</td><td>{u.role.replace(/_/g, ' ')}</td>
                <td>{u.mfaEnabled ? '✓ On' : '-'}</td>
                <td><span className={'badge ' + (u.active ? 'Approved' : 'Draft')}>{u.active ? '✓ Active' : '○ Inactive'}</span>{u.mustChangePassword && <small className="mute"> · must change password</small>}</td>
                <td className="acts">
                  <button className="btn s sm" onClick={() => { setReset(u); setPw(''); }}>Reset password</button>
                  {u.mfaEnabled && <button className="btn s sm" onClick={() => resetMfa(u)}>Reset 2FA</button>}
                  <button className="btn s sm" disabled={u.username === user.username} onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button>
                </td></tr>))}</tbody>
          </table></div>)}
      </div>
      {reset && (
        <Modal title={`Reset password for ${reset.username}`} onClose={() => setReset(null)}>
          <label>New temporary password<input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /></label>
          <div className="actions"><button className="btn s" onClick={() => setReset(null)}>Cancel</button><button className="btn" onClick={doReset}>Reset password</button></div>
        </Modal>)}
    </>
  );
}
