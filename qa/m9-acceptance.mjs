/**
 * M9 관리자 독립 실제 인수 runner.
 *
 * QA 소유 black-box 도구다. 실제 실행 시에도 합성 계정만 HTTP로 생성하고,
 * access token/password/email/body marker는 메모리에만 둔다. evidence에는
 * 합성 ID·hash·check 결과·screenshot 파일명만 남긴다.
 *
 * 기본 runtime:
 *   M9_API_BASE_URL=http://localhost:18089
 *   M9_FRONTEND_URL=http://localhost:5179
 *   M9_MYSQL_PORT=13319 (root synthetic runtime helper만 사용)
 *   M9_RUNTIME_SQL_HELPER=build/m9-runtime.py
 *   M9_PLAYWRIGHT_MODULE=/path/to/playwright/node_modules/playwright
 *
 * 사용자 DB(13307), .env.local, 기존 업로드·파일 저장소에는 접근하지 않는다.
 * M9_RUNTIME_SQL_HELPER는 root가 격리 MySQL에서만 실행한다. fingerprint는
 * stdin/stdout JSON handshake를 사용하고, query-count는 고정된 performance_schema
 * scalar SELECT만 사용한다. 사용자 입력을 SQL로 조합하지 않는다.
 *
 * 정본 계약: REQUIREMENTS §6.13-A, ADR-0008, M9 회의 §5·§10·§10.1·§11.
 * A-v2 승인 자체는 PASS가 아니며, runtime/source/JAR/BE/FE evidence가 모두
 * 준비되기 전에는 acceptance를 완료로 주장하지 않는다.
 */

import { createRequire } from 'node:module';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M9_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium, request: requestApi } = require(playwrightModule);

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const apiBaseUrl = process.env.M9_API_BASE_URL ?? 'http://localhost:18089';
const frontendUrl = process.env.M9_FRONTEND_URL ?? 'http://localhost:5179';
const mysqlPort = Number(process.env.M9_MYSQL_PORT ?? 13319);
const chromeExecutable = process.env.M9_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const outputDir = resolve(root, process.env.M9_OUTPUT_DIR ?? `build/m9-acceptance-${Date.now().toString(36)}`);
const seedFile = resolve(root, process.env.M9_FIXTURE_SEED_FILE ?? `${outputDir}/fixture-seed.json`);
const statusReadyFile = resolve(root, process.env.M9_STATUS_READY_FILE ?? `${outputDir}/status-ready.json`);
const versionManifestFile = resolve(root, process.env.M9_VERSION_MANIFEST ?? 'build/m9-evidence/tested-version.json');
const runtimeSqlHelper = resolve(root, process.env.M9_RUNTIME_SQL_HELPER ?? 'build/m9-runtime.py');
const runtimePython = process.env.M9_RUNTIME_PYTHON ?? 'python3';
const requireFull = process.env.M9_REQUIRE_FULL !== '0';
const requireStatusFixtures = process.env.M9_REQUIRE_STATUS_FIXTURES !== '0';
const waitForStatusFixturesFlag = process.env.M9_WAIT_FOR_STATUS_FIXTURES === '1';
const statusWaitMs = Number(process.env.M9_STATUS_WAIT_MS ?? 180_000);
const screenshotsEnabled = process.env.M9_SCREENSHOTS !== '0';
const chromeHeadless = process.env.M9_HEADLESS !== '0';
const runSuffix = `${Date.now().toString(36)}${randomBytes(5).toString('hex')}`;

const fixture = makeFixtureCredentials(runSuffix);
const evidence = {
  milestone: 'M9',
  contract: 'A-v2',
  status: 'NOT RUN',
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  apiBaseUrl,
  frontendUrl,
  mysqlPort,
  fixture: {
    runId: fixture.runId,
    accountIds: {},
    blogIds: {},
    postIds: {},
    commentIds: {},
    statusReady: false,
    statusPhase: null,
    fingerprint: {},
    queryCounts: {},
  },
  version: null,
  screenshots: [],
  checks: [],
  failures: [],
};

let apiContext;
let browser;

