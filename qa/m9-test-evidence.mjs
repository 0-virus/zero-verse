import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
const IDS = ['concurrency-physical-barrier-evidence', 'backend-rr-writer-evidence', 'backend-noop-audit-evidence', 'backend-state-policy-evidence', 'frontend-matrix-evidence'];
const admin = 'com.zeroverse.domain.admin.';
const expectedSuiteCounts = { AdminConcurrencyMySqlTest: 7, AdminQueryBoundMySqlTest: 3, AdminMutationMySqlTest: 7, AdminControllerTest: 10 };
const javaPath = name => `src/test/java/${name.replaceAll('.', '/')}.java`;
const required = [
  [0, admin + 'AdminConcurrencyMySqlTest', '[1] change=ROLE', 'mutualChangesLockByIdAndRejectStaleActor', 'actor-target-role-lock'],
  [0, admin + 'AdminConcurrencyMySqlTest', '[2] change=STATUS', 'mutualChangesLockByIdAndRejectStaleActor', 'actor-target-status-lock'],
  [0, admin + 'AdminConcurrencyMySqlTest', '[3] change=DELETE', 'mutualChangesLockByIdAndRejectStaleActor', 'actor-target-delete-lock'],
  [0, admin + 'AdminConcurrencyMySqlTest', '사용자 삭제가 먼저 user lock을 잡으면 대기 중 좋아요는 USER_001이고 원행위/알림은 없다', 'deleteBeforeSourceMutationRejectsQueuedLike', 'delete-before-like'],
  [0, admin + 'AdminConcurrencyMySqlTest', '좋아요가 먼저 user lock을 잡으면 commit 후 사용자만 삭제하고 좋아요와 알림을 보존한다', 'sourceMutationBeforeDeleteCommitsAndPreservesHistory', 'like-before-delete'],
  [0, admin + 'AdminConcurrencyMySqlTest', '정지가 user lock을 선취득하면 refresh 둘은 실제 lockwait 후 AUTH_003이고 세션행은 보존된다', 'suspensionBeforeRefreshStateReadRejectsBothRotations', 'suspend-before-refresh'],
  [0, admin + 'AdminConcurrencyMySqlTest', 'refresh의 ACTIVE read와 user lock이 먼저면 발급 commit 후 정지하고 후행 refresh/admin을 거부한다', 'refreshStateReadBeforeSuspensionCommitsOneRotationThenRejectsNewRequests', 'refresh-before-suspend'],
  [1, admin + 'AdminQueryBoundMySqlTest', '목록 item 이후 writer가 commit해도 count·summary·aggregate는 같은 RR snapshot이다', 'listRetainsOneSnapshotAcrossItemsCountAndSummary', 'list-rr-writer'],
  [1, admin + 'AdminQueryBoundMySqlTest', '상세는 인증 DB read 이후 writer commit에도 target·통계를 같은 RR snapshot에서 읽는다', 'detailRetainsSnapshotEstablishedByCurrentActorLookup', 'detail-rr-writer'],
  [2, admin + 'AdminMutationMySqlTest', 'sameValueIncludingSelfPreservesEveryAuditAndUserColumn()', 'sameValueIncludingSelfPreservesEveryAuditAndUserColumn', 'same-value-noop-audit'],
  [2, admin + 'AdminMutationMySqlTest', 'selfRevocationSuspensionAndDeletionHaveNoSideEffects()', 'selfRevocationSuspensionAndDeletionHaveNoSideEffects', 'self-protect-no-side-effects'],
  [2, admin + 'AdminMutationMySqlTest', 'deleteChangesOnlyUserDeletedAtAndUpdatedAtAndPreservesAllChildRows()', 'deleteChangesOnlyUserDeletedAtAndUpdatedAtAndPreservesAllChildRows', 'soft-delete-child-rows'],
  [2, admin + 'AdminMutationMySqlTest', 'roleAndStatusReturnCanonicalDetailAndChangeOnlyTheRequestedField()', 'roleAndStatusReturnCanonicalDetailAndChangeOnlyTheRequestedField', 'canonical-only-requested-field'],
  [3, admin + 'AdminControllerTest', 'everyAdminEndpointRechecksSuspendedDeletedAndDemotedActorWithNoStore()', 'everyAdminEndpointRechecksSuspendedDeletedAndDemotedActorWithNoStore', 'five-method-current-actor-policy'],
  [3, admin + 'AdminControllerTest', 'DB에서 승격된 USER claim도 관리자 목록에 접근한다', 'currentDatabaseAdminRoleOverridesStaleUserClaim', 'promoted-old-user-claim'],
  [3, admin + 'AdminMutationMySqlTest', 'currentActorErrorsTakePrecedenceOverMissingTargetForEveryMutation()', 'currentActorErrorsTakePrecedenceOverMissingTargetForEveryMutation', 'current-actor-error-precedence'],
  [3, 'com.zeroverse.domain.comment.CommentBehaviorTest', 'SUSPENDED actor는 댓글 update/delete 모두 USER_003이다', 'suspendedActorCannotMutateComment', 'suspended-comment-update-delete'],
  [3, 'com.zeroverse.domain.like.LikeControllerTest', 'SUSPENDED actor의 like/unlike mutation은 USER_003이다', 'suspendedActorCannotLikeOrUnlike', 'suspended-like-unlike'],
  [3, 'com.zeroverse.domain.universe.UniverseServiceTest', '정지된 actor의 신청은 USER_003이다', 'suspendedActorCannotRequest', 'suspended-universe-request'],
];

