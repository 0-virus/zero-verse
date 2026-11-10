# M8 — 알림

> 2026-11-10: 사용자 **A-v2 전체 승인**. [구체 계약](../governance/meetings/M8-20261110-notifications.md#9-a-v2--구체-계약실행-승인-대상)과 Task 1~6의 구현·독립 QA·실제 인수·dev 통합 실행 중. 현재 feature/M8-notifications, M9 제외.

## [계획]

**Goal:** 승인된 M8 알림을 실제 생성·조회·관리 API와 세 화면에 연결하고 독립 QA·dev 통합까지 완료한다.
**Architecture:** 기존 MySQL에서 원행위와 알림을 동기 transaction으로 저장한다. 정렬 user 잠금/current 후보 재검증과 유한 retry, RR 조회·batch 권한 projection, 계정/요청세대별 FE store를 사용한다.
**Tech Stack:** 기존 Java21/Spring Boot/JPA/QueryDSL/MySQL8/Flyway 및 React19/TypeScript/Vite. 새 의존성 없음.
**Spec:** [회의 A-v2 §9](../governance/meetings/M8-20261110-notifications.md#9-a-v2--구체-계약실행-승인-대상), §2/§5와 사용자 승인 §10. subagent-driven-development의 구현자·독립검토자 분리와 TDD를 적용한다.

### Global Constraints

- V1~V5 및 기존 데이터·audit 불변, V6 인덱스 2개만 추가, backfill 없음. 사용자 DB·업로드·미추적4파일 보존.
- ApiResponse/PageResponse, read canonical NotificationItem, private/no-store, ACTIVE 관리·SUSPENDED 읽기전용, 불가대상 generic masking.
- POST scalar blog→sorted users→post, source/알림 원자성, 새 transaction 총3attempt, batch100·수신자 절단 없음, GET SQL≤14/size1→100 증가0.
- 1440px·radius0·크림페이퍼 토큰·센터860px/dropdown330px. M9/S3/외부알림/실시간/새패키지 제외.

### Review Focus

- Task2: legacy actor null/지원하지 않는 type-target/NEIGHBOR 문구는 원문을 보존하고 unavailable로 반환한다.
- Task2: 같은 createdAt의 21개 이상 알림은 id역순으로 안정 paging하며 hidden-before-count를 지킨다.
- Task3: 내부rollback retry와 commit 여부가 불명확한 응답유실을 구분해 중복 원행위가 생기지 않게 한다.
- Task3: 낮은ID수신자·상호발행·후보신규ACCEPTED 경쟁을 실제 MySQL barrier로 검증한다.
- Task4: 같은계정이라도 mutation 전 GET 지연응답이 새 read/delete/count를 되돌리지 못하도록 테스트한다.

### 실행 체크포인트

- [x] 사용자 A-v2 승인, feature 브랜치 생성, scope/preflight 확인.
- [x] Task1 정본/API 계약 동기화 완료(020ab1f, ignored PRD/REQUIREMENTS 동기화 별도 확인).
- [x] Task2 API/DB 자체 검증 및 독립 task review(최초24 tests+fix16 tests, 독립 Important3건 closure; 최종통합gate는별도).
- [x] Task3 생성/경쟁 자체 검증 및 독립 task review(fix1 replacement reject/cancel closure, 최종통합gate는별도).
- [x] Task4 FE 자체 검증 및 독립 task review(fix2까지 모든 blocking closure, 실제브라우저/통합gate는별도).
- [ ] Task5 전체 리뷰·실제 인수·독립 QA PASS.
- [ ] Task6 dev 통합·tree 대조·기록·runtime 정리.

### 2026-11-10 KST — 정책 선택과 심의 준비

- 사용자 `모두 권고안으로 진행하자`로 전체 이력/답글 이중수신+중복제거/실제 재행동 재알림/공개 발행상태 전환 알림/취소 이력 보존/권한 상실 masking·차단 숨김/클릭읽음·전체미읽음 공유/진입·open·복귀 갱신/과거 backfill 없음의 9 개 권고를 선택했다.
- 기준 dev `b80ede08c3e54ff3f3d6742b58c401d869574331`. M7 PR13 실제 merged 상태, final merge tree=tested tree, 보존 BE XML91 suites/538 tests·FE405·실제 인수 259 PASS 를 직접 대조했다. 이는 M7 증거이며 M8 검증은 아니다.
- 정본: FR-NOT-01~05, PRD §5.11/§7/§9-T/§10 M8/§11~12, 디자인 Pages 알림센터·Main Feed 원본. V1 notifications 만 있고 서버 도메인과 FE 연결은 미구현이다.
- root 소유: 본 마일스톤 worklog 단일 작성자, governance/JOURNAL·후속 Git/runtime. backend 는 src/Gradle·역할기록, frontend 는 frontend·역할기록, QA 는 qa·역할기록, PM 은 PRD·역할기록. 같은 파일 동시배정 금지.
- [M8 심의 A-v1](../governance/meetings/M8-20261110-notifications.md)은 API/보안/다중도메인 영향으로 HIGH 심의 대상. 사용자 정책은 재승인하지 않으며 구체 기술안·실행계획을 별도로 승인받는다.
- 사용자 root 미추적 4 파일·환경·DB·업로드를 보존한다. dev 유지, 브랜치 생성/commit/push/runtime/제품/테스트/DB 변경 없음.

### 실행 순서 초안 — 상세 계약 승인 이후

1. **계약·분기(root/PM)**: A-v2 승인 후 REQUIREMENTS/PRD/지침 동기화, dev 기반 feature/M8-notifications 분기. root 단일 Git 소유.
2. **알림 도메인·조회 API(backend)**: `domain/notification/{entity,repository,service,controller,dto}`와 인증 resolver, 필요시 V6 인덱스. paging/unread/ACL/soft-delete/idempotent/Swagger 부터 RED→GREEN.
3. **생성 연계(backend, 2 의존)**: PostService/CommentService/LikeService/UniverseService 단일 작성자, 생성전이·수신자·rollback·잠금·fanout. 기존 M5/M6 no-notification 테스트는 M8 생성/거부 기대값으로 갱신하고 M7 읽기 무부작용 유지.
4. **알림 클라이언트·화면(frontend, 승인 DTO 의존)**: `features/notifications` store/API/types, NotificationsPage/TopBar/RightPanel·app provider. backend 와 독립 병렬 가능하나 최종 검증은 실 API 사용. 디자인 1440px, stale/auth/3 곳동기화/접근불가/실패 retry 테스트.
5. **독립 QA·실제 인수(qa/root, 2~4 의존)**: 저자와 다른 컨텍스트 검토, 원소유자 수정→재검토, BE full/FE test-lint-build/bootJar, 격리 MySQL/API/Chrome acceptance, source/JAR 일치 증거. 사용자 DB 는 건드리지 않음.
6. **통합(root/PM/역할)**: 정확한 파일만 커밋, dev PR·독립리뷰·머지·tree 대조, 각 소유 기록 closeout, 임시 runtime 종료. M9 제외.

## [개발 기록]

- 미착수. 문서 준비·심의는 제품 구현으로 계산하지 않는다.

## [이슈·결정]

- 사용자 정책 9 개 확정, [회의](../governance/meetings/M8-20261110-notifications.md) §2.
- 미확정: 동기 발행 fan-out/FK 잠금 정합성, 인덱스 migration·정확 API/인증/숨김/복원 세부 계약. 심의 후 단일 A-v2 로 제시한다.

## [리뷰]

- 기획 독립 심의 진행 중. 제품 검증/독립 QA 는 미실행.

## [머지]

- 미실행.

## [계획] 2026-11-10 KST — 인수 매트릭스 (A-v2 승인 후 실행)

아래는 실행할 검증이며 아직 PASS 증거가 아니다. 테스트명은 구현 파일에서 동일 의미가 추적되도록 사용한다. BE=backend 소유 src/test, FE=frontend 소유 frontend/src/test, 실제 인수=QA 소유 도구·root 실행이며 최종 판정은 독립 QA 다.

| 기준 | 테스트/관측 | 소유·증거 |
|---|---|---|
| FR-NOT-01·정책 1 | `historyOver30DaysStillVisible`, read all/unread/read, 동일시각 id 역순, 21+/size1/100·큰 offset, 마지막페이지 문구 | BE MySQL/HTTP + FE + Chrome |
| FR-NOT-01·정책 6 | `blockedRowsExcludedBeforePageAndCounts`, unavailable 포함·actor/targetId/href/민감문구 null/제거, private→public 복구·softdeleted 영구제외 | BE MySQL/HTTP + Chrome |
| FR-NOT-02/03·정책 7 | `readPreservesFirstTimestamp`, readall 전체 페이지·신규 INSERT 경쟁·차단 숨김제외, 조회만으로 readAt 불변 | BE MySQL/HTTP + FE/Chrome |
| FR-NOT-04·정책 5 | `deleteIsOwnedSoftDelete`, 반복 delete·read/deleted 오류, 원인삭제/취소는알림 row 보존 | BE MySQL/HTTP |
| FR-NOT-05·정책 2 | `replyNotifiesPostAndParentWithReplyPriority`, 글작성자/부모/행위자 일치조합, self 제외, parent·post 접근권한/차단 | BE MySQL + 실제 댓글→센터 |
| FR-NOT-05·정책 3 | `repeatedLikeCreatesOnceRelikeCreatesAgain`, request/retry/accept/cancel/re-request, 거부·실패 0 건 | BE MySQL/HTTP |
| FR-NOT-05·정책 4/9 | `eligiblePublishTransitionsOnly`, create/draft/private/public/universe 전이표, 당시 ACCEPTED·친구·신규 follow 소급없음 | BE MySQL/HTTP |
| 생성 원자성 | `lateNotificationFailureRollsBackOriginAndAllRecipients`, COMMENT/LIKE/NEIGHBOR/POST 실패주입 후 원행위·알림 0 부분반영 | BE MySQL + 실제 API 오류/화면복구 |
| 잠금·수신자 | `publishRacesAcceptBlockDeleteAndReadAll`, 양방향발행, 낮은 ID 수신자·복수블로그, 실제 barrier/lockwait 관측·유한종료 | BE MySQL `performance_schema` + 별도 QA 감사 |
| API·보안 | 모든 endpoint bearer 없음/빈/형식/서명/type/만료/탈퇴/정지, 타인소유·숨김·없는 ID·잘못된 query, no-store 오류포함 | BE Controller + 실제 HTTP/OpenAPI |
| FE 공유상태 | `threeSurfacesShareCanonicalUnread`, open 미읽음·click 읽음·delete 실패보존·readall 후무조건 0 금지·unavailable 무이동 | FE + 1440px Chrome |
| FE 격리·갱신 | `accountSwitchDiscardsLateNotificationResponse`, authloading/guest 미호출·logoutclear·refresh 최종실패·focus/visible·동시요청 | FE + Chrome 지연응답 |
| 회귀 | M5/M6 count0 테스트는 실제성공/중복/실패 구분으로 교체; M7 GET notification fingerprint 불변, M4 publish/ACL/upload/view/image 보존 | BE full + 기존 M7/MySQL 회귀 |
| DB·성능 | V6 필요시 V5 기존 행→V6·재기동·빈 DB·ID/audit/data 불변·snapshot 복원·구버전앱호환; 목록 size1/100 querybound·EXPLAIN | BE migration/query 테스트 + root 보존출력 |

- 실행 전 환경 확인: JDK21/Node·의존성, 격리 Docker/MySQL8.4·Testcontainers socket·`performance_schema` 지원, 실제 Chrome1440px, 기존 디자인원본 2 개와 support.js. 사용자 DB 의 자격증명·데이터를 복사하지 않는다.
- 최종 실제 source manifest 와 실행 JAR hash 를 맞춰 BE full/FE test-lint-build/HTTP/Chrome 결과를 연결한다. 구조적 성능 또는 경쟁 결함은 skip/stub/조건완화로 통과시키지 않고 원소유자 수정과 재검증으로 닫는다.

## [계획] 2026-11-10 KST — A-v2 실행계획 보완

위 초안의 미확정 기술 선택은 회의 §9 A-v2 로 대체한다. 사용자 정책 승인과 구체 계약 승인은 분리하며 아래 항목은 승인 후 수행한다.

### Task 1 — 계약 정합화·실행 경계 (root/PM)

- root 가 승인된 A-v2 를 REQUIREMENTS FR-NOT 세부 계약/NFR 오류표와 governance 에 반영, PM 이 PRD §5.11/§7/§9.11/§10/§11/§13 에 구현 계약을 반영한다. 현재 PRD 는 승인된 제품 정책만 기록한다.
- root 가 dev 최신 상태·사용자 변경을 다시 확인한 뒤 `feature/M8-notifications`로 분기한다. 기존 DB 에 migration 을 적용하지 않으며 검증 환경은 합성 전용이다.

### Task 2 — 스키마 매핑·조회·관리 API (backend)

- 생성: `src/main/java/com/zeroverse/domain/notification/{entity,repository,service,controller,dto}`와 해당 AGENTS, `src/main/resources/db/migration/V6__notification_indexes.sql`.
- 인증 전용 resolver·no-store 경로 및 OpenAPI 를 구현하되 다른 API 의 인증 동작은 유지한다. 정확 NotificationPage/Item, 개별 read 의 canonical Item 응답, read-all/delete empty 응답은 A-v2 §9.1 을 따른다.
- 테스트 후보: `NotificationControllerTest`, `NotificationQueryMySqlTest`, `NotificationMutationMySqlTest`, `NotificationMigrationMySqlTest`, `NotificationQueryBoundMySqlTest`. 타입/hidden/legacy/owner/정지/탈퇴/offset/RR/전체읽음 경쟁 및 V6 2 개 index 의 업그레이드·복구·EXPLAIN 검증.
- RED→GREEN: 최초 미구현 API·mapping 검증을 실패로 확인한 뒤 구현. 목록 SQL 은 인증 포함 14 회 이하, size1/100 증가 0, source LONGTEXT 미적재. 조건미달은 숨기지 않고 수정한다.

### Task 3 — 이벤트 생성·원자성·경쟁 (backend, Task2 의존)

- 단일 작성자가 `PostService.create/update`, `CommentService.create`, `LikeService.like`, `UniverseService.request/accept` 및 전용 알림 생성기/transaction executor 를 담당한다. `PostInteractionLockService`, user/universe projection 등 공통 helper 수정도 같은 backend 소유로 조정한다.
- 순서는 scalar blog→전체 user ID 정렬→post→source/notification. 후보 변동 시 전체 rollback, 새 transaction 총 3attempt, 확정 rollback 된 1213/1205/후보충돌만재시도, 소진 COMMON_500. 첫시도/후보 재검증/후반 INSERT 실패/commit 후재시도금지 테스트를 먼저 작성한다.
- 테스트 후보: `NotificationGenerationMySqlTest`, `NotificationFanoutConcurrencyMySqlTest`, `NotificationRollbackMySqlTest`, `NotificationPublishTransitionTest`. 수신자 0/1/100/101/1001, batch100 경계, 낮은 ID/상호발행/accept·block·delete·readall 경쟁의 실제 barrier 와 data_lock_waits 관측.
- `UniverseServiceMySqlTest`, `CommentBehaviorTest`, `LikeControllerTest`의 기존 0 건단정을 단순삭제하지 않고 정확한성공/반복/실패별알림기대값으로전환. `M7DiscoverySnapshotCoverageMySqlTest`의읽기무부작용은유지.
- 기존 `PostService`에넣은 M4/M7 권한·JSON/HTML/content_text·태그·imagebinding·조회수·publishedAt/audit 회귀를유지. 성공알림생성의도와다른 M4 데이터변경금지.

### Task 4 — 공통 상태·센터·최근 알림 (frontend, 승인 DTO 의존)

- 생성: `frontend/src/features/notifications/{types,notificationApi,NotificationProvider}.ts(x)` 및 경로별 AGENTS. 수정: NotificationsPage, TopBar, RightPanel, AppShell/provider 연결, SettingsUniversePage 의 성공 block/unblock invalidation 경계.
- 같은 viewer 의 preview 와센터 page 는별도 cache, 공통 unread 는요청/계정/mutation 세대순서로만갱신. read 응답의안전한 href 로이동, 실패 시안내·입력/목록보존. 정지 계정은 read-only 안내.
- 테스트 후보: `notificationApi.test.ts`, `notificationStore.test.tsx`, `notificationsBehavior.test.tsx`, `notificationAuthRace.test.tsx`, 기존 TopBar/RightPanel/router 회귀. case 별 RED→GREEN, lint/build.

### Task 5 — 독립 리뷰·실제 인수 (QA/root)

- QA 소유 산출물: `qa/M8-review.md`, 필요 시 `qa/m8-acceptance.mjs`. 제품 src/frontend 수정은각원 작성자만. 독립 컨텍스트로계약과 diff/실측을대조한다.
- root 단일실행조정으로 BE `test bootJar`, frontend `npm test -- --run --maxWorkers=1`/lint/build, immutable JAR·source manifest, 격리 MySQL/API/FE/Chrome1440px 실제 인수. 테스트 fixture 에 40 일초과·21+목록·모든알림 type·권한 상실·복구·양방향 차단·탈퇴·정지·계정 전환을포함한다.
- 제품/도구 finding 은원 작성자수정→독립재리뷰. 신규 source 변경이없으면동일검증을불필요하게반복하지않는다. 성능은 querycount·실행 시간·lockwait·recipient 수로보고하며미측정 SLA 통과를주장하지않는다.

### Task 6 — 완료 기록·dev 통합 (root/PM/역할)

- 각역할 STATE/WORKLOG, root worklog/JOURNAL/governance, PM PRD 를실측기반으로동기화. 명시파일만원자커밋, dev 대상 PR, 독립 review finding closure 후 merge/tree 일치확인.
- M8 임시 runtime 만종료하고합성데이터/증거와사용자데이터보존. M9 로확장하지않음. Git 통합까지 A-v2 구현승인범위에포함한다.

## [리뷰] 2026-11-10 KST — 독립 기획 심의 종합

- Product90/Architecture89/Delivery92, 모두 APPROVE_WITH_CHANGES/HIGH. 상세·반대논거·실패 시나리오는 회의§6 에보존했다.
- root 는소스에서기존 Post owner 선잠금과 notification FK 를직접확인하고 A-v2 에정렬 잠금·현재 후보 재검증·총 3attempt·원자 rollback 을명시했다. 실제 MySQL 증명은 Task3/5 gate 이며아직미실행이다.
- PM 이 9 개정책을 PRD§9.11 에반영했다. root 가관련본문·STATE·WORKLOG 와정책개수를재독했다. 제품 정책과기술미승인경계를유지한다.
- 상태: 구체 A-v2/Task1~6 사용자승인대기. 제품코드/테스트/DB/runtime/Git 변경없음. 문서만검사한다.

## [개발 기록] 2026-11-10 KST — A-v2 승인·실행 착수

- 실제 승인: 사용자 `A-v2 전체 승인`. feature/M8-notifications/base b80ede0 생성. 회의§10과 레지스터를 APPROVED로 갱신했다. 이전 승인대기 기록은 당시 이력이다.
- root가 REQUIREMENTS §6.11-A, ADR-0007, 루트 AGENTS/CLAUDE에 승인된 M8 실행 경계를 연결했다. PM은 PRD를 동일 계약으로 반영한다.
- backend Task2는 m8_backend_api, frontend Task4는 m8_frontend_impl, QA 준비는 m8_qa_acceptance, PM은 m8_pm_policy다. Task3는 Task2 후 단일 backend 소유자가 이어받고 독립 검토자는 별도 컨텍스트다.
- root: 전용 Colima 재기동과 합성 MySQL13318 생성 완료. 사용자DB·환경·업로드·미추적4파일보존. API18088/FE5178은 최종검증artifact 준비뒤기동한다.

## [개발 기록] 2026-11-10 14:08 KST — 구현 중간 점검·QA 준비

- 독립 QA m8_qa_acceptance가 `qa/M8-review.md` 인수 매트릭스와 `qa/m8-acceptance.mjs`를 작성했다. root의 `node --check` exit0. 실제 API/DB/Chrome 인수는 NOT RUN이며 준비 완료를 제품 PASS로 취급하지 않는다.
- root가 ignored M8 runtime helper를 합성 account/status/old-history seed와 맞췄다. 정확한 owner 수신 알림만 41일 전으로 변경하며, transaction 성공·수정 건수 확인 뒤 ready를 기록한다. 실제 fixture 변경은 최종 artifact 이후에만 수행한다.
- root 중간 검토로 필터별 totalElements와 전체 unreadCount 분리를 요청했고 backend가 반영했다. 최신 targeted run `task2-green-all-targeted-3.log` BUILD SUCCESSFUL을 확인했으나 전체 Task2 완료·독립 리뷰는 아직 아니다.
- Task2 migration의 기존 검사로는 V5→V6 보존을 증명할 수 없어 실제 업그레이드·audit 비교·별도 DB 복원 검증을 요청했다. FE에는 계정 전환 중 지연 mutation의 상태 갱신·이동 방지를 보강 요청했다. 각 소유자만 수정한다.

## [리뷰] 2026-11-10 14:25 KST — Task2/4 자체검증·독립 검토

- frontend Task4 저자 m8_frontend_impl 인계 후 root가 실제 전체 test/lint/build를 실행했다. `build/m8-evidence/frontend-final-test.log`:47 files/423 tests PASS, lint/build exit0. 기존500kB bundle warning은 남아 있으며 오류 없는 경고제로를 주장하지 않는다. 검토본을 6b443de로 보존했다.
- 독립 m8_frontend_review는 4 Important(메인 재진입 preview 갱신, USER_001 최종실패 cache 제거, 읽음 실패 실제 재시도, transient 조회실패 기존목록 표시)와 같은계정 stale GET 회귀 부족을 보고했다. 원 저자에게 fix round1을 배정했다. 아직 Task4 최종승인 아님.
- backend가 실제 V5 source/별도 backup→V6 migration 및 전체 notification ID/audit 비교를 추가했다. `task2-final-targeted-migration-v6.log` SUCCESS27s, root XML집계7 suites/24tests/failures0/errors0/skipped0, `build/m8-evidence/task2-final-xml/`에 보존. 원문 source/로그를 대조했으며 최종 whole backend는 Task3후 수행한다.
- 독립 m8_backend_review가 고정 Task2 diff를 검토 중이다. Task3 생성 연계는 해당 저장인터페이스 검토 후 이어간다. QA 실제 인수는 여전히 NOT RUN.

## [개발 기록] 2026-11-10 14:40 KST — Task2 독립 closure·Task3 착수

- Task2 review Important3건은 원작성자가수정하고 독립 m8_backend_review가모두ADDRESSED로재검토했다. resolver scalar조회로staleACTIVE를제거하고 실제정지commitbarrier, LONGTEXT전수assertion, RRcount→동시delete→items, 동일시각21행paging를검증했다. covering16tests/실패오류skip0, 수정commit a1a48dd.
- backend Task3를 새컨텍스트 m8_generation_impl에배정했다. 단일backend작성자/Gradlerunner를이전하며 원행위5경로·전체rollback·sortedlock/currentcandidate·총3attempt·fanout/실제경쟁을담당한다. Task2author는freeze상태다.
- Task2minor DELETEOpenAPI400 및후속 Task3경쟁/Task5인수gate는ledger에유지한다. FEfix1/QA실제인수준비는별도소유로계속한다. M8완료판정은아직아니다.

## [리뷰] 2026-11-10 16:59 KST — 구현 단계 closure와 전체 검증

- Task4 fix2의 session generation·오래된 retry 무효화는 독립 m8_frontend_review가 승인했다. root 최종 전체 FE는47files/439tests PASS(`frontend-final-2-test.log`), covering31tests/lint/build도PASS다. 최종FE수정commit b8964b7.
- Task3는 원행위와알림 동시저장, 정렬잠금과기존post잠금이후current후보재검증, 확정rollback 총3attempt를구현했다. 독립review가53distinct검증/0failerrorSkip과1001명=1001행/11logicalbatches/2018SQL/854.487ms를확인했다. logicalbatch는JDBCbatch수가아니며heapdelta는peak측정이아니다.
- root 전체BE1은102suites/597tests/0failures/errors/skips,bootJar SUCCESS2m45s(`backend-final-1/summary.json`,JAR d11a75d4...)였다. 리뷰에서 reject/cancel의oldID가replacement신청을삭제하는1건을발견했고, 원작성자수정2줄+actualbarrier RED2→GREEN26을독립review가ADDRESSED로확인했다. 수정commit713822c, 전체BE2+새JAR는실행중이다.
- 실제합성DB13318에서M7보존JAR로V5legacy240행을만든뒤M8V6적용,전체ID/내용/audit지문불변을확인했다. 같은실제count/list SQL계획은V6인덱스를선택하며M7JAR재기동/docs/feed/search200을확인했다. `v5-runtime-baseline.json`, `v6-runtime-comparison.json` 보존;호환성M7프로세스는종료했다.
- Task5실제M8HTTP/Chrome는새JAR이후실행한다. Whole-branch독립리뷰에DELETEOpenAPI400,preview읽기전용안내,기존chunkwarning/새SpyBeanremovalwarning ledger를전달한다. M8최종QA/dev통합은아직완료아니다.

## [리뷰] 2026-11-10 KST — 전체 리뷰와 실제 인수 도구 보완

- root 최종 BE2는 102 suites/599 tests/실패·오류·skip 0, bootJar 성공이다. `build/m8-evidence/backend-final-2/summary.json`과 원본 XML을 보존했다. FE 전체 439 tests PASS와 함께 제품 테스트 gate를 통과했다.
- 독립 m8_final_review는 신규 제품 차단 결함 없이 With fixes를 판정했다. QA 도구의 fixture ID/삭제 순서, SQL 비용 계산, DOM locator/SPA 경쟁/무이동 검증, 잘못된 type JWT, 전체 행 fingerprint를 보완해야 한다. 미측정 운영 최대규모·SLA·peak memory와 JDBC batch 보장을 완료 범위로 주장하지 않는다.
- 실제 인수 run1(`build/m8-acceptance-20261110-170212`)은 OpenAPI 2개 확인 후 source manifest 정렬 불일치로 중단했다. 396개 개별 파일과 JAR 해시는 일치했지만 Node localeCompare와 Python ordinal 순서가 달랐다. 계정 fixture 생성 이전 실패이며 HTTP/Chrome 인수 PASS가 아니다.
- 최종 보완은 소유권에 따라 QA m8_qa_final_fix(위 도구 7개 묶음), backend m8_backend_final_doc(DELETE 400 문서), frontend m8_frontend_final_copy(최근 알림 정지계정 안내)에 분리했다. 동일 파일 중복 작성 없이 수정 후 하나의 독립 재검토를 진행한다. root는 합성 runtime의 정상 서명 REFRESH token을 메모리로만 공급하며 ACCESS 200/REFRESH 401 AUTH_004 대조를 보존했다.
- 이후 수정 영향 범위 검증·새 artifact/source manifest·실제 인수·증거 감사가 남아 있다. dev 통합은 아직 수행하지 않았다.

## [리뷰] 2026-11-10 KST — 실제 인수 run2와 최종 보완

- API 문서·preview 읽기 전용 안내는12d7ab5로 보존했다. 기존 BE599/FE439 전체 통과 이후 변경 범위 검증은 BE controller5 tests/bootJar PASS, FE shell8 tests/lint/build PASS다. 새 runtime JAR1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5, source108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47를manifest에연결했다.
- QA 도구 수정본 run2(`build/m8-acceptance-20261110-172852/evidence.json`)는310PASS/4FAIL 뒤 SPA signin timeout으로exit1했다. 실제 HTTP의생성·권한·전체이력·인증·no-store 검사는통과했고 SELECT계측은size1=4,size100=4,차이0이다.
- 실패4개는center외곽폭측정,dropdown을연뒤alarm선택자,최근알림제목의장식문자,0건badge생략처리를잘못검사한도구문제다. root가원본/제품PNG를직접확인했으며center목록780px와외곽860px는디자인과일치한다. 로그인중signin접근은GuestOnlyRoute가막으므로같은Provider의인증만료경로를주입한뒤실제signin하는검사로보완한다.
- QA m8_qa_final_fix가도구를수정하고독립m8_final_review가잔여finding을재검토한다. 실제인수최종PASS와dev통합은아직아니다. 앞선실패evidence를삭제하거나PASS로재기록하지않는다.

## [리뷰] 2026-11-10 17:40 KST — 실제 인수 run3 PASS

- `build/m8-acceptance-20261110-173900/evidence.json`: 실제 HTTP/MySQL/Chrome1440px 323 PASS, 0 FAIL, 0 NOT_RUN, failures=[]; runner exit0. root가 JSON·stdout과 provenance를 직접 대조했다.
- 396파일 source108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47, runtime JAR1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5가모두일치했다. QA도구/runtime helper/manifest/JAR도실행전provenance와실행후동일하다.
- 인증된실제GET의SELECT4/4(1개/100개), 증가량0. 생성·보존·mask/복구·차단·정지/탈퇴·인증오류·no-store·페이징·전체읽음·관리실패보존·retry·USER001clear·동일계정staleGET·계정전환후지연응답·unavailable읽음후무이동·guestguard가PASS다.
- 계정전환browser probe는실제owner200응답을hold하고기존apiClient의/me401→refresh401을주입해인증만료를발생시킨뒤같은Provider에서friend로그인·지연응답release를검증했다. 존재하지않는logoutUI를사용했다고주장하지않는다.
- 디자인원본2/제품3 PNG(센터·상단dropdown·Main최근알림)를보존했다. 독립m8_final_review가잔여finding및실제5PNG를판정중이며dev통합은아직아니다. 이전run1/2실패는도구보완이력으로보존한다.

## [리뷰] 2026-11-10 17:44 KST — 독립 최종 승인·통합 준비

- 독립 `m8_final_review`가 `qa/M8-review.md` §10에 Ready to merge: Yes를 기록했다. Critical/Important 0, QA 도구7묶음·후속locator/session probe 지적모두CLOSED, 실제5PNG디자인대조PASS다. root가판정본문과실측323PASS/source/JAR/SQL비용을직접재확인했다.
- RISK-0017은실제경쟁·원자성·1001명검증으로MITIGATED(운영용량/SLA/peak별도), RISK-0018은승인권한·상태행렬과실제stale인수로CLOSED했다. 기존chunk/SpyBean경고는비차단한계로남긴다.
- 사용자승인에포함된dev대상PR·머지를진행한다. 원격dev는기준b80ede0그대로이며제품manifest는최종인수와동일하다. 이기록시점에는아직PR/merge미실행이다. M9로확장하지않는다.

## [머지·완료] 2026-11-10 17:49 KST — M8 dev 통합

- PR #14 https://github.com/0-virus/zero-verse/pull/14 merged=true; merge b9fcb0469f42077447ce8a8842ce22f24bc94af5, tested head 201cd181ec8332e85f2cc985c9560d947b2833f4.
- Tested head/merge diff empty (exit 0), tree 9da6077a89a4528df3cb55c13556ed28300cfd28 identical; local dev fast-forward done. All 396 product files and runtime JAR match the final manifest: build/m8-evidence/integration.json. Registered remote CI checks: 0; local tests, runtime acceptance and independent review supply the verification evidence.
- Task 1-6 complete: BE full 599 + final covering 5; FE full 439 + final covering 8; HTTP/Chrome 323 PASS. No claim that the final full 441 FE suite was executed. Identical product tree does not require duplicate full testing.
- Stopped this run API18088 PID32743, Vite5178 PID24738 and zeroverse-m8-mysql84. Synthetic database/uploads/evidence/screenshots preserved. User DB13307, environment, uploads and four untracked root files preserved.
- Role STATE/WORKLOG and PRD are synchronized to M8 completion. M9 remains excluded. Execution state: WAITING FOR USER after M8 completion; no new scope under project-lead.
