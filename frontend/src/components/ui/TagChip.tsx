/** 디자인 정본 §7.4. 표시는 항상 `#태그명`. */
export interface TagChipProps {
  name: string;
  onClick?: () => void;
}

export function TagChip({ name, onClick }: TagChipProps) {
  const className =
    'inline-block border border-line bg-surface-soft px-2 py-0.5 text-[11px] font-semibold text-accent';

  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${className} cursor-pointer`}>
        #{name}
      </button>
    );
  }
  return <span className={className}>#{name}</span>;
}
