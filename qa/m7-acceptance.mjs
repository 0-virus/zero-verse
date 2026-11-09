/**
 * M7 피드·검색 독립 인수 runner.
 *
 * 이 파일은 QA 소유 black-box 도구다. 합성 계정·블로그·글만 HTTP로 만들고,
 * 자격증명/access token/본문 marker는 메모리에만 둔다. evidence에는 합성 ID,
 * check 결과와 screenshot 파일명만 남긴다. 사용자 DB(기존 M5 DB 포함)에
 * 직접 접속하거나 행을 수정하지 않는다.
 *
 * 기본 runtime:
 *   M7_API_BASE_URL=http://localhost:18087
 *   M7_FRONTEND_URL=http://localhost:5177
 *   M7_PLAYWRIGHT_MODULE=/path/to/playwright/node_modules/playwright
 *
 * suspended/deleted author는 API에 상태 변경 endpoint가 없으므로, runner가
 * `build/m7-acceptance/fixture-seed.json`을 쓴 뒤 root가 합성 M7 DB에서만
 * 상태를 바꾸고 `build/m7-acceptance/status-ready.json`을 만들어야 한다.
 * `M7_WAIT_FOR_STATUS_FIXTURES=1`이면 ready 파일을 기다린다. ready 파일에는
 * ID/상태만 두며 자격증명은 넣지 않는다. 최종 full run에서는 상태 fixture를
 * 생략하지 않는다(`M7_REQUIRE_STATUS_FIXTURES=1`, 기본값).
 *
 * source/runtime SHA는 root가 생성하는
 * `build/m7-evidence/tested-version.json`(또는 M7_VERSION_MANIFEST)에서 읽는다.
 * manifest의 files를 실제로 다시 hash하고, path\0hash\n aggregate와
 * runtime.jar SHA-256을 독립 계산해 비교한다. manifest가 없거나 불일치하면
 * full run은 NOT RUN/FAIL이며 환경값만으로 통과시키지 않는다.
 *
 * 실행 전 API/FE/JAR가 준비되지 않았으면 이 runner를 호출하지 않는다.
 * 호출되더라도 health/runtime failure는 성공으로 삼지 않고 exit 1로 끝난다.
 */

import { createRequire } from 'node:module';
import { randomBytes, createHash } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M7_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium, request: requestApi } = require(playwrightModule);

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const apiBaseUrl = process.env.M7_API_BASE_URL ?? 'http://localhost:18087';
const frontendUrl = process.env.M7_FRONTEND_URL ?? 'http://localhost:5177';
const chromeExecutable = process.env.M7_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const designMainFile = resolve(root, process.env.M7_DESIGN_MAIN_FILE ?? 'docs/design/ZeroVerse Main Feed v2.dc.html');
const designPagesFile = resolve(root, process.env.M7_DESIGN_PAGES_FILE ?? 'docs/design/ZeroVerse Pages.dc.html');
const outputDir = resolve(root, process.env.M7_OUTPUT_DIR ?? 'build/m7-acceptance');
const seedFile = resolve(root, process.env.M7_FIXTURE_SEED_FILE ?? 'build/m7-acceptance/fixture-seed.json');
const statusReadyFile = resolve(root, process.env.M7_STATUS_READY_FILE ?? 'build/m7-acceptance/status-ready.json');
const versionManifestFile = resolve(root, process.env.M7_VERSION_MANIFEST ?? 'build/m7-evidence/tested-version.json');
const runtimeSqlHelper = resolve(root, process.env.M7_RUNTIME_SQL_HELPER ?? 'build/m7-runtime.py');
const requireFull = process.env.M7_REQUIRE_FULL !== '0';
const requireStatusFixtures = process.env.M7_REQUIRE_STATUS_FIXTURES !== '0';
const waitForStatusFixtures = process.env.M7_WAIT_FOR_STATUS_FIXTURES === '1';
const statusWaitMs = Number(process.env.M7_STATUS_WAIT_MS ?? 180_000);
const screenshotsEnabled = process.env.M7_SCREENSHOTS !== '0';
const suffix = `${Date.now().toString(36)}${randomBytes(5).toString('hex')}`;

const evidence = {
  milestone: 'M7',
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  frontendUrl,
  apiBaseUrl,
  fixture: {
    accountIds: {},
    blogIds: {},
    postIds: {},
    relationshipLabels: [],
    statusFixtureReady: false,
  },
  screenshots: [],
  checks: [],
  failures: [],
};

const fixture = makeFixtureCredentials(suffix);
let apiContext;
let browser;
let statusReady = false;

