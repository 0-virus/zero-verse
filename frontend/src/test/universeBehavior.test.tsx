import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { SettingsUniversePage } from '../pages/SettingsUniversePage';
import { BlogPage } from '../pages/BlogPage';
import { AppShell } from '../components/layout/AppShell';
import { AuthProvider } from '../lib/authContext';
import { HeroBlogProvider } from '../lib/heroBlogContext';
import { resetApiClient } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';

const USER = {
  id: 1,
  email: 'viewer@test.dev',
  name: '뷰어',
  nickname: 'viewer',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '뷰어 블로그', urlSlug: 'viewer', isSetupCompleted: true },
};

function envelope(data: unknown, success = true, status = 200) {
  return {
    ok: success,
    status,
    text: async () =>
      JSON.stringify({
        success,
        data: success ? data : null,
        error: success ? null : { code: 'COMMON_500', message: '실패', details: [] },
        timestamp: '',
      }),
    json: async () => ({ success, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function page(items: unknown[]) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    hasNext: false,
    hasPrevious: false,
  };
}

function failure(code: string, status = 409) {
  return {
    ok: false,
    status,
    text: async () =>
      JSON.stringify({
        success: false,
        data: null,
        error: { code, message: code === 'UNI_005' ? '관계 상태 충돌' : '실패', details: [] },
        timestamp: '',
      }),
    json: async () => ({
      success: false,
      data: null,
      error: { code, message: code === 'UNI_005' ? '관계 상태 충돌' : '실패', details: [] },
      timestamp: '',
    }),
  } as unknown as Response;
}

function blogResponse(ownerId: number, slug: string) {
  return {
    id: ownerId,
    title: `${slug} 블로그`,
    urlSlug: slug,
    description: '소개',
    owner: { id: ownerId, nickname: `owner-${ownerId}`, profileImageUrl: null, bio: null },
  };
}

function snapshotFor(
  availability: 'AVAILABLE' | 'BLOCKED_BY_ME' | 'UNAVAILABLE',
  outgoing: { status: 'NONE' | 'PENDING' | 'ACCEPTED'; requestId: number | null },
  incoming: { status: 'NONE' | 'PENDING' | 'ACCEPTED'; requestId: number | null },
) {
  return {
    target: { id: 9, nickname: '관계 별', profileImageUrl: null, blogSlug: 'target' },
    availability,
    outgoing,
    incoming,
  };
}

function renderBlogWithFetch(fetchMock: ReturnType<typeof vi.fn>, initialEntries = ['/blog/target']) {
  vi.stubGlobal('fetch', fetchMock);
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <HeroBlogProvider>
          <AppShell user={USER}>
            <Routes>
              <Route path="/blog/:blogSlug" element={<BlogPage />} />
            </Routes>
          </AppShell>
        </HeroBlogProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function blogFetch(snapshot: unknown, mutationPath?: string, mutationMethod?: string) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
    if (url.includes('/auth/me')) return envelope(USER);
    if (url.includes('/blogs/slug/target/posts')) return envelope(page([]));
    if (url.includes('/blogs/slug/target')) return envelope(blogResponse(9, 'target'));
    if (url.includes('/universe/relationship/9')) return envelope(snapshot);
    if (mutationPath && url.includes(mutationPath) && (!mutationMethod || init?.method === mutationMethod)) {
      return envelope(null);
    }
    throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
  });
}

function guestBlogFetch() {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/auth/refresh')) return failure('AUTH_003', 401);
    if (url.includes('/blogs/slug/target/posts')) return envelope(page([]));
    if (url.includes('/blogs/slug/target')) return envelope(blogResponse(9, 'target'));
    throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
  });
}

function AccountSwitcher({ onSwitch }: { onSwitch: () => void }) {
  const auth = useAuth();
  return <button type="button" onClick={() => void auth.refreshUser().then(onSwitch)}>계정 전환</button>;
}

