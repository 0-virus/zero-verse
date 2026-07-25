import { NavLink } from 'react-router-dom';

/**
 * 앱 내비게이션 사이드바.
 *
 * **`/` 메인 피드에만 존재**하며 폭은 210px이다(PRD §6.7, §9-L).
 * `NAVIGATION` 헤더 + 5개 항목 + 하단 토큰 캡션이 디자인 정본 구성이다.
 *
 * - Admin 항목 없음(PRD §9-K 폐기).
 * - `/blog/:slug`·`/settings*`의 240px 좌측 패널은 앱 내비가 아니라 **화면 전용 패널**이므로
 *   이 컴포넌트가 아니라 각 화면에서 만든다.
 */
const ITEMS = [
  { to: '/', icon: '▲', label: 'Home' },
  { to: '/blog/me', icon: '■', label: 'My Blog' },
  { to: '/search', icon: '◎', label: 'Search' },
  { to: '/settings/universe', icon: '✦', label: 'Universe' },
  { to: '/settings', icon: '▤', label: 'Settings' },
];

export function SideNav() {
  return (
    <nav
      aria-label="주 메뉴"
      className="h-fit w-[210px] shrink-0 border-[3px] border-ink bg-surface shadow-card"
    >
      <h2 className="border-b-[3px] border-ink bg-surface-raise px-4 py-3 font-pixel text-[10px] tracking-[2px]">
        NAVIGATION
      </h2>

      <ul>
        {ITEMS.map((item) => (
          <li key={item.label}>
            <NavLink
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-2.5 border-b border-line px-4 py-3 text-[13px] font-semibold ${
                  isActive
                    ? 'border-l-[5px] border-l-accent bg-surface-raise text-ink'
                    : 'text-text-nav hover:bg-surface-soft'
                }`
              }
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>

      <p className="px-4 py-3 text-[11px] leading-[1.6] text-text-muted">
        Access Token · 메모리
        <br />
        Refresh Token · HttpOnly Cookie
      </p>
    </nav>
  );
}