try {
  await mkdir(outputDir, { recursive: true });
  apiContext = await requestApi.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });

  await verifyApiReadiness();

  browser = await chromium.launch({
    headless: process.env.M7_HEADLESS !== '0',
    executablePath: chromeExecutable,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  const accounts = {};
  for (const [label, account] of Object.entries(fixture.accounts)) {
    accounts[label] = await registerAndSetup(account, label);
  }

  const posts = await createFixturePosts(accounts);
  await createRelationships(accounts);
  await writeFixtureSeed(accounts, posts);
  statusReady = await waitForPrivilegedStatusFixtures(accounts, posts);
  evidence.fixture.statusFixtureReady = statusReady;

  await runHttpAcceptance(accounts, posts);
  await runBrowserAcceptance(accounts, posts);
  await finishEvidence();
} catch (error) {
  const safeMessage = scrubSecrets(error instanceof Error ? error.message : String(error));
  evidence.failures.push(`runner: ${safeMessage}`);
  await writeEvidence();
  throw new Error(`M7 acceptance failed: ${safeMessage}`);
} finally {
  await apiContext?.dispose();
  await browser?.close();
}

function makeFixtureCredentials(runSuffix) {
  const key = runSuffix.slice(-8).toLowerCase();
  const searchToken = `m7${key}`;
  const pageToken = `m7page${key}`;
  const wildcardToken = `m7wild${key}`;
  const password = `M7!Qa${runSuffix}7`;
  const labels = ['owner', 'actor', 'friend', 'oneWay', 'unrelated', 'blocked', 'suspended', 'deleted'];
  const accounts = Object.fromEntries(labels.map((label) => {
    const nickPrefix = label === 'owner' ? '' : `${label.slice(0, 2).toLowerCase()}`;
    const nickname = `${searchToken}${nickPrefix}`.slice(0, 20);
    return [label, {
      email: `m7-${label}-${runSuffix}@example.test`,
      password,
      name: `M7 QA ${label}`,
      nickname,
      birthDate: '1990-01-01',
      blogTitle: label === 'owner'
        ? `M7 ${searchToken} owner blog`
        : `M7 ${label} ${key} blog`,
      blogSlug: `m7-${label}-${key}`.toLowerCase().slice(0, 30),
    }];
  }));
  return {
    accounts,
    searchToken,
    pageToken,
    wildcardToken,
    richText: `sp${'ring'} ${searchToken} ${wildcardToken}%_ & <tag>`,
    uiSearchToken: searchToken,
    pageMarker: pageToken,
  };
}

async function verifyApiReadiness() {
  const response = await apiContext.fetch('/v3/api-docs', { method: 'GET' });
  const json = await safeJson(response);
  const paths = json?.paths ?? {};
  check('runtime-api-openapi', response.status() === 200, 'backend /v3/api-docs is reachable');
  check(
    'openapi-m7-paths',
    response.status() === 200
      && ['/api/v1/feed/public', '/api/v1/feed/universe', '/api/v1/search']
        .every((path) => Object.prototype.hasOwnProperty.call(paths, path)),
    'OpenAPI exposes public feed, universe feed and search paths',
  );
  if (response.status() !== 200 || !(['/api/v1/feed/public', '/api/v1/feed/universe', '/api/v1/search']
    .every((path) => Object.prototype.hasOwnProperty.call(paths, path)))) {
    throw new Error('backend runtime is not ready for M7 endpoints');
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
    description: `M7 synthetic ${label} blog`,
  });
  check(`${label}-initial-setup`, setup.status === 200, 'synthetic blog initialized');
  if (setup.status !== 200) throw new Error(`${label} initial setup failed`);

  const me = await apiCall('GET', '/api/v1/auth/me', token);
  const blog = await apiCall('GET', '/api/v1/blogs/me', token);
  check(`${label}-blog-readback`, me.status === 200 && blog.status === 200 && Number.isInteger(me.data?.id) && Number.isInteger(blog.data?.id), 'account/blog IDs read back');
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
    friend: {},
    oneWay: {},
    unrelated: {},
    blocked: {},
    suspended: {},
    deleted: {},
  };

  posts.owner.multi = await createPost(accounts.owner, {
    title: `${fixture.searchToken} spring ${fixture.wildcardToken}%_`,
    contentJson: richContent(),
    contentHtml: `<p>sp<strong>ring</strong> ${fixture.searchToken} ${fixture.wildcardToken}%_ &amp; &lt;tag&gt;</p><p>A</p><p>B</p>`,
    visibility: 'PUBLIC',
    publish: true,
    tags: [fixture.searchToken, `${fixture.searchToken}tag`],
  }, 'owner-multi');
  posts.owner.universe = await createPost(accounts.owner, {
    title: `M7 ${fixture.searchToken} universe`,
    contentJson: plainContent(`universe ${fixture.searchToken}`),
    contentHtml: `<p>universe ${fixture.searchToken}</p>`,
    visibility: 'UNIVERSE',
    publish: true,
    tags: [`${fixture.searchToken}universe`],
  }, 'owner-universe');
  posts.owner.private = await createPost(accounts.owner, {
    title: `M7 ${fixture.searchToken} private`,
    contentJson: plainContent(`private ${fixture.searchToken}`),
    contentHtml: `<p>private ${fixture.searchToken}</p>`,
    visibility: 'PRIVATE',
    publish: true,
    tags: [`${fixture.searchToken}private`],
  }, 'owner-private');
  posts.owner.draft = await createPost(accounts.owner, {
    title: `M7 ${fixture.searchToken} draft`,
    contentJson: plainContent(`draft ${fixture.searchToken}`),
    contentHtml: `<p>draft ${fixture.searchToken}</p>`,
    visibility: 'PRIVATE',
    publish: false,
    tags: [`${fixture.searchToken}draft`],
  }, 'owner-draft');
  posts.owner.wildExact = await createPost(accounts.owner, {
    title: `M7 ${fixture.wildcardToken}%_ exact`,
    contentJson: plainContent(`wild ${fixture.wildcardToken}%_ exact`),
    contentHtml: `<p>wild ${fixture.wildcardToken}%_ exact</p>`,
    visibility: 'PUBLIC',
    publish: true,
    tags: [],
  }, 'owner-wild-exact');

  const pagePosts = [];
  for (let index = 1; index <= 21; index += 1) {
    pagePosts.push(await createPost(accounts.owner, {
      title: `${fixture.pageToken} ${String(index).padStart(2, '0')}`,
      contentJson: plainContent(`${fixture.pageToken} body ${index}`),
      contentHtml: `<p>${fixture.pageToken} body ${index}</p>`,
      visibility: 'PUBLIC',
      publish: true,
      tags: [],
    }, `owner-page-${index}`));
  }
  posts.owner.page = pagePosts;

  for (const label of ['actor', 'friend', 'oneWay', 'blocked', 'suspended', 'deleted']) {
    posts[label].public = await createPost(accounts[label], {
      title: `M7 ${label} ${fixture.searchToken} public`,
      contentJson: plainContent(`${label} ${fixture.searchToken} public`),
      contentHtml: `<p>${label} ${fixture.searchToken} public</p>`,
      visibility: 'PUBLIC',
      publish: true,
      tags: [`${label}${fixture.searchToken}`],
    }, `${label}-public`);
  }
  posts.unrelated.wildDecoy = await createPost(accounts.unrelated, {
    title: `M7 wild decoy`,
    contentJson: plainContent(`wild ${fixture.wildcardToken}XX decoy`),
    contentHtml: `<p>wild ${fixture.wildcardToken}XX decoy</p>`,
    visibility: 'PUBLIC',
    publish: true,
    tags: [],
  }, 'unrelated-wild-decoy');

  for (const [label, group] of Object.entries(posts)) {
    evidence.fixture.postIds[label] = Object.fromEntries(
      Object.entries(group)
        .filter(([, value]) => Array.isArray(value) || value?.id)
        .map(([key, value]) => [key, Array.isArray(value) ? value.map((item) => item.id) : value.id]),
    );
  }
  return posts;
}

function richContent() {
  const longText = ' 유니코드검증'.repeat(35);
  return {
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'sp' },
          { type: 'text', text: `ring ${fixture.searchToken} ${fixture.wildcardToken}%_ & <tag>${longText}`, marks: [{ type: 'bold' }] },
        ],
      },
      { type: 'paragraph', content: [{ type: 'text', text: 'A' }] },
      { type: 'paragraph', content: [{ type: 'text', text: 'B' }] },
    ],
  };
}

function plainContent(text) {
  return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] };
}

async function createPost(account, values, label) {
  const response = await apiCall('POST', '/api/v1/posts', account.token, {
    blogId: account.blog.id,
    title: values.title,
    contentJson: values.contentJson,
    contentHtml: values.contentHtml,
    categoryId: null,
    visibility: values.visibility,
    publish: values.publish,
    thumbnailUrl: null,
    tagNames: values.tags,
    images: [],
  });
  const id = response.data?.id;
  check(`fixture-${label}`, response.status === 201 && Number.isInteger(id), 'synthetic post created');
  if (response.status !== 201 || !Number.isInteger(id)) throw new Error(`fixture post ${label} failed`);
  return response.data;
}

async function createRelationships(accounts) {
  await acceptRelationship(accounts.actor, accounts.owner, 'actor-owner');
  await acceptRelationship(accounts.actor, accounts.oneWay, 'actor-one-way');
  await acceptRelationship(accounts.actor, accounts.suspended, 'actor-suspended');
  await acceptRelationship(accounts.actor, accounts.deleted, 'actor-deleted');
  await acceptRelationship(accounts.actor, accounts.friend, 'actor-friend-outgoing');
  await acceptRelationship(accounts.friend, accounts.actor, 'friend-actor-incoming');
  await acceptRelationship(accounts.actor, accounts.blocked, 'actor-blocked-before-block');

  const blocked = await apiCall('POST', `/api/v1/universe/blocks/${accounts.blocked.me.id}`, accounts.actor.token);
  check('fixture-block', blocked.status === 200, 'actor blocked synthetic blocked author after relationship setup');
  if (blocked.status !== 200) throw new Error('fixture block failed');
  evidence.fixture.relationshipLabels.push(
    'actor→owner:outgoing',
    'actor↔friend:friends',
    'actor→oneWay:following-only',
    'actor→suspended:following-only',
    'actor→deleted:following-only',
    'actor→blocked:relationship-removed-by-block',
  );
}

async function acceptRelationship(from, to, label) {
  const request = await apiCall('POST', `/api/v1/universe/requests/${to.me.id}`, from.token);
  check(`fixture-${label}-request`, request.status === 201 && Number.isInteger(request.data?.requestId), 'universe request created');
  if (request.status !== 201) throw new Error(`fixture relation ${label} request failed`);
  let requestId = request.data?.requestId;
  if (!Number.isInteger(requestId)) {
    const received = await apiCall('GET', '/api/v1/universe/requests/received?page=0&size=100', to.token);
    const item = received.data?.items?.find((entry) => entry.user?.id === from.me.id);
    requestId = item?.requestId;
  }
  if (!Number.isInteger(requestId)) throw new Error(`fixture relation ${label} request ID missing`);
  const accepted = await apiCall('POST', `/api/v1/universe/requests/${requestId}/accept`, to.token);
  check(`fixture-${label}-accept`, accepted.status === 200, 'universe request accepted');
  if (accepted.status !== 200) throw new Error(`fixture relation ${label} accept failed`);
}

