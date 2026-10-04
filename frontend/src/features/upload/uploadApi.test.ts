import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import { uploadImage } from './uploadApi';

function ok<T>(data: T) {
  const body = { success: true, data, error: null, timestamp: '' };
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

describe('uploadApi', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  const uploadId = '550e8400-e29b-41d4-a716-446655440000';

  beforeEach(() => {
    resetApiClient();
    setAccessToken('test-token');
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    resetApiClient();
  });

  it('multipart 업로드는 JSON Content-Type을 강제로 붙이지 않는다', async () => {
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    const response = {
      id: uploadId,
      imageUrl: `/api/v1/uploads/${uploadId}/content`,
      contentType: 'image/png',
      size: file.size,
      purpose: 'PROFILE_IMAGE',
    } as const;
    fetchMock.mockResolvedValueOnce(ok(response));

    await expect(uploadImage(file, 'PROFILE_IMAGE')).resolves.toEqual(response);

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('POST');
    expect(init.headers).toEqual({ Authorization: 'Bearer test-token' });
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBe(file);
    expect((init.body as FormData).get('purpose')).toBe('PROFILE_IMAGE');
  });

  it.each([
    ['외부 URL', (id: string) => ({ id, imageUrl: 'https://evil.example/image.png' })],
    ['blob URL', (id: string) => ({ id, imageUrl: 'blob:http://localhost/image' })],
    ['data URL', (id: string) => ({ id, imageUrl: 'data:image/png;base64,AAAA' })],
    ['query가 붙은 URL', (id: string) => ({ id, imageUrl: `/api/v1/uploads/${id}/content?x=1` })],
    ['경로의 id가 다른 URL', (id: string) => ({ id, imageUrl: `/api/v1/uploads/550e8400-e29b-41d4-a716-446655440001/content` })],
    ['UUID가 아닌 id', () => ({ id: 'upload-1', imageUrl: '/api/v1/uploads/upload-1/content' })],
  ])('canonical이 아닌 %s 업로드 응답은 저장하지 않는다', async (_label, values) => {
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock.mockResolvedValueOnce(
      ok({
        ...values(uploadId),
        contentType: 'image/png',
        size: file.size,
        purpose: 'PROFILE_IMAGE',
      }),
    );

    await expect(uploadImage(file, 'PROFILE_IMAGE')).rejects.toThrow('응답이 올바르지');
  });

  it('요청 purpose·MIME·바이트 수가 다른 업로드 응답은 저장하지 않는다', async () => {
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    const canonical = `/api/v1/uploads/${uploadId}/content`;
    fetchMock
      .mockResolvedValueOnce(
        ok({
          id: uploadId,
          imageUrl: canonical,
          contentType: 'image/png',
          size: file.size,
          purpose: 'POST_IMAGE',
        }),
      )
      .mockResolvedValueOnce(
        ok({
          id: uploadId,
          imageUrl: canonical,
          contentType: 'image/svg+xml',
          size: file.size,
          purpose: 'PROFILE_IMAGE',
        }),
      )
      .mockResolvedValueOnce(
        ok({
          id: uploadId,
          imageUrl: canonical,
          contentType: 'image/png',
          size: file.size + 1,
          purpose: 'PROFILE_IMAGE',
        }),
      );

    await expect(uploadImage(file, 'PROFILE_IMAGE')).rejects.toThrow('응답이 올바르지');
    await expect(uploadImage(file, 'PROFILE_IMAGE')).rejects.toThrow('응답이 올바르지');
    await expect(uploadImage(file, 'PROFILE_IMAGE')).rejects.toThrow('응답이 올바르지');
  });

  it('빈 파일·5MiB 초과·허용되지 않은 MIME은 요청하지 않는다', async () => {
    await expect(
      uploadImage(new File([], 'empty.png', { type: 'image/png' }), 'POST_IMAGE'),
    ).rejects.toThrow('이미지 파일이 비어');
    await expect(
      uploadImage(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), 'POST_IMAGE'),
    ).rejects.toThrow('5MB');
    await expect(
      uploadImage(new File(['text'], 'script.svg', { type: 'image/svg+xml' }), 'POST_IMAGE'),
    ).rejects.toThrow('지원하지 않는 이미지 형식');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
