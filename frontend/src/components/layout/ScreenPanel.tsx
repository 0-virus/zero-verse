import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { Panel } from '../ui/Panel';
import { getAllCategories, type Category } from '../../features/category/categoryApi';
import { useOptionalAuth } from '../../lib/authContext';
import { useHeroBlog } from '../../lib/heroBlogContext';

/**
 * 화면 전용 좌측 패널(240px).
 *
 * **앱 내비게이션이 아니다**(PRD §6.7). 값은 `ZeroVerse Pages.dc.html`이 정본이다.
 *
 * - `settings`: 단일 패널 + `SETTINGS` 헤더(Press Start 9px) + 메뉴 3항목.
 * - `blog`: `■ 카테고리` / `■ 블로그 통계` **두 패널**을 `gap:18px`로 쌓는다.
 *
 * 공개 블로그 패널은 현재 블로그의 카테고리 endpoint를 직접 읽는다. 글 목록·필터는 M4
 * 범위라 여기서는 실제 category count만 보여 준다.
 */
export type ScreenPanelKind = 'settings' | 'blog';

const SETTINGS_NAV = [
  { to: '/settings', label: '프로필 · 계정' },
  { to: '/settings/universe', label: '유니버스' },
  { to: '/settings/posts', label: '카테고리 관리' },
];

export function ScreenPanel({ kind }: { kind: ScreenPanelKind }) {
  if (kind === 'blog') {
    return <BlogScreenPanel />;
  }

  return (
    <aside
      aria-label="설정 메뉴"
      style={{ position: 'sticky', top: 20 }}
      className="w-60 shrink-0 self-start border-[3px] border-ink bg-surface shadow-card"
    >
      <h2 className="border-b-[3px] border-ink bg-surface-raise px-3.5 py-3 font-pixel text-[9px] tracking-[1px]">
        SETTINGS
      </h2>
      <ul>
        {SETTINGS_NAV.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              end
              className={({ isActive }) =>
                `block px-3.5 py-3 text-sm ${
                  isActive
                    ? 'border-l-[5px] border-l-accent bg-surface-raise font-bold text-ink'
                    : 'border-l-[5px] border-l-transparent font-medium text-text-nav hover:bg-surface-soft'
                }`
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </aside>
  );
}

function BlogScreenPanel() {
  const { blog } = useHeroBlog();
  const auth = useOptionalAuth();
  const viewerId = auth?.user?.id ?? null;
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!blog) {
      setCategories([]);
      setIsLoading(false);
      setError(false);
      return () => {
        cancelled = true;
      };
    }

    setCategories([]);
    setIsLoading(true);
    setError(false);
    void getAllCategories(blog.id, false)
      .then((loaded) => {
        if (!cancelled) setCategories(loaded);
      })
      .catch(() => {
        if (!cancelled) {
          setCategories([]);
          setError(true);
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [blog, viewerId]);

  const totalCount = categories.reduce(
    (sum, category) =>
      sum +
      category.postCount +
      category.children.reduce((childSum, child) => childSum + child.postCount, 0),
    0,
  );

  return (
    <aside
      aria-label="블로그 메뉴"
      style={{ position: 'sticky', top: 20, gap: 18 }}
      className="flex w-60 shrink-0 flex-col self-start"
    >
      <Panel title="카테고리">
        {isLoading && (
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            카테고리를 불러오는 중...
          </p>
        )}
        {!isLoading && error && (
          <p role="alert" className="px-3.5 py-8 text-center text-[13px] text-danger">
            카테고리를 불러오지 못했습니다.
          </p>
        )}
        {!isLoading && !blog && (
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            카테고리를 불러오면 여기에 표시됩니다.
          </p>
        )}
        {!isLoading && blog && !error && categories.length === 0 && (
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            아직 카테고리가 없습니다.
          </p>
        )}
        {!isLoading && !error && categories.length > 0 && (
          <ul className="py-1" aria-label="카테고리 목록">
            {categories.map((category) => (
              <li key={category.id}>
                <div className="flex items-center justify-between gap-2 px-3.5 py-2 text-[12px] font-bold text-text-body">
                  <span className="truncate">{category.name}</span>
                  <span className="shrink-0 text-[11px] font-normal text-text-muted">
                    {category.postCount}
                  </span>
                </div>
                {category.children.length > 0 && (
                  <ul aria-label={`${category.name} 하위 카테고리`}>
                    {category.children.map((child) => (
                      <li
                        key={child.id}
                        className="flex items-center justify-between gap-2 border-l-2 border-shadow px-3.5 py-1.5 pl-7 text-[11px] text-text-body"
                      >
                        <span className="truncate">{child.name}</span>
                        <span className="shrink-0 text-text-muted">{child.postCount}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>
      <Panel title="블로그 통계">
        {!blog ? (
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            통계를 불러오면 여기에 표시됩니다.
          </p>
        ) : (
          <dl className="divide-y-2 divide-shadow text-[12px]">
            <div className="flex items-center justify-between px-3.5 py-3">
              <dt className="text-text-muted">전체 글</dt>
              <dd className="font-bold text-ink">
                {isLoading ? '…' : error ? '—' : totalCount}
              </dd>
            </div>
            <div className="flex items-center justify-between px-3.5 py-3">
              <dt className="text-text-muted">이번 달</dt>
              <dd className="font-bold text-text-muted">—</dd>
            </div>
            <div className="flex items-center justify-between px-3.5 py-3">
              <dt className="text-text-muted">나를 발견한 별</dt>
              <dd className="font-bold text-text-muted">—</dd>
            </div>
          </dl>
        )}
      </Panel>
    </aside>
  );
}
