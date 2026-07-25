import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';
import { SigninPage } from '../pages/SigninPage';
import { SignupPage } from '../pages/SignupPage';

/** 인증 폼(FR-AUTH-01·02, PRD §9.4-AA). */

type Handler = (url: string, init?: RequestInit) => Response | Promise<Response>;

function ok(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function err(status: number, code: string, message: string, details: unknown[] = []) {
  const body = { success: false, data: null, error: { code, message, details }, timestamp: '' };
  return {
    ok: false,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

function renderPage(page: 'signin' | 'signup', handler: Handler) {
  vi.stubGlobal('fetch', vi.fn(handler));

  return render(
    <MemoryRouter initialEntries={[`/${page}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/signin" element={<SigninPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/" element={<p>홈</p>} />
          <Route path="/blog/setup" element={<p>초기 설정</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** 세션 복구(refresh)는 비로그인으로 응답한다. */
const noSession: Handler = (url) =>
  url.includes('/auth/refresh') ? err(401, 'AUTH_003', '유효하지 않은 Refresh Token입니다.') : ok(null);

describe('SigninPage', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('이메일·비밀번호 필드와 CTA를 렌더한다', async () => {
    renderPage('signin', noSession);

    await waitFor(() => expect(screen.getByLabelText('이메일')).toBeInTheDocument());
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '접속하기 ✦' })).toBeInTheDocument();
  });

  it('로그인·회원가입 2분할 탭을 렌더하고 현재 탭을 표시한다', async () => {
    renderPage('signin', noSession);

    await waitFor(() =>
      expect(screen.getByRole('tab', { name: '로그인' })).toHaveAttribute('aria-selected', 'true'),
    );
    expect(screen.getByRole('tab', { name: '회원가입' })).toHaveAttribute('aria-selected', 'false');
  });

  it('로그인 실패는 계정 존재 여부를 드러내지 않는 문구를 보여준다', async () => {
    renderPage('signin', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/signin')) {
        return err(401, 'AUTH_001', '이메일 또는 비밀번호가 올바르지 않습니다.');
      }
      return ok(null);
    });

    await waitFor(() => expect(screen.getByLabelText('이메일')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('이메일'), 'nobody@zeroverse.test');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: '접속하기 ✦' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다.');
    // 존재 여부를 짐작하게 하는 표현이 없어야 한다.
    expect(alert).not.toHaveTextContent(/가입되지 않은|없는 계정|등록되지/);
  });

  it('정지된 계정은 별도 안내를 보여준다', async () => {
    renderPage('signin', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/signin')) return err(403, 'USER_003', '정지된 사용자입니다.');
      return ok(null);
    });

    await waitFor(() => expect(screen.getByLabelText('이메일')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('이메일'), 'sus@zeroverse.test');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: '접속하기 ✦' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('정지된 계정입니다');
  });
});

describe('SignupPage', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('name·birthDate를 포함한 5개 필드를 렌더한다 (PRD §9.4-AA)', async () => {
    renderPage('signup', noSession);

    await waitFor(() => expect(screen.getByLabelText('닉네임')).toBeInTheDocument());
    expect(screen.getByLabelText('이름')).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByLabelText('생년월일')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '나의 별 만들기 ✦' })).toBeInTheDocument();
  });

  it('비밀번호 안내는 실제 검증 규칙(특수문자 포함)을 알린다', async () => {
    renderPage('signup', noSession);

    await waitFor(() => expect(screen.getByLabelText('비밀번호')).toBeInTheDocument());

    // 정본은 placeholder로만 안내한다. 힌트를 겹쳐 쓰면 같은 문구가 두 번 나온다.
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute(
      'placeholder',
      '8자 이상, 영문·숫자·특수문자 포함',
    );
    expect(screen.queryByText(/모두 포함해야 합니다/)).not.toBeInTheDocument();
  });

  it('가입 성공 후 자동 로그인하면 /blog/setup으로 이동한다', async () => {
    renderPage('signup', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/register')) return ok(null);
      if (url.includes('/auth/signin')) {
        return ok({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
      }
      if (url.includes('/auth/me')) {
        return ok({
          id: 1,
          email: 'new@zeroverse.test',
          name: '테스터',
          nickname: 'newbie',
          role: 'USER',
          profileImageUrl: null,
          defaultBlog: { id: 1, title: 'x', urlSlug: 'newbie', isSetupCompleted: false },
        });
      }
      return ok(null);
    });

    await fillSignupForm();
    await userEvent.click(screen.getByRole('button', { name: '나의 별 만들기 ✦' }));

    await waitFor(() => expect(screen.getByText('초기 설정')).toBeInTheDocument());
  });

  it('자동 로그인만 실패하면 "계정 생성됨"을 안내하고 재가입을 유도하지 않는다', async () => {
    renderPage('signup', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/register')) return ok(null);
      // 가입은 됐는데 로그인만 실패하는 상황.
      if (url.includes('/auth/signin')) return err(401, 'AUTH_001', '실패');
      return ok(null);
    });

    await fillSignupForm();
    await userEvent.click(screen.getByRole('button', { name: '나의 별 만들기 ✦' }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('계정이 생성되었습니다');
    expect(status).toHaveTextContent('로그인해 주세요');
    // 가입 실패로 오인하게 하는 오류를 함께 띄우지 않는다.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('이메일 중복은 해당 필드 오류로 보여준다', async () => {
    renderPage('signup', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/register')) {
        return err(409, 'USER_004', '이미 사용 중인 이메일입니다.');
      }
      return ok(null);
    });

    await fillSignupForm();
    await userEvent.click(screen.getByRole('button', { name: '나의 별 만들기 ✦' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 이메일입니다.');
  });

  it('서버 검증 실패는 서버가 준 필드 사유를 그대로 보여준다', async () => {
    renderPage('signup', (url) => {
      if (url.includes('/auth/refresh')) return err(401, 'AUTH_003', '');
      if (url.includes('/auth/register')) {
        return err(400, 'VALIDATION_001', '요청 값이 올바르지 않습니다.', [
          { field: 'password', reason: '비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.' },
        ]);
      }
      return ok(null);
    });

    await fillSignupForm();
    await userEvent.click(screen.getByRole('button', { name: '나의 별 만들기 ✦' }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('입력값을 확인해 주세요.'),
    );
  });
});

async function fillSignupForm() {
  await waitFor(() => expect(screen.getByLabelText('닉네임')).toBeInTheDocument());
  await userEvent.type(screen.getByLabelText('닉네임'), 'newbie');
  await userEvent.type(screen.getByLabelText('이름'), '테스터');
  await userEvent.type(screen.getByLabelText('이메일'), 'new@zeroverse.test');
  await userEvent.type(screen.getByLabelText('비밀번호'), 'Password123!');
  await userEvent.type(screen.getByLabelText('생년월일'), '1995-01-01');
}
