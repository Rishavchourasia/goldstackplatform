import { TRADE_QUOTE_REQUEST } from '../../Redux/Actions/TradeConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState, type FormEvent } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { FormError } from '../../Components/FormError/FormError';
import { Button, Card, Stack, Text, TextField } from '../../Components/CustomUI';
import * as fmt from '../../Utils/format';
import { coinError, gramError } from '../../Utils/validation';
import { TopUpForm } from '../../Components/TopUpForm/TopUpForm';
import styles from './Trade.module.scss';

const BUY_PRESETS = ['100', '500', '1000', '5000'];

export function TradeForm() {
  const dispatch = useDispatch();
  const { side, stage, err: error } = useSelector((state: RootState) => state.trade);
  const gold = useSelector((state: RootState) => state.balances.gold.data);
  const [amount, setAmount] = useState(side === 'BUY' ? '500' : '');

  const validationError = amount ? (side === 'BUY' ? coinError(amount) : gramError(amount)) : null;
  const canSubmit = amount !== '' && !validationError;

  function submit(e: FormEvent) {
    e.preventDefault();
    if (canSubmit) dispatch({ type: TRADE_QUOTE_REQUEST, payload: { side, amount: amount.trim() } });
  }

  return (
    <Stack gap={4}>
      <Card as="form" onSubmit={submit} noValidate>
        <Stack gap={4}>
          <TextField
            label={side === 'BUY' ? 'How much do you want to spend?' : 'How many grams do you want to sell?'}
            inputMode="decimal"
            autoFocus
            prefix={side === 'BUY' ? '₹' : 'g'}
            placeholder={side === 'BUY' ? '500' : '0.0100'}
            value={amount}
            error={validationError}
            onChange={(e) => setAmount(e.target.value)}
          />
          <div className={styles.presets}>
            {side === 'BUY'
              ? BUY_PRESETS.map((p) => (
                  <button
                    type="button"
                    key={p}
                    className={styles.preset}
                    aria-pressed={amount === p}
                    onClick={() => setAmount(p)}
                  >
                    ₹{Number(p).toLocaleString('en-IN')}
                  </button>
                ))
              : gold &&
                !fmt.isZero(gold.grams) && (
                  <button type="button" className={styles.preset} onClick={() => setAmount(gold.grams)}>
                    Sell all ({fmt.grams(gold.grams)})
                  </button>
                )}
          </div>
          <FormError error={error} />
          <Button
            type="submit"
            variant="primary"
            size="lg"
            fullWidth
            disabled={!canSubmit}
            loading={stage === 'quoting'}
          >
            Get quote
          </Button>
          <Text variant="caption" tone="muted" align="center">
            The quote locks the price for 5 minutes. Nothing is charged until you confirm.
          </Text>
        </Stack>
      </Card>
      {error?.code === 'INSUFFICIENT_BALANCE' && (
        <Card>
          <TopUpForm />
        </Card>
      )}
    </Stack>
  );
}
