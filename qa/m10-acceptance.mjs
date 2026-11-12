/**
 * M10 local MVP closeout acceptance runner.
 *
 * QA-owned black-box runner. It never connects to the user's MySQL (13307),
 * never writes credentials/tokens/cookies to evidence, and is fail-closed:
 * missing runtime, manifest, browser, status fixtures, or required checks
 * produces NOT RUN/exit 1 rather than a synthetic PASS.
 *
 * Full run prerequisites are supplied by root through environment variables:
 *   M10_API_BASE_URL, M10_FRONTEND_URL, M10_VERSION_MANIFEST
 *   M10_ADMIN_EMAIL/M10_ADMIN_PASSWORD (optional synthetic admin fixture)
 *   M10_PLAYWRIGHT_MODULE (defaults to playwright)
 *
 * `node qa/m10-acceptance.mjs --self-check` performs only static runner
 * validation and is safe before final source/runtime freeze.
 */

import { createRequire } from 'node:module';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { access, mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import { resolve, relative } from 'node:path';

const require = createRequire(import.meta.url);
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const selfCheck = process.argv.includes('--self-check');
const suffix = `${Date.now().toString(36)}${randomBytes(6).toString('hex')}`;
const apiBaseUrl = process.env.M10_API_BASE_URL ?? 'http://127.0.0.1:18090';
const frontendUrl = process.env.M10_FRONTEND_URL ?? 'http://127.0.0.1:5180';
const outputDir = resolve(root, process.env.M10_OUTPUT_DIR ?? `build/m10-acceptance-${suffix}`);
const manifestPath = resolve(root, process.env.M10_VERSION_MANIFEST ?? 'build/m10-evidence/tested-version.json');
const chromeExecutable = process.env.M10_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const playwrightModule = process.env.M10_PLAYWRIGHT_MODULE ?? 'playwright';
const screenshotsEnabled = process.env.M10_SCREENSHOTS !== '0';
const headless = process.env.M10_HEADLESS !== '0';

const canonicalScreens = [
  ['main', '/'],
  ['blog', '/blog/__BLOG_SLUG__'],
  ['post-detail', '/blog/__BLOG_SLUG__/__POST_ID__'],
  ['editor', '/write'],
  ['auth', '/signin'],
  ['setup', '/blog/setup'],
  ['settings', '/settings'],
  ['settings-universe', '/settings/universe'],
  ['settings-posts', '/settings/posts'],
  ['search', '/search'],
  ['notifications', '/notifications'],
  ['admin', '/admin'],
  ['admin-detail', '/admin/users/__OWNER_ID__'],
];

// Each screenshot must prove the intended route and a screen-specific, visible
// DOM anchor. A non-empty body is deliberately not sufficient: guards can
// render a guest redirect, a not-found page, or a shell with no screen content.
const screenExpectations = {
  main: { pathname: /^\/$/, selectors: ['main form[role="search"]', 'nav[aria-label="주 메뉴"]'] },
  blog: { pathname: /^\/blog\/[^/]+$/, selectors: ['section[aria-label*="글 목록"]', 'main h1'] },
  'post-detail': { pathname: /^\/blog\/[^/]+\/\d+$/, selectors: ['nav[aria-label="브레드크럼"]', 'main h1'] },
  editor: { pathname: /^\/write$/, selectors: ['[data-testid="post-editor-surface"]', 'input[aria-label="제목"]'] },
  auth: { pathname: /^\/signin$/, selectors: ['[data-onboarding-card="true"] form', 'input[type="email"]'] },
  setup: { pathname: /^\/blog\/setup$/, selectors: ['[data-onboarding-card="true"] form', 'input[placeholder="my-blog"]'] },
  settings: { pathname: /^\/settings$/, selectors: ['main form', 'h1'] },
  'settings-universe': { pathname: /^\/settings\/universe$/, selectors: ['section[aria-label="유니버스 관리"]'] },
  'settings-posts': { pathname: /^\/settings\/posts$/, selectors: ['main h1', 'input[aria-label="새 카테고리 이름"]'] },
  search: { pathname: /^\/search$/, selectors: ['form[role="search"]', 'input[placeholder*="유니버스 전체 검색"]'] },
  notifications: { pathname: /^\/notifications$/, selectors: ['section[aria-label="알림 센터"]'] },
  admin: { pathname: /^\/admin$/, selectors: ['section[aria-label="관리자 콘솔"]', 'form[role="search"]'] },
  'admin-detail': { pathname: /^\/admin\/users\/\d+$/, selectors: ['section[aria-label="사용자 상세"]'] },
};

const requiredChecks = [
  'source-runtime-identity', 'runtime-openapi', 'openapi-required-paths',
  'synthetic-register-owner', 'synthetic-register-friend', 'signin-owner', 'signin-friend',
  'setup-owner', 'setup-friend', 'draft-empty-allowed', 'publish-public', 'public-guest-read',
  'refresh-rotation', 'logout-refresh-reuse-rejected', 'universe-request', 'universe-accept',
  'universe-post-read', 'comment-create', 'like-create', 'notification-list', 'notification-read-delete',
  'content-sanitize',
  'common-envelope', 'private-no-store', 'invalid-bearer-rejected', 'pagination-shape',
  'admin-current-actor', 'admin-soft-delete-preservation', 'upload-5mib-mime', 'restart-persistence',
  'provenance-hash-stability', 'static-skip-placeholder-scan', 'keyboard-topbar-observation', 'keyboard-modal-observation',
  'keyboard-draft-picker-observation', 'keyboard-category-observation', 'keyboard-admin-observation',
  'screenshot-main', 'screenshot-blog', 'screenshot-post-detail', 'screenshot-editor', 'screenshot-auth',
  'screenshot-setup', 'screenshot-settings', 'screenshot-settings-universe', 'screenshot-settings-posts',
  'screenshot-search', 'screenshot-notifications', 'screenshot-admin', 'screenshot-admin-detail',
];

const evidence = {
  milestone: 'M10',
  contract: 'local-mvp-closeout',
  status: 'NOT RUN',
  startedAt: new Date().toISOString(),
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  apiBaseUrl,
  frontendUrl,
  sourceManifest: null,
  provenance: { before: null, after: null },
  fixture: { runId: `m10-${suffix}`, accountIds: {}, blogIds: {}, postIds: {}, commentIds: {} },
  screenshots: [],
  checks: [],
  failures: [],
  requiredChecks,
};

let api;
let requestFactory;
let browser;
let owner;
let friend;
let post;

function check(id, ok, detail, options = {}) {
  evidence.checks.push({ id, ok: Boolean(ok), required: options.required !== false, detail });
  return Boolean(ok);
}

function scrub(value) {
  return String(value)
    .replace(/Bearer\s+[A-Za-z0-9._~-]+/gi, 'Bearer [redacted]')
    .replace(/(?:password|token|cookie|secret|authorization)[=:][^\s,;]+/gi, '$1=[redacted]')
    .replace(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g, '[synthetic-email]');
}

async function sha256(file) {
  const data = await readFile(file);
  return createHash('sha256').update(data).digest('hex');
}

async function verifyManifest() {
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  if (!Array.isArray(manifest.files) || typeof manifest.sourceSha256 !== 'string') {
    throw new Error('M10 manifest must contain sourceSha256 and files[]');
  }
  const actual = [];
  for (const entry of manifest.files) {
    if (!entry || typeof entry.path !== 'string' || typeof entry.sha256 !== 'string') {
      throw new Error('M10 manifest contains malformed file entry');
    }
    const file = resolve(root, entry.path);
    if (relative(root, file).startsWith('..')) throw new Error('manifest escapes repository root');
    const hash = await sha256(file);
    if (hash !== entry.sha256) throw new Error(`source manifest mismatch: ${entry.path}`);
    actual.push({ path: entry.path, sha256: hash });
  }
  actual.sort((a, b) => a.path.localeCompare(b.path));
  const aggregate = createHash('sha256')
    .update(actual.map((entry) => `${entry.path}\0${entry.sha256}\n`).join(''))
    .digest('hex');
  if (aggregate !== manifest.sourceSha256) throw new Error('source manifest aggregate mismatch');
  const jarPath = resolve(root, manifest.jarPath ?? 'build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar');
  const jarSha256 = await sha256(jarPath);
  if (manifest.jarSha256 !== jarSha256) throw new Error('runtime JAR hash mismatch');
  evidence.sourceManifest = { ...manifest, verifiedAt: new Date().toISOString() };
  evidence.provenance.before = {
    sourceSha256: manifest.sourceSha256,
    jarSha256,
    harnessSha256: await sha256(resolve(root, 'qa/m10-acceptance.mjs')),
  };
  check('source-runtime-identity', true, 'source manifest and runtime JAR hashes match');
}

async function safeJson(response) {
  try { return await response.json(); } catch { return null; }
}

async function call(method, path, token, body) {
  const headers = { Origin: frontendUrl };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await api.fetch(path, { method, headers, data: body });
  return { status: response.status(), headers: response.headers(), body: await safeJson(response) };
}

function data(result) { return result?.body?.data ?? null; }

function cookiePair(headers) {
  const raw = headers?.['set-cookie'];
  if (typeof raw !== 'string' || !raw) return null;
  const first = raw.split(/,(?=[^;=]+=[^;]+)/, 1)[0];
  const pair = first.split(';', 1)[0];
  return pair.includes('=') ? pair : null;
}

function syntheticEmail(value) {
  return typeof value === 'string' && /@(example\.test|zeroverse\.test)$/i.test(value);
}

async function isolatedRefreshLifecycle(account) {
  if (!requestFactory || !account?.email || !account?.password) {
    check('refresh-rotation', false, 'NOT RUN: isolated request contexts are unavailable', { required: true });
    check('logout-refresh-reuse-rejected', false, 'NOT RUN: isolated request contexts are unavailable', { required: true });
    return;
  }

  const ownerContext = await requestFactory.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });
  const oldCookieContext = await requestFactory.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });
  try {
    const signed = await ownerContext.post('/api/v1/auth/signin', {
      data: { email: account.email, password: account.password },
    });
    const oldCookie = cookiePair(signed.headers());
    const rotated = await ownerContext.post('/api/v1/auth/refresh');
    const newCookie = cookiePair(rotated.headers());

    // The old cookie is deliberately replayed from a separate context. Values
    // remain in memory only and are never placed in evidence or error details.
    const oldReuse = oldCookie
      ? await oldCookieContext.post('/api/v1/auth/refresh', { headers: { Cookie: oldCookie } })
      : null;
    const signedOut = await ownerContext.post('/api/v1/auth/signout');
    const afterLogout = await ownerContext.post('/api/v1/auth/refresh');

    check(
      'refresh-rotation',
      signed.status() === 200
        && rotated.status() === 200
        && Boolean(oldCookie)
        && Boolean(newCookie)
        && oldCookie !== newCookie,
      'isolated owner context received a distinct rotated refresh cookie',
    );
    check(
      'logout-refresh-reuse-rejected',
      signedOut.status() === 200
        && oldReuse !== null
        && [401, 403].includes(oldReuse.status())
        && [401, 403].includes(afterLogout.status()),
      'old-cookie replay and post-logout refresh were rejected in isolated contexts',
    );
  } finally {
    await ownerContext.dispose();
    await oldCookieContext.dispose();
  }
}

