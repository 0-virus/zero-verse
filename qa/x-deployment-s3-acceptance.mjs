#!/usr/bin/env node
/**
 * T5 direct-S3 acceptance runner.
 *
 * This is a QA-owned, fail-closed black-box harness. It never connects to the
 * user's MySQL port 13307, never reads the private runtime config, and never
 * writes presigned URLs, cookies, tokens, credentials, or object keys to the
 * receipt. A normal run requires root's explicit synthetic-runtime approval:
 * T5_AWS_WRITE_APPROVED=1 and the public runtime config. Without both, the
 * runner records NOT RUN and performs no API, browser, or AWS operation.
 *
 * Normal run example (only after root starts the approved runtime):
 *   T5_AWS_WRITE_APPROVED=1 node qa/x-deployment-s3-acceptance.mjs
 *
 * Safe preparation checks:
 *   node qa/x-deployment-s3-acceptance.mjs --self-check
 */

import { createRequire } from 'node:module';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { access, mkdir, readFile, writeFile } from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import { resolve } from 'node:path';
import { once } from 'node:events';
import net from 'node:net';

const require = createRequire(import.meta.url);
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const suffix = `${Date.now().toString(36)}${randomBytes(5).toString('hex')}`;
const selfCheck = process.argv.includes('--self-check');
const publicConfigPath = resolve(root, process.env.T5_RUNTIME_PUBLIC_CONFIG ?? 'build/x-deployment-runtime-20261113/public.json');
const outputDir = resolve(root, process.env.T5_OUTPUT_DIR ?? `build/x-deployment-s3-acceptance-${suffix}`);
const playwrightModule = process.env.T5_PLAYWRIGHT_MODULE
  ?? '/Users/zerovirus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright';
const chromeExecutable = process.env.T5_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const maxBytes = 5 * 1024 * 1024;
const approvedMySqlPort = 13337;
const forbiddenMySqlPort = 13307;
const requiredChecks = [
  'runtime-config', 'mysql-synthetic-port', 'api-availability',
  'synthetic-register-owner', 'synthetic-register-friend', 'setup-owner', 'setup-friend',
  's3-cors-preflight', 'presign-prefix-bound', 'small-put', 'exact-5mib-put',
  'four-concurrent-5mib-reads', 'signed-header-tamper-denied', 'oversize-rejected',
  'mime-mismatch-rejected', 'checksum-mismatch-rejected',
  'if-none-match-replay', 'complete-success', 'complete-idempotent',
  'malformed-complete-not-a-uuid', 'malformed-content-not-a-uuid',
  'malformed-complete-shortened-uuid', 'malformed-content-shortened-uuid',
  'malformed-complete-uppercase-uuid', 'malformed-content-uppercase-uuid',
  'missing-complete-upload003', 'missing-content-upload003',
  'pending-complete-blocked', 'pending-binding-blocked', 'owner-content-read',
  'friend-content-denied', 'anonymous-content-denied', 'anonymous-s3-denied',
  'browser-product-upload', 'browser-put-no-app-auth', 'browser-cors',
  'browser-oversize-no-presign', 'secret-free-evidence',
];

const evidence = {
  schema: 'zeroverse.x-deployment-s3-acceptance.v1',
  status: 'NOT RUN',
  startedAt: new Date().toISOString(),
  runtime: null,
  checks: [],
  failures: [],
  requiredChecks,
  mutationBoundary: {
    database: 'synthetic runtime only; no SQL issued by this harness',
    s3: 'approved deployment prefix only; cleanup is root-owned and not automatic',
    secrets: 'tokens, cookies, credentials, presigned URLs and object keys are not recorded',
  },
};

let apiContext;
let guestContext;
let browser;
let browserContext;

function scrub(value) {
  return String(value)
    .replace(/https?:\/\/[^\s)]+/gi, '[url-redacted]')
    .replace(/Bearer\s+[A-Za-z0-9._~-]+/gi, 'Bearer [redacted]')
    .replace(/(?:password|token|cookie|secret|authorization|signature)[=:][^\s,;]+/gi, '$1=[redacted]')
    .replace(/X-Amz-[A-Za-z-]+=[^&\s]+/gi, 'X-Amz-[redacted]')
    .replace(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g, '[synthetic-email]');
}

function check(id, ok, detail, options = {}) {
  const result = { id, ok: Boolean(ok), required: options.required !== false, detail: scrub(detail) };
  evidence.checks.push(result);
  return result.ok;
}

