import { useEffect, useState } from 'react';

const reduce = () => typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default function CountUp({ value, format = (v) => Math.round(v).toLocaleString('en-IN'), ms = 1000 }) {
  const [v, setV] = useState(reduce() ? value : 0);
  useEffect(() => {
    if (reduce()) { setV(value); return undefined; }
    let raf, start;
    const tick = (t) => {
      if (start === undefined) start = t;
      const p = Math.min(1, (t - start) / ms);
      setV(value * (1 - Math.pow(1 - p, 3)));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [value, ms]);
  return <>{format(v)}</>;
}
