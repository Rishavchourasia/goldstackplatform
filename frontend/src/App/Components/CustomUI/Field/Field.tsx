import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { cx } from '../../../Utils/cx';
import styles from './Field.module.scss';

interface FieldShellProps {
  label: string;

  error?: string | null;

  hint?: ReactNode;
  className?: string;
}

function FieldShell({
  label,
  error,
  hint,
  className,
  id,
  children,
}: FieldShellProps & { id: string; children: ReactNode }) {
  return (
    <div className={cx(styles.field, className)}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {children}
      {error ? (
        <span id={`${id}-msg`} className={styles.error} role="alert">
          {error}
        </span>
      ) : hint ? (
        <span id={`${id}-msg`} className={styles.hint}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

type TextFieldProps = FieldShellProps &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> & {
    prefix?: string;
  };

export function TextField({ label, error, hint, className, prefix, ...input }: TextFieldProps) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <div className={cx(styles.control, error && styles.invalid)}>
        {prefix && <span className={styles.prefix}>{prefix}</span>}
        <input id={id} className={styles.input} aria-invalid={!!error} aria-describedby={`${id}-msg`} {...input} />
      </div>
    </FieldShell>
  );
}

type SelectFieldProps = FieldShellProps &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> & {
    options: ReadonlyArray<{ value: string; label: string }>;
  };

export function SelectField({ label, error, hint, className, options, ...select }: SelectFieldProps) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <div className={cx(styles.control, error && styles.invalid)}>
        <select id={id} className={styles.input} aria-describedby={`${id}-msg`} {...select}>
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
    </FieldShell>
  );
}
