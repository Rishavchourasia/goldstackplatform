import type { ReactNode } from 'react';
import { useDocumentTitle } from '../CustomHooks/useDocumentTitle';
import { Text } from '../CustomUI';
import styles from './Layout.module.scss';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  useDocumentTitle(title);
  return (
    <header className={styles.pageHeader}>
      <div>
        <Text variant="h1">{title}</Text>
        {subtitle && (
          <Text tone="secondary" className={styles.subtitle}>
            {subtitle}
          </Text>
        )}
      </div>
      {actions && <div className={styles.pageActions}>{actions}</div>}
    </header>
  );
}
