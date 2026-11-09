import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../../lib/apiClient';
import { getPublicFeed, getUniverseFeed, normalizeDiscoveryKeyword } from './feedApi';

describe('feedApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('public feed는 정규화된 keyword와 pagination을 서버 URL에 전달한다', async () => {
    const page = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false };
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue(page);

    await getPublicFeed({ keyword: '  spring   boot  ', page: 0, size: 20, sort: 'latest' });

    expect(get).toHaveBeenCalledWith(
      '/api/v1/feed/public?keyword=spring+boot&page=0&size=20&sort=latest',
    );
  });

  it('universe feed는 relation을 소문자 canonical 값으로 보낸다', async () => {
    const page = { items: [], page: 1, size: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: true };
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue(page);

    await getUniverseFeed({ relation: 'friends', page: 1, size: 10, sort: 'popular' });

    expect(get).toHaveBeenCalledWith(
      '/api/v1/feed/universe?relation=friends&page=1&size=10&sort=popular',
    );
  });

  it('keyword는 연속 공백을 한 칸으로 만들고 UTF-16 100자까지만 허용한다', () => {
    expect(normalizeDiscoveryKeyword('  A\t B\nC  ')).toBe('A B C');
    expect(normalizeDiscoveryKeyword('😀'.repeat(50))).toHaveLength(100);
    expect(() => normalizeDiscoveryKeyword('😀'.repeat(51))).toThrow('100');
  });
});
