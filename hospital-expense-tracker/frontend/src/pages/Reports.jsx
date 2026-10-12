import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, download } from '../api.js';
import CountUp from '../components/CountUp.jsx';
import Skeleton from '../components/Skeleton.jsx';
import { useLookups } from '../lookups.jsx';
import { chartColors, useTheme } from '../theme.jsx';
import { STATUSES, inr, lakh } from '../util.jsx';

const COLORS = ['#0f766e', '#2563eb', '#d97706', '#7c3aed', '#db2777', '#65a30d', '#0891b2', '#64748b', '#a16207'];
const iso = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');

function range(preset) {
  const t = new Date(), y = t.getFullYear(), m = t.getMonth();
  if (preset === 'month') return [iso(new Date(y, m, 1)), iso(new Date(y, m + 1, 0))];
  if (preset === 'last') return [iso(new Date(y, m - 1, 1)), iso(new Date(y, m, 0))];
  if (preset === 'q') return [iso(new Date(y, m - 2, 1)), iso(t)];
  if (preset === 'year') return [iso(new Date(y, 0, 1)), iso(t)];
  if (preset === 'fy') return [iso(new Date(m >= 3 ? y : y - 1, 3, 1)), iso(t)]; // Indian financial year starts 1 April
  return ['', ''];
}
const PRESETS = [['month', 'This month'], ['last', 'Last month'], ['q', 'Last 3 months'], ['fy', 'Financial year'], ['year', 'Calendar year'], ['all', 'All time'], ['custom', 'Custom']];