async function registerAndSetup(label) {
  const key = `${suffix}-${label}`;
  const account = {
    email: `m10-${label}-${key}@example.test`,
    password: `M10!Qa${key}7`,
    name: `M10 QA ${label}`,
    nickname: `m10${label}${suffix.slice(-6)}`.slice(0, 20),
    birthDate: '1990-01-01',
    title: `M10 ${label} blog ${suffix.slice(-6)}`,
    slug: `m10-${label}-${suffix.slice(-8)}`.toLowerCase(),
  };
  const registered = await call('POST', '/api/v1/auth/register', undefined, {
    email: account.email, password: account.password, name: account.name,
    nickname: account.nickname, birthDate: account.birthDate,
  });
  check(`synthetic-register-${label}`, registered.status === 201, 'synthetic account registered');
  if (registered.status !== 201) throw new Error(`${label} registration failed`);
  const signed = await call('POST', '/api/v1/auth/signin', undefined, { email: account.email, password: account.password });
  const token = data(signed)?.accessToken;
  check(`signin-${label}`, signed.status === 200 && typeof token === 'string', 'access token held in memory only');
  if (!token) throw new Error(`${label} signin failed`);
  const me = await call('GET', '/api/v1/auth/me', token);
  const blogBefore = await call('GET', '/api/v1/blogs/me', token);
  const setup = await call('PUT', '/api/v1/blogs/me/initial-setup', token, {
    title: account.title, urlSlug: account.slug, description: `M10 synthetic ${label}`,
  });
  check(`setup-${label}`, setup.status === 200, 'synthetic blog setup completed');
  if (setup.status !== 200) throw new Error(`${label} setup failed`);
  const blog = data(setup) ?? data(await call('GET', '/api/v1/blogs/me', token));
  const id = Number(data(me)?.id);
  const blogId = Number(blog?.id ?? data(blogBefore)?.id);
  evidence.fixture.accountIds[label] = Number.isInteger(id) ? id : null;
  evidence.fixture.blogIds[label] = Number.isInteger(blogId) ? blogId : null;
  return { ...account, token, id, blogId, blog };
}

