/**
 * M8 알림 독립 실제 인수 runner.
 *
 * QA 소유 black-box 도구다. 합성 계정·블로그·게시글·댓글·좋아요·관계만
 * HTTP로 만들고 access token/password/email/본문 marker는 memory에 둔다.
 * evidence와 fixture seed에는 합성 ID·check 결과·screenshot 파일명만 남긴다.
 * 사용자 DB, 기존 M5~M7 DB, .env.local, .local-data에는 접근하지 않는다.
 *
 * 기본 runtime:
 *   M8_API_BASE_URL=http://localhost:18088
 *   M8_FRONTEND_URL=http://localhost:5178
 *   M8_PLAYWRIGHT_MODULE=/path/to/playwright/node_modules/playwright
 *   M8_RUNTIME_SQL_HELPER=build/m8-runtime.py
 *
 * root runner는 fixture-seed.json이 생성된 뒤 합성 DB에서만
 * SUSPENDED/deleted user와 oldNotificationIds의 41일 timestamp를 적용하고
 * status-ready.json을 만든다. 이 파일은 SQL을 변경하지 않고 read-only
 * COUNT/fingerprint 관측만 build/m8-runtime.py sql 경로로 수행한다.
 *
 * 실행 전 immutable JAR/API/FE/source manifest가 준비되지 않았으면 호출하지
 * 않는다. 호출되더라도 readiness/hash/status gate가 없으면 PASS로 완화하지
 * 않고 evidence와 exit 1을 남긴다.
 */

import { createRequire } from 'node:module';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M8_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium, request: requestApi } = require(playwrightModule);

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const apiBaseUrl = process.env.M8_API_BASE_URL ?? 'http://localhost:18088';
const frontendUrl = process.env.M8_FRONTEND_URL ?? 'http://localhost:5178';
const chromeExecutable = process.env.M8_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const designPagesFile = resolve(root, process.env.M8_DESIGN_PAGES_FILE ?? 'docs/design/ZeroVerse Pages.dc.html');
const designMainFile = resolve(root, process.env.M8_DESIGN_MAIN_FILE ?? 'docs/design/ZeroVerse Main Feed v2.dc.html');
const outputDir = resolve(root, process.env.M8_OUTPUT_DIR ?? `build/m8-acceptance-${Date.now().toString(36)}`);
const seedFile = resolve(root, process.env.M8_FIXTURE_SEED_FILE ?? `${outputDir}/fixture-seed.json`);
const statusReadyFile = resolve(root, process.env.M8_STATUS_READY_FILE ?? `${outputDir}/status-ready.json`);
const versionManifestFile = resolve(root, process.env.M8_VERSION_MANIFEST ?? 'build/m8-evidence/tested-version.json');
const runtimeSqlHelper = resolve(root, process.env.M8_RUNTIME_SQL_HELPER ?? 'build/m8-runtime.py');
const requireFull = process.env.M8_REQUIRE_FULL !== '0';
const requireStatusFixtures = process.env.M8_REQUIRE_STATUS_FIXTURES !== '0';
const waitForStatusFixturesFlag = process.env.M8_WAIT_FOR_STATUS_FIXTURES === '1';
const statusWaitMs = Number(process.env.M8_STATUS_WAIT_MS ?? 180_000);
const screenshotsEnabled = process.env.M8_SCREENSHOTS !== '0';
const suffix = `${Date.now().toString(36)}${randomBytes(5).toString('hex')}`;

const fixture = makeFixtureCredentials(suffix);
const evidence = {
  milestone: 'M8',
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  apiBaseUrl,
  frontendUrl,
  fixture: {
    accountIds: {},
    blogIds: {},
    postIds: {},
    commentIds: {},
    notificationIds: {},
    relationshipLabels: [],
    statusFixtureReady: false,
  },
  version: null,
  performance: {},
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
    headless: process.env.M8_HEADLESS !== '0',
    executablePath: chromeExecutable,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  const accounts = {};
  for (const [label, account] of Object.entries(fixture.accounts)) {
    accounts[label] = await registerAndSetup(account, label);
  }
  const posts = await createFixturePosts(accounts);
  await createRelationships(accounts);
  const events = await seedNotificationEvents(accounts, posts);
  await writeFixtureSeed(accounts, posts, events);

  const statusReady = await waitForStatusFixtures(accounts, posts, events);
  evidence.fixture.statusFixtureReady = statusReady;
  if (!statusReady && requireStatusFixtures) throw new Error('synthetic M8 status/timestamp fixtures are not ready');

  await runHttpAcceptance(accounts, posts, events);
  await runBrowserAcceptance(accounts, posts, events);
  await finishEvidence();
} catch (error) {
  const safeMessage = scrubSecrets(error instanceof Error ? error.message : String(error));
  evidence.failures.push(`runner: ${safeMessage}`);
  await writeEvidence();
  throw new Error(`M8 acceptance failed: ${safeMessage}`);
} finally {
  await apiContext?.dispose();
  await browser?.close();
}

function makeFixtureCredentials(runSuffix) {
  const key = runSuffix.slice(-8).toLowerCase();
  const marker = `m8${key}`;
  const password = `M8!Qa${runSuffix}7`;
  const labels = ['owner', 'actor', 'parent', 'friend', 'blocked', 'suspended', 'deleted', 'unrelated'];
  const accounts = Object.fromEntries(labels.map((label) => {
    const nickPrefix = label === 'owner' ? 'ow' : label.slice(0, 2).toLowerCase();
    return [label, {
      email: `m8-${label}-${runSuffix}@example.test`,
      password,
      name: `M8 QA ${label}`,
      nickname: `${marker}${nickPrefix}`.slice(0, 20),
      birthDate: '1990-01-01',
      blogTitle: `M8 ${label} blog ${key}`,
      blogSlug: `m8-${label}-${key}`.toLowerCase().slice(0, 30),
    }];
  }));
  return {
    accounts,
    marker,
    pageMarker: `m8page${key}`,
    password,
  };
}

async function verifyApiReadiness() {
  const response = await apiContext.fetch('/v3/api-docs', { method: 'GET' });
  const json = await safeJson(response);
  const paths = json?.paths ?? {};
  const expected = [
    '/api/v1/notifications',
    '/api/v1/notifications/{notificationId}/read',
    '/api/v1/notifications/read-all',
    '/api/v1/notifications/{notificationId}',
  ];
  check('runtime-api-openapi', response.status() === 200, 'backend /v3/api-docs is reachable');
  check(
    'openapi-m8-paths',
    response.status() === 200 && expected.every((path) => Object.prototype.hasOwnProperty.call(paths, path)),
    'OpenAPI exposes M8 notification list/read/read-all/delete paths',
  );
  if (response.status() !== 200 || !expected.every((path) => Object.prototype.hasOwnProperty.call(paths, path))) {
    throw new Error('backend runtime is not ready for M8 notification endpoints');
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
    description: `M8 synthetic ${label} blog`,
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
    owner: {},
    actor: {},
    parent: {},
    friend: {},
    blocked: {},
    suspended: {},
    deleted: {},
    unrelated: {},
  };
  posts.owner.public = await createPost(accounts.owner, 'owner public', 'PUBLIC', true, 'owner-public');
  posts.owner.universe = await createPost(accounts.owner, 'owner universe', 'UNIVERSE', true, 'owner-universe');
  posts.owner.private = await createPost(accounts.owner, 'owner private', 'PRIVATE', true, 'owner-private');
  posts.owner.draft = await createPost(accounts.owner, 'owner draft', 'PRIVATE', false, 'owner-draft');
  posts.owner.transition = await createPost(accounts.owner, 'owner transition', 'PRIVATE', true, 'owner-transition');
  for (const label of ['actor', 'parent', 'friend', 'blocked', 'suspended', 'deleted', 'unrelated']) {
    posts[label].public = await createPost(accounts[label], `${label} public`, 'PUBLIC', true, `${label}-public`);
  }
  for (const [label, group] of Object.entries(posts)) {
    evidence.fixture.postIds[label] = Object.fromEntries(Object.entries(group).map(([key, value]) => [key, value.id]));
  }
  return posts;
}

async function createPost(account, label, visibility, publish, fixtureLabel) {
  const text = `${fixture.marker} ${label} ${fixtureLabel}`;
  const response = await apiCall('POST', '/api/v1/posts', account.token, {
    blogId: account.blog.id,
    title: `M8 ${label} ${fixture.marker}`,
    contentJson: plainContent(text),
    contentHtml: `<p>${text}</p>`,
    categoryId: null,
    visibility,
    publish,
    thumbnailUrl: null,
    tagNames: [],
    images: [],
  });
  check(`fixture-${fixtureLabel}`, response.status === 201 && Number.isInteger(response.data?.id), 'synthetic post created');
  if (response.status !== 201 || !Number.isInteger(response.data?.id)) throw new Error(`fixture post ${fixtureLabel} failed`);
  return response.data;
}

function plainContent(text) {
  return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] };
}

async function createRelationships(accounts) {
  await acceptRelationship(accounts.friend, accounts.owner, 'friend-owner');
  await acceptRelationship(accounts.actor, accounts.owner, 'actor-owner');
  await acceptRelationship(accounts.parent, accounts.owner, 'parent-owner');
  await acceptRelationship(accounts.blocked, accounts.owner, 'blocked-owner');
  evidence.fixture.relationshipLabels.push(
    'friend→owner:accepted',
    'actor→owner:accepted',
    'parent→owner:accepted',
    'blocked→owner:accepted-before-block',
  );
}

async function acceptRelationship(from, to, label) {
  const request = await apiCall('POST', `/api/v1/universe/requests/${to.me.id}`, from.token);
  check(`fixture-${label}-request`, request.status === 201 && Number.isInteger(request.data?.requestId), 'universe request created');
  if (request.status !== 201) throw new Error(`fixture relation ${label} request failed`);
  let requestId = request.data?.requestId;
  if (!Number.isInteger(requestId)) {
    const received = await apiCall('GET', '/api/v1/universe/requests/received?page=0&size=100', to.token);
    requestId = received.data?.items?.find((entry) => Number(entry.user?.id) === Number(from.me.id))?.requestId;
  }
  if (!Number.isInteger(requestId)) throw new Error(`fixture relation ${label} request ID missing`);
  const accepted = await apiCall('POST', `/api/v1/universe/requests/${requestId}/accept`, to.token);
  check(`fixture-${label}-accept`, accepted.status === 200, 'universe request accepted');
  if (accepted.status !== 200) throw new Error(`fixture relation ${label} accept failed`);
  return requestId;
}

async function createComment(account, post, content, parentId = null) {
  const response = await apiCall('POST', `/api/v1/posts/${post.id}/comments`, account.token, {
    content,
    parentId,
  });
  check(`fixture-comment-${account.me.id}-${parentId ?? 'root'}`, response.status === 201 && Number.isInteger(response.data?.id), 'synthetic comment created');
  if (response.status !== 201 || !Number.isInteger(response.data?.id)) throw new Error('fixture comment failed');
  return response.data;
}

