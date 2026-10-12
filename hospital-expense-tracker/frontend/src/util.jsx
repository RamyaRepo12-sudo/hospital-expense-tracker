export const PAYMENT_METHODS = ['Bank Transfer', 'UPI', 'Card', 'Cheque', 'Cash'];
export const STATUSES = ['Draft', 'Pending', 'Approved', 'Rejected', 'Paid'];

// UI hints only. The Spring Boot API enforces every one of these rules.
export const PERMS = {
  create: ['SUPER_ADMIN', 'FINANCE_ADMIN', 'DEPARTMENT_MANAGER', 'ACCOUNTANT', 'STAFF'],
  approve: ['SUPER_ADMIN', 'FINANCE_ADMIN', 'DEPARTMENT_MANAGER'],
  pay: ['SUPER_ADMIN', 'FINANCE_ADMIN', 'ACCOUNTANT'],
  delete: ['SUPER_ADMIN', 'FINANCE_ADMIN'],
  budget: ['SUPER_ADMIN', 'FINANCE_ADMIN'],
  audit: ['SUPER_ADMIN', 'FINANCE_ADMIN', 'AUDITOR'],
  users: ['SUPER_ADMIN'],
  settings: ['SUPER_ADMIN', 'FINANCE_ADMIN'],
  analytics: ['SUPER_ADMIN', 'FINANCE_ADMIN', 'DEPARTMENT_MANAGER', 'ACCOUNTANT', 'AUDITOR'],
};
export const can = (user, perm) => !!user && PERMS[perm].includes(user.role);

export const inr = (n) => '₹' + Math.round(Number(n) || 0).toLocaleString('en-IN');
export const lakh = (n) => (Number(n) >= 1e5 ? '₹' + (Number(n) / 1e5).toFixed(2) + ' L' : inr(n));
export const total = (e) => Number(e.amount) + Number(e.gst || 0);

const ICON = { Draft: '✎', Pending: '⏳', Approved: '✓', Rejected: '✕', Paid: '₹' };
export const Badge = ({ status }) => <span className={'badge ' + status}>{ICON[status]} {status}</span>;

export function level(p) {
  if (p >= 1) return ['Exceeded', 'bad'];
  if (p >= 0.9) return ['Critical', 'bad'];
  if (p >= 0.8) return ['High', 'warn'];
  if (p >= 0.7) return ['Warning', 'warn'];
  return ['Normal', ''];
}
