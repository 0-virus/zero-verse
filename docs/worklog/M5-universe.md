# M5 — 유니버스 관계

## [계획]

### 2026-11-07 KST — M5 착수 승인·현황 대조

- 사용자 원문 `m5 완료를 목표로 작업 착수해줘`를 M5 계획·필수 심의·구현·독립 QA·Git 마감까지 진행하는 착수 승인으로 기록한다. S3/U0와 M6 이후 기능은 범위에 포함하지 않는다.
- 시작 Git은 `feature/M5-universe`/`7b03de5`이며 `dev`와 동일하고 작업 트리는 clean이다. 원격 M5 브랜치·열린 PR·M5 커밋은 없다.
- M4는 PR #10으로 `dev`에 머지됐고, 기존 Universe 구현은 V1 `universes` 테이블, `Universe`/`UniverseStatus`, ACCEPTED 조회 predicate와 FE `SettingsUniversePage` scaffold뿐이다. 관계 CRUD·목록·차단 API와 실제 화면 동작은 없다.
- 이 새 clone에는 gitignore 대상 시각 정본 `docs/design/DESIGN-SYSTEM.md`와 `*.dc.html`이 없고 `docs/design/AGENTS.md`만 있다. PRD §6·§7 요약으로 계획·계약 검토는 가능하지만 정확한 시각 QA와 디자인 DoD는 원본 복구 전 완료로 판정하지 않는다.

### 구현 전 계약 초안 — 독립 심의 대상

**Goal:** FR-UNI-01~11의 신청·수락·거절·취소·단방향 관계 삭제·목록·차단/해제를 실제 MySQL/Spring API/React 화면에서 완성하고, 수락 후 기존 M4 `UNIVERSE` 글 접근이 열리는 통합 흐름을 검증한다.

**권고 Architecture:** V1 스키마와 `(from_user_id,to_user_id)` unique를 유지한다. mutation은 두 사용자 행을 ID 오름차순으로 pessimistic lock한 뒤 양방향 관계를 재조회해 직렬화한다. PENDING/ACCEPTED/BLOCKED 상태는 기존 한 방향 한 행 모델을 사용하고 거절·취소·삭제·해제는 hard delete한다. 알림 행 생성은 PRD §10 M8 책임으로 유지한다.

**공개 계약 보완 권고:**

1. 기존 FR-UNI endpoint 11개를 그대로 구현하고 모든 목록은 `page=0,size=20`, 최대 `100`, nickname 검색과 공통 `PageResponse`를 사용한다.
2. BlogPage가 새로고침 후에도 현재 액션을 정확히 표시하도록 보호 `GET /api/v1/universe/relationship/{targetUserId}`를 추가한다. 단일 enum 대신 `availability`, `outgoing`, `incoming`과 각 PENDING `requestId`를 반환해 양방향 PENDING·부분/양방향 ACCEPTED를 손실 없이 표현한다. 상대가 나를 차단한 사실은 `UNAVAILABLE`로 일반화한다.
3. 요청/관계 ID가 없거나 요청자가 소유하지 않은 경우를 동일 404로 숨기는 `UNI_004`, 잘못된 상태 전이나 동시 경합 패배를 409로 나타내는 `UNI_005`를 NFR-04에 추가한다. 기존 `UNI_001` 자기 자신, `UNI_002` 중복, `UNI_003` 양방향 차단 의미는 유지한다.
4. 사용자 요약은 `id,nickname,profileImageUrl,blogSlug`만 노출한다. 신청 목록은 `requestId,user,createdAt`, 관계/차단 목록은 `user,updatedAt`을 제공하며 실명·email·birthDate를 노출하지 않는다.
5. mutation은 create `201`, 나머지 성공 `200`을 사용하고 공통 `ApiResponse`를 유지한다. 인증 없음은 `AUTH_004`; 삭제/정지 사용자는 `USER_001/003`; 자기 신청·차단은 `UNI_001`; block 우선 판정은 `UNI_003`이다.

**FE 범위:**

