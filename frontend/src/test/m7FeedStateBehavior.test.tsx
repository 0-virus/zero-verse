import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useSearchParams } from 'react-router-dom';
import { MainPage } from '../pages/MainPage';
import { useOptionalAuth } from '../lib/authContext';
import { getPublicFeed, getUniverseFeed } from '../features/feed/feedApi';
import { ApiRequestError } from '../lib/apiClient';

vi.mock('../lib/authContext', () => ({
  useOptionalAuth: vi.fn(),
}));

vi.mock('../features/feed/feedApi', () => ({
  getPublicFeed: vi.fn(),
  getUniverseFeed: vi.fn(),
  normalizeDiscoveryKeyword: (value: string) => {
    const normalized = value.trim().replace(/\s+/gu, ' ');
    if (normalized.length > 100) throw new Error('검색어는 UTF-16 기준 100자 이하로 입력해 주세요.');
    return normalized;
  },
}));

const mockUseOptionalAuth = vi.mocked(useOptionalAuth);
const mockGetPublicFeed = vi.mocked(getPublicFeed);
const mockGetUniverseFeed = vi.mocked(getUniverseFeed);

const AUTH_USER = {
  id: 7,
  email: 'seven@zeroverse.test',
  name: '세븐',
  nickname: '세븐별',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 17, title: '세븐별의 기록', urlSlug: 'seven', isSetupCompleted: true },
};

function authFor(id: number) {
  return {
    user: { ...AUTH_USER, id },
    isLoading: false,
    isAuthenticated: true,
    sessionExpired: false,
    signin: vi.fn(),
    register: vi.fn(),
    signout: vi.fn(),
    refreshUser: vi.fn(),
  };
}