async function writeFixtureSeed(accounts, posts) {
  const seed = {
    milestone: 'M7',
    createdAt: new Date().toISOString(),
    accounts: Object.fromEntries(Object.entries(accounts).map(([label, account]) => [label, {
      userId: account.me.id,
      blogId: account.blog.id,
      nickname: account.me.nickname,
      postIds: Object.fromEntries(Object.entries(posts[label] ?? {}).map(([key, value]) => [
        key,
        Array.isArray(value) ? value.map((post) => post.id) : value?.id ?? null,
      ])),
    }])),
  };
  await writeFile(seedFile, JSON.stringify(seed, null, 2));
}

async function waitForPrivilegedStatusFixtures(accounts, posts) {
  let ready = await readStatusReadyFile(accounts, posts);
  if (!ready && waitForStatusFixtures) {
    const deadline = Date.now() + statusWaitMs;
    while (!ready && Date.now() < deadline) {
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000));
      ready = await readStatusReadyFile(accounts, posts);
    }
  }
  if (ready) {
    check('status-fixture-ready', true, 'synthetic suspended/deleted DB fixture acknowledged');
    return true;
  }
  if (requireStatusFixtures) {
    notRun('status-fixture-ready', `root must mutate only synthetic DB and create ${statusReadyFile}`);
  } else {
    notRun('status-fixture-ready', 'M7_REQUIRE_STATUS_FIXTURES=0');
  }
  return false;
}

async function readStatusReadyFile(accounts, posts) {
  try {
    const raw = await readFile(statusReadyFile, 'utf8');
    const json = JSON.parse(raw);
    if (json?.ready !== true) return false;
    const expected = {
      suspendedUserId: accounts.suspended.me.id,
      deletedUserId: accounts.deleted.me.id,
      suspendedPostId: posts.suspended.public.id,
      deletedPostId: posts.deleted.public.id,
    };
    return Object.entries(expected).every(([key, value]) => (
      Object.prototype.hasOwnProperty.call(json, key)
        && Number.isInteger(Number(json[key]))
        && Number(json[key]) === Number(value)
    ));
  } catch {
    return false;
  }
}

