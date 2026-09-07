import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEffect } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SettingsPostsPage } from '../pages/SettingsPostsPage';
import { ScreenPanel } from '../components/layout/ScreenPanel';
import { AuthProvider, useAuth } from '../lib/authContext';
import { resetApiClient } from '../lib/apiClient';
import { HeroBlogProvider, useHeroBlog } from '../lib/heroBlogContext';
import type { Category } from '../features/category/categoryApi';

const USER = {
  id: 1,
  email: 'category@test.dev',
  name: '카테고리 사용자',
  nickname: 'category-user',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '카테고리 별', urlSlug: 'category', isSetupCompleted: true },
};

function envelope(data: unknown, status = 200) {
  return {
    ok: true,
    status,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function trackText(response: Response, onRead: () => void) {
  const readText = response.text.bind(response);
  return {
    ...response,
    text: async () => {
      const body = await readText();
      onRead();
      return body;
    },
  } as unknown as Response;
}

function failure(status: number, code: string) {
  const body = { success: false, data: null, error: { code, message: code, details: [] }, timestamp: '' };
  return {
    ok: false,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

function category(
  id: number,
  name: string,
  type: Category['type'],
  displayOrder: number,
  parentId: number | null = null,
): Category {
  return { id, name, type, displayOrder, parentId, postCount: id, children: [] };
}

function page(items: Category[], totalElements = items.length, totalPages = 1, pageNumber = 0) {
  return {
    items,
    page: pageNumber,
    size: 100,
    totalElements,
    totalPages,
    hasNext: pageNumber + 1 < totalPages,
    hasPrevious: pageNumber > 0,
  };
}

function findCategory(categories: Category[], id: number): Category | undefined {
  for (const current of categories) {
    if (current.id === id) return current;
    const child = current.children.find((item) => item.id === id);
    if (child) return child;
  }
  return undefined;
}

function renderSettings() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <SettingsPostsPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function PublicBlogSeed() {
  const { setBlog } = useHeroBlog();
  useEffect(() => {
    setBlog({
      id: 1,
      title: '공개 별',
      urlSlug: 'public-star',
      description: '공개 기록',
      owner: { id: 2, nickname: '별지기', profileImageUrl: null, bio: null },
    });
  }, [setBlog]);
  return null;
}

function BlogSwitchControl() {
  const { setBlog } = useHeroBlog();
  useEffect(() => {
    setBlog({
      id: 1,
      title: 'A 별',
      urlSlug: 'a',
      description: 'A 기록',
      owner: { id: 2, nickname: 'A 주인', profileImageUrl: null, bio: null },
    });
  }, [setBlog]);
  return (
    <button
      type="button"
      onClick={() =>
        setBlog({
          id: 2,
          title: 'B 별',
          urlSlug: 'b',
          description: 'B 기록',
          owner: { id: 3, nickname: 'B 주인', profileImageUrl: null, bio: null },
        })
      }
    >
      B 블로그로 전환
    </button>
  );
}

function AuthViewerControls() {
  const { signout } = useAuth();
  return (
    <button type="button" onClick={() => void signout()}>
      공개 방문자로 전환
    </button>
  );
}

function installCategoryApi(initial: Category[], options?: { conflictOnce?: boolean }) {
  const roots = initial.map((item) => ({ ...item, children: [...item.children] }));
  let nextId = Math.max(0, ...roots.flatMap((root) => [root.id, ...root.children.map((child) => child.id)])) + 1;
  let conflict = options?.conflictOnce ?? false;
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
    if (url.includes('/auth/me')) return envelope(USER);
    if (!url.includes('/api/v1/blogs/1/categories')) throw new Error(`예상하지 못한 요청: ${url}`);

    const method = init?.method ?? 'GET';
    if (method === 'GET') {
      const query = new URL(url).searchParams;
      const requestedPage = Number(query.get('page') ?? '0');
      const requestedSize = Number(query.get('size') ?? '100');
      const sorted = [...roots].sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id);
      const items = sorted.slice(requestedPage * requestedSize, (requestedPage + 1) * requestedSize);
      const totalPages = Math.max(1, Math.ceil(sorted.length / requestedSize));
      return envelope(page(items, sorted.length, totalPages, requestedPage));
    }

    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : {};
    if (method === 'POST') {
      const created = category(
        nextId++,
        String(body.name),
        body.type as Category['type'],
        Number(body.displayOrder),
        body.parentId == null ? null : Number(body.parentId),
      );
      if (created.parentId == null) roots.push(created);
      else findCategory(roots, created.parentId)?.children.push(created);
      return envelope(created, 201);
    }

    if (url.endsWith('/order')) {
      if (conflict) {
        conflict = false;
        return failure(409, 'CAT_007');
      }
      const ids = body as unknown as number[];
      const first = findCategory(roots, ids[0]);
      const siblings = first?.parentId == null ? roots : findCategory(roots, first?.parentId ?? -1)?.children;
      if (siblings) {
        const byId = new Map(siblings.map((item) => [item.id, item]));
        ids.forEach((id, index) => {
          const item = byId.get(id);
          if (item) item.displayOrder = index;
        });
      }
      return envelope(null);
    }

    const match = url.match(/\/categories\/(\d+)$/);
    const id = match ? Number(match[1]) : -1;
    const target = findCategory(roots, id);
    if (!target) return failure(404, 'CAT_001');
    if (method === 'PUT') {
      target.name = String(body.name);
      target.type = body.type as Category['type'];
      target.displayOrder = Number(body.displayOrder);
      return envelope(target);
    }
    if (method === 'DELETE') {
      const rootIndex = roots.findIndex((item) => item.id === id);
      if (rootIndex >= 0) roots.splice(rootIndex, 1);
      else {
        for (const root of roots) root.children = root.children.filter((child) => child.id !== id);
      }
      return envelope(null);
    }
    throw new Error(`예상하지 못한 메서드: ${method}`);
  });

  vi.stubGlobal('fetch', fetchMock);
  return { fetchMock, roots };
}

beforeEach(() => resetApiClient());
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  resetApiClient();
});

