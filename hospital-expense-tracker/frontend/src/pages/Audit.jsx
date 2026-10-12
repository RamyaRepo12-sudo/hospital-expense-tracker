import { useEffect, useState } from 'react';
import { api } from '../api.js';
import Skeleton from '../components/Skeleton.jsx';

export default function Audit() {
  const [rows, setRows] = useState(null);
  const [err, setErr] = useState('');
  const [check, setCheck] = useState(null);
  useEffect(() => { api('/api/audit').then(setRows).catch((e) => setErr(e.message)); }, []);
  async function verify() {
    setCheck(null);
    try { setCheck(await api('/api/audit/verify')); } catch (ex) { setErr(ex.message); }
  }
  if (err) return <div className="err" role="alert">{err}</div>;
  if (!rows) return <><div className="top"><h1>Audit logs</h1></div><Skeleton rows={5} /></>;
  return (
    <>
      <div className="top"><h1>Audit logs</h1><button className="btn s" onClick={verify}>🔒 Verify integrity</button></div>
      {check && (
        <div className={'alert ' + (check.ok ? '' : 'bad')} role="status">
          {check.ok ? `✓ The audit trail is intact. ${check.checked} entries were checked and none were altered or removed.`
            : `✕ The audit trail was tampered with: the chain breaks at entry #${check.brokenAtSeq}. Investigate before trusting later entries.`}
        </div>)}
      <div className="card"><h2>Latest 200 actions (read-only, tamper-evident)</h2>
        {rows.length === 0 ? <div className="empty">No activity recorded yet.</div> : (
          <div className="sc"><table>
            <thead><tr><th>#</th><th>Time</th><th>User</th><th>Action</th><th>Record</th><th>Address</th></tr></thead>
            <tbody>{rows.map((l) => <tr key={l.id}><td>{l.seq || '-'}</td><td>{new Date(l.timestamp).toLocaleString('en-IN')}</td><td>{l.username}</td><td>{l.action}</td><td>{l.recordId}</td><td>{l.ip || '-'}</td></tr>)}</tbody>
          </table></div>)}
      </div>
    </>
  );
}
