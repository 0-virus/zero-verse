# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-07 KST (M3 FE 시각·native DnD 최종 패치 독립 재대조)

## 현재 단계

- M2는 PR #8이 `dev`에 머지됐고 QA 최종 판정은 APPROVE(96/100)다. M2 전체 증거를 반복 실행하지 않는다.
- M3는 사용자의 `시작` 지시로 Q1~Q4 계약이 승인된 뒤 실행 준비 단계다. 제품 acceptance PASS/APPROVE는 아직 아니다.
- 현재 기준선은 `feature/M3-categories`의 M3 backend 구현 진행본과 FE 구현 진행본이다. M3 제품 acceptance PASS/APPROVE는 아직 아니며 QA는 구현 파일을 수정하지 않는다.

## 진행 중

- `qa/M3-review.md`에 FR-CAT-01~05/FR-BLOG-02/NFR-04·08·09 및 승인 계약의 독립 acceptance matrix를 작성했다.
- V1→V2 active-key unique, 잠금·subtree 삭제, 전체 order 배열·last-write-wins, 실제 posts 이동/count, 방향성 UNIVERSE, setup 부분 실패 복구, Swagger/보안, 1440px 디자인의 증거 요구를 구체화했다.
- 기존 `MySqlTestSupport`·보안/OpenAPI/Blog 테스트와 FE `apiClient`·초기 설정 테스트 자산을 재사용 대상으로 확인했다.
- 리더 동기화 후 회의록 `APPROVED`, ADR-0005 `ACCEPTED`, 결정 레지스터, REQUIREMENTS §6.4/NFR-04·08, PRD §9.5·§10~§12, M3 worklog의 승인 맥락을 독립 대조했다. 과거 대기 문구는 회의 §10 역사 기록으로 보존되어 있다.
- BE 초안에서 전달된 LOCKED reorder no-op, 삭제 owner GET, trim 전 validation, root `parentId=null`, GET CAT_004 문서 status, DELETE/order OpenAPI 항목을 acceptance 재검증 행으로 추가했다. BE 수정 후 독립 검증 대기다.
- QA 소유 `qa/m3-api-smoke.ps1`를 추가했다. loopback만 허용하고 합성 계정 2개·실제 auth/initial-setup DTO·공통 envelope·401/403·카테고리 CRUD/잠금/순서/삭제 경계를 메모리 내 토큰·비밀번호로 점검하도록 작성했으며, PowerShell `Parser::ParseFile` 문법 검사와 loopback HTTP smoke를 종료 코드 0으로 완료했다.
- 준비된 `http://127.0.0.1:8080` fresh API에서 `qa/m3-api-smoke.ps1`를 실제 실행해 종료 코드 0을 확인했다. 출력에는 fixture prefix `m3sdab71e3d`만 남았고 합성 owner/viewer 계정은 cleanup하지 않았다. HTTP envelope/timestamp·auth/setup DTO·anonymous/owner/non-owner 보안·trim/duplicate·child/tree·GENERAL→LOCKED·DEFAULT/LOCKED 보호·full-ID reorder·subtree delete/recreate·stale CAT_007 assertion이 통과했다. 이 결과는 SQL post 이동/count·101 root·numeric enum·Swagger·FE/1440px 증거가 아니다.
- 최신 backend full build 결과를 직접 읽었다. `build/m3-root-full-build.log:38`은 `BUILD SUCCESSFUL in 10m 24s`이며 parent가 보고한 wrapper exit는 0이다. `build/test-results/test/TEST-*.xml` 56개를 재실행 없이 XML 파싱해 합계 370 tests, failures/errors/skips 모두 0을 확인했다.
- 관련 XML 증거: `CategoryServiceMySqlTest` 14/0은 101 root/page·direct child, full sibling/CAT_007, LOCKED slot/CAT_006, DEFAULT·LOCKED 불변, GENERAL→LOCKED, LOCKED child subtree 보호, live/deleted post의 DEFAULT 이동, owner/draft·direct count·UNIVERSE 방향, create/reorder/delete 경쟁을 포함한다. `CategoryControllerMySqlTest` 6/0은 anonymous/non-owner write, 삭제 owner GET/POST BLOG_001, root null/children, trim 후 100자, malformed enum/string/array/query `VALIDATION_001`, generated OpenAPI status/security를 포함한다. `CategoryMigrationTest` 1/0은 V1 기존행·삭제 history·active_key/name/order scope와 V2 재사용을 확인한다. `GlobalExceptionHandlerTest` 5/0은 JSON/query binding 400을 포함한다. `BlogPublicControllerTest` 8/0도 deleted-owner blog 404를 통과했다.
- 이전 F-M3-ERR-03은 현재 source `JacksonConfig.java:15–18`의 `featuresToEnable(FAIL_ON_NUMBERS_FOR_ENUMS)`와 controller numeric enum case(`CategoryControllerMySqlTest.java:128–175`)가 full XML 6/0으로 통과해 **현재 열린 결함이 아님**으로 정리한다. 삭제 owner HTTP/OpenAPI/공통 오류도 같은 full 결과로 정적·실행 증거가 보강됐다.
- LWW는 `CategoryServiceMySqlTest.xml`의 동시 same-ID reorder가 통과하고 `CategoryService.java:180–261`이 blog `PESSIMISTIC_WRITE` lock·전체 sibling ID 검증·임시/최종 flush를 수행한다. `CategoryServiceMySqlTest.java:607–610`의 callback은 outer `TransactionTemplate` 안에서 실행되지만 같은 blog lock이 outer commit까지 유지되므로, T2 callback은 T1 성공 commit 뒤에만 도달한다. 두 `Future.get()` null assertion이 outer commit 성공을 확인하므로 최신 소스·테스트 경계에서 last-write-wins 증거 공백은 해소로 정정한다.
- 최신 completion-intent 패치까지 실제 source/관련 회귀 소스를 재독해했다. `BlogInitialSetupPage`는 `setupCompletionTo:'blog'` history intent를 먼저 커밋한 뒤 effect에서 `refreshUser`하고, `SetupGuard`는 인증된 `defaultBlog.urlSlug`로 own-blog 목적지를 고정한다. partial management·refresh failure·retry success·remount recovery 및 F05/F06 async drain 회귀를 확인했다. parent의 관련 68 tests와 FE 269/lint/build exit 0은 구현자 참고 결과이며 QA는 npm/Vitest를 재실행하지 않았다. 새 critical 실제 결함은 발견하지 못했다.
- FE 테스트 소스에는 mutation reload 실패·A→B→null→A 전환·지연 create/order response drain·count/동일 viewer 재조회·partial/lost category POST·completion intent own-blog·refresh failure management recovery·remount history recovery가 추가되어 있다. 실제 브라우저 1440px layout/copy/loading/error와 OS 수준 full reload 관찰은 미검증이다.
- 최신 FE 동결 패치도 정본과 정적 정합하다. `BlogInitialSetupPage.tsx:321–361`의 시작 칩/점선 `+ 추가`, `SettingsPostsPage.tsx:490–579`의 native DnD payload·행 토큰·DEFAULT/LOCKED inert+disabled 조작, `:621–673`의 카드 밖 `미분류` 안내를 `DESIGN-SYSTEM` 및 ADR-0005/REQUIREMENTS override와 대조해 신규 critical/high finding을 찾지 못했다. 관련 회귀 소스 `BlogInitialSetupPage.test.tsx:267–279`, `categoryBehavior.test.tsx:657–734`도 확인했다.
- parent 최신 FE 참고 결과는 `build/m3-final-frontend-tests.log` 23 files/273 tests pass, exit 0이며 lint·tsc+Vite build도 exit 0으로 보고됐다. QA는 npm을 재실행하지 않았다. BE full 370-test 결론과 LWW closure는 유지한다.
- frontend 작성자와 별도 컨텍스트인 root가 직접 실행·관측한 브라우저 evidence에는 가입→setup→자기 blog slug, 새로고침/session 복원, 자식 생성·ArrowUp 순서 PUT/GET·notice, inline rename·notice, 중복 거부·기존 목록 보존, 새 탭 재조회 및 1440px native screenshot의 단일 카드/들여쓰기/행/타이포/DEFAULT disabled/미분류 안내가 포함된다. 이 evidence를 QA 직접 실행으로 표기하지 않되 root 독립 검토 근거로 반영한다. CUA mouse DnD는 HTML5 lifecycle을 완료하지 못했고 LOCKED confirm 수락도 timeout/`No dialog is showing`으로 저장되지 않아 수동 DnD와 LOCKED mutation은 미검증이다.

