import { useEffect, useState } from 'react';

export default function Ring({ pct, cls = '' }) {
  const r = 38, c = 2 * Math.PI * r, p = Math.min(1, pct);
  const [on, setOn] = useState(false);
  useEffect(() => { const t = setTimeout(() => setOn(true), 80); return () => clearTimeout(t); }, []);
  return (
    <svg width="92" height="92" viewBox="0 0 92 92" role="img" aria-label={`${Math.round(pct * 100)} percent of budget used`}>
      <circle cx="46" cy="46" r={r} className="ring-bg" />
      <circle cx="46" cy="46" r={r} className={'ring-fg ' + cls} strokeDasharray={c} strokeDashoffset={on ? c * (1 - p) : c} transform="rotate(-90 46 46)" />
      <text x="46" y="52" textAnchor="middle" className="ring-t">{Math.round(pct * 100)}%</text>
    </svg>
  );
}
