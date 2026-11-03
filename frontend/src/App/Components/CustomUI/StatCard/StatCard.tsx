import type { ReactNode } from 'react';
import { Card } from '../Card/Card';
import { Skeleton } from '../Skeleton/Skeleton';
import { Text } from '../Text/Text';
import styles from './StatCard.module.scss';

export function StatCard({
  label,
  value,
  sub,
  loading,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  loading?: boolean;
}) {
  return (
    <Card padding="sm" className={styles.stat}>
      <Text variant="overline" tone="muted">
        {label}
      </Text>
      {loading ? (
        <Skeleton height={28} width="70%" className={styles.skeleton} />
      ) : (
        <Text as="div" variant="h1" tabular className={styles.value}>
          {value}
        </Text>
      )}
      {sub && !loading && (
        <Text as="div" variant="small" tone="muted">
          {sub}
        </Text>
      )}
    </Card>
  );
}
