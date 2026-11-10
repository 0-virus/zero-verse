import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from '../components/ui/Button';
import { ApiRequestError } from '../lib/apiClient';
import { useOptionalAuth } from '../lib/authContext';
import {
  acceptUniverseRequest,
  blockUniverseUser,
  cancelUniverseRequest,
  deleteUniverseRelationship,
  listUniverse,
  rejectUniverseRequest,
  unblockUniverseUser,
} from '../features/universe/universeApi';
import { UniverseListCard } from '../features/universe/UniverseListCard';
import type {
  UniverseListKind,
  UniverseRelationItem,
  UniverseRequestItem,
} from '../features/universe/types';
import { useOptionalNotificationActions } from '../features/notifications/NotificationProvider';

type UniverseItem = UniverseRequestItem | UniverseRelationItem;

type ListState = {
  items: UniverseItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  search: string;
  loading: boolean;
  error: string | null;
};

const KINDS: UniverseListKind[] = [
  'received',
  'sent',
  'friends',
  'following',
  'followers',
  'blocks',
];

const CARD_META: Record<UniverseListKind, { title: string; empty: string }> = {
  received: { title: '받은 유니버스 신청', empty: '받은 신청이 없습니다.' },
  sent: { title: '보낸 유니버스 신청', empty: '보낸 신청이 없습니다.' },
  friends: { title: '연결된 별', empty: '서로 연결된 별이 없습니다.' },
  following: { title: '내가 발견한', empty: '내가 발견한 별이 없습니다.' },
  followers: { title: '나를 발견한', empty: '나를 발견한 별이 없습니다.' },
  blocks: { title: '차단', empty: '차단한 별이 없습니다.' },
};

const INLINE_ACTION_CLASS = 'px-3 py-[5px] text-xs font-normal';

function emptyState(search = ''): ListState {
  return {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
    search,
    loading: false,
    error: null,
  };
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'UNI_005' || error.status === 409) {
      return '관계 상태가 바뀌었습니다. 목록을 새로 고쳐 주세요.';
    }
    if (error.status === 401) return '로그인이 만료되었습니다. 다시 접속해 주세요.';
    if (error.status === 403) return '이 유니버스 목록을 볼 권한이 없습니다.';
    return error.message || '유니버스 목록을 불러오지 못했습니다.';
  }
  return '유니버스 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function userIdOf(item: UniverseItem): number {
  return item.user.id;
}

function requestIdOf(item: UniverseItem): number | null {
  return 'requestId' in item ? item.requestId : null;
}

function actionButton(
  label: string,
  variant: 'neutral' | 'inert' | 'danger' | 'accentSm' = 'neutral',
  onClick?: () => void,
  disabled = false,
  className?: string,
) {
  return (
    <Button size="sm" variant={variant} className={className} disabled={disabled} onClick={onClick}>
      {label}
    </Button>
  );
}

/**
 * 유니버스 여섯 목록.
 *
 * 목록별 로딩/오류/검색/페이지 상태를 분리해 한 목록의 느린 응답이 다른 카드나 계정의
 * 화면을 덮어쓰지 않도록 한다. mutation 뒤에는 서버 canonical 목록을 다시 읽는다.
 */
