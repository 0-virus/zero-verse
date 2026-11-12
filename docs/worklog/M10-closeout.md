# M10 — 테스트 & 마감

> 2026-11-12 KST: 사용자 `착수하라` 지시에 따라 M10 완료를 목표로 계획·독립 심의·승인된 수정·전체 검증·독립 QA·`dev` 통합을 수행했다. 현재 상태는 **LOCAL MVP CLOSEOUT COMPLETE / DEV INTEGRATED**다. 배포, S3 전환/U0 해소, 실사용 DB 적용, 운영 ADMIN bootstrap은 포함하지 않는다.

## [계획]

### 목표와 완료 언어

- 현재 `feature/M10-closeout` 소스에서 REQUIREMENTS NFR-09와 PRD §11의 필수 테스트·인수 항목을 전부 명시적으로 매핑한다.
- 필수 명령은 fresh source에서 exit `0`, 자동 테스트는 failures/errors/skips `0/0/0`, 필수 실제 인수는 FAIL/NOT RUN `0/0`이어야 한다.
- PRD §12 각 DoD 증거, source manifest, backend JAR, HTTP/MySQL/Chrome 결과와 독립 QA가 같은 provenance로 연결될 때만 **M10 local MVP closeout PASS**로 판정한다.
- M0~M9의 과거 PASS와 실제 인수 수치는 회귀 범위·역사 evidence로만 사용하며 M10 fresh PASS에 합산하지 않는다.
- M10 PASS는 로컬 MVP 마감이다. 배포·운영·S3/U0·실사용 DB·모바일·운영 SLA readiness를 의미하지 않는다.

### 기준과 제약

- 시작 기준: `main=dev=origin/main=origin/dev=ce7d037cc6779be5144c15474607000280673cc4`, 작업 브랜치 `feature/M10-closeout`.
- 정본: REQUIREMENTS NFR-09, PRD §10 M10·§11·§12, `docs/design/`, M0~M9 worklog/ADR/QA evidence.
- 기존 스택·공개 API·DB schema·인증/권한·제품 정책·외부 의존성은 변경하지 않는다. 테스트 또는 인수에서 계약 결함이 발견되면 영향 작업만 보류하고 별도 governance 등급을 판정한다.
- 사용자 DB `13307`, 기존 파일·업로드·환경에는 접근·적용하지 않는다. M10 전용 합성 DB/runtime과 `.local-data` 보존 정책만 사용한다.
- 역할별 파일 소유권, 원작성자와 독립 검토자 분리, root 단독 Git/worklog/governance/JOURNAL 원칙을 유지한다.

### Task 1 — 계획·coverage matrix·독립 심의 (root/PM/QA)

- [x] `feature/M10-closeout` 분기 및 backend/frontend/PM/QA preflight 배정.
- [x] PM·backend·frontend·QA가 정본, 현재 테스트/소스, 역사 evidence와 잔여 위험을 read-only 대조했다.
- [x] root baseline: frontend `51 files / 465 tests`, lint, build PASS. build는 기존 `958.14 kB` chunk warning을 재현했다.
- [x] root baseline: backend full run에서 `637 tests` 중 `PostConcurrencyTest.concurrentReadsAndRetentionBoundary` 1건이 expected `1`, actual `2`로 실패했다. 동일 테스트 단독 fresh 재실행은 PASS하여 시각 의존 간헐 실패로 분류할 근거를 확보했다.
- [x] root build-tooling 진단: tracked `gradlew`는 실행 비트가 없고 line 168의 비표준 `getopt`가 인자에 `-l`을 주입하여 `bash ./gradlew ...`가 `Task '-l' not found`로 결정적으로 실패한다. wrapper JAR 직접 실행만 성공한다.
- [x] Product / Architecture / Delivery & Risk 독립 3인 심의와 진행자 종합. 세 명 모두 `APPROVE_WITH_CHANGES`; Product는 LOW, Architecture·Delivery는 전체 evidence 비용 때문에 MEDIUM을 제안했다. 계약 불변 실행 자체는 LOW라는 데 합의했다.
- [x] 사용자 `착수하라`는 직전 제시된 “로컬 전체 DoD closeout, 발견 결함 TDD 수정, 배포/S3/U0/실사용 DB 제외” MEDIUM 실행 범위를 승인한 명시 지시로 적용한다. 새 계약·schema·보안·dependency 변경 발견 시에만 해당 작업을 다시 보류한다.
- [x] QA matrix를 테스트 ID·실행 명령·실제 evidence 경로에 연결하고 필수/비목표를 확정한다.