async function registerPending(label) {
  const key = `${suffix}-${label}`;
  const account = {
    email: `m10-${label}-${key}@example.test`,
    password: `M10!Qa${key}7`,
    name: `M10 QA ${label}`,
    nickname: `m10${label}${suffix.slice(-6)}`.slice(0, 20),
    birthDate: '1990-01-01',
  };
  const registered = await call('POST', '/api/v1/auth/register', undefined, {
    email: account.email,
    password: account.password,
    name: account.name,
    nickname: account.nickname,
    birthDate: account.birthDate,
  });
  check(
    'synthetic-register-pending',
    registered.status === 201,
    registered.status === 201 ? 'synthetic pending-setup browser fixture registered' : 'NOT RUN: pending-setup browser fixture could not be registered',
    { required: false },
  );
  return registered.status === 201 ? account : null;
}

async function verifyOpenApi() {
  const response = await api.fetch('/v3/api-docs');
  const spec = await safeJson(response);
  const paths = spec?.paths ?? {};
  const expected = [
    '/api/v1/auth/register', '/api/v1/auth/signin', '/api/v1/auth/refresh', '/api/v1/auth/signout',
    '/api/v1/posts', '/api/v1/universe/requests/{targetUserId}', '/api/v1/notifications',
    '/api/v1/admin/users', '/api/v1/uploads', '/api/v1/search',
  ];
  const ok = response.status() === 200 && expected.every((path) => paths[path]);
  check('runtime-openapi', response.status() === 200, 'generated /v3/api-docs reachable');
  check('openapi-required-paths', ok, 'required M10 endpoint paths exist in generated OpenAPI');
  if (!ok) throw new Error('OpenAPI readiness failed');
}

