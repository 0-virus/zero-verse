import { NavLink } from 'react-router-dom';
import { Panel } from '../ui/Panel';

/**
 * 화면 전용 좌측 패널(240px).
 *
 * **앱 내비게이션이 아니다**(PRD §6.7). 값은 `ZeroVerse Pages.dc.html`이 정본이다.
 *
 * - `settings`: 단일 패널 + `SETTINGS` 헤더(Press Start 9px) + 메뉴 3항목.
 * - `blog`: `■ 카테고리` / `■ 블로그 통계` **두 패널**을 `gap:18px`로 쌓는다.
 *
 * M0는 패널 경계와 내비게이션 구조만 확정한다. 카테고리·통계 데이터는 M2·M3다.
 */
export type ScreenPanelKind = 'settings' | 'blog';

const SETTINGS_NAV = [
  { to: '/settings', label: '프로필' },
  { to: '/settings/universe', label: '유니버스' },
  { to: '/settings/posts', label: '글·카테고리' },
];

export function ScreenPanel({ kind }: { kind: ScreenPanelKind }) {
  if (kind === 'blog') {
    return (
      <aside
        aria-label="블로그 메뉴"
        style={{ position: 'sticky', top: 20, gap: 18 }}
        className="flex w-60 shrink-0 flex-col self-start"
      >
        <Panel title="카테고리">
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            카테고리를 불러오면 여기에 표시됩니다.
          </p>
        </Panel>
        <Panel title="블로그 통계">
          <p className="px-3.5 py-8 text-center text-[13px] text-text-muted">
            통계를 불러오면 여기에 표시됩니다.
          </p>
        </Panel>
      </aside>
    );
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