async function likePost(account, post) {
  const response = await apiCall('POST', `/api/v1/posts/${post.id}/likes`, account.token);
  check(`fixture-like-${account.me.id}-${post.id}`, response.status === 200, 'synthetic like request succeeded');
  if (response.status !== 200) throw new Error('fixture like failed');
  return response.data;
}

async function unlikePost(account, post) {
  const response = await apiCall('DELETE', `/api/v1/posts/${post.id}/likes`, account.token);
  check(`fixture-unlike-${account.me.id}-${post.id}`, response.status === 200, 'synthetic unlike request succeeded');
  if (response.status !== 200) throw new Error('fixture unlike failed');
  return response.data;
}

async function updatePost(account, post, patch) {
  const request = {
    title: patch.title ?? post.title,
    contentJson: patch.contentJson ?? post.contentJson,
    contentHtml: patch.contentHtml ?? post.contentHtml,
    categoryId: post.category?.id ?? null,
    visibility: patch.visibility ?? post.visibility,
    publish: patch.publish ?? (post.publishedAt != null),
    thumbnailUrl: post.thumbnailUrl ?? null,
    tagNames: Array.isArray(post.tags) ? post.tags.map((tag) => typeof tag === 'string' ? tag : tag.name).filter(Boolean) : [],
    images: Array.isArray(post.images) ? post.images : [],
  };
  const response = await apiCall('PUT', `/api/v1/posts/${post.id}`, account.token, request);
  check(`fixture-update-${post.id}-${request.visibility}-${request.publish}`, response.status === 200, 'synthetic post transition succeeded');
  if (response.status !== 200) throw new Error(`fixture post update ${post.id} failed`);
  Object.assign(post, response.data);
  return response.data;
}

async function seedNotificationEvents(accounts, posts) {
  const events = { commentIds: {}, notificationIds: {}, transitionPostId: posts.owner.transition.id };
  const selfRoot = await createComment(accounts.owner, posts.owner.public, `${fixture.marker} self root`);
  const sameRecipientReply = await createComment(accounts.actor, posts.owner.public, `${fixture.marker} same recipient reply`, selfRoot.id);
  const parentRoot = await createComment(accounts.parent, posts.owner.public, `${fixture.marker} parent root`);
  const distinctReply = await createComment(accounts.actor, posts.owner.public, `${fixture.marker} distinct reply`, parentRoot.id);
  events.commentIds.selfRoot = selfRoot.id;
  events.commentIds.sameRecipientReply = sameRecipientReply.id;
  events.commentIds.parentRoot = parentRoot.id;
  events.commentIds.distinctReply = distinctReply.id;

  const likeBefore = await fetchAllNotifications(accounts.owner.token);
  await likePost(accounts.actor, posts.owner.public);
  const likeAfterFirst = await fetchAllNotifications(accounts.owner.token);
  await likePost(accounts.actor, posts.owner.public);
  const likeAfterRetry = await fetchAllNotifications(accounts.owner.token);
  await unlikePost(accounts.actor, posts.owner.public);
  const likeAfterUnlike = await fetchAllNotifications(accounts.owner.token);
  await likePost(accounts.actor, posts.owner.public);
  const likeAfterRelike = await fetchAllNotifications(accounts.owner.token);
  check('generation-like-first', countType(likeAfterFirst, 'LIKE', posts.owner.public.id) === countType(likeBefore, 'LIKE', posts.owner.public.id) + 1, 'first like creates one LIKE');
  check('generation-like-retry', countType(likeAfterRetry, 'LIKE', posts.owner.public.id) === countType(likeAfterFirst, 'LIKE', posts.owner.public.id), 'same active like retry creates no duplicate');
  check('generation-like-unlike-preserves', countType(likeAfterUnlike, 'LIKE', posts.owner.public.id) === countType(likeAfterRetry, 'LIKE', posts.owner.public.id), 'unlike preserves LIKE history');
  check('generation-like-re-like', countType(likeAfterRelike, 'LIKE', posts.owner.public.id) === countType(likeAfterUnlike, 'LIKE', posts.owner.public.id) + 1, 're-like creates a new LIKE event');

  const rootComments = [];
  for (let index = 1; index <= 25; index += 1) {
    rootComments.push(await createComment(accounts.actor, posts.owner.public, `${fixture.pageMarker} root ${index}`));
  }
  events.commentIds.pageRoots = rootComments.map((comment) => comment.id);

  const blockedComment = await createComment(accounts.blocked, posts.owner.public, `${fixture.marker} blocked actor`);
  const deletedActorComment = await createComment(accounts.deleted, posts.owner.public, `${fixture.marker} deleted actor`);
  const deletedComment = await createComment(accounts.actor, posts.owner.public, `${fixture.marker} deleted comment`);
  events.commentIds.blocked = blockedComment.id;
  events.commentIds.deletedActor = deletedActorComment.id;
  events.commentIds.deleted = deletedComment.id;

  const suspendedReceiverComment = await createComment(accounts.actor, posts.suspended.public, `${fixture.marker} suspended receiver`);
  events.commentIds.suspendedReceiver = suspendedReceiverComment.id;

  const parentRequest = await apiCall('POST', `/api/v1/universe/requests/${accounts.parent.me.id}`, accounts.actor.token);
  check('generation-neighbor-request', parentRequest.status === 201 && Number.isInteger(parentRequest.data?.requestId), 'NEIGHBOR request succeeds');
  if (parentRequest.status !== 201) throw new Error('NEIGHBOR request fixture failed');
  const firstRequestId = parentRequest.data.requestId;
  const parentCancel = await apiCall('DELETE', `/api/v1/universe/requests/${firstRequestId}`, accounts.actor.token);
  check('generation-neighbor-cancel', parentCancel.status === 200, 'NEIGHBOR request cancellation succeeds');
  const secondRequest = await apiCall('POST', `/api/v1/universe/requests/${accounts.parent.me.id}`, accounts.actor.token);
  check('generation-neighbor-rerequest', secondRequest.status === 201 && Number.isInteger(secondRequest.data?.requestId), 're-request succeeds as a new event');
  const secondRequestId = secondRequest.data?.requestId;
  if (!Number.isInteger(secondRequestId)) throw new Error('NEIGHBOR re-request ID missing');
  const parentAccept = await apiCall('POST', `/api/v1/universe/requests/${secondRequestId}/accept`, accounts.parent.token);
  check('generation-neighbor-accept', parentAccept.status === 200, 'NEIGHBOR request accept succeeds');
  events.relationshipIds = { parentFirstRequest: firstRequestId, parentSecondRequest: secondRequestId };

  const ownerRequest = await apiCall('POST', `/api/v1/universe/requests/${accounts.owner.me.id}`, accounts.unrelated.token);
  check('generation-owner-neighbor-request', ownerRequest.status === 201 && Number.isInteger(ownerRequest.data?.requestId), 'owner receives a NEIGHBOR request notification');
  const ownerRequestId = ownerRequest.data?.requestId;
  if (!Number.isInteger(ownerRequestId)) throw new Error('owner NEIGHBOR request ID missing');
  const ownerReject = await apiCall('POST', `/api/v1/universe/requests/${ownerRequestId}/reject`, accounts.owner.token);
  check('generation-owner-neighbor-reject', ownerReject.status === 200, 'owner rejects synthetic request after notification creation');
  events.relationshipIds.ownerRequest = ownerRequestId;

  const parentRowsAfterAccept = await fetchAllNotifications(accounts.parent.token);
  const relationNotification = parentRowsAfterAccept.find((item) => item.type === 'NEIGHBOR'
    && item.targetType === 'UNIVERSE'
    && Number(item.actor?.id) === Number(accounts.actor.me.id));
  events.relationshipNotificationId = relationNotification?.id ?? null;

  const friendBefore = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'PUBLIC', publish: true });
  const friendAfterDraftPublish = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, {
    title: `${fixture.marker} body edit`,
    contentJson: plainContent(`${fixture.marker} body edit`),
    contentHtml: `<p>${fixture.marker} body edit</p>`,
    visibility: 'PUBLIC',
    publish: true,
  });
  const friendAfterBodyEdit = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'UNIVERSE', publish: true });
  const friendAfterPublicUniverse = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'PUBLIC', publish: true });
  const friendAfterUniversePublic = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'PRIVATE', publish: true });
  const friendAfterPrivate = await fetchAllNotifications(accounts.friend.token);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'UNIVERSE', publish: true });
  const friendAfterPrivateUniverse = await fetchAllNotifications(accounts.friend.token);
  // targetId is intentionally masked while the friend loses access to the PRIVATE
  // post, so count stable notification IDs rather than the projected targetId.
  const transitionSnapshots = [
    friendBefore,
    friendAfterDraftPublish,
    friendAfterBodyEdit,
    friendAfterPublicUniverse,
    friendAfterUniversePublic,
    friendAfterPrivate,
    friendAfterPrivateUniverse,
  ].map((items) => new Set(items.filter((item) => item.type === 'POST').map((item) => Number(item.id))));
  const transitionCounts = transitionSnapshots.slice(1).map((current, index) => {
    const previous = transitionSnapshots[index];
    return [...current].filter((id) => !previous.has(id)).length;
  });
  const privateProjection = friendAfterPrivate.find((item) => item.type === 'POST' && item.available === false);
  check('generation-post-private-masking', Boolean(privateProjection) && unavailableShape(privateProjection),
    'POST target projection is masked while the friend cannot access the private post');
  check('generation-post-transition-table', transitionCounts.join(',') === '1,0,0,0,0,1',
    'POST is created only on eligible publication transitions using stable notification IDs');

  const ownerBeforeFinal = await fetchAllNotifications(accounts.owner.token);
  const ownerRows = ownerBeforeFinal.filter((item) => Number.isInteger(Number(item.id)));
  const blockedRow = ownerRows.find((item) => Number(item.targetId) === Number(blockedComment.id));
  const deletedActorRow = ownerRows.find((item) => Number(item.targetId) === Number(deletedActorComment.id));
  const deletedCommentRow = ownerRows.find((item) => Number(item.targetId) === Number(deletedComment.id));
  const likeRow = ownerRows.find((item) => item.type === 'LIKE' && Number(item.targetId) === Number(posts.owner.public.id));
  const oldRow = ownerRows.at(-1);
  const friendRows = await fetchAllNotifications(accounts.friend.token);
  const accessLossRow = friendRows.find((item) => item.type === 'POST' && Number(item.targetId) === Number(posts.owner.transition.id));
  const suspendedRows = await fetchAllNotifications(accounts.suspended.token);
  const suspendedReceiverRow = suspendedRows.find((item) => Number(item.targetId) === Number(suspendedReceiverComment.id));
  events.notificationIds = {
    blocked: blockedRow?.id ?? null,
    deletedActor: deletedActorRow?.id ?? null,
    deletedComment: deletedCommentRow?.id ?? null,
    like: likeRow?.id ?? null,
    accessLoss: accessLossRow?.id ?? null,
    suspendedReceiver: suspendedReceiverRow?.id ?? null,
    relationshipDeleted: events.relationshipNotificationId,
    readTarget: ownerRows.find((item) => item.id !== oldRow?.id && item.id !== blockedRow?.id)?.id ?? null,
    deleteTarget: ownerRows.find((item) => item.id !== oldRow?.id && item.id !== blockedRow?.id && item.id !== likeRow?.id)?.id ?? null,
  };
  events.oldNotificationIds = oldRow?.id ? [oldRow.id] : [];
  check('fixture-m8-notification-identifiers', Boolean(blockedRow && deletedActorRow && deletedCommentRow && likeRow
    && accessLossRow && suspendedReceiverRow && relationNotification && oldRow), 'seeded notification IDs can be handed off without credentials');
  if (!blockedRow || !deletedActorRow || !deletedCommentRow || !likeRow || !accessLossRow || !suspendedReceiverRow
    || !relationNotification || !oldRow) {
    throw new Error('M8 fixture notification identifiers are incomplete');
  }
  evidence.fixture.commentIds = events.commentIds;
  evidence.fixture.notificationIds = events.notificationIds;
  return events;
}

