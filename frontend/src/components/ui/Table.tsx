import type { ReactNode } from 'react'

interface TableProps {
  children: ReactNode
  className?: string
}

interface TableHeadProps {
  children: ReactNode
}

interface TableBodyProps {
  children: ReactNode
}

interface TableRowProps {
  children: ReactNode
  className?: string
}

interface TableCellProps {
  children: ReactNode
  className?: string
  isHeader?: boolean
}

export function Table({ children, className = '' }: TableProps) {
  return (
    <table className={`w-full border-collapse border-2 border-border-cyan-dark shadow-card ${className}`}>
      {children}
    </table>
  )
}

export function TableHead({ children }: TableHeadProps) {
  return <thead className="bg-bg-panel border-b-2 border-border-cyan-dark">{children}</thead>
}

export function TableBody({ children }: TableBodyProps) {
  return <tbody>{children}</tbody>
}

export function TableRow({ children, className = '' }: TableRowProps) {
  return <tr className={`border-b border-border-purple-dark ${className}`}>{children}</tr>
}

export function TableCell({ children, isHeader = false, className = '' }: TableCellProps) {
  const baseClass = `px-4 py-3 text-text-body-cyan ${className}`
  if (isHeader) {
    return <th className={`text-left text-text-primary font-semibold ${baseClass}`}>{children}</th>
  }
  return <td className={baseClass}>{children}</td>
}