describe('M5 Universe 화면 행동', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/universe/requests/received')) {
        return envelope(
          page([
            {
              requestId: 91,
              user: { id: 9, nickname: '받은 별', profileImageUrl: null, blogSlug: 'received' },
              createdAt: '2026-11-07T00:00:00Z',
            },
          ]),
        );
      }
      if (url.includes('/universe/relationship/9')) {
        return envelope({
          target: { id: 9, nickname: '받은 별', profileImageUrl: null, blogSlug: 'received' },
          availability: 'AVAILABLE',
          outgoing: { status: 'NONE', requestId: null },
          incoming: { status: 'NONE', requestId: null },
        });
      }
      if (url.includes('/blogs/slug/received/posts')) {
        return envelope(page([]));
      }
      if (url.includes('/blogs/slug/')) {
        return envelope({
          id: 9,
          title: '받은 별 블로그',
          urlSlug: 'received',
          description: '소개',
          owner: { id: 9, nickname: '받은 별', profileImageUrl: null, bio: null },
        });
      }
      if (url.includes('/universe/requests/91/accept')) return envelope(null);
      if (url.includes('/universe/requests/sent')) return envelope(page([]));
      if (url.includes('/universe/following')) return envelope(page([]));
      if (url.includes('/universe/followers')) return envelope(page([]));
      if (url.includes('/universe/friends')) return envelope(page([]));
      if (url.includes('/universe/blocks')) return envelope(page([]));
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('여섯 목록을 독립 카드로 읽고 받은 신청 수락 뒤 canonical 목록을 다시 읽는다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/settings/universe']}>
        <AuthProvider>
          <SettingsUniversePage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('받은 별')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /받은 유니버스 신청/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /보낸 유니버스 신청/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /연결된 별/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /내가 발견한/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /나를 발견한/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^■ 차단/ })).toBeInTheDocument();

    const receivedCard = screen.getByRole('heading', { name: /받은 유니버스 신청/ }).closest('section');
    expect(receivedCard?.parentElement).toHaveClass('flex', 'flex-col', 'gap-5');

    await user.click(screen.getByRole('button', { name: '수락 ✦' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('관계 상태를 반영했습니다.'));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/requests/91/accept'))).toBe(true);
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/universe/requests/received')).length).toBeGreaterThan(1);
  });

  it('타인 블로그는 보호 snapshot의 NONE 상태에서 신청 액션을 보여준다', async () => {
    render(
      <MemoryRouter initialEntries={['/blog/received']}>
        <AuthProvider>
          <HeroBlogProvider>
            <AppShell user={USER}>
              <Routes>
                <Route path="/blog/:blogSlug" element={<BlogPage />} />
              </Routes>
            </AppShell>
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('button', { name: '✦ 유니버스 신청' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '✦ 유니버스 신청' })).toHaveAttribute('data-variant', 'neutral');
    expect(screen.getByRole('button', { name: '✦ 유니버스 신청' })).toHaveClass('px-4', 'py-2');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/universe/relationship/9'))).toBe(true);
  });

  it('비로그인 블로그도 정본 prefix CTA로 signin을 유도한다', async () => {
    const fetchMock = guestBlogFetch();
    renderBlogWithFetch(fetchMock);

    const action = await screen.findByRole('link', { name: '✦ 유니버스 신청' });
    expect(action).toHaveAttribute('href', '/signin');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/universe/relationship/'))).toBe(false);
  });
});

