import { useEffect, useState } from 'react';

export default function WakeBanner() {
  const [on, setOn] = useState(false);
  useEffect(() => {
    const h = (e) => setOn(!!e.detail);
    window.addEventListener('api-waking', h);
    return () => window.removeEventListener('api-waking', h);
  }, []);
  if (!on) return null;
  return (
    <div className="wake" role="status">
      <span className="spin" aria-hidden="true" />
      Waking up the server. Free hosting sleeps when idle, so this can take up to a minute.
    </div>
  );
}