- `/settings/universe`: PRD §7의 리스트 카드 패턴으로 받은 신청, 보낸 신청, 친구, 내가 발견한, 나를 발견한, 차단 목록을 제공한다. 수락·거절·취소·단방향 연결 끊기·차단·해제를 실제 API에 연결하고 각 목록의 loading/error/empty/page 상태를 둔다.
- `BlogPage`: 본인 블로그에는 기존 관리 버튼, 비로그인에는 signin 유도, 타인 로그인에는 관계 스냅샷에 맞는 신청/취소/수락/연결 상태 액션을 히어로 우측에 제공한다. RSS·신고·M6 댓글/좋아요·M7 feed는 추가하지 않는다.
- 현재 clone에 디자인 원본이 없으므로 PRD §6 토큰과 §7.1/§7.6 레이아웃을 적용하되, 최종 시각 승인 전에 원본을 복구해 1440px 대조한다.

### 작업 분해

#### Task 1 — 계약·동시성·backend TDD (backend)

- RED: 자기 신청/차단, 중복, 양방향 block, 동시 신청/차단/수락, 소유자 아닌 request ID, 상태 경합, hard-delete 재신청, 목록 방향/친구 계산/검색/페이징, deleted/SUSPENDED 경계를 먼저 추가한다.
- GREEN: Universe entity factory/state transition, pair lock, repository query, DTO/service/controller/OpenAPI와 `UNI_004/005`를 최소 구현한다. V1은 수정하지 않으며 migration은 필요하지 않다는 것을 실제 MySQL로 증명한다.
- 통합: 신청→수락→기존 `UNIVERSE` 글 GET 성공, 관계 삭제/차단 뒤 다시 거부를 확인한다.

#### Task 2 — frontend API·화면 TDD (frontend)

- RED: API endpoint/method/query, 401 refresh, stale account/late response, 각 mutation 후 목록 정합성, 관계 스냅샷별 BlogPage 액션, 실패 시 상태 보존을 먼저 작성한다.
- GREEN: `features/universe/**`, SettingsUniversePage와 BlogPage hero action을 구현한다. 디자인 토큰·`min-width:1440px`·square border 규칙을 유지한다.

#### Task 3 — 독립 QA·리더 인수·기록 (qa/root/pm)

- 정본/ADR/API/OpenAPI/source diff와 BE/FE 전체 테스트를 독립 검토한다.
- 실제 MySQL에서 양방향 관계·차단 경쟁과 신청→수락→UNIVERSE 글 조회를 검증한다.
- 실제 1440px 브라우저에서 설정 목록과 블로그 액션을 디자인 원본과 대조한다. 원본이 없으면 시각 DoD는 완료로 올리지 않는다.
- Critical/Important 수정 루프 종료 후 명시 파일만 commit/push하고 `feature/M5-universe`→`dev` PR·독립 리뷰·merge를 수행한다.

### Global Constraints

- 정본 우선순위는 사용자 결정/PRD §9 → `docs/design/**` → REQUIREMENTS → PRD다.
- M5 밖의 알림 저장(M8), 댓글/좋아요(M6), feed/search(M7), S3/U0를 구현하지 않는다.
- Universe 재생성 관계는 hard delete, V1은 수정하지 않는다. 같은 사용자 쌍 mutation은 결정적 잠금 순서와 양방향 재조회로 경쟁을 직렬화한다.
- 목록과 snapshot은 실명·email·birthDate를 노출하지 않는다. 차단당한 사실은 구체적으로 노출하지 않는다.
- 모든 새 동작은 RED→GREEN 증거, Swagger, 공통 응답/페이징, 401/403/404/409 경계를 포함한다.
- 저자와 최종 검토자를 분리하고, M5 HIGH 심의 승인과 디자인 원본 복구 전 제품 구현·최종 시각 PASS를 선언하지 않는다.

## [개발 기록]

### 2026-11-07 KST — backend·frontend 구현과 수정 루프

