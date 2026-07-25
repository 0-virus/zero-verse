/** 디자인 정본 §2.6 · §7.8. 이모지 + 잉크 보더 정사각. */
export type AvatarSize = 28 | 34 | 38;

export interface AvatarProps {
  emoji: string;
  size?: AvatarSize;
  /** §2.6 아바타 배경 팔레트 */
  background?: string;
  label?: string;
}

export function Avatar({ emoji, size = 34, background = '#ffe9c9', label }: AvatarProps) {
  return (
    <span
      role="img"
      aria-label={label ?? '아바타'}
      style={{ width: size, height: size, background, fontSize: size * 0.5 }}
      className="inline-flex shrink-0 items-center justify-center border-2 border-ink"
    >
      {emoji}
    </span>
  );
}
