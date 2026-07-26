import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { BlogInitialSetupPage } from '../pages/BlogInitialSetupPage';
import { AuthProvider } from '../lib/authContext';
import { HeroBlogProvider } from '../lib/heroBlogContext';
import { resetApiClient } from '../lib/apiClient';

const AUTHENTICATED_USER = {
  id: 1,
  email: 'setup@test.dev',
  name: '테스터',
  nickname: 'setup',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '테스터의 블로그', urlSlug: 'setup', isSetupCompleted: false },
};

function createFetchStub(opts?: { setupError?: string; setupStatus?: number }) {
  return vi.fn(async (url: string) => {
    const body = (data: unknown, success = true, status = 200, code = 'ERROR') => ({
      ok: success,
      status,
      text: async () =>
        JSON.stringify({ success, data, error: success ? null : { code, details: [] }, timestamp: '' }),
      json: async () => ({ success, data, error: null, timestamp: '' }),
    });
    if (url.includes('/auth/refresh')) {
      return body({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 });
    }
    if (url.includes('/auth/me')) return body(AUTHENTICATED_USER);
    if (url.includes('/blogs/me/initial-setup')) {
      if (opts?.setupError) {
        return body(null, false, opts.setupStatus || 400, opts.setupError);
      }
      return body({ id: 1, title: 'My Blog', urlSlug: 'myblog', description: 'Test', isSetupCompleted: true });
    }
    return body(null);
  });
}

function renderComponent(fetchStub?: ReturnType<typeof createFetchStub>) {
  vi.stubGlobal('fetch', fetchStub || createFetchStub());
  return render(
    <MemoryRouter>
      <AuthProvider>
        <HeroBlogProvider>
          <BlogInitialSetupPage />
        </HeroBlogProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('BlogInitialSetupPage', () => {
  beforeEach(() => resetApiClient());
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('초기 설정 폼 필드를 렌더한다', () => {
    renderComponent();
    expect(screen.getByLabelText('블로그 이름')).toBeInTheDocument();
    expect(screen.getByLabelText('주소 (slug)')).toBeInTheDocument();
    expect(screen.getByLabelText('한 줄 소개')).toBeInTheDocument();
  });

  it('slug 입력에 접두사 "zeroverse.dev/blog/"가 있다', () => {
    renderComponent();
    const prefix = screen.getByText('zeroverse.dev/blog/');
    expect(prefix).toBeInTheDocument();
  });

  it('제출 버튼에 "항해 시작하기 ✦" 텍스트가 있다', () => {
    renderComponent();
    expect(screen.getByRole('button', { name: /항해 시작하기/ })).toBeInTheDocument();
  });

  it('폼을 채우고 제출하면 initialSetup API가 호출된다', async () => {
    const user = userEvent.setup();
    const fetchStub = createFetchStub();
    renderComponent(fetchStub);

    const titleInput = screen.getByLabelText('블로그 이름');
    const slugInput = screen.getByLabelText('주소 (slug)');
    const descInput = screen.getByLabelText('한 줄 소개');

    await user.type(titleInput, '내 블로그');
    await user.type(slugInput, 'myblog');
    await user.type(descInput, '안녕하세요');

    const submitBtn = screen.getByRole('button', { name: /항해 시작하기/ });
    await user.click(submitBtn);

    await waitFor(() => {
      const call = fetchStub.mock.calls.find((c) => (c[0] as string).includes('/initial-setup'));
      expect(call).toBeDefined();
    });
  });

  it('BLOG_002 오류를 표시한다 (slug 중복)', async () => {
    const fetchStub = createFetchStub({ setupError: 'BLOG_002', setupStatus: 409 });
    renderComponent(fetchStub);

    const user = userEvent.setup();
    const submitBtn = screen.getByRole('button', { name: /항해 시작하기/ });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/이미 사용 중인 주소/)).toBeInTheDocument();
    });
  });

  it('BLOG_003 오류를 표시한다 (형식 위반)', async () => {
    const fetchStub = createFetchStub({ setupError: 'BLOG_003', setupStatus: 400 });
    renderComponent(fetchStub);

    const user = userEvent.setup();
    const submitBtn = screen.getByRole('button', { name: /항해 시작하기/ });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/유효하지 않은 주소 형식/)).toBeInTheDocument();
    });
  });

  it('BLOG_004 오류를 표시한다 (이미 완료)', async () => {
    const fetchStub = createFetchStub({ setupError: 'BLOG_004', setupStatus: 409 });
    renderComponent(fetchStub);

    const user = userEvent.setup();
    const submitBtn = screen.getByRole('button', { name: /항해 시작하기/ });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/이미 초기 설정을 완료/)).toBeInTheDocument();
    });
  });

  it('"사용 가능" 배지를 표시하지 않는다 (실시간 가용성 API 부재)', () => {
    renderComponent();
    expect(screen.queryByText(/사용 가능/)).not.toBeInTheDocument();
  });
});