- 실제 backend 구현자: Codex backend 역할 `/root/m5_backend_impl`. `Universe` 상태 전이, `UNI_004/005`, ID 오름차순 user pair lock, lock 후 양방향 row 재조회, 신청·수락·거절·취소·단방향 관계 삭제·차단·해제, 6개 목록, FR-UNI-12 snapshot, `private,no-store`, 최소 DTO와 14개 operation OpenAPI 계약을 구현했다. V1과 `(from_user_id,to_user_id)` unique는 변경하지 않았다.
- 실제 frontend 구현자: Codex frontend 역할 `/root/m5_frontend_impl`. Universe API/types, SettingsUniversePage 여섯 카드와 mutation/canonical reload, BlogPage hero snapshot 우선순위, 409 재조회, route·owner·account stale response 방어를 구현했다. M6~M8 기능과 RSS·신고는 추가하지 않았다.
- backend RED→GREEN: 양방향 BLOCKED에서 actor의 `BLOCKED_BY_ME`가 incoming 차단에 가려지는 실패를 재현해 outgoing 우선으로 수정했다. 독립 QA의 BE-001(pair lock 후 일부 단방향 reread)과 BE-002(received/sent slug N+1)를 추가 RED→GREEN으로 닫고, 모든 mutation의 `findPair` reread와 request page batch slug map을 고정했다.
- frontend RED→GREEN: API import 부재 RED 뒤 구현을 추가했고, size clamp를 제거한 mutation test가 실패하는 것을 확인한 뒤 `max=100`을 복원했다. hero 전 상태, 정확한 PENDING requestId, 여섯 카드 action mapping, 409 canonical reload, 목록별 검색·페이지 독립성과 stale owner/account를 테스트했다.
- M4 연결 통합: 실제 MySQL에서 UNIVERSE 글을 만들고 M5 request→accept 후 service·MockMvc GET 200, deleteFollowing 및 block 후 `POST_002`/HTTP 403, notification row 0을 검증했다.
- 실행 환경은 제품 변경 없이 임시 Temurin 21.0.13, Gradle 8.10 wrapper main, Colima Docker 29.5.2와 MySQL 8.4 Testcontainers를 사용했다. 기존 POSIX `gradlew`는 tracked script의 비표준 getopt 때문에 직접 wrapper main으로 우회했다.

## [이슈·결정]

- M5는 backend+frontend, 접근제어, 공개 API 계약 변경을 포함해 `docs/governance/README.md`의 필수 소집 조건과 대형 마일스톤 조건을 충족한다. 독립 Product/Architecture/Delivery 검토 후 HIGH 이상은 사용자 승인 전 구현하지 않는다.
- 현재 결정 질문은 [M5 기획 심의](../governance/meetings/M5-20261107-universe.md)에 기록한다.
- 2026-11-07 독립 심의 결과 Product `APPROVE_WITH_CHANGES/88/HIGH`, Architecture `APPROVE_WITH_CHANGES/93/HIGH`, Delivery `APPROVE_WITH_CHANGES/90/HIGH`다. 공통 보완은 방향별 snapshot+requestId, pair lock 단일 경계, 404/409 선형화, 개인정보 최소 DTO, MySQL 경쟁/OpenAPI/전체 회귀, 디자인 원본 복구다.
- 진행자 종합 A-v2와 정확한 상태표·오류·SUSPENDED 경계·Blog hero 우선순위는 회의 §6에 기록했다. 상태는 `USER_DECISION_REQUIRED`; 승인 전 제품 구현은 보류한다.
- 2026-11-07 사용자 원문 `승인`으로 회의 §6 A-v2를 HIGH 계약으로 확정했다. REQUIREMENTS FR-UNI-12/NFR-04와 PRD에 반영한 뒤 backend/frontend TDD를 시작한다. 디자인 원본 복구는 최종 시각 PASS gate로 유지한다.
- 2026-11-07 전체 backend 첫 실행의 M4 upload 7건 실패는 M5 회귀가 아니라 macOS `/var` symlink 아래 JUnit temp path가 `LocalImageStore`의 symlink 방어에 걸린 환경 차이였다. test worker의 `java.io.tmpdir`를 비-symlink `/private/tmp/zeroverse-junit-tmp`로 고정하자 해당 upload 15/15와 전체 suite가 통과했다. 제품 코드는 변경하지 않았다.
- 2026-11-07 독립 QA 1차 finding BE-001/BE-002/QA-001/QA-002/DOC-001은 backend 수정 루프와 fresh 검증으로 모두 CLOSED했다. 유일한 OPEN/BLOCKER는 이 clone에 `docs/design/DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`, `support.js`가 없는 V-001이다.

