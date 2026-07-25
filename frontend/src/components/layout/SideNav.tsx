import { NavLink } from 'react-router-dom';

/**
 * 앱 내비게이션 사이드바.
 *
 * **`/` 메인 피드에만 존재**하며 폭 210px, `position:sticky; top:20px`이다.
 * 값은 `docs/design/ZeroVerse Main Feed v2.dc.html`의 LEFT NAV 인라인 style이 정본이다
 * (PRD §6.7, DESIGN-SYSTEM §6.2).
 *
 * - 헤더: 12px/14px 패딩, 11px/700, `letter-spacing:3px`, muted `#9b8aa8`.
 * - 항목: 11px/14px 패딩, 14px, `gap:10px`, 아이콘 12px, `border-left:5px`(활성만 accent), hover `#fff3dd`.
 * - 푸터: 상단 3px 보더, 10px, `line-height:1.6`, muted.
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
      style={{ position: 'sticky', top: 20 }}
      className="flex w-[210px] shrink-0 flex-col self-start border-[3px] border-ink bg-surface shadow-card"
    >
      <h2 className="border-b-[3px] border-ink px-3.5 py-3 text-[11px] font-bold tracking-[3px] text-text-muted">
        NAVIGATION
      </h2>

      <ul>
        {ITEMS.map((item) => (
          <li key={item.label}>
            <NavLink
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-2.5 px-3.5 py-[11px] text-sm ${
                  isActive
                    ? 'border-l-[5px] border-l-accent bg-surface-raise font-bold text-ink'
                    : 'border-l-[5px] border-l-transparent font-medium text-text-nav hover:bg-surface-soft'
                }`
              }
            >
              <span aria-hidden="true" className="text-xs">
                {item.icon}
              </span>
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>

      <p className="border-t-[3px] border-ink px-3.5 py-3 text-[10px] leading-[1.6] text-text-muted">
        Access Token · 메모리
        <br />
        Refresh Token · HttpOnly Cookie
      </p>
    </nav>
  );
}
