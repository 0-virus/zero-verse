# M5 독립 QA 리뷰 — 유니버스 관계

- 검토자: Codex QA (`/root/m5_independent_qa`)
- 검토일: 2026-11-07 KST
- 검토 대상: `feature/M5-universe` 작업 트리의 M5 backend/frontend 산출물
- 정본: `docs/REQUIREMENTS.md` §6.6 FR-UNI-01~12·NFR-04/09, `docs/PRD.md` §4·§5.6·§7.1/§7.6·§9.8·§10~12, `docs/worklog/M5-universe.md`, `docs/governance/meetings/M5-20261107-universe.md`

## 1차 판정 (수정 전)

**조건부 통과이나 최종 인수는 차단(BLOCKED FOR FINAL ACCEPTANCE)** 이다.

- 자동 검증과 기본 기능 계약은 확인했다. frontend 전체 `31 files / 336 tests` 통과, lint/build exit 0, backend M5 대상 `28 tests` 통과, 실패·오류·skip 0이다.
- 그러나 승인된 A-v2 계약을 source 수준에서 위반하는 Important finding 2건이 남았다: 일부 mutation의 lock 후 양방향 재조회 누락, received/sent 목록의 per-row blog slug N+1.
- M5가 명시한 `신청→수락→UNIVERSE 글 조회→delete/block 철회` 실제 통합/HTTP 증거가 현재 직접 확인되지 않았다. 이는 기능 실패 단정이 아닌 인수 증거 갭이지만, PRD §11 M5 통합 테스트 gate를 닫지 못한다.
- `docs/design/`에는 `AGENTS.md`만 있고 시각 원본이 없다. 따라서 1440px 시각 대조 및 PRD §12.4의 디자인 DoD는 BLOCKED이며 M5 완료·merge-ready를 선언할 수 없다.

## 검증 범위와 정본 대조

직접 읽은 헌법·역할 지침·상태와 M5 정본은 `.claude/CONSTITUTION.md`, `AGENTS.md`, `.claude/team/qa/CLAUDE.md`, `.claude/team/qa/STATE.md`, `docs/REQUIREMENTS.md`, `docs/PRD.md`, `docs/worklog/M5-universe.md`, `docs/governance/meetings/M5-20261107-universe.md`다. 제품 코드와 backend/frontend 역할 기록은 읽기만 했고 QA 소유 파일만 갱신했다.

## 독립 실측 증거

### Frontend

- `cd frontend && npm test -- --run --maxWorkers=1` → exit 0, `Test Files 31 passed (31)`, `Tests 336 passed (336)`.
- `cd frontend && npm run lint` → exit 0 (`oxlint`).
- `cd frontend && npm run build` → exit 0. Vite가 500 kB 초과 chunk warning을 출력했으나 build 실패는 아니다.
- `frontend/src` 테스트에서 `@Disabled`, `it.skip`, `describe.skip`, `test.skip`, `xit` 검색 결과가 없었다.

### Backend

- 승인된 임시 Temurin 21 + Colima/MySQL 8.4 환경에서 직접 실행한 M5 대상 명령은 Gradle wrapper main으로 `UniverseService*`, `UniverseControllerTest`, `UniverseEntityTest`, `PostConcurrencyTest`, `OpenApiConfigTest`를 지정했다.
- 결과: `BUILD SUCCESSFUL in 14s`; XML 직접 집계 `6 files / 28 tests / 0 failures / 0 errors / 0 skipped`.
- 리더가 별도로 보존한 전체 backend 실측은 `448 tests / 0 failures / 0 errors / 0 skipped`, bootJar와 frontend 전체 검증도 성공으로 보고됐다. 이 기록은 QA가 다시 전체 runtime을 기동해 얻은 결과가 아니라 리더 실측 교차확인으로 표시한다.
- `git diff --check -- qa .claude/team/qa`는 QA 문서 작성 전·후 모두 exit 0이었다.

## 계약 검증 매트릭스 (1차 검토)