### 2026-11-07 — Approved Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development`. Backend·frontend는 각 역할 소유 경로만 편집하고, 독립 QA와 root가 최종 검토한다.

**Spec:** `docs/REQUIREMENTS.md` §4 Universe·§6.6 FR-UNI-01~12·NFR-03/04/09, `docs/PRD.md` §4·§5.6·§7.1/7.6·§9.8·§10~12, `docs/governance/meetings/M5-20261107-universe.md` §6~7.

**Tech Stack:** Java 21, Spring Boot/JPA/MySQL/Flyway, React 19/TypeScript/Vite, 기존 `ApiResponse`·`PageResponse`·JWT AuthContext. 새 외부 의존성 없음.

#### Review Focus

1. 양방향 PENDING과 부분/양방향 ACCEPTED를 snapshot이 두 방향 `requestId`와 함께 손실 없이 반환하는가.
2. 수락/차단/취소/관계삭제 경쟁에서 모든 경로가 같은 pair lock 순서를 사용하고 최종 접근권한과 HTTP 404/409가 선형화되는가.
3. page 밖 관계, nickname 검색, soft-deleted 사용자, 큰 offset에서도 목록 total/items가 일치하는가.
4. guest BlogPage의 공개 조회가 snapshot 401과 결합되지 않고, 계정 전환·늦은 응답이 이전 관계 상태를 노출하지 않는가.
5. 관계 DTO·OpenAPI·캐시에 실명/email/birthDate/bio/상대 차단 방향이 노출되지 않는가.

### Task 1 — Backend 관계 도메인·동시성·목록 API

**Files:**

- Modify: `src/main/java/com/zeroverse/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/zeroverse/domain/universe/entity/Universe.java`
- Modify: `src/main/java/com/zeroverse/domain/universe/repository/UniverseRepository.java`
- Create: `src/main/java/com/zeroverse/domain/universe/dto/UniverseDtos.java`
- Create: `src/main/java/com/zeroverse/domain/universe/service/UniverseService.java`
- Create: `src/main/java/com/zeroverse/domain/universe/controller/UniverseController.java`
- Modify only if a batch user/blog projection needs it: `src/main/java/com/zeroverse/domain/blog/repository/BlogRepository.java`
- Update: `src/main/java/com/zeroverse/domain/universe/AGENTS.md`
- Tests: `src/test/java/com/zeroverse/domain/universe/**`, `src/test/java/com/zeroverse/security/SecurityAccessControlTest.java`, `src/test/java/com/zeroverse/config/OpenApiConfigTest.java`

**Interfaces:**

- `UniverseService.request(Long actorId, Long targetId): UniverseRequestResponse`
- `UniverseService.accept(Long actorId, Long requestId): UniverseRelationshipSnapshot`
- `UniverseService.reject(Long actorId, Long requestId): void`
- `UniverseService.cancel(Long actorId, Long requestId): void`
- `UniverseService.deleteFollowing(Long actorId, Long targetId): void`
- `UniverseService.block(Long actorId, Long targetId): void`
- `UniverseService.unblock(Long actorId, Long targetId): void`
- `UniverseService.listReceived/listSent/listFollowing/listFollowers/listFriends/listBlocks(Long actorId,String nickname,int page,int size): PageResponse<...>`
- `UniverseService.snapshot(Long actorId, Long targetId): UniverseRelationshipSnapshot`
- `UniverseRelationshipSnapshot(target,availability,outgoing,incoming)`와 `DirectionalRelation(status,requestId)`는 정본 이름·값을 그대로 사용한다.

