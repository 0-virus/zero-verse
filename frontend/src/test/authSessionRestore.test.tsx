import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';

/**
 * 초기 세션 복구(PRD §8.1).
 *
 * <p>React StrictMode는 개발 모드에서 effect를 **두 번 실행**한다. 초기 복구가 `/auth/refresh`를
 * 직접 호출하면 같은 쿠키로 rotation이 두 번 일어나고, 두 번째는 이미 폐기된 토큰을 써서
 * `AUTH_003`을 받는다. 복구된 세션이 도로 비워질 수 있다.
 *
 * <p>`main.tsx`가 실제로 `<StrictMode>`를 쓰므로 그 조건에서 검증한다.
 */

const USER = {
  id: 1,
  email: 'restore@zeroverse.test',
  name: '테스터',
  nickname: 'restorer',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '테스터의 블로그', urlSlug: 'restorer', isSetupCompleted: true },
};

function ok(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function unauthorized() {
  const body = {
    success: false,
    data: null,
    error: { code: 'AUTH_003', message: '유효하지 않은 Refresh Token입니다.', details: [] },
    timestamp: '',
  };
  return {
    ok: false,
    status: 401,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

/** 현재 인증 상태를 화면에 드러내는 프로브. */
function AuthProbe() {
  const { user, isLoading } = useAuth();
  if (isLoading) return <p>loading</p>;
  return <p>{user ? `signed-in:${user.nickname}` : 'signed-out'}</p>;
}

function renderInStrictMode() {
  return render(
    <StrictMode>
      <MemoryRouter>
        <AuthProvider>
          <AuthProbe />
        </AuthProvider>
      </MemoryRouter>
    </StrictMode>,
  );
}

describe('초기 세션 복구', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('StrictMode에서 effect가 두 번 실행돼도 refresh는 1회만 호출된다', async () => {
    let refreshCount = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.includes('/auth/refresh')) {
          refreshCount += 1;
          // 두 번째 호출이 있다면 rotation된 토큰이라 실패해야 정상이다.
          if (refreshCount > 1) {
            return unauthorized();
          }
          return ok({ accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 3600 });
        }
        if (url.includes('/auth/me')) return ok(USER);
        return ok(null);
      }),
    );

    renderInStrictMode();

    await waitFor(() => expect(screen.getByText('signed-in:restorer')).toBeInTheDocument());
    expect(refreshCount).toBe(1);
  });

  it('StrictMode 이중 실행 후에도 복구된 세션이 유지된다', async () => {
    let refreshCount = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.includes('/auth/refresh')) {
          refreshCount += 1;
          // 두 번째 rotation은 폐기된 토큰이므로 실패한다 — 실제 서버 동작을 재현한다.
          return refreshCount === 1
            ? ok({ accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 3600 })
            : unauthorized();
        }
        if (url.includes('/auth/me')) return ok(USER);
        return ok(null);
      }),
    );

    renderInStrictMode();

    await waitFor(() => expect(screen.getByText('signed-in:restorer')).toBeInTheDocument());
    // 두 번째 refresh가 세션을 비우지 않았다.
    expect(screen.queryByText('signed-out')).not.toBeInTheDocument();
  });

  it('쿠키가 없으면 비로그인 상태로 마무리한다 (오류가 아니다)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => (url.includes('/auth/refresh') ? unauthorized() : ok(null))),
    );

    renderInStrictMode();

    await waitFor(() => expect(screen.getByText('signed-out')).toBeInTheDocument());
  });
});
