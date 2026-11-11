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
 * 최종 BE XML/summary와 FE JSON은 qa/m9-test-evidence.mjs의 구조화 parser로만
 * 연결한다. 문자열 marker나 전체-suite 수만으로 PASS를 만들지 않는다.
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
import { collectTestEvidence } from './m9-test-evidence.mjs';

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
const ADMIN_USERS_ROUTE_PATTERN = /\/api\/v1\/admin\/users(?:\/|\?|$)/;
const STALE_PROBE_TIMEOUT_MS = Number(process.env.M9_STALE_PROBE_TIMEOUT_MS ?? 15_000);

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

// I9 fail-closed gate: a required row that is never reached is NOT RUN,
// rather than an accidental PASS caused by an early return or empty fixture.
const REQUIRED_EVIDENCE_IDS = [
  'source-runtime-identity',
  'runtime-api-openapi',
  'openapi-m9-paths',
  'fixture-credentials-unique',
  'fixture-admin-claim-tokens',
  'fixture-native-subgraph',
  'fixture-native-api-subgraph',
  'fixture-target-all-visibility',
  'fixture-target-deleted-opponent-relation',
  'http-admin-list-status',
  'http-admin-detail-status',
  'http-admin-detail-exact-aggregates',
  'fixture-target-comments-replies',
  'fixture-target-actor-comment',
  'fixture-target-actor-notification',
  'fixture-target-soft-deleted-own-comment',
  'auth-missing-admin-endpoints',
  'auth-malformed-admin-endpoints',
  'auth-bad-signature-admin-endpoints',
  'auth-user-admin-endpoints',
  'auth-suspended-admin-endpoints',
  'auth-deleted-admin-endpoints',
  'auth-expired-admin-endpoints',
  'auth-wrong-type-admin-endpoints',
  'auth-missing-role-admin-endpoints',
  'auth-missing-subject-admin-endpoints',
  'auth-invalid-role-admin-endpoints',
  'auth-invalid-subject-admin-endpoints',
  'validation-admin-queries',
  'validation-admin-keyword-boundary',
  'validation-admin-bodies',
  'admin-query-bound',
  'list-role-filter-exact',
  'list-status-filter-exact',
  'list-literal-wildcard-search-exact',
  'list-email-search-exact',
  'list-nickname-search-exact',
  'list-name-search-exact',
  'list-summary-exact',
  'list-same-created-at-pages',
  'list-same-created-at-global-order',
  'list-large-offset-empty-exact',
  'mutation-self-protect',
  'mutation-role-canonical',
  'mutation-role-promote-current-token',
  'mutation-role-demote-canonical',
  'mutation-status-canonical',
  'mutation-same-value-audit-immutable',
  'mutation-suspend-boundary',
  'mutation-soft-delete',
  'mutation-suspended-signin-and-writes',
  'mutation-deleted-signin-and-writes',
  'fixture-native-child-counts',
  'http-target-actor-notification-mask',
  'http-target-comment-placeholder',
  'http-target-reply-placeholder',
  'http-target-own-comment-preservation',
  'refresh-suspended-rejected',
  'refresh-suspended-reuse-rejected',
  'refresh-reactivated-existing-accepted',
  'refresh-active-accepted',
  'refresh-deleted-rejected',
  'post-mutation-subgraph-fingerprint',
  'http-target-image-before-delete',
  'http-target-image-after-delete',
  'http-deleted-image-preservation',
  'concurrency-actor-demotion-exact',
  'concurrency-target-mutation-exact',
  'concurrency-physical-barrier-evidence',
  'browser-guest-admin-guard',
  'browser-user-admin-guard',
  'browser-admin-list-http-ready',
  'browser-admin-list-pending-sensitive',
  'browser-list-empty-state',
  'browser-list-transient-retry',
  'browser-list-transient-retry-result',
  'browser-search-paging-history',
  'browser-admin-focus-suspended',
  'browser-admin-visible-deleted',
  'browser-admin-denied-reentry',
  'browser-admin-detail-http-ready',
  'browser-detail-transient-retry',
  'browser-detail-transient-retry-result',
  'browser-screenshot-evidence',
  'browser-role-confirm-canonical',
  'browser-mutation-duplicate-submit',
  'browser-mutation-error-retry',
  'browser-status-confirm-canonical',
  'browser-delete-copy',
  'browser-delete-confirm-canonical',
  'browser-stale-list',
  'browser-stale-list-pending-sensitive',
  'browser-stale-detail',
  'browser-stale-detail-pending-sensitive',
  'browser-stale-mutation',
  'browser-auth-final-failure-boundary',
  'browser-account-switch-stale',
  'backend-rr-writer-evidence',
  'backend-noop-audit-evidence',
  'backend-state-policy-evidence',
  'frontend-matrix-evidence',
];
evidence.requiredChecks = REQUIRED_EVIDENCE_IDS;

