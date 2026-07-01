interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

export default function Pagination({
  currentPage,
  totalPages,
  onPageChange,
}: PaginationProps) {
  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)

  return (
    <div className="flex gap-2 justify-center items-center my-6">
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
        className="px-4 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary disabled:opacity-50"
      >
        Prev
      </button>

      {pages.map((page) => (
        <button
          key={page}
          onClick={() => onPageChange(page)}
          className={`px-3 py-2 border-2 ${
            page === currentPage
              ? 'bg-bg-button-primary border-border-cyan-dark text-text-primary'
              : 'bg-bg-panel border-border-purple-dark text-text-muted hover:text-text-primary'
          }`}
        >
          {page}
        </button>
      ))}

      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
        className="px-4 py-2 bg-bg-button-neutral border-2 border-border-purple-dark text-text-primary disabled:opacity-50"
      >
        Next
      </button>
    </div>
  )
}
