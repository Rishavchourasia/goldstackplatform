import { UnknownAction } from 'redux';

export interface AppAction extends UnknownAction {
  type: string;
  payload?: unknown;
  error?: boolean;
  err?: unknown;
}
