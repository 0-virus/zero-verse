# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- M8 알림은 독립 QA PASS·DEV INTEGRATED로 완료됐다. Run3는 323 PASS / 0 FAIL / 0 NOT_RUN, backend 102 suites/599 tests, frontend 47 files/439 tests 증거를 보존한다.
- M9 관리자 A-v2가 사용자 전체 승인됐다. 현재 계약은 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이다.
- `qa/M9-review.md`는 A-v2 승인 계약 기준으로 갱신됐고, `qa/m9-acceptance.mjs`는 합성 HTTP/Chrome 인수 harness로 준비됐다.
- 현재 판정은 **A-v2 APPROVED / 실제 진단2 RUN FAILED (fixture-target-image-bind) / FIX5 STATIC-FROZEN / 최종 인수 미완료**다. 진단2 `build/m9-acceptance-20261111-163859/evidence.json`은 upload PASS(201)/bind FAIL/runner 중단이고 top-level status는 NOT RUN이다. root exact 재현 `build/m9-evidence/image-bind-diagnostic-response.json`의 HTTP400/UPLOAD_004를 읽었다. 본문 image node 없이 images만 전송한 QA fixture 결함이다. audit-5 N1 ADDRESSED/CLEAN을 실제 인수 PASS로 판정하지 않는다.
- audit-2 F1~F9와 audit-3 R1~R5에 맞춰 `qa/m9-acceptance.mjs`를 보완하고, 구조화 artifact parser `qa/m9-test-evidence.mjs` 및 focused test를 연결했다. mapped-only helper PASS는 전체 acceptance PASS가 아니며 direct uncovered rows는 실제 harness evidence로 닫아야 한다.

## 진행 중

- fix5는 full PUT /posts/{id}로 기존 marker 문단과 image node·동일 images snapshot을 함께 저장한다. canonical 응답의 PUBLIC/기존 publishedAt/tag 2개/본문/이미지 URL·alt·order를 검증한다. bind 실패 진단에는 status와 제한된 error code만 남긴다. 기존 stale·child row/file preservation 검사는 유지했다.
- root의 source/JAR manifest, API `18089`, FE `5179`, synthetic MySQL `13319`, native/status-ready handshake 및 backend barrier XML을 fresh 실제 재실행과 대조해야 한다. final BE/FE artifact는 동결된 QA helper의 구조 parser와 direct matrix 결과를 함께 확인한다.
- QA는 fixture seed/status, root fingerprint, performance_schema query-count, current DB auth, soft-delete 하위 fingerprint, 실제 image bytes/`UPLOAD_003`, SPA stale와 1440px evidence를 독립 대조한다.
- 사용자 DB `13307`, `.env.local`, 기존 업로드·파일, 제품 코드·타 역할 테스트는 접근·수정하지 않는다.

## 다음 작업

1. root가 fix5 소스/기록을 직접 확인하고 실패 run을 보존한 뒤 fresh 격리 runtime 실제 인수를 단일 실행한다. QA는 DB/API를 재실행하지 않는다.
2. 실제 evidence JSON·raw log·source/JAR manifest·BE/FE 결과와 helper/direct uncovered rows를 `qa/M9-review.md` 매트릭스 각 행에 연결하고, 실패는 원 소유자 수정 후 scoped re-review한다.
3. 독립 whole-branch/시각 검토와 dev 통합 후 M9 회귀를 수행한다.

## 차단 요인

- 진단2는 fixture image bind에서 중단됐고 미도달 required matrix는 NOT RUN이다. fix5의 실제 재실행 결과, immutable live manifest/JAR provenance, status/native fixture, backend barrier 및 HTTP/Chrome/1440px 대조가 남아 있다.
- A-v2 승인·정적 보완·helper focused test 또는 runtime 환경 보정을 M9 PASS로 완화하지 않는다. helper 두 파일은 이번 fix5에서 수정·재실행하지 않았다.

## 산출물

- `qa/M9-review.md` (fix5 image fixture 보완·진단2 RUN FAILED 경계 포함)
- `qa/m9-acceptance.mjs` (본문 image/snapshot 동시 저장 fixture를 보완한 HTTP/Chrome harness)
- `qa/m9-test-evidence.mjs`, `qa/m9-test-evidence.test.mjs` (구조화 final artifact parser/focused tests; 별도 QA helper 소유)
- 기존 M8 산출물: `qa/M8-review.md`, `qa/m8-acceptance.mjs`

## 갱신 시각

2026-11-11 KST — fixround5 image fixture 정적 동결. main `node --check`와 scoped `git diff --check` exit 0. 실제 진단2는 RUN FAILED이며 fresh 인수·최종 gate는 남아 있다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 `src/**`, `frontend/**`, 타 역할 테스트, governance/worklog 원본, 사용자 DB/환경/업로드, Git은 수정하지 않았다.
