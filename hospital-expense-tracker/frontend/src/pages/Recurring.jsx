import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import Modal from '../components/Modal.jsx';
import Skeleton from '../components/Skeleton.jsx';
import { useLookups } from '../lookups.jsx';
import { PAYMENT_METHODS, inr } from '../util.jsx';

const FREQ = { WEEKLY: 'Every week', MONTHLY: 'Every month', QUARTERLY: 'Every 3 months', YEARLY: 'Every year' };
const today = () => new Date().toISOString().slice(0, 10);

export default function Recurring() {
  const { user } = useAuth();
  const lk = useLookups();
  const [rows, setRows] = useState(null);
  const [form, setForm] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const load = () => api('/api/recurring').then(setRows).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, []);

  const blank = () => ({ frequency: 'MONTHLY', startDate: today(), endDate: '', department: lk.departments[0] || '', category: lk.categories[0] || '', vendor: lk.vendors[0] || '', amount: '', gst: 0, paymentMethod: PAYMENT_METHODS[0], description: '' });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const sel = (k, opts) => <select value={form[k]} onChange={set(k)}>{opts.map((o) => <option key={o}>{o}</option>)}</select>;

  async function save() {
    setErr('');
    if (!form.department || !form.category || !form.vendor) return setErr('Choose a department, category and vendor.');
    if (!(Number(form.amount) > 0)) return setErr('Enter an amount greater than 0.');
    try {
      await api('/api/recurring', { method: 'POST', body: { ...form, amount: Number(form.amount), gst: Number(form.gst) || 0, endDate: form.endDate || null } });
      setForm(null); setMsg('Recurring expense saved. Drafts appear under Expenses on each due date.'); load();
    } catch (ex) { setErr(ex.message); }
  }
  async function toggle(r) { try { await api(`/api/recurring/${r.id}/active`, { method: 'PUT', body: { active: !r.active } }); load(); } catch (ex) { setErr(ex.message); } }
  async function remove(r) { if (!window.confirm('Delete this recurring rule? Drafts already created are kept.')) return; try { await api(`/api/recurring/${r.id}`, { method: 'DELETE' }); load(); } catch (ex) { setErr(ex.message); } }

  return (
    <>
      <div className="top"><h1>Recurring expenses</h1><button className="btn" onClick={() => { setForm(blank()); setErr(''); }}>+ New recurring expense</button></div>
      <p className="mute" style={{ marginTop: 0 }}>For rent, service contracts and subscriptions. On each due date a <b>Draft</b> expense is created for the person who set it up. They check the invoice details and submit it for approval as usual.</p>
      {msg && <div className="alert" role="status">{msg}</div>}
      {err && !form && <div className="err" role="alert">{err}</div>}
      <div className="card rise">
        {!rows ? <Skeleton rows={4} /> : rows.length === 0 ? <div className="empty">No recurring expenses yet. Add the first one with the button above.</div> : (
          <div className="sc"><table>
            <thead><tr><th>Vendor</th><th>Department</th><th>Category</th><th>Repeats</th><th className="r">Amount</th><th>Next date</th><th>Status</th><th></th></tr></thead>
            <tbody>{rows.map((r) => (
              <tr key={r.id}><td>{r.vendor}</td><td>{r.department}</td><td>{r.category}</td><td>{FREQ[r.frequency]}</td><td className="r">{inr(Number(r.amount) + Number(r.gst || 0))}</td>
                <td>{r.active ? r.nextDate : '-'}</td>
                <td><span className={'badge ' + (r.active ? 'Approved' : 'Draft')}>{r.active ? '✓ Active' : r.endDate && r.nextDate > r.endDate ? '○ Finished' : '○ Paused'}</span></td>
                <td className="acts">
                  {!(r.endDate && r.nextDate > r.endDate) && <button className="btn s sm" onClick={() => toggle(r)}>{r.active ? 'Pause' : 'Resume'}</button>}
                  {(user.role === 'SUPER_ADMIN' || user.role === 'FINANCE_ADMIN' || user.username === r.createdBy) && <button className="btn s sm" onClick={() => remove(r)}>Delete</button>}
                </td></tr>))}</tbody>
          </table></div>)}
      </div>
      {form && (
        <Modal title="New recurring expense" onClose={() => setForm(null)}>
          <div className="form">
            <label>Repeats{<select value={form.frequency} onChange={set('frequency')}>{Object.entries(FREQ).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>}</label>
            <label>First date<input type="date" value={form.startDate} onChange={set('startDate')} /></label>
            <label>Department{sel('department', lk.departments)}</label>
            <label>Category{sel('category', lk.categories)}</label>
            <label>Vendor{sel('vendor', lk.vendors)}</label>
            <label>Payment method{sel('paymentMethod', PAYMENT_METHODS)}</label>
            <label>Amount (₹)<input type="number" min="0" step="0.01" value={form.amount} onChange={set('amount')} /></label>
            <label>GST (₹)<input type="number" min="0" step="0.01" value={form.gst} onChange={set('gst')} /></label>
            <label className="w2">Last date (optional)<input type="date" value={form.endDate} onChange={set('endDate')} /></label>
            <label className="w2">Description<textarea rows="2" maxLength={500} value={form.description} onChange={set('description')} /></label>
            {err && <div className="err w2" role="alert">{err}</div>}
            <div className="w2 actions"><button className="btn s" onClick={() => setForm(null)}>Cancel</button><button className="btn" onClick={save}>Save</button></div>
          </div>
        </Modal>)}
    </>
  );
}
