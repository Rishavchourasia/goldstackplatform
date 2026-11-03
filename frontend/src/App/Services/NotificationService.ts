import { notificationsApiUrl } from '../Constants/urlConstants';
import { Page } from '../Models/GeneralResponse';
import { Notification } from '../Models/Notification';
import { httpGet, httpPost, withQuery } from './httpServices';

export class NotificationService {
  static async getList(page: number): Promise<Page<Notification>> {
    return await httpGet<Page<Notification>>(withQuery(notificationsApiUrl, { page, size: 20 }), true);
  }

  static async getUnreadCount(): Promise<{ unread: number }> {
    return await httpGet<{ unread: number }>(`${notificationsApiUrl}/unread-count`, true);
  }

  static async markRead(id: string): Promise<Notification> {
    return await httpPost<Notification>(`${notificationsApiUrl}/${id}/read`, true);
  }

  static async markAllRead(): Promise<{ unread: number }> {
    return await httpPost<{ unread: number }>(`${notificationsApiUrl}/read-all`, true);
  }
}
