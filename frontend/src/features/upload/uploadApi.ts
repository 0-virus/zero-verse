import { apiClient, getApiBaseUrl } from '../../lib/apiClient';

export type UploadPurpose = 'PROFILE_IMAGE' | 'POST_THUMBNAIL' | 'POST_IMAGE';

export interface UploadResponse {
  id: string;
  imageUrl: string;
  contentType: string;
  size: number;
  purpose: UploadPurpose;
}

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;
const UPLOAD_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function validateImage(file: File): void {
  if (file.size < 1) throw new Error('이미지 파일이 비어 있습니다.');
  if (file.size > MAX_IMAGE_BYTES) throw new Error('이미지 파일은 최대 5MB까지 업로드할 수 있습니다.');
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    throw new Error('지원하지 않는 이미지 형식입니다. JPEG, PNG, WebP, GIF만 사용할 수 있습니다.');
  }
}

/**
 * 업로드 응답은 곧바로 프로필/본문에 저장되는 관리 이미지 참조다.
 * 서버가 돌려준 값이 다른 리소스·외부 URL·blob URL이면 저장 전에 폐기한다.
 */
function validateUploadResponse(value: unknown, file: File, purpose: UploadPurpose): UploadResponse {
  if (!value || typeof value !== 'object') {
    throw new Error('이미지 업로드 응답이 올바르지 않습니다.');
  }

  const response = value as Partial<UploadResponse>;
  if (
    typeof response.id !== 'string' ||
    !UPLOAD_ID_PATTERN.test(response.id) ||
    response.imageUrl !== `/api/v1/uploads/${response.id}/content` ||
    response.purpose !== purpose ||
    typeof response.contentType !== 'string' ||
    !(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(response.contentType) ||
    typeof response.size !== 'number' ||
    !Number.isSafeInteger(response.size) ||
    response.size !== file.size
  ) {
    throw new Error('이미지 업로드 응답이 올바르지 않습니다.');
  }

  return response as UploadResponse;
}

export async function uploadImage(file: File, purpose: UploadPurpose): Promise<UploadResponse> {
  validateImage(file);
  const form = new FormData();
  form.append('file', file);
  form.append('purpose', purpose);
  const response = await apiClient.upload<UploadResponse>('/api/v1/uploads', form);
  return validateUploadResponse(response, file, purpose);
}

/** canonical 관리 URL만 API origin에서 해석한다. 외부 legacy 프로필 URL은 일반 img로 읽는다. */
export function resolveManagedImage(src: string): { href: string; path: string } | null {
  try {
    const base = new URL(getApiBaseUrl());
    const url = new URL(src, base);
    if (
      url.origin !== base.origin ||
      url.search ||
      url.hash ||
      url.username ||
      url.password ||
      !/^\/api\/v1\/uploads\/[^/]+\/content$/.test(url.pathname)
    ) {
      return null;
    }
    return { href: url.href, path: `${url.pathname}${url.search}` };
  } catch {
    return null;
  }
}

export async function fetchManagedImage(path: string): Promise<Blob> {
  return apiClient.getBlob(path);
}