- [ ] **Step 1.1 RED — 상태전이 단위 테스트:** 자기 신청/차단, 같은 방향 중복, 어느 방향 BLOCKED, 수신자만 accept/reject, 신청자만 cancel, 방향성 delete, unblock, hard-delete 재신청을 실패하는 테스트로 추가한다.
- [ ] **Step 1.2 RED 확인:** 해당 신규 unit suite를 실행해 구현 부재/상태전이 부재로 실패하는지 확인하고 로그를 보존한다.
- [ ] **Step 1.3 GREEN — entity/error 최소 구현:** `UNI_004/005`, `Universe.pending(...)`, `accept()` 등 승인 상태전이에 필요한 최소 메서드만 구현해 unit suite를 통과시킨다.
- [ ] **Step 1.4 RED — 실제 MySQL 경쟁:** 양방향 동시 request, accept↔cancel, accept↔block, delete↔request, mutual block을 독립 transaction으로 재현하고 deadlock/unique `COMMON_500` 없이 승인된 최종 상태를 assert한다.
- [ ] **Step 1.5 GREEN — pair lock 서비스:** 모든 mutation이 사용자 ID 오름차순으로 `findByIdAndDeletedAtIsNullForUpdate`를 호출하고 lock 후 양방향 row를 재조회하도록 단일 helper를 구현한다. block은 delete+flush 후 BLOCKED insert/update한다.
- [ ] **Step 1.6 RED — 목록/snapshot:** received/sent/following/followers/friends/blocks의 방향·친구 중복제거·nickname·stable sort·page/total·soft-delete·SUSPENDED 비노출과 snapshot 양방향 matrix/requestId/`UNAVAILABLE`/minimal JSON key를 검증한다.
- [ ] **Step 1.7 GREEN — DB projection/page:** DB query 단계에서 상태·방향·삭제 사용자·검색을 제한하고 `updatedAt DESC,id DESC`, `page=0,size=20,max=100`, 큰 offset empty-page 방어를 구현한다. Java 전체목록 페이징이나 per-row blog N+1을 만들지 않는다.
- [ ] **Step 1.8 RED/GREEN — HTTP/OpenAPI:** 201 request, 200 mutations, 401/404/409, wrong-owner 동일 `UNI_004`, snapshot `private,no-store`, bearerAuth와 실제 error codes를 Controller/OpenAPI 테스트로 고정한다.
- [ ] **Step 1.9 통합:** 신청→수락→기존 UNIVERSE 글 200, 반대 방향만 ACCEPTED는 거부, delete/block 후 POST_002, unblock 후 재신청, notification row 불변을 실제 MySQL/MockMvc로 검증한다.
- [ ] **Step 1.10 backend 전체 검증:** `./gradlew test`, `./gradlew bootJar`, `git diff --check -- src build.gradle gradle .claude/team/backend`를 실행하고 suite/test/failure/error/skip 및 JAR hash를 보고한다.
- [ ] **Step 1.11 역할 기록:** backend `STATE.md`를 현재 상태로 덮어쓰고 `WORKLOG.md`에 RED/GREEN/전체 검증과 미해결을 append한다.

### Task 2 — Frontend API·SettingsUniversePage·BlogPage 액션

**Files:**

- Create: `frontend/src/features/universe/types.ts`
- Create: `frontend/src/features/universe/universeApi.ts`
- Create: `frontend/src/features/universe/universeApi.test.ts`
- Create: `frontend/src/features/universe/UniverseListCard.tsx`
- Create: `frontend/src/features/universe/UniverseListCard.test.tsx`
- Modify: `frontend/src/pages/SettingsUniversePage.tsx`
- Modify: `frontend/src/pages/BlogPage.tsx`
- Test/modify: `frontend/src/test/router.test.tsx`, `frontend/src/test/postBehavior.test.tsx` 또는 M5 전용 `universeBehavior.test.tsx`
- Create: `frontend/src/features/universe/AGENTS.md`
- Update: `.claude/team/frontend/STATE.md`, append `.claude/team/frontend/WORKLOG.md`

**Interfaces:**

- API functions map one-to-one to FR-UNI-01~12 and use `apiClient` only.
- `listUniverse(kind,{nickname,page,size})` returns `PageResponse<UniverseRequest|UniverseRelation>` without client-side all-page aggregation.
- `getRelationshipSnapshot(targetUserId)` returns the exact A-v2 directional payload.
- Mutation success triggers canonical affected-list and snapshot reload; `UNI_005` also reloads before presenting a neutral conflict message.

