import { createContext, useContext, useEffect, useState } from 'react';

const Ctx = createContext({ theme: 'light', toggle() {} });
export const useTheme = () => useContext(Ctx);
export const chartColors = (t) => (t === 'dark' ? { grid: '#243049', text: '#94a3b8' } : { grid: '#e2e8f0', text: '#64748b' });

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    try { const s = localStorage.getItem('theme'); if (s) return s; } catch { /* ignore */ }
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('theme', theme); } catch { /* ignore */ }
  }, [theme]);
  return <Ctx.Provider value={{ theme, toggle: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')) }}>{children}</Ctx.Provider>;
}
