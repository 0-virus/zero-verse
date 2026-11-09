/**
 * M6 self-contained synthetic acceptance runner.
 *
 * This QA-only runner creates isolated random accounts/blogs/posts and seeds
 * comments through the HTTP API. It then performs the user-facing comment and
 * like mutations in a real 1440px Playwright browser: root comment submit,
 * reply submit, root/reply pagination, edit, soft-delete, like, unlike and
 * refresh. It may leave synthetic data behind; it never targets known user
 * data and never prints or persists credentials, passwords or access tokens.
 *
 * Required environment for a full run:
 *   M6_PLAYWRIGHT_MODULE (when Playwright is not project-local)
 *   M6_FRONTEND_URL (default http://localhost:5173)
 *   M6_API_BASE_URL (default http://localhost:18080)
 *
 * Optional:
 *   M6_CHROME_EXECUTABLE (default macOS system Chrome)
 *   M6_HEADLESS=0 for a headed run
 *   M6_ROLLBACK_PROBE=0 to skip the injected failed-like rollback check
 *   M6_REQUIRE_FULL=0 to allow diagnostic NOT RUN checks
 *
 * Example (from repository root, after backend/frontend runtime startup):
 *   M6_PLAYWRIGHT_MODULE=/path/to/playwright/node_modules/playwright \
 *   node qa/m6-acceptance.mjs
 */

import { createRequire } from 'node:module';
import { randomBytes } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M6_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium, request: requestApi } = require(playwrightModule);

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const frontendUrl = process.env.M6_FRONTEND_URL ?? 'http://localhost:5173';
const apiBaseUrl = process.env.M6_API_BASE_URL ?? 'http://localhost:18080';
const designFile = resolve(root, process.env.M6_DESIGN_FILE ?? 'docs/design/ZeroVerse Pages.dc.html');
const outputDir = resolve(root, 'build/m6-acceptance');
const chromeExecutable = process.env.M6_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const requireFull = process.env.M6_REQUIRE_FULL !== '0';
const rollbackProbe = process.env.M6_ROLLBACK_PROBE !== '0';
const suffix = `${Date.now().toString(36)}${randomBytes(5).toString('hex')}`;

const evidence = {
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  frontendUrl,
  apiBaseUrl,
  fixture: {
    // IDs are synthetic target references; credentials and content markers are
    // intentionally kept only in memory.
    ownerUserId: null,
    actorUserId: null,
    ownerBlogId: null,
    postIds: [],
    rootCommentIds: [],
    replyCommentIds: [],
  },
  screenshots: [],
  checks: [],
  failures: [],
};

const fixture = makeFixtureCredentials(suffix);
let apiContext;
let browser;

