import { Page } from '../../Models/GeneralResponse';
import { AppError } from '../../Models/HttpModels';
import { Notification } from '../../Models/Notification';
import { RequestState, initialRequestState } from '../../Models/RequestState';
import { AppAction } from '../Actions/AppAction';
import {
  NOTIFICATIONS_LIST_FAILURE,
  NOTIFICATIONS_LIST_REQUEST,
  NOTIFICATIONS_LIST_SUCCESS,
  NOTIFICATIONS_UNREAD_SUCCESS,
  NOTIFICATION_MARK_ALL_READ_REQUEST,
  NOTIFICATION_MARK_READ_REQUEST,
} from '../Actions/NotificationConstants';

export interface NotificationState {
  list: RequestState<Page<Notification>>;
  unread: number;
}

const initialState: NotificationState = { list: initialRequestState<Page<Notification>>(), unread: 0 };

export const notificationReducer = (state = initialState, action: AppAction): NotificationState => {
  switch (action.type) {
    case NOTIFICATIONS_LIST_REQUEST:
      return { ...state, list: { ...state.list, loading: true, error: false, err: null } };
    case NOTIFICATIONS_LIST_SUCCESS:
      return {
        ...state,
        list: { data: action.payload as Page<Notification>, loading: false, error: false, err: null },
      };
    case NOTIFICATIONS_LIST_FAILURE:
      return { ...state, list: { ...state.list, loading: false, error: true, err: action.err as AppError } };
    case NOTIFICATIONS_UNREAD_SUCCESS:
      return { ...state, unread: action.payload as number };

    case NOTIFICATION_MARK_READ_REQUEST: {
      const id = action.payload as string;
      const target = state.list.data?.content.find((n) => n.id === id);
      if (!state.list.data || !target || target.read) return state;
      const content = state.list.data.content.map((n) => (n.id === id ? { ...n, read: true } : n));
      return {
        ...state,
        list: { ...state.list, data: { ...state.list.data, content } },
        unread: Math.max(0, state.unread - 1),
      };
    }
    case NOTIFICATION_MARK_ALL_READ_REQUEST: {
      if (!state.list.data) return { ...state, unread: 0 };
      const content = state.list.data.content.map((n) => ({ ...n, read: true }));
      return { ...state, list: { ...state.list, data: { ...state.list.data, content } }, unread: 0 };
    }
    default:
      return state;
  }
};
