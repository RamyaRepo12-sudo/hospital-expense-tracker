import { useState } from 'react';
import { api } from '../api.js';
import { useLookups } from '../lookups.jsx';

const SECTIONS = [
  ['DEPARTMENT', 'Departments', 'Head of department (optional)', 'A new department also gets a default monthly budget of ₹1,00,000. Change it under Budgets.'],
  ['CATEGORY', 'Expense categories', 'Notes (optional)', ''],
  ['VENDOR', 'Vendors', 'GSTIN or contact (optional)', 'Only vendors listed here can be used on new expenses.'],
];

function Section({ type, title, extraLabel, help }) {
  const lk = useLookups();
  const [name, setName] = useState('');
  const [extra, setExtra] = useState('');
  const [msg, setMsg] = useState({ t: '', bad: false });
  const rows = lk.all.filter((l) => l.type === type);
  const say = (t, bad = false) => setMsg({ t, bad });

  async function add(e) {
    e.preventDefault();
    try { await api('/api/lookups', { method: 'POST', body: { type, name, extra } }); setName(''); setExtra(''); say('Added'); lk.reload(); }
    catch (ex) { say(ex.message, true); }
  }
  async function toggle(l) {
    try { await api(`/api/lookups/${l.id}/active`, { method: 'PUT', body: { active: !l.active } }); say(l.active ? 'Deactivated. Existing expenses keep their history.' : 'Activated'); lk.reload(); }
    catch (ex) { say(ex.message, true); }
  }
  return (
    <div className="card rise">
      <h2>{title}</h2>
      {help && <p className="hint">{help}</p>}
      {msg.t && <div className={'alert ' + (msg.bad ? 'bad' : '')} role="status">{msg.t}</div>}
      <form className="bar" onSubmit={add}>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={'New ' + title.toLowerCase().replace(/s$/, '')} aria-label={'Name for ' + title} maxLength={80} required />
        <input value={extra} onChange={(e) => setExtra(e.target.value)} placeholder={extraLabel} aria-label={extraLabel} maxLength={120} />
        <button className="btn">Add</button>
      </form>
      <div className="sc"><table><tbody>
        {rows.length === 0 && <tr><td className="mute">Nothing here yet.</td></tr>}
        {rows.map((l) => (
          <tr key={l.id}><td>{l.name}{l.extra && <small className="mute"> · {l.extra}</small>}</td>
            <td><span className={'badge ' + (l.active ? 'Approved' : 'Draft')}>{l.active ? '✓ Active' : '○ Inactive'}</span></td>
            <td className="r"><button className="btn s sm" onClick={() => toggle(l)}>{l.active ? 'Deactivate' : 'Activate'}</button></td></tr>))}
      </tbody></table></div>
    </div>
  );
}

export default function Settings() {
  return (
    <>
      <div className="top"><h1>Settings</h1></div>
      <p className="mute" style={{ marginTop: 0 }}>Set up your hospital's own departments, categories and vendors. Deactivating something hides it from new expenses but keeps all past records and reports intact.</p>
      <div className="grid c2">{SECTIONS.map(([type, title, extra, help]) => <Section key={type} type={type} title={title} extraLabel={extra} help={help} />)}</div>
    </>
  );
}
