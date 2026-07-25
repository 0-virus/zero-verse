import type { ApiEnvelope, ApiError } from '../types/auth';

/**
 * API 클라이언트(PRD §8.2, REQUIREMENTS §8.3).
 *
 * 책임 셋:
 * 1. 공통 응답 래퍼를 벗겨 `data`만 돌려주고, 실패는 {@link ApiRequestError}로 던진다.
 * 2. Access Token을 메모리에서 읽어 Bearer로 붙인다.
 * 3. 보호 요청이 401이면 refresh를 **single-flight**로 한 번만 돌리고 재시도한다.
 *
 * localStorage·sessionStorage·document.cookie를 쓰지 않는다(PRD §8.1). Access는 모듈
 * 메모리에만 있고 Refresh는 HttpOnly 쿠키라 JS가 접근할 수 없다.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** refresh를 재귀적으로 호출하면 안 되는 경로. 이들의 401은 그대로 던진다. */
const NO_REFRESH_PATHS = [
  '/api/v1/auth/register',
  '/api/v1/auth/signin',
  '/api/v1/auth/refresh',
  '/api/v1/auth/signout',
];

/** 서버가 준 공통 실패 응답을 그대로 감싼 오류. 가짜 코드를 만들어내지 않는다. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ApiError['details'];

  constructor(status: number, error: ApiError | null) {
    super(error?.message ?? '요청을 처리하지 못했습니다.');
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = error?.code ?? 'UNKNOWN';
    this.details = error?.details ?? [];
  }

  /** 특정 필드의 검증 메시지. */
  fieldError(field: string): string | undefined {
    return this.details.find((detail) => detail.field === field)?.reason;
  }
}

let accessToken: string | null = null;

/**
 * Access Token의 세대 번호.
 *
 * <p>지연된 401을 구분하기 위해 필요하다. 요청을 보낼 때의 버전과 401을 받은 시점의 버전이
 * 다르면, 그 사이 **다른 요청이 이미 갱신을 마친 것**이므로 refresh를 또 돌릴 이유가 없다.
 * 버전 비교 없이 401만 보고 판단하면 rotation이 연쇄로 일어나 세션이 깨진다.
 */
let tokenVersion = 0;

/** 진행 중인 refresh. 동시 401이 여러 개여도 갱신은 한 번만 돈다. */
let refreshPromise: Promise<boolean> | null = null;

/** refresh까지 실패했을 때 호출된다(AuthContext가 상태를 정리하고 /signin으로 보낸다). */
let onAuthExpired: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
  tokenVersion += 1;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAuthExpiredHandler(handler: (() => void) | null): void {
  onAuthExpired = handler;
}

/** 테스트에서 모듈 상태를 초기화한다. */
export function resetApiClient(): void {
  accessToken = null;
  tokenVersion = 0;
  refreshPromise = null;
  onAuthExpired = null;
}

/**
 * 세션 복구용 refresh.
 *
 * <p>{@link AuthProvider} 초기화가 직접 `/auth/refresh`를 호출하면 React StrictMode의
 * effect 이중 실행으로 **같은 쿠키에 rotation이 두 번** 일어난다. 두 번째는 이미 폐기된
 * 토큰을 써서 `AUTH_003`을 받고, 복구된 세션이 도로 비워질 수 있다. 그래서 초기 복구도
 * 401 갱신과 **같은 single-flight**를 탄다.
 */
export function restoreSession(): Promise<boolean> {
  return refreshOnce();
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** true면 401이어도 refresh를 시도하지 않는다. */
  skipRefresh?: boolean;
}

async function rawRequest(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  return fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    // Refresh 쿠키를 주고받으려면 필수다.
    credentials: 'include',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

async function parseEnvelope<T>(response: Response): Promise<T | null> {
  // 204 등 본문 없는 성공.
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  const envelope = JSON.parse(text) as ApiEnvelope<T>;
  if (!response.ok || !envelope.success) {
    throw new ApiRequestError(response.status, envelope.error);
  }
  return envelope.data;
}

/**
 * Refresh를 single-flight로 실행한다.
 *
 * <p>동시에 여러 요청이 401을 받아도 갱신은 한 번만 돈다. 각자 갱신하면 rotation 때문에
 * 뒤늦은 요청이 이미 폐기된 토큰을 쓰게 되고, 서버는 정상적으로 `AUTH_003`을 준다.
 */
function refreshOnce(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const response = await rawRequest('/api/v1/auth/refresh', { method: 'POST' });
        if (!response.ok) {
          return false;
        }
        const envelope = (await response.json()) as ApiEnvelope<{ accessToken: string }>;
        if (!envelope.success || !envelope.data) {
          return false;
        }
        // setAccessToken을 거쳐야 tokenVersion이 올라가고, 대기 중인 요청이
        // "이미 갱신됨"을 알아볼 수 있다.
        setAccessToken(envelope.data.accessToken);
        return true;
      } catch {
        return false;
      } finally {
        // 성공·실패와 무관하게 다음 401은 새 refresh를 시작할 수 있어야 한다.
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

/**
 * 인증을 고려한 요청.
 *
 * <p>401이면 refresh를 한 번 돌리고 **각 요청은 1회만** 재시도한다. 재시도도 401이거나
 * refresh가 실패하면 세션이 끝난 것으로 보고 만료 핸들러를 부른다.
 *
 * <p>**지연된 401 처리**: 요청을 보낼 때의 {@link tokenVersion}을 기억해 두고, 401이 돌아온
 * 시점에 버전이 이미 바뀌었다면 다른 요청이 갱신을 마친 것이므로 refresh 없이 새 토큰으로
 * 바로 재시도한다. 이 확인이 없으면 느린 요청의 뒤늦은 401이 불필요한 두 번째 rotation을
 * 일으켜, 방금 발급된 토큰이 곧바로 폐기된다.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T | null> {
  const skipRefresh = options.skipRefresh || NO_REFRESH_PATHS.includes(path);

  const versionAtRequest = tokenVersion;
  let response = await rawRequest(path, options);

  if (response.status === 401 && !skipRefresh) {
    // 내 요청이 나간 뒤 토큰이 바뀌었다면 이미 누군가 갱신했다.
    const alreadyRefreshed = tokenVersion !== versionAtRequest;
    const refreshed = alreadyRefreshed || (await refreshOnce());

    if (!refreshed) {
      accessToken = null;
      onAuthExpired?.();
      return parseEnvelope<T>(response);
    }
    response = await rawRequest(path, options);
    if (response.status === 401) {
      accessToken = null;
      onAuthExpired?.();
    }
  }

  return parseEnvelope<T>(response);
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