try {
  await mkdir(outputDir, { recursive: true });
  apiContext = await requestApi.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });

  await verifyApiReadiness();
  const version = await verifyVersionManifest();
  evidence.version = version.evidence;
  check('source-runtime-identity', version.ok, version.detail ?? 'source manifest and immutable JAR hashes match');
  if (requireFull && !version.ok) throw new Error(`source/runtime identity gate failed: ${version.detail}`);

  browser = await chromium.launch({
    headless: chromeHeadless,
    executablePath: chromeExecutable,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  const accounts = {};
  for (const [label, account] of Object.entries(fixture.accounts)) {
    accounts[label] = await registerAndSetup(account, label);
  }
  const posts = await createFixturePosts(accounts);
  await createFixtureComments(accounts, posts);
  await writeFixtureSeed(accounts, posts);

  const statusReady = await waitForStatusFixtures();
  evidence.fixture.statusReady = statusReady;
  if (!statusReady && requireStatusFixtures) throw new Error('synthetic M9 status fixtures are not ready');

  await runHttpAcceptance(accounts, posts);
  await runBrowserAcceptance(accounts, posts);

  await finishEvidence();
} catch (error) {
  const safeMessage = scrubSecrets(error instanceof Error ? error.message : String(error));
  evidence.failures.push(`runner: ${safeMessage}`);
  evidence.status = 'NOT RUN';
  await writeEvidence();
  throw new Error(`M9 acceptance failed: ${safeMessage}`);
} finally {
  await apiContext?.dispose();
  await browser?.close();
}

function makeFixtureCredentials(suffix) {
  const key = suffix.slice(-8).toLowerCase();
  const password = `M9!Qa${suffix}7`;
  const searchLiteral = `%_\\${key}`;
  const bulkSearchLiteral = `bulk${key}`;
  const labels = ['admin', 'adminPeer', 'adminKeeper', 'user', 'target', 'race', 'suspended', 'deleted', 'special', 'weekBefore', 'weekStart', 'weekAfter'];
  const bulkLabels = Array.from({ length: 21 }, (_, index) => `bulk${String(index + 1).padStart(2, '0')}`);
  const allLabels = [...labels, ...bulkLabels];
  const accounts = Object.fromEntries(allLabels.map((label) => {
    const nickname = label.startsWith('bulk') ? `m9${bulkSearchLiteral}${label.slice(-2)}`.slice(0, 20) : `m9${label.slice(0, 4)}${key}`.slice(0, 20);
    const special = label === 'special';
    return [label, {
      email: `m9-${label}-${suffix}@example.test`,
      password,
      name: `M9 QA ${special ? `name ${searchLiteral}` : label.startsWith('bulk') ? `${bulkSearchLiteral} ${label}` : label}`,
      nickname,
      birthDate: '1990-01-01',
      blogTitle: `M9 ${label} blog ${key}`,
      blogSlug: `m9-${label}-${key}`.toLowerCase().slice(0, 30),
    }];
  }));
  return {
    runId: `m9-${suffix}`,
    key,
    password,
    searchLiteral,
    bulkSearchLiteral,
    labels: allLabels,
    bulkLabels,
    weekBoundaryLabels: ['weekBefore', 'weekStart', 'weekAfter'],
    accounts,
  };
}

async function verifyApiReadiness() {
  const response = await apiContext.fetch('/v3/api-docs', { method: 'GET' });
  const json = await safeJson(response);
  const paths = json?.paths ?? {};
  const expected = [
    '/api/v1/admin/users',
    '/api/v1/admin/users/{userId}',
    '/api/v1/admin/users/{userId}/role',
    '/api/v1/admin/users/{userId}/status',
  ];
  const pathChecks = [
    paths[expected[0]]?.get != null,
    paths[expected[1]]?.get != null,
    paths[expected[1]]?.delete != null,
    paths[expected[2]]?.put != null,
    paths[expected[3]]?.put != null,
  ];
  check('runtime-api-openapi', response.status() === 200, 'backend /v3/api-docs is reachable');
  check('openapi-m9-paths', response.status() === 200 && pathChecks.every(Boolean), 'OpenAPI exposes all M9 admin methods');
  if (response.status() !== 200 || !pathChecks.every(Boolean)) {
    throw new Error('backend runtime is not ready for M9 admin endpoints');
  }
}

async function registerAndSetup(account, label) {
  const registered = await apiCall('POST', '/api/v1/auth/register', undefined, {
    email: account.email,
    password: account.password,
    name: account.name,
    nickname: account.nickname,
    birthDate: account.birthDate,
  });
  check(`${label}-register`, registered.status === 201, 'synthetic account created');
  if (registered.status !== 201) throw new Error(`${label} account registration failed`);

  const signin = await apiCall('POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  const token = signin.data?.accessToken;
  check(`${label}-signin`, signin.status === 200 && typeof token === 'string' && token.length > 20, 'access token held in memory');
  if (typeof token !== 'string') throw new Error(`${label} signin failed`);

  const before = await apiCall('GET', '/api/v1/blogs/me', token);
  const setup = await apiCall('PUT', '/api/v1/blogs/me/initial-setup', token, {
    title: account.blogTitle,
    urlSlug: account.blogSlug,
    description: `M9 synthetic ${label} blog`,
  });
  check(`${label}-initial-setup`, setup.status === 200, 'synthetic blog initialized');
  if (setup.status !== 200) throw new Error(`${label} initial setup failed`);

  const me = await apiCall('GET', '/api/v1/auth/me', token);
  const blog = await apiCall('GET', '/api/v1/blogs/me', token);
  check(`${label}-blog-readback`, me.status === 200 && blog.status === 200
    && Number.isInteger(me.data?.id) && Number.isInteger(blog.data?.id), 'account/blog IDs read back');
  if (!Number.isInteger(me.data?.id) || !Number.isInteger(blog.data?.id)) throw new Error(`${label} fixture IDs missing`);
  check(`${label}-pre-setup-blog`, before.status === 200, 'pre-setup default blog remains readable');
  evidence.fixture.accountIds[label] = me.data.id;
  evidence.fixture.blogIds[label] = blog.data.id;
  return { token, me: me.data, blog: blog.data };
}

async function createFixturePosts(accounts) {
  const posts = {
    adminPublic: await createPost(accounts.admin, 'admin public', 'PUBLIC', true),
    userPublic: await createPost(accounts.user, 'user public', 'PUBLIC', true),
    targetPublic: await createPost(accounts.target, 'target public', 'PUBLIC', true),
    targetPrivate: await createPost(accounts.target, 'target private', 'PRIVATE', true),
    targetDraft: await createPost(accounts.target, 'target draft', 'PRIVATE', false),
    suspendedPublic: await createPost(accounts.suspended, 'suspended public', 'PUBLIC', true),
    deletedActorPublic: await createPost(accounts.deleted, 'deleted actor public', 'PUBLIC', true),
    racePublic: await createPost(accounts.race, 'race public', 'PUBLIC', true),
  };
  const image = await uploadSyntheticImage(accounts.target);
  if (image?.id && image?.url) {
    const bound = await apiCall('PUT', `/api/v1/posts/${posts.targetPublic.id}/images`, accounts.target.token, {
      images: [{ imageUrl: image.url, altText: 'M9 synthetic image', displayOrder: 0 }],
    });
    check('fixture-target-image-bind', bound.status === 200, 'synthetic POST_IMAGE is bound to target post');
    if (bound.status !== 200) throw new Error('fixture image binding failed');
    posts.targetImage = image;
    evidence.fixture.imageIds = { target: image.id };
  }
  for (const [label, post] of Object.entries(posts)) {
    if (label.endsWith('Image')) continue;
    evidence.fixture.postIds[label] = post.id;
  }
  return posts;
}

async function createPost(account, label, visibility, publish) {
  const marker = `${fixture.runId} ${label}`;
  const response = await apiCall('POST', '/api/v1/posts', account.token, {
    blogId: account.blog.id,
    title: `M9 ${label}`,
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: marker }] }] },
    contentHtml: `<p>${marker}</p>`,
    categoryId: null,
    visibility,
    publish,
    thumbnailUrl: null,
    tagNames: [],
    images: [],
  });
  check(`fixture-post-${label}`, response.status === 201 && Number.isInteger(response.data?.id), 'synthetic post created');
  if (response.status !== 201 || !Number.isInteger(response.data?.id)) throw new Error(`fixture post ${label} failed`);
  return response.data;
}

async function createFixtureComments(accounts, posts) {
  const targets = [
    ['deletedActorComment', posts.adminPublic.id, accounts.deleted.token, 'deleted actor comment'],
    ['targetComment', posts.targetPublic.id, accounts.user.token, 'target comment'],
  ];
  for (const [label, postId, token, content] of targets) {
    const response = await apiCall('POST', `/api/v1/posts/${postId}/comments`, token, { content, parentId: null });
    check(`fixture-comment-${label}`, response.status === 201 && Number.isInteger(response.data?.id), 'synthetic comment created');
    if (response.status !== 201 || !Number.isInteger(response.data?.id)) throw new Error(`fixture comment ${label} failed`);
    evidence.fixture.commentIds[label] = response.data.id;
  }
  const notification = await apiCall('GET', '/api/v1/notifications?read=all&page=0&size=100', accounts.admin.token);
  const actorNotification = (notification.data?.items ?? []).find((item) => Number(item.targetId) === Number(evidence.fixture.commentIds.deletedActorComment));
  if (actorNotification?.id != null) evidence.fixture.notificationIds = { deletedActorComment: actorNotification.id };
  check('fixture-deleted-actor-notification', actorNotification?.id != null, 'deleted actor comment notification ID captured before masking');
}

async function uploadSyntheticImage(account) {
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');
  const response = await apiContext.fetch('/api/v1/uploads', {
    method: 'POST',
    headers: { Authorization: `Bearer ${account.token}` },
    multipart: {
      file: { name: 'm9-synthetic.png', mimeType: 'image/png', buffer: png },
      purpose: 'POST_IMAGE',
    },
  });
  const json = await safeJson(response);
  const url = json?.data?.imageUrl;
  const match = typeof url === 'string' ? url.match(/^\/api\/v1\/uploads\/([0-9a-f-]{36})\/content$/i) : null;
  check('fixture-image-upload', response.status() === 201 && match != null, 'authenticated synthetic multipart image uploaded');
  if (response.status() !== 201 || !match) throw new Error('fixture image upload failed');
  return { id: match[1], url, bytes: png, sha256: createHash('sha256').update(png).digest('hex') };
}

