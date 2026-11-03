import { all, put, takeLatest } from 'redux-saga/effects';
import { AppError } from '../../Models/HttpModels';
import { DailyPrice } from '../../Models/Price';
import { LumpSumResult, LumpSumResponse, SipWhatIfResult } from '../../Models/WhatIf';
import { PriceService } from '../../Services/PriceService';
import { WhatIfService } from '../../Services/WhatIfService';
import { todayIso } from '../../Utils/format';
import { callApi } from '../../Utils/sagaHelpers';
import { AppAction } from '../Actions/AppAction';
import {
  WHAT_IF_LUMP_FAILURE,
  WHAT_IF_LUMP_REQUEST,
  WHAT_IF_LUMP_SUCCESS,
  WHAT_IF_SIP_FAILURE,
  WHAT_IF_SIP_REQUEST,
  WHAT_IF_SIP_SUCCESS,
} from '../Actions/WhatIfConstants';
import { Frequency } from '../../Models/GeneralResponse';

function* lumpSumSaga({ payload }: AppAction) {
  const { amount, date } = payload as { amount: string; date: string };
  try {
    const [result, prices]: [LumpSumResult, DailyPrice[]] = yield all([
      callApi(WhatIfService.getLumpSum, amount, date),
      callApi(PriceService.getDailyPrices, date, todayIso()),
    ]);
    const response: LumpSumResponse = { result, prices };
    yield put({ type: WHAT_IF_LUMP_SUCCESS, payload: response, error: false, err: null });
  } catch (error) {
    yield put({ type: WHAT_IF_LUMP_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* sipSaga({ payload }: AppAction) {
  const { amount, frequency, startDate } = payload as { amount: string; frequency: Frequency; startDate: string };
  try {
    const result: SipWhatIfResult = yield* callApi(WhatIfService.getSip, amount, frequency, startDate);
    yield put({ type: WHAT_IF_SIP_SUCCESS, payload: result, error: false, err: null });
  } catch (error) {
    yield put({ type: WHAT_IF_SIP_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

export default function* whatIfSaga() {
  yield all([takeLatest(WHAT_IF_LUMP_REQUEST, lumpSumSaga), takeLatest(WHAT_IF_SIP_REQUEST, sipSaga)]);
}
