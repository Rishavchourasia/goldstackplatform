import { useEffect, useState } from 'react';

export function useCountdown(deadlineIso: string | null | undefined): number {
  const deadline = deadlineIso ? new Date(deadlineIso).getTime() : 0;
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!deadline) return;
    const id = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(id);
  }, [deadline]);

  return deadline ? Math.max(0, Math.ceil((deadline - now) / 1000)) : 0;
}
