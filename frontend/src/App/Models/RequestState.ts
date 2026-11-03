import { AppError } from './HttpModels';

export interface RequestState<T> {
  data: T | null;
  loading: boolean;
  error: boolean;
  err: AppError | null;
}

export const initialRequestState = <T>(): RequestState<T> => ({
  data: null,
  loading: false,
  error: false,
  err: null,
});
