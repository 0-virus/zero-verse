import test from 'node:test';
import assert from 'node:assert/strict';

import {
  assertProductionTarget,
  assertSafeRunId,
  scrubEvidence,
  isProductionObjectKey,
  cookieContract,
} from './x-deployment-production-acceptance.mjs';

test('production target guard rejects every non-production origin', () => {
  assert.doesNotThrow(() => assertProductionTarget('https://zeroverse.co.kr'));
  for (const value of [
    'http://zeroverse.co.kr',
    'https://zeroverse.co.kr.evil.test',
    'https://localhost:5173',
    'https://54.116.133.220',
    'https://zeroverse.co.kr/path',
  ]) {
    assert.throws(() => assertProductionTarget(value));
  }
});

test('runId guard accepts only deployment marker ids', () => {
  assert.doesNotThrow(() => assertSafeRunId('deployment-20261116-01234567'));
  assert.throws(() => assertSafeRunId('local-test'));
  assert.throws(() => assertSafeRunId('deployment-20261116-../../secrets'));
});

test('evidence scrub removes credentials, cookies, and signed URL values', () => {
  const value = scrubEvidence(
    'Bearer abc.def.ghi Set-Cookie: refresh_token=secret; '
      + 'https://zero-verse-s3.s3.ap-northeast-2.amazonaws.com/zeroverse/prod/id?X-Amz-Signature=sig',
  );
  assert.equal(value.includes('abc.def.ghi'), false);
  assert.equal(value.includes('refresh_token=secret'), false);
  assert.equal(value.includes('X-Amz-Signature=sig'), false);
});

test('production object scope accepts only zeroverse/prod keys', () => {
  assert.equal(isProductionObjectKey('zeroverse/prod/01234567-89ab-cdef-0123-456789abcdef'), true);
  assert.equal(isProductionObjectKey('zeroverse/dev/01234567-89ab-cdef-0123-456789abcdef'), false);
  assert.equal(isProductionObjectKey('zeroverse/prod/../other'), false);
  assert.equal(isProductionObjectKey('zeroverse/prod/'), false);
});

test('cookie contract requires secure httpOnly strict auth cookie attributes', () => {
  assert.equal(cookieContract('refresh_token=redacted; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict'), true);
  assert.equal(cookieContract('refresh_token=redacted; Path=/; HttpOnly; SameSite=Lax'), false);
});