async function runHttpAcceptance() {
  owner = await registerAndSetup('owner');
  friend = await registerAndSetup('friend');
  const pending = await registerPending('pending');
  const categories = await call('GET', `/api/v1/blogs/${owner.blogId}/categories`, owner.token);
  const defaultCategory = data(categories)?.items?.find((item) => item.type === 'DEFAULT') ?? data(categories)?.[0];
  const categoryId = Number(defaultCategory?.id);
  const emptyDraft = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: '', contentJson: { type: 'doc', content: [] }, contentHtml: '',
    categoryId: Number.isInteger(categoryId) ? categoryId : null, visibility: 'PRIVATE', publish: false,
    thumbnailUrl: null, tagNames: [], images: [],
  });
  check('draft-empty-allowed', emptyDraft.status === 201, 'draft permits empty title/body');
  const sanitizeProbe = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: `M10 sanitize ${suffix}`,
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: '<script>alert(1)</script>' }] }] },
    contentHtml: '<p><script>alert(1)</script></p>', categoryId: Number.isInteger(categoryId) ? categoryId : null,
    visibility: 'PRIVATE', publish: false, thumbnailUrl: null, tagNames: [], images: [],
  });
  const sanitizedHtml = String(data(sanitizeProbe)?.contentHtml ?? '');
  check(
    'content-sanitize',
    sanitizeProbe.status === 201 && !/<script\b/i.test(sanitizedHtml) && /alert\(1\)/i.test(sanitizedHtml),
    'content HTML is rendered/sanitized without retaining an executable script element',
  );
  const published = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: `M10 public ${suffix}`,
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: `M10 public ${suffix}` }] }] },
    contentHtml: `<p>M10 public ${suffix}</p>`, categoryId: Number.isInteger(categoryId) ? categoryId : null,
    visibility: 'PUBLIC', publish: true, thumbnailUrl: null, tagNames: ['m10'], images: [],
  });
  post = data(published);
  evidence.fixture.postIds.public = Number(post?.id) || null;
  check('publish-public', published.status === 201 && Number.isInteger(post?.id), 'public post created');
  const guestRead = await call('GET', `/api/v1/posts/${post?.id}`, undefined);
  check('public-guest-read', guestRead.status === 200, 'guest can read PUBLIC post');
  const envelope = guestRead.body && typeof guestRead.body.success === 'boolean' && 'timestamp' in guestRead.body;
  check('common-envelope', envelope, 'response includes common success/timestamp envelope');
  check('private-no-store', /no-store/i.test(guestRead.headers['cache-control'] ?? ''), 'sensitive read has private,no-store');
  const invalidBearer = await call('GET', `/api/v1/posts/${post?.id}`, 'not-a-token');
  check('invalid-bearer-rejected', invalidBearer.status === 401, 'malformed bearer is rejected');
  const list = await call('GET', `/api/v1/blogs/${owner.blogId}/posts?page=0&size=20`, owner.token);
  const page = data(list);
  check('pagination-shape', list.status === 200 && Number.isInteger(page?.page) && Array.isArray(page?.items), 'offset page shape is present');

  // The shared API context intentionally has multiple synthetic sign-ins above;
  // its cookie jar is not a valid rotation fixture. Use isolated contexts so a
  // friend sign-in cannot overwrite the owner's cookie under test.
  await isolatedRefreshLifecycle(owner);

  const universePublished = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: `M10 universe ${suffix}`,
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: `M10 universe ${suffix}` }] }] },
    contentHtml: `<p>M10 universe ${suffix}</p>`, categoryId: Number.isInteger(categoryId) ? categoryId : null,
    visibility: 'UNIVERSE', publish: true, thumbnailUrl: null, tagNames: ['m10-universe'], images: [],
  });
  const universePost = data(universePublished);
  evidence.fixture.postIds.universe = Number(universePost?.id) || null;
  check('universe-post-created', universePublished.status === 201 && Number.isInteger(universePost?.id), 'synthetic UNIVERSE post created', { required: false });
  if (universePublished.status !== 201 || !Number.isInteger(universePost?.id)) {
    throw new Error('UNIVERSE fixture creation failed; relation/read checks were not executed');
  }
  const beforeAccept = await call('GET', `/api/v1/posts/${universePost?.id}`, friend.token);
  const deniedBeforeAccept = [401, 403].includes(beforeAccept.status);

  const request = await call('POST', `/api/v1/universe/requests/${owner.id}`, friend.token);
  const requestId = Number(data(request)?.requestId ?? data(request)?.id);
  check('universe-request', request.status === 201 && Number.isInteger(requestId), 'universe request created');
  const received = await call('GET', '/api/v1/universe/requests/received', owner.token);
  const receivedItem = data(received)?.items?.find((item) => Number(item.id) === requestId) ?? data(received)?.items?.[0];
  const accepted = await call('POST', `/api/v1/universe/requests/${receivedItem?.id ?? requestId}/accept`, owner.token);
  check('universe-accept', accepted.status === 200, 'universe request accepted');
  const universeRead = await call('GET', `/api/v1/posts/${universePost?.id}`, friend.token);
  check(
    'universe-post-read',
    deniedBeforeAccept && universePublished.status === 201 && universeRead.status === 200,
    `UNIVERSE post denied before acceptance (${beforeAccept.status}) and allowed after acceptance (${universeRead.status})`,
  );

  const interactionPostId = Number(universePost?.id) || Number(post?.id);
  const comment = await call('POST', `/api/v1/posts/${interactionPostId}/comments`, friend.token, { content: `M10 comment ${suffix}` });
  evidence.fixture.commentIds.root = Number(data(comment)?.id) || null;
  check('comment-create', comment.status === 201, 'comment created');
  const like = await call('POST', `/api/v1/posts/${interactionPostId}/likes`, friend.token);
  check('like-create', like.status === 200 || like.status === 201, 'like created');
  const notifications = await call('GET', '/api/v1/notifications?page=0&size=20', owner.token);
  check('notification-list', notifications.status === 200, 'notification list reachable');
  const item = data(notifications)?.items?.[0];
  if (item?.id) {
    const read = await call('PUT', `/api/v1/notifications/${item.id}/read`, owner.token);
    const deleted = await call('DELETE', `/api/v1/notifications/${item.id}`, owner.token);
    check('notification-read-delete', read.status === 200 && deleted.status === 200, 'notification read/delete lifecycle');
  } else check('notification-read-delete', false, 'no synthetic notification item reached', { required: true });
  return { owner, friend, pending, post };
}

