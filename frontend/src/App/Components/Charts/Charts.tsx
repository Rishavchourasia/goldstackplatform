import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Decimal } from '../../Models/GeneralResponse';
import { LivePrice } from '../../Models/Price';
import * as fmt from '../../Utils/format';
import styles from './Charts.module.scss';

interface ComparisonPoint {
  date: string;
  invested: Decimal;
  value: Decimal;
}

export function InvestedVsValueChart({ points, height = 300 }: { points: ComparisonPoint[]; height?: number }) {
  const data = points.map((p) => ({
    ...p,
    investedN: fmt.toChartNumber(p.invested),
    valueN: fmt.toChartNumber(p.value),
  }));
  return (
    <div className={styles.chart} role="img" aria-label="Line chart of amount invested versus value over time">
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={shortDate}
            minTickGap={40}
            stroke="var(--chart-axis)"
            tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
            tickLine={false}
          />
          <YAxis
            tickFormatter={(n: number) => '₹' + fmt.compact(n)}
            width={56}
            stroke="var(--chart-axis)"
            tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            content={({ active, payload }) => <ComparisonTooltip active={active} payload={payload} />}
            cursor={{ stroke: 'var(--chart-axis)' }}
          />
          <Legend
            verticalAlign="top"
            align="left"
            height={28}
            iconType="plainline"
            formatter={(v: string) => <span className={styles.legend}>{v}</span>}
          />
          <Line
            type="monotone"
            dataKey="valueN"
            name="Value"
            stroke="var(--series-1)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--color-surface)' }}
            isAnimationActive={false}
          />
          <Line
            type="stepAfter"
            dataKey="investedN"
            name="Invested"
            stroke="var(--series-2)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--color-surface)' }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

interface HoverProps {
  active?: boolean;
  payload?: ReadonlyArray<{ payload?: unknown }>;
}

function ComparisonTooltip({ active, payload }: HoverProps) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload as ComparisonPoint;
  return (
    <div className={styles.tooltip}>
      <div className={styles.tooltipTitle}>{fmt.date(p.date)}</div>
      <div className={styles.tooltipRow}>
        <i style={{ background: 'var(--series-1)' }} />
        Value <b>{fmt.coins(p.value)}</b>
      </div>
      <div className={styles.tooltipRow}>
        <i style={{ background: 'var(--series-2)' }} />
        Invested <b>{fmt.coins(p.invested)}</b>
      </div>
    </div>
  );
}

export function PriceChart({ points, height = 260 }: { points: { date: string; price: Decimal }[]; height?: number }) {
  const data = points.map((p) => ({ ...p, priceN: fmt.toChartNumber(p.price) }));
  return (
    <div className={styles.chart} role="img" aria-label="Line chart of the gold price per gram over time">
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={shortDate}
            minTickGap={40}
            stroke="var(--chart-axis)"
            tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
            tickLine={false}
          />
          <YAxis
            domain={['auto', 'auto']}
            tickFormatter={(n: number) => '₹' + fmt.compact(n)}
            width={56}
            stroke="var(--chart-axis)"
            tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            cursor={{ stroke: 'var(--chart-axis)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const p = payload[0].payload as { date: string; price: Decimal };
              return (
                <div className={styles.tooltip}>
                  <div className={styles.tooltipTitle}>{fmt.date(p.date)}</div>
                  <div className={styles.tooltipRow}>
                    Price/g <b>{fmt.coins(p.price)}</b>
                  </div>
                </div>
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="priceN"
            stroke="var(--series-1)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--color-surface)' }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function Sparkline({ ticks, height = 56 }: { ticks: LivePrice[]; height?: number }) {
  if (ticks.length < 2) return <div style={{ height }} />;
  const data = ticks.map((t) => ({ t: t.recordedAt, p: fmt.toChartNumber(t.pricePerGram), raw: t.pricePerGram }));
  return (
    <div className={styles.chart} role="img" aria-label="Sparkline of the gold price over the last few minutes">
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={data} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
          <YAxis hide domain={['dataMin', 'dataMax']} />
          <Tooltip
            cursor={{ stroke: 'var(--chart-axis)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const p = payload[0].payload as { t: string; raw: Decimal };
              return (
                <div className={styles.tooltip}>
                  <div className={styles.tooltipRow}>
                    {fmt.time(p.t)} <b>{fmt.coins(p.raw)}</b>
                  </div>
                </div>
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="p"
            stroke="var(--series-1)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 3 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function shortDate(iso: string): string {
  return new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { month: 'short', year: '2-digit' });
}
