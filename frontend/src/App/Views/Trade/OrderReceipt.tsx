import { TRADE_RESET } from '../../Redux/Actions/TradeConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useDispatch, useSelector } from 'react-redux';
import { Button, ButtonLink, Card, Icon, RollingNumber, Stack, Text } from '../../Components/CustomUI';
import { CoinRain } from '../../Components/CoinRain/CoinRain';
import * as fmt from '../../Utils/format';
import styles from './Trade.module.scss';

export function OrderReceipt() {
  const dispatch = useDispatch();
  const { order } = useSelector((state: RootState) => state.trade);
  if (!order) return null;
  return (
    <Card padding="lg" aria-live="polite" className={styles.receipt}>
      {order.side === 'BUY' && <CoinRain key={order.id} />}
      <Stack gap={4} align="center">
        <span className={styles.success}>
          <Icon name="check" size={28} />
        </span>
        <Text variant="h1">{order.side === 'BUY' ? 'Gold bought' : 'Gold sold'}</Text>
        <dl className={styles.summary}>
          <dt>Gold</dt>
          <dd>
            <RollingNumber value={fmt.grams(order.grams)} />
          </dd>
          <dt>{order.side === 'BUY' ? 'Paid' : 'Received'}</dt>
          <dd>{fmt.coins(order.coins)}</dd>
          <dt>Price</dt>
          <dd>{fmt.coins(order.pricePerGram)}/g</dd>
          <dt>Order</dt>
          <dd>
            <Text variant="mono">{order.id.slice(0, 8)}…</Text>
          </dd>
        </dl>
        <Stack direction="row" gap={2}>
          <Button variant="primary" onClick={() => dispatch({ type: TRADE_RESET })}>
            New trade
          </Button>
          <ButtonLink to="/portfolio">View portfolio</ButtonLink>
        </Stack>
      </Stack>
    </Card>
  );
}