try {
  await mkdir(outputDir, { recursive: true });
  apiContext = await requestApi.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });
  browser = await chromium.launch({
    headless: process.env.M6_HEADLESS !== '0',
    executablePath: chromeExecutable,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  const owner = await registerAndSetup(fixture.owner, 'owner');
  const actor = await registerAndSetup(fixture.actor, 'actor');
  evidence.fixture.ownerUserId = owner.me.id;
  evidence.fixture.actorUserId = actor.me.id;
  evidence.fixture.ownerBlogId = owner.blog.id;

  const posts = [];
  for (let index = 1; index <= 2; index += 1) {
    posts.push(await createPublicPost(owner.token, owner.blog.id, fixture.postMarkers[index - 1], index));
  }
  evidence.fixture.postIds = posts.map((post) => post.id);

  const seeded = await seedCommentTree(actor.token, posts[0].id, fixture.commentMarkers);
  evidence.fixture.rootCommentIds = seeded.roots.map((item) => item.id);
  evidence.fixture.replyCommentIds = seeded.replies.map((item) => item.id);

  await verifyPublicHttpBoundary(posts[0].id, actor.token);
  await captureDesignOriginal();
  await runOwnerBrowserFlow(owner, actor, posts[0], seeded);
  await runActorBrowserFlow(owner, actor, posts[0], seeded);
  await finishEvidence();
} catch (error) {
  const safeMessage = error instanceof Error ? error.message.replace(/\b\S+@\S+\b/g, '[redacted-email]') : 'unknown failure';
  evidence.failures.push(`runner: ${safeMessage}`);
  await writeEvidence();
  throw error;
} finally {
  await apiContext?.dispose();
  await browser?.close();
}

function makeFixtureCredentials(runSuffix) {
  const ownerNickname = `m6owner${runSuffix.slice(-8)}`.slice(0, 20);
  const actorNickname = `m6actor${runSuffix.slice(-8)}`.slice(0, 20);
  const password = `M6!Qa${runSuffix}7`;
  return {
    owner: {
      email: `m6-owner-${runSuffix}@example.test`,
      password,
      name: 'M6 QA 합성 소유자',
      nickname: ownerNickname,
      birthDate: '1990-01-01',
      blogTitle: `M6 QA Blog ${runSuffix.slice(-6)}`,
      blogSlug: `m6-qa-${runSuffix}`,
    },
    actor: {
      email: `m6-actor-${runSuffix}@example.test`,
      password,
      name: 'M6 QA 합성 참여자',
      nickname: actorNickname,
      birthDate: '1991-01-01',
      blogTitle: `M6 Actor Blog ${runSuffix.slice(-6)}`,
      blogSlug: `m6-actor-${runSuffix}`,
    },
    postMarkers: [
      `M6 synthetic public post one ${runSuffix}`,
      `M6 synthetic public post two ${runSuffix}`,
    ],
    commentMarkers: Array.from({ length: 21 }, (_, index) => `M6 seeded root ${index + 1} ${runSuffix}`),
    replyMarkers: Array.from({ length: 21 }, (_, index) => `M6 seeded reply ${index + 1} ${runSuffix}`),
    uiRootMarker: `M6 browser root ${runSuffix}`,
    uiReplyMarker: `M6 browser reply ${runSuffix}`,
    uiEditMarker: `M6 browser edited ${runSuffix}`,
  };
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

  const signin = await apiCall('POST', '/api/v1/auth/signin', undefined, {
    email: account.email,
    password: account.password,
  });
  const token = signin.data?.accessToken;
  check(`${label}-signin`, signin.status === 200 && typeof token === 'string' && token.length > 20, 'access token acquired in memory');
  if (typeof token !== 'string') throw new Error(`${label} signin did not return access token`);

  const before = await apiCall('GET', '/api/v1/blogs/me', token);
  const setup = await apiCall('PUT', '/api/v1/blogs/me/initial-setup', token, {
    title: account.blogTitle,
    urlSlug: account.blogSlug,
    description: 'M6 QA synthetic fixture',
  });
  check(`${label}-initial-setup`, setup.status === 200 && setup.data?.isSetupCompleted === true, 'synthetic blog initialized');
  const me = await apiCall('GET', '/api/v1/auth/me', token);
  const blog = await apiCall('GET', '/api/v1/blogs/me', token);
  check(`${label}-blog-readback`, blog.status === 200 && blog.data?.isSetupCompleted === true, 'blog setup read back');
  if (!me.data?.id || !blog.data?.id) throw new Error(`${label} fixture readback missing IDs`);
  check(`${label}-pre-setup-blog`, before.status === 200, 'pre-setup default blog is readable');
  return { token, me: me.data, blog: blog.data };
}

async function createPublicPost(token, marker, index) {
  const contentText = `${marker} content`;
  const response = await apiCall('POST', '/api/v1/posts', token, {
    blogId: evidence.fixture.ownerBlogId ?? 0,
    title: `M6 QA 공개 글 ${index}`,
    contentJson: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: contentText }] }],
    },
    contentHtml: `<p>${contentText}</p>`,
    categoryId: null,
    visibility: 'PUBLIC',
    publish: true,
    thumbnailUrl: null,
    tagNames: [],
    images: [],
  });
  check(`fixture-post-${index}`, response.status === 201 && Number.isInteger(response.data?.id), 'PUBLIC post created');
  if (!response.data?.id) throw new Error(`fixture post ${index} missing ID`);
  return response.data;
}