check('fixture-credentials-unique', uniqueFixtureValues('email') && uniqueFixtureValues('nickname') && uniqueFixtureValues('blogSlug'), 'synthetic email/nickname/blog slug values are unique before any HTTP registration');

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
  await createNativeApiSubgraph(accounts, posts);
  await writeFixtureSeed(accounts, posts);

  const statusReady = await waitForStatusFixtures();
  evidence.fixture.statusReady = statusReady;
  if (!statusReady && requireStatusFixtures) throw new Error('synthetic M9 status fixtures are not ready');
  await refreshAdminClaims(accounts);

  await runHttpAcceptance(accounts, posts);
  await runBrowserAcceptance(accounts, posts);
  await verifyPhysicalConcurrencyEvidence();

  await finishEvidence();
} catch (error) {
  const safeMessage = scrubSecrets(error instanceof Error ? error.message : String(error));
  evidence.failures.push(`runner: ${safeMessage}`);
  evidence.status = 'NOT RUN';
  ensureRequiredEvidenceChecks();
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
  const searchEmailLiteral = `m9email${key}`;
  const searchNicknameLiteral = `m9nick${key}`;
  const bulkSearchLiteral = `bulk${key}`;
  const labels = ['admin', 'adminPeer', 'adminKeeper', 'user', 'target', 'race', 'suspended', 'deleted', 'special', 'weekBefore', 'weekStart', 'weekAfter'];
  const bulkLabels = Array.from({ length: 21 }, (_, index) => `bulk${String(index + 1).padStart(2, '0')}`);
  const allLabels = [...labels, ...bulkLabels];
  const accounts = Object.fromEntries(allLabels.map((label, index) => {
    const labelKey = label.replace(/[^a-z0-9]/gi, '').toLowerCase();
    const nickname = label === 'special'
      ? `${searchNicknameLiteral}00`.slice(0, 20)
      : `m9${labelKey.slice(0, 8)}${key}${String(index).padStart(2, '0')}`.slice(0, 20);
    const special = label === 'special';
    return [label, {
      email: special ? `${searchEmailLiteral}@example.test` : `m9-${label}-${suffix}@example.test`,
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
    searchEmailLiteral,
    searchNicknameLiteral,
    bulkSearchLiteral,
    labels: allLabels,
    bulkLabels,
    weekBoundaryLabels: ['weekBefore', 'weekStart', 'weekAfter'],
    accounts,
  };
}

function uniqueFixtureValues(field) {
  const values = Object.values(fixture.accounts).map((account) => account[field]);
  return values.length > 0 && new Set(values).size === values.length;
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
  return {
    token,
    userToken: token,
    email: account.email,
    password: account.password,
    me: me.data,
    blog: blog.data,
  };
}

async function signinAccount(account, label) {
  const signin = await apiCall('POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  const token = signin.data?.accessToken;
  check(`${label}-re-signin`, signin.status === 200 && typeof token === 'string' && token.length > 20, 'post-seed access token held in memory');
  if (typeof token !== 'string') throw new Error(`${label} post-seed signin failed`);
  return token;
}

async function signinAttempt(account, label) {
  const response = await apiCall('POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  check(`fixture-${label}-signin-attempt`, [200, 401, 403, 404].includes(response.status),
    'signin boundary response is captured without retaining a token');
  return response;
}

function jwtClaimsInMemory(token) {
  try {
    const segment = String(token).split('.')[1];
    if (!segment) return null;
    return JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

function tamperJwt(token) {
  const parts = String(token).split('.');
  if (parts.length !== 3) return 'malformed.synthetic.signature';
  const signature = parts[2];
  const replacement = signature.endsWith('A') ? `${signature.slice(0, -1)}B` : `${signature}A`;
  return `${parts[0]}.${parts[1]}.${replacement}`;
}

async function refreshAdminClaims(accounts) {
  const adminLabels = ['admin', 'adminPeer', 'adminKeeper'];
  const values = [];
  for (const label of adminLabels) {
    const account = accounts[label];
    const token = await signinAccount(account, label);
    account.adminClaimToken = token;
    account.token = token;
    values.push({ label, userClaim: jwtClaimsInMemory(account.userToken), adminClaim: jwtClaimsInMemory(token) });
  }
  const valid = values.every(({ label, userClaim, adminClaim }) => userClaim?.role === 'USER'
    && userClaim?.type === 'ACCESS'
    && adminClaim?.role === 'ADMIN'
    && adminClaim?.type === 'ACCESS'
    && String(adminClaim.sub) === String(accounts[label].me.id));
  check('fixture-admin-claim-tokens', valid, 'post-seed ADMIN sign-in has ADMIN ACCESS claim while pre-seed token remains USER claim');
  if (!valid) throw new Error('post-seed ADMIN claim fixture is invalid');
}

async function createFixturePosts(accounts) {
  const posts = {
    adminPublic: await createPost(accounts.admin, 'admin public', 'PUBLIC', true),
    userPublic: await createPost(accounts.user, 'user public', 'PUBLIC', true),
    targetPublic: await createPost(accounts.target, 'target public', 'PUBLIC', true, [
      `m9-${fixture.key}-tag-a`, `m9-${fixture.key}-tag-b`,
    ]),
    targetPrivate: await createPost(accounts.target, 'target universe', 'UNIVERSE', true),
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
  check('fixture-target-all-visibility', posts.targetPublic.visibility === 'PUBLIC'
    && posts.targetPrivate.visibility === 'UNIVERSE'
    && posts.targetDraft.visibility === 'PRIVATE'
    && posts.targetDraft.publishedAt == null,
  'target fixture has non-empty PUBLIC, UNIVERSE, and PRIVATE/draft visibility rows for aggregate coverage');
  return posts;
}

async function createPost(account, label, visibility, publish, tagNames = []) {
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
    tagNames,
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

async function createNativeApiSubgraph(accounts, posts) {
  const root = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.token, {
    content: 'M9 target root comment', parentId: null,
  });
  const reply = root.data?.id
    ? await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.token, {
      content: 'M9 target reply comment', parentId: root.data.id,
    }) : null;
  const actor = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.token, {
    content: 'M9 target actor comment', parentId: null,
  });
  const deletedOwn = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.token, {
    content: 'M9 target soft-deleted own comment', parentId: null,
  });
  const deletedOwnId = deletedOwn.data?.id;
  const deletedOwnDelete = Number.isInteger(deletedOwnId)
    ? await apiCall('DELETE', `/api/v1/comments/${deletedOwnId}`, accounts.target.token)
    : null;
  const relations = await Promise.all([
    createUniverseRelation(accounts.target, accounts.user),
    createUniverseRelation(accounts.user, accounts.target),
    createUniverseRelation(accounts.target, accounts.special),
    createUniverseRelation(accounts.adminKeeper, accounts.target),
    createUniverseRelation(accounts.target, accounts.deleted),
  ]);
  const like = await apiCall('POST', `/api/v1/posts/${posts.targetPublic.id}/likes`, accounts.user.token);
  const valid = root.status === 201 && Number.isInteger(root.data?.id)
    && reply?.status === 201 && Number.isInteger(reply.data?.id)
    && actor.status === 201 && Number.isInteger(actor.data?.id)
    && deletedOwn.status === 201 && Number.isInteger(deletedOwnId)
    && deletedOwnDelete?.status === 200
    && relations.every((result) => result.request.status === 201 && result.accept.status === 200)
    && like.status === 200;
  check('fixture-native-api-subgraph', valid, 'target comments/reply/actor notification, accepted universe directions, like, and post tags are created through real APIs');
  check('fixture-target-deleted-opponent-relation', relations[4]?.request?.status === 201 && relations[4]?.accept?.status === 200,
    'target has an accepted relation to a still-live opponent before the root soft-deletes that opponent');
  if (!valid) throw new Error('native API subgraph fixture failed');
  evidence.fixture.commentIds.nativeRoot = root.data.id;
  evidence.fixture.commentIds.nativeReply = reply.data.id;
  evidence.fixture.commentIds.nativeActor = actor.data.id;
  evidence.fixture.commentIds.nativeDeletedOwn = deletedOwnId;
  evidence.fixture.nativeApi = {
    rootCommentId: root.data.id,
    replyCommentId: reply.data.id,
    actorCommentId: actor.data.id,
    deletedOwnCommentId: deletedOwnId,
    universeIds: relations.map((result) => result.request.data?.requestId).filter(Number.isInteger),
    likeCreated: true,
    tagNames: [`m9-${fixture.key}-tag-a`, `m9-${fixture.key}-tag-b`],
  };
}

async function createUniverseRelation(from, to) {
  const request = await apiCall('POST', `/api/v1/universe/requests/${to.me.id}`, from.token);
  const requestId = request.data?.requestId;
  const accept = Number.isInteger(requestId)
    ? await apiCall('POST', `/api/v1/universe/requests/${requestId}/accept`, to.token)
    : { status: null, data: null, errorCode: null, envelope: false, cacheControl: '' };
  return { request, accept };
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
    nativeFixtureRequest: { extraBlogs: true, softDeletedPost: true },
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
    || !Number.isInteger(Number(ready.summaryExpectations?.suspendedUsers))
    || !validNativeFixtureReady({
      schema: 'zeroverse.m9.native-fixture.v1',
      ids: ready.nativeFixtures,
      expected: ready.targetDetailExpectations,
    })) return false;
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

function validNativeFixtureReady(nativeFixture) {
  const ids = nativeFixture?.ids;
  const expected = nativeFixture?.expected;
  const requiredIds = ['activeBlogId', 'deletedBlogId', 'deletedBlogPostId', 'deletedPostId'];
  return nativeFixture?.schema === 'zeroverse.m9.native-fixture.v1'
    && requiredIds.every((key) => Number.isInteger(Number(ids?.[key])) && Number(ids[key]) > 0)
    && new Set(['activeBlogId', 'deletedBlogId'].map((key) => Number(ids[key]))).size === 2
    && new Set(['deletedBlogPostId', 'deletedPostId'].map((key) => Number(ids[key]))).size === 2
    && Number(expected?.defaultBlogId) === Number(evidence.fixture.blogIds.target)
    && Number(expected?.blogCount) === 2
    && Number(expected?.postCount) === 4
    && Number(expected?.postStats?.total) === 4
    && Number(expected?.postStats?.published) === 3
    && Number(expected?.postStats?.draft) === 1
    && Number(expected?.commentCount) === 3
    && Number(expected?.universeStats?.outgoingCount) === 2
    && Number(expected?.universeStats?.incomingCount) === 2
    && Number(expected?.universeStats?.connectionCount) === 1;
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
    nativeFixture: ready.nativeFixtures ? {
      schema: 'zeroverse.m9.native-fixture.v1',
      ids: Object.fromEntries(Object.entries(ready.nativeFixtures).map(([key, id]) => [key, Number(id)])),
      expected: {
        defaultBlogId: Number(ready.targetDetailExpectations?.defaultBlogId),
        blogCount: Number(ready.targetDetailExpectations?.blogCount),
        postCount: Number(ready.targetDetailExpectations?.postCount),
        postStats: {
          total: Number(ready.targetDetailExpectations?.postStats?.total),
          published: Number(ready.targetDetailExpectations?.postStats?.published),
          draft: Number(ready.targetDetailExpectations?.postStats?.draft),
        },
        commentCount: Number(ready.targetDetailExpectations?.commentCount),
        universeStats: {
          outgoingCount: Number(ready.targetDetailExpectations?.universeStats?.outgoingCount),
          incomingCount: Number(ready.targetDetailExpectations?.universeStats?.incomingCount),
          connectionCount: Number(ready.targetDetailExpectations?.universeStats?.connectionCount),
        },
      },
    } : null,
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
  const native = evidence.fixture.statusExpectations?.nativeFixture;
  const expectedNative = native?.expected;
  check('fixture-native-subgraph', native != null && validNativeFixtureReady(native)
    && evidence.fixture.nativeApi?.likeCreated === true
    && evidence.fixture.nativeApi?.tagNames?.length === 2,
  'status-ready declares root native blog/post IDs and exact ground-truth detail while API fixture owns comments/relations/like/tags');
  check('http-admin-detail-exact-aggregates', detail.status === 200 && expectedNative != null
    && Number(detail.data?.defaultBlog?.id) === Number(expectedNative.defaultBlogId)
    && Number(detail.data?.blogCount) === Number(expectedNative.blogCount)
    && Number(detail.data?.postCount) === Number(expectedNative.postCount)
    && Number(detail.data?.postStats?.total) === Number(expectedNative.postStats?.total)
    && Number(detail.data?.postStats?.published) === Number(expectedNative.postStats?.published)
    && Number(detail.data?.postStats?.draft) === Number(expectedNative.postStats?.draft)
    && Number(detail.data?.commentCount) === Number(expectedNative.commentCount)
    && Number(detail.data?.universeStats?.outgoingCount) === Number(expectedNative.universeStats?.outgoingCount)
    && Number(detail.data?.universeStats?.incomingCount) === Number(expectedNative.universeStats?.incomingCount)
    && Number(detail.data?.universeStats?.connectionCount) === Number(expectedNative.universeStats?.connectionCount),
  'detail aggregate fields exactly match non-empty native fixture and active default blog');
  check('http-admin-detail-privacy', noForbiddenFields(detail.data), 'detail DTO does not expose secret/body fields');
  const targetComments = await apiCall('GET', `/api/v1/posts/${posts.adminPublic.id}/comments?page=0&size=20`, accounts.adminKeeper.token);
  const nativeIds = evidence.fixture.nativeApi ?? {};
  const targetRoot = findItem(targetComments, nativeIds.rootCommentId);
  const targetReply = pageItems({ data: targetRoot?.replies }).find((item) => Number(item.id) === Number(nativeIds.replyCommentId));
  check('fixture-target-comments-replies', targetComments.status === 200
    && targetRoot?.author?.id === accounts.target.me.id
    && targetRoot?.parentId == null
    && targetRoot?.content === 'M9 target root comment'
    && targetReply?.author?.id === accounts.target.me.id
    && Number(targetReply?.parentId) === Number(nativeIds.rootCommentId)
    && targetReply?.content === 'M9 target reply comment',
  'target owns a surviving root comment and nested reply on a surviving post');
  const targetActorComments = await apiCall('GET', `/api/v1/posts/${posts.adminPublic.id}/comments?page=0&size=20`, accounts.admin.token);
  const targetActorComment = findItem(targetActorComments, nativeIds.actorCommentId);
  check('fixture-target-actor-comment', targetActorComments.status === 200
    && targetActorComment?.author?.id === accounts.target.me.id
    && targetActorComment?.content === 'M9 target actor comment',
  'target owns a comment on a surviving other-user post for actor-notification masking');
  const targetDeletedOwnComment = findItem(targetComments, nativeIds.deletedOwnCommentId);
  check('fixture-target-soft-deleted-own-comment', targetComments.status === 200
    && targetDeletedOwnComment?.content == null
    && targetDeletedOwnComment?.deleted === true
    && targetDeletedOwnComment?.author?.id === accounts.target.me.id,
  'target has a non-empty soft-deleted own comment counterexample that must be excluded from active aggregates but retained as history');
  const targetActorNotificationPage = await apiCall('GET', '/api/v1/notifications?read=all&page=0&size=100', accounts.admin.token);
  const targetActorNotification = (targetActorNotificationPage.data?.items ?? [])
    .find((item) => Number(item.targetId) === Number(nativeIds.actorCommentId));
  if (targetActorNotification?.id != null) {
    evidence.fixture.notificationIds = {
      ...(evidence.fixture.notificationIds ?? {}),
      targetActorComment: targetActorNotification.id,
    };
  }
  check('fixture-target-actor-notification', targetActorNotificationPage.status === 200 && targetActorNotification?.id != null,
    'target actor notification ID is captured before deleting target, without exposing tokens');

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
  const targetDeletedComment = findItem(survivingComments, evidence.fixture.nativeApi?.actorCommentId);
  check('http-target-comment-placeholder', survivingComments.status === 200 && targetDeletedComment != null
    && targetDeletedComment.content == null && (targetDeletedComment.author == null || targetDeletedComment.deleted === true),
  'target-owned comment history is preserved as a placeholder after target soft delete');
  const targetRootPlaceholder = findItem(survivingComments, evidence.fixture.nativeApi?.rootCommentId);
  const targetReplyPlaceholder = pageItems({ data: targetRootPlaceholder?.replies })
    .find((item) => Number(item.id) === Number(evidence.fixture.nativeApi?.replyCommentId));
  check('http-target-reply-placeholder', survivingComments.status === 200 && targetRootPlaceholder != null
    && targetRootPlaceholder.content == null && (targetRootPlaceholder.author == null || targetRootPlaceholder.deleted === true)
    && targetReplyPlaceholder != null && targetReplyPlaceholder.content == null
    && (targetReplyPlaceholder.author == null || targetReplyPlaceholder.deleted === true),
  'target root comment and nested reply remain as masked history after target soft delete');
  const targetOwnPlaceholder = findItem(survivingComments, evidence.fixture.nativeApi?.deletedOwnCommentId);
  check('http-target-own-comment-preservation', survivingComments.status === 200 && targetOwnPlaceholder != null
    && targetOwnPlaceholder.content == null && targetOwnPlaceholder.deleted === true,
  'target-owned comment deleted before account soft delete remains a deleted placeholder after account soft delete');

  const notifications = await apiCall('GET', '/api/v1/notifications?read=all&page=0&size=100', admin.token);
  const maskedNotification = (notifications.data?.items ?? []).find((item) => Number(item.id) === Number(evidence.fixture.notificationIds?.deletedActorComment));
  check('http-deleted-actor-notification-mask', notifications.status === 200 && maskedNotification != null
    && maskedNotification.actor == null && maskedNotification.targetId == null && maskedNotification.href == null,
  'notification actor and target are masked after the actor soft delete');
  const targetMaskedNotification = (notifications.data?.items ?? [])
    .find((item) => Number(item.id) === Number(evidence.fixture.notificationIds?.targetActorComment));
  check('http-target-actor-notification-mask', notifications.status === 200 && targetMaskedNotification != null
    && targetMaskedNotification.actor == null && targetMaskedNotification.targetId == null && targetMaskedNotification.href == null,
  'target actor notification remains as a masked history row after target soft delete');

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

  const badSignature = tamperJwt(accounts.admin.token);
  const badSignatureResponses = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, badSignature)));
  check('auth-bad-signature-admin-endpoints', badSignatureResponses.every((response) => response.status === 401
    && response.errorCode === 'AUTH_004' && privateNoStore(response.cacheControl) && response.envelope), 'signed token with invalid signature is AUTH_004/no-store on all five admin methods');

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
      check(label, responses.every((response) => response.status === 401 && response.errorCode === 'AUTH_004'
        && privateNoStore(response.cacheControl) && response.envelope), 'signed incomplete claim is AUTH_004/no-store envelope on all five methods');
    }
  }

  for (const [label, variable] of [['auth-invalid-role-admin-endpoints', 'M9_INVALID_ROLE_TOKEN'], ['auth-invalid-subject-admin-endpoints', 'M9_INVALID_SUBJECT_TOKEN']]) {
    const token = process.env[variable];
    if (!token) notRun(label, `${variable} is not supplied by isolated runtime`);
    else {
      const responses = await Promise.all(endpoints.map((endpoint) => apiCall(endpoint.method, endpoint.path, token)));
      check(label, responses.every((response) => response.status === 401 && response.errorCode === 'AUTH_004'
        && privateNoStore(response.cacheControl) && response.envelope), 'signed invalid role/subject claim is AUTH_004/no-store envelope on all five methods');
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
  const liveLabels = Object.keys(accounts).filter((label) => label !== 'deleted');
  const expectedLiveIds = new Set(liveLabels.map((label) => Number(accounts[label].me.id)));
  const expectedAdminIds = new Set(['admin', 'adminPeer', 'adminKeeper'].map((label) => Number(accounts[label].me.id)));
  const expectedUserIds = new Set(liveLabels.filter((label) => !expectedAdminIds.has(Number(accounts[label].me.id)))
    .map((label) => Number(accounts[label].me.id)));
  const expectedActiveIds = new Set(liveLabels.filter((label) => accounts[label].me.id !== accounts.suspended.me.id)
    .map((label) => Number(accounts[label].me.id)));
  const summaryExpected = evidence.fixture.statusExpectations?.summaryExpectations;
  check('list-page-shape', pageShape(pageHundred), 'admin list uses offset PageResponse shape');
  check('list-page-size-boundary', pageOne.status === 200 && pageHundred.status === 200 && pageOneIds.size <= 1 && pageHundredIds.size >= pageOneIds.size, 'page size 1 and 100 are accepted');
  check('list-no-duplicate-ids', pageHundredIds.size === pageItems(pageHundred).length, 'list contains no duplicate users');
  check('list-stable-sort', stableCreatedSort(pageItems(pageHundred)), 'list is sorted createdAt DESC,id DESC');
  check('list-live-id-set-exact', pageHundred.status === 200 && setsEqual(pageHundredIds, expectedLiveIds)
    && Number(pageHundred.data?.users?.totalElements) === expectedLiveIds.size,
  'unfiltered list returns exactly every non-deleted fixture user and no deleted ID');

  const roleFiltered = await adminList(token, { role: 'USER', size: 100 });
  const statusFiltered = await adminList(token, { status: 'ACTIVE', size: 100 });
  checkPageResponse('list-role-filter', roleFiltered, true);
  checkPageResponse('list-status-filter', statusFiltered, true);
  check('list-role-filter-exact', roleFiltered.status === 200 && setsEqual(ids(roleFiltered), expectedUserIds)
    && pageItems(roleFiltered).every((item) => item.role === 'USER')
    && Number(roleFiltered.data?.users?.totalElements) === expectedUserIds.size,
  'role=USER returns the exact non-deleted USER ID set');
  check('list-status-filter-exact', statusFiltered.status === 200 && setsEqual(ids(statusFiltered), expectedActiveIds)
    && pageItems(statusFiltered).every((item) => item.status === 'ACTIVE')
    && Number(statusFiltered.data?.users?.totalElements) === expectedActiveIds.size,
  'status=ACTIVE returns the exact non-deleted ACTIVE ID set');

  const special = await adminList(token, { keyword: fixture.searchLiteral, size: 100 });
  checkPageResponse('list-literal-wildcard-search', special, true);
  check('list-literal-wildcard-search-exact', special.status === 200
    && setsEqual(ids(special), new Set([Number(accounts.special.me.id)])), 'percent/underscore/backslash search stays literal and returns the exact fixture match');
  const emailSearch = await adminList(token, { keyword: fixture.searchEmailLiteral, size: 100 });
  const nicknameSearch = await adminList(token, { keyword: fixture.searchNicknameLiteral, size: 100 });
  const nameSearch = await adminList(token, { keyword: `name ${fixture.searchLiteral}`, size: 100 });
  checkPageResponse('list-email-search', emailSearch, true);
  checkPageResponse('list-nickname-search', nicknameSearch, true);
  checkPageResponse('list-name-search', nameSearch, true);
  check('list-email-search-exact', emailSearch.status === 200
    && setsEqual(ids(emailSearch), new Set([Number(accounts.special.me.id)])), 'email-only keyword search returns the exact special fixture ID');
  check('list-nickname-search-exact', nicknameSearch.status === 200
    && setsEqual(ids(nicknameSearch), new Set([Number(accounts.special.me.id)])), 'nickname-only keyword search returns the exact special fixture ID');
  check('list-name-search-exact', nameSearch.status === 200
    && setsEqual(ids(nameSearch), new Set([Number(accounts.special.me.id)])), 'name-only keyword search returns the exact special fixture ID');
  check('list-summary-exact', hasSummary(special) && summaryExpected != null
    && Number(special.data.summary.totalUsers) === Number(summaryExpected.totalUsers)
    && Number(special.data.summary.joinedThisWeek) === Number(summaryExpected.joinedThisWeek)
    && Number(special.data.summary.suspendedUsers) === Number(summaryExpected.suspendedUsers), 'filtered list summary is global and exactly matches KST fixture expectations');
  check('list-summary-no-deleted', !containsId(pageHundred, accounts.deleted.me.id), 'summary/list exclude deleted account');

  const expectedBulkIds = new Set(fixture.bulkLabels.map((label) => Number(accounts[label].me.id)));
  const sameTime = await adminList(token, { keyword: fixture.bulkSearchLiteral, page: 0, size: 100 });
  const returnedBulkIds = ids(sameTime);
  checkPageResponse('list-same-created-at-complete', sameTime, true);
  check('list-same-created-at-complete', sameTime.status === 200 && returnedBulkIds.size === expectedBulkIds.size
    && setsEqual(returnedBulkIds, expectedBulkIds)
    && Number(sameTime.data?.users?.totalElements) === expectedBulkIds.size, 'same-createdAt fixture returns exactly all 21 bulk IDs');
  check('list-same-created-at-deterministic', stableCreatedSort(pageItems(sameTime)), 'same-createdAt fixture is deterministic by createdAt DESC,id DESC');
  const samePage0 = await adminList(token, { keyword: fixture.bulkSearchLiteral, page: 0, size: 20 });
  const samePage1 = await adminList(token, { keyword: fixture.bulkSearchLiteral, page: 1, size: 20 });
  const sameIds0 = ids(samePage0);
  const sameIds1 = ids(samePage1);
  const expectedBulkOrder = [...expectedBulkIds].sort((left, right) => right - left);
  const actualBulkOrder0 = pageItems(samePage0).map((item) => Number(item.id));
  const actualBulkOrder1 = pageItems(samePage1).map((item) => Number(item.id));
  const sameUnion = new Set([...sameIds0, ...sameIds1]);
  const sameIntersection = [...sameIds0].filter((id) => sameIds1.has(id));
  checkPageResponse('list-same-created-at-page0', samePage0, true);
  checkPageResponse('list-same-created-at-page1', samePage1, true);
  check('list-same-created-at-pages', samePage0.status === 200 && samePage1.status === 200
    && sameIds0.size === 20 && sameIds1.size === 1 && sameIntersection.length === 0
    && setsEqual(sameUnion, expectedBulkIds)
    && Number(samePage0.data?.users?.totalElements) === expectedBulkIds.size
    && Number(samePage0.data?.users?.totalPages) === 2
    && samePage0.data?.users?.hasNext === true && samePage0.data?.users?.hasPrevious === false
    && samePage1.data?.users?.hasNext === false && samePage1.data?.users?.hasPrevious === true
    && stableCreatedSort(pageItems(samePage0)) && stableCreatedSort(pageItems(samePage1)),
  'same-createdAt 21 rows split across pages have exact union, no intersection, metadata, and deterministic order');
  check('list-same-created-at-global-order', samePage0.status === 200 && samePage1.status === 200
    && arrayEqual(actualBulkOrder0, expectedBulkOrder.slice(0, 20))
    && arrayEqual(actualBulkOrder1, expectedBulkOrder.slice(20)),
  'same-createdAt page0/page1 match the exact globally sorted ID slices, not merely per-page order');
  const offset = await adminList(token, { page: 999_999, size: 100 });
  checkPageResponse('list-large-offset', offset, true);
  check('list-large-offset-empty-exact', offset.status === 200 && pageItems(offset).length === 0
    && Number(offset.data?.users?.totalElements) === expectedLiveIds.size
    && Number(offset.data?.users?.totalPages) === Math.ceil(expectedLiveIds.size / 100)
    && offset.data?.users?.hasNext === false && offset.data?.users?.hasPrevious === true,
  'large valid offset returns an exact empty page with unchanged metadata');
}

async function runMutationMatrix(accounts, posts) {
  const admin = accounts.admin;
  const keeper = accounts.adminKeeper;
  const targetId = accounts.target.me.id;

  const selfRole = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/role`, admin.token, { role: 'USER' });
  const selfStatus = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/status`, admin.token, { status: 'SUSPENDED' });
  const selfDelete = await apiCall('DELETE', `/api/v1/admin/users/${admin.me.id}`, admin.token);
  const selfErrorMessage = '자기 자신의 관리자 권한 회수, 계정 정지 또는 삭제는 할 수 없습니다.';
  check('mutation-self-protect', [selfRole, selfStatus, selfDelete].every((response) => response.status === 400
    && response.errorCode === 'ADMIN_002' && response.errorMessage === selfErrorMessage
    && response.envelope && privateNoStore(response.cacheControl)), 'self role removal/status/delete are ADMIN_002 with the approved message/no-store envelope');

  const selfBeforeKeep = await apiCall('GET', `/api/v1/admin/users/${admin.me.id}`, admin.token);
  const selfKeepRole = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/role`, admin.token, { role: 'ADMIN' });
  const selfKeepStatus = await apiCall('PUT', `/api/v1/admin/users/${admin.me.id}/status`, admin.token, { status: 'ACTIVE' });
  const selfAfterKeep = await apiCall('GET', `/api/v1/admin/users/${admin.me.id}`, admin.token);
  check('mutation-self-keep', selfKeepRole.status === 200 && selfKeepStatus.status === 200, 'self ADMIN/ACTIVE idempotent keep requests succeed');
  check('mutation-same-value-audit-immutable', selfKeepRole.status === 200 && selfKeepStatus.status === 200
    && selfAfterKeep.status === 200 && selfAfterKeep.data?.role === 'ADMIN'
    && selfAfterKeep.data?.status === 'ACTIVE'
    && selfBeforeKeep.status === 200
    && selfBeforeKeep.data?.updatedAt === selfAfterKeep.data?.updatedAt
    && selfKeepRole.data?.updatedAt === selfAfterKeep.data?.updatedAt
    && selfKeepStatus.data?.updatedAt === selfAfterKeep.data?.updatedAt,
  'same-value role/status mutations return canonical detail without an audit updatedAt change');

  const promoted = await apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, admin.token, { role: 'ADMIN' });
  const targetAdminClaimToken = await signinAccount(fixture.accounts.target, 'target-promoted-admin');
  accounts.target.adminClaimToken = targetAdminClaimToken;
  const targetAdminClaims = jwtClaimsInMemory(targetAdminClaimToken);
  const targetAsAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', targetAdminClaimToken);
  const targetOldUserClaim = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.target.userToken);
  check('mutation-role-promote-current-token', promoted.status === 200 && targetAdminClaims?.role === 'ADMIN'
    && targetAsAdmin.status === 200
    && targetOldUserClaim.status === 200 && targetOldUserClaim.envelope && privateNoStore(targetOldUserClaim.cacheControl),
  'role promotion is authorized by current ACTIVE DB role even for a pre-promotion USER claim; a fresh ADMIN claim also succeeds');
  check('mutation-role-canonical', promoted.status === 200 && promoted.envelope && privateNoStore(promoted.cacheControl)
    && Number(promoted.data?.id) === Number(targetId) && promoted.data?.role === 'ADMIN',
  'role promotion returns canonical target ID/role and private no-store envelope');
  const demoted = await apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, admin.token, { role: 'USER' });
  const staleTargetAdminAfterDemote = await adminActorMatrix(targetAdminClaimToken, targetId, 'ADMIN_001', 403);
  check('mutation-role-demote', demoted.status === 200 && demoted.data?.role === 'USER'
    && staleTargetAdminAfterDemote, 'role demotion returns canonical detail and invalidates existing ADMIN claim authorization');
  check('mutation-role-demote-canonical', demoted.status === 200 && demoted.envelope && privateNoStore(demoted.cacheControl)
    && Number(demoted.data?.id) === Number(targetId) && demoted.data?.role === 'USER',
  'role demotion returns canonical target ID/role and private no-store envelope');

  const suspendedRefreshProbe = await createRefreshProbe(fixture.accounts.target, 'target-before-suspend');
  const suspended = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'SUSPENDED' });
  const suspendedAccess = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', targetAdminClaimToken);
  const suspendedPublic = await apiCall('GET', `/api/v1/posts/${posts.targetPublic.id}`);
  const suspendedWrite = await apiCall('POST', '/api/v1/posts', accounts.target.userToken, {
    blogId: accounts.target.blog.id,
    title: 'M9 suspended write must fail',
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'suspended' }] }] },
    contentHtml: '<p>suspended</p>', categoryId: null, visibility: 'PUBLIC', publish: true,
    thumbnailUrl: null, tagNames: [], images: [],
  });
  const suspendedCommentWrite = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.userToken, {
    content: 'M9 suspended comment must fail', parentId: null,
  });
  const suspendedLikeWrite = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/likes`, accounts.target.userToken);
  const suspendedUniverseWrite = await apiCall('POST', `/api/v1/universe/requests/${accounts.special.me.id}`, accounts.target.userToken);
  const suspendedSignin = await signinAttempt(fixture.accounts.suspended, 'suspended');
  const suspendedNotifications = await apiCall('GET', '/api/v1/notifications?read=all&page=0&size=20', accounts.target.userToken);
  const suspendedNotificationId = suspendedNotifications.data?.items?.[0]?.id;
  const suspendedNotificationRead = Number.isInteger(Number(suspendedNotificationId))
    ? await apiCall('PUT', `/api/v1/notifications/${suspendedNotificationId}/read`, accounts.target.userToken)
    : null;
  const suspendedSigninAndWritesOk = suspended.status === 200 && suspendedAccess.status === 403 && suspendedAccess.errorCode === 'USER_003'
    && suspendedPublic.status === 200 && suspendedWrite.status === 403 && suspendedWrite.errorCode === 'USER_003'
    && suspendedCommentWrite.status === 403 && suspendedCommentWrite.errorCode === 'USER_003'
    && suspendedLikeWrite.status === 403 && suspendedLikeWrite.errorCode === 'USER_003'
    && suspendedUniverseWrite.status === 403 && suspendedUniverseWrite.errorCode === 'USER_003'
    && suspendedSignin.status === 403 && suspendedSignin.errorCode === 'USER_003'
    && suspendedNotifications.status === 200 && suspendedNotifications.data?.canManage === false
    && suspendedNotificationRead != null && suspendedNotificationRead.status === 403 && suspendedNotificationRead.errorCode === 'USER_003';
  check('mutation-suspend-boundary', suspendedSigninAndWritesOk,
    'SUSPENDED blocks admin/write/read mutation access but preserves PUBLIC and notification read-only view');
  check('mutation-suspended-signin-and-writes', suspendedSigninAndWritesOk,
    'SUSPENDED signin and post/comment/like/universe writes remain denied with the exact state error');
  const suspendedRefresh = suspendedRefreshProbe ? await refreshFromProbe(suspendedRefreshProbe) : null;
  const suspendedRefreshAgain = suspendedRefreshProbe ? await refreshFromProbe(suspendedRefreshProbe) : null;
  check('refresh-suspended-rejected', suspendedRefresh?.status === 401 && suspendedRefresh.errorCode === 'AUTH_003'
    && suspendedRefresh.envelope && privateNoStore(suspendedRefresh.cacheControl),
  'refresh token issued before suspension is rejected as AUTH_003');
  check('refresh-suspended-reuse-rejected', suspendedRefreshAgain?.status === 401 && suspendedRefreshAgain.errorCode === 'AUTH_003'
    && suspendedRefreshAgain.envelope && privateNoStore(suspendedRefreshAgain.cacheControl),
  'the same pre-suspension refresh credential remains rejected on reuse and is not consumed into a false success');
  const activated = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'ACTIVE' });
  const reactivatedRefresh = suspendedRefreshProbe ? await refreshFromProbe(suspendedRefreshProbe) : null;
  check('refresh-reactivated-existing-accepted', activated.status === 200
    && reactivatedRefresh?.status === 200 && reactivatedRefresh.envelope && privateNoStore(reactivatedRefresh.cacheControl),
  'the pre-suspension refresh credential remains usable after reactivation when it was not discarded');
  await suspendedRefreshProbe?.dispose();
  const repeatedActive = await apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, admin.token, { status: 'ACTIVE' });
  const targetAdminAfterActivate = await adminActorMatrix(targetAdminClaimToken, targetId, 'ADMIN_001', 403);
  check('mutation-status-idempotence', activated.status === 200 && repeatedActive.status === 200 && targetAdminAfterActivate,
    'status changes and repeated same value succeed without restoring a demoted ADMIN claim');
  check('mutation-status-canonical', suspended.status === 200 && suspended.envelope && privateNoStore(suspended.cacheControl)
    && suspended.data?.status === 'SUSPENDED' && activated.status === 200 && activated.data?.status === 'ACTIVE'
    && repeatedActive.data?.status === 'ACTIVE', 'status mutations return canonical status and private no-store envelopes');

  const activeRefreshProbe = await createRefreshProbe(fixture.accounts.target, 'target-active');
  const activeRefresh = activeRefreshProbe ? await refreshFromProbe(activeRefreshProbe) : null;
  check('refresh-active-accepted', activeRefresh?.status === 200 && activeRefresh.envelope,
    'ACTIVE account refresh succeeds with a current DB-backed user');
  await activeRefreshProbe?.dispose();

  // Sign in before the fingerprint: the refresh row is a real child and must
  // be present in both sides of the later soft-delete comparison. No target
  // signin is allowed after the before snapshot.
  const deleteRefreshProbe = await createRefreshProbe(fixture.accounts.target, 'target-before-delete');
  const beforeDeleteFingerprint = await runtimeFingerprint(accounts.target.me.id, 'before-target-delete');
  evidence.fixture.fingerprint.beforeDelete = {
    phase: beforeDeleteFingerprint.phase,
    users: beforeDeleteFingerprint.users,
    childFingerprints: beforeDeleteFingerprint.childFingerprints,
    tableCounts: beforeDeleteFingerprint.tableCounts,
  };
  const childCounts = beforeDeleteFingerprint.tableCounts ?? {};
  check('fixture-native-child-counts', Number(childCounts.blogs) >= 2
    && Number(childCounts.posts) >= 4
    && Number(childCounts.comments) >= 5
    && Number(childCounts.post_tags) >= 2
    && Number(childCounts.post_likes) >= 1
    && Number(childCounts.universes) >= 5
    && Number(childCounts.image_uploads) >= 1,
  'before-delete fingerprint proves non-empty target child rows for blogs/posts/comments/tags/likes/universe/uploads');
  const imageBefore = posts.targetImage
    ? await binaryCall(posts.targetImage.url, accounts.target.token)
    : null;
  check('http-target-image-before-delete', imageBefore?.status === 200 && imageBefore.sha256 === posts.targetImage?.sha256 && imageBefore.size === posts.targetImage?.bytes.length, 'target image is readable with target token and byte hash matches upload');

  const deleted = await apiCall('DELETE', `/api/v1/admin/users/${targetId}`, admin.token);
  const repeatedDelete = await apiCall('DELETE', `/api/v1/admin/users/${targetId}`, admin.token);
  const deletedDetail = await apiCall('GET', `/api/v1/admin/users/${targetId}`, admin.token);
  check('mutation-soft-delete', [200, 204].includes(deleted.status) && deleted.data == null, 'delete returns empty success');
  check('mutation-delete-idempotence-contract', repeatedDelete.status === 404 && repeatedDelete.errorCode === 'USER_001' && deletedDetail.status === 404, 'deleted target and repeated DELETE are USER_001/404');
  const targetAdminAfterDelete = await adminActorMatrix(targetAdminClaimToken, targetId, 'USER_001', 404);
  check('mutation-deleted-admin-claim-recheck', targetAdminAfterDelete, 'previously issued ADMIN claim is rejected as USER_001 after actor deletion');
  const deletedRefresh = deleteRefreshProbe ? await refreshFromProbe(deleteRefreshProbe) : null;
  check('refresh-deleted-rejected', deletedRefresh?.status === 401 && deletedRefresh.errorCode === 'AUTH_003'
    && deletedRefresh.envelope && privateNoStore(deletedRefresh.cacheControl),
  'refresh token issued before soft delete is rejected as AUTH_003');
  await deleteRefreshProbe?.dispose();

  const deletedSignin = await signinAttempt(fixture.accounts.target, 'deleted-target');
  const deletedPostWrite = await apiCall('POST', '/api/v1/posts', accounts.target.userToken, {
    blogId: accounts.target.blog.id,
    title: 'M9 deleted post must fail',
    contentJson: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'deleted' }] }] },
    contentHtml: '<p>deleted</p>', categoryId: null, visibility: 'PUBLIC', publish: true,
    thumbnailUrl: null, tagNames: [], images: [],
  });
  const deletedCommentWrite = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/comments`, accounts.target.userToken, {
    content: 'M9 deleted comment must fail', parentId: null,
  });
  const deletedLikeWrite = await apiCall('POST', `/api/v1/posts/${posts.adminPublic.id}/likes`, accounts.target.userToken);
  const deletedUniverseWrite = await apiCall('POST', `/api/v1/universe/requests/${accounts.special.me.id}`, accounts.target.userToken);
  check('mutation-deleted-signin-and-writes', deletedSignin.status === 401 && deletedSignin.errorCode === 'AUTH_001'
    && deletedPostWrite.status === 404 && deletedPostWrite.errorCode === 'USER_001'
    && deletedCommentWrite.status === 404 && deletedCommentWrite.errorCode === 'USER_001'
    && deletedLikeWrite.status === 404 && deletedLikeWrite.errorCode === 'USER_001'
    && deletedUniverseWrite.status === 404 && deletedUniverseWrite.errorCode === 'USER_001',
  'soft-deleted actor signin is AUTH_001/401 while authenticated mutations remain USER_001/404');

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

async function adminActorMatrix(token, targetId, errorCode, status) {
  const responses = await Promise.all([
    apiCall('GET', '/api/v1/admin/users?page=0&size=1', token),
    apiCall('GET', `/api/v1/admin/users/${targetId}`, token),
    apiCall('PUT', `/api/v1/admin/users/${targetId}/role`, token, { role: 'USER' }),
    apiCall('PUT', `/api/v1/admin/users/${targetId}/status`, token, { status: 'ACTIVE' }),
    apiCall('DELETE', `/api/v1/admin/users/${targetId}`, token),
  ]);
  return responses.every((response) => response.status === status
    && response.errorCode === errorCode && response.envelope && privateNoStore(response.cacheControl));
}

async function runConcurrencyMatrix(accounts) {
  const raceId = accounts.race.me.id;
  const actorDemotion = await Promise.all([
    apiCall('PUT', `/api/v1/admin/users/${accounts.admin.me.id}/role`, accounts.adminPeer.token, { role: 'USER' }),
    apiCall('PUT', `/api/v1/admin/users/${accounts.user.me.id}/status`, accounts.admin.token, { status: 'SUSPENDED' }),
  ]);
  const demoteResponse = actorDemotion[0];
  const actorStatusResponse = actorDemotion[1];
  const adminFinal = await apiCall('GET', `/api/v1/admin/users/${accounts.admin.me.id}`, accounts.adminKeeper.token);
  const userFinal = await apiCall('GET', `/api/v1/admin/users/${accounts.user.me.id}`, accounts.adminKeeper.token);
  const actorRaceAllowed = [200, 403].includes(actorStatusResponse.status)
    && (actorStatusResponse.status === 200 || actorStatusResponse.errorCode === 'ADMIN_001')
    && demoteResponse.status === 200
    && demoteResponse.data?.role === 'USER'
    && adminFinal.status === 200 && adminFinal.data?.role === 'USER'
    && userFinal.status === 200
    && userFinal.data?.status === (actorStatusResponse.status === 200 ? 'SUSPENDED' : 'ACTIVE')
    && [demoteResponse, actorStatusResponse].every((response) => response.envelope && privateNoStore(response.cacheControl));
  evidence.fixture.concurrency = {
    actorDemotion: actorDemotion.map((response) => ({ status: response.status, errorCode: response.errorCode })),
    actorFinal: { role: adminFinal.data?.role ?? null, userStatus: userFinal.data?.status ?? null },
  };
  check('concurrency-actor-demotion-exact', actorRaceAllowed,
    'actor demotion race accepts only linearized 200/ADMIN_001 outcomes and verifies final actor/user DB state');
  check('concurrency-actor-demotion-race', actorRaceAllowed,
    'HTTP race is an outcome probe only; final state is checked separately from physical lock evidence');
  const restoreUser = await apiCall('PUT', `/api/v1/admin/users/${accounts.user.me.id}/status`, accounts.adminKeeper.token, { status: 'ACTIVE' });
  check('concurrency-actor-race-restore', restoreUser.status === 200 && restoreUser.data?.status === 'ACTIVE', 'race fixture user is restored for subsequent browser evidence');

  const targetRace = await Promise.all([
    apiCall('PUT', `/api/v1/admin/users/${raceId}/role`, accounts.adminPeer.token, { role: 'ADMIN' }),
    apiCall('PUT', `/api/v1/admin/users/${raceId}/status`, accounts.adminKeeper.token, { status: 'SUSPENDED' }),
    apiCall('DELETE', `/api/v1/admin/users/${raceId}`, accounts.adminPeer.token),
  ]);
  const targetRoleRace = targetRace[0];
  const targetStatusRace = targetRace[1];
  const targetDeleteRace = targetRace[2];
  const raceFinal = await apiCall('GET', `/api/v1/admin/users/${raceId}`, accounts.adminKeeper.token);
  const targetRaceAllowed = [200, 404].includes(targetRoleRace.status)
    && (targetRoleRace.status === 200 ? targetRoleRace.data?.role === 'ADMIN' : targetRoleRace.errorCode === 'USER_001')
    && [200, 404].includes(targetStatusRace.status)
    && (targetStatusRace.status === 200 ? targetStatusRace.data?.status === 'SUSPENDED' : targetStatusRace.errorCode === 'USER_001')
    && [200, 204].includes(targetDeleteRace.status)
    && targetDeleteRace.data == null
    && raceFinal.status === 404 && raceFinal.errorCode === 'USER_001'
    && targetRace.every((response) => response.envelope && privateNoStore(response.cacheControl));
  evidence.fixture.concurrency.targetMutation = {
    responses: targetRace.map((response) => ({ status: response.status, errorCode: response.errorCode })),
    final: { status: raceFinal.status, errorCode: raceFinal.errorCode },
  };
  check('concurrency-target-mutation-exact', targetRaceAllowed,
    'target role/status/delete race accepts only canonical success-or-deleted outcomes and verifies final USER_001');
  check('concurrency-target-mutation-race', targetRaceAllowed,
    'target race is not treated as a MySQL barrier; exact response set and final DB state are required');

  const oldAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.admin.token);
  const keeperStillAdmin = await apiCall('GET', '/api/v1/admin/users?page=0&size=1', accounts.adminKeeper.token);
  check('concurrency-stale-access', oldAdmin.status === 403 && oldAdmin.errorCode === 'ADMIN_001' && keeperStillAdmin.status === 200, 'old ADMIN token loses current DB permission while keeper remains active');
}

async function verifyPhysicalConcurrencyEvidence() {
  const backendRoot = process.env.M9_BACKEND_EVIDENCE_DIR ?? 'build/m9-evidence/backend-final-2';
  const backendDir = process.env.M9_BACKEND_TEST_RESULTS_DIR ?? `${backendRoot}/test-results`;
  const backendSummary = resolve(root, process.env.M9_BACKEND_SUMMARY_JSON ?? `${backendRoot}/summary.json`);
  const frontendJson = resolve(root, process.env.M9_FRONTEND_EVIDENCE_JSON ?? 'build/m9-evidence/frontend-final-result.json');
  let result;
  try {
    result = await collectTestEvidence({ root, backendDir: resolve(root, backendDir), backendSummary, frontendJson });
  } catch (error) {
    notRun('concurrency-physical-barrier-evidence', `test evidence helper unavailable: ${scrubSecrets(error instanceof Error ? error.message : String(error))}`);
    notRun('backend-rr-writer-evidence', 'test evidence helper did not return a structured result');
    notRun('backend-noop-audit-evidence', 'test evidence helper did not return a structured result');
    notRun('backend-state-policy-evidence', 'test evidence helper did not return a structured result');
    notRun('frontend-matrix-evidence', 'test evidence helper did not return a structured result');
    return;
  }
  evidence.fixture.testArtifacts = {
    mappings: result?.mappings ?? {},
    artifactHashes: result?.artifactHashes ?? {},
  };
  const checks = Array.isArray(result?.checks) ? result.checks : [];
  const expectedIds = [
    'concurrency-physical-barrier-evidence',
    'backend-rr-writer-evidence',
    'backend-noop-audit-evidence',
    'backend-state-policy-evidence',
    'frontend-matrix-evidence',
  ];
  for (const id of expectedIds) {
    const row = checks.find((checkRow) => checkRow?.id === id);
    if (!row) notRun(id, 'test evidence helper omitted required structured artifact mapping');
    else {
      const uncovered = id === 'frontend-matrix-evidence'
        ? (result.mappings?.uncoveredFrontendRows ?? [])
        : id === 'backend-state-policy-evidence'
          ? (result.mappings?.uncoveredStatePolicyRows ?? []) : [];
      const unresolved = uncovered.filter((item) => !directEvidenceCovers(id, item));
      const passed = row.passed === true && unresolved.length === 0;
      if (unresolved.length > 0) {
        evidence.fixture.testArtifacts.uncovered = {
          ...(evidence.fixture.testArtifacts.uncovered ?? {}),
          [id]: unresolved,
        };
      }
      check(id, passed, scrubSecrets(JSON.stringify({ helper: row.detail, unresolved })));
    }
  }
}

function directEvidenceCovers(group, row) {
  const id = String(row?.id ?? row ?? '');
  const required = group === 'frontend-matrix-evidence'
    ? id === 'admin-probe-pending'
      ? ['browser-admin-list-pending-sensitive']
      : id === 'same-provider-account-switch'
        ? ['browser-stale-list', 'browser-stale-detail', 'browser-stale-mutation', 'browser-account-switch-stale']
        : id === 'return-probe-state-variants'
          ? ['browser-admin-focus-suspended', 'browser-admin-visible-deleted', 'browser-admin-denied-reentry']
          : id === 'list-detail-empty-error-retry'
            ? ['browser-list-empty-state', 'browser-list-transient-retry', 'browser-list-transient-retry-result', 'browser-detail-transient-retry', 'browser-detail-transient-retry-result', 'browser-mutation-error-retry']
            : id === 'mutation-duplicate-submit'
              ? ['browser-mutation-duplicate-submit']
              : id === 'visual-browser-url'
                ? ['browser-admin-list-width', 'browser-admin-detail-width', 'browser-search-url-state', 'browser-search-paging-history', 'browser-screenshot-evidence', 'browser-admin-entry-menu', 'browser-admin-heading', 'browser-admin-role-label']
                : []
    : id === 'suspended-deleted-signin'
      ? ['mutation-suspend-boundary', 'mutation-deleted-signin-and-writes']
      : id === 'reactivated-existing-refresh'
        ? ['refresh-reactivated-existing-accepted']
        : id === 'full-write-state-transitions'
          ? ['mutation-suspend-boundary', 'mutation-deleted-signin-and-writes']
          : [];
  return required.length > 0 && required.every((name) => {
    const rows = evidence.checks.filter((item) => item.name === name);
    return rows.length > 0 && rows.every((item) => item.status === 'PASS');
  });
}

async function runBrowserAcceptance(accounts, posts) {
  // Refresh cookies are context-scoped. Keep guest, USER, and ADMIN baseline
  // journeys isolated so a closed page cannot restore the next account.
  const guestContext = await newBrowserContext();
  try {
    const guestPage = await guestContext.newPage();
    const guestRequests = [];
    guestPage.on('request', (request) => {
      if (request.url().includes('/api/v1/admin/')) guestRequests.push(request.url());
    });
    await guestPage.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
    await guestPage.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
    check('browser-guest-admin-guard', guestPage.url().endsWith('/signin') && guestRequests.length === 0, 'guest reaches signin without admin API call');
    await guestPage.goto(`${frontendUrl}/admin/users/${accounts.user.me.id}`, { waitUntil: 'domcontentloaded' });
    await guestPage.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
    check('browser-guest-detail-guard', guestPage.url().endsWith('/signin'), 'guest detail route is protected before target data renders');
  } finally {
    await guestContext.close();
  }

  const userContext = await newBrowserContext();
  try {
    const userPage = await userContext.newPage();
    await signInInSpa(userPage, accounts.user);
    await userPage.goto(`${frontendUrl}/admin`, { waitUntil: 'domcontentloaded' });
    await userPage.getByRole('heading', { name: '관리자 화면에 접근할 수 없습니다', exact: true }).waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
    check('browser-user-admin-guard', userPage.url().endsWith('/admin')
      && await userPage.getByRole('heading', { name: '관리자 화면에 접근할 수 없습니다', exact: true }).count() === 1
      && await exactCount(adminUserRow(userPage, accounts.adminKeeper.me.nickname)) === 0,
    'authenticated USER direct entry renders denied guard without admin data');
  } finally {
    await userContext.close();
  }

  const adminContext = await newBrowserContext();
  try {
    const page = await adminContext.newPage();
    await signInInSpa(page, accounts.adminKeeper);
    const profile = page.getByRole('link', { name: accounts.adminKeeper.me.nickname, exact: true });
    await profile.waitFor({ state: 'visible', timeout: 15_000 });
    await profile.click();
    const adminMenu = page.getByRole('menuitem', { name: '관리자', exact: true });
    await adminMenu.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
    check('browser-admin-entry-menu', await exactCount(adminMenu) === 1, 'ADMIN enters through the profile menu only');
    await adminMenu.click();
    await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15_000 });
    const pendingListHolder = await holdAdminResponse(page, (request) => request.method() === 'GET'
      && new URL(request.url()).pathname === '/api/v1/admin/users'
      && new URL(request.url()).searchParams.get('size') === '100');
    const listReady = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return response.request().method() === 'GET' && url.pathname === '/api/v1/admin/users'
        && url.searchParams.get('size') === '100' && response.status() === 200;
    }, { timeout: 15_000 });
    await page.goto(`${frontendUrl}/admin?page=0&size=100`, { waitUntil: 'domcontentloaded' });
    const pendingListUpstream = await withTimeout(pendingListHolder.readyPromise, STALE_PROBE_TIMEOUT_MS, 'admin list pending response');
    check('browser-admin-list-pending-sensitive', pendingListUpstream.status === 200
      && await exactCount(adminUserRow(page, accounts.adminKeeper.me.email)) === 0,
    'list pending state does not expose fixture rows before the actual HTTP 200 is released');
    pendingListHolder.release();
    await withTimeout(pendingListHolder.released, STALE_PROBE_TIMEOUT_MS, 'admin list pending release');
    await pendingListHolder.dispose();
    const listResponse = await listReady.catch(() => null);
    const listBody = listResponse ? await listResponse.json().catch(() => null) : null;
    check('browser-admin-list-http-ready', listResponse?.status() === 200
      && (listBody?.data?.users?.items ?? []).some((item) => Number(item.id) === Number(accounts.adminKeeper.me.id)),
    'admin list readiness requires an actual HTTP 200 and a fixture row, not only heading visibility');
    await waitForAdminReady(page, accounts.adminKeeper.me.nickname);
    check('browser-admin-heading', await exactCount(page.getByText('ADMIN CONSOLE', { exact: true })) === 1, 'admin list renders canonical visible label once');
    check('browser-admin-role-label', await exactCount(page.getByText('ROLE: ADMIN', { exact: true })) === 1, 'admin list renders canonical role label once');
    check('browser-admin-list-width', await contentWidth(page, 'list') >= 1080 && await contentWidth(page, 'list') <= 1120, 'admin list content is approximately 1100px at 1440px');
    await runBrowserListTransientAcceptance(page, accounts);
    await runBrowserSearchPagingHistory(page, accounts);
    await runBrowserRevalidationBoundaryProbes(accounts);

    const search = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
    check('browser-search-selector-unique', await exactCount(search) === 1, 'search control uses a duplicate-safe accessible selector');
    if (await exactCount(search) === 1) {
      await search.fill(fixture.searchLiteral);
      await search.press('Enter');
      await page.waitForTimeout(250);
      check('browser-search-url-state', new URL(page.url()).searchParams.get('keyword') === fixture.searchLiteral, 'search keyword is URL state');
    }

    await captureProduct(page, 'admin-list-1440');
    const detailReady = page.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === `/api/v1/admin/users/${accounts.user.me.id}` && response.status() === 200, { timeout: 15_000 });
    await page.goto(`${frontendUrl}/admin/users/${accounts.user.me.id}`, { waitUntil: 'domcontentloaded' });
    const detailResponse = await detailReady.catch(() => null);
    const detailBody = detailResponse ? await detailResponse.json().catch(() => null) : null;
    check('browser-admin-detail-http-ready', detailResponse?.status() === 200
      && Number(detailBody?.data?.id) === Number(accounts.user.me.id),
    'admin detail readiness requires an actual HTTP 200 for the requested fixture ID');
    await waitForAdminDetail(page);
    check('browser-admin-detail-last-access', await page.getByText('마지막 접속 기록 없음', { exact: true }).count() === 1, 'detail renders unrecorded last access copy');
    check('browser-admin-detail-width', await contentWidth(page, 'detail') >= 820 && await contentWidth(page, 'detail') <= 900, 'admin detail content is approximately 860px at 1440px');
    await captureProduct(page, 'admin-detail-1440');
    check('browser-screenshot-evidence', evidence.screenshots.includes('admin-list-1440.png')
      && evidence.screenshots.includes('admin-detail-1440.png'),
    '1440px list and detail screenshots are captured as evidence');

    await runBrowserTarget404Probe(page, accounts.target.me.id);
    await runBrowserMutationAcceptance(page, accounts);
    await runBrowserStaleProbe(adminContext, accounts);
    await verifyDesignReference(adminContext);
  } finally {
    await adminContext.close();
  }

  // posts is intentionally accepted by the signature: the browser target route uses only IDs.
  void posts;
}

async function runBrowserListTransientAcceptance(page, accounts) {
  const search = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
  const transientKeyword = accounts.adminKeeper.email;
  const emptyKeyword = `${fixture.runId}-empty`;
  const keeperRow = adminUserRow(page, accounts.adminKeeper.email);
  let listFailed = false;
  const listPattern = /\/api\/v1\/admin\/users(?:\?|$)/;
  await page.route(listPattern, async (route) => {
    const url = new URL(route.request().url());
    if (!listFailed && route.request().method() === 'GET' && url.searchParams.get('keyword') === transientKeyword) {
      listFailed = true;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        headers: { 'cache-control': 'private, no-store' },
        body: JSON.stringify({ success: false, data: null, error: { code: 'ADMIN_999', message: 'synthetic list retry' }, timestamp: new Date().toISOString() }),
      });
      return;
    }
    await route.continue();
  });
  await search.fill(transientKeyword);
  await search.press('Enter');
  const listRetryNotice = page.getByRole('alert').filter({ hasText: 'synthetic list retry' });
  await listRetryNotice.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);
  const listRetryButton = page.getByRole('button', { name: '다시 시도', exact: true }).first();
  const priorRowVisible = await exactCount(keeperRow) === 1;
  check('browser-list-transient-retry', listFailed && priorRowVisible && await exactCount(listRetryButton) === 1,
    'transient list failure preserves the prior fixture row and exposes a single retry action');
  await page.unroute(listPattern);
  let listRetryResponse = null;
  if (await exactCount(listRetryButton) === 1) {
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === '/api/v1/admin/users' && response.status() === 200, { timeout: 15_000 });
    await listRetryButton.click();
    listRetryResponse = await responsePromise.catch(() => null);
  }
  check('browser-list-transient-retry-result', listRetryResponse?.status() === 200
    && await exactCount(keeperRow) === 1,
  'list retry restores a canonical HTTP 200 result and the prior fixture row');

  await search.fill(emptyKeyword);
  await search.press('Enter');
  await page.getByText('검색 결과가 없습니다.', { exact: true }).waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  check('browser-list-empty-state', await page.getByText('검색 결과가 없습니다.', { exact: true }).count() === 1
    && await exactCount(keeperRow) === 0,
  'a real successful empty response renders the empty state without stale rows');
  await search.fill('');
  await search.press('Enter');
  await waitForAdminReady(page).catch(() => undefined);

  const detailPattern = new RegExp(`/api/v1/admin/users/${accounts.user.me.id}$`);
  let detailFailed = false;
  await page.route(detailPattern, async (route) => {
    if (!detailFailed && route.request().method() === 'GET') {
      detailFailed = true;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        headers: { 'cache-control': 'private, no-store' },
        body: JSON.stringify({ success: false, data: null, error: { code: 'ADMIN_999', message: 'synthetic detail retry' }, timestamp: new Date().toISOString() }),
      });
      return;
    }
    await route.continue();
  });
  await page.goto(`${frontendUrl}/admin/users/${accounts.user.me.id}`, { waitUntil: 'domcontentloaded' });
  const detailRetryNotice = page.getByRole('alert').filter({ hasText: 'synthetic detail retry' });
  await detailRetryNotice.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);
  const detailRetryButton = page.getByRole('button', { name: '다시 시도', exact: true });
  check('browser-detail-transient-retry', detailFailed && await exactCount(detailRetryButton) === 1,
    'transient detail failure exposes a scoped retry without stale target data');
  await page.unroute(detailPattern);
  let detailRetryResponse = null;
  if (await exactCount(detailRetryButton) === 1) {
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === `/api/v1/admin/users/${accounts.user.me.id}` && response.status() === 200, { timeout: 15_000 });
    await detailRetryButton.click();
    detailRetryResponse = await responsePromise.catch(() => null);
  }
  check('browser-detail-transient-retry-result', detailRetryResponse?.status() === 200
    && await page.getByText(accounts.user.me.nickname, { exact: true }).count() === 1,
  'detail retry restores the requested canonical target');
  await page.goto(`${frontendUrl}/admin?page=0&size=20`, { waitUntil: 'domcontentloaded' });
  await waitForAdminReady(page);
}

async function runBrowserSearchPagingHistory(page, accounts) {
  const search = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
  const bulkAccounts = Array.from({ length: 21 }, (_, index) => accounts[`bulk${String(index + 1).padStart(2, '0')}`])
    .sort((left, right) => Number(right.me.id) - Number(left.me.id));
  const expectedPageZero = bulkAccounts.slice(0, 20);
  const expectedPageOne = bulkAccounts.slice(20);
  const table = adminUserTable(page);
  await search.fill(fixture.bulkSearchLiteral);
  await search.press('Enter');
  await page.getByRole('button', { name: '2 페이지', exact: true }).waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  await adminUserRow(page, expectedPageZero[0].email).first().waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  let pageZeroExpected = true;
  for (const account of expectedPageZero) pageZeroExpected = pageZeroExpected && await exactCount(adminUserRow(page, account.email)) === 1;
  let pageOneAbsent = true;
  for (const account of expectedPageOne) pageOneAbsent = pageOneAbsent && await exactCount(adminUserRow(page, account.email)) === 0;
  const pageZeroRows = await table.getByRole('row').count();
  const next = page.getByRole('button', { name: '다음 페이지', exact: true });
  let pageOneResponse = null;
  if (await exactCount(next) === 1) {
    const responsePromise = page.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === '/api/v1/admin/users'
      && new URL(response.url()).searchParams.get('page') === '1' && response.status() === 200, { timeout: 15_000 });
    await next.click();
    pageOneResponse = await responsePromise.catch(() => null);
  }
  const pageOne = new URL(page.url()).searchParams.get('page') === '1';
  await adminUserRow(page, expectedPageOne[0].email).first().waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  let pageOneExpected = true;
  for (const account of expectedPageOne) pageOneExpected = pageOneExpected && await exactCount(adminUserRow(page, account.email)) === 1;
  let pageZeroAbsent = true;
  for (const account of expectedPageZero) pageZeroAbsent = pageZeroAbsent && await exactCount(adminUserRow(page, account.email)) === 0;
  const pageOneRows = await table.getByRole('row').count();
  await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
  await waitForAdminReady(page).catch(() => undefined);
  const backToPageZero = !new URL(page.url()).searchParams.get('page');
  await adminUserRow(page, expectedPageZero[0].email).first().waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  let backPageZeroExpected = true;
  for (const account of expectedPageZero) backPageZeroExpected = backPageZeroExpected && await exactCount(adminUserRow(page, account.email)) === 1;
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitForAdminReady(page).catch(() => undefined);
  await adminUserRow(page, expectedPageZero[0].email).first().waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
  const reloadedKeyword = new URL(page.url()).searchParams.get('keyword') === fixture.bulkSearchLiteral;
  const reloadedSize = new URL(page.url()).searchParams.get('size') === '20';
  let reloadedPageZeroExpected = true;
  for (const account of expectedPageZero) reloadedPageZeroExpected = reloadedPageZeroExpected && await exactCount(adminUserRow(page, account.email)) === 1;
  let reloadedPageOneAbsent = true;
  for (const account of expectedPageOne) reloadedPageOneAbsent = reloadedPageOneAbsent && await exactCount(adminUserRow(page, account.email)) === 0;
  const reloadedRows = await table.getByRole('row').count();
  check('browser-search-paging-history', pageOneResponse?.status() === 200 && pageOne && backToPageZero
    && pageZeroExpected && pageOneAbsent && pageZeroRows === 21
    && pageOneExpected && pageZeroAbsent && pageOneRows === 2
    && backPageZeroExpected && reloadedKeyword && reloadedSize
    && reloadedPageZeroExpected && reloadedPageOneAbsent && reloadedRows === 21,
  'size=20 search paging matches the exact DESC ID slices (20+1), and keyword/page state survives next-page navigation, browser back, and reload');
  await search.fill('');
  await search.press('Enter');
  await waitForAdminReady(page).catch(() => undefined);
}

async function runBrowserRevalidationBoundaryProbes(accounts) {
  const probes = [
    { check: 'browser-admin-focus-suspended', code: 'USER_003', event: 'focus', message: 'synthetic suspended actor' },
    { check: 'browser-admin-visible-deleted', code: 'USER_001', event: 'visibilitychange', message: 'synthetic deleted actor' },
  ];
  for (const probe of probes) {
    const context = await newBrowserContext();
    try {
      const page = await context.newPage();
      let phase = 'initial';
      const routePattern = /\/api\/v1\/admin\/users(?:\?|$)/;
      await page.route(routePattern, async (route) => {
        const url = new URL(route.request().url());
        if (phase === 'boundary' && route.request().method() === 'GET' && url.searchParams.get('size') === '1') {
          await route.fulfill({
            status: probe.code === 'USER_003' ? 403 : 404,
            contentType: 'application/json',
            headers: { 'cache-control': 'private, no-store' },
            body: JSON.stringify({ success: false, data: null, error: { code: probe.code, message: probe.message }, timestamp: new Date().toISOString() }),
          });
          return;
        }
        await route.continue();
      });
      await signInInSpa(page, accounts.adminKeeper);
      await page.goto(`${frontendUrl}/admin?page=0&size=100`, { waitUntil: 'domcontentloaded' });
      await waitForAdminReady(page, accounts.adminKeeper.me.nickname);
      phase = 'boundary';
      await page.evaluate((eventName) => {
        if (eventName === 'visibilitychange') {
          Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
          document.dispatchEvent(new Event('visibilitychange'));
        } else {
          window.dispatchEvent(new Event(eventName));
        }
      }, probe.event);
      const denied = page.getByRole('heading', { name: '관리자 화면에 접근할 수 없습니다', exact: true });
      await denied.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => undefined);
      const deniedOk = await exactCount(denied) === 1
        && await exactCount(adminUserRow(page, accounts.adminKeeper.me.email)) === 0;
      check(probe.check, deniedOk, `${probe.event} revalidation renders ${probe.code} denied state without stale admin rows`);
      phase = 'allow';
      await page.goto(`${frontendUrl}/admin?page=0&size=100`, { waitUntil: 'domcontentloaded' });
      await waitForAdminReady(page, accounts.adminKeeper.me.nickname).catch(() => undefined);
      const reentryOk = await exactCount(page.getByText('ADMIN CONSOLE', { exact: true })) === 1
        && await exactCount(adminUserRow(page, accounts.adminKeeper.me.email)) === 1;
      check('browser-admin-denied-reentry', reentryOk, 'denied state can re-enter after a fresh current probe succeeds');
    } catch (error) {
      check(probe.check, false, scrubSecrets(error instanceof Error ? error.message : String(error)));
      check('browser-admin-denied-reentry', false, 'revalidation boundary probe did not reach a stable re-entry state');
    } finally {
      await context.close();
    }
  }
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
  const listBody = listResponse ? await listResponse.json().catch(() => null) : null;
  check('browser-current-actor-list-after-target-404', listResponse?.status() === 200
    && (listBody?.data?.users?.items ?? []).some((item) => Number(item.id) === Number(accounts.adminKeeper.me.id)),
  'current ACTIVE ADMIN remains in session after target 404 and list returns its exact fixture row');
}

async function runBrowserMutationAcceptance(page, accounts) {
  const targetId = accounts.user.me.id;
  await page.goto(`${frontendUrl}/admin/users/${targetId}`, { waitUntil: 'domcontentloaded' });
  await waitForAdminDetail(page);

  const role = page.getByRole('combobox', { name: '역할', exact: true });
  const roleButton = page.getByRole('button', { name: '역할 변경', exact: true });
  await role.selectOption('ADMIN');
  await roleButton.click();
  const roleDialog = page.getByRole('dialog', { name: '역할 변경 확인', exact: true });
  await roleDialog.waitFor({ state: 'visible', timeout: 10_000 });
  let roleMutationCount = 0;
  const initialRoleRoutePattern = new RegExp(`/api/v1/admin/users/${targetId}/role$`);
  await page.route(initialRoleRoutePattern, async (route) => {
    if (route.request().method() === 'PUT') roleMutationCount += 1;
    await route.continue();
  });
  const roleMutation = page.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}/role`);
  const roleCanonical = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}` && response.status() === 200);
  await roleDialog.getByRole('button', { name: '변경 확인', exact: true }).dblclick().catch(() => undefined);
  const [roleResponse, roleDetailResponse] = await Promise.all([roleMutation, roleCanonical]);
  await page.unroute(initialRoleRoutePattern);
  check('browser-role-confirm-canonical', roleResponse.status() === 200 && roleDetailResponse.status() === 200
    && await role.inputValue() === 'ADMIN', 'role confirmation sends one mutation and renders the canonical ADMIN detail');
  check('browser-mutation-duplicate-submit', roleMutationCount === 1,
    'double activation of a confirmation control sends exactly one role mutation');

  // Failure must preserve the row and expose a real retry path; the retry is released
  // only after the injected response has been observed.
  let failedRoleRequest = false;
  const roleRoutePattern = new RegExp(`/api/v1/admin/users/${targetId}/role$`);
  await page.route(roleRoutePattern, async (route) => {
    if (!failedRoleRequest && route.request().method() === 'PUT') {
      failedRoleRequest = true;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, data: null, error: { code: 'ADMIN_999', message: 'synthetic retry' }, timestamp: new Date().toISOString() }),
      });
      return;
    }
    await route.continue();
  });
  await role.selectOption('USER');
  await roleButton.click();
  const retryRoleDialog = page.getByRole('dialog', { name: '역할 변경 확인', exact: true });
  await retryRoleDialog.waitFor({ state: 'visible', timeout: 10_000 });
  await retryRoleDialog.getByRole('button', { name: '변경 확인', exact: true }).click();
  const retryNotice = page.getByRole('alert').filter({ hasText: 'synthetic retry' });
  await retryNotice.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);
  const retryButton = page.getByRole('button', { name: '다시 시도', exact: true });
  const rowStillVisible = await page.getByText(accounts.user.me.nickname, { exact: true }).count() === 1;
  await page.unroute(roleRoutePattern);
  let retryResponse = null;
  let retryDetailResponse = null;
  if (await exactCount(retryButton) === 1) {
    const retryMutation = page.waitForResponse((response) => response.request().method() === 'PUT'
      && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}/role` && response.status() === 200);
    const retryCanonical = page.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}` && response.status() === 200);
    await retryButton.click();
    [retryResponse, retryDetailResponse] = await Promise.all([retryMutation, retryCanonical]);
  }
  check('browser-mutation-error-retry', failedRoleRequest && rowStillVisible && await exactCount(retryButton) === 0
    && retryResponse?.status() === 200 && retryDetailResponse?.status() === 200 && await role.inputValue() === 'USER',
  'failed role mutation preserves the row and retry restores canonical state');

  const suspendButton = page.getByRole('button', { name: '일시 정지', exact: true });
  await suspendButton.click();
  const suspendDialog = page.getByRole('dialog', { name: '계정 상태 변경 확인', exact: true });
  await suspendDialog.waitFor({ state: 'visible', timeout: 10_000 });
  const suspendMutation = page.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}/status`);
  const suspendCanonical = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}` && response.status() === 200);
  await suspendDialog.getByRole('button', { name: '변경 확인', exact: true }).click();
  const [suspendResponse, suspendDetailResponse] = await Promise.all([suspendMutation, suspendCanonical]);
  check('browser-status-confirm-canonical', suspendResponse.status() === 200 && suspendDetailResponse.status() === 200
    && await page.getByRole('button', { name: '정지 해제', exact: true }).count() === 1, 'status confirmation renders canonical SUSPENDED state');

  const activateButton = page.getByRole('button', { name: '정지 해제', exact: true });
  await activateButton.click();
  const activateDialog = page.getByRole('dialog', { name: '계정 상태 변경 확인', exact: true });
  await activateDialog.waitFor({ state: 'visible', timeout: 10_000 });
  const activateMutation = page.waitForResponse((response) => response.request().method() === 'PUT'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}/status`);
  const activateCanonical = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${targetId}` && response.status() === 200);
  await activateDialog.getByRole('button', { name: '변경 확인', exact: true }).click();
  await Promise.all([activateMutation, activateCanonical]);

  const deleteId = accounts.weekAfter.me.id;
  await page.goto(`${frontendUrl}/admin/users/${deleteId}`, { waitUntil: 'domcontentloaded' });
  await waitForAdminDetail(page);
  await page.getByRole('button', { name: '계정 삭제', exact: true }).click();
  const deleteDialog = page.getByRole('dialog', { name: '계정 삭제 확인', exact: true });
  await deleteDialog.waitFor({ state: 'visible', timeout: 10_000 });
  check('browser-delete-copy', await deleteDialog.getByText('계정 접근이 차단되고 블로그와 게시글이 공개되지 않습니다. 데이터는 보존됩니다.', { exact: true }).count() === 1,
    'delete confirmation uses the approved soft-delete copy');
  const deleteMutation = page.waitForResponse((response) => response.request().method() === 'DELETE'
    && new URL(response.url()).pathname === `/api/v1/admin/users/${deleteId}`);
  await deleteDialog.getByRole('button', { name: '삭제 확인', exact: true }).click();
  const deleteResponse = await deleteMutation;
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15_000 });
  const deletedSearch = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
  const deleteListResponsePromise = page.waitForResponse((response) => response.request().method() === 'GET'
    && new URL(response.url()).pathname === '/api/v1/admin/users' && response.status() === 200, { timeout: 15_000 });
  await deletedSearch.fill(accounts.weekAfter.me.nickname);
  await deletedSearch.press('Enter');
  const deleteListResponse = await deleteListResponsePromise.catch(() => null);
  await page.waitForTimeout(250);
  check('browser-delete-confirm-canonical', [200, 204].includes(deleteResponse.status()) && page.url().endsWith('/admin')
    && deleteListResponse?.status() === 200
    && await page.getByText(accounts.weekAfter.me.nickname, { exact: true }).count() === 0,
  'delete confirmation returns to canonical list and exact search proves the deleted row is absent');
}

async function runBrowserStaleProbe(context, accounts) {
  void context;
  const listOk = await runBrowserStaleListProbe(accounts);
  const detailOk = await runBrowserStaleDetailProbe(accounts);
  const mutationOk = await runBrowserStaleMutationProbe(accounts);
  check('browser-account-switch-stale', listOk && detailOk && mutationOk,
    'all independent same-Provider list/detail/mutation stale probes reject the prior account response');
}

async function enterAdminViaProfileMenu(page, account) {
  const profile = page.getByRole('link', { name: account.me.nickname, exact: true });
  await profile.waitFor({ state: 'visible', timeout: 15_000 });
  await profile.click();
  const adminMenu = page.getByRole('menuitem', { name: '관리자', exact: true });
  await adminMenu.waitFor({ state: 'visible', timeout: 15_000 });
  await adminMenu.click();
  await page.waitForURL((url) => url.pathname === '/admin', { timeout: 15_000 });
}

async function runBrowserStaleListProbe(accounts) {
  const probeContext = await newBrowserContext();
  const page = await probeContext.newPage();
  let holder;
  let armed = false;
  try {
    holder = await holdAdminResponse(page, (request) => request.method() === 'GET'
      && new URL(request.url()).pathname === '/api/v1/admin/users'
      && new URL(request.url()).searchParams.get('size') === '20'
      && armed);
    await signInInSpa(page, accounts.adminKeeper);
    await enterAdminViaProfileMenu(page, accounts.adminKeeper);
    await waitForAdminReady(page);
    const search = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
    await search.fill(fixture.searchLiteral);
    await search.press('Enter');
    await adminUserRow(page, accounts.special.me.email).first().waitFor({ state: 'visible', timeout: 15_000 });
    const providerMarker = await markProviderDocument(page);
    armed = true;
    await search.fill(fixture.bulkSearchLiteral);
    await search.press('Enter');
    const upstream = await withTimeout(holder.readyPromise, STALE_PROBE_TIMEOUT_MS, 'stale list upstream response');
    check('browser-stale-list-upstream-200', upstream.status === 200 && upstream.bodyBytes > 0, 'held list response is fetched from the real upstream before account expiry');
    check('browser-stale-list-pending-sensitive', await exactCount(adminUserRow(page, accounts.adminKeeper.email)) === 0,
      'held list response does not expose the sensitive A row before release');
    await triggerAuthExpiry(page, holder);
    await signInExistingSpa(page, accounts.special);
    await waitForUserReady(page, accounts.special);
    const bStable = new URL(page.url()).pathname === '/'
      && await page.getByRole('link', { name: accounts.special.me.nickname, exact: true }).count() === 1;
    const sameDocument = await sameProviderDocument(page, providerMarker);
    check('browser-stale-list-b-session-stable', bStable && sameDocument, 'B is stably authenticated on the same SPA Provider/document before A release');
    holder.release();
    await withTimeout(holder.released, STALE_PROBE_TIMEOUT_MS, 'stale list release');
    await page.waitForTimeout(300);
    const ok = bStable && sameDocument && new URL(page.url()).pathname === '/'
      && await exactCount(adminUserRow(page, accounts.adminKeeper.email)) === 0;
    check('browser-stale-list', ok,
    'late A list 200 cannot repopulate the same SPA Provider after B is ready');
    return ok;
  } catch (error) {
    check('browser-stale-list', false, scrubSecrets(error instanceof Error ? error.message : String(error)));
    return false;
  } finally {
    holder?.release();
    await holder?.dispose();
    await probeContext.close();
  }
}

async function runBrowserStaleDetailProbe(accounts) {
  const probeContext = await newBrowserContext();
  const page = await probeContext.newPage();
  let holder;
  let armed = false;
  try {
    holder = await holdAdminResponse(page, (request) => request.method() === 'GET'
      && new URL(request.url()).pathname === `/api/v1/admin/users/${accounts.user.me.id}`
      && armed);
    await signInInSpa(page, accounts.adminKeeper);
    await enterAdminViaProfileMenu(page, accounts.adminKeeper);
    await page.goto(`${frontendUrl}/admin?page=0&size=100`, { waitUntil: 'domcontentloaded' });
    await waitForAdminReady(page, accounts.adminKeeper.me.nickname);
    const providerMarker = await markProviderDocument(page);
    armed = true;
    const targetRow = page.locator('tr').filter({ hasText: accounts.user.me.nickname }).first();
    await targetRow.getByRole('link', { name: '관리', exact: true }).click();
    const upstream = await withTimeout(holder.readyPromise, STALE_PROBE_TIMEOUT_MS, 'stale detail upstream response');
    check('browser-stale-detail-upstream-200', upstream.status === 200 && upstream.bodyBytes > 0, 'held detail response is fetched from the real upstream before account expiry');
    check('browser-stale-detail-pending-sensitive', await page.getByText(accounts.user.me.email, { exact: true }).count() === 0,
      'held detail response does not expose target details before release');
    await triggerAuthExpiry(page, holder);
    await signInExistingSpa(page, accounts.special);
    await waitForUserReady(page, accounts.special);
    const bStable = new URL(page.url()).pathname === '/'
      && await page.getByRole('link', { name: accounts.special.me.nickname, exact: true }).count() === 1;
    const sameDocument = await sameProviderDocument(page, providerMarker);
    check('browser-stale-detail-b-session-stable', bStable && sameDocument, 'B is stably authenticated on the same SPA Provider/document before A release');
    holder.release();
    await withTimeout(holder.released, STALE_PROBE_TIMEOUT_MS, 'stale detail release');
    await page.waitForTimeout(300);
    const ok = bStable && sameDocument && new URL(page.url()).pathname === '/'
      && await exactCount(adminUserRow(page, accounts.user.me.email)) === 0;
    check('browser-stale-detail', ok,
    'late A detail 200 cannot repopulate the same SPA Provider after B is ready');
    return ok;
  } catch (error) {
    check('browser-stale-detail', false, scrubSecrets(error instanceof Error ? error.message : String(error)));
    return false;
  } finally {
    holder?.release();
    await holder?.dispose();
    await probeContext.close();
  }
}

async function runBrowserStaleMutationProbe(accounts) {
  const probeContext = await newBrowserContext();
  const page = await probeContext.newPage();
  let holder;
  try {
    await signInInSpa(page, accounts.adminKeeper);
    await page.goto(`${frontendUrl}/admin/users/${accounts.user.me.id}`, { waitUntil: 'domcontentloaded' });
    await waitForAdminDetail(page);
    const providerMarker = await markProviderDocument(page);
    holder = await holdAdminResponse(page, (request) => request.method() === 'PUT'
      && new URL(request.url()).pathname === `/api/v1/admin/users/${accounts.user.me.id}/role`);
    const role = page.getByRole('combobox', { name: '역할', exact: true });
    await role.selectOption('ADMIN');
    await page.getByRole('button', { name: '역할 변경', exact: true }).click();
    const roleDialog = page.getByRole('dialog', { name: '역할 변경 확인', exact: true });
    await roleDialog.waitFor({ state: 'visible', timeout: 10_000 });
    await roleDialog.getByRole('button', { name: '변경 확인', exact: true }).click();
    const upstream = await withTimeout(holder.readyPromise, STALE_PROBE_TIMEOUT_MS, 'stale mutation upstream response');
    check('browser-stale-mutation-upstream-200', upstream.status === 200 && upstream.bodyBytes > 0, 'held mutation response is fetched from the real upstream before account expiry');
    await triggerAuthExpiry(page, holder);
    await signInExistingSpa(page, accounts.special);
    await waitForUserReady(page, accounts.special);
    const bStable = new URL(page.url()).pathname === '/'
      && await page.getByRole('link', { name: accounts.special.me.nickname, exact: true }).count() === 1;
    const sameDocument = await sameProviderDocument(page, providerMarker);
    check('browser-stale-mutation-b-session-stable', bStable && sameDocument, 'B is stably authenticated on the same SPA Provider/document before A release');
    holder.release();
    await withTimeout(holder.released, STALE_PROBE_TIMEOUT_MS, 'stale mutation release');
    await page.waitForTimeout(300);
    const ok = bStable && sameDocument && new URL(page.url()).pathname === '/'
      && await exactCount(adminUserRow(page, accounts.user.me.email)) === 0;
    check('browser-stale-mutation', ok,
    'late A mutation 200 cannot navigate or restore detail after B is ready');
    return ok;
  } catch (error) {
    check('browser-stale-mutation', false, scrubSecrets(error instanceof Error ? error.message : String(error)));
    return false;
  } finally {
    await apiCall('PUT', `/api/v1/admin/users/${accounts.user.me.id}/role`, accounts.adminKeeper.token, { role: 'USER' }).catch(() => undefined);
    holder?.release();
    await holder?.dispose();
    await probeContext.close();
  }
}

async function holdAdminResponse(page, predicate) {
  let held = null;
  let expireNext = false;
  let expiryConsumed = false;
  let released = false;
  let resolveReady;
  let releaseResolve;
  let resolveReleased;
  const readyPromise = new Promise((resolvePromise) => { resolveReady = resolvePromise; });
  const releasePromise = new Promise((resolvePromise) => { releaseResolve = resolvePromise; });
  const releasedPromise = new Promise((resolvePromise) => { resolveReleased = resolvePromise; });
  const pattern = ADMIN_USERS_ROUTE_PATTERN;
  await page.route(pattern, async (route) => {
    const url = new URL(route.request().url());
    if (expireNext && !expiryConsumed && route.request().method() === 'GET' && url.searchParams.get('size') === '1') {
      expiryConsumed = true;
      await route.fulfill({ status: 401, contentType: 'application/json', headers: { 'cache-control': 'private, no-store' },
        body: JSON.stringify({ success: false, data: null, error: { code: 'AUTH_004', message: 'expired synthetic access' }, timestamp: new Date().toISOString() }) });
      return;
    }
    if (held || !predicate(route.request())) {
      await route.continue();
      return;
    }
    const upstream = await route.fetch();
    const body = await upstream.body();
    held = {
      status: upstream.status(),
      headers: upstream.headers(),
      body,
      bodyBytes: body.length,
      readyPromise,
      release() { releaseResolve(); },
    };
    resolveReady({ status: upstream.status(), bodyBytes: body.length });
    await releasePromise;
    await route.fulfill({ status: held.status, headers: held.headers, body: held.body });
    if (!released) {
      released = true;
      resolveReleased();
    }
  });
  return {
    get readyPromise() { return readyPromise; },
    get released() { return releasedPromise; },
    release() { releaseResolve(); },
    expireOnce() { expireNext = true; },
    async dispose() { await page.unroute(pattern).catch(() => undefined); },
  };
}

async function triggerAuthExpiry(page, holder) {
  await page.route('**/api/v1/auth/refresh', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', headers: { 'cache-control': 'private, no-store' },
      body: JSON.stringify({ success: false, data: null, error: { code: 'AUTH_003', message: 'expired synthetic refresh' }, timestamp: new Date().toISOString() }) });
  });
  holder.expireOnce();
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await page.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
  check('browser-auth-final-failure-boundary', page.url().endsWith('/signin'), 'same SPA auth-expiry path reaches signin after access and refresh fail');
  await page.unroute('**/api/v1/auth/refresh');
}

async function markProviderDocument(page) {
  return page.evaluate(() => {
    const key = '__m9ProviderDocumentMarker';
    const marker = window[key] ?? `${Date.now()}-${Math.random()}`;
    window[key] = marker;
    return marker;
  });
}

async function sameProviderDocument(page, marker) {
  return page.evaluate((expected) => window.__m9ProviderDocumentMarker === expected, marker).catch(() => false);
}

function withTimeout(promise, timeoutMs, label) {
  let timeout;
  const timeoutPromise = new Promise((_, reject) => {
    timeout = setTimeout(() => reject(new Error(`${label} timed out after ${timeoutMs}ms`)), timeoutMs);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => clearTimeout(timeout));
}

async function signInInSpa(page, account) {
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await submitSigninForm(page, account);
}

async function signInExistingSpa(page, account) {
  await page.waitForURL((url) => url.pathname.endsWith('/signin'), { timeout: 15_000 }).catch(() => undefined);
  await submitSigninForm(page, account);
  // Signin preserves the protected `from` pathname. For the USER stale probe
  // that is /admin, so leave the denied route through the real SPA logo link
  // while retaining the same document/Provider instance.
  if (new URL(page.url()).pathname.startsWith('/admin')) {
    const homeLink = page.getByRole('link', { name: 'ZEROVERSE', exact: true });
    await homeLink.waitFor({ state: 'visible', timeout: 15_000 });
    await homeLink.click();
    await page.waitForURL((url) => url.pathname === '/', { timeout: 15_000 });
  }
}

async function submitSigninForm(page, account) {
  const email = page.getByLabel('이메일', { exact: true });
  const password = page.getByLabel('비밀번호', { exact: true });
  const submit = page.getByRole('button', { name: '접속하기 ✦', exact: true });
  await email.waitFor({ state: 'visible', timeout: 15_000 });
  await password.waitFor({ state: 'visible', timeout: 15_000 });
  await submit.waitFor({ state: 'visible', timeout: 15_000 });
  check(`browser-signin-${account.me?.id ?? 'fixture'}`, await exactCount(email) === 1 && await exactCount(password) === 1 && await exactCount(submit) === 1, 'signin controls are unique');
  if (await exactCount(email) !== 1 || await exactCount(password) !== 1 || await exactCount(submit) !== 1) throw new Error('signin controls are not unique');
  await email.fill(account.email);
  await password.fill(account.password);
  await submit.click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 15_000 });
}

async function waitForUserReady(page, account) {
  await page.getByRole('link', { name: account.me.nickname, exact: true })
    .waitFor({ state: 'visible', timeout: 15_000 });
}

function adminUserTable(page) {
  return page.getByRole('table', { name: '관리자 사용자 목록', exact: true });
}

function adminUserRow(page, marker) {
  return adminUserTable(page).getByRole('row').filter({ hasText: marker });
}

async function waitForAdminReady(page, expectedNickname) {
  const section = page.locator('section[aria-label="관리자 콘솔"]');
  await section.waitFor({ state: 'visible', timeout: 15_000 });
  await page.getByText('ADMIN CONSOLE', { exact: true }).waitFor({ state: 'visible', timeout: 15_000 });
  const search = page.getByRole('searchbox', { name: '관리자 사용자 검색', exact: true });
  await search.waitFor({ state: 'visible', timeout: 15_000 });
  if (expectedNickname) {
    await adminUserRow(page, expectedNickname).first().waitFor({ state: 'visible', timeout: 15_000 });
  }
  const busy = page.locator('[aria-busy="true"]');
  if (await busy.count() > 0) await busy.first().waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => undefined);
}

async function waitForAdminDetail(page) {
  await page.locator('section[aria-label="사용자 상세"]').waitFor({ state: 'visible', timeout: 15_000 });
}

async function contentWidth(page, kind) {
  void kind;
  const locator = page.locator('[data-layout="app"]');
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
    errorMessage: json?.error?.message ?? null,
    envelope: Boolean(json && typeof json.success === 'boolean' && 'data' in json && 'error' in json && 'timestamp' in json),
  };
}

async function createRefreshProbe(account, label) {
  const context = await requestApi.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });
  const response = await context.fetch('/api/v1/auth/signin', {
    method: 'POST',
    data: { email: account.email, password: account.password },
  });
  const json = await safeJson(response);
  const ok = response.status() === 200 && typeof json?.data?.accessToken === 'string';
  check(`refresh-probe-${label}`, ok, 'refresh probe signed in with credentials held only in memory');
  if (!ok) {
    await context.dispose();
    return null;
  }
  return context;
}

async function refreshFromProbe(context) {
  const response = await context.fetch('/api/v1/auth/refresh', { method: 'POST' });
  const json = await safeJson(response);
  return {
    status: response.status(),
    errorCode: json?.error?.code ?? null,
    envelope: Boolean(json && typeof json.success === 'boolean' && 'data' in json && 'error' in json && 'timestamp' in json),
    cacheControl: response.headers()['cache-control'] ?? '',
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

function setsEqual(left, right) {
  return left instanceof Set && right instanceof Set && left.size === right.size
    && [...left].every((value) => right.has(value));
}

function arrayEqual(left, right) {
  return Array.isArray(left) && Array.isArray(right) && left.length === right.length
    && left.every((value, index) => value === right[index]);
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
  ensureRequiredEvidenceChecks();
  const requiredNotRun = REQUIRED_EVIDENCE_IDS.filter((id) => evidence.checks.some((item) => item.name === id && item.status === 'NOT RUN'));
  const requiredFailed = REQUIRED_EVIDENCE_IDS.filter((id) => evidence.checks.some((item) => item.name === id && item.status === 'FAIL'));
  // M9_REQUIRE_FULL may relax process exit handling, but it never turns an
  // incomplete required matrix into a top-level PASS.
  evidence.status = evidence.failures.length === 0 && requiredNotRun.length === 0 && requiredFailed.length === 0
    ? 'PASS' : 'NOT RUN';
  evidence.requiredNotRun = requiredNotRun;
  evidence.requiredFailed = requiredFailed;
  await writeEvidence();
  const pass = evidence.checks.filter((item) => item.status === 'PASS').length;
  const notRunCount = evidence.checks.filter((item) => item.status === 'NOT RUN').length;
  console.log(JSON.stringify({ outputDir, screenshots: evidence.screenshots.length, pass, notRun: notRunCount, failures: evidence.failures.length, fixtureIds: evidence.fixture }));
  if (evidence.failures.length > 0) throw new Error(`M9 acceptance has ${evidence.failures.length} findings`);
}

function ensureRequiredEvidenceChecks() {
  const present = new Set(evidence.checks.map((item) => item.name));
  const missing = REQUIRED_EVIDENCE_IDS.filter((id) => !present.has(id));
  for (const id of missing) {
    notRun(id, 'required matrix evidence was not reached; fail-closed until the independent runtime path executes');
  }
  if (missing.length === 0) {
    const existing = evidence.checks.find((item) => item.name === 'required-matrix-complete');
    if (!existing) check('required-matrix-complete', true, 'all required matrix evidence IDs were reached');
  }
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
  result = result.replaceAll(fixture.searchEmailLiteral, '[redacted-email-marker]');
  result = result.replaceAll(fixture.searchNicknameLiteral, '[redacted-nickname-marker]');
  result = result.replaceAll(fixture.bulkSearchLiteral, '[redacted-bulk-marker]');
  result = result.replace(/Bearer\s+\S+/gi, 'Bearer [redacted-token]');
  result = result.replace(/[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '[redacted-token]');
  return result;
}