// These are reviewed source-test mappings, not an assertion that the entire browser matrix ran.
const frontend = [
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'guest 세션에서는 admin probe를 호출하지 않는다', 'guest-no-probe'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'stale USER claim이어도 size=1 probe 성공이면 관리자 접근을 허용한다', 'current-probe-promotion'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', '현재 actor가 non-admin이면 민감 화면을 허용하지 않는다', 'current-probe-denial'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'AdminRoute는 guest를 signin으로 보내고 admin endpoint를 호출하지 않는다', 'guest-route-guard'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'AdminRoute는 stale USER claim을 current probe 성공으로 승격한다', 'route-entry-probe'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'denied 상태도 관리자 경로 재진입에서 current probe로 승격한다', 'denied-entry-promotion'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', '허용된 관리자 화면은 브라우저 focus 복귀에서 권한을 다시 확인한다', 'focus-probe-demotion'],
  ['adminAccess.test.tsx', 'AdminAccessProvider', 'visible 복귀 이벤트도 허용된 관리자 화면의 current probe를 갱신한다', 'visible-probe-demotion'],
  ['adminApi.test.ts', 'adminApi', '목록은 trim된 keyword와 고정 paging만 query로 보낸다', 'api-query-normalization'],
  ['adminApi.test.ts', 'adminApi', '역할·상태 변경은 승인된 endpoint와 payload를 사용한다', 'api-mutation-contract'],
  ['adminApi.test.ts', 'adminApi', '잘못된 page/size와 100자를 넘는 keyword를 호출 전에 거부한다', 'api-query-validation'],
  ['adminPages.test.tsx', '관리자 화면', '목록은 검색·통계·관리 액션과 디자인 카피를 렌더한다', 'list-render'],
  ['adminPages.test.tsx', '관리자 화면', '상세는 lastAccess 미기록을 표시하고 역할·상태·삭제 확인을 제공한다', 'detail-copy-confirm'],
  ['adminPages.test.tsx', '관리자 화면', '상태 변경은 canonical 상세를 다시 읽고 삭제 확인은 승인 카피 뒤 delete를 호출한다', 'canonical-status-delete'],
  ['adminPages.test.tsx', '관리자 화면', 'transient 목록 오류에서도 직전 사용자 행과 pagination을 보존한다', 'transient-list-row-preservation'],
  ['adminRaces.test.tsx', '관리자 오류 경계', 'actor 권한 오류와 target USER_001을 구분할 수 있다', 'actor-target-error-classification'],
  ['adminRaces.test.tsx', '관리자 계정 세대 race', 'A probe의 늦은 성공은 B 계정 권한으로 승격시키지 않는다', 'stale-viewer-probe'],
  ['adminRaces.test.tsx', '관리자 계정 세대 race', '동일 계정의 역순 검색 응답은 최신 URL 결과만 화면에 반영한다', 'stale-search'],
  ['adminRaces.test.tsx', '관리자 계정 세대 race', '상세 GET의 역순 응답은 최신 target 상세만 유지한다', 'stale-detail-target'],
  ['adminRaces.test.tsx', 'AdminRoute와 상세 target/actor 경계', 'target USER_001은 current probe 성공 뒤 유한하게 target 404로 정착한다', 'target-404-probe'],
  ['adminRaces.test.tsx', 'AdminRoute와 상세 target/actor 경계', 'actor USER_001은 probe 실패 뒤 민감 상세와 target retry를 제거한다', 'actor-error-sensitive-retry-removal'],
  ['adminRaces.test.tsx', '상세 mutation target 세대', 'target7 mutation이 target8 전환 뒤 canonical 상세를 오염시키지 않는다', 'stale-mutation-target'],
  ['adminRaces.test.tsx', '상세 mutation target 세대', 'target7 삭제가 target8로 이탈한 뒤 /admin으로 강제 이동하지 않는다', 'stale-delete-navigation'],
  ['apiClient.test.ts', 'apiClient 401 자동 갱신', 'refresh가 실패하면 만료 핸들러를 부르고 토큰을 지운다', 'client-refresh-failure-expiry'],
  ['apiClient.test.ts', 'apiClient 401 자동 갱신', '재시도까지 401이면 만료로 처리하고 더 재시도하지 않는다', 'client-final-401-expiry'],
];

