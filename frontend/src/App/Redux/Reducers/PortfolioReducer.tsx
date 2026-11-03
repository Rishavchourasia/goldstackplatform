import { Page } from '../../Models/GeneralResponse';
import { HistoryPoint, Portfolio } from '../../Models/Portfolio';
import { RequestState, initialRequestState } from '../../Models/RequestState';
import { Order } from '../../Models/Trade';
import { AppAction } from '../Actions/AppAction';
import {
  PORTFOLIO_HISTORY_FAILURE,
  PORTFOLIO_HISTORY_REQUEST,
  PORTFOLIO_HISTORY_SUCCESS,
  PORTFOLIO_SUMMARY_FAILURE,
  PORTFOLIO_SUMMARY_REQUEST,
  PORTFOLIO_SUMMARY_SUCCESS,
  PORTFOLIO_TRANSACTIONS_FAILURE,
  PORTFOLIO_TRANSACTIONS_REQUEST,
  PORTFOLIO_TRANSACTIONS_SUCCESS,
} from '../Actions/PortfolioConstants';
import { AppError } from '../../Models/HttpModels';

export interface PortfolioState {
  summary: RequestState<Portfolio>;
  history: RequestState<HistoryPoint[]>;
  transactions: RequestState<Page<Order>>;
}

const initialState: PortfolioState = {
  summary: initialRequestState<Portfolio>(),
  history: initialRequestState<HistoryPoint[]>(),
  transactions: initialRequestState<Page<Order>>(),
};

const started = <T,>(s: RequestState<T>): RequestState<T> => ({ ...s, loading: true, error: false, err: null });
const succeeded = <T,>(data: T): RequestState<T> => ({ data, loading: false, error: false, err: null });
const failed = <T,>(s: RequestState<T>, err: unknown): RequestState<T> => ({
  ...s,
  loading: false,
  error: true,
  err: err as AppError,
});

export const portfolioReducer = (state = initialState, action: AppAction): PortfolioState => {
  switch (action.type) {
    case PORTFOLIO_SUMMARY_REQUEST:
      return { ...state, summary: started(state.summary) };
    case PORTFOLIO_SUMMARY_SUCCESS:
      return { ...state, summary: succeeded(action.payload as Portfolio) };
    case PORTFOLIO_SUMMARY_FAILURE:
      return { ...state, summary: failed(state.summary, action.err) };
    case PORTFOLIO_HISTORY_REQUEST:
      return { ...state, history: started(state.history) };
    case PORTFOLIO_HISTORY_SUCCESS:
      return { ...state, history: succeeded(action.payload as HistoryPoint[]) };
    case PORTFOLIO_HISTORY_FAILURE:
      return { ...state, history: failed(state.history, action.err) };
    case PORTFOLIO_TRANSACTIONS_REQUEST:
      return { ...state, transactions: started(state.transactions) };
    case PORTFOLIO_TRANSACTIONS_SUCCESS:
      return { ...state, transactions: succeeded(action.payload as Page<Order>) };
    case PORTFOLIO_TRANSACTIONS_FAILURE:
      return { ...state, transactions: failed(state.transactions, action.err) };
    default:
      return state;
  }
};
