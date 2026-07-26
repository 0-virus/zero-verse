/**
 * 사용자/블로그 설정 타입(M2 Gate 5).
 *
 * 백엔드 DTO(UserSettingsDtos·BlogSettingsDtos)와 필드명·nullable 여부가 일치한다.
 */

export interface UpdateProfileRequest {
  name: string;
  nickname: string;
  bio?: string | null;
  birthDate?: string | null; // ISO 날짜 `YYYY-MM-DD`
  profileImageUrl?: string | null;
}

export interface UserProfileResponse {
  id: number;
  email: string;
  name: string;
  nickname: string;
  birthDate?: string | null;
  bio?: string | null;
  profileImageUrl?: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateBlogRequest {
  title: string;
  urlSlug: string;
  description?: string | null;
}

export interface BlogResponse {
  id: number;
  title: string;
  urlSlug: string;
  description?: string | null;
  isSetupCompleted: boolean;
}

export interface InitialSetupRequest {
  title?: string | null;
  urlSlug?: string | null;
  description?: string | null;
}

export interface InitialSetupResponse {
  id: number;
  title: string;
  urlSlug: string;
  description?: string | null;
  isSetupCompleted: boolean;
}

export interface PublicBlogResponse {
  id: number;
  title: string;
  urlSlug: string;
  description?: string | null;
  /** 공개 소유자 정보 — 실명(name)은 포함하지 않는다(BlogSettingsDtos.OwnerInfo 참조). */
  owner: {
    id: number;
    nickname: string;
    profileImageUrl?: string | null;
    bio?: string | null;
  };
}