async function runHttpAcceptance(accounts, posts) {
  const snapshotDiffSelfCheck = idSnapshotDiffSelfCheck();
  check(
    'qa-snapshot-diff-selfcheck',
    snapshotDiffSelfCheck,
    'snapshot comparison detects both missing and unexpected IDs instead of serializing Set as {}',
  );
  const publicFeedBefore = await apiCall('GET', '/api/v1/feed/public?page=0&size=100&sort=latest');
  checkPage('http-public-feed', publicFeedBefore, true);
  check('http-public-feed-no-store', privateNoStore(publicFeedBefore.cacheControl), 'public feed is private/no-store');
  check('http-public-feed-envelope', publicFeedBefore.envelope, 'public feed uses common envelope');
  const publicIds = itemIds(publicFeedBefore);
  check('public-includes-owner-public', publicIds.has(posts.owner.multi.id), 'PUBLIC feed includes owner PUBLIC post');
  check('public-excludes-owner-universe', !publicIds.has(posts.owner.universe.id), 'PUBLIC feed excludes UNIVERSE post');
  check('public-excludes-owner-private-draft', !publicIds.has(posts.owner.private.id) && !publicIds.has(posts.owner.draft.id), 'PUBLIC feed excludes PRIVATE and draft');
  check('public-includes-blocked-author', publicIds.has(posts.blocked.public.id), 'PUBLIC reading is not hidden by block');
  if (statusReady) {
    check('public-includes-suspended-author', publicIds.has(posts.suspended.public.id), 'existing SUSPENDED author PUBLIC post remains visible');
    check('public-excludes-deleted-author', !publicIds.has(posts.deleted.public.id), 'deleted author post is excluded');
  }

  const malformedPublic = await apiCall('GET', '/api/v1/feed/public?page=0&size=20', 'not-a-valid-bearer');
  check('auth-malformed-public', malformedPublic.status === 401 && malformedPublic.errorCode === 'AUTH_004', 'malformed Bearer is not downgraded to guest');
  checkErrorResponseHeaders('auth-malformed-public', malformedPublic);
  const missingUniverse = await apiCall('GET', '/api/v1/feed/universe?relation=all&page=0&size=20');
  check('auth-missing-universe', missingUniverse.status === 401 && missingUniverse.errorCode === 'AUTH_004', 'guest universe request is AUTH_004');
  checkErrorResponseHeaders('auth-missing-universe', missingUniverse);
  const malformedUniverse = await apiCall('GET', '/api/v1/feed/universe?relation=all&page=0&size=20', 'not-a-valid-bearer');
  check('auth-malformed-universe', malformedUniverse.status === 401 && malformedUniverse.errorCode === 'AUTH_004', 'malformed universe Bearer is AUTH_004');
  checkErrorResponseHeaders('auth-malformed-universe', malformedUniverse);
  const malformedSearch = await apiCall('GET', `/api/v1/search?keyword=${encodeURIComponent(fixture.searchToken)}&type=ALL&page=0&size=20`, 'not-a-valid-bearer');
  check('auth-malformed-search', malformedSearch.status === 401 && malformedSearch.errorCode === 'AUTH_004', 'malformed search Bearer is AUTH_004');
  checkErrorResponseHeaders('auth-malformed-search', malformedSearch);
  const expiredToken = process.env.M7_EXPIRED_TOKEN;
  if (expiredToken) {
    for (const [label, path] of [
      ['public', '/api/v1/feed/public?page=0&size=20'],
      ['search', `/api/v1/search?keyword=${encodeURIComponent(fixture.searchToken)}&type=ALL&page=0&size=20`],
      ['universe', '/api/v1/feed/universe?relation=all&page=0&size=20'],
    ]) {
      const response = await apiCall('GET', path, expiredToken);
      check(`auth-expired-${label}`, response.status === 401 && response.errorCode === 'AUTH_002', 'expired token is AUTH_002 and never guest-downgraded');
      checkErrorResponseHeaders(`auth-expired-${label}`, response);
    }
  } else {
    notRun('auth-expired-token', 'set M7_EXPIRED_TOKEN from an isolated runtime fixture for AUTH_002 checks');
  }

  const actorAll = await feed('all', accounts.actor.token);
  const actorFriends = await feed('friends', accounts.actor.token);
  const actorFollowing = await feed('following', accounts.actor.token);
  assertFeedRelation('universe-all', actorAll, [posts.owner.multi.id, posts.owner.universe.id, posts.friend.public.id, posts.oneWay.public.id], [posts.actor.public.id, posts.owner.private.id, posts.owner.draft.id, posts.blocked.public.id, posts.unrelated.public?.id]);
  assertFeedRelation('universe-friends', actorFriends, [posts.friend.public.id], [posts.owner.multi.id, posts.oneWay.public.id, posts.owner.universe.id]);
  assertFeedRelation('universe-following', actorFollowing, [posts.owner.multi.id, posts.owner.universe.id, posts.oneWay.public.id], [posts.friend.public.id, posts.blocked.public.id]);
  if (statusReady) {
    const allIds = itemIds(actorAll);
    const followingIds = itemIds(actorFollowing);
    check('universe-all-includes-suspended', allIds.has(posts.suspended.public.id), 'SUSPENDED outgoing author remains readable');
    check('universe-following-includes-suspended', followingIds.has(posts.suspended.public.id), 'SUSPENDED outgoing author appears in following-only feed');
    check('universe-excludes-deleted', !allIds.has(posts.deleted.public.id) && !followingIds.has(posts.deleted.public.id), 'deleted author is excluded from universe feed');
  }

  const invalidRelation = await apiCall('GET', '/api/v1/feed/universe?relation=FRIENDS&page=0&size=20', accounts.actor.token);
  check('feed-invalid-relation', invalidRelation.status === 400 && invalidRelation.errorCode === 'VALIDATION_001', 'invalid relation is VALIDATION_001');
  checkErrorResponseHeaders('feed-invalid-relation', invalidRelation);
  const invalidFeedSize = await apiCall('GET', '/api/v1/feed/public?page=0&size=101', accounts.actor.token);
  check('feed-invalid-size', invalidFeedSize.status === 400 && invalidFeedSize.errorCode === 'VALIDATION_001', 'size>100 is VALIDATION_001');
  checkErrorResponseHeaders('feed-invalid-size', invalidFeedSize);

  const page0 = await apiCall('GET', `/api/v1/feed/public?keyword=${encodeURIComponent(fixture.pageToken)}&page=0&size=20&sort=latest`, accounts.actor.token);
  const page1 = await apiCall('GET', `/api/v1/feed/public?keyword=${encodeURIComponent(fixture.pageToken)}&page=1&size=20&sort=latest`, accounts.actor.token);
  checkPage('feed-page0', page0, true);
  checkPage('feed-page1', page1, true);
  const page0Ids = itemIds(page0);
  const page1Ids = itemIds(page1);
  check('feed-21-page-bound', Number(page0.data?.totalElements) >= 21 && page0Ids.size === 20 && page1Ids.size > 0, '21+ items cross page boundary with exact page size');
  check('feed-page-disjoint', [...page0Ids].every((id) => !page1Ids.has(id)), 'feed pages are disjoint');

  const actorFollowingBefore = await apiCall('GET', `/api/v1/feed/universe?relation=following&keyword=${encodeURIComponent(fixture.searchToken)}&page=0&size=20`, accounts.actor.token);
  const notificationsBefore = await syntheticNotificationSnapshot(accounts.actor.me.id);
  const searchAll = await search(fixture.searchToken, 'ALL', accounts.owner.token);
  const searchPost = await search(fixture.searchToken, 'POST', accounts.owner.token);
  const publicFeedAfter = await apiCall('GET', '/api/v1/feed/public?page=0&size=100&sort=latest', accounts.actor.token);
  const actorFollowingAfter = await apiCall('GET', `/api/v1/feed/universe?relation=following&keyword=${encodeURIComponent(fixture.searchToken)}&page=0&size=20`, accounts.actor.token);
  const notificationsAfter = await syntheticNotificationSnapshot(accounts.actor.me.id);
  const beforeView = findItem(publicFeedBefore, posts.owner.multi.id)?.viewCount;
  const afterView = findItem(publicFeedAfter, posts.owner.multi.id)?.viewCount;
  check('read-no-view-side-effect', Number.isInteger(beforeView) && Number.isInteger(afterView) && beforeView === afterView, 'feed/search reads do not increment viewCount');
  check(
    'read-no-notification-side-effect',
    notificationsBefore.available
      && notificationsAfter.available
      && notificationsBefore.count === notificationsAfter.count
      && notificationsBefore.fingerprint === notificationsAfter.fingerprint,
    'synthetic DB notification count/fingerprint is unchanged by feed/search reads',
  );
  const snapshotDiff = idSnapshotDiff(itemIds(actorFollowingBefore), itemIds(actorFollowingAfter));
  check(
    'read-snapshot-no-mutation',
    snapshotDiff.missing.length === 0 && snapshotDiff.unexpected.length === 0,
    `repeated read snapshot has same visible IDs (missing=${snapshotDiff.missing.join(',') || 'none'}, unexpected=${snapshotDiff.unexpected.join(',') || 'none'})`,
  );

  const allIds = itemIds(searchAll);
  const matched = findItem(searchAll, posts.owner.multi.id);
  check('search-all-page', searchAll.status === 200 && Array.isArray(searchAll.data?.items), 'search ALL returns a page');
  check('search-all-distinct', allIds.size === (searchAll.data?.items?.length ?? -1), 'search ALL removes multi-field duplicates by post ID');
  check('search-matched-types', matched?.matchedTypes?.join(',') === 'POST,BLOG,USER,TAG', 'matchedTypes contains all scopes in canonical order');
  check('search-count-snapshot', countsHaveKeys(searchAll.data?.counts), 'search counts includes ALL/POST/BLOG/USER/TAG');
  check('search-post-tab', itemIds(searchPost).has(posts.owner.multi.id), 'POST tab contains rich multi-match post');
  check('search-post-count-overlap', Number(searchAll.data?.counts?.ALL) <= Number(searchAll.data?.counts?.POST ?? 0) + Number(searchAll.data?.counts?.BLOG ?? 0) + Number(searchAll.data?.counts?.USER ?? 0) + Number(searchAll.data?.counts?.TAG ?? 0), 'facet counts may overlap while ALL remains distinct');
  check('search-snippet-bound', [...(matched?.excerpt ?? '')].length <= 161, 'search snippet is at most 160 code points plus ellipsis');
  check('search-owner-private-draft', itemIds(searchAll).has(posts.owner.private.id) && itemIds(searchAll).has(posts.owner.draft.id), 'owner search includes own PRIVATE and draft');

  const actorSearch = await search(fixture.searchToken, 'ALL', accounts.actor.token);
  check('search-acl-hides-private-draft', !itemIds(actorSearch).has(posts.owner.private.id) && !itemIds(actorSearch).has(posts.owner.draft.id), 'non-owner search excludes PRIVATE and draft');

  const springSearch = await search('spring', 'POST', accounts.actor.token);
  const ampSearch = await search('&', 'POST', accounts.actor.token);
  const ampFalseSearch = await search('amp', 'POST', accounts.actor.token);
  const tagSearch = await search('<tag>', 'POST', accounts.actor.token);
  const markNameSearch = await search('strong', 'POST', accounts.actor.token);
  check('search-inline-mark-word', itemIds(springSearch).has(posts.owner.multi.id), 'inline mark does not split spring');
  check('search-entity-actual', itemIds(ampSearch).has(posts.owner.multi.id) && !itemIds(ampFalseSearch).has(posts.owner.multi.id), 'actual ampersand is not HTML entity amp');
  check('search-text-tag-literal', itemIds(tagSearch).has(posts.owner.multi.id), 'literal <tag> text is searchable');
  check('search-mark-name-excluded', !itemIds(markNameSearch).has(posts.owner.multi.id), 'mark name is not searchable text');

  const wildcardSearch = await search(`${fixture.wildcardToken}%_`, 'POST', accounts.actor.token);
  check('search-wildcards-literal', itemIds(wildcardSearch).has(posts.owner.wildExact.id) && !itemIds(wildcardSearch).has(posts.unrelated.wildDecoy.id), 'percent/underscore are literal prepared parameters');

  const blankSearch = await apiCall('GET', '/api/v1/search?keyword=&type=ALL&page=0&size=20', accounts.actor.token);
  const missingSearch = await apiCall('GET', '/api/v1/search?type=ALL&page=0&size=20', accounts.actor.token);
  check('search-blank-validation', blankSearch.status === 400 && blankSearch.errorCode === 'VALIDATION_001', 'blank search keyword is explicit VALIDATION_001');
  checkErrorResponseHeaders('search-blank-validation', blankSearch);
  check('search-missing-validation', missingSearch.status === 400 && missingSearch.errorCode === 'VALIDATION_001', 'missing search keyword is explicit VALIDATION_001');
  checkErrorResponseHeaders('search-missing-validation', missingSearch);

  for (const [label, response] of [
    ['search-all', searchAll],
    ['search-post', searchPost],
    ['search-spring', springSearch],
  ]) {
    check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} is private/no-store`);
    check(`${label}-envelope`, response.envelope, `${label} uses common envelope`);
  }

  const relationCounts = await readRelationCounts(accounts.actor.token);
  check('relation-counts-supplied', relationCounts.every((item) => item.status === 200 && Number.isInteger(item.total)), 'right-rail relation counts come from three existing list endpoints');
  check('relation-friends-nonempty', relationCounts.find((item) => item.kind === 'friends')?.total >= 1, 'friends count is not replaced by an error-as-zero value');

  if (statusReady) await runStatusAuthChecks(accounts, posts);
  else {
    notRun('auth-expired-status-matrix', 'suspended/deleted synthetic DB mutation was not acknowledged');
  }

  const verification = await verifyVersionManifest();
  evidence.verification = verification.evidence;
  if (verification.ok) check('source-runtime-sha', true, 'version manifest source hashes and independently computed immutable JAR SHA-256 match');
  else if (verification.notRun) notRun('source-runtime-sha', verification.detail);
  else check('source-runtime-sha', false, verification.detail);
}

async function runStatusAuthChecks(accounts, posts) {
  const suspendedPublic = await apiCall('GET', '/api/v1/feed/public?page=0&size=100', accounts.suspended.token);
  const suspendedSearch = await search(fixture.searchToken, 'ALL', accounts.suspended.token);
  const suspendedUniverse = await feed('all', accounts.suspended.token);
  check('auth-suspended-public-read', suspendedPublic.status === 200, 'valid suspended viewer may read public feed');
  check('auth-suspended-search-read', suspendedSearch.status === 200, 'valid suspended viewer may read search');
  check('auth-suspended-universe-read', suspendedUniverse.status === 200, 'valid suspended viewer may read universe endpoint');
  check('auth-suspended-author-visible', itemIds(suspendedPublic).has(posts.suspended.public.id), 'suspended author existing PUBLIC post remains visible');

  for (const [label, path] of [
    ['public', '/api/v1/feed/public?page=0&size=20'],
    ['search', `/api/v1/search?keyword=${encodeURIComponent(fixture.searchToken)}&type=ALL&page=0&size=20`],
    ['universe', '/api/v1/feed/universe?relation=all&page=0&size=20'],
  ]) {
    const response = await apiCall('GET', path, accounts.deleted.token);
    check(`auth-deleted-${label}`, response.status === 404 && response.errorCode === 'USER_001', 'valid token for deleted user is not downgraded to guest');
    checkErrorResponseHeaders(`auth-deleted-${label}`, response);
  }
}

async function feed(relation, token) {
  return apiCall('GET', `/api/v1/feed/universe?relation=${relation}&page=0&size=100&sort=latest`, token);
}

async function search(keyword, type, token) {
  return apiCall('GET', `/api/v1/search?keyword=${encodeURIComponent(keyword)}&type=${type}&page=0&size=100&sort=latest`, token);
}

function assertFeedRelation(label, response, included, excluded) {
  const ids = itemIds(response);
  check(`${label}-page`, response.status === 200 && Array.isArray(response.data?.items), `${label} returns PageResponse`);
  check(`${label}-includes`, included.every((id) => ids.has(id)), `${label} includes expected relation-visible posts`);
  check(`${label}-excludes`, excluded.filter(Number.isInteger).every((id) => !ids.has(id)), `${label} excludes ACL-invisible posts`);
  check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} is private/no-store`);
}

