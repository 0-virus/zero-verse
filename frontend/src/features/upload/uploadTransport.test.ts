import { describe, expect, it } from 'vitest';
import {
  assertProductionUploadTransport,
  productionUploadTransportError,
  resolveUploadTransport,
} from './uploadTransport';

describe('upload transport selection', () => {
  it('defaults to local outside production and S3 in production', () => {
    expect(resolveUploadTransport(undefined, false)).toBe('local');
    expect(resolveUploadTransport(undefined, true)).toBe('s3');
    expect(resolveUploadTransport('  s3 ', false)).toBe('s3');
    expect(resolveUploadTransport('LOCAL', false)).toBe('local');
  });

  it('rejects local or unknown transport in production', () => {
    expect(productionUploadTransportError('local')).toContain('s3');
    expect(productionUploadTransportError('wat')).toContain('local or s3');
    expect(() => assertProductionUploadTransport('local')).toThrow('s3');
    expect(() => resolveUploadTransport('local', true)).toThrow('s3');
    expect(() => resolveUploadTransport('wat', false)).toThrow('local or s3');
  });

  it('accepts an explicitly selected local or S3 transport outside production', () => {
    expect(resolveUploadTransport('local', false)).toBe('local');
    expect(resolveUploadTransport('s3', false)).toBe('s3');
    expect(productionUploadTransportError('s3')).toBeNull();
  });
});
