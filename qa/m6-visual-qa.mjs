/**
 * M6 댓글·좋아요 독립 HTTP/Chrome harness.
 *
 * QA 산출물이다. 이 파일은 fixture를 만들거나 제품 데이터를 변경하지 않는다.
 * 계정·블로그·PUBLIC 게시글은 caller/backend fixture runner가 준비해야 한다.
 * 비밀값(이메일·비밀번호·access token)은 stdout/evidence/screenshot에 기록하지 않는다.
 * 최종 증거는 ignored build/m6-visual-qa/ 아래에만 쓴다.
 *
 * Required for a full run:
 *   M6_USER_EMAIL M6_USER_PASSWORD M6_BLOG_SLUG M6_POST_ID
 *   M6_SECOND_EMAIL M6_SECOND_PASSWORD
 *
 * Optional:
 *   M6_FRONTEND_URL       default http://localhost:5173
 *   M6_API_BASE_URL       default http://localhost:18080
 *   M6_DESIGN_FILE        default docs/design/ZeroVerse Pages.dc.html
 *   M6_PLAYWRIGHT_MODULE  module name/path when Playwright is not project-local
 *   M6_CHROME_EXECUTABLE  default macOS system Chrome
 *   M6_HEADLESS=0         headed run
 *   M6_REQUIRE_FULL=0     permit diagnostic NOT RUN checks
 *   M6_SECOND_NICKNAME    optional account-switch identity assertion
 *   M6_SECOND_POST_ID     optional route-stale probe target
 *   M6_SECOND_BLOG_SLUG   optional route-stale probe target slug
 *   M6_SECOND_POST_TITLE  optional route-stale probe expected title
 *   M6_STALE_PROBE=1      execute the optional delayed-response route probe
 *   M6_ACCOUNT_STALE_PROBE=1
 *                         sign out/reload/login B while A requests are delayed
 *   M6_ACCOUNT_ONE_MARKER optional A-only comment marker for stale assertion
 *   M6_ACCOUNT_TWO_MARKER optional B-only comment marker for stale assertion
 *
 * Example (from repository root, after fixture/runtime setup):
 *   M6_PLAYWRIGHT_MODULE=/path/to/playwright/node_modules/playwright \
 *   node qa/m6-visual-qa.mjs
 */

import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M6_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium, request: requestApi } = require(playwrightModule);

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const frontendUrl = process.env.M6_FRONTEND_URL ?? 'http://localhost:5173';
const apiBaseUrl = process.env.M6_API_BASE_URL ?? 'http://localhost:18080';
const blogSlug = process.env.M6_BLOG_SLUG ?? '';
const postId = positiveInteger(process.env.M6_POST_ID);
const secondPostId = positiveInteger(process.env.M6_SECOND_POST_ID);
const secondBlogSlug = process.env.M6_SECOND_BLOG_SLUG ?? blogSlug;
const secondPostTitle = process.env.M6_SECOND_POST_TITLE ?? '';
const email = process.env.M6_USER_EMAIL ?? '';
const password = process.env.M6_USER_PASSWORD ?? '';
const secondEmail = process.env.M6_SECOND_EMAIL ?? '';
const secondPassword = process.env.M6_SECOND_PASSWORD ?? '';
const secondNickname = process.env.M6_SECOND_NICKNAME ?? '';
const requireFull = process.env.M6_REQUIRE_FULL !== '0';
const staleProbe = process.env.M6_STALE_PROBE === '1';
const accountStaleProbe = process.env.M6_ACCOUNT_STALE_PROBE === '1';
const accountOneMarker = process.env.M6_ACCOUNT_ONE_MARKER ?? '';
const accountTwoMarker = process.env.M6_ACCOUNT_TWO_MARKER ?? '';
const designFile = resolve(root, process.env.M6_DESIGN_FILE ?? 'docs/design/ZeroVerse Pages.dc.html');
const outputDir = resolve(root, 'build/m6-visual-qa');
const chromeExecutable = process.env.M6_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

if (!blogSlug || !postId) {
  throw new Error('M6_BLOG_SLUG and positive M6_POST_ID are required');
}
if (requireFull && (!email || !password || !secondEmail || !secondPassword)) {
  throw new Error('full M6 QA requires both account credential pairs in environment');
}

await mkdir(outputDir, { recursive: true });

const evidence = {
  viewport: { width: 1440, height: 1000, deviceScaleFactor: 1 },
  frontendUrl,
  apiBaseUrl,
  route: `/blog/${blogSlug}/${postId}`,
  screenshots: [],
  checks: [],
  network: [],
  failures: [],
};

