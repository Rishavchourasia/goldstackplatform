import { SipAction } from '../../Models/Sip';
import {
  SIPS_LIST_REQUEST,
  SIP_ACTION_REQUEST,
  SIP_CREATE_REQUEST,
  SIP_DEV_RUN_REQUEST,
  SIP_EXECUTIONS_REQUEST,
} from '../../Redux/Actions/SipConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState, type FormEvent } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { FormError } from '../../Components/FormError/FormError';
import { Frequency } from '../../Models/GeneralResponse';
import { Sip } from '../../Models/Sip';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Grid,
  Icon,
  InlineAlert,
  Modal,
  SelectField,
  Skeleton,
  Stack,
  Text,
  TextField,
  type BadgeTone,
} from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import { friendlyMessage } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { isFirstLoad } from '../../Utils/requestState';
import { FREQUENCY_OPTIONS } from '../../Constants/optionConstants';
import { coinError } from '../../Utils/validation';
import styles from './Sips.module.scss';

const EVERY: Record<Frequency, string> = { DAILY: 'day', WEEKLY: 'week', MONTHLY: 'month' };
const STATUS_TONE: Record<Sip['status'], BadgeTone> = { ACTIVE: 'success', PAUSED: 'warning', CANCELLED: 'neutral' };

export function SipsPage() {
  const list = useSelector((state: RootState) => state.sips.list);
  useDispatchOnMount([{ type: SIPS_LIST_REQUEST }]);

  return (
    <Stack gap={6}>
      <PageHeader
        title="SIPs"
        subtitle="Buy gold automatically every day, week or month at the live price. If your coins run out, that installment is skipped and you're notified."
      />
      <Grid min={340} gap={6}>
        <CreateSipForm />
        {import.meta.env.DEV && <DevRunner />}
      </Grid>
      <Card>
        <CardHeader title="My SIPs" />
        {isFirstLoad(list) ? (
          <Stack gap={3}>
            {[1, 2].map((i) => (
              <Skeleton key={i} height={56} />
            ))}
          </Stack>
        ) : list.err ? (
          <InlineAlert tone="danger">{friendlyMessage(list.err)}</InlineAlert>
        ) : list.data?.length ? (
          <div className={styles.list}>
            {list.data.map((s) => (
              <SipRow key={s.id} sip={s} />
            ))}
          </div>
        ) : (
          <EmptyState icon="repeat" title="No SIPs yet" description="Create one above to invest on autopilot." />
        )}
      </Card>
    </Stack>
  );
}

function CreateSipForm() {
  const dispatch = useDispatch();
  const { loading, err: error } = useSelector((state: RootState) => state.sips.create);
  const [amount, setAmount] = useState('500');
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY');
  const [startDate, setStartDate] = useState(fmt.todayIso());
  const invalid = coinError(amount, 10);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!invalid) dispatch({ type: SIP_CREATE_REQUEST, payload: { amount: amount.trim(), frequency, startDate } });
  }

  return (
    <Card as="form" onSubmit={submit} noValidate>
      <CardHeader title="New SIP" />
      <Stack gap={4}>
        <Stack direction="row" gap={3} wrap>
          <TextField
            label="Amount"
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
        </Stack>
        <TextField
          label="Starting"
          type="date"
          value={startDate}
          min={fmt.todayIso()}
          onChange={(e) => setStartDate(e.target.value)}
        />
        <FormError error={error} />
        <Button type="submit" variant="primary" loading={loading} disabled={!!invalid}>
          Start SIP
        </Button>
      </Stack>
    </Card>
  );
}