- [ ] **Step 2.1 RED — API contract:** exact HTTP method/path/query, envelope unwrapping, snapshot field validation, `private/no-store`가 서버 책임임을 전제로 한 account-safe 호출을 실패 테스트로 추가한다.
- [ ] **Step 2.2 GREEN — types/API:** 정본 DTO·enum과 FR-UNI-01~12 함수만 구현한다. 응답에 예상하지 않은 private key를 UI가 사용하지 않도록 한다.
- [ ] **Step 2.3 RED — Settings 화면:** 여섯 list card의 독립 loading/error/empty/page, 받은 신청 우선 배치, accept/reject/cancel/delete/block/unblock, mutation 후 reload, duplicate click 방지를 실패 테스트로 추가한다.
- [ ] **Step 2.4 GREEN — Settings 화면:** PRD §7.6/§9-R 리스트 카드와 기존 design token/Tailwind 규칙으로 구현한다. 테이블·필터 탭·M8 알림·M6/7 기능을 만들지 않는다.
- [ ] **Step 2.5 RED — Blog hero:** guest signin 유도, owner 기존 관리, A-v2 우선순위, 양방향 pending, friend/follower, block/unavailable, mutation conflict reload, route/account 변경의 stale response 차단을 실패 테스트로 추가한다.
- [ ] **Step 2.6 GREEN — Blog hero:** 공개 blog/post load와 snapshot load를 분리해 guest 401이 공개 페이지를 실패시키지 않게 한다. 현재 auth user와 owner ID에 묶인 generation guard를 사용한다.
- [ ] **Step 2.7 frontend 검증:** M5 targeted → 전체 `npm test -- --maxWorkers=1` → `npm run lint` → `npm run build` → `git diff --check -- frontend .claude/team/frontend`를 실행하고 실제 counts/exit를 보고한다.
- [ ] **Step 2.8 역할 기록:** frontend `STATE.md`를 현재 상태로 덮어쓰고 `WORKLOG.md`에 RED/GREEN/전체 검증과 디자인 원본 미복구 경계를 append한다.

### Task 3 — 독립 QA·리더 인수·Git 마감

**Files:**

- Create: `qa/M5-review.md`
- Create or modify only if reusable HTTP automation is needed: `qa/m5-api-smoke.*`
- Update: `.claude/team/qa/STATE.md`, append `.claude/team/qa/WORKLOG.md`
- Root records: this worklog, governance/decision/risk/JOURNAL, README only if runtime instructions changed.

- [ ] **Step 3.1 독립 source/spec review:** `dev...feature/M5-universe` diff와 FR-UNI-01~12, PRD §9.8, OpenAPI, 개인정보 JSON keys, SecurityConfig를 대조한다.
- [ ] **Step 3.2 검증 산출물 감사:** backend XML과 frontend JSON을 직접 집계하고 placeholder/skip/stub, M4 회귀, notification row 생성 여부를 확인한다.
- [ ] **Step 3.3 실제 MySQL/HTTP:** 양방향 경쟁, wrong-owner 404, 상태 409, list page/search, snapshot no-store, 신청→수락→UNIVERSE 글 조회, delete/block 철회를 독립 실행한다.
- [ ] **Step 3.4 실제 브라우저 1440px:** 디자인 원본 복구 후 SettingsUniverse와 Blog hero를 원본과 대조하고 키보드/상태/새로고침/계정전환을 확인한다. 원본이 없으면 이 step은 BLOCKED이며 M5 완료·merge 금지다.
- [ ] **Step 3.5 수정 루프:** Critical/Important를 원 구현 역할에 돌려 RED→GREEN과 영향 회귀 후 재검토한다.
- [ ] **Step 3.6 최종 검증:** root가 최종 파일·명령 출력·artifact 버전을 직접 확인하고 author와 다른 whole review를 통과시킨다.
- [ ] **Step 3.7 Git:** 사용자 승인·프로젝트 규칙에 따라 명시 파일만 stage/commit/push, `dev` 대상 PR, merge 결과를 확인한 뒤 `[머지]`와 역할 상태를 갱신한다.

## [리뷰]

### 2026-11-07 KST — 독립 QA 수정 루프 최종 판정