const browser = await chromium.launch({
  headless: process.env.M6_HEADLESS !== '0',
  executablePath: chromeExecutable,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

let apiContext;
try {
  apiContext = await requestApi.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Origin: frontendUrl },
  });

  await captureDesignOriginal();
  if (!email || !password) {
    notRun('authenticated-http', 'M6_USER_EMAIL/M6_USER_PASSWORD not supplied');
    notRun('authenticated-post-detail', 'M6_USER_EMAIL/M6_USER_PASSWORD not supplied');
    notRun('account-switch', 'credential fixture not supplied');
  } else {
    const accountOneToken = await signInApi(email, password, 'account-1');
    await captureHttpMatrix(accountOneToken);

    const accountOneContext = await newBrowserContext();
    try {
      const page = await accountOneContext.newPage();
      attachNetwork(page, 'account-1');
      await signInUi(page, email, password);
      await capturePostDetail(page, 'account-1');
      await captureRefresh(page, 'account-1');
      if (staleProbe) {
        await captureRouteStaleProbe(page);
      } else {
        notRun('route-stale-probe', 'M6_STALE_PROBE=1 not supplied');
      }
      if (accountStaleProbe) {
        await captureAccountStaleProbe(page);
      } else {
        notRun('account-stale-probe', 'M6_ACCOUNT_STALE_PROBE=1 not supplied');
      }
    } finally {
      await accountOneContext.close();
    }

    if (secondEmail && secondPassword) {
      const accountTwoContext = await newBrowserContext();
      try {
        const page = await accountTwoContext.newPage();
        attachNetwork(page, 'account-2');
        await signInUi(page, secondEmail, secondPassword);
        await capturePostDetail(page, 'account-2');
        if (secondNickname) {
          const body = await page.locator('body').innerText();
          check('account-switch-second-identity', body.includes(secondNickname), 'second account identity is rendered');
        } else {
          notRun('account-switch-second-identity', 'M6_SECOND_NICKNAME not supplied');
        }
      } finally {
        await accountTwoContext.close();
      }
    } else {
      notRun('account-switch', 'M6_SECOND_EMAIL/M6_SECOND_PASSWORD not supplied');
    }
  }

  await captureGuest();
  await finishEvidence();
} finally {
  await apiContext?.dispose();
  await browser.close();
}