async function writeFixtureSeed(accounts, posts) {
  const safe = {
    schema: 'zeroverse.m9.fixture-seed.v1',
    milestone: 'M9',
    contract: 'A-v2',
    runId: fixture.runId,
    accountIds: Object.fromEntries(Object.entries(accounts).map(([label, value]) => [label, value.me.id])),
    blogIds: Object.fromEntries(Object.entries(accounts).map(([label, value]) => [label, value.blog.id])),
    postIds: Object.fromEntries(Object.entries(posts).filter(([label]) => !label.endsWith('Image')).map(([label, value]) => [label, value.id])),
    commentIds: evidence.fixture.commentIds,
    imageIds: evidence.fixture.imageIds ?? {},
    bulkLabels: fixture.bulkLabels,
    weekBoundaryLabels: fixture.weekBoundaryLabels,
    preservedImageIds: Object.values(evidence.fixture.imageIds ?? {}),
    requiredStatusFixture: {
      baseline: {
        admin: { role: 'ADMIN', status: 'ACTIVE', deletedAt: null },
        adminPeer: { role: 'ADMIN', status: 'ACTIVE', deletedAt: null },
        adminKeeper: { role: 'ADMIN', status: 'ACTIVE', deletedAt: null },
        user: { role: 'USER', status: 'ACTIVE', deletedAt: null },
        target: { role: 'USER', status: 'ACTIVE', deletedAt: null },
        race: { role: 'USER', status: 'ACTIVE', deletedAt: null },
        suspended: { role: 'USER', status: 'SUSPENDED', deletedAt: null },
        deleted: { role: 'USER', status: 'ACTIVE', deletedAt: 'non-null' },
      },
      sameCreatedAtLabels: fixture.bulkLabels,
      literalSearchLabel: 'special',
    },
  };
  await writeFile(seedFile, JSON.stringify(safe, null, 2), 'utf8');
}

async function waitForStatusFixtures() {
  const deadline = Date.now() + statusWaitMs;
  do {
    try {
      const raw = await readFile(statusReadyFile, 'utf8');
      const ready = JSON.parse(raw);
      const valid = validateStatusReady(ready);
      if (valid) {
        evidence.fixture.statusPhase = ready.phase ?? 'baseline';
        evidence.fixture.statusReadyAt = ready.appliedAt ?? null;
        evidence.fixture.statusExpectations = safeStatusEvidence(ready);
        return true;
      }
    } catch {
      // Root helper has not completed the isolated fixture handshake yet.
    }
    if (!waitForStatusFixturesFlag) return false;
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 250));
  } while (Date.now() < deadline);
  return false;
}

function validateStatusReady(ready) {
  if (ready?.schema !== 'zeroverse.m9.status-ready.v1'
    || ready.milestone !== 'M9'
    || ready.runId !== fixture.runId
    || ready.phase !== 'baseline'
    || !ready.accounts
    || !Array.isArray(ready.sameCreatedAt?.ids)
    || ready.sameCreatedAt.ids.length !== fixture.bulkLabels.length
    || !validUtc(ready.time?.requestNowUtc)
    || !validUtc(ready.time?.weekStartUtc)
    || !validUtc(ready.time?.weekEndUtc)
    || !Number.isInteger(Number(ready.summaryExpectations?.totalUsers))
    || !Number.isInteger(Number(ready.summaryExpectations?.joinedThisWeek))
    || !Number.isInteger(Number(ready.summaryExpectations?.suspendedUsers))) return false;
  const expected = {
    admin: ['ADMIN', 'ACTIVE', false],
    adminPeer: ['ADMIN', 'ACTIVE', false],
    adminKeeper: ['ADMIN', 'ACTIVE', false],
    user: ['USER', 'ACTIVE', false],
    target: ['USER', 'ACTIVE', false],
    race: ['USER', 'ACTIVE', false],
    suspended: ['USER', 'SUSPENDED', false],
    deleted: ['USER', 'ACTIVE', true],
  };
  return Object.entries(expected).every(([label, [role, status, deleted]]) => {
    const current = ready.accounts[label];
    return Number(current?.id) === Number(evidence.fixture.accountIds[label])
      && current.role === role
      && current.status === status
      && Boolean(current.deletedAt) === deleted;
  }) && ready.sameCreatedAt.ids.map(Number).sort((left, right) => left - right).join(',')
    === fixture.bulkLabels.map((bulkLabel) => Number(evidence.fixture.accountIds[bulkLabel])).sort((left, right) => left - right).join(',')
    && (!evidence.fixture.imageIds?.target || ready.preservedImageIds?.includes(evidence.fixture.imageIds.target));
}