async function runAdminAcceptance() {
  const email = process.env.M10_ADMIN_EMAIL;
  const password = process.env.M10_ADMIN_PASSWORD;
  if (!syntheticEmail(email) || !password) {
    check('admin-current-actor', false, 'NOT RUN: synthetic admin credential fixture was not supplied', { required: true });
    return;
  }

  const signed = await call('POST', '/api/v1/auth/signin', undefined, { email, password });
  const token = data(signed)?.accessToken;
  const listing = token
    ? await call('GET', '/api/v1/admin/users?page=0&size=1', token)
    : null;
  const listingData = data(listing);
  const responseText = JSON.stringify(listing?.body ?? '');
  const noCredentialFields = !/(?:password|refreshToken|accessToken|clientSecret|privateKey)/i.test(responseText);
  check(
    'admin-current-actor',
    signed.status === 200
      && typeof token === 'string'
      && listing?.status === 200
      && Array.isArray(listingData?.items)
      && noCredentialFields,
    'synthetic admin signed in and current ACTIVE actor reached admin list without credential fields',
  );
}

function markRuntimeEvidenceNotRun() {
  // These checks require a runtime-specific SQL/HTTP fixture contract that is
  // intentionally not guessed by this generic runner. Environment variable
  // presence alone can never make an acceptance check pass.
  check(
    'admin-soft-delete-preservation',
    false,
    'NOT RUN: no verified read-only SQL/helper result was supplied for child-row preservation',
    { required: true },
  );
  check(
    'upload-5mib-mime',
    false,
    'NOT RUN: no verified multipart byte/MIME runtime evidence was supplied',
    { required: true },
  );
  check(
    'restart-persistence',
    false,
    'NOT RUN: restart persistence requires an isolated runtime process/DB fixture',
    { required: true },
  );
}

