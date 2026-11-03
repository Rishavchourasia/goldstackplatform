import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { cx } from '../../../Utils/cx';
import { Spinner } from '../Spinner/Spinner';
import styles from './Button.module.scss';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

interface StyleProps {
  variant?: Variant;
  size?: Size;
  fullWidth?: boolean;
}

interface ButtonProps extends StyleProps, ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean;
  children: ReactNode;
}

function classes({ variant = 'secondary', size = 'md', fullWidth }: StyleProps, extra?: string) {
  return cx(styles.button, styles[variant], styles[size], fullWidth && styles.fullWidth, extra);
}

export function Button({
  variant,
  size,
  fullWidth,
  loading = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={classes({ variant, size, fullWidth }, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <Spinner size={14} className={styles.spinner} />}
      <span className={cx(styles.label, loading && styles.labelLoading)}>{children}</span>
    </button>
  );
}

export function ButtonLink({ variant, size, fullWidth, className, children, ...rest }: StyleProps & LinkProps) {
  return (
    <Link className={classes({ variant, size, fullWidth }, className)} {...rest}>
      <span className={styles.label}>{children}</span>
    </Link>
  );
}
