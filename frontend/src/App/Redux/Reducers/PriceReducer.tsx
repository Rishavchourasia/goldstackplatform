import { ConnectionStatus, LivePrice } from '../../Models/Price';
import { AppAction } from '../Actions/AppAction';
import { CONNECTION_CHANGED, TICK_RECEIVED, TICKS_SEEDED } from '../Actions/PriceConstants';

const MAX_TICKS = 60;

export interface PriceState {
  ticks: LivePrice[];
  connection: ConnectionStatus;
}

const initialState: PriceState = { ticks: [], connection: 'connecting' };

export const priceReducer = (state = initialState, action: AppAction): PriceState => {
  switch (action.type) {
    case TICKS_SEEDED: {
      const seeded = action.payload as LivePrice[];
      return state.ticks.length < 2 ? { ...state, ticks: seeded.slice(-MAX_TICKS) } : state;
    }
    case TICK_RECEIVED: {
      const tick = action.payload as LivePrice;
      const last = state.ticks[state.ticks.length - 1];
      if (last?.recordedAt === tick.recordedAt) return state;
      return { ...state, ticks: [...state.ticks, tick].slice(-MAX_TICKS) };
    }
    case CONNECTION_CHANGED:
      return { ...state, connection: action.payload as ConnectionStatus };
    default:
      return state;
  }
};
