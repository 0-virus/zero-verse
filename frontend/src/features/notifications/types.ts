import type { PageResponse } from '../category/categoryApi';

/** M8 승인 계약 §9.1의 알림 종류. */
export type NotificationType = 'COMMENT' | 'REPLY' | 'LIKE' | 'NEIGHBOR' | 'POST';
export type NotificationTargetType = 'POST' | 'COMMENT' | 'UNIVERSE';
export type NotificationReadFilter = 'all' | 'unread' | 'read';

export interface NotificationActor {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
}

export interface NotificationItem {
  id: number;
  type: NotificationType;
  message: string;
  actor: NotificationActor | null;
  targetType: NotificationTargetType | null;
  targetId: number | null;
  href: string | null;
  available: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage extends PageResponse<NotificationItem> {
  /** 현재 filter/page와 무관한 visible 미읽음 수. */
  unreadCount: number;
  /** ACTIVE만 관리 mutation을 수행할 수 있다. */
  canManage: boolean;
}

export interface NotificationListParams {
  read?: NotificationReadFilter;
  page?: number;
  size?: number;
}

