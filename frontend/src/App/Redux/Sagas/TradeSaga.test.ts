import { confirmOrderSaga } from './TradeSaga';
import { runSaga } from 'redux-saga';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../Models/HttpModels';
import { TradeService } from '../../Services/TradeService';
import { AppAction } from '../Actions/AppAction';
import { EXCEPTION_RAISED } from '../Actions/ErrorConstants';
import { TRADE_CONFIRM_FAILURE, TRADE_CONFIRM_SUCCESS } from '../Actions/TradeConstants';

vi.mock('../../Services/TradeService', () => ({
  TradeService: { executeOrder: vi.fn(), getOrder: vi.fn() },
}));

const state = {
  trade: {
    side: 'BUY',
    stage: 'confirming',
    quote: { quoteId: 'q1' },
    attemptKey: 'key-1',
    order: null,
    error: false,
    err: null,
  },
};

async function run(saga: () => Generator): Promise<AppAction[]> {
  const dispatched: AppAction[] = [];
  await runSaga({ dispatch: (a: AppAction) => dispatched.push(a), getState: () => state }, saga).toPromise();
  return dispatched;
}

describe('confirmOrderSaga', () => {
  beforeEach(() => vi.clearAllMocks());

  it('polls a PENDING order until the server-side saga completes it', async () => {
    vi.mocked(TradeService.executeOrder).mockResolvedValue({ id: 'o1', status: 'PENDING' } as never);
    vi.mocked(TradeService.getOrder).mockResolvedValue({
      id: 'o1',
      status: 'COMPLETED',
      side: 'BUY',
      grams: '0.0416',
      coins: '500.00',
    } as never);

    const actions = await run(confirmOrderSaga);

    expect(TradeService.executeOrder).toHaveBeenCalledWith('BUY', 'q1', 'key-1');
    expect(TradeService.getOrder).toHaveBeenCalledWith('o1');
    expect(actions.map((a) => a.type)).toContain(TRADE_CONFIRM_SUCCESS);
  });

  it('a definite failure (not enough coins) rotates the idempotency key', async () => {
    vi.mocked(TradeService.executeOrder).mockRejectedValue(new ApiError(422, 'INSUFFICIENT_BALANCE', 'no coins'));

    const actions = await run(confirmOrderSaga);
    const failed = actions.find((a) => a.type === TRADE_CONFIRM_FAILURE)!;

    expect((failed.payload as { nextAttemptKey: string }).nextAttemptKey).toMatch(/[0-9a-f-]{36}/);
  });

  it("a network error KEEPS the key, so 'Retry safely' can never buy twice", async () => {
    vi.mocked(TradeService.executeOrder).mockRejectedValue(new ApiError(0, 'NETWORK', 'offline'));

    const actions = await run(confirmOrderSaga);
    const failed = actions.find((a) => a.type === TRADE_CONFIRM_FAILURE)!;

    expect((failed.payload as { nextAttemptKey: string | null }).nextAttemptKey).toBeNull();
    expect(actions.some((a) => a.type === EXCEPTION_RAISED)).toBe(false);
  });
});