describe('M5 Blog hero 관계 snapshot 우선순위', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it.each([
    {
      name: 'BLOCKED_BY_ME는 해제',
      snapshot: snapshotFor('BLOCKED_BY_ME', { status: 'NONE', requestId: null }, { status: 'NONE', requestId: null }),
      label: '차단 해제',
      path: '/universe/blocks/9',
      method: 'DELETE',
    },
    {
      name: '양방향 BLOCKED 정규화도 해제',
      snapshot: snapshotFor('BLOCKED_BY_ME', { status: 'NONE', requestId: null }, { status: 'NONE', requestId: null }),
      label: '차단 해제',
      path: '/universe/blocks/9',
      method: 'DELETE',
    },
    {
      name: 'incoming PENDING은 incoming requestId로 수락',
      snapshot: snapshotFor('AVAILABLE', { status: 'PENDING', requestId: 222 }, { status: 'PENDING', requestId: 111 }),
      label: '신청 수락 ✦',
      path: '/universe/requests/111/accept',
      method: 'POST',
    },
    {
      name: 'outgoing PENDING은 outgoing requestId로 취소',
      snapshot: snapshotFor('AVAILABLE', { status: 'PENDING', requestId: 222 }, { status: 'NONE', requestId: null }),
      label: '신청 취소',
      path: '/universe/requests/222',
      method: 'DELETE',
    },
    {
      name: 'outgoing ACCEPTED는 관계 삭제',
      snapshot: snapshotFor('AVAILABLE', { status: 'ACCEPTED', requestId: null }, { status: 'NONE', requestId: null }),
      label: '연결 끊기',
      path: '/universe/9',
      method: 'DELETE',
    },
    {
      name: '양방향 ACCEPTED는 친구 라벨과 관계 삭제',
      snapshot: snapshotFor('AVAILABLE', { status: 'ACCEPTED', requestId: null }, { status: 'ACCEPTED', requestId: null }),
      label: '친구 · 연결 끊기',
      path: '/universe/9',
      method: 'DELETE',
    },
    {
      name: 'NONE은 신청',
      snapshot: snapshotFor('AVAILABLE', { status: 'NONE', requestId: null }, { status: 'NONE', requestId: null }),
      label: '✦ 유니버스 신청',
      path: '/universe/requests/9',
      method: 'POST',
    },
    {
      name: 'incoming ACCEPTED only는 맞신청',
      snapshot: snapshotFor('AVAILABLE', { status: 'NONE', requestId: null }, { status: 'ACCEPTED', requestId: null }),
      label: '✦ 유니버스 신청',
      path: '/universe/requests/9',
      method: 'POST',
    },
  ])('$name', async ({ snapshot, label, path, method }) => {
    const fetchMock = blogFetch(snapshot, path, method);
    renderBlogWithFetch(fetchMock);
    const action = await screen.findByRole('button', { name: label });
    await userEvent.setup().click(action);
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes(path) && init?.method === method)).toBe(true);
    });
  });

  it('UNAVAILABLE은 관계 방향을 노출하지 않고 disabled 액션을 표시한다', async () => {
    const fetchMock = blogFetch(
      snapshotFor('UNAVAILABLE', { status: 'NONE', requestId: null }, { status: 'NONE', requestId: null }),
    );
    renderBlogWithFetch(fetchMock);
    const action = await screen.findByRole('button', { name: '관계 이용 불가' });
    expect(action).toBeDisabled();
    expect(screen.queryByRole('button', { name: '차단 해제' })).not.toBeInTheDocument();
  });
});

describe('M5 stale snapshot guard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('블로그 owner가 바뀐 뒤 늦게 도착한 이전 snapshot이 새 hero 액션을 덮지 않는다', async () => {
    let resolveFirst: ((response: Response) => void) | null = null;
    const firstSnapshot = new Promise<Response>((resolve) => {
      resolveFirst = resolve;
    });
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/blogs/slug/first/posts')) return envelope(page([]));
      if (url.includes('/blogs/slug/second/posts')) return envelope(page([]));
      if (url.includes('/blogs/slug/first')) return envelope(blogResponse(9, 'first'));
      if (url.includes('/blogs/slug/second')) return envelope(blogResponse(10, 'second'));
      if (url.includes('/universe/relationship/9')) return firstSnapshot;
      if (url.includes('/universe/relationship/10')) {
        return envelope(snapshotFor('AVAILABLE', { status: 'NONE', requestId: null }, { status: 'NONE', requestId: null }));
      }
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    let navigateToSecond: (() => void) | null = null;
    function NavigationProbe() {
      const navigate = useNavigate();
      navigateToSecond = () => navigate('/blog/second');
      return <button type="button" onClick={() => navigate('/blog/second')}>두 번째 블로그</button>;
    }
    vi.stubGlobal('fetch', fetchMock);
    render(
      <MemoryRouter initialEntries={['/blog/first']}>
        <AuthProvider>
          <HeroBlogProvider>
            <AppShell user={USER}>
              <NavigationProbe />
              <Routes><Route path="/blog/:blogSlug" element={<BlogPage />} /></Routes>
            </AppShell>
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
    await screen.findByText('first 블로그');
    navigateToSecond!();
    expect(await screen.findByRole('button', { name: '✦ 유니버스 신청' })).toBeInTheDocument();
    resolveFirst!(envelope(snapshotFor('AVAILABLE', { status: 'ACCEPTED', requestId: null }, { status: 'ACCEPTED', requestId: null })));
    await waitFor(() => expect(screen.getByRole('button', { name: '✦ 유니버스 신청' })).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '친구 · 연결 끊기' })).not.toBeInTheDocument();
  });

  it('로그인 계정이 바뀐 뒤 이전 계정 snapshot 응답이 현재 액션을 덮지 않는다', async () => {
    let currentUser = USER;
    let resolveOld: ((response: Response) => void) | null = null;
    const oldSnapshot = new Promise<Response>((resolve) => { resolveOld = resolve; });
    const nextUser = { ...USER, id: 2, email: 'next@test.dev', nickname: 'next' };
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(currentUser);
      if (url.includes('/blogs/slug/target/posts')) return envelope(page([]));
      if (url.includes('/blogs/slug/target')) return envelope(blogResponse(9, 'target'));
      if (url.includes('/universe/relationship/9')) {
        return currentUser.id === 1
          ? oldSnapshot
          : envelope(snapshotFor('AVAILABLE', { status: 'PENDING', requestId: 333 }, { status: 'NONE', requestId: null }));
      }
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(
      <MemoryRouter initialEntries={['/blog/target']}>
        <AuthProvider>
          <HeroBlogProvider>
            <AppShell user={USER}>
              <AccountSwitcher onSwitch={() => {}} />
              <Routes><Route path="/blog/:blogSlug" element={<BlogPage />} /></Routes>
            </AppShell>
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
    await screen.findByText('target 블로그');
    currentUser = nextUser;
    await userEvent.setup().click(screen.getByRole('button', { name: '계정 전환' }));
    expect(await screen.findByRole('button', { name: '신청 취소' })).toBeInTheDocument();
    resolveOld!(envelope(snapshotFor('AVAILABLE', { status: 'ACCEPTED', requestId: null }, { status: 'ACCEPTED', requestId: null })));
    await waitFor(() => expect(screen.getByRole('button', { name: '신청 취소' })).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '친구 · 연결 끊기' })).not.toBeInTheDocument();
  });
});