export default function Reports() {
  const lk = useLookups();
  const { theme } = useTheme();
  const cc = chartColors(theme);
  const [preset, setPreset] = useState('month');
  const [from, setFrom] = useState(range('month')[0]);
  const [to, setTo] = useState(range('month')[1]);
  const [department, setDepartment] = useState('');
  const [category, setCategory] = useState('');
  const [vendor, setVendor] = useState('');
  const [status, setStatus] = useState('spend');
  const [d, setD] = useState(null);
  const [err, setErr] = useState('');

  const qs = useMemo(() => new URLSearchParams({ from, to, department, category, vendor, status }).toString(), [from, to, department, category, vendor, status]);
  useEffect(() => {
    setErr('');
    const t = setTimeout(() => api('/api/reports/summary?' + qs).then(setD).catch((e) => setErr(e.message)), 200);
    return () => clearTimeout(t);
  }, [qs]);

  function pick(p) { setPreset(p); if (p !== 'custom') { const [a, b] = range(p); setFrom(a); setTo(b); } }
  async function exportAs(kind) { try { await download(`/api/reports/expenses.${kind}?${qs}`, `expenses.${kind}`); } catch (ex) { setErr(ex.message); } }

  const axis = { tick: { fill: cc.text, fontSize: 12 }, stroke: cc.grid };
  const tip = { contentStyle: { background: theme === 'dark' ? '#121b2c' : '#fff', border: '1px solid ' + cc.grid, borderRadius: 8 }, formatter: (v) => inr(v) };
  const kpis = d ? [['💰', 'Total', d.total, lakh], ['🧾', 'Entries', d.count, (v) => Math.round(v)], ['⌀', 'Average per entry', d.average, inr], ['🏛️', 'GST included', d.gst, lakh]] : [];

  return (
    <>
      <div className="top"><h1>Reports</h1>
        <div className="actions noprint" style={{ margin: 0 }}>
          <button className="btn s" onClick={() => exportAs('xlsx')}>Excel</button>
          <button className="btn s" onClick={() => exportAs('pdf')}>PDF</button>
          <button className="btn s" onClick={() => window.print()}>Print</button>
        </div>
      </div>
      <div className="card rise noprint" style={{ marginBottom: 14 }}>
        <div className="chips" role="group" aria-label="Period">{PRESETS.map(([k, l]) => <button key={k} className={'chip-btn' + (preset === k ? ' on' : '')} onClick={() => pick(k)}>{l}</button>)}</div>
        <div className="bar" style={{ marginTop: 12, marginBottom: 0 }}>
          <label>From<input type="date" value={from} onChange={(e) => { setPreset('custom'); setFrom(e.target.value); }} /></label>
          <label>To<input type="date" value={to} onChange={(e) => { setPreset('custom'); setTo(e.target.value); }} /></label>
          <label>Department<select value={department} onChange={(e) => setDepartment(e.target.value)}><option value="">All</option>{lk.allDepartments.map((x) => <option key={x}>{x}</option>)}</select></label>
          <label>Category<select value={category} onChange={(e) => setCategory(e.target.value)}><option value="">All</option>{lk.categories.map((x) => <option key={x}>{x}</option>)}</select></label>
          <label>Vendor<select value={vendor} onChange={(e) => setVendor(e.target.value)}><option value="">All</option>{lk.vendors.map((x) => <option key={x}>{x}</option>)}</select></label>
          <label>Status<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="spend">Approved + Paid (spend)</option><option value="all">All statuses</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}</select></label>
        </div>
      </div>
      {err && <div className="err" role="alert">{err}</div>}
      {!d ? <Skeleton rows={4} /> : d.count === 0 ? <div className="card empty">No expenses match these filters. Try a wider period or fewer filters.</div> : (
        <>
          <div className="grid kpis">{kpis.map(([ic, l, v, f], i) => <div className="card lift kpi rise" style={{ '--i': i }} key={l}><span className="ic" aria-hidden="true">{ic}</span><small>{l}</small><b><CountUp value={Number(v)} format={f} /></b></div>)}</div>
          <div className="grid c2">
            <div className="card rise"><h2>By month</h2><div className="chart"><ResponsiveContainer><BarChart data={d.byMonth}><CartesianGrid strokeDasharray="3 3" stroke={cc.grid} /><XAxis dataKey="name" {...axis} /><YAxis tickFormatter={(v) => lakh(v)} width={72} {...axis} /><Tooltip {...tip} /><Bar dataKey="amount" name="Spend" fill="#0f766e" radius={[6, 6, 0, 0]} /></BarChart></ResponsiveContainer></div></div>
            <div className="card rise" style={{ '--i': 1 }}><h2>By department</h2><div className="chart"><ResponsiveContainer><BarChart data={d.byDepartment} layout="vertical"><CartesianGrid strokeDasharray="3 3" stroke={cc.grid} /><XAxis type="number" tickFormatter={(v) => lakh(v)} {...axis} /><YAxis type="category" dataKey="name" width={110} {...axis} /><Tooltip {...tip} /><Bar dataKey="amount" name="Spend" fill="#2563eb" radius={[0, 6, 6, 0]} /></BarChart></ResponsiveContainer></div></div>
            <div className="card rise" style={{ '--i': 2 }}><h2>By category</h2><div className="chart"><ResponsiveContainer><PieChart><Pie data={d.byCategory} dataKey="amount" nameKey="name" innerRadius={55} outerRadius={90} stroke={theme === 'dark' ? '#121b2c' : '#fff'}>{d.byCategory.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}</Pie><Tooltip {...tip} /><Legend layout="vertical" align="right" verticalAlign="middle" wrapperStyle={{ color: cc.text, fontSize: 12 }} /></PieChart></ResponsiveContainer></div></div>
            <div className="card rise" style={{ '--i': 3 }}><h2>Top vendors</h2><div className="chart"><ResponsiveContainer><BarChart data={d.byVendor} layout="vertical"><CartesianGrid strokeDasharray="3 3" stroke={cc.grid} /><XAxis type="number" tickFormatter={(v) => lakh(v)} {...axis} /><YAxis type="category" dataKey="name" width={130} {...axis} /><Tooltip {...tip} /><Bar dataKey="amount" name="Spend" fill="#d97706" radius={[0, 6, 6, 0]} /></BarChart></ResponsiveContainer></div></div>
          </div>
          <div className="grid c2">
            {[['By status', d.byStatus], ['By payment method', d.byPayment]].map(([title, rows]) => (
              <div className="card rise" key={title}><h2>{title}</h2><div className="sc"><table><thead><tr><th>Name</th><th className="r">Entries</th><th className="r">Total</th></tr></thead>
                <tbody>{rows.map((r) => <tr key={r.name}><td>{r.name}</td><td className="r">{r.count}</td><td className="r">{inr(r.amount)}</td></tr>)}</tbody></table></div></div>))}
          </div>
        </>
      )}
    </>
  );
}