async function writeFixtureSeed(accounts, posts, events) {
  const seed = {
    milestone: 'M8',
    createdAt: new Date().toISOString(),
    accounts: Object.fromEntries(Object.entries(accounts).map(([label, account]) => [label, {
      userId: account.me.id,
      blogId: account.blog.id,
      postIds: Object.fromEntries(Object.entries(posts[label] ?? {}).map(([key, value]) => [key, value.id])),
    }])),
    comments: Object.fromEntries(Object.entries(events.commentIds).map(([key, value]) => [key, Array.isArray(value) ? value : value])),
    notifications: events.notificationIds,
    statusTargets: {
      suspendedUserId: accounts.suspended.me.id,
      deletedUserId: accounts.deleted.me.id,
      suspendedPostId: posts.suspended.public.id,
      deletedPostId: posts.deleted.public.id,
      ownerUserId: accounts.owner.me.id,
      oldNotificationIds: events.oldNotificationIds,
    },
    suspendedUserId: accounts.suspended.me.id,
    deletedUserId: accounts.deleted.me.id,
    suspendedPostId: posts.suspended.public.id,
    deletedPostId: posts.deleted.public.id,
    ownerUserId: accounts.owner.me.id,
    oldNotificationIds: events.oldNotificationIds,
  };
  await writeFile(seedFile, JSON.stringify(seed, null, 2));
}

async function waitForStatusFixtures(accounts, posts, events) {
  let ready = await readStatusReadyFile(accounts, posts, events);
  if (!ready && waitForStatusFixturesFlag) {
    const deadline = Date.now() + statusWaitMs;
    while (!ready && Date.now() < deadline) {
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000));
      ready = await readStatusReadyFile(accounts, posts, events);
    }
  }
  if (ready) {
    check('status-fixture-ready', true, 'synthetic suspended/deleted/old-history DB fixture acknowledged');
    return true;
  }
  if (requireStatusFixtures) notRun('status-fixture-ready', `root must mutate only synthetic IDs and create ${statusReadyFile}`);
  else notRun('status-fixture-ready', 'M8_REQUIRE_STATUS_FIXTURES=0');
  return false;
}

async function readStatusReadyFile(accounts, posts, events) {
  try {
    const json = JSON.parse(await readFile(statusReadyFile, 'utf8'));
    if (json?.ready !== true) return false;
    const expected = {
      suspendedUserId: accounts.suspended.me.id,
      deletedUserId: accounts.deleted.me.id,
      suspendedPostId: posts.suspended.public.id,
      deletedPostId: posts.deleted.public.id,
      ownerUserId: accounts.owner.me.id,
    };
    const exactIds = Object.entries(expected).every(([key, value]) => Number(json[key]) === Number(value));
    const oldExpected = [...events.oldNotificationIds].map(Number).sort((a, b) => a - b);
    const oldActual = Array.isArray(json.oldNotificationIds) ? json.oldNotificationIds.map(Number).sort((a, b) => a - b) : [];
    const updatedCount = Number(json.oldNotificationUpdatedCount);
    return exactIds && JSON.stringify(oldExpected) === JSON.stringify(oldActual)
      && Number.isInteger(updatedCount) && updatedCount === oldExpected.length;
  } catch {
    return false;
  }
}

