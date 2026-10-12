import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import CountUp from '../components/CountUp.jsx';
import Ecg from '../components/Ecg.jsx';
import Skeleton from '../components/Skeleton.jsx';
import { chartColors, useTheme } from '../theme.jsx';
import { Badge, can, inr, lakh, level, total } from '../util.jsx';

const COLORS = ['#0f766e', '#2563eb', '#d97706', '#7c3aed', '#db2777', '#65a30d', '#0891b2', '#64748b', '#a16207'];

export default function Dashboard() {
  const { user } = useAuth();
  const { theme } = useTheme();
  const nav = useNavigate();
  const cc = chartColors(theme);
  const [d, setD] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api('/api/dashboard').then(setD).catch((e) => setErr(e.message)); }, []);
  if (err) return <div className="err" role="alert">{err}</div>;
  if (!d) return <><div className="top"><h1>Dashboard</h1></div><Skeleton rows={4} /></>;

  const h = new Date().getHours();
  const greet = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  const delta = d.lastMonth > 0 ? Math.round(((d.thisMonth - d.lastMonth) / d.lastMonth) * 100) : 0;
  const util = d.budgetTotal > 0 ? Math.round((d.thisMonth / d.budgetTotal) * 100) : 0;
  const alerts = d.departments.filter((x) => x.utilization >= 0.8);
  const top = [...d.departments].sort((a, b) => b.spent - a.spent)[0];
  const share = d.thisMonth > 0 && top ? Math.round((top.spent / d.thisMonth) * 100) : 0;
  const insights = [
    [delta >= 0 ? '📈' : '📉', `Spending is ${Math.abs(delta)}% ${delta >= 0 ? 'higher' : 'lower'} than last month.`],
    top && ['🏥', `${top.department} is the biggest spender this month, at ${share}% of the total.`],
    alerts.length ? ['⚠️', `${alerts.length} department${alerts.length > 1 ? 's are' : ' is'} at 80% or more of budget: ${alerts.map((a) => a.department).join(', ')}.`] : ['✅', 'Every department is within budget.'],
    d.pendingCount ? ['⏳', `${d.pendingCount} expense${d.pendingCount > 1 ? 's' : ''} worth ${lakh(d.pendingAmount)} ${d.pendingCount > 1 ? 'are' : 'is'} waiting for approval.`] : ['🎉', 'No approvals are waiting.'],
  ].filter(Boolean);
  const kpis = [
    ['💰', 'Total expenses', d.totalExpenses, lakh, 'Approved and paid, all time', ''],
    ['📅', 'This month', d.thisMonth, lakh, `${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta)}% vs last month`, 'k2'],
    ['⏳', 'Pending approvals', d.pendingCount, (v) => Math.round(v), `${lakh(d.pendingAmount)} awaiting review`, 'k3'],
    ['📊', 'Budget utilization', util, (v) => Math.round(v) + '%', `${lakh(d.thisMonth)} of ${lakh(d.budgetTotal)}`, 'k4'],
    ['🧾', 'Outstanding payments', d.outstanding, lakh, 'Approved, not yet paid', 'k5'],
  ];
  const axis = { tick: { fill: cc.text, fontSize: 12 }, stroke: cc.grid };
  const tip = { contentStyle: { background: theme === 'dark' ? '#121b2c' : '#fff', border: '1px solid ' + cc.grid, borderRadius: 8 }, formatter: (v) => inr(v) };
  return (
    <>
      <div className="hero rise">
        <Ecg className="hero-ecg" />
        <div>
          <span className="chip">{user.role.replace(/_/g, ' ')}</span>
          <h1>{greet}, {user.name.split(' ')[0]}</h1>
          <p>Here is the hospital's financial position for this month.</p>
        </div>
        <div className="actions" style={{ margin: 0 }}>
          {can(user, 'approve') && d.pendingCount > 0 && <button className="btn" onClick={() => nav('/approvals')}>Review {d.pendingCount} pending</button>}
          {can(user, 'create') && <button className="btn s" onClick={() => nav('/expenses')}>Go to expenses</button>}
        </div>
      </div>
      <div className="grid kpis">{kpis.map(([ic, l, v, f, s, c], i) => (
        <div className={'card lift kpi rise ' + c} style={{ '--i': i + 1 }} key={l}><span className="ic" aria-hidden="true">{ic}</span><small>{l}</small><b><CountUp value={v} format={f} /></b><span>{s}</span></div>))}</div>
      <div className="grid c2">
        <div className="card rise" style={{ '--i': 6 }}><h2>✨ Smart insights</h2><div className="insights">{insights.map(([ic, t]) => <div className="insight" key={t}><span aria-hidden="true">{ic}</span><span>{t}</span></div>)}</div></div>
        <div className="card rise" style={{ '--i': 7 }}><h2>Monthly expense trend</h2><div className="chart">
          <ResponsiveContainer><AreaChart data={d.trend}><CartesianGrid strokeDasharray="3 3" stroke={cc.grid} /><XAxis dataKey="month" {...axis} /><YAxis tickFormatter={(v) => lakh(v)} width={72} {...axis} /><Tooltip {...tip} />
            <Area dataKey="amount" name="Spend" stroke="#0f766e" fill="#0f766e33" animationDuration={1200} /></AreaChart></ResponsiveContainer></div></div>
        <div className="card rise" style={{ '--i': 8 }}><h2>Department spending this month</h2><div className="hint">Click a bar to open that department's expenses.</div><div className="chart" style={{ height: 230 }}>
          <ResponsiveContainer><BarChart data={d.departments} layout="vertical"><CartesianGrid strokeDasharray="3 3" stroke={cc.grid} /><XAxis type="number" tickFormatter={(v) => lakh(v)} {...axis} /><YAxis type="category" dataKey="department" width={110} {...axis} /><Tooltip {...tip} />
            <Bar dataKey="spent" name="Spent" fill="#2563eb" radius={[0, 6, 6, 0]} cursor="pointer" animationDuration={1200} onClick={(p) => nav('/expenses?department=' + encodeURIComponent(p.department || (p.payload && p.payload.department)))} /></BarChart></ResponsiveContainer></div></div>
        <div className="card rise" style={{ '--i': 9 }}><h2>Category distribution</h2><div className="chart">
          <ResponsiveContainer><PieChart><Pie data={d.categories} dataKey="amount" nameKey="name" innerRadius={55} outerRadius={90} stroke={theme === 'dark' ? '#121b2c' : '#fff'} animationDuration={1200}>
            {d.categories.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}</Pie><Tooltip {...tip} /><Legend layout="vertical" align="right" verticalAlign="middle" wrapperStyle={{ color: cc.text, fontSize: 12 }} /></PieChart></ResponsiveContainer></div></div>
      </div>
      {alerts.length > 0 && (
        <div className="card rise" style={{ marginBottom: 14, '--i': 10 }}>
          <h2>Budget alerts</h2>
          {alerts.map((a) => <div key={a.department} className={'alert ' + level(a.utilization)[1]}><b>{level(a.utilization)[0]}:</b> {a.department} has used {Math.round(a.utilization * 100)}% of its monthly budget.</div>)}
        </div>
      )}
      <div className="card rise" style={{ '--i': 11 }}><h2>Recent expenses</h2><div className="sc"><table>
        <thead><tr><th>ID</th><th>Date</th><th>Category</th><th>Department</th><th>Vendor</th><th className="r">Total</th><th>Status</th></tr></thead>
        <tbody>{d.recent.map((e, i) => <tr key={e.id} style={{ animationDelay: i * 60 + 'ms' }}><td>{e.expenseNo}</td><td>{e.date}</td><td>{e.category}</td><td>{e.department}</td><td>{e.vendor}</td><td className="r">{inr(total(e))}</td><td><Badge status={e.status} /></td></tr>)}</tbody>
      </table></div></div>
    </>
  );
}