describe('M3 카테고리 관리 행동', () => {
  it('mutation 성공 뒤 재조회가 실패하면 성공 notice를 남기지 않고 재시도한다', async () => {
    const initial = [category(1, '기본', 'DEFAULT', 0), category(2, '일반', 'GENERAL', 1)];
    let categoryReads = 0;
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (!url.includes('/api/v1/blogs/1/categories')) {
        throw new Error(`예상하지 못한 요청: ${url}`);
      }
      if ((init?.method ?? 'GET') === 'GET') {
        categoryReads += 1;
        if (categoryReads === 2) return failure(500, 'COMMON_500');
        return envelope(page(initial));
      }
      if (url.endsWith('/order')) return envelope(null);
      throw new Error(`예상하지 못한 메서드: ${init?.method}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderSettings();
    await screen.findByRole('button', { name: '일반 순서 이동' });

    fireEvent.keyDown(screen.getByRole('button', { name: '기본 순서 이동' }), { key: 'ArrowDown' });
    expect(await screen.findByRole('button', { name: '카테고리 다시 불러오기' })).toBeInTheDocument();
    expect(screen.queryByText('카테고리 순서를 저장했습니다.')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '카테고리 다시 불러오기' }));
    await screen.findByRole('button', { name: '일반 순서 이동' });
    expect(screen.queryByText('카테고리 순서를 저장했습니다.')).not.toBeInTheDocument();
  });

  it('A mutation이 pending인 동안 B·null을 거쳐 A로 돌아와도 오래된 성공이 현재 화면을 덮지 않는다', async () => {
    const userB = {
      ...USER,
      id: 2,
      defaultBlog: { id: 2, title: 'B 별', urlSlug: 'b', isSetupCompleted: true },
    };
    const categoryA = [
      category(11, 'A 카테고리', 'GENERAL', 0),
      category(12, 'A 두번째', 'GENERAL', 1),
    ];
    const categoryB = [category(22, 'B 카테고리', 'GENERAL', 0)];
    const authUsers = [USER, userB, USER];
    let authMeCalls = 0;
    let releaseOrder: (() => void) | null = null;
    let orderStarted: (() => void) | null = null;
    let orderParsed: (() => void) | null = null;
    const orderPending = new Promise<void>((resolve) => {
      releaseOrder = resolve;
    });
    const orderSeen = new Promise<void>((resolve) => {
      orderStarted = resolve;
    });
    const orderResponseParsed = new Promise<void>((resolve) => {
      orderParsed = resolve;
    });
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/signout')) return envelope(null);
      if (url.includes('/auth/signin')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(authUsers[Math.min(authMeCalls++, authUsers.length - 1)]);
      const blogMatch = url.match(/\/blogs\/(\d+)\/categories/);
      if (!blogMatch) throw new Error(`예상하지 못한 요청: ${url}`);
      if ((init?.method ?? 'GET') === 'GET') {
        const items = blogMatch[1] === '1' ? categoryA : categoryB;
        return envelope(page(items));
      }
      if (url.endsWith('/order')) {
        orderStarted?.();
        await orderPending;
        return trackText(envelope(null), orderParsed!);
      }
      throw new Error(`예상하지 못한 메서드: ${init?.method}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    function IdentityControls() {
      const { refreshUser, signout, signin } = useAuth();
      return (
        <>
          <button type="button" onClick={() => void refreshUser()}>
            B로 전환
          </button>
          <button type="button" onClick={() => void signout()}>
            로그아웃
          </button>
          <button
            type="button"
            onClick={() => void signin({ email: 'a@test.dev', password: 'Password1!' })}
          >
            A로 로그인
          </button>
        </>
      );
    }

    render(
      <MemoryRouter>
        <AuthProvider>
          <IdentityControls />
          <SettingsPostsPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    await screen.findByRole('button', { name: 'A 카테고리 순서 이동' });
    fireEvent.keyDown(screen.getByRole('button', { name: 'A 카테고리 순서 이동' }), { key: 'ArrowDown' });
    await orderSeen;

    await userEvent.click(screen.getByRole('button', { name: 'B로 전환' }));
    await screen.findByRole('button', { name: 'B 카테고리 순서 이동' });
    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }));
    expect(await screen.findByText('블로그 정보를 확인한 뒤 카테고리를 불러올 수 있습니다.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'A로 로그인' }));
    await screen.findByRole('button', { name: 'A 카테고리 순서 이동' });

    await act(async () => {
      (releaseOrder as (() => void) | null)?.();
      await orderResponseParsed;
      // apiClient의 JSON 파싱과 SettingsPostsPage의 async continuation까지 flush한다.
      await Promise.resolve();
      await Promise.resolve();
    });
    await waitFor(() => expect(screen.queryByText('카테고리 순서를 저장했습니다.')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'A 카테고리 순서 이동' })).toBeInTheDocument();
  });

  it('A create 응답이 늦게 도착해도 B에서 입력한 새 이름을 지우지 않는다', async () => {
    const userB = {
      ...USER,
      id: 2,
      defaultBlog: { id: 2, title: 'B 별', urlSlug: 'b', isSetupCompleted: true },
    };
    const categoryA = [category(11, 'A 카테고리', 'GENERAL', 0)];
    const categoryB = [category(22, 'B 카테고리', 'GENERAL', 0)];
    const authUsers = [USER, userB];
    let authMeCalls = 0;
    let releaseCreate: (() => void) | null = null;
    let createStarted: (() => void) | null = null;
    let createParsed: (() => void) | null = null;
    const createGate = new Promise<void>((resolve) => {
      releaseCreate = resolve;
    });
    const createRequest = new Promise<void>((resolve) => {
      createStarted = resolve;
    });
    const createResponseParsed = new Promise<void>((resolve) => {
      createParsed = resolve;
    });
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(authUsers[Math.min(authMeCalls++, authUsers.length - 1)]);
      const blogMatch = url.match(/\/blogs\/(\d+)\/categories/);
      if (!blogMatch) throw new Error(`예상하지 못한 요청: ${url}`);
      const method = init?.method ?? 'GET';
      if (method === 'GET') {
        return envelope(page(blogMatch[1] === '1' ? categoryA : categoryB));
      }
      if (method === 'POST' && blogMatch[1] === '1') {
        createStarted?.();
        await createGate;
        return trackText(envelope(category(12, 'A 새 카테고리', 'GENERAL', 1), 201), createParsed!);
      }
      throw new Error(`예상하지 못한 mutation: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    function IdentityControls() {
      const { refreshUser } = useAuth();
      return (
        <button type="button" onClick={() => void refreshUser()}>
          B로 전환
        </button>
      );
    }

    render(
      <MemoryRouter>
        <AuthProvider>
          <IdentityControls />
          <SettingsPostsPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await screen.findByRole('button', { name: 'A 카테고리 순서 이동' });
    await user.type(screen.getByRole('textbox', { name: '새 카테고리 이름' }), 'A 입력');
    await user.click(screen.getByRole('button', { name: '＋ 추가' }));
    await createRequest;

    await user.click(screen.getByRole('button', { name: 'B로 전환' }));
    await screen.findByRole('button', { name: 'B 카테고리 순서 이동' });
    const input = screen.getByRole('textbox', { name: '새 카테고리 이름' });
    await user.clear(input);
    await user.type(input, 'B 새 입력');

    await act(async () => {
      (releaseCreate as (() => void) | null)?.();
      await createResponseParsed;
      // apiClient의 JSON 파싱과 SettingsPostsPage의 async continuation까지 flush한다.
      await Promise.resolve();
      await Promise.resolve();
    });
    await waitFor(() => expect(input).toHaveValue('B 새 입력'));
    expect(
      fetchMock.mock.calls.some(
        ([url, request]) =>
          String(url).includes('/blogs/2/categories') && request?.method === 'POST',
      ),
    ).toBe(false);
  });

  it('blog 전환 중에는 이전 blog의 loaded 목록으로 mutation을 시작하지 않는다', async () => {
    const userB = {
      ...USER,
      id: 2,
      defaultBlog: { id: 2, title: 'B 별', urlSlug: 'b', isSetupCompleted: true },
    };
    const authUsers = [USER, userB];
    let authMeCalls = 0;
    let releaseB: (() => void) | null = null;
    let bStarted: (() => void) | null = null;
    const bGate = new Promise<void>((resolve) => {
      releaseB = resolve;
    });
    const bRequest = new Promise<void>((resolve) => {
      bStarted = resolve;
    });
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(authUsers[Math.min(authMeCalls++, authUsers.length - 1)]);
      const blogMatch = url.match(/\/blogs\/(\d+)\/categories/);
      if (!blogMatch) throw new Error(`예상하지 못한 요청: ${url}`);
      if ((init?.method ?? 'GET') === 'GET') {
        if (blogMatch[1] === '2') {
          bStarted?.();
          await bGate;
        }
        return envelope(page([category(Number(blogMatch[1]) * 10, `${blogMatch[1]} 카테고리`, 'GENERAL', 0)]));
      }
      throw new Error(`전환 중 mutation이 시작됨: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    function IdentityControls() {
      const { refreshUser } = useAuth();
      return (
        <button type="button" onClick={() => void refreshUser()}>
          B로 전환
        </button>
      );
    }

    render(
      <MemoryRouter>
        <AuthProvider>
          <IdentityControls />
          <SettingsPostsPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await screen.findByRole('button', { name: '1 카테고리 순서 이동' });
    await user.click(screen.getByRole('button', { name: 'B로 전환' }));
    await bRequest;

    const addButton = screen.getByRole('button', { name: '＋ 추가' });
    expect(addButton).toBeDisabled();
    const form = screen.getByRole('textbox', { name: '새 카테고리 이름' }).closest('form');
    expect(form).not.toBeNull();
    fireEvent.submit(form as HTMLFormElement);
    expect(
      fetchMock.mock.calls.some(
        ([url, request]) => String(url).includes('/categories') && request?.method === 'POST',
      ),
    ).toBe(false);

    (releaseB as (() => void) | null)?.();
    await screen.findByRole('button', { name: '2 카테고리 순서 이동' });
    expect(addButton).toBeEnabled();
  });

  it('공개 패널은 blog 전환 중 이전 count를 숨기고 실패 시 0 대신 미확인 표시를 낸다', async () => {
    const aRoot = category(1, 'A 루트', 'GENERAL', 0);
    aRoot.postCount = 7;
    let releaseB: (() => void) | null = null;
    let bStarted: (() => void) | null = null;
    const bGate = new Promise<void>((resolve) => {
      releaseB = resolve;
    });
    const bRequest = new Promise<void>((resolve) => {
      bStarted = resolve;
    });
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/blogs/1/categories')) return envelope(page([aRoot]));
      if (url.includes('/blogs/2/categories')) {
        bStarted?.();
        await bGate;
        return failure(500, 'COMMON_500');
      }
      throw new Error(`예상하지 못한 요청: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter>
        <HeroBlogProvider>
          <BlogSwitchControl />
          <ScreenPanel kind="blog" />
        </HeroBlogProvider>
      </MemoryRouter>,
    );
    const totalRow = screen.getByText('전체 글').parentElement as HTMLElement;
    await waitFor(() => expect(within(totalRow).getByText('7')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'B 블로그로 전환' }));
    await bRequest;
    expect(within(totalRow).getByText('…')).toBeInTheDocument();
    (releaseB as (() => void) | null)?.();
    await waitFor(() => expect(within(totalRow).getByText('—')).toBeInTheDocument());
    expect(within(totalRow).queryByText('0')).not.toBeInTheDocument();
  });

  it('공개 패널은 같은 blog에서도 viewer identity가 바뀌면 count를 다시 읽는다', async () => {
    const root = category(1, '공개 루트', 'GENERAL', 0);
    let categoryReads = 0;
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/auth/signout')) return envelope(null);
      if (url.includes('/api/v1/blogs/1/categories')) {
        categoryReads += 1;
        const headers = init?.headers as Record<string, string> | undefined;
        root.postCount = headers?.Authorization ? 5 : 1;
        return envelope(page([root]));
      }
      throw new Error(`예상하지 못한 요청: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <MemoryRouter>
        <AuthProvider>
          <HeroBlogProvider>
            <PublicBlogSeed />
            <AuthViewerControls />
            <ScreenPanel kind="blog" />
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText('전체 글').parentElement).toHaveTextContent('5'));
    const readsBeforeLogout = categoryReads;
    await userEvent.click(screen.getByRole('button', { name: '공개 방문자로 전환' }));
    await waitFor(() => expect(screen.getByText('전체 글').parentElement).toHaveTextContent('1'));
    expect(categoryReads).toBeGreaterThan(readsBeforeLogout);
  });

  it('공개 블로그 패널은 includeDrafts=false 트리와 실제 count를 표시한다', async () => {
    const root = category(1, '공개 루트', 'GENERAL', 0);
    root.postCount = 2;
    root.children = [category(2, '공개 하위', 'GENERAL', 0, 1)];
    root.children[0].postCount = 3;
    const { fetchMock } = installCategoryApi([root]);

    render(
      <MemoryRouter>
        <HeroBlogProvider>
          <PublicBlogSeed />
          <ScreenPanel kind="blog" />
        </HeroBlogProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('공개 하위')).toBeInTheDocument();
    expect(screen.getByText('공개 루트')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('includeDrafts=false'))).toBe(true);
  });

  it('루트 전체 페이지를 모두 읽은 뒤에야 조작을 활성화한다', async () => {
    const first = [category(1, '첫 번째 루트', 'GENERAL', 0)];
    const second = [category(2, '마지막 루트', 'GENERAL', 1)];
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      const requestedPage = Number(new URL(url).searchParams.get('page') ?? '0');
      return envelope(page(requestedPage === 0 ? first : second, 2, 2, requestedPage));
    });
    vi.stubGlobal('fetch', fetchMock);

    renderSettings();

    expect(await screen.findByRole('button', { name: '마지막 루트 순서 이동' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/categories?page=')).length).toBe(2);
    expect(screen.getByRole('button', { name: '＋ 추가' })).toBeEnabled();
  }, 10000);

  it('하위 카테고리를 생성하고 GENERAL 이름을 인라인 수정한다', async () => {
    const { fetchMock } = installCategoryApi([category(1, '기본 루트', 'DEFAULT', 0)]);
    renderSettings();
    const user = userEvent.setup();

    await screen.findByRole('button', { name: '기본 루트 순서 이동' });
    const nameInput = screen.getByRole('textbox', { name: '새 카테고리 이름' });
    await user.type(nameInput, '하위 기록');
    await user.selectOptions(screen.getByRole('combobox', { name: '부모 카테고리' }), '1');
    await user.click(screen.getByRole('button', { name: '＋ 추가' }));
    expect(await screen.findByText('하위 기록')).toBeInTheDocument();

    const createdCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url).endsWith('/categories') && init?.method === 'POST',
    );
    expect(JSON.parse(String(createdCall?.[1]?.body))).toMatchObject({
      name: '하위 기록',
      parentId: 1,
      type: 'GENERAL',
    });

    await user.click(screen.getByRole('button', { name: '하위 기록 이름 변경' }));
    const editInput = screen.getByRole('textbox', { name: '하위 기록 이름 편집' });
    await user.clear(editInput);
    await user.type(editInput, '수정 기록');
    await user.keyboard('{Enter}');
    expect(await screen.findByText('수정 기록')).toBeInTheDocument();
  });

  it('GENERAL을 LOCKED로 바꿀 때 경고하고 DEFAULT·LOCKED 조작을 막는다', async () => {
    installCategoryApi([
      category(1, '기본', 'DEFAULT', 0),
      category(2, '일반', 'GENERAL', 1),
      category(3, '잠금', 'LOCKED', 2),
    ]);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderSettings();
    const user = userEvent.setup();
    await screen.findByRole('button', { name: '일반 순서 이동' });

    await user.selectOptions(screen.getByRole('combobox', { name: '일반 타입' }), 'LOCKED');
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('되돌릴 수 없습니다'));
    await screen.findByRole('button', { name: '잠금 순서 이동' });
    expect(screen.getByRole('combobox', { name: '기본 타입' })).toBeDisabled();
    expect(screen.getByRole('combobox', { name: '잠금 타입' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '기본 이름 변경' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '기본 삭제' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '잠금 이름 변경' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '잠금 삭제' })).toBeDisabled();

    const defaultItem = screen
      .getByRole('button', { name: '기본 순서 이동' })
      .closest('[data-category-id]') as HTMLElement;
    const defaultRow = defaultItem.firstElementChild as HTMLElement;
    expect(defaultRow).toHaveClass('gap-3', 'border-b', 'border-line', 'px-5', 'py-[13px]');
    expect(within(defaultRow).getByText('기본')).toHaveClass('text-sm', 'font-bold');
    expect(within(defaultRow).getByText('1개의 글')).toHaveClass('text-xs');
    expect(screen.getByRole('button', { name: '기본 순서 이동' })).toHaveClass('text-[15px]', 'text-shadow');

    const panel = screen
      .getByRole('heading', { level: 2, name: /카테고리 관리/ })
      .closest('section') as HTMLElement;
    expect(panel.querySelector('form')).toHaveClass('gap-2.5', 'px-5', 'py-4');
    expect(panel.querySelector('form')).not.toHaveClass('border-t-[3px]', 'bg-surface-raise');
    const caption = screen.getByText(/카테고리를 삭제하면 글은 '미분류'로 이동합니다/);
    expect(caption).toHaveClass('mt-2', 'px-1', 'text-xs', 'text-text-muted');
    expect(caption.closest('section')).toBeNull();
  });

  it('키보드와 native DnD는 sibling 전체 ID를 보내고 LOCKED 숫자 자리는 지킨다', async () => {
    const { fetchMock } = installCategoryApi([
      category(1, '기본', 'DEFAULT', 0),
      category(2, '일반', 'GENERAL', 1),
      category(3, '잠금', 'LOCKED', 2),
    ]);
    renderSettings();
    await screen.findByRole('button', { name: '잠금 순서 이동' });

    const defaultHandle = screen.getByRole('button', { name: '기본 순서 이동' });
    fireEvent.keyDown(defaultHandle, { key: 'ArrowDown' });
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([url, init]) => String(url).endsWith('/categories/order') && init?.method === 'PUT',
        ),
      ).toBe(true),
    );
    const orderCall = fetchMock.mock.calls.find(
      ([url, init]) => String(url).endsWith('/categories/order') && init?.method === 'PUT',
    );
    expect(JSON.parse(String(orderCall?.[1]?.body))).toEqual([2, 1, 3]);

    const generalRow = screen
      .getByRole('button', { name: '일반 순서 이동' })
      .closest('[data-category-id]') as HTMLElement;
    const lockedRow = screen
      .getByRole('button', { name: '잠금 순서 이동' })
      .closest('[data-category-id]') as HTMLElement;
    await waitFor(() => expect(screen.getByText('카테고리 순서를 저장했습니다.')).toBeInTheDocument());
    const dataTransfer = { setData: vi.fn(), effectAllowed: '' };
    fireEvent.dragStart(generalRow.firstElementChild as HTMLElement, { dataTransfer });
    expect(dataTransfer.setData).toHaveBeenCalledWith('text/plain', '2');
    expect(dataTransfer.effectAllowed).toBe('move');
    await new Promise((resolve) => setTimeout(resolve, 0));
    fireEvent.drop(lockedRow.firstElementChild as HTMLElement);
    expect(screen.getByRole('alert')).toHaveTextContent('잠금 카테고리의 숫자 순서는 변경할 수 없습니다');
  });

  it('CAT_007 충돌 시 최신 카테고리를 다시 읽고 오류를 표시한다', async () => {
    const { fetchMock } = installCategoryApi(
      [category(1, '기본', 'DEFAULT', 0), category(2, '일반', 'GENERAL', 1)],
      { conflictOnce: true },
    );
    renderSettings();
    await screen.findByRole('button', { name: '일반 순서 이동' });
    fireEvent.keyDown(screen.getByRole('button', { name: '기본 순서 이동' }), { key: 'ArrowDown' });

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('순서가 바뀌었습니다'));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/categories?page=')).length).toBeGreaterThan(1);
  });
});
