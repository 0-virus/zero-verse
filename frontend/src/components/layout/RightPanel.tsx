import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Panel } from '../ui/Panel';
import type { DefaultBlog } from '../../types/auth';
import { useOptionalAuth } from '../../lib/authContext';
import { listUniverse } from '../../features/universe/universeApi';
import type { UniverseListKind } from '../../features/universe/types';

/** `/` 오른쪽 레일의 서버 관계 현황. 알림 row는 M8 범위라 기존 빈 상태를 유지한다. */
type StatKey = 'friends' | 'following' | 'followers';
type StatState = Record<StatKey, { value: number | null; loading: boolean; error: boolean }>;

const STAT_ITEMS: Array<{ key: StatKey; label: string; kind: UniverseListKind }> = [
  { key: 'friends', label: '친구', kind: 'friends' },
  { key: 'following', label: '내가 발견한', kind: 'following' },
  { key: 'followers', label: '나를 발견한', kind: 'followers' },
];

const IDLE_STATS: StatState = {
  friends: { value: null, loading: false, error: false },
  following: { value: null, loading: false, error: false },
  followers: { value: null, loading: false, error: false },
};

function loadingStats(): StatState {
  return {
    friends: { value: null, loading: true, error: false },
    following: { value: null, loading: true, error: false },
    followers: { value: null, loading: true, error: false },
  };
}

export function RightPanel({ blog }: { blog?: DefaultBlog | null }) {
  const auth = useOptionalAuth();
  const viewerId = auth?.user?.id ?? null;
  const authLoading = auth?.isLoading ?? false;
  const [stats, setStats] = useState<StatState>(IDLE_STATS);
  const [statsViewerId, setStatsViewerId] = useState<number | null>(null);
  const generation = useRef(0);

  useEffect(() => {
    const currentGeneration = ++generation.current;
    if (authLoading || viewerId == null) {
      setStats(IDLE_STATS);
      setStatsViewerId(null);
      return () => undefined;
    }

    setStatsViewerId(viewerId);
    setStats(loadingStats());
    STAT_ITEMS.forEach(({ key, kind }) => {
      void listUniverse(kind, { page: 0, size: 1 })
        .then((page) => {
          if (generation.current !== currentGeneration) return;
          setStats((previous) => ({
            ...previous,
            [key]: { value: page.totalElements, loading: false, error: false },
          }));
        })
        .catch(() => {
          if (generation.current !== currentGeneration) return;
          setStats((previous) => ({
            ...previous,
            [key]: { value: null, loading: false, error: true },
          }));
        });
    });

    return () => {
      generation.current += 1;
    };
  }, [authLoading, viewerId]);

  const visibleStats = statsViewerId === viewerId ? stats : loadingStats();

  return (
    <aside
      aria-label="사이드 패널"
      style={{ position: 'sticky', top: 20, gap: 18 }}
      className="flex w-[300px] shrink-0 flex-col self-start"
    >
      <Panel title="내 블로그">
        {blog ? (
          <div className="p-3.5">
            <p className="mb-[3px] text-[15px] font-bold text-ink">{blog.title}</p>
            <p className="mb-3 text-xs text-text-muted">/blog/{blog.urlSlug}</p>
            <div className="flex gap-2">
              <Link
                to="/settings"
                className="flex-1 border-2 border-ink bg-surface py-[7px] text-center text-xs font-semibold text-ink hover:bg-surface-raise"
              >
                블로그 관리
              </Link>
              <Link
                to="/write"
                className="flex-1 border-2 border-ink bg-accent py-[7px] text-center text-xs font-bold text-white hover:brightness-108"
              >
                새 글 작성
              </Link>
            </div>
          </div>
        ) : (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">
            블로그 정보를 불러오면 여기에 표시됩니다.
          </p>
        )}
      </Panel>

      <Panel title="최근 알림">
        <p className="px-5 py-8 text-center text-[13px] text-text-muted">아직 알림이 없습니다.</p>
      </Panel>

      <Panel title="유니버스 현황">
        {authLoading ? (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">인증 상태를 확인하는 중...</p>
        ) : viewerId == null ? (
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">
            로그인하면 유니버스 현황을 확인할 수 있습니다.
          </p>
        ) : (
          <>
            <div className="flex gap-2 p-3.5 text-center">
              {STAT_ITEMS.map(({ key, label }) => {
                const stat = visibleStats[key];
                return (
                  <div key={key} className="flex-1 border-2 border-line px-0 py-2.5">
                    <div
                      className={
                        key === 'friends'
                          ? 'text-[20px] font-bold text-accent'
                          : 'text-[20px] font-bold text-ink'
                      }
                    >
                      {stat.loading ? '…' : stat.error ? '오류' : stat.value ?? '—'}
                    </div>
                    <div className="text-[11px] text-text-muted">{label}</div>
                  </div>
                );
              })}
            </div>
            <p className="px-3.5 pb-3 text-[11px] leading-[1.5] text-text-muted">
              내가 발견한 수에는 친구가 포함됩니다.
            </p>
          </>
        )}
      </Panel>
    </aside>
  );
}