async function runHttpAcceptance(accounts, posts, events) {
  const ownerAll = await listNotifications(accounts.owner.token, 'all', 0, 100);
  checkPage('http-owner-all', ownerAll, true);
  checkResponseHeaders('http-owner-all', ownerAll);
  const ownerDefaultRead = await apiCall('GET', '/api/v1/notifications?page=0&size=20', accounts.owner.token);
  check('http-owner-default-read-all', ownerDefaultRead.status === 200 && Number(ownerDefaultRead.data?.totalElements) === Number(ownerAll.data?.totalElements), 'omitted read defaults to all');
  check('http-owner-page-contract', pageShape(ownerAll) && Number.isInteger(ownerAll.data?.unreadCount) && typeof ownerAll.data?.canManage === 'boolean', 'NotificationPage exposes page and M8 fields');
  check('http-owner-history-over-40-days', hasId(ownerAll, events.oldNotificationIds[0]) && ageInDays(findItem(ownerAll, events.oldNotificationIds[0])?.createdAt) >= 40, '41-day synthetic history remains visible');
  const friendAllForTypes = await fetchAllNotifications(accounts.friend.token);
  const parentAllForTypes = await fetchAllNotifications(accounts.parent.token);
  const observedTypes = [...ownerAll.data.items, ...friendAllForTypes, ...parentAllForTypes];
  check('http-owner-types', ['COMMENT', 'REPLY', 'LIKE', 'NEIGHBOR', 'POST'].every((type) => observedTypes.some((item) => item.type === type)), 'all five notification types are observable across synthetic receivers');
  check('http-owner-sort', sortedNotifications(ownerAll.data.items), 'notifications sort by createdAt DESC,id DESC');

  const ownerPageOne = await listNotifications(accounts.owner.token, 'all', 1, 20);
  check('http-owner-21-plus-pagination', ownerAll.data.totalElements >= 21 && ownerPageOne.status === 200 && ownerPageOne.data.items.length > 0, '21+ notification rows reach page 2');
  const performanceBeforeSize = await syntheticSelectCount();
  const ownerSizeOne = await listNotifications(accounts.owner.token, 'all', 0, 1);
  const performanceAfterSizeOne = await syntheticSelectCount();
  check('http-owner-size-one', ownerSizeOne.status === 200 && ownerSizeOne.data.items.length <= 1, 'size=1 is honored');
  const ownerSize100 = await listNotifications(accounts.owner.token, 'all', 0, 100);
  const performanceAfterSize100 = await syntheticSelectCount();
  check('http-owner-size-100', ownerSize100.status === 200 && ownerSize100.data.items.length <= 100, 'size=100 is honored');
  if (performanceBeforeSize.available && performanceAfterSizeOne.available && performanceAfterSize100.available) {
    const deltaSizeOne = performanceAfterSizeOne.count - performanceBeforeSize.count;
    const deltaSize100 = performanceAfterSize100.count - performanceAfterSizeOne.count;
    const sizeCostDifference = deltaSize100 - deltaSizeOne;
    evidence.performance.notificationSelectQueryBound = {
      user: 'zeroverse_m8',
      sizeOneSelectDelta: deltaSizeOne,
      size100SelectDelta: deltaSize100,
      sizeCostDifference,
    };
    check('http-query-bound-actual-bearer', ownerSizeOne.status === 200 && ownerSize100.status === 200
      && deltaSizeOne > 0 && deltaSizeOne <= 14
      && deltaSize100 > 0 && deltaSize100 <= 14
      && sizeCostDifference === 0,
    'actual bearer size=1 and size=100 each use 1..14 SELECT statements with equal bounded cost');
  } else {
    evidence.performance.notificationSelectQueryBound = { available: false };
    notRun('http-query-bound-actual-bearer', 'performance_schema app-account SELECT counter was unavailable');
  }
  const ownerLargeOffset = await listNotifications(accounts.owner.token, 'all', 9999, 20);
  check('http-owner-large-offset-empty', ownerLargeOffset.status === 200 && ownerLargeOffset.data.items.length === 0 && Number(ownerLargeOffset.data.totalElements) === Number(ownerAll.data.totalElements), 'large offset returns normal empty page with total');

  const readUnread = await listNotifications(accounts.owner.token, 'unread', 0, 100);
  const readRead = await listNotifications(accounts.owner.token, 'read', 0, 100);
  check('http-owner-read-filters', readUnread.status === 200 && readRead.status === 200
    && readUnread.data.items.every((item) => item.readAt == null)
    && readRead.data.items.every((item) => item.readAt != null), 'read=unread/read filters match readAt');

  const readTarget = findItem(ownerAll, events.notificationIds.readTarget) ?? ownerAll.data.items[0];
  const readFirst = await apiCall('PUT', `/api/v1/notifications/${readTarget.id}/read`, accounts.owner.token);
  check('http-read-first', readFirst.status === 200 && readFirst.data?.id === readTarget.id && readFirst.data?.readAt != null, 'individual read returns canonical current NotificationItem');
  const firstReadAt = readFirst.data?.readAt;
  const readAgain = await apiCall('PUT', `/api/v1/notifications/${readTarget.id}/read`, accounts.owner.token);
  check('http-read-idempotent-preserves-timestamp', readAgain.status === 200 && readAgain.data?.readAt === firstReadAt, 'repeated read preserves first readAt');
  checkResponseHeaders('http-read-first', readFirst);

  const deleteTarget = findItem(ownerAll, events.notificationIds.deleteTarget) ?? ownerAll.data.items.at(-1);
  const deleteOne = await apiCall('DELETE', `/api/v1/notifications/${deleteTarget.id}`, accounts.owner.token);
  const deleteAgain = await apiCall('DELETE', `/api/v1/notifications/${deleteTarget.id}`, accounts.owner.token);
  const deletedRead = await apiCall('PUT', `/api/v1/notifications/${deleteTarget.id}/read`, accounts.owner.token);
  check('http-delete-soft-idempotent', deleteOne.status === 200 && deleteAgain.status === 200 && deletedRead.status === 404 && deletedRead.errorCode === 'NOT_001', 'owned delete is soft, repeated delete is 200, read is NOT_001');
  checkResponseHeaders('http-delete', deleteOne);

  const readAll = await apiCall('PUT', '/api/v1/notifications/read-all', accounts.owner.token);
  check('http-read-all', readAll.status === 200 && readAll.data == null, 'read-all returns empty success');
  checkResponseHeaders('http-read-all', readAll);
  const afterReadAll = await listNotifications(accounts.owner.token, 'all', 0, 100);
  check('http-read-all-server-count', afterReadAll.status === 200 && Number(afterReadAll.data.unreadCount) === 0, 'read-all count comes from server response');
  await createComment(accounts.actor, posts.owner.public, `${fixture.marker} post-read-all new event`);
  const afterNewInsert = await listNotifications(accounts.owner.token, 'unread', 0, 100);
  check('http-read-all-new-insert-remains-unread', afterNewInsert.status === 200 && afterNewInsert.data.items.length >= 1, 'notification inserted after read-all remains unread');

  const blockedBefore = await listNotifications(accounts.owner.token, 'all', 0, 100);
  await blockUser(accounts.owner, accounts.blocked);
  const blockedAfter = await listNotifications(accounts.owner.token, 'all', 0, 100);
  const blockedId = events.notificationIds.blocked;
  const blockedDirectRead = await apiCall('PUT', `/api/v1/notifications/${blockedId}/read`, accounts.owner.token);
  check('http-block-pre-page-count', !hasId(blockedAfter, blockedId) && Number(blockedAfter.data.unreadCount) <= Number(blockedBefore.data.unreadCount), 'blocked rows are hidden before page/count/unread');
  check('http-block-direct-read-hidden', blockedDirectRead.status === 404 && blockedDirectRead.errorCode === 'NOT_001', 'blocked notification direct read is NOT_001');
  checkResponseHeaders('http-block-direct-read-hidden', blockedDirectRead);
  await unblockUser(accounts.owner, accounts.blocked);
  const unblocked = await listNotifications(accounts.owner.token, 'all', 0, 100);
  const unblockedDirectRead = await apiCall('PUT', `/api/v1/notifications/${blockedId}/read`, accounts.owner.token);
  checkResponseHeaders('http-block-restore-read', unblockedDirectRead);
  check('http-block-restore', hasId(unblocked, blockedId)
    && unblockedDirectRead.status === 200 && unblockedDirectRead.data?.available === true,
  'unblocking restores the historical row and current read projection');

  const deletedActorItem = findItem(unblocked, events.notificationIds.deletedActor);
  const deletedCommentItem = findItem(unblocked, events.notificationIds.deletedComment);
  check('http-unavailable-actor-mask', unavailableShape(deletedActorItem), 'deleted actor rows use generic masking and null sensitive fields');

  const friendBeforeLoss = await listNotifications(accounts.friend.token, 'all', 0, 100);
  const accessLossId = events.notificationIds.accessLoss;
  const accessLossBefore = findItem(friendBeforeLoss, accessLossId);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'PRIVATE', publish: true });
  const friendMasked = await listNotifications(accounts.friend.token, 'all', 0, 100);
  const accessLossMasked = findItem(friendMasked, accessLossId);
  await updatePost(accounts.owner, posts.owner.transition, { visibility: 'UNIVERSE', publish: true });
  const friendRestored = await listNotifications(accounts.friend.token, 'all', 0, 100);
  const accessLossRestored = findItem(friendRestored, accessLossId);
  const accessLossRead = await apiCall('PUT', `/api/v1/notifications/${accessLossId}/read`, accounts.friend.token);
  checkResponseHeaders('http-content-acl-restore-read', accessLossRead);
  check('http-content-acl-mask-restore', accessLossBefore?.available !== false && unavailableShape(accessLossMasked)
    && accessLossRestored?.available === true && accessLossRestored.href
    && accessLossRead.status === 200 && accessLossRead.data?.available === true,
  'content ACL loss masks and recovery reprojects the same row and read projection');

  const ownerBeforeDeleteComment = await listNotifications(accounts.owner.token, 'all', 0, 100);
  const deleteComment = await apiCall('DELETE', `/api/v1/comments/${events.commentIds.deleted}`, accounts.actor.token);
  const ownerAfterDeleteComment = await listNotifications(accounts.owner.token, 'all', 0, 100);
  check('http-comment-delete-preserves-history', deleteComment.status === 200
    && unavailableShape(findItem(ownerAfterDeleteComment, events.notificationIds.deletedComment)), 'comment delete preserves an unavailable notification row');
  check('http-comment-delete-mask', deletedCommentItem?.available === true
    && unavailableShape(findItem(ownerAfterDeleteComment, events.notificationIds.deletedComment)),
  'comment notification becomes unavailable only after the source comment is deleted');

  const relationDelete = await apiCall('DELETE', `/api/v1/universe/${accounts.parent.me.id}`, accounts.actor.token);
  const parentAfterRelationDelete = await listNotifications(accounts.parent.token, 'all', 0, 100);
  const relationDeletedItem = findItem(parentAfterRelationDelete, events.notificationIds.relationshipDeleted);
  const relationDeletedRead = await apiCall('PUT', `/api/v1/notifications/${events.notificationIds.relationshipDeleted}/read`, accounts.parent.token);
  checkResponseHeaders('http-relationship-delete-read', relationDeletedRead);
  check('http-relationship-delete-preserves-unavailable', relationDelete.status === 200
    && relationDeletedItem?.targetType === 'UNIVERSE'
    && unavailableShape(relationDeletedItem)
    && relationDeletedRead.status === 200
    && unavailableShape(relationDeletedRead.data),
  'hard-deleted universe relation preserves a masked NEIGHBOR history row and read projection');

  const nonOwnerRead = await apiCall('PUT', `/api/v1/notifications/${readTarget.id}/read`, accounts.actor.token);
  const nonOwnerDelete = await apiCall('DELETE', `/api/v1/notifications/${readTarget.id}`, accounts.actor.token);
  check('http-owner-boundary', nonOwnerRead.status === 403 && nonOwnerRead.errorCode === 'NOT_002'
    && nonOwnerDelete.status === 403 && nonOwnerDelete.errorCode === 'NOT_002', 'other receiver cannot read/delete owner notification');

  await runAuthAndValidationMatrix(accounts, events, readTarget);

  const snapshotBeforeGet = await syntheticNotificationSnapshot(accounts.owner.me.id);
  await listNotifications(accounts.owner.token, 'all', 0, 20);
  const snapshotAfterGet = await syntheticNotificationSnapshot(accounts.owner.me.id);
  check('http-list-no-mutation', snapshotBeforeGet.available && snapshotAfterGet.available
    && snapshotBeforeGet.count === snapshotAfterGet.count && snapshotBeforeGet.fingerprint === snapshotAfterGet.fingerprint, 'notification GET/read projections do not mutate rows');
  const feed = await apiCall('GET', '/api/v1/feed/public?page=0&size=20&sort=latest');
  const search = await apiCall('GET', `/api/v1/search?keyword=${encodeURIComponent(fixture.marker)}&type=ALL&page=0&size=20&sort=latest`, accounts.owner.token);
  const snapshotAfterDiscovery = await syntheticNotificationSnapshot(accounts.owner.me.id);
  check('http-m7-discovery-no-notification-side-effect', feed.status === 200 && search.status === 200
    && snapshotAfterDiscovery.available && snapshotAfterDiscovery.count === snapshotAfterGet.count
    && snapshotAfterDiscovery.fingerprint === snapshotAfterGet.fingerprint, 'feed/search reads keep notification fingerprint unchanged');
}

async function runAuthAndValidationMatrix(accounts, events, readTarget) {
  await runNotificationEndpointAuthMatrix(accounts, readTarget);
  const cases = [
    ['validation-read', await listNotifications(accounts.owner.token, 'wat', 0, 20), 400, 'VALIDATION_001'],
    ['validation-page', await listNotifications(accounts.owner.token, 'all', -1, 20), 400, 'VALIDATION_001'],
    ['validation-size', await listNotifications(accounts.owner.token, 'all', 0, 101), 400, 'VALIDATION_001'],
    ['validation-size-zero', await listNotifications(accounts.owner.token, 'all', 0, 0), 400, 'VALIDATION_001'],
    ['not-found-read', await apiCall('PUT', '/api/v1/notifications/9223372036854770000/read', accounts.owner.token), 404, 'NOT_001'],
  ];
  for (const [label, response, status, code] of cases) {
    check(label, response.status === status && response.errorCode === code, `${label} returns ${status} ${code}`);
    checkResponseHeaders(label, response);
  }
}

function notificationEndpointCalls(notificationId, token, authorization) {
  const call = (method, path) => authorization === undefined
    ? apiCall(method, path, token)
    : apiCallWithAuthorization(method, path, authorization);
  return [
    ['list', () => call('GET', '/api/v1/notifications?read=all&page=0&size=20')],
    ['read', () => call('PUT', `/api/v1/notifications/${notificationId}/read`)],
    ['read-all', () => call('PUT', '/api/v1/notifications/read-all')],
    ['delete', () => call('DELETE', `/api/v1/notifications/${notificationId}`)],
  ];
}

async function runNotificationEndpointAuthMatrix(accounts, readTarget) {
  const invalidAuthCases = [
    ['auth-missing', undefined, undefined],
    ['auth-empty', undefined, ''],
    ['auth-basic', undefined, 'Basic m8'],
    ['auth-malformed-bearer', undefined, 'Bearer invalid-m8-token'],
  ];
  for (const [label, token, authorization] of invalidAuthCases) {
    for (const [endpoint, invoke] of notificationEndpointCalls(readTarget.id, token, authorization)) {
      const response = await invoke();
      check(`${label}-${endpoint}`, response.status === 401 && response.errorCode === 'AUTH_004', `${label} ${endpoint} returns 401 AUTH_004`);
      checkResponseHeaders(`${label}-${endpoint}`, response);
    }
  }

  const expired = process.env.M8_EXPIRED_TOKEN;
  if (expired) {
    for (const [endpoint, invoke] of notificationEndpointCalls(readTarget.id, undefined, `Bearer ${expired}`)) {
      const response = await invoke();
      check(`auth-expired-${endpoint}`, response.status === 401 && response.errorCode === 'AUTH_002', `expired ${endpoint} returns 401 AUTH_002`);
      checkResponseHeaders(`auth-expired-${endpoint}`, response);
    }
  } else {
    for (const endpoint of ['list', 'read', 'read-all', 'delete']) {
      notRun(`auth-expired-${endpoint}`, 'M8_EXPIRED_TOKEN was not provided by isolated runtime');
    }
  }

  const wrongType = process.env.M8_WRONG_TYPE_TOKEN;
  if (wrongType) {
    for (const [endpoint, invoke] of notificationEndpointCalls(readTarget.id, undefined, `Bearer ${wrongType}`)) {
      const response = await invoke();
      check(`auth-wrong-type-${endpoint}`, response.status === 401 && response.errorCode === 'AUTH_004', `wrong JWT type ${endpoint} returns 401 AUTH_004`);
      checkResponseHeaders(`auth-wrong-type-${endpoint}`, response);
    }
  } else {
    for (const endpoint of ['list', 'read', 'read-all', 'delete']) {
      notRun(`auth-wrong-type-${endpoint}`, 'M8_WRONG_TYPE_TOKEN was not provided by isolated runtime');
    }
  }

  for (const [endpoint, invoke] of notificationEndpointCalls(readTarget.id, accounts.deleted.token)) {
    const response = await invoke();
    check(`auth-deleted-principal-${endpoint}`, response.status === 404 && response.errorCode === 'USER_001', `deleted principal ${endpoint} returns 404 USER_001`);
    checkResponseHeaders(`auth-deleted-principal-${endpoint}`, response);
  }

  for (const [endpoint, invoke] of notificationEndpointCalls(readTarget.id, accounts.suspended.token)) {
    const response = await invoke();
    const expected = endpoint === 'list'
      ? response.status === 200 && response.data?.canManage === false
      : response.status === 403 && response.errorCode === 'USER_003';
    check(`auth-suspended-${endpoint}`, expected, `SUSPENDED ${endpoint} preserves read-only contract`);
    checkResponseHeaders(`auth-suspended-${endpoint}`, response);
  }
}

