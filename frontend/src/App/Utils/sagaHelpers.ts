import { call, put } from 'redux-saga/effects';
import { AppError } from '../Models/HttpModels';
import { SESSION_EXPIRED } from '../Redux/Actions/AuthConstants';
import { EXCEPTION_RAISED } from '../Redux/Actions/ErrorConstants';
import { hasAuthToken } from '../Services/httpServices';
import { isUnexpected, toAppError } from './errors';

type AsyncFn = (...args: never[]) => Promise<unknown>;

export function* callApi<F extends AsyncFn>(fn: F, ...args: Parameters<F>) {
  return yield* invoke(true, fn, ...args);
}

export function* callApiHandled<F extends AsyncFn>(fn: F, ...args: Parameters<F>) {
  return yield* invoke(false, fn, ...args);
}

function* invoke<F extends AsyncFn>(useModal: boolean, fn: F, ...args: Parameters<F>) {
  try {
    return (yield call(fn, ...args)) as Awaited<ReturnType<F>>;
  } catch (e) {
    const error: AppError = toAppError(e);
    if (error.status === 401 && hasAuthToken()) {
      yield put({ type: SESSION_EXPIRED });
      yield put({ type: EXCEPTION_RAISED, payload: sessionExpired() });
    } else if (useModal && isUnexpected(error)) {
      yield put({ type: EXCEPTION_RAISED, payload: { ...error, occurredAt: new Date().toISOString() } });
    }
    throw error;
  }
}

function sessionExpired() {
  return {
    status: 401,
    code: 'SESSION_EXPIRED',
    message: 'Your session has expired.',
    fieldErrors: [],
    occurredAt: new Date().toISOString(),
  };
}
