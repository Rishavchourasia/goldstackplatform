import { describe, expect, it } from 'vitest';
import { AppError } from '../../Models/HttpModels';
import { Order, Quote } from '../../Models/Trade';
import {
  TRADE_CONFIRM_FAILURE,
  TRADE_CONFIRM_SUCCESS,
  TRADE_QUOTE_REQUEST,
  TRADE_QUOTE_SUCCESS,
  TRADE_RESET,
  TRADE_SIDE_CHANGED,
} from '../Actions/TradeConstants';
import { tradeReducer } from './TradeReducer';

const quote = {
  quoteId: 'q1',
  side: 'BUY',
  pricePerGram: '12000.00',
  coins: '500.00',
  grams: '0.0416',
  expiresAt: '2099-01-01T00:00:00Z',
} as Quote;
const order = { id: 'o1', status: 'COMPLETED' } as Order;
const insufficient = { status: 422, code: 'INSUFFICIENT_BALANCE', message: 'x', fieldErrors: [] } as AppError;

describe('trade reducer (state machine)', () => {
  it('walks form -> quoting -> quoted -> confirming -> done', () => {
    let s = tradeReducer(undefined, { type: TRADE_QUOTE_REQUEST });
    expect(s.stage).toBe('quoting');
    s = tradeReducer(s, { type: TRADE_QUOTE_SUCCESS, payload: { quote, attemptKey: 'k1' } });
    expect(s).toMatchObject({ stage: 'quoted', attemptKey: 'k1' });
    s = tradeReducer(s, { type: TRADE_CONFIRM_SUCCESS, payload: order });
    expect(s).toMatchObject({ stage: 'done', order });
  });

  it('a definite failure installs the NEW attempt key; an unknown outcome keeps the old one', () => {
    const quoted = tradeReducer(undefined, { type: TRADE_QUOTE_SUCCESS, payload: { quote, attemptKey: 'k1' } });
    const withNew = tradeReducer(quoted, {
      type: TRADE_CONFIRM_FAILURE,
      payload: { nextAttemptKey: 'k2' },
      error: true,
      err: insufficient,
    });
    expect(withNew.attemptKey).toBe('k2');
    const kept = tradeReducer(quoted, {
      type: TRADE_CONFIRM_FAILURE,
      payload: { nextAttemptKey: null },
      error: true,
      err: insufficient,
    });
    expect(kept.attemptKey).toBe('k1');
  });

  it('switching side and resetting start over', () => {
    const quoted = tradeReducer(undefined, { type: TRADE_QUOTE_SUCCESS, payload: { quote, attemptKey: 'k1' } });
    expect(tradeReducer(quoted, { type: TRADE_SIDE_CHANGED, payload: 'SELL' })).toMatchObject({
      side: 'SELL',
      stage: 'form',
      quote: null,
    });
    expect(tradeReducer(quoted, { type: TRADE_RESET })).toMatchObject({ stage: 'form', quote: null });
  });
});
