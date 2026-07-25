import type { ButtonHTMLAttributes, ReactNode } from 'react';

/** 디자인 정본 §7.1의 7개 variant. */
export type ButtonVariant =
  | 'primary'
  | 'neutral'
  | 'ink'
  | 'inert'
  | 'danger'
  | 'warning'
  | 'accentSm';

export type ButtonSize = 'sm' | 'md' | 'submit';

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-white border-[3px] border-ink shadow-btn hover:brightness-108',
  neutral: 'bg-surface text-ink border-[3px] border-ink shadow-btn hover:bg-surface-raise',
  ink: 'bg-ink text-text-on-ink border-[3px] border-ink shadow-btn hover:brightness-120',
  inert: 'bg-surface text-text-muted border-2 border-shadow hover:border-ink hover:text-ink',
  danger: 'bg-surface text-danger border-2 border-danger hover:bg-danger-bg',
  warning: 'bg-surface text-warning border-2 border-warning hover:bg-warning-bg',
  accentSm: 'bg-accent text-white border-2 border-ink shadow-sm hover:brightness-108',
};

const SIZE_CLASS: Record<ButtonSize, string> = {
  sm: 'px-3.5 py-1.5 text-xs',
  md: 'px-[18px] py-2 text-[13px]',
  submit: 'w-full py-3 text-sm',
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
}

export function Button({
  variant = 'neutral',
  size = 'md',
  disabled = false,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  const disabledClass = disabled
    ? 'cursor-not-allowed opacity-60 hover:brightness-100 hover:bg-inherit'
    : 'cursor-pointer';

  return (
    <button
      type="button"
      disabled={disabled}
      data-variant={variant}
      className={`font-sans font-bold ${VARIANT_CLASS[variant]} ${SIZE_CLASS[size]} ${disabledClass} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