async function readRelationCounts(token) {
  const values = [];
  for (const kind of ['following', 'friends', 'followers']) {
    const response = await apiCall('GET', `/api/v1/universe/${kind}?page=0&size=1`, token);
    values.push({ kind, status: response.status, total: response.data?.totalElements });
  }
  return values;
}

async function syntheticNotificationSnapshot(userId) {
  if (!Number.isInteger(userId)) return { available: false, fingerprint: 'invalid-user-id' };
  const userIdSql = String(userId).replace(/[^0-9]/g, '');
  if (!userIdSql) return { available: false, fingerprint: 'invalid-user-id' };
  const sql = `SELECT COUNT(*), SHA2(COALESCE(GROUP_CONCAT(CONCAT_WS(':', id, receiver_user_id, actor_user_id, type, target_type, target_id, created_at, read_at, deleted_at) ORDER BY id SEPARATOR '|'), ''), 256) FROM notifications WHERE receiver_user_id = ${userIdSql};`;
  try {
    const output = await runSyntheticDbSql(sql);
    const [count, fingerprint] = output.trim().split(/\s+/);
    if (!/^\d+$/.test(count ?? '') || !/^[0-9a-f]{64}$/i.test(fingerprint ?? '')) {
      return { available: false, fingerprint: 'malformed-sql-result' };
    }
    return { available: true, count: Number(count), fingerprint };
  } catch {
    return { available: false, fingerprint: 'synthetic-sql-unavailable' };
  }
}

async function runSyntheticDbSql(sql) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn('python3', [runtimeSqlHelper, 'sql'], {
      cwd: root,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString(); });
    child.stderr.on('data', () => {});
    child.on('error', () => rejectPromise(new Error('synthetic SQL helper unavailable')));
    child.on('close', (code) => {
      if (code === 0) resolvePromise(stdout);
      else rejectPromise(new Error('synthetic SQL helper failed'));
    });
    child.stdin.end(sql);
  });
}