describe('M5 SettingsUniverse mutation mapping', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('여섯 카드의 액션이 각 계약 endpoint로 매핑된다', async () => {
    const data: Record<string, unknown> = {
      received: page([{ requestId: 11, user: { id: 101, nickname: 'received', profileImageUrl: null, blogSlug: 'r' }, createdAt: '' }]),
      sent: page([{ requestId: 12, user: { id: 102, nickname: 'sent', profileImageUrl: null, blogSlug: 's' }, createdAt: '' }]),
      following: page([{ user: { id: 103, nickname: 'following', profileImageUrl: null, blogSlug: 'f' }, updatedAt: '' }]),
      followers: page([{ user: { id: 104, nickname: 'followers', profileImageUrl: null, blogSlug: 'fl' }, updatedAt: '' }]),
      friends: page([{ user: { id: 105, nickname: 'friends', profileImageUrl: null, blogSlug: 'fr' }, updatedAt: '' }]),
      blocks: page([{ user: { id: 106, nickname: 'blocks', profileImageUrl: null, blogSlug: 'b' }, updatedAt: '' }]),
    };
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/universe/requests/received')) return envelope(data.received);
      if (url.includes('/universe/requests/sent')) return envelope(data.sent);
      if (url.includes('/universe/following')) return envelope(data.following);
      if (url.includes('/universe/followers')) return envelope(data.followers);
      if (url.includes('/universe/friends')) return envelope(data.friends);
      if (url.includes('/universe/blocks/')) return envelope(null);
      if (url.includes('/universe/blocks')) return envelope(data.blocks);
      if (url.includes('/universe/requests/11/accept')) return envelope(null);
      if (url.includes('/universe/requests/11/reject')) return envelope(null);
      if (url.includes('/universe/requests/12')) return envelope(null);
      if (url.includes('/universe/103')) return envelope(null);
      if (url.includes('/universe/104')) return envelope(null);
      if (url.includes('/universe/105')) return envelope(null);
      if (url.includes('/universe/106')) return envelope(null);
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter><AuthProvider><SettingsUniversePage /></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('received')).toBeInTheDocument();
    const card = (title: RegExp) => screen.getByRole('heading', { name: title }).closest('section') as HTMLElement;
    const click = async (title: RegExp, button: string) => {
      await userEvent.setup().click(within(card(title)).getByRole('button', { name: button }));
    };
    expect(within(card(/받은 유니버스 신청/)).getByRole('button', { name: '수락 ✦' })).toHaveAttribute('data-variant', 'accentSm');
    expect(within(card(/받은 유니버스 신청/)).getByRole('button', { name: '거절' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/보낸 유니버스 신청/)).getByRole('button', { name: '취소' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/내가 발견한/)).getByRole('button', { name: '연결 끊기' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/내가 발견한/)).getByRole('button', { name: '연결 끊기' })).toHaveClass('px-3', 'py-[5px]', 'font-normal');
    expect(within(card(/내가 발견한/)).getByRole('button', { name: '차단' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/나를 발견한/)).getByRole('button', { name: '차단' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/연결된 별/)).getByRole('button', { name: '연결 끊기' })).toHaveAttribute('data-variant', 'inert');
    expect(within(card(/연결된 별/)).getByRole('button', { name: '연결 끊기' })).toHaveClass('px-3', 'py-[5px]', 'font-normal');
    expect(within(card(/연결된 별/)).getByRole('button', { name: '차단' })).toHaveAttribute('data-variant', 'inert');
    await click(/받은 유니버스 신청/, '수락 ✦');
    await click(/보낸 유니버스 신청/, '취소');
    await click(/내가 발견한/, '연결 끊기');
    await click(/나를 발견한/, '차단');
    await click(/연결된 별/, '연결 끊기');
    await click(/^■ 차단/, '해제');
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/requests/11/accept') && init?.method === 'POST')).toBe(true);
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/requests/12') && init?.method === 'DELETE')).toBe(true);
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/universe/103') && init?.method === 'DELETE')).toBe(true);
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/blocks/104') && init?.method === 'POST')).toBe(true);
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/universe/105') && init?.method === 'DELETE')).toBe(true);
      expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/blocks/106') && init?.method === 'DELETE')).toBe(true);
    });
  });

  it('UNI_005 충돌은 canonical 목록을 재조회한다', async () => {
    let conflict = true;
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/universe/requests/received') && init?.method !== 'POST') {
        return conflict ? envelope(page([{ requestId: 11, user: { id: 101, nickname: '충돌 별', profileImageUrl: null, blogSlug: 'r' }, createdAt: '' }])) : envelope(page([]));
      }
      if (url.includes('/universe/requests/11/accept')) {
        conflict = false;
        return failure('UNI_005');
      }
      if (url.includes('/universe/requests/sent')) return envelope(page([]));
      if (url.includes('/universe/following')) return envelope(page([]));
      if (url.includes('/universe/followers')) return envelope(page([]));
      if (url.includes('/universe/friends')) return envelope(page([]));
      if (url.includes('/universe/blocks')) return envelope(page([]));
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter><AuthProvider><SettingsUniversePage /></AuthProvider></MemoryRouter>);
    await userEvent.setup().click(await screen.findByRole('button', { name: '수락 ✦' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('최신 목록을 불러왔습니다'));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/universe/requests/received')).length).toBeGreaterThan(1);
  });

  it('검색과 페이지 요청은 카드별로 독립된다', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.includes('/auth/refresh')) return envelope({ accessToken: 'token' });
      if (url.includes('/auth/me')) return envelope(USER);
      if (url.includes('/universe/requests/sent')) {
        return envelope({ items: [], page: url.includes('page=1') ? 1 : 0, size: 20, totalElements: 21, totalPages: 2, hasNext: !url.includes('page=1'), hasPrevious: url.includes('page=1') });
      }
      if (url.includes('/universe/requests/received')) return envelope(page([]));
      if (url.includes('/universe/following')) return envelope(page([]));
      if (url.includes('/universe/followers')) return envelope(page([]));
      if (url.includes('/universe/friends')) return envelope(page([]));
      if (url.includes('/universe/blocks')) return envelope(page([]));
      throw new Error(`예상하지 못한 요청: ${init?.method ?? 'GET'} ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<MemoryRouter><AuthProvider><SettingsUniversePage /></AuthProvider></MemoryRouter>);
    await screen.findByRole('heading', { name: /보낸 유니버스 신청/ });
    const sent = (screen.getByRole('heading', { name: /보낸 유니버스 신청/ }).closest('section') as HTMLElement);
    const received = (screen.getByRole('heading', { name: /받은 유니버스 신청/ }).closest('section') as HTMLElement);
    const search = within(received).getByRole('searchbox');
    await userEvent.setup().type(search, 'neo');
    await userEvent.setup().keyboard('{Enter}');
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/requests/received?page=0&size=20&nickname=neo'))).toBe(true));
    await userEvent.setup().click(within(sent).getByRole('button', { name: '다음 페이지' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/requests/sent?page=1&size=20'))).toBe(true));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/requests/received?page=1'))).toBe(false);
  });
});
