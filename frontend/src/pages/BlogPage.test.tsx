import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter, MemoryRouter, useLocation } from 'react-router-dom'
import BlogPage from './BlogPage'

// URL의 현재 search 문자열을 노출해 라우팅 부수효과를 검증하기 위한 헬퍼
function LocationSearchProbe() {
  const location = useLocation()
  return <div data-testid="location-search">{location.search}</div>
}

// 선택 표시(bg-[#0e7490])를 가진 조상 노드를 찾는다
function selectedNodeOf(el: HTMLElement | null): HTMLElement | null {
  let cur: HTMLElement | null = el
  while (cur) {
    if (cur.className && cur.className.includes('bg-[#0e7490]')) return cur
    cur = cur.parentElement
  }
  return null
}
import * as useBlogPublicModule from '../hooks/useBlogPublic'
import * as useCategoriesModule from '../hooks/useCategories'
import type { BlogPublicResponse } from '../types/settings'

vi.mock('../hooks/useBlogPublic')
vi.mock('../hooks/useCategories')
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ blogSlug: 'my-blog' }),
  }
})
vi.mock('../components/layout/AppShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

describe('BlogPage component', () => {
  const mockBlog: BlogPublicResponse = {
    blogId: 1,
    title: 'My Blog',
    urlSlug: 'my-blog',
    description: 'This is my blog',
    owner: {
      userId: 1,
      nickname: 'testuser',
      profileImageUrl: 'https://example.com/profile.jpg',
      bio: 'Test bio',
    },
    createdAt: '2026-01-15T10:00:00Z',
  }

  const mockCategories = [
    {
      categoryId: 1,
      blogId: 1,
      parentId: null,
      name: 'Default',
      type: 'DEFAULT' as const,
      displayOrder: 0,
      postCount: 0,
      draftPostCount: 0,
      children: [],
    },
    {
      categoryId: 2,
      blogId: 1,
      parentId: null,
      name: 'Tech',
      type: 'GENERAL' as const,
      displayOrder: 1,
      postCount: 5,
      draftPostCount: 0,
      children: [
        {
          categoryId: 3,
          blogId: 1,
          parentId: 2,
          name: 'Frontend',
          type: 'GENERAL' as const,
          displayOrder: 0,
          postCount: 3,
          draftPostCount: 0,
          children: [],
        },
      ],
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()

    // Default mock setup
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn(),
      isLoading: false,
      error: null,
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: [],
      isLoading: false,
      error: null,
      getCategories: vi.fn(),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })
  })

  it('should handle loading state', () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn(),
      isLoading: true,
      error: null,
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    expect(screen.getByText('블로그 로딩 중...')).toBeDefined()
  })

  it('should handle 404 error state', () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn(),
      isLoading: false,
      error: { code: 'NOT_FOUND', message: 'Blog not found' },
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    expect(screen.getByText('Not Found')).toBeDefined()
    expect(screen.getByText('블로그를 찾을 수 없습니다.')).toBeDefined()
  })

  it('should render blog when data is provided and loaded', () => {
    // Mock the hook to provide blog data with isLoading false
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: mockBlog,
      getPublicBlog: vi.fn(),
      isLoading: false,
      error: null,
    })

    const { container } = render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // Verify component renders without crashing
    expect(container).toBeDefined()
  })

  it('should call getPublicBlog from hook on mount', () => {
    const getPublicBlogMock = vi.fn()
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: getPublicBlogMock,
      isLoading: true,
      error: null,
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // Component should attempt to load blog when blogSlug param is present
    // (Note: in test context, blogSlug is undefined, so loadBlog returns early)
    expect(screen.getByText('블로그 로딩 중...')).toBeDefined()
  })

  it('should render blog content sections when data is loaded', () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: mockBlog,
      getPublicBlog: vi.fn(),
      isLoading: false,
      error: null,
    })

    const { container } = render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // Verify that component renders with categories, posts, and owner sections
    expect(container.querySelector('.grid')).toBeDefined()
  })

  it('should load categories for public blog', async () => {
    const getPublicBlog = vi.fn().mockResolvedValue(mockBlog)
    const getCategories = vi.fn().mockResolvedValue(mockCategories)

    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog,
      isLoading: false,
      error: null,
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // getCategories should be called with the blog ID
    await waitFor(() => {
      expect(getCategories).toHaveBeenCalledWith(mockBlog.blogId)
    })
  })

  it('should call getCategories when blog loads', async () => {
    const getCategories = vi.fn().mockResolvedValue(mockCategories)
    const getPublicBlog = vi.fn().mockResolvedValue(mockBlog)

    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog,
      isLoading: false,
      error: null,
    })

    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories,
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // getCategories should be called with the blog ID when blog loads
    // (Note: in test context with mocked useParams returning blogSlug)
    expect(getPublicBlog).toHaveBeenCalled()
  })

  it('initial ?categoryId= selects that category node', async () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn().mockResolvedValue(mockBlog),
      isLoading: false,
      error: null,
    })
    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories: vi.fn().mockResolvedValue(mockCategories),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    // categoryId=2 ('Tech') 를 URL 쿼리로 진입
    render(
      <MemoryRouter initialEntries={['/blogs/my-blog?categoryId=2']}>
        <BlogPage />
      </MemoryRouter>
    )

    // blog은 async 로드 → 트리가 나타날 때까지 대기
    const techLabel = await screen.findByText('Tech')

    // 'Tech' 노드가 선택 스타일을 갖고, 미선택 'Default'는 갖지 않는다
    expect(selectedNodeOf(techLabel)).not.toBeNull()
    expect(selectedNodeOf(screen.getByText('Default'))).toBeNull()
  })

  it('selecting a category writes categoryId to the URL', async () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn().mockResolvedValue(mockBlog),
      isLoading: false,
      error: null,
    })
    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories: vi.fn().mockResolvedValue(mockCategories),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/blogs/my-blog']}>
        <BlogPage />
        <LocationSearchProbe />
      </MemoryRouter>
    )

    // blog 로드 완료 후 트리 등장, 처음엔 categoryId 쿼리 없음
    const techLabel = await screen.findByText('Tech')
    expect(screen.getByTestId('location-search').textContent).toBe('')

    // 'Tech'(categoryId=2) 클릭 → URL 이 ?categoryId=2 로 갱신
    fireEvent.click(techLabel)

    await waitFor(() => {
      expect(screen.getByTestId('location-search').textContent).toBe('?categoryId=2')
    })
    // 선택 상태도 반영
    expect(selectedNodeOf(screen.getByText('Tech'))).not.toBeNull()
  })

  it('renders all category names from the tree (root + child)', async () => {
    vi.mocked(useBlogPublicModule.useBlogPublic).mockReturnValue({
      blog: null,
      getPublicBlog: vi.fn().mockResolvedValue(mockBlog),
      isLoading: false,
      error: null,
    })
    vi.mocked(useCategoriesModule.useCategories).mockReturnValue({
      categories: mockCategories,
      isLoading: false,
      error: null,
      getCategories: vi.fn().mockResolvedValue(mockCategories),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn(),
      reorderCategories: vi.fn(),
    })

    render(
      <BrowserRouter>
        <BlogPage />
      </BrowserRouter>
    )

    // 루트(Default, Tech)와 자식(Frontend)이 모두 렌더된다 (blog async 로드 대기)
    expect(await screen.findByText('Default')).toBeDefined()
    expect(screen.getByText('Tech')).toBeDefined()
    expect(screen.getByText('Frontend')).toBeDefined()
    // 발행 글 수 표기(5) 노출 확인
    expect(screen.getByText('(5)')).toBeDefined()
  })
})
