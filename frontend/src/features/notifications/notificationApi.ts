import { apiClient } from '../../lib/apiClient';
import type {
  NotificationItem,
  NotificationListParams,
  NotificationPage,
  NotificationReadFilter,
} from './types';

const MAX_PAGE_SIZE = 100;

function requireData<T>(response: T | null, label: string): T {
  if (response === null) throw new Error(`${label} 응답이 비어 있습니다.`);
  return response;
}

function query(params: NotificationListParams): string {
  const read: NotificationReadFilter = params.read ?? 'all';
  const page = Math.max(0, params.page ?? 0);
  const size = Math.min(Math.max(1, params.size ?? 20), MAX_PAGE_SIZE);
  const search = new URLSearchParams();
  search.set('read', read);
  search.set('page', String(page));
  search.set('size', String(size));
  return search.toString();
}

export async function listNotifications(
  params: NotificationListParams = {},
): Promise<NotificationPage> {
  return requireData(
    await apiClient.get<NotificationPage>(`/api/v1/notifications?${query(params)}`),
    '알림 목록',
  );
}

export async function markNotificationRead(notificationId: number): Promise<NotificationItem> {
  return requireData(
    await apiClient.put<NotificationItem>(`/api/v1/notifications/${notificationId}/read`),
    '알림 읽음 처리',
  );
}

export async function markAllNotificationsRead(): Promise<null> {
  return apiClient.put<null>('/api/v1/notifications/read-all');
}

export async function deleteNotification(notificationId: number): Promise<null> {
  return apiClient.delete<null>(`/api/v1/notifications/${notificationId}`);
}

