import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { AppAction } from '../../Redux/Actions/AppAction';

export function useDispatchOnMount(actions: AppAction[], deps: unknown[] = []): void {
  const dispatch = useDispatch();
  useEffect(() => {
    actions.forEach((action) => dispatch(action));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dispatch, ...deps]);
}
