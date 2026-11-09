# M7 — 피드·검색

> 현재 실행: **A-v2 전체 승인 / 구현·자동검증·실제 인수·독립 QA PASS / dev 통합 진행**. 사용자 `승인된 작업 시작해.`로 이전 보류를 해제했다. BE 538 tests, FE 405 tests, 실제 인수 259 checks를 통과했으며 M8 구현은 제외한다.

## [계획]

### 2026-11-09 19:12 KST — 기준과 역할 배정

- 기준: `dev`, HEAD와 로컬 origin/dev 추적 ref 모두 `bc99cba60457c03a346fa354d98929902f630d82`. M6 PR #12와 merge `4399f73` 및 실제 worklog/역할 기록을 대조했다. 기존 보존 XML을 직접 집계한 결과 77 suites/490 tests, failures/errors/skips=0/0/0이다. 이는 M6 증거 재확인이며 M7 테스트 실행이 아니다.
- 사용자 작업: 루트 미추적 `package.json`, `package-lock.json`, `run-backend.sh`, `run-frontend.sh`를 보존한다. `.env.local`, `.local-data/`, 기존 DB·업로드는 변경·삭제하지 않는다.
- 정본: REQUIREMENTS FR-FEED-01~02, FR-SEARCH-01, NFR-04/09; PRD §5.9/5.10, §7 MainPage/SearchPage, §9-G/N/S, §10 M7, §11/12; DESIGN-SYSTEM §8.1/8.8 및 Main Feed v2/Pages 원본.
- 현재 코드: MainPage 탭/빈 상태, SearchPage scaffold, TopBar 검색 submit 미연결. QueryDSL 의존성과 PostAccessPolicy, M5 방향별 관계, M6 댓글·좋아요 집계를 재사용할 수 있다. 기존 PostSummary는 count가 없고 RightPanel 관계 현황은 빈 상태다.
- 소유권: 리더가 본 worklog·governance·JOURNAL·Git 단독 담당. backend는 src/Gradle와 역할 기록, frontend는 frontend와 역할 기록, QA는 qa와 역할 기록, PM은 PRD와 역할 기록을 소유한다. 독립 심의 검토자는 읽기 전용이다.
- 준비 배정: `/root/m7_backend_prep`은 쿼리·ACL·본문 검색·API/검증 실현성을, `/root/m7_frontend_prep`은 화면·URL·counts/디자인 계약을 조사한다. 승인 전 제품·테스트 변경은 배정하지 않았다.
- 심의 사유: BE/FE 및 여러 도메인, 접근제어, 공개 API 세부 계약을 포함한다. 3인 독립 심의 후 HIGH 계약은 사용자에게 구체안을 제출한다.

### 실행계획 — A-v2 계약 승인 후 실행

