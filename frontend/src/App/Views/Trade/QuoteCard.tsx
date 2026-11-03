import { TRADE_CONFIRM_REQUEST, TRADE_QUOTE_REQUEST, TRADE_RESET } from '../../Redux/Actions/TradeConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useDispatch, useSelector } from 'react-redux';
import { Button, Card, CardHeader, InlineAlert, Stack, Text } from '../../Components/CustomUI';
import { useCountdown } from '../../Components/CustomHooks/useCountdown';
import { cx } from '../../Utils/cx';
import { friendlyMessage, isOutcomeUnknown } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { TopUpForm } from '../../Components/TopUpForm/TopUpForm';
import styles from './Trade.module.scss';

const QUOTE_SECONDS = 300;

export function QuoteCard() {
  const dispatch = useDispatch();
  const { side, stage, quote, attemptKey, err: error } = useSelector((state: RootState) => state.trade);
  const secondsLeft = useCountdown(quote?.expiresAt);
  if (!quote) return null;

  const confirming = stage === 'confirming';
  const expired = secondsLeft <= 0 || error?.code === 'QUOTE_EXPIRED' || error?.code === 'QUOTE_ALREADY_USED';
  const unknownOutcome = error && isOutcomeUnknown(error);
  const requote = () =>
    dispatch({ type: TRADE_QUOTE_REQUEST, payload: { side, amount: side === 'BUY' ? quote.coins : quote.grams } });

  return (
    <Stack gap={4}>
      <Card tone="accent" aria-live="polite">
        <CardHeader title="Your quote" action={<Countdown seconds={secondsLeft} expired={expired} />} />
        <dl className={styles.summary}>
          <dt>{side === 'BUY' ? 'You pay' : 'You sell'}</dt>
          <dd>{side === 'BUY' ? fmt.coins(quote.coins) : fmt.grams(quote.grams)}</dd>
          <dt>{side === 'BUY' ? 'You get' : 'You receive'}</dt>
          <dd className={styles.highlight}>{side === 'BUY' ? fmt.grams(quote.grams) : fmt.coins(quote.coins)}</dd>
          <dt>Locked price</dt>
          <dd>{fmt.coins(quote.pricePerGram)}/g</dd>
        </dl>

        <Stack gap={3}>
          {expired ? (
            <InlineAlert
              tone="warning"
              title="This quote has expired"
              actions={
                <>
                  <Button variant="primary" size="sm" onClick={requote}>
                    Get fresh quote
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => dispatch({ type: TRADE_RESET })}>
                    Change amount
                  </Button>
                </>
              }
            >
              The price may have moved. Get a fresh quote to continue.
            </InlineAlert>
          ) : (
            <>
              {error && (
                <InlineAlert
                  tone={unknownOutcome ? 'warning' : 'danger'}
                  title={unknownOutcome ? "We're not sure your order went through" : undefined}
                >
                  {friendlyMessage(error)}
                  {unknownOutcome &&
                    ' Retrying is safe: it reuses the same idempotency key, so you can never be charged twice.'}
                </InlineAlert>
              )}
              <Stack direction="row" gap={2}>
                <Button
                  variant="primary"
                  size="lg"
                  className={styles.grow}
                  loading={confirming}
                  onClick={() => dispatch({ type: TRADE_CONFIRM_REQUEST })}
                >
                  {confirming
                    ? 'Processing…'
                    : unknownOutcome
                      ? 'Retry safely'
                      : side === 'BUY'
                        ? 'Confirm purchase'
                        : 'Confirm sale'}
                </Button>
                <Button variant="ghost" size="lg" disabled={confirming} onClick={() => dispatch({ type: TRADE_RESET })}>
                  Cancel
                </Button>
              </Stack>
            </>
          )}
          <Text variant="mono" tone="muted">
            Attempt key {attemptKey?.slice(0, 8)}… (sent as Idempotency-Key)
          </Text>
        </Stack>
      </Card>
      {error?.code === 'INSUFFICIENT_BALANCE' && !expired && (
        <Card>
          <TopUpForm initialAmount={quote.coins} />
        </Card>
      )}
    </Stack>
  );
}

function Countdown({ seconds, expired }: { seconds: number; expired: boolean }) {
  const r = 16;
  const circumference = 2 * Math.PI * r;
  const fraction = expired ? 0 : Math.min(1, seconds / QUOTE_SECONDS);
  const label = expired ? 'Expired' : `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  return (
    <span
      className={cx(styles.countdown, (expired || seconds <= 30) && styles.countdownLow)}
      aria-label={`Time left ${label}`}
    >
      <svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true">
        <circle cx="20" cy="20" r={r} className={styles.ringTrack} />
        <circle
          cx="20"
          cy="20"
          r={r}
          className={styles.ring}
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - fraction)}
        />
      </svg>
      <span className="tabular">{label}</span>
    </span>
  );
}
