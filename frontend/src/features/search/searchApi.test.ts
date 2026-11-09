import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../lib/apiClient';
import { normalizeSearchKeyword, search } from './searchApi';

describe('searchApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('검색은 keyword/type/page/size/sort를 하나의 서버 요청으로 전달한다', async () => {
    const response = {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
      counts: { ALL: 0, POST: 0, BLOG: 0, USER: 0, TAG: 0 },
    };
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue(response);

    await search({ keyword: '  Query   DSL ', type: 'POST', page: 0, size: 20, sort: 'latest' });

    expect(get).toHaveBeenCalledWith(
      '/api/v1/search?keyword=Query+DSL&type=POST&page=0&size=20&sort=latest',
    );
  });

  it('blank keyword는 API helper에서 호출하지 않고 명시적으로 거부한다', async () => {
    const get = vi.spyOn(apiClient, 'get');
    await expect(search({ keyword: '  ', type: 'ALL', page: 0, size: 20, sort: 'latest' })).rejects.toThrow(
      '검색어',
    );
    expect(get).not.toHaveBeenCalled();
  });

  it('UTF-16 100자 경계를 공통 정규화한다', () => {
    expect(normalizeSearchKeyword('😀'.repeat(50))).toHaveLength(100);
    expect(() => normalizeSearchKeyword('😀'.repeat(51))).toThrow('100');
  });
});
