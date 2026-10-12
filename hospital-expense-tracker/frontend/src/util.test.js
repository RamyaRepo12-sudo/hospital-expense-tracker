import { describe, expect, it } from 'vitest';
import { can, inr, lakh, level, total } from './util.jsx';

describe('formatting', () => {
  it('formats rupees with Indian grouping', () => {
    expect(inr(1234567)).toBe('₹12,34,567');
    expect(inr(0)).toBe('₹0');
  });
  it('shows lakhs for large values only', () => {
    expect(lakh(250000)).toBe('₹2.50 L');
    expect(lakh(99999)).toBe('₹99,999');
  });
  it('adds amount and GST', () => {
    expect(total({ amount: 1000, gst: 120 })).toBe(1120);
    expect(total({ amount: 1000 })).toBe(1000);
  });
});

describe('budget levels', () => {
  it.each([[0.5, 'Normal'], [0.7, 'Warning'], [0.85, 'High'], [0.95, 'Critical'], [1.2, 'Exceeded']])('%s -> %s', (pct, label) => {
    expect(level(pct)[0]).toBe(label);
  });
});

describe('permissions (UI hints)', () => {
  it('lets finance approve and pay but not staff', () => {
    expect(can({ role: 'FINANCE_ADMIN' }, 'approve')).toBe(true);
    expect(can({ role: 'FINANCE_ADMIN' }, 'pay')).toBe(true);
    expect(can({ role: 'STAFF' }, 'approve')).toBe(false);
  });
  it('limits user management to the super admin and keeps auditors read-only', () => {
    expect(can({ role: 'SUPER_ADMIN' }, 'users')).toBe(true);
    expect(can({ role: 'FINANCE_ADMIN' }, 'users')).toBe(false);
    expect(can({ role: 'AUDITOR' }, 'create')).toBe(false);
    expect(can(null, 'create')).toBe(false);
  });
});
