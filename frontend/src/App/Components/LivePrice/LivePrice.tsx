import type { RootState } from '../../Redux/Reducers/reducers';
import { useSelector } from 'react-redux';
import { Sparkline } from '../Charts/Charts';
import { Icon, Text } from '../CustomUI';
import { usePrevious } from '../CustomHooks/usePrevious';
import { cx } from '../../Utils/cx';
import * as fmt from '../../Utils/format';
import styles from './LivePrice.module.scss';

function useDirection(price: string | undefined): 'up' | 'down' | null {
  const previous = usePrevious(price);
  if (!price || !previous || price === previous) return null;
  return Number(price) > Number(previous) ? 'up' : 'down';
}

export function LivePriceChip() {
  const price = useSelector((state: RootState) => state.price.ticks[state.price.ticks.length - 1] ?? null);
  const connection = useSelector((state: RootState) => state.price.connection);
  const direction = useDirection(price?.pricePerGram);
  return (
    <span
      className={cx(styles.chip, connection === 'live' && styles.live)}
      title={connection === 'live' ? 'Live price' : 'Reconnecting…'}
    >
      <span className={styles.dot} aria-hidden="true" />
      {}
      <span key={price?.recordedAt} className={cx('tabular', direction && styles[`flash-${direction}`])}>
        {price ? `${fmt.coins(price.pricePerGram)}/g` : '—'}
      </span>
    </span>
  );
}

export function LivePriceHero() {
  const price = useSelector((state: RootState) => state.price.ticks[state.price.ticks.length - 1] ?? null);
  const ticks = useSelector((state: RootState) => state.price.ticks);
  const connection = useSelector((state: RootState) => state.price.connection);
  const direction = useDirection(price?.pricePerGram);
  return (
    <div className={styles.hero}>
      <div>
        <Text variant="overline" tone="muted">
          24K gold · live
        </Text>
        <div className={styles.heroPrice}>
          <Text
            key={price?.recordedAt}
            variant="display"
            as="span"
            className={cx(direction && styles[`flash-${direction}`])}
          >
            {price ? fmt.coins(price.pricePerGram) : '—'}
          </Text>
          <Text variant="body" tone="muted" as="span">
            /gram
          </Text>
          {direction && (
            <Icon
              name={direction === 'up' ? 'arrowUp' : 'arrowDown'}
              size={18}
              className={styles[`arrow-${direction}`]}
              label={`Price went ${direction}`}
            />
          )}
        </div>
        <Text variant="caption" tone="muted">
          {connection === 'live'
            ? `Live · updated ${price ? fmt.time(price.recordedAt) : '…'}`
            : 'Reconnecting to live price…'}
        </Text>
      </div>
      <div className={styles.spark}>
        <Sparkline ticks={ticks} height={72} />
      </div>
    </div>
  );
}
