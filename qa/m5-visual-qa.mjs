/**
 * M5 V-001 browser evidence harness.
 *
 * This file is QA-only. It never creates or mutates product data. The runtime,
 * fixture accounts and credentials are supplied by the caller. Screenshots and
 * JSON evidence are written below ignored `build/m5-visual-qa/`.
 *
 * Required environment:
 *   M5_USER_EMAIL, M5_USER_PASSWORD, M5_BLOG_SLUG
 * Optional:
 *   M5_FRONTEND_URL (default http://localhost:5173)
 *   M5_DESIGN_FILE (default docs/design/ZeroVerse Pages.dc.html)
 *   M5_SECOND_EMAIL, M5_SECOND_PASSWORD (required for final account-switch evidence)
 *   M5_HERO_FIXTURES (required final seven-state matrix JSON from the fixture runner)
 *   M5_REQUIRE_FULL=0 for an explicitly partial diagnostic run
 *   M5_CHROME_EXECUTABLE (default system Chrome on macOS)
 *   M5_PLAYWRIGHT_MODULE (module path when Playwright is not in this project)
 *   M5_HEADLESS=0 for a headed run
 *
 * Example (from repository root):
 *   NODE_PATH=/path/to/playwright/node_modules node qa/m5-visual-qa.mjs
 */

import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const require = createRequire(import.meta.url);
const playwrightModule = process.env.M5_PLAYWRIGHT_MODULE ?? 'playwright';
const { chromium } = require(playwrightModule);

const root = resolve(new URL('..', import.meta.url).pathname);
const frontendUrl = process.env.M5_FRONTEND_URL ?? 'http://localhost:5173';
const blogSlug = required('M5_BLOG_SLUG');
const email = required('M5_USER_EMAIL');
const password = required('M5_USER_PASSWORD');
const secondEmail = process.env.M5_SECOND_EMAIL;
const secondPassword = process.env.M5_SECOND_PASSWORD;
const heroFixtures = parseHeroFixtures(process.env.M5_HERO_FIXTURES);
const requireFullMatrix = process.env.M5_REQUIRE_FULL !== '0';
const designFile = resolve(root, process.env.M5_DESIGN_FILE ?? 'docs/design/ZeroVerse Pages.dc.html');
const outputDir = resolve(root, 'build/m5-visual-qa');
const chromeExecutable = process.env.M5_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';

await mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({
  headless: process.env.M5_HEADLESS !== '0',
  executablePath: chromeExecutable,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  deviceScaleFactor: 1,
  locale: 'ko-KR',
  reducedMotion: 'reduce',
});
const page = await context.newPage();
const evidence = {
  viewport: await page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight })),
  frontendUrl,
  blogSlug,
  screenshots: [],
  checks: [],
  failures: [],
};

try {
  await captureDesignOriginal();
  await signIn(page, email, password);
  await captureSettings('account-1');
  await captureBlog('account-1');
  await captureHeroFixtures();

  if (secondEmail && secondPassword) {
    await context.clearCookies();
    // AuthContext keeps the access token in memory; a document navigation is
    // required before the second account can be authenticated independently.
    await page.goto('about:blank');
    await signIn(page, secondEmail, secondPassword);
    await captureSettings('account-2');
    await captureBlog('account-2');
  } else {
    check('account-switch', 'NOT RUN (M5_SECOND_EMAIL/M5_SECOND_PASSWORD not supplied)');
  }

  await captureGuestBlog();

  if (requireFullMatrix) {
    if (!heroFixtures) evidence.failures.push('hero-state-matrix: required fixture matrix was not supplied');
    if (!secondEmail || !secondPassword) evidence.failures.push('account-switch: second account was not supplied');
  }

  await writeFile(resolve(outputDir, 'evidence.json'), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify({ outputDir, ...evidence }, null, 2));
  if (evidence.failures.length > 0) {
    throw new Error(`visual QA failed: ${evidence.failures.join('; ')}`);
  }
} finally {
  await browser.close();
}

