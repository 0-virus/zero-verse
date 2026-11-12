# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M10 테스트·마감 QA preflight — PREPARING / NOT RUN**이다.
- M9 A-v2는 `REQUIREMENTS §6.13-A`·`ADR-0008`에 따라 독립 QA `PASS / READY TO MERGE`와 최종 scoped review `CLEAN`을 거쳐 PR #15로 `dev`에 통합된 `COMPLETE` 상태다.
- M9 통합 근거는 backend 109 suites/637 tests, failures/errors/skips `0/0/0`, `bootJar`, frontend 51 files/465 tests·lint/build, 역사 run7 `396 PASS / 0 FAIL / 0 NOT RUN`이다. run7은 FE M1 수정 전 source의 역사 evidence이며 현재 FE465와 합산하지 않는다.

## 진행 중

- `qa/M10-review.md`에 PRD §11~§12·REQUIREMENTS NFR-09 기준의 M0~M9 provenance 경계, 전체 회귀/DoD acceptance matrix, PostEditor publish/draft validation, Modal·DraftPicker·TopBar·category·admin 실제 keyboard evidence 행, 필수 E2E, fail-closed 규칙, 1440px·Swagger·skip/placeholder/secret 기준을 기록했다. M9 PR #15 merge SHA와 M10 base/current `dev` ref도 분리해 정정했다.
- `qa/m10-acceptance.mjs`를 준비했다. source/JAR/harness before·after hash stability, generated OpenAPI, synthetic HTTP smoke, sanitization, refresh/logout·Universe·comment/like·notification, no-store/envelope/page, explicit runtime-only NOT RUN rows, unique evidence directory, 13 canonical screenshots와 M10-FE-07~11 route-specific keyboard observation을 지원하며 `M10_MYSQL_PORT=13307`은 fail-closed 거부한다.
- `$brief` read-only 대조와 정본·worklog·QA review·위험 레지스터 교차 확인을 완료했다. 제품 source/test, frontend, backend, runtime, DB, Git mutation은 수행하지 않았다.
- 실제 M10 backend/frontend full run, generated OpenAPI, synthetic HTTP/MySQL/Chrome 1440px acceptance는 아직 `NOT RUN`이다.
- `node qa/m10-acceptance.mjs --self-check` 및 `node --check qa/m10-acceptance.mjs`는 exit 0이며 self-check evidence는 `SELF-CHECK PASS`다. 이는 실제 M10 acceptance PASS가 아니다.

## 다음 작업

1. root가 현재 M10 source/JAR/tree provenance와 사용자 DB 미접근 경계를 고정한다.
2. backend/frontend 역할의 현재 source full test·lint·build·bootJar 결과를 받고, source/JAR identity를 독립 대조한다.
3. 합성 계정·격리 DB·UTC runtime에서 가입→설정→글→관계→UNIVERSE→댓글/좋아요→알림→관리자, refresh/logout 및 1440px 화면을 실제로 검증한다.
4. 실패·미도달·skip·placeholder·secret·OpenAPI·시각 결과를 숨기지 않고 원 소유 역할 수정 후 scoped 재검토한다.

## 남은 gate / 차단 요인

- M10 필수 acceptance 전부가 `NOT RUN`이며, 따라서 M10 PASS·COMPLETE·release-ready를 주장할 수 없다.
- source manifest↔JAR↔HTTP/DB/Chrome evidence provenance, current full BE/FE 회귀, generated Swagger, synthetic E2E, actual 1440px screenshots가 필요하다.
- 배포/S3·AWS/U0 전환/실사용 DB·기존 업로드 적용/운영 bootstrap·모바일은 M10 범위 밖이다. RISK-0010/0012/0013/0021과 RISK-0005/0007 등 운영 위험은 별도로 OPEN/잔여로 유지한다.

## 산출물

- `qa/M10-review.md`: PREPARING/NOT RUN 사전 검토·provenance 규칙·M10 matrix·E2E·fail-closed·risk 기록.
- `qa/m10-acceptance.mjs`: final manifest/runtime에서만 실행하는 M10 black-box acceptance harness(실제 실행은 아직 NOT RUN).
- 기존 M2~M9 QA review와 M0~M9 worklog는 역사 evidence로 보존하며 M10 결과와 합산하지 않는다.

## 갱신 시각

2026-11-12 KST — M10 QA preflight 정본·matrix 정합성 보완 및 Task4 harness self-check.

## 2026-11-12 KST — M10 Task4 false-pass hardening

- `qa/m10-acceptance.mjs`를 보강했다. PUBLIC fixture로 UNIVERSE read를 통과시키던 경로를 실제 UNIVERSE 생성→수락 전 거부→수락 후 허용으로 바꾸고, comment/like도 accepted UNIVERSE post에서 실행한다.
- refresh 검사는 shared cookie jar를 사용하지 않는 격리 request context에서 rotation·old-cookie replay·logout reuse를 상태만으로 검증한다. admin current actor는 synthetic `.test` credential의 실제 signin/admin-list HTTP probe만 허용하며, admin child-row preservation/upload 5MiB-MIME/restart는 helper/env 존재만으로 통과하지 않고 required `NOT RUN`으로 남긴다.
- 13개 screenshot 행은 expected pathname·route-specific visible selector·HTTP success를 모두 요구한다. keyboard 5개는 generic control이 아니라 TopBar notification popover, admin Modal status trigger/cancel, DraftPicker open/close, GENERAL category rename/Escape, admin search Enter/query라는 실제 route-specific trigger/state outcome을 관찰하고 fixture 미존재 시 required `NOT RUN`으로 남긴다. focus-trap/initial-focus/restore 미구현은 새 hard blocker로 승격하지 않는다.
- static scan은 `src/main`, `src/test`, `frontend/src` 359개 파일을 읽고 findings를 명시 분류한다. 전 product findings 281건은 허용 분류, 미분류 0건이며 evidence에 source line/token은 쓰지 않는다.
- 재검증: `node --check` exit 0, `--self-check` exit 0 / `SELF-CHECK PASS`, scoped `git diff --check` exit 0. 실제 source/JAR/API/MySQL/Chrome acceptance 및 runtime-only rows는 여전히 `NOT RUN`이다.

## 소유권 준수

이번 수정은 `qa/M10-review.md`, `.claude/team/qa/STATE.md`, `.claude/team/qa/WORKLOG.md`만 대상으로 한다. 제품 source/test·frontend·backend·governance·마일스톤 worklog·runtime·DB·사용자 데이터·업로드는 수정하지 않고, Git 조작·commit·push·merge도 수행하지 않는다.