| 영역 | 결과 | 근거와 범위 |
|---|---|---|
| V1 schema/unique 유지 | PASS | Universe 구현에 migration 추가 없음; 기존 `(from_user_id,to_user_id)` 모델 유지 |
| actor/target ID 오름차순 user lock | PARTIAL | `UniverseService.lockPair()`가 `Math.min/max`와 `findByIdAndDeletedAtIsNullForUpdate`를 사용한다. 다만 모든 mutation의 row reread는 Finding BE-001 참조 |
| UNI_004/005·owner/상태 | PARTIAL | request/accept와 service unit이 wrong owner·재수락을 검증한다. reject/cancel/delete/unblock의 lock 후 최종 재조회·hard-delete race 직접 증거는 부족하다 |
| actor suspended/deleted 경계 | PASS/PARTIAL | suspended actor request는 `USER_003`, soft-deleted user lock은 `USER_001` 경로다. 각 mutation별 독립 HTTP 경계 테스트는 없다 |
| block 양방향 정리 | PASS (범위 제한) | unit/MySQL에서 PENDING·ACCEPTED 삭제, actor BLOCKED, unblock 후 재신청을 확인했다. unit에서 opposite BLOCKED 보존도 확인했다 |
| 관계 삭제 방향성 | SOURCE PASS / TEST GAP | `deleteFollowing`은 actor→target만 삭제하는 source다. 양방향 row와 실제 재조회 회귀는 별도 통합 증거가 없다 |
| 6개 목록 DB filter/search/page/sort/DTO | PARTIAL | repository가 방향·상태·deletedAt·DB nickname 검색·`updatedAt DESC,id DESC`·PageResponse를 사용한다. request 목록의 slug N+1은 Finding BE-002다 |
| FR-UNI-12 snapshot | PASS (service/MockMvc) | target 최소 DTO, outgoing/incoming 상태·PENDING requestId, 양방향 BLOCKED 우선순위와 `private,no-store` 테스트가 있다 |
| snapshot 인증 | TEST GAP | `SecurityAccessControlTest`는 `/universe/friends`를 대표 보호 경로로 검사하지만 snapshot path 자체의 무토큰 401 테스트는 없다 |
| M4 UNIVERSE 접근 회귀 | NOT EVIDENCED | `UniverseServiceMySqlTest`는 신청→수락→snapshot/listFollowing만 확인한다. `PostConcurrencyTest`는 Universe row를 직접 INSERT해 글 접근 방향만 확인하며 M5 신청/수락과 연결하지 않는다 |
| 공통 envelope/OpenAPI | PARTIAL | request/snapshot MockMvc와 OpenAPI 검사가 있다. request 외 mutation/list의 명시적 error response 문서·직접 HTTP 검사는 부족하다 |
| FE 6 cards/action mapping | PASS (자동 검증) | SettingsUniversePage의 received/sent/friends/following/followers/blocks와 mapping 테스트를 확인했다 |
| Blog hero priority/stale guard | PASS (자동 검증) | block/unavailable/incoming pending/outgoing pending/accepted/none, owner/account stale, 409 reload 테스트가 있다 |
| 개인정보 최소화 | PASS (source) | `UserSummary`/snapshot에 id·nickname·profileImageUrl·blogSlug만 있고 name/email/birthDate/bio가 없다 |
| 1440px 시각 DoD | BLOCKED | `docs/design/` 원본 부재로 실제 디자인 대조를 실행할 수 없다 |

## Findings (1차 검토)

### BLOCKER — V-001: 시각 정본 부재로 M5 완료 gate 차단

`find docs/design -maxdepth 1 -type f -print` 결과는 `docs/design/AGENTS.md` 하나뿐이다. 승인 회의와 PRD §12.4가 요구하는 `DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`, `support.js`가 없다. 따라서 SettingsUniversePage와 BlogPage를 1440px 원본과 대조할 수 없으며, 기능 테스트 통과를 근거로 M5 완료를 선언하면 안 된다. 원본 복구 후 실제 브라우저에서 두 화면, 상태·새로고침·계정전환을 재검증해야 한다.

### IMPORTANT — BE-001: 모든 mutation의 lock 후 양방향 Universe 재조회 계약 미충족

승인된 A-v2와 REQUIREMENTS §6.6/NFR-04는 모든 mutation이 actor/target user를 ID 오름차순으로 lock한 뒤 양방향 Universe row를 재조회하도록 한다. 현재 `UniverseService.java`는:

