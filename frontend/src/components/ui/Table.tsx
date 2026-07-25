import type { ReactNode } from 'react';

/** 관리자 목록 등에서 쓰는 표. 대형 컨테이너이므로 shadow-panel(§3). */
export interface TableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  width?: string;
}

export interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  emptyMessage?: string;
}

export function Table<T>({ columns, rows, rowKey, emptyMessage = '항목이 없습니다.' }: TableProps<T>) {
  return (
    <table className="w-full border-[3px] border-ink bg-surface shadow-panel">
      <thead>
        <tr className="border-b-[3px] border-ink bg-surface-raise">
          {columns.map((col) => (
            <th
              key={col.key}
              scope="col"
              style={{ width: col.width }}
              className="px-4 py-3 text-left text-xs font-bold tracking-[2px]"
            >
              {col.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={columns.length} className="px-4 py-10 text-center text-[13px] text-text-muted">
              {emptyMessage}
            </td>
          </tr>
        ) : (
          rows.map((row) => (
            <tr key={rowKey(row)} className="border-b border-line hover:bg-surface-soft">
              {columns.map((col) => (
                <td key={col.key} className="px-4 py-3 text-[13px]">
                  {col.render(row)}
                </td>
              ))}
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
