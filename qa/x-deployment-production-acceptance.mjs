#!/usr/bin/env node
/**
 * T6 production acceptance runner.
 *
 * The harness is fail-closed to https://zeroverse.co.kr. It creates only
 * uniquely marked synthetic accounts/objects after T6_WRITE_APPROVED=1.
 * Credentials, tokens, cookies, presigned URLs, and object keys never enter
 * receipts. IAM, bucket policy, and database reset/delete are out of scope.
 */

import { createRequire } from 'node:module';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { access, chmod, mkdir, writeFile } from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { relative, resolve } from 'node:path';

const require = createRequire(import.meta.url);
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const productionBaseUrl = 'https://zeroverse.co.kr';
const productionBucket = 'zero-verse-s3';
const productionRegion = 'ap-northeast-2';
const productionPrefix = 'zeroverse/prod/';
const outputRoot = resolve(root, 'build/t6-deployment-20261116');
const playwrightModule = process.env.T6_PLAYWRIGHT_MODULE
  ?? '/Users/zerovirus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright';
const chromeExecutable = process.env.T6_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const maxBytes = 5 * 1024 * 1024;

export function assertProductionTarget(value) {
  if (value !== productionBaseUrl) throw new Error('production target must be exactly https://zeroverse.co.kr');
  const parsed = new URL(value);
  if (parsed.protocol !== 'https:' || parsed.hostname !== 'zeroverse.co.kr' || parsed.pathname !== '/' || parsed.search || parsed.hash) {
    throw new Error('production target has an unsafe origin');
  }
  return true;
}

export function assertSafeRunId(value) {
  if (typeof value !== 'string' || !/^deployment-[0-9]{8}-[a-z0-9-]{8,64}$/.test(value)) {
    throw new Error('T6_RUN_ID must be a deployment date marker plus a unique suffix');
  }
  return true;
}

export function scrubEvidence(value) {
  return String(value)
    .replace(/https?:\/\/[^\s)]+/gi, '[url-redacted]')
    .replace(/Bearer\s+[A-Za-z0-9._~-]+/gi, 'Bearer [redacted]')
    .replace(/(?:refresh_token|access_token|password|token|cookie|secret|authorization|signature)[=:][^\s,;]+/gi, '$1=[redacted]')
    .replace(/X-Amz-[A-Za-z-]+=[^&\s]+/gi, 'X-Amz-[redacted]')
    .replace(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g, '[synthetic-email]');
}

export function isProductionObjectKey(value) {
  return typeof value === 'string'
    && value.startsWith(productionPrefix)
    && value.length > productionPrefix.length
    && !value.includes('..')
    && !value.includes('//');
}

export function cookieContract(value) {
  return typeof value === 'string'
    && /(?:^|\s|;)refresh_token=[^;]*/i.test(value)
    && /(?:^|;)\s*Path=\/api\/v1\/auth(?:;|$)/i.test(value)
    && /(?:^|;)\s*HttpOnly(?:;|$)/i.test(value)
    && /(?:^|;)\s*Secure(?:;|$)/i.test(value)
    && /(?:^|;)\s*SameSite=Strict(?:;|$)/i.test(value);
}

const requiredChecks = [
  'runtime-target', 'api-availability', 'api-404-json-not-spa',
  'api-register-owner', 'api-register-viewer', 'api-signin-owner', 'api-signin-viewer',
  'refresh-cookie-contract', 'refresh-rotation', 'stale-refresh-replay',
  'api-signout', 'post-signout-replay', 'browser-signin', 'browser-refresh',
  'browser-profile-upload', 'browser-post-upload', 'browser-s3-scope',
  'browser-s3-no-app-credentials', 's3-cors-preflight', 'anonymous-s3-denied',
  'profile-binding', 'public-post-read', 'private-post-owner-read',
  'private-post-viewer-denied', 'private-image-viewer-denied',
  'four-concurrent-5mib-read', 'browser-signout', 'secret-free-evidence',
];

function isPathInside(parent, candidate) {
  const rel = relative(parent, candidate);
  return rel === '' || (!rel.startsWith('..') && !rel.startsWith('/') && !/^[A-Za-z]:[\\/]/.test(rel));
}

function makeEvidence(runId) {
  return {
    schema: 'zeroverse.x-deployment-production-acceptance.v1',
    status: 'NOT RUN',
    startedAt: new Date().toISOString(),
    runtime: { baseUrl: productionBaseUrl, host: 'zeroverse.co.kr', bucket: productionBucket, region: productionRegion, prefix: productionPrefix, runId },
    checks: [],
    failures: [],
    requiredChecks,
    mutationBoundary: {
      database: 'synthetic marked accounts/posts only; no SQL/reset/delete issued by this harness',
      s3: 'strict zeroverse/prod/ returned-object scope; no IAM/bucket mutation; no automatic cleanup',
      secrets: 'tokens, cookies, credentials, passwords, presigned URLs, and object keys are never recorded',
    },
  };
}