async function captureDesignOriginal() {
  const context = await newBrowserContext();
  const page = await context.newPage();
  try {
    await page.goto(pathToFileURL(designFile).href, { waitUntil: 'load' });
    await page.waitForTimeout(500);
    const switcher = page.getByRole('button', { name: '게시글' });
    if (await switcher.count() !== 1) {
      throw new Error('design original post-detail switcher is missing');
    }
    await switcher.click();
    await page.waitForTimeout(250);
    const screen = page.locator('[data-screen-label="게시글 상세"]:visible');
    const screenCount = await screen.count();
    const text = screenCount === 1 ? await screen.innerText() : '';
    if (screenCount !== 1 || !/댓글/.test(text) || !/좋아요/.test(text)) {
      throw new Error('design original did not render populated post-detail screen');
    }
    const screenshot = 'design-post-detail.png';
    await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
    evidence.screenshots.push(screenshot);
    check('design-original-post-detail', true, 'Pages source rendered at 1440 CSS px with comment/like content');
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

async function signInUi(page, targetEmail, targetPassword) {
  await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel('이메일').fill(targetEmail);
  await page.getByLabel('비밀번호').fill(targetPassword);
  await page.getByRole('button', { name: /접속하기/ }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), { timeout: 10_000 });
  check('ui-signin', !page.url().endsWith('/signin'), 'authenticated UI reached application route');
}

async function signInApi(targetEmail, targetPassword, label) {
  const response = await apiContext.post('/api/v1/auth/signin', {
    data: { email: targetEmail, password: targetPassword },
  });
  const json = await safeJson(response);
  const token = json?.data?.accessToken;
  check(`${label}-api-signin`, response.ok() && typeof token === 'string' && token.length > 20, 'token acquired in memory');
  if (!response.ok() || typeof token !== 'string') {
    throw new Error(`${label} API signin failed with status ${response.status()}`);
  }
  return token;
}

async function captureHttpMatrix(token) {
  const guestComments = await apiGet(`/api/v1/posts/${postId}/comments?page=0&size=20`);
  const guestInteractions = await apiGet(`/api/v1/posts/${postId}/interactions`);
  const authenticatedComments = await apiGet(`/api/v1/posts/${postId}/comments?page=0&size=20`, token);
  const authenticatedInteractions = await apiGet(`/api/v1/posts/${postId}/interactions`, token);
  const invalidComments = await apiGet(`/api/v1/posts/${postId}/comments?page=0&size=20`, 'invalid-m6-bearer');
  const invalidInteractions = await apiGet(`/api/v1/posts/${postId}/interactions`, 'invalid-m6-bearer');

  recordHttp('guest-comments', guestComments);
  recordHttp('guest-interactions', guestInteractions);
  recordHttp('authenticated-comments', authenticatedComments);
  recordHttp('authenticated-interactions', authenticatedInteractions);
  recordHttp('invalid-bearer-comments', invalidComments);
  recordHttp('invalid-bearer-interactions', invalidInteractions);

  check('http-guest-comments', guestComments.status === 200 && guestComments.success, 'PUBLIC guest comments GET succeeds');
  check('http-guest-interactions', guestInteractions.status === 200 && guestInteractions.success, 'PUBLIC guest interactions GET succeeds');
  check('http-auth-comments', authenticatedComments.status === 200 && authenticatedComments.success, 'authenticated comments GET succeeds');
  check('http-auth-interactions', authenticatedInteractions.status === 200 && authenticatedInteractions.success, 'authenticated interactions GET succeeds');
  check('http-invalid-bearer-comments', invalidComments.status === 401 && invalidComments.errorCode === 'AUTH_004', 'invalid bearer is not downgraded to guest');
  check('http-invalid-bearer-interactions', invalidInteractions.status === 401 && invalidInteractions.errorCode === 'AUTH_004', 'invalid bearer is not downgraded to guest');
  check('http-comments-envelope', guestComments.envelope, 'comments response uses common envelope');
  check('http-interactions-envelope', guestInteractions.envelope, 'interactions response uses common envelope');
  check('http-comments-no-store', privateNoStore(guestComments.cacheControl), 'comments response is private/no-store');
  check('http-interactions-no-store', privateNoStore(guestInteractions.cacheControl), 'interactions response is private/no-store');

  const state = authenticatedInteractions.data;
  const guestState = guestInteractions.data;
  check(
    'http-interactions-guest-flags',
    guestState && guestState.liked === false && guestState.canComment === false && guestState.canLike === false && guestState.canUnlike === false,
    'guest liked/can* flags are false',
  );
  check(
    'http-interactions-shape',
    state && [
      'postId', 'commentCount', 'likeCount', 'liked', 'canComment', 'canLike', 'canUnlike',
    ].every((key) => Object.hasOwn(state, key)),
    'interactions DTO contains only required state fields',
  );
  check(
    'http-comments-page-shape',
    guestComments.data && Array.isArray(guestComments.data.content)
      && Number.isInteger(guestComments.data.page)
      && Number.isInteger(guestComments.data.size),
    'comments response is PageResponse-shaped',
  );
}

async function apiGet(path, token) {
  const response = await apiContext.get(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  const json = await safeJson(response);
  return {
    status: response.status(),
    cacheControl: response.headers()['cache-control'] ?? '',
    envelope: Boolean(json && typeof json.success === 'boolean' && 'data' in json && 'error' in json && 'timestamp' in json),
    success: json?.success === true,
    errorCode: json?.error?.code ?? null,
    data: json?.data ?? null,
  };
}

async function capturePostDetail(page, label) {
  await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}/${postId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const result = await readPostDetail(page);
  const screenshot = `${label}-post-detail.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);
  evidence.checks.push({ name: `${label}-post-detail-dom`, status: 'INFO', result: sanitizeDomResult(result) });
  check(`${label}-post-detail-viewport`, result.innerWidth === 1440, `CSS viewport is ${result.innerWidth}`);
  check(`${label}-post-detail-comments`, result.commentHeading && result.hasCommentContent, 'comment panel is rendered with content');
  check(`${label}-post-detail-like`, result.hasLikeAction, 'like action is rendered');
  check(`${label}-post-detail-placeholder`, result.hasPlaceholder, 'comment placeholder is rendered');
  check(`${label}-post-detail-submit`, result.hasSubmit, 'comment submit action is rendered');
  check(`${label}-post-detail-column`, result.articleWidth > 0 && result.articleWidth <= 1000, `article width ${result.articleWidth}`);
  check(`${label}-post-detail-network-comments`, networkCheck(label, 'comments'), 'comments GET was observed with 2xx response');
  check(`${label}-post-detail-network-interactions`, networkCheck(label, 'interactions'), 'interactions GET was observed with 2xx response');
  check(`${label}-post-detail-no-passive-mutation`, networkMutationCheck(label), 'passive capture emitted no mutation request');
  check(`${label}-post-detail-no-private-data`, result.noPrivateData, 'DOM does not expose email/internal block fields');
}

async function captureRefresh(page, label) {
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(900);
  const result = await readPostDetail(page);
  check(`${label}-post-detail-refresh-comments`, result.commentHeading && result.hasCommentContent, 'comment panel survives reload');
  check(`${label}-post-detail-refresh-like`, result.hasLikeAction, 'like action survives reload');
  const screenshot = `${label}-post-detail-refresh.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);
}

async function captureGuest() {
  const context = await newBrowserContext();
  const page = await context.newPage();
  attachNetwork(page, 'guest');
  try {
    await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}/${postId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(900);
    const result = await readPostDetail(page);
    const screenshot = 'guest-post-detail.png';
    await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
    evidence.screenshots.push(screenshot);
    check('guest-post-detail-viewport', result.innerWidth === 1440, `CSS viewport is ${result.innerWidth}`);
    check('guest-post-detail-network-comments', networkCheck('guest', 'comments'), 'guest comments GET was observed with 2xx response');
    check('guest-post-detail-network-interactions', networkCheck('guest', 'interactions'), 'guest interactions GET was observed with 2xx response');
    check('guest-post-detail-signin-guidance', result.hasSigninGuidance || result.createDisabled, 'guest UI provides signin guidance or disables mutation');
    check('guest-post-detail-no-private-data', result.noPrivateData, 'guest DOM does not expose private/internal fields');
  } finally {
    await context.close();
  }
}

async function captureRouteStaleProbe(page) {
  if (!secondPostId || !secondPostTitle) {
    notRun('route-stale-probe', 'M6_SECOND_POST_ID and M6_SECOND_POST_TITLE are required', true);
    return;
  }
  let delayed = false;
  await page.route('**/api/v1/posts/*/comments*', async (route) => {
    delayed = true;
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 700));
    await route.continue();
  });
  await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}/${postId}`, { waitUntil: 'domcontentloaded' });
  await page.goto(`${frontendUrl}/blog/${encodeURIComponent(secondBlogSlug)}/${secondPostId}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);
  const body = await page.locator('body').innerText();
  check('route-stale-probe', delayed && body.includes(secondPostTitle), 'late first-route response did not replace second route');
  await page.unroute('**/api/v1/posts/*/comments*');
}