async function verifyVersionManifest() {
  const emptyEvidence = {
    manifestPath: versionManifestFile,
    sourceSha256: null,
    actualSourceSha256: null,
    jarSha256: null,
    actualJarSha256: null,
    filesChecked: 0,
  };
  let manifest;
  try {
    manifest = JSON.parse(await readFile(versionManifestFile, 'utf8'));
  } catch {
    return { ok: false, notRun: true, detail: `version manifest is not ready: ${versionManifestFile}`, evidence: emptyEvidence };
  }
  const files = Array.isArray(manifest.files) ? manifest.files : [];
  const jarPathValue = typeof manifest.jarPath === 'string' ? manifest.jarPath : '';
  const expectedSourceSha = manifest.sourceSha256;
  const expectedJarSha = manifest.jarSha256;
  const evidenceBase = {
    ...emptyEvidence,
    sourceSha256: typeof expectedSourceSha === 'string' ? expectedSourceSha : null,
    jarSha256: typeof expectedJarSha === 'string' ? expectedJarSha : null,
  };
  if (!/^[0-9a-f]{64}$/i.test(expectedSourceSha ?? '')
    || !/^[0-9a-f]{64}$/i.test(expectedJarSha ?? '')
    || !jarPathValue
    || files.length === 0) {
    return { ok: false, detail: 'version manifest must contain sourceSha256, jarSha256, jarPath and files', evidence: evidenceBase };
  }

  const entries = files.map((entry) => ({
    path: entry?.path,
    expectedSha: entry?.sha256,
  }));
  if (entries.some((entry) => typeof entry.path !== 'string' || !/^[0-9a-f]{64}$/i.test(entry.expectedSha ?? ''))) {
    return { ok: false, detail: 'version manifest has an invalid source file entry', evidence: evidenceBase };
  }
  entries.sort((left, right) => (left.path < right.path ? -1 : left.path > right.path ? 1 : 0));
  const actualHashes = [];
  for (const entry of entries) {
    const filePath = resolve(root, entry.path);
    if (!isInsideRoot(filePath)) {
      return { ok: false, detail: `version manifest path escapes repository: ${entry.path}`, evidence: evidenceBase };
    }
    try {
      const actualSha = await sha256File(filePath);
      actualHashes.push({ path: entry.path, sha256: actualSha });
      if (actualSha !== entry.expectedSha) {
        return { ok: false, detail: `source file hash mismatch: ${entry.path}`, evidence: { ...evidenceBase, filesChecked: actualHashes.length } };
      }
    } catch {
      return { ok: false, detail: `source file is unavailable: ${entry.path}`, evidence: { ...evidenceBase, filesChecked: actualHashes.length } };
    }
  }

  const aggregateInput = actualHashes
    .map((entry) => `${entry.path}\0${entry.sha256}\n`)
    .join('');
  const actualSourceSha = createHash('sha256').update(aggregateInput, 'utf8').digest('hex');
  const jarPath = resolve(root, jarPathValue);
  if (!isInsideRoot(jarPath)) {
    return { ok: false, detail: `version manifest JAR path escapes repository: ${jarPathValue}`, evidence: { ...evidenceBase, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  }
  let actualJarSha;
  try {
    actualJarSha = await sha256File(jarPath);
  } catch {
    return { ok: false, detail: `immutable runtime JAR is unavailable: ${jarPathValue}`, evidence: { ...evidenceBase, actualSourceSha256: actualSourceSha, filesChecked: actualHashes.length } };
  }
  const finalEvidence = {
    ...evidenceBase,
    actualSourceSha256: actualSourceSha,
    actualJarSha256: actualJarSha,
    filesChecked: actualHashes.length,
    jarPath: jarPathValue,
  };
  if (actualSourceSha !== expectedSourceSha) return { ok: false, detail: 'source aggregate SHA-256 mismatch', evidence: finalEvidence };
  if (actualJarSha !== expectedJarSha) return { ok: false, detail: 'immutable runtime JAR SHA-256 mismatch', evidence: finalEvidence };
  return { ok: true, evidence: finalEvidence };
}

async function sha256File(filePath) {
  const bytes = await readFile(filePath);
  return createHash('sha256').update(bytes).digest('hex');
}

function isInsideRoot(filePath) {
  return filePath === root || filePath.startsWith(`${root}/`);
}

async function runBrowserAcceptance(accounts, posts) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  try {
    await captureDesignOriginal();
    await signInUi(page, fixture.accounts.actor);
    const feedResponsePromise = waitForFeedResponse(page);
    await page.goto(`${frontendUrl}/`, { waitUntil: 'domcontentloaded' });
    await feedResponsePromise;
    await waitForAppReady(page);
    check('browser-actor-viewport', await page.evaluate(() => window.innerWidth) === 1440, 'MainPage CSS viewport is 1440px');
    check('browser-main-hero', await hasText(page, '유니버스 새 소식'), 'Main Feed hero is rendered');
    check('browser-main-tabs', await hasAllText(page, ['전체', '친구', '내가 발견한']), 'three canonical relation tabs are rendered');
    check(
      'browser-main-no-private',
      !(await hasPostCardTitle(page, posts.owner.private.title)) && !(await hasPostCardTitle(page, posts.owner.draft.title)),
      'owner PRIVATE and draft cards are not shown on actor MainPage',
    );
    await captureProduct(page, 'main-actor');

    const requestPaths = [];
    const requestListener = (request) => {
      if (request.url().includes('/api/v1/feed/')) requestPaths.push(new URL(request.url()).pathname + new URL(request.url()).search);
    };
    page.on('request', requestListener);
    await clickFeedTabAndWait(page, '친구', 'friends');
    await clickFeedTabAndWait(page, '내가 발견한', 'following');
    check('browser-relation-url-contract', requestPaths.some((path) => path.includes('relation=friends')) && requestPaths.some((path) => path.includes('relation=following')), 'FE uses canonical friends/following relation query');
    page.off('request', requestListener);

    const searchInput = page.locator('header').getByRole('searchbox', { name: '검색', exact: true });
    await searchInput.fill(fixture.uiSearchToken);
    const initialSearchResponsePromise = waitForSearchResponse(page);
    const [, initialSearchResponse] = await Promise.all([
      page.waitForURL((url) => url.pathname.endsWith('/search') && url.searchParams.get('keyword') === fixture.uiSearchToken, { timeout: 15_000 }),
      initialSearchResponsePromise,
      searchInput.press('Enter'),
    ]);
    const initialSearchPayload = await responsePayload(initialSearchResponse);
    const initialSearchFirstItem = initialSearchPayload?.data?.items?.[0];
    await waitForSearchReady(page);
    check('browser-search-route', new URL(page.url()).pathname.endsWith('/search'), 'TopBar search navigates to /search');
    check('browser-search-tabs', await hasAllText(page, ['전체', '글', '블로그', '사용자', '태그']), 'SearchPage renders five canonical tabs');
    check('browser-search-post-centric-copy', await hasText(page, '블로그·사용자·태그 조건에 맞는 글을 검색합니다.'), 'SearchPage explains BLOG/USER/TAG as post facets');
    check(
      'browser-search-result',
      initialSearchFirstItem && await waitForSearchResultItem(page, initialSearchFirstItem, accounts.actor.me.id),
      'SearchPage renders the first API result item as a card with its canonical href',
    );
    await captureProduct(page, 'search-actor');
    const reloadSearchResponsePromise = waitForSearchResponse(page);
    await Promise.all([
      reloadSearchResponsePromise,
      page.reload({ waitUntil: 'domcontentloaded' }),
    ]);
    await waitForSearchReady(page);
    check('browser-search-refresh-state', new URL(page.url()).searchParams.get('keyword') === fixture.searchToken, 'search keyword survives refresh through URL state');
    check('browser-search-input-sync', await searchInputValue(page) === fixture.searchToken, 'search input is synchronized from URL');

    await runBrowserSearchInteractions(page, posts, accounts.actor.me.id);

    await runBrowserAccountSwitch(context, page, accounts, posts);
    await runBrowserStaleAccountProbe(accounts, posts);
  } finally {
    await context.close();
  }

  await runGuestProtectedTabCheck();
}

async function runBrowserSearchInteractions(page, posts, viewerId) {
  const tabs = [
    ['POST', '글'],
    ['BLOG', '블로그'],
    ['USER', '사용자'],
    ['TAG', '태그'],
    ['ALL', '전체'],
  ];
  for (const [type, label] of tabs) {
    const responsePromise = waitForSearchResponse(page, (response) => (
      new URL(response.url()).searchParams.get('type') === type
    ));
    const tab = page.locator('[role="tab"]').filter({ hasText: label }).first();
    await tab.click();
    const response = await responsePromise;
    const payload = await responsePayload(response);
    await waitForSearchReady(page);
    const url = new URL(page.url());
    const count = payload?.data?.counts?.[type];
    const tabCountReady = Number.isInteger(count) && await waitForSearchTabCount(page, label, count);
    const tabText = (await tab.innerText()).trim();
    check(`browser-search-tab-${type}-url`, url.searchParams.get('type') === type, `${type} tab updates URL type`);
    check(`browser-search-tab-${type}-response`, response.status() === 200 && Number.isInteger(count), `${type} tab response includes its snapshot count`);
    check(`browser-search-tab-${type}-count`, tabCountReady && tabText.endsWith(String(count)), `${type} tab label matches API snapshot count after render`);
  }

  const pageSearchUrl = `${frontendUrl}/search?keyword=${encodeURIComponent(fixture.pageToken)}&type=ALL&page=0&size=20&sort=latest`;
  const page0Response = await navigateSearchAndWait(page, pageSearchUrl);
  const page0Payload = await responsePayload(page0Response);
  const page0Items = page0Payload?.data?.items ?? [];
  const page0Ids = new Set(page0Items.map((item) => Number(item.id)).filter(Number.isInteger));
  check('browser-search-pagination-page0', page0Response.status() === 200 && Number(page0Payload?.data?.totalElements) >= 21 && page0Items.length === 20, 'search page 0 returns 20 of 21+ synthetic posts');

  const page1ResponsePromise = waitForSearchResponse(page, (response) => (
    new URL(response.url()).searchParams.get('page') === '1'
  ));
  await page.getByRole('button', { name: '2 페이지', exact: true }).click();
  const page1Response = await page1ResponsePromise;
  const page1Payload = await responsePayload(page1Response);
  const page1Items = page1Payload?.data?.items ?? [];
  const page1Ids = new Set(page1Items.map((item) => Number(item.id)).filter(Number.isInteger));
  await waitForSearchReady(page);
  check('browser-search-pagination-page1-url', new URL(page.url()).searchParams.get('page') === '1', 'search pagination updates URL page');
  check('browser-search-pagination-disjoint', page1Items.length > 0 && [...page0Ids].every((id) => !page1Ids.has(id)), 'search pages are disjoint across the 21+ fixture');

  await page.goBack({ waitUntil: 'domcontentloaded' });
  await waitForSearchReady(page);
  check('browser-search-back-restores-page0', new URL(page.url()).searchParams.get('page') === '0' && page0Items.length > 0 && await waitForPostCardTitle(page, page0Items[0].title), 'browser back restores page 0 URL and result card');

  const emptyKeyword = `${fixture.searchToken}-no-match-${suffix.slice(-6)}`;
  const emptyResponse = await navigateSearchAndWait(page, `${frontendUrl}/search?keyword=${encodeURIComponent(emptyKeyword)}&type=ALL&page=0&size=20&sort=latest`);
  const emptyPayload = await responsePayload(emptyResponse);
  await page.getByText('일치하는 신호가 없습니다.', { exact: true }).waitFor({ state: 'visible', timeout: 15_000 });
  check('browser-search-empty-response', emptyResponse.status() === 200 && Number(emptyPayload?.data?.totalElements) === 0, 'unique search keyword returns an empty API page');
  check('browser-search-empty-no-card', await page.locator('article h2').count() === 0, 'empty search state has no result card');

  let failedOnce = false;
  await page.route('**/api/v1/search*', async (route) => {
    if (!failedOnce) {
      failedOnce = true;
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          data: null,
          error: { code: 'QA_RETRY_500', message: 'synthetic retry failure' },
          timestamp: new Date().toISOString(),
        }),
      });
      return;
    }
    await route.continue();
  });
  try {
    const retryUrl = `${frontendUrl}/search?keyword=${encodeURIComponent(fixture.searchToken)}&type=ALL&page=0&size=20&sort=latest`;
    const failureResponsePromise = waitForSearchResponse(page, (response) => response.status() === 500);
    await page.goto(retryUrl, { waitUntil: 'domcontentloaded' });
    const failureResponse = await failureResponsePromise;
    const retryButton = page.getByRole('button', { name: '다시 시도', exact: true });
    await retryButton.waitFor({ state: 'visible', timeout: 15_000 });
    check('browser-search-retry-error', failureResponse.status() === 500 && await page.getByRole('alert').count() > 0, 'one-time search HTTP 500 renders retryable error state');
    const retryResponsePromise = waitForSearchResponse(page, (response) => response.status() === 200);
    await retryButton.click();
    const retryResponse = await retryResponsePromise;
    const retryPayload = await responsePayload(retryResponse);
    const retryFirstItem = retryPayload?.data?.items?.[0];
    await waitForSearchReady(page);
    check(
      'browser-search-retry-success',
      failedOnce
        && retryResponse.status() === 200
        && retryFirstItem
        && await waitForSearchResultItem(page, retryFirstItem, viewerId),
      'retry calls the real API and restores the first API result card and href',
    );
  } finally {
    await page.unroute('**/api/v1/search*');
  }
}

