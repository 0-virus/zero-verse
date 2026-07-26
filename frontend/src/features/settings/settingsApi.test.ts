import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiRequestError,
  resetApiClient,
  setAccessToken,
} from '../../lib/apiClient';
import {
  getProfile,
  updateProfile,
  changePassword,
} from './settingsApi';
import type {
  UpdateProfileRequest,
  UserProfileResponse,
  ChangePasswordRequest,
} from './types';

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

describe('settingsApi', () => {
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

  describe('getProfile', () => {
    it('GET /api/v1/users/me를 호출하고 응답 data를 돌려준다', async () => {
      const response: UserProfileResponse = {
        id: 1,
        email: 'test@zeroverse.test',
        name: '테스터',
        nickname: 'tester',
        birthDate: '1990-01-01',
        bio: '안녕하세요',
        profileImageUrl: 'https://example.com/img.jpg',
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await getProfile();

      expect(result).toEqual(response);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/users/me');
      expect(fetchMock.mock.calls[0][1].method).toBe('GET');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
    });

    it('응답에서 password 필드는 제외된다', async () => {
      const response: UserProfileResponse = {
        id: 1,
        email: 'test@zeroverse.test',
        name: '테스터',
        nickname: 'tester',
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await getProfile();

      expect(result).not.toHaveProperty('password');
      expect(Object.keys(result)).not.toContain('password');
    });
  });

  describe('updateProfile', () => {
    it('PUT /api/v1/users/me를 호출하고 본문에 요청값을 담는다', async () => {
      const request: UpdateProfileRequest = {
        name: '새이름',
        nickname: 'newname',
        bio: '새소개',
      };
      const response: UserProfileResponse = {
        id: 1,
        email: 'test@zeroverse.test',
        name: '새이름',
        nickname: 'newname',
        bio: '새소개',
      };
      fetchMock.mockResolvedValueOnce(ok(response));

      const result = await updateProfile(request);

      expect(result).toEqual(response);
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/users/me');
      expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body).toEqual(request);
    });

    it('USER_002(nickname 중복) 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(409, 'USER_002', '이미 사용 중인 닉네임입니다.'),
      );

      await expect(updateProfile({ name: 'test', nickname: 'taken' })).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'USER_002' &&
          e.status === 409,
      );
    });

    it('VALIDATION_001(입력값 오류) 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(400, 'VALIDATION_001', '요청값이 올바르지 않습니다.', [
          { field: 'nickname', reason: '닉네임은 2~20자여야 합니다.' },
        ]),
      );

      await expect(updateProfile({ name: 'test', nickname: 'x' })).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'VALIDATION_001' &&
          e.status === 400,
      );
    });
  });

  describe('changePassword', () => {
    it('PUT /api/v1/users/me/password를 호출하고 본문에 현재/새 비밀번호를 담는다', async () => {
      const request: ChangePasswordRequest = {
        currentPassword: 'old123!A',
        newPassword: 'new456!B',
      };
      fetchMock.mockResolvedValueOnce(ok(null));

      await changePassword(request);

      expect(fetchMock).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/users/me/password');
      expect(fetchMock.mock.calls[0][1].method).toBe('PUT');
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token');
      const body = JSON.parse(fetchMock.mock.calls[0][1].body);
      expect(body).toEqual(request);
    });

    it('USER_005(현재 비밀번호 불일치) 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(400, 'USER_005', '현재 비밀번호가 일치하지 않습니다.'),
      );

      await expect(
        changePassword({ currentPassword: 'wrong', newPassword: 'new456!B' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'USER_005' &&
          e.status === 400,
      );
    });

    it('비밀번호를 요청 본문으로만 보내고 반환값에 보관하지 않는다', async () => {
      fetchMock.mockResolvedValueOnce(ok(null));

      const result = await changePassword({
        currentPassword: 'old123!A',
        newPassword: 'new456!B',
      });

      expect(result).toBeNull();
    });

    it('VALIDATION_001(비밀번호 정책 위반) 오류를 ApiRequestError로 전달한다', async () => {
      fetchMock.mockResolvedValueOnce(
        error(400, 'VALIDATION_001', '요청값이 올바르지 않습니다.', [
          { field: 'newPassword', reason: '비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.' },
        ]),
      );

      await expect(
        changePassword({ currentPassword: 'old123!A', newPassword: 'invalid' }),
      ).rejects.toSatisfy(
        (e: unknown) =>
          e instanceof ApiRequestError &&
          e.code === 'VALIDATION_001' &&
          e.status === 400,
      );
    });
  });
});
