import { useEffect, useState, type CSSProperties } from 'react';
import styles from './RollingNumber.module.scss';

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];

export function RollingNumber({ value }: { value: string }) {
  const [shown, setShown] = useState(() => value.replace(/\d/g, '0'));
  useEffect(() => {
    const frame = requestAnimationFrame(() => setShown(value));
    return () => cancelAnimationFrame(frame);
  }, [value]);

  const chars = [...value];
  const shownChars = [...shown.padStart(chars.length, '0')].slice(-chars.length);
  return (
    <span className={styles.rolling}>
      <span className="sr-only">{value}</span>
      <span aria-hidden="true" className={styles.track}>
        {chars.map((ch, i) => {
          const fromRight = chars.length - i;
          if (!/\d/.test(ch)) {
            return (
              <span key={`s${fromRight}`} className={styles.symbol}>
                {ch}
              </span>
            );
          }
          const target = /\d/.test(shownChars[i]) ? Number(shownChars[i]) : 0;
          return (
            <span key={`d${fromRight}`} className={styles.digit} style={{ '--i': fromRight } as CSSProperties}>
              <span className={styles.column} style={{ transform: `translateY(${-target * 10}%)` }}>
                {DIGITS.map((d) => (
                  <span key={d}>{d}</span>
                ))}
              </span>
            </span>
          );
        })}
      </span>
    </span>
  );
}
