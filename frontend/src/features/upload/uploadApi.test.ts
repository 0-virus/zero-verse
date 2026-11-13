import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';
import { resolveManagedImage, uploadImage } from './uploadApi';

function ok<T>(data: T) {
  const body = { success: true, data, error: null, timestamp: '' };
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

function failure(status: number, code: string) {
  const body = {
    success: false,
    data: null,
    error: { code, message: code, details: [] },
    timestamp: '',
  };
  return {
    ok: false,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

function rawOk(status = 200) {
  return { ok: status >= 200 && status < 300, status } as unknown as Response;
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
    vi.unstubAllEnvs();
    resetApiClient();
  });

  async function loadS3Modules() {
    vi.stubEnv('PROD', false);
    vi.stubEnv('VITE_UPLOAD_TRANSPORT', 's3');
    vi.resetModules();
    const [uploadModule, clientModule] = await Promise.all([
      import('./uploadApi'),
      import('../../lib/apiClient'),
    ]);
    clientModule.resetApiClient();
    clientModule.setAccessToken('test-token');
    return { uploadImage: uploadModule.uploadImage, clientModule };
  }

  function stubChecksum() {
    const digest = new Uint8Array(32).fill(7).buffer;
    vi.stubGlobal('crypto', { subtle: { digest: vi.fn().mockResolvedValue(digest) } });
    return btoa(String.fromCharCode(...new Uint8Array(digest)));
  }

  function s3Intent(checksumSha256: string, headers: Record<string, string> = {
    'Content-Type': 'image/png',
    'x-amz-checksum-sha256': checksumSha256,
    'If-None-Match': '*',
  }) {
    return {
      id: uploadId,
      uploadUrl: 'https://zero-verse-s3.s3.ap-northeast-2.amazonaws.com/zeroverse/dev/object?X-Amz-Signature=test',
      method: 'PUT',
      headers,
      expiresAt: '2099-01-01T00:00:00',
    };
  }

  function canonical(file: File) {
    return {
      id: uploadId,
      imageUrl: `/api/v1/uploads/${uploadId}/content`,
      contentType: 'image/png',
      size: file.size,
      purpose: 'PROFILE_IMAGE' as const,
    };
  }

  it('기본 API base에서 canonical 관리 이미지의 기준 URL을 만든다', () => {
    expect(
      resolveManagedImage('/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content'),
    ).toEqual({
      href: 'http://localhost:8080/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content',
      path: '/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content',
    });
  });

  it.each([
    ['query', '/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content?sig=x'],
    ['hash', '/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content#fragment'],
    ['userinfo', 'https://user:pass@zeroverse.example/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content'],
    ['external origin', 'https://images.example/upload.png'],
  ])('managed image resolver rejects %s URLs', (_label, src) => {
    expect(resolveManagedImage(src)).toBeNull();
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

  it('local multipart 업로드는 정상 401 refresh 뒤에도 같은 auth session으로 완료한다', async () => {
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    const response = {
      id: uploadId,
      imageUrl: `/api/v1/uploads/${uploadId}/content`,
      contentType: 'image/png',
      size: file.size,
      purpose: 'PROFILE_IMAGE',
    } as const;
    fetchMock
      .mockResolvedValueOnce(failure(401, 'AUTH_002'))
      .mockResolvedValueOnce(ok({ accessToken: 'refreshed-token' }))
      .mockResolvedValueOnce(ok(response));

    await expect(uploadImage(file, 'PROFILE_IMAGE')).resolves.toEqual(response);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer refreshed-token');
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

  it('S3 transport는 checksum presign→원본 바이트 PUT→canonical complete 순서를 지킨다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(3);

    const [presignUrl, presignInit] = fetchMock.mock.calls[0];
    expect(presignUrl).toBe('http://localhost:8080/api/v1/uploads/presigned-url');
    expect(presignInit.credentials).toBe('include');
    expect(presignInit.headers.Authorization).toBe('Bearer test-token');
    expect(JSON.parse(presignInit.body)).toEqual({
      fileName: 'avatar.png',
      contentType: 'image/png',
      size: file.size,
      purpose: 'PROFILE_IMAGE',
      checksumSha256: checksum,
    });

    const [putUrl, putInit] = fetchMock.mock.calls[1];
    expect(putUrl).toContain('https://zero-verse-s3.s3.ap-northeast-2.amazonaws.com/');
    expect(putInit.method).toBe('PUT');
    expect(putInit.credentials).toBe('omit');
    expect(putInit.redirect).toBe('error');
    expect(putInit.referrerPolicy).toBe('no-referrer');
    expect(putInit.body).toBe(file);
    expect(putInit.headers).toEqual({
      'Content-Type': 'image/png',
      'x-amz-checksum-sha256': checksum,
      'If-None-Match': '*',
    });
    expect(putInit.headers.Authorization).toBeUndefined();
    expect(putInit.headers.Cookie).toBeUndefined();
    expect(putInit.headers['Content-Length']).toBeUndefined();
    expect(putInit.headers.Host).toBeUndefined();

    const [completeUrl, completeInit] = fetchMock.mock.calls[2];
    expect(completeUrl).toBe(`http://localhost:8080/api/v1/uploads/${uploadId}/complete`);
    expect(completeInit.credentials).toBe('include');
    expect(completeInit.headers.Authorization).toBe('Bearer test-token');
  });

  it('S3 presign 401 refresh 성공은 업로드 auth session을 폐기하지 않는다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(failure(401, 'AUTH_002'))
      .mockResolvedValueOnce(ok({ accessToken: 'refreshed-token' }))
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer refreshed-token');
  });

  it('S3 complete 401 refresh 성공도 동일 intent 완료를 유지한다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(failure(401, 'AUTH_002'))
      .mockResolvedValueOnce(ok({ accessToken: 'refreshed-token' }))
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[4][1].headers.Authorization).toBe('Bearer refreshed-token');
  });

  it('S3 PUT 412는 같은 intent의 idempotent complete로 복구한다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk(412))
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT');
    expect(fetchMock.mock.calls[2][0]).toContain(`/uploads/${uploadId}/complete`);
  });

  it('모호한 네트워크 PUT은 missing 확인 뒤에만 한 번 재전송한다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockRejectedValueOnce(new TypeError('network lost'))
      .mockResolvedValueOnce(failure(409, 'UPLOAD_005'))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT');
    expect(fetchMock.mock.calls[3][1].method).toBe('PUT');
  });

  it('complete 네트워크 응답 소실은 한 번만 재시도하고 deterministic 오류는 재시도하지 않는다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockRejectedValueOnce(new TypeError('response lost'))
      .mockResolvedValueOnce(ok(canonical(file)));

    await expect(upload(file, 'PROFILE_IMAGE')).resolves.toEqual(canonical(file));
    expect(fetchMock).toHaveBeenCalledTimes(4);

    fetchMock.mockReset();
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(failure(400, 'UPLOAD_007'));
    await expect(upload(file, 'PROFILE_IMAGE')).rejects.toMatchObject({ code: 'UPLOAD_007' });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('complete 응답은 presign intent id와 요청 MIME을 모두 일치시켜야 한다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });

    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(ok({
        ...canonical(file),
        id: '550e8400-e29b-41d4-a716-446655440001',
        imageUrl: '/api/v1/uploads/550e8400-e29b-41d4-a716-446655440001/content',
      }));
    await expect(upload(file, 'PROFILE_IMAGE')).rejects.toThrow('요청한 파일과 일치');
    expect(fetchMock).toHaveBeenCalledTimes(3);

    fetchMock.mockReset();
    fetchMock
      .mockResolvedValueOnce(ok(s3Intent(checksum)))
      .mockResolvedValueOnce(rawOk())
      .mockResolvedValueOnce(ok({ ...canonical(file), contentType: 'image/jpeg' }));
    await expect(upload(file, 'PROFILE_IMAGE')).rejects.toThrow('요청한 파일과 일치');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('S3 URL·서명 헤더 경계를 넘으면 PUT을 시작하지 않는다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    fetchMock.mockResolvedValueOnce(ok({
      ...s3Intent(checksum),
      uploadUrl: 'https://evil.example/upload?signature=test',
    }));
    await expect(upload(file, 'PROFILE_IMAGE')).rejects.toThrow('S3 업로드 URL');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fetchMock.mockReset();
    fetchMock.mockResolvedValueOnce(ok(s3Intent(checksum, {
      Authorization: 'Bearer leaked',
      'Content-Type': 'image/png',
      'x-amz-checksum-sha256': checksum,
      'If-None-Match': '*',
    })));
    await expect(upload(file, 'PROFILE_IMAGE')).rejects.toThrow('앱 인증 헤더');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('abort와 계정 전환 뒤의 늦은 presign 응답은 저장 단계로 진행하지 않는다', async () => {
    const checksum = stubChecksum();
    const { uploadImage: upload, clientModule } = await loadS3Modules();
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' });
    const controller = new AbortController();
    controller.abort();
    await expect(upload(file, 'PROFILE_IMAGE', { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    });
    expect(fetchMock).not.toHaveBeenCalled();

    let release: ((response: Response) => void) | null = null;
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => {
      release = resolve;
    }));
    const pending = upload(file, 'PROFILE_IMAGE');
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    clientModule.setAccessToken('other-account-token');
    release!(ok(s3Intent(checksum)));
    await expect(pending).rejects.toThrow('로그인 상태가 바뀌어');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
