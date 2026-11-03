import { cx } from '../../../Utils/cx';
import styles from './Spinner.module.scss';

export function Spinner({ size = 16, className }: { size?: number; className?: string }) {
  return <span className={cx(styles.spinner, className)} style={{ width: size, height: size }} aria-hidden="true" />;
}