async function seedCommentTree(token, targetPostId, rootMarkers) {
  const roots = [];
  for (const marker of rootMarkers) {
    const created = await apiCall('POST', `/api/v1/posts/${targetPostId}/comments`, token, {
      content: marker,
      parentId: null,
    });
    if (created.status !== 201 || !created.data?.id) throw new Error('root seed comment failed');
    roots.push(created.data);
  }
  const newestRoot = roots[roots.length - 1];
  const replies = [];
  for (const marker of fixture.replyMarkers) {
    const created = await apiCall('POST', `/api/v1/posts/${targetPostId}/comments`, token, {
      content: marker,
      parentId: newestRoot.id,
    });
    if (created.status !== 201 || !created.data?.id) throw new Error('reply seed comment failed');
    replies.push(created.data);
  }
  check('fixture-comment-tree', roots.length >= 21 && replies.length >= 21, '21 roots and 21 replies seeded');
  return { roots, replies, newestRoot };
}

async function verifyPublicHttpBoundary(targetPostId, token) {
  const guestComments = await apiCall('GET', `/api/v1/posts/${targetPostId}/comments?page=0&size=20`);
  const guestInteractions = await apiCall('GET', `/api/v1/posts/${targetPostId}/interactions`);
  const invalidComments = await apiCall('GET', `/api/v1/posts/${targetPostId}/comments?page=0&size=20`, 'invalid-m6-token');
  const invalidInteractions = await apiCall('GET', `/api/v1/posts/${targetPostId}/interactions`, 'invalid-m6-token');
  check('http-guest-comments', guestComments.status === 200 && guestComments.data?.items?.length > 0, 'guest PUBLIC comments GET succeeds');
  check('http-guest-interactions', guestInteractions.status === 200 && guestInteractions.data?.postId === targetPostId, 'guest PUBLIC interactions GET succeeds');
  check('http-invalid-bearer-comments', invalidComments.status === 401 && invalidComments.errorCode === 'AUTH_004', 'invalid bearer comments is not guest downgrade');
  check('http-invalid-bearer-interactions', invalidInteractions.status === 401 && invalidInteractions.errorCode === 'AUTH_004', 'invalid bearer interactions is not guest downgrade');
  check('http-comments-no-store', privateNoStore(guestComments.cacheControl), 'comments cache is private/no-store');
  check('http-interactions-no-store', privateNoStore(guestInteractions.cacheControl), 'interactions cache is private/no-store');
  const replyPage = await apiCall('GET', `/api/v1/posts/${targetPostId}/comments?page=1&size=20&parentId=${evidence.fixture.rootCommentIds.at(-1) ?? 0}`, token);
  check('http-reply-page', replyPage.status === 200 && replyPage.data?.page === 1, 'reply page 2 is reachable over HTTP');
}

async function runOwnerBrowserFlow(owner, actor, post, seeded) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  try {
    await signInUi(page, fixture.owner);
    await openPost(page, post);
    await waitForCommentPanel(page);
    await capture(page, 'owner-initial');
    check('owner-root-page-populated', (await page.locator('[data-comment-id]').count()) >= 1, 'root page renders seeded comments');
    await assertRootPageTwo(page, fixture.commentMarkers[0]);
    await returnToRootPage(page);

    const rootId = seeded.newestRoot.id;
    await openReplyAndAssertPageTwo(page, rootId, fixture.replyMarkers[0]);
    await returnToRootPage(page);

    const rootForm = page.locator('form').filter({ has: page.getByLabel('댓글 입력') });
    await rootForm.getByLabel('댓글 입력').fill(fixture.uiRootMarker);
    await submitFormAndWait(page, rootForm, '/comments', 'POST');
    await expectVisibleText(page, fixture.uiRootMarker, 'owner browser root submit');
    check('owner-root-create-ui', true, 'root comment created through browser form');

    await openReplyForm(page, rootId);
    const replyForm = page.locator('form').filter({ has: page.getByLabel(`댓글 ${rootId} 답글 내용`) });
    await replyForm.getByLabel(`댓글 ${rootId} 답글 내용`).fill(fixture.uiReplyMarker);
    await submitFormAndWait(page, replyForm, '/comments', 'POST');
    await expectVisibleText(page, fixture.uiReplyMarker, 'owner browser reply submit');
    check('owner-reply-create-ui', true, 'reply created through browser form');

    await toggleLikeAndAssert(page, true, 'owner-like');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitForCommentPanel(page);
    check('owner-refresh-comment-state', (await page.getByRole('heading', { name: /댓글/ }).count()) === 1, 'comment state survives refresh');
    await toggleLikeAndAssert(page, false, 'owner-unlike-after-refresh');
    if (rollbackProbe) await likeRollbackProbe(page);
    else notRun('like-rollback-ui', 'M6_ROLLBACK_PROBE=0');
  } finally {
    await context.close();
  }
}

