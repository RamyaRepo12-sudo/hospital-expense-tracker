import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, download } from '../api.js';
import { useLookups } from '../lookups.jsx';
import { useAuth } from '../auth.jsx';
import Modal from '../components/Modal.jsx';
import ExpenseForm from '../components/ExpenseForm.jsx';
import Skeleton from '../components/Skeleton.jsx';
import { Badge, STATUSES, can, inr, total } from '../util.jsx';

const PAGE = 10;

export default function Expenses({ mode }) {
  const { user } = useAuth();
  const lk = useLookups();
  const pendingOnly = mode === 'pending';
  const [rows, setRows] = useState([]);
  const [params] = useSearchParams();
  const [q, setQ] = useState(params.get('q') || '');
  const [dept, setDept] = useState(params.get('department') || '');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [toast, setToast] = useState('');
  const [form, setForm] = useState(null);      // null | {} (new) | expense (edit)
  const [dialog, setDialog] = useState(null);  // { type, expense }
  const [reason, setReason] = useState('');
  const [dErr, setDErr] = useState('');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState({ key: 'date', dir: -1 });
  useEffect(() => setPage(0), [dept, status, q]);
  const sorted = useMemo(() => [...rows].sort((a, b) => {
    const x = sort.key === 'total' ? total(a) : a.date, y = sort.key === 'total' ? total(b) : b.date;
    return (x > y ? 1 : x < y ? -1 : 0) * sort.dir;
  }), [rows, sort]);
  const pages = Math.max(1, Math.ceil(sorted.length / PAGE));
  const pageRows = sorted.slice(page * PAGE, page * PAGE + PAGE);
  const toggleSort = (key) => setSort((s) => (s.key === key ? { key, dir: -s.dir } : { key, dir: -1 }));
  const arrow = (key) => (sort.key === key ? (sort.dir > 0 ? ' ▲' : ' ▼') : '');

  const load = useCallback(async () => {
    setLoading(true); setErr('');
    try {
      const p = new URLSearchParams({ department: dept, status: pendingOnly ? 'Pending' : status, q });
      setRows(await api('/api/expenses?' + p));
    } catch (ex) { setErr(ex.message); }
    finally { setLoading(false); }
  }, [dept, status, q, pendingOnly]);

  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t); }, [load]);
  useEffect(() => { if (!toast) return; const t = setTimeout(() => setToast(''), 2500); return () => clearTimeout(t); }, [toast]);

  async function act(path, method = 'POST', body) {
    try { await api(`/api/expenses/${path}`, { method, body }); return true; }
    catch (ex) { setDErr(ex.message); return false; }
  }
  async function runDialog() {
    const { type, expense: e } = dialog;
    setDErr('');
    if (type === 'reject' && !reason.trim()) return setDErr('Enter a reason so the submitter knows what to fix.');
    const ok =
      type === 'approve' ? await act(`${e.id}/approve`) :
      type === 'reject' ? await act(`${e.id}/reject`, 'POST', { reason }) :
      type === 'pay' ? await act(`${e.id}/pay`) :
      type === 'delete' ? await act(e.id, 'DELETE') : false;
    if (ok) { setDialog(null); setReason(''); setToast('Done'); load(); }
  }
  async function submit(e) {
    try { await api(`/api/expenses/${e.id}/submit`, { method: 'POST' }); setToast('Submitted for approval'); load(); }
    catch (ex) { setErr(ex.message); }
  }
  async function exportAs(kind) {
    try {
      const p = new URLSearchParams({ department: dept, status: pendingOnly ? 'Pending' : status, q });
      await download(`/api/reports/expenses.${kind}?${p}`, `expenses.${kind}`);
    } catch (ex) { setErr(ex.message); }
  }
  function csv() {
    const lines = [['ID', 'Date', 'Category', 'Department', 'Vendor', 'Total', 'Status'],
      ...rows.map((e) => [e.expenseNo, e.date, e.category, e.department, `"${e.vendor}"`, total(e), e.status])];
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([lines.map((l) => l.join(',')).join('\n')], { type: 'text/csv' }));
    a.download = 'expenses.csv'; a.click();
  }
  const titles = { approve: 'Approve expense?', reject: 'Reject expense?', pay: 'Mark as paid?', delete: 'Delete expense?' };

  return (
    <>
      <div className="top">
        <h1>{pendingOnly ? 'Approvals' : 'Expenses'}</h1>
        {can(user, 'create') && <button className="btn" onClick={() => setForm({})}>+ Add Expense</button>}
      </div>
      <div className="card rise">
        <div className="bar">
          <input placeholder="Search ID, invoice, vendor" aria-label="Search" value={q} onChange={(e) => setQ(e.target.value)} />
          <select aria-label="Department" value={dept} onChange={(e) => setDept(e.target.value)}>
            <option value="">All departments</option>{lk.allDepartments.map((d) => <option key={d}>{d}</option>)}
          </select>
          {!pendingOnly && (
            <select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>{STATUSES.map((s) => <option key={s}>{s}</option>)}
            </select>
          )}
          <button className="btn s" onClick={csv}>CSV</button>
          <button className="btn s" onClick={() => exportAs('xlsx')}>Excel</button>
          <button className="btn s" onClick={() => exportAs('pdf')}>PDF</button>
        </div>
        {err && <div className="err" role="alert">{err}</div>}
        {loading ? <Skeleton rows={6} /> : rows.length === 0 ? (
          <div className="empty">{pendingOnly ? 'All caught up. Nothing is waiting for approval.' : 'No expenses found. Try changing your filters or add your first expense.'}</div>
        ) : (
          <><div className="sc"><table>
            <thead><tr><th>ID</th><th><button className="sortb" onClick={() => toggleSort('date')}>Date{arrow('date')}</button></th><th>Category</th><th>Department</th><th>Vendor</th><th className="r"><button className="sortb" onClick={() => toggleSort('total')}>Total{arrow('total')}</button></th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{pageRows.map((e) => (
              <tr key={e.id}>
                <td>{e.expenseNo}</td><td>{e.date}</td><td>{e.category}</td><td>{e.department}</td><td>{e.vendor}</td>
                <td className="r">{inr(total(e))}</td><td><Badge status={e.status} /></td>
                <td className="acts">
                  <button className="btn s sm" onClick={() => setDialog({ type: 'view', expense: e })}>View</button>
                  {['Draft', 'Rejected'].includes(e.status) && can(user, 'create') && <>
                    <button className="btn s sm" onClick={() => setForm(e)}>Edit</button>
                    <button className="btn sm" onClick={() => submit(e)}>Submit</button></>}
                  {e.status === 'Pending' && can(user, 'approve') && <>
                    <button className="btn sm" onClick={() => { setDErr(''); setDialog({ type: 'approve', expense: e }); }}>Approve</button>
                    <button className="btn bad sm" onClick={() => { setDErr(''); setReason(''); setDialog({ type: 'reject', expense: e }); }}>Reject</button></>}
                  {e.status === 'Approved' && can(user, 'pay') && <button className="btn sm" onClick={() => { setDErr(''); setDialog({ type: 'pay', expense: e }); }}>Mark paid</button>}
                  {can(user, 'delete') && <button className="btn s sm" onClick={() => { setDErr(''); setDialog({ type: 'delete', expense: e }); }}>Delete</button>}
                </td>
              </tr>))}
            </tbody>
          </table></div>
          <div className="pager">
            <span className="mute">{sorted.length} expenses · page {page + 1} of {pages}</span>
            <div>
              <button className="btn s sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
              <button className="btn s sm" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          </div></>
        )}
      </div>

      {form && <ExpenseForm expense={form.id ? form : null} onClose={() => setForm(null)} onSaved={(m) => { setForm(null); setToast(m); load(); }} />}

      {dialog && dialog.type === 'view' && (
        <Modal title={`${dialog.expense.expenseNo}`} onClose={() => setDialog(null)}>
          <Badge status={dialog.expense.status} />
          <table className="kv"><tbody>
            {[['Date', dialog.expense.date], ['Department', dialog.expense.department], ['Category', dialog.expense.category], ['Vendor', dialog.expense.vendor],
              ['Invoice', dialog.expense.invoiceNo], ['Payment method', dialog.expense.paymentMethod], ['Amount', inr(dialog.expense.amount)],
              ['GST', inr(dialog.expense.gst)], ['Total', inr(total(dialog.expense))], ['Submitted by', dialog.expense.createdBy],
              ['Invoice file', dialog.expense.attachmentName ? <button className="btn s sm" onClick={() => download(`/api/expenses/${dialog.expense.id}/attachment`, dialog.expense.attachmentName).catch((ex) => setErr(ex.message))}>⬇ {dialog.expense.attachmentName}</button> : '-'],
              ['Description', dialog.expense.description || '-'], ...(dialog.expense.rejectionReason ? [['Rejection reason', dialog.expense.rejectionReason]] : [])]
              .map(([k, v]) => <tr key={k}><th>{k}</th><td>{v}</td></tr>)}
          </tbody></table>
          <div className="actions"><button className="btn s" onClick={() => setDialog(null)}>Close</button></div>
        </Modal>
      )}
      {dialog && dialog.type !== 'view' && (
        <Modal title={titles[dialog.type]} onClose={() => setDialog(null)}>
          <p>{dialog.type === 'delete' ? 'This action cannot be undone.' : `${dialog.expense.expenseNo}: ${inr(total(dialog.expense))} (${dialog.expense.vendor})`}</p>
          {dialog.type === 'reject' && <label>Reason (required)<textarea rows="2" value={reason} onChange={(e) => setReason(e.target.value)} /></label>}
          {dErr && <div className="err" role="alert">{dErr}</div>}
          <div className="actions">
            <button className="btn s" onClick={() => setDialog(null)}>Cancel</button>
            <button className={'btn' + (['reject', 'delete'].includes(dialog.type) ? ' bad' : '')} onClick={runDialog}>
              {{ approve: 'Approve', reject: 'Reject expense', pay: 'Mark paid', delete: 'Delete expense' }[dialog.type]}
            </button>
          </div>
        </Modal>
      )}
      {toast && <div className="toast" role="status">{toast}</div>}
    </>
  );
}