### Task 2 — backend 결정성·build gate·전체 회귀 (backend)

Files: `gradlew`/wrapper metadata, `src/test/**`, 실제 결함이 증명된 경우에만 최소 `src/main/**`; backend STATE/WORKLOG. 공개 API·migration·의존성 변경 없음.

- [x] RED/GREEN: `PostConcurrencyTest`가 시스템 시각과 1초 여유에 의존하지 않도록 이미 주입된 `Clock` 기반 고정 시각으로 23:59:59/24h 경계를 결정적으로 검증한다. Clock override는 해당 test context로 격리하고 다른 audit/notification test에 누출되지 않게 한다. 제품 24시간 정책은 변경하지 않는다.
- [x] RED/GREEN: 표준 Gradle 8.10 wrapper script를 복원하고 POSIX 실행 비트를 되살려 README의 `./gradlew`/`gradlew.bat` 계약을 실제로 실행 가능하게 한다. 기존 wrapper JAR/properties/version과 최초 도입 diff를 기록하고 script/mode 외 metadata/dependency는 변경하지 않는다.
- [x] `./gradlew --version`과 실제 `--tests`·`--max-workers`·`--rerun-tasks` 전달 smoke 후 JDK21/UTC·격리 MySQL 8.4에서 단일 Gradle 실행자만 `test check bootJar --rerun-tasks --no-daemon --max-workers=1` fresh gate를 수행한다.
- [x] JUnit XML suites/tests/failures/errors/skips, 61 mapping↔61 `@Operation`, Flyway forward/ORM, security/no-store/envelope/paging/soft-delete·concurrency evidence를 재집계한다.
- [x] 최종 backend source manifest와 immutable JAR SHA-256을 고정한다. 기존 build artifact hash는 최종 증거로 재사용하지 않는다.

### Task 3 — frontend 전체 회귀·디자인/접근 경계 (frontend)

Files: 현재 frontend source/tests; 실제 재현 결함이 있을 때만 TDD 최소 수정. frontend STATE/WORKLOG.

- [x] `npm test -- --run --maxWorkers=1`, `npm run lint`, `npm run build` fresh gate와 skip/only/placeholder scan.
- [x] AuthContext·401 refresh single-flight/rotation·logout·Protected/Admin route·계정/요청 세대 격리와 M0~M9 화면/API 소비 회귀를 현재 source에서 확인한다.
- [x] RED/GREEN: NFR-09의 글쓰기/수정 폼 유효성 공백을 닫는다. 임시저장은 빈 제목/본문을 허용한다. 발행은 trim 제목 1~200자, text 또는 본문 image를 요구하되 image-only는 허용한다. 안내는 서버와 같은 `발행 제목은 1~200자여야 합니다.` / `발행할 본문 또는 이미지가 필요합니다.`를 사용하며 server `VALIDATION_001` 계약은 변경하지 않는다.
- [x] `docs/design/` 기준 데스크톱 `min-width:1440px`, radius 0, cream/ink/accent token을 정확한 13화면에 연결한다: Main, Blog, Post Detail, Write/Edit 공통 Editor, Signin/Signup 공통 Auth, Setup, Settings Basic, Settings Universe, Settings Posts, Search, Notifications, Admin List, Admin Detail. 각 화면은 실제 visible DOM·1440px screenshot 최소 1장(제품 screenshot 총 13장)과 loading/empty/error/confirm 해당 상태를 판정한다.
- [x] Modal/DraftPicker의 dialog focus·Escape·restore와 TopBar/category/admin keyboard path는 실제 브라우저로 관찰한다. 명시된 기존 동작 결함만 기존 의존성 안에서 최소 TDD 수정하며, 별도 focus-trap 제품 계약은 M10 hard blocker로 새로 만들지 않는다.
- [x] 기존 958.14 kB bundle warning은 기능 실패와 분리해 원인·영향을 판정한다. 새 dependency나 범위 확대 최적화는 근거·심의 없이 하지 않는다.

### Task 4 — 현재 artifact 실제 인수 (QA/root)

Files: QA `qa/M10-review.md` 및 M10 acceptance harness/evidence, root의 ignored `build/m10-*`. 제품 수정은 원 소유 역할만 수행한다.

