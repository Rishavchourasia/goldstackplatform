import {
  NOTIFICATIONS_LIST_REQUEST,
  NOTIFICATION_MARK_ALL_READ_REQUEST,
  NOTIFICATION_MARK_READ_REQUEST,
} from '../../Redux/Actions/NotificationConstants';
import type { RootState } from '../../Redux/Reducers/reducers';
import { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { PageHeader } from '../../Components/AppShell/PageHeader';
import {
  Button,
  Card,
  EmptyState,
  Icon,
  InlineAlert,
  Pagination,
  Skeleton,
  Stack,
  Text,
  type IconName,
} from '../../Components/CustomUI';
import { useDispatchOnMount } from '../../Components/CustomHooks/useDispatchOnMount';
import { cx } from '../../Utils/cx';
import { friendlyMessage } from '../../Utils/errors';
import * as fmt from '../../Utils/format';
import { isFirstLoad } from '../../Utils/requestState';
import styles from './Notifications.module.scss';

const ICON: Record<string, IconName> = {
  WELCOME: 'sparkle',
  ORDER_COMPLETED: 'check',
  ORDER_FAILED: 'alert',
  SIP_SUCCESS: 'repeat',
  SIP_FAILED: 'alert',
  TOPUP: 'wallet',
};

export function NotificationsPage() {
  const dispatch = useDispatch();
  const [page, setPage] = useState(0);
  const list = useSelector((state: RootState) => state.notifications.list);
  const unread = useSelector((state: RootState) => state.notifications.unread);

  useDispatchOnMount([{ type: NOTIFICATIONS_LIST_REQUEST, payload: page }], [page]);

  return (
    <div className={styles.page}>
      <PageHeader
        title="Notifications"
        actions={
          unread > 0 && (
            <Button size="sm" variant="ghost" onClick={() => dispatch({ type: NOTIFICATION_MARK_ALL_READ_REQUEST })}>
              Mark all read
            </Button>
          )
        }
      />
      <Card>
        {isFirstLoad(list) ? (
          <Stack gap={4}>
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} height={40} />
            ))}
          </Stack>
        ) : list.err ? (
          <InlineAlert tone="danger">{friendlyMessage(list.err)}</InlineAlert>
        ) : !list.data?.content.length ? (
          <EmptyState icon="bell" title="You're all caught up" />
        ) : (
          <>
            <ul className={styles.list}>
              {list.data.content.map((n) => (
                <li key={n.id} className={cx(styles.item, !n.read && styles.unread)}>
                  <span className={cx(styles.icon, n.type.endsWith('FAILED') && styles.iconDanger)}>
                    <Icon name={ICON[n.type] ?? 'bell'} size={16} />
                  </span>
                  <div>
                    <Text variant={n.read ? 'body' : 'bodyStrong'}>{n.message}</Text>
                    <Text variant="caption" tone="muted">
                      {fmt.dateTime(n.createdAt)}
                    </Text>
                  </div>
                  {!n.read && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => dispatch({ type: NOTIFICATION_MARK_READ_REQUEST, payload: n.id })}
                    >
                      Mark read
                    </Button>
                  )}
                </li>
              ))}
            </ul>
            <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
