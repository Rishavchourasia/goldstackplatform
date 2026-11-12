import type { RootState } from '../../Redux/Reducers/reducers';
import { useSelector } from 'react-redux';
import { Grid, InlineAlert, PnL, RollingNumber, StatCard } from '../../Components/CustomUI';
import { friendlyMessage } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { isFirstLoad } from '../../Utils/requestState';

export function SummaryTiles({ showCoins = true }: { showCoins?: boolean }) {
  const summary = useSelector((state: RootState) => state.portfolio.summary);
  const loading = isFirstLoad(summary);
  const p = summary.data;

  if (summary.err && !p) return <InlineAlert tone="danger">{friendlyMessage(summary.err)}</InlineAlert>;
  return (
    <Grid min={190} className="stagger">
      {showCoins && (
        <StatCard
          label="Coin balance"
          loading={loading}
          value={p && <RollingNumber value={fmt.coins(p.coinBalance)} />}
        />
      )}
      <StatCard
        label="Gold held"
        loading={loading}
        value={p && <RollingNumber value={fmt.grams(p.totalGrams)} />}
        sub={p && `at ${fmt.coins(p.currentPricePerGram)}/g`}
      />
      <StatCard
        label="Current value"
        loading={loading}
        value={p && <RollingNumber value={fmt.coins(p.currentValue)} />}
        sub={p && `Invested ${fmt.coins(p.totalInvested)}`}
      />
      <StatCard
        label="Profit / loss"
        loading={loading}
        value={p && <PnL amount={p.profitLoss} animated />}
        sub={p && `${fmt.percent(p.returnPercent)} return`}
      />
    </Grid>
  );
}
