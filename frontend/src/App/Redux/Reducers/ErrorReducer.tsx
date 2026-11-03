import { AppError } from '../../Models/HttpModels';
import { AppAction } from '../Actions/AppAction';
import { EXCEPTION_DISMISSED, EXCEPTION_RAISED } from '../Actions/ErrorConstants';

export interface ErrorState {
  exception: (AppError & { occurredAt: string }) | null;
}

const initialState: ErrorState = { exception: null };

export const errorReducer = (state = initialState, action: AppAction): ErrorState => {
  switch (action.type) {
    case EXCEPTION_RAISED:
      return { ...state, exception: action.payload as AppError & { occurredAt: string } };
    case EXCEPTION_DISMISSED:
      return { ...state, exception: null };
    default:
      return state;
  }
};