async function runActorBrowserFlow(owner, actor, post, seeded) {
  const context = await newBrowserContext();
  const page = await context.newPage();
  try {
    await signInUi(page, fixture.actor);
    await openPost(page, post);
    await waitForCommentPanel(page);
    const rootId = seeded.newestRoot.id;
    const root = page.locator(`[data-comment-id="${rootId}"]`);
    await root.getByRole('button', { name: `댓글 ${rootId} 수정` }).click();
    const editor = page.getByLabel(`댓글 ${rootId} 수정 내용`);
    await editor.fill(fixture.uiEditMarker);
    const editResponse = waitForResponse(page, '/api/v1/comments/', 'PUT');
    await editor.locator('xpath=..').getByRole('button', { name: '저장' }).click();
    await editResponse;
    await expectVisibleText(page, fixture.uiEditMarker, 'actor browser comment edit');
    check('comment-edit-ui', true, 'comment edited through browser controls');

    page.once('dialog', (dialog) => void dialog.accept());
    const deleteResponse = waitForResponse(page, '/api/v1/comments/', 'DELETE');
    await root.getByRole('button', { name: `댓글 ${rootId} 삭제` }).click();
    await deleteResponse;
    await expectVisibleText(page, '삭제된 댓글', 'actor browser comment delete');
    check('comment-delete-ui', true, 'comment soft-deleted through browser controls');

    await toggleLikeAndAssert(page, true, 'actor-like');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await waitForCommentPanel(page);
    await toggleLikeAndAssert(page, false, 'actor-unlike-after-refresh');
    await capture(page, 'actor-final');
    await verifyReadAccessLoss(owner, post, page);
  } finally {
    await context.close();
  }
}

async function verifyReadAccessLoss(owner, post, page) {
  // REQUIREMENTS FR-POST-02's UpdatePostRequest is a full snapshot. Keep the
  // owner token in memory and send every contract field while changing only
  // visibility; this is an isolated synthetic post owned by this run.
  const updateRequest = {
    title: post.title,
    contentJson: post.contentJson,
    contentHtml: post.contentHtml,
    categoryId: post.category?.id ?? null,
    visibility: 'PRIVATE',
    publish: true,
    thumbnailUrl: post.thumbnailUrl ?? null,
    tagNames: Array.isArray(post.tags) ? post.tags : [],
    images: Array.isArray(post.images) ? post.images : [],
  };
  if (
    typeof updateRequest.title !== 'string'
    || updateRequest.contentJson == null
    || typeof updateRequest.contentHtml !== 'string'
    || typeof updateRequest.visibility !== 'string'
    || typeof updateRequest.publish !== 'boolean'
    || !Array.isArray(updateRequest.tagNames)
    || !Array.isArray(updateRequest.images)
  ) {
    throw new Error('private visibility fixture update is missing an UpdatePostRequest field');
  }

  const updated = await apiCall('PUT', `/api/v1/posts/${post.id}`, owner.token, updateRequest);
  check(
    'access-loss-owner-private',
    updated.status === 200 && updated.data?.visibility === 'PRIVATE',
    'synthetic owner changed the PUBLIC post to PRIVATE through the full update contract',
  );
  if (updated.status !== 200 || updated.data?.visibility !== 'PRIVATE') {
    throw new Error('synthetic owner could not make the post PRIVATE');
  }

  const likeButton = page.getByRole('button', { name: /♥ 좋아요/ });
  await likeButton.waitFor({ state: 'visible', timeout: 10_000 });
  if (!(await likeButton.isEnabled())) throw new Error('stale actor like action was not enabled before ACL-loss probe');

  const likeResponsePromise = waitForResponse(page, '/api/v1/posts/', 'POST', '/likes');
  const interactionsResponsePromise = page.waitForResponse((response) => (
    response.url().includes(`/api/v1/posts/${post.id}/interactions`)
      && response.request().method() === 'GET'
      && response.status() >= 400
  ), { timeout: 10_000 });
  await likeButton.click();
  const [likeResponse, interactionsResponse] = await Promise.all([
    likeResponsePromise,
    interactionsResponsePromise,
  ]);
  const [likeErrorCode, interactionsErrorCode] = await Promise.all([
    responseErrorCode(likeResponse),
    responseErrorCode(interactionsResponse),
  ]);
  check(
    'access-loss-like-001',
    likeResponse.status() === 403 && likeErrorCode === 'LIKE_001',
    'stale actor like POST is rejected with LIKE_001 after PRIVATE transition',
  );
  check(
    'access-loss-interactions-post-002',
    interactionsResponse.status() === 403 && interactionsErrorCode === 'POST_002',
    'canonical interactions GET reports POST_002 after PRIVATE transition',
  );

  const deniedAlert = page.getByRole('alert').filter({ hasText: '이 글을 볼 권한이 없습니다.' });
  await deniedAlert.waitFor({ state: 'visible', timeout: 10_000 });
  check(
    'access-loss-like-count-cleared',
    await page.getByRole('button', { name: /♥ 좋아요/ }).count() === 0
      && await page.getByText(/♥ 좋아요 \d+/).count() === 0,
    'like action and count are removed after canonical ACL denial',
  );
  check(
    'access-loss-comments-cleared',
    await page.getByRole('heading', { name: /댓글/ }).count() === 0
      && await page.getByLabel('댓글 입력').count() === 0,
    'previous comments and composer are removed after canonical ACL denial',
  );
  check(
    'access-loss-comment-actions-cleared',
    await page.getByRole('button', { name: /댓글 \d+ (답글|수정|삭제)/ }).count() === 0,
    'previous comment action buttons are removed after canonical ACL denial',
  );
}

