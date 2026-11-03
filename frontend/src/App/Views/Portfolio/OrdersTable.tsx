import { Order } from '../../Models/Trade';
import { Badge, DataTable, type Column } from '../../Components/CustomUI';
import * as fmt from '../../Utils/format';

const COLUMNS: ReadonlyArray<Column<Order>> = [
  { header: 'When', cell: (o) => fmt.dateTime(o.createdAt) },
  {
    header: 'Type',
    cell: (o) => (
      <span style={{ display: 'inline-flex', gap: 6 }}>
        <Badge tone={o.side === 'BUY' ? 'accent' : 'neutral'}>{o.side === 'BUY' ? 'Buy' : 'Sell'}</Badge>
        {o.source === 'SIP' && <Badge>SIP</Badge>}
      </span>
    ),
  },
  { header: 'Gold', cell: (o) => fmt.grams(o.grams), numeric: true },
  { header: 'Coins', cell: (o) => fmt.coins(o.coins), numeric: true },
  { header: 'Price/g', cell: (o) => fmt.coins(o.pricePerGram), numeric: true },
];

export function OrdersTable({ orders }: { orders: ReadonlyArray<Order> }) {
  return <DataTable caption="Transactions" columns={COLUMNS} rows={orders} rowKey={(o) => o.id} />;
}
