import { NavLink } from 'react-router-dom';

/**
 * 앱 내비 사이드바. **`/`에서만** 렌더한다(PRD §6.7, §9-L).
 *
 * 사이드바에 Admin 항목을 두지 않는다 — 폐기됨(PRD §9-K).
 */
const ITEMS = [
  { to: '/', label: '메인 피드' },
  { to: '/settings/posts', label: '내 글 관리' },
  { to: '/settings/universe', label: '유니버스' },
  { to: '/notifications', label: '알림' },
  { to: '/settings', label: '설정' },
];

export function SideNav() {
  return (
    <nav
      aria-label="주 메뉴"
      className="h-fit w-60 border-[3px] border-ink bg-surface shadow-card"
    >
      {ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) =>
            `block border-b border-line px-4 py-3 text-[13px] font-semibold ${
              isActive
                ? 'border-l-[5px] border-l-accent bg-surface-raise text-ink'
                : 'text-text-nav hover:bg-surface-soft'
            }`
          }
        >
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
