import { useEffect, useMemo, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api } from './api.js';
import { useAuth } from './auth.jsx';
import { useTheme } from './theme.jsx';
import { can } from './util.jsx';
import ChangePassword from './components/ChangePassword.jsx';
import WakeBanner from './components/WakeBanner.jsx';
import MfaSetup from './components/MfaSetup.jsx';
import CommandPalette from './components/CommandPalette.jsx';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Expenses from './pages/Expenses.jsx';
import Vendors from './pages/Vendors.jsx';
import Budgets from './pages/Budgets.jsx';
import Audit from './pages/Audit.jsx';
import Users from './pages/Users.jsx';
import Reports from './pages/Reports.jsx';
import Settings from './pages/Settings.jsx';
import Recurring from './pages/Recurring.jsx';
import Security from './pages/Security.jsx';

function Layout({ children }) {
  const { user, logout, passwordChanged, refresh } = useAuth();
  const { theme, toggle } = useTheme();
  const loc = useLocation();
  const nav = useNavigate();
  const [alerts, setAlerts] = useState(0);
  const [palette, setPalette] = useState(false);
  const [pw, setPw] = useState(false);
  const [toast, setToast] = useState('');

  useEffect(() => {
    if (!can(user, 'analytics')) return;
    api('/api/dashboard').then((d) => setAlerts((can(user, 'approve') ? d.pendingCount : 0) + d.departments.filter((x) => x.utilization >= 0.8).length)).catch(() => {});
  }, [user, loc.pathname]);
  useEffect(() => {
    const h = (e) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPalette(true); } };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);
  useEffect(() => { if (!toast) return undefined; const t = setTimeout(() => setToast(''), 2500); return () => clearTimeout(t); }, [toast]);

  const links = [
    can(user, 'analytics') && ['/dashboard', '📊', 'Dashboard'],
    ['/expenses', '🧾', 'Expenses'],
    can(user, 'create') && ['/recurring', '🔁', 'Recurring'],
    can(user, 'approve') && ['/approvals', '✅', 'Approvals'],
    can(user, 'analytics') && ['/budgets', '🎯', 'Budgets'],
    can(user, 'analytics') && ['/reports', '📈', 'Reports'],
    can(user, 'analytics') && ['/vendors', '🏢', 'Vendors'],
    can(user, 'audit') && ['/audit', '🛡️', 'Audit Logs'],
    can(user, 'settings') && ['/settings', '⚙️', 'Settings'],
    can(user, 'users') && ['/users', '👥', 'Users'],
    ['/security', '🔐', 'Security'],
  ].filter(Boolean);
  const items = useMemo(() => [
    ...links.map(([to, icon, label]) => ({ label: 'Go to ' + label, hint: 'Page', icon, run: () => nav(to) })),
    { label: 'Toggle light / dark theme', hint: 'Action', icon: theme === 'dark' ? '☀️' : '🌙', run: toggle },
    { label: 'Change my password', hint: 'Action', icon: '🔑', run: () => setPw(true) },
    { label: 'Sign out', hint: 'Action', icon: '🚪', run: logout },
  // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [user, theme]);

  return (
    <div className="app">
      <nav aria-label="Main">
        <div className="logo"><i aria-hidden="true">✚</i>Hospital Finance</div>
        <button className="searchbtn" onClick={() => setPalette(true)} aria-label="Open command palette"><span>🔎 Search or jump to…</span><kbd>Ctrl K</kbd></button>
        <div className="links">
          {links.map(([to, icon, label]) => <NavLink key={to} to={to} className={({ isActive }) => (isActive ? 'on' : '')}><span aria-hidden="true">{icon}</span>{label}</NavLink>)}
        </div>
        <div className="who">
          <b>{user.name}</b><span>{user.role.replace(/_/g, ' ')}</span>
          <div className="row"><button className="btn s sm" onClick={() => setPw(true)}>Password</button><button className="btn s sm" onClick={logout}>Sign out</button></div>
        </div>
      </nav>
      <main>
        <header className="topbar">
          <span className="mute">{new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</span>
          <div className="tools">
            {can(user, 'analytics') && (
              <NavLink to={can(user, 'approve') ? '/approvals' : '/budgets'} className={'iconbtn' + (alerts > 0 ? ' bell-on' : '')} aria-label={`${alerts} items need attention`} title="Items needing attention">
                <span aria-hidden="true">🔔</span>{alerts > 0 && <span className="dot">{alerts}</span>}
              </NavLink>
            )}
            <button className="iconbtn" onClick={toggle} aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`} title="Toggle theme"><span aria-hidden="true">{theme === 'dark' ? '☀️' : '🌙'}</span></button>
          </div>
        </header>
        <div className="page" key={loc.pathname + loc.search}>{children}</div>
      </main>
      {palette && <CommandPalette items={items} onClose={() => setPalette(false)} />}
      {user.mustChange && <ChangePassword force onClose={() => {}} onDone={(m) => { passwordChanged(); setToast(m); }} />}
      {user.mfaSetupRequired && !user.mustChange && <MfaSetup force onClose={() => {}} onDone={async () => { await refresh(); setToast('Two-factor authentication is on'); }} />}
      {pw && !user.mustChange && <ChangePassword onClose={() => setPw(false)} onDone={(m) => { setPw(false); setToast(m); }} />}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function Protected({ perm, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (perm && !can(user, perm)) return <Navigate to="/expenses" replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  const { user, ready } = useAuth();
  if (!ready) return <><WakeBanner /><div className="splash"><div className="logo-big" aria-hidden="true">✚</div><p>Loading…</p></div></>;
  const home = user && can(user, 'analytics') ? '/dashboard' : '/expenses';
  return (
    <>
    <WakeBanner />
    <Routes>
      <Route path="/login" element={user ? <Navigate to={home} replace /> : <Login />} />
      <Route path="/dashboard" element={<Protected perm="analytics"><Dashboard /></Protected>} />
      <Route path="/expenses" element={<Protected><Expenses mode="all" /></Protected>} />
      <Route path="/approvals" element={<Protected perm="approve"><Expenses mode="pending" /></Protected>} />
      <Route path="/budgets" element={<Protected perm="analytics"><Budgets /></Protected>} />
      <Route path="/vendors" element={<Protected perm="analytics"><Vendors /></Protected>} />
      <Route path="/audit" element={<Protected perm="audit"><Audit /></Protected>} />
      <Route path="/recurring" element={<Protected perm="create"><Recurring /></Protected>} />
      <Route path="/security" element={<Protected><Security /></Protected>} />
      <Route path="/reports" element={<Protected perm="analytics"><Reports /></Protected>} />
      <Route path="/settings" element={<Protected perm="settings"><Settings /></Protected>} />
      <Route path="/users" element={<Protected perm="users"><Users /></Protected>} />
      <Route path="*" element={<Navigate to={user ? home : '/login'} replace />} />
    </Routes>
    </>
  );
}