function validUtc(value) {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

function safeStatusEvidence(ready) {
  return {
    phase: ready.phase ?? null,
    appliedAt: ready.appliedAt ?? null,
    accounts: Object.fromEntries(Object.entries(ready.accounts ?? {}).map(([label, value]) => [label, {
      id: Number(value?.id), role: value?.role ?? null, status: value?.status ?? null, deleted: Boolean(value?.deletedAt),
    }])),
    time: ready.time ?? null,
    summaryExpectations: ready.summaryExpectations ?? null,
    sameCreatedAtCount: Array.isArray(ready.sameCreatedAt?.ids) ? ready.sameCreatedAt.ids.length : 0,
    preservedImageIds: Array.isArray(ready.preservedImageIds)
      ? ready.preservedImageIds.filter((id) => typeof id === 'string' && /^[0-9a-f-]{36}$/i.test(id))
      : [],
  };
}

async function runHttpAcceptance(accounts, posts) {
  const admin = accounts.admin;
  const keeper = accounts.adminKeeper;
  const targetId = accounts.target.me.id;
  const endpoints = adminEndpoints(targetId);

  const list = await adminList(admin.token, {});
  const listAll = await adminList(admin.token, { size: 100 });
  checkPageResponse('http-admin-list', list, true);
  check('http-admin-list-summary', hasSummary(list), 'list includes summary independent of page/filter');
  check('http-admin-list-no-deleted', !containsId(listAll, accounts.deleted.me.id), 'deleted users are excluded from list');
  check('http-admin-list-privacy', noForbiddenFields(list.data), 'list DTO does not expose secret/body fields');
  const summaryExpected = evidence.fixture.statusExpectations?.summaryExpectations;
  check('http-admin-summary-kst-boundary', hasSummary(list)
    && summaryExpected != null
    && Number(list.data.summary.totalUsers) === Number(summaryExpected.totalUsers)
    && Number(list.data.summary.joinedThisWeek) === Number(summaryExpected.joinedThisWeek)
    && Number(list.data.summary.suspendedUsers) === Number(summaryExpected.suspendedUsers),
  'summary matches root-provided KST week-boundary synthetic expectation');

  const detail = await apiCall('GET', `/api/v1/admin/users/${targetId}`, admin.token);
  checkCanonicalDetail('http-admin-detail', detail, targetId);
  check('http-admin-detail-last-access-null', detail.data?.lastAccessAt === null, 'last access remains explicitly unrecorded');
  check('http-admin-detail-post-stats', detail.data?.postStats?.total === detail.data?.postCount
    && Number(detail.data?.postStats?.published) + Number(detail.data?.postStats?.draft) === Number(detail.data?.postStats?.total),
  'postStats totals are internally consistent');
  check('http-admin-detail-privacy', noForbiddenFields(detail.data), 'detail DTO does not expose secret/body fields');

  await runAdminAuthMatrix(accounts, endpoints);
  await runListMatrix(admin.token, accounts);
  await runQueryBoundMatrix(admin.token, targetId);
  await runMutationMatrix(accounts, posts);
  await runConcurrencyMatrix(accounts);

  const publicAfterDelete = await apiCall('GET', `/api/v1/posts/${posts.targetPublic.id}`);
  check('http-deleted-post-acl', publicAfterDelete.status >= 400 && !containsText(publicAfterDelete, fixture.runId), 'deleted owner post is no longer publicly readable');
  const deletedBlog = await apiCall('GET', `/api/v1/blogs/slug/${encodeURIComponent(accounts.target.blog.urlSlug ?? fixture.accounts.target.blogSlug)}`);
  check('http-deleted-blog-acl', deletedBlog.status >= 400, 'deleted owner blog is no longer publicly readable');

  const survivingComments = await apiCall('GET', `/api/v1/posts/${posts.adminPublic.id}/comments?page=0&size=20`);
  const deletedComment = findItem(survivingComments, evidence.fixture.commentIds.deletedActorComment);
  check('http-deleted-comment-placeholder', survivingComments.status === 200 && deletedComment != null
    && deletedComment.content == null && (deletedComment.author == null || deletedComment.deleted === true),
  'deleted actor comment is preserved as a placeholder on a surviving post');

  const notifications = await apiCall('GET', '/api/v1/notifications?read=all&page=0&size=100', admin.token);
  const maskedNotification = (notifications.data?.items ?? []).find((item) => Number(item.id) === Number(evidence.fixture.notificationIds?.deletedActorComment));
  check('http-deleted-actor-notification-mask', notifications.status === 200 && maskedNotification != null
    && maskedNotification.actor == null && maskedNotification.targetId == null && maskedNotification.href == null,
  'notification actor and target are masked after the actor soft delete');

  const preservedImageIds = evidence.fixture.statusExpectations?.preservedImageIds ?? [];
  if (preservedImageIds.length === 0) {
    notRun('http-deleted-image-preservation', 'status-ready.json did not provide a synthetic image fixture');
  } else {
    const imageChecks = await Promise.all(preservedImageIds.map(async (imageId) => {
      const response = await binaryCall(`/api/v1/uploads/${encodeURIComponent(imageId)}/content`, keeper.token);
      return response;
    }));
    check('http-deleted-image-preservation', imageChecks.every((response) => response.status === 404 && response.errorCode === 'UPLOAD_003'), 'deleted owner image content is exactly UPLOAD_003/404 while row/file fingerprint is preserved');
  }
}

async function runAdminAuthMatrix(accounts, endpoints) {
  const missing = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path)));
  check('auth-missing-admin-endpoints', missing.every((response) => response.status === 401 && response.errorCode === 'AUTH_004' && privateNoStore(response.cacheControl) && response.envelope), 'missing auth is 401 AUTH_004 with envelope/no-store');

  const malformed = await Promise.all(endpoints.map((endpoint) => apiCallWithHeaders(endpoint.method, endpoint.path, { Authorization: 'Basic not-a-bearer' })));
  check('auth-malformed-admin-endpoints', malformed.every((response) => response.status === 401 && response.errorCode === 'AUTH_004' && privateNoStore(response.cacheControl) && response.envelope), 'malformed bearer is not downgraded to guest and remains no-store');

  const user = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, accounts.user.token)));
  check('auth-user-admin-endpoints', user.every((response) => response.status === 403 && response.errorCode === 'ADMIN_001' && privateNoStore(response.cacheControl) && response.envelope), 'current USER is rejected by every admin endpoint');

  const suspended = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, accounts.suspended.token)));
  check('auth-suspended-admin-endpoints', suspended.every((response) => response.status === 403 && response.errorCode === 'USER_003' && privateNoStore(response.cacheControl) && response.envelope), 'current SUSPENDED actor is rejected by every admin endpoint');

  const deleted = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, accounts.deleted.token)));
  check('auth-deleted-admin-endpoints', deleted.every((response) => response.status === 404 && response.errorCode === 'USER_001' && privateNoStore(response.cacheControl) && response.envelope), 'current deleted actor is rejected by every admin endpoint');

  const expired = process.env.M9_EXPIRED_ACCESS_TOKEN;
  if (!expired) notRun('auth-expired-admin-endpoints', 'M9_EXPIRED_ACCESS_TOKEN is not supplied by isolated runtime');
  else {
    const responses = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, expired)));
    check('auth-expired-admin-endpoints', responses.every((response) => response.status === 401 && response.errorCode === 'AUTH_002' && privateNoStore(response.cacheControl) && response.envelope), 'signed expired ACCESS is AUTH_002 with no-store envelope');
  }

  const wrongType = process.env.M9_WRONG_TYPE_TOKEN;
  if (!wrongType) notRun('auth-wrong-type-admin-endpoints', 'M9_WRONG_TYPE_TOKEN is not supplied by isolated runtime');
  else {
    const responses = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, wrongType)));
    check('auth-wrong-type-admin-endpoints', responses.every((response) => response.status === 401 && response.errorCode === 'AUTH_004' && privateNoStore(response.cacheControl) && response.envelope), 'signed wrong-type token is AUTH_004 with no-store envelope');
  }

  for (const [label, variable] of [['auth-missing-role-admin-endpoints', 'M9_MISSING_ROLE_TOKEN'], ['auth-missing-subject-admin-endpoints', 'M9_MISSING_SUBJECT_TOKEN']]) {
    const token = process.env[variable];
    if (!token) notRun(label, `${variable} is not supplied by isolated runtime`);
    else {
      const responses = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, token)));
      check(label, responses.every((response) => response.status === 401 && response.errorCode === 'AUTH_004' && privateNoStore(response.cacheControl)), 'signed incomplete claim is AUTH_004 with no-store on all five methods');
    }
  }

  const invalidInputs = [
    '/api/v1/admin/users?page=-1',
    '/api/v1/admin/users?size=0',
    '/api/v1/admin/users?size=101',
    '/api/v1/admin/users?role=ROOT',
    '/api/v1/admin/users?status=UNKNOWN',
    '/api/v1/admin/users?sort=updatedAt',
    '/api/v1/admin/users/not-an-id',
  ];
  const invalidResponses = await Promise.all(invalidInputs.map((path) => apiCall('GET', path, accounts.admin.token)));
  check('validation-admin-queries', invalidResponses.every((response) => response.status === 400 && response.errorCode === 'VALIDATION_001' && response.envelope && privateNoStore(response.cacheControl)), 'invalid query/id is VALIDATION_001 with no-store envelope');

  const keywordAtLimit = await adminList(accounts.admin.token, { keyword: 'x'.repeat(100), size: 1 });
  const keywordOverLimit = await adminList(accounts.admin.token, { keyword: 'x'.repeat(101), size: 1 });
  check('validation-admin-keyword-boundary', keywordAtLimit.status === 200 && keywordAtLimit.envelope && privateNoStore(keywordAtLimit.cacheControl)
    && keywordOverLimit.status === 400 && keywordOverLimit.errorCode === 'VALIDATION_001'
    && keywordOverLimit.envelope && privateNoStore(keywordOverLimit.cacheControl), 'keyword length 100 is accepted and 101 is VALIDATION_001/no-store');

  const targetId = endpoints.find((endpoint) => endpoint.method === 'GET' && /\/users\/\d+$/.test(endpoint.path))?.path.split('/').at(-1);
  const invalidBodies = await Promise.all([
    apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, accounts.admin.token, { role: 'ROOT' }),
    apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, accounts.admin.token, { status: 'UNKNOWN' }),
    apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, accounts.admin.token, {}),
    apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, accounts.admin.token, {}),
  ]);
  check('validation-admin-bodies', invalidBodies.every((response) => response.status === 400 && response.errorCode === 'VALIDATION_001'
    && response.envelope && privateNoStore(response.cacheControl)), 'invalid role/status bodies are VALIDATION_001 with no-store envelope');
}

