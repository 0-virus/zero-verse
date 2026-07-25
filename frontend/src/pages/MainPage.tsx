import { useState } from 'react';
import { Tabs } from '../components/ui/Tab';

/**
 * `/` 메인 피드의 중앙 열.
 *
 * 페이지 제목은 히어로(`AppShell`)가 담당하므로 여기서 h1을 다시 두지 않는다
 * (`ZeroVerse Main Feed v2.dc.html` CENTER FEED 구조).
 *
 * 관계 탭은 `전체` / `친구` / `내가 발견한` 3개다 — `나를 발견한` 탭은 제거됐다(PRD §9-G).
 * M0는 탭과 빈 상태만 확정하며 실제 피드 데이터는 M7이다.
 */
const TABS = [
  { key: 'all', label: '전체' },
  { key: 'friend', label: '친구' },
  { key: 'following', label: '내가 발견한' },
];

export function MainPage() {
  const [activeKey, setActiveKey] = useState('all');

  return (
    <div className="flex flex-col gap-5">
      <Tabs items={TABS} activeKey={activeKey} onChange={setActiveKey} />
      <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
        <p className="text-[13px] text-text-muted">아직 도착한 신호가 없습니다.</p>
      </div>
    </div>
  );
}