- 독립 검토자: Codex QA 역할 `/root/m5_independent_qa`. 상세 매트릭스와 finding closure는 `qa/M5-review.md`가 정본이다.
- backend root fresh full: 70 XML suites, **457 tests**, failures/errors/skipped `0/0/0`, `BUILD SUCCESSFUL in 1m 31s`. `bootJar`도 성공했다. M5/연동 targeted는 QA fresh 7 files/61 tests, `0/0/0`이다.
- frontend root/QA fresh: 31 files, **336 tests passed**, lint exit 0, production build exit 0. Vite 500 kB chunk warning은 비차단 기존 최적화 항목이다.
- `git diff --check` 통과, 신규 skip/placeholder 없음. BE-001 pair reread, BE-002 request-list N+1, QA-001 M4 UNIVERSE 접근·철회/알림 불변, QA-002 snapshot 401, DOC-001 OpenAPI 전수 문서화는 모두 **CLOSED**다.
- 판정: **기능/API/자동검증 범위 PASS · merge-ready YES**. 그러나 시각 정본 원본이 없어 SettingsUniversePage·Blog hero의 1440px 대조를 수행할 수 없으므로 **전체 M5 final acceptance BLOCKED · Git merge-ready NO**다. V-001 복구·독립 시각 QA 전 commit/PR/merge를 진행하지 않는다.

## [머지]

### 2026-11-07 16:53:41 KST — PR #11 dev 통합 완료