async function runListMatrix(token, accounts) {
  const pageOne = await adminList(token, { page: 0, size: 1 });
  const pageHundred = await adminList(token, { page: 0, size: 100 });
  const pageOneIds = ids(pageOne);
  const pageHundredIds = ids(pageHundred);
  check('list-page-shape', pageShape(pageHundred), 'admin list uses offset PageResponse shape');
  check('list-page-size-boundary', pageOne.status === 200 && pageHundred.status === 200 && pageOneIds.size <= 1 && pageHundredIds.size >= pageOneIds.size, 'page size 1 and 100 are accepted');
  check('list-no-duplicate-ids', pageHundredIds.size === pageItems(pageHundred).length, 'list contains no duplicate users');
  check('list-stable-sort', stableCreatedSort(pageItems(pageHundred)), 'list is sorted createdAt DESC,id DESC');

  const roleFiltered = await adminList(token, { role: 'USER', size: 100 });
  const statusFiltered = await adminList(token, { status: 'ACTIVE', size: 100 });
  check('list-role-filter', pageItems(roleFiltered).every((item) => item.role === 'USER'), 'role=USER filter is exact');
  check('list-status-filter', pageItems(statusFiltered).every((item) => item.status === 'ACTIVE'), 'status=ACTIVE filter is exact');

  const special = await adminList(token, { keyword: fixture.searchLiteral, size: 100 });
  check('list-literal-wildcard-search', special.status === 200 && pageItems(special).some((item) => Number(item.id) === Number(accounts.special.me.id)), 'percent/underscore/backslash search stays literal');
  check('list-summary-global', hasSummary(special) && Number(special.data.summary.totalUsers) >= Object.keys(accounts).length - 1, 'summary is global and excludes deleted users');
  check('list-summary-no-deleted', !containsId(pageHundred, accounts.deleted.me.id), 'summary/list exclude deleted account');

  const sameTime = await adminList(token, { keyword: fixture.bulkSearchLiteral, size: 100 });
  const returnedBulkIds = ids(sameTime);
  const expectedBulkIds = new Set(fixture.bulkLabels.map((label) => Number(accounts[label].me.id)));
  check('list-same-created-at-complete', sameTime.status === 200 && returnedBulkIds.size === expectedBulkIds.size
    && [...expectedBulkIds].every((id) => returnedBulkIds.has(id)), 'same-createdAt fixture returns exactly all 21 bulk IDs');
  check('list-same-created-at-deterministic', stableCreatedSort(pageItems(sameTime)), 'same-createdAt fixture is deterministic by createdAt DESC,id DESC');
  const offset = await adminList(token, { page: 999_999, size: 100 });
  check('list-large-offset-empty', offset.status === 200 && Array.isArray(pageItems(offset)), 'large valid offset returns a bounded empty/normal page');
}

async function runMutationMatrix(accounts, posts) {
  const admin = accounts.admin;
  const keeper = accounts.adminKeeper;
  const targetId = accounts.target.me.id;

  const selfRole = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/role`, admin.token, { role: 'USER' });
  const selfStatus = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/status`, admin.token, { status: 'SUSPENDED' });
  const selfDelete = await apiCall('DELETE', `/api/v1/admin/users/${admin.me.id}`, admin.token);
  check('mutation-self-protect', [selfRole, selfStatus, selfDelete].every((response) => response.status === 400 && response.errorCode === 'ADMIN_002'), 'self role removal/status/delete are ADMIN_002');

  const selfKeepRole = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/role`, admin.token, { role: 'ADMIN' });
  const selfKeepStatus = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/status`, admin.token, { status: 'ACTIVE' });
  check('mutation-self-keep', selfKeepRole.status === 200 && selfKeepStatus.status === 200, 'self ADMIN/ACTIVE idempotent keep requests succeed');

  const promoted = await apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, admin.token, { role: 'ADMIN' });
  const targetAsAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.target.token);
  check('mutation-role-promote-current-token', promoted.status === 200 && targetAsAdmin.status === 200, 'role promotion is reflected by current DB authorization');
  const demoted = await apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, admin.token, { role: 'USER' });
  check('mutation-role-demote', demoted.status === 200 && demoted.data?.role === 'USER', 'role demotion returns canonical detail');

  const suspended = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'SUSPENDED' });
  const suspendedAccess = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.target.token);
  const suspendedPublic = await apiCall('GET', `/api/v1/posts/${posts.targetPublic.id}`);
  check('mutation-suspend-boundary', suspended.status === 200 && suspendedAccess.status === 403 && suspendedAccess.errorCode === 'USER_003'
    && suspendedPublic.status === 200, 'SUSPENDED blocks admin access but preserves existing PUBLIC read');
  const activated = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'ACTIVE' });
  const repeatedActive = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'ACTIVE' });
  check('mutation-status-idempotence', activated.status === 200 && repeatedActive.status === 200, 'status changes and repeated same value succeed');

  const beforeDeleteFingerprint = await runtimeFingerprint(accounts.target.me.id, 'before-target-delete');
  evidence.fixture.fingerprint.beforeDelete = {
    phase: beforeDeleteFingerprint.phase,
    users: beforeDeleteFingerprint.users,
    childFingerprints: beforeDeleteFingerprint.childFingerprints,
    tableCounts: beforeDeleteFingerprint.tableCounts,
  };
  const imageBefore = posts.targetImage
    ? await binaryCall(posts.targetImage.url, accounts.target.token)
    : null;
  check('http-target-image-before-delete', imageBefore?.status === 200 && imageBefore.sha256 === posts.targetImage?.sha256 && imageBefore.size === posts.targetImage?.bytes.length, 'target image is readable with target token and byte hash matches upload');

  const deleted = await apiCall('DELETE', `/api/v1/admin/users/${targetId}`, admin.token);
  const repeatedDelete = await apiCall('DELETE', `/api/v1/admin/users/${targetId}`, admin.token);
  const deletedDetail = await apiCall('GET', `/api/v1/admin/users/${targetId}`, admin.token);
  check('mutation-soft-delete', [200, 204].includes(deleted.status) && deleted.data == null, 'delete returns empty success');
  check('mutation-delete-idempotence-contract', repeatedDelete.status === 404 && repeatedDelete.errorCode === 'USER_001' && deletedDetail.status === 404, 'deleted target and repeated DELETE are USER_001/404');

  const afterDeleteFingerprint = await runtimeFingerprint(accounts.target.me.id, 'after-target-delete');
  evidence.fixture.fingerprint.afterDelete = {
    phase: afterDeleteFingerprint.phase,
    users: afterDeleteFingerprint.users,
    childFingerprints: afterDeleteFingerprint.childFingerprints,
    tableCounts: afterDeleteFingerprint.tableCounts,
  };
  check('post-mutation-subgraph-fingerprint', fingerprintPreservesChildren(beforeDeleteFingerprint, afterDeleteFingerprint, targetId), 'soft delete changes only target users.deleted_at while child rows remain byte-identical');

  const imageAfter = posts.targetImage
    ? await binaryCall(posts.targetImage.url, keeper.token)
    : null;
  check('http-target-image-after-delete', imageAfter?.status === 404 && imageAfter.errorCode === 'UPLOAD_003', 'deleted owner image content is exactly UPLOAD_003/404');

  const missing = await apiCall('PUT', '/api/v1/admin/users/999999999/role', keeper.token, { role: 'USER' });
  check('mutation-missing-target', missing.status === 404 && missing.errorCode === 'USER_001', 'missing target is USER_001/404');
}

