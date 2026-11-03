import { AppAction } from '../Actions/AppAction';
import { TOAST_DISMISSED, TOAST_SHOWN } from '../Actions/ToastConstants';

export interface Toast {
  id: string;
  tone: 'success' | 'info' | 'danger';
  message: string;
}

export const toastReducer = (state: Toast[] = [], action: AppAction): Toast[] => {
  switch (action.type) {
    case TOAST_SHOWN:
      return [...state, action.payload as Toast];
    case TOAST_DISMISSED:
      return state.filter((t) => t.id !== action.payload);
    default:
      return state;
  }
};
