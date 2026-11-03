import { WHAT_IF_LUMP_REQUEST, WHAT_IF_SIP_REQUEST } from '../../Redux/Actions/WhatIfConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState, type FormEvent } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Frequency } from '../../Models/GeneralResponse';
import { InvestedVsValueChart, PriceChart } from '../../Components/Charts/Charts';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import {
  Button,
  Card,
  CardHeader,
  DataTable,
  Grid,
  InlineAlert,
  PnL,
  SegmentedControl,
  SelectField,
  Skeleton,
  Stack,
  StatCard,
  Text,
  TextField,
} from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import { friendlyMessage } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { FREQUENCY_OPTIONS } from '../../Constants/optionConstants';
import { coinError } from '../../Utils/validation';

type Mode = 'lump' | 'sip';
const MODES = [
  { value: 'lump', label: 'One-time' },
  { value: 'sip', label: 'Every day / week / month' },
] as const;

export function WhatIfPage() {
  const [mode, setMode] = useState<Mode>('lump');
  return (
    <Stack gap={6}>
      <PageHeader
        title="What if?"
        subtitle="What would an investment in gold be worth today? Based on ~7 years of daily prices."
      />
      <SegmentedControl label="Calculator" options={MODES} value={mode} onChange={setMode} />
      {mode === 'lump' ? <LumpSum /> : <SipCalculator />}
    </Stack>
  );
}

function LumpSum() {
  const dispatch = useDispatch();
  const state = useSelector((state: RootState) => state.whatIf.lumpSum);
  const [amount, setAmount] = useState('10000');
  const [date, setDate] = useState(fmt.yearsAgoIso(5));
  const invalid = coinError(amount);

  useDispatchOnMount([{ type: WHAT_IF_LUMP_REQUEST, payload: { amount, date } }]);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!invalid) dispatch({ type: WHAT_IF_LUMP_REQUEST, payload: { amount, date } });
  }

  const r = state.data?.result;
  return (
    <Stack gap={5}>
      <Card as="form" onSubmit={submit} noValidate>
        <Stack direction="row" gap={3} align="flex-end" wrap>
          <TextField
            label="If I had invested"
            prefix="₹"
            inputMode="decimal"
            value={amount}
            error={amount ? invalid : null}
            onChange={(e) => setAmount(e.target.value)}
          />
          <TextField
            label="On"
            type="date"
            value={date}
            min={fmt.yearsAgoIso(7)}
            max={fmt.todayIso()}
            onChange={(e) => setDate(e.target.value)}
          />
          <Button type="submit" variant="primary" loading={state.loading} disabled={!!invalid}>
            Calculate
          </Button>
        </Stack>
      </Card>

      {state.err && <InlineAlert tone="danger">{friendlyMessage(state.err)}</InlineAlert>}
      {!r && state.loading && <Skeleton height={120} />}
      {r && (
        <>
          <Card tone="highlight">
            <Text variant="h2" as="p">
              {fmt.coins(r.amount)} on {fmt.date(r.investedOn)} would have bought {fmt.grams(r.grams)}, worth{' '}
              <Text as="span" variant="h2" tone="accent">
                {fmt.coins(r.valueNow)}
              </Text>{' '}
              today.
            </Text>
          </Card>
          <Grid min={180}>
            <StatCard label="Price then" value={fmt.coins(r.priceThen)} sub="per gram" />
            <StatCard label="Price now" value={fmt.coins(r.priceNow)} sub="per gram" />
            <StatCard label="Value today" value={fmt.coins(r.valueNow)} />
            <StatCard label="Gain" value={<PnL amount={r.profit} />} sub={`${fmt.percent(r.returnPercent)} return`} />
          </Grid>
          {state.data!.prices.length > 1 && (
            <Card>
              <CardHeader title={`Gold price since ${fmt.date(r.investedOn)}`} />
              <PriceChart points={state.data!.prices.map((p) => ({ date: p.date, price: p.closePrice }))} />
            </Card>
          )}
        </>
      )}
    </Stack>
  );
}

function SipCalculator() {
  const dispatch = useDispatch();
  const state = useSelector((state: RootState) => state.whatIf.sip);
  const [amount, setAmount] = useState('1000');
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY');
  const [startDate, setStartDate] = useState(fmt.yearsAgoIso(3));
  const invalid = coinError(amount);

  useDispatchOnMount([{ type: WHAT_IF_SIP_REQUEST, payload: { amount, frequency, startDate } }]);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!invalid) dispatch({ type: WHAT_IF_SIP_REQUEST, payload: { amount, frequency, startDate } });
  }

  const r = state.data;
  return (
    <Stack gap={5}>
      <Card as="form" onSubmit={submit} noValidate>
        <Stack direction="row" gap={3} align="flex-end" wrap>
          <TextField
            label="Invest"
            prefix="₹"
            inputMode="decimal"
            value={amount}
            error={amount ? invalid : null}
            onChange={(e) => setAmount(e.target.value)}
          />
          <SelectField
            label="Every"
            options={FREQUENCY_OPTIONS}
            value={frequency}
            onChange={(e) => setFrequency(e.target.value as Frequency)}
          />
          <TextField
            label="Starting"
            type="date"
            value={startDate}
            min={fmt.yearsAgoIso(7)}
            max={fmt.todayIso()}
            onChange={(e) => setStartDate(e.target.value)}
          />
          <Button type="submit" variant="primary" loading={state.loading} disabled={!!invalid}>
            Calculate
          </Button>
        </Stack>
      </Card>

      {state.err && <InlineAlert tone="danger">{friendlyMessage(state.err)}</InlineAlert>}
      {!r && state.loading && <Skeleton height={300} />}
      {r && (
        <>
          <Grid min={180}>
            <StatCard
              label="Installments"
              value={String(r.installments)}
              sub={`${fmt.coins(r.amountPerInstallment)} each`}
            />
            <StatCard label="Total invested" value={fmt.coins(r.totalInvested)} />
            <StatCard label="Value today" value={fmt.coins(r.valueNow)} sub={fmt.grams(r.totalGrams)} />
            <StatCard label="Gain" value={<PnL amount={r.profit} />} sub={`${fmt.percent(r.returnPercent)} return`} />
          </Grid>
          <Card>
            <CardHeader title="Invested vs value" />
            <InvestedVsValueChart points={r.series} />
            <details style={{ marginTop: 'var(--space-3)' }}>
              <summary>Show as table</summary>
              <DataTable
                caption="SIP what-if by installment"
                rows={[...r.series].reverse().slice(0, 200)}
                rowKey={(p) => p.date}
                columns={[
                  { header: 'Date', cell: (p) => fmt.date(p.date) },
                  { header: 'Invested', cell: (p) => fmt.coins(p.invested), numeric: true },
                  { header: 'Value', cell: (p) => fmt.coins(p.value), numeric: true },
                ]}
              />
            </details>
          </Card>
        </>
      )}
    </Stack>
  );
}