async function captureAccountStaleProbe(page) {
  if (!secondEmail || !secondPassword || !secondNickname) {
    notRun('account-stale-probe', 'second credentials and M6_SECOND_NICKNAME are required', true);
    return;
  }
  const delayedRoutes = [];
  const delayRoute = async (route) => {
    delayedRoutes.push(route.request().url());
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 700));
    try {
      await route.continue();
    } catch {
      // Navigation can cancel the old request; the assertion below is about
      // the new account's rendered state, not the canceled old response.
    }
  };
  await page.route('**/api/v1/posts/*/comments*', delayRoute);
  await page.route('**/api/v1/posts/*/interactions', delayRoute);
  try {
    await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}/${postId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(120);
    await page.evaluate(async () => {
      await fetch('/api/v1/auth/signout', { method: 'POST', credentials: 'include' });
    });
    // The access token is in the page's module memory; a document reload is
    // required before the next signin can establish an independent account.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
    await signInUi(page, secondEmail, secondPassword);
    await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}/${postId}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1200);
    const body = await page.locator('body').innerText();
    const noA = accountOneMarker ? !body.includes(accountOneMarker) : true;
    const hasB = body.includes(secondNickname) && (accountTwoMarker ? body.includes(accountTwoMarker) : true);
    check('account-stale-probe', delayedRoutes.length > 0 && hasB && noA, 'late account-A response did not paint account-B PostDetail');
  } finally {
    await page.unroute('**/api/v1/posts/*/comments*', delayRoute);
    await page.unroute('**/api/v1/posts/*/interactions', delayRoute);
  }
}

