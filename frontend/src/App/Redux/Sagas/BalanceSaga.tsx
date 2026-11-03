import { all, put, takeLatest } from 'redux-saga/effects';
import { GoldBalance, Wallet } from '../../Models/Wallet';
import { AppError } from '../../Models/HttpModels';
import { WalletService } from '../../Services/WalletService';
import * as fmt from '../../Utils/format';
import { callApi } from '../../Utils/sagaHelpers';
import { AppAction } from '../Actions/AppAction';
import { PORTFOLIO_SUMMARY_REQUEST } from '../Actions/PortfolioConstants';
import { TOAST_SHOWN } from '../Actions/ToastConstants';
import {
  BALANCES_FAILURE,
  BALANCES_REQUEST,
  COINS_SUCCESS,
  GOLD_SUCCESS,
  TOP_UP_FAILURE,
  TOP_UP_REQUEST,
  TOP_UP_SUCCESS,
} from '../Actions/BalanceConstants';

function* fetchBalancesSaga() {
  try {
    const [coins, gold]: [Wallet, GoldBalance] = yield all([
      callApi(WalletService.getWallet),
      callApi(WalletService.getGold),
    ]);
    yield put({ type: COINS_SUCCESS, payload: coins, error: false, err: null });
    yield put({ type: GOLD_SUCCESS, payload: gold, error: false, err: null });
  } catch (error) {
    yield put({ type: BALANCES_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* topUpSaga({ payload }: AppAction) {
  const amount = payload as string;
  try {
    const wallet: Wallet = yield* callApi(WalletService.topUp, amount);
    yield put({ type: TOP_UP_SUCCESS, payload: wallet, error: false, err: null });
    yield put({
      type: TOAST_SHOWN,
      payload: { id: crypto.randomUUID(), tone: 'success', message: `Added ${fmt.coins(amount)} to your wallet` },
    });
    yield put({ type: PORTFOLIO_SUMMARY_REQUEST });
  } catch (error) {
    yield put({ type: TOP_UP_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

export default function* balanceSaga() {
  yield all([takeLatest(BALANCES_REQUEST, fetchBalancesSaga), takeLatest(TOP_UP_REQUEST, topUpSaga)]);
}