async function runBrowserAcceptance(accounts) {
  const { chromium } = require(playwrightModule);
  browser = await chromium.launch({ headless, executablePath: chromeExecutable, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const contexts = [];
  const makeContext = async () => {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    contexts.push(context);
    return context;
  };
  const ownerPage = await (await makeContext()).newPage();
  const guestPage = await (await makeContext()).newPage();
  const pendingPage = accounts.pending ? await (await makeContext()).newPage() : null;
  const adminFixture = syntheticEmail(process.env.M10_ADMIN_EMAIL) && Boolean(process.env.M10_ADMIN_PASSWORD)
    ? { email: process.env.M10_ADMIN_EMAIL, password: process.env.M10_ADMIN_PASSWORD }
    : null;
  const adminPage = adminFixture ? await (await makeContext()).newPage() : null;

  async function browserSignIn(page, account) {
    if (!page || !account?.email || !account?.password) return false;
    try {
      await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
      const email = page.locator('input[type="email"]').first();
      const password = page.locator('input[type="password"]').first();
      if (await email.count() === 0 || await password.count() === 0) return false;
      await email.fill(account.email);
      await password.fill(account.password);
      const submit = page.locator('form button[type="submit"]').first();
      if (await submit.count() === 0) return false;
      const routeChange = page
        .waitForURL((url) => !/^\/signin\/?$/.test(url.pathname), { timeout: 12000 })
        .then(() => true)
        .catch(() => false);
      await submit.click();
      const changed = await routeChange;
      await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
      return changed && !/^\/signin\/?$/.test(new URL(page.url()).pathname);
    } catch {
      return false;
    }
  }

  const ownerSignedIn = await browserSignIn(ownerPage, accounts.owner);
  const pendingSignedIn = await browserSignIn(pendingPage, accounts.pending);
  const adminSignedIn = await browserSignIn(adminPage, adminFixture);
  const blogSlug = accounts.owner.blog?.urlSlug ?? accounts.owner.slug;
  for (const [key, route] of canonicalScreens) {
    const path = route.replace('__BLOG_SLUG__', blogSlug).replace('__POST_ID__', String(accounts.post?.id ?? '0')).replace('__OWNER_ID__', String(accounts.owner.id ?? '0'));
    const target = key === 'auth'
      ? guestPage
      : key === 'setup'
        ? (pendingSignedIn ? pendingPage : null)
        : key === 'admin' || key === 'admin-detail'
          ? (adminSignedIn ? adminPage : null)
          : key === 'blog' || key === 'post-detail' || key === 'search'
            ? guestPage
            : ownerSignedIn ? ownerPage : null;
    if (!target) {
      check(`screenshot-${key}`, false, `NOT RUN: required ${key} browser fixture/session was not established`, { required: true });
      continue;
    }
    try {
      const response = await target.goto(`${frontendUrl}${path}`, { waitUntil: 'domcontentloaded' });
      await target.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
      const expected = screenExpectations[key];
      const actualPath = new URL(target.url()).pathname;
      const routeMatches = Boolean(expected?.pathname.test(actualPath));
      const visibleAnchors = [];
      for (const selector of expected?.selectors ?? []) {
        const locator = target.locator(selector).first();
        if (await locator.count() > 0 && await locator.isVisible().catch(() => false)) visibleAnchors.push(selector);
      }
      const visible = (response?.status() ?? 0) >= 200
        && (response?.status() ?? 0) < 400
        && routeMatches
        && visibleAnchors.length > 0;
      check(
        `screenshot-${key}`,
        visible,
        `${key}: requested ${path}, landed ${actualPath}, anchors=${visibleAnchors.join('|') || 'none'}`,
      );
      if (screenshotsEnabled && visible) {
        const file = `${key}.png`;
        await target.screenshot({ path: resolve(outputDir, file), fullPage: false });
        evidence.screenshots.push({ key, file, status: response?.status() ?? null, path: actualPath });
      }
    } catch (error) {
      check(`screenshot-${key}`, false, `NOT RUN: browser route failed (${scrub(error instanceof Error ? error.message : error)})`, { required: true });
    }
  }

  const notRunKeyboard = (id, detail) => check(id, false, `NOT RUN: ${detail}`, { required: true });
  const waitNetwork = async (page) => {
    await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
  };

  // TopBar: open the actual notification popover with the semantic link and
  // verify the documented Escape close behavior. Focus return/trapping is only
  // observed, not promoted to a new contract.
  if (!ownerSignedIn) {
    notRunKeyboard('keyboard-topbar-observation', 'owner browser session was not established');
  } else {
    try {
      await ownerPage.goto(`${frontendUrl}/`, { waitUntil: 'domcontentloaded' });
      await waitNetwork(ownerPage);
      const trigger = ownerPage.locator('nav[aria-label="사용자 메뉴"] a[aria-label="알림"]').first();
      const dialog = ownerPage.locator('[role="dialog"][aria-label="최근 알림"]').first();
      if (await trigger.count() === 0 || !await trigger.isVisible()) {
        notRunKeyboard('keyboard-topbar-observation', 'TopBar notification trigger is absent');
      } else {
        await trigger.focus();
        await ownerPage.keyboard.press('Enter');
        const opened = await dialog.isVisible().catch(() => false);
        await ownerPage.keyboard.press('Escape');
        const closed = !(await dialog.isVisible().catch(() => false));
        check(
          'keyboard-topbar-observation',
          opened && closed,
          `TopBar notification trigger Enter opened=${opened} and Escape closed=${closed}; focus-trap/restore remains observation-only`,
        );
      }
    } catch (error) {
      notRunKeyboard('keyboard-topbar-observation', `route-specific trigger failed (${scrub(error instanceof Error ? error.message : error)})`);
    }
  }

  // Modal: use the actual admin detail status trigger, then close through the
  // dialog's documented cancel button. Modal Escape is observed for residual
  // UX but is not itself a hard blocker because the contract does not promise it.
  if (!adminSignedIn) {
    notRunKeyboard('keyboard-modal-observation', 'synthetic admin browser session was not established');
  } else {
    try {
      await adminPage.goto(`${frontendUrl}/admin/users/${accounts.owner.id}`, { waitUntil: 'domcontentloaded' });
      await waitNetwork(adminPage);
      const trigger = adminPage.getByRole('button', { name: /일시 정지|정지 해제/ }).first();
      const dialog = adminPage.locator('[role="dialog"]').first();
      if (await trigger.count() === 0 || !await trigger.isVisible().catch(() => false) || !await trigger.isEnabled().catch(() => false)) {
        notRunKeyboard('keyboard-modal-observation', 'admin status modal trigger is absent or disabled');
      } else {
        await trigger.focus();
        await adminPage.keyboard.press('Enter');
        const opened = await dialog.isVisible().catch(() => false);
        await adminPage.keyboard.press('Escape');
        const escapeObserved = await dialog.isVisible().catch(() => false);
        const cancel = dialog.getByRole('button', { name: '취소' }).first();
        if (await cancel.count() > 0 && await cancel.isVisible().catch(() => false)) {
          await cancel.focus();
          await adminPage.keyboard.press('Enter');
        }
        const closedByDocumentedCancel = !(await dialog.isVisible().catch(() => false));
        check(
          'keyboard-modal-observation',
          opened && escapeObserved && closedByDocumentedCancel,
          `status trigger opened=${opened}; Escape observed=${escapeObserved}; cancel Enter closed=${closedByDocumentedCancel}; focus-trap/restore remains observation-only`,
        );
      }
    } catch (error) {
      notRunKeyboard('keyboard-modal-observation', `route-specific modal flow failed (${scrub(error instanceof Error ? error.message : error)})`);
    }
  }

  // DraftPicker: open through the editor's real "임시저장 가져오기" trigger,
  // verify its dialog, then close using the existing close button.
  if (!ownerSignedIn) {
    notRunKeyboard('keyboard-draft-picker-observation', 'owner browser session was not established');
  } else {
    try {
      await ownerPage.goto(`${frontendUrl}/write`, { waitUntil: 'domcontentloaded' });
      await waitNetwork(ownerPage);
      const trigger = ownerPage.getByRole('button', { name: '임시저장 가져오기' }).first();
      const dialog = ownerPage.locator('[role="dialog"][aria-labelledby="draft-picker-title"]').first();
      if (await trigger.count() === 0 || !await trigger.isVisible().catch(() => false) || !await trigger.isEnabled().catch(() => false)) {
        notRunKeyboard('keyboard-draft-picker-observation', 'DraftPicker trigger is absent or disabled');
      } else {
        await trigger.focus();
        await ownerPage.keyboard.press('Enter');
        const opened = await dialog.isVisible().catch(() => false);
        await ownerPage.keyboard.press('Escape');
        const escapeObserved = await dialog.isVisible().catch(() => false);
        const close = dialog.getByRole('button', { name: '임시저장 목록 닫기' }).first();
        if (await close.count() > 0 && await close.isVisible().catch(() => false)) {
          await close.focus();
          await ownerPage.keyboard.press('Enter');
        }
        const closedByDocumentedButton = !(await dialog.isVisible().catch(() => false));
        check(
          'keyboard-draft-picker-observation',
          opened && escapeObserved && closedByDocumentedButton,
          `DraftPicker trigger Enter opened=${opened}; Escape observed=${escapeObserved}; close Enter closed=${closedByDocumentedButton}; focus-trap remains observation-only`,
        );
      }
    } catch (error) {
      notRunKeyboard('keyboard-draft-picker-observation', `route-specific DraftPicker flow failed (${scrub(error instanceof Error ? error.message : error)})`);
    }
  }

  // Category: exercise the existing GENERAL rename control with Enter, then
  // cancel the inline editor with its documented Escape handler. A missing
  // GENERAL fixture is NOT RUN, never a generic button PASS.
  if (!ownerSignedIn) {
    notRunKeyboard('keyboard-category-observation', 'owner browser session was not established');
  } else {
    try {
      await ownerPage.goto(`${frontendUrl}/settings/posts`, { waitUntil: 'domcontentloaded' });
      await waitNetwork(ownerPage);
      const trigger = ownerPage.locator('button[aria-label$="이름 변경"]').first();
      if (await trigger.count() === 0 || !await trigger.isVisible().catch(() => false) || !await trigger.isEnabled().catch(() => false)) {
        notRunKeyboard('keyboard-category-observation', 'enabled GENERAL category rename trigger is absent');
      } else {
        await trigger.focus();
        await ownerPage.keyboard.press('Enter');
        const editor = ownerPage.locator('input[aria-label$="이름 편집"]').first();
        const opened = await editor.isVisible().catch(() => false);
        await ownerPage.keyboard.press('Escape');
        const cancelled = !(await editor.isVisible().catch(() => false));
        check(
          'keyboard-category-observation',
          opened && cancelled,
          `category rename Enter opened=${opened}; Escape cancelled=${cancelled}; focus-trap remains observation-only`,
        );
      }
    } catch (error) {
      notRunKeyboard('keyboard-category-observation', `route-specific category flow failed (${scrub(error instanceof Error ? error.message : error)})`);
    }
  }

  // Admin: exercise the actual list search form with Enter and require the
  // URL query state change. Role/status/delete confirmation is covered by the
  // Modal row above; no generic admin control is treated as evidence.
  if (!adminSignedIn) {
    notRunKeyboard('keyboard-admin-observation', 'synthetic admin browser session was not established');
  } else {
    try {
      await adminPage.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
      await waitNetwork(adminPage);
      const input = adminPage.locator('input[aria-label="관리자 사용자 검색"]').first();
      const form = adminPage.locator('form[role="search"]').first();
      if (await input.count() === 0 || !await input.isVisible().catch(() => false) || await form.count() === 0) {
        notRunKeyboard('keyboard-admin-observation', 'admin search form is absent');
      } else {
        await input.fill(`m10-${suffix.slice(-6)}`);
        await input.focus();
        await adminPage.keyboard.press('Enter');
        await waitNetwork(adminPage);
        const url = new URL(adminPage.url());
        const queryChanged = url.pathname === '/admin' && url.searchParams.get('keyword') === `m10-${suffix.slice(-6)}`;
        const listAnchor = await adminPage.locator('section[aria-label="관리자 콘솔"]').isVisible().catch(() => false);
        check(
          'keyboard-admin-observation',
          queryChanged && listAnchor,
          `admin search Enter changed keyword query=${queryChanged} and retained visible console=${listAnchor}; focus-trap/restore remains observation-only`,
        );
      }
    } catch (error) {
      notRunKeyboard('keyboard-admin-observation', `route-specific admin search flow failed (${scrub(error instanceof Error ? error.message : error)})`);
    }
  }

  for (const context of contexts) await context.close();
}

async function listProductFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const file = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...await listProductFiles(file));
    else if (/\.(?:java|ts|tsx|js|jsx|mjs|sql|json|yml|yaml|html)$/.test(entry.name)) files.push(file);
  }
  return files;
}

