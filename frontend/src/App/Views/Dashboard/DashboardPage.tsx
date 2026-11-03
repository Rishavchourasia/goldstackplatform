import {
  LIVE_SUMMARY_START,
  LIVE_SUMMARY_STOP,
  PORTFOLIO_TRANSACTIONS_REQUEST,
} from '../../Redux/Actions/PortfolioConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import { ButtonLink, Card, CardHeader, EmptyState, Grid, Icon, Skeleton, Stack, Text } from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import { isFirstLoad } from '../../Utils/requestState';
import { TopUpForm } from '../../Components/TopUpForm/TopUpForm';
import { OrdersTable } from '../Portfolio/OrdersTable';
import { SummaryTiles } from '../Portfolio/SummaryTiles';
import { LivePriceHero } from '../../Components/LivePrice/LivePrice';

export function DashboardPage() {
  const dispatch = useDispatch();
  const firstName = useSelector((state: RootState) => state.auth.user?.fullName.split(' ')[0]);
  const recent = useSelector((state: RootState) => state.portfolio.transactions);

  useDispatchOnMount([{ type: PORTFOLIO_TRANSACTIONS_REQUEST, payload: { page: 0, size: 5 } }]);

  useEffect(() => {
    dispatch({ type: LIVE_SUMMARY_START });
    return () => {
      dispatch({ type: LIVE_SUMMARY_STOP });
    };
  }, [dispatch]);

  return (
    <Stack gap={6}>
      <PageHeader title={firstName ? `Hi ${firstName}` : 'Dashboard'} subtitle="Your gold, live." />

      <Card padding="lg">
        <Stack direction="row" gap={6} align="center" justify="space-between" wrap>
          <div style={{ flex: '1 1 420px', minWidth: 0 }}>
            <LivePriceHero />
          </div>
          <Stack direction="row" gap={2}>
            <ButtonLink to="/trade?side=BUY" variant="primary" size="lg">
              Buy gold
            </ButtonLink>
            <ButtonLink to="/trade?side=SELL" size="lg">
              Sell
            </ButtonLink>
          </Stack>
        </Stack>
      </Card>

      <SummaryTiles />

      <Grid min={340} gap={6}>
        <Card>
          <CardHeader title="Recent activity" action={<Link to="/portfolio">See all →</Link>} />
          {isFirstLoad(recent) ? (
            <Stack gap={3}>
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} height={20} />
              ))}
            </Stack>
          ) : recent.data?.content.length ? (
            <OrdersTable orders={recent.data.content} />
          ) : (
            <EmptyState
              icon="sparkle"
              title="No trades yet"
              description="Your first gram is one click away."
              action={
                <ButtonLink to="/trade" variant="primary" size="sm">
                  Buy gold
                </ButtonLink>
              }
            />
          )}
        </Card>
        <Card>
          <CardHeader title="Wallet" subtitle="Add virtual coins to invest" />
          <TopUpForm />
          <div style={{ marginTop: 'var(--space-5)' }}>
            <Text variant="small" tone="muted">
              <Icon name="repeat" size={14} /> Invest automatically with a <Link to="/sips">SIP</Link>, or see what past
              investments would be worth with the <Link to="/what-if">what-if calculator</Link>.
            </Text>
          </div>
        </Card>
      </Grid>
    </Stack>
  );
}