async function runBrowserAcceptance(accounts, posts, events) {
  await captureDesignOriginal();
  const finalComment = await createComment(accounts.actor, posts.owner.public, `${fixture.marker} browser click event`);
  const ownerRows = await fetchAllNotifications(accounts.owner.token);
  const browserNotification = ownerRows.find((item) => Number(item.targetId) === Number(finalComment.id));
  if (!browserNotification) throw new Error('browser notification fixture row missing');
  events.notificationIds.browser = browserNotification.id;

  const context = await newBrowserContext();
  const page = await context.newPage();
  const mutationRequests = [];
  page.on('request', (request) => {
    if (/\/api\/v1\/notifications\/.*(?:read|read-all)|\/api\/v1\/notifications\/\d+/.test(request.url())
      && request.method() !== 'GET') mutationRequests.push({ method: request.method(), url: request.url() });
  });
  try {
    await signInUi(page, fixture.accounts.owner);
    const firstGet = waitForNotificationGet(page);
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await firstGet;
    await waitForNotificationsReady(page);
    check('browser-center-heading', await page.getByRole('heading', { name: '알림 센터', exact: true }).count() === 1, 'center heading renders');
    const centerBox = await centerContainer(page);
    check('browser-center-width', centerBox != null && centerBox.width >= 800 && centerBox.width <= 900, 'notification center is approximately 860px wide');
    check('browser-center-no-filter-tabs', await page.getByRole('tab').count() === 0, 'notification center has no filter tabs');
    await captureProduct(page, 'notifications-owner');

    const openMutationCount = mutationRequests.length;
    const alarm = await alarmControl(page);
    const dropdownGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '3');
    await alarm.click();
    await dropdownGet;
    check('browser-dropdown-open-no-read', mutationRequests.length === openMutationCount, 'opening dropdown does not mark rows read');
    check('browser-dropdown-width', await dropdownWidth(page), 'TopBar notification dropdown is approximately 330px wide');
    check('browser-shared-unread-center-topbar', await sharedUnreadCount(page), 'center and topbar expose same unread count');
    await captureProduct(page, 'topbar-notifications');

    const row = await notificationRow(page, browserNotification, ownerRows);
    const clickResponse = waitForNotificationMutation(page, 'PUT', `/notifications/${browserNotification.id}/read`);
    await clickRowWithoutDelete(row);
    const clickResult = await clickResponse;
    check('browser-click-read', clickResult.status() === 200, 'available notification click sends individual read');
    await page.waitForTimeout(150);
    check('browser-click-navigation', browserNotification.href ? page.url().includes(browserNotification.href) : page.url() !== `${frontendUrl}/notifications`, 'read success navigates using server href');

    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(page);
    const readAll = page.getByRole('button', { name: '모두 읽음 처리', exact: true });
    const readAllResponse = waitForNotificationMutation(page, 'PUT', '/notifications/read-all');
    await readAll.click();
    const readAllResult = await readAllResponse;
    check('browser-read-all', readAllResult.status() === 200, 'center read-all sends server mutation');
    await waitForNotificationGet(page);
    check('browser-read-all-count-server', (await extractCenterUnread(page)) === 0, 'center count follows server re-fetch after read-all');

    const deletePage = await listNotifications(accounts.owner.token, 'all', 0, 20);
    const deleteItems = Array.isArray(deletePage.data?.items) ? deletePage.data.items : [];
    const deleteItem = deleteItems.find((item) => item.available !== false && item.id !== browserNotification.id);
    if (!deleteItem) throw new Error('browser delete fixture row missing');
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(page);
    const deleteRow = await notificationRow(page, deleteItem, deleteItems);
    await page.route(`**/api/v1/notifications/${deleteItem.id}`, async (route) => {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({
        success: false, data: null, error: { code: 'COMMON_500', message: 'synthetic delete failure', details: [] }, timestamp: new Date().toISOString(),
      }) });
    });
    try {
      const deleteFailure = waitForNotificationMutation(page, 'DELETE', `/notifications/${deleteItem.id}`);
      await clickDeleteControl(deleteRow);
      const failureResult = await deleteFailure;
      check('browser-delete-failure', failureResult.status() === 500, 'delete failure is surfaced');
      check('browser-delete-failure-preserves-row', await deleteRow.count() > 0 && await deleteRow.isVisible(), 'delete failure preserves existing row');
    } finally {
      await page.unroute(`**/api/v1/notifications/${deleteItem.id}`);
    }

    await page.goto(`${frontendUrl}/`, { waitUntil: 'domcontentloaded' });
    await waitForMainReady(page);
    check('browser-right-panel-recent', await page.locator('aside section h2').filter({ hasText: '최근 알림' }).count() > 0,
      'Main right panel renders recent notifications');
    const recentCount = await recentNotificationRows(page);
    check('browser-right-panel-three-or-fewer', recentCount >= 1 && recentCount <= 3, 'Main right panel renders recent 3 rows or fewer when data exists');
    check('browser-shared-unread-right-panel', await sharedUnreadCount(page), 'right panel unread count agrees with topbar/center');
    await captureProduct(page, 'main-owner-notifications');
    await runBrowserReentryAndReadRetry(page, accounts, events);
  } finally {
    await context.close();
  }

  await runBrowserUnavailableAndStale(accounts, posts, events);
  await runGuestNotificationGuard();
}

async function runBrowserReentryAndReadRetry(page, accounts, events) {
  const reentryGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '20');
  await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
  const reentryResponse = await reentryGet;
  await waitForNotificationsReady(page);
  check('browser-notification-reentry-refresh', reentryResponse.status() === 200, 're-entering notification center refreshes the center request');

  const ownerRows = await fetchAllNotifications(accounts.owner.token);
  const retryItem = ownerRows.find((item) => Number(item.id) === Number(events.notificationIds.browser) && item.available !== false);
  if (!retryItem) throw new Error('read retry notification fixture row missing');
  const retryRow = await notificationRow(page, retryItem, ownerRows);
  let failedReadInjected = false;
  await page.route(`**/api/v1/notifications/${retryItem.id}/read`, async (route) => {
    if (!failedReadInjected) {
      failedReadInjected = true;
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({
        success: false,
        data: null,
        error: { code: 'COMMON_500', message: 'synthetic read failure', details: [] },
        timestamp: new Date().toISOString(),
      }) });
      return;
    }
    await route.continue();
  });
  try {
    const failedRead = waitForNotificationMutation(page, 'PUT', `/notifications/${retryItem.id}/read`);
    await clickRowWithoutDelete(retryRow);
    const failedReadResult = await failedRead;
    const readFailureAlert = page.getByRole('alert').filter({ hasText: 'synthetic read failure' }).first();
    await readFailureAlert.waitFor({ state: 'visible', timeout: 5_000 });
    check('browser-read-failure-preserves-row', failedReadResult.status() === 500
      && await retryRow.count() > 0 && await retryRow.isVisible(), 'read failure preserves the existing notification row');
    await page.unroute(`**/api/v1/notifications/${retryItem.id}/read`);
    const retryButton = readFailureAlert.getByRole('button', { name: '다시 시도', exact: true });
    const retriedRead = waitForNotificationMutation(page, 'PUT', `/notifications/${retryItem.id}/read`);
    await retryButton.click();
    const retriedReadResult = await retriedRead;
    check('browser-read-retry', retriedReadResult.status() === 200, 'notification read failure can be retried');
    await page.waitForTimeout(150);
    check('browser-read-retry-navigation', retryItem.href ? page.url().includes(retryItem.href) : page.url() !== `${frontendUrl}/notifications`, 'successful read retry uses the canonical href');
  } finally {
    await page.unroute(`**/api/v1/notifications/${retryItem.id}/read`).catch(() => undefined);
  }
}

async function runBrowserTransientListAndAuth(accounts) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  let transientInjected = false;
  let authFailureInjected = false;
  try {
    await signInUi(page, fixture.accounts.owner);
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(page);

    await page.route('**/api/v1/notifications*', async (route) => {
      const requestUrl = new URL(route.request().url());
      const method = route.request().method();
      if (!transientInjected && method === 'GET' && requestUrl.searchParams.get('size') === '20') {
        transientInjected = true;
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({
          success: false,
          data: null,
          error: { code: 'COMMON_500', message: 'synthetic list failure', details: [] },
          timestamp: new Date().toISOString(),
        }) });
        return;
      }
      await route.continue();
    });

    const failedGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '20');
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    const failedGetResult = await failedGet;
    const listFailureAlert = page.getByRole('alert').filter({ hasText: 'synthetic list failure' }).first();
    await listFailureAlert.waitFor({ state: 'visible', timeout: 5_000 });
    const emptyState = page.getByText('아직 알림이 없습니다', { exact: true }).first();
    check('browser-transient-list-preserves-state', failedGetResult.status() === 500
      && !(await emptyState.count() > 0 && await emptyState.isVisible()), 'transient list failure does not become an empty notification state');
    await page.unroute('**/api/v1/notifications*');
    const retryGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '20');
    await listFailureAlert.getByRole('button', { name: '다시 시도', exact: true }).click();
    const retryGetResult = await retryGet;
    await waitForNotificationsReady(page);
    check('browser-transient-list-retry', retryGetResult.status() === 200 && !(await emptyState.count() > 0 && await emptyState.isVisible()), 'transient list failure can be retried without losing rows');

    const warmAlarm = await alarmControl(page);
    const warmPreview = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '3');
    await warmAlarm.click();
    await warmPreview;
    await warmAlarm.click();
    await page.route('**/api/v1/notifications*', async (route) => {
      const requestUrl = new URL(route.request().url());
      if (!authFailureInjected && route.request().method() === 'GET' && requestUrl.searchParams.get('size') === '3') {
        authFailureInjected = true;
        await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({
          success: false,
          data: null,
          error: { code: 'USER_001', message: 'synthetic deleted principal', details: [] },
          timestamp: new Date().toISOString(),
        }) });
        return;
      }
      await route.continue();
    });
    const authFailureGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '3');
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    const authFailureResult = await authFailureGet;
    const authFailureMessage = page.getByText('로그인이 만료되었습니다. 다시 접속해 주세요.', { exact: true }).first();
    await authFailureMessage.waitFor({ state: 'visible', timeout: 5_000 });
    check('browser-user001-clears-notification-state', authFailureResult.status() === 404
      && await notificationCenterRows(page).count() === 0
      && await page.locator('header [aria-label="알림"] span').count() === 0,
    'USER_001 clears notification rows, unread summary, and management state');
  } finally {
    await page.unroute('**/api/v1/notifications*').catch(() => undefined);
    await context.close();
  }
}