function feedPage(title = '신호') {
  return {
    items: [
      {
        id: title === '신호' ? 1 : 2,
        blogId: 17,
        blogSlug: 'seven',
        blogTitle: '세븐별의 기록',
        author: { id: 12, nickname: '궤도역학', profileImageUrl: null },
        category: { id: 3, name: '백엔드' },
        title,
        thumbnailUrl: null,
        visibility: 'PUBLIC' as const,
        viewCount: 0,
        publishedAt: '2026-11-09T00:00:00Z',
        createdAt: '2026-11-09T00:00:00Z',
        updatedAt: '2026-11-09T00:00:00Z',
        tags: [],
        excerpt: 'plain text',
        commentCount: 1,
        likeCount: 2,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  };
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

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function SetQueryButton({ search }: { search: string }) {
  const [, setSearchParams] = useSearchParams();
  return (
    <button type="button" onClick={() => setSearchParams(new URLSearchParams(search))}>
      query 변경
    </button>
  );
}

function renderMain(initialEntry = '/') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <MainPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('MainPage M7 state boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseOptionalAuth.mockReturnValue(null);
  });

  afterEach(() => vi.restoreAllMocks());

  it('keyword 제출과 관계 탭 변경은 모두 URL page=0으로 초기화한다', async () => {
    const user = userEvent.setup();
    mockUseOptionalAuth.mockReturnValue(authFor(7));
    mockGetUniverseFeed.mockResolvedValue(feedPage());
    renderMain('/?relation=friends&page=3');
    await screen.findByRole('heading', { level: 2, name: '신호' });

    const keyword = screen.getByRole('searchbox', { name: '피드 검색' });
    await user.type(keyword, '  hello   world  ');
    await user.click(screen.getByRole('button', { name: '검색' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/?relation=friends&page=0&keyword=hello+world');
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenLastCalledWith({
      relation: 'friends', keyword: 'hello world', page: 0, size: 20, sort: 'latest',
    }));

    await user.click(screen.getByRole('tab', { name: '내가 발견한' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/?relation=following&page=0&keyword=hello+world');
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenLastCalledWith({
      relation: 'following', keyword: 'hello world', page: 0, size: 20, sort: 'latest',
    }));
  });

  it('계정 전환 뒤 늦은 이전 resolve는 새 계정 결과를 덮지 않는다', async () => {
    let currentAuth = authFor(7);
    mockUseOptionalAuth.mockImplementation(() => currentAuth);
    const oldRequest = deferred<ReturnType<typeof feedPage>>();
    const newRequest = deferred<ReturnType<typeof feedPage>>();
    mockGetUniverseFeed.mockImplementationOnce(() => oldRequest.promise).mockImplementationOnce(() => newRequest.promise);
    const rendered = renderMain('/');
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(1));

    currentAuth = authFor(8);
    rendered.rerender(
      <MemoryRouter initialEntries={['/']}>
        <MainPage />
        <LocationProbe />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(2));
    newRequest.resolve(feedPage('새 계정 신호'));
    expect(await screen.findByRole('heading', { level: 2, name: '새 계정 신호' })).toBeInTheDocument();

    oldRequest.resolve(feedPage('이전 계정 신호'));
    await waitFor(() => expect(screen.queryByRole('heading', { level: 2, name: '이전 계정 신호' })).not.toBeInTheDocument());
  });

  it('관계 탭 전환 뒤 늦은 이전 reject는 새 결과 오류를 오염시키지 않는다', async () => {
    const user = userEvent.setup();
    mockUseOptionalAuth.mockReturnValue(authFor(7));
    const oldRequest = deferred<ReturnType<typeof feedPage>>();
    const nextRequest = deferred<ReturnType<typeof feedPage>>();
    mockGetUniverseFeed.mockImplementationOnce(() => oldRequest.promise).mockImplementationOnce(() => nextRequest.promise);
    renderMain('/');
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('tab', { name: '내가 발견한' }));
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(2));
    nextRequest.resolve(feedPage('following 신호'));
    expect(await screen.findByRole('heading', { level: 2, name: 'following 신호' })).toBeInTheDocument();

    oldRequest.reject(new Error('old request failed'));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { level: 2, name: 'following 신호' })).toBeInTheDocument();
  });

  it('page 전환 뒤 늦은 이전 resolve는 새 page 결과만 표시한다', async () => {
    const user = userEvent.setup();
    mockUseOptionalAuth.mockReturnValue(authFor(7));
    const oldRequest = deferred<ReturnType<typeof feedPage>>();
    const nextRequest = deferred<ReturnType<typeof feedPage>>();
    mockGetUniverseFeed.mockImplementationOnce(() => oldRequest.promise).mockImplementationOnce(() => nextRequest.promise);
    render(
      <MemoryRouter initialEntries={['/?page=0']}>
        <MainPage />
        <SetQueryButton search="?page=1" />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: 'query 변경' }));
    await waitFor(() => expect(mockGetUniverseFeed).toHaveBeenCalledTimes(2));
    nextRequest.resolve(feedPage('page 1 신호'));
    expect(await screen.findByRole('heading', { level: 2, name: 'page 1 신호' })).toBeInTheDocument();

    oldRequest.resolve(feedPage('page 0 신호'));
    await waitFor(() => expect(screen.queryByRole('heading', { level: 2, name: 'page 0 신호' })).not.toBeInTheDocument());
  });

  it.each([
    ['AUTH_002', '로그인 상태를 확인하지 못했습니다. 다시 접속해 주세요.'],
    ['AUTH_004', '로그인 상태를 확인하지 못했습니다. 다시 접속해 주세요.'],
    ['USER_001', '사용자 정보를 확인하지 못했습니다. 다시 시도해 주세요.'],
  ] as const)('확정 %s 오류는 결과를 비우고 retry로 재조회한다', async (code, message) => {
    const user = userEvent.setup();
    const error = new ApiRequestError(401, { code, message: 'server error', details: [] });
    mockGetPublicFeed.mockRejectedValueOnce(error).mockResolvedValueOnce(feedPage('재시도 성공'));
    renderMain('/');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('heading', { level: 2, name: '신호' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { level: 2, name: '재시도 성공' })).toBeInTheDocument();
  });
});
