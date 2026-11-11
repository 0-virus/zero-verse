# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **NOT RUN / A-v2 APPROVED / I1~I9 STATICALLY CLOSED / 격리 RUNTIME·provenance 대기**다. root가 source/FE freeze를 보고했지만 immutable manifest/JAR·status/native·BE XML·실제 API·DB·Chrome·제품 테스트는 아직 QA가 실행·독립 확인하지 않았다.
- QA fix1 harness와 closure 기록은 이 시점에 동결했다. 추가 변경은 root handoff 또는 독립 scoped re-review가 생길 때만 허용한다.

## 진행 중

- root가 immutable source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake와 backend barrier XML을 인계한다. source/FE freeze hash는 root 증거로 재대조한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root source freeze와 runtime readiness 후 `qa/m9-acceptance.mjs`를 합성 계정으로 단일 실행한다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- immutable manifest/JAR·격리 runtime/status-ready/native fixture·backend barrier XML이 아직 QA에 인계되지 않았다.
- 실제 acceptance를 실행할 때까지 모든 M9 행은 `NOT RUN`이다. A-v2 승인이나 구현자 자체 테스트를 QA PASS로 완화하지 않는다.
- root가 `build/m9-runtime.py`의 `apply_seed`에 `time`, `summaryExpectations`, KST `weekBoundaryLabels`를 보완해 exact handshake를 재인계했다. 현재 추가 handoff는 `nativeFixtureRequest`에 대한 `nativeFixtures`/`targetDetailExpectations`, backend `AdminConcurrencyMySqlTest` XML barrier 증거, source/JAR·FE freeze provenance다. 이 증거와 격리 runtime 전에는 주경계/native aggregate/실제 acceptance를 실행하지 않는다.

## 산출물

- `qa/M9-review.md` (I1~I9 fix1 정적 closure 포함)
- `qa/m9-acceptance.mjs` (I1~I9 보완 harness)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — A-v2 승인 계약·I1~I9 fix1 정적 closure 및 native/status/BE barrier handoff 기준 갱신. `node --check`/정적 검증만 실행, 실제 인수는 NOT RUN.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