async function captureDesignOriginal() {
  const designPage = await context.newPage();
  try {
    await designPage.goto(`file://${encodeURI(designFile)}`, { waitUntil: 'load' });
    await designPage.waitForTimeout(500);
    const blogScreen = designPage.locator('[data-screen-label="블로그 페이지"]:visible');
    if (await blogScreen.count() !== 1) {
      throw new Error('design original did not render the blog screen');
    }
    await designPage.screenshot({ path: resolve(outputDir, 'design-blog.png'), fullPage: true });
    evidence.screenshots.push('design-blog.png');
    const universeButton = designPage.getByRole('button', { name: '설정·유니버스' });
    if (await universeButton.count() !== 1) {
      throw new Error('design original screen switcher did not render');
    }
    await universeButton.click();
    await designPage.waitForTimeout(200);
    const universeScreen = designPage.locator('[data-screen-label="설정"]:visible');
    if (await universeScreen.count() !== 1 || !(await universeScreen.innerText()).includes('받은 유니버스 신청')) {
      throw new Error('design original did not render the settings shell after switching');
    }
    const screenshot = 'design-settings-universe.png';
    await designPage.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
    evidence.screenshots.push(screenshot);
    check('design-original', 'captured at 1440 CSS px');
  } finally {
    await designPage.close();
  }
}

async function signIn(target, targetEmail, targetPassword) {
  await target.goto(`${frontendUrl}/signin`, { waitUntil: 'domcontentloaded' });
  await target.waitForTimeout(400);
  await target.getByLabel('이메일').fill(targetEmail);
  await target.getByLabel('비밀번호').fill(targetPassword);
  await target.getByRole('button', { name: /접속하기/ }).click();
  await target.waitForTimeout(700);
  if (target.url().endsWith('/signin') || target.url().endsWith('/blog/setup')) {
    throw new Error(`fixture account did not reach an initialized application: ${target.url()}`);
  }
}

async function captureSettings(label) {
  await page.goto(`${frontendUrl}/settings/universe`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(700);
  const screenshot = `${label}-settings-universe.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);

  const result = await page.evaluate(() => {
    const headings = [...document.querySelectorAll('section h2')].map((node) => node.textContent?.trim() ?? '');
    const section = document.querySelector('main section');
    const rect = section?.getBoundingClientRect();
    const rootStyle = getComputedStyle(document.documentElement);
    const bodyStyle = getComputedStyle(document.body);
    return {
      headings,
      cards: headings.filter((text) => /받은 유니버스 신청|보낸 유니버스 신청|연결된 별|내가 발견한|나를 발견한|차단/.test(text)),
      innerWidth: window.innerWidth,
      minWidth: rootStyle.minWidth,
      bodyBackground: bodyStyle.backgroundColor,
      mainRect: rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height } : null,
    };
  });
  evidence.checks.push({ name: `${label}-settings`, result });
  check(`${label}-settings six cards`, result.cards.length === 6 ? 'PASS' : `FAIL (${result.cards.length})`);
  check(`${label}-settings viewport`, result.innerWidth === 1440 ? 'PASS' : `FAIL (${result.innerWidth})`);
  check(`${label}-settings min-width`, result.minWidth === '1440px' ? 'PASS' : `FAIL (${result.minWidth})`);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(700);
  const afterRefresh = await page.locator('section h2').allTextContents();
  evidence.checks.push({ name: `${label}-settings-refresh`, result: afterRefresh });
  check(`${label}-settings refresh`, afterRefresh.filter((text) => /받은|보낸|연결된|내가 발견|나를 발견|차단/.test(text)).length === 6 ? 'PASS' : 'FAIL');
}

async function captureBlog(label) {
  await page.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(700);
  const screenshot = `${label}-blog-hero.png`;
  await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: true });
  evidence.screenshots.push(screenshot);
  const result = await page.evaluate(() => {
    const hero = document.querySelector('[data-hero-variant="blog"]');
    const actionRegion = document.querySelector('[data-hero-actions="true"]');
    const heroText = actionRegion?.textContent?.trim() ?? '';
    const rect = hero?.getBoundingClientRect();
    return {
      url: location.href,
      heroHeight: rect?.height ?? null,
      actionText: heroText,
      hasRss: /RSS/i.test(heroText),
      hasHeroAction: Boolean(actionRegion?.querySelector('button,a')),
      bodyText: document.body.innerText.slice(0, 6000),
    };
  });
  evidence.checks.push({ name: `${label}-blog`, result });
  check(`${label}-blog hero height`, result.heroHeight === 190 ? 'PASS' : `FAIL (${result.heroHeight})`);
  check(`${label}-blog single non-RSS action`, result.hasHeroAction && !result.hasRss ? 'PASS' : `FAIL (${result.actionText})`);
}

async function captureHeroFixtures() {
  if (!heroFixtures) {
    check('hero-state-matrix', 'NOT RUN (M5_HERO_FIXTURES not supplied)');
    return;
  }

  for (const fixture of heroFixtures) {
    await page.goto(`${frontendUrl}/blog/${encodeURIComponent(fixture.slug)}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);
    const screenshot = `account-1-hero-${fixture.key}.png`;
    await page.screenshot({ path: resolve(outputDir, screenshot), fullPage: false });
    evidence.screenshots.push(screenshot);
    const result = await page.evaluate(() => {
      const hero = document.querySelector('[data-hero-variant="blog"]');
      const actionRegion = document.querySelector('[data-hero-actions="true"]');
      return {
        heroHeight: hero?.getBoundingClientRect().height ?? null,
        actionText: actionRegion?.textContent?.trim() ?? '',
        hasRss: /RSS/i.test(actionRegion?.textContent ?? ''),
      };
    });
    const expectedLabel = canonicalHeroLabel(fixture.expectedLabel);
    evidence.checks.push({
      name: `hero-${fixture.key}`,
      expected: expectedLabel,
      expectedAvailability: fixture.expectedAvailability,
      result,
    });
    check(`hero-${fixture.key} action`, result.actionText.includes(expectedLabel) && !result.hasRss ? 'PASS' : `FAIL (${result.actionText})`);
    check(`hero-${fixture.key} height`, result.heroHeight === 190 ? 'PASS' : `FAIL (${result.heroHeight})`);
  }
}

