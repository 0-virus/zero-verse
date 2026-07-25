import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * 로그인·회원가입 공용 카드(디자인 정본 `Pages.dc.html` SCREEN: LOGIN).
 *
 * <p>정본은 하나의 카드 안에서 2분할 탭으로 전환하지만, 라우트는 `/signin`·`/signup` 둘을
 * 유지한다(PRD §9-M). 탭 클릭이 라우트를 바꾼다.
 *
 * <p>카드 폭 420px·`#fff8ec`·3px 잉크 보더·`shadow-on-dark`는 `AppShell`의 온보딩 분기가
 * 제공하는 다크 배경 위에 놓인다.
 */
export type AuthTab = 'signin' | 'signup';

export interface AuthCardProps {
  activeTab: AuthTab;
  children: ReactNode;
}

export function AuthCard({ activeTab, children }: AuthCardProps) {
  const navigate = useNavigate();

  return (
    <section
      style={{ boxShadow: 'var(--shadow-on-dark)' }}
      className="border-[3px] border-ink bg-surface-warm"
    >
      <header className="px-[30px] pt-[26px] text-center">
        <p className="mb-2.5 font-pixel text-base text-ink">
          ZERO<span className="text-accent">VERSE</span>
        </p>
        <p className="text-[13px] text-text-body">나만의 우주에 접속하세요</p>
      </header>

      <div role="tablist" className="mx-[30px] mt-5 flex border-[3px] border-ink">
        <TabButton
          label="로그인"
          active={activeTab === 'signin'}
          onClick={() => navigate('/signin')}
        />
        <TabButton
          label="회원가입"
          active={activeTab === 'signup'}
          onClick={() => navigate('/signup')}
          withDivider
        />
      </div>

      {children}
    </section>
  );
}

function TabButton({
  label,
  active,
  onClick,
  withDivider = false,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
  withDivider?: boolean;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`flex-1 cursor-pointer border-0 py-2.5 text-[13px] font-bold ${
        withDivider ? 'border-l-[3px] border-l-ink' : ''
      } ${active ? 'bg-ink text-text-on-ink' : 'bg-surface text-ink hover:bg-surface-raise'}`}
    >
      {label}
    </button>
  );
}

/** 카드 본문(폼 영역). 정본의 `padding:22px 30px 8px`, `gap:12px`. */
export function AuthCardBody({ children }: { children: ReactNode }) {
  return (
    <div style={{ padding: '22px 30px 8px', gap: 12 }} className="flex flex-col">
      {children}
    </div>
  );
}

/** 카드 하단 안내문. */
export function AuthCardFooter({ children }: { children: ReactNode }) {
  return (
    <p
      style={{ padding: '6px 30px 24px' }}
      className="text-center text-xs text-text-muted"
    >
      {children}
    </p>
  );
}

/** 폼 전송 버튼. 정본의 accent 버튼(`padding:11px 0`, 전폭). */
export function AuthSubmitButton({
  children,
  disabled = false,
}: {
  children: ReactNode;
  disabled?: boolean;
}) {
  return (
    <button
      type="submit"
      disabled={disabled}
      style={{ padding: '11px 0', marginTop: 4 }}
      className="w-full border-[3px] border-ink bg-accent text-sm font-bold text-white shadow-btn enabled:cursor-pointer enabled:hover:brightness-108 disabled:cursor-not-allowed disabled:opacity-60"
    >
      {children}
    </button>
  );
}

/** 폼 전체 오류(로그인 실패 등). 개별 필드가 아니라 카드 상단에 보여준다. */
export function AuthFormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p
      role="alert"
      className="border-2 border-danger bg-danger-bg px-3 py-2 text-xs text-danger"
    >
      {message}
    </p>
  );
}

/** 가입은 됐지만 자동 로그인만 실패한 경우의 안내(ADR-0003 §4). */
export function AuthNotice({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p
      role="status"
      className="border-2 border-info bg-info-bg px-3 py-2 text-xs text-info"
    >
      {message}
    </p>
  );
}
