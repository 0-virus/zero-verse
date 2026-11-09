import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { MainPage } from '../pages/MainPage';
import { RightPanel } from '../components/layout/RightPanel';
import { useOptionalAuth } from '../lib/authContext';
import { listUniverse } from '../features/universe/universeApi';

vi.mock('../lib/authContext', () => ({
  useOptionalAuth: vi.fn(),
}));

vi.mock('../features/universe/universeApi', () => ({
  listUniverse: vi.fn(),
}));

const mockUseOptionalAuth = vi.mocked(useOptionalAuth);
const mockListUniverse = vi.mocked(listUniverse);

const AUTH_USER = {
  id: 7,
  email: 'seven@zeroverse.test',
  name: '세븐',
  nickname: '세븐별',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: {
    id: 17,
    title: '세븐별의 기록',
    urlSlug: 'seven',
    isSetupCompleted: true,
  },
};

const feedPage = (overrides: Record<string, unknown> = {}) => ({
  items: [
    {
      id: 91,
      blogId: 17,
      blogSlug: 'seven',
      blogTitle: '세븐별의 기록',
      author: { id: 12, nickname: '궤도역학', profileImageUrl: null },
      category: { id: 3, name: '백엔드' },
      title: '서버가 보낸 신호',
      thumbnailUrl: null,
      visibility: 'PUBLIC',
      viewCount: 12,
      publishedAt: '2026-11-09T00:00:00Z',
      createdAt: '2026-11-09T00:00:00Z',
      updatedAt: '2026-11-09T00:00:00Z',
      tags: ['Spring'],
      excerpt: '서버 집계 발췌',
      commentCount: 4,
      likeCount: 8,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
  ...overrides,
});

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderMain(initialEntry = '/') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <MainPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe('MainPage M7 피드 행동', () => {
  beforeEach(() => {
    mockUseOptionalAuth.mockReturnValue(null);
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('guest는 public API의 카드·서버 total·상호작용 집계를 렌더한다', async () => {
    const get = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ success: true, data: feedPage({ totalElements: 17, totalPages: 1 }), error: null, timestamp: '' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    renderMain();

    expect(await screen.findByRole('heading', { level: 2, name: '서버가 보낸 신호' })).toBeInTheDocument();
    expect(screen.getByText('17개의 신호')).toBeInTheDocument();
    expect(screen.getByText('댓글 4 · 좋아요 8')).toBeInTheDocument();
    expect(get).toHaveBeenCalledTimes(1);
    expect(String(get.mock.calls[0][0])).toContain('/api/v1/feed/public');
  });

  it('결과가 있지만 요청 page가 범위를 넘으면 빈 상태와 유효 page 복귀용 pagination을 유지한다', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          data: feedPage({ items: [], page: 3, totalElements: 3, totalPages: 1 }),
          error: null,
          timestamp: '',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    renderMain('/?page=3');

    expect(await screen.findByText('이 페이지에는 도착한 신호가 없습니다. 다른 페이지를 선택해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '1 페이지' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/?page=0');
  });

  it('guest가 친구 탭을 선택하면 보호 API 없이 로그인 안내를 보여준다', async () => {
    const user = userEvent.setup();
    const get = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: feedPage(), error: null, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    renderMain();
    await screen.findByRole('heading', { level: 2, name: '서버가 보낸 신호' });

    await user.click(screen.getByRole('tab', { name: '친구' }));

    expect(screen.getByText('친구 피드는 로그인 후 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인하기' })).toHaveAttribute('href', '/signin');
    expect(get).toHaveBeenCalledTimes(1);
    expect(String(get.mock.calls[0][0])).not.toContain('/api/v1/feed/universe');
  });

  it('인증 사용자의 following 탭은 friends canonical URL과 안내 문구를 함께 사용한다', async () => {
    const user = userEvent.setup();
    mockUseOptionalAuth.mockReturnValue({
      user: AUTH_USER,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout: vi.fn(),
      refreshUser: vi.fn(),
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: feedPage(), error: null, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    renderMain('/?relation=friend');
    await screen.findByRole('heading', { level: 2, name: '서버가 보낸 신호' });
    expect(screen.getByTestId('location')).toHaveTextContent('/?relation=friends');

    await user.click(screen.getByRole('tab', { name: '내가 발견한' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/?relation=following');
    expect(screen.getByText('친구를 제외한 내가 발견한 사람의 글입니다.')).toBeInTheDocument();
  });

  it('카드가 네이티브 글 상세 링크로 키보드 포커스를 받는다', async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: true, data: feedPage(), error: null, timestamp: '' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    renderMain();
    const card = await screen.findByRole('link', { name: /서버가 보낸 신호/ });
    card.focus();
    await user.tab({ shift: true });
    await user.tab();
    expect(card).toHaveAttribute('href', '/blog/seven/91');
    expect(card).toHaveFocus();
  });

  it('RightPanel은 기존 세 목록의 page0 size1 total을 독립적으로 표시한다', async () => {
    mockUseOptionalAuth.mockReturnValue({
      user: AUTH_USER,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout: vi.fn(),
      refreshUser: vi.fn(),
    });
    mockListUniverse.mockImplementation(async (kind) => ({
      items: [],
      page: 0,
      size: 1,
      totalElements: kind === 'friends' ? 12 : kind === 'following' ? 34 : 21,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    }));

    render(
      <MemoryRouter>
        <RightPanel blog={AUTH_USER.defaultBlog} />
      </MemoryRouter>,
    );

    const stats = await screen.findByRole('heading', { name: /유니버스 현황/ });
    expect(within(stats.parentElement?.parentElement as HTMLElement).getByText('12')).toBeInTheDocument();
    expect(screen.getByText('34')).toBeInTheDocument();
    expect(screen.getByText('21')).toBeInTheDocument();
    expect(mockListUniverse).toHaveBeenCalledTimes(3);
    expect(mockListUniverse).toHaveBeenCalledWith('friends', { page: 0, size: 1 });
    expect(mockListUniverse).toHaveBeenCalledWith('following', { page: 0, size: 1 });
    expect(mockListUniverse).toHaveBeenCalledWith('followers', { page: 0, size: 1 });
  });

  it('RightPanel은 계정이 바뀐 첫 렌더에서 이전 계정 통계를 숨기고 새 응답만 표시한다', async () => {
    let currentAuth = {
      user: AUTH_USER,
      isLoading: false,
      isAuthenticated: true,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout: vi.fn(),
      refreshUser: vi.fn(),
    };
    const nextStats = deferred<{ items: never[]; page: number; size: number; totalElements: number; totalPages: number; hasNext: boolean; hasPrevious: boolean }>();
    let phase: 'first' | 'next' = 'first';
    mockUseOptionalAuth.mockImplementation(() => currentAuth);
    mockListUniverse.mockImplementation(async (kind) => {
      if (phase === 'next') return nextStats.promise;
      return {
        items: [],
        page: 0,
        size: 1,
        totalElements: kind === 'friends' ? 12 : kind === 'following' ? 34 : 21,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      };
    });

    const { rerender } = render(
      <MemoryRouter>
        <RightPanel blog={AUTH_USER.defaultBlog} />
      </MemoryRouter>,
    );
    expect(await screen.findByText('12')).toBeInTheDocument();

    phase = 'next';
    currentAuth = { ...currentAuth, user: { ...AUTH_USER, id: 8 } };
    rerender(
      <MemoryRouter>
        <RightPanel blog={AUTH_USER.defaultBlog} />
      </MemoryRouter>,
    );
    expect(screen.queryByText('12')).not.toBeInTheDocument();
    expect(screen.getAllByText('…')).toHaveLength(3);

    nextStats.resolve({ items: [], page: 0, size: 1, totalElements: 99, totalPages: 1, hasNext: false, hasPrevious: false });
    expect((await screen.findAllByText('99'))).toHaveLength(3);
  });

  it('인증 준비 중에는 guest 피드를 호출하지 않는다', () => {
    mockUseOptionalAuth.mockReturnValue({
      user: null,
      isLoading: true,
      isAuthenticated: false,
      sessionExpired: false,
      signin: vi.fn(),
      register: vi.fn(),
      signout: vi.fn(),
      refreshUser: vi.fn(),
    });
    const get = vi.spyOn(globalThis, 'fetch');
    renderMain();
    expect(get).not.toHaveBeenCalled();
    expect(screen.getByText('인증 상태를 확인하는 중...')).toBeInTheDocument();
  });
});