async function runConcurrencyMatrix(accounts) {
  const raceId = accounts.race.me.id;
  const actorDemotion = await Promise.all([
    apiCall('PUT', `/api/v1/admin/users/${accounts.admin.me.id}/role`, accounts.adminPeer.token, { role: 'USER' }),
    apiCall('PUT', `/api/v1/admin/users/${accounts.user.me.id}/status`, accounts.admin.token, { status: 'SUSPENDED' }),
  ]);
  check('concurrency-actor-demotion-race', actorDemotion.every((response) => response.status !== 500 && response.status !== 409), 'actor demotion race has no 500/lock leak and stale actor is rechecked');

  const targetRace = await Promise.all([
    apiCall('PUT', `/api/v1/admin/users/${raceId}/role`, accounts.adminPeer.token, { role: 'ADMIN' }),
    apiCall('PUT', `/api/v1/admin/users/${raceId}/status`, accounts.adminKeeper.token, { status: 'SUSPENDED' }),
    apiCall('DELETE', `/api/v1/admin/users/${raceId}`, accounts.adminPeer.token),
  ]);
  check('concurrency-target-mutation-race', targetRace.every((response) => response.status !== 500 && response.status !== 409), 'role/status/delete race linearizes without 500 or deadlock response');

  const oldAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.admin.token);
  const keeperStillAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.adminKeeper.token);
  check('concurrency-stale-access', oldAdmin.status === 403 && oldAdmin.errorCode === 'ADMIN_001' && keeperStillAdmin.status === 200, 'old ADMIN token loses current DB permission while keeper remains active');
}

async function runBrowserAcceptance(accounts, posts) {
  const context = await newBrowserContext();
  const guestPage = await context.newPage();
  const guestRequests = [];
  guestPage.on('request', (request) => {
    if (request.url().includes('/api/v1/admin/')) guestRequests.push(request.url());
  });
  await guestPage.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
  await guestPage.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
  check('browser-guest-admin-guard', guestPage.url().endsWith('/signin') && guestRequests.length === 0, 'guest reaches signin without admin API call');
  await guestPage.close();

  const page = await context.newPage();
  await signInInSpa(page, accounts.adminKeeper);
  await page.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
  await waitForAdminReady(page);
  check('browser-admin-heading', await exactCount(page.getByRole('heading', { name: 'ADMIN CONSOLE', exact: true })) === 1, 'admin list renders canonical heading once');
  check('browser-admin-role-label', await exactCount(page.getByText('ROLE: ADMIN', { exact: true })) === 1, 'admin list renders canonical role label once');
  check('browser-admin-list-width', await contentWidth(page, 'list') >= 1080 && await contentWidth(page, 'list') <= 1120, 'admin list content is approximately 1100px at 1440px');

  const search = page.getByRole('textbox', { name: '사용자 검색', exact: true });
  check('browser-search-selector-unique', await exactCount(search) === 1, 'search control uses a duplicate-safe accessible selector');
  if (await exactCount(search) === 1) {
    await search.fill(fixture.searchLiteral);
    await search.press('Enter');
    await page.waitForTimeout(250);
    check('browser-search-url-state', new URL(page.url()).searchParams.get('keyword') === fixture.searchLiteral, 'search keyword is URL state');
  }

  await captureProduct(page, 'admin-list-1440');
  await page.goto(`${frontendUrl}/admin/users/${accounts.user.me.id}`, { waitUntil: 'domcontentloaded' });
  await waitForAdminDetail(page);
  check('browser-admin-detail-last-access', await page.getByText('마지막 접속 기록 없음', { exact: true }).count() === 1, 'detail renders unrecorded last access copy');
  check('browser-admin-detail-width', await contentWidth(page, 'detail') >= 820 && await contentWidth(page, 'detail') <= 900, 'admin detail content is approximately 860px at 1440px');
  await captureProduct(page, 'admin-detail-1440');

  await runBrowserTarget404Probe(page, accounts.target.me.id);
  await runBrowserStaleProbe(context, accounts);
  await verifyDesignReference(context);
  await page.close();
  await context.close();

  // posts is intentionally accepted by the signature: the browser target route uses only IDs.
  void posts;
}

async function runBrowserTarget404Probe(page, targetId) {
  const responsePromise = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}`);
  await page.goto(`${frontendUrl}/admin/users/${targetId}`, { waitUntil: 'domcontentloaded' });
  const response = await responsePromise.catch(() => null);
  const body = response ? await response.json().catch(() => null) : null;
  check('browser-target-404', response?.status() === 404 && body?.error?.code === 'USER_001', 'deleted target is shown as target-not-found, not actor session failure');
  const listResponsePromise = page.waitForResponse((candidate) => candidate.request().method() === 'GET'
    && new URL(candidate.url()).pathname === '/api/v1/admin/users');
  await page.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
  const listResponse = await listResponsePromise.catch(() => null);
  check('browser-current-actor-list-after-target-404', listResponse?.status() === 200, 'current ACTIVE ADMIN remains in session after target 404');
}

async function runBrowserStaleProbe(context, accounts) {
  const page = await context.newPage();
  await signInInSpa(page, accounts.adminKeeper);
  await page.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
  await waitForAdminReady(page);

  let heldRoute;
  let observed = false;
  await page.route('**/api/v1/admin/users*', async (route) => {
    const request = route.request();
    if (request.method() === 'GET' && !heldRoute) {
      observed = true;
      heldRoute = route;
      return;
    }
    await route.continue();
  });

  const reloadPromise = page.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(200);
  const signout = page.getByRole('button', { name: '로그아웃', exact: true });
  if (await exactCount(signout) !== 1) {
    await page.goto(`${frontendUrl}/settings`, { waitUntil: 'domcontentloaded' });
  }
  const signoutFallback = page.getByRole('button', { name: '로그아웃', exact: true });
  check('browser-stale-signout-selector', await exactCount(signoutFallback) === 1, 'SPA stale probe uses one accessible logout control');
  if (await exactCount(signoutFallback) === 1) {
    await signoutFallback.click();
    await page.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
    await signInInSpa(page, accounts.special);
  }
  if (heldRoute) await heldRoute.continue().catch(() => undefined);
  await reloadPromise.catch(() => undefined);
  await page.waitForTimeout(300);
  check('browser-account-switch-stale', observed && !page.url().includes('/admin') && await page.getByText(accounts.adminKeeper.me.nickname, { exact: true }).count() === 0, 'late ADMIN response cannot repopulate USER SPA session');
  await page.unroute('**/api/v1/admin/users*').catch(() => undefined);
  await page.close();
}

async function signInInSpa(page, account) {
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  const email = page.getByLabel('이메일', { exact: true });
  const password = page.getByLabel('비밀번호', { exact: true });
  const submit = page.getByRole('button', { name: '로그인', exact: true });
  check(`browser-signin-${account.me?.id ?? 'fixture'}`, await exactCount(email) === 1 && await exactCount(password) === 1 && await exactCount(submit) === 1, 'signin controls are unique');
  if (await exactCount(email) !== 1 || await exactCount(password) !== 1 || await exactCount(submit) !== 1) throw new Error('signin controls are not unique');
  await email.fill(account.email);
  await password.fill(account.password);
  await submit.click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 15_000 });
}

async function waitForAdminReady(page) {
  const heading = page.getByRole('heading', { name: 'ADMIN CONSOLE', exact: true });
  await heading.waitFor({ state: 'visible', timeout: 15_000 });
  const busy = page.locator('[aria-busy="true"]');
  if (await busy.count() > 0) await busy.first().waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => undefined);
}

async function waitForAdminDetail(page) {
  const heading = page.getByRole('heading', { name: '사용자 상세', exact: true });
  await heading.waitFor({ state: 'visible', timeout: 15_000 });
}

async function contentWidth(page, kind) {
  const selector = kind === 'list' ? 'main' : 'main';
  const locator = page.locator(selector);
  if (await exactCount(locator) !== 1) return 0;
  const box = await locator.boundingBox();
  return box?.width ?? 0;
}

async function verifyDesignReference(context) {
  const page = await context.newPage();
  const designPath = resolve(root, 'docs/design/ZeroVerse Pages.dc.html');
  try {
    await page.goto(`file://${designPath}`, { waitUntil: 'domcontentloaded' });
    const text = await page.locator('body').innerText();
    check('design-admin-reference', /ADMIN CONSOLE/.test(text) && /사용자/.test(text), 'design source contains M9 admin reference');
  } catch (error) {
    check('design-admin-reference', false, scrubSecrets(error instanceof Error ? error.message : String(error)));
  } finally {
    await page.close();
  }
}

