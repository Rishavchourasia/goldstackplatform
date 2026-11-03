import { EXCEPTION_DISMISSED } from '../../Redux/Actions/ErrorConstants';
import { LOGOUT } from '../../Redux/Actions/AuthConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { friendlyMessage } from '../../Utils/errors';
import { AppError } from '../../Models/HttpModels';
import * as fmt from '../../Utils/format';
import { Button, Icon, Modal, Text } from '../CustomUI';
import styles from './ExceptionModal.module.scss';

interface Presentation {
  title: string;
  icon: 'alert' | 'clock' | 'logout' | 'wallet';
  primary: 'retry' | 'login' | 'dismiss';
}

function present(e: AppError): Presentation {
  if (e.code === 'SESSION_EXPIRED') return { title: 'Your session expired', icon: 'logout', primary: 'login' };
  if (e.code === 'NETWORK') return { title: "You're offline", icon: 'alert', primary: 'retry' };
  if (e.code === 'RATE_LIMITED') return { title: 'Slow down a little', icon: 'clock', primary: 'dismiss' };
  if (e.status === 503 || e.code.endsWith('UNAVAILABLE')) {
    return { title: 'A service is taking a break', icon: 'clock', primary: 'retry' };
  }
  return { title: 'Something went wrong', icon: 'alert', primary: 'retry' };
}

export function ExceptionModal() {
  const dispatch = useDispatch();
  const exception = useSelector((state: RootState) => state.errors.exception);
  const dismiss = useCallback(() => dispatch({ type: EXCEPTION_DISMISSED }), [dispatch]);

  if (!exception) return null;
  const p = present(exception);

  const primary = {
    retry: (
      <Button variant="primary" onClick={() => window.location.reload()}>
        <Icon name="refresh" size={16} />
        Reload page
      </Button>
    ),
    login: (
      <Button
        variant="primary"
        onClick={() => {
          dispatch({ type: LOGOUT });
          dismiss();
        }}
      >
        Log in again
      </Button>
    ),
    dismiss: (
      <Button variant="primary" onClick={dismiss}>
        OK
      </Button>
    ),
  }[p.primary];

  return (
    <Modal
      open
      title={p.title}
      onClose={dismiss}
      icon={
        <span className={styles.icon}>
          <Icon name={p.icon} size={20} />
        </span>
      }
      footer={
        <>
          {p.primary !== 'dismiss' && (
            <Button variant="ghost" onClick={dismiss}>
              Dismiss
            </Button>
          )}
          {primary}
        </>
      }
    >
      <Text>{friendlyMessage(exception)}</Text>
      <div className={styles.reference}>
        <Text variant="caption" tone="muted">
          Reference
        </Text>
        <Text variant="mono" tone="secondary">
          {exception.code} · {fmt.time(exception.occurredAt)}
        </Text>
      </div>
    </Modal>
  );
}
