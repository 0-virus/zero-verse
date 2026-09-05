/**
 * 블로그 API(FR-SETTINGS-03·04, FR-BLOG-01, M2 Gate 5).
 */

import { apiClient } from '../../lib/apiClient';
import type {
  BlogResponse,
  UpdateBlogRequest,
  InitialSetupRequest,
  InitialSetupResponse,
  PublicBlogResponse,
} from '../settings/types';

/**
 * 현재 사용자의 블로그를 조회한다(FR-SETTINGS-03).
 *
 * @returns 블로그 정보
 */
export async function getBlog(): Promise<BlogResponse> {
  const response = await apiClient.get<BlogResponse>('/api/v1/blogs/me');
  if (!response) {
    throw new Error('블로그 조회 응답이 비어 있습니다.');
  }
  return response;
}

/**
 * 현재 사용자의 블로그 정보를 수정한다(FR-SETTINGS-03).
 *
 * <p>title, urlSlug, description을 변경할 수 있다.
 * slug unique 검사는 자신의 기존 slug를 제외한 다른 블로그만 확인한다.
 *
 * @param request 수정 요청
 * @returns 수정된 블로그 정보
 */
export async function updateBlog(request: UpdateBlogRequest): Promise<BlogResponse> {
  const response = await apiClient.put<BlogResponse>('/api/v1/blogs/me', request);
  if (!response) {
    throw new Error('블로그 수정 응답이 비어 있습니다.');
  }
  return response;
}

/**
 * 블로그 초기 설정을 완료한다(FR-SETTINGS-04).
 *
 * <p>title/slug이 비어 있으면 기본값을 사용한다:
 * - title 빈 값 → "{nickname}의 블로그"
 * - slug 빈 값 → nickname 기반 자동 생성, 충돌 시 suffix 추가
 *
 * <p><b>초기 설정은 정확히 1회만 성공한다.</b> 이미 완료되면 409를 반환한다.
 *
 * @param request 초기 설정 요청
 * @returns 초기 설정 후 블로그 정보
 */
export async function initialSetup(
  request: InitialSetupRequest,
): Promise<InitialSetupResponse> {
  const response = await apiClient.put<InitialSetupResponse>(
    '/api/v1/blogs/me/initial-setup',
    request,
  );
  if (!response) {
    throw new Error('초기 설정 응답이 비어 있습니다.');
  }
  return response;
}

/**
 * 공개 블로그를 URL slug로 조회한다(FR-BLOG-01).
 *
 * <p>블로그와 소유자가 모두 soft delete되지 않은 경우만 반환된다.
 * 소유자 상태가 SUSPENDED인 경우 블로그는 여전히 공개된다.
 *
 * <p>인증이 필요 없는 경로지만 <b>다른 호출과 똑같이 {@code apiClient}를 쓴다</b>. apiClient는
 * 토큰이 있을 때만 Authorization을 붙이므로(`apiClient.ts`) 비로그인 상태에서는 그대로 무인증
 * 요청이 나가고, 로그인 상태에서 헤더가 붙어도 서버는 이 경로를 permitAll GET으로 열어 두어
 * 결과가 달라지지 않는다. 헤더를 떼겠다고 fetch를 직접 부르면 envelope 파싱·오류 변환·자격증명
 * 처리를 한 벌 더 구현하게 되고, 그 사본이 apiClient와 어긋나는 순간이 버그가 된다.
 *
 * @param urlSlug 조회할 블로그의 URL slug
 * @returns 공개 블로그 정보 (소유자 기본 정보 포함)
 */
export async function getPublicBlog(urlSlug: string): Promise<PublicBlogResponse> {
  const response = await apiClient.get<PublicBlogResponse>(
    `/api/v1/blogs/slug/${encodeURIComponent(urlSlug)}`,
  );
  if (!response) {
    throw new Error('공개 블로그 조회 응답이 비어 있습니다.');
  }
  return response;
}
