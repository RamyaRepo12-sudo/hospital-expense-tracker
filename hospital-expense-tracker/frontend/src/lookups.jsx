import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from './api.js';
import { useAuth } from './auth.jsx';

const Ctx = createContext({ all: [], departments: [], categories: [], vendors: [], allDepartments: [], reload() {} });
export const useLookups = () => useContext(Ctx);

export function LookupsProvider({ children }) {
  const { user } = useAuth();
  const [all, setAll] = useState([]);
  const reload = useCallback(() => api('/api/lookups').then(setAll).catch(() => {}), []);
  useEffect(() => { if (user) reload(); else setAll([]); }, [user, reload]);
  const value = useMemo(() => {
    const names = (type, onlyActive) => all.filter((l) => l.type === type && (!onlyActive || l.active)).map((l) => l.name);
    return { all, reload, departments: names('DEPARTMENT', true), categories: names('CATEGORY', true), vendors: names('VENDOR', true), allDepartments: names('DEPARTMENT', false) };
  }, [all, reload]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