const XML_PARSER = `import sys,json,xml.etree.ElementTree as E
result=[]
for artifact in json.load(sys.stdin):
 raw=artifact['xml']
 if '<!DOCTYPE' in raw.upper() or '<!ENTITY' in raw.upper(): raise ValueError('DTD/entity declarations prohibited')
 root=E.fromstring(raw)
 if root.tag!='testsuite': raise ValueError('expected testsuite root')
 if root.findall('testsuite'): raise ValueError('nested suites unsupported')
 cases=[]
 for case in root.findall('testcase'):
  cases.append(dict(case.attrib, nonpassing=[node.tag for node in case.iter() if node.tag in ('failure','error','skipped')]))
 result.append({'path':artifact['path'],'attributes':root.attrib,'cases':cases,'nonpassing':[node.tag for node in root.iter() if node.tag in ('failure','error','skipped')]})
print(json.dumps(result,ensure_ascii=False))`;

// Keep offsets stable; strip comments and optionally quoted literals before locating real declarations.
function mask(source, literals = false) {
  let out = '', i = 0;
  const blank = text => text.replace(/[^\n]/g, ' ');
  while (i < source.length) {
    if (source.startsWith('//', i)) {
      const end = source.indexOf('\n', i); const stop = end < 0 ? source.length : end;
      out += blank(source.slice(i, stop)); i = stop;
    } else if (source.startsWith('/*', i)) {
      const end = source.indexOf('*/', i + 2); const stop = end < 0 ? source.length : end + 2;
      out += blank(source.slice(i, stop)); i = stop;
    } else if ('\'"`'.includes(source[i])) {
      const start = i, quote = source.startsWith('"""', i) ? '"""' : source[i]; i += quote.length;
      while (i < source.length) {
        if (source[i] === '\\') { i += 2; continue; }
        if (source.startsWith(quote, i)) { i += quote.length; break; }
        i++;
      }
      out += literals ? blank(source.slice(start, i)) : source.slice(start, i);
    } else { out += source[i++]; }
  }
  return out;
}
const escape = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
function bodyAt(source, open) {
  const structural = mask(source, true); let depth = 1, end = open + 1;
  for (; end < source.length && depth; end++) {
    if (structural[end] === '{') depth++;
    if (structural[end] === '}') depth--;
  }
  if (depth) throw new Error('unclosed test body');
  return mask(source.slice(open + 1, end - 1));
}
function javaBody(source, method, testcase) {
  const clean = mask(source);
  const expression = new RegExp(`\\bvoid\\s+${escape(method)}\\s*\\([^)]*\\)\\s*(?:throws[^\\{]+)?\\{`, 'g');
  const matches = [...clean.matchAll(expression)];
  if (matches.length !== 1) throw new Error(`source test declaration missing/duplicate: ${method}`);
  const found = matches[0], prefix = clean.slice(0, found.index);
  const previousClose = prefix.lastIndexOf('}');
  const annotations = prefix.slice(previousClose + 1);
  if (!/@(?:Test|ParameterizedTest)\b/.test(annotations)) throw new Error(`not an annotated test: ${method}`);
  if (testcase.startsWith('[')) {
    if (!/@ParameterizedTest\b/.test(annotations) || !annotations.includes('@EnumSource(Change.class)') || !annotations.includes('@DisplayName("상호 변경은 낮은 ID부터 잠그고 stale actor의 후행 변경을 거부한다")')) throw new Error(`parameterized source mapping missing: ${method}`);
  } else if (testcase !== `${method}()` && !annotations.includes(`@DisplayName(${JSON.stringify(testcase)})`)) {
    throw new Error(`DisplayName/source mapping missing: ${method}`);
  }
  const body = bodyAt(source, found.index + found[0].length - 1);
  if (!/\b(?:assertThat|assertBusiness|expect|fails|andExpect)\s*\(/.test(body)) throw new Error(`test has no assertion: ${method}`);
  return body;
}
function frontendBody(source, title) {
  const clean = mask(source);
  const expression = new RegExp(`\\bit\\(\\s*(['"])${escape(title)}\\1\\s*,\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>\\s*\\{`, 'g');
  const matches = [...clean.matchAll(expression)];
  if (matches.length !== 1) throw new Error(`source assertion declaration missing/duplicate: ${title}`);
  const found = matches[0];
  const body = bodyAt(source, found.index + found[0].length - 1);
  if (!/\bexpect\s*\(/.test(body)) throw new Error(`test has no assertion: ${title}`);
  return body;
}

/** Read-only scoped test evidence. Full matrix closure and source/JAR identity remain caller gates. */
export async function collectTestEvidence({ root, backendDir, backendSummary, frontendJson }) {
  root = path.resolve(root);
  const artifactHashes = {}, cache = new Map(), problems = IDS.map(() => []);
  const mappings = {
    backend: [], frontend: [],
    uncoveredFrontendRows: [
      { id: 'admin-probe-pending', detail: 'auth restore/current probe pending: sensitive screen/data absent' },
      { id: 'return-probe-state-variants', detail: 'focus/visible suspended/deleted actor and denied-state return variants' },
      { id: 'list-detail-empty-error-retry', detail: 'list empty; detail transient/error retry; mutation failed retry and preserved input' },
      { id: 'mutation-duplicate-submit', detail: 'pending duplicate-click dedupe' },
      { id: 'same-provider-account-switch', detail: 'actual A→B document/Provider continuity and delayed list/detail/mutation body/retry/navigation disposal' },
      { id: 'visual-browser-url', detail: '1440px visual/menus/tokens and URL paging/back/reload require browser evidence' },
    ],
    uncoveredStatePolicyRows: [
      { id: 'suspended-deleted-signin', detail: 'signin denied after suspension/deletion' },
      { id: 'reactivated-existing-refresh', detail: 'unsuspended account can reuse previously unrevoked refresh' },
      { id: 'full-write-state-transitions', detail: 'before/after all comment/like/universe/new-post mutations, including deletion; mapped tests cover only the named subsets' },
    ],
  };
  async function artifact(file) {
    const absolute = path.resolve(root, file);
    if (!cache.has(absolute)) {
      const raw = await readFile(absolute);
      artifactHashes[path.relative(root, absolute).split(path.sep).join('/')] = createHash('sha256').update(raw).digest('hex');
      cache.set(absolute, raw.toString('utf8'));
    }
    return cache.get(absolute);
  }
  let suites = [];
  try {
    const dir = path.resolve(root, backendDir);
    const files = (await readdir(dir)).filter(file => /^TEST-.*\.xml$/.test(file)).sort();
    const xmls = await Promise.all(files.map(async file => ({ path: path.relative(root, path.join(dir, file)), xml: await artifact(path.join(dir, file)) })));
    // execFile does not accept stdin as an option: use the child stream directly.
    const parsed = await new Promise((resolve, reject) => {
      const child = execFile('python3', ['-c', XML_PARSER], { maxBuffer: 32 * 1024 * 1024 }, (error, stdout, stderr) => error ? reject(new Error(`XML parsing failed: ${stderr.trim()}`)) : resolve(stdout));
      child.stdin.on('error', reject);
      child.stdin.end(JSON.stringify(xmls));
    });
    suites = JSON.parse(parsed);
    const totals = { suites: suites.length, tests: 0, failures: 0, errors: 0, skipped: 0 };
    for (const suite of suites) {
      const attrs = suite.attributes;
      for (const field of ['tests', 'failures', 'errors', 'skipped']) {
        if (!/^\d+$/.test(attrs[field] ?? '')) throw new Error(`invalid suite ${field}: ${suite.path}`);
        totals[field] += Number(attrs[field]);
      }
      if (Number(attrs.tests) !== suite.cases.length || suite.nonpassing.length || ['failures', 'errors', 'skipped'].some(field => Number(attrs[field]) !== 0)) throw new Error(`nonpassing/count mismatch: ${suite.path}`);
    }
    mappings.backendTotals = totals;
    const summary = JSON.parse(await artifact(backendSummary));
    for (const [field, expected] of Object.entries({ suites: 109, tests: 637, failures: 0, errors: 0, skipped: 0 })) {
      if (totals[field] !== expected || summary[field] !== totals[field]) throw new Error(`backend total/summary mismatch: ${field}`);
    }
    if (summary.exitCode !== 0) throw new Error('backend summary nonzero/missing exitCode');
  } catch (error) { problems.slice(0, 4).forEach(items => items.push(error.message)); }

  for (const [group, classname, name, method, row] of required) {
    const mapping = { checkId: IDS[group], row, classname, testcase: name, source: javaPath(classname), method, passed: false };
    try {
      const matchedSuites = suites.filter(suite => suite.attributes.name === classname);
      if (matchedSuites.length !== 1) throw new Error(`required suite missing/duplicate: ${classname}`);
      const suite = matchedSuites[0], cases = suite.cases.filter(item => item.name === name && item.classname === classname);
      const expectedCount = expectedSuiteCounts[classname.slice(classname.lastIndexOf('.') + 1)];
      if (expectedCount !== undefined && suite.cases.length !== expectedCount) throw new Error(`required suite case count mismatch: ${classname}`);
      if (cases.length !== 1 || cases[0].nonpassing.length) throw new Error(`required passing testcase missing: ${classname}#${name}`);
      const body = javaBody(await artifact(mapping.source), method, name);
      if (group === 0 && !/\bwaitFor(?:UserLock|AnyUserLock|TableLock)\s*\(/.test(body)) throw new Error(`physical lock observation missing: ${method}`);
      if (group === 1 && !(/\bawait(?:Boundary)?\s*\(/.test(body) && /jdbc\.update\s*\(/.test(body) && /snapshot\./.test(body) && /after\./.test(body))) throw new Error(`writer barrier/assertions missing: ${method}`);
      mapping.artifact = suite.path;
      mapping.passed = true;
    } catch (error) { mapping.missing = error.message; problems[group].push(error.message); }
    mappings.backend.push(mapping);
  }

  try {
    const result = JSON.parse(await artifact(frontendJson)), files = result.testResults;
    if (!Array.isArray(files)) throw new Error('missing Vitest testResults');
    const assertions = files.flatMap(file => file.assertionResults ?? []);
    mappings.frontendTotals = { files: files.length, tests: assertions.length, passed: assertions.filter(item => item.status === 'passed').length };
    if (files.length !== 51 || assertions.length !== 464 || new Set(files.map(file => file.name)).size !== files.length) throw new Error('frontend actual file/assertion counts mismatch');
    if (result.success !== true || result.numTotalTests !== 464 || result.numPassedTests !== 464 || result.numFailedTests !== 0 || result.numPendingTests !== 0 || result.numTodoTests !== 0 || result.numFailedTestSuites !== 0 || result.numPendingTestSuites !== 0 || !Number.isInteger(result.numTotalTestSuites) || result.numTotalTestSuites <= 0 || result.numPassedTestSuites !== result.numTotalTestSuites || result.snapshot?.failure === true) throw new Error('frontend summary is not zero-failure success');
    if (files.some(file => file.status !== 'passed') || assertions.some(item => item.status !== 'passed' || (item.failureMessages?.length ?? 0) !== 0)) throw new Error('frontend file/assertion failure/skip');
    for (const [filename, ancestor, title, row] of frontend) {
      const mapping = { checkId: IDS[4], row, source: `frontend/src/test/${filename}`, assertion: `${ancestor} ${title}`, passed: false };
      try {
        const matchedFiles = files.filter(file => file.name.replaceAll('\\', '/').endsWith(`/frontend/src/test/${filename}`));
        if (matchedFiles.length !== 1) throw new Error(`required frontend file missing/duplicate: ${filename}`);
        const matches = matchedFiles[0].assertionResults.filter(item => item.fullName === mapping.assertion && item.title === title && item.ancestorTitles.join(' ') === ancestor && item.status === 'passed');
        if (matches.length !== 1) throw new Error(`required frontend assertion missing/duplicate: ${mapping.assertion}`);
        frontendBody(await artifact(mapping.source), title);
        mapping.artifact = path.relative(root, path.resolve(root, frontendJson));
        mapping.passed = true;
      } catch (error) { mapping.missing = error.message; problems[4].push(error.message); }
      mappings.frontend.push(mapping);
    }
  } catch (error) { problems[4].push(error.message); }

  return {
    checks: IDS.map((id, index) => ({ id, passed: problems[index].length === 0, detail: {
      scope: index === 4 ? 'Only the 23 named admin assertions and 2 client expiry assertions; uncoveredFrontendRows require direct caller evidence.' : index === 3 ? 'Only mapped current-actor and suspended-write cases; uncoveredStatePolicyRows require direct caller evidence.' : 'Exact executed cases, suite/class/source mapping and complete backend zero-failure artifacts.',
      missing: problems[index], mappings: (index === 4 ? mappings.frontend : mappings.backend).filter(item => item.checkId === id),
    } })), mappings, artifactHashes,
  };
}