- `request`와 `block`은 `findPair`를 호출한다.
- `accept`는 lock 뒤 requested row를 재조회하고 `findPair`로 block 여부를 확인한다.
- 그러나 `reject`(`105-121`), `cancel`(`123-139`), `deleteFollowing`(`141-152`), `unblock`(`172-183`)은 lock 뒤 `findWithUsers(actor,target)` 단일 방향만 읽는다. `findPair` 또는 양방향 최종 재조회가 없다.

이 상태는 현재 단위 테스트가 고정하지 않은 승인계약 위반이며, delete/reject/cancel/unblock 경합에서 반대 방향 상태를 같은 transaction의 판단에 포함하지 않는다. 공통 pair-reread helper로 통일하고, lock 전에 보였던 row가 hard delete된 경우 `UNI_004`, 상태가 존재하지만 전이 불가하면 `UNI_005`가 되도록 각 경로의 MySQL 경쟁 테스트를 보강해야 한다.

### IMPORTANT — BE-002: received/sent 목록의 per-row blog slug N+1

승인계획 Task 1.7은 DB projection/page 단계에서 per-row blog N+1을 만들지 말 것을 명시한다. `UniverseService.listReceived/listSent`(`186-201`)는 각 `Universe`를 `summary()`로 매핑하고, `summary()`(`320-322`)가 `blogSlug()`(`329-331`)를 호출해 `findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc`를 매 행마다 실행한다. 최대 page size가 100이므로 목록 조회마다 최대 100개의 추가 blog query가 생길 수 있다.

`relationPage()`(`304-317`)에는 이미 `findByUserIdInAndDeletedAtIsNullOrdered` batch 경로가 있으므로 received/sent에도 동일한 batch projection/map을 적용하는 것이 수정 방향이다. 이 finding은 기능 결과가 틀렸다는 뜻은 아니지만 승인된 성능/구조 계약을 위반하고, 큰 목록에서 비용이 선형 증가한다.

### IMPORTANT — QA-001: M4 신청→수락→UNIVERSE 글 접근의 직접 통합/HTTP 증거 부재

M5 계획 Task 1.9와 PRD §11은 `신청→수락→기존 UNIVERSE 글 GET 200`, 이어서 관계 삭제/차단 후 `POST_002` 철회를 요구한다. 현재 `UniverseServiceMySqlTest.requestAcceptAndSnapshotContract()`는 신청·수락·snapshot·following 목록을 검증하지만 게시글을 만들거나 GET하지 않는다. `PostConcurrencyTest.universeDirectionIsViewerToOwner()`는 `universes` row를 SQL로 직접 INSERT하고 PostService를 호출하므로 M5 mutation과의 연결을 증명하지 않는다.

따라서 현재 구현이 맞을 가능성과 별개로, 실제 MySQL/HTTP에서 M5 API를 거친 positive flow와 delete/block 철회, 알림 row 미생성, 공통 envelope를 독립적으로 확인할 증거가 없다. 이 검증을 추가한 뒤에만 해당 acceptance gate를 PASS로 올려야 한다.

### MINOR — QA-002: snapshot 직접 무토큰 401 테스트 부재

보호 경로 전역 설정과 `/universe/friends` 대표 테스트 때문에 우회 가능성은 낮다. 다만 FR-UNI-12는 별도 보호 API이고 `UniverseControllerTest`는 인증 principal을 항상 붙인다. `GET /api/v1/universe/relationship/{targetUserId}` 무토큰이 `401/AUTH_004`와 공통 envelope를 반환하는 직접 Controller/Security 테스트를 추가하면 계약 증거가 닫힌다.

### MINOR — DOC-001: request 외 Universe endpoint의 명시적 OpenAPI error response 검증 부족

`UniverseController`의 `@ApiResponses`는 신청 endpoint에 집중되어 있고, 목록·accept/reject/cancel/delete/block/unblock/snapshot에는 공통 401/404/409 error annotation이 없다. `OpenApiConfigTest`도 request의 개별 status와 snapshot의 bearer/200만 검사한다. 실제 SpringDoc 결과가 동작하더라도 승인계획 Task 1.8의 “실제 error codes를 Swagger에 문서화”를 독립적으로 증명하지 못하므로, endpoint별 response/error code 문서와 검사를 보강해야 한다.

## 확인된 긍정 사항

