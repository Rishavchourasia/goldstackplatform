import { AppError } from '../../Models/HttpModels';
import { RequestState, initialRequestState } from '../../Models/RequestState';
import { Sip, SipExecution, SipRunSummary } from '../../Models/Sip';
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

export interface SipState {
  list: RequestState<Sip[]>;
  create: RequestState<Sip>;

  busy: Record<string, boolean>;
  rowErrors: Record<string, AppError | undefined>;
  executions: Record<string, RequestState<SipExecution[]>>;
  devRun: RequestState<SipRunSummary>;
}

const initialState: SipState = {
  list: initialRequestState<Sip[]>(),
  create: initialRequestState<Sip>(),
  busy: {},
  rowErrors: {},
  executions: {},
  devRun: initialRequestState<SipRunSummary>(),
};

export const sipReducer = (state = initialState, action: AppAction): SipState => {
  switch (action.type) {
    case SIPS_LIST_REQUEST:
      return { ...state, list: { ...state.list, loading: true, error: false, err: null } };
    case SIPS_LIST_SUCCESS:
      return { ...state, list: { data: action.payload as Sip[], loading: false, error: false, err: null } };
    case SIPS_LIST_FAILURE:
      return { ...state, list: { ...state.list, loading: false, error: true, err: action.err as AppError } };
    case SIP_CREATE_REQUEST:
      return { ...state, create: { ...state.create, loading: true, error: false, err: null } };
    case SIP_CREATE_SUCCESS:
      return { ...state, create: { data: null, loading: false, error: false, err: null } };
    case SIP_CREATE_FAILURE:
      return { ...state, create: { ...state.create, loading: false, error: true, err: action.err as AppError } };
    case SIP_ACTION_REQUEST: {
      const { id } = action.payload as { id: string };
      const { [id]: _previous, ...rowErrors } = state.rowErrors;
      return { ...state, busy: { ...state.busy, [id]: true }, rowErrors };
    }
    case SIP_ACTION_FINISHED: {
      const { id, error } = action.payload as { id: string; error?: AppError };
      const { [id]: _done, ...busy } = state.busy;
      return { ...state, busy, rowErrors: error ? { ...state.rowErrors, [id]: error } : state.rowErrors };
    }
    case SIP_EXECUTIONS_REQUEST: {
      const id = action.payload as string;
      const current = state.executions[id] ?? initialRequestState<SipExecution[]>();
      return {
        ...state,
        executions: { ...state.executions, [id]: { ...current, loading: true, error: false, err: null } },
      };
    }
    case SIP_EXECUTIONS_SUCCESS: {
      const { id, items } = action.payload as { id: string; items: SipExecution[] };
      return {
        ...state,
        executions: { ...state.executions, [id]: { data: items, loading: false, error: false, err: null } },
      };
    }
    case SIP_EXECUTIONS_FAILURE: {
      const { id } = action.payload as { id: string };
      const current = state.executions[id] ?? initialRequestState<SipExecution[]>();
      return {
        ...state,
        executions: {
          ...state.executions,
          [id]: { ...current, loading: false, error: true, err: action.err as AppError },
        },
      };
    }
    case SIP_DEV_RUN_REQUEST:
      return { ...state, devRun: { ...state.devRun, loading: true, error: false, err: null } };
    case SIP_DEV_RUN_SUCCESS:
      return { ...state, devRun: { data: action.payload as SipRunSummary, loading: false, error: false, err: null } };
    case SIP_DEV_RUN_FAILURE:
      return { ...state, devRun: { ...state.devRun, loading: false, error: true, err: action.err as AppError } };
    default:
      return state;
  }
};
