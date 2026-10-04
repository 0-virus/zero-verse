import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ManagedImage } from './ManagedImage';
import { useOptionalAuth } from '../../lib/authContext';
import { resetApiClient, setAccessToken } from '../../lib/apiClient';

vi.mock('../../lib/authContext', () => ({
  useOptionalAuth: vi.fn(),
}));

const mockedUseOptionalAuth = vi.mocked(useOptionalAuth);

function imageResponse(bytes: string) {
  const blob = new Blob([bytes], { type: 'image/png' });
  return {
    ok: true,
    status: 200,
    blob: async () => blob,
  } as unknown as Response;
}

describe('ManagedImage', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let createObjectUrl: ReturnType<typeof vi.fn>;
  let revokeObjectUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetApiClient();
    setAccessToken('token-a');
    mockedUseOptionalAuth.mockReturnValue({ user: { id: 1 } } as never);
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    createObjectUrl = vi.fn((blob: Blob) => `blob:${blob.size}`);
    revokeObjectUrl = vi.fn();
    Object.defineProperty(globalThis.URL, 'createObjectURL', {
      configurable: true,
      value: createObjectUrl,
    });
    Object.defineProperty(globalThis.URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrl,
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    resetApiClient();
  });

  it('canonical image를 Bearer로 읽고 Blob URL만 렌더한다', async () => {
    fetchMock.mockResolvedValueOnce(imageResponse('canonical'));

    render(
      <ManagedImage
        src="/api/v1/uploads/upload-1/content"
        alt="본문 이미지"
        className="managed-image"
      />,
    );

    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:9'));
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v1/uploads/upload-1/content');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer token-a');
    expect(screen.getByRole('img')).toHaveClass('managed-image');
    expect(screen.getByRole('img')).not.toHaveAttribute('src', '/api/v1/uploads/upload-1/content');
  });

  it('외부 프로필 URL은 그대로 렌더하고 Bearer fetch를 하지 않는다', () => {
    render(<ManagedImage src="https://images.example/avatar.png" alt="프로필" />);

    expect(screen.getByRole('img')).toHaveAttribute('src', 'https://images.example/avatar.png');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('이미지 교체·언마운트 때 Blob URL을 revoke한다', async () => {
    fetchMock
      .mockResolvedValueOnce(imageResponse('one'))
      .mockResolvedValueOnce(imageResponse('two'));
    const view = render(<ManagedImage src="/api/v1/uploads/one/content" alt="이미지" />);
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:3'));

    view.rerender(<ManagedImage src="/api/v1/uploads/two/content" alt="이미지" />);
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:3'));
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:3');
    view.unmount();
    expect(revokeObjectUrl.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('계정이 바뀐 뒤 늦게 도착한 Blob을 새 세션에 노출하지 않는다', async () => {
    let release: ((response: Response) => void) | null = null;
    const pending = new Promise<Response>((resolve) => {
      release = resolve;
    });
    fetchMock.mockReturnValueOnce(pending);
    const view = render(<ManagedImage src="/api/v1/uploads/private/content" alt="비공개" />);
    mockedUseOptionalAuth.mockReturnValue({ user: { id: 2 } } as never);
    view.rerender(<ManagedImage src="/api/v1/uploads/private/content" alt="비공개" />);
    release!(imageResponse('old-session'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(createObjectUrl).not.toHaveBeenCalled();
    expect(screen.getByRole('img')).not.toHaveAttribute('src', expect.stringMatching(/^blob:/));
  });

  it('계정 전환 직후 동기 렌더에서도 이전 Blob을 노출하지 않는다', async () => {
    fetchMock.mockResolvedValueOnce(imageResponse('private'));
    const view = render(<ManagedImage src="/api/v1/uploads/private/content" alt="비공개" />);
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:7'));

    mockedUseOptionalAuth.mockReturnValue({ user: { id: 2 } } as never);
    view.rerender(<ManagedImage src="/api/v1/uploads/private/content" alt="비공개" />);

    expect(screen.getByRole('img')).not.toHaveAttribute('src', 'blob:7');
  });
});
