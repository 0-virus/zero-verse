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
import { execFile as execFileCallback } from 'node:child_process';
import { access, mkdir, readdir, readFile, stat, writeFile } from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import { resolve, relative } from 'node:path';
import { promisify } from 'node:util';

const require = createRequire(import.meta.url);
const execFile = promisify(execFileCallback);
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const selfCheck = process.argv.includes('--self-check');
const suffix = `${Date.now().toString(36)}${randomBytes(6).toString('hex')}`;
const runId = process.env.M10_RUN_ID ?? `m10-${suffix}`;
const apiBaseUrl = process.env.M10_API_BASE_URL ?? 'http://127.0.0.1:18090';
const frontendUrl = process.env.M10_FRONTEND_URL ?? 'http://127.0.0.1:5180';
const outputDir = resolve(root, process.env.M10_OUTPUT_DIR ?? `build/m10-acceptance-${suffix}`);
const manifestPath = resolve(root, process.env.M10_VERSION_MANIFEST ?? 'build/m10-evidence/tested-version.json');
const chromeExecutable = process.env.M10_CHROME_EXECUTABLE
  ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const playwrightModule = process.env.M10_PLAYWRIGHT_MODULE ?? 'playwright';
const harnessPath = resolve(root, 'qa/m10-acceptance.mjs');
const runtimeEvidencePath = process.env.M10_RUNTIME_EVIDENCE
  ? resolve(root, process.env.M10_RUNTIME_EVIDENCE)
  : null;
const approvedMysqlHost = '127.0.0.1';
const approvedMysqlPort = 13320;
const approvedApiBaseUrl = 'http://127.0.0.1:18090';
const approvedFrontendUrl = 'http://127.0.0.1:5180';
const screenshotsEnabled = true;
const headless = process.env.M10_HEADLESS !== '0';

const runtimeEvidenceSchema = 'zeroverse.m10.runtime-evidence.v1';
const runtimeAttestationSchema = 'zeroverse.m10.runtime-attestation.v1';
const attestationSkewMs = 15 * 60 * 1000;
const runtimeEvidenceCheckIds = [
  'admin-current-actor', 'admin-stale-claim', 'admin-self-protection',
  'admin-mutations', 'admin-deleted-target', 'admin-soft-delete-preservation',
  'upload-5mib-mime', 'restart-persistence',
];
const runtimeAdminEvidenceIds = [
  'current-active-actor', 'stale-claim', 'self-protection', 'role-status-delete', 'deleted-target', 'child-fingerprint',
];
const runtimeUploadEvidenceIds = ['exact-5242880', 'over-5242880', 'mime-mismatch', 'authorized-content-get'];
const runtimeRestartEvidenceIds = ['same-jar', 'same-db', 'same-file-hash', 'post-restart-content-get'];

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

// Generated OpenAPI marks these current operations with @SecurityRequirements.
// Keep the public map explicit so a missing security annotation cannot be
// mistaken for a protected operation (or silently make coverage incomplete).
const publicOpenApiOperations = new Set([
  'post:/api/v1/auth/register', 'post:/api/v1/auth/signin', 'post:/api/v1/auth/refresh', 'post:/api/v1/auth/signout',
  'get:/api/v1/blogs/slug/{urlSlug}', 'get:/api/v1/blogs/{blogId}/posts',
  'get:/api/v1/blogs/slug/{urlSlug}/posts', 'get:/api/v1/blogs/{blogId}/categories',
  'get:/api/v1/posts/{postId}', 'get:/api/v1/posts/{postId}/comments', 'get:/api/v1/posts/{postId}/interactions',
  'get:/api/v1/tags/{tagName}/posts', 'get:/api/v1/feed/public', 'get:/api/v1/search',
]);