## 다음 작업

1. loopback smoke와 backend full build/XML, LWW closure 및 최신 FE 시각/native DnD 정적 closure를 `qa/M3-review.md` 최신 결론으로 고정한다.
2. 수동 HTML5 mouse-DnD 순서 저장/notice와 LOCKED confirm 수락 후 불변 상태를 실제 브라우저에서 확인하거나, 사용자 확인·도구 한계를 명시한 대체 증거를 검토한다. root가 확인한 1440px 화면·full-refresh/session·키보드/rename/duplicate 경로는 완료 근거로 유지한다.
3. 브라우저 잔여 게이트가 끝날 때까지 M3 최종 PASS/APPROVE 및 dev 머지 완료 주장을 보류하고, 사용자 종료 조건인 M3 마감 기록 뒤 M4는 착수하지 않는다.

## 차단 요인

- backend full build와 관련 MySQL/MockMvc/migration/OpenAPI XML 및 LWW lock-serialized commit 범위는 통과했다. root 독립 브라우저에서 signup/setup/self-blog/full-refresh/session·keyboard/rename/duplicate/new-tab·1440px 화면을 확인했지만, 수동 mouse-DnD lifecycle과 LOCKED confirm 수락 mutation은 미검증이다.
- loopback HTTP smoke 자체는 종료 코드 0이다. fixture 계정이 서버에 남아 있으며 cleanup/delete는 수행하지 않았다. parser/HTTP smoke 성공은 제품 전체 acceptance 통과를 의미하지 않는다.
- numeric enum, owner HTTP, migration, count/move, 101 roots, DEFAULT/LOCKED/subtree, malformed binding, generated OpenAPI는 full XML 통과로 현재 blocking 결함이 아니다.
- LWW callback 순서가 outer commit 전이라는 점만으로는 반례가 되지 않는다. 동일 blog `PESSIMISTIC_WRITE` lock과 Future null(commit 성공) 경계로 현재 acceptance 범위는 해소다.
- FE F-M3-FE-01~07, completion-intent 및 최신 시각/native DnD 패치는 최신 source/관련 회귀 구조상 정적 closure다. FE 273-test/lint/build 결과와 root의 1440px/full-refresh·기능 브라우저 evidence는 독립 검토 참고로 반영했으며, 실제 mouse-DnD·LOCKED confirm만 pending이다.
- 계약 승인 상태는 정합하다. PM의 최종 PRD 기록과 backend/frontend 구현·검증 산출물은 아직 acceptance 전제조건으로 남아 있다.
- QA 소유권 밖인 제품 코드·제품 테스트·governance/worklog 원본은 변경하지 않는다.

## 주요 산출물

- `qa/AGENTS.md`
- `qa/M2-review.md`
- `qa/M3-review.md`
- `.claude/team/qa/WORKLOG.md`
