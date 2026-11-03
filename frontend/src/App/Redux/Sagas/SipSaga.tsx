import { all, delay, put, select, takeEvery, takeLatest } from 'redux-saga/effects';
import { Frequency, Page } from '../../Models/GeneralResponse';
import { AppError } from '../../Models/HttpModels';
import { Sip, SipExecution, SipRunSummary } from '../../Models/Sip';
import { SipService } from '../../Services/SipService';
import { callApi } from '../../Utils/sagaHelpers';
import type { RootState } from '../Reducers/reducers';
import { AppAction } from '../Actions/AppAction';
import {
  SIPS_LIST_FAILURE,
  SIPS_LIST_REQUEST,
  SIPS_LIST_SUCCESS,
  SIP_ACTION_FINISHED,
  SIP_ACTION_REQUEST,
  SIP_CREATE_FAILURE,
  SIP_CREATE_REQUEST,
  SIP_CREATE_SUCCESS,
  SIP_DEV_RUN_FAILURE,
  SIP_DEV_RUN_REQUEST,
  SIP_DEV_RUN_SUCCESS,
  SIP_EXECUTIONS_FAILURE,
  SIP_EXECUTIONS_REQUEST,
  SIP_EXECUTIONS_SUCCESS,
} from '../Actions/SipConstants';
import { TOAST_SHOWN } from '../Actions/ToastConstants';

const PAST_TENSE = { pause: 'paused', resume: 'resumed', cancel: 'cancelled' } as const;

function* listSaga() {
  try {
    const sips: Sip[] = yield* callApi(SipService.getList);
    yield put({ type: SIPS_LIST_SUCCESS, payload: sips, error: false, err: null });
  } catch (error) {
    yield put({ type: SIPS_LIST_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* createSaga({ payload }: AppAction) {
  const { amount, frequency, startDate } = payload as { amount: string; frequency: Frequency; startDate: string };
  try {
    yield* callApi(SipService.create, amount, frequency, startDate);
    yield put({ type: SIP_CREATE_SUCCESS, payload: null, error: false, err: null });
    yield put({ type: TOAST_SHOWN, payload: { id: crypto.randomUUID(), tone: 'success', message: 'SIP created' } });
    yield put({ type: SIPS_LIST_REQUEST });
  } catch (error) {
    yield put({ type: SIP_CREATE_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* changeStatusSaga({ payload }: AppAction) {
  const { id, action } = payload as { id: string; action: 'pause' | 'resume' | 'cancel' };
  try {
    yield* callApi(SipService.changeStatus, id, action);
    yield put({ type: SIP_ACTION_FINISHED, payload: { id }, error: false, err: null });
    yield put({
      type: TOAST_SHOWN,
      payload: { id: crypto.randomUUID(), tone: 'info', message: `SIP ${PAST_TENSE[action]}` },
    });
    yield put({ type: SIPS_LIST_REQUEST });
  } catch (error) {
    yield put({
      type: SIP_ACTION_FINISHED,
      payload: { id, error: error as AppError },
      error: true,
      err: error as AppError,
    });
  }
}

function* executionsSaga({ payload }: AppAction) {
  const id = payload as string;
  try {
    const page: Page<SipExecution> = yield* callApi(SipService.getExecutions, id);
    yield put({ type: SIP_EXECUTIONS_SUCCESS, payload: { id, items: page.content }, error: false, err: null });
  } catch (error) {
    yield put({ type: SIP_EXECUTIONS_FAILURE, payload: { id }, error: true, err: error as AppError });
  }
}

function* devRunSaga() {
  try {
    const summary: SipRunSummary = yield* callApi(SipService.runDueNow);
    yield put({ type: SIP_DEV_RUN_SUCCESS, payload: summary, error: false, err: null });
    yield put({
      type: TOAST_SHOWN,
      payload: { id: crypto.randomUUID(), tone: 'info', message: `Requested ${summary.requested} installment(s)` },
    });
    yield delay(1500);
    yield put({ type: SIPS_LIST_REQUEST });
    const openIds: string[] = yield select((state: RootState) => Object.keys(state.sips.executions));
    yield all(openIds.map((id) => put({ type: SIP_EXECUTIONS_REQUEST, payload: id })));
  } catch (error) {
    yield put({ type: SIP_DEV_RUN_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

export default function* sipSaga() {
  yield all([
    takeLatest(SIPS_LIST_REQUEST, listSaga),
    takeLatest(SIP_CREATE_REQUEST, createSaga),
    takeEvery(SIP_ACTION_REQUEST, changeStatusSaga),
    takeEvery(SIP_EXECUTIONS_REQUEST, executionsSaga),
    takeLatest(SIP_DEV_RUN_REQUEST, devRunSaga),
  ]);
}