async function signInUi(page, account) {
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: /접속하기/ }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 10_000 });
  check('browser-signin', !page.url().endsWith('/signin'), 'synthetic account reached application route');
}

async function openPost(page, post) {
  await page.goto(`${frontendUrl}/blog/${encodeURIComponent(fixture.owner.blogSlug)}/${post.id}`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('main article', { timeout: 10_000 });
}

async function waitForCommentPanel(page) {
  await page.getByRole('heading', { name: /댓글/ }).waitFor({ state: 'visible', timeout: 10_000 });
  await page.waitForTimeout(250);
}

async function assertRootPageTwo(page, oldestMarker) {
  const next = page.getByRole('button', { name: '댓글 다음 페이지' });
  await next.waitFor({ state: 'visible', timeout: 10_000 });
  check('root-page-next-enabled', await next.isEnabled(), 'root page 2 is available');
  await next.click();
  await expectVisibleText(page, oldestMarker, 'root page 2 marker');
  check('root-page-two-ui', true, 'root page 2 reached through browser pagination');
}

async function returnToRootPage(page) {
  const previous = page.getByRole('button', { name: '댓글 이전 페이지' });
  if (await previous.count() > 0 && await previous.isEnabled()) {
    await previous.click();
    await page.waitForTimeout(250);
  }
}

async function openReplyAndAssertPageTwo(page, rootId, oldestReplyMarker) {
  const root = page.locator(`[data-comment-id="${rootId}"]`);
  await root.getByRole('button', { name: `댓글 ${rootId} 답글`, exact: true }).click();
  const replyNext = page.getByRole('button', { name: `댓글 ${rootId} 답글 다음 페이지` });
  await replyNext.waitFor({ state: 'visible', timeout: 10_000 });
  check('reply-page-next-enabled', await replyNext.isEnabled(), 'reply page 2 is available');
  await replyNext.click();
  await expectVisibleText(page, oldestReplyMarker, 'reply page 2 marker');
  check('reply-page-two-ui', true, 'reply page 2 reached through browser pagination');
  const previous = page.getByRole('button', { name: `댓글 ${rootId} 답글 이전 페이지` });
  if (await previous.count() > 0 && await previous.isEnabled()) await previous.click();
}

async function openReplyForm(page, rootId) {
  if (await page.getByLabel(`댓글 ${rootId} 답글 내용`).count() > 0) return;
  const root = page.locator(`[data-comment-id="${rootId}"]`);
  await root.getByRole('button', { name: `댓글 ${rootId} 답글`, exact: true }).click();
  await page.getByLabel(`댓글 ${rootId} 답글 내용`).waitFor({ state: 'visible', timeout: 10_000 });
}

async function submitFormAndWait(page, form, pathPart, method) {
  const responsePromise = page.waitForResponse((response) => (
    response.url().includes(pathPart) && response.request().method() === method && response.status() >= 200 && response.status() < 300
  ));
  await form.getByRole('button', { name: '전송' }).click();
  await responsePromise;
  await page.waitForTimeout(300);
}

async function toggleLikeAndAssert(page, expectedLiked, label) {
  const button = page.getByRole('button', { name: /♥ 좋아요/ });
  await button.waitFor({ state: 'visible', timeout: 10_000 });
  const responsePromise = waitForResponse(page, '/api/v1/posts/', expectedLiked ? 'POST' : 'DELETE', '/likes');
  await button.click();
  await responsePromise;
  await page.waitForTimeout(300);
  check(label, (await button.getAttribute('aria-pressed')) === String(expectedLiked), `canonical liked=${expectedLiked}`);
}

async function likeRollbackProbe(page) {
  const button = page.getByRole('button', { name: /♥ 좋아요/ });
  const beforeText = await button.innerText();
  await page.route('**/api/v1/posts/*/likes', async (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        success: false,
        data: null,
        error: { code: 'LIKE_001', message: 'synthetic rollback denial', details: [] },
        timestamp: new Date().toISOString(),
      }),
    });
  });
  try {
    await button.click();
    await page.waitForTimeout(450);
    check('like-rollback-ui', (await button.getAttribute('aria-pressed')) === 'false' && (await button.innerText()) === beforeText, 'failed like restores canonical prior state');
  } finally {
    await page.unroute('**/api/v1/posts/*/likes');
  }
}

