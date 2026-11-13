import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('apiClient production environment', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('PROD', true);
    vi.stubEnv('DEV', false);
    vi.stubEnv('VITE_API_BASE_URL', undefined as unknown as string);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ success: true, data: null, error: null, timestamp: '' }),
    }));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('uses a relative API URL when production has no API origin', async () => {
    const { apiClient, getApiBaseUrl } = await import('../lib/apiClient');

    expect(getApiBaseUrl()).toBe('');
    await apiClient.get('/api/v1/auth/me');

    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/me');
  });

  it('keeps an explicit HTTPS production API origin', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.zeroverse.example');
    vi.resetModules();
    const { getApiBaseUrl } = await import('../lib/apiClient');

    expect(getApiBaseUrl()).toBe('https://api.zeroverse.example');
  });

  it('normalizes a trailing slash before building the real fetch URL', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.zeroverse.example/');
    vi.resetModules();
    const { apiClient, getApiBaseUrl } = await import('../lib/apiClient');

    expect(getApiBaseUrl()).toBe('https://api.zeroverse.example');
    await apiClient.get('/api/v1/auth/me');

    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls[0][0]).toBe('https://api.zeroverse.example/api/v1/auth/me');
  });

  it('uses the same production base for the default managed-image resolver', async () => {
    const { resolveManagedImage } = await import('../features/upload/uploadApi');

    expect(resolveManagedImage('/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content')).toEqual({
      href: 'http://localhost:3000/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content',
      path: '/api/v1/uploads/550e8400-e29b-41d4-a716-446655440000/content',
    });
  });
});
