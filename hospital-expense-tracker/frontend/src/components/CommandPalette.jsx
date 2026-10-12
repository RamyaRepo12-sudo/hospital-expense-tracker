import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';

export default function CommandPalette({ items, onClose }) {
  const nav = useNavigate();
  const [q, setQ] = useState('');
  const [sel, setSel] = useState(0);
  const [found, setFound] = useState([]);
  const ref = useRef(null);
  useEffect(() => { ref.current && ref.current.focus(); }, []);
  useEffect(() => {
    if (q.trim().length < 2) { setFound([]); return undefined; }
    const t = setTimeout(() => api('/api/expenses?q=' + encodeURIComponent(q.trim())).then((r) => setFound(r.slice(0, 5))).catch(() => setFound([])), 250);
    return () => clearTimeout(t);
  }, [q]);
  const list = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const m = items.filter((i) => i.label.toLowerCase().includes(needle));
    const f = found.map((e) => ({ label: `${e.expenseNo} · ${e.vendor}`, hint: `${e.department} · ${e.status}`, icon: '🔎', run: () => nav('/expenses?q=' + e.expenseNo) }));
    return [...m, ...f];
  }, [items, found, q, nav]);
  useEffect(() => setSel(0), [q]);
  const go = (i) => { i.run(); onClose(); };
  function key(e) {
    if (e.key === 'Escape') onClose();
    if (e.key === 'ArrowDown') { e.preventDefault(); setSel((s) => Math.min(s + 1, list.length - 1)); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setSel((s) => Math.max(s - 1, 0)); }
    if (e.key === 'Enter' && list[sel]) go(list[sel]);
  }
  return (
    <div className="overlay topal" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="palette" role="dialog" aria-modal="true" aria-label="Command palette">
        <input ref={ref} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={key} placeholder="Jump to a page, run an action, or search expenses…" aria-label="Search commands" />
        <ul>
          {list.map((i, k) => (
            <li key={i.label} className={k === sel ? 'on' : ''} onMouseEnter={() => setSel(k)} onClick={() => go(i)}>
              <span aria-hidden="true">{i.icon}</span>{i.label}<small>{i.hint}</small>
            </li>))}
        </ul>
        {list.length === 0 && <div className="empty">No matches</div>}
      </div>
    </div>
  );
}
