import { RequestState, initialRequestState } from '../../Models/RequestState';
import { LumpSumResponse, SipWhatIfResult } from '../../Models/WhatIf';
import { AppAction } from '../Actions/AppAction';
import {
  WHAT_IF_LUMP_FAILURE,
  WHAT_IF_LUMP_REQUEST,
  WHAT_IF_LUMP_SUCCESS,
  WHAT_IF_SIP_FAILURE,
  WHAT_IF_SIP_REQUEST,
  WHAT_IF_SIP_SUCCESS,
} from '../Actions/WhatIfConstants';
import { AppError } from '../../Models/HttpModels';

export interface WhatIfState {
  lumpSum: RequestState<LumpSumResponse>;
  sip: RequestState<SipWhatIfResult>;
}

const initialState: WhatIfState = {
  lumpSum: initialRequestState<LumpSumResponse>(),
  sip: initialRequestState<SipWhatIfResult>(),
};

export const whatIfReducer = (state = initialState, action: AppAction): WhatIfState => {
  switch (action.type) {
    case WHAT_IF_LUMP_REQUEST:
      return { ...state, lumpSum: { ...state.lumpSum, loading: true, error: false, err: null } };
    case WHAT_IF_LUMP_SUCCESS:
      return {
        ...state,
        lumpSum: { data: action.payload as LumpSumResponse, loading: false, error: false, err: null },
      };
    case WHAT_IF_LUMP_FAILURE:
      return { ...state, lumpSum: { ...state.lumpSum, loading: false, error: true, err: action.err as AppError } };
    case WHAT_IF_SIP_REQUEST:
      return { ...state, sip: { ...state.sip, loading: true, error: false, err: null } };
    case WHAT_IF_SIP_SUCCESS:
      return { ...state, sip: { data: action.payload as SipWhatIfResult, loading: false, error: false, err: null } };
    case WHAT_IF_SIP_FAILURE:
      return { ...state, sip: { ...state.sip, loading: false, error: true, err: action.err as AppError } };
    default:
      return state;
  }
};
