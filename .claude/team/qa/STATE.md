# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-08 10:05 KST (M3 최종 LOCKED 실측·QA APPROVE)

## 현재 단계

- M2는 PR #8이 `dev`에 머지됐고 QA 최종 판정은 APPROVE(96/100)다. M2 전체 증거를 반복 실행하지 않는다.
- M3는 사용자의 `시작` 지시로 Q1~Q4 계약이 승인되어 구현·자동/API 검증과 native DnD·LOCKED confirm 실측까지 끝났다. QA 최종 acceptance는 **APPROVE**이며 dev merge·마감은 root가 수행할 다음 단계다.
- 현재 기준선은 `feature/M3-categories` HEAD `08239e0514b6a1a78f090c3b0961e8fd4a60403e`다. `4fc9ae2` 이후 `src`·`frontend` 변경은 없고 QA는 구현 파일을 수정하지 않는다.

## 진행 중

- `qa/M3-review.md`에 FR-CAT-01~05/FR-BLOG-02/NFR-04·08·09 및 승인 계약의 독립 acceptance matrix를 작성했다.
- V1→V2 active-key unique, 잠금·subtree 삭제, 전체 order 배열·last-write-wins, 실제 posts 이동/count, 방향성 UNIVERSE, setup 부분 실패 복구, Swagger/보안, 1440px 디자인의 증거 요구를 구체화했다.
- 기존 `MySqlTestSupport`·보안/OpenAPI/Blog 테스트와 FE `apiClient`·초기 설정 테스트 자산을 재사용 대상으로 확인했다.
- 리더 동기화 후 회의록 `APPROVED`, ADR-0005 `ACCEPTED`, 결정 레지스터, REQUIREMENTS §6.4/NFR-04·08, PRD §9.5·§10~§12, M3 worklog의 승인 맥락을 독립 대조했다. 과거 대기 문구는 회의 §10 역사 기록으로 보존되어 있다.
- BE 초안에서 전달된 LOCKED reorder no-op, 삭제 owner GET, trim 전 validation, root `parentId=null`, GET CAT_004 문서 status, DELETE/order OpenAPI 항목은 최신 full XML·HTTP/JAR 증거로 재검증되어 현재 blocker가 아니다.
- QA 소유 `qa/m3-api-smoke.ps1`를 추가했다. loopback만 허용하고 합성 계정 2개·실제 auth/initial-setup DTO·공통 envelope·401/403·카테고리 CRUD/잠금/순서/삭제 경계를 메모리 내 토큰·비밀번호로 점검하도록 작성했으며, PowerShell `Parser::ParseFile` 문법 검사와 loopback HTTP smoke를 종료 코드 0으로 완료했다.
- 준비된 `http://127.0.0.1:8080` fresh API에서 `qa/m3-api-smoke.ps1`를 실제 실행해 종료 코드 0을 확인했다. 출력에는 fixture prefix `m3sdab71e3d`만 남았고 합성 owner/viewer 계정은 cleanup하지 않았다. HTTP envelope/timestamp·auth/setup DTO·anonymous/owner/non-owner 보안·trim/duplicate·child/tree·GENERAL→LOCKED·DEFAULT/LOCKED 보호·full-ID reorder·subtree delete/recreate·stale CAT_007 assertion이 통과했다. 이 결과는 SQL post 이동/count·101 root·numeric enum·Swagger·FE/1440px 증거가 아니다.
- 최신 backend full build 결과를 직접 읽었다. `build/m3-root-full-build.log:38`은 `BUILD SUCCESSFUL in 10m 24s`이며 parent가 보고한 wrapper exit는 0이다. `build/test-results/test/TEST-*.xml` 56개를 재실행 없이 XML 파싱해 합계 370 tests, failures/errors/skips 모두 0을 확인했다.
- 관련 XML 증거: `CategoryServiceMySqlTest` 14/0은 101 root/page·direct child, full sibling/CAT_007, LOCKED slot/CAT_006, DEFAULT·LOCKED 불변, GENERAL→LOCKED, LOCKED child subtree 보호, live/deleted post의 DEFAULT 이동, owner/draft·direct count·UNIVERSE 방향, create/reorder/delete 경쟁을 포함한다. `CategoryControllerMySqlTest` 6/0은 anonymous/non-owner write, 삭제 owner GET/POST BLOG_001, root null/children, trim 후 100자, malformed enum/string/array/query `VALIDATION_001`, generated OpenAPI status/security를 포함한다. `CategoryMigrationTest` 1/0은 V1 기존행·삭제 history·active_key/name/order scope와 V2 재사용을 확인한다. `GlobalExceptionHandlerTest` 5/0은 JSON/query binding 400을 포함한다. `BlogPublicControllerTest` 8/0도 deleted-owner blog 404를 통과했다.
- 이전 F-M3-ERR-03은 현재 source `JacksonConfig.java:15–18`의 `featuresToEnable(FAIL_ON_NUMBERS_FOR_ENUMS)`와 controller numeric enum case(`CategoryControllerMySqlTest.java:128–175`)가 full XML 6/0으로 통과해 **현재 열린 결함이 아님**으로 정리한다. 삭제 owner HTTP/OpenAPI/공통 오류도 같은 full 결과로 정적·실행 증거가 보강됐다.
- LWW는 `CategoryServiceMySqlTest.xml`의 동시 same-ID reorder가 통과하고 `CategoryService.java:180–261`이 blog `PESSIMISTIC_WRITE` lock·전체 sibling ID 검증·임시/최종 flush를 수행한다. `CategoryServiceMySqlTest.java:607–610`의 callback은 outer `TransactionTemplate` 안에서 실행되지만 같은 blog lock이 outer commit까지 유지되므로, T2 callback은 T1 성공 commit 뒤에만 도달한다. 두 `Future.get()` null assertion이 outer commit 성공을 확인하므로 최신 소스·테스트 경계에서 last-write-wins 증거 공백은 해소로 정정한다.
- 최신 completion-intent 패치까지 실제 source/관련 회귀 소스를 재독해했다. `BlogInitialSetupPage`는 `setupCompletionTo:'blog'` history intent를 먼저 커밋한 뒤 effect에서 `refreshUser`하고, `SetupGuard`는 인증된 `defaultBlog.urlSlug`로 own-blog 목적지를 고정한다. partial management·refresh failure·retry success·remount recovery 및 F05/F06 async drain 회귀를 확인했다. parent의 관련 68 tests와 FE 269/lint/build exit 0은 구현자 참고 결과이며 QA는 npm/Vitest를 재실행하지 않았다. 새 critical 실제 결함은 발견하지 못했다.
 - FE 테스트 소스에는 mutation reload 실패·A→B→null→A 전환·지연 create/order response drain·count/동일 viewer 재조회·partial/lost category POST·completion intent own-blog·refresh failure management recovery·remount history recovery가 추가되어 있다. root 독립 브라우저에서 full reload/session·1440px screenshot·keyboard/rename/duplicate/new-tab과 native DnD 저장·notice·DB 재조회를 확인했고, 별도 IAB에서 LOCKED confirm 수락·저장 notice·reload 후 disabled 상태·DB 재조회를 확인했다. 오전 Chrome native DnD 시점에만 zoom 90%(`innerWidth=1600`, `dpr=0.9`)가 관측됐고 IAB에는 viewport override를 설정하지 않았으며 치수도 측정하지 않았으므로 이를 IAB의 computed 1440 수치로 연결하지 않는다. 기존 1440 시각 evidence를 유지한다.