function SipRow({ sip }: { sip: Sip }) {
  const dispatch = useDispatch();
  const busy = useSelector((state: RootState) => !!state.sips.busy[sip.id]);
  const rowError = useSelector((state: RootState) => state.sips.rowErrors[sip.id]);
  const history = useSelector((state: RootState) => state.sips.executions[sip.id]);
  const [open, setOpen] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);

  const act = (action: SipAction) => dispatch({ type: SIP_ACTION_REQUEST, payload: { id: sip.id, action } });
  const toggleHistory = () => {
    if (!open) dispatch({ type: SIP_EXECUTIONS_REQUEST, payload: sip.id });
    setOpen(!open);
  };

  return (
    <div className={styles.row}>
      <div className={styles.rowMain}>
        <div>
          <Stack direction="row" gap={2} align="center">
            <Text variant="bodyStrong" tabular>
              {fmt.coins(sip.amount)}
            </Text>
            <Text tone="secondary">every {EVERY[sip.frequency]}</Text>
            <Badge tone={STATUS_TONE[sip.status]}>{sip.status.toLowerCase()}</Badge>
          </Stack>
          <Text variant="small" tone="muted">
            Started {fmt.date(sip.startDate)}
            {sip.status === 'ACTIVE' && (
              <>
                {' '}
                · next on <strong>{fmt.date(sip.nextRunDate)}</strong>
              </>
            )}
          </Text>
        </div>
        <Stack direction="row" gap={1} wrap>
          {sip.status === 'ACTIVE' && (
            <Button size="sm" loading={busy} onClick={() => act('pause')}>
              Pause
            </Button>
          )}
          {sip.status === 'PAUSED' && (
            <Button size="sm" loading={busy} onClick={() => act('resume')}>
              Resume
            </Button>
          )}
          {sip.status !== 'CANCELLED' && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirmCancel(true)}>
              Cancel
            </Button>
          )}
          <Button size="sm" variant="ghost" aria-expanded={open} onClick={toggleHistory}>
            {open ? 'Hide' : 'History'}
          </Button>
        </Stack>
      </div>
      {rowError && <InlineAlert tone="danger">{friendlyMessage(rowError)}</InlineAlert>}
      {open && (
        <ul className={styles.history}>
          {!history || (history.loading && !history.data) ? (
            <Skeleton height={18} width="60%" />
          ) : history.data?.length ? (
            history.data.map((e) => (
              <li key={e.id}>
                <Badge tone={e.status === 'SUCCESS' ? 'success' : e.status === 'FAILED' ? 'danger' : 'warning'}>
                  {e.status === 'SUCCESS' ? 'Bought' : e.status === 'FAILED' ? 'Failed' : 'Processing'}
                </Badge>
                <Text variant="small" tabular>
                  {fmt.date(e.runDate)}
                </Text>
                {e.failureReason && (
                  <Text variant="caption" tone="muted">
                    {e.failureReason}
                  </Text>
                )}
              </li>
            ))
          ) : (
            <Text variant="small" tone="muted">
              No installments have run yet.
            </Text>
          )}
        </ul>
      )}

      {}
      <Modal
        open={confirmCancel}
        title="Cancel this SIP?"
        onClose={() => setConfirmCancel(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmCancel(false)}>
              Keep it
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                act('cancel');
                setConfirmCancel(false);
              }}
            >
              Cancel SIP
            </Button>
          </>
        }
      >
        <Text>
          Future installments of {fmt.coins(sip.amount)} every {EVERY[sip.frequency]} will stop. Gold you already bought
          stays in your account.
        </Text>
      </Modal>
    </div>
  );
}

function DevRunner() {
  const dispatch = useDispatch();
  const run = useSelector((state: RootState) => state.sips.devRun);
  return (
    <Card className={styles.dev}>
      <CardHeader
        title={
          <>
            <Icon name="sparkle" size={16} /> Dev: run SIP job
          </>
        }
        subtitle="Runs every due SIP now. Results arrive via Kafka."
      />
      <Stack gap={3}>
        <Button loading={run.loading} onClick={() => dispatch({ type: SIP_DEV_RUN_REQUEST })}>
          Run due SIPs now
        </Button>
        {run.data && (
          <Text variant="small">
            Due {run.data.due} · requested {run.data.requested} · skipped {run.data.skipped}
          </Text>
        )}
        {run.err && <InlineAlert tone="danger">{friendlyMessage(run.err)}</InlineAlert>}
      </Stack>
    </Card>
  );
}