function classifyStaticFinding(patternId, relativePath, line) {
  const normalizedPath = relativePath.replace(/^\/+/, '');
  const inTest = normalizedPath.includes('src/test/')
    || normalizedPath.includes('frontend/src/test/')
    || /(?:\.test|\.spec)\.[^.]+$/.test(normalizedPath)
    || normalizedPath.includes('/__tests__/');
  if ((patternId === 'placeholder' || patternId === 'mock-stub')
      && /placeholder\s*=|placeholder용|placeholders?\b/i.test(line)
      && normalizedPath.startsWith('frontend/src/')) {
    return 'ALLOWED_UI_OR_SQL_PLACEHOLDER_TOKEN';
  }
  if ((patternId === 'mock-stub' || patternId === 'placeholder') && inTest) {
    return 'ALLOWED_TEST_FIXTURE_OR_TEST_DOUBLE';
  }
  return 'UNCLASSIFIED_REQUIRED_REVIEW';
}

async function runProductStaticScan() {
  const roots = ['src/main', 'src/test', 'frontend/src'];
  const patterns = [
    { id: 'test-skip-only', regex: /\b(?:describe|it|test)\.(?:skip|only)\b/ },
    { id: 'disabled-test', regex: /@(?:Disabled|Ignore)\b/ },
    { id: 'todo-stub', regex: /\b(?:TODO|FIXME|HACK|UnsupportedOperationException)\b/ },
    { id: 'mock-stub', regex: /\b(?:mock|stub|placeholder)\b/i },
    { id: 'secret-literal', regex: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\bAKIA[0-9A-Z]{16}\b|Bearer\s+eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/ },
  ];
  const findings = [];
  let filesScanned = 0;
  try {
    for (const rootPath of roots) {
      const absoluteRoot = resolve(root, rootPath);
      const files = await listProductFiles(absoluteRoot);
      for (const file of files) {
        filesScanned += 1;
        const relativePath = relative(root, file);
        const source = await readFile(file, 'utf8');
        source.split(/\r?\n/).forEach((line, index) => {
          for (const pattern of patterns) {
            if (!pattern.regex.test(line)) continue;
            const classification = classifyStaticFinding(pattern.id, `/${relativePath}`, line);
            findings.push({
              pattern: pattern.id,
              path: relativePath,
              line: index + 1,
              classification,
            });
          }
        });
      }
    }
  } catch (error) {
    evidence.staticScan = {
      roots,
      filesScanned,
      findings: [],
      error: scrub(error instanceof Error ? error.message : error),
    };
    check('static-skip-placeholder-scan', false, 'NOT RUN: required product static scan could not read all roots', { required: true });
    return;
  }

  const unclassified = findings.filter((finding) => finding.classification === 'UNCLASSIFIED_REQUIRED_REVIEW');
  evidence.staticScan = {
    roots,
    filesScanned,
    findings,
    classificationSummary: {
      allowed: findings.length - unclassified.length,
      requiredReview: unclassified.length,
    },
  };
  check(
    'static-skip-placeholder-scan',
    unclassified.length === 0,
    unclassified.length === 0
      ? `product static scan covered ${filesScanned} files; all findings were explicitly classified`
      : `product static scan found ${unclassified.length} unclassified finding(s)`,
    { required: true },
  );
}

