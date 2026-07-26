import type { InputHTMLAttributes, ReactNode } from 'react';

/** 디자인 정본 §7.7. */
export interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  /** slug 입력의 접두 span */
  prefix?: string;
  trailing?: ReactNode;
  /**
   * 라벨 배치. 기본은 라벨이 위(`/blog/setup` 등 온보딩 카드 정본).
   * `inline`은 `/settings` 정본의 `90px 1fr` 그리드 — 라벨이 입력 왼쪽에 온다.
   */
  orientation?: 'stacked' | 'inline';
  /** 입력 배경. `/blog/setup` 정본은 흰색, `/settings` 정본은 surface-warm. */
  surface?: 'plain' | 'warm';
}

export function FormField({
  label,
  hint,
  prefix,
  trailing,
  id,
  disabled = false,
  className = '',
  orientation = 'stacked',
  surface = 'plain',
  ...rest
}: FormFieldProps) {
  const inputId = id ?? `field-${label}`;
  const inputClass = disabled
    ? 'border-2 border-shadow bg-surface-inert text-text-muted'
    : `border-2 border-ink ${surface === 'warm' ? 'bg-surface-warm' : 'bg-surface'}`;

  const control = (
    <>
      <div className="flex items-center">
        {prefix && (
          <span className="border-2 border-r-0 border-ink bg-surface-inert px-3 py-2 text-[13px] text-text-muted">
            {prefix}
          </span>
        )}
        <input
          id={inputId}
          disabled={disabled}
          className={`w-full px-3 py-2.5 text-[13px] outline-0 ${inputClass}`}
          {...rest}
        />
        {trailing}
      </div>
      {hint && <p className="mt-[5px] text-[11px] text-text-muted">{hint}</p>}
    </>
  );

  if (orientation === 'inline') {
    return (
      <div className={`grid grid-cols-[90px_1fr] items-center gap-3 ${className}`}>
        <label htmlFor={inputId} className="text-[13px] font-semibold text-text-muted">
          {label}
        </label>
        <div>{control}</div>
      </div>
    );
  }

  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-[5px] block text-xs font-bold">
        {label}
      </label>
      {control}
    </div>
  );
}