async function runBrowserSameAccountStale(accounts, posts) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  let previewHoldStarted = false;
  let resolvePreviewStarted;
  let resolvePreviewReleased;
  const previewStarted = new Promise((resolvePromise) => { resolvePreviewStarted = resolvePromise; });
  const previewReleased = new Promise((resolvePromise) => { resolvePreviewReleased = resolvePromise; });
  try {
    await signInUi(page, fixture.accounts.owner);
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(page);
    const warmAlarm = await alarmControl(page);
    const warmPreview = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '3');
    await warmAlarm.click();
    await warmPreview;
    await warmAlarm.click();
    await page.route('**/api/v1/notifications*', async (route) => {
      const requestUrl = new URL(route.request().url());
      if (!previewHoldStarted && route.request().method() === 'GET' && requestUrl.searchParams.get('size') === '3') {
        previewHoldStarted = true;
        const response = await route.fetch();
        resolvePreviewStarted(true);
        await new Promise((resolvePromise) => setTimeout(resolvePromise, 1_200));
        try { await route.fulfill({ response }); } catch { /* navigation may cancel a stale request */ }
        resolvePreviewReleased(true);
        return;
      }
      try { await route.continue(); } catch { /* request may be canceled during route transition */ }
    });

    const alarm = await alarmControl(page);
    await alarm.click();
    const started = await Promise.race([
      previewStarted,
      new Promise((resolvePromise) => setTimeout(() => resolvePromise(false), 15_000)),
    ]);
    if (started !== true) throw new Error('same-account stale preview request was not captured');

    const freshComment = await createComment(accounts.actor, posts.owner.public, `${fixture.marker} same-account stale event`);
    const ownerRows = await fetchAllNotifications(accounts.owner.token);
    const freshNotification = ownerRows.find((item) => Number(item.targetId) === Number(freshComment.id));
    const latestServer = await listNotifications(accounts.owner.token, 'all', 0, 20);
    const pageTwo = page.getByRole('button', { name: '2 페이지', exact: true });
    if (await pageTwo.count() === 0) throw new Error('same-account stale pagination fixture page is missing');
    const centerGet = waitForNotificationGet(page, (response) => new URL(response.url()).searchParams.get('size') === '20');
    await pageTwo.click();
    const centerResponse = await centerGet;
    await waitForNotificationsReady(page);
    const unreadBeforeStale = await extractCenterUnread(page);
    const released = await Promise.race([
      previewReleased,
      new Promise((resolvePromise) => setTimeout(() => resolvePromise(false), 15_000)),
    ]);
    const unreadAfterStale = await extractCenterUnread(page);
    check('browser-same-account-stale-get', centerResponse.status() === 200
      && released === true
      && freshNotification != null
      && Number(latestServer.data?.unreadCount) > 0
      && unreadBeforeStale === Number(latestServer.data?.unreadCount)
      && unreadAfterStale === Number(latestServer.data?.unreadCount),
    'same-account older preview GET cannot overwrite the newer unread summary');
  } finally {
    await page.unroute('**/api/v1/notifications*').catch(() => undefined);
    await context.close();
  }
}

async function runBrowserUnavailableAndStale(accounts, posts, events) {
  await runBrowserTransientListAndAuth(accounts);
  await runBrowserSameAccountStale(accounts, posts);
  const context = await newBrowserContext();
  const page = await context.newPage();
  let heldOwnerRoute = null;
  let heldOwnerUrl = null;
  let heldOwnerStatus = null;
  let heldOwnerResponse = null;
  let resolveOwnerHold;
  let resolveOwnerRelease;
  const ownerHoldObserved = new Promise((resolvePromise) => { resolveOwnerHold = resolvePromise; });
  const ownerRelease = new Promise((resolvePromise) => { resolveOwnerRelease = resolvePromise; });
  const friendRows = await fetchAllNotifications(accounts.friend.token);
  const ownerPage = await listNotifications(accounts.owner.token, 'all', 0, 20);
  const ownerVisibleRows = Array.isArray(ownerPage.data?.items) ? ownerPage.data.items : [];
  const staleOwnerItem = ownerVisibleRows.find((item) => item.available !== false
    && Number(item.id) !== Number(events.notificationIds.browser)
    && Number(item.id) !== Number(events.notificationIds.accessLoss)
    && !friendRows.some((friendItem) => notificationDisplayMessage(friendItem) === notificationDisplayMessage(item)
      && notificationTimeLabel(friendItem.createdAt) === notificationTimeLabel(item.createdAt)));
  try {
    await signInUi(page, fixture.accounts.owner);
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(page);
    const ownerRowBeforeSwitch = staleOwnerItem
      ? await notificationRowsContaining(page, staleOwnerItem).count()
      : 0;
    await page.route('**/api/v1/notifications*', async (route) => {
      const requestUrl = new URL(route.request().url());
      if (!heldOwnerRoute && route.request().method() === 'GET' && requestUrl.searchParams.get('size') === '20') {
        heldOwnerRoute = route;
        heldOwnerUrl = route.request().url();
        try {
          heldOwnerResponse = await route.fetch();
          heldOwnerStatus = heldOwnerResponse.status();
          resolveOwnerHold(true);
          await ownerRelease;
          await route.fulfill({ response: heldOwnerResponse });
        } catch {
          resolveOwnerHold(false);
          try { await route.continue(); } catch { /* request may be canceled during the account switch */ }
        }
        return;
      }
      try { await route.continue(); } catch { /* navigation may cancel the stale request */ }
    });
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    const observed = await Promise.race([
      ownerHoldObserved,
      new Promise((resolvePromise) => setTimeout(() => resolvePromise(false), 15_000)),
    ]);
    check('browser-stale-owner-response-held', observed === true && ownerRowBeforeSwitch > 0 && heldOwnerStatus === 200,
      'a real owner center response is held after the owner screen is ready with upstream status 200');
    const expiryResult = await triggerBrowserSessionExpiry(page);
    check('browser-session-expiry-injected', expiryResult.ok === false
      && expiryResult.meCalls >= 1
      && expiryResult.refreshCalls >= 1
      && page.url().endsWith('/signin'),
    'a scoped synthetic session expiry invokes the existing auth-expired path without adding logout UI');
    await signInUi(page, fixture.accounts.friend, { spa: true });
    await page.evaluate(() => {
      window.history.pushState({}, '', '/notifications');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    await waitForNotificationsReady(page);
    const staleResponse = heldOwnerRoute && heldOwnerUrl
      ? page.waitForResponse((response) => response.url() === heldOwnerUrl, { timeout: 15_000 })
      : null;
    if (heldOwnerRoute) resolveOwnerRelease(true);
    const releasedResponse = staleResponse ? await staleResponse : null;
    check('browser-stale-request-observed', releasedResponse?.status() === 200,
      'held owner notification response completed after the SPA account switch');
    await page.waitForTimeout(150);
    const stalePresent = staleOwnerItem
      ? await notificationRowsContaining(page, staleOwnerItem).count() > 0
      : false;
    check('browser-stale-account-cleared', !stalePresent, 'late owner response cannot repopulate friend notifications');
  } finally {
    resolveOwnerRelease?.(true);
    await page.unroute('**/api/v1/notifications*').catch(() => undefined);
    await context.close();
  }

  const unavailableContext = await newBrowserContext();
  const unavailablePage = await unavailableContext.newPage();
  try {
    await signInUi(unavailablePage, fixture.accounts.owner);
    await unavailablePage.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await waitForNotificationsReady(unavailablePage);
    const generic = unavailablePage.getByText('더 이상 확인할 수 없는 알림입니다', { exact: true });
    check('browser-unavailable-generic', await generic.count() > 0, 'unavailable row uses generic message');
    if (await generic.count() > 0) {
      const unavailableUrl = unavailablePage.url();
      const unavailableItem = ownerVisibleRows.find((item) => item.available === false);
      const unavailableRow = unavailableItem
        ? await notificationRow(unavailablePage, unavailableItem, ownerVisibleRows)
        : generic.first().locator('..');
      const readMutation = unavailableItem
        ? waitForNotificationMutation(unavailablePage, 'PUT', `/notifications/${unavailableItem.id}/read`)
        : null;
      await unavailableRow.click();
      if (readMutation) {
        const response = await readMutation;
        const body = await response.json().catch(() => null);
        check('browser-unavailable-read-mask', response.status() === 200
          && body?.success === true
          && body?.data?.available === false
          && body?.data?.href == null,
        'unavailable row may be marked read but remains masked with no canonical href');
      }
      await unavailablePage.waitForTimeout(100);
      check('browser-unavailable-no-navigation', unavailablePage.url() === unavailableUrl, 'unavailable row does not navigate');
    }
  } finally {
    await unavailableContext.close();
  }
}

async function triggerBrowserSessionExpiry(page) {
  const failureBody = JSON.stringify({
    success: false,
    data: null,
    error: { code: 'AUTH_004', message: 'synthetic session expiry', details: [] },
    timestamp: new Date().toISOString(),
  });
  let meCalls = 0;
  let refreshCalls = 0;
  await page.route('**/api/v1/auth/me', async (route) => {
    meCalls += 1;
    await route.fulfill({ status: 401, contentType: 'application/json', body: failureBody });
  });
  await page.route('**/api/v1/auth/refresh', async (route) => {
    refreshCalls += 1;
    await route.fulfill({ status: 401, contentType: 'application/json', body: failureBody });
  });
  let result;
  try {
    result = await page.evaluate(async () => {
      const { apiClient } = await import('/src/lib/apiClient.ts');
      try {
        await apiClient.get('/api/v1/auth/me');
        return { ok: true };
      } catch (error) {
        return { ok: false, message: error instanceof Error ? error.message : String(error) };
      }
    });
  } finally {
    await page.unroute('**/api/v1/auth/me').catch(() => undefined);
    await page.unroute('**/api/v1/auth/refresh').catch(() => undefined);
  }
  await page.getByLabel('이메일').waitFor({ state: 'visible', timeout: 10_000 });
  return { ...result, meCalls, refreshCalls };
}

async function runGuestNotificationGuard() {
  const context = await newBrowserContext();
  const page = await context.newPage();
  const notificationRequests = [];
  const listener = (request) => {
    if (request.url().includes('/api/v1/notifications')) notificationRequests.push(request.url());
  };
  page.on('request', listener);
  try {
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(250);
    check('browser-guest-notification-guard', notificationRequests.length === 0 && page.url().endsWith('/signin'), 'guest cannot call protected notification GET and reaches signin');
  } finally {
    page.off('request', listener);
    await context.close();
  }
}

async function captureDesignOriginal() {
  if (!screenshotsEnabled) {
    notRun('design-original-screenshots', 'M8_SCREENSHOTS=0');
    return;
  }
  const context = await newBrowserContext();
  try {
    const pages = await context.newPage();
    await pages.goto(pathToFileURL(designPagesFile).href, { waitUntil: 'load' });
    const switcher = pages.getByText('알림 센터', { exact: true }).first();
    if (await switcher.count() > 0) await switcher.click();
    const screen = pages.locator('[data-screen-label="알림 센터"]:visible');
    await screen.waitFor({ state: 'visible', timeout: 10_000 });
    await pages.screenshot({ path: resolve(outputDir, 'design-notifications.png'), fullPage: true });
    evidence.screenshots.push('design-notifications.png');
    check('design-notifications-original', /알림 센터/.test(await screen.innerText()) && /모두 읽음 처리/.test(await screen.innerText()), 'Pages original renders notification center');

    const main = await context.newPage();
    await main.goto(pathToFileURL(designMainFile).href, { waitUntil: 'load' });
    const mainBodyText = await main.locator('body').innerText();
    check('design-main-notifications-original', /최근 알림/.test(mainBodyText) && /알림 센터 열기/.test(mainBodyText), 'Main original renders recent notifications panel');
    await main.screenshot({ path: resolve(outputDir, 'design-main-notifications.png'), fullPage: true });
    evidence.screenshots.push('design-main-notifications.png');
  } catch (error) {
    check('design-notifications-original', false, scrubSecrets(error instanceof Error ? error.message : String(error)));
  } finally {
    await context.close();
  }
}

async function signInUi(page, account, { spa = false } = {}) {
  if (spa) {
    await page.evaluate(() => {
      window.history.pushState({}, '', '/signin');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    await page.getByLabel('이메일').waitFor({ state: 'visible', timeout: 10_000 });
  } else {
    await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  }
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: /접속하기|로그인/ }).last().click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 15_000 });
  check('browser-signin', !page.url().endsWith('/signin'), 'synthetic account reached application route');
}

