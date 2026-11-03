import { all, call, delay, put, race, select, take, takeEvery, takeLatest } from 'redux-saga/effects';
import { Page } from '../../Models/GeneralResponse';
import { AppError } from '../../Models/HttpModels';
import { Notification } from '../../Models/Notification';
import { NotificationService } from '../../Services/NotificationService';
import { callApi, callApiHandled } from '../../Utils/sagaHelpers';
import type { RootState } from '../Reducers/reducers';
import { AUTH_SUCCESS, LOGOUT, SESSION_EXPIRED } from '../Actions/AuthConstants';
import { AppAction } from '../Actions/AppAction';
import { TOP_UP_SUCCESS } from '../Actions/BalanceConstants';
import { SIP_DEV_RUN_SUCCESS } from '../Actions/SipConstants';
import { TRADE_CONFIRM_FAILURE, TRADE_CONFIRM_SUCCESS } from '../Actions/TradeConstants';
import {
  NOTIFICATIONS_LIST_FAILURE,
  NOTIFICATIONS_LIST_REQUEST,
  NOTIFICATIONS_LIST_SUCCESS,
  NOTIFICATIONS_UNREAD_REQUEST,
  NOTIFICATIONS_UNREAD_SUCCESS,
  NOTIFICATION_MARK_ALL_READ_REQUEST,
  NOTIFICATION_MARK_READ_REQUEST,
} from '../Actions/NotificationConstants';

const POLL_MS = 20_000;

function* listSaga({ payload }: AppAction) {
  try {
    const page: Page<Notification> = yield* callApi(NotificationService.getList, payload as number);
    yield put({ type: NOTIFICATIONS_LIST_SUCCESS, payload: page, error: false, err: null });
  } catch (error) {
    yield put({ type: NOTIFICATIONS_LIST_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* unreadSaga() {
  try {
    const res: { unread: number } = yield* callApiHandled(NotificationService.getUnreadCount);
    yield put({ type: NOTIFICATIONS_UNREAD_SUCCESS, payload: res.unread, error: false, err: null });
  } catch {
    return;
  }
}

function* markReadSaga({ payload }: AppAction) {
  try {
    yield* callApiHandled(NotificationService.markRead, payload as string);
  } catch {
    yield put({ type: NOTIFICATIONS_UNREAD_REQUEST });
  }
}

function* markAllReadSaga() {
  try {
    yield* callApiHandled(NotificationService.markAllRead);
  } catch {
    yield put({ type: NOTIFICATIONS_UNREAD_REQUEST });
  }
}

function* pollUnreadSaga() {
  while (true) {
    yield put({ type: NOTIFICATIONS_UNREAD_REQUEST });
    yield delay(POLL_MS);
  }
}

function* pollWhileLoggedInSaga() {
  yield race({ poll: call(pollUnreadSaga), stop: take([LOGOUT, SESSION_EXPIRED]) });
}

function* refreshSoonSaga() {
  yield delay(1500);
  yield put({ type: NOTIFICATIONS_UNREAD_REQUEST });
}

export default function* notificationSaga() {
  yield all([
    takeLatest(NOTIFICATIONS_LIST_REQUEST, listSaga),
    takeLatest(NOTIFICATIONS_UNREAD_REQUEST, unreadSaga),
    takeEvery(NOTIFICATION_MARK_READ_REQUEST, markReadSaga),
    takeLatest(NOTIFICATION_MARK_ALL_READ_REQUEST, markAllReadSaga),
    takeEvery([TRADE_CONFIRM_SUCCESS, TRADE_CONFIRM_FAILURE, TOP_UP_SUCCESS, SIP_DEV_RUN_SUCCESS], refreshSoonSaga),
    takeLatest(AUTH_SUCCESS, pollWhileLoggedInSaga),
  ]);

  const loggedIn: boolean = yield select((state: RootState) => state.auth.token !== null);
  if (loggedIn) yield call(pollWhileLoggedInSaga);
}