- [x] 합성 계정·격리 MySQL·UTC backend·현재 FE로 가입→로그인→기본 blog/category→글 임시저장/발행→PUBLIC 조회를 검증한다.
- [x] refresh rotation→logout→재사용 실패, Universe 신청/수락→UNIVERSE 글, comment/like→notification→read/delete를 실제 HTTP/DB로 검증한다.
- [x] admin current actor/ACL/stale claim/self 보호/정지/삭제 및 M7 discovery·M8 notification·M9 admin 특수 계약을 실제 API와 Chrome으로 회귀한다.
- [x] `/v3/api-docs`·Swagger, 401/403/404·공통 응답·PageResponse·private,no-store, sanitize/upload 5MiB/권한 GET/재시작 보존을 검증한다.
- [x] source/JAR/harness/evidence hash를 실행 전후 대조하고 failures와 NOT RUN을 숨기지 않는다. 사용자 DB·기존 업로드는 fail-closed로 접근하지 않는다.
- [x] static skip/placeholder/stub/TODO 검색은 required runtime path에서 수동 분류하고, 합법적 fallback·test mock helper·SQL placeholder false positive를 차단 결함으로 오판하지 않는다.

### Task 5 — 독립 whole-branch QA·수정 루프 (독립 QA/root)

- [x] `dev...feature/M10-closeout` diff, 정본, 역할 기록, raw XML/log/JSON/PNG와 source/JAR provenance를 독립 컨텍스트에서 검토한다.
- [x] Critical/Important finding은 원 소유 역할의 RED→GREEN 최소 수정 후 scoped 재검토한다. 저자와 최종 검토자를 분리한다.
- [x] PRD §12 여섯 DoD 항목과 잔여 risk를 PASS/FAIL/NOT RUN/OUT OF SCOPE로 명시한다.

### Task 6 — closeout·PR·`dev` 통합 (root/역할/PM)

- [x] 역할 STATE/WORKLOG, QA review, JOURNAL, worklog를 최종 evidence와 동기화하고 `git diff --check`·추적/비밀 경계를 재검증한다.
- [x] 명시 파일만 원자적 커밋하고 `dev` 대상 PR을 생성한다. 등록 CI/check/review thread 상태를 확인하고 검증 head와 merge tree 동일성을 대조한다.
- [x] 승인·독립 QA gate 충족 시 `dev`에 merge하고 `[머지]` 기록, 최종 ref/tree를 확인한다.
- [x] M10 runtime은 정상 종료하되 합성 DB·업로드·evidence는 임의 삭제하지 않는다. 잔여 운영 위험과 후속 배포 범위를 분리해 인계한다.

## [개발 기록]

- 2026-11-12 12:48~12:58 KST — root가 M10 전용 branch를 생성하고 backend/frontend/PM/QA preflight를 역할별로 분할했다. 제품 구현은 아직 시작하지 않았다.
- 2026-11-12 KST — frontend fresh baseline은 51 files/465 tests, lint/build PASS. backend fresh full baseline은 637 tests 중 시간 경계 1건 실패로 전체 gate FAIL. 동일 focused test는 PASS하여 간헐성을 재현했다.
- 2026-11-12 KST — 기존 전용 Lima/Colima VM을 복구해 Testcontainers용 Docker Engine 응답을 확인했다. `/private/tmp` runtime만 보완했으며 저장소·사용자 DB·기존 업로드는 변경하지 않았다.
- 2026-11-12 KST — backend가 표준 Gradle 8.10 wrapper/mode를 복구하고 `PostConcurrencyTest`를 고정 Clock으로 결정화했다. 최초 final gate와 독립 review는 109 suites/637 tests/0 failures, JAR `1ade616...`로 승인됐다. 제품 24시간 정책, API, DB, 보안, 의존성은 변경하지 않았다.
- 2026-11-12 KST — frontend가 발행 시 trim 제목 1~200자와 text 또는 본문 image 요구를 TDD로 추가했다. 빈 draft와 image-only publish는 유지하고 thumbnail-only는 거부한다. 최종 fresh gate는 51 files/472 tests, lint/build PASS이며 958.38 kB 경고는 비차단으로 남겼다.
- 2026-11-12 15:33~15:35 KST — 격리 MySQL `13320`, API `18090`, FE `5180`에서 고유 evidence run을 수행했다. `82/82 PASS`, failures 0, 13개 고유 `1440x1000` PNG, source/JAR/FE/harness 및 실행 전후 provenance 일치를 기록했다. 사용자 DB `13307`과 기존 업로드는 접근하지 않았다.
- 2026-11-12 KST — whole-branch 독립 review가 I-1 runtime fail-closed, I-2 exact rotated cookie logout replay, I-3 Chrome/unit/API/source coverage 경계, I-4 고유 evidence/legacy pointer 거부를 Important로 제기했다. 한 차례 fix wave 뒤 scoped 재검토에서 네 항목 모두 ADDRESSED, Critical0/Important0, `Ready to merge Yes`를 받았다.
- 2026-11-12 15:39~15:44 KST — root가 최종 source에서 backend `test check bootJar --rerun-tasks --no-daemon --max-workers=1`을 정확한 격리 Docker/temp 조건으로 다시 강제 실행해 109 suites/637 tests/failures0/errors0/skips0을 확인했다. 이 재패키징 JAR `9dedd8...`는 실제 82/82 인수 JAR `1ade616...`으로 재라벨링하지 않는다.