function markNotRun(id, detail) {
  if (!evidence.checks.some((entry) => entry.id === id)) check(id, false, `NOT RUN: ${detail}`);
}

function requireExactRuntime(runtime) {
  const expectedPrefix = `zeroverse/dev/${runtime.runId}/`;
  if (!/^deployment-[A-Za-z0-9-]+$/.test(runtime.runId)) throw new Error('runtime runId is not a safe deployment identifier');
  if (runtime.prefix !== expectedPrefix) throw new Error('runtime S3 prefix is not bound to the runtime runId');
  if (runtime.bucket !== 'zero-verse-s3' || runtime.region !== 'ap-northeast-2') throw new Error('runtime S3 location is not the approved development bucket');
  if (runtime.profile !== 'zeroverse-s3-dev') throw new Error('runtime AWS profile is not the approved development profile');
  if (runtime.mysqlPort !== approvedMySqlPort || runtime.mysqlPort === forbiddenMySqlPort) throw new Error('runtime MySQL port is not the approved synthetic port');
  if (runtime.apiBase !== 'http://localhost:18092') throw new Error('runtime API base is not http://localhost:18092');
  if (runtime.frontendOrigin !== 'http://localhost:5173') throw new Error('runtime frontend origin is not http://localhost:5173');
  if (!/^zeroverse\/dev\/deployment-[A-Za-z0-9-]+\/$/.test(runtime.prefix)) throw new Error('runtime S3 prefix contains an unsafe path');
  return true;
}

function assertNoSecrets(serialized) {
  if (/(?:Bearer\s|X-Amz-Signature|X-Amz-Credential|Set-Cookie|refreshToken|accessToken|password\s*[:=])/i.test(serialized)) {
    throw new Error('receipt secret guard rejected sensitive output');
  }
}

async function exists(path) {
  try {
    await access(path, fsConstants.R_OK);
    return true;
  } catch {
    return false;
  }
}

async function loadPublicRuntime() {
  if (!(await exists(publicConfigPath))) throw new Error('public runtime config is missing');
  const runtime = JSON.parse(await readFile(publicConfigPath, 'utf8'));
  requireExactRuntime(runtime);
  return runtime;
}

async function writeEvidence(status, error = null) {
  evidence.status = status;
  if (error) evidence.failures.push(scrub(error));
  evidence.finishedAt = new Date().toISOString();
  const serialized = JSON.stringify(evidence, null, 2) + '\n';
  assertNoSecrets(serialized);
  await mkdir(outputDir, { recursive: true });
  const path = resolve(outputDir, 'evidence.json');
  await writeFile(path, serialized, 'utf8');
  return path;
}

function checksum(bytes) {
  return createHash('sha256').update(bytes).digest('base64');
}

function differentChecksum(value) {
  const replacement = value[0] === 'A' ? 'B' : 'A';
  return replacement + value.slice(1);
}

function smallPng() {
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  );
}

function paddedPng(size) {
  const bytes = Buffer.alloc(size);
  smallPng().copy(bytes);
  return bytes;
}

function dataOf(result) {
  return result?.body?.data ?? null;
}

