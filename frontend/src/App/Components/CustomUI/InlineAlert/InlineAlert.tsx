import type { ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import { Icon, type IconName } from '../Icon/Icon';
import styles from './InlineAlert.module.scss';

type Tone = 'info' | 'success' | 'warning' | 'danger';
const ICON: Record<Tone, IconName> = { info: 'info', success: 'check', warning: 'clock', danger: 'alert' };

export function InlineAlert({
  tone = 'info',
  title,
  children,
  actions,
}: {
  tone?: Tone;
  title?: ReactNode;
  children?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className={cx(styles.alert, styles[tone])} role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}>
      <Icon name={ICON[tone]} className={styles.icon} />
      <div className={styles.body}>
        {title && <strong className={styles.title}>{title}</strong>}
        {children && <div>{children}</div>}
        {actions && <div className={styles.actions}>{actions}</div>}
      </div>
    </div>
  );
}
