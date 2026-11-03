import { BALANCES_REQUEST } from '../../Redux/Actions/BalanceConstants';
import { TRADE_SIDE_CHANGED } from '../../Redux/Actions/TradeConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Side } from '../../Models/Trade';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import { Card, SegmentedControl, Skeleton, Stack, Text } from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import * as fmt from '../../Utils/format';
import { OrderReceipt } from './OrderReceipt';
import { QuoteCard } from './QuoteCard';
import { TradeForm } from './TradeForm';
import styles from './Trade.module.scss';

const SIDES = [
  { value: 'BUY', label: 'Buy' },
  { value: 'SELL', label: 'Sell' },
] as const;

export function TradePage() {
  const dispatch = useDispatch();
  const [params, setParams] = useSearchParams();
  const urlSide: Side = params.get('side') === 'SELL' ? 'SELL' : 'BUY';
  const { side, stage } = useSelector((state: RootState) => state.trade);

  useDispatchOnMount([{ type: BALANCES_REQUEST }]);

  useEffect(() => {
    dispatch({ type: TRADE_SIDE_CHANGED, payload: urlSide });
  }, [dispatch, urlSide]);

  return (
    <div className={styles.page}>
      <PageHeader
        title={side === 'BUY' ? 'Buy gold' : 'Sell gold'}
        subtitle="Lock a price for 5 minutes, then confirm."
      />
      <Stack gap={5}>
        <SegmentedControl
          label="Buy or sell"
          options={SIDES}
          value={urlSide}
          onChange={(s) => setParams({ side: s })}
        />
        <BalancesStrip />
        {}
        {stage === 'form' || stage === 'quoting' ? (
          <TradeForm key={side} />
        ) : stage === 'done' ? (
          <OrderReceipt />
        ) : (
          <QuoteCard />
        )}
      </Stack>
    </div>
  );
}

function BalancesStrip() {
  const { coins, gold } = useSelector((state: RootState) => state.balances);
  const price = useSelector((state: RootState) => state.price.ticks[state.price.ticks.length - 1] ?? null);
  const cell = (label: string, value: string | undefined) => (
    <div>
      <Text variant="overline" tone="muted">
        {label}
      </Text>
      {value ? (
        <Text variant="bodyStrong" tabular>
          {value}
        </Text>
      ) : (
        <Skeleton width={90} height={20} />
      )}
    </div>
  );
  return (
    <Card padding="sm" className={styles.balances}>
      {cell('Coins', coins.data ? fmt.coins(coins.data.balance) : undefined)}
      {cell('Gold', gold.data ? fmt.grams(gold.data.grams) : undefined)}
      {cell('Live price', price ? `${fmt.coins(price.pricePerGram)}/g` : undefined)}
    </Card>
  );
}