async function runBrowserAccountSwitch(context, page, accounts, posts) {
  await context.clearCookies();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await signInUi(page, fixture.accounts.owner);
  const privateSearchUrl = `${frontendUrl}/search?keyword=${encodeURIComponent(posts.owner.private.title)}&type=ALL&page=0&size=20&sort=latest`;
  const ownerPrivateResponse = await navigateSearchAndWait(page, privateSearchUrl);
  const ownerPrivatePayload = await responsePayload(ownerPrivateResponse);
  const ownerPrivateFirstItem = ownerPrivatePayload?.data?.items?.[0];
  check(
    'browser-owner-private-visible',
    ownerPrivateFirstItem && await waitForSearchResultItem(page, ownerPrivateFirstItem, accounts.owner.me.id),
    'owner search can see the first PRIVATE result card with its canonical href',
  );

  await context.clearCookies();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await signInUi(page, fixture.accounts.actor);
  const actorPrivateResponse = await navigateSearchAndWait(page, privateSearchUrl);
  const actorPrivatePayload = await responsePayload(actorPrivateResponse);
  check(
    'browser-account-switch-clears-private',
    Number(actorPrivatePayload?.data?.totalElements) === 0 && !(await hasPostCardTitle(page, posts.owner.private.title)),
    'account switch does not retain owner private result card or result total',
  );
  const actorSearchResponse = await navigateSearchAndWait(page, `${frontendUrl}/search?keyword=${encodeURIComponent(fixture.searchToken)}&type=ALL&page=0&size=20&sort=latest`);
  const actorSearchPayload = await responsePayload(actorSearchResponse);
  const actorSearchFirstItem = actorSearchPayload?.data?.items?.[0];
  check(
    'browser-account-switch-actor-result',
    actorSearchFirstItem && await waitForSearchResultItem(page, actorSearchFirstItem, accounts.actor.me.id),
    'new account renders the first ACL-filtered API result card with its canonical href',
  );
}

async function runBrowserStaleAccountProbe(accounts, posts) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  let delayedObserved = false;
  await page.route('**/api/v1/search*', async (route) => {
    if (!delayedObserved) {
      delayedObserved = true;
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 1200));
    }
    try {
      await route.continue();
    } catch {
      // Navigation can cancel the deliberately delayed request; that is part of this probe.
    }
  });
  try {
    await signInUi(page, fixture.accounts.owner);
    const privateSearchUrl = `${frontendUrl}/search?keyword=${encodeURIComponent(posts.owner.private.title)}&type=ALL&page=0&size=20&sort=latest`;
    void waitForSearchResponse(page).catch(() => null);
    await page.goto(privateSearchUrl, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(120);
    await context.clearCookies();
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
    await signInUi(page, fixture.accounts.actor);
    await navigateSearchAndWait(page, privateSearchUrl);
    check('browser-stale-request-observed', delayedObserved, 'delayed owner search request was observed by the stale-response probe');
    check('browser-stale-account-cleared', !(await hasPostCardTitle(page, posts.owner.private.title)), 'late owner response cannot repopulate actor search card');
  } finally {
    await context.close();
  }
}

async function runGuestProtectedTabCheck() {
  const context = await newBrowserContext();
  const page = await context.newPage();
  const requests = [];
  const listener = (request) => {
    if (request.url().includes('/api/v1/feed/universe')) requests.push(request.url());
  };
  page.on('request', listener);
  try {
    const feedResponsePromise = waitForFeedResponse(page);
    await page.goto(`${frontendUrl}/`, { waitUntil: 'domcontentloaded' });
    await feedResponsePromise;
    await waitForAppReady(page);
    await clickExact(page, '친구');
    await page.getByText('로그인', { exact: false }).first().waitFor({ state: 'visible', timeout: 15_000 });
    check('browser-guest-protected-prompt', await hasText(page, '로그인'), 'guest protected tab shows login prompt');
    check('browser-guest-no-protected-call', requests.length === 0, 'guest protected tabs do not call universe API');
  } finally {
    page.off('request', listener);
    await context.close();
  }
}

async function captureDesignOriginal() {
  if (!screenshotsEnabled) {
    notRun('design-original-screenshots', 'M7_SCREENSHOTS=0');
    return;
  }
  const context = await newBrowserContext();
  const main = await context.newPage();
  try {
    await main.goto(pathToFileURL(designMainFile).href, { waitUntil: 'load' });
    check('design-main-original', await hasText(main, '유니버스 새 소식'), 'Main Feed v2 original renders hero');
    await main.screenshot({ path: resolve(outputDir, 'design-main-feed.png'), fullPage: true });
    evidence.screenshots.push('design-main-feed.png');
    const pages = await context.newPage();
    await pages.goto(pathToFileURL(designPagesFile).href, { waitUntil: 'load' });
    await pages.getByRole('button', { name: '검색' }).first().click();
    const screen = pages.locator('[data-screen-label="검색 결과"]:visible');
    await screen.waitFor({ state: 'visible', timeout: 10_000 });
    check('design-search-original', /SCANNING UNIVERSE/.test(await screen.innerText()) && /검색 결과/.test(await screen.innerText()), 'Pages original renders search screen');
    await pages.screenshot({ path: resolve(outputDir, 'design-search.png'), fullPage: true });
    evidence.screenshots.push('design-search.png');
    await pages.close();
  } finally {
    await context.close();
  }
}

async function signInUi(page, account) {
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: /접속하기|로그인/ }).last().click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 15_000 });
  check('browser-signin', !page.url().endsWith('/signin'), 'synthetic account reached application route');
}

async function waitForAppReady(page) {
  await page.locator('main').waitFor({ state: 'visible', timeout: 15_000 });
}

async function clickFeedTabAndWait(page, label, relation) {
  const responsePromise = page.waitForResponse((response) => (
    response.url().includes('/api/v1/feed/universe')
      && new URL(response.url()).searchParams.get('relation') === relation
      && response.status() === 200
  ), { timeout: 15_000 });
  await clickExact(page, label);
  await responsePromise;
}