- 최신 FE 동결 패치도 정본과 정적 정합하다. `BlogInitialSetupPage.tsx:321–361`의 시작 칩/점선 `+ 추가`, `SettingsPostsPage.tsx:490–579`의 native DnD payload·행 토큰·DEFAULT/LOCKED inert+disabled 조작, `:621–673`의 카드 밖 `미분류` 안내를 `DESIGN-SYSTEM` 및 ADR-0005/REQUIREMENTS override와 대조해 신규 critical/high finding을 찾지 못했다. 관련 회귀 소스 `BlogInitialSetupPage.test.tsx:267–279`, `categoryBehavior.test.tsx:657–734`도 확인했다.
- parent 최신 FE 참고 결과는 `build/m3-final-frontend-tests.log` 23 files/273 tests pass, exit 0이며 lint·tsc+Vite build도 exit 0으로 보고됐다. QA는 npm을 재실행하지 않았다. BE full 370-test 결론과 LWW closure는 유지한다.
- 2026-09-08 종료 재개 감사에서 `frontend/package.json`·`package-lock.json`·직접 `node_modules` manifest를 읽기 전용 비교해 17개 직접 의존성의 lock/설치 version이 모두 일치함을 확인했다. `vite`는 선언 `^8.1.1`, lock/설치/실행 로그 모두 `8.1.5`다. `npm ls --depth=0`는 exit 0이며 @emnapi 계열·`tslib` 6개 extraneous만 보고했다. install/update/정리는 하지 않았다.
- 검증 JAR SHA-256 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`는 기존 기록과 일치한다. root는 remote PR body의 project-lead 공개 범위 문장을 `포함`으로 정정했다고 보고했으며, QA는 GitHub API 인증 실패로 직접 재확인하지 않았다.
- frontend 작성자와 별도 컨텍스트인 root가 직접 실행·관측한 브라우저 evidence에는 가입→setup→자기 blog slug, 새로고침/session 복원, 자식 생성·ArrowUp 순서 PUT/GET·notice, inline rename·notice, 중복 거부·기존 목록 보존, 새 탭 재조회 및 1440px native screenshot의 단일 카드/들여쓰기/행/타이포/DEFAULT disabled/미분류 안내가 포함된다. 추가 native mouse DnD는 `프론트엔드`를 `백엔드` 위로 옮긴 뒤 정확한 성공 notice와 DB active orders `19=0, 21=1, 20=2, 22=3`을 확인해 PASS로 반영했다. 별도 IAB는 `회고`를 LOCKED로 저장하고 confirm accept·notice·reload/session·4개 조작 disabled·DB blog 7/category 26 `LOCKED order=3`을 확인했다. 이 evidence를 QA 직접 조작으로 표기하지 않는다.

## 다음 작업

1. root가 PR `OPEN/Draft/head=08239e0`를 `dev`에 merge하고 M3 `[머지]` 마감 기록을 남기는지 확인한다.
2. QA 최종 `APPROVE`와 현재 evidence 경계를 보존하며 추가 제품 테스트·브라우저 조작·중복 실행은 하지 않는다.
3. M3 마감 후에도 M4는 시작하지 않는다.

## 차단 요인

- QA acceptance 차단 요인은 없다. backend full build와 관련 MySQL/MockMvc/migration/OpenAPI XML 및 LWW lock-serialized commit, root 독립 브라우저의 signup/setup/self-blog/full-refresh/session·keyboard/rename/duplicate/new-tab·1440px 화면·native mouse-DnD·LOCKED confirm 저장/재조회가 모두 확인됐다. root의 dev merge·M3 마감 기록만 남아 있으며 merge 완료로 오인하지 않는다.
- loopback HTTP smoke 자체는 종료 코드 0이다. fixture 계정이 서버에 남아 있으며 cleanup/delete는 수행하지 않았다. parser/HTTP smoke 성공은 제품 전체 acceptance 통과를 의미하지 않는다.
- numeric enum, owner HTTP, migration, count/move, 101 roots, DEFAULT/LOCKED/subtree, malformed binding, generated OpenAPI는 full XML 통과로 현재 blocking 결함이 아니다.
- LWW callback 순서가 outer commit 전이라는 점만으로는 반례가 되지 않는다. 동일 blog `PESSIMISTIC_WRITE` lock과 Future null(commit 성공) 경계로 현재 acceptance 범위는 해소다.
 - FE F-M3-FE-01~07, completion-intent 및 최신 시각/native DnD 패치는 최신 source/관련 회귀 구조상 정적 closure이고, native mouse-DnD·LOCKED confirm은 root 독립 실측으로 PASS다. FE 273-test/lint/build 결과와 root의 1440px/full-refresh·기능 브라우저 evidence는 독립 검토 참고로 반영했다. 오전 Chrome native DnD에서 관측한 zoom 90% 수치는 IAB에 적용·측정된 값이 아니며 1440 CSS computed PASS로 사용하지 않는다.
- 의존성은 lock↔직접 설치 exact match이며 Vite `8.1.5` mismatch가 아니다. extraneous 6개는 설치 환경 위생 항목으로 기록하고 제품 결함으로 승격하지 않는다. root가 remote PR body의 project-lead 포함 문장을 정정했다고 보고했으나 QA는 직접 API 재확인하지 않았다.
- 계약 승인 상태는 정합하다. PM의 최종 PRD 기록과 backend/frontend 구현·검증 산출물 및 root 독립 browser evidence를 대조 완료해 QA 최종 `APPROVE`를 기록했다.
- QA 소유권 밖인 제품 코드·제품 테스트·governance/worklog 원본은 변경하지 않는다.

## 주요 산출물

- `qa/AGENTS.md`
- `qa/M2-review.md`
- `qa/M3-review.md`
- `.claude/team/qa/WORKLOG.md`
