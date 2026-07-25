import type { InputHTMLAttributes, ReactNode } from 'react';

/** 디자인 정본 §7.7. */
export interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  /** slug 입력의 접두 span */
  prefix?: string;
  trailing?: ReactNode;
}

export function FormField({
  label,
  hint,
  prefix,
  trailing,
  id,
  disabled = false,
  className = '',
  ...rest
}: FormFieldProps) {
  const inputId = id ?? `field-${label}`;
  const inputClass = disabled
    ? 'border-2 border-shadow bg-surface-inert text-text-muted'
    : 'border-2 border-ink bg-surface';

  return (
    <div className={className}>
      <label htmlFor={inputId} className="mb-[5px] block text-xs font-bold">
        {label}
      </label>
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
    </div>
  );
}
