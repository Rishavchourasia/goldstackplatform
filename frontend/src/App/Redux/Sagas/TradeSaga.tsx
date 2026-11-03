import { all, delay, put, select, takeLatest } from 'redux-saga/effects';
import { AppError } from '../../Models/HttpModels';
import { Order, Quote } from '../../Models/Trade';
import { TradeService } from '../../Services/TradeService';
import { isOutcomeUnknown } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { callApi, callApiHandled } from '../../Utils/sagaHelpers';
import type { RootState } from '../Reducers/reducers';
import type { TradeState } from '../Reducers/TradeReducer';
import { AppAction } from '../Actions/AppAction';
import { BALANCES_REQUEST } from '../Actions/BalanceConstants';
import { TOAST_SHOWN } from '../Actions/ToastConstants';
import {
  TRADE_CONFIRM_FAILURE,
  TRADE_CONFIRM_REQUEST,
  TRADE_CONFIRM_SUCCESS,
  TRADE_QUOTE_FAILURE,
  TRADE_QUOTE_REQUEST,
  TRADE_QUOTE_SUCCESS,
} from '../Actions/TradeConstants';

export const POLL_INTERVAL_MS = 700;
export const POLL_TIMEOUT_MS = 30_000;

function* requestQuoteSaga({ payload }: AppAction) {
  const { side, amount } = payload as { side: 'BUY' | 'SELL'; amount: string };
  try {
    const quote: Quote = yield* callApi(TradeService.createQuote, side, amount);
    yield put({
      type: TRADE_QUOTE_SUCCESS,
      payload: { quote, attemptKey: crypto.randomUUID() },
      error: false,
      err: null,
    });
  } catch (error) {
    yield put({ type: TRADE_QUOTE_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function orderError(status: number, code: string, message: string): AppError {
  return { status, code, message, fieldErrors: [] };
}

export function* confirmOrderSaga() {
  const trade: TradeState = yield select((state: RootState) => state.trade);
  const { side, quote, attemptKey } = trade;
  if (!quote || !attemptKey) return;
  try {
    let order: Order = yield* callApiHandled(TradeService.executeOrder, side, quote.quoteId, attemptKey);
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    while (order.status === 'PENDING' && Date.now() < deadline) {
      yield delay(POLL_INTERVAL_MS);
      order = yield* callApiHandled(TradeService.getOrder, order.id);
    }
    if (order.status === 'FAILED') {
      throw orderError(422, order.failureCode ?? 'ORDER_FAILED', order.failureReason ?? 'The order failed');
    }
    if (order.status === 'PENDING') {
      throw orderError(202, 'ORDER_STILL_PROCESSING', 'Still processing');
    }
    yield put({ type: TRADE_CONFIRM_SUCCESS, payload: order, error: false, err: null });
    const verb = order.side === 'BUY' ? 'Bought' : 'Sold';
    yield put({
      type: TOAST_SHOWN,
      payload: {
        id: crypto.randomUUID(),
        tone: 'success',
        message: `${verb} ${fmt.grams(order.grams)} for ${fmt.coins(order.coins)}`,
      },
    });
    yield put({ type: BALANCES_REQUEST });
  } catch (error) {
    const err = error as AppError;
    const nextAttemptKey = isOutcomeUnknown(err) ? null : crypto.randomUUID();
    yield put({ type: TRADE_CONFIRM_FAILURE, payload: { nextAttemptKey }, error: true, err });
    if (err.code === 'GOLD_CREDIT_FAILED') {
      yield delay(1500);
      yield put({ type: BALANCES_REQUEST });
    }
  }
}

export default function* tradeSaga() {
  yield all([takeLatest(TRADE_QUOTE_REQUEST, requestQuoteSaga), takeLatest(TRADE_CONFIRM_REQUEST, confirmOrderSaga)]);
}