function errorCode(result) {
  return result?.body?.error?.code ?? null;
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function apiCall(method, path, token, body) {
  const headers = { Origin: runtime.frontendOrigin };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await apiContext.fetch(path, {
    method,
    headers,
    data: body,
  });
  return {
    status: response.status(),
    headers: response.headers(),
    body: await safeJson(response),
  };
}

async function binaryCall(path, token) {
  const headers = { Origin: runtime.frontendOrigin };
  if (token) headers.Authorization = `Bearer ${token}`;
  const context = token ? apiContext : guestContext;
  if (!context) throw new Error('guest request context is not initialized');
  const response = await context.fetch(path, { method: 'GET', headers });
  return {
    status: response.status(),
    headers: response.headers(),
    body: await response.body(),
  };
}

async function registerAndSetup(label) {
  const random = randomBytes(5).toString('hex');
  const account = {
    email: `t5-${label}-${random}@example.test`,
    password: `T5!${label}A${random}9`,
    name: `T5 ${label}`,
    nickname: `t5${label}${random}`.slice(0, 20),
    birthDate: '1990-01-01',
    slug: `t5-${label}-${random}`,
  };
  const registered = await apiCall('POST', '/api/v1/auth/register', undefined, {
    email: account.email,
    password: account.password,
    name: account.name,
    nickname: account.nickname,
    birthDate: account.birthDate,
  });
  check(`synthetic-register-${label}`, registered.status === 201, 'synthetic account registered');
  if (registered.status !== 201) throw new Error(`${label} registration did not return 201`);

  const signed = await apiCall('POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  const token = dataOf(signed)?.accessToken;
  check(`signin-${label}`, signed.status === 200 && typeof token === 'string', 'access token retained in memory only');
  if (!token) throw new Error(`${label} sign-in failed`);

  const setup = await apiCall('PUT', '/api/v1/blogs/me/initial-setup', token, {
    title: `T5 ${label} blog`,
    urlSlug: account.slug,
    description: 'synthetic T5 deployment fixture',
  });
  check(`setup-${label}`, setup.status === 200, 'synthetic blog setup completed');
  if (setup.status !== 200) throw new Error(`${label} initial setup failed`);
  return { ...account, token, id: Number(dataOf(await apiCall('GET', '/api/v1/auth/me', token))?.id) };
}

function s3HostFor(runtimeValue) {
  return `${runtimeValue.bucket}.s3.${runtimeValue.region}.amazonaws.com`;
}

function objectKeyFromPresigned(presigned, runtimeValue) {
  const url = new URL(presigned.uploadUrl);
  const expectedHosts = new Set([
    s3HostFor(runtimeValue),
    `${runtimeValue.bucket}.s3.${runtimeValue.region}.amazonaws.com.cn`,
  ]);
  if (!expectedHosts.has(url.hostname)) throw new Error('presigned host is outside the approved S3 bucket/region');
  const key = decodeURIComponent(url.pathname.replace(/^\/+/, ''));
  if (!key.startsWith(runtimeValue.prefix)) throw new Error('presigned object key escaped the approved deployment prefix');
  if (!key.slice(runtimeValue.prefix.length)) throw new Error('presigned object key has no server suffix');
  return { url, key };
}

function safePresignedSummary(presigned, runtimeValue) {
  objectKeyFromPresigned(presigned, runtimeValue);
  const headers = presigned.headers;
  return {
    idShape: typeof presigned.id === 'string' && /^[0-9a-f-]{36}$/i.test(presigned.id),
    method: presigned.method,
    host: new URL(presigned.uploadUrl).hostname,
    signedHeaderNames: Object.keys(headers ?? {}).map((name) => name.toLowerCase()).sort(),
  };
}

async function presign(owner, bytes, contentType, purpose = 'PROFILE_IMAGE', requestedChecksum = checksum(bytes)) {
  const result = await apiCall('POST', '/api/v1/uploads/presigned-url', owner.token, {
    fileName: `t5-${purpose.toLowerCase()}.png`,
    contentType,
    size: bytes.length,
    purpose,
    checksumSha256: requestedChecksum,
  });
  return result;
}

async function rawPut(presigned, bytes) {
  const response = await fetch(presigned.uploadUrl, {
    method: presigned.method,
    headers: presigned.headers,
    body: bytes,
    redirect: 'error',
  });
  await response.arrayBuffer();
  return { status: response.status, headers: Object.fromEntries(response.headers.entries()) };
}

async function unsignedS3Get(presigned) {
  const url = new URL(presigned.uploadUrl);
  url.search = '';
  const response = await fetch(url, { method: 'GET', redirect: 'error' });
  await response.arrayBuffer();
  return response.status;
}

async function corsPreflight(presigned) {
  const response = await fetch(presigned.uploadUrl, {
    method: 'OPTIONS',
    headers: {
      Origin: runtime.frontendOrigin,
      'Access-Control-Request-Method': 'PUT',
      'Access-Control-Request-Headers': 'content-type,if-none-match,x-amz-checksum-sha256',
    },
  });
  await response.arrayBuffer();
  const headers = Object.fromEntries(response.headers.entries());
  return {
    status: response.status,
    allowOrigin: headers['access-control-allow-origin'] ?? '',
    allowMethods: headers['access-control-allow-methods'] ?? '',
    allowHeaders: headers['access-control-allow-headers'] ?? '',
  };
}

async function waitForTcp(host, port, timeoutMs = 5000) {
  return new Promise((resolvePromise, reject) => {
    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`synthetic MySQL port ${port} did not accept a TCP connection`));
    }, timeoutMs);
    socket.once('connect', () => {
      clearTimeout(timer);
      socket.end();
      resolvePromise(true);
    });
    socket.once('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

async function runApiWorkflow(owner, friend) {
  const png = smallPng();
  const pngChecksum = checksum(png);
  const intentResult = await presign(owner, png, 'image/png');
  const intent = dataOf(intentResult);
  check('presign-prefix-bound', intentResult.status === 201 && Boolean(intent), 'presign issued under the approved prefix');
  if (!intent) throw new Error('small presign response missing');
  const summary = safePresignedSummary(intent, runtime);
  check('presign-response-shape', summary.idShape && summary.method === 'PUT', 'presign response shape and S3 host are valid', { required: false });

  const cors = await corsPreflight(intent);
  const corsAllowedHeaders = cors.allowHeaders
    .split(',')
    .map((header) => header.trim().toLowerCase())
    .filter(Boolean);
  const corsAllowedMethods = cors.allowMethods
    .split(',')
    .map((method) => method.trim().toUpperCase())
    .filter(Boolean);
  check(
    's3-cors-preflight',
    cors.status === 200
      && cors.allowOrigin === runtime.frontendOrigin
      && corsAllowedMethods.includes('PUT')
      && ['content-type', 'if-none-match', 'x-amz-checksum-sha256'].every((header) => corsAllowedHeaders.includes(header)),
    'approved development origin/method/signed headers accepted by S3 preflight',
  );

  const put = await rawPut(intent, png);
  check('small-put', put.status >= 200 && put.status < 300, 'small valid PNG PUT completed');
  const replay = await rawPut(intent, png);
  check('if-none-match-replay', replay.status === 412, 'sequential replay was rejected by If-None-Match');

  const complete = await apiCall('POST', `/api/v1/uploads/${intent.id}/complete`, owner.token);
  const completeData = dataOf(complete);
  const canonical = typeof completeData?.imageUrl === 'string' ? completeData.imageUrl : null;
  check(
    'complete-success',
    complete.status === 200
      && completeData?.id === intent.id
      && completeData?.contentType === 'image/png'
      && completeData?.size === png.length,
    'complete created verified canonical metadata',
  );
  if (!canonical) throw new Error('complete response missing canonical metadata');

  const duplicate = await apiCall('POST', `/api/v1/uploads/${intent.id}/complete`, owner.token);
  const duplicateData = dataOf(duplicate);
  check(
    'complete-idempotent',
    duplicate.status === 200
      && duplicateData?.id === completeData.id
      && duplicateData?.imageUrl === completeData.imageUrl
      && duplicateData?.size === completeData.size,
    'duplicate complete returned the same metadata',
  );

  // A syntactically valid but non-existent UUID must reach the resource layer
  // (UPLOAD_003/404), while malformed and non-canonical path IDs are input
  // errors (VALIDATION_001/400). These calls are API-only; they do not touch S3.
  const missingId = `f${randomUUID().slice(1)}`;
  const malformedIds = [
    ['not-a-uuid', 'not-a-uuid'],
    ['shortened-uuid', missingId.slice(0, -1)],
    ['uppercase-uuid', missingId.toUpperCase()],
  ];
  for (const [label, idValue] of malformedIds) {
    const completeMalformed = await apiCall('POST', `/api/v1/uploads/${encodeURIComponent(idValue)}/complete`, owner.token);
    check(
      `malformed-complete-${label}`,
      completeMalformed.status === 400 && errorCode(completeMalformed) === 'VALIDATION_001',
      `S3 complete ${label} UUID returned VALIDATION_001/400`,
    );
    const contentMalformed = await apiCall('GET', `/api/v1/uploads/${encodeURIComponent(idValue)}/content`, owner.token);
    check(
      `malformed-content-${label}`,
      contentMalformed.status === 400 && errorCode(contentMalformed) === 'VALIDATION_001',
      `S3 content ${label} UUID returned VALIDATION_001/400`,
    );
  }
  const missingComplete = await apiCall('POST', `/api/v1/uploads/${missingId}/complete`, owner.token);
  check(
    'missing-complete-upload003',
    missingComplete.status === 404 && errorCode(missingComplete) === 'UPLOAD_003',
    'canonical but missing S3 complete UUID returned UPLOAD_003/404',
  );
  const missingContent = await apiCall('GET', `/api/v1/uploads/${missingId}/content`, owner.token);
  check(
    'missing-content-upload003',
    missingContent.status === 404 && errorCode(missingContent) === 'UPLOAD_003',
    'canonical but missing S3 content UUID returned UPLOAD_003/404',
  );

  const exactBytes = paddedPng(maxBytes);
  const exactIntentResult = await presign(owner, exactBytes, 'image/png', 'POST_IMAGE');
  const exactIntent = dataOf(exactIntentResult);
  if (!exactIntent) throw new Error('exact 5MiB presign response missing');
  safePresignedSummary(exactIntent, runtime);
  const exactPut = await rawPut(exactIntent, exactBytes);
  const exactComplete = await apiCall('POST', `/api/v1/uploads/${exactIntent.id}/complete`, owner.token);
  const exactData = dataOf(exactComplete);
  const exactCanonical = typeof exactData?.imageUrl === 'string' ? exactData.imageUrl : null;
  check(
    'exact-5mib-put',
    exactPut.status >= 200 && exactPut.status < 300
      && exactComplete.status === 200
      && exactData?.contentType === 'image/png'
      && exactData?.size === maxBytes,
    'exactly 5MiB PNG PUT and bounded complete succeeded',
  );
  if (!exactCanonical) throw new Error('exact 5MiB complete response missing canonical metadata');
  const concurrentReads = await Promise.all(
    Array.from({ length: 4 }, () => binaryCall(exactCanonical, owner.token)),
  );
  check(
    'four-concurrent-5mib-reads',
    concurrentReads.every((result) => result.status === 200 && result.body.length === maxBytes),
    'four simultaneous canonical reads returned exact 5MiB bytes; buffers are released after this check',
  );
  concurrentReads.length = 0;

  const ownerRead = await binaryCall(canonical, owner.token);
  check('owner-content-read', ownerRead.status === 200 && Buffer.compare(ownerRead.body, png) === 0, 'owner canonical read returned exact bytes');
  const friendRead = await binaryCall(canonical, friend.token);
  check('friend-content-denied', [401, 403, 404].includes(friendRead.status), 'unbound image is not readable by another account');
  const anonymousRead = await binaryCall(canonical);
  check('anonymous-content-denied', [401, 403, 404].includes(anonymousRead.status), 'unbound image is not readable anonymously');
  const anonymousS3 = await unsignedS3Get(intent);
  check('anonymous-s3-denied', anonymousS3 === 403, 'unsigned direct S3 GET is denied for the private object');

  const tamperIntentResult = await presign(owner, png, 'image/png', 'POST_IMAGE');
  const tamperIntent = dataOf(tamperIntentResult);
  if (!tamperIntent) throw new Error('signed-header tamper presign response missing');
  safePresignedSummary(tamperIntent, runtime);
  const tamperedHeaders = { ...tamperIntent.headers };
  const contentTypeHeader = Object.keys(tamperedHeaders)
    .find((name) => name.toLowerCase() === 'content-type');
  if (!contentTypeHeader) throw new Error('signed-header tamper fixture lacks content-type');
  tamperedHeaders[contentTypeHeader] = 'image/jpeg';
  const tamperPut = await rawPut({ ...tamperIntent, headers: tamperedHeaders }, png);
  check(
    'signed-header-tamper-denied',
    tamperPut.status === 400 || tamperPut.status === 403,
    'tampered signed content-type was rejected by S3',
  );

  const pendingResult = await presign(owner, png, 'image/png');
  const pending = dataOf(pendingResult);
  if (!pending) throw new Error('pending presign response missing');
  safePresignedSummary(pending, runtime);
  const profile = dataOf(await apiCall('GET', '/api/v1/users/me', owner.token));
  const pendingBind = await apiCall('PUT', '/api/v1/users/me', owner.token, {
    name: profile?.name,
    nickname: profile?.nickname,
    bio: profile?.bio ?? null,
    birthDate: profile?.birthDate ?? '1990-01-01',
    profileImageUrl: `/api/v1/uploads/${pending.id}/content`,
  });
  check('pending-binding-blocked', pendingBind.status === 400 && errorCode(pendingBind) === 'UPLOAD_004', 'PENDING intent cannot be bound as a profile image');
  const pendingComplete = await apiCall('POST', `/api/v1/uploads/${pending.id}/complete`, owner.token);
  const pendingCode = errorCode(pendingComplete);
  const notMissingBy403 = pendingComplete.status === 503 && pendingCode === 'UPLOAD_008';
  check(
    'pending-complete-blocked',
    (pendingComplete.status === 409 && pendingCode === 'UPLOAD_005') || notMissingBy403,
    notMissingBy403
      ? 'missing/ambiguous object remained unbound as UPLOAD_008; 403 was not treated as 404'
      : 'clear missing object remained unbound as UPLOAD_005',
  );

  const oversized = paddedPng(maxBytes + 1);
  const oversizedResult = await presign(owner, oversized, 'image/png');
  check('oversize-rejected', oversizedResult.status === 400, 'API rejected >5MiB before any S3 PUT');

  const wrongMimeBytes = png;
  const mimeIntentResult = await presign(owner, wrongMimeBytes, 'image/jpeg', 'POST_IMAGE');
  const mimeIntent = dataOf(mimeIntentResult);
  if (!mimeIntent) throw new Error('MIME mismatch presign response missing');
  safePresignedSummary(mimeIntent, runtime);
  const mimePut = await rawPut(mimeIntent, wrongMimeBytes);
  const mimeComplete = await apiCall('POST', `/api/v1/uploads/${mimeIntent.id}/complete`, owner.token);
  check(
    'mime-mismatch-rejected',
    mimePut.status >= 200 && mimePut.status < 300 && mimeComplete.status === 400 && errorCode(mimeComplete) === 'UPLOAD_001',
    'Tika MIME mismatch rejected before image metadata creation',
  );

  const wrongChecksum = differentChecksum(pngChecksum);
  const checksumIntentResult = await presign(owner, png, 'image/png', 'POST_IMAGE', wrongChecksum);
  const checksumIntent = dataOf(checksumIntentResult);
  if (!checksumIntent) throw new Error('checksum mismatch presign response missing');
  safePresignedSummary(checksumIntent, runtime);
  const checksumPut = await rawPut(checksumIntent, png);
  const checksumComplete = await apiCall('POST', `/api/v1/uploads/${checksumIntent.id}/complete`, owner.token);
  const checksumCode = errorCode(checksumComplete);
  const checksumNotCreated = checksumComplete.status !== 200
    && (checksumPut.status >= 400 || checksumCode === 'UPLOAD_007');
  const checksumOutcomeAccepted = ['UPLOAD_005', 'UPLOAD_007', 'UPLOAD_008'].includes(checksumCode);
  check(
    'checksum-mismatch-rejected',
    checksumNotCreated && checksumOutcomeAccepted,
    checksumComplete.status === 503 && errorCode(checksumComplete) === 'UPLOAD_008'
      ? 'S3 rejected checksum and ambiguous HEAD remained UPLOAD_008; no 403-to-404 inference'
      : checksumComplete.status === 409 && checksumCode === 'UPLOAD_005'
        ? 'S3 rejected checksum and clear missing object remained unbound as UPLOAD_005'
        : 'wrong checksum did not create canonical metadata',
  );

  return { canonical, intentId: intent.id, png };
}

async function runBrowserWorkflow(owner) {
  const { chromium, request } = require(playwrightModule);
  browser = await chromium.launch({
    headless: process.env.T5_HEADLESS !== '0',
    executablePath: process.env.T5_CHROME_EXECUTABLE ?? chromeExecutable,
  });
  browserContext = await browser.newContext();
  const ownerContext = await request.newContext({ baseURL: runtime.apiBase });
  try {
    const signin = await ownerContext.fetch('/api/v1/auth/signin', {
      method: 'POST',
      headers: { Origin: runtime.frontendOrigin },
      data: { email: owner.email, password: owner.password },
    });
    if (signin.status() !== 200) throw new Error('owner browser session sign-in failed');
    const state = await ownerContext.storageState();
    await browserContext.addCookies(state.cookies);
  } finally {
    await ownerContext.dispose();
  }
  const page = await browserContext.newPage();
  const browserEvents = { put: [], options: [], presign: 0, complete: 0 };
  const browserHeaderTasks = [];
  page.on('request', (request) => {
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (url.hostname === s3HostFor(runtime) && request.method() === 'PUT') {
      // `request.headers()` can omit browser-managed security/cookie headers.
      // Resolve the async complete header set before asserting no app credentials
      // crossed the presigned S3 boundary. Store booleans only, never values.
      const headerTask = request.allHeaders().then((headers) => {
        browserEvents.put.push({
          auth: Boolean(headers.authorization || headers.Authorization),
          cookie: Boolean(headers.cookie || headers.Cookie),
          referer: Boolean(headers.referer || headers.Referer),
          pathname: url.pathname,
        });
      }).catch(() => {
        // A failed header read must fail closed rather than create false PASS.
        browserEvents.put.push({ auth: true, cookie: true, referer: true, pathname: url.pathname });
      });
      browserHeaderTasks.push(headerTask);
    }
    if (url.hostname === s3HostFor(runtime) && request.method() === 'OPTIONS') browserEvents.options.push({ pathname: url.pathname });
    if (url.pathname.endsWith('/presigned-url')) browserEvents.presign += 1;
    if (/\/api\/v1\/uploads\/[0-9a-f-]+\/complete$/.test(url.pathname)) browserEvents.complete += 1;
  });
  const s3Responses = [];
  page.on('response', (response) => {
    let url;
    try { url = new URL(response.url()); } catch { return; }
    if (url.hostname === s3HostFor(runtime) && ['PUT', 'OPTIONS'].includes(response.request().method())) {
      s3Responses.push({ method: response.request().method(), status: response.status() });
    }
  });

  await page.goto(`${runtime.frontendOrigin}/settings`, { waitUntil: 'domcontentloaded' });
  const fileInput = page.getByLabel('프로필 이미지 파일 업로드');
  await fileInput.waitFor({ state: 'attached', timeout: 10000 });
  const beforeComplete = browserEvents.complete;
  const completeResponsePromise = page.waitForResponse((response) => {
    try {
      const url = new URL(response.url());
      return response.status() === 200 && /\/api\/v1\/uploads\/[0-9a-f-]+\/complete$/.test(url.pathname);
    } catch {
      return false;
    }
  }, { timeout: 15000 });
  await fileInput.setInputFiles({ name: 't5-browser.png', mimeType: 'image/png', buffer: smallPng() });
  const completeResponse = await completeResponsePromise;
  const completeBody = await safeJson(completeResponse);
  const expectedProfileUrl = completeBody?.data?.imageUrl;
  if (typeof expectedProfileUrl !== 'string') throw new Error('browser complete response missing canonical metadata');
  await page.waitForFunction(
    (expected) => document.querySelector('#profile-image-url')?.value === expected,
    expectedProfileUrl,
    { timeout: 5000 },
  );
  const profileUrl = await page.locator('#profile-image-url').inputValue();
  const saveResponsePromise = page.waitForResponse((response) => {
    try {
      return new URL(response.url()).pathname === '/api/v1/users/me' && response.status() === 200;
    } catch {
      return false;
    }
  }, { timeout: 10000 });
  const profileForm = page.locator('form').filter({ has: page.locator('#profile-image-url') });
  await profileForm.getByRole('button', { name: '저장', exact: true }).click();
  const saveResponse = await saveResponsePromise;
  await page.getByText('프로필이 저장되었습니다.').waitFor({ state: 'visible', timeout: 5000 });
  await Promise.all(browserHeaderTasks);

  check(
    'browser-product-upload',
    completeResponse.status() === 200
      && beforeComplete < browserEvents.complete
      && completeBody?.data?.contentType === 'image/png'
      && /^\/api\/v1\/uploads\/[0-9a-f-]+\/content$/.test(profileUrl)
      && saveResponse.status() === 200,
    'production uploadImage path completed presign→S3 PUT→complete→profile binding with synthetic File',
  );
  check(
    'browser-put-no-app-auth',
    browserEvents.put.length > 0 && browserEvents.put.every((entry) => !entry.auth && !entry.cookie && !entry.referer),
    'browser direct PUT carried no app Authorization/cookie/referrer',
  );
  check(
    'browser-cors',
    browserEvents.options.length === 0 || s3Responses.some((entry) => entry.method === 'OPTIONS' && entry.status === 200),
    'browser direct PUT observed successful CORS preflight or no separate OPTIONS was needed',
  );

  const beforeOversizePresign = browserEvents.presign;
  await fileInput.setInputFiles({ name: 't5-browser-too-large.png', mimeType: 'image/png', buffer: paddedPng(maxBytes + 1) });
  await page.waitForTimeout(250);
  check('browser-oversize-no-presign', browserEvents.presign === beforeOversizePresign, 'browser rejected >5MiB before requesting a presigned URL');
  await page.close();
  return { browserEvents: { put: browserEvents.put.length, options: browserEvents.options.length, presign: browserEvents.presign } };
}

async function runSelfCheck() {
  const fakeRuntime = {
    runId: 'deployment-self-check-00000000',
    bucket: 'zero-verse-s3',
    region: 'ap-northeast-2',
    profile: 'zeroverse-s3-dev',
    prefix: 'zeroverse/dev/deployment-self-check-00000000/',
    mysqlPort: approvedMySqlPort,
    apiBase: 'http://localhost:18092',
    frontendOrigin: 'http://localhost:5173',
  };
  requireExactRuntime(fakeRuntime);
  if (fakeRuntime.mysqlPort === forbiddenMySqlPort) throw new Error('self-check accepted forbidden MySQL port');
  let rejected = false;
  try {
    requireExactRuntime({ ...fakeRuntime, prefix: 'zeroverse/dev/other/' });
  } catch {
    rejected = true;
  }
  if (!rejected) throw new Error('self-check accepted an unbound S3 prefix');
  if (!(await exists(playwrightModule))) throw new Error('configured Playwright module is missing');
  if (requiredChecks.length !== new Set(requiredChecks).size) throw new Error('required check IDs are duplicated');
  assertNoSecrets(JSON.stringify({ status: 'SELF-CHECK PASS', prefix: fakeRuntime.prefix, url: '[url-redacted]' }));
  check('self-check-runtime-guards', true, 'runtime, forbidden DB port, prefix, module, and secret guards passed');
  const path = await writeEvidence('SELF-CHECK PASS');
  console.log(`T5 S3 harness self-check: PASS\nreceipt: ${path}`);
  return 0;
}

async function closeResources() {
  await browserContext?.close().catch(() => {});
  await browser?.close().catch(() => {});
  await guestContext?.dispose().catch(() => {});
  await apiContext?.dispose().catch(() => {});
}

let runtime;

async function main() {
  if (selfCheck) return runSelfCheck();
  if (process.env.T5_AWS_WRITE_APPROVED !== '1') {
    check('runtime-config', false, 'NOT RUN: T5_AWS_WRITE_APPROVED=1 was not provided; no runtime/API/AWS operation attempted');
    const path = await writeEvidence('NOT RUN');
    console.log(`T5 S3 acceptance: NOT RUN\nreceipt: ${path}`);
    return 1;
  }

  try {
    runtime = await loadPublicRuntime();
    evidence.runtime = {
      runId: runtime.runId,
      bucket: runtime.bucket,
      region: runtime.region,
      prefix: runtime.prefix,
      mysqlPort: runtime.mysqlPort,
      apiBase: runtime.apiBase,
      frontendOrigin: runtime.frontendOrigin,
    };
    check('runtime-config', true, 'approved public runtime config is exact and prefix-bound');
    await waitForTcp('127.0.0.1', runtime.mysqlPort);
    check('mysql-synthetic-port', true, 'synthetic MySQL TCP port accepted a connection');

    const { request } = require(playwrightModule);
    apiContext = await request.newContext({ baseURL: runtime.apiBase });
    guestContext = await request.newContext({ baseURL: runtime.apiBase });
    const availability = await apiCall('GET', '/api/v1/feed/public?size=1');
    check(
      'api-availability',
      availability.status === 200
        && availability.body?.success === true
        && Array.isArray(availability.body?.data?.items),
      'local S3 runtime application availability returned the documented public-feed success envelope; this is not prod readiness evidence',
    );
    if (
      availability.status !== 200
      || availability.body?.success !== true
      || !Array.isArray(availability.body?.data?.items)
    ) throw new Error('local S3 runtime application availability did not return a success envelope');

    const owner = await registerAndSetup('owner');
    const friend = await registerAndSetup('friend');
    await runApiWorkflow(owner, friend);
    await runBrowserWorkflow(owner);
    check('secret-free-evidence', true, 'secret guard ran before receipt write');
    const failed = evidence.checks.filter((entry) => entry.required && !entry.ok);
    const path = await writeEvidence(failed.length === 0 ? 'PASS' : 'FAIL');
    console.log(`T5 S3 acceptance: ${failed.length === 0 ? 'PASS' : 'FAIL'}\nreceipt: ${path}`);
    return failed.length === 0 ? 0 : 1;
  } catch (error) {
    const message = scrub(error instanceof Error ? error.message : error);
    evidence.failures.push(message);
    for (const id of requiredChecks) markNotRun(id, `aborted: ${message}`);
    const path = await writeEvidence('FAIL', message);
    console.error(`T5 S3 acceptance: FAIL\nreceipt: ${path}\nreason: ${message}`);
    return 1;
  } finally {
    await closeResources();
  }
}

process.exitCode = await main();
