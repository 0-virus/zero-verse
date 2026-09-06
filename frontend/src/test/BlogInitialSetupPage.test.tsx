import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { BlogInitialSetupPage } from '../pages/BlogInitialSetupPage';
import { AuthProvider } from '../lib/authContext';
import { HeroBlogProvider } from '../lib/heroBlogContext';
import { resetApiClient } from '../lib/apiClient';
import { SetupGuard } from '../routes/guards';

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
  let nextCategoryId = 10;
  return vi.fn(async (url: string, init?: RequestInit) => {
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
    if (url.includes('/blogs/1/categories')) {
      if (init?.method !== 'POST') {
        return body({
          items: [],
          page: 0,
          size: 100,
          totalElements: 0,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        });
      }
      return body({
        id: nextCategoryId++,
        parentId: null,
        name: '시작 카테고리',
        type: 'GENERAL',
        displayOrder: 0,
        postCount: 0,
        children: [],
      });
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

function SetupRoute() {
  const location = useLocation();
  const { pathname } = location;
  if (pathname === '/settings/posts') {
    return (
      <SetupGuard>
        <p>카테고리 관리 경로</p>
      </SetupGuard>
    );
  }
  if (pathname === '/blog/myblog') return <p>완료 블로그 경로</p>;
  if (pathname === '/blog/setup') {
    return (
      <SetupGuard>
        <BlogInitialSetupPage />
      </SetupGuard>
    );
  }
  return <p>홈 경로</p>;
}

function renderComponentWithSetupGuard(fetchStub: ReturnType<typeof createFetchStub>) {
  vi.stubGlobal('fetch', fetchStub);
  return render(
    <MemoryRouter initialEntries={['/blog/setup']}>
      <AuthProvider>
          <HeroBlogProvider>
            <Routes>
              <Route path="*" element={<SetupRoute />} />
            </Routes>
        </HeroBlogProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function renderRefreshableSetupWithGuard(fetchStub: ReturnType<typeof createFetchStub>) {
  vi.stubGlobal('fetch', fetchStub);

  function RefreshableTree() {
    const [refreshKey, setRefreshKey] = useState(0);
    return (
      <MemoryRouter initialEntries={['/blog/setup']}>
        <button type="button" onClick={() => setRefreshKey((current) => current + 1)}>
          브라우저 새로고침
        </button>
        <div key={refreshKey}>
          <AuthProvider>
            <HeroBlogProvider>
              <Routes>
                <Route path="*" element={<SetupRoute />} />
              </Routes>
            </HeroBlogProvider>
          </AuthProvider>
        </div>
      </MemoryRouter>
    );
  }

  return render(<RefreshableTree />);
}

function createCategoryRecoveryStub(mode: 'partial' | 'lost' | 'refresh-failure') {
  let initialSetupCalls = 0;
  let authMeCalls = 0;
  let categoryGetCalls = 0;
  let lostResponseUsed = false;
  let partialFailureUsed = false;
  let refreshFailureUsed = false;
  let nextId = 10;
  const categories: Array<{
    id: number;
    parentId: null;
    name: string;
    type: 'GENERAL';
    displayOrder: number;
    postCount: number;
    children: [];
  }> = [];
  const postNames: string[] = [];
  const fetchStub = vi.fn(async (url: string, init?: RequestInit) => {
    const body = (data: unknown, success = true, status = 200, code = 'ERROR') => ({
      ok: success,
      status,
      text: async () =>
        JSON.stringify({ success, data, error: success ? null : { code, details: [] }, timestamp: '' }),
      json: async () => ({ success, data, error: success ? null : { code, details: [] }, timestamp: '' }),
    });
    if (url.includes('/auth/refresh')) return body({ accessToken: 't' });
    if (url.includes('/auth/me')) {
      authMeCalls += 1;
      if (mode === 'refresh-failure' && authMeCalls === 2 && !refreshFailureUsed) {
        refreshFailureUsed = true;
        return body(null, false, 500, 'COMMON_500');
      }
      return body({
        ...AUTHENTICATED_USER,
        defaultBlog: {
          ...AUTHENTICATED_USER.defaultBlog,
          urlSlug: authMeCalls > 1 ? 'myblog' : 'setup',
          isSetupCompleted: authMeCalls > 1,
        },
      });
    }
    if (url.includes('/blogs/me/initial-setup')) {
      initialSetupCalls += 1;
      return body({ id: 1, title: 'My Blog', urlSlug: 'myblog', description: 'Test', isSetupCompleted: true });
    }
    if (url.includes('/blogs/1/categories')) {
      if (init?.method !== 'POST') {
        categoryGetCalls += 1;
        return body({
          items: categories,
          page: 0,
          size: 100,
          totalElements: categories.length,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        });
      }
      const request = JSON.parse(String(init.body)) as { name: string; displayOrder: number };
      postNames.push(request.name);
      const created = {
        id: nextId++,
        parentId: null,
        name: request.name,
        type: 'GENERAL' as const,
        displayOrder: request.displayOrder,
        postCount: 0,
        children: [] as [],
      };
      if (mode === 'partial' && request.name === '프론트엔드' && !partialFailureUsed) {
        partialFailureUsed = true;
        return body(null, false, 500, 'COMMON_500');
      }
      categories.push(created);
      if (mode === 'lost' && request.name === '백엔드' && !lostResponseUsed) {
        lostResponseUsed = true;
        throw new Error('생성 응답 유실');
      }
      return body(created);
    }
    throw new Error(`예상하지 못한 요청: ${url}`);
  });
  return {
    fetchStub: fetchStub as unknown as ReturnType<typeof createFetchStub>,
    categories,
    postNames,
    get initialSetupCalls() {
      return initialSetupCalls;
    },
    get authMeCalls() {
      return authMeCalls;
    },
    get categoryGetCalls() {
      return categoryGetCalls;
    },
  };
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

  it('카테고리 일부 실패 뒤 재시도해도 initial-setup은 다시 호출하지 않는다', async () => {
    const stub = createCategoryRecoveryStub('partial');
    renderComponent(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));
    expect(
      await screen.findByText(/블로그 설정은 완료되었으니 다시 시도하거나 카테고리 관리/),
    ).toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);
    expect(stub.postNames).toEqual(['백엔드', '프론트엔드']);

    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));
    await waitFor(() => expect(stub.postNames).toEqual(['백엔드', '프론트엔드', '프론트엔드', '회고']));
    expect(stub.initialSetupCalls).toBe(1);
    expect(stub.categories.map((item) => item.name)).toEqual(['백엔드', '프론트엔드', '회고']);
  });

  it('카테고리 일부 실패 뒤 refreshUser와 SetupGuard를 거쳐 관리 화면으로 이어간다', async () => {
    const stub = createCategoryRecoveryStub('partial');
    renderComponentWithSetupGuard(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /항해 시작하기/ }));
    expect(
      await screen.findByRole('button', { name: '카테고리 관리에서 이어서 하기' }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '카테고리 관리에서 이어서 하기' }));
    await waitFor(() => expect(stub.authMeCalls).toBeGreaterThan(1));
    expect(await screen.findByText('카테고리 관리 경로')).toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);
  });

  it('부분 실패를 같은 화면에서 재시도해 성공하면 관리가 아닌 자기 블로그로 이동한다', async () => {
    const stub = createCategoryRecoveryStub('partial');
    renderComponentWithSetupGuard(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /항해 시작하기/ }));
    expect(
      await screen.findByText(/블로그 설정은 완료되었으니 다시 시도하거나 카테고리 관리/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));
    expect(await screen.findByText('완료 블로그 경로')).toBeInTheDocument();
    expect(screen.queryByText('카테고리 관리 경로')).not.toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);
    expect(stub.postNames).toEqual(['백엔드', '프론트엔드', '프론트엔드', '회고']);
  });

  it('완료 intent의 refreshUser 실패는 recovery 상태에서 관리 재시도로 이어진다', async () => {
    const stub = createCategoryRecoveryStub('refresh-failure');
    renderComponentWithSetupGuard(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /항해 시작하기/ }));
    expect(
      await screen.findByRole('button', { name: '카테고리 관리에서 이어서 하기' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('완료 블로그 경로')).not.toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);

    await user.click(screen.getByRole('button', { name: '카테고리 관리에서 이어서 하기' }));
    expect(await screen.findByText('카테고리 관리 경로')).toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);
  });

  it('카테고리 부분 실패 뒤 새로고침해도 history state로 관리 화면에 진입한다', async () => {
    const stub = createCategoryRecoveryStub('partial');
    renderRefreshableSetupWithGuard(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: /항해 시작하기/ }));
    expect(
      await screen.findByText(/블로그 설정은 완료되었으니 다시 시도하거나 카테고리 관리/),
    ).toBeInTheDocument();
    expect(stub.initialSetupCalls).toBe(1);

    await user.click(screen.getByRole('button', { name: '브라우저 새로고침' }));
    expect(await screen.findByText('카테고리 관리 경로')).toBeInTheDocument();
    expect(stub.authMeCalls).toBeGreaterThan(1);
    expect(stub.initialSetupCalls).toBe(1);
  });

  it('카테고리 생성 응답이 유실되어도 GET으로 확인해 같은 카테고리를 재생성하지 않는다', async () => {
    const stub = createCategoryRecoveryStub('lost');
    renderComponent(stub.fetchStub);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));
    await waitFor(() => expect(stub.initialSetupCalls).toBe(1));
    expect(stub.categoryGetCalls).toBeGreaterThanOrEqual(2);
    expect(stub.postNames).toEqual(['백엔드', '프론트엔드', '회고']);
    expect(stub.categories.map((item) => item.name)).toEqual(['백엔드', '프론트엔드', '회고']);
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