const requiredChecks = [
  'source-runtime-identity', 'runtime-evidence-schema', 'runtime-attestation-schema', 'runtime-openapi', 'openapi-required-paths', 'openapi-coverage',
  'synthetic-register-owner', 'synthetic-register-friend', 'signin-owner', 'signin-friend',
  'setup-owner', 'setup-friend', 'general-category-fixture', 'image-upload-fixture', 'draft-empty-allowed', 'publish-empty-rejected', 'publish-image-only-allowed', 'publish-public', 'public-guest-read',
  'refresh-rotation', 'logout-refresh-reuse-rejected', 'universe-request', 'universe-accept',
  'universe-post-read', 'comment-create', 'like-create', 'notification-list', 'notification-event-identity', 'notification-read-delete',
  'content-sanitize',
  'common-envelope', 'private-no-store', 'invalid-bearer-rejected', 'pagination-shape', 'paging-bound', 'http-status-matrix',
  'refresh-cookie-policy',
  'admin-current-actor', 'admin-stale-claim', 'admin-self-protection', 'admin-mutations', 'admin-deleted-target',
  'admin-soft-delete-preservation', 'upload-5mib-mime', 'restart-persistence',
  'provenance-hash-stability', 'static-skip-placeholder-scan', 'keyboard-topbar-observation', 'keyboard-modal-observation',
  'keyboard-draft-picker-observation', 'keyboard-category-observation', 'keyboard-admin-observation',
  'screenshot-main', 'screenshot-blog', 'screenshot-post-detail', 'screenshot-editor', 'screenshot-auth',
  'screenshot-setup', 'screenshot-settings', 'screenshot-settings-universe', 'screenshot-settings-posts',
  'screenshot-search', 'screenshot-notifications', 'screenshot-admin', 'screenshot-admin-detail',
  'screenshots-complete',
  'self-check-negative-fixtures', 'self-check-manifest-ordering', 'self-check-admin-list-shape', 'self-check-browser-signin-form',
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
  runtimeEvidence: null,
  provenance: { before: null, after: null },
  fixture: { runId, accountIds: {}, blogIds: {}, categoryIds: {}, postIds: {}, commentIds: {} },
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

function isSha256(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/i.test(value);
}

function isGitHash(value) {
  return typeof value === 'string' && /^[a-f0-9]{40,64}$/i.test(value);
}

// build/m10-runtime.py uses Python's deterministic code-point ordering for
// manifest/FE paths. Product paths are ASCII, so JS relational comparison is
// the equivalent byte/code-unit comparator and does not depend on locale.
function canonicalPathCompare(left, right) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function isCanonicalManifestPath(value) {
  return typeof value === 'string'
    && value.length > 0
    && /^[\x20-\x7e]+$/.test(value)
    && !value.startsWith('/')
    && !value.includes('\\')
    && !value.split('/').some((segment) => segment === '' || segment === '.' || segment === '..');
}

function isCanonicalManifestOrder(paths) {
  if (!Array.isArray(paths)) return false;
  const seen = new Set();
  for (let index = 0; index < paths.length; index += 1) {
    const path = paths[index];
    if (!isCanonicalManifestPath(path) || seen.has(path)) return false;
    if (index > 0 && canonicalPathCompare(paths[index - 1], path) >= 0) return false;
    seen.add(path);
  }
  return true;
}

function assertInsideRoot(file, label) {
  if (relative(root, file).startsWith('..')) throw new Error(`${label} escapes repository root`);
}

async function sha256Directory(directory) {
  const files = [];
  async function walk(current, prefix = '') {
    const entries = await readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const absolute = resolve(current, entry.name);
      const logical = prefix ? `${prefix}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        await walk(absolute, logical);
      } else if (entry.isFile()) {
        files.push({ path: logical, sha256: await sha256(absolute) });
      } else {
        throw new Error(`frontend dist contains unsupported entry: ${logical}`);
      }
    }
  }
  await walk(directory);
  files.sort((a, b) => canonicalPathCompare(a.path, b.path));
  return createHash('sha256')
    .update(files.map((entry) => `${entry.path}\0${entry.sha256}\n`).join(''))
    .digest('hex');
}

async function gitValue(...args) {
  const result = await execFile('git', args, { cwd: root, maxBuffer: 4 * 1024 * 1024 });
  return result.stdout.trim();
}

async function currentGitIdentity() {
  const [commit, tree] = await Promise.all([
    gitValue('rev-parse', 'HEAD'),
    gitValue('rev-parse', 'HEAD^{tree}'),
  ]);
  if (!/^[a-f0-9]{40,64}$/i.test(commit) || !/^[a-f0-9]{40,64}$/i.test(tree)) {
    throw new Error('current Git commit/tree identity is unavailable');
  }
  return { commit, tree };
}

async function trackedProductFiles() {
  const output = await gitValue('ls-files', '-z');
  return output.split('\0').filter(Boolean).filter((file) => (
    file.startsWith('src/')
      || file.startsWith('frontend/')
      || file === 'build.gradle'
      || file === 'settings.gradle'
      || file === 'gradle.properties'
      || file.startsWith('gradle/')
      || file === 'gradlew'
      || file === 'gradlew.bat'
  ));
}

async function verifyManifest() {
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  if (manifest?.schemaVersion !== 'm10-version-manifest.v2') {
    throw new Error('M10 manifest schemaVersion must be m10-version-manifest.v2');
  }
  if (!Array.isArray(manifest.files) || !isSha256(manifest.sourceSha256)) {
    throw new Error('M10 manifest must contain schemaVersion, sourceSha256 and files[]');
  }
  const actual = [];
  const seen = new Set();
  const manifestPaths = manifest.files.map((entry) => entry?.path);
  if (!isCanonicalManifestOrder(manifestPaths)) {
    throw new Error('M10 manifest files[] paths must be unique printable-ASCII relative paths in canonical ascending order');
  }
  for (const entry of manifest.files) {
    if (!entry || typeof entry.path !== 'string' || !isSha256(entry.sha256)) {
      throw new Error('M10 manifest contains malformed file entry');
    }
    if (seen.has(entry.path)) throw new Error(`M10 manifest contains duplicate file entry: ${entry.path}`);
    seen.add(entry.path);
    const file = resolve(root, entry.path);
    assertInsideRoot(file, 'manifest entry');
    const hash = await sha256(file);
    if (hash !== entry.sha256) throw new Error(`source manifest mismatch: ${entry.path}`);
    actual.push({ path: entry.path, sha256: hash });
  }
  const aggregate = createHash('sha256')
    .update(actual.map((entry) => `${entry.path}\0${entry.sha256}\n`).join(''))
    .digest('hex');
  if (aggregate !== manifest.sourceSha256) throw new Error('source manifest aggregate mismatch');
  const tracked = await trackedProductFiles();
  const missingProductFiles = tracked.filter((file) => !seen.has(file));
  if (missingProductFiles.length > 0) {
    throw new Error(`M10 manifest omits tracked product files (${missingProductFiles.length})`);
  }
  const git = await currentGitIdentity();
  if (manifest.git?.commit !== git.commit || manifest.git?.tree !== git.tree) {
    throw new Error('M10 manifest Git commit/tree does not match current checkout');
  }
  const jarPath = resolve(root, manifest.jarPath ?? 'build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar');
  assertInsideRoot(jarPath, 'runtime JAR');
  const jarSha256 = await sha256(jarPath);
  if (manifest.jarSha256 !== jarSha256 || !isSha256(manifest.jarSha256)) throw new Error('runtime JAR hash mismatch');
  const frontendDistPath = resolve(root, manifest.frontendDistPath ?? 'frontend/dist');
  assertInsideRoot(frontendDistPath, 'frontend dist');
  const frontendDistSha256 = await sha256Directory(frontendDistPath);
  if (manifest.frontendDistSha256 !== frontendDistSha256 || !isSha256(manifest.frontendDistSha256)) {
    throw new Error('frontend dist hash mismatch');
  }
  evidence.sourceManifest = {
    ...manifest,
    productFiles: { tracked: tracked.length, listed: seen.size, missing: 0 },
    verifiedAt: new Date().toISOString(),
  };
  evidence.provenance.before = {
    sourceSha256: manifest.sourceSha256,
    jarSha256,
    frontendSha256: frontendDistSha256,
    harnessSha256: await sha256(harnessPath),
    gitCommit: git.commit,
    gitTree: git.tree,
  };
  check(
    'source-runtime-identity',
    true,
    `source manifest, tracked product set (${tracked.length}), Git commit/tree, JAR and FE dist hashes match`,
  );
}

function validIso(value) {
  return typeof value === 'string' && !Number.isNaN(Date.parse(value));
}

function hasAll(values, required) {
  return Array.isArray(values) && required.every((value) => values.includes(value));
}

function markRuntimeEvidenceMissing(detail) {
  check('runtime-evidence-schema', false, `NOT RUN: ${detail}`, { required: true });
  check('runtime-attestation-schema', false, `NOT RUN: runtime attestation did not verify (${detail})`, { required: true });
  for (const id of runtimeEvidenceCheckIds) {
    check(id, false, `NOT RUN: runtime evidence did not supply ${id}`, { required: true });
  }
}

function withinCurrentRun(value, now = Date.now()) {
  if (!validIso(value)) return false;
  const timestamp = Date.parse(value);
  const started = Date.parse(evidence.startedAt);
  return timestamp >= started - attestationSkewMs && timestamp <= now + attestationSkewMs;
}

function safeRelativePath(value) {
  return typeof value === 'string'
    && value.length > 0
    && !value.startsWith('/')
    && !value.split('/').includes('..')
    && /^[A-Za-z0-9._/-]+$/.test(value);
}

async function verifyRuntimeAttestation(bundle) {
  const reference = bundle?.attestation;
  if (!reference || !safeRelativePath(reference.path) || !isSha256(reference.sha256)) {
    return { ok: false, detail: 'attestation path/SHA-256 reference is missing' };
  }
  let attestationPath;
  try {
    attestationPath = resolve(root, reference.path);
    assertInsideRoot(attestationPath, 'attestation');
  } catch (error) {
    return { ok: false, detail: scrub(error instanceof Error ? error.message : error) };
  }
  let raw;
  let attestation;
  try {
    raw = await readFile(attestationPath, 'utf8');
    const actualHash = createHash('sha256').update(raw).digest('hex');
    if (actualHash !== reference.sha256) return { ok: false, detail: 'attestation SHA-256 mismatch' };
    attestation = JSON.parse(raw);
  } catch (error) {
    return { ok: false, detail: `attestation could not be read (${scrub(error instanceof Error ? error.message : error)})` };
  }
  const artifactHashes = attestation?.artifactHashes;
  const artifactEntries = artifactHashes && typeof artifactHashes === 'object' && !Array.isArray(artifactHashes)
    ? Object.entries(artifactHashes)
    : [];
  const now = Date.now();
  const basicShape = attestation?.schema === runtimeAttestationSchema
    && attestation?.milestone === 'M10'
    && attestation?.status === 'PASS'
    && attestation?.runId === bundle?.runId
    && attestation?.runId === process.env.M10_RUN_ID
    && withinCurrentRun(attestation?.observedAt, now)
    && artifactEntries.length > 0
    && artifactEntries.every(([path, hash]) => safeRelativePath(path) && isSha256(hash));
  const verifiedArtifacts = [];
  let artifactsOk = basicShape;
  if (basicShape) {
    for (const [path, expectedHash] of artifactEntries) {
      try {
        const artifactPath = resolve(root, path);
        assertInsideRoot(artifactPath, 'attestation artifact');
        const actualHash = await sha256(artifactPath);
        const ok = actualHash === expectedHash;
        artifactsOk = artifactsOk && ok;
        verifiedArtifacts.push({ path, sha256: expectedHash, ok });
      } catch (error) {
        artifactsOk = false;
        verifiedArtifacts.push({ path, sha256: expectedHash, ok: false });
      }
    }
  }
  const checkResults = {};
  let checksOk = artifactsOk;
  for (const id of runtimeEvidenceCheckIds) {
    const result = attestation?.checks?.[id];
    const artifactPaths = Array.isArray(result?.artifactPaths) ? result.artifactPaths : [];
    const ok = result?.ok === true
      && withinCurrentRun(result?.observedAt, now)
      && artifactPaths.length > 0
      && artifactPaths.every((path) => safeRelativePath(path) && Object.hasOwn(artifactHashes ?? {}, path));
    checksOk = checksOk && ok;
    checkResults[id] = {
      ok: result?.ok === true,
      observedAt: withinCurrentRun(result?.observedAt, now) ? result.observedAt : null,
      artifactPaths: artifactPaths.filter((path) => safeRelativePath(path) && Object.hasOwn(artifactHashes ?? {}, path)),
    };
  }
  const secretFree = !/(?:Bearer\s+|password\s*[:=]|refresh[_-]?token\s*[:=]|set-cookie\s*[:=]|@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/i.test(`${JSON.stringify(bundle)}\n${raw}`);
  const summary = {
    path: reference.path,
    sha256: reference.sha256,
    schema: attestation?.schema === runtimeAttestationSchema ? runtimeAttestationSchema : null,
    milestone: attestation?.milestone === 'M10' ? 'M10' : null,
    status: attestation?.status === 'PASS' ? 'PASS' : null,
    runId: typeof attestation?.runId === 'string' && /^m10-[A-Za-z0-9_-]+$/.test(attestation.runId) ? attestation.runId : null,
    observedAt: withinCurrentRun(attestation?.observedAt, now) ? attestation.observedAt : null,
    artifactHashes: verifiedArtifacts,
    checks: checkResults,
  };
  return {
    ok: Boolean(basicShape && artifactsOk && checksOk && secretFree),
    detail: basicShape && artifactsOk && checksOk && secretFree
      ? 'current attestation file, raw artifact hashes, per-check observations and run timestamps verified'
      : 'NOT RUN: attestation schema/runId/timestamp/per-check/artifact hash or secret-free validation failed',
    summary,
    secretFree,
  };
}

async function verifyRuntimeEvidence() {
  if (!runtimeEvidencePath) {
    markRuntimeEvidenceMissing('M10_RUNTIME_EVIDENCE is not configured; env presence alone cannot pass runtime-only checks');
    return;
  }
  try {
    assertInsideRoot(runtimeEvidencePath, 'runtime evidence');
  } catch (error) {
    markRuntimeEvidenceMissing(scrub(error instanceof Error ? error.message : error));
    return;
  }
  let bundle;
  try {
    bundle = JSON.parse(await readFile(runtimeEvidencePath, 'utf8'));
  } catch (error) {
    markRuntimeEvidenceMissing(`runtime bundle could not be read (${scrub(error instanceof Error ? error.message : error)})`);
    return;
  }
  const admin = bundle?.checks?.admin;
  const upload = bundle?.checks?.upload;
  const restart = bundle?.checks?.restart;
  const attestationResult = await verifyRuntimeAttestation(bundle);
  const now = Date.now();
  const bundleSchemaShape = bundle?.schema === runtimeEvidenceSchema
    && bundle?.milestone === 'M10'
    && bundle?.runId === process.env.M10_RUN_ID
    && typeof bundle?.runId === 'string'
    && /^m10-[A-Za-z0-9_-]+$/.test(bundle.runId)
    && withinCurrentRun(bundle?.generatedAt, now)
    && bundle?.runtime?.mysql?.host === approvedMysqlHost
    && Number(bundle?.runtime?.mysql?.port) === approvedMysqlPort
    && bundle?.runtime?.mysql?.database === 'zeroverse_m10'
    && bundle?.runtime?.api?.baseUrl === apiBaseUrl
    && bundle?.runtime?.api?.timezone === 'UTC'
    && isSha256(bundle?.runtime?.api?.jarSha256)
    && bundle?.runtime?.frontend?.baseUrl === frontendUrl
    && isSha256(bundle?.runtime?.frontend?.distSha256)
    && isGitHash(bundle?.provenance?.gitCommit)
    && isGitHash(bundle?.provenance?.gitTree)
    && isSha256(bundle?.provenance?.sourceManifestSha256)
    && isSha256(bundle?.provenance?.jarSha256)
    && isSha256(bundle?.provenance?.frontendDistSha256)
    && isSha256(bundle?.provenance?.harnessSha256)
    && bundle?.status === 'PASS'
    && bundle?.runtime?.api?.jarSha256 === bundle?.provenance?.jarSha256
    && bundle?.runtime?.frontend?.distSha256 === bundle?.provenance?.frontendDistSha256
    && admin?.status === 'PASS'
    && upload?.status === 'PASS'
    && restart?.status === 'PASS'
    && hasAll(admin?.ids, runtimeAdminEvidenceIds)
    && hasAll(upload?.ids, runtimeUploadEvidenceIds)
    && hasAll(restart?.ids, runtimeRestartEvidenceIds);
  const schemaShape = bundleSchemaShape && attestationResult.ok;
  const noSecrets = attestationResult.secretFree !== false
    && !/(?:Bearer\s+|password\s*[:=]|refresh[_-]?token\s*[:=]|set-cookie\s*[:=]|@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/i.test(JSON.stringify(bundle));
  const before = evidence.provenance.before;
  const currentMatches = schemaShape
    && noSecrets
    && before
    && bundle.provenance.sourceManifestSha256 === before.sourceSha256
    && bundle.provenance.jarSha256 === before.jarSha256
    && bundle.provenance.frontendDistSha256 === before.frontendSha256
    && bundle.provenance.harnessSha256 === before.harnessSha256
    && bundle.provenance.gitCommit === before.gitCommit
    && bundle.provenance.gitTree === before.gitTree
    && attestationResult.ok;
  const sanitizedChecks = {
    admin: { status: admin?.status === 'PASS' ? 'PASS' : null, ids: Array.isArray(admin?.ids) ? admin.ids.filter((id) => runtimeAdminEvidenceIds.includes(id)) : [] },
    upload: { status: upload?.status === 'PASS' ? 'PASS' : null, ids: Array.isArray(upload?.ids) ? upload.ids.filter((id) => runtimeUploadEvidenceIds.includes(id)) : [] },
    restart: { status: restart?.status === 'PASS' ? 'PASS' : null, ids: Array.isArray(restart?.ids) ? restart.ids.filter((id) => runtimeRestartEvidenceIds.includes(id)) : [] },
  };
  evidence.runtimeEvidence = {
    schema: bundle?.schema === runtimeEvidenceSchema ? runtimeEvidenceSchema : null,
    milestone: bundle?.milestone === 'M10' ? 'M10' : null,
    runId: typeof bundle?.runId === 'string' && /^m10-[A-Za-z0-9_-]+$/.test(bundle.runId) ? bundle.runId : null,
    status: bundle?.status === 'PASS' ? 'PASS' : null,
    generatedAt: validIso(bundle?.generatedAt) ? bundle.generatedAt : null,
    provenance: schemaShape ? {
      gitCommit: bundle.provenance.gitCommit,
      gitTree: bundle.provenance.gitTree,
      sourceManifestSha256: bundle.provenance.sourceManifestSha256,
      jarSha256: bundle.provenance.jarSha256,
      frontendDistSha256: bundle.provenance.frontendDistSha256,
      harnessSha256: bundle.provenance.harnessSha256,
    } : null,
    attestation: attestationResult.summary ?? null,
    checks: sanitizedChecks,
  };
  check(
    'runtime-evidence-schema',
    Boolean(currentMatches),
    currentMatches
      ? 'runtime bundle schema, loopback DB/runtime identity, grouped named checks, current Git/source/JAR/FE/harness hashes, attestation artifacts/timestamps and secret-free boundary match the verified manifest'
      : `NOT RUN: runtime bundle schema/status/named checks, attestation (${attestationResult.detail}) or current provenance hashes do not match the verified manifest`,
    { required: true },
  );
  check(
    'runtime-attestation-schema',
    attestationResult.ok && noSecrets,
    attestationResult.ok && noSecrets
      ? 'attestation schema, exact M10_RUN_ID, bounded observedAt values, per-check ok/artifact references and raw artifact hashes are current and secret-free'
      : `NOT RUN: ${attestationResult.detail}`,
    { required: true },
  );
  const namedPass = currentMatches && schemaShape;
  const namedDetails = {
    'admin-current-actor': 'admin.current-active-actor',
    'admin-stale-claim': 'admin.stale-claim',
    'admin-self-protection': 'admin.self-protection',
    'admin-mutations': 'admin.role-status-delete',
    'admin-deleted-target': 'admin.deleted-target',
    'admin-soft-delete-preservation': 'admin.child-fingerprint',
    'upload-5mib-mime': 'upload.exact-5242880/over-5242880/mime-mismatch/authorized-content-get',
    'restart-persistence': 'restart.same-jar/same-db/same-file-hash/post-restart-content-get',
  };
  for (const id of runtimeEvidenceCheckIds) {
    check(id, namedPass, namedPass ? `runtime bundle named check ${namedDetails[id]} and attestation evidence are PASS` : `NOT RUN: runtime bundle did not verify ${namedDetails[id]} with current attestation`, { required: true });
  }
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

async function callMultipart(path, token, multipart) {
  const headers = { Origin: frontendUrl };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await api.fetch(path, { method: 'POST', headers, multipart });
  return { status: response.status(), headers: response.headers(), body: await safeJson(response) };
}

function data(result) { return result?.body?.data ?? null; }

function failureEnvelopeMatches(result, expectedStatus, expectedCode) {
  const body = result?.body;
  const error = body?.error;
  const timestamp = body?.timestamp;
  return result?.status === expectedStatus
    && body?.success === false
    && body?.data === null
    && error !== null
    && typeof error === 'object'
    && !Array.isArray(error)
    && error.code === expectedCode
    && typeof error.message === 'string'
    && error.message.trim().length > 0
    && Array.isArray(error.details)
    && typeof timestamp === 'string'
    && Number.isFinite(Date.parse(timestamp));
}

function hasAdminListShape(result) {
  const payload = result?.body?.data;
  const users = payload?.users;
  const summary = payload?.summary;
  const pageKeys = ['items', 'page', 'size', 'totalElements', 'totalPages', 'hasNext', 'hasPrevious'];
  const summaryKeys = ['totalUsers', 'joinedThisWeek', 'suspendedUsers'];
  return payload !== null
    && typeof payload === 'object'
    && users !== null
    && typeof users === 'object'
    && Array.isArray(users.items)
    && pageKeys.every((key) => Object.hasOwn(users, key))
    && Number.isInteger(users.page)
    && Number.isInteger(users.size)
    && Number.isInteger(users.totalPages)
    && summary !== null
    && typeof summary === 'object'
    && summaryKeys.every((key) => Number.isInteger(summary[key]));
}

function cookiePair(headers) {
  const raw = headers?.['set-cookie'];
  if (typeof raw !== 'string' || !raw) return null;
  const first = raw.split(/,(?=[^;=]+=[^;]+)/, 1)[0];
  const pair = first.split(';', 1)[0];
  return pair.includes('=') ? pair : null;
}

function cookiePolicy(headers) {
  const raw = headers?.['set-cookie'];
  if (typeof raw !== 'string' || !raw) return { present: false, httpOnly: false, sameSite: null, path: null, maxAge: null };
  const first = raw.split(/,(?=[^;=]+=[^;]+)/, 1)[0];
  const attributes = first.split(';').slice(1).map((part) => part.trim());
  const lower = attributes.map((part) => part.toLowerCase());
  const path = attributes.find((part) => /^path=/i.test(part))?.slice(5) ?? null;
  const sameSite = attributes.find((part) => /^samesite=/i.test(part))?.slice(9).toLowerCase() ?? null;
  const maxAgeValue = attributes.find((part) => /^max-age=/i.test(part))?.slice(8) ?? null;
  const maxAge = maxAgeValue !== null && /^\d+$/.test(maxAgeValue) ? Number(maxAgeValue) : null;
  return {
    present: first.includes('='),
    httpOnly: lower.includes('httponly'),
    sameSite,
    path,
    maxAge,
  };
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
    const rotatedAccessToken = (await rotated.json().catch(() => null))?.data?.accessToken;
    const rotatedProtected = typeof rotatedAccessToken === 'string'
      ? await ownerContext.get('/api/v1/auth/me', { headers: { Authorization: `Bearer ${rotatedAccessToken}` } })
      : null;

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
        && oldCookie !== newCookie
        && rotatedProtected?.status() === 200,
      'isolated owner context received a distinct rotated refresh cookie and its access token reached /auth/me',
    );
    const initialCookiePolicy = cookiePolicy(signed.headers());
    const rotatedCookiePolicy = cookiePolicy(rotated.headers());
    check(
      'refresh-cookie-policy',
      [initialCookiePolicy, rotatedCookiePolicy].every((policy) => (
        policy.present
          && policy.httpOnly
          && policy.sameSite === 'strict'
          && policy.path === '/api/v1/auth'
          && Number.isInteger(policy.maxAge)
          && policy.maxAge > 0
      )),
      'refresh cookies expose HttpOnly, SameSite=Strict, /api/v1/auth Path and positive Max-Age without recording cookie values',
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
  const expected = {
    '/api/v1/auth/register': ['post'],
    '/api/v1/auth/signin': ['post'],
    '/api/v1/auth/refresh': ['post'],
    '/api/v1/auth/signout': ['post'],
    '/api/v1/posts': ['post'],
    '/api/v1/universe/requests/{targetUserId}': ['post'],
    '/api/v1/notifications': ['get'],
    '/api/v1/admin/users': ['get'],
    '/api/v1/uploads': ['post'],
    '/api/v1/search': ['get'],
  };
  const requiredPathsOk = response.status() === 200
    && Object.entries(expected).every(([path, methods]) => (
      paths[path] && methods.every((method) => typeof paths[path][method] === 'object')
    ));
  check('runtime-openapi', response.status() === 200 && spec && typeof spec === 'object', 'generated /v3/api-docs reachable');
  check('openapi-required-paths', requiredPathsOk, 'required M10 endpoint paths and methods exist in generated OpenAPI');

  const operations = [];
  for (const [path, item] of Object.entries(paths)) {
    for (const [method, operation] of Object.entries(item ?? {})) {
      if (!['get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'trace'].includes(method)) continue;
      operations.push({ path, method, operation });
    }
  }
  const operationIds = operations.map(({ operation }) => operation?.operationId).filter(Boolean);
  const operationIdsUnique = operationIds.length === new Set(operationIds).size;
  const bearer = spec?.components?.securitySchemes?.bearerAuth;
  const bearerContract = bearer?.type === 'http' && String(bearer.scheme).toLowerCase() === 'bearer';
  const securedOperations = operations.filter(({ path, method }) => !publicOpenApiOperations.has(`${method}:${path}`));
  const securityOk = securedOperations.every(({ operation }) => {
    const security = operation?.security ?? spec?.security;
    return Array.isArray(security) && security.some((entry) => entry && typeof entry === 'object' && Object.hasOwn(entry, 'bearerAuth'));
  });
  const responseSchemaOk = operations.every(({ operation }) => {
    if (!operation || !operation.responses || typeof operation.responses !== 'object') return false;
    return Object.entries(operation.responses).every(([status, responseEntry]) => {
      if (status === '204' || status === 'default') return true;
      const content = responseEntry?.content ?? {};
      return Object.values(content).some((media) => media?.schema);
    });
  });
  const requestSchemaOk = operations.every(({ operation }) => {
    const request = operation?.requestBody;
    if (!request) return true;
    return Object.values(request.content ?? {}).some((media) => media?.schema);
  });
  const errorSchema = new Map(['400', '401', '403', '404', '409'].map((status) => [status, false]));
  for (const { operation } of operations) {
    for (const [status, responseEntry] of Object.entries(operation?.responses ?? {})) {
      if (!errorSchema.has(status)) continue;
      const hasSchema = Object.values(responseEntry?.content ?? {}).some((media) => media?.schema);
      errorSchema.set(status, errorSchema.get(status) || hasSchema);
    }
  }
  const errorSchemaOk = [...errorSchema.values()].every(Boolean);
  const coverageOk = response.status() === 200
    && operations.length === 61
    && operationIds.length === 61
    && operationIdsUnique
    && bearerContract
    && securityOk
    && requestSchemaOk
    && responseSchemaOk
    && errorSchemaOk;
  check(
    'openapi-coverage',
    coverageOk,
    `OpenAPI operations=${operations.length}/61, uniqueOperationId=${operationIdsUnique}, bearer=${bearerContract && securityOk}, requestSchemas=${requestSchemaOk}, responseSchemas=${responseSchemaOk}, errorSchemas=${[...errorSchema.entries()].filter(([, ok]) => ok).map(([status]) => status).join('|') || 'none'}`,
  );
  if (!requiredPathsOk || !coverageOk) throw new Error('OpenAPI readiness/coverage failed');
}

async function runHttpAcceptance() {
  owner = await registerAndSetup('owner');
  friend = await registerAndSetup('friend');
  const pending = await registerPending('pending');
  const categories = await call('GET', `/api/v1/blogs/${owner.blogId}/categories`, owner.token);
  const categoryItems = Array.isArray(data(categories)?.items) ? data(categories).items : (Array.isArray(data(categories)) ? data(categories) : []);
  const defaultCategory = categoryItems.find((item) => item.type === 'DEFAULT') ?? categoryItems[0];
  const categoryId = Number(defaultCategory?.id);
  const generalName = `M10 General ${suffix}`;
  const generalCategory = await call('POST', `/api/v1/blogs/${owner.blogId}/categories`, owner.token, {
    name: generalName, parentId: null, type: 'GENERAL', displayOrder: 1,
  });
  const generalCategoryData = data(generalCategory);
  const generalCategoryId = Number(generalCategoryData?.id);
  evidence.fixture.categoryIds = {
    default: Number.isInteger(categoryId) ? categoryId : null,
    general: Number.isInteger(generalCategoryId) ? generalCategoryId : null,
  };
  check(
    'general-category-fixture',
    generalCategory.status === 201
      && Number.isInteger(generalCategoryId)
      && generalCategoryData?.type === 'GENERAL'
      && generalCategoryData?.name === generalName,
    'GENERAL category fixture was created through the current API for post/category keyboard and duplicate checks',
  );
  const duplicateGeneral = await call('POST', `/api/v1/blogs/${owner.blogId}/categories`, owner.token, {
    name: generalName, parentId: null, type: 'GENERAL', displayOrder: 2,
  });
  const emptyDraft = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: '', contentJson: { type: 'doc', content: [] }, contentHtml: '',
    categoryId: Number.isInteger(categoryId) ? categoryId : null, visibility: 'PRIVATE', publish: false,
    thumbnailUrl: null, tagNames: [], images: [],
  });
  check('draft-empty-allowed', emptyDraft.status === 201, 'draft permits empty title/body');
  const publishEmpty = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: '', contentJson: { type: 'doc', content: [] }, contentHtml: '',
    categoryId: Number.isInteger(categoryId) ? categoryId : null, visibility: 'PUBLIC', publish: true,
    thumbnailUrl: null, tagNames: [], images: [],
  });
  check(
    'publish-empty-rejected',
    failureEnvelopeMatches(publishEmpty, 400, 'VALIDATION_001'),
    'publishing an empty title/body returns the full failure envelope with VALIDATION_001',
  );
  const imageBytes = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  );
  const imageUpload = await callMultipart('/api/v1/uploads?purpose=POST_IMAGE', owner.token, {
    file: { name: `m10-image-${suffix}.png`, mimeType: 'image/png', buffer: imageBytes },
  });
  const imageUploadData = data(imageUpload);
  const imageOnlyUrl = String(imageUploadData?.imageUrl ?? '');
  const canonicalImageUrl = /^\/api\/v1\/uploads\/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\/content$/.test(imageOnlyUrl);
  check(
    'image-upload-fixture',
    imageUpload.status === 201
      && imageUploadData?.purpose === 'POST_IMAGE'
      && imageUploadData?.contentType === 'image/png'
      && Number(imageUploadData?.size) === imageBytes.length
      && canonicalImageUrl,
    'synthetic multipart POST_IMAGE upload returned a canonical content URL and exact byte metadata',
  );
  const imageOnly = await call('POST', '/api/v1/posts', owner.token, {
    blogId: owner.blogId, title: `M10 image-only ${suffix}`,
    contentJson: { type: 'doc', content: [{ type: 'image', attrs: { src: imageOnlyUrl, alt: 'M10 image' } }] },
    contentHtml: '', categoryId: Number.isInteger(categoryId) ? categoryId : null, visibility: 'PUBLIC', publish: true,
    thumbnailUrl: null, tagNames: [], images: [{ imageUrl: imageOnlyUrl, altText: 'M10 image', displayOrder: 0 }],
  });
  const imageOnlyData = data(imageOnly);
  check(
    'publish-image-only-allowed',
    imageOnly.status === 201
      && Number.isInteger(Number(imageOnlyData?.id))
      && imageOnlyData?.publishedAt
      && Number(imageOnlyData?.id) > 0
      && imageOnlyData?.thumbnailUrl == null
      && imageOnlyData?.contentJson?.content?.[0]?.type === 'image'
      && imageOnlyData?.contentJson?.content?.[0]?.attrs?.src === imageOnlyUrl
      && imageOnlyData?.images?.length === 1
      && imageOnlyData?.images?.[0]?.imageUrl === imageOnlyUrl
      && canonicalImageUrl,
    'publishing an image-only document is allowed with the uploaded canonical URL in contentJson and image snapshot, without thumbnail',
  );
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
  check(
    'public-guest-read',
    guestRead.status === 200
      && Number(data(guestRead)?.id) === Number(post?.id)
      && data(guestRead)?.visibility === 'PUBLIC',
    'guest can read the expected PUBLIC post by fixture ID',
  );
  const envelope = guestRead.body && typeof guestRead.body.success === 'boolean' && 'timestamp' in guestRead.body;
  check('common-envelope', envelope, 'response includes common success/timestamp envelope');
  const invalidBearer = await call('GET', `/api/v1/posts/${post?.id}`, 'not-a-token');
  check(
    'invalid-bearer-rejected',
    failureEnvelopeMatches(invalidBearer, 401, 'AUTH_004'),
    'malformed bearer returns the full failure envelope with AUTH_004',
  );
  const list = await call('GET', `/api/v1/blogs/${owner.blogId}/posts?page=0&size=20`, owner.token);
  const page = data(list);
  const pageKeys = ['items', 'page', 'size', 'totalElements', 'totalPages', 'hasNext', 'hasPrevious'];
  check(
    'pagination-shape',
    list.status === 200
      && pageKeys.every((key) => Object.hasOwn(page ?? {}, key))
      && Number.isInteger(page?.page)
      && Number.isInteger(page?.size)
      && Number.isInteger(page?.totalPages)
      && Array.isArray(page?.items),
    'offset PageResponse contains all canonical fields',
  );
  const bound = await call('GET', `/api/v1/blogs/${owner.blogId}/posts?page=0&size=101`, owner.token);
  check(
    'paging-bound',
    failureEnvelopeMatches(bound, 400, 'VALIDATION_001'),
    'page size above the 100-row bound returns the full failure envelope with VALIDATION_001',
  );

  const invalidRegistration = await call('POST', '/api/v1/auth/register', undefined, {});
  const unauthenticatedDrafts = await call('GET', '/api/v1/posts/drafts', undefined);
  const missingPost = await call('GET', '/api/v1/posts/999999999', undefined);
  const duplicateCategory = duplicateGeneral;
  const httpStatusMatrixBase = failureEnvelopeMatches(invalidRegistration, 400, 'VALIDATION_001')
    && failureEnvelopeMatches(unauthenticatedDrafts, 401, 'AUTH_004')
    && failureEnvelopeMatches(missingPost, 404, 'POST_001')
    && failureEnvelopeMatches(duplicateCategory, 409, 'CAT_005');

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
  const deniedBeforeAccept = beforeAccept.status === 403;
  check(
    'http-status-matrix',
    httpStatusMatrixBase && failureEnvelopeMatches(beforeAccept, 403, 'POST_002'),
    'representative HTTP 400/401/403/404/409 cases returned exact status/code and the full failure envelope',
  );

  const request = await call('POST', `/api/v1/universe/requests/${owner.id}`, friend.token);
  const requestId = Number(data(request)?.requestId ?? data(request)?.id);
  check(
    'universe-request',
    request.status === 201
      && Number.isInteger(requestId)
      && Number(data(request)?.user?.id) === Number(owner.id)
      && data(request)?.status === 'PENDING',
    'universe request created for the expected owner target with PENDING status',
  );
  const received = await call('GET', '/api/v1/universe/requests/received', owner.token);
  const receivedItem = data(received)?.items?.find((item) => Number(item.requestId) === requestId);
  const accepted = await call('POST', `/api/v1/universe/requests/${receivedItem?.requestId ?? requestId}/accept`, owner.token);
  check(
    'universe-accept',
    accepted.status === 200
      && Number(data(accepted)?.target?.id) === Number(friend.id)
      && data(accepted)?.incoming?.status === 'ACCEPTED',
    'owner accepted the exact received request and the relation snapshot targets friend',
  );
  const universeRead = await call('GET', `/api/v1/posts/${universePost?.id}`, friend.token);
  check(
    'universe-post-read',
    deniedBeforeAccept
      && universePublished.status === 201
      && universeRead.status === 200
      && Number(data(universeRead)?.id) === Number(universePost?.id)
      && data(universeRead)?.visibility === 'UNIVERSE',
    `UNIVERSE post denied before acceptance (${beforeAccept.status}) and allowed after acceptance (${universeRead.status})`,
  );

  const interactionPostId = Number(universePost?.id) || Number(post?.id);
  const comment = await call('POST', `/api/v1/posts/${interactionPostId}/comments`, friend.token, { content: `M10 comment ${suffix}` });
  const commentId = Number(data(comment)?.id);
  evidence.fixture.commentIds.root = Number.isInteger(commentId) ? commentId : null;
  check('comment-create', comment.status === 201 && Number(data(comment)?.postId) === interactionPostId, 'comment created for the expected post ID');
  const like = await call('POST', `/api/v1/posts/${interactionPostId}/likes`, friend.token);
  check('like-create', (like.status === 200 || like.status === 201) && Number(data(like)?.postId) === interactionPostId && data(like)?.liked === true, 'like created for the expected post ID and reports liked=true');
  const notifications = await call('GET', '/api/v1/notifications?page=0&size=20', owner.token);
  const notificationItems = Array.isArray(data(notifications)?.items) ? data(notifications).items : [];
  check('notification-list', notifications.status === 200 && Array.isArray(data(notifications)?.items), 'notification list reachable with items page');
  const protectedCacheControl = notifications.headers['cache-control'] ?? '';
  check(
    'private-no-store',
    notifications.status === 200 && /private/i.test(protectedCacheControl) && /no-store/i.test(protectedCacheControl),
    'authenticated notification list has both private and no-store cache directives',
  );
  const actorMatches = (item) => Number(item?.actor?.id) === Number(friend.id);
  const requestNotification = notificationItems.find((item) => actorMatches(item)
    && item.type === 'NEIGHBOR' && item.targetType === 'UNIVERSE' && Number(item.targetId) === requestId);
  const commentNotification = notificationItems.find((item) => actorMatches(item)
    && item.type === 'COMMENT' && item.targetType === 'COMMENT' && Number(item.targetId) === commentId);
  const likeNotification = notificationItems.find((item) => actorMatches(item)
    && item.type === 'LIKE' && item.targetType === 'POST' && Number(item.targetId) === interactionPostId);
  check(
    'notification-event-identity',
    Boolean(requestNotification?.id && commentNotification?.id && likeNotification?.id),
    `identified request=${Boolean(requestNotification?.id)}, comment=${Boolean(commentNotification?.id)}, like=${Boolean(likeNotification?.id)} by actor/type/target ID`,
  );
  if (commentNotification?.id && likeNotification?.id) {
    const read = await call('PUT', `/api/v1/notifications/${commentNotification.id}/read`, owner.token);
    const deleted = await call('DELETE', `/api/v1/notifications/${likeNotification.id}`, owner.token);
    const reread = await call('GET', '/api/v1/notifications?page=0&size=20', owner.token);
    const remaining = Array.isArray(data(reread)?.items) ? data(reread).items : [];
    const readItem = data(read);
    check(
      'notification-read-delete',
      read.status === 200
        && Number(readItem?.id) === Number(commentNotification.id)
        && readItem?.readAt
        && deleted.status === 200
        && reread.status === 200
        && !remaining.some((item) => Number(item.id) === Number(likeNotification.id)),
      'exact identified comment notification was read and exact like notification was deleted',
    );
  } else check('notification-read-delete', false, 'NOT RUN: exact comment/like notification identities were not found', { required: true });
  return { owner, friend, pending, post };
}

async function runAdminAcceptance() {
  const email = process.env.M10_ADMIN_EMAIL;
  const password = process.env.M10_ADMIN_PASSWORD;
  if (!syntheticEmail(email) || !password) {
    check('admin-list-probe', false, 'NOT RUN: synthetic admin credential fixture was not supplied', { required: false });
    return;
  }

  const signed = await call('POST', '/api/v1/auth/signin', undefined, { email, password });
  const token = data(signed)?.accessToken;
  const listing = token
    ? await call('GET', '/api/v1/admin/users?page=0&size=1', token)
    : null;
  const responseText = JSON.stringify(listing?.body ?? '');
  const noCredentialFields = !/(?:password|refreshToken|accessToken|clientSecret|privateKey)/i.test(responseText);
  const adminListShape = hasAdminListShape(listing);
  check(
    'admin-list-shape',
    adminListShape,
    adminListShape
      ? 'admin list uses canonical data.users PageResponse plus summary fields'
      : 'NOT RUN: admin list did not expose canonical data.users PageResponse plus summary',
    { required: false },
  );
  check(
    'admin-list-probe',
    signed.status === 200
      && typeof token === 'string'
      && listing?.status === 200
      && adminListShape
      && noCredentialFields,
    'synthetic admin reached admin list with canonical users/summary shape and without credential fields; authoritative admin ACL/mutation checks require the named runtime evidence bundle',
    { required: false },
  );
}

async function runBrowserAcceptance(accounts) {
  const { chromium } = require(playwrightModule);
  await mkdir(outputDir, { recursive: true });
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
      if (await email.count() === 0) return false;
      const authForm = email.locator('xpath=ancestor::form[1]');
      const password = authForm.locator('input[type="password"]').first();
      if (await authForm.count() === 0 || await password.count() === 0) return false;
      await email.fill(account.email);
      await password.fill(account.password);
      const namedSubmit = authForm.getByRole('button', { name: /접속하기/ }).first();
      const submit = await namedSubmit.count() > 0
        ? namedSubmit
        : authForm.locator('button[type="submit"]').first();
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
      if (visible) {
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
        const escapeStillVisible = await dialog.isVisible().catch(() => false);
        if (!escapeStillVisible) {
          await trigger.focus();
          await adminPage.keyboard.press('Enter');
        }
        const documentedClosePathAvailable = await dialog.isVisible().catch(() => false);
        const cancel = dialog.getByRole('button', { name: '취소' }).first();
        if (await cancel.count() > 0 && await cancel.isVisible().catch(() => false)) {
          await cancel.focus();
          await adminPage.keyboard.press('Enter');
        }
        const closedByDocumentedCancel = !(await dialog.isVisible().catch(() => false));
        check(
          'keyboard-modal-observation',
          opened && documentedClosePathAvailable && closedByDocumentedCancel,
          `status trigger opened=${opened}; Escape left dialog visible=${escapeStillVisible} (observation-only); documented cancel path available=${documentedClosePathAvailable} and closed=${closedByDocumentedCancel}; focus-trap/restore remains observation-only`,
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
        const escapeStillVisible = await dialog.isVisible().catch(() => false);
        if (!escapeStillVisible) {
          await trigger.focus();
          await ownerPage.keyboard.press('Enter');
        }
        const documentedClosePathAvailable = await dialog.isVisible().catch(() => false);
        const close = dialog.getByRole('button', { name: '임시저장 목록 닫기' }).first();
        if (await close.count() > 0 && await close.isVisible().catch(() => false)) {
          await close.focus();
          await ownerPage.keyboard.press('Enter');
        }
        const closedByDocumentedButton = !(await dialog.isVisible().catch(() => false));
        check(
          'keyboard-draft-picker-observation',
          opened && documentedClosePathAvailable && closedByDocumentedButton,
          `DraftPicker trigger Enter opened=${opened}; Escape left dialog visible=${escapeStillVisible} (observation-only); documented close path available=${documentedClosePathAvailable} and closed=${closedByDocumentedButton}; focus-trap remains observation-only`,
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

async function verifyScreenshotArtifacts() {
  const expectedKeys = canonicalScreens.map(([key]) => key);
  const actualKeys = evidence.screenshots.map((entry) => entry.key);
  const uniqueKeys = new Set(actualKeys);
  let artifactOk = evidence.screenshots.length === expectedKeys.length
    && uniqueKeys.size === expectedKeys.length
    && expectedKeys.every((key) => uniqueKeys.has(key));
  const verified = [];
  for (const entry of evidence.screenshots) {
    try {
      const file = resolve(outputDir, entry.file);
      if (relative(outputDir, file).startsWith('..')) throw new Error('screenshot path escapes evidence directory');
      const bytes = await readFile(file);
      const png = bytes.length >= 24
        && bytes.subarray(0, 8).toString('hex') === '89504e470d0a1a0a'
        && bytes.subarray(12, 16).toString('ascii') === 'IHDR';
      const width = png ? bytes.readUInt32BE(16) : null;
      const height = png ? bytes.readUInt32BE(20) : null;
      const digest = await sha256(file);
      const ok = bytes.length > 0 && png && width === 1440 && height === 1000 && isSha256(digest);
      artifactOk = artifactOk && ok;
      verified.push({
        key: entry.key,
        file: entry.file,
        path: entry.path,
        status: entry.status,
        bytes: bytes.length,
        width,
        height,
        sha256: digest,
        ok,
      });
    } catch (error) {
      artifactOk = false;
      verified.push({ key: entry.key, file: entry.file, ok: false, detail: `NOT RUN: ${scrub(error instanceof Error ? error.message : error)}` });
    }
  }
  evidence.screenshots = verified;
  check(
    'screenshots-complete',
    artifactOk,
    artifactOk
      ? 'all 13 canonical screenshots are unique nonzero PNGs at 1440x1000 with SHA-256 evidence'
      : 'NOT RUN: exactly 13 unique canonical 1440x1000 PNG artifacts were not verified',
    { required: true },
  );
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
  const source = await readFile(harnessPath, 'utf8');
  const staleFalsePass = [
    ['check(\'admin-current-actor\',', 'Boolean('].join(''),
    ['check(\'admin-soft-delete-preservation\',', 'Boolean('].join(''),
    ['page.keyboard.press(\'Escape\');', 'check(id,', 'true'].join(''),
    ['locator(\'body\').evaluate((body)', 'Boolean(body'].join(''),
    ['Boolean(', 'before && after && stillVisible)'].join(''),
    ['M10_SCREENSHOTS', '!==', '\'0\''].join(''),
    ['screenshotsEnabled', '=', 'process.env.M10_SCREENSHOTS'].join(''),
    ['function ', 'markRuntimeEvidence', 'NotRun'].join(''),
    ['Array.isArray(listingData?.items)', 'admin-list-probe'].join(''),
    ['page.locator(\'form button[type="submit"]\')', '.first()'].join(''),
  ].some((pattern) => source.includes(pattern));
  check('harness-fail-closed-guards', !staleFalsePass, 'runner contains no known false-pass guard patterns', { required: true });
  const browserSigninFormSelectors = [
    "locator('xpath=ancestor::form[1]')",
    "getByRole('button', { name: /접속하기/ })",
    "authForm.locator('button[type=\"submit\"]')",
  ];
  check(
    'self-check-browser-signin-form',
    browserSigninFormSelectors.every((selector) => source.includes(selector))
      && !source.includes("page.locator('form button[type=\"submit\"]')"),
    'browser signin submits within the email field ancestor form and prefers the 접속하기 accessible name, never a global first submit',
    { required: true },
  );
  const selfCheckRunId = process.env.M10_RUN_ID ?? 'm10-self-check-current';
  const staleTimestamp = new Date(Date.now() - (attestationSkewMs * 2)).toISOString();
  const negativeFixtures = [
    { schema: 'wrong', status: 'PASS' },
    { schema: runtimeEvidenceSchema, status: 'NOT RUN' },
    { schema: runtimeEvidenceSchema, status: 'PASS', mysql: { host: '127.0.0.1', port: 13307, database: 'zeroverse_m10' }, runId: 'm10-stale', attestation: { schema: runtimeAttestationSchema, runId: 'm10-stale' } },
    { schema: runtimeEvidenceSchema, status: 'PASS', mysql: { host: '127.0.0.1', port: 13320, database: 'wrong_db' }, runId: 'm10-forged', attestation: { schema: runtimeAttestationSchema, runId: 'm10-forged' } },
    { schema: runtimeEvidenceSchema, status: 'PASS', mysql: { host: approvedMysqlHost, port: approvedMysqlPort, database: 'zeroverse_m10' }, runId: selfCheckRunId, generatedAt: staleTimestamp, attestation: { schema: runtimeAttestationSchema, runId: selfCheckRunId, observedAt: staleTimestamp } },
  ];
  const negativeFixturesRejected = negativeFixtures.every((fixture) => (
    fixture.schema !== runtimeEvidenceSchema
      || fixture.status !== 'PASS'
      || fixture.mysql?.host !== approvedMysqlHost
      || fixture.mysql?.port !== approvedMysqlPort
      || fixture.mysql?.database !== 'zeroverse_m10'
      || fixture.runId !== selfCheckRunId
      || fixture.attestation?.schema !== runtimeAttestationSchema
      || fixture.attestation?.runId !== selfCheckRunId
      || !withinCurrentRun(fixture.generatedAt)
      || !withinCurrentRun(fixture.attestation?.observedAt)
  ));
  check('self-check-negative-fixtures', negativeFixturesRejected, 'synthetic malformed/NOT RUN/user-DB/stale-run/forged-database runtime fixtures remain non-pass', { required: true });
  const validFailureTimestamp = new Date().toISOString();
  const failureEnvelopeFixtures = [
    {
      result: {
        status: 400,
        body: {
          success: false,
          data: null,
          error: { code: 'VALIDATION_001', message: 'invalid request', details: [] },
          timestamp: validFailureTimestamp,
        },
      },
      expected: true,
    },
    {
      result: {
        status: 401,
        body: {
          success: true,
          data: null,
          error: { code: 'AUTH_004', message: 'authentication required', details: [] },
          timestamp: validFailureTimestamp,
        },
      },
      expected: false,
    },
    {
      result: {
        status: 403,
        body: {
          success: false,
          data: {},
          error: { code: 'POST_002', message: 'forbidden', details: [] },
          timestamp: validFailureTimestamp,
        },
      },
      expected: false,
    },
    {
      result: {
        status: 404,
        body: {
          success: false,
          data: null,
          error: { code: 'POST_001' },
          timestamp: validFailureTimestamp,
        },
      },
      expected: false,
    },
    {
      result: {
        status: 409,
        body: {
          success: false,
          data: null,
          error: { code: 'CAT_005', message: 'duplicate', details: [] },
          timestamp: 'not-a-timestamp',
        },
      },
      expected: false,
    },
  ];
  const failureEnvelopeSelfCheck = failureEnvelopeFixtures.every(({ result, expected }) => (
    failureEnvelopeMatches(result, result.status, result.body.error.code) === expected
  ));
  check(
    'self-check-failure-envelope',
    failureEnvelopeSelfCheck,
    'representative failure fixtures require success=false, data=null, structured error, and a valid timestamp',
    { required: true },
  );
  const manifestOrderingFixtures = [
    { paths: ['src/a', 'src/Z'], expected: false },
    { paths: ['src/Z', 'src/a'], expected: true },
    { paths: ['src/Z', 'src/Z'], expected: false },
    { paths: ['src/../a', 'src/Z'], expected: false },
  ];
  const manifestOrderingSelfCheck = manifestOrderingFixtures.every(({ paths, expected }) => (
    isCanonicalManifestOrder(paths) === expected
  ));
  check(
    'self-check-manifest-ordering',
    manifestOrderingSelfCheck,
    'manifest ordering fixtures reject locale-sensitive/duplicate/unsafe order and accept Python-compatible canonical order',
    { required: true },
  );
  const canonicalAdminListFixture = {
    body: {
      data: {
        users: {
          items: [], page: 0, size: 1, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false,
        },
        summary: { totalUsers: 0, joinedThisWeek: 0, suspendedUsers: 0 },
      },
    },
  };
  const legacyAdminListFixture = {
    body: { data: { items: [], summary: { totalUsers: 0, joinedThisWeek: 0, suspendedUsers: 0 } } },
  };
  check(
    'self-check-admin-list-shape',
    hasAdminListShape(canonicalAdminListFixture) && !hasAdminListShape(legacyAdminListFixture),
    'admin shape fixture requires data.users PageResponse and summary, rejecting legacy data.items',
    { required: true },
  );
  await runProductStaticScan();
  if (canonicalScreens.length !== 13) throw new Error('canonical screen list must contain exactly 13 routes');
  const requiredPublicOperations = [
    'get:/api/v1/blogs/slug/{urlSlug}', 'get:/api/v1/blogs/{blogId}/posts',
    'get:/api/v1/blogs/slug/{urlSlug}/posts', 'get:/api/v1/blogs/{blogId}/categories',
    'get:/api/v1/posts/{postId}/comments', 'get:/api/v1/posts/{postId}/interactions',
    'get:/api/v1/feed/public',
  ];
  if (!requiredPublicOperations.every((operation) => publicOpenApiOperations.has(operation))) {
    throw new Error('public OpenAPI operation allowlist is incomplete');
  }
  if (requiredChecks.length < 50) throw new Error('required M10 checks unexpectedly incomplete');
}

function validateFullRunGuards() {
  if (selfCheck) return;
  if (process.env.M10_SCREENSHOTS === '0') throw new Error('M10 full acceptance cannot disable canonical screenshots');
  if (process.env.M10_MYSQL_HOST !== approvedMysqlHost) {
    throw new Error(`M10_MYSQL_HOST must be explicit loopback ${approvedMysqlHost}`);
  }
  if (!process.env.M10_RUN_ID || !/^m10-[A-Za-z0-9_-]+$/.test(process.env.M10_RUN_ID)) {
    throw new Error('M10_RUN_ID is required and must match the synthetic current run identity');
  }
  const configuredPort = Number(process.env.M10_MYSQL_PORT);
  if (!process.env.M10_MYSQL_PORT) throw new Error('M10_MYSQL_PORT is required for full acceptance');
  if (configuredPort === 13307) throw new Error('refusing user MySQL port 13307');
  if (configuredPort !== approvedMysqlPort) throw new Error(`M10_MYSQL_PORT must be approved synthetic port ${approvedMysqlPort}`);
  if (apiBaseUrl !== approvedApiBaseUrl || frontendUrl !== approvedFrontendUrl) {
    throw new Error(`M10 API/FE endpoints must be ${approvedApiBaseUrl} and ${approvedFrontendUrl}`);
  }
  if (!runtimeEvidencePath || relative(root, runtimeEvidencePath).startsWith('..')) {
    throw new Error('M10_RUNTIME_EVIDENCE must point to a repository-local runtime bundle');
  }
}

async function currentManifestIdentity() {
  const manifest = evidence.sourceManifest;
  if (!manifest || !Array.isArray(manifest.files)) return null;
  const entries = [];
  for (const entry of manifest.files) {
    const file = resolve(root, entry.path);
    entries.push({ path: entry.path, sha256: await sha256(file) });
  }
  const sourceSha256 = createHash('sha256')
    .update(entries.map((entry) => `${entry.path}\0${entry.sha256}\n`).join(''))
    .digest('hex');
  const jarPath = resolve(root, manifest.jarPath ?? 'build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar');
  const frontendDistPath = resolve(root, manifest.frontendDistPath ?? 'frontend/dist');
  assertInsideRoot(jarPath, 'runtime JAR');
  assertInsideRoot(frontendDistPath, 'frontend dist');
  const git = await currentGitIdentity();
  return {
    sourceSha256,
    jarSha256: await sha256(jarPath),
    frontendSha256: await sha256Directory(frontendDistPath),
    harnessSha256: await sha256(harnessPath),
    gitCommit: git.commit,
    gitTree: git.tree,
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
          && before.frontendSha256 === after.frontendSha256
          && before.harnessSha256 === after.harnessSha256
          && before.gitCommit === after.gitCommit
          && before.gitTree === after.gitTree,
        before && after
          ? 'source aggregate, JAR, FE dist, Git commit/tree, and harness hashes are stable before/after acceptance'
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
  const selfCheckFailures = failures.filter((item) => new Set([
    'harness-fail-closed-guards', 'static-skip-placeholder-scan', 'self-check-negative-fixtures', 'self-check-failure-envelope', 'self-check-manifest-ordering', 'self-check-admin-list-shape', 'self-check-browser-signin-form',
  ]).has(item.id));
  evidence.status = selfCheck
    ? selfCheckFailures.length === 0 && evidence.failures.length === 0 ? 'SELF-CHECK PASS' : 'SELF-CHECK FAIL'
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
    validateFullRunGuards();
    await verifyManifest();
    await verifyRuntimeEvidence();
    requestFactory = require(playwrightModule).request;
    api = await requestFactory.newContext({ baseURL: apiBaseUrl, extraHTTPHeaders: { Origin: frontendUrl } });
    await verifyOpenApi();
    const accounts = await runHttpAcceptance();
    await runAdminAcceptance();
    await runBrowserAcceptance(accounts);
    await verifyScreenshotArtifacts();
  } catch (error) {
    evidence.failures.push(scrub(error instanceof Error ? error.message : error));
  } finally {
    await api?.dispose();
    await browser?.close();
    await writeEvidence();
  }
  if (!['PASS', 'SELF-CHECK PASS'].includes(evidence.status)) process.exitCode = 1;
}

await main();