## [이슈·결정]

- M10의 `§11 전체 통과`는 현재 source의 fresh 회귀와 실제 인수로 해석한다. 역사 PASS는 범위 추적 자료이며 현재 PASS의 대체재가 아니다. 이 계획은 공개 계약·DB·보안·정본을 바꾸지 않는 검증/결함 수정 범위다.
- `PostConcurrencyTest`는 product `PostService`가 주입된 `Clock`을 사용함에도 test가 별도 `LocalDateTime.now(UTC)`와 1초 여유를 사용한다. full run 실패와 focused PASS가 함께 관측되어 테스트 결정성 결함을 우선 가설로 둔다. 고정 Clock RED/GREEN 전에는 제품 정책 결함으로 단정하지 않는다.
- tracked `gradlew`의 비표준 `getopt`와 mode `100644`는 README의 실행 계약을 깨뜨리는 deterministic build defect다. Gradle 8.10 표준 wrapper 복원 외 version/dependency 변경은 하지 않는다.
- frontend preflight에서 NFR-09의 글쓰기/수정 폼 유효성 자동검증 공백을 확인했다. server는 draft의 빈 제목/본문은 허용하고 publish만 빈 제목·빈 text/image 본문을 `VALIDATION_001`로 거부하므로 client도 같은 publish 경계만 선검증한다. 제품 계약 확대가 아니다.
- 공통 Modal/DraftPicker는 dialog semantics는 있으나 initial focus/focus trap/Escape/restore 자동 증거가 없다. 실제 키보드 경로를 관찰하되 명시 근거 없는 새 focus-trap 계약은 hard blocker로 확대하지 않고 잔여 UX evidence로 분리한다.
- frontend bundle-size warning은 현재 build 성공을 막지 않는다. 실제 초기 로드/기능/DoD 영향과 심의 결과 없이 M10 범위를 코드 분할 리팩터링으로 확대하지 않는다.
- RISK-0010/0012/0013/0021 및 배포·운영 관련 기존 open risk를 M10 local PASS로 닫지 않는다.
- 독립 심의는 Product91 `LOW`, Architecture92 `MEDIUM(계약 불변 실행 자체 LOW)`, Delivery87 `MEDIUM`으로 모두 `APPROVE_WITH_CHANGES`였다. 전체 증거 수집 비용이 MEDIUM의 이유이며 공개 계약 변경 제안은 없다. 사용자 `착수하라`를 이미 제시된 M10 로컬 closeout 범위의 실행 승인으로 적용한다.
- 최종 source manifest/JAR hash는 모든 승인 수정 뒤에만 확정한다. 그 전 baseline/focused/과거 evidence는 diagnostic/historical로 표시한다.
- 최종 실제 인수 provenance는 HEAD `75ac4d8bcbfe777e1c1a71dca6283be2344704a3`, tree `90e7657a6ccd6e101abcfa46ffee22c375c6e9c8`, source manifest `a188b0dcc6278010b9298300d15474be98450035ca846948e5a44382ea717c27`, runtime JAR `1ade616199bd0da11daa5856dcd5d88fe8fae4042c0ee8ab66725baa01188b21`, FE dist `992d0e1dc72c094afb13754473d07dd3944c1209f3cb0b3331addd83ffb802ef`, harness `9864b13181cab9e3563f5effc1f951546f9e30bb5a8597363d4989e4c5af9ecb`다. 강제 backend 재실행이 만든 JAR `9dedd8...`는 동일 source의 별도 패키징 산출물로만 기록한다.
- 실제 Chrome hard evidence는 다섯 bounded keyboard observation과 13개 화면이다. DraftPicker 선택→edit, TopBar profile/search 전 경로, category create/delete/reorder, admin select/confirm/paging 전부를 Chrome에서 실행했다고 주장하지 않으며 해당 경계는 기존 unit/API/source 및 runtime 증거로 구분한다.
- 독립 final review의 잔여 Minor(archive 비원자성, assertion-derived 일부 artifact 값, 문자열 기반 FE origin scan, unknown listener stop 보고, symlink/동시-run hardening, bundle warning, 선택적 trim-positive test)는 이번 합성 run의 정합성·보안·제품 계약을 깨지 않는 비차단 항목으로 수용한다.

