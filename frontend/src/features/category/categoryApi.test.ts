import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import {
  createCategory,
  deleteCategory,
  getAllCategories,
  getCategories,
  reorderCategories,
  updateCategory,
  type Category,
} from './categoryApi';

function ok<T>(data: T, status = 200) {
  return {
    ok: true,
    status,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

const category = (id: number, parentId: number | null = null): Category => ({
  id,
  parentId,
  name: `카테고리 ${id}`,
  type: 'GENERAL',
  displayOrder: id,
  postCount: id,
  children: [],
});

describe('categoryApi', () => {
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

  it('GET은 루트 페이지와 includeDrafts를 API 계약대로 전달한다', async () => {
    const page = {
      items: [category(1)],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    };
    fetchMock.mockResolvedValueOnce(ok(page));

    await expect(getCategories(7, { includeDrafts: true })).resolves.toEqual(page);
    expect(fetchMock.mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/blogs/7/categories?page=0&size=20&includeDrafts=true',
    );
  });

  it('전체 루트 페이지를 순서대로 읽어 합친다', async () => {
    fetchMock
      .mockResolvedValueOnce(
        ok({
          items: [category(1)],
          page: 0,
          size: 100,
          totalElements: 101,
          totalPages: 2,
          hasNext: true,
          hasPrevious: false,
        }),
      )
      .mockResolvedValueOnce(
        ok({
          items: [category(101)],
          page: 1,
          size: 100,
          totalElements: 101,
          totalPages: 2,
          hasNext: false,
          hasPrevious: true,
        }),
      );

    await expect(getAllCategories(7, true)).resolves.toEqual([category(1), category(101)]);
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      'http://localhost:8080/api/v1/blogs/7/categories?page=0&size=100&includeDrafts=true',
      'http://localhost:8080/api/v1/blogs/7/categories?page=1&size=100&includeDrafts=true',
    ]);
  });

  it('생성·수정·삭제·순서 변경 body를 계약대로 전달한다', async () => {
    fetchMock
      .mockResolvedValueOnce(ok(category(2), 201))
      .mockResolvedValueOnce(ok(category(2)))
      .mockResolvedValueOnce(ok(null))
      .mockResolvedValueOnce(ok(null));

    await createCategory(7, {
      name: '새 카테고리',
      parentId: null,
      type: 'GENERAL',
      displayOrder: 2,
    });
    await updateCategory(7, 2, {
      name: '수정 카테고리',
      type: 'LOCKED',
      displayOrder: 2,
    });
    await deleteCategory(7, 2);
    await reorderCategories(7, [3, 1, 2]);

    expect(fetchMock.mock.calls.map((call) => [call[1].method, call[1].body])).toEqual([
      ['POST', JSON.stringify({ name: '새 카테고리', parentId: null, type: 'GENERAL', displayOrder: 2 })],
      ['PUT', JSON.stringify({ name: '수정 카테고리', type: 'LOCKED', displayOrder: 2 })],
      ['DELETE', undefined],
      ['PUT', JSON.stringify([3, 1, 2])],
    ]);
  });
});