- 구현 커밋: `060bbcf15d54025a8e098627be279143e4e3094a` — `feat(M5): complete universe relationships and visual acceptance`, 명시 44개 파일.
- PR: [#11](https://github.com/0-virus/zero-verse/pull/11), `feature/M5-universe` → `dev`. GitHub 응답 `merged=true`, merge SHA `30a863000a918075e9e1530ec6627e2f9167989a`, merged_at `2026-11-07T07:53:41Z`를 확인했다.
- 독립 QA가 커밋된 `dev...HEAD` 44개 파일과 핵심 소스·harness가 검증본과 동일함을 재확인하고 `merge-ready YES`를 반환했다. GitHub 별도 workflow/status는 없으며 로컬 BE457/FE338·실제 브라우저·독립 QA 증거를 적용했다.
- 리더가 fetch 후 `git diff --exit-code 060bbcf origin/dev` exit 0과 양쪽 tree `ae40db51c5de9f9d262347802962579775ee7c77` 동일을 확인했다. 로컬 `dev`를 fast-forward했다.
- **M5 완료.** 기능/API/자동검증/시각 인수와 저장소 통합이 모두 완료됐고 열린 M5 finding은 없다. M6 이후·S3/U0는 이번 작업 범위에 추가하지 않는다.
- 실행 정리: 이번 FE localhost:5173, API PID30056/18080, `zeroverse-m5-mysql84`, 임시 Colima VM을 정상 종료했다. 합성 DB·이미지·디자인 원본·스크린샷·기존 `.local-data/`는 삭제하지 않았다. 역할·머지 기록은 별도 closeout 문서 커밋으로 남긴다.

## 2026-11-07 — 시각 정본 복구 후 인수 재개

- 사용자 `파일들 옮겨뒀다` 이후 `docs/design/`에서 네 파일의 실제 존재를 확인했다. 원본은 Git 제외 상태로 보존한다.
- 대조 원본 SHA-256: `DESIGN-SYSTEM.md` = `24bfe98e45f393a87721ef371172954f16b0b9b5fe237f273c4fc811ee37227e`, `ZeroVerse Pages.dc.html` = `93b6c3c29167dee6c9b0e5b5e07f5ddf1204edc3cea90a77e9330b65deff210b`, `ZeroVerse Main Feed v2.dc.html` = `84c3c50384936a4157737540278a095bd12ef5290beb8f6e372bd06291dd437d`, `support.js` = `c60c49083997f51a592df118c0068475337afd20b8cfd8e1cd9d5eb0c7e254f6`.
- 실행 `진행`: frontend 역할은 원본 리스트 카드·요청 배지·아바타·인라인 액션·블로그 hero 대조, QA 역할은 1440px 실제 브라우저와 API 연동 검증, backend 역할은 합성 로컬 runtime을 준비한다. V-001은 실제 시각 대조가 끝난 뒤 판정한다.
- 확인한 시각 차이: 현재 설정의 두 열 카드 배치는 원본 한 열 리스트와 다르며, 요청 count 배지·요청/연결 아바타 크기·작은 버튼 스타일도 정밀 대조 대상이다. 승인 §9.8의 여섯 카드와 RSS 제외를 유지하면서 시각 표현을 수정한다.
- Git 사전 확인: 원격 `dev`는 로컬 기준 `7b03de5`와 같고 M5 원격 브랜치·PR은 아직 없다. 리더가 최종 독립 인수 후 명시 파일 commit·push·`dev` PR·merge 단계를 담당한다.

### [개발 기록·리더 검증] 원본 스타일 보정

- 구현자 `/root/m5_frontend_impl`: 설정 여섯 카드를 단일 열·20px 간격으로 보정하고, 원본에 없는 시각용 소개를 제거하면서 접근성 제목을 유지했다. 요청 count 배지, 요청 38px·연결 34px 아바타, 연결 날짜, accentSm/inert 액션, 흰색 연결 헤더, hero CTA의 neutral/4px 다크 그림자를 적용했다. 새 기능·API 변경은 없다.
- 작성자 RED 5건 → GREEN 18건을 확인했다. 리더가 최종 FE 파일을 재독하고 fresh 전체 실행: `npm test -- --run --maxWorkers=1` → **31 files / 337 tests passed**, `npm run lint` → exit 0, `npm run build` → exit 0(Vite chunk 크기 warning만), `git diff --check` → exit 0. BE 제품 소스와 기존 검증 JAR은 변경되지 않았다.
- 독립 브라우저 검증을 위해 기존 Colima와 별도 `zeroverse-m5-mysql84` 합성 DB, API localhost:18080 및 FE localhost:5173을 기동했다. 사용자 `.env.local`과 영속 upload 데이터는 수정하지 않았다. QA screenshot은 Git 제외 `build/m5-visual-qa/`에 보존한다.

### [리뷰] 최종 시각 인수·전체 M5 PASS

- 독립 검토자 `/root/m5_independent_qa`가 `qa/M5-review.md` 최종 브라우저 재검토에서 **전체 M5 final acceptance PASS / merge-ready YES**를 판정했다. BE-001/002, QA-001/002, DOC-001, V-001 및 CTA 카피 V-002 모두 CLOSED다.
- 마지막 frontend 보정은 원본 카피 `✦ 유니버스 신청`을 NONE/follower/guest에 동일 적용한 것이다. guest는 `/signin` 링크이며 보호 snapshot을 호출하지 않는 회귀를 추가했다. 리더 fresh 최종 FE: **31 files / 338 tests passed**, lint/build exit 0. 기존 chunk warning 외 오류 없음.
- QA 실제 API+Chrome run: viewport **1440×1000**, 14 screenshots, 43 checks(판정 PASS 28개와 관찰 데이터 포함), **failures=[]**, child exit 0. 여섯 카드, hero 7상태, 두 계정 새로고침·전환, guest 로그인 유도, RSS 제외, 보조 Tab 포커스를 확인했다. 리더는 원본 Blog/Settings와 제품 Settings/NONE/guest screenshot 및 evidence JSON을 직접 대조했다.
- backend는 시각 보정 중 제품 소스가 바뀌지 않았고 기존 **70 suites / 457 tests / 0 failures / 0 errors / 0 skips** 검증이 유지된다. 사용한 JAR SHA-256은 `312e5f88ba7488b046e1070e2591cd03633b475e7628161b5be68e9dc24cd948`다.
- 변경 집합 `git diff --check` 통과, 원격 dev `7b03de5` 일치, push dry-run 인증 성공. `.env.local`, 시각 원본, PRD/REQUIREMENTS 로컬 정본, 합성 credential/runtime 및 PNG/JSON은 기존 ignore 정책으로 Git에서 제외한다. 리더가 명시 M5 파일만 커밋하고 PR·머지를 실행한다.
