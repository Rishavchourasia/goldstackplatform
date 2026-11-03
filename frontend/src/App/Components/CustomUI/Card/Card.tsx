import type { HTMLAttributes, ReactNode } from 'react';
import { cx } from '../../../Utils/cx';
import { Text } from '../Text/Text';
import styles from './Card.module.scss';

interface CardProps extends HTMLAttributes<HTMLElement> {
  tone?: 'default' | 'highlight' | 'accent';
  padding?: 'sm' | 'md' | 'lg';

  interactive?: boolean;

  as?: 'section' | 'div' | 'article' | 'form';

  noValidate?: boolean;
}

export function Card({
  tone = 'default',
  padding = 'md',
  interactive,
  as: Element = 'section',
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <Element
      className={cx(styles.card, styles[tone], styles[`pad-${padding}`], interactive && styles.interactive, className)}
      {...rest}
    >
      {children}
    </Element>
  );
}

export function CardHeader({
  title,
  subtitle,
  action,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={styles.header}>
      <div>
        <Text variant="h2">{title}</Text>
        {subtitle && (
          <Text variant="small" tone="muted">
            {subtitle}
          </Text>
        )}
      </div>
      {action && <div className={styles.action}>{action}</div>}
    </div>
  );
}
