/**
 * 인증 도메인 타입 — 백엔드 계약(FR-AUTH-01~05, ADR-0003)과 1:1로 맞춘다.
 *
 * Refresh Token은 어떤 타입에도 없다. HttpOnly 쿠키로만 오가며 JS가 볼 수 없다.
 */

/** 공통 응답 래퍼(PRD §4.1). 성공·실패 모두 네 키가 항상 있다. */
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  timestamp: string;
}

export interface ApiError {
  code: string;
  message: string;
  details: ApiFieldError[];
}

export interface ApiFieldError {
  field: string;
  reason: string;
}

/** 회원가입 요청. `name`·`birthDate` 모두 필수다(FR-AUTH-01, PRD §9.4-AA). */
export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
  nickname: string;
  /** ISO 날짜(`YYYY-MM-DD`). */
  birthDate: string;
}

export interface SigninRequest {
  email: string;
  password: string;
}

/** 토큰 응답. Access만 담기며 프론트 메모리에만 보관한다(PRD §4.3). */
export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  /** 초 단위 잔여 수명. */
  expiresIn: number;
}

export interface DefaultBlog {
  id: number;
  title: string;
  urlSlug: string;
  isSetupCompleted: boolean;
}

export interface AuthUser {
  id: number;
  email: string;
  name: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  profileImageUrl: string | null;
  defaultBlog: DefaultBlog;
}

/** 백엔드 에러 코드(REQUIREMENTS NFR-04). 화면 분기에 쓰는 것만 추린다. */
export const ERROR_CODE = {
  /** 로그인 실패 — 이메일 없음과 비밀번호 불일치를 구분하지 않는다. */
  SIGNIN_FAILED: 'AUTH_001',
  ACCESS_TOKEN_EXPIRED: 'AUTH_002',
  REFRESH_INVALID: 'AUTH_003',
  UNAUTHENTICATED: 'AUTH_004',
  SUSPENDED: 'USER_003',
  NICKNAME_TAKEN: 'USER_002',
  EMAIL_TAKEN: 'USER_004',
  VALIDATION: 'VALIDATION_001',
} as const;
