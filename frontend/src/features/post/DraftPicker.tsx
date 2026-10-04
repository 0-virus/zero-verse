import type { PostSummary } from './types';

export interface DraftPickerProps {
  drafts: PostSummary[];
  isLoading: boolean;
  error: string | null;
  page: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
  onPageChange: (page: number) => void;
  onSelect: (draft: PostSummary) => void;
  onClose: () => void;
}

export function DraftPicker({
  drafts,
  isLoading,
  error,
  page,
  totalPages,
  hasNext,
  hasPrevious,
  onPageChange,
  onSelect,
  onClose,
}: DraftPickerProps) {
  return (
    <div className="fixed inset-0 z-20 grid place-items-center bg-ink/30" role="presentation">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="draft-picker-title"
        className="w-[560px] border-[3px] border-ink bg-surface shadow-panel"
      >
        <header className="flex items-center justify-between border-b-[3px] border-ink bg-surface-raise px-5 py-3">
          <h2 id="draft-picker-title" className="text-[13px] font-bold tracking-[2px]">
            ■ 임시저장된 글 가져오기
          </h2>
          <button type="button" aria-label="임시저장 목록 닫기" onClick={onClose} className="text-lg">
            ×
          </button>
        </header>
        {isLoading && <p className="px-5 py-8 text-center text-sm text-text-muted">불러오는 중...</p>}
        {!isLoading && error && (
          <p role="alert" className="px-5 py-8 text-center text-sm text-danger">
            {error}
          </p>
        )}
        {!isLoading && !error && drafts.length === 0 && (
          <p className="px-5 py-8 text-center text-sm text-text-muted">임시저장된 글이 없습니다.</p>
        )}
        {!isLoading && !error && drafts.length > 0 && (
          <div className="max-h-[360px] overflow-y-auto">
            {drafts.map((draft) => (
              <button
                type="button"
                key={draft.id}
                aria-label={draft.title || '제목 없는 초안'}
                onClick={() => onSelect(draft)}
                className="flex w-full items-center justify-between border-b border-line px-5 py-4 text-left hover:bg-surface-soft"
              >
                <span>
                  <strong className="block text-sm text-ink">{draft.title || '제목 없는 초안'}</strong>
                  <span className="text-xs text-text-muted">{draft.excerpt || '내용 없음'}</span>
                </span>
                <span className="text-xs text-text-muted">수정하기 →</span>
              </button>
            ))}
          </div>
        )}
        {!isLoading && !error && totalPages > 1 && (
          <footer className="flex items-center justify-between border-t-2 border-ink bg-surface-warm px-5 py-3">
            <button
              type="button"
              aria-label="이전 초안 페이지"
              disabled={!hasPrevious}
              onClick={() => onPageChange(page - 1)}
              className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-40"
            >
              이전
            </button>
            <span className="text-xs text-text-muted">{page + 1} / {totalPages}</span>
            <button
              type="button"
              aria-label="다음 초안 페이지"
              disabled={!hasNext}
              onClick={() => onPageChange(page + 1)}
              className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-40"
            >
              다음
            </button>
          </footer>
        )}
      </section>
    </div>
  );
}