function adminEndpoints(targetId) {
  return [
    { method: 'GET', path: '/api/v1/admin/users?page=0&size=20' },
    { method: 'GET', path: `/api/v1/admin/users/${targetId}` },
    { method: 'PUT', path: `/api/v1/admin/users/${targetId}/role` },
    { method: 'PUT', path: `/api/v1/admin/users/${targetId}/status` },
    { method: 'DELETE', path: `/api/v1/admin/users/${targetId}` },
  ];
}

async function adminList(token, params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return apiCall('GET', `/api/v1/admin/users${suffix}`, token);
}

async function apiCall(method, path, token, data) {
  return apiCallWithHeaders(method, path, token ? { Authorization: `Bearer ${token}` } : undefined, data);
}

async function apiCallWithHeaders(method, path, headers, data) {
  const options = { method, headers };
  if (data !== undefined) options.data = data;
  const response = await apiContext.fetch(path, options);
  const json = await safeJson(response);
  return {
    status: response.status(),
    path,
    cacheControl: response.headers()['cache-control'] ?? '',
    success: json?.success === true,
    data: json?.data ?? null,
    errorCode: json?.error?.code ?? null,
    envelope: Boolean(json && typeof json.success === 'boolean' && 'data' in json && 'error' in json && 'timestamp' in json),
  };
}

async function safeJson(response) {
  try { return await response.json(); } catch { return null; }
}

function checkPageResponse(label, response, expectSuccess) {
  check(`${label}-status`, expectSuccess ? response.status === 200 : response.status !== 200, `${label} status=${response.status}`);
  check(`${label}-shape`, !expectSuccess || pageShape(response), `${label} has nested users PageResponse shape`);
  check(`${label}-envelope`, response.envelope, `${label} uses ApiResponse envelope`);
  check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} is private/no-store`);
}

function checkCanonicalDetail(label, response, expectedId) {
  check(`${label}-status`, response.status === 200, `${label} status=${response.status}`);
  check(`${label}-id`, Number(response.data?.id) === Number(expectedId), `${label} returns requested target`);
  check(`${label}-shape`, response.data?.postStats != null && response.data?.universeStats != null, `${label} has canonical aggregate fields`);
  check(`${label}-envelope`, response.envelope, `${label} uses ApiResponse envelope`);
  check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} is private/no-store`);
}

function pageShape(response) {
  const page = response?.data?.users ?? response?.data;
  return Array.isArray(page?.items)
    && Number.isInteger(Number(page.page))
    && Number.isInteger(Number(page.size))
    && Number.isInteger(Number(page.totalElements))
    && Number.isInteger(Number(page.totalPages));
}

function hasSummary(response) {
  const summary = response?.data?.summary;
  return summary && ['totalUsers', 'joinedThisWeek', 'suspendedUsers'].every((key) => Number.isInteger(Number(summary[key])));
}

function ids(response) {
  return new Set(pageItems(response).map((item) => Number(item.id)).filter(Number.isInteger));
}

function pageItems(response) {
  return response?.data?.users?.items ?? response?.data?.items ?? [];
}

function containsId(response, id) {
  return ids(response).has(Number(id));
}

function stableCreatedSort(items) {
  for (let index = 1; index < items.length; index += 1) {
    const previous = items[index - 1];
    const current = items[index];
    const previousTime = Date.parse(previous.createdAt ?? '');
    const currentTime = Date.parse(current.createdAt ?? '');
    if (!Number.isFinite(previousTime) || !Number.isFinite(currentTime)) return false;
    if (previousTime < currentTime) return false;
    if (previousTime === currentTime && Number(previous.id) < Number(current.id)) return false;
  }
  return true;
}

function findItem(response, id) {
  return pageItems(response).find((item) => Number(item.id) === Number(id));
}

function noForbiddenFields(value) {
  const forbidden = new Set(['password', 'passwordhash', 'hash', 'token', 'accesstoken', 'refreshtoken', 'birthdate', 'content', 'contenthtml', 'contentjson']);
  let safe = true;
  const visit = (current) => {
    if (!safe || current == null) return;
    if (Array.isArray(current)) { current.forEach(visit); return; }
    if (typeof current !== 'object') return;
    for (const [key, child] of Object.entries(current)) {
      if (forbidden.has(key.replaceAll('_', '').toLowerCase())) { safe = false; return; }
      visit(child);
    }
  };
  visit(value);
  return safe;
}

function containsText(response, text) {
  return JSON.stringify(response).includes(text);
}

function privateNoStore(value) {
  return /private/i.test(value ?? '') && /no-store/i.test(value ?? '');
}

function exactCount(locator) {
  return locator.count().catch(() => 0);
}

async function runQueryBoundMatrix(token, targetId) {
  const size1 = await measureSelectDelta(() => adminList(token, { page: 0, size: 1 }));
  const size100 = await measureSelectDelta(() => adminList(token, { page: 0, size: 100 }));
  const detail = await measureSelectDelta(() => apiCall('GET', `/api/v1/admin/users/${targetId}`, token));
  evidence.fixture.queryCounts = { size1, size100, detail };
  check('admin-query-bound', [size1, size100, detail].every((value) => value?.status === 200 && Number.isInteger(value.before) && Number.isInteger(value.after) && value.delta >= 1 && value.delta <= 14)
    && size100.delta - size1.delta === 0,
  'actual API requests use 1..14 SELECT and size1→100 delta is zero');
}

async function measureSelectDelta(request) {
  const before = await readSelectCount();
  const response = await request();
  const after = await readSelectCount();
  return {
    before,
    after,
    delta: Number.isInteger(before) && Number.isInteger(after) ? after - before : null,
    status: response?.status ?? null,
  };
}

async function readSelectCount() {
  const output = await runSyntheticDbSql(
    "SELECT COALESCE(SUM(COUNT_STAR), 0) FROM performance_schema.events_statements_summary_by_account_by_event_name WHERE USER = 'zeroverse_m9' AND EVENT_NAME = 'statement/sql/select';",
  );
  const value = output.trim();
  return /^\d+$/.test(value) ? Number(value) : null;
}

async function runtimeFingerprint(targetId, phase) {
  const request = {
    schema: 'zeroverse.m9.helper-request.v1',
    milestone: 'M9',
    contract: 'A-v2',
    runId: fixture.runId,
    operation: 'fingerprint',
    mysql: { host: '127.0.0.1', port: 13319 },
    payload: { phase, accountIds: { target: Number(targetId) } },
  };
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(runtimePython, [runtimeSqlHelper, 'fingerprint'], {
      cwd: root,
      env: { ...process.env, M9_MYSQL_PORT: String(mysqlPort), M9_RUN_ID: fixture.runId },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString(); });
    child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
    child.on('error', () => rejectPromise(new Error('M9 fingerprint helper unavailable')));
    child.on('close', (code) => {
      if (code !== 0) {
        rejectPromise(new Error(`M9 fingerprint helper failed: ${scrubSecrets(stderr).slice(0, 240)}`));
        return;
      }
      try {
        const result = JSON.parse(stdout);
        if (result?.schema !== 'zeroverse.m9.fingerprint.v1'
          || !result.childFingerprints || !result.users) throw new Error('invalid fingerprint schema');
        resolvePromise(result);
      } catch {
        rejectPromise(new Error('M9 fingerprint helper returned invalid JSON'));
      }
    });
    child.stdin.end(JSON.stringify(request));
  });
}

