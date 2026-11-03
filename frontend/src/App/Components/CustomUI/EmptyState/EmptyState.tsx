import type { ReactNode } from 'react';
import { Icon, type IconName } from '../Icon/Icon';
import { Text } from '../Text/Text';
import styles from './EmptyState.module.scss';

export function EmptyState({
  icon = 'sparkle',
  title,
  description,
  action,
}: {
  icon?: IconName;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={styles.empty}>
      <span className={styles.icon}>
        <Icon name={icon} size={22} />
      </span>
      <Text variant="h3">{title}</Text>
      {description && (
        <Text variant="small" tone="muted" align="center">
          {description}
        </Text>
      )}
      {action}
    </div>
  );
}
