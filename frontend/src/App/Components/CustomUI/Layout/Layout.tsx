import type { CSSProperties, ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import styles from './Layout.module.scss';

type Space = 1 | 2 | 3 | 4 | 5 | 6 | 8 | 10;

export function Stack({
  children,
  gap = 4,
  direction = 'column',
  align,
  justify,
  wrap,
  className,
}: {
  children: ReactNode;
  gap?: Space;
  direction?: 'row' | 'column';
  align?: CSSProperties['alignItems'];
  justify?: CSSProperties['justifyContent'];
  wrap?: boolean;
  className?: string;
}) {
  return (
    <div
      className={cx(styles.stack, wrap && styles.wrap, className)}
      style={{ gap: `var(--space-${gap})`, flexDirection: direction, alignItems: align, justifyContent: justify }}
    >
      {children}
    </div>
  );
}

export function Grid({
  children,
  min = 220,
  gap = 4,
  className,
}: {
  children: ReactNode;

  min?: number;
  gap?: Space;
  className?: string;
}) {
  return (
    <div
      className={cx(styles.grid, className)}
      style={{ gap: `var(--space-${gap})`, gridTemplateColumns: `repeat(auto-fit, minmax(min(${min}px, 100%), 1fr))` }}
    >
      {children}
    </div>
  );
}