async function waitForNotificationsReady(page) {
  await page.getByRole('heading', { name: '알림 센터', exact: true }).waitFor({ state: 'visible', timeout: 15_000 });
  const loading = page.getByText(/알림을 불러오는 중|불러오는 중/, { exact: false });
  if (await loading.count() > 0) await loading.first().waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => undefined);
}

async function waitForMainReady(page) {
  await page.locator('main').waitFor({ state: 'visible', timeout: 15_000 });
  const loading = page.getByText(/불러오는 중/, { exact: false });
  if (await loading.count() > 0) await loading.first().waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => undefined);
}

function waitForNotificationGet(page, predicate = () => true) {
  return page.waitForResponse((response) => response.request().method() === 'GET'
    && response.url().includes('/api/v1/notifications') && predicate(response), { timeout: 15_000 });
}

function waitForNotificationMutation(page, method, suffixPart) {
  return page.waitForResponse((response) => response.request().method() === method
    && response.url().includes('/api/v1/notifications') && response.url().includes(suffixPart), { timeout: 15_000 });
}

async function alarmControl(page) {
  const explicit = page.locator('header a[aria-label="알림"]').first();
  if (await explicit.count() > 0) return explicit;
  const header = page.locator('header');
  const candidates = [
    header.getByRole('button', { name: /알림/ }).first(),
    header.getByRole('link', { name: /알림/ }).first(),
    header.getByText('알림', { exact: true }).first(),
  ];
  for (const candidate of candidates) if (await candidate.count() > 0) return candidate;
  throw new Error('notification alarm control not found');
}

function notificationDisplayMessage(item) {
  return item?.available === false ? '더 이상 확인할 수 없는 알림입니다' : item?.message;
}

