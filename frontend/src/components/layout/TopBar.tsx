import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { normalizeSearchKeyword } from '../../features/search/searchApi';

/**
 * 전 화면 공통 상단바.
 *
 * 값은 `ZeroVerse Main Feed v2.dc.html`의 TOP BAR 인라인 style이 정본이다
 * (DESIGN-SYSTEM §6.1, PRD §6.6).
 *
 * - 높이 64px, `#fff8ec`, 하단 3px 잉크 보더, `padding:0 28px`, `gap:20px`, `z-index:10`.
 * - 좌: 14px 정사각 accent 마크(`4px 4px 0 shadow, -3px 3px 0 ink`) + `ZERO`(잉크)`VERSE`(accent),
 *   Press Start 2P 15px `letter-spacing:1px`.
 * - 중앙: 520px 검색바 — 3px 보더 + `shadow-btn`, 흰 입력 + 잉크 검색 버튼(`#ffd9a0`, `tracking:2px`).
 * - 우(gap 12px): `✎ 글쓰기`(accent) / `알림`(중립) / `user.nickname`(중립, 비로그인은 `로그인`).
 *
 * **관리자 전용 버튼은 없다**(PRD §9-K 폐기). `/admin` 진입은 프로필 메뉴 경유.
 *
 * 검색 submit은 `/search?keyword=...`로 이동하고, 빈 입력은 API를 호출하지 않는다(M7).
 */
export function TopBar({ nickname }: { nickname?: string | null }) {
  const isSignedIn = Boolean(nickname);
  const profileLabel = nickname || '로그인';
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setKeyword(searchParams.get('keyword') ?? '');
    setError(null);
  }, [searchParams.toString()]);

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      const normalized = normalizeSearchKeyword(keyword);
      if (!normalized) {
        setError('검색어를 입력해 주세요.');
        return;
      }
      const params = new URLSearchParams();
      params.set('keyword', normalized);
      setError(null);
      navigate(`/search?${params.toString()}`);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '검색어를 확인해 주세요.');
    }
  };

  return (
    <header
      style={{ height: 64, padding: '0 28px', gap: 20, zIndex: 10 }}
      className="relative flex items-center border-b-[3px] border-ink bg-surface-warm"
    >
      <Link to="/" aria-label="ZEROVERSE" className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          style={{
            width: 14,
            height: 14,
            background: 'var(--color-accent)',
            boxShadow: '4px 4px 0 var(--color-shadow), -3px 3px 0 var(--color-ink)',
          }}
        />
        <span className="font-pixel text-[15px] tracking-[1px] text-ink">
          ZERO<span className="text-accent">VERSE</span>
        </span>
      </Link>

      <div className="flex flex-1 justify-center">
        <form
          role="search"
          onSubmit={submitSearch}
          style={{ width: 520 }}
          className="flex border-[3px] border-ink bg-surface shadow-btn"
        >
          <input
            type="search"
            aria-label="검색"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setError(null);
            }}
            placeholder="유니버스 전체 검색 — 글 · 블로그 · 사용자 · 태그"
            className="min-w-0 flex-1 border-0 bg-transparent px-3 py-[9px] text-[13px] text-ink outline-0"
          />
          <button
            type="submit"
            style={{ padding: '0 18px' }}
            className="shrink-0 cursor-pointer border-0 bg-ink text-xs font-bold tracking-[2px] text-text-on-ink"
          >
            검색
          </button>
        </form>
        {error && (
          <span role="alert" className="absolute mt-[58px] text-xs text-danger">
            {error}
          </span>
        )}
      </div>

      <nav aria-label="사용자 메뉴" className="flex items-center gap-3">
        <Link
          to="/write"
          style={{ padding: '7px 16px' }}
          className="border-[3px] border-ink bg-accent text-[13px] font-bold text-white shadow-btn hover:brightness-108"
        >
          ✎ 글쓰기
        </Link>
        <Link
          to="/notifications"
          style={{ padding: '7px 14px' }}
          className="border-[3px] border-ink bg-surface text-[13px] font-semibold text-ink shadow-btn hover:bg-surface-raise"
        >
          알림
        </Link>
        <Link
          to={isSignedIn ? '/settings' : '/signin'}
          style={{ padding: '7px 14px' }}
          className="border-[3px] border-ink bg-surface text-[13px] font-semibold text-ink shadow-btn hover:bg-surface-raise"
        >
          {profileLabel}
        </Link>
      </nav>
    </header>
  );
}
