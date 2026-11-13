import {
  apiClient,
  ApiRequestError,
  getAuthSessionVersion,
  getApiBaseUrl,
} from '../../lib/apiClient';
import { resolveApiOrigin } from '../../lib/apiBase';
import { resolveUploadTransport } from './uploadTransport';

export type UploadPurpose = 'PROFILE_IMAGE' | 'POST_THUMBNAIL' | 'POST_IMAGE';

export interface UploadResponse {
  id: string;
  imageUrl: string;
  contentType: string;
  size: number;
  purpose: UploadPurpose;
}

export interface UploadOptions {
  /** Cancels an in-flight S3 PUT and prevents a late response from being used. */
  signal?: AbortSignal;
}

interface PresignedUploadResponse {
  id: string;
  uploadUrl: string;
  method: 'PUT';
  headers: Record<string, string>;
  expiresAt: string;
}

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;
const UPLOAD_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const S3_HOST_PATTERN = /(^|[.-])s3([.-]|$)/i;

class UploadPreconditionError extends Error {
  constructor() {
    super('이미 업로드된 객체를 확인하는 중입니다.');
    this.name = 'UploadPreconditionError';
  }
}

class UploadResponseError extends Error {
  readonly status: number;

  constructor(status: number) {
    super('이미지 저장소에 업로드하지 못했습니다.');
    this.name = 'UploadResponseError';
    this.status = status;
  }
}

function throwIfAborted(signal?: AbortSignal): void {
  if (!signal?.aborted) return;
  const error = new Error('이미지 업로드가 취소되었습니다.');
  error.name = 'AbortError';
  throw error;
}

function assertAuthGeneration(expectedVersion: number): void {
  if (getAuthSessionVersion() === expectedVersion) return;
  throw new Error('로그인 상태가 바뀌어 이미지 업로드를 중단했습니다.');
}

function validateImage(file: File): void {
  if (file.size < 1) throw new Error('이미지 파일이 비어 있습니다.');
  if (file.size > MAX_IMAGE_BYTES) throw new Error('이미지 파일은 최대 5MB까지 업로드할 수 있습니다.');
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) {
    throw new Error('지원하지 않는 이미지 형식입니다. JPEG, PNG, WebP, GIF만 사용할 수 있습니다.');
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof Error && error.name === 'NetworkError');
}

function isRetryableCompleteError(error: unknown): boolean {
  return isNetworkError(error)
    || (error instanceof ApiRequestError && (error.status === 503 || error.code === 'UPLOAD_008'));
}

function isMissingObjectError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.code === 'UPLOAD_005';
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

async function sha256Base64(file: File, signal?: AbortSignal): Promise<string> {
  throwIfAborted(signal);
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  throwIfAborted(signal);
  return bytesToBase64(new Uint8Array(digest));
}

function headerValue(headers: Record<string, string>, name: string): string | undefined {
  const normalizedName = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === normalizedName) return value;
  }
  return undefined;
}

function validateS3Url(uploadUrl: string): void {
  let url: URL;
  try {
    url = new URL(uploadUrl);
  } catch {
    throw new Error('서버가 발급한 S3 업로드 URL이 올바르지 않습니다.');
  }
  const hostname = url.hostname.toLowerCase();
  const isAmazonS3 =
    (hostname.endsWith('.amazonaws.com') || hostname.endsWith('.amazonaws.com.cn'))
    && S3_HOST_PATTERN.test(hostname);
  if (
    url.protocol !== 'https:'
    || !isAmazonS3
    || url.username
    || url.password
    || url.hash
    || (url.port !== '' && url.port !== '443')
    || !url.search
  ) {
    throw new Error('서버가 발급한 S3 업로드 URL이 올바르지 않습니다.');
  }
}

function validateSignedHeaders(
  value: unknown,
  file: File,
  checksumSha256: string,
): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('서버가 발급한 S3 서명 헤더가 올바르지 않습니다.');
  }

  const source = value as Record<string, unknown>;
  const result: Record<string, string> = {};
  const seen = new Set<string>();
  for (const [rawName, rawValue] of Object.entries(source)) {
    const name = rawName.trim();
    const normalizedName = name.toLowerCase();
    if (!name || typeof rawValue !== 'string' || seen.has(normalizedName)) {
      throw new Error('서버가 발급한 S3 서명 헤더가 올바르지 않습니다.');
    }
    seen.add(normalizedName);
    if (['authorization', 'cookie', 'set-cookie', 'proxy-authorization'].includes(normalizedName)) {
      throw new Error('S3 업로드에는 앱 인증 헤더를 전달할 수 없습니다.');
    }
    // Browser-controlled headers are supplied by fetch. JavaScript must not
    // forge Content-Length or Host even when a signer includes them implicitly.
    if (normalizedName === 'content-length' || normalizedName === 'host') continue;
    result[name] = rawValue;
  }

  if (headerValue(source as Record<string, string>, 'content-type') !== file.type
    || headerValue(source as Record<string, string>, 'x-amz-checksum-sha256') !== checksumSha256
    || headerValue(source as Record<string, string>, 'if-none-match') !== '*') {
    throw new Error('S3 업로드에 필요한 서명 헤더가 없습니다.');
  }
  return result;
}

