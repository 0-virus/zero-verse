# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **A-v2 APPROVED / 진단6 394 PASS·2 FAIL·0 NOT RUN / 잔여2검사 STATIC-FROZEN / 최종 인수 미완료**다. `build/m9-acceptance-20261111-171732/evidence.json`에서 stale list pending-sensitive와 design reference만 실패했고 나머지 HTTP/controls/stale detail/mutation/route/helper gate는 통과했다. top-level은 NOT RUN이며 실제 전체 PASS는 아니다. 초기 정적 loop 5회는 audit-6 CLEAN으로 닫혔으며 post-loop diff는 최종 whole-branch 검토 대상이다.
- audit-2 F1~F9와 audit-3 R1~R5에 맞춰 `qa/m9-acceptance.mjs`를 보완하고, 구조화 artifact parser `qa/m9-test-evidence.mjs` 및 focused test를 연결했다. mapped-only helper PASS는 전체 acceptance PASS가 아니며 direct uncovered rows는 실제 harness evidence로 닫아야 한다.

## 진행 중

- 진단6 보완은 special 검색200/정확ID1개/렌더1행 baseline 이후 clear keyword real200의 실제 숫자 userIds에서 민감 anchor를 정해 pending/final 부재를 검사한다. 디자인은 switcher로 관리자/상세를 선택한 뒤 실제 visible label·innerText·bounds를 검증한다. 기존 route delivery/expiry/disposal·same-document/Bstable·민감 fragment 부재·row/file preservation 기대는 유지했다.
- root의 source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake 및 backend barrier XML을 fresh 실제 재실행과 대조해야 한다. final BE/FE artifact는 동결된 QA helper의 구조 parser와 direct matrix 결과를 함께 확인한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root가 진단6 두 검사 보완 소스/기록을 직접 확인하고 실패 run을 보존한 뒤 fresh run7를 단일 실행한다. QA는 DB/API를 재실행하지 않는다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과와 helper/direct uncovered rows를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- 진단6 잔여 두 검사 보완은 실제 재실행 전이다. 진단6의 미도달 항목은0이지만 실패2개가 남았고 fresh run7 및 immutable live manifest/JAR provenance·최종 whole-branch/시각 대조 전 PASS로 판정하지 않는다.
- A-v2 승인·정적 보완·순수 signature 검사 또는 runtime 환경 보정을 M9 PASS로 완화하지 않는다. helper 두 파일은 이번 Step3 보완에서 수정·재실행하지 않았다.

## 산출물

- `qa/M9-review.md` (Step3 진단6 394/2/0 및 잔여 두 검사 보완 경계 포함)
- `qa/m9-acceptance.mjs` (실제 held-row anchor와 visible design reference를 보완한 HTTP/Chrome harness)
- `qa/m9-test-evidence.mjs`, `qa/m9-test-evidence.test.mjs` (구조화 final artifact parser/focused tests; 별도 QA helper 소유)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — 진단6 보완 소스 동결. 숫자IDs metadata 포함 순수 route protocol 검사, main node --check, scoped oxlint no-undef(Node/browser env), git diff --check exit0. run7·최종 gate는 남아 있다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
