import { describe, expect, it } from 'vitest';
import {
  productionApiBaseError,
  resolveApiBaseUrl,
  resolveApiOrigin,
} from '../lib/apiBase';

describe('api base configuration', () => {
  it('keeps localhost for dev and test while production defaults to same-origin', () => {
    expect(resolveApiBaseUrl(undefined, false, 'http://localhost:8080')).toBe('http://localhost:8080');
    expect(resolveApiBaseUrl(undefined, true, 'http://localhost:8080')).toBe('');
    expect(resolveApiBaseUrl('   ', true, 'http://localhost:8080')).toBe('');
  });

  it('preserves an explicitly configured API origin in every mode', () => {
    expect(resolveApiBaseUrl('http://127.0.0.1:18090', false, 'http://localhost:8080')).toBe(
      'http://127.0.0.1:18090',
    );
    expect(resolveApiBaseUrl('https://api.zeroverse.example', true, '')).toBe('https://api.zeroverse.example');
    expect(resolveApiBaseUrl('https://api.zeroverse.example/', true, '')).toBe('https://api.zeroverse.example');
  });

  it('allows empty or HTTPS production values and rejects HTTP/loopback values', () => {
    expect(productionApiBaseError(undefined)).toBeNull();
    expect(productionApiBaseError('')).toBeNull();
    expect(productionApiBaseError('https://api.zeroverse.example')).toBeNull();
    expect(productionApiBaseError('http://api.zeroverse.example')).toContain('HTTPS');
    expect(productionApiBaseError('https://localhost:8080')).toContain('localhost');
    expect(productionApiBaseError('https://127.42.0.8')).toContain('loopback');
    expect(productionApiBaseError('https://service.localhost')).toContain('localhost');
    expect(productionApiBaseError('https://localhost.')).toContain('localhost');
    expect(productionApiBaseError('https://127.42.0.8.')).toContain('loopback');
    expect(productionApiBaseError('https://[::1]')).toContain('localhost');
    expect(productionApiBaseError('https://user:pass@api.zeroverse.example')).toContain('origin');
    expect(productionApiBaseError('https://api.zeroverse.example?x=1')).toContain('origin');
    expect(productionApiBaseError('https://api.zeroverse.example/#fragment')).toContain('origin');
    expect(productionApiBaseError('not a URL')).toContain('absolute HTTPS URL');
  });

  it('resolves an empty base against the browser origin', () => {
    expect(resolveApiOrigin('', 'https://zeroverse.example').origin).toBe('https://zeroverse.example');
    expect(resolveApiOrigin('http://localhost:8080', 'https://zeroverse.example').origin).toBe(
      'http://localhost:8080',
    );
  });
});
