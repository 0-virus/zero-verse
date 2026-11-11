# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **NOT RUN / A-v2 APPROVED / audit-3 R1~R5 FIX3 HARNESS STATIC-FROZEN / 격리 RUNTIME·live provenance 대기**다. final BE/FE artifact의 구조 parser와 focused test는 독립 정적 확인했지만 immutable manifest/JAR live identity·status/native·실제 API·DB·Chrome은 아직 QA가 실행·독립 확인하지 않았다.
- audit-2 F1~F9와 audit-3 R1~R5에 맞춰 `qa/m9-acceptance.mjs`를 보완하고, 구조화 artifact parser `qa/m9-test-evidence.mjs` 및 focused test를 연결했다. mapped-only helper PASS는 전체 acceptance PASS가 아니며 direct uncovered rows는 실제 harness evidence로 닫아야 한다.

## 진행 중

- root가 immutable source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake와 backend barrier XML을 인계한다. `6a8763d..HEAD`의 제품 소스 변경 경로가 없다는 점은 read-only로 확인했지만, live source/JAR/FE freeze hash는 root provenance로 재대조해야 한다. final BE/FE artifact는 QA helper의 구조 parser와 direct matrix 결과를 함께 확인한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root source freeze와 runtime readiness 후 `qa/m9-acceptance.mjs`를 합성 계정으로 단일 실행한다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과와 helper/direct uncovered rows를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- immutable live manifest/JAR provenance·격리 runtime/status-ready/native fixture·backend barrier XML의 QA 실제 실행 대조가 아직 남아 있다. 저장 artifact parser focused 검증만 완료됐다.
- 실제 acceptance를 실행할 때까지 모든 M9 행은 `NOT RUN`이다. A-v2 승인이나 구현자 자체 테스트를 QA PASS로 완화하지 않는다.
- root가 `build/m9-runtime.py`의 `apply_seed`에 `time`, `summaryExpectations`, KST `weekBoundaryLabels`를 보완해 exact handshake를 재인계했다. 현재 추가 handoff는 `nativeFixtureRequest`에 대한 `nativeFixtures`/`targetDetailExpectations`, backend `AdminConcurrencyMySqlTest` XML barrier 증거, source/JAR·FE freeze provenance다. 이 증거와 격리 runtime 전에는 주경계/native aggregate/실제 acceptance를 실행하지 않는다.

## 산출물

- `qa/M9-review.md` (audit-2 fix2 정적 보완·NOT RUN 경계 포함)
- `qa/m9-acceptance.mjs` (F1~F9 보완 HTTP/Chrome harness)
- `qa/m9-test-evidence.mjs`, `qa/m9-test-evidence.test.mjs` (구조화 final artifact parser/focused tests; 별도 QA helper 소유)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — A-v2 승인 계약·audit-3 R1~R5 fix3 harness 및 구조화 artifact helper handoff 기준 갱신. main `node --check`와 `git diff --check`를 통과했으며 실제 인수는 NOT RUN.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
