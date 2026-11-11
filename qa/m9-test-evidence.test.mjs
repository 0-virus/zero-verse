import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, cp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

const repository = path.resolve(import.meta.dirname, '..');
const physicalXml = 'TEST-com.zeroverse.domain.admin.AdminConcurrencyMySqlTest.xml';
async function fixture(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'm9-evidence-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await cp(path.join(repository, 'build/m9-evidence/backend-final-2'), path.join(root, 'backend'), { recursive: true });
  await cp(path.join(repository, 'build/m9-evidence/frontend-final-result.json'), path.join(root, 'frontend.json'));
  await cp(path.join(repository, 'src/test'), path.join(root, 'src/test'), { recursive: true });
  await cp(path.join(repository, 'frontend/src/test'), path.join(root, 'frontend/src/test'), { recursive: true });
  return { root, backendDir: 'backend/test-results', backendSummary: 'backend/summary.json', frontendJson: 'frontend.json' };
}
async function collect(options) {
  let module;
  try { module = await import('./m9-test-evidence.mjs'); } catch { assert.fail('collectTestEvidence helper must exist'); }
  return module.collectTestEvidence(options);
}
const check = (result, id = 'concurrency-physical-barrier-evidence') => result.checks.find(item => item.id === id);
async function alterXml(options, mutate) {
  const file = path.join(options.root, options.backendDir, physicalXml);
  await writeFile(file, mutate(await readFile(file, 'utf8')));
}

test('current real artifacts pass scoped named coverage and expose uncovered matrix rows', async t => {
  const result = await collect(await fixture(t));
  assert.equal(result.checks.length, 5);
  assert.ok(result.checks.every(item => item.passed), JSON.stringify(result.checks.map(({ id, detail }) => ({ id, missing: detail.missing }))));
  assert.ok(result.mappings.uncoveredFrontendRows.some(row => row.id === 'admin-probe-pending'));
  assert.ok(result.mappings.uncoveredStatePolicyRows.some(row => row.id === 'reactivated-existing-refresh'));
  assert.equal(result.mappings.backendTotals.tests, 637);
  assert.equal(result.mappings.frontendTotals.files, 51);
  assert.ok(result.artifactHashes['backend/test-results/' + physicalXml]?.match(/^[a-f0-9]{64}$/));
});

for (const [name, mutate] of [
  ['testcase failure despite zero suite totals', xml => xml.replace(/(<testcase[^>]*\/>)/, tag => tag.replace('/>', '><failure message="changed"/></testcase>'))],
  ['testcase skipped despite zero suite totals', xml => xml.replace(/(<testcase[^>]*\/>)/, tag => tag.replace('/>', '><skipped/></testcase>'))],
  ['testcase error despite zero suite totals', xml => xml.replace(/(<testcase[^>]*\/>)/, tag => tag.replace('/>', '><error/></testcase>'))],
  ['suite failure despite passing testcases', xml => xml.replace('failures="0"', 'failures="1"')],
  ['missing case with deceptive comment marker', xml => xml.replace(/<testcase name="\[1\] change=ROLE"[^>]*\/>/, '<!-- <testcase name="[1] change=ROLE"/> -->')],
  ['wrong testcase class', xml => xml.replace('classname="com.zeroverse.domain.admin.AdminConcurrencyMySqlTest"', 'classname="other.Suite"')],
]) {
  test(`rejects ${name}`, async t => {
    const options = await fixture(t);
    await alterXml(options, mutate);
    assert.equal(check(await collect(options)).passed, false);
  });
}

test('rejects summary metadata that disagrees with actual XML counts', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, options.backendSummary);
  const summary = JSON.parse(await readFile(file, 'utf8'));
  summary.tests = 636;
  await writeFile(file, JSON.stringify(summary));
  assert.equal(check(await collect(options)).passed, false);
});

test('rejects failed FE assertion even when top-level success/counts claim PASS', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, options.frontendJson);
  const result = JSON.parse(await readFile(file, 'utf8'));
  result.testResults.find(item => item.name.endsWith('/adminAccess.test.tsx')).assertionResults[0].status = 'failed';
  await writeFile(file, JSON.stringify(result));
  assert.equal(check(await collect(options), 'frontend-matrix-evidence').passed, false);
});

test('rejects source method replaced by a comment while XML still claims PASS', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, 'src/test/java/com/zeroverse/domain/admin/AdminConcurrencyMySqlTest.java');
  await writeFile(file, '// mutualChangesLockByIdAndRejectStaleActor(Change change) { assertThat(true); }');
  assert.equal(check(await collect(options)).passed, false);
});

test('rejects required FE case renamed while total successful counts remain unchanged', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, options.frontendJson);
  const result = JSON.parse(await readFile(file, 'utf8'));
  const assertion = result.testResults.find(item => item.name.endsWith('/adminAccess.test.tsx')).assertionResults[0];
  assertion.fullName += ' changed';
  await writeFile(file, JSON.stringify(result));
  assert.equal(check(await collect(options), 'frontend-matrix-evidence').passed, false);
});

test('rejects required FE source test present only inside comments', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, 'frontend/src/test/adminAccess.test.tsx');
  const source = await readFile(file, 'utf8');
  await writeFile(file, source.split('\n').map(line => '// ' + line).join('\n'));
  assert.equal(check(await collect(options), 'frontend-matrix-evidence').passed, false);
});

test('rejects missing RR writer testcase instead of accepting a system-out marker', async t => {
  const options = await fixture(t);
  const file = path.join(options.root, options.backendDir, 'TEST-com.zeroverse.domain.admin.AdminQueryBoundMySqlTest.xml');
  const source = await readFile(file, 'utf8');
  await writeFile(file, source.replace(/<testcase name="목록 item[^>]*\/>/, '<system-out>목록 item 이후 writer가 commit해도 count·summary·aggregate는 같은 RR snapshot이다</system-out>'));
  assert.equal(check(await collect(options), 'backend-rr-writer-evidence').passed, false);
});