async function staticSelfCheck() {
  const files = ['qa/m10-acceptance.mjs', 'qa/M10-review.md'];
  for (const file of files) await access(resolve(root, file), fsConstants.R_OK);
  const source = await readFile(resolve(root, 'qa/m10-acceptance.mjs'), 'utf8');
  const staleFalsePass = [
    /check\('admin-current-actor',\s*Boolean\(/,
    /check\('admin-soft-delete-preservation',\s*Boolean\(/,
    /page\.keyboard\.press\('Escape'\);\s*check\(id,\s*true/,
    /locator\('body'\)\.evaluate\(\(body\)\s*=>\s*Boolean\(body/,
    /Boolean\(before\s*&&\s*after\s*&&\s*stillVisible\)/,
  ].some((pattern) => pattern.test(source));
  check('harness-fail-closed-guards', !staleFalsePass, 'runner contains no known false-pass guard patterns', { required: false });
  await runProductStaticScan();
  if (canonicalScreens.length !== 13) throw new Error('canonical screen list must contain exactly 13 routes');
  if (requiredChecks.length < 40) throw new Error('required M10 checks unexpectedly incomplete');
}

async function currentManifestIdentity() {
  const manifest = evidence.sourceManifest;
  if (!manifest || !Array.isArray(manifest.files)) return null;
  const entries = [];
  for (const entry of manifest.files) {
    const file = resolve(root, entry.path);
    entries.push({ path: entry.path, sha256: await sha256(file) });
  }
  entries.sort((a, b) => a.path.localeCompare(b.path));
  const sourceSha256 = createHash('sha256')
    .update(entries.map((entry) => `${entry.path}\0${entry.sha256}\n`).join(''))
    .digest('hex');
  const jarPath = resolve(root, manifest.jarPath ?? 'build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar');
  return {
    sourceSha256,
    jarSha256: await sha256(jarPath),
    harnessSha256: await sha256(resolve(root, 'qa/m10-acceptance.mjs')),
  };
}

async function writeEvidence() {
  if (!evidence.checks.some((item) => item.id === 'provenance-hash-stability')) {
    try {
      evidence.provenance.after = await currentManifestIdentity();
      const before = evidence.provenance.before;
      const after = evidence.provenance.after;
      check(
        'provenance-hash-stability',
        Boolean(before && after)
          && before.sourceSha256 === after.sourceSha256
          && before.jarSha256 === after.jarSha256
          && before.harnessSha256 === after.harnessSha256,
        before && after
          ? 'source aggregate, JAR, and harness hashes are stable before/after acceptance'
          : 'NOT RUN: final source/JAR manifest provenance was not loaded',
        { required: true },
      );
    } catch (error) {
      evidence.provenance.after = { error: scrub(error instanceof Error ? error.message : error) };
      check('provenance-hash-stability', false, 'NOT RUN: after-hash capture failed', { required: true });
    }
  }
  evidence.finishedAt = new Date().toISOString();
  await mkdir(outputDir, { recursive: true });
  const missing = requiredChecks.filter((id) => !evidence.checks.some((item) => item.id === id));
  for (const id of missing) evidence.checks.push({ id, ok: false, required: true, detail: 'NOT RUN: runner did not reach this required check' });
  const failures = evidence.checks.filter((item) => item.required && !item.ok);
  evidence.status = selfCheck
    ? 'SELF-CHECK PASS'
    : failures.length === 0 && evidence.failures.length === 0 ? 'PASS' : 'NOT RUN';
  await writeFile(resolve(outputDir, 'evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
}

async function main() {
  try {
    await staticSelfCheck();
    if (selfCheck) {
      evidence.status = 'SELF-CHECK PASS';
      await writeEvidence();
      return;
    }
    if (process.env.M10_MYSQL_PORT === '13307') throw new Error('refusing user MySQL port 13307');
    await verifyManifest();
    requestFactory = require(playwrightModule).request;
    api = await requestFactory.newContext({ baseURL: apiBaseUrl, extraHTTPHeaders: { Origin: frontendUrl } });
    await verifyOpenApi();
    const accounts = await runHttpAcceptance();
    await runAdminAcceptance();
    markRuntimeEvidenceNotRun();
    await runBrowserAcceptance(accounts);
  } catch (error) {
    evidence.failures.push(scrub(error instanceof Error ? error.message : error));
  } finally {
    await api?.dispose();
    await browser?.close();
    await writeEvidence();
  }
  if (evidence.status !== 'PASS') process.exitCode = 1;
}

await main();
