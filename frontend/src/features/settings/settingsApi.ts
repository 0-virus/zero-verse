/**
 * 사용자 설정 API(FR-SETTINGS-01·02, M2 Gate 5).
 */

import { apiClient } from '../../lib/apiClient';
import type {
  UserProfileResponse,
  UpdateProfileRequest,
  ChangePasswordRequest,
} from './types';

/**
 * 현재 사용자 프로필을 조회한다(FR-SETTINGS-01).
 *
 * @returns 프로필 정보 (password 필드 제외)
 */
export async function getProfile(): Promise<UserProfileResponse> {
  const response = await apiClient.get<UserProfileResponse>('/api/v1/users/me');
  if (!response) {
    throw new Error('프로필 조회 응답이 비어 있습니다.');
  }
  return response;
}

/**
 * 현재 사용자 프로필을 수정한다(FR-SETTINGS-01).
 *
 * <p>name, nickname, bio, birthDate, profileImageUrl을 변경할 수 있다.
 * nickname unique 검사는 자신을 제외한 다른 사용자만 확인한다.
 *
 * @param request 수정 요청
 * @returns 수정된 프로필 정보
 */
export async function updateProfile(request: UpdateProfileRequest): Promise<UserProfileResponse> {
  const response = await apiClient.put<UserProfileResponse>('/api/v1/users/me', request);
  if (!response) {
    throw new Error('프로필 수정 응답이 비어 있습니다.');
  }
  return response;
}

/**
 * 현재 사용자 비밀번호를 변경한다(FR-SETTINGS-02).
 *
 * <p>현재 비밀번호를 검증한 후 새 비밀번호로 변경한다. 새 비밀번호 정책: 8~64자, 영문·숫자·특수문자 포함.
 *
 * @param request 비밀번호 변경 요청
 * @returns null (성공 시 빈 응답)
 */
export async function changePassword(request: ChangePasswordRequest): Promise<null> {
  // 요청 본문에만 비밀번호를 담고 응답에는 보관하지 않는다.
  return await apiClient.put<null>('/api/v1/users/me/password', request);
}
