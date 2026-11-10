# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M8 알림 Task5 독립 QA PASS / merge-ready Yes (dev 통합 pending)**다. 사용자 A-v2 전체 승인, `feature/M8-notifications` base `b80ede0`; Run3 immutable runtime acceptance와 `m8_final_review` 독립 최종 audit가 완료됐다. Run2의 브라우저 4개 판정 실패와 stale-account timeout 원인을 반영한 하네스는 freeze 상태다.
- `qa/M8-review.md` header와 §10에 현재 판정을 반영했다. `qa/m8-acceptance.mjs`와 `qa/M8-review.md`는 추가 수정 없이 freeze하며, Critical/Important finding 없음·Ready to merge Yes를 기록했다. dev 통합은 root 후속 단계이고 QA가 통합 완료를 주장하지 않는다.
- `qa/m8-acceptance.mjs`는 합성 HTTP/Playwright harness다. 합성 ID만 seed/evidence에 남기고 credentials/token/marker는 memory/env에서만 사용한다.

## 완료한 이번 파동

- manifest aggregate path sort를 root ordinal 순서와 일치시켰다.
- PRIVATE masking을 견디는 stable notification ID transition 집합, suspended receiver COMMENT ID, 삭제 후 comment masking을 고쳤다.
- size=1/100 query cost를 독립 측정하고 양쪽 `1..14` 및 차이 `0`을 요구하도록 고쳤다.
- 실제 센터 `role=button[tabindex=0]`·최근 preview `<button>`과 API 순서/표시 시각을 쓰는 duplicate-safe locator로 바꿨다.
- USER_001 state clear, SPA 계정 전환 stale response, unavailable의 masked-read/no-navigation 검사를 실제 DOM/URL로 바꿨다.
- runtime이 제공하는 정상 서명 `M8_WRONG_TYPE_TOKEN`만 wrong-type auth에 사용한다.
- `HEX(JSON_ARRAY(...))` 행별 SQL로 모든 fingerprint scalar/NULL/audit 필드를 보존한다.

Root spot-check 후 ACTIVE unavailable row는 read를 허용하는 실제 동작에 맞춰 `200 + available=false + href=null + URL 불변`으로 수정했다. stale test는 owner 화면 준비 뒤 `route.fetch()`로 실제 size=20 upstream `200` 응답을 hold하고, 기존 `apiClient` 모듈에 `/me`·`/refresh` 401을 scoped injection해 auth-expired 경로를 통과시킨 뒤 same-Provider SPA friend 전환·센터 로딩·release 순서로 보강했다. 열린 dropdown 시각 캡처도 추가했다.

## 검증

- `node --check qa/m8-acceptance.mjs` exit 0.
- `qa/m8-acceptance.mjs` trailing whitespace clean.
- `git diff --check -- qa/m8-acceptance.mjs` exit 0.
- Run3 `build/m8-acceptance-20261110-173900/evidence.json` 및 `build/m8-evidence/acceptance-run3.log`를 read-only 대조했다. `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`, exit 0이며 source/JAR hash identity, status fixture, performance query-bound(`sizeOne=4`, `size100=4`, delta 0), HTTP/DB/Chrome와 5개 screenshot이 포함됐다.
- Run3는 실제 runtime acceptance의 PASS 증거다. `m8_final_review`는 1440px screenshot 5개, backend 102 suites/599 tests, frontend 47 files/439 tests 및 covering 검증을 대조해 Critical/Important finding 없음과 Ready to merge Yes를 판정했다. 이 수치는 독립 review 기록의 범위를 그대로 반영하며 새 전체 재실행을 주장하지 않는다.

## 다음 작업

1. root가 Ready to merge 결과를 바탕으로 M8을 dev에 통합하고 PR/merge 기록을 남긴다.
2. 통합 후 새 finding이나 회귀가 나오면 해당 소유자 수정 후 QA가 독립 재검토한다.
3. QA는 현재 M8 독립 PASS·merge-ready Yes를 유지하되 dev 통합 완료는 주장하지 않는다.

## 차단 요인

- QA 측 독립 최종 audit은 종료됐고 Ready to merge Yes다. 남은 의존은 root의 dev 통합·PR/merge 기록이며, QA가 제품 코드나 Git을 대신 수정하지 않는다.
- 사용자 DB `13307`, 기존 DB/업로드, `.env.local`은 접근하지 않았다. Git 조작·runtime 기동도 하지 않았다.

## 산출물

- `qa/M8-review.md`
- `qa/m8-acceptance.mjs`
- `.superpowers/sdd/M8-notifications/qa-final-fix.md`

## 갱신 시각

- 2026-11-10 KST — whole-branch QA Important 7개 harness finding 수정·정적 검증·root handoff 완료. 실제 acceptance는 NOT RUN.
- 2026-11-10 KST — Run2 브라우저 4개 locator/scope/zero-badge/outer-width 원인과 stale SPA account-switch timeout을 최소 수정하고 opened dropdown 캡처를 추가했다. `route.fetch()` upstream 200 hold, 기존 `apiClient` auth-expired injection, same-Provider friend signin/release 순서를 정적 검증하고 root run3에 handoff했다. 실제 acceptance는 NOT RUN.
- 2026-11-10 KST — root Run3 `build/m8-acceptance-20261110-173900`를 read-only 대조: `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`, exit 0, source/JAR provenance unchanged. 실제 runtime acceptance PASS로 기록하되 독립 최종 visual/audit 및 merge-ready는 pending으로 유지한다. 하네스와 `qa/M8-review.md`는 freeze했다.
- 2026-11-10 KST — `m8_final_review` §10 독립 최종 audit 완료: Critical/Important 없음, Ready to merge Yes. `qa/M8-review.md` 현재 header/status를 PASS·merge-ready로 정합화하고, dev 통합 pending을 유지했다. 하네스는 freeze 상태다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`와 root가 명시 허용한 `.superpowers/sdd/M8-notifications/qa-final-fix.md`만 수정했다. 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance/worklog 원본·Git은 수정하지 않았다.