async function waitForResponse(page, pathPart, method, suffixPart = '') {
  const response = await page.waitForResponse((candidate) => (
    candidate.url().includes(pathPart)
      && candidate.url().includes(suffixPart)
      && candidate.request().method() === method
  ), { timeout: 10_000 });
  return response;
}

async function responseErrorCode(response) {
  try {
    const json = await response.json();
    return json?.error?.code ?? null;
  } catch {
    return null;
  }
}

async function expectVisibleText(page, text, label) {
  await page.getByText(text, { exact: false }).first().waitFor({ state: 'visible', timeout: 10_000 });
  check(label, true, 'expected synthetic marker is visible');
}

async function captureDesignOriginal() {
  const context = await newBrowserContext();
  const page = await context.newPage();
  try {
    await page.goto(pathToFileURL(designFile).href, { waitUntil: 'load' });
    await page.getByRole('button', { name: '게시글' }).click();
    const screen = page.locator('[data-screen-label="게시글 상세"]:visible');
    await screen.waitFor({ state: 'visible', timeout: 10_000 });
    check('design-original-post-detail', /댓글/.test(await screen.innerText()) && /좋아요/.test(await screen.innerText()), 'design source post detail is populated');
    const screenshot = 'design-post-detail.png';
    await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
    evidence.screenshots.push(screenshot);
  } finally {
    await context.close();
  }
}

async function newBrowserContext() {
  return browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    locale: 'ko-KR',
    reducedMotion: 'reduce',
  });
}

async function capture(page, label) {
  const screenshot = `${label}.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);
  const width = await page.evaluate(() => window.innerWidth);
  check(`${label}-viewport`, width === 1440, `CSS viewport is ${width}`);
}

async function apiCall(method, path, token, data) {
  const response = await apiContext.fetch(path, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    data,
  });
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

function privateNoStore(value) {
  return /private/i.test(value) && /no-store/i.test(value);
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
  console.log(JSON.stringify({
    outputDir,
    screenshots: evidence.screenshots.length,
    pass: evidence.checks.filter((item) => item.status === 'PASS').length,
    failures: evidence.failures.length,
    fixtureIds: evidence.fixture,
  }));
  if (evidence.failures.length > 0) throw new Error(`M6 acceptance failed (${evidence.failures.length} findings)`);
}

async function writeEvidence() {
  await writeFile(resolve(outputDir, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
