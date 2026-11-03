import { TOP_UP_REQUEST } from '../../Redux/Actions/BalanceConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState, type FormEvent } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { FormError } from '../FormError/FormError';
import { Button, Stack, TextField } from '../CustomUI';
import { coinError } from '../../Utils/validation';

export function TopUpForm({ initialAmount = '1000' }: { initialAmount?: string }) {
  const dispatch = useDispatch();
  const { loading, err: error } = useSelector((state: RootState) => state.balances.topUp);
  const [amount, setAmount] = useState(initialAmount);
  const invalid = amount ? coinError(amount) : null;

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!coinError(amount)) dispatch({ type: TOP_UP_REQUEST, payload: amount.trim() });
  }

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap={3}>
        <Stack direction="row" gap={2} align="flex-end">
          <TextField
            label="Top up coins"
            inputMode="decimal"
            prefix="₹"
            value={amount}
            error={invalid}
            hint="Virtual coins only, no real money"
            onChange={(e) => setAmount(e.target.value)}
          />
          <Button type="submit" loading={loading} disabled={!!coinError(amount)}>
            Add coins
          </Button>
        </Stack>
        <FormError error={error} />
      </Stack>
    </form>
  );
}