async function readPostDetail(page) {
  return page.evaluate(() => {
    const bodyText = document.body.innerText;
    const article = document.querySelector('main article') ?? document.querySelector('article');
    const articleRect = article?.getBoundingClientRect();
    const commentHeading = [...document.querySelectorAll('h1,h2,h3,h4,section,div')]
      .some((node) => /^\s*■?\s*댓글\s*\d*/.test(node.textContent?.trim() ?? ''));
    const commentContent = bodyText.includes('댓글');
    const likeAction = [...document.querySelectorAll('button,a')]
      .find((node) => /좋아요/.test(node.textContent ?? ''));
    const placeholder = [...document.querySelectorAll('input,textarea')]
      .some((node) => node.getAttribute('placeholder') === '따뜻한 신호를 남겨주세요…');
    const submit = [...document.querySelectorAll('button')]
      .some((node) => /^\s*전송\s*$/.test(node.textContent ?? ''));
    const privateTokens = /@[A-Z0-9._%+-]+\.[A-Z]{2,}|birthDate|birth_date|blockedBy|blockReason/i.test(bodyText);
    const signinGuidance = [...document.querySelectorAll('a,button')]
      .some((node) => /로그인|접속/.test(node.textContent ?? '') || (node instanceof HTMLAnchorElement && node.href.endsWith('/signin')));
    const createDisabled = [...document.querySelectorAll('input,textarea,button')]
      .filter((node) => node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement || node instanceof HTMLButtonElement)
      .every((node) => node.disabled || !/전송|댓글|좋아요/.test(node.textContent ?? '') && node.getAttribute('placeholder') !== '따뜻한 신호를 남겨주세요…');
    return {
      innerWidth: window.innerWidth,
      articleWidth: articleRect?.width ?? 0,
      commentHeading,
      hasCommentContent: commentContent,
      hasLikeAction: Boolean(likeAction),
      hasPlaceholder: placeholder,
      hasSubmit: submit,
      noPrivateData: !privateTokens,
      hasSigninGuidance: signinGuidance,
      createDisabled,
    };
  });
}

const networkByLabel = new Map();

function attachNetwork(page, label) {
  const state = { requests: [], responses: [] };
  networkByLabel.set(label, state);
  page.on('request', (request) => {
    const entry = relevantRequest(request);
    if (entry) state.requests.push(entry);
  });
  page.on('response', async (response) => {
    const request = response.request();
    const entry = relevantRequest(request);
    if (!entry) return;
    state.responses.push({
      ...entry,
      status: response.status(),
      cacheControl: response.headers()['cache-control'] ?? '',
    });
  });
}

function relevantRequest(request) {
  const url = new URL(request.url());
  const match = url.pathname.match(/^\/api\/v1\/posts\/(\d+)\/(comments|interactions)(?:$|\/)/);
  if (!match || Number(match[1]) !== postId) return null;
  return {
    method: request.method(),
    path: url.pathname,
    query: url.search,
    kind: match[2],
  };
}

function networkCheck(label, kind) {
  const state = networkByLabel.get(label);
  if (!state) return false;
  return state.responses.some((response) => response.kind === kind && response.method === 'GET' && response.status >= 200 && response.status < 300);
}

function networkMutationCheck(label) {
  const state = networkByLabel.get(label);
  if (!state) return false;
  return !state.requests.some((request) => request.method !== 'GET');
}

function recordHttp(name, result) {
  evidence.network.push({
    name,
    status: result.status,
    cacheControl: result.cacheControl,
    envelope: result.envelope,
    success: result.success,
    errorCode: result.errorCode,
    dataShape: summarizeData(result.data),
  });
}

function summarizeData(data) {
  if (data === null || data === undefined) return null;
  if (Array.isArray(data)) return { type: 'array', length: data.length };
  if (typeof data !== 'object') return { type: typeof data };
  return { type: 'object', keys: Object.keys(data).sort() };
}

function sanitizeDomResult(result) {
  return {
    innerWidth: result.innerWidth,
    articleWidth: result.articleWidth,
    commentHeading: result.commentHeading,
    hasCommentContent: result.hasCommentContent,
    hasLikeAction: result.hasLikeAction,
    hasPlaceholder: result.hasPlaceholder,
    hasSubmit: result.hasSubmit,
    noPrivateData: result.noPrivateData,
  };
}

function privateNoStore(value) {
  return /private/i.test(value) && /no-store/i.test(value);
}

async function safeJson(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function check(name, pass, detail) {
  const status = pass ? 'PASS' : 'FAIL';
  evidence.checks.push({ name, status, detail });
  if (!pass) evidence.failures.push(`${name}: ${detail ?? 'failed'}`);
  console.log(`[${status}] ${name}`);
}

function notRun(name, detail, required = false) {
  evidence.checks.push({ name, status: 'NOT RUN', detail });
  console.log(`[NOT RUN] ${name}`);
  if (required) evidence.failures.push(`${name}: ${detail ?? 'not run'}`);
}

async function finishEvidence() {
  await writeFile(resolve(outputDir, 'evidence.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify({
    outputDir,
    screenshots: evidence.screenshots.length,
    checks: evidence.checks.filter((item) => item.status === 'PASS').length,
    failures: evidence.failures.length,
  }));
  if (evidence.failures.length > 0) {
    throw new Error(`M6 visual/API QA failed (${evidence.failures.length} findings)`);
  }
}

function positiveInteger(value) {
  if (value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}
