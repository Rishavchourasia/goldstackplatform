import type { ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import styles from './Badge.module.scss';

export type BadgeTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger';

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={cx(styles.badge, styles[tone])}>{children}</span>;
}
