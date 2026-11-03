import { Decimal } from '../../../Models/GeneralResponse';
import * as fmt from '../../../Utils/format';
import { cx } from '../../../Utils/cx';
import { Icon } from '../Icon/Icon';
import styles from './PnL.module.scss';

export function PnL({ amount, percent }: { amount: Decimal; percent?: Decimal }) {
  const tone = fmt.isZero(amount) ? 'flat' : fmt.isNegative(amount) ? 'down' : 'up';
  return (
    <span className={cx(styles.pnl, styles[tone], 'tabular')}>
      {tone !== 'flat' && <Icon name={tone === 'up' ? 'arrowUp' : 'arrowDown'} size={16} />}
      {fmt.signedCoins(amount)}
      {percent !== undefined && <span className={styles.percent}>({fmt.percent(percent)})</span>}
    </span>
  );
}
