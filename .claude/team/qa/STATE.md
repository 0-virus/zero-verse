# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **T6 실제 운영 QA — FINAL PASS (첫 배포·범위 제한 명시)**다. T0~T5와 M10은 최종 PASS/DEV INTEGRATED로 닫혔고, root의 Aiven/S3/HTTPS preflight, rootcheck02 fresh account·browser·object flow, same-fixture comment/notification/UNIVERSE/draft flow, restart 보존, 암호화 백업/격리 복원, exact teardown을 독립 대조했다. 첫 배포라 historical-version rollback은 N/A이며 same-artifact switch/return만 실제 검증했다. 운영 DB 고의 중단 live 503 주입은 실행·주장하지 않는다.
- **2026-11-17 로그아웃 좁은 독립 QA — PASS**다. `TopBar.test.tsx`·`logoutBehavior.test.tsx` focused 7/7, 로컬 Vite + 기존 Chrome + Playwright route-mocked API smoke PASS로 성공/세션 이동/busy/guest 숨김을 확인했다. 구현자 test-only `act(...)` 보정 후 clean log에서도 2 files/7 tests PASS·경고 0을 확인했다. 운영 URL·AWS·DB·새 계정은 사용하지 않았다.
- 최종 acceptance는 `feature/M10-closeout` tested head `b5843743c5b86f84cb0bb045e60dc063aa8cccae`의 product/harness 상태에서 실행됐고, PR #16으로 `dev` merge SHA `288f29cffef0671c20a365921f087fe02d4dfcdb`에 통합됐다. tested tree `23ce12bc28c1613c04537587692b89ed9f1922e8`와 merge tree가 동일하며 diff는 비어 있다. acceptance의 실행 commit/tree는 기록 commit으로 재표기하지 않는다. M9 A-v2는 PR #15로 `dev`에 통합된 COMPLETE 상태다.
- M10 local MVP closeout은 배포·S3/AWS/U0·실사용 DB·기존 업로드·운영 ADMIN bootstrap·모바일·운영 SLA/peak memory를 포함하지 않는다.

## 진행 중

- `qa/M10-review.md`는 현재 source의 fresh BE/FE 회귀, isolated HTTP/MySQL/Chrome acceptance, 13개 1440×1000 PNG, fail-closed·provenance 규칙과 최종 독립 QA verdict를 기록한다.
- 최종 acceptance evidence는 `build/m10-evidence/m10-20261112053726-e37fa5d0/acceptance-20261112T063315284522Z-e23298bb/evidence.json`이다: `PASS`, 82 checks/82 ok/78 required/0 failures, evidence SHA-256 `52ef66fcffc0a9f14e8fbf3e84fe1435258ef2efd27d49c12f5695ba7cb0c536`.
- Runtime evidence는 동일 run의 unique runtime attempt에서 `PASS`다. MySQL `127.0.0.1:13320`/`zeroverse_m10`/`mysql:8.4`, API `18090`, FE `5180`, admin·upload·restart named checks가 모두 PASS다.
- Acceptance provenance는 source manifest `a188b0dcc6278010b9298300d15474be98450035ca846948e5a44382ea717c27`, acceptance JAR `1ade616199bd0da11daa5856dcd5d88fe8fae4042c0ee8ab66725baa01188b21`, FE dist `992d0e1dc72c094afb13754473d07dd3944c1209f3cb0b3331addd83ffb802ef`, harness `9864b13181cab9e3563f5effc1f951546f9e30bb5a8597363d4989e4c5af9ecb`, Git tree `90e7657a6ccd6e101abcfa46ffee22c375c6e9c8`이며 before/after가 일치한다.
- QA는 현재 431-file manifest를 재계산해 missing/mismatch `0/0`, PNG 13개를 재계산해 모두 고유·nonzero·1440×1000임을 확인했다. backend XML은 109 suites/637 tests/failures/errors/skips `0/0/0`으로 재집계했고, frontend는 51 files/472 tests, lint/build exit 0 기록을 대조했다.
- 최종 whole-branch scoped review는 I-1~I-4 `ADDRESSED`, 새 Critical/Important `0`, `Ready to merge: Yes`로 판정했다. Chrome에서 실행하지 않은 DraftPicker 선택·TopBar profile/search mutation·category mutation·admin select/confirm/paging은 Chrome PASS로 확대하지 않고 unit/API/source/runtime mapping으로 남겼다.
- T6 실제 운영은 회의 §15.4와 `deploy/README.md`를 읽기 전용 대조해 `qa/X-deployment-review.md`에 최종 acceptance·복원·정리·same-artifact recovery와 범위 제한을 기록했다. 7개 최종 receipt 및 rootcheck02/supplement evidence를 독립 재집계했으며 QA는 운영 S3/DB/server mutation을 직접 하지 않았다.
- 로그아웃 변경 검토는 `build/logout-20261117/focused-tests.log`, `focused-tests-clean.log`, `mock-browser.log`, `mock-browser-final.png`로 보존했다. 구현자 test-only `act(...)` 보정 후 clean log는 7/7 PASS·경고 0이며 기능/테스트 blocker가 해소됐다.

## 다음 작업

