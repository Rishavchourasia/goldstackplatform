import { RequestState } from '../Models/RequestState';

export const isFirstLoad = <T>(state: RequestState<T>): boolean => state.data === null && !state.err;
