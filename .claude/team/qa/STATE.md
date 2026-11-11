# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **A-v2 APPROVED / 진단5 HTTP·상세 일부 PASS·브라우저 중단 / ROUTE PROTOCOL STATIC-FROZEN / 최종 인수 미완료**다. 진단5 raw log는 이전 보완 항목을 통과했지만 paging/target404 list/mutation retry/delete/stale detail 실패 뒤 Route already handled로 종료됐다. run `build/m9-acceptance-20261111-170234`에는 최종 evidence.json이 없다. 초기 정적 loop 5회는 audit-6 CLEAN으로 닫혔으며 이번 post-loop diff는 최종 whole-branch 검토 대상이다.
- audit-2 F1~F9와 audit-3 R1~R5에 맞춰 `qa/m9-acceptance.mjs`를 보완하고, 구조화 artifact parser `qa/m9-test-evidence.mjs` 및 focused test를 연결했다. mapped-only helper PASS는 전체 acceptance PASS가 아니며 direct uncovered rows는 실제 harness evidence로 닫아야 한다.

## 진행 중

- 진단5 보완은 query별 canonical 목록 대기, header email 행 확인·retry canonical 상태, pathname/keyword/empty DTO 삭제 확인, A Authorization별 지속 expiry·중복 route별 payload/실제 delivery·bounded disposal이다. stage/route failure는 NOT RUN checkpoint를 보존한다. 기존5-method auth·정확20+1 paging·stale same-document/Bstable/민감 fragment 부재·row/file preservation 기대는 유지했다.
- root의 source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake 및 backend barrier XML을 fresh 실제 재실행과 대조해야 한다. final BE/FE artifact는 동결된 QA helper의 구조 parser와 direct matrix 결과를 함께 확인한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root가 진단5 route 보완 소스/기록을 직접 확인하고 실패 run을 보존한 뒤 fresh 격리 runtime을 단일 실행한다. QA는 DB/API를 재실행하지 않는다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과와 helper/direct uncovered rows를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- 진단5 route/브라우저 보완은 순수 검사만 통과했으며 실제 재실행 전이다. 미도달 required matrix는 NOT RUN이며 immutable live manifest/JAR provenance, backend barrier 및 HTTP/Chrome/1440px 최종 대조가 남아 있다.
- A-v2 승인·정적 보완·순수 signature 검사 또는 runtime 환경 보정을 M9 PASS로 완화하지 않는다. helper 두 파일은 이번 Step3 보완에서 수정·재실행하지 않았다.

## 산출물

- `qa/M9-review.md` (Step3 진단5 route/브라우저 보완과 부분 PASS 경계 포함)
- `qa/m9-acceptance.mjs` (중복 route·expiry·delivery·checkpoint를 보완한 HTTP/Chrome harness)
- `qa/m9-test-evidence.mjs`, `qa/m9-test-evidence.test.mjs` (구조화 final artifact parser/focused tests; 별도 QA helper 소유)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — 진단5 보완 소스 동결. 순수 route protocol 검사, main node --check, scoped oxlint no-undef(Node/browser env), git diff --check exit0. 실제 인수·최종 gate는 남아 있다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