function validatePresignedUploadResponse(
  value: unknown,
  file: File,
  checksumSha256: string,
): PresignedUploadResponse {
  if (!value || typeof value !== 'object') {
    throw new Error('이미지 업로드 URL 응답이 올바르지 않습니다.');
  }
  const response = value as Partial<PresignedUploadResponse>;
  if (
    typeof response.id !== 'string'
    || !UPLOAD_ID_PATTERN.test(response.id)
    || typeof response.uploadUrl !== 'string'
    || response.method !== 'PUT'
    || typeof response.expiresAt !== 'string'
    || Number.isNaN(Date.parse(response.expiresAt))
  ) {
    throw new Error('이미지 업로드 URL 응답이 올바르지 않습니다.');
  }
  validateS3Url(response.uploadUrl);
  const headers = validateSignedHeaders(response.headers, file, checksumSha256);
  return { ...response, headers } as PresignedUploadResponse;
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

async function uploadLocalImage(
  file: File,
  purpose: UploadPurpose,
  options: UploadOptions,
): Promise<UploadResponse> {
  const authVersion = getAuthSessionVersion();
  throwIfAborted(options.signal);
  const form = new FormData();
  form.append('file', file);
  form.append('purpose', purpose);
  const response = await apiClient.upload<UploadResponse>('/api/v1/uploads', form, options.signal);
  throwIfAborted(options.signal);
  assertAuthGeneration(authVersion);
  return validateUploadResponse(response, file, purpose);
}

async function putToS3(
  intent: PresignedUploadResponse,
  file: File,
  options: UploadOptions,
): Promise<void> {
  throwIfAborted(options.signal);
  const response = await fetch(intent.uploadUrl, {
    method: intent.method,
    headers: intent.headers,
    body: file,
    credentials: 'omit',
    redirect: 'error',
    referrerPolicy: 'no-referrer',
    signal: options.signal,
  });
  if (response.status === 412) throw new UploadPreconditionError();
  if (!response.ok) throw new UploadResponseError(response.status);
}

async function completeUpload(
  intent: PresignedUploadResponse,
  file: File,
  purpose: UploadPurpose,
  authVersion: number,
  options: UploadOptions,
): Promise<UploadResponse> {
  for (let attempt = 0; ; attempt += 1) {
    throwIfAborted(options.signal);
    assertAuthGeneration(authVersion);
    try {
      const response = await apiClient.post<UploadResponse>(
        `/api/v1/uploads/${intent.id}/complete`,
        undefined,
        options.signal,
      );
      throwIfAborted(options.signal);
      assertAuthGeneration(authVersion);
      const validated = validateUploadResponse(response, file, purpose);
      if (validated.id !== intent.id || validated.contentType !== file.type) {
        throw new Error('이미지 업로드 완료 응답이 요청한 파일과 일치하지 않습니다.');
      }
      return validated;
    } catch (error) {
      if (attempt >= 1 || isAbortError(error) || !isRetryableCompleteError(error)) throw error;
    }
  }
}

async function completeAfterAmbiguousPut(
  intent: PresignedUploadResponse,
  file: File,
  purpose: UploadPurpose,
  authVersion: number,
  options: UploadOptions,
): Promise<UploadResponse> {
  try {
    return await completeUpload(intent, file, purpose, authVersion, options);
  } catch (error) {
    if (!isMissingObjectError(error)) throw error;
    // A network failure can mean that PUT succeeded or that it never reached
    // S3. Only a confirmed missing object permits one safe PUT retry.
    assertAuthGeneration(authVersion);
    try {
      await putToS3(intent, file, options);
    } catch (retryError) {
      if (!(retryError instanceof UploadPreconditionError)) throw retryError;
    }
    return completeUpload(intent, file, purpose, authVersion, options);
  }
}

async function uploadToS3(
  file: File,
  purpose: UploadPurpose,
  options: UploadOptions,
): Promise<UploadResponse> {
  const authVersion = getAuthSessionVersion();
  throwIfAborted(options.signal);
  const checksumSha256 = await sha256Base64(file, options.signal);
  assertAuthGeneration(authVersion);

  const intentValue = await apiClient.post<PresignedUploadResponse>(
    '/api/v1/uploads/presigned-url',
    {
      fileName: file.name,
      contentType: file.type,
      size: file.size,
      purpose,
      checksumSha256,
    },
    options.signal,
  );
  throwIfAborted(options.signal);
  assertAuthGeneration(authVersion);
  const intent = validatePresignedUploadResponse(intentValue, file, checksumSha256);

  try {
    await putToS3(intent, file, options);
  } catch (error) {
    if (error instanceof UploadPreconditionError) {
      // S3 already owns the key. The idempotent complete call is the source of
      // truth for whether that object matches this intent.
      return completeUpload(intent, file, purpose, authVersion, options);
    }
    if (isNetworkError(error)) {
      return completeAfterAmbiguousPut(intent, file, purpose, authVersion, options);
    }
    throw error;
  }
  return completeUpload(intent, file, purpose, authVersion, options);
}

export async function uploadImage(
  file: File,
  purpose: UploadPurpose,
  options: UploadOptions = {},
): Promise<UploadResponse> {
  validateImage(file);
  const transport = resolveUploadTransport(
    import.meta.env.VITE_UPLOAD_TRANSPORT,
    import.meta.env.PROD,
  );
  if (transport === 's3') return uploadToS3(file, purpose, options);
  return uploadLocalImage(file, purpose, options);
}

/** canonical 관리 URL만 API origin에서 해석한다. 외부 legacy 프로필 URL은 일반 img로 읽는다. */
export function resolveManagedImage(src: string): { href: string; path: string } | null {
  try {
    const locationOrigin = typeof window === 'undefined' ? '' : window.location.origin;
    const base = resolveApiOrigin(getApiBaseUrl(), locationOrigin);
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
