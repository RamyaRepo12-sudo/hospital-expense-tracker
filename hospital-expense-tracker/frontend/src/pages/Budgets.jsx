import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import Ring from '../components/Ring.jsx';
import Skeleton from '../components/Skeleton.jsx';
import { can, inr, level } from '../util.jsx';

export default function Budgets() {
  const { user } = useAuth();
  const [rows, setRows] = useState(null);
  const [edit, setEdit] = useState({});
  const [msg, setMsg] = useState('');
  const load = () => api('/api/budgets').then(setRows).catch((e) => setMsg(e.message));
  useEffect(() => { load(); }, []);

  async function save(dept) {
    try { await api(`/api/budgets/${encodeURIComponent(dept)}`, { method: 'PUT', body: { monthlyLimit: Number(edit[dept]) } }); setEdit({ ...edit, [dept]: '' }); setMsg('Budget updated'); load(); }
    catch (ex) { setMsg(ex.message); }
  }
  if (!rows) return <><div className="top"><h1>Budgets</h1></div>{msg ? <div className="err">{msg}</div> : <Skeleton rows={4} />}</>;
  return (
    <>
      <div className="top"><h1>Budgets</h1></div>
      {msg && <div className="alert" role="status">{msg}</div>}
      <div className="grid c2">{rows.map((r, i) => {
        const [label, cls] = level(r.utilization);
        return (
          <div className="card lift rise" style={{ '--i': i }} key={r.department}>
            <div className="bud">
              <Ring pct={r.utilization} cls={cls} />
              <div>
                <div className="between"><b>{r.department}</b><span className={'badge ' + (cls === 'bad' ? 'Rejected' : cls === 'warn' ? 'Pending' : 'Approved')}>{label}</span></div>
                <div className="track"><div className={'fill ' + cls} style={{ width: Math.min(100, r.utilization * 100) + '%' }} /></div>
                <small className="mute">{inr(r.spent)} of {inr(r.monthlyLimit)} this month · {r.spent > r.monthlyLimit ? inr(r.spent - r.monthlyLimit) + ' over' : inr(r.monthlyLimit - r.spent) + ' left'}</small>
              </div>
            </div>
            {can(user, 'budget') && (
              <div className="bar" style={{ marginTop: 12, marginBottom: 0 }}>
                <input type="number" min="1" placeholder="New monthly limit (₹)" aria-label={'New limit for ' + r.department} value={edit[r.department] ?? ''} onChange={(e) => setEdit({ ...edit, [r.department]: e.target.value })} />
                <button className="btn s" disabled={!edit[r.department]} onClick={() => save(r.department)}>Save limit</button>
              </div>)}
          </div>);
      })}</div>
    </>
  );
}
