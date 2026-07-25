import { useState, type KeyboardEvent } from 'react';
import { TagChip } from './TagChip';

/** 태그 입력. Enter로 추가, 각 칩의 ×로 제거. 정규화(trim+lowercase)는 서버 계약과 동일하게 맞춘다. */
export interface TagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
}

export function TagInput({ tags, onChange, placeholder = '태그 입력 후 Enter' }: TagInputProps) {
  const [draft, setDraft] = useState('');

  const commit = () => {
    const normalized = draft.trim().toLowerCase();
    if (!normalized || tags.includes(normalized)) {
      setDraft('');
      return;
    }
    onChange([...tags, normalized]);
    setDraft('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      commit();
    }
  };

  return (
    <div>
      <div className="mb-2 flex flex-wrap gap-1.5">
        {tags.map((tag) => (
          <span key={tag} className="flex items-center gap-1">
            <TagChip name={tag} />
            <button
              type="button"
              aria-label={`${tag} 태그 제거`}
              onClick={() => onChange(tags.filter((t) => t !== tag))}
              className="cursor-pointer text-[11px] text-text-muted hover:text-danger"
            >
              ×
            </button>
          </span>
        ))}
      </div>
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        aria-label="태그 입력"
        className="w-full border-2 border-ink bg-surface px-3 py-2 text-[13px] outline-0"
      />
    </div>
  );
}