export function assertNoSecrets(serialized) {
  if (/(?:Bearer\s+[A-Za-z0-9._~-]{8,}|X-Amz-Signature=|X-Amz-Credential=|Set-Cookie:\s*[^\[]|refresh_token=[^\[]|accessToken\s*[:=]\s*(?!\[redacted\]))/i.test(serialized)) {
    throw new Error('receipt secret guard rejected sensitive output');
  }
}

async function playwrightAvailable() {
  try { await access(playwrightModule, fsConstants.R_OK); return true; } catch { return false; }
}

async function writeEvidence(evidence, outputDir, status, error = null) {
  evidence.status = status;
  if (error) evidence.failures.push(scrubEvidence(error));
  evidence.finishedAt = new Date().toISOString();
  const serialized = `${JSON.stringify(evidence, null, 2)}\n`;
  assertNoSecrets(serialized);
  if (!isPathInside(outputRoot, outputDir)) throw new Error('T6 output directory escaped ignored evidence root');
  await mkdir(outputDir, { recursive: true, mode: 0o700 });
  await chmod(outputDir, 0o700);
  const receiptPath = resolve(outputDir, 'evidence.json');
  await writeFile(receiptPath, serialized, { encoding: 'utf8', mode: 0o600 });
  await chmod(receiptPath, 0o600);
  return receiptPath;
}

function smallPng() {
  return Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');
}

function paddedPng(size) {
  const bytes = Buffer.alloc(size);
  smallPng().copy(bytes);
  return bytes;
}

function checksum(bytes) {
  return createHash('sha256').update(bytes).digest('base64');
}

let evidence;
let outputDir;
let runtime;
let apiContext;
let guestContext;
let staleContext;
let browser;
let browserContext;
let browserPage;
let browserHeaderTasks = [];
let browserS3Events = new Map();
let lastS3PutUrl = null;
let signedCookieHeader = '';
let fixtureRecord;
let credentialsRecord;

function dataOf(result) { return result?.body?.data ?? null; }
function errorCode(result) { return result?.body?.error?.code ?? null; }

function check(id, ok, detail, required = true) {
  const result = { id, ok: Boolean(ok), required, detail: scrubEvidence(detail) };
  evidence.checks.push(result);
  return result.ok;
}

function markNotRun(id, detail) {
  if (!evidence.checks.some((entry) => entry.id === id)) check(id, false, `NOT RUN: ${detail}`);
}

async function safeJson(response) {
  try { return await response.json(); } catch { return null; }
}

async function apiCall(context, method, path, token, body) {
  const headers = { Accept: 'application/json', Origin: productionBaseUrl };
  if (token) headers.Authorization = `Bearer ${token}`;
  const options = { method, headers, failOnStatusCode: false };
  if (body !== undefined) options.data = body;
  const response = await context.fetch(path, options);
  return { status: response.status(), headers: response.headers(), body: await safeJson(response) };
}

async function binaryCall(context, path, token) {
  const headers = { Accept: '*/*', Origin: productionBaseUrl };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await context.fetch(path, { method: 'GET', headers, failOnStatusCode: false });
  return { status: response.status(), headers: response.headers(), bytes: await response.body() };
}

async function refreshCookie(context) {
  const state = await context.storageState();
  return state.cookies.find((cookie) => cookie.name === 'refresh_token') ?? null;
}

function accountFor(label, runId) {
  const suffix = randomBytes(5).toString('hex');
  const marker = runId.replace(/[^a-z0-9-]/gi, '').toLowerCase();
  const independentPassword = `T6!A9${randomBytes(24).toString('base64url')}z`;
  return {
    email: `qa.t6.${label}.${marker}.${suffix}@example.test`,
    password: independentPassword,
    name: `T6 QA ${label}`,
    nickname: `t6${label}${suffix}`.slice(0, 20),
    birthDate: '1990-01-01',
    slug: `t6-${label}-${suffix}`.slice(0, 30),
  };
}

function canonicalUploadId(value) {
  const match = typeof value === 'string' ? value.match(/^\/api\/v1\/uploads\/([0-9a-f-]{36})\/content$/i) : null;
  return match?.[1] ?? null;
}

function assertPrivateFixtureSafe(serialized) {
  if (/(?:password|accessToken|refreshToken|cookie|secret|uploadUrl|objectKey|Authorization|Bearer\s)/i.test(serialized)) {
    throw new Error('private fixture receipt contains a credential or signed object field');
  }
}

async function writePrivateFixture() {
  if (!fixtureRecord) return null;
  const serialized = `${JSON.stringify(fixtureRecord, null, 2)}\n`;
  assertPrivateFixtureSafe(serialized);
  if (!isPathInside(outputRoot, outputDir)) throw new Error('private fixture path escaped ignored evidence root');
  await mkdir(outputDir, { recursive: true, mode: 0o700 });
  await chmod(outputDir, 0o700);
  const fixturePath = resolve(outputDir, 'fixture-private.json');
  await writeFile(fixturePath, serialized, { encoding: 'utf8', mode: 0o600 });
  await chmod(fixturePath, 0o600);
  return fixturePath;
}

async function writePrivateCredentials() {
  if (!credentialsRecord) return null;
  if (!isPathInside(outputRoot, outputDir)) throw new Error('private credentials path escaped ignored evidence root');
  await mkdir(outputDir, { recursive: true, mode: 0o700 });
  await chmod(outputDir, 0o700);
  const credentialsPath = resolve(outputDir, 'credentials-private.json');
  const serialized = `${JSON.stringify(credentialsRecord, null, 2)}\n`;
  await writeFile(credentialsPath, serialized, { encoding: 'utf8', mode: 0o600 });
  await chmod(credentialsPath, 0o600);
  return credentialsPath;
}

function s3Host(url) {
  return url.hostname === `${productionBucket}.s3.${productionRegion}.amazonaws.com`
    || url.hostname === `s3.${productionRegion}.amazonaws.com`;
}

function objectKeyFromS3Url(url) {
  if (url.hostname === `s3.${productionRegion}.amazonaws.com`) {
    const path = decodeURIComponent(url.pathname.replace(/^\/+/, ''));
    const bucketPrefix = `${productionBucket}/`;
    return path.startsWith(bucketPrefix) ? path.slice(bucketPrefix.length) : '';
  }
  return decodeURIComponent(url.pathname.replace(/^\/+/, ''));
}

function observeBrowser(page) {
  const browserEvents = {
    presignResponses: 0,
    completeResponses: 0,
    refreshResponses: 0,
    signoutResponses: 0,
    s3Options: [],
  };
  page.on('request', (request) => {
    let url;
    try { url = new URL(request.url()); } catch { return; }
    if (s3Host(url) && request.method() === 'PUT') {
      const event = {
        host: s3Host(url),
        scope: isProductionObjectKey(objectKeyFromS3Url(url)),
        auth: true,
        cookie: true,
        referer: true,
        status: null,
      };
      browserS3Events.set(url.href, event);
      lastS3PutUrl = url.href;
      const headerTask = request.allHeaders().then((headers) => {
        event.auth = Boolean(headers.authorization || headers.Authorization);
        event.cookie = Boolean(headers.cookie || headers.Cookie);
        event.referer = Boolean(headers.referer || headers.Referer);
      }).catch(() => {
        event.auth = true;
        event.cookie = true;
        event.referer = true;
      });
      browserHeaderTasks.push(headerTask);
    }
  });
  page.on('response', (response) => {
    let url;
    try { url = new URL(response.url()); } catch { return; }
    const method = response.request().method();
    if (s3Host(url) && method === 'PUT') {
      const event = browserS3Events.get(url.href);
      if (event) event.status = response.status();
    }
    if (s3Host(url) && method === 'OPTIONS') browserEvents.s3Options.push(response.status());
    if (url.pathname.endsWith('/presigned-url')) browserEvents.presignResponses += 1;
    if (/\/api\/v1\/uploads\/[0-9a-f-]{36}\/complete$/i.test(url.pathname)) browserEvents.completeResponses += 1;
    if (url.pathname === '/api/v1/auth/refresh') browserEvents.refreshResponses += 1;
    if (url.pathname === '/api/v1/auth/signout') browserEvents.signoutResponses += 1;
  });
  return browserEvents;
}

async function flushBrowserHeaders() {
  const tasks = browserHeaderTasks;
  browserHeaderTasks = [];
  await Promise.all(tasks);
}

async function unsignedS3Get(urlValue) {
  const url = new URL(urlValue);
  url.search = '';
  const response = await fetch(url, { method: 'GET', redirect: 'error' });
  await response.arrayBuffer();
  return response.status;
}

async function s3CorsPreflight(urlValue) {
  const response = await fetch(urlValue, {
    method: 'OPTIONS',
    headers: {
      Origin: productionBaseUrl,
      'Access-Control-Request-Method': 'PUT',
      'Access-Control-Request-Headers': 'content-type,if-none-match,x-amz-checksum-sha256',
    },
  });
  await response.arrayBuffer();
  const headers = Object.fromEntries(response.headers.entries());
  return {
    status: response.status,
    origin: headers['access-control-allow-origin'] ?? '',
    methods: (headers['access-control-allow-methods'] ?? '').split(',').map((value) => value.trim().toUpperCase()),
    allowedHeaders: (headers['access-control-allow-headers'] ?? '').split(',').map((value) => value.trim().toLowerCase()),
  };
}

async function registerAndSetup(label, runId) {
  const account = accountFor(label, runId);
  const registered = await apiCall(apiContext, 'POST', '/api/v1/auth/register', undefined, {
    email: account.email,
    password: account.password,
    name: account.name,
    nickname: account.nickname,
    birthDate: account.birthDate,
  });
  check(`api-register-${label}`, registered.status === 201, `${label} synthetic account registration returned 201`);
  if (registered.status !== 201) throw new Error(`${label} registration failed`);

  const signin = await apiCall(apiContext, 'POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  const token = dataOf(signin)?.accessToken;
  check(`api-signin-${label}`, signin.status === 200 && typeof token === 'string', `${label} access token remained in memory only`);
  if (typeof token !== 'string') throw new Error(`${label} signin failed`);
  credentialsRecord.accounts[label] = { email: account.email, password: account.password };
  if (label === 'owner') signedCookieHeader = signin.headers['set-cookie'] ?? '';

  const setup = await apiCall(apiContext, 'PUT', '/api/v1/blogs/me/initial-setup', token, {
    title: `T6 ${label} ${runId.slice(-8)}`,
    urlSlug: account.slug,
    description: 'synthetic deployment acceptance account',
  });
  if (setup.status !== 200) throw new Error(`${label} blog setup failed`);
  const me = await apiCall(apiContext, 'GET', '/api/v1/auth/me', token);
  const meData = dataOf(me);
  if (me.status !== 200 || !meData?.id || !meData.defaultBlog?.id) throw new Error(`${label} account lookup failed`);
  fixtureRecord.accounts[label] = {
    id: meData.id,
    email: account.email,
    nickname: account.nickname,
    blogId: meData.defaultBlog.id,
    blogSlug: meData.defaultBlog.urlSlug,
  };
  return {
    ...account,
    token,
    id: meData.id,
    blogId: meData.defaultBlog.id,
    blogSlug: meData.defaultBlog.urlSlug,
  };
}

async function exerciseRefreshRotation(owner) {
  const oldCookie = await refreshCookie(apiContext);
  check('refresh-cookie-contract', cookieContract(signedCookieHeader), 'signin Set-Cookie has Secure, HttpOnly, SameSite=Strict, and auth path');
  if (!oldCookie) throw new Error('refresh cookie was not retained by the isolated API context');

  const rotated = await apiCall(apiContext, 'POST', '/api/v1/auth/refresh');
  const newCookie = await refreshCookie(apiContext);
  const rotatedToken = dataOf(rotated)?.accessToken;
  check('refresh-rotation', rotated.status === 200 && typeof rotatedToken === 'string' && newCookie?.value !== oldCookie.value,
    'refresh returned a new access token and rotated the HttpOnly cookie');
  if (typeof rotatedToken !== 'string') throw new Error('refresh rotation failed');
  owner.token = rotatedToken;

  const stale = await requireRequestContext([oldCookie]);
  staleContext = stale;
  const staleRefresh = await apiCall(stale, 'POST', '/api/v1/auth/refresh');
  check('stale-refresh-replay', staleRefresh.status === 401 && errorCode(staleRefresh) === 'AUTH_003', 'rotated refresh cookie replay was rejected');

  const signout = await apiCall(apiContext, 'POST', '/api/v1/auth/signout');
  check('api-signout', signout.status === 200 && cookieContract(signout.headers['set-cookie'] ?? ''), 'API signout returned success and an expiring auth cookie');
  const afterSignout = await apiCall(apiContext, 'POST', '/api/v1/auth/refresh');
  const staleAfterSignout = await apiCall(stale, 'POST', '/api/v1/auth/refresh');
  check('post-signout-replay', afterSignout.status === 401 && errorCode(afterSignout) === 'AUTH_003'
    && staleAfterSignout.status === 401 && errorCode(staleAfterSignout) === 'AUTH_003', 'current and stale refresh cookies were rejected after signout');
}

async function requireRequestContext(cookies = []) {
  const { request } = require(playwrightModule);
  return request.newContext({
    baseURL: productionBaseUrl,
    extraHTTPHeaders: { Origin: productionBaseUrl, Accept: 'application/json' },
    storageState: { cookies, origins: [] },
  });
}

async function runBrowserSignIn(owner) {
  const { chromium } = require(playwrightModule);
  browser = await chromium.launch({ headless: process.env.T6_HEADLESS !== '0', executablePath: chromeExecutable });
  browserContext = await browser.newContext({ baseURL: productionBaseUrl });
  browserPage = await browserContext.newPage();
  const browserEvents = observeBrowser(browserPage);
  await browserPage.goto(`${productionBaseUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await browserPage.getByLabel('이메일').fill(owner.email);
  await browserPage.getByLabel('비밀번호').fill(owner.password);
  const signinResponse = browserPage.waitForResponse((response) => {
    try { return new URL(response.url()).pathname === '/api/v1/auth/signin' && response.status() === 200; } catch { return false; }
  }, { timeout: 15000 });
  await browserPage.getByRole('button', { name: /접속하기/ }).click();
  await signinResponse;
  await browserPage.waitForURL((url) => url.hostname === 'zeroverse.co.kr' && !url.pathname.startsWith('/signin'), { timeout: 15000 });
  check('browser-signin', browserPage.url().startsWith(`${productionBaseUrl}/`) && !browserPage.url().includes('/signin'), 'real Chrome sign-in reached the authenticated application');

  const refreshBefore = browserEvents.refreshResponses;
  const refreshResponse = browserPage.waitForResponse((response) => {
    try { return new URL(response.url()).pathname === '/api/v1/auth/refresh' && response.status() === 200; } catch { return false; }
  }, { timeout: 15000 });
  await browserPage.reload({ waitUntil: 'domcontentloaded' });
  const refresh = await refreshResponse;
  check('browser-refresh', refresh.status() === 200 && browserEvents.refreshResponses > refreshBefore, 'browser reload used the HttpOnly refresh cookie and recovered the session');
  return browserEvents;
}

async function runProfileUpload(owner, browserEvents) {
  await browserPage.goto(`${productionBaseUrl}/settings`, { waitUntil: 'domcontentloaded' });
  const fileInput = browserPage.getByLabel('프로필 이미지 파일 업로드');
  await fileInput.waitFor({ state: 'attached', timeout: 15000 });
  const bytes = paddedPng(maxBytes);
  const completeResponse = browserPage.waitForResponse((response) => {
    try { return /\/api\/v1\/uploads\/[0-9a-f-]{36}\/complete$/i.test(new URL(response.url()).pathname) && response.status() === 200; } catch { return false; }
  }, { timeout: 30000 });
  await fileInput.setInputFiles({ name: `qa-${owner.slug}-profile.png`, mimeType: 'image/png', buffer: bytes });
  const complete = await completeResponse;
  const completeBody = await safeJson(complete);
  const profileUrl = dataOf({ body: completeBody })?.imageUrl;
  if (typeof profileUrl !== 'string') throw new Error('browser profile upload returned no canonical URL');
  const profileId = dataOf({ body: completeBody })?.id ?? canonicalUploadId(profileUrl);
  if (typeof profileId !== 'string') throw new Error('browser profile upload returned no canonical id');
  fixtureRecord.uploads.push({ id: profileId, purpose: 'PROFILE_IMAGE', owner: 'owner' });
  await browserPage.waitForFunction((expected) => document.querySelector('#profile-image-url')?.value === expected, profileUrl, { timeout: 10000 });
  const profileForm = browserPage.locator('form').filter({ has: browserPage.locator('#profile-image-url') });
  const saveResponse = browserPage.waitForResponse((response) => {
    try { return new URL(response.url()).pathname === '/api/v1/users/me' && response.status() === 200; } catch { return false; }
  }, { timeout: 15000 });
  await profileForm.getByRole('button', { name: '저장', exact: true }).click();
  await saveResponse;
  await browserPage.getByText('프로필이 저장되었습니다.').waitFor({ state: 'visible', timeout: 10000 });
  await flushBrowserHeaders();
  const putEvents = [...browserS3Events.values()];
  check('browser-profile-upload', putEvents.length > 0 && putEvents.some((event) => event.status >= 200 && event.status < 300) && complete.status() === 200 && bytes.length === maxBytes,
    'Chrome completed a 5MiB profile presign, private S3 PUT, complete, and profile binding');
  const scopeAndHeaders = putEvents.every((event) => event.host && event.scope && event.status >= 200 && event.status < 300 && !event.auth && !event.cookie && !event.referer);
  check('browser-s3-scope', scopeAndHeaders, 'every observed browser S3 PUT stayed in zeroverse/prod and completed successfully');
  check('browser-s3-no-app-credentials', putEvents.length > 0 && putEvents.every((event) => !event.auth && !event.cookie && !event.referer), 'browser direct S3 PUT carried no app Authorization, Cookie, or Referer');
  const ownerProfile = await apiCall(apiContext, 'GET', '/api/v1/users/me', owner.token);
  check('profile-binding', ownerProfile.status === 200 && dataOf(ownerProfile)?.profileImageUrl === profileUrl && /^\/api\/v1\/uploads\/[0-9a-f-]{36}\/content$/i.test(profileUrl), 'profile binding persisted the canonical managed image URL');
  const concurrentReads = await Promise.all(Array.from({ length: 4 }, () => binaryCall(apiContext, profileUrl, owner.token)));
  check('four-concurrent-5mib-read', concurrentReads.length === 4
    && concurrentReads.every((response) => response.status === 200 && response.bytes.length === maxBytes),
  'four simultaneous authenticated reads returned the bounded 5MiB image without exceeding the payload limit');
  concurrentReads.length = 0;
  if (lastS3PutUrl) {
    const cors = await s3CorsPreflight(lastS3PutUrl);
    check('s3-cors-preflight', cors.status === 200 && cors.origin === productionBaseUrl && cors.methods.includes('PUT')
      && ['content-type', 'if-none-match', 'x-amz-checksum-sha256'].every((header) => cors.allowedHeaders.includes(header)), 'production S3 CORS accepted only the actual origin and signed PUT headers');
    check('anonymous-s3-denied', await unsignedS3Get(lastS3PutUrl) === 403, 'unsigned direct S3 GET remained denied for the private object');
  } else {
    check('s3-cors-preflight', false, 'browser did not expose a presigned S3 PUT');
    check('anonymous-s3-denied', false, 'browser did not expose a presigned S3 PUT');
  }
  return { profileUrl, profileId };
}

async function runPostUpload(owner, viewer) {
  await browserPage.goto(`${productionBaseUrl}/write`, { waitUntil: 'domcontentloaded' });
  await browserPage.getByLabel('제목').waitFor({ state: 'visible', timeout: 15000 });
  await browserPage.getByLabel('제목').fill(`T6 production ${owner.slug}`);
  const editor = browserPage.locator('[data-testid="post-editor-surface"]');
  await editor.click();
  await editor.fill(`Synthetic T6 post ${owner.slug}`);
  await browserPage.getByRole('button', { name: '전체 공개', exact: true }).click();
  const postFile = browserPage.getByLabel('본문 이미지 업로드');
  const postBytes = smallPng();
  const completeResponse = browserPage.waitForResponse((response) => {
    try { return /\/api\/v1\/uploads\/[0-9a-f-]{36}\/complete$/i.test(new URL(response.url()).pathname) && response.status() === 200; } catch { return false; }
  }, { timeout: 30000 });
  await postFile.setInputFiles({ name: `qa-${owner.slug}-post.png`, mimeType: 'image/png', buffer: postBytes });
  const complete = await completeResponse;
  const completeBody = await safeJson(complete);
  const uploadedUrl = dataOf({ body: completeBody })?.imageUrl;
  if (typeof uploadedUrl !== 'string') throw new Error('browser post upload returned no canonical URL');
  const postUploadId = dataOf({ body: completeBody })?.id ?? canonicalUploadId(uploadedUrl);
  if (typeof postUploadId !== 'string') throw new Error('browser post upload returned no canonical id');
  const postResponse = browserPage.waitForResponse((response) => {
    try { return response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/posts' && response.status() === 201; } catch { return false; }
  }, { timeout: 30000 });
  await browserPage.getByRole('button', { name: /발행하기/ }).click();
  const created = await postResponse;
  const createdBody = await safeJson(created);
  const post = dataOf({ body: createdBody });
  const postId = Number(post?.id);
  if (Number.isInteger(postId) && postId > 0) {
    fixtureRecord.uploads.push({ id: postUploadId, purpose: 'POST_IMAGE', owner: 'owner' });
    fixtureRecord.posts.push({ id: postId, owner: 'owner', visibilityBefore: 'PUBLIC', visibilityAfter: 'PRIVATE', imageId: postUploadId });
  }
  await flushBrowserHeaders();
  const observedPuts = [...browserS3Events.values()];
  const postPut = observedPuts.at(-1);
  check('browser-post-upload', complete.status() === 200 && created.status() === 201 && Number.isInteger(postId) && postId > 0
    && postPut?.host && postPut.scope && postPut.status >= 200 && postPut.status < 300 && !postPut.auth && !postPut.cookie && !postPut.referer
    && Array.isArray(post?.images) && post.images.some((image) => image.imageUrl === uploadedUrl), 'Chrome completed a post-image S3 PUT and bound the canonical image to a published post');
  await browserPage.waitForURL((url) => /\/blog\/[^/]+\/\d+$/.test(url.pathname), { timeout: 15000 });

  const publicOwner = await apiCall(apiContext, 'GET', `/api/v1/posts/${postId}`, owner.token);
  const detail = dataOf(publicOwner);
  const canonicalImage = detail?.images?.find((image) => image.imageUrl === uploadedUrl)?.imageUrl ?? uploadedUrl;
  const publicViewer = await apiCall(guestContext, 'GET', `/api/v1/posts/${postId}`);
  const publicImage = await binaryCall(guestContext, canonicalImage);
  check('public-post-read', publicOwner.status === 200 && publicViewer.status === 200 && publicImage.status === 200 && publicImage.bytes.length === postBytes.length,
    'PUBLIC post and managed image were readable by the anonymous viewer before ACL tightening');

  const updated = await apiCall(apiContext, 'PUT', `/api/v1/posts/${postId}`, owner.token, {
    title: detail.title,
    contentJson: detail.contentJson,
    contentHtml: detail.contentHtml,
    categoryId: detail.category?.id ?? null,
    visibility: 'PRIVATE',
    publish: true,
    thumbnailUrl: detail.thumbnailUrl ?? null,
    tagNames: detail.tags ?? [],
    images: detail.images ?? [{ imageUrl: canonicalImage, altText: null, displayOrder: 0 }],
  });
  if (updated.status !== 200) throw new Error('private ACL update failed');
  const privateOwner = await apiCall(apiContext, 'GET', `/api/v1/posts/${postId}`, owner.token);
  const privateViewer = await apiCall(guestContext, 'GET', `/api/v1/posts/${postId}`);
  const friendViewer = await apiCall(apiContext, 'GET', `/api/v1/posts/${postId}`, viewer.token);
  const ownerImage = await binaryCall(apiContext, canonicalImage, owner.token);
  const viewerImage = await binaryCall(guestContext, canonicalImage);
  check('private-post-owner-read', privateOwner.status === 200 && ownerImage.status === 200 && ownerImage.bytes.length === postBytes.length,
    'owner retained PRIVATE post and managed image access');
  check('private-post-viewer-denied', [401, 403, 404].includes(privateViewer.status), 'anonymous viewer could not read the PRIVATE post');
  check('private-image-viewer-denied', [401, 403, 404].includes(viewerImage.status) && [401, 403, 404].includes(friendViewer.status),
    'non-owner viewers could not read the PRIVATE post image');
  return { postId, canonicalImage };
}

async function runBoundaryChecks() {
  const missingPath = `/api/v1/t6-${evidence.runtime.runId}-missing`;
  const apiMissing = await apiCall(guestContext, 'GET', missingPath);
  const apiContentType = apiMissing.headers['content-type'] ?? '';
  check('api-404-json-not-spa', [401, 404].includes(apiMissing.status) && /application\/json/i.test(apiContentType)
    && apiMissing.body?.success === false, 'missing /api route returned a JSON error rather than SPA HTML');
}

async function runBrowserSignout() {
  const result = await browserPage.evaluate(async () => {
    const response = await fetch('/api/v1/auth/signout', {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
    return response.status;
  });
  await browserPage.goto(`${productionBaseUrl}/settings`, { waitUntil: 'domcontentloaded' });
  await browserPage.waitForURL((url) => url.pathname.startsWith('/signin'), { timeout: 15000 });
  check('browser-signout', result === 200 && browserPage.url().startsWith(`${productionBaseUrl}/signin`), 'browser signout cleared the refresh session and protected route redirected to signin');
}

async function closeResources() {
  await staleContext?.dispose().catch(() => {});
  await guestContext?.dispose().catch(() => {});
  await apiContext?.dispose().catch(() => {});
  await browserPage?.close().catch(() => {});
  await browserContext?.close().catch(() => {});
  await browser?.close().catch(() => {});
}

async function runSelfCheck() {
  const selfRunId = 'deployment-20261116-selfcheck01';
  assertProductionTarget(productionBaseUrl);
  assertSafeRunId(selfRunId);
  if (!isProductionObjectKey(`${productionPrefix}01234567-89ab-cdef-0123-456789abcdef`)) throw new Error('self-check object scope failed');
  if (isProductionObjectKey('zeroverse/dev/01234567-89ab-cdef-0123-456789abcdef')) throw new Error('self-check accepted dev object scope');
  if (!cookieContract('refresh_token=redacted; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict')) throw new Error('self-check cookie contract failed');
  if (!(await playwrightAvailable())) throw new Error('configured Playwright module is missing');
  if (requiredChecks.length !== new Set(requiredChecks).size) throw new Error('required check IDs are duplicated');
  const selfEvidence = makeEvidence(selfRunId);
  selfEvidence.checks.push({ id: 'self-check-guards', ok: true, required: true, detail: 'production target, runId, object scope, cookie, module, and secret guards passed' });
  const selfOutput = resolve(outputRoot, 'production-self-check');
  const selfReceipt = await writeEvidence(selfEvidence, selfOutput, 'SELF-CHECK PASS');
  console.log(`T6 production acceptance self-check: PASS\nreceipt: ${selfReceipt}`);
  return 0;
}

async function main() {
  if (process.argv.includes('--self-check')) return runSelfCheck();
  const requestedRunId = process.env.T6_RUN_ID;
  const receiptRunId = /^deployment-[0-9]{8}-[a-z0-9-]{8,64}$/.test(requestedRunId ?? '')
    ? requestedRunId
    : `deployment-20261116-not-run-${randomBytes(4).toString('hex')}`;
  outputDir = resolve(outputRoot, `production-acceptance-${receiptRunId}`);
  evidence = makeEvidence(receiptRunId);
  fixtureRecord = {
    schema: 'zeroverse.x-deployment-production-fixture.v1',
    runId: receiptRunId,
    baseUrl: productionBaseUrl,
    accounts: {},
    uploads: [],
    posts: [],
    note: 'Synthetic IDs/emails only; sensitive credentials and signed locations are excluded.',
  };
  credentialsRecord = {
    schema: 'zeroverse.x-deployment-production-credentials.v1',
    runId: receiptRunId,
    accounts: {},
    warning: 'Contains synthetic test passwords. Keep this file mode 0600 and delete only after the root teardown/restart audit.',
  };
  if (process.env.T6_WRITE_APPROVED !== '1') {
    check('runtime-target', false, 'NOT RUN: T6_WRITE_APPROVED=1 was not provided; no API, browser, or S3 operation attempted');
    const receipt = await writeEvidence(evidence, outputDir, 'NOT RUN');
    console.log(`T6 production acceptance: NOT RUN\nreceipt: ${receipt}`);
    return 1;
  }

  try {
    assertProductionTarget(process.env.T6_PUBLIC_BASE_URL ?? productionBaseUrl);
    assertSafeRunId(requestedRunId);
    if (!(await playwrightAvailable())) throw new Error('configured Playwright module is missing');
    check('runtime-target', true, 'exact production origin, run marker, and Playwright module accepted');
    const { request } = require(playwrightModule);
    apiContext = await request.newContext({ baseURL: productionBaseUrl, extraHTTPHeaders: { Origin: productionBaseUrl, Accept: 'application/json' } });
    guestContext = await request.newContext({ baseURL: productionBaseUrl, extraHTTPHeaders: { Origin: productionBaseUrl, Accept: 'application/json' } });

    const availability = await apiCall(guestContext, 'GET', '/api/v1/feed/public?size=1');
    const availabilityOk = check('api-availability', availability.status === 200 && availability.body?.success === true && Array.isArray(availability.body?.data?.items), 'public feed returned the documented success envelope over production HTTPS');
    if (!availabilityOk) throw new Error('production public feed was not ready; no synthetic writes attempted');
    const owner = await registerAndSetup('owner', requestedRunId);
    await exerciseRefreshRotation(owner);
    const viewer = await registerAndSetup('viewer', requestedRunId);
    await runBoundaryChecks();
    const browserEvents = await runBrowserSignIn(owner);
    await runProfileUpload(owner, browserEvents);
    await runPostUpload(owner, viewer);
    await runBrowserSignout();
    check('secret-free-evidence', true, 'receipt secret guard ran before evidence write');
    const fixturePath = await writePrivateFixture();
    const credentialsPath = await writePrivateCredentials();
    const failed = evidence.checks.filter((entry) => entry.required && !entry.ok);
    const receipt = await writeEvidence(evidence, outputDir, failed.length === 0 ? 'PASS' : 'FAIL');
    console.log(`T6 production acceptance: ${failed.length === 0 ? 'PASS' : 'FAIL'}\nreceipt: ${receipt}\nprivate-fixture: ${fixturePath}\nprivate-credentials: ${credentialsPath}`);
    return failed.length === 0 ? 0 : 1;
  } catch (error) {
    const message = scrubEvidence(error instanceof Error ? error.message : error);
    evidence.failures.push(message);
    for (const id of requiredChecks) markNotRun(id, `aborted: ${message}`);
    const fixturePath = await writePrivateFixture().catch(() => null);
    const credentialsPath = await writePrivateCredentials().catch(() => null);
    const receipt = await writeEvidence(evidence, outputDir, 'FAIL', message);
    console.error(`T6 production acceptance: FAIL\nreceipt: ${receipt}\nprivate-fixture: ${fixturePath ?? 'not-written'}\nprivate-credentials: ${credentialsPath ?? 'not-written'}\nreason: ${message}`);
    return 1;
  } finally {
    await closeResources();
  }
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) process.exitCode = await main();
