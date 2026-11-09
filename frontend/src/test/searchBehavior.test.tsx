import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useEffect } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useSearchParams } from 'react-router-dom';
import { SearchPage } from '../pages/SearchPage';
import { TopBar } from '../components/layout/TopBar';
import { useOptionalAuth } from '../lib/authContext';
import { search } from '../features/search/searchApi';

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

const searchResponse = {
  items: [
    {
      id: 44,
      blogId: 17,
      blogSlug: 'seven',
      blogTitle: '세븐별의 기록',
      author: { id: 7, nickname: '세븐별', profileImageUrl: null },
      category: { id: 3, name: '백엔드' },
      title: 'QueryDSL 초안',
      thumbnailUrl: null,
      visibility: 'PRIVATE' as const,
      viewCount: 0,
      publishedAt: null,
      createdAt: '2026-11-09T00:00:00Z',
      updatedAt: '2026-11-09T00:00:00Z',
      tags: ['QueryDSL'],
      excerpt: '검색용 plain text 스니펫',
      commentCount: 0,
      likeCount: 0,
      matchedTypes: ['POST' as const, 'TAG' as const],
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
  counts: { ALL: 3, POST: 2, BLOG: 1, USER: 1, TAG: 2 },
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function QueryChanger({ keyword }: { keyword: string }) {
  const [, setSearchParams] = useSearchParams();
  useEffect(() => {
    setSearchParams({ keyword });
  }, [keyword, setSearchParams]);
  return null;
}

function renderSearch(initialEntry = '/search?keyword=QueryDSL') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <SearchPage />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('SearchPage·TopBar M7 행동', () => {
  beforeEach(() => {
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
    vi.clearAllMocks();
  });

  afterEach(() => vi.restoreAllMocks());

  it('서버 counts·matchedTypes를 5탭과 게시글 중심 카드로 렌더하고 owner draft는 edit URL로 연결한다', async () => {
    mockSearch.mockResolvedValue(searchResponse);
    renderSearch();

    expect(await screen.findByRole('heading', { level: 1, name: "'QueryDSL' 검색 결과 3" })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '전체 3' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '글 2' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '블로그 1' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '사용자 1' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '태그 2' })).toBeInTheDocument();
    expect(screen.getByText('블로그·사용자·태그 조건에 맞는 글을 검색합니다.')).toBeInTheDocument();
    expect(screen.getByText('전체 매칭: 글 · 태그')).toBeInTheDocument();
    expect(screen.getByText('임시저장')).toBeInTheDocument();
    const draftLink = screen.getByRole('link', { name: /QueryDSL 초안/ });
    expect(draftLink).toHaveAttribute('href', '/edit/44');
    expect(draftLink.querySelector('article')).toHaveClass('px-5', 'py-4');
    expect(screen.getByRole('heading', { level: 2, name: 'QueryDSL 초안' })).toHaveClass('text-[16.5px]');
    expect(screen.getByText('검색용 plain text 스니펫')).toHaveClass('text-[13px]', 'leading-[1.6]', 'text-text-body');
  });

  it('빈 입력에서는 API를 호출하지 않고 입력 안내를 표시한다', () => {
    renderSearch('/search');
    expect(mockSearch).not.toHaveBeenCalled();
    expect(screen.getByText('검색어를 입력하면 유니버스의 글을 찾을 수 있습니다.')).toBeInTheDocument();
  });

  it('검색 탭 변경은 URL type과 page0을 갱신하고 같은 검색 API를 다시 호출한다', async () => {
    const user = userEvent.setup();
    mockSearch.mockResolvedValue(searchResponse);
    renderSearch('/search?keyword=QueryDSL&type=ALL&page=2');
    await screen.findByRole('heading', { level: 1, name: "'QueryDSL' 검색 결과 3" });

    await user.click(screen.getByRole('tab', { name: '글 2' }));

    expect(screen.getByTestId('location')).toHaveTextContent('/search?keyword=QueryDSL&type=POST&page=0');
    await waitFor(() => expect(mockSearch).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'POST', page: 0 })));
  });

  it('결과가 있지만 요청 page가 범위를 넘으면 빈 상태와 유효 page 복귀용 pagination을 유지한다', async () => {
    const user = userEvent.setup();
    mockSearch.mockResolvedValue({ ...searchResponse, items: [], page: 3, totalElements: 3, totalPages: 1 });
    renderSearch('/search?keyword=QueryDSL&page=3');

    expect(await screen.findByText('이 페이지에는 검색 결과가 없습니다. 다른 페이지를 선택해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '페이지' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '1 페이지' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/search?keyword=QueryDSL&page=0');
  });

  it('TopBar submit은 공백을 정규화해 SearchPage로 이동한다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <TopBar />
        <LocationProbe />
      </MemoryRouter>,
    );
    const input = screen.getByRole('searchbox', { name: '검색' });
    await user.type(input, '  hello   world  ');
    await user.click(screen.getByRole('button', { name: '검색' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/search?keyword=hello+world');
  });

  it('검색어 세대가 바뀌면 늦은 이전 응답이 새 결과를 덮지 않는다', async () => {
    const first = new Promise<typeof searchResponse>(() => undefined);
    mockSearch.mockImplementation(async ({ keyword }) => {
      if (keyword === 'old') return first;
      return { ...searchResponse, items: [{ ...searchResponse.items[0], id: 55, title: '새 결과', publishedAt: '2026-11-09T00:00:00Z' }] };
    });
    const { rerender } = render(
      <MemoryRouter initialEntries={['/search?keyword=old']}>
        <SearchPage />
        <QueryChanger keyword="old" />
      </MemoryRouter>,
    );
    await waitFor(() => expect(mockSearch).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'old' })));
    rerender(
      <MemoryRouter initialEntries={['/search?keyword=old']}>
        <SearchPage />
        <QueryChanger keyword="new" />
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { level: 2, name: '새 결과' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 2, name: 'QueryDSL 초안' })).not.toBeInTheDocument();
  });
});
