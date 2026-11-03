import { AppError } from '../../Models/HttpModels';
import { GoldBalance, Wallet } from '../../Models/Wallet';
import { RequestState, initialRequestState } from '../../Models/RequestState';
import { AppAction } from '../Actions/AppAction';
import {
  BALANCES_FAILURE,
  BALANCES_REQUEST,
  COINS_SUCCESS,
  GOLD_SUCCESS,
  TOP_UP_FAILURE,
  TOP_UP_REQUEST,
  TOP_UP_SUCCESS,
} from '../Actions/BalanceConstants';

export interface BalanceState {
  coins: RequestState<Wallet>;
  gold: RequestState<GoldBalance>;
  topUp: RequestState<Wallet>;
}

const initialState: BalanceState = {
  coins: initialRequestState<Wallet>(),
  gold: initialRequestState<GoldBalance>(),
  topUp: initialRequestState<Wallet>(),
};

export const balanceReducer = (state = initialState, action: AppAction): BalanceState => {
  switch (action.type) {
    case BALANCES_REQUEST:
      return {
        ...state,
        coins: { ...state.coins, loading: true, error: false, err: null },
        gold: { ...state.gold, loading: true, error: false, err: null },
      };
    case COINS_SUCCESS:
      return { ...state, coins: { data: action.payload as Wallet, loading: false, error: false, err: null } };
    case GOLD_SUCCESS:
      return { ...state, gold: { data: action.payload as GoldBalance, loading: false, error: false, err: null } };
    case BALANCES_FAILURE:
      return {
        ...state,
        coins: { ...state.coins, loading: false, error: true, err: action.err as AppError },
        gold: { ...state.gold, loading: false, error: true, err: action.err as AppError },
      };
    case TOP_UP_REQUEST:
      return { ...state, topUp: { ...state.topUp, loading: true, error: false, err: null } };
    case TOP_UP_SUCCESS: {
      const wallet = action.payload as Wallet;
      return {
        ...state,
        topUp: { data: wallet, loading: false, error: false, err: null },
        coins: { data: wallet, loading: false, error: false, err: null },
      };
    }
    case TOP_UP_FAILURE:
      return { ...state, topUp: { ...state.topUp, loading: false, error: true, err: action.err as AppError } };
    default:
      return state;
  }
};