1. 현재 T6 및 로그아웃 narrow QA 검토는 종료됐다. 새로운 운영 변경·장애·release 전환이 생길 때에만 QA 재검토를 연다.
2. 이전 React `act(...)` 경고는 구현자의 test-only 보정과 clean log 확인으로 해소됐다.
3. historical rollback N/A·live DB-failure injection 미실행 범위를 유지하고 M10·T0~T5는 COMPLETE/FINAL CLOSEOUT PASS로 유지한다.

## X-deployment 후속 상태

- `qa/X-deployment-review.md`에 X-20261113-deployment §10 A-v2의 배포 검증 매트릭스와 §11 FE LOW 독립 리뷰 슬롯을 기록했다.
- 현재 단계: **T0~T5 FINAL QA CLOSEOUT PASS / T6 FINAL QA PASS (첫 배포·범위 제한)**. 사용자 T6 명시 승인(회의 §15), backend 별도 reviewer 최종 PASS, final artifact·production acceptance·teardown/shutdown, Aiven/S3/HTTPS preflight, rootcheck02 core browser/API/S3 및 same-fixture comment/notification/UNIVERSE/draft receipt, restart/restore/backup final metadata를 확인했다.
- 진행 중: `qa/X-deployment-review.md`에 T6 대상(`zeroverse.co.kr`/`54.116.133.220`, 신규 Aiven `defaultdb`, S3 `zeroverse/prod/`)과 release SHA, fresh inventory·CA `VERIFY_IDENTITY`·private S3·HTTPS cookie·ACL·restart·encrypted backup/restore·S3-compatible rollback 매트릭스, 기존 harness 재사용 경계를 기록했다. `qa/X-deployment-backend-review.md`는 별도 reviewer 소유 artifact로 유지하며 덮어쓰지 않았다.
- rootcheck02 PASS는 실행 snapshot SHA `2b716301…`에 결속된다. 이후 QA harness의 `requiredChecks`에 `api-signin-viewer`를 추가한 metadata-only 보정 후 현재 SHA `43890b5e…`가 달라졌으며, syntax/unit/self-check만 재실행하고 운영 PASS를 새 SHA로 재결속하지 않았다.
- 다음 작업: 현 배포 범위의 QA 필수 작업은 없다. 새 release/운영 장애/데이터 변경이 생기면 동일 receipt·secret-free·소유권 경계를 재검토한다.
- 차단 요인: 없음. 첫 배포 historical-version rollback은 대상 release 부재로 N/A이며 same-artifact switch/return은 PASS다. live DB failure 503 주입은 안전상 수행하지 않은 비주장 범위다. 로그아웃 기능은 focused/browser mock 및 clean focused log PASS다.
- 갱신 시각: 2026-11-17 KST — T6 최종 receipts와 함께 로그아웃 focused 2 files/7 tests PASS, local Chrome mock smoke PASS를 독립 확인했다. smoke는 `signout` 1회, busy/aria-busy, `/signin` 이동, guest logout 메뉴 0개를 기록했고 운영 자원은 사용하지 않았다.

## 남은 gate / 차단 요인

- M10 QA gate와 dev 통합은 차단되지 않았다. PR #16이 `dev`에 병합됐고 root가 마일스톤/JOURNAL 기록을 완료하는 단계까지 닫혔다.
- 비차단 Minor: archive copy 원자성, FE origin 문자열 검사, 추적되지 않은 listener에 대한 `stop-fe` 상태 문구, attempt parent symlink/동시 실행 방어, 일부 artifact 상수 기록, 958.38 kB bundle warning, 선택적 trim-positive 테스트. T6의 historical rollback/live DB-failure injection 비실행은 기록된 범위 제한이며 현재 차단 요인이 아니다.
- RISK-0010/0012/0013/0021 및 운영·배포 관련 기존 위험은 local MVP PASS로 닫지 않는다.

## 산출물

- `qa/M10-review.md`: 최종 독립 QA APPROVED/READY TO MERGE와 evidence/provenance/범위 경계.
- `qa/m10-acceptance.mjs`: fail-closed M10 black-box acceptance harness; 현재 최종 evidence hash와 결속.
- `qa/M10-task4-prep-review.md`: Task4 사전 검토와 수정 wave 이력.
- M0~M9 QA review/worklog와 역사 evidence는 보존하되 M10 현재 수치에 합산하지 않는다.
- `qa/X-deployment-review.md`: T0~T5 final matrix, final reviewed T5 41/40, prod smoke, artifact, teardown/shutdown 및 T6 경계.
- `qa/x-deployment-s3-acceptance.mjs`: fail-closed API/S3/browser harness와 UUID/allHeaders checks.
- `qa/deployment-artifacts-test.py`: 격리 Caddy/release artifact 검증 harness.

## M10 historical 갱신 시각

2026-11-12 KST — 독립 최종 M10 QA audit 82/82 PASS, backend 109/637/0, frontend 51/472·lint/build PASS, provenance/PNG/manifest 재대조, final scoped review CLEAN/READY TO MERGE 후 PR #16 `dev` merge 및 runtime stopped/preserved를 확인해 COMPLETE/DEV INTEGRATED로 갱신.

## 현재 소유권 준수

이번 T6 준비 갱신은 `qa/X-deployment-review.md`, `.claude/team/qa/STATE.md`, `.claude/team/qa/WORKLOG.md`만 대상으로 한다. 제품 source/test·frontend·backend·governance·마일스톤 worklog·runtime·DB·사용자 데이터·업로드·AWS 설정·운영 helper는 수정하지 않는다.
