import { Decimal } from '../../../Models/GeneralResponse';
import * as fmt from '../../../Utils/format';
import { cx } from '../../../Utils/cx';
import { Icon } from '../Icon/Icon';
import { RollingNumber } from '../RollingNumber/RollingNumber';
import styles from './PnL.module.scss';

export function PnL({ amount, percent, animated }: { amount: Decimal; percent?: Decimal; animated?: boolean }) {
  const tone = fmt.isZero(amount) ? 'flat' : fmt.isNegative(amount) ? 'down' : 'up';
  return (
    <span className={cx(styles.pnl, styles[tone], 'tabular')}>
      {tone !== 'flat' && <Icon name={tone === 'up' ? 'arrowUp' : 'arrowDown'} size={16} />}
      {animated ? <RollingNumber value={fmt.signedCoins(amount)} /> : fmt.signedCoins(amount)}
      {percent !== undefined && <span className={styles.percent}>({fmt.percent(percent)})</span>}
    </span>
  );
}
