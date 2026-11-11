# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **A-v2 APPROVED / 진단4 PARTIAL PASS·상태/상세 harness FAIL / Step3 STATIC-FROZEN / 최종 인수 미완료**다. 진단4 `build/m9-acceptance-20261111-165511/evidence.json`은 global set/query/auth를 통과한 뒤 refresh/deleted write 및 상세 retry/lastAccess 기대 불일치가 발생했고 target404 후 accounts 미정의로 중단됐다. 초기 정적 loop 5회는 audit-6 CLEAN으로 닫혔으며 이번 post-loop diff는 최종 whole-branch 검토 대상이다.
- audit-2 F1~F9와 audit-3 R1~R5에 맞춰 `qa/m9-acceptance.mjs`를 보완하고, 구조화 artifact parser `qa/m9-test-evidence.mjs` 및 focused test를 연결했다. mapped-only helper PASS는 전체 acceptance PASS가 아니며 direct uncovered rows는 실제 harness evidence로 닫아야 한다.

## 진행 중

- Step3 진단4 보완은 keeperId 명시 인자, 기존 refresh no-store와 deleted own-blog POST BLOG_001 계약, canonical detail/email/null lastAccess 및 header 부분 텍스트, StrictMode initial GET 전체500→실제retry200 흐름이다. 실패 state행은 안전한 응답 메타데이터만 기록하며 admin strict privateNoStore·global set/summary/query·stale·child row/file preservation 기대는 유지했다.
- root의 source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake 및 backend barrier XML을 fresh 실제 재실행과 대조해야 한다. final BE/FE artifact는 동결된 QA helper의 구조 parser와 direct matrix 결과를 함께 확인한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root가 Step3 진단4 보완 소스/기록을 직접 확인하고 실패 run을 보존한 뒤 fresh 격리 runtime을 단일 실행한다. QA는 DB/API를 재실행하지 않는다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과와 helper/direct uncovered rows를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- 진단4의 refresh/deleted write 및 상세 browser 수정은 실제 재실행 전이다. 미도달 required matrix는 NOT RUN이며 immutable live manifest/JAR provenance, backend barrier 및 HTTP/Chrome/1440px 최종 대조가 남아 있다.
- A-v2 승인·정적 보완·순수 signature 검사 또는 runtime 환경 보정을 M9 PASS로 완화하지 않는다. helper 두 파일은 이번 Step3 보완에서 수정·재실행하지 않았다.

## 산출물

- `qa/M9-review.md` (Step3 진단4 상태·상세 보완과 부분 PASS 경계 포함)
- `qa/m9-acceptance.mjs` (fixture/auth/state/detail browser를 보완한 HTTP/Chrome harness)
- `qa/m9-test-evidence.mjs`, `qa/m9-test-evidence.test.mjs` (구조화 final artifact parser/focused tests; 별도 QA helper 소유)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — Step3 진단4 보완 정적 동결. main node --check, scoped oxlint no-undef(Node/browser env), git diff --check exit0. fresh 인수·최종 gate는 남아 있다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
