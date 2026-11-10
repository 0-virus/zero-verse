import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../lib/apiClient';
import {
  deleteNotification,
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from './notificationApi';

const page = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
  unreadCount: 0,
  canManage: true,
};

describe('notificationApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('목록은 all/read/page/size를 승인된 순서와 최대 size로 보낸다', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue(page);

    await listNotifications({ read: 'unread', page: 2, size: 120 });

    expect(get).toHaveBeenCalledWith('/api/v1/notifications?read=unread&page=2&size=100');
  });

  it('read는 현재 권한으로 재평가한 NotificationItem을 반환한다', async () => {
    const item = {
      id: 7,
      type: 'LIKE',
      message: '별사람님이 좋아요를 보냈습니다.',
      actor: null,
      targetType: 'POST',
      targetId: 42,
      href: '/blog/star/42',
      available: true,
      readAt: '2026-11-10T00:00:00Z',
      createdAt: '2026-11-09T00:00:00Z',
    };
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue(item);

    await expect(markNotificationRead(7)).resolves.toEqual(item);
    expect(put).toHaveBeenCalledWith('/api/v1/notifications/7/read');
  });

  it('read-all과 delete는 empty success를 그대로 전달한다', async () => {
    const put = vi.spyOn(apiClient, 'put').mockResolvedValue(null);
    const del = vi.spyOn(apiClient, 'delete').mockResolvedValue(null);

    await markAllNotificationsRead();
    await deleteNotification(12);

    expect(put).toHaveBeenCalledWith('/api/v1/notifications/read-all');
    expect(del).toHaveBeenCalledWith('/api/v1/notifications/12');
  });
});
