import { NavLink } from 'react-router-dom';

/**
 * 화면 전용 좌측 패널(240px).
 *
 * **앱 내비게이션이 아니다**(PRD §6.7). `/settings*`는 설정 메뉴, `/blog/:slug`는
 * 카테고리·블로그 통계를 담는다. 값은 `ZeroVerse Pages.dc.html`의 SETTINGS shell이 정본이다.
 *
 * M0는 패널 경계와 내비게이션 구조만 확정한다. 블로그 카테고리·통계 데이터는 M2·M3다.
 */
export type ScreenPanelKind = 'settings' | 'blog';

const SETTINGS_NAV = [
  { to: '/settings', label: '프로필' },
  { to: '/settings/universe', label: '유니버스' },
  { to: '/settings/posts', label: '글·카테고리' },
];

export function ScreenPanel({ kind }: { kind: ScreenPanelKind }) {
  return (
    <aside
      aria-label={kind === 'settings' ? '설정 메뉴' : '블로그 메뉴'}
      style={{ position: 'sticky', top: 20 }}
      className="w-60 shrink-0 self-start border-[3px] border-ink bg-surface shadow-card"
    >
      <h2 className="border-b-[3px] border-ink bg-surface-raise px-3.5 py-3 font-pixel text-[9px] tracking-[1px]">
        {kind === 'settings' ? 'SETTINGS' : 'BLOG'}
      </h2>

      {kind === 'settings' ? (
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
      ) : (
        <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
          카테고리를 불러오면 여기에 표시됩니다.
        </p>
      )}
    </aside>
  );
}