async function waitForSearchReady(page) {
  await page.locator('main').waitFor({ state: 'visible', timeout: 15_000 });
  const loading = page.getByText('검색 결과를 불러오는 중...', { exact: true });
  if (await loading.count() > 0) {
    await loading.first().waitFor({ state: 'hidden', timeout: 15_000 });
  }
  check('browser-search-eyebrow', await hasText(page, 'SCANNING UNIVERSE'), 'SearchPage has canonical eyebrow');
}

async function waitForFeedResponse(page, predicate = () => true) {
  return page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && response.url().includes('/api/v1/feed/')
      && predicate(response)
  ), { timeout: 15_000 });
}

async function waitForSearchResponse(page, predicate = () => true) {
  return page.waitForResponse((response) => (
    response.request().method() === 'GET'
      && response.url().includes('/api/v1/search')
      && predicate(response)
  ), { timeout: 15_000 });
}

async function responsePayload(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function waitForSearchTabCount(page, label, expectedCount) {
  try {
    await page.waitForFunction(({ tabLabel, count }) => {
      const tab = [...document.querySelectorAll('[role="tab"]')]
        .find((candidate) => candidate.textContent?.trim().startsWith(tabLabel));
      return Boolean(tab?.textContent?.trim().endsWith(String(count)));
    }, { tabLabel: label, count: expectedCount }, { timeout: 15_000 });
    return true;
  } catch {
    return false;
  }
}

async function navigateSearchAndWait(page, url) {
  const responsePromise = waitForSearchResponse(page);
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  const response = await responsePromise;
  await waitForSearchReady(page);
  return response;
}

async function clickExact(page, text) {
  const candidates = [
    page.getByRole('tab', { name: text, exact: true }),
    page.getByRole('button', { name: text, exact: true }),
    page.getByText(text, { exact: true }),
  ];
  for (const candidate of candidates) {
    if (await candidate.count() > 0) {
      await candidate.first().click();
      return;
    }
  }
  throw new Error(`browser control not found: ${text}`);
}

async function searchInputValue(page) {
  const input = page.locator('header').getByRole('searchbox', { name: '검색', exact: true });
  return input.inputValue();
}

async function hasText(page, text) {
  return (await page.getByText(text, { exact: false }).count()) > 0;
}

async function hasPostCardTitle(page, title) {
  return (await page.locator('article').filter({
    has: page.locator('h2', { hasText: title }),
  }).count()) > 0;
}

async function waitForPostCardTitle(page, title) {
  const card = page.locator('article').filter({
    has: page.locator('h2', { hasText: title }),
  }).first();
  try {
    await card.waitFor({ state: 'visible', timeout: 15_000 });
    return true;
  } catch {
    return false;
  }
}

async function waitForSearchResultItem(page, item, viewerId) {
  if (!item || !Number.isInteger(Number(item.id)) || typeof item.title !== 'string') return false;
  const expectedHref = item.publishedAt === null && Number(item.author?.id) === Number(viewerId)
    ? `/edit/${item.id}`
    : `/blog/${item.blogSlug}/${item.id}`;
  const card = page.locator('article').filter({
    has: page.locator('h2', { hasText: item.title }),
  }).first();
  try {
    await card.waitFor({ state: 'visible', timeout: 15_000 });
    const link = page.locator('a').filter({ hasText: item.title }).first();
    await link.waitFor({ state: 'visible', timeout: 15_000 });
    return await link.getAttribute('href') === expectedHref
      && await link.locator('article').count() > 0
      && await link.locator('h2').filter({ hasText: item.title }).count() > 0;
  } catch {
    return false;
  }
}

async function hasAllText(page, texts) {
  const values = await Promise.all(texts.map((text) => hasText(page, text)));
  return values.every(Boolean);
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
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const options = { method, headers };
  if (data !== undefined) options.data = data;
  const response = await apiContext.fetch(path, options);
  const json = await safeJson(response);
  return {
    status: response.status(),
    cacheControl: response.headers()['cache-control'] ?? '',
    success: json?.success === true,
    data: json?.data ?? null,
    errorCode: json?.error?.code ?? null,
    envelope: Boolean(json && typeof json.success === 'boolean' && 'data' in json && 'error' in json && 'timestamp' in json),
  };
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function checkPage(label, response, expectSuccess) {
  check(`${label}-status`, expectSuccess ? response.status === 200 : response.status !== 200, `${label} HTTP status is ${response.status}`);
  check(`${label}-page-shape`, !expectSuccess || Array.isArray(response.data?.items), `${label} has PageResponse items`);
}

function itemIds(response) {
  return new Set((response?.data?.items ?? []).map((item) => Number(item.id)).filter(Number.isInteger));
}

function idSnapshotDiff(before, after) {
  const beforeSorted = [...before].sort((left, right) => left - right);
  const afterSorted = [...after].sort((left, right) => left - right);
  const beforeSet = new Set(beforeSorted);
  const afterSet = new Set(afterSorted);
  return {
    before: beforeSorted,
    after: afterSorted,
    missing: beforeSorted.filter((id) => !afterSet.has(id)),
    unexpected: afterSorted.filter((id) => !beforeSet.has(id)),
  };
}

function idSnapshotDiffSelfCheck() {
  const same = idSnapshotDiff(new Set([9, 2, 7]), new Set([7, 9, 2]));
  const changed = idSnapshotDiff(new Set([9, 2, 7]), new Set([7, 9, 13]));
  return same.missing.length === 0
    && same.unexpected.length === 0
    && changed.missing.length === 1
    && changed.missing[0] === 2
    && changed.unexpected.length === 1
    && changed.unexpected[0] === 13;
}

function findItem(response, id) {
  return (response?.data?.items ?? []).find((item) => Number(item.id) === Number(id));
}

function viewCountOf(response, id) {
  return findItem(response, id)?.viewCount ?? null;
}

function countsHaveKeys(counts) {
  return counts && ['ALL', 'POST', 'BLOG', 'USER', 'TAG'].every((key) => Number.isInteger(counts[key]));
}

function privateNoStore(value) {
  return /private/i.test(value) && /no-store/i.test(value);
}

function checkErrorResponseHeaders(label, response) {
  check(`${label}-no-store`, privateNoStore(response.cacheControl), `${label} error response is private/no-store`);
  check(`${label}-envelope`, response.envelope, `${label} error response uses common envelope`);
}

function check(name, pass, detail) {
  const status = pass ? 'PASS' : 'FAIL';
  evidence.checks.push({ name, status, detail });
  if (!pass) evidence.failures.push(`${name}: ${detail ?? 'failed'}`);
  console.log(`[${status}] ${name}`);
}

function notRun(name, detail) {
  evidence.checks.push({ name, status: 'NOT RUN', detail });
  console.log(`[NOT RUN] ${name}`);
  if (requireFull) evidence.failures.push(`${name}: ${detail ?? 'not run'}`);
}

async function finishEvidence() {
  await writeEvidence();
  const pass = evidence.checks.filter((item) => item.status === 'PASS').length;
  const notRunCount = evidence.checks.filter((item) => item.status === 'NOT RUN').length;
  console.log(JSON.stringify({
    outputDir,
    screenshots: evidence.screenshots.length,
    pass,
    notRun: notRunCount,
    failures: evidence.failures.length,
    fixtureIds: evidence.fixture,
  }));
  if (evidence.failures.length > 0) throw new Error(`M7 acceptance failed (${evidence.failures.length} findings)`);
}

async function writeEvidence() {
  await writeFile(resolve(outputDir, 'evidence.json'), JSON.stringify(evidence, null, 2));
}

function scrubSecrets(value) {
  let result = String(value);
  for (const account of Object.values(fixture.accounts ?? {})) {
    result = result.replaceAll(account.email, '[redacted-email]');
    result = result.replaceAll(account.password, '[redacted-password]');
  }
  for (const marker of [fixture.searchToken, fixture.pageToken, fixture.wildcardToken]) {
    result = result.replaceAll(marker, '[redacted-marker]');
  }
  result = result.replace(/Bearer\s+\S+/gi, 'Bearer [redacted-token]');
  result = result.replace(/[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '[redacted-token]');
  return result;
}
