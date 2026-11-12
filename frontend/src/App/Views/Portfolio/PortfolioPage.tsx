import {
  PORTFOLIO_HISTORY_REQUEST,
  PORTFOLIO_SUMMARY_REQUEST,
  PORTFOLIO_TRANSACTIONS_REQUEST,
} from '../../Redux/Actions/PortfolioConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState } from 'react';
import { useSelector } from 'react-redux';
import { InvestedVsValueChart } from '../../Components/Charts/Charts';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import {
  ButtonLink,
  Card,
  CardHeader,
  DataTable,
  EmptyState,
  InlineAlert,
  Pagination,
  Skeleton,
  Stack,
} from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import { friendlyMessage } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { isFirstLoad } from '../../Utils/requestState';
import { OrdersTable } from './OrdersTable';
import { SummaryTiles } from './SummaryTiles';

export function PortfolioPage() {
  const [page, setPage] = useState(0);
  const { history, transactions } = useSelector((state: RootState) => state.portfolio);

  useDispatchOnMount([{ type: PORTFOLIO_SUMMARY_REQUEST }, { type: PORTFOLIO_HISTORY_REQUEST }]);
  useDispatchOnMount([{ type: PORTFOLIO_TRANSACTIONS_REQUEST, payload: { page } }], [page]);

  return (
    <Stack gap={6} className="stagger">
      <PageHeader title="Portfolio" subtitle="What you hold, what you paid, and how it's doing." />
      <SummaryTiles showCoins={false} />

      <Card>
        <CardHeader title="Value over time" subtitle="Daily value of your gold vs. what you invested" />
        {isFirstLoad(history) ? (
          <Skeleton height={300} />
        ) : history.err ? (
          <InlineAlert tone="danger">{friendlyMessage(history.err)}</InlineAlert>
        ) : (history.data?.length ?? 0) < 2 ? (
          <EmptyState
            icon="chart"
            title={history.data?.length ? 'Your chart starts tomorrow' : 'No investments yet'}
            description={
              history.data?.length
                ? 'It draws a point for every full day you hold gold.'
                : 'Buy gold to start your chart.'
            }
            action={
              !history.data?.length && (
                <ButtonLink to="/trade" variant="primary" size="sm">
                  Buy gold
                </ButtonLink>
              )
            }
          />
        ) : (
          <>
            <InvestedVsValueChart points={history.data!} />
            <details style={{ marginTop: 'var(--space-3)' }}>
              <summary>Show as table</summary>
              <DataTable
                caption="Portfolio value by day"
                rows={[...history.data!].reverse()}
                rowKey={(h) => h.date}
                columns={[
                  { header: 'Date', cell: (h) => fmt.date(h.date) },
                  { header: 'Gold', cell: (h) => fmt.grams(h.grams), numeric: true },
                  { header: 'Invested', cell: (h) => fmt.coins(h.invested), numeric: true },
                  { header: 'Value', cell: (h) => fmt.coins(h.value), numeric: true },
                ]}
              />
            </details>
          </>
        )}
      </Card>

      <Card>
        <CardHeader title="Transactions" />
        {isFirstLoad(transactions) ? (
          <Stack gap={3}>
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} height={20} />
            ))}
          </Stack>
        ) : transactions.err ? (
          <InlineAlert tone="danger">{friendlyMessage(transactions.err)}</InlineAlert>
        ) : transactions.data?.content.length ? (
          <>
            <OrdersTable orders={transactions.data.content} />
            <Pagination page={transactions.data.page} totalPages={transactions.data.totalPages} onChange={setPage} />
          </>
        ) : (
          <EmptyState title="No transactions yet" />
        )}
      </Card>
    </Stack>
  );
}
