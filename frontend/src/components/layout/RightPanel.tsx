import { Panel } from '../ui/Panel';

/**
 * `/` 메인 피드 우측 패널(300px). **메인 피드에만 존재**한다(PRD §6.7, DESIGN-SYSTEM §6.2).
 *
 * 정본 구성은 `내 블로그` / `최근 알림` / `유니버스 현황` 세 패널이다.
 * M0는 패널 경계와 빈 상태만 확정하며, 실제 데이터는 각 마일스톤에서 채운다
 * (내 블로그 M2, 최근 알림 M8, 유니버스 현황 M5).
 */
const SECTIONS = [
  { title: '내 블로그', empty: '블로그 정보를 불러오면 여기에 표시됩니다.' },
  { title: '최근 알림', empty: '아직 알림이 없습니다.' },
  { title: '유니버스 현황', empty: '아직 연결된 별이 없습니다.' },
];

export function RightPanel() {
  return (
    <aside
      aria-label="사이드 패널"
      style={{ position: 'sticky', top: 20, gap: 18 }}
      className="flex w-[300px] shrink-0 flex-col self-start"
    >
      {SECTIONS.map((section) => (
        <Panel key={section.title} title={section.title}>
          <p className="px-5 py-8 text-center text-[13px] text-text-muted">{section.empty}</p>
        </Panel>
      ))}
    </aside>
  );
}
