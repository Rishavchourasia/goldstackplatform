import { EventChannel, eventChannel } from 'redux-saga';
import { all, call, delay, fork, put, take } from 'redux-saga/effects';
import { LivePrice } from '../../Models/Price';
import { PriceService } from '../../Services/PriceService';
import { callApi } from '../../Utils/sagaHelpers';
import { CONNECTION_CHANGED, TICK_RECEIVED, TICKS_SEEDED } from '../Actions/PriceConstants';

type SocketEvent = { kind: 'open' } | { kind: 'tick'; price: LivePrice } | { kind: 'closed' };

function liveChannel(): EventChannel<SocketEvent> {
  return eventChannel<SocketEvent>((emit) =>
    PriceService.connectLive({
      onOpen: () => emit({ kind: 'open' }),
      onTick: (price) => emit({ kind: 'tick', price }),
      onClose: () => emit({ kind: 'closed' }),
    }),
  );
}

function* liveSocketSaga() {
  let attempt = 0;
  while (true) {
    const channel: EventChannel<SocketEvent> = yield call(liveChannel);
    try {
      while (true) {
        const event: SocketEvent = yield take(channel);
        if (event.kind === 'open') {
          attempt = 0;
          yield put({ type: CONNECTION_CHANGED, payload: 'live' });
        } else if (event.kind === 'tick') {
          yield put({ type: TICK_RECEIVED, payload: event.price });
        } else {
          break;
        }
      }
    } finally {
      channel.close();
    }
    yield put({ type: CONNECTION_CHANGED, payload: 'reconnecting' });
    yield delay(Math.min(30_000, 1000 * 2 ** attempt++));
  }
}

function* seedRecentTicksSaga() {
  try {
    const recent: LivePrice[] = yield* callApi(PriceService.getRecentTicks, 60);
    yield put({ type: TICKS_SEEDED, payload: [...recent].reverse() });
  } catch {
    return;
  }
}

export default function* priceSaga() {
  yield all([fork(seedRecentTicksSaga), fork(liveSocketSaga)]);
}