async function captureGuestBlog() {
  // A fresh context is required: clearing cookies on an existing page cannot
  // clear the in-memory access token held by the loaded apiClient module.
  const guestContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    locale: 'ko-KR',
    reducedMotion: 'reduce',
  });
  const guestPage = await guestContext.newPage();
  try {
    await guestPage.goto(`${frontendUrl}/blog/${encodeURIComponent(blogSlug)}`, { waitUntil: 'domcontentloaded' });
    await guestPage.waitForTimeout(800);
    const screenshot = 'guest-blog-hero.png';
    await guestPage.screenshot({ path: resolve(outputDir, screenshot), fullPage: false });
    evidence.screenshots.push(screenshot);
    const result = await guestPage.evaluate(() => {
      const hero = document.querySelector('[data-hero-variant="blog"]');
      const actionRegion = document.querySelector('[data-hero-actions="true"]');
      const signInLink = actionRegion?.querySelector('a[href$="/signin"]');
      return {
        heroHeight: hero?.getBoundingClientRect().height ?? null,
        actionText: actionRegion?.textContent?.trim() ?? '',
        hasRss: /RSS/i.test(actionRegion?.textContent ?? ''),
        hasSigninHref: Boolean(signInLink),
      };
    });
    evidence.checks.push({ name: 'guest-blog', result });
    check('guest-blog signin action', result.actionText.includes('✦ 유니버스 신청') && result.hasSigninHref && !result.hasRss ? 'PASS' : `FAIL (${result.actionText})`);
    check('guest-blog hero height', result.heroHeight === 190 ? 'PASS' : `FAIL (${result.heroHeight})`);
  } finally {
    await guestContext.close();
  }
}

function check(name, value) {
  evidence.checks.push({ name, result: value });
  if (typeof value === 'string' && value.startsWith('FAIL')) {
    evidence.failures.push(`${name}: ${value}`);
  }
  console.log(`[${name}] ${value}`);
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function parseHeroFixtures(value) {
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed) || parsed.length === 0) {
      throw new Error('must be a non-empty array');
    }
    for (const fixture of parsed) {
      if (!fixture?.key || !fixture.slug || !fixture.expectedLabel) {
        throw new Error('each fixture requires key, slug and expectedLabel');
      }
    }
    return parsed;
  } catch (error) {
    throw new Error(`M5_HERO_FIXTURES is invalid: ${error instanceof Error ? error.message : 'invalid JSON'}`);
  }
}

function canonicalHeroLabel(label) {
  // The design/PRD source places the star before the CTA. Older fixture
  // runners used the equivalent text with the star after it; final evidence
  // always checks the source-canonical label.
  return label === '유니버스 신청 ✦' ? '✦ 유니버스 신청' : label;
}
