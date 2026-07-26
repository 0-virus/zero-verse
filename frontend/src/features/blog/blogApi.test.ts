import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiRequestError,
  resetApiClient,
  setAccessToken,
} from '../../lib/apiClient';
import {
  getBlog,
  updateBlog,
  initialSetup,
  getPublicBlog,
} from './blogApi';
import type {
  BlogResponse,
  UpdateBlogRequest,
  InitialSetupRequest,
  InitialSetupResponse,
  PublicBlogResponse,
} from '../settings/types';

function ok<T>(data: T) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '2026-07-26T00:00:00Z' }),
    json: async () => ({ success: true, data, error: null, timestamp: '2026-07-26T00:00:00Z' }),
  } as unknown as Response;
}

function error(status: number, code: string, message = '실패', details: unknown[] = []) {
  return {
    ok: false,
    status,
    text: async () =>
      JSON.stringify({
        success: false,
        data: null,
        error: { code, message, details },
        timestamp: '2026-07-26T00:00:00Z',
      }),
    json: async () => ({
      success: false,
      data: null,
      error: { code, message, details },
      timestamp: '2026-07-26T00:00:00Z',
    }),
  } as unknown as Response;
}

describe('blogApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    setAccessToken('test-token');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  describe('getBlog', () => {
    it('GET /api/v1/blogs/me를 호출하고 응답 data를 돌려준다', async () => {
      const response: BlogResponse = {
        id: 1,
        title: '테스터의 블로그',
        urlSlug: 'tester',
        description: '테스트 블로그',
        isSetupCompleted: true,
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await getBlog();

      expect(result).toEqual(response);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/blogs/me');
      expect(fetchMock.mock.calls[0][1].method).toBe('GET');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
    });

    it('BLOG_001(블로그 없음) 404 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(404, 'BLOG_001', '블로그를 찾을 수 없습니다.'),
      );

      await expect(getBlog()).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_001' &&
          e.status === 404,
      );
    });
  });

  describe('updateBlog', () => {
    it('PUT /api/v1/blogs/me를 호출하고 본문에 요청값을 담는다', async () => {
      const request: UpdateBlogRequest = {
        title: '새 제목',
        urlSlug: 'newslug',
        description: '새 소개',
      };
      const response: BlogResponse = {
        id: 1,
        title: '새 제목',
        urlSlug: 'newslug',
        description: '새 소개',
        isSetupCompleted: true,
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await updateBlog(request);

      expect(result).toEqual(response);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/blogs/me');
      expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body).toEqual(request);
    });

    it('BLOG_002(slug 중복) 409 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(409, 'BLOG_002', '이미 사용 중인 주소입니다.'),
      );

      await expect(
        updateBlog({ title: 'test', urlSlug: 'taken', description: '' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_002' &&
          e.status === 409,
      );
    });

    it('BLOG_003(slug 형식/예약어 오류) 400을 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(400, 'BLOG_003', '주소가 올바르지 않습니다.'),
      );

      await expect(
        updateBlog({ title: 'test', urlSlug: 'INVALID', description: '' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_003' &&
          e.status === 400,
      );
    });
  });

  describe('initialSetup', () => {
    it('PUT /api/v1/blogs/me/initial-setup를 호출하고 본문에 요청값을 담는다', async () => {
      const request: InitialSetupRequest = {
        title: '내 블로그',
        urlSlug: 'myblog',
        description: '처음 소개',
      };
      const response: InitialSetupResponse = {
        id: 1,
        title: '내 블로그',
        urlSlug: 'myblog',
        description: '처음 소개',
        isSetupCompleted: true,
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await initialSetup(request);

      expect(result).toEqual(response);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/blogs/me/initial-setup');
      expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body).toEqual(request);
    });

    it('BLOG_004(초기 설정 중복) 409 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(409, 'BLOG_004', '이미 초기 설정을 완료했습니다.'),
      );

      await expect(
        initialSetup({ title: '', urlSlug: '', description: '' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_004' &&
          e.status === 409,
      );
    });

    it('빈 title/slug는 기본값으로 대체된다는 것을 서버 응답으로 확인한다', async () => {
      const request: InitialSetupRequest = {
        title: '',
        urlSlug: '',
        description: '소개만',
      };
      const response: InitialSetupResponse = {
        id: 1,
        title: 'tester의 블로그',
        urlSlug: 'tester',
        description: '소개만',
        isSetupCompleted: true,
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await initialSetup(request);

      expect(result.title).toBe('tester의 블로그');
      expect(result.urlSlug).toBe('tester');
    });

    it('BLOG_002(slug 중복) 409 오류를 initialSetup 중복 호출과 분리한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(409, 'BLOG_002', '이미 사용 중인 주소입니다.'),
      );

      await expect(
        initialSetup({ title: 'test', urlSlug: 'taken' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_002' &&
          e.status === 409,
      );
    });
  });

  describe('getPublicBlog', () => {
    it('비로그인 상태에서 GET /api/v1/blogs/slug/{urlSlug}를 무인증으로 호출한다', async () => {
      resetApiClient(); // 토큰 초기화
      fetchMock.mockResolvedValueOnce(ok({
        id: 1,
        title: '공개 블로그',
        urlSlug: 'public',
        description: '공개 소개',
        owner: {
          id: 1,
          nickname: 'owner',
          name: '소유자',
          profileImageUrl: null,
          bio: '소개',
        },
      }));

      const result = await getPublicBlog('public');

      expect(result.urlSlug).toBe('public');
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/blogs/slug/public');
      expect(fetchMock.mock.calls[0][1].method).toBe('GET');
      // Authorization 헤더 없음
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
    });

    /**
     * 공개 경로는 로그인 여부와 무관하게 같은 결과를 준다. 서버가 이 경로를 permitAll GET으로
     * 열어 두므로 토큰이 붙든 말든 조회가 성립해야 한다 — 헤더 유무 자체는 계약이 아니다.
     */
    it('로그인 상태에서도 공개 조회가 그대로 성립한다', async () => {
      setAccessToken('logged-in-token');

      const response: PublicBlogResponse = {
        id: 1,
        title: '공개 블로그',
        urlSlug: 'public',
        description: '공개 소개',
        owner: {
          id: 1,
          nickname: 'owner',
          name: '소유자',
          profileImageUrl: null,
          bio: '소개',
        },
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await getPublicBlog('public');

      expect(result.urlSlug).toBe('public');
      expect(fetchMock.mock.calls[0][0]).toContain('/api/v1/blogs/slug/public');
    });

    it('응답에서 owner 정보를 포함해 돌려준다', async () => {
      const response: PublicBlogResponse = {
        id: 1,
        title: '공개 블로그',
        urlSlug: 'public',
        description: '공개 소개',
        owner: {
          id: 2,
          nickname: 'owner',
          name: '소유자',
          profileImageUrl: 'https://example.com/avatar.jpg',
          bio: '나는 소유자',
        },
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await getPublicBlog('public');

      expect(result.owner).toBeDefined();
      expect(result.owner.nickname).toBe('owner');
      expect(result.owner.name).toBe('소유자');
      expect(result.owner.bio).toBe('나는 소유자');
    });

    it('없는 slug는 404를 반환한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(404, 'BLOG_001', '블로그를 찾을 수 없습니다.'),
      );

      await expect(getPublicBlog('notfound')).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_001' &&
          e.status === 404,
      );
    });

    it('삭제된 블로그 또는 삭제된 소유자는 404를 반환한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(404, 'BLOG_001', '블로그를 찾을 수 없습니다.'),
      );

      await expect(getPublicBlog('deleted')).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'BLOG_001' &&
          e.status === 404,
      );
    });
  });
});