function notificationTimeLabel(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '시간 미정'
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function notificationCenterRows(page) {
  return page.locator('section[aria-label="알림 센터"] [role="button"][tabindex="0"]');
}

function notificationRowsContaining(page, item) {
  const display = notificationDisplayMessage(item);
  return page.locator(
    'section[aria-label="알림 센터"] [role="button"][tabindex="0"], '
      + '[role="dialog"][aria-label="최근 알림"] button',
  ).filter({ hasText: display ?? '' });
}

async function notificationRow(page, item, orderedItems = []) {
  const display = notificationDisplayMessage(item);
  if (typeof display !== 'string') throw new Error(`notification row ${item.id} has no display message`);
  const time = notificationTimeLabel(item.createdAt);
  const center = page.locator('section[aria-label="알림 센터"]').first();
  const dialog = page.getByRole('dialog', { name: '최근 알림', exact: true }).first();
  const surfaces = [];
  if (await dialog.count() > 0) surfaces.push({ locator: dialog.locator('button'), kind: 'preview' });
  if (await center.count() > 0) surfaces.push({ locator: center.locator('[role="button"][tabindex="0"]'), kind: 'center' });
  for (const surface of surfaces) {
    const allRows = surface.locator;
    const matching = allRows.filter({ hasText: display });
    const timed = matching.filter({ hasText: time });
    if (await timed.count() === 1) return timed.first();
    if (await matching.count() === 1) return matching.first();
    if (await matching.count() > 1 && orderedItems.length > 0) {
      const occurrence = orderedItems
        .filter((candidate) => notificationDisplayMessage(candidate) === display
          && notificationTimeLabel(candidate.createdAt) === time)
        .findIndex((candidate) => Number(candidate.id) === Number(item.id));
      if (occurrence >= 0 && occurrence < await matching.count()) return matching.nth(occurrence);
      const fallbackOccurrence = orderedItems
        .filter((candidate) => notificationDisplayMessage(candidate) === display)
        .findIndex((candidate) => Number(candidate.id) === Number(item.id));
      if (fallbackOccurrence >= 0 && fallbackOccurrence < await matching.count()) return matching.nth(fallbackOccurrence);
    }
  }
  throw new Error(`notification row ${item.id} not found in browser using message/time/order`);
}

async function clickRowWithoutDelete(row) {
  const content = row.getByText(/./).first();
  if (await content.count() > 0) await content.click();
  else await row.click();
}

async function clickDeleteControl(row) {
  const controls = [
    row.getByRole('button', { name: /삭제|×/ }).first(),
    row.locator('[aria-label*="삭제"]').first(),
    row.locator('button').last(),
  ];
  for (const control of controls) if (await control.count() > 0) {
    await control.click();
    return;
  }
  throw new Error('notification delete control not found');
}

async function centerContainer(page) {
  const explicit = page.locator('[data-testid="notification-center"], [data-testid="notifications-page"]').first();
  if (await explicit.count() > 0) return explicit.boundingBox();
  const appLayout = page.locator('[data-layout="app"]').first();
  if (await appLayout.count() > 0) return appLayout.boundingBox();
  return page.locator('main').first().boundingBox();
}

async function dropdownWidth(page) {
  const explicit = page.locator('[data-testid="notification-dropdown"], [role="dialog"]').first();
  if (await explicit.count() > 0) {
    const box = await explicit.boundingBox();
    return Boolean(box && box.width >= 300 && box.width <= 360);
  }
  const candidate = page.getByText('알림 센터', { exact: true }).last().locator('..');
  const box = await candidate.boundingBox();
  return Boolean(box && box.width >= 300 && box.width <= 360);
}

async function sharedUnreadCount(page) {
  const center = await extractCenterUnread(page);
  await alarmControl(page);
  const badge = page.locator('header a[aria-label="알림"] span').first();
  const badgeValue = await badge.count() > 0 ? Number((await badge.innerText()).trim()) : 0;
  if (center == null || !Number.isFinite(badgeValue)) return false;
  return center === badgeValue;
}

async function extractCenterUnread(page) {
  const centerSection = page.locator('main section[aria-label="알림 센터"]').first();
  const rightPanel = page.locator('aside section').filter({ hasText: '최근 알림' }).first();
  const scope = await centerSection.count() > 0 ? centerSection : rightPanel;
  if (await scope.count() === 0) return null;
  const selectors = [
    '[data-testid="notification-unread-count"]',
    '[aria-label^="읽지 않음"]',
  ];
  for (const selector of selectors) {
    const node = scope.locator(selector).first();
    if (await node.count() > 0) {
      const text = await node.innerText().catch(() => '');
      const match = text.match(/(\d+)/);
      if (match) return Number(match[1]);
    }
  }
  const badge = scope.getByText(/읽지 않음\s*\d+/, { exact: false }).first();
  if (await badge.count() > 0) {
    const match = (await badge.innerText()).match(/읽지 않음\s*(\d+)/);
    if (match) return Number(match[1]);
  }
  return null;
}

async function recentNotificationRows(page) {
  const explicit = page.locator('[data-testid="recent-notification"]');
  if (await explicit.count() > 0) return explicit.count();
  const panel = page.locator('aside section').filter({ hasText: '최근 알림' }).first();
  if (await panel.count() === 0) return 0;
  return panel.locator('button').count();
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

async function apiCall(method, path, token, data) {
  return apiCallWithOptions(method, path, token ? { Authorization: `Bearer ${token}` } : undefined, data);
}

async function apiCallWithAuthorization(method, path, authorization, data) {
  return apiCallWithOptions(method, path, { Authorization: authorization }, data);
}

async function apiCallWithOptions(method, path, headers, data) {
  const options = { method, headers };
  if (data !== undefined) options.data = data;
  const response = await apiContext.fetch(path, options);
  const json = await safeJson(response);
  return {
    status: response.status(),
    url: path,
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

async function listNotifications(token, read = 'all', page = 0, size = 20) {
  return apiCall('GET', `/api/v1/notifications?read=${encodeURIComponent(read)}&page=${page}&size=${size}`, token);
}

async function fetchAllNotifications(token) {
  const first = await listNotifications(token, 'all', 0, 100);
  if (first.status !== 200 || !Array.isArray(first.data?.items)) return [];
  const totalPages = Math.min(Number(first.data.totalPages ?? 1), 20);
  const items = [...first.data.items];
  for (let page = 1; page < totalPages; page += 1) {
    const response = await listNotifications(token, 'all', page, 100);
    if (response.status !== 200 || !Array.isArray(response.data?.items)) break;
    items.push(...response.data.items);
  }
  return items;
}

function countType(items, type, targetId) {
  return items.filter((item) => item.type === type && (targetId == null || Number(item.targetId) === Number(targetId))).length;
}

function findItem(response, id) {
  return (response?.data?.items ?? []).find((item) => Number(item.id) === Number(id));
}

function hasId(response, id) {
  return Boolean(id) && (response?.data?.items ?? []).some((item) => Number(item.id) === Number(id));
}

function unavailableShape(item) {
  return item?.available === false
    && item.message === '더 이상 확인할 수 없는 알림입니다'
    && item.actor == null && item.targetId == null && item.href == null;
}

function ageInDays(value) {
  const time = Date.parse(value ?? '');
  return Number.isFinite(time) ? (Date.now() - time) / 86_400_000 : -1;
}

function sortedNotifications(items) {
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

function pageShape(response) {
  return Array.isArray(response?.data?.items)
    && Number.isInteger(Number(response.data.page))
    && Number.isInteger(Number(response.data.size))
    && Number.isInteger(Number(response.data.totalElements))
    && Number.isInteger(Number(response.data.totalPages));
}

function checkPage(label, response, expectSuccess) {
  check(`${label}-status`, expectSuccess ? response.status === 200 : response.status !== 200, `${label} status=${response.status}`);
  check(`${label}-page-shape`, !expectSuccess || pageShape(response), `${label} has PageResponse shape`);
}

function checkResponseHeaders(label, response) {
  check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} is private/no-store`);
  check(`${label}-envelope`, response.envelope, `${label} uses common ApiResponse envelope`);
}

function privateNoStore(value) {
  return /private/i.test(value ?? '') && /no-store/i.test(value ?? '');
}

async function blockUser(from, target) {
  const response = await apiCall('POST', `/api/v1/universe/blocks/${target.me.id}`, from.token);
  check('fixture-block', response.status === 200, 'owner blocked synthetic account');
  if (response.status !== 200) throw new Error('fixture block failed');
}

async function unblockUser(from, target) {
  const response = await apiCall('DELETE', `/api/v1/universe/blocks/${target.me.id}`, from.token);
  check('fixture-unblock', response.status === 200, 'owner unblocked synthetic account');
  if (response.status !== 200) throw new Error('fixture unblock failed');
}

async function syntheticNotificationSnapshot(userId) {
  if (!Number.isInteger(Number(userId))) return { available: false, fingerprint: 'invalid-user-id' };
  const numericId = String(userId).replace(/[^0-9]/g, '');
  // Read one scalar JSON row at a time. GROUP_CONCAT/CONCAT_WS can truncate or
  // silently drop NULLs, which would let a tail/audit-field mutation evade the
  // M7 read-only fingerprint check. HEX keeps mysql --batch output lossless.
  const sql = `SELECT COUNT(*) FROM notifications WHERE receiver_user_id = ${numericId};
SELECT HEX(JSON_ARRAY(id, receiver_user_id, actor_user_id, type, target_type, target_id, message, read_at, created_at, updated_at, deleted_at))
FROM notifications WHERE receiver_user_id = ${numericId} ORDER BY id;`;
  try {
    const output = await runSyntheticDbSql(sql);
    const lines = output.split(/\r?\n/).map((line) => line.trim()).filter((line, index) => index === 0 || line.length > 0);
    const count = lines[0];
    if (!/^\d+$/.test(count ?? '')) return { available: false, fingerprint: 'malformed-sql-count' };
    const rows = [];
    for (const encoded of lines.slice(1)) {
      if (!/^[0-9a-f]+$/i.test(encoded) || encoded.length % 2 !== 0) {
        return { available: false, fingerprint: 'malformed-sql-row' };
      }
      try {
        rows.push(JSON.parse(Buffer.from(encoded, 'hex').toString('utf8')));
      } catch {
        return { available: false, fingerprint: 'malformed-json-row' };
      }
    }
    if (rows.length !== Number(count)) return { available: false, fingerprint: 'row-count-mismatch' };
    const canonical = JSON.stringify(rows);
    const fingerprint = createHash('sha256').update(canonical, 'utf8').digest('hex');
    return { available: true, count: Number(count), fingerprint };
  } catch {
    return { available: false, fingerprint: 'synthetic-sql-unavailable' };
  }
}

async function syntheticSelectCount() {
  const sql = "SELECT COALESCE(SUM(COUNT_STAR), 0) FROM performance_schema.events_statements_summary_by_account_by_event_name WHERE USER = 'zeroverse_m8' AND EVENT_NAME = 'statement/sql/select';";
  try {
    const output = await runSyntheticDbSql(sql);
    const value = output.trim();
    if (!/^\d+$/.test(value)) return { available: false, count: null };
    return { available: true, count: Number(value) };
  } catch {
    return { available: false, count: null };
  }
}

async function runSyntheticDbSql(sql) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn('python3', [runtimeSqlHelper, 'sql'], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'] });
    let stdout = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString(); });
    child.stderr.on('data', () => {});
    child.on('error', () => rejectPromise(new Error('synthetic SQL helper unavailable')));
    child.on('close', (code) => code === 0 ? resolvePromise(stdout) : rejectPromise(new Error('synthetic SQL helper failed')));
    child.stdin.end(sql);
  });
}

async function verifyVersionManifest() {
  const empty = { manifestPath: versionManifestFile, sourceSha256: null, actualSourceSha256: null, jarSha256: null, actualJarSha256: null, filesChecked: 0 };
  let manifest;
  try { manifest = JSON.parse(await readFile(versionManifestFile, 'utf8')); } catch {
    return { ok: false, detail: `version manifest is not ready: ${versionManifestFile}`, evidence: empty };
  }
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  const jarPathValue = typeof manifest.jarPath === 'string' ? manifest.jarPath : '';
  const expectedSourceSha = manifest.sourceSha256;
  const expectedJarSha = manifest.jarSha256;
  const base = { ...empty, sourceSha256: expectedSourceSha ?? null, jarSha256: expectedJarSha ?? null };
  if (!/^[0-9a-f]{64}$/i.test(expectedSourceSha ?? '') || !/^[0-9a-f]{64}$/i.test(expectedJarSha ?? '') || !jarPathValue || files.length === 0) {
    return { ok: false, detail: 'version manifest must contain valid sourceSha256, jarSha256, jarPath and files', evidence: base };
  }
  const entries = files.map((entry) => ({ path: entry?.path, expectedSha: entry?.sha256 }));
  if (entries.some((entry) => typeof entry.path !== 'string' || !/^[0-9a-f]{64}$/i.test(entry.expectedSha ?? ''))) {
    return { ok: false, detail: 'version manifest has an invalid source file entry', evidence: base };
  }
  // Match build/m8-runtime.py and the M7 manifest's byte/ordinal path order;
  // localeCompare is host-locale dependent and produced a false hash failure.
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
      return { ok: false, detail: `source file is unavailable: ${entry.path}`, evidence: { ...base, filesChecked: actualHashes.length } };
    }
  }
  const aggregateInput = actualHashes.map((entry) => `${entry.path}\0${entry.sha256}\n`).join('');
  const actualSourceSha = createHash('sha256').update(aggregateInput, 'utf8').digest('hex');
  const jarPath = resolve(root, jarPathValue);
  if (!isInsideRoot(jarPath)) return { ok: false, detail: `manifest JAR path escapes repository: ${jarPathValue}`, evidence: { ...base, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  let actualJarSha;
  try { actualJarSha = await sha256File(jarPath); } catch {
    return { ok: false, detail: `immutable runtime JAR is unavailable: ${jarPathValue}`, evidence: { ...base, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  }
  const finalEvidence = { ...base, actualSourceSha256: actualSourceSha, actualJarSha256: actualJarSha, filesChecked: actualHashes.length, jarPath: jarPathValue };
  if (actualSourceSha !== expectedSourceSha) return { ok: false, detail: 'source aggregate SHA-256 mismatch', evidence: finalEvidence };
  if (actualJarSha !== expectedJarSha) return { ok: false, detail: 'immutable runtime JAR SHA-256 mismatch', evidence: finalEvidence };
  if (process.env.M8_SOURCE_SHA && process.env.M8_SOURCE_SHA !== actualSourceSha) return { ok: false, detail: 'M8_SOURCE_SHA does not match manifest', evidence: finalEvidence };
  if (process.env.M8_RUNTIME_SOURCE_SHA && process.env.M8_RUNTIME_SOURCE_SHA !== actualSourceSha) return { ok: false, detail: 'M8_RUNTIME_SOURCE_SHA does not match manifest', evidence: finalEvidence };
  if (process.env.M8_RUNTIME_JAR_SHA && process.env.M8_RUNTIME_JAR_SHA !== actualJarSha) return { ok: false, detail: 'M8_RUNTIME_JAR_SHA does not match immutable JAR', evidence: finalEvidence };
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
  evidence.checks.push({ name, status, detail });
  if (!pass) evidence.failures.push(`${name}: ${detail ?? 'failed'}`);
  console.log(`[${status}] ${name}`);
}

function notRun(name, detail) {
  evidence.checks.push({ name, status: 'NOT RUN', detail });
  if (requireFull) evidence.failures.push(`${name}: ${detail ?? 'not run'}`);
  console.log(`[NOT RUN] ${name}`);
}

async function finishEvidence() {
  await writeEvidence();
  const pass = evidence.checks.filter((item) => item.status === 'PASS').length;
  const notRunCount = evidence.checks.filter((item) => item.status === 'NOT RUN').length;
  console.log(JSON.stringify({ outputDir, screenshots: evidence.screenshots.length, pass, notRun: notRunCount, failures: evidence.failures.length, fixtureIds: evidence.fixture }));
  if (evidence.failures.length > 0) throw new Error(`M8 acceptance failed (${evidence.failures.length} findings)`);
}

async function writeEvidence() {
  const safe = scrubSecrets(JSON.stringify(evidence));
  await writeFile(resolve(outputDir, 'evidence.json'), safe);
}

function scrubSecrets(value) {
  let result = String(value);
  for (const account of Object.values(fixture.accounts ?? {})) {
    result = result.replaceAll(account.email, '[redacted-email]');
    result = result.replaceAll(account.password, '[redacted-password]');
  }
  result = result.replaceAll(fixture.marker, '[redacted-marker]');
  result = result.replaceAll(fixture.pageMarker, '[redacted-page-marker]');
  result = result.replace(/Bearer\s+\S+/gi, 'Bearer [redacted-token]');
  result = result.replace(/[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '[redacted-token]');
  return result;
}
