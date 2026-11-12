# M10 Task4 acceptance harness 독립 검토

- 검토자: 독립 `/root/m10_task4_prep_review` (ZeroVerse QA)
- 범위: `8f27c88`의 `qa/m10-acceptance.mjs`, `qa/M10-review.md`, QA 역할 기록과 Task4 brief/report
- 판정 시점: 2026-11-12 KST
- 실제 API/MySQL/Chrome acceptance와 Git mutation은 실행하지 않았다. `node --check qa/m10-acceptance.mjs` 및 `node qa/m10-acceptance.mjs --self-check`는 exit 0으로 확인했다. self-check evidence의 runtime required 행은 NOT RUN이며 실제 M10 PASS가 아니다.

## Spec Compliance

부분 준수. 합성 계정·UNIVERSE 수락 전/후 분리·격리 refresh cookie replay/logout·route-specific 13화면 selector·route-specific keyboard flow·359 파일 static scan·secret scrub·13307 refusal 의도는 Task4 계획과 일치한다. 그러나 현재 runner는 실제 M10 DoD 전체를 닫을 수 있는 provenance/coverage와 runtime-only evidence 입력 경로가 부족하다.

## Strengths

- PUBLIC post를 UNIVERSE fixture로 재사용하던 false-pass를 제거하고, 실제 UNIVERSE post의 수락 전 deny와 수락 후 allow를 같은 post ID로 확인한다.
- refresh rotation은 owner/friend shared jar를 피한 두 request context에서 old cookie replay와 signout 후 refresh 거부를 확인하며 cookie/token 원문을 evidence에 쓰지 않는다.
- screenshot은 HTTP 2xx, 기대 pathname, 화면별 visible selector를 모두 요구해 body non-empty/redirect/hidden switcher만으로 통과하지 않는다.
- keyboard는 generic focus가 아니라 TopBar 알림 popover, admin status modal, DraftPicker, GENERAL category rename, admin search의 실제 trigger/state flow를 사용한다. 새 focus-trap 계약을 임의로 추가하지 않는다.
- static scan은 제품 세 root에서 359개 파일을 순회하고 281개 finding을 분류했으며, 현재 미분류 0건이다.

## Important Findings

1. **최종 runtime-only 필수 check에 공급 경로가 없다.** `markRuntimeEvidenceNotRun()`은 `admin-soft-delete-preservation`, `upload-5mib-mime`, `restart-persistence`를 매 실행 무조건 required `NOT RUN`으로 기록한다. helper/SQL/runtime 결과를 주입하거나 실제 검증하는 분기가 없으므로 이 파일만으로는 full acceptance `PASS`가 불가능하다. Prep 단계의 fail-closed 의도는 타당하지만, M10 PASS 전 Task4 follow-up에서 검증 결과를 실제 check로 연결해야 한다.
2. **source manifest와 FE runtime provenance가 불충분하다.** `verifyManifest()`는 manifest에 적힌 파일만 해시하고 파일 목록의 완전성·tracked product source 전체 포함·현재 commit/tree·frontend build artifact/runtime hash를 확인하지 않는다. `M10_MYSQL_PORT=13307`도 환경변수가 없으면 guard가 작동하지 않는다. full runner 전용 manifest 생성기, isolated DB port의 명시적 필수값/loopback 검증, FE build/runtime identity를 추가하지 않으면 오래된 FE 또는 사용자 DB에 연결한 결과를 현재 source 증거로 오인할 수 있다.
3. **OpenAPI/API coverage가 matrix보다 얕다.** `verifyOpenApi()`는 대표 10개 path의 존재만 확인하고 method, operationId, bearerAuth, request/response schema, error status/code를 대조하지 않는다. HTTP smoke도 envelope·no-store·pagination의 일부 필드와 status만 확인하며 invalid bearer error code, 400/403/404/409, all-list paging bound, notification event identity, comment/like canonical body를 닫지 않는다. `qa/M10-review.md` M10-API-01~03과 PRD §11 기준에 맞춘 추가 검증 또는 별도 helper evidence가 필요하다.
4. **admin/ACL acceptance가 list probe에 머문다.** `runAdminAcceptance()`은 signin 후 admin list 200과 credential field 부재만 확인한다. current ACTIVE actor, stale claim, self role/status/delete 보호, role/status mutation, deleted target, child-row/file preservation, suspended/deleted ACL은 실행하지 않는다. `M10-review.md` E2E-5/M10-ADMIN-01~02의 실제 계약을 Task4 follow-up에서 별도 SQL/API 단계로 닫아야 한다.
5. **브라우저 증거의 필수성·fixture가 불완전하다.** `M10_SCREENSHOTS=0`이면 visible check만 통과하고 13 PNG가 생성되지 않아도 full runner가 통과할 수 있다. screenshot byte/dimension/hash 검증도 없다. 또한 `registerAndSetup()`은 가입 시 DEFAULT만 받고 GENERAL category를 생성하지 않으므로 category keyboard 행은 정상 fixture에서도 required NOT RUN이 될 수 있다. E2E-1의 GENERAL 시작 category를 API로 생성하고 screenshotsEnabled=false를 full-run failure로 만들며 screenshot artifact를 검증해야 한다.
6. **refresh/notification assertion이 계약보다 약하다.** refresh는 새 access token이 실제 보호 API에서 작동하는지, cookie HttpOnly/SameSite/Path/Max-Age가 맞는지 확인하지 않는다. notification은 comment/like/request 사건의 actor/type/target/message와 수신자 연결을 확인하지 않고 임의 첫 item을 read/delete한다. 이 상태에서는 잘못된 token/cookie 또는 다른 알림으로도 PASS할 수 있다.
7. **known false-pass guard가 optional이다.** `harness-fail-closed-guards` check가 `required:false`라서 stale guard 패턴을 발견해도 evidence status를 실패로 만들지 않는다. self-check/full-run 모두 이 guard를 required failure 또는 throw로 승격해야 한다. `private-no-store`도 `no-store`만 찾고 `private`를 요구하지 않는다.

## Minor Findings

- guest/public·UNIVERSE read와 comment/like는 status 중심이며 응답의 실제 target ID/body를 확인하지 않는다. fixture ID와 response data를 함께 대조하면 오탐을 줄일 수 있다.
- `verifyOpenApi()`의 expected path 목록과 canonical screen selector는 코드에 고정돼 있으므로 route/API가 추가·변경될 때 matrix와 자동 동기화되지 않는다. final review에서 정본 목록과 수동 대조가 필요하다.
- self-check는 `SELF-CHECK PASS`라는 상태를 쓰지만 required provenance/runtime 행은 false/NOT RUN이다. 보고서·최종 집계에서 이 상태를 실제 acceptance PASS로 재사용하지 않아야 한다(현재 문서에는 경계가 명시되어 있다).

## Assessment

**Task4 prep quality: Approved with Important follow-up; M10 acceptance: NOT RUN.**

현재 하네스는 초기 false-pass 여섯 경로를 상당 부분 제거했고 자체 구문·static 검증은 통과했다. 그러나 위 Important 항목이 해결되기 전에는 full acceptance PASS, M10 DoD 완료, release-ready를 주장할 수 없다. 원 소유 QA가 runtime follow-up에서 실제 admin/업로드/restart/GENERAL fixture/OpenAPI/API/FE provenance를 연결하고, 독립 QA가 재검토해야 한다.
