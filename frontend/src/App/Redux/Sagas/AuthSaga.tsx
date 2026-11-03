import { all, call, put, takeLatest } from 'redux-saga/effects';
import { AuthResponse } from '../../Models/User';
import { AppError } from '../../Models/HttpModels';
import { AuthService } from '../../Services/AuthService';
import { infoStore } from '../../Utils/InfoStore';
import { callApi } from '../../Utils/sagaHelpers';
import { AppAction } from '../Actions/AppAction';
import {
  AUTH_FAILURE,
  AUTH_SUCCESS,
  LOGIN_REQUEST,
  LOGOUT,
  SESSION_EXPIRED,
  SIGNUP_REQUEST,
} from '../Actions/AuthConstants';

function* loginSaga({ payload }: AppAction) {
  const { email, password } = payload as { email: string; password: string };
  try {
    const response: AuthResponse = yield* callApi(AuthService.login, email, password);
    yield call([infoStore, infoStore.saveSession], response);
    yield put({ type: AUTH_SUCCESS, payload: response, error: false, err: null });
  } catch (error) {
    yield put({ type: AUTH_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* signupSaga({ payload }: AppAction) {
  const { email, password, fullName } = payload as { email: string; password: string; fullName: string };
  try {
    const response: AuthResponse = yield* callApi(AuthService.signup, email, password, fullName);
    yield call([infoStore, infoStore.saveSession], response);
    yield put({ type: AUTH_SUCCESS, payload: response, error: false, err: null });
  } catch (error) {
    yield put({ type: AUTH_FAILURE, payload: null, error: true, err: error as AppError });
  }
}

function* forgetSessionSaga() {
  yield call([infoStore, infoStore.deleteSession]);
}

export default function* authSaga() {
  yield all([
    takeLatest(LOGIN_REQUEST, loginSaga),
    takeLatest(SIGNUP_REQUEST, signupSaga),
    takeLatest([LOGOUT, SESSION_EXPIRED], forgetSessionSaga),
  ]);
}
