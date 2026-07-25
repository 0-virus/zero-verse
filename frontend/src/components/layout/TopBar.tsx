import { Link } from 'react-router-dom';

/**
 * 전 화면 공통 상단바(디자인 정본 §6.1, PRD §6.7).
 *
 * 관리자 버튼은 두지 않는다 — 폐기됨(PRD §9-K). `/admin` 진입은 프로필 메뉴 경유.
 */
export function TopBar() {
  return (
    <header className="flex h-16 items-center gap-6 border-b-[3px] border-ink bg-surface-warm px-6">
      <Link to="/" className="font-pixel text-[15px] tracking-[1px]">
        ZEROVERSE
      </Link>

      <form
        role="search"
        onSubmit={(e) => e.preventDefault()}
        className="flex w-[520px] border-[3px] border-ink shadow-btn"
      >
        <input
          type="search"
          aria-label="검색"
          placeholder="글, 블로그, 사용자, 태그 검색"
          className="w-full bg-surface px-3 py-2 text-[13px] outline-0"
        />
        <button type="submit" className="cursor-pointer bg-ink px-4 text-[13px] text-text-on-ink">
          검색
        </button>
      </form>

      <nav className="ml-auto flex items-center gap-3">
        <Link
          to="/write"
          className="border-[3px] border-ink bg-accent px-4 py-1.5 text-[13px] font-bold text-white shadow-btn"
        >
          글쓰기
        </Link>
        <Link to="/notifications" aria-label="알림" className="text-[13px] font-bold">
          알림
        </Link>
        <Link to="/settings" aria-label="프로필" className="text-[13px] font-bold">
          프로필
        </Link>
      </nav>
    </header>
  );
}
