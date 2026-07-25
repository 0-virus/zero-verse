/** 디자인 정본 §7.9. 32×32 정사각. 무한 스크롤이 아니라 페이지네이션(PRD §9-N). */
export interface PaginationProps {
  /** 0-base */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 0) return null;

  const pages = Array.from({ length: totalPages }, (_, i) => i);
  const cell = 'h-8 w-8 border-2 text-[12px] font-bold';

  return (
    <nav aria-label="페이지" className="flex justify-center gap-1.5">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className={`${cell} border-shadow bg-surface disabled:cursor-not-allowed disabled:opacity-50 enabled:cursor-pointer enabled:hover:border-ink`}
      >
        ‹
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          aria-label={`${p + 1} 페이지`}
          aria-current={p === page ? 'page' : undefined}
          onClick={() => onChange(p)}
          className={`${cell} cursor-pointer ${
            p === page
              ? 'border-ink bg-ink text-text-on-ink'
              : 'border-shadow bg-surface hover:border-ink'
          }`}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className={`${cell} border-shadow bg-surface disabled:cursor-not-allowed disabled:opacity-50 enabled:cursor-pointer enabled:hover:border-ink`}
      >
        ›
      </button>
    </nav>
  );
}