- V1 schema를 변경하지 않았고 Universe 관계 재생성 경로는 hard delete를 사용한다.
- `lockPair()`의 user row lock 순서는 ID 오름차순이다.
- block은 양방향 PENDING/ACCEPTED를 삭제·flush한 뒤 actor→target BLOCKED를 만들며, 반대 BLOCKED를 보존하는 unit 검증이 있다.
- 목록 repository query는 상태·방향·soft-delete·DB nickname 검색을 제한하고 `updatedAt DESC,id DESC` 정렬과 `PageResponse`를 사용한다.
- snapshot은 `outgoing`/`incoming`을 분리하고 PENDING에 requestId를 유지하며, 상대 차단은 `UNAVAILABLE`, actor 차단은 `BLOCKED_BY_ME`로 우선한다. response는 `private, no-store`다.
- FE는 여섯 카드의 독립 loading/error/empty/page 상태, canonical reload, stale account/owner guard와 Blog hero 우선순위를 구현하고 자동 테스트했다.
- 테스트에서 placeholder/skip 표시는 확인되지 않았고, fresh frontend/backend targeted 결과에 failure/error/skip이 없다.

## 1차 검토 당시 잔여 gate

1. BE-001: 모든 mutation의 lock 후 양방향 reread 공통화 및 경합/삭제 race 회귀.
2. BE-002: received/sent slug batch projection으로 N+1 제거 및 목록 회귀.
3. QA-001: 실제 MySQL/HTTP 신청→수락→UNIVERSE 글 GET, delete/block 철회, notification row 불변, envelope 검증.
4. QA-002/DOC-001: snapshot 무토큰 401과 모든 Universe endpoint OpenAPI error response 검증.
5. 누락된 디자인 원본 복구 후 1440px SettingsUniverse/Blog hero 시각 대조.
6. 위 수정 후 backend full, frontend full/lint/build, `git diff --check`를 독립 재실행하고 결과를 갱신.

QA는 commit/push/PR/merge를 수행하지 않았으며 제품 코드·타 역할 테스트·governance·마일스톤 worklog는 수정하지 않았다.

## 수정 루프 독립 재검토 — 2026-11-07 KST

### 최종 판정

- **기능/API/자동검증 범위: PASS — merge-ready YES.** BE-001/BE-002/QA-001/QA-002/DOC-001은 실제 변경 diff와 fresh 테스트 결과를 다시 대조해 모두 CLOSED로 판정했다.
- **전체 M5 final acceptance: BLOCKED — merge-ready NO.** `docs/design/` 시각 원본이 계속 없어 1440px 브라우저 대조를 수행할 수 없다는 V-001만 OPEN으로 남았다. PRD §12.4와 M5 승인 회의의 명시 gate이므로 기능/API PASS를 전체 완료로 확대하지 않는다.

### Fresh 독립 검증

- Backend 전체 `GradleWrapperMain test` → `BUILD SUCCESSFUL in 1m 27s`; `build/test-results/test/TEST-*.xml` 직접 집계 `70 files / 457 tests / 0 failures / 0 errors / 0 skipped`.
- Backend 수정 범위 targeted 재실행( Universe·PostConcurrency·OpenAPI·Security ) → `7 files / 61 tests / 0 failures / 0 errors / 0 skipped`, `BUILD SUCCESSFUL in 15s`.
- Backend `bootJar` → `BUILD SUCCESSFUL in 958ms`(UP-TO-DATE).
- Frontend `npm test -- --run --maxWorkers=1` → `31 files / 336 tests passed`; `npm run lint` exit 0; `npm run build` exit 0. Vite chunk-size warning만 출력됐다.
- skip/placeholder 검색(`@Disabled`, `it.skip`, `describe.skip`, `test.skip`, `xit`) 결과 없음; `git diff --check -- qa .claude/team/qa` exit 0.
- 디자인 재확인: `find docs/design -maxdepth 1 -type f -print` 결과 `docs/design/AGENTS.md` 하나뿐이다.

### Finding closure matrix