function fingerprintPreservesChildren(before, after, targetId) {
  const targetBefore = before?.users?.[String(targetId)];
  const targetAfter = after?.users?.[String(targetId)];
  return targetBefore != null && targetAfter != null
    && targetBefore.id === targetAfter.id
    && targetBefore.preservedSha256 === targetAfter.preservedSha256
    && targetBefore.deletedAt == null
    && targetAfter.deletedAt != null
    && JSON.stringify(before.childFingerprints) === JSON.stringify(after.childFingerprints);
}

async function runSyntheticDbSql(sql) {
  if (/13307|\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE)\b/i.test(sql)) {
    throw new Error('unsafe or user-DB SQL rejected by QA harness');
  }
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(runtimePython, [runtimeSqlHelper, 'sql'], {
      cwd: root,
      env: { ...process.env, M9_MYSQL_PORT: String(mysqlPort), M9_RUN_ID: fixture.runId },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString(); });
    child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
    child.on('error', () => rejectPromise(new Error('M9 synthetic SQL helper unavailable')));
    child.on('close', (code) => code === 0
      ? resolvePromise(stdout)
      : rejectPromise(new Error(`M9 synthetic SQL helper failed: ${scrubSecrets(stderr).slice(0, 240)}`)));
    child.stdin.end(sql);
  });
}

async function binaryCall(path, token) {
  const response = await apiContext.fetch(path, { method: 'GET', headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  const bytes = await response.body();
  let errorCode = null;
  if (response.status() >= 400) {
    try { errorCode = (JSON.parse(bytes.toString('utf8')))?.error?.code ?? null; } catch { errorCode = null; }
  }
  return { status: response.status(), errorCode, contentType: response.headers()['content-type'] ?? '', sha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length };
}

async function verifyVersionManifest() {
  const empty = { manifestPath: versionManifestFile, sourceSha256: null, actualSourceSha256: null, jarSha256: null, actualJarSha256: null, filesChecked: 0, jarPath: null };
  let manifest;
  try { manifest = JSON.parse(await readFile(versionManifestFile, 'utf8')); } catch {
    return { ok: false, detail: `version manifest is not ready: ${versionManifestFile}`, evidence: empty };
  }
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  const jarPathValue = typeof manifest.jarPath === 'string' ? manifest.jarPath : '';
  const base = { ...empty, sourceSha256: manifest.sourceSha256 ?? null, jarSha256: manifest.jarSha256 ?? null, jarPath: jarPathValue || null };
  if (!/^[0-9a-f]{64}$/i.test(manifest.sourceSha256 ?? '') || !/^[0-9a-f]{64}$/i.test(manifest.jarSha256 ?? '') || !jarPathValue || files.length === 0) {
    return { ok: false, detail: 'version manifest must contain sourceSha256, jarSha256, jarPath and files', evidence: base };
  }
  const entries = files.map((entry) => ({ path: entry?.path, expectedSha: entry?.sha256 }));
  if (entries.some((entry) => typeof entry.path !== 'string' || !/^[0-9a-f]{64}$/i.test(entry.expectedSha ?? ''))) {
    return { ok: false, detail: 'version manifest has an invalid source file entry', evidence: base };
  }
  entries.sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);
  const actualHashes = [];
  for (const entry of entries) {
    const filePath = resolve(root, entry.path);
    if (!isInsideRoot(filePath)) return { ok: false, detail: `manifest path escapes repository: ${entry.path}`, evidence: base };
    try {
      const actualSha = await sha256File(filePath);
      actualHashes.push({ path: entry.path, sha256: actualSha });
      if (actualSha !== entry.expectedSha) return { ok: false, detail: `source file hash mismatch: ${entry.path}`, evidence: { ...base, filesChecked: actualHashes.length } };
    } catch {
      return { ok: false, detail: `source file unavailable: ${entry.path}`, evidence: { ...base, filesChecked: actualHashes.length } };
    }
  }
  const aggregateInput = actualHashes.map((entry) => `${entry.path}\0${entry.sha256}\n`).join('');
  const actualSourceSha = createHash('sha256').update(aggregateInput, 'utf8').digest('hex');
  const jarPath = resolve(root, jarPathValue);
  if (!isInsideRoot(jarPath)) return { ok: false, detail: `manifest JAR path escapes repository: ${jarPathValue}`, evidence: { ...base, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  let actualJarSha;
  try { actualJarSha = await sha256File(jarPath); } catch {
    return { ok: false, detail: `immutable runtime JAR unavailable: ${jarPathValue}`, evidence: { ...base, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  }
  const finalEvidence = { ...base, actualSourceSha256: actualSourceSha, actualJarSha256: actualJarSha, filesChecked: actualHashes.length };
  if (actualSourceSha !== manifest.sourceSha256) return { ok: false, detail: 'source aggregate SHA-256 mismatch', evidence: finalEvidence };
  if (actualJarSha !== manifest.jarSha256) return { ok: false, detail: 'immutable runtime JAR SHA-256 mismatch', evidence: finalEvidence };
  return { ok: true, evidence: finalEvidence };
}

async function sha256File(filePath) {
  return createHash('sha256').update(await readFile(filePath)).digest('hex');
}

function isInsideRoot(filePath) {
  return filePath === root || filePath.startsWith(`${root}/`);
}

function check(name, pass, detail) {
  const status = pass ? 'PASS' : 'FAIL';
  evidence.checks.push({ name, status, detail: scrubSecrets(detail ?? '') });
  if (!pass) evidence.failures.push(`${name}: ${scrubSecrets(detail ?? 'failed')}`);
  console.log(`[${status}] ${name}`);
}

function notRun(name, detail) {
  evidence.checks.push({ name, status: 'NOT RUN', detail: scrubSecrets(detail ?? 'not run') });
  if (requireFull) evidence.failures.push(`${name}: ${scrubSecrets(detail ?? 'not run')}`);
  console.log(`[NOT RUN] ${name}`);
}

async function captureProduct(page, label) {
  if (!screenshotsEnabled) return;
  const screenshot = `${label}.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);
}

async function newBrowserContext() {
  return browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    locale: 'ko-KR',
    reducedMotion: 'reduce',
  });
}

async function finishEvidence() {
  evidence.status = evidence.failures.length === 0 ? 'PASS' : 'NOT RUN';
  await writeEvidence();
  const pass = evidence.checks.filter((item) => item.status === 'PASS').length;
  const notRunCount = evidence.checks.filter((item) => item.status === 'NOT RUN').length;
  console.log(JSON.stringify({ outputDir, screenshots: evidence.screenshots.length, pass, notRun: notRunCount, failures: evidence.failures.length, fixtureIds: evidence.fixture }));
  if (evidence.failures.length > 0) throw new Error(`M9 acceptance has ${evidence.failures.length} findings`);
}

async function writeEvidence() {
  await writeFile(resolve(outputDir, 'evidence.json'), scrubSecrets(JSON.stringify(evidence, null, 2)), 'utf8');
}

function scrubSecrets(value) {
  let result = String(value);
  for (const account of Object.values(fixture.accounts ?? {})) {
    result = result.replaceAll(account.email, '[redacted-email]');
    result = result.replaceAll(account.password, '[redacted-password]');
  }
  result = result.replaceAll(fixture.runId, '[redacted-run-id]');
  result = result.replaceAll(fixture.searchLiteral, '[redacted-search-marker]');
  result = result.replaceAll(fixture.bulkSearchLiteral, '[redacted-bulk-marker]');
  result = result.replace(/Bearer\s+\S+/gi, 'Bearer [redacted-token]');
  result = result.replace(/[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '[redacted-token]');
  return result;
}
