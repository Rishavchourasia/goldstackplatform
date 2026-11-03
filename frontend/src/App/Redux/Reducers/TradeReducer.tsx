import { AppError } from '../../Models/HttpModels';
import { Order, Quote, Side } from '../../Models/Trade';
import { AppAction } from '../Actions/AppAction';
import {
  TRADE_CONFIRM_FAILURE,
  TRADE_CONFIRM_REQUEST,
  TRADE_CONFIRM_SUCCESS,
  TRADE_QUOTE_FAILURE,
  TRADE_QUOTE_REQUEST,
  TRADE_QUOTE_SUCCESS,
  TRADE_RESET,
  TRADE_SIDE_CHANGED,
} from '../Actions/TradeConstants';

export type TradeStage = 'form' | 'quoting' | 'quoted' | 'confirming' | 'done';

export interface TradeState {
  side: Side;
  stage: TradeStage;
  quote: Quote | null;

  attemptKey: string | null;
  order: Order | null;
  error: boolean;
  err: AppError | null;
}

const initialState: TradeState = {
  side: 'BUY',
  stage: 'form',
  quote: null,
  attemptKey: null,
  order: null,
  error: false,
  err: null,
};

export const tradeReducer = (state = initialState, action: AppAction): TradeState => {
  switch (action.type) {
    case TRADE_SIDE_CHANGED:
      return { ...initialState, side: action.payload as Side };
    case TRADE_QUOTE_REQUEST:
      return { ...state, stage: 'quoting', error: false, err: null };
    case TRADE_QUOTE_SUCCESS: {
      const { quote, attemptKey } = action.payload as { quote: Quote; attemptKey: string };
      return { ...state, stage: 'quoted', quote, attemptKey };
    }
    case TRADE_QUOTE_FAILURE:
      return { ...state, stage: 'form', error: true, err: action.err as AppError };
    case TRADE_CONFIRM_REQUEST:
      return { ...state, stage: 'confirming', error: false, err: null };
    case TRADE_CONFIRM_SUCCESS:
      return { ...state, stage: 'done', order: action.payload as Order };
    case TRADE_CONFIRM_FAILURE: {
      const { nextAttemptKey } = (action.payload ?? {}) as { nextAttemptKey: string | null };
      return {
        ...state,
        stage: 'quoted',
        error: true,
        err: action.err as AppError,
        attemptKey: nextAttemptKey ?? state.attemptKey,
      };
    }
    case TRADE_RESET:
      return { ...initialState, side: state.side };
    default:
      return state;
  }
};
