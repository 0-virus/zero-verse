import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import {
  createPost,
  deletePost,
  getPost,
  listBlogPosts,
  listBlogPostsBySlug,
  listDrafts,
  listTagPosts,
  updatePost,
  updatePostImages,
} from './postApi';
import type { PostDetail, PostSummary } from './types';

function ok<T>(data: T, status = 200) {
  const body = { success: true, data, error: null, timestamp: '' };
  return {
    ok: true,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

const detail: PostDetail = {
  id: 4,
  blogId: 2,
  blogSlug: 'orbit',
  blogTitle: '궤도역학',
  author: { id: 9, nickname: 'owner', profileImageUrl: null },
  category: { id: 3, name: '개발' },
  title: '첫 로그',
  contentJson: { type: 'doc', content: [{ type: 'paragraph' }] },
  contentHtml: '<p>첫 로그</p>',
  thumbnailUrl: null,
  visibility: 'PUBLIC',
  viewCount: 1,
  publishedAt: '2026-10-03T00:00:00Z',
  createdAt: '2026-10-03T00:00:00Z',
  updatedAt: '2026-10-03T00:00:00Z',
  tags: ['react'],
  images: [],
  previous: null,
  next: null,
};

const summary: PostSummary = {
  ...detail,
  excerpt: '첫 로그',
};

describe('postApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    setAccessToken('test-token');
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('create/update는 전체 snapshot camelCase를 그대로 전송한다', async () => {
    fetchMock.mockResolvedValueOnce(ok(detail, 201)).mockResolvedValueOnce(ok(detail));
    const request = {
      blogId: 2,
      title: '첫 로그',
      contentJson: detail.contentJson,
      contentHtml: detail.contentHtml,
      categoryId: null,
      visibility: 'PUBLIC' as const,
      publish: true,
      thumbnailUrl: null,
      tagNames: ['react'],
      images: [],
    };

    await expect(createPost(request)).resolves.toEqual(detail);
    await expect(updatePost(4, { ...request, blogId: undefined })).resolves.toEqual(detail);

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method, init.body])).toEqual([
      ['http://localhost:8080/api/v1/posts', 'POST', JSON.stringify(request)],
      [
        'http://localhost:8080/api/v1/posts/4',
        'PUT',
        JSON.stringify({ ...request, blogId: undefined }),
      ],
    ]);
  });

  it('목록·draft·tag 경로는 접근 필터와 표준 페이징 쿼리를 전달한다', async () => {
    const page = {
      items: [summary],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
      hasNext: false,
      hasPrevious: true,
    };
    fetchMock.mockResolvedValue(ok(page));

    await listBlogPosts(2, {
      page: 1,
      size: 20,
      sort: 'popular',
      categoryId: 3,
      tag: 'React',
      visibility: 'UNIVERSE',
      publish: true,
    });
    await listDrafts();
    await listTagPosts('React', { page: 0, size: 20, sort: 'latest' });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      'http://localhost:8080/api/v1/blogs/2/posts?page=1&size=20&sort=popular&categoryId=3&tag=React&visibility=UNIVERSE&publish=true',
      'http://localhost:8080/api/v1/posts/drafts?page=0&size=20',
      'http://localhost:8080/api/v1/tags/React/posts?page=0&size=20&sort=latest',
    ]);
  });

  it('공개 slug 목록은 블로그 slug 경로를 사용한다', async () => {
    fetchMock.mockResolvedValue(ok({
      items: [summary],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    }));

    await expect(listBlogPostsBySlug('my orbit', { page: 0, size: 20 })).resolves.toMatchObject({
      items: [summary],
    });
    expect(fetchMock.mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/blogs/slug/my%20orbit/posts?page=0&size=20',
    );
  });

  it('상세·이미지 snapshot·삭제를 계약 경로로 보낸다', async () => {
    fetchMock
      .mockResolvedValueOnce(ok(detail))
      .mockResolvedValueOnce(ok(detail.images))
      .mockResolvedValueOnce(ok(null));

    await expect(getPost(4)).resolves.toEqual(detail);
    await expect(updatePostImages(4, { images: [] })).resolves.toEqual([]);
    await expect(deletePost(4)).resolves.toBeNull();

    expect(fetchMock.mock.calls.map(([url, init]) => [url, init.method, init.body])).toEqual([
      ['http://localhost:8080/api/v1/posts/4', 'GET', undefined],
      ['http://localhost:8080/api/v1/posts/4/images', 'PUT', JSON.stringify({ images: [] })],
      ['http://localhost:8080/api/v1/posts/4', 'DELETE', undefined],
    ]);
  });
});
