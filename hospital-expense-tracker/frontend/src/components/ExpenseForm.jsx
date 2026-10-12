import { useState } from 'react';
import { api, upload } from '../api.js';
import { useLookups } from '../lookups.jsx';
import Modal from './Modal.jsx';
import { PAYMENT_METHODS, inr } from '../util.jsx';

const withCurrent = (list, value) => (value && !list.includes(value) ? [value, ...list] : list);

export default function ExpenseForm({ expense, onClose, onSaved }) {
  const lk = useLookups();
  const [f, setF] = useState(expense ? { ...expense } : {
    date: new Date().toISOString().slice(0, 10), department: lk.departments[0] || '', category: lk.categories[0] || '',
    vendor: lk.vendors[0] || '', amount: '', gst: 0, paymentMethod: PAYMENT_METHODS[0], invoiceNo: '', description: '',
  });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [file, setFile] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const sum = (Number(f.amount) || 0) + (Number(f.gst) || 0);
  const missing = !lk.departments.length || !lk.categories.length || !lk.vendors.length;

  async function save(submit) {
    if (!f.date) return setErr('Choose a date.');
    if (!f.department || !f.category || !f.vendor) return setErr('Choose a department, category and vendor.');
    if (!(Number(f.amount) > 0)) return setErr('Enter an amount greater than 0.');
    if (!String(f.invoiceNo).trim()) return setErr('Enter the invoice number.');
    if (file && file.size > 5 * 1024 * 1024) return setErr('The invoice file is larger than 5 MB.');
    setErr(''); setBusy(true);
    const body = { date: f.date, department: f.department, category: f.category, vendor: f.vendor, amount: Number(f.amount),
      gst: Number(f.gst) || 0, paymentMethod: f.paymentMethod, invoiceNo: f.invoiceNo, description: f.description };
    try {
      const saved = await api(expense ? `/api/expenses/${expense.id}?submit=${submit}` : `/api/expenses?submit=${submit}`, { method: expense ? 'PUT' : 'POST', body });
      let msg = submit ? 'Submitted for approval' : 'Draft saved';
      if (file) {
        try { await upload(`/api/expenses/${saved.id}/attachment`, file); }
        catch (ex) { msg = 'Saved, but the invoice upload failed: ' + ex.message; }
      }
      onSaved(msg);
    } catch (ex) { setErr(ex.message); setBusy(false); }
  }
  const sel = (k, opts) => <select value={f[k]} onChange={set(k)}>{withCurrent(opts, f[k]).map((o) => <option key={o}>{o}</option>)}</select>;
  return (
    <Modal title={expense ? 'Edit expense' : 'Add expense'} onClose={onClose}>
      <div className="form">
        {missing && <div className="alert w2">Some lists are empty. A finance admin must add departments, categories and vendors under <b>Settings</b> first.</div>}
        <label>Date<input type="date" value={f.date} onChange={set('date')} /></label>
        <label>Department{sel('department', lk.departments)}</label>
        <label>Category{sel('category', lk.categories)}</label>
        <label>Vendor{sel('vendor', lk.vendors)}</label>
        <label>Amount (₹)<input type="number" min="0" step="0.01" value={f.amount} onChange={set('amount')} /></label>
        <label>GST (₹)<input type="number" min="0" step="0.01" value={f.gst} onChange={set('gst')} /></label>
        <label>Payment method{sel('paymentMethod', PAYMENT_METHODS)}</label>
        <label>Invoice number<input value={f.invoiceNo} maxLength={60} onChange={set('invoiceNo')} /></label>
        <label className="w2">Description<textarea rows="2" maxLength={500} value={f.description || ''} onChange={set('description')} /></label>
        <label className="w2">Invoice or receipt (PDF, JPG or PNG, up to 5 MB)
          <div className="file"><input type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={(e) => setFile(e.target.files[0] || null)} />
            {expense && expense.attachmentName && !file && <small>Current: {expense.attachmentName}. Choose a file to replace it.</small>}</div></label>
        <div className="w2"><b>Total: {inr(sum)}</b></div>
        {err && <div className="err w2" role="alert">{err}</div>}
        <div className="w2 actions">
          <button className="btn s" onClick={onClose}>Cancel</button>
          <button className="btn s" disabled={busy} onClick={() => save(false)}>Save draft</button>
          <button className="btn" disabled={busy} onClick={() => save(true)}>Submit for approval</button>
        </div>
      </div>
    </Modal>
  );
}
