import { all, call, spawn } from 'redux-saga/effects';
import authSaga from './AuthSaga';
import balanceSaga from './BalanceSaga';
import notificationSaga from './NotificationSaga';
import portfolioSaga from './PortfolioSaga';
import priceSaga from './PriceSaga';
import sipSaga from './SipSaga';
import toastSaga from './ToastSaga';
import tradeSaga from './TradeSaga';
import whatIfSaga from './WhatIfSaga';

const sagas = [
  authSaga,
  priceSaga,
  balanceSaga,
  tradeSaga,
  portfolioSaga,
  whatIfSaga,
  sipSaga,
  notificationSaga,
  toastSaga,
];

export default function* rootSaga() {
  yield all(
    sagas.map((saga) =>
      spawn(function* keepAlive() {
        while (true) {
          try {
            yield call(saga);
            break;
          } catch (error) {
            console.error(`Saga ${saga.name} crashed and will restart`, error);
          }
        }
      }),
    ),
  );
}