**Goal:** M7 피드·검색을 실제 API와 디자인 화면, 접근권한별 정확한 paging/count까지 완성한다.
**Architecture:** QueryDSL DB predicate로 필터/ACL/정렬/count를 먼저 적용한다. 기존 JSON에서 생성한 검색용 text cache와 page IDs batch DTO를 사용하고 FE URL/계정별 요청 세대로 결과를 격리한다.
**Tech Stack:** 기존 Java21/Spring Boot/JPA/QueryDSL/MySQL8/Flyway 및 React19/TypeScript/Vite. 새 외부 의존성 없음.
**Spec:** [승인 계약 A-v2](../governance/meetings/M7-20261109-feed-search.md#6-a-v2--사용자-승인-계약-실행-보류). 독립3인 보완 및 사용자 전체 승인 완료. 실행은 추가 지시까지 보류한다.
**Execution:** 역할별 subagent 구현과 별도 QA 컨텍스트. 작업자는 subagent-driven-development/TDD/verification 원칙을 적용한다. 역할에 Git을 배정하지 않으며 리더가 최종 명시 파일만 통합한다.

#### Global Constraints

- V1~V3 불변·기존 데이터/원문/audit 보존·추가 라이브러리 없음. 사용자 DB에는 임의 migration/repair를 적용하지 않는다.
- ApiResponse/PageResponse 필드·기존 AUTH/VALIDATION 오류, M4 Post ACL·M5 방향별 관계·M6 count 의미를 따른다.
- 디자인1440px·radius0·3px ink보더·크림페이퍼·pixel 영문 전용. M8/S3/BlogPage count 확장 제외.

#### Task 1 — 검색용 본문 캐시와 기존 DB 업그레이드 (backend)

- Create: `src/main/resources/db/migration/V4__post_search_text.sql`, `src/main/java/db/migration/V5__Backfill_post_search_text.java`, `src/main/java/com/zeroverse/domain/post/service/PostTextExtractor.java`.
- Create: `src/main/java/com/zeroverse/domain/post/service/PostTextExtractorV1.java`(버전고정), `src/main/java/com/zeroverse/tools/PostSearchTextMaintenance.java`(offline verify/rebuild); `build.gradle`에 `verifyPostSearchText`, `rebuildPostSearchText` JavaExec 작업. env `M7_TEXT_DB_URL`, `M7_TEXT_DB_USER`, `M7_TEXT_DB_PASSWORD` 사용, stdout에 값 출력 금지. rebuild는 `-Pm7TextApply=true -Pm7TextOffline=true`가 모두 없으면 거부한다.
- Modify: `domain/post/entity/Post.java`, `domain/post/service/PostContentService.java`, `PostService.java`; 위치를 참조하는 AGENTS는 해당 소유자가 동기화.
- Test: `src/test/java/com/zeroverse/domain/post/PostTextExtractorTest.java`, `PostSearchTextMigrationMySqlTest.java`, 기존 post create/update tests.
- Interface: `PostTextExtractor.extract(JsonNode): String`; `Post.getContentText(): String`; create/update가 동일 transaction에서 text를 갱신. Flyway migration은 Spring bean을 사용하지 않는다.
- `PostTextExtractorV1.extract(JsonNode):String`이 migration/앱/maintenance의 고정 의미를 공유한다. V5 DML 전체rollback·원래updated_at 명시보존·V4DDL잔존/실패history 복구는 계약 §6.4를 따른다. nullable 컬럼을 임의 NOT NULL로 바꾸거나 corrupt JSON에 HTML fallback을 적용하지 않는다.
- [ ] `inline_marks_do_not_split_words`, `entities_are_actual_text`, `attributes_are_excluded`, `block_boundaries_are_spaces`, `empty_image_doc` 실패 테스트 작성·RED 확인.
- [ ] 파생 컬럼/extractor와 모든 쓰기 경로를 구현하고 targeted GREEN.
- [ ] 격리 MySQL V3 rich/draft/deleted rows→V5 upgrade에서 원문/audit/view/publish 보존·batch 경계·재시작·잘못된 JSON 실패 검증. 빈 DB 전체 migrate도 통과.
- [ ] 정상 최소doc/empty/image-only, 100/101/201·IDgap·후반invalid에서 V5전체rollback, 합성fixture수정/repair/재실행, snapshot backup/restore, verify stale탐지→offline rebuild→stale0, oldwriter 후 재구축을 검증. 새 테스트 `PostSearchTextMaintenanceMySqlTest.java` 포함.
- 명령: 기존 Java21/Gradle/Colima 환경에서 `./gradlew test --tests '*PostTextExtractorTest' --tests '*PostSearchTextMigrationMySqlTest' --no-daemon`; failure/error/skip0이어야 완료.

#### Task 2 — 서버 피드·검색과 batch DTO (backend, Task1 의존)

- Create: `src/main/java/com/zeroverse/config/QuerydslConfig.java`, `domain/post/repository/PostDiscoveryRepository.java`, `domain/post/service/PostDiscoveryMapper.java`, `domain/feed/{controller/FeedController,service/FeedService,dto/FeedDtos}.java`, `domain/search/{controller/SearchController,service/SearchService,dto/SearchDtos}.java`, 해당 domain AGENTS.
- Create: `src/main/java/com/zeroverse/security/DiscoveryViewerResolver.java` — `resolve(HttpServletRequest,ZeroverseUserPrincipal,boolean requireAuth):Long`, 계약 §6.2의 missing/malformed/expired/deleted/SUSPENDED 표를 M7에만 적용.
- Modify as needed: 기존 post-tag/comment/like repository에 page-ID batch 조회만 추가; SecurityConfig/OpenAPI는 기존 M4~M6 계약을 유지하며 새로운 API 인수를 연결한다.
- Test: `src/test/java/com/zeroverse/domain/feed/FeedQueryMySqlTest.java`, `FeedControllerTest.java`, `domain/search/SearchQueryMySqlTest.java`, `SearchControllerTest.java`, `DiscoveryQueryBoundMySqlTest.java`.
- Interface: `FeedService.publicFeed(viewerId,keyword,page,size,sort)` / `universeFeed(viewerId,relation,keyword,page,size,sort)` → `PageResponse<FeedPostSummary>`; `SearchService.search(viewerId,keyword,type,page,size,sort)` → 계약 §6.3 SearchResponse. enum/DTO 필드 이름은 회의 계약과 동일.
- [ ] guest/owner/unrelated/one-way/friends/blocked/suspended/deleted × 공개범위/임시저장 matrix에서 items·total·counts 기대값을 고정하고 RED 확인.
- [ ] DB predicate, deterministic sort, selected-page batch mapper, counts snapshot 및 validation 구현·GREEN.
- [ ] multi-tag 중복·inline word/amp 오탐·wildcard·100자 경계·large page·count누출 없음·no views/no notifications, size1/100 SQL 상한을 검증.
- [ ] Swagger schema/status/security와 malformed Bearer/쿼리문자열/guest401 회귀 확인.
- [ ] `SearchSnapshotMySqlTest.java`에서 RR snapshot 도중 다른transaction의 title/relation/tag/count 변경을 강제하고 items/total/5facet/matchedTypes/metadata가 같은snapshot인지 증명. keyword 누락/blank·모든 header종류·유효SUSPENDED·deleted404·offsetint한계 테스트 포함. readReady NULL cache 거부·projection LONGTEXT미적재·빈IDs쿼리0 확인.
- 명령: `./gradlew test --tests '*Feed*Test' --tests '*Search*Test' --tests '*Discovery*Test' --no-daemon`, 이어서 최종 `./gradlew test bootJar --no-daemon`. Gradle은 backend 단일 실행자로 조정한다.

#### Task 3 — 메인 피드·관계 현황 (frontend, 승인 DTO 의존; Task1/2와 병렬 가능)

- Create: `frontend/src/features/feed/{types,feedApi}.ts`, `feedApi.test.ts`, `AGENTS.md`, `frontend/src/test/mainFeedBehavior.test.tsx`.
- Modify: `frontend/src/pages/MainPage.tsx`, `components/layout/RightPanel.tsx`, 필요한 AppShell 연결 및 PostCard의 접근성/실제 link 처리.
- Interface: FeedPage는 계약 §6.3 필드. 관계현황은 기존 `listUniverse(kind,{page:0,size:1}).totalElements`, 오류를0으로 대체하지 않는다. 기존 BlogPage 호출 호환 유지.
- [ ] guest/public vs auth/universe, 3탭과 page reset, keyword 제출, server total/count, keyboard 카드 이동 테스트 RED.
- [ ] 원본 레이아웃에서 API/URL·loading/error/empty/retry·3개 관계현황 연결 후 GREEN.
- [ ] `friend`→`friends` canonicalURL와 friends포함통계/친구제외following탭 설명, AUTH_002/004/USER_001 최종실패 결과폐기를 검증.
- [ ] 지연된 이전 계정/탭/page 응답은 새 상태를 덮지 않고, auth 확인 중 guest 조회를 하지 않음을 검증.

#### Task 4 — 검색 화면·전역 검색 (frontend, 승인 DTO 의존; Task3 뒤)

- Create: `frontend/src/features/search/{types,searchApi}.ts`, `searchApi.test.ts`, `AGENTS.md`, `frontend/src/test/searchBehavior.test.tsx`.
- Modify: `frontend/src/pages/SearchPage.tsx`, `components/layout/TopBar.tsx`, 필요 시 `components/ui/Tab.tsx`; existing TopBar/router tests 확장.
- Interface: SearchResponse는 회의 §6.3의 PageResponse+counts와 matchedTypes. keyword/type/page/size/sort URL로 복원. all검색 중복/범위별 overlapping counts를 클라이언트에서 재집계하지 않는다.
- [ ] submit→URL→실 API 호출, blank 미호출, 5탭 서버count/배지, page/back/reload, owner draft→edit 링크 테스트 RED.
- [ ] 원본1000px 단일컬럼 UI/상태 구현 후 GREEN.
- [ ] post-centric 검색보조문구·전체matchedTypes/선택type배지·overlap counts·unknown/빈/대소문자잘못된 URL값을 일관되게 처리. 직접 API는400, FE malformed URL은valid defaults로 canonical replace하거나명시입력오류를 보이되서버계약우회 없음.
- [ ] keyword/계정 변경 시 즉시 old items/count 폐기, 늦은 응답 무시, transient오류 재시도와 확정 auth실패 상태폐기 검증.
- 명령(Task3/4): `cd frontend` 후 `npm test -- --run --maxWorkers=1`, `npm run lint`, `npm run build`. exit0·skip없는 관련 행동 검증 필요.

#### Task 5 — 독립 QA·실제 인수 (qa, Task1~4 산출물 의존)

- Create: `qa/M7-review.md`, 필요 시 `qa/m7-acceptance.mjs`. QA는 src/frontend 제품·테스트를 직접 수정하지 않는다.
- [ ] 독립 컨텍스트로 spec/diff/source/tests와 migration·ACL·counts·query-bound 증거를 대조한다.
- [ ] 합성 MySQL/API·FE에서 guest/owner/친구/단방향/차단/비공개·임시저장·다중매칭·page2·back/reload·본문 escaping을 실제 HTTP로 검증한다.
- [ ] Chrome1440px 메인3열/검색1000px, 입력·탭·페이지·빈상태·오류·계정전환과 원본 시각 대조. 실제 counts/보호글 미노출 확인.
- [ ] finding은 원소유자에 반환하고 독립 재검토한다. backend/FE 전체검증은 의미 있는 최종수정 후 한 번 통합 실행한다.

#### Task 6 — 정본·Git·완료 (리더, PM/각 역할 기록)

- [ ] 승인 후 root REQUIREMENTS/governance와 PM PRD를 동일 계약으로 동기화. 각 역할 STATE/WORKLOG는 해당 소유자 갱신.
- [ ] PRD route map `/universe/summary`를 실제3목록 total로 교체하고 §9에 post-centric 검색/피드following/텍스트cache 승인결정을 반영. README에 실제검증한 offline verify/rebuild·upgrade/rollback호환표·명령 기록. 원본 디자인 파일 변경없음.
- [ ] 검증 source와 최종 diff 동일성·비밀/사용자 파일 미포함·`git diff --check` 확인.
- [ ] 명시 파일 commit/push, dev PR, 독립 최종 review, merge SHA/tree 확인과 local dev 반영.
- [ ] 이번 검증용 runtime 정상 종료·DB/파일 보존, worklog/JOURNAL/README·역할 완료 기록. M8 착수하지 않음.

#### Review Focus

1. HTML 엔티티 문자열 오탐과 inline mark 단어 누락 — Task1 extractor+Task2 실제 검색.
2. DB backfill이 updated_at을 바꾸거나 구버전 앱 복귀 뒤 cache가 stale — Task1 upgrade/복귀 한계 기록.
3. 비공개 글이 items에 없어도 탭 count/total에 새는 경우 — Task2 ACL 전체 matrix+Task5 HTTP.
4. multi-tag 조인이 중복글/빈페이지/잘못된 total을 만드는 경우 — Task2 distinct·21+paging/query 상한.
5. 계정·검색어 전환 후 늦은 응답이 개인 결과를 복원하는 경우 — Task3/4 지연응답 행동+Task5 실제 전환.

## [개발 기록]

제품 구현 미착수. 실행계획과 승인 계약을 작성한 뒤 역할별 TDD·독립 QA로 진행한다.

## [이슈·결정]

- 2026-11-09: root가 `feature/M7-feed-search`를 생성했다(base bc99cba). 독립 Product88/Architecture87/Delivery91 모두 APPROVE_WITH_CHANGES·HIGH. 실제소스 확인 후 A-v2에 인증상태표·RR전체snapshot·migration audit/전체rollback·빈doc/복구/immutable extractor·offline재구축·post-centric카피·관계의미를 반영했다. [심의 §5~7](../governance/meetings/M7-20261109-feed-search.md) 참조. 사용자 승인 전 제품 구현은 시작하지 않는다.

- 로그인 메인은 `/feed/universe`, guest는 `/feed/public`이다(PRD §7 MainPage). 로그인 `전체`를 전체 공개 피드로 임의 변경하지 않는다.
- 검색의 BLOG/USER/TAG는 게시글 검색 범위다. 독립 사용자·블로그 결과로 확장하지 않는다. 탭별 전체 건수·매칭 배지 계약은 심의에서 구체화한다.
- HTML 태그/속성 검색 오염 및 entity 문자 누락을 피하는 본문 텍스트 전략을 조사한다. 전체 후보를 앱으로 읽거나 페이지를 받은 후 필터링하는 접근은 계획에 채택하지 않는다.
- M8 알림 row/최근 알림/배지/드롭다운, S3 후속, 관리자, 기존 BlogPage 목록 count 확장은 제외한다. 메인 피드 카드 count와 유니버스 현황 공급원은 M7 화면 범위로 구체화한다.

## [리뷰]

계획 독립 심의 3인 완료·보완 A-v2 사용자 결정 대기. root가 실제 PostContentService 빈doc허용·V1 updated_at ON UPDATE·M6 controller 인증검사를 직접 확인했다. QA 준비 `qa/M7-review.md`를 직접 읽었으며 제품 테스트/HTTP/Chrome 인수는 모두 NOT RUN이다.

## [머지]

미실행.

### 2026-11-09 — 승인·사용자 보류 체크포인트

- 사용자 A-v2 전체 승인과 착수 보류를 함께 수신했다. 리더가 실행 중 PM/QA를 중단했고 새 배정·재시도·Git 변경을 멈췄다.
- 보존: `feature/M7-feed-search`(base bc99cba), 실행계획·독립 심의·QA 준비 문서. 제품 구현/테스트/마이그레이션/실제 인수/commit/PR은 미실행이다.
- 재개 위치: 사용자 명시 지시 확인→현재 Git/사용자 변경·역할 상태 재대조→승인된 REQUIREMENTS/PRD 동기화→Task1~4 구현. 같은 계약의 승인을 다시 요청하지 않는다.

### 2026-11-09 22:05 KST — 승인된 실행 재개

- 사용자 `승인된 작업 시작해.`에 따라 보류를 해제했다. base/HEAD bc99cba와 feature/M7-feed-search, 이전 준비문서 및 사용자 root4미추적파일을 대조했다. 현재 제품diff는 없었다. 이전 STATE의 승인대기는 최신 사용자 결정으로 해제된다.
- backend `/root/m7_backend_impl` Task1~2, frontend `/root/m7_frontend_impl` Task3~4, PM `/root/m6_pm_closeout` PRD, QA `/root/m5_independent_qa` Task5 준비를 배정했다. 공유 checkout의 소유권을 분리했고 Git은 리더만 조작한다.
- REQUIREMENTS §6.10-A에 승인된 API/DTO/auth/RR/text-cache/migration/복구/FE 계약을 반영했다. 원본 newline 바이트를 보존했고 기존 FR/과거 결정은 삭제하지 않았다.
- 기존 Colima VM을 재기동했다(exit0). 사용자 Docker Desktop의13307 listener는 보존하며 테스트는 Colima 명시socket과 격리Testcontainers를 사용한다. 실제인수는 M7전용13317/18087/5177로 준비한다. 사용자DB/.local-data/.env.local은 변경하지 않는다.
- 다음: Task1 text/migration 자체GREEN→독립scopedreview, FE구현→BE통합·독립QA. 아직 M7 PASS/완료 주장은 없다.

### 2026-11-09 — backend 구현 소유권 분할

- Task1의 Post.getContentText/extractor interface와 초기 단위7건·실제MySQL이관2건의 통과XML을 root가 확인했다. 이는 Task1전체완료가 아니며 maintenance/복구 검증은계속한다.
- Task2신규소스미작성상태를확인한뒤 `/root/m7_discovery_impl`에 신규feed/search/QueryDSL/security·batchrepository·Task2 tests만분리배정했다. `/root/m7_backend_impl`은Post entity/content·extractor·migration·maintenance·Gradle·Task1 tests·backend역할기록을유지한다. 작업자는서로의소유파일을편집하지않는다.
- Gradle실행은Task1담당이단일조정하며Task2담당이실행을요청한다. root는승인계약을 ADR-0006으로기록했고PM PRD동기화를직접확인했다. 계약변경·범위확대는없다.

### 2026-11-09 — M7 구현 checkpoint와 독립 검토

- FE Task3/4 담당 보고는 전체 386 tests, lint/build exit 0이다. root가 실제 API/types·주요 diff·역할 로그를 확인했고 `/root/m7_frontend_review`에 tracked/untracked 전체 snapshot 독립 검토를 배정했다. 실제 HTTP/1440px 인수는 아직 미실행이다.
- Task1 담당 보고는 backend 전체 504 tests 및 bootJar 성공이다. 후속 targeted run이 XML을 덮었으므로 root가 이 전체 XML을 직접 재집계했다고 주장하지 않는다. Task2 통합 후 최종 suite 출력·XML을 보존해 확인한다. `/root/m7_text_review`에 Task1 diff 독립 검토를 배정했다.
- QA harness의 M8 알림 API 의존을 합성 DB 전후 fingerprint로 교체하고 status-ready 필수 ID 및 실제 파일/JAR SHA 재계산을 보강했다. root가 수정 코드를 직접 확인했다.
- README에 승인된 M7 이관·offline verify/rebuild·구버전 writer 복귀 절차를 추가했다. 사용자 DB는 변경하지 않았다.
- Task3/4 fix round 1: 최종 인증 실패의 guest 자동 전환, RightPanel 계정 변경 순간의 이전 통계 노출, 필수 지연응답/오류 행동 검증 누락을 원 FE 작성자에게 수정 배정했다. 검색 카드의 정본 수치와 범위 초과 페이지 복귀 경로도 함께 보완한다. 독립 재검토 및 실제 인수 전에는 PASS로 닫지 않는다.
- Task1 fix round 1: 파싱 예외 cause의 원문 노출과 JSON trailing root 수용을 원 backend 작성자에게 수정 배정했다. snapshot backup/restore, maintenance 후반 실패 전체 rollback·connection 복원, 정확한 201행·gap·deleted·legacy 보존, extractor 제한 경계도 승인된 필수 검증으로 보강한다. 코드 결함과 아직 입증하지 않은 경계를 구분했으며 기존 504 통과를 전체 인수로 간주하지 않는다.
- Task2 fix round 1: 독립 `/root/m7_discovery_review`는 초기 custom filter 등록 순서(후속 소스에서 수정 확인), Unicode 공백 정규화 결함 및 필수 검증 공백을 보고했다. 원 작성자는 제품/기존 6 tests/실제 HTTP query-bound를 담당한다. `/root/m7_discovery_contract_tests`는 신규 `M7DiscoveryHttpContractMySqlTest`·`M7DiscoverySnapshotCoverageMySqlTest` 두 파일만 담당해 실제 JWT/ACL/페이징/읽기 부작용과 전체 RR metadata 경계를 보강한다. helper는 최종 QA가 아니며 자기 검증을 최종 승인으로 쓰지 않는다. Gradle은 기존 Task1 담당 단일 실행으로 유지한다.
- Task3/4 fix round 1 재검토: Important 3건 및 검색 디자인 보완은 모두 ADDRESSED, 신규 blocking 없음이다. Main 범위 초과 페이지 복귀 경로만 남아 원 작성자에게 작은 후속 수정을 배정했다. 실제 HTTP/Chrome 인수는 계속 미실행이다.
- Task1 fix round 1 재검토: 코드 결함 2건은 ADDRESSED이며 보존 XML 3 files/17 tests, 실패·오류·skip 0을 root와 독립 검토자가 확인했다. 다만 실제 201행이 아닌 200행인 fixture와 DB 복구 대신 별도 테이블 복사만 수행한 증거가 남았다. round 2로 정확한 201행 및 V3 스키마/history 포함 backup→별도 DB restore→V4/V5 재이관을 배정했다. 앞 역할 보고의 전체 복구·201행 완료 주장은 현재 확인 근거보다 넓었으며 이 항목으로 정정한다.
- Task1 담당 turn 종료 후 Gradle 단일 실행 조정은 root로 인수했다. 모든 backend 작성자는 소스 freeze와 target class를 root에 인계한 뒤 테스트 결과를 받아 후속 수정한다. 최종 full 실행은 모든 제품·테스트 작성이 멈춘 뒤 수행한다.
- 22:50 KST 최신 FE 검증: Main 범위 초과 페이지 복귀 수정 후 root가 직접 `npm test -- --run --maxWorkers=1`(41 files/405 tests, exit 0), `npm run lint`(exit 0), `npm run build`(exit 0)를 실행했다. stdout은 `build/m7-evidence/frontend-final-{tests,lint,build}.log`에 보존했다. 기존 500KB chunk 경고는 유지되며 새 의존성 추가는 없다. 해당 소스 추가 변경 전까지 이 결과를 최신 FE 자동 검증 근거로 사용한다.
- 22:53 KST root backend targeted 1: 12 files/34 tests 중 33 PASS, 1 FAIL. 원본 stdout 및 XML은 `build/m7-evidence/backend-targeted-1.log`·동명 디렉터리에 보존했다. Task2 9 suites/24 tests는 모두 통과했다. 실패는 새 backup 복원 테스트의 외래키 session 설정이 실제 insert 연결과 달랐던 것으로 추적됐다. 원 Task1 작성자가 단일 connection으로 수정 중이며, 첫 재컴파일의 checked SQLException 선언 누락도 전달했다. 이 단계 전체 PASS 주장은 없다.
- Task2 fix round 1 재검토: 필터 순서/주요 JWT·ACL·paging·부작용은 ADDRESSED. Unicode 양끝 공백 정규화 순서, helper `anySatisfy` 내부 조건문으로 대상 아닌 행이 검증 없이 통과하는 문제 2곳, size100 쿼리 절대 상한·증가 없음 입증은 남아 fix round 2로 배정했다. SQL projection 실행 증거와 offset의 int 거부 분기도 보강한다. 기존 snapshot/code 변경 범위 안에서 검증을 강화하며 승인 계약을 완화하지 않는다.
- Task1 fix round 2 최종 재검토 PASS: root 실제 `migration-snapshot-fix2.log`와 보존 XML은 migration 5 + helper snapshot 6 = 11 tests, 실패·오류·skip 0이다. 독립 검토자는 기존 코드 2건 및 남은 검증 2건 모두 ADDRESSED, 신규 blocking 없음으로 확인했다. 복구 방식은 **V3 migration으로 별도 MySQL 스키마 재생성 + JDBC 전체 테이블 데이터/history 복원 + V4/V5 재이관 대조**이며 mysqldump 검증은 아니다. 최종 fixture는 202행 삽입·1행 gap 삭제 후 실제 201행(100+100+1)을 유지한다.
- 최신 전체 자동 검증: root의 `--rerun-tasks test bootJar`는 2m16s, 91 suites/538 tests, 실패·오류·skip 0으로 종료했다. stdout/XML/집계는 `build/m7-evidence/backend-final-1*`, JAR SHA256은 `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`이다. FE 최신 405 tests/lint/build 통과와 함께 immutable JAR 18087·합성 DB13317·FE5177를 기동했다. 제품/테스트 354파일 manifest를 보존했다.
- 전체 독립 리뷰 `/root/m7_whole_branch_review`는 신규 제품 Critical/Important 없음으로 판정했다. QA 도구의 body 전체 검색 판정이 heading 또는 공개 블로그 제목을 잘못 해석하는 문제와 실제 검색 탭/페이지/back/empty/error/retry 흐름 누락을 확인해 QA 원 작성자에게 단일 수정 작업으로 배정했다. 실제 인수 1차는 합성 oneWay 초기 설정에서 중단됐으며 아직 실제 QA PASS가 아니다. 최종 merge-ready는 도구 수정·실제 인수·수정 범위 재검토 이후 판정한다.

### 2026-11-09 23:26 KST — 실제 HTTP·Chrome 인수 최종 PASS

- 실제 실행자는 root, 인수 도구 작성·수정과 독립 판정은 `/root/m5_independent_qa`다. 모든 실행은 합성 계정·별도 MySQL13317/API18087/FE5177에서 수행했다. 사용자 DB는 적용 대상이 아니었다.
- run1(`build/m7-acceptance-20261109-230615`)은 oneWay fixture의 대문자 slug가 기존 `BLOG_003` 검증에 걸려 실패했다. QA slug만 lowercase로 수정했고 제품 정책은 유지했다.
- run2(`build/m7-acceptance-20261109-231741`)는 HTTP·manifest·Main 관계 전환 등 203 checks가 통과한 뒤 상단/피드 검색창의 부분 이름 매칭으로 strict-mode 오류가 발생했다. 상단 `header` 및 accessible name exact 매칭으로 수정했다.
- run3(`build/m7-acceptance-20261109-232138`)는 256 PASS/3 FAIL이었다. 최초 생성한 owner.multi가 최신순 page1에 있는데 page0 카드로 고정 기대한 도구 오류였다. 초기/재시도/계정 전환에서 실제 API page item의 ID 기반 href와 제목을 화면의 result article에 대조하도록 수정했다. 실패 실행 이력은 삭제하지 않았다.
- run4(`build/m7-acceptance-20261109-232636`)는 **259 PASS / 0 FAIL / 0 NOT_RUN**, `failures=[]`, root process exit 0이다. 실제 5 검색 탭·서버 counts, 21건 pagination/back, empty, HTTP500 후 retry, owner/다른 계정·guest, 지연 요청, public/universe/private/draft, suspended/deleted, no-store·조회수/알림 무변경을 확인했다. screenshot 4개와 `evidence.json`을 보존했다.
- manifest 354파일의 actual source SHA256은 `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c`, actual JAR SHA256은 `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`로 각각 기대값과 일치했다. root가 실제 출력·JSON·대표 Main/Search 이미지와 원본을 직접 확인했다.
- 전체 독립 검토자는 제품 및 QA 도구의 원 finding 모두 CLOSED, 신규 Critical/Important 없음으로 판정했고 실제 run4 증거도 직접 확인했다. 최종 4 screenshot의 독립 시각 판정과 기록을 마친 뒤 dev 통합한다.
- 제품 원자적 커밋: Task1 `5d7ede2`, Task2 `059ceed`, FE `0b3008a`. 실제 인수 중 제품 source 변경은 없었다. 원격 dev는 기준 `bc99cba` 그대로임을 fetch로 확인했다.

### 2026-11-09 — 최종 독립 리뷰·QA closure

- `/root/m7_whole_branch_review`: 제품 전체 및 QA 수정 재검토에서 Critical/Important 0, 원 finding 모두 CLOSED. run4의 실제 259 PASS와 source/JAR 해시를 직접 확인했다.
- `/root/m5_independent_qa`: 원본/제품 1440px screenshot 4개 직접 대조 PASS, 제품 finding 0, **M7 QA PASS / merge-ready Yes**. 상세는 `qa/M7-review.md`에 고정했다.
- 시각 차이 중 feed keyword 입력·게시글 중심 검색 카피/메타데이터는 승인 A-v2 기능이며, Main hero 하단 계단 장식은 이전 마일스톤 baseline이다. 해당 구분과 실제 데이터 차이를 독립 QA가 기록했다.
- owner draft `/edit/:id`는 FE 행동 회귀·source·실제 HTTP owner draft 접근으로 검증했다. Chrome에서 별도 draft 클릭을 실행했다고 주장하지 않으며 QA 기록의 증거 경계를 유지한다.
- README의 macOS/Linux 명령은 실제 검증에 사용한 Java `GradleWrapperMain` 경로를 병기해 tracked wrapper의 실행 권한(100644)에 의존하지 않도록 정리했다. 원문/audit/호환·복구 계약 변경은 없다.
- 모든 필수 제품/검증 finding이 닫혔으므로 승인된 PR/dev 통합을 진행한다. 새 사용자 승인 안건은 없다.