| Finding | 최종 상태 | 독립 재검토 근거 |
|---|---|---|
| BE-001 pair lock 후 양방향 reread | **CLOSED** | `UniverseService.pairRelation()`가 `findPair()` 결과에서 방향을 선택하고 accept/reject/cancel/deleteFollowing/unblock 모두 lock 뒤 호출한다(`UniverseService.java:87-184,287-294`). 수정 unit tests와 full/targeted backend 모두 통과했다. |
| BE-002 received/sent N+1 | **CLOSED** | `listReceived/listSent`가 `requestPage→blogSlugs→findByUserIdInAndDeletedAtIsNullOrdered` batch 경로를 사용한다(`UniverseService.java:188-200,324-344`). batch 호출·per-row finder 미호출 unit tests와 full backend가 통과했다. 빈 page는 `blogSlugs` early return으로 query를 만들지 않는다. |
| QA-001 M4 UNIVERSE 접근 회귀 | **CLOSED** | MySQL 통합 테스트가 post 생성→request/accept→service GET 200 및 MockMvc GET 200, following delete/block 후 service `POST_002`와 HTTP 403, notification count 0을 확인한다(`UniverseServiceMySqlTest.java:151-194`). 관계 mutation 자체는 service layer로 호출하고 HTTP post read는 MockMvc로 검증하며, controller envelope는 별도 controller/OpenAPI 테스트가 보강한다. |
| QA-002 snapshot 무토큰 401 | **CLOSED** | `SecurityAccessControlTest` 보호 경로 목록에 `/api/v1/universe/relationship/1`을 추가하고 `401/AUTH_004` 공통 응답을 fresh full suite에서 통과했다(`SecurityAccessControlTest.java:82-98`). |
| DOC-001 Universe OpenAPI 응답 | **CLOSED** | Universe 14개 operation을 순회해 bearer, 개별 HTTP status, envelope schema를 검증하는 `OpenApiConfigTest`가 추가됐고, controller 각 operation에 status/code 설명과 `ApiResponse` schema가 존재한다(`OpenApiConfigTest.java:116-171`, `UniverseController.java:44-...`). |
| V-001 디자인 원본/1440px | **OPEN / BLOCKER** | 원본 `DESIGN-SYSTEM.md`, `.dc.html`, `support.js`가 여전히 없으므로 실제 시각 대조를 수행할 수 없다. |

### 수정 루프 이후 잔여 gate

1. 디자인 원본 복구.
2. 1440px 실제 브라우저에서 SettingsUniversePage 여섯 카드와 Blog hero 상태·새로고침·계정전환을 원본과 대조.
3. 시각 결과를 독립 QA 문서에 기록한 뒤에만 전체 M5 final acceptance를 PASS로 전환.

현재 기능/API/자동검증 범위의 QA gate는 닫혔지만, V-001 때문에 전체 M5 완료·merge 승인은 닫지 않았다. QA는 commit/push/PR/merge를 수행하지 않았다.

## 최종 독립 브라우저 재검토 — 2026-11-07 KST

### 실행 조건과 증거

- `docs/design/` 원본 복구 후 실제 정적 원본을 Playwright + 시스템 Chrome으로 열었다. 브라우저 컨텍스트는 CSS viewport `1440×1000`, `deviceScaleFactor=1`, `locale=ko-KR`이다. 원본 Blog 화면과 Settings Universe 화면이 각각 실제 `:visible`인지 확인한 뒤 캡처했다. 빈 화면·switcher placeholder는 PASS로 취급하지 않았다.
- root가 준비한 `http://localhost:18080` 실제 API와 `http://localhost:5173` Vite를 사용했다. QA harness는 API mock을 사용하지 않고, fixture runner가 메모리에서 만든 합성 A~H 계정·관계만 사용했다. runner는 기존 데이터를 삭제하지 않고 새 계정/관계를 남긴다.
- 실행: `M5_PLAYWRIGHT_MODULE=/Users/zerovirus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright M5_HEADLESS=1 node build/m5-visual-fixture-runner.mjs` → fixture setup 및 QA child exit `0`, 최종 `failures=[]`.
- QA harness: [m5-visual-qa.mjs](m5-visual-qa.mjs). 산출물은 Git 무시 `build/m5-visual-qa/`에 보존했다. 최종 evidence는 `design-blog.png`, `design-settings-universe.png`, `account-1-settings-universe.png`, `account-1-blog-hero.png`, 7개 `account-1-hero-*.png`, `account-2-settings-universe.png`, `account-2-blog-hero.png`, `guest-blog-hero.png`, `evidence.json`이다. 토큰·비밀번호·이메일은 evidence에 기록하지 않았다.

### 실제 API·브라우저 검증 결과

