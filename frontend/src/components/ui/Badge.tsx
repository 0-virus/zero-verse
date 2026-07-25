/** 디자인 정본 §7.3. enum 값과 화면 라벨을 분리한다 — UNIVERSE는 "친구"로 표기(PRD §9-B). */
export type BadgeKind =
  | 'PUBLIC'
  | 'UNIVERSE'
  | 'PRIVATE'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'ADMIN'
  | 'USER';

const LABEL: Record<BadgeKind, string> = {
  PUBLIC: 'PUBLIC',
  UNIVERSE: '친구',
  PRIVATE: 'PRIVATE',
  ACTIVE: 'ACTIVE',
  SUSPENDED: 'SUSPENDED',
  ADMIN: 'ADMIN',
  USER: 'USER',
};

const STYLE: Record<BadgeKind, string> = {
  PUBLIC: 'text-success border-success bg-success-bg',
  UNIVERSE: 'text-universe border-universe bg-universe-bg',
  PRIVATE: 'text-neutral border-neutral bg-neutral-bg',
  ACTIVE: 'text-success border-success bg-success-bg',
  SUSPENDED: 'text-danger border-danger bg-danger-bg',
  ADMIN: 'text-universe border-universe tracking-[1px]',
  USER: 'text-neutral border-neutral tracking-[1px]',
};

export interface BadgeProps {
  kind: BadgeKind;
}

export function Badge({ kind }: BadgeProps) {
  return (
    <span
      data-kind={kind}
      className={`inline-block border-2 px-2 py-[3px] text-[10px] font-bold tracking-[2px] ${STYLE[kind]}`}
    >
      {LABEL[kind]}
    </span>
  );
}

/** 미읽음·대기 신청 카운트 배지(§7.3). */
export function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="inline-block border-2 border-ink bg-accent px-1.5 text-[10px] font-bold text-white">
      {count}
    </span>
  );
}
