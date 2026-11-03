import { AuthResponse, User } from '../../Models/User';
import { AppError } from '../../Models/HttpModels';
import { infoStore } from '../../Utils/InfoStore';
import {
  AUTH_FAILURE,
  AUTH_SUCCESS,
  LOGIN_REQUEST,
  LOGOUT,
  SESSION_EXPIRED,
  SIGNUP_REQUEST,
} from '../Actions/AuthConstants';
import { AppAction } from '../Actions/AppAction';

export interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  error: boolean;
  err: AppError | null;
}

const initialState: AuthState = {
  user: infoStore.getUser(),
  token: infoStore.getAuthToken(),
  loading: false,
  error: false,
  err: null,
};

export const authReducer = (state = initialState, action: AppAction): AuthState => {
  switch (action.type) {
    case LOGIN_REQUEST:
    case SIGNUP_REQUEST:
      return { ...state, loading: true, error: false, err: null };
    case AUTH_SUCCESS: {
      const res = action.payload as AuthResponse;
      return { ...state, loading: false, user: res.user, token: res.token };
    }
    case AUTH_FAILURE:
      return { ...state, loading: false, error: true, err: action.err as AppError };
    case LOGOUT:
    case SESSION_EXPIRED:
      return { ...state, user: null, token: null, loading: false };
    default:
      return state;
  }
};