export function SettingsUniversePage() {
  const auth = useOptionalAuth();
  const notifications = useOptionalNotificationActions();
  const userId = auth?.user?.id ?? null;
  const [states, setStates] = useState<Record<UniverseListKind, ListState>>(() =>
    Object.fromEntries(KINDS.map((kind) => [kind, emptyState()])) as Record<UniverseListKind, ListState>,
  );
  const [mutationKey, setMutationKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const accountGeneration = useRef(0);
  const requestGeneration = useRef<Record<UniverseListKind, number>>(
    Object.fromEntries(KINDS.map((kind) => [kind, 0])) as Record<UniverseListKind, number>,
  );

  const loadOne = useCallback(
    async (kind: UniverseListKind, page: number, search: string, accountAtStart: number) => {
      const currentAccountGeneration = accountGeneration.current;
      const requestNumber = requestGeneration.current[kind] + 1;
      requestGeneration.current[kind] = requestNumber;
      setStates((previous) => ({
        ...previous,
        [kind]: { ...previous[kind], page, search, loading: true, error: null },
      }));

      try {
        const response = await listUniverse(kind, { page, size: 20, nickname: search });
        if (
          accountGeneration.current !== currentAccountGeneration ||
          requestGeneration.current[kind] !== requestNumber ||
          userId !== accountAtStart
        ) {
          return;
        }
        setStates((previous) => ({
          ...previous,
          [kind]: {
            ...previous[kind],
            items: response.items,
            page: response.page,
            size: response.size,
            totalElements: response.totalElements,
            totalPages: response.totalPages,
            hasNext: response.hasNext,
            hasPrevious: response.hasPrevious,
            search,
            loading: false,
            error: null,
          },
        }));
      } catch (error) {
        if (
          accountGeneration.current !== currentAccountGeneration ||
          requestGeneration.current[kind] !== requestNumber ||
          userId !== accountAtStart
        ) {
          return;
        }
        setStates((previous) => ({
          ...previous,
          [kind]: { ...previous[kind], loading: false, error: errorMessage(error) },
        }));
      }
    },
    [userId],
  );

  const reloadAll = useCallback(async () => {
    if (userId === null) return;
    await Promise.all(
      KINDS.map((kind) => {
        const current = states[kind];
        return loadOne(kind, current.page, current.search, userId);
      }),
    );
  }, [loadOne, states, userId]);

  useEffect(() => {
    accountGeneration.current += 1;
    if (userId === null) return;
    setStates(Object.fromEntries(KINDS.map((kind) => [kind, emptyState()])) as Record<UniverseListKind, ListState>);
    void Promise.all(KINDS.map((kind) => loadOne(kind, 0, '', userId)));

    return () => {
      accountGeneration.current += 1;
    };
  }, [loadOne, userId]);

  const runMutation = useCallback(
    async (key: string, action: () => Promise<unknown>) => {
      if (mutationKey) return;
      setMutationKey(key);
      setNotice(null);
      try {
        await action();
        if (key.startsWith('block:') || key.startsWith('unblock:')) {
          await notifications?.invalidate();
        }
        await reloadAll();
        setNotice('관계 상태를 반영했습니다.');
      } catch (error) {
        if (error instanceof ApiRequestError && (error.code === 'UNI_005' || error.status === 409)) {
          await reloadAll();
          setNotice('관계 상태가 이미 바뀌었습니다. 최신 목록을 불러왔습니다.');
        } else {
          setNotice(errorMessage(error));
        }
      } finally {
        setMutationKey(null);
      }
    },
    [mutationKey, notifications, reloadAll],
  );

  const listActions = (kind: UniverseListKind, item: UniverseItem): ReactNode => {
    const targetId = userIdOf(item);
    const requestId = requestIdOf(item);
    const busy = mutationKey?.endsWith(`:${targetId}`) || mutationKey?.endsWith(`:${requestId}`);
    if (kind === 'received' && requestId !== null) {
      return (
        <>
          {actionButton('수락 ✦', 'accentSm', () => void runMutation(`accept:${requestId}`, () => acceptUniverseRequest(requestId)), Boolean(busy))}
          {actionButton('거절', 'inert', () => void runMutation(`reject:${requestId}`, () => rejectUniverseRequest(requestId)), Boolean(busy), 'px-3.5 py-1.5 text-xs font-semibold')}
        </>
      );
    }
    if (kind === 'sent' && requestId !== null) {
      return actionButton('취소', 'inert', () => void runMutation(`cancel:${requestId}`, () => cancelUniverseRequest(requestId)), Boolean(busy), INLINE_ACTION_CLASS);
    }
    if (kind === 'blocks') {
      return actionButton('해제', 'danger', () => void runMutation(`unblock:${targetId}`, () => unblockUniverseUser(targetId)), Boolean(busy));
    }
    if (kind === 'followers') {
      return actionButton('차단', 'inert', () => void runMutation(`block:${targetId}`, () => blockUniverseUser(targetId)), Boolean(busy), INLINE_ACTION_CLASS);
    }
    return (
      <>
        {actionButton('연결 끊기', 'inert', () => void runMutation(`delete:${targetId}`, () => deleteUniverseRelationship(targetId)), Boolean(busy), `${INLINE_ACTION_CLASS} hover:border-danger hover:text-danger`)}
        {actionButton('차단', 'inert', () => void runMutation(`block:${targetId}`, () => blockUniverseUser(targetId)), Boolean(busy), INLINE_ACTION_CLASS)}
      </>
    );
  };

  if (userId === null) {
    return (
      <section aria-label="유니버스 관리">
        <h1 className="sr-only">유니버스 관리</h1>
        <p className="text-[13px] text-text-body">유니버스를 관리하려면 로그인이 필요합니다.</p>
      </section>
    );
  }

  return (
    <section aria-label="유니버스 관리">
      <h1 className="sr-only">유니버스 관리</h1>
      {notice && (
        <p role="status" className="mb-5 border-2 border-info bg-info-bg px-3 py-2 text-xs text-info">
          {notice}
        </p>
      )}
      <div className="flex flex-col gap-5">
        {KINDS.map((kind) => {
          const state = states[kind];
          return (
            <UniverseListCard
              key={kind}
              title={CARD_META[kind].title}
              kind={kind}
              items={state.items}
              page={state.page}
              totalPages={state.totalPages}
              hasPrevious={state.hasPrevious}
              hasNext={state.hasNext}
              search={state.search}
              onSearch={(search) => void loadOne(kind, 0, search, userId)}
              onPageChange={(page) => void loadOne(kind, page, state.search, userId)}
              loading={state.loading}
              error={state.error}
              count={state.totalElements}
              actions={(item) => listActions(kind, item)}
              emptyMessage={CARD_META[kind].empty}
            />
          );
        })}
      </div>
    </section>
  );
}