## [리뷰]

- PM·backend·frontend·QA preflight 시점에는 M10 제품 PASS/COMPLETE를 주장하지 않았고, 이후 fresh gate와 실제 인수로 별도 승격했다.
- 대형 마일스톤 독립 심의 완료. 회의록 `M10-20261112-closeout.md`의 필수 보완을 계획에 반영했으며 승인 범위 실행 준비 중이다.
- backend/frontend 구현은 각각 별도 독립 review에서 Approved(Critical0/Important0)를 받았다. 최종 backend 강제 회귀 109/637/0, frontend 51/472·lint/build PASS를 root가 재확인했다.
- 실제 인수 evidence `build/m10-evidence/m10-20261112053726-e37fa5d0/acceptance-20261112T063315284522Z-e23298bb/evidence.json`은 82/82 PASS, failures 0이다. 독립 reviewer가 431 source files, JAR/FE/harness/runtime runner, attestation 원본 4개, PNG 13개 hash와 before=after를 직접 대조했다.
- whole-branch single fix wave scoped 재검토는 I-1~I-4를 모두 ADDRESSED로 판정했고 새 Critical/Important 0, `Ready to merge Yes`다. 당시 남았던 최종 QA 역할 기록과 PR/dev 통합도 아래 `[머지]` 단계에서 완료했다.
- 독립 QA 최종 판정은 `APPROVED / READY TO MERGE`다. acceptance 후 QA/PM이 추가한 커밋 `e95d313`, `4bc25a7`은 역할 기록만 바꾸며, `git diff --exit-code 75ac4d8..HEAD -- gradlew build.gradle settings.gradle src frontend qa/m10-acceptance.mjs`는 빈 출력이다. 따라서 실행 당시 commit/tree를 사후 기록 commit으로 재라벨링하지 않고 product/harness provenance 불변을 근거로 사용한다.
- M10 runtime stop 후 포트 `13320/18090/5180`, API PID `16948`, FE PID `47634`가 모두 종료됐고 합성 uploads/evidence는 보존됐다.

## [머지]

- 2026-11-12 KST — feature 최종 head `b5843743c5b86f84cb0bb045e60dc063aa8cccae`를 origin에 push하고 [PR #16](https://github.com/0-virus/zero-verse/pull/16)을 `dev` 대상으로 생성했다. 원격 compare는 base/merge-base `ce7d037`, ahead 21, behind 0이었다.
- 등록 combined status/check와 PR-triggered workflow run은 0건, 제출 review와 review thread도 0건이었다. 저장소의 독립 local review·QA gate를 근거로 expected head SHA를 고정해 merge했다.
- GitHub merge 결과는 `merged=true`, merge SHA `288f29cffef0671c20a365921f087fe02d4dfcdb`다. 로컬 `dev`를 `origin/dev`로 fast-forward한 뒤 feature tested head와 merge commit의 tree가 모두 `23ce12bc28c1613c04537587692b89ed9f1922e8`이고 `git diff --exit-code b584374 288f29c --`가 빈 출력임을 확인했다.
- 최종 gate는 backend 109 suites/637 tests/failures0/errors0/skips0와 bootJar, frontend 51 files/472 tests·lint/build PASS, 격리 실제 인수 82/82 PASS·13개 1440x1000 PNG, 독립 QA `APPROVED / READY TO MERGE`, whole-branch Critical0/Important0이다.
- M10 runtime은 merge 전에 정상 종료했고 포트 `13320/18090/5180`과 API/FE PID가 내려갔다. 합성 DB·업로드·evidence는 보존했으며 사용자 DB `13307`·기존 업로드는 접근하지 않았다.
- M10은 **LOCAL MVP CLOSEOUT COMPLETE / DEV INTEGRATED**다. 배포, S3/U0, 실사용 DB 적용·역사 시간 보정, 운영 ADMIN bootstrap/SLA, 모바일, 브라우저 파일 선택 자동화는 완료 범위가 아니며 별도 사용자 결정 전 자동 확장하지 않는다.
