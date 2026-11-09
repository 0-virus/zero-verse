import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { SearchPage } from '../pages/SearchPage';
import { useOptionalAuth } from '../lib/authContext';
import { search } from '../features/search/searchApi';
import { ApiRequestError } from '../lib/apiClient';

vi.mock('../lib/authContext', () => ({
  useOptionalAuth: vi.fn(),
}));

vi.mock('../features/search/searchApi', () => ({
  search: vi.fn(),
  normalizeSearchKeyword: (value: string) => value.trim().replace(/\s+/gu, ' '),
}));

const mockUseOptionalAuth = vi.mocked(useOptionalAuth);
const mockSearch = vi.mocked(search);

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

function searchResponse(title = '검색 신호') {
  return {
    items: [
      {
        id: title === '검색 신호' ? 1 : 2,
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
        excerpt: '검색 결과 발췌',
        commentCount: 1,
        likeCount: 2,
        matchedTypes: ['POST' as const],
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
    counts: { ALL: 1, POST: 1, BLOG: 0, USER: 0, TAG: 0 },
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

function BackButton() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(-1)}>뒤로</button>;
}

function renderSearch(initialEntries: string[], initialIndex?: number) {
  return render(
    <MemoryRouter initialEntries={initialEntries} initialIndex={initialIndex}>
      <SearchPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('SearchPage M7 state boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseOptionalAuth.mockReturnValue(authFor(7));
  });

  afterEach(() => vi.restoreAllMocks());

  it('인증 준비 중에는 guest 검색을 호출하지 않는다', () => {
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
    renderSearch(['/search?keyword=QueryDSL']);
    expect(mockSearch).not.toHaveBeenCalled();
    expect(screen.getByText('인증 상태를 확인하는 중...')).toBeInTheDocument();
  });

  it('계정 전환 뒤 늦은 이전 resolve는 새 계정 결과를 덮지 않는다', async () => {
    let currentAuth = authFor(7);
    mockUseOptionalAuth.mockImplementation(() => currentAuth);
    const oldRequest = deferred<ReturnType<typeof searchResponse>>();
    const newRequest = deferred<ReturnType<typeof searchResponse>>();
    mockSearch.mockImplementationOnce(() => oldRequest.promise).mockImplementationOnce(() => newRequest.promise);
    const rendered = renderSearch(['/search?keyword=old']);
    await waitFor(() => expect(mockSearch).toHaveBeenCalledTimes(1));

    currentAuth = authFor(8);
    rendered.rerender(
      <MemoryRouter initialEntries={['/search?keyword=old']}>
        <SearchPage />
        <LocationProbe />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mockSearch).toHaveBeenCalledTimes(2));
    newRequest.resolve(searchResponse('새 계정 검색'));
    expect(await screen.findByRole('heading', { level: 2, name: '새 계정 검색' })).toBeInTheDocument();

    oldRequest.resolve(searchResponse('이전 계정 검색'));
    await waitFor(() => expect(screen.queryByRole('heading', { level: 2, name: '이전 계정 검색' })).not.toBeInTheDocument());
  });

  it('계정 전환 뒤 늦은 이전 reject는 새 계정 오류 상태를 오염시키지 않는다', async () => {
    let currentAuth = authFor(7);
    mockUseOptionalAuth.mockImplementation(() => currentAuth);
    const oldRequest = deferred<ReturnType<typeof searchResponse>>();
    const newRequest = deferred<ReturnType<typeof searchResponse>>();
    mockSearch.mockImplementationOnce(() => oldRequest.promise).mockImplementationOnce(() => newRequest.promise);
    const rendered = renderSearch(['/search?keyword=old']);
    await waitFor(() => expect(mockSearch).toHaveBeenCalledTimes(1));

    currentAuth = authFor(8);
    rendered.rerender(
      <MemoryRouter initialEntries={['/search?keyword=old']}>
        <SearchPage />
        <LocationProbe />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mockSearch).toHaveBeenCalledTimes(2));
    newRequest.resolve(searchResponse('새 계정 검색'));
    expect(await screen.findByRole('heading', { level: 2, name: '새 계정 검색' })).toBeInTheDocument();

    oldRequest.reject(new Error('old search failed'));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('back으로 URL page를 복원하면 복원 page를 다시 검색한다', async () => {
    const user = userEvent.setup();
    mockSearch.mockResolvedValue(searchResponse());
    render(
      <MemoryRouter initialEntries={['/search?keyword=QueryDSL&page=0', '/search?keyword=QueryDSL&page=1']} initialIndex={1}>
        <SearchPage />
        <LocationProbe />
        <BackButton />
      </MemoryRouter>,
    );
    await screen.findByRole('heading', { level: 2, name: '검색 신호' });
    expect(mockSearch).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));

    await user.click(screen.getByRole('button', { name: '뒤로' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/search?keyword=QueryDSL&page=0');
    await waitFor(() => expect(mockSearch).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })));
  });

  it('transient 오류는 결과를 비우고 retry로 재조회한다', async () => {
    const user = userEvent.setup();
    mockSearch.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(searchResponse('검색 재시도 성공'));
    renderSearch(['/search?keyword=QueryDSL']);

    expect(await screen.findByRole('alert')).toHaveTextContent('network down');
    expect(screen.queryByRole('heading', { level: 2, name: '검색 신호' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { level: 2, name: '검색 재시도 성공' })).toBeInTheDocument();
  });

  it('확정 AUTH_004 오류는 이전 검색 결과를 폐기하고 retry할 수 있다', async () => {
    const user = userEvent.setup();
    mockSearch
      .mockRejectedValueOnce(new ApiRequestError(401, { code: 'AUTH_004', message: '세션 없음', details: [] }))
      .mockResolvedValueOnce(searchResponse('AUTH 재시도 성공'));
    renderSearch(['/search?keyword=QueryDSL']);

    expect(await screen.findByRole('alert')).toHaveTextContent('이전 검색 결과를 폐기했습니다.');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { level: 2, name: 'AUTH 재시도 성공' })).toBeInTheDocument();
  });
});
