import { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { inr, total } from '../util.jsx';

export default function Vendors() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api('/api/expenses').then(setList).catch((e) => setErr(e.message)); }, []);
  const rows = useMemo(() => {
    const m = {};
    (list || []).forEach((e) => {
      const v = (m[e.vendor] ||= { vendor: e.vendor, n: 0, paid: 0, out: 0 });
      v.n++;
      if (e.status === 'Paid') v.paid += total(e);
      if (e.status === 'Approved') v.out += total(e);
    });
    return Object.values(m).sort((a, b) => a.vendor.localeCompare(b.vendor));
  }, [list]);
  if (err) return <div className="err" role="alert">{err}</div>;
  if (!list) return <div className="empty">Loading vendors…</div>;
  return (
    <>
      <div className="top"><h1>Vendors</h1></div>
      <div className="card"><div className="sc"><table>
        <thead><tr><th>Vendor</th><th className="r">Invoices</th><th className="r">Total paid</th><th className="r">Outstanding</th></tr></thead>
        <tbody>{rows.map((r) => <tr key={r.vendor}><td>{r.vendor}</td><td className="r">{r.n}</td><td className="r">{inr(r.paid)}</td><td className="r">{inr(r.out)}</td></tr>)}</tbody>
      </table></div></div>
    </>
  );
}
