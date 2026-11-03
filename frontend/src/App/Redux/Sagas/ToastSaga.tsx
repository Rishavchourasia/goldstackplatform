import { delay, put, takeEvery } from 'redux-saga/effects';
import { TOAST_VISIBLE_MS } from '../../Constants/variableConstant';
import { AppAction } from '../Actions/AppAction';
import { TOAST_DISMISSED, TOAST_SHOWN } from '../Actions/ToastConstants';
import { Toast } from '../Reducers/ToastReducer';

function* dismissLaterSaga({ payload }: AppAction) {
  yield delay(TOAST_VISIBLE_MS);
  yield put({ type: TOAST_DISMISSED, payload: (payload as Toast).id });
}

export default function* toastSaga() {
  yield takeEvery(TOAST_SHOWN, dismissLaterSaga);
}