| 검증 | 결과 | 독립 관찰 |
|---|---|---|
| 디자인 원본 렌더 | PASS | 원본 Blog/Settings 화면 `:visible` assertion 후 1440px 캡처. 원본 Settings의 2개 카드 표현은 M5 승인 §9.8의 6개 제품 카드와 구분했다. |
| `/settings/universe` 6개 카드 | PASS | 실제 API A 계정에서 `받은/보낸/연결된 별/내가 발견한/나를 발견한/차단` heading 6개, one-column list-card, count pill, 38/34px avatar, row action을 확인했다. `innerWidth=1440`, root `min-width=1440px`, body `rgb(246,234,216)`, main `x=404,width=896`였다. |
| Settings refresh | PASS | A·B 계정 각각 실제 `page.reload()` 뒤 동일 6개 카드가 재조회·재렌더됐다. |
| Blog hero 기본·owner | PASS | A 계정 자기 블로그에서 hero `190px`, `블로그 관리`/`새 글 작성`, RSS 미노출을 확인했다. |
| Blog hero 7상태 | PASS | 실제 API snapshot fixture로 incoming PENDING=`신청 수락 ✦`, outgoing PENDING=`신청 취소`, friend=`친구 · 연결 끊기`, follower/NONE=`✦ 유니버스 신청`, `BLOCKED_BY_ME`=`차단 해제`, target block=`관계 이용 불가`를 모두 확인했다. 각 상태 hero 높이 190px·RSS 미노출이다. |
| Account switch | PASS | A→B를 새 로그인으로 전환한 뒤 A 블로그 hero가 B의 outgoing PENDING=`신청 취소`로 바뀌고, B Settings 6개 카드/refresh가 B 데이터로 재조회됐다. |
| Guest | PASS | fresh BrowserContext(쿠키 0, refresh 401)에서 공개 Blog를 열고 hero `✦ 유니버스 신청` 링크의 `/signin` href, 190px, RSS 미노출을 확인했다. |
| Keyboard Tab 보조 | PASS | guest Blog에서 Tab 28회 중 검색 input(`aria-label=검색`)과 hero signin link(`/signin`)에 실제 포커스가 도달했다. 반복 순환 관찰은 보조 증거로만 기록한다. |

### 최종 finding closure

| Finding | 최종 상태 | 근거 |
|---|---|---|
| BE-001 | **CLOSED** | 수정 후 pair lock→`findPair()` 재조회 공통화 및 backend full/targeted 0/0/0. |
| BE-002 | **CLOSED** | received/sent batch blog slug map·empty page guard 및 backend full/targeted 0/0/0. |
| QA-001 | **CLOSED** | MySQL `request→accept→UNIVERSE GET 200`, delete/block 후 service `POST_002`·HTTP 403, notification 0. |
| QA-002 | **CLOSED** | snapshot 무토큰 `401/AUTH_004` 보호 경로 검증. |
| DOC-001 | **CLOSED** | 14개 Universe operation OpenAPI status/envelope 검증. |
| V-001 | **CLOSED** | 복구된 실제 원본과 제품의 1440px 브라우저 렌더·스크린샷·상태 matrix·refresh·account switch·guest를 독립 확인. |
| V-002 (초기 CTA 카피 순서) | **CLOSED** | 첫 캡처에서 `유니버스 신청 ✦`가 원본 `✦ 유니버스 신청`과 달랐으나 FE 수정 후 현재 소스/FE 테스트/최종 browser matrix가 prefix 카피로 일치했다. |

### 최종 판정

- **기능/API/자동검증 범위: PASS — merge-ready YES.** Backend 최신 root evidence는 70 XML suites/457 tests, failures/errors/skips `0/0/0`, `bootJar` 성공이다. Frontend 최신 root evidence는 31 files/338 tests, lint/build exit `0`이다. placeholder/skip 없음과 QA `git diff --check`도 통과했다.
- **1440px 시각 gate: PASS — V-001 CLOSED.** Settings Universe 여섯 카드와 Blog hero의 실제 API 상태·refresh·계정 전환·guest·CTA 우선순위·RSS 제외를 원본과 대조했다. Mock 캡처는 참고용으로만 분리했고 최종 판정은 실제 API run에 근거한다.
- **전체 M5 final acceptance: PASS — merge-ready YES.** QA 관점의 기능/API/자동검증/브라우저 잔여 gate는 없다. 단, 커밋·푸시·PR·merge 자체는 root 권한이며 QA가 수행하지 않았다.
