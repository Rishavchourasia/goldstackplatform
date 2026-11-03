import { all, call, delay, put, race, take, takeLatest } from 'redux-saga/effects';
import { Page } from '../../Models/GeneralResponse';
import { HistoryPoint, Portfolio } from '../../Models/Portfolio';
import { AppError } from '../../Models/HttpModels';
import { Order } from '../../Models/Trade';
import { PortfolioService } from '../../Services/PortfolioService';
import { callApi, callApiHandled } from '../../Utils/sagaHelpers';
import { AppAction } from '../Actions/AppAction';
import {
  LIVE_SUMMARY_START,
  LIVE_SUMMARY_STOP,
  PORTFOLIO_HISTORY_FAILURE,
  PORTFOLIO_HISTORY_REQUEST,
  PORTFOLIO_HISTORY_SUCCESS,
  PORTFOLIO_SUMMARY_FAILURE,
  PORTFOLIO_SUMMARY_REQUEST,
  PORTFOLIO_SUMMARY_SUCCESS,
  PORTFOLIO_TRANSACTIONS_FAILURE,
  PORTFOLIO_TRANSACTIONS_REQUEST,
  PORTFOLIO_TRANSACTIONS_SUCCESS,
} from '../Actions/PortfolioConstants';
import { TICK_RECEIVED } from '../Actions/PriceConstants';

const LIVE_REFRESH_MIN_GAP_MS = 4_000;

function* fetchSummarySaga() {
  try {
    const summary: Portfolio = yield* callApiHandled(PortfolioService.getSummary);
    yield put({ type: PORTFOLIO_SUMMARY_SUCCESS, payload: summary, error: false, err: null });
  } catch (error) {
    yield put({ type: PORTFOLIO_SUMMARY_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* fetchHistorySaga() {
  try {
    const history: HistoryPoint[] = yield* callApi(PortfolioService.getHistory);
    yield put({ type: PORTFOLIO_HISTORY_SUCCESS, payload: history, error: false, err: null });
  } catch (error) {
    yield put({ type: PORTFOLIO_HISTORY_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* fetchTransactionsSaga({ payload }: AppAction) {
  const { page, size } = payload as { page: number; size?: number };
  try {
    const result: Page<Order> = yield* callApi(PortfolioService.getTransactions, page, size ?? 10);
    yield put({ type: PORTFOLIO_TRANSACTIONS_SUCCESS, payload: result, error: false, err: null });
  } catch (error) {
    yield put({ type: PORTFOLIO_TRANSACTIONS_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* refreshOnEveryTickSaga() {
  yield put({ type: PORTFOLIO_SUMMARY_REQUEST });
  while (true) {
    yield take(TICK_RECEIVED);
    yield put({ type: PORTFOLIO_SUMMARY_REQUEST });
    yield delay(LIVE_REFRESH_MIN_GAP_MS);
  }
}

function* liveSummarySaga() {
  yield race({ refresh: call(refreshOnEveryTickSaga), stop: take(LIVE_SUMMARY_STOP) });
}

export default function* portfolioSaga() {
  yield all([
    takeLatest(PORTFOLIO_SUMMARY_REQUEST, fetchSummarySaga),
    takeLatest(PORTFOLIO_HISTORY_REQUEST, fetchHistorySaga),
    takeLatest(PORTFOLIO_TRANSACTIONS_REQUEST, fetchTransactionsSaga),
    takeLatest(LIVE_SUMMARY_START, liveSummarySaga),
  ]);
}
