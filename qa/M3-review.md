# M3 카테고리 독립 QA acceptance 준비

- 기준 시각: 2026-09-08 10:05 KST
- 기준 브랜치: `feature/M3-categories`
- 범위: FR-CAT-01~05, FR-BLOG-02, NFR-04·08·09, PRD §5.4·§7·§9-H/R·§10~§12, 디자인 정본 §8.7, M3 회의 §4~§7, ADR-0005 승인 계약
- 현재 판정: **APPROVE — M3 독립 acceptance 완료; dev merge·마감 대기**

## 현재 산출물 기준

준비 시작 시 제품 코드에는 `Category`/`CategoryType`/기본 카테고리 repository 조회만 있고 카테고리 service·controller·migration V2가 없었다. `SettingsPostsPage`도 `PageScaffold`만 렌더했고 `ErrorCode`는 CAT_001~003까지만 정의되어 있었다. V1의 categories unique는 `name`/`display_order`에 `deleted_at`을 반영하지 않으며 `active_key`도 없다. 따라서 아래 표의 PASS/FAIL을 아직 기록하지 않고, 구현 버전 고정 후 실제 MySQL·MockMvc·브라우저 증거로 판정한다.

## 계약 추적과 검증 증거

| ID | 계약·경계 | 독립 acceptance 증거 | 판정 게이트 |
| --- | --- | --- | --- |
| M3-DB-01 | V1 기존 행 보존 후 V2 forward migration. `active_key`는 활성 행만 1, 삭제 행은 NULL. 이름/순서는 `(blog_id,parent_key,field,active_key)` unique | 기존 카테고리·posts를 넣은 실제 MySQL에서 V2 적용 전후 행/제약/재시작 확인. 기존 migration 수정·삭제 없음 | 실제 MySQL 출력과 migration checksum 필요 |
| M3-DB-02 | 활성 형제만 name/order unique, 삭제 후 이름·순서 재사용. 교환은 중간 unique 충돌 없이 완료 | 삭제→동일 name/order 생성, 두 순서 교환, 동시 생성/정렬/삭제를 DB 제약과 최종 행으로 확인 | nullable `deleted_at` 단순 unique면 FAIL |
| M3-API-01 | GET은 공개, 루트 단위 page + 각 루트의 직속 children. `size` 최대 100, order는 `displayOrder,id` | 101개 이상 루트와 자식 fixture로 page 경계, totalElements=루트 수, 모든 child 포함, 전체 로드 누락 없음 확인 | 일부 page로 reorder 전송하면 FAIL |
| M3-API-02 | 쓰기는 인증·경로 blog·소유권·도메인 규칙 순서. 무인증 `AUTH_004`, blog 없음 `BLOG_001`, 타인 `CAT_004`, 타 blog/deleted category·parent `CAT_001` | 무토큰/타 사용자/타 blog/deleted 대상 MockMvc와 공통 envelope의 code/status/message 확인 | 공개 GET이 쓰기까지 열리면 FAIL |
| M3-API-03 | 삭제된 blog 또는 삭제된 blog owner의 category GET은 `BLOG_001`이며 category 이름/count를 노출하지 않음 | active blog→soft-deleted owner fixture와 deleted blog fixture를 anonymous/owner/non-owner로 조회 | owner soft delete를 확인하지 않고 200이면 FAIL |
| M3-CAT-01 | 생성은 GENERAL/LOCKED만, DEFAULT는 시스템 전용. trim 1~100자, parent는 동일 blog의 활성 root, parent의 parent 금지 | valid root/child, 빈·공백·101자·음수 order·잘못된 enum/null/타 blog parent/깊이 3 요청 | 형식 오류 `VALIDATION_001`, 규칙 오류 CAT 코드 분리 |
| M3-CAT-02 | DEFAULT는 이름·타입 불변, 순서는 허용. LOCKED는 이름·타입·순서·삭제 불변. 동일 값 멱등 수정 허용. GENERAL→LOCKED는 되돌릴 수 없음 | 각 필드별 변경/동일값 PUT, POST DEFAULT, LOCKED PUT/DELETE, 잠금 안내의 저장 전 노출 | 잠금 필드 우회·DEFAULT 전환·`SERIES`/`is_default` 사용이면 FAIL |
| M3-CAT-03 | 이름은 trim 후 길이 1~100을 검증하고, root의 `parentId`는 null을 보존해 응답한다 | 공백을 포함한 유효 100자 이름, root `parentId` 생략/null 생성 및 응답 JSON 키 확인 | trim 전 `@Size` 거부 또는 root `parentId` 키 누락이면 FAIL |
| M3-ORDER-01 | 같은 부모의 살아 있는 모든 ID를 정확히 한 번 포함. 빈/중복/누락/타 부모는 CAT_007. LOCKED 숫자 order 보존 | root/child 각각 전체 배열, 101개 경계, malformed 배열, locked 위치·간격 보존, 임시 음수→flush→최종 순서 trace | 일부 형제만 성공하거나 LOCKED 숫자를 재번호하면 FAIL |
| M3-ORDER-02 | 같은 ID 집합 동시 요청은 직렬 처리 후 last-write-wins. 생성/삭제로 집합이 변한 stale 배열은 CAT_007 | 두 트랜잭션을 같은 blog lock에서 실행하고 최종 순서·응답을 확인. revision/ETag를 구현했다고 주장하지 않음 | 같은 집합을 stale 차단한다고 문서화하면 계약 모순 보고 |
| M3-ORDER-03 | LOCKED를 제외한 ID만 재정렬해 성공시키지 않는다. D0/L1/G2에서 `[L,D,G]`처럼 LOCKED 숫자 위치를 바꾸는 요청은 CAT_006 | locked 포함 전체 배열을 그대로 전송하고 최종 displayOrder·응답 code 확인 | locked ID를 필터링한 뒤 200/no-op이면 FAIL |
| M3-DELETE-01 | GENERAL subtree soft delete. LOCKED 또는 불변 child가 있으면 전체 거부. DEFAULT 직접 삭제 CAT_003 | GENERAL→child→post fixture, LOCKED child fixture, deleted category 재조회. 삭제 행 보존과 active 목록 제외 확인 | 잠금 child를 cascade로 우회하면 FAIL |
| M3-DELETE-02 | 삭제 subtree의 모든 posts(soft-deleted post 포함)를 같은 blog의 활성 DEFAULT로 이동. 집계는 deleted post 제외 | 실제 posts 행을 발행/임시/soft-deleted로 나눠 삭제 전후 category_id를 SQL로 확인. bulk 전 flush/후 context 동기화 확인 | 0 count stub, 삭제 post 누락, 타 blog 이동이면 FAIL |
| M3-COUNT-01 | postCount는 직접 소속 글만. 삭제 post 제외. 비로그인은 PUBLIC만 | 공개 GET을 anonymous로 호출하고 PRIVATE/UNIVERSE/draft/삭제 post의 count 누출 여부를 실제 행으로 확인 | 하위 합산·비공개 수치 노출이면 FAIL |
| M3-COUNT-02 | 비소유 로그인자는 PUBLIC + viewer→owner ACCEPTED UNIVERSE만. 반대 방향·PENDING·BLOCKED는 제외 | 두 사용자 A/B와 방향성 universes를 만들고 A viewer/B owner 및 반대 방향을 각각 조회. count와 items를 대조 | 관계 방향을 뒤집거나 양방향으로 세면 FAIL |
| M3-COUNT-03 | 소유자는 발행 PUBLIC/UNIVERSE/PRIVATE를 보고 `includeDrafts=true`일 때만 자기 draft 추가. 비소유자의 includeDrafts=true는 CAT_004 | owner/non-owner 동일 category를 false/true로 호출하고 draft/private 수를 비교 | 타인 draft/private 존재가 노출되면 FAIL |
| M3-SETUP-01 | initial-setup 성공 뒤 category GET→누락된 시작 root GENERAL만 순차 POST. 새 setup 필드/API 없음 | 첫 POST 성공 후 두 번째 실패, duplicate, 응답 유실, 새로고침 각각에서 GET 재조회·성공분 보존·남은 항목 재시도 확인 | 전체 롤백/성공 위장/initial-setup 재호출이면 FAIL |
| M3-FE-01 | `/settings/posts` 단일 카드·인라인 편집·들여쓰기 tree·기존 DnD/키보드 순서. 전체 root page 로드 전 쓰기 비활성 | Testing Library에서 page 1/2 순차 로드, loading/error/retry, keyboard reorder, disabled DEFAULT/LOCKED controls 확인 | 새 DnD 의존성·상세 2열·부분 목록 reorder면 FAIL |
| M3-FE-02 | 디자인 정본 §8.7 및 데스크톱 `min-width:1440px`: 각진 보더/그림자/카피, 삭제 안내는 “미분류” | 실제 1440px 브라우저 screenshot/DOM computed style 및 `SettingsPostsPage` route smoke | radius/폐기 팔레트/“전체” 이동 카피면 FAIL |
| M3-DOC-01 | 모든 operation의 Swagger 보안·응답·오류 envelope와 CAT_004~007이 실제 동작과 일치 | 새 JAR에서 `/v3/api-docs`와 `/swagger-ui.html` 조회, protected write/public GET security, 201/200/400/401/403/404/409 schema 확인 | 구 JAR·정적 annotation만으로 PASS 금지 |
| M3-DOC-02 | 공개 GET의 비소유 `includeDrafts=true`는 HTTP 403 `CAT_004`; DELETE/order도 operation·security·오류 응답을 문서화 | 생성된 OpenAPI JSON에서 GET 403/CAT_004와 DELETE/order의 200·400·401·403·404 및 bearer 요구를 확인 | GET CAT_004를 400으로 문서화하거나 DELETE/order operation이 빠지면 FAIL |
| M3-ERR-01 | CAT_004~007 상세 메시지와 malformed enum/body·형식 오류의 `VALIDATION_001` 공통 envelope | ErrorCode/GlobalExceptionHandler 정적 경로와 invalid enum·JSON·query binding MockMvc를 대조 | CAT_004 메시지 불일치 또는 형식 오류가 `COMMON_500`/기본 HTML이면 FAIL |
| M3-M4-01 | M4의 모든 `category_id` 쓰기도 같은 blog lock·활성/동일 blog 재검증. M3가 Post/Universe CRUD를 앞당기지 않음 | M4 인계 문서/코드 경로를 검색하고 삭제 이동 직후 category write 경쟁을 확인. M3 review에서는 의존성 증거만 기록 | M3에 Post CRUD stub 또는 lock 우회가 있으면 FAIL |

## 오류 계약 확인표

`CAT_001` category/parent 소속·삭제 없음, `CAT_002` 깊이 제한, `CAT_003` DEFAULT 삭제 불가, `CAT_004` 타 사용자 쓰기·비소유 draft 옵션, `CAT_005` 활성 같은 부모 이름 중복(409), `CAT_006` LOCKED의 숫자 순서·이름·타입·삭제 및 DEFAULT 불변 필드 변경, DEFAULT 생성, 잠금 subtree 삭제(400), `CAT_007` displayOrder 중복 또는 전체 형제 ID 배열의 빈 배열·중복·누락·타 부모 등 부적합(400), `VALIDATION_001` 형식·필수·enum 오류를 구분한다. 모든 응답은 기존 공통 `success/data/error/timestamp` 래퍼다.

## 기존 검증 자산 재사용

- BE: `MySqlTestSupport`, `SecurityAccessControlTest`, `OpenApiConfigTest`, `BlogPublicControllerTest`, repository/service/controller 테스트 패턴을 재사용한다. M2 전체 suite는 반복하지 않고 M3 대상 test class와 실제 MySQL migration/acceptance만 실행한다.
- FE: `apiClient.test.ts`, `features/blog/blogApi.test.ts`, `BlogInitialSetupPage.test.tsx`, Testing Library setup을 재사용한다. 카테고리 API client·페이지 테스트는 구현 역할이 소유하고 QA는 결과와 실제 화면을 독립 대조한다.
- 실제 동작: 구현 완료 후 fresh DB와 기존 데이터가 있는 DB를 분리해 V2, owner/non-owner/anonymous, setup 부분 실패, 1440px 브라우저, 새 JAR Swagger를 실행한다. 명령·종료 코드·핵심 출력이 없는 항목은 미검증으로 남긴다.

## 현재 게이트

- [ ] backend category CRUD/order/count/delete 및 V2 migration 구현
- [ ] frontend category API/단일 리스트/setup 칩 복구 구현
- [ ] 실제 MySQL V1→V2·동시성·posts 이동 검증
- [ ] 공개/비소유/소유자 count와 방향성 UNIVERSE 검증
- [ ] setup 응답 유실·부분 실패·새로고침 복구 검증
- [ ] Swagger/보안/오류 envelope 및 1440px 디자인 검증
- [ ] 구현자와 다른 컨텍스트의 QA acceptance + 리더 최종 확인

현재는 위 항목을 실행하지 않았고 M3 PASS/APPROVE를 주장하지 않는다. 구현 산출물과 승인된 정본/ADR의 SHA가 고정된 뒤 이 문서의 각 행에 실제 명령·종료 코드·증거 경로를 append한다.

## 구현 중 관찰된 재검증 항목

리더가 BE 초안에서 전달한 아래 후보를 acceptance 행으로 승격했다. 모두 **수정 후 독립 재검증 대기**이며, 구현 중인 소스에 최종 FAIL/APPROVE를 부여하지 않는다.

- `CategoryService.reorder`: LOCKED를 `unlockedRequested`에서 제외한 뒤 남은 ID만 재배정하는 경로가 있어, D0/L1/G2에서 `[L,D,G]`를 거부하지 않고 no-op으로 끝낼 수 있다. M3-ORDER-03에서 CAT_006과 숫자 순서를 확인한다.
- `CategoryService.findBlog`/GET 경로: 삭제된 blog는 제외하지만 삭제된 소유자까지 확인하는지 별도 fixture가 필요하다. M3-API-03에서 BLOG_001과 정보 비노출을 확인한다.
- `CategoryDtos`의 `@Size(max=100)`와 service trim 순서: 양끝 공백을 포함한 trim 후 100자 이름이 Bean Validation 단계에서 먼저 거부되지 않는지 확인한다. M3-CAT-03에 추가했다.
- `CategoryResponse` root `parentId`: root 응답에서 null 필드가 JSON에 보존되는지 확인한다. M3-CAT-03에 추가했다.
- `CategoryController.getCategories` OpenAPI 초안: 비소유 `includeDrafts=true`의 CAT_004가 400으로 기재된 후보를 확인한다. 계약은 HTTP 403이므로 M3-DOC-02에서 생성된 `/v3/api-docs`를 기준으로 재검증한다.
- DELETE/order OpenAPI operation·응답 문서는 현재 소스 재독에서 annotation이 존재하지만, 최종 JAR 문서에서 실제 operation과 200/400/401/403/404 schema가 모두 노출되는지 M3-DOC-02로 확인한다.

## 2026-09-06 backend 정적 대조 — 신규 finding 및 증거 공백

아래는 구현 중인 현재 소스를 승인 정본과 직접 대조한 결과다. 제품 파일·제품 테스트는 수정하지 않았고, HTTP/Gradle/테스트 실행 없이 소스 경로만 판정했다. 기존 root 전달 후보와 겹치는 항목은 “현재 소스에서 수정 흔적 확인·runtime 대기”로만 남겼다.

- **F-M3-ERR-01 · CAT_004 상세 메시지 불일치 (수정 전까지 blocking)**: `src/main/java/com/zeroverse/common/exception/ErrorCode.java:56`은 `"카테고리 관리 권한이 없습니다."`를 반환한다. 승인 정본은 REQUIREMENTS `:937`, PRD `:238`, 회의록 `M3-20260906-categories.md:102`의 고정 문구 `"카테고리에 대한 권한이 없습니다."`다. `BusinessException` 응답은 이 enum message를 그대로 쓰므로 현재 비소유 write/includeDrafts 403의 code/status는 맞아도 message 계약은 틀린다. 현재 category controller 테스트 `CategoryControllerMySqlTest.java:66–94`는 CAT_004 code/status만 확인해 이 차이를 잡지 못한다.
- **F-M3-ERR-02 · JSON enum/body/query 형식 오류가 VALIDATION_001 경계를 벗어남 (수정 전까지 blocking)**: M3 정본 REQUIREMENTS `:500`, M3-ERR-01은 알 수 없는 enum·형식 오류를 HTTP 400 `VALIDATION_001`로 요구한다. 그러나 `GlobalExceptionHandler.java:34–44`는 `MethodArgumentNotValidException`만 `VALIDATION_001`로 처리하고, `:59–65`의 포괄 `Exception`은 `COMMON_500`으로 응답한다. 따라서 `POST /api/v1/blogs/{blogId}/categories`에 `{"name":"x","type":"SERIES","displayOrder":1}` 또는 숫자 대신 문자열을 보내는 Jackson `HttpMessageNotReadableException`, `PUT .../order`에 숫자가 아닌 배열 원소를 보내는 body binding, `GET .../categories?size=abc`의 type binding은 현재 정적 경로상 `COMMON_500`(또는 공통 envelope를 보장하지 않는 framework 기본 경로)이며 `VALIDATION_001`이 될 수 없다. `CategoryController.java:96–99,186–189`의 요청 경계와 invalid enum/format MockMvc 증거가 필요하다.

### 현재 소스에서 확인했으나 runtime 재검증이 필요한 항목

- `CategoryService.java:212–219`는 LOCKED의 요청 slot 숫자 비교를 `unlockedRequested` 필터보다 먼저 수행하고, `BlogRepository.java:25–34`의 두 query는 blog 및 owner `deletedAt IS NULL`을 포함한다. `CategoryDtos.java:21–40,45`의 compact-constructor trim과 `CategoryResponse` `ALWAYS`, `CategoryController.java:51–56`의 GET CAT_004=403 문서도 확인했다. 기존 root 후보는 현재 정적 소스상 수정된 것으로 보지만, 실제 MySQL/MockMvc/JAR 출력 없이는 PASS로 승격하지 않는다.
- `CategoryService.java:93,116,153,180`에서 모든 category write가 `findByIdAndDeletedAtIsNullForUpdate`를 먼저 호출하고, 이후 parent/category/active sibling을 읽는다. `CategoryRepository.java:75–126`의 실제 post/count SQL과 `CategoryService.java:168–176`의 flush→bulk move→clear 흐름은 계약 방향과 일치해 보이나, lock 경쟁·soft-deleted post 이동·관계 방향 count는 실행 증거가 없다.

### 추가 테스트 증거 공백

- `CategoryMigrationTest.java:28–93`은 현재 Flyway가 만든 fresh schema의 active_key/index와 재사용을 확인하지만, V1 기존 rows를 넣은 뒤 V2를 forward 적용해 보존하는 시나리오는 없다(M3-DB-01 미검증).
- `CategoryServiceMySqlTest.java:98–117`은 한 locked slot 변경과 `:119–142`의 post 이동, `:72–96`의 한 방향 관계 count를 확인한다. 같은 ID 집합의 동시 last-write-wins, owner soft-delete의 GET/쓰기 BLOG_001, duplicate/missing/cross-parent order CAT_007, malformed enum/body/query 형식 오류와 CAT_004 고정 message는 추가 증거가 없다.
- `CategoryControllerMySqlTest.java:96–110`은 trim 100자 경계를 보강했지만, invalid enum/JSON binding과 공통 error message는 아직 검증하지 않는다. 테스트·Gradle·HTTP 실행은 이 review에서 의도적으로 하지 않았다.

## 정본 정합성 대조

리더 동기화 후 다음을 독립 재독했다. `docs/governance/meetings/M3-20260906-categories.md` 상단/§7은 `APPROVED`, §10은 구체 Q1~Q4 안내 뒤 사용자의 원문 **"시작"**을 승인 근거로 기록한다. `docs/governance/decisions/ADR-0005-categories-contract.md`는 `ACCEPTED`이며 결정 주체를 사용자로 적고, `DECISION-REGISTER.md`도 회의 `APPROVED`와 ADR `ACCEPTED`를 연결한다. `docs/REQUIREMENTS.md` §6.4/NFR-04·08, `docs/PRD.md` §9.5·§10~§12, `docs/worklog/M3-categories.md`의 착수 기록이 같은 계약을 가리킨다. 과거 `USER_DECISION_REQUIRED` 문구는 회의 §10에 역사 기록으로 보존되어 있고 현재 승인 상태로 오인하지 않는다.

이 대조는 계약 승인 상태만 확인한 것이며 제품 DoD나 acceptance PASS를 의미하지 않는다. PM의 최종 PRD 기록과 backend/frontend 구현 산출물이 확정된 뒤 아래 게이트를 실행한다.

문서 위생 후속은 해소 확인했다. 회의록 상단에 승인된 ADR-0005 링크가 추가됐고 §9에는 최초 준비 문장을 역사 기록으로 한정하는 갱신이 append됐다. §7·§10, ADR-0005, 결정 레지스터, REQUIREMENTS/PRD의 현재 승인 상태와 일치한다.

## 미검증 범위

제품 코드 변경, M3 테스트 실행, V2 migration 적용, 실제 MySQL 경쟁, 실제 게시글 이동/count, 공개 API 권한, setup 부분 실패 복구, Swagger, 1440px 브라우저는 모두 미검증이다. 이 문서는 실행 계획과 판정 기준이며 완료 판정이나 구현자의 자체 테스트를 대체하지 않는다.

## 2026-09-06 최신 backend 재대조 — 정적 해소·잔여 blocker·증거 강도

- 이 항목은 backend 구현 중 최신 소스와 테스트를 승인된 ADR-0005/REQUIREMENTS §6.4·NFR-04·08에 다시 대조한 기록이다. 제품 소스·제품 테스트는 수정하지 않았고, 이 컨텍스트에서는 Gradle/HTTP를 실행하지 않았다. M3 전체 PASS/APPROVE는 아직 아니다.
- 기존 `F-M3-ERR-01`은 현재 정적 소스에서 해소 흔적을 확인했다. `ErrorCode.java:56`의 CAT_004가 정본 고정 문구 `카테고리에 대한 권한이 없습니다.`와 일치한다. 실제 비소유 mutation/includeDrafts 403의 envelope·message는 MockMvc/HTTP 재검증 전까지 미검증이다.
- 기존 `F-M3-ERR-02`도 현재 정적 경로는 보강됐다. `GlobalExceptionHandler.java:49–58`이 `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `BindException`을 `VALIDATION_001` 400으로 묶고, 별도 `MethodArgumentNotValidException` 경로는 field details를 보존한다. 마지막 targeted 결과 XML에는 수정 전 고정문구 assertion 1건 실패가 남아 있고 현재 테스트 소스는 수정됐으므로, 새 실행이 끝나기 전 PASS로 올리지 않는다. `HandlerMethodValidationException`은 현재 category controller에 해당 method constraint가 없어 이번 경계에서 별도 처리됐다고 주장하지 않는다.
- **F-M3-ERR-03 · 숫자 enum 입력 coercion이 여전히 blocking 후보**: 승인 계약은 `type`의 숫자/알 수 없는 enum을 `VALIDATION_001`로 거부한다. 그러나 새 `JacksonConfig.java:13–17`은 `ALLOW_COERCION_OF_SCALARS`와 `ACCEPT_FLOAT_AS_INT`만 끄고 `DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS`를 설정하지 않는다. Jackson의 enum 숫자 ordinal 경로를 이 설정만으로 차단한다고 볼 근거가 없으므로 `type:1`이 `GENERAL`로 수용될 위험이 있다. `CategoryControllerMySqlTest.java:121–126`의 회귀 케이스는 마지막 targeted 실행 뒤 추가되어 아직 미실행이다. backend가 숫자 enum 거부 설정(또는 동등한 coercion 정책)을 반영한 뒤 해당 케이스와 공통 400 회귀를 재실행해야 한다.

### 현재 소스와 테스트 증거의 독립 대조

- Q1 migration: `CategoryMigrationTest.java:42–60`은 V1 target에서 기존 active/deleted rows를 만든 뒤 V2를 forward 적용하고, `:62–99`에서 ID·parent·deleted_at·active_key 보존, active 중복 거부, 삭제 후 name/order 재사용을 확인한다. 이전의 “fresh schema만 확인” 공백은 소스상 보강됐지만 QA가 실행한 증거는 아니며, parent가 전달한 targeted XML 1/1 결과만 참고 상태다.
- Q2 order/lock: `CategoryService.java:180–205`의 전체 active ID·동일 sibling 집합 검증, `:212–219`의 LOCKED 숫자 slot 검증, `:241–261`의 임시 음수→flush→최종 flush가 계약과 맞는다. `CategoryServiceMySqlTest.java:152–237`의 두 barrier/thread 경쟁 테스트는 실제 Spring transaction 호출 두 개를 겹치고 최종 행을 확인하므로 **중간 이상** 증거다. 다만 create 경쟁·락 대기 여부·commit 순서 자체를 계측하지 않고, same-ID 결과를 `onSuccess` 시점에 기록하므로 last-write-wins의 독립적인 전 범위 증명으로 승격하지 않는다.
- Q3 count/move: `CategoryRepository.java:75–126`은 deleted 제외·published 조건·PUBLIC/UNIVERSE 방향성·owner draft 분기를 실제 SQL로 갖고, `CategoryService.java:168–176`은 subtree soft delete 후 동일 blog의 모든 posts(soft-deleted 포함)를 DEFAULT로 bulk 이동하고 context를 clear한다. 서비스 테스트는 live+deleted post 이동(`CategoryServiceMySqlTest.java:240–262`), owner published/draft·private count(`:50–77`), viewer→owner와 역방향 Universe(`:79–103`)를 확인한다. anonymous/PENDING/BLOCKED HTTP count 및 실제 SQL 실행은 별도 acceptance gate다.
- root/page: `CategoryServiceMySqlTest.java:264–292`는 DEFAULT 포함 101 roots, page 1/2 경계와 Root 1/100의 direct child를 확인한다. 구현의 `findActiveRoots`/`findActiveChildren` 정렬은 정적 계약과 맞지만, 모든 root의 child 순서·전체 페이지를 FE가 읽은 뒤 쓰기를 여는 것은 아직 별도 검증이다.
- DEFAULT/LOCKED/subtree: `CategoryService.java:121–140,155–166`은 DEFAULT 이름/타입 변경·LOCKED 이름/타입/숫자순서·DEFAULT 전환·불변 child를 차단하고 같은 값 PUT은 통과시키는 구조다. 현재 제품 테스트에는 각 필드별 PUT, DEFAULT/LOCKED DELETE, GENERAL→LOCKED 비가역성, LOCKED child가 있는 root subtree 거부를 모두 직접 확인하는 케이스가 없어 **증거 공백**으로 남긴다. `:240–262`의 GENERAL child 삭제와 post 이동만 확인한다.
- owner soft-delete: `BlogRepository.java:22–29`의 GET/lock query 모두 blog와 user `deletedAt IS NULL`을 확인하므로 정적 경로는 맞다. 현재 `CategoryServiceMySqlTest.java:315–335`는 direct service GET/create만 BLOG_001을 확인하고, controller의 anonymous/owner/non-owner HTTP GET 정보 비노출 케이스가 없어 M3-API-03은 미검증이다.

### 실행 산출물 및 wrapper 경계

- parent가 전달한 최신 targeted run은 `CategoryServiceMySqlTest` 10/10, `CategoryControllerMySqlTest` 5/5, `CategoryMigrationTest` 1/1 통과 보고다. QA는 이 테스트를 재실행하지 않았고, 해당 XML은 구현자 실행 참고 증거일 뿐 독립 acceptance PASS가 아니다.
- `GlobalExceptionHandlerTest` XML에는 source test message 교정 전의 5건 중 1건 assertion 실패가 남아 있다. 현재 `GlobalExceptionHandlerTest.java:123`은 `ErrorCode.VALIDATION_001` 문구와 정합하므로 교정 후 재실행 결과를 기다린다. 숫자 enum 케이스도 마지막 실행에 포함되지 않았다.
- `gradlew.bat:33`의 초기 `ERROR_CODE=1`과 `:79`의 `%ERRORLEVEL%` 캡처는 현재 wrapper diff에서 확인했다. parent의 invalid `JAVA_HOME` 직접 확인은 exit 1을 반환했고, wrapper 원본의 실패 exit 0 회귀를 고친 변경은 현재 정적으로 타당하다. 전체 test/build/bootJar의 종료 코드는 별도 gate다.

현 시점 판정은 **정적 계약 대조 + 일부 구현자 targeted 결과 참고, QA acceptance 미완료**다. F-M3-ERR-03 숫자 enum 거부와 HandlerException 회귀 재실행을 우선 blocker로 보고, 이후 V2/동시성/SQL count·move/owner HTTP/불변 subtree/FE·Swagger·1440px을 독립 gate로 남긴다.

## 2026-09-06 FE M3 정적 대조 — 구현 중 후보와 증거 공백

이번 대조는 `frontend/src/features/category/categoryApi.ts`, `SettingsPostsPage.tsx`, `ScreenPanel.tsx`, `BlogInitialSetupPage.tsx` 및 관련 FE 테스트를 ADR-0005 Q2~Q4, REQUIREMENTS FR-CAT-01~05·FR-SETTINGS-04, PRD §7·§9.5, `DESIGN-SYSTEM.md` §6.2·§8.2·§8.6~§8.7과 읽기 전용으로 비교한 것이다. 제품 소스·제품 테스트는 수정하지 않았고 npm/Vitest/브라우저는 실행하지 않았다. 아래는 구현 중 소스에 대한 수정 전 후보이며 M3 최종 FAIL/APPROVE가 아니다.

- **F-M3-FE-01 · blog 전환 중 오래된 category load/mutation이 현재 blog 상태를 덮을 수 있음 (수정 전 blocking 후보)**: `frontend/src/pages/SettingsPostsPage.tsx:103–125`의 `loadGeneration`은 현재 callback이 시작한 GET만 세대 비교하지만, `blogId == null` 경로 `:104–107`에서는 세대를 증가시키지 않는다. 더 중요하게 각 mutation은 렌더 시점의 `blogId`와 `loadCategories`를 캡처해 `:163–165`, `:224–229`, `:253–258`, `:275–280`, `:313–317`에서 재사용한다. 재현 흐름은 (1) blog A에서 reorder/create/rename/delete/type PUT을 지연시킨다, (2) auth refresh/signout 또는 default blog 변경으로 blog B가 렌더되고 새 GET이 세대 B를 시작한다, (3) A mutation 응답이 돌아와 오래된 `loadCategories`가 A를 다시 요청하면 공유 ref 세대를 A 요청으로 탈취한다, (4) A 응답이 `:116–123`의 동일 generation 조건을 통과해 현재 화면에 반영된다. 이때 B GET은 폐기되고, 현재 `blogId` B로 A ID를 재전송할 수 있으며 오래된 성공 notice/error와 `isMutating`도 현재 blog에 적용된다. signout처럼 `blogId`가 null이 되는 경우에도 null 경로가 세대를 무효화하지 않아 이미 진행 중인 A GET이 `isLoaded=true`를 되살릴 수 있다. 모든 load/mutation continuation을 현재 blog 식별자와 operation generation에 묶고, blogId 변경·null 전환 시 이전 세대를 먼저 무효화하는 독립 회귀가 필요하다.
- **F-M3-FE-02 · mutation 후 재조회 실패를 성공으로 확정하고 stale notice를 남김 (수정 전 high 후보)**: `loadCategories`는 `:119–124`에서 GET 오류를 state에 기록하고 정상 resolve한다. 따라서 reorder/rename/type/delete/create의 `await loadCategories()` 뒤 `:165`, `:226`, `:255`, `:277`, `:314`의 성공 `setNotice`가 실행된다. 재현은 전체 로드 후 mutation PUT/POST를 성공시킨 다음 후속 page GET을 실패시키는 것이다. 첫 화면에서는 `isLoaded=false`라 notice가 숨겨지지만, `:452` 재시도 GET이 성공하면 `loadCategories`가 notice를 지우지 않으므로 실제 mutation 확인 없이 이전 성공 문구가 다시 노출된다. 후속 GET 실패를 throw/명시적 미확인 상태로 전달하고 reload에서 stale notice를 초기화하는 처리가 필요하다. 현재 테스트는 초기 두 page 로드와 mutation 성공/CAT_007만 다루며 “mutation 성공→reload 실패→retry”를 다루지 않는다.
- **F-M3-FE-03 · Q4 부분 category 실패 시 SetupGuard가 category management 경로를 막음 (수정 전 high 후보)**: `frontend/src/pages/BlogInitialSetupPage.tsx:148–155`는 initial-setup 성공 후 category GET/POST가 일부 실패하면 `completedBlog.current`는 보존하지만 `refreshUser()`를 호출하지 않고 setup 페이지에 남긴다. 세션의 `user.defaultBlog.isSetupCompleted`는 여전히 false이므로 사용자가 안내 문구(`:151–153`)의 `/settings/posts`로 이동하면 `frontend/src/routes/guards.tsx:73–81`의 SetupGuard가 다시 `/blog/setup`으로 보낸다. 새로고침하면 서버 상태를 다시 읽어 완료 플래그는 true가 되지만, 같은 guard의 `:76–77`은 `/`로만 보내며 category management로 이어지는 안내/이동은 없다. Q4가 요구하는 “완료 상태 유지 및 카테고리 관리에서 이어 추가” 경로를 충족하려면 초기 설정 성공 이후 부분 실패 시 세션/라우팅 상태와 관리 경로를 명시적으로 연결해야 한다. `ensureStartCategories(:43–75)`의 GET→누락 root GENERAL 순차 POST·POST 실패 후 GET 재확인은 계약 방향과 일치하며, 결함은 부분 실패 이후 guard/refresh 경계다.
- **F-M3-FE-04 · 공개 패널의 blog 전환 중 통계가 이전 blog count를 잠시 표시함 (non-blocking 후보)**: `frontend/src/components/layout/ScreenPanel.tsx:80–94`는 blog 변경 시 새 GET이 끝날 때까지 `categories`를 비우지 않고, 카테고리 목록만 `isLoading`으로 숨긴다. 통계는 `:101–107`, `:164–183`에서 `blog`만 있으면 이전 categories의 `totalCount`를 표시한다. blog A→B에서 B category GET을 지연시키면 B 제목/히어로 아래에 A의 전체 글 수가 노출된다. `:69–99`의 `cancelled` guard는 늦은 A 응답이 B를 덮지 못하게 하므로 late response 폐기는 정합하지만, 새 blog 요청 시작 시 표시 count를 비우거나 loading으로 감추는 회귀가 필요하다.

### FE 정적 확인·증거 공백

- `categoryApi.ts:58–68`은 `size=100`으로 첫 root page의 `totalPages`를 순차 순회하고, `SettingsPostsPage.tsx:145–165`는 전체 sibling 길이/set과 `sameLockedSlots(:58–62)`를 확인한 뒤 full ID 배열을 보낸다. 현재 `categoryBehavior.test.tsx:205–221,272–306`도 2 page 완료 전 추가 비활성, `[DEFAULT,GENERAL,LOCKED]`의 full array 및 LOCKED numeric slot 보존 흐름을 확인한다. 이 부분에서 BE 초안의 LOCKED ID 필터링 no-op 결함을 FE 소스에서는 재현하지 않았다.
- `ScreenPanel.tsx:69–99`는 blog 변경 cleanup/cancelled flag로 늦은 성공·실패 응답을 폐기하고, `:82`는 `includeDrafts=false`, `:101–107`은 서버의 direct `postCount` 합산만 사용한다. 카테고리 `0` count나 fake item은 확인되지 않았다. `이번 달`·`나를 발견한 별` em dash는 M3 category API가 제공하지 않는 별도 통계이며 M3 count PASS로 해석하지 않고 브라우저/후속 범위로 남긴다.
- `BlogInitialSetupPage.tsx:117–143`은 같은 mount에서 `completedBlog`가 있으면 initial-setup을 다시 호출하지 않고, `:43–75`는 partial/lost category POST를 GET으로 확인해 성공분을 보존한다. 구현자 테스트 `BlogInitialSetupPage.test.tsx:184–212`는 category partial/lost POST를 검증하지만 initial-setup 응답 유실 자체, 부분 실패 뒤 새로고침/SetupGuard, mutation 후 reload 실패, auth/defaultBlog 전환을 검증하지 않는다.
- `SettingsPostsPage`의 CAT_001 mapping `:39–40`은 이름 검증 오류가 아니라 “카테고리를 찾을 수 없음”으로 표시해 승인 오류 의미와 맞는다. CAT_005/006/007 및 삭제 이동 카피 `:439–441`도 현재 정본 문구 방향과 맞는다. 1440px 실제 computed style/screenshot은 브라우저 연결 전이라 미검증이다.

### FE 다음 review gate

1. 구현 역할이 F-M3-FE-01~03의 현재 blog/operation 세대, reload 실패 표시, setup 부분 실패 후 관리 경로를 보완한 뒤 static re-review한다.
2. 구현자 FE 테스트와 별도로 auth/defaultBlog 전환 중 늦은 A 응답, mutation 성공 후 page GET 실패·retry, setup refresh/guard, 101 root page full-load write lock을 실행한다.
3. 이후 실제 1440px 브라우저에서 `/blog/setup`, `/settings/posts`, `/blog/:slug`의 layout/copy와 loading/error 상태를 확인한다. FE 정적 대조만으로 M3 PASS/APPROVE를 부여하지 않는다.

## 2026-09-06 최신 BE 변경 정적 재대조 — enum/LWW/삭제 owner HTTP/OpenAPI

- 범위: 최신 backend 소스와 추가 테스트를 ADR-0005/REQUIREMENTS §6.4·NFR-04·08 및 M3 API 계약에 다시 대조했다. 이 검토에서는 제품 파일·테스트를 수정하지 않았고 Gradle, HTTP, JAR을 실행하지 않았다.
- **F-M3-ERR-03 유지(blocking)**: 이전 스냅샷의 “미설정” 관찰 이후 현재 `src/main/java/com/zeroverse/config/JacksonConfig.java:15-18`은 `DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS`를 `featuresToDisable(...)`에 넣도록 시도했지만, 숫자 enum 차단 극성이 계약과 반대다. 따라서 `type:1` 입력을 현재 소스만으로 HTTP 400/`VALIDATION_001`로 보장할 수 없다. `CategoryControllerMySqlTest.java:121-126`의 기대는 실행 증거가 없고, `featuresToEnable` 또는 동등한 명시 설정과 재실행이 필요하다. 이 항목은 해소로 판정하지 않는다.
- **F-M3-ORDER-02 증거 강도 부족(제품 FAIL 판정 아님)**: `src/test/java/com/zeroverse/domain/category/CategoryServiceMySqlTest.java:357,363-385,582-610`의 LWW 검증은 서비스 호출이 반환된 뒤 callback에서 `lastCompletedOrder`를 기록한다. `@Transactional` 프록시의 commit 순서와 callback 실행 순서 사이에 스케줄링 간격이 있으므로, 먼저 commit한 스레드가 callback 전에 중단되면 callback 순서가 commit/락 획득 순서를 뒤집을 수 있다. 현재 테스트는 “마지막 callback”을 최종 DB 순서의 기준으로 삼아 실제 commit 순서를 독립적으로 증명하지 못하고, 올바른 구현에서도 가짜 실패 가능성이 있다. commit 순서를 관찰하거나 트랜잭션 경계를 통제한 재현 증거가 추가될 때까지 M3-ORDER-02는 미검증으로 유지한다.
- **삭제 owner HTTP 미검증**: `CategoryServiceMySqlTest.java:512-533`은 owner soft-delete 후 service 직접 호출에서 `BLOG_001`을 확인하지만 MockMvc/public envelope를 거치지 않는다. `BlogRepository.java:22-29`의 blog 및 owner `deletedAt IS NULL` 조회 조건은 정적 방향이 맞아 보이나, 삭제 owner의 anonymous/authenticated GET이 `BLOG_001`을 반환하고 category 데이터/count를 노출하지 않는지는 미검증이다. M3-API-03을 닫으려면 최소 anonymous와 authenticated owner/non-owner HTTP 사례를 backend 실행 결과로 확인해야 한다.
- **Swagger GET 정적 방향은 계약과 일치**: `CategoryController.java:45-46`의 `@SecurityRequirements`는 public GET의 명시적 빈 security 배열을 선언하고, write operation은 `:76,107,140,169`에서 `bearerAuth`를 명시한다. `CategoryControllerMySqlTest.java:180-208`은 GET `security=[]`, GET 403/CAT_004 및 DELETE/order 응답·보안을 검사하도록 작성되어 있다. 다만 생성된 `/v3/api-docs`/최종 JAR을 이번 검토에서 실행하지 않았으므로 M3-DOC-02 runtime PASS는 보류한다.
- 실제 명령/종료: `rg`로 Jackson 설정·서비스 transaction 경계·controller annotation·관련 테스트를 읽기 전용 대조했다(명령 자체 exit 0; Gradle/HTTP 미실행). 다음 gate는 Jackson polarity 수정 후 malformed numeric enum 400 실측, LWW commit-order 증거 보강, deleted-owner MockMvc, 생성 OpenAPI와 전체 backend 실행이다.

## 2026-09-06 FE 제한 수정본 정적 재대조 — F-M3-FE-01~04 후속

- 범위: 최신 `SettingsPostsPage`, `ScreenPanel`, `BlogInitialSetupPage`, `SetupGuard`, `SideNav`와 추가 FE 회귀 테스트를 ADR-0005 Q2~Q4, REQUIREMENTS FR-CAT-01~05·FR-SETTINGS-04, PRD §7·§9.5 및 `DESIGN-SYSTEM.md`와 읽기 전용으로 다시 대조했다. 제품 파일·제품 테스트는 수정하지 않았고 npm/Vitest/lint/build·브라우저는 실행하지 않았다. parent가 전달한 FE 자체 결과 `266 tests/lint/build exit 0`은 참고 결과일 뿐 QA 독립 PASS가 아니다.
- **기존 F-M3-FE-01~04는 최신 정적 소스에서 주 경로가 보완된 것으로 확인**했다. `SettingsPostsPage.tsx:102–115,137–155`는 blog/null 전환 때 operation/load generation을 먼저 무효화하고 늦은 load/mutation continuation을 blog·generation과 함께 폐기한다. `:193–224,278–318,322–363,366–400,404–453`의 mutation은 재조회가 성공하고 현재 operation일 때만 성공 notice를 표시하며, 설정 화면은 `isLoaded`가 되기 전 reorder/create UI를 비활성화한다. `categoryBehavior.test.tsx:220–247,250–333`은 reload 실패와 A→B→null→A 늦은 mutation을, `:335–373,375+`는 count·viewer 전환을 다룬다. 따라서 과거 후보를 현재 제품 FAIL로 유지하지 않지만, 독립 실행 전 구현자 테스트 통과를 QA acceptance PASS로 승격하지 않는다.
- **F-M3-FE-05 · stale create가 현재 blog의 입력을 지울 수 있는 잔여 후보(비차단, runtime 미재현)**: `SettingsPostsPage.tsx:432–441`에서 A blog의 create가 응답한 뒤 `setNewName('')`(`:439`)을 `isCurrentOperation` 검사(`:440`)보다 먼저 실행한다. A create가 pending인 동안 B로 전환하고 사용자가 B의 새 이름을 입력하면, A 응답이 B 화면의 입력을 먼저 비울 수 있다. 이후 mutation 결과 자체는 `:440`에서 폐기되지만 사용자 입력 손실은 방지하지 못한다. `setNewName`을 현재 operation 확인 뒤로 이동하거나 blog별 draft를 분리할 필요가 있다. 현재는 구현자/브라우저 재현 전 후보로 기록하며 M3 blocking으로 승격하지 않는다.
- **F-M3-FE-06 · blog 전환 직후 passive effect 전 stale state window(증거 공백, runtime 미재현)**: `SettingsPostsPage.tsx:106–112`에서 ref/generation은 렌더 중 즉시 갱신되지만 categories·`isLoaded` 초기화는 `useEffect` `:159–169`에서 수행된다. commit과 effect 사이에 사용자가 이벤트를 발생시키면 B blog 렌더가 A의 `categories`/`isLoaded=true`를 잠시 보유하고, 특히 `changeType`·`handleDelete`(`:322–375`)는 초기 `!isLoaded` guard가 없어 B ID로 오래된 A category mutation을 시작할 정적 가능성이 남는다. 현재 operation guard가 늦은 응답을 폐기하는 것은 확인했으나 이 짧은 전환 경계는 테스트되지 않았다. `loadedBlogId`를 state로 함께 확인하거나 전환 렌더에서 쓰기 가능 상태를 동기적으로 무효화하는 보강 후 별도 runtime 회귀가 필요하다. 제품 FAIL 단정은 보류한다.
- **F-M3-FE-07 · Q4 전체 새로고침 뒤 관리 경로 안내 잔여 후보(비차단, runtime 미검증)**: 같은 mount의 부분 실패는 `BlogInitialSetupPage.tsx:112–130,190–204`에서 완료 blog를 보존하고 `refreshUser` 후 `setupRecoveryTo: '/settings/posts'`를 사용한다. `SetupGuard.tsx:73–89`도 이 in-app recovery state를 받아 관리 화면으로 보낸다. 그러나 부분 실패 화면에서 사용자가 버튼을 누르기 전에 `/blog/setup`을 새로고침하면 `completedBlog`/recovery state가 사라지고, 이미 완료된 user를 본 `SetupGuard`가 `:84–85`에서 `/`로 보낸다. 서버에 저장된 setup/category는 유지되지만 `SideNav.tsx:27–31`에는 category management 직접 항목이 없고 `/settings`를 거쳐야 하므로, ADR-0005 Q4의 “새로고침 뒤 … 카테고리 관리에서 확인/추가할 수 있게 안내”를 정적 경로만으로 완전히 입증할 수 없다. full-refresh 브라우저 확인 및 management 안내 보강 여부를 다음 gate로 남긴다.

### 최신 FE 판정·다음 gate

- F-M3-FE-01~04: 최신 소스/회귀 테스트에서 주요 후보 경로 보완 확인. 독립 FE 실행·브라우저 증거가 없으므로 PASS/APPROVE 아님.
- F-M3-FE-05: stale 입력 초기화 후보, F-M3-FE-06: 전환 직후 쓰기 경계 후보, F-M3-FE-07: full-refresh 관리 안내 후보. 모두 현재 정적/자체 테스트만으로 제품 FAIL을 확정하지 않고 원 구현자 또는 브라우저 gate에 전달한다.
- 실제 1440px layout/copy/loading/error 상태, setup full-refresh, auth/defaultBlog 전환과 위 잔여 후보는 아직 미검증이다. API loopback smoke는 별도 계약 경계만 확인하며 post SQL 이동/count·101 root 페이지의 증거를 대체하지 않는다.

## 2026-09-06 loopback API smoke 실측

- 실행 명령: `& 'C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe' -NoProfile -ExecutionPolicy Bypass -File '.\qa\m3-api-smoke.ps1' -BaseUri 'http://127.0.0.1:8080'; $exit=$LASTEXITCODE; Write-Output ('M3_API_SMOKE_EXIT='+$exit); exit $exit`
- 종료 코드: `M3_API_SMOKE_EXIT=0`.
- 출력: `M3 API smoke passed. Fixture prefix: m3sdab71e3d. Accounts remain for cleanup.` 합성 owner/viewer 계정이 남으며, 실제 자격증명·비밀번호·access token은 출력하지 않았다. 계정 prefix만 기록한다.
- 실측 범위: 두 합성 계정 register/signin/auth-me·initial-setup/blog settings DTO, 공통 envelope/timestamp, anonymous GET 및 root `parentId=null`, owner `includeDrafts=true`, non-owner draft/read·mutation 403/CAT_004, anonymous write 401, 이름 trim·trim 후 100자·duplicate 409, child parent/tree, GENERAL→LOCKED 및 idempotent update, LOCKED numeric order/불변 변경 CAT_006, DEFAULT create/rename/delete 보호, full sibling ID reorder와 허용된 DEFAULT order, GENERAL subtree delete 후 name/order 재생성, stale ID set CAT_007을 모두 스크립트 assertion으로 통과했다.
- 경계: 이 smoke는 HTTP category 계약만 확인한다. posts 실제 SQL 이동·published/draft/UNIVERSE count, V1→V2 migration, 101-root 전체 페이지 경계, same-ID commit-order LWW, deleted-owner HTTP, 생성 `/v3/api-docs`, FE/1440px은 이 실행의 PASS에 포함하지 않는다. `type:1` numeric enum 400도 이 스크립트의 assertion 범위가 아니며 backend/MockMvc gate로 별도 확인한다.
- 판정: loopback smoke 자체는 PASS이나 M3 전체 PASS/APPROVE가 아니다. parent의 fresh JAR/Swagger·SQL·전체 backend 결과와 FE 브라우저 gate를 결합한 뒤 최종 판정한다.

### backend 정적 finding의 현재성 주의

- 앞선 `F-M3-ERR-03` 항목은 `featuresToDisable(...)`를 읽었던 **이전 소스 기준의 역사 기록**으로 보존한다. parent는 이후 `featuresToEnable(...)`로 수정했다고 전달했으나, 이 QA 컨텍스트는 backend 제품/Gradle을 실행하지 않았고 loopback smoke도 numeric enum 400을 포함하지 않는다. 따라서 이전 finding을 현재 FAIL로 재확정하지도, 수정 반영을 QA PASS로 승격하지도 않으며 backend 실행 gate의 결과를 기다린다.

## 2026-09-06 backend full build/test XML 독립 대조

- 실행하지 않고 읽은 산출물: `build/m3-root-full-build.log`, `build/test-results/test/TEST-*.xml`, 관련 현재 source/test. `build/m3-root-full-build.log:38`은 `BUILD SUCCESSFUL in 10m 24s`이며 parent가 전달한 wrapper exit는 0이다. XML 파일 56개를 PowerShell XML 파싱으로 집계해 `370 tests / failures 0 / errors 0 / skipped 0`을 직접 확인했다.
- `CategoryServiceMySqlTest.xml`은 14/14 통과다. test case 이름과 source assertion을 연결하면 101 root/page·direct child(`CategoryServiceMySqlTest.java:464–492`), full sibling ID/CAT_007(`:326–347`), LOCKED numeric slot/CAT_006(`:305–324`), DEFAULT/LOCKED 불변·GENERAL→LOCKED(`:137–215`), LOCKED child subtree 보호(`:217–235`), live/deleted post의 DEFAULT 이동(`:439–462`), owner draft/direct count/UNIVERSE 방향(`:54–135,494–513`), create/reorder/delete 경쟁(`:241–303,391–437`)을 포함한다.
- `CategoryControllerMySqlTest.xml`은 6/6 통과다. 삭제 owner public GET 및 other-user POST가 `BLOG_001` 404임을 `CategoryControllerMySqlTest.java:98–118`에서 확인하고, malformed `SERIES`, numeric string, numeric enum `type:1`, order body/query 형식 오류가 `VALIDATION_001` 400임을 `:120–175`에서 확인한다. root `parentId=null`/children(`:47–65`), trim 후 100자(`:177–192`), GET public security 빈 배열 및 write bearer·DELETE/order 응답 schema(`:194–233`)도 포함한다.
- `CategoryMigrationTest.xml`은 1/1 통과다. V1 rows와 삭제 이력을 V2에 forward 적용한 뒤 ID/parent/deleted_at/active_key 보존 및 blog/parent 범위 unique·삭제 후 재사용을 `CategoryMigrationTest.java:39–100`에서 확인한다. `GlobalExceptionHandlerTest.xml` 5/5는 JSON 역직렬화·요청 파라미터 변환의 `VALIDATION_001` 400(`GlobalExceptionHandlerTest.java:114–129`)까지 통과했다. `BlogPublicControllerTest.xml` 8/8에는 soft-deleted owner blog 404(`BlogPublicControllerTest.java:117–138`)가 포함된다.
- 이전 F-M3-ERR-03은 현재 `JacksonConfig.java:15–18`의 `featuresToEnable(FAIL_ON_NUMBERS_FOR_ENUMS)`와 controller numeric enum test의 full XML 통과로 **현재 열린 제품 결함이 아님**으로 정리한다. 삭제 owner HTTP·CAT_004 message·OpenAPI·malformed binding도 위 full 결과로 현재 blocking 결함을 찾지 못했다.
- **잔여 증거 공백 F-M3-ORDER-02**: same-ID reorder case는 XML상 통과하고 `CategoryService.java:180–261`은 blog lock 및 full sibling/flush 경계를 구현한다. 그러나 `CategoryServiceMySqlTest.java:607–610`의 `onSuccess` callback은 `TransactionTemplate` commit 전에 기록되므로 callback 순서가 실제 commit 순서를 독립 증명하지 않는다. 이를 제품 FAIL로 판정할 재현은 없지만, 엄밀한 last-write-wins acceptance 증거로는 제한을 남긴다.
- 이 full build/XML 결과는 backend 기능·migration·SQL count/move·OpenAPI 자동 증거를 보강하지만, FE 자체 보고를 QA 독립 실행으로 대체하지 않으며 실제 브라우저 1440px/full-refresh와 결합 전 M3 전체 PASS/APPROVE를 부여하지 않는다.

## 2026-09-06 F-M3-ORDER-02 LWW 증거 재판정 정정

- 앞선 기록의 “callback이 commit 전이므로 last-write-wins를 증명하지 못한다”는 판단은 최신 트랜잭션 경계를 충분히 연결하지 못한 **역사적 오판**으로 정정한다. 제품 코드·테스트는 수정하지 않았고 재실행도 하지 않았다.
- `CategoryServiceMySqlTest.java:603–610`의 각 worker는 `TransactionTemplate.executeWithoutResult` outer transaction 안에서 `categoryService.reorder`를 호출하고 곧바로 `onSuccess`를 기록한다. 서비스 `reorder`는 `@Transactional` REQUIRED(`CategoryService.java:179–180`)로 outer transaction에 합류한다.
- `reorder` 시작의 `lockOwnedBlog`(`CategoryService.java:269–275`)는 `BlogRepository.findByIdAndDeletedAtIsNullForUpdate`(`BlogRepository.java:26–29`)의 `PESSIMISTIC_WRITE` row lock을 획득한다. 따라서 T1이 callback을 기록한 뒤 outer commit 전이라도 T2는 같은 blog row lock을 얻을 수 없고, T1 commit/rollback이 끝난 뒤에만 T2 callback에 도달한다. callback 순서는 성공 transaction commit 순서와 일치한다.
- `firstResult.get()`/`secondResult.get()`(`CategoryServiceMySqlTest.java:375–376`)는 outer `TransactionTemplate`가 commit까지 정상 종료한 경우에만 null이다. callback 후 commit 실패/rollback이면 해당 Future가 Throwable을 반환해 assertion에서 실패하므로 rollback callback이 성공 순서로 남는 반례도 없다.
- 따라서 현재 `CategoryServiceMySqlTest.xml`의 same-ID concurrent reorder test 통과는 **blog lock으로 직렬화된 성공 commit의 last-write-wins 범위**를 증명한다. 구체적인 반례는 발견하지 못했으며 F-M3-ORDER-02는 현재 제품 결함·잔여 증거 blocker가 아닌 **해소**로 정정한다. 브라우저 1440px/full-refresh 미검증은 별도 gate로 유지한다.

## 2026-09-06 FE05~07 최종 제한 패치 정적 closure 대조

- 범위는 이전에 기록한 F-M3-FE-05~07만이다. 최신 `SettingsPostsPage.tsx`, `BlogInitialSetupPage.tsx`와 관련 회귀 소스를 재독해했으며 제품 파일·테스트는 수정하지 않았다. parent가 전달한 FE `269 tests/lint/build exit 0`은 구현자 결과 참고로만 취급하고 QA가 npm/Vitest를 재실행하지 않았다.
- **F-M3-FE-05 closure**: `SettingsPostsPage.tsx:419–458`는 create 응답 후 `isCurrentOperation(...) && isReadyForBlog`를 먼저 확인하고 `setNewName('')`을 실행한다(`:454–455`). A create 응답이 늦게 도착한 B 전환 시 B 입력을 보존하고 B POST를 하지 않는 회귀가 `categoryBehavior.test.tsx:357–440`에 있으며, `act`로 response parsing/async continuation을 drain한 뒤 assert한다.
- **F-M3-FE-06 closure**: `SettingsPostsPage.tsx:92,118–120,127–150,168–179`의 `loadedBlogId`와 `isReadyForBlog`가 blog 전환 직후 stale categories/`isLoaded`를 쓰기 대상으로 허용하지 않는다. reorder/move/drop/rename/type/delete/create와 렌더 컨트롤 모두 `isReadyForBlog`를 확인하거나 disabled 상태다(`:203–211,248–262,275–299,335–346,380–390,419–428,490–570,631–668`). B GET을 지연한 상태에서 add disabled·POST 없음·B 목록 후 enabled를 검증하는 회귀가 `categoryBehavior.test.tsx:442–478`에 있다. scoped source에서 이전 passive-effect write window를 재현할 열린 경계는 찾지 못했다.
- **F-M3-FE-07 closure**: partial category failure 즉시 `markCategoryRecovery()`가 `/blog/setup` history state `setupRecoveryTo='/settings/posts'`를 보존한다(`BlogInitialSetupPage.tsx:100–107,178–186`). 같은 화면 재시도 성공과 BLOG_004 recovery 성공 경로는 `flushSync`로 recovery state를 먼저 비운 뒤 `refreshUser()`하고 blog 목적지로 이동한다(`:189–216`), 따라서 SetupGuard가 stale recovery state를 먼저 소비하지 않는다. 회귀 `BlogInitialSetupPage.test.tsx:295–327`은 partial→retry 성공 시 `완료 블로그 경로`, partial→remount/refresh 시 `카테고리 관리 경로`, initial-setup 1회와 category 생성 결과를 확인한다.
- 결론: F-M3-FE-05~07은 최신 소스와 관련 회귀 구조상 **정적 closure**로 판정한다. 이는 구현자 테스트를 QA 독립 PASS로 대체하지 않는다. 실제 브라우저의 새로고침·1440px layout/copy/loading/error 관찰은 여전히 별도 gate다.

## 2026-09-06 FE completion-intent 최종 제한 패치 대조

- 범위: 최신 `BlogInitialSetupPage.tsx`, `guards.tsx`, `BlogInitialSetupPage.test.tsx`, `guards.test.tsx`와 F05/F06 관련 async 회귀 소스를 읽기 전용 대조했다. parent가 전달한 관련 FE 68 tests 및 full FE 269/lint/build exit 0은 재실행하지 않은 구현자 결과다. 제품/테스트/npm은 실행·수정하지 않았다.
- 성공 경계: `BlogInitialSetupPage.tsx:86–150`은 `useLocation`의 `setupCompletionTo: 'blog'` intent를 history에 먼저 커밋한 뒤 effect에서 `refreshUser()`를 수행하고, 완료된 `completedBlog.current.urlSlug`로 이동한다. refresh 실패 시 intent를 관리 recovery로 바꾸고 재시도 버튼을 유지한다. `:220–254`의 partial/BLOG_004 성공 경로도 같은 intent를 사용하므로 SetupGuard가 stale recovery state를 먼저 소비하는 timing 경계를 제거한다.
- guard 경계: `guards.tsx:62–100`의 `completionTarget`은 완료된 세션의 `user.defaultBlog.urlSlug`로 `/blog/{slug}`를 고정하고, 그 다음 recovery target과 `/` fallback을 적용한다. 미인증·loading·미완료 보호 정책은 그대로다. 별도 권한 정책 변경이나 우회 경로는 확인되지 않았다.
- 실제 route 모델 회귀: `BlogInitialSetupPage.test.tsx:81–140`의 `Routes`/`SetupGuard`가 `/blog/setup`, `/settings/posts`, `/blog/myblog`를 실제 경로로 렌더한다. `:301–316`은 partial→retry 성공이 관리 경로가 아닌 own blog로 가는지, `:318–333`은 completion refresh 실패가 관리 recovery로 이어지는지, `:335–350`은 remount/full-refresh history recovery를 확인한다. `guards.test.tsx:172–182`는 인증 사용자 slug 기반 completion intent를 확인한다. F05/F06 지연 응답 assertion은 `categoryBehavior.test.tsx:346–354,426–433`에서 `act`로 응답 파싱·continuation을 drain한 뒤 검사한다.
- 타이머/`flushSync`: 최신 intent 구현·관련 intent 테스트에는 `flushSync`, `setTimeout`, `setInterval`, fake-timer 실험이 남아 있지 않다. 범위 밖 기존 drag/drop 회귀의 event-loop 양보는 intent 판정과 무관하다.
- 결론: completion intent, partial management, refresh failure recovery, 기존 setup 정책은 최신 source/관련 회귀 구조상 **정적 closure**다. 새 critical 실제 결함은 발견하지 못했다. 실제 브라우저 1440px layout/copy/loading/error 및 OS full reload 관찰만 pending이며, 이를 확인하기 전 M3/FE 전체 최종 승인으로 승격하지 않는다.

## 2026-09-07 M3 FE 시각·native DnD 최종 패치 독립 재대조

- 범위: 동결된 최신 `BlogInitialSetupPage.tsx`, `SettingsPostsPage.tsx` 및 추가 `BlogInitialSetupPage.test.tsx`/`categoryBehavior.test.tsx`만 읽기 전용으로 재대조했다. 기준은 ADR-0005 Q2~Q4, REQUIREMENTS FR-CAT-01~05·FR-BLOG-02·FR-SETTINGS-04, PRD §7·§9.5 및 `DESIGN-SYSTEM.md` §3·§7.8·§8.6~§8.7이다. 제품 파일·제품 테스트는 수정하지 않았고 npm/Vitest/lint/tsc/build·브라우저도 실행하지 않았다.
- 정본 카피 대조: 설정 화면의 삭제 안내는 소스 `SettingsPostsPage.tsx:670–673`의 `미분류`와 일치한다. 디자인 원문 `DESIGN-SYSTEM.md:388`은 `전체`라고 적지만, 승인 ADR-0005:34와 REQUIREMENTS FR-CAT-04:519가 실제 저장 정책명 **`미분류`**로 명시적으로 override하므로 불일치 finding으로 올리지 않는다.
- setup 시각 정합: `BlogInitialSetupPage.tsx:321–361`은 시작 칩에 `bg-surface-raise`, 2px ink border, `px-3 py-[5px] text-xs font-bold`를 적용하고 `+ 추가`를 `2px dashed border-shadow`의 점선 칩으로 렌더한다. `fieldset disabled={isLoading}`도 유지되어 진행 중 중복 조작을 허용하지 않는다. 이는 `DESIGN-SYSTEM.md:120–123,371`의 칩·점선·비활성 토큰과 맞는다.
- 카테고리 행/조작 정합: `SettingsPostsPage.tsx:490–519`은 현재 blog가 전체 로드되고 mutation 중이 아닐 때만 row를 native draggable로 열고, `dataTransfer.setData('text/plain', String(category.id))`와 `effectAllowed='move'`를 기록한다. `:501–537`의 `gap-3 border-b border-line px-5 py-[13px]`, 핸들 15px shadow 색, 이름 14px bold, count 12px는 `DESIGN-SYSTEM.md:293,387`과 맞는다. `:539–568`은 DEFAULT/LOCKED에도 이름 변경·삭제 버튼을 렌더하되 inert variant와 `disabled`를 함께 적용하고, `:569–579`의 type select·핸들 잠금도 유지한다. 따라서 이전의 “버튼 숨김” 정합성 문제나 full-ID/native DnD payload 관련 신규 결함은 발견하지 못했다.
- 카드 구조 정합: `SettingsPostsPage.tsx:621–673`은 추가 form을 정본의 카드 하단 행으로 두고, 삭제 이동 안내를 `Panel` 밖으로 이동했다. 정본 카드 밖 캡션 위치와 `mt-2 px-1 text-xs text-text-muted` 스타일을 충족하며, 삭제 안내의 `미분류` 명칭은 위 승인 override와 일치한다.
- 관련 회귀 구조: `BlogInitialSetupPage.test.tsx:267–279`가 칩·점선 추가 버튼 클래스와 추가 동작을 확인한다. `categoryBehavior.test.tsx:657–695`가 DEFAULT/LOCKED의 disabled rename/delete, 행 간격·타이포·count·handle, form/caption 위치를 확인하고, `:697–734`가 keyboard reorder와 native DnD `text/plain` ID/`move` effect 및 LOCKED 대상 거부를 확인한다. 범위 밖 drag 테스트의 `setTimeout(0)` event-loop 양보는 completion-intent 경로가 아니므로 이번 시각 패치의 결함으로 보지 않는다.
- 실행 증거: parent가 전달한 최신 FE 결과는 `build/m3-final-frontend-tests.log`의 `23 passed (23)` 파일·`273 passed (273)` 테스트, exit 0이다. QA는 재실행하지 않고 해당 로그를 읽어 확인했다(`Get-Content` 명령 exit 0). parent가 보고한 lint 및 `tsc+vite build` exit 0도 구현자 실행 참고로만 취급한다. BE 소스는 이 패치 범위에서 변경되지 않았고, 이전에 독립 대조한 full XML 370 tests, failures/errors/skips 0/0/0 및 LWW/owner HTTP/numeric enum/migration/count·move 증거 결론을 유지한다.
- 결론: 최신 FE 시각·native DnD 패치에서 승인 정본과 모순되는 critical/high finding은 발견하지 못했다. 이는 구현자 FE 실행 결과를 QA acceptance PASS로 대체하는 것이 아니다. 실제 브라우저에서 1440px computed layout/카피/disabled·loading·error 상태와 OS 수준 full-refresh/session 복원을 관찰하는 게이트는 parent의 상세 증거가 도착할 때까지 **pending**이며, 그 전에는 M3 최종 PASS/APPROVE 또는 dev 머지 완료를 주장하지 않는다.

## 2026-09-07 M3 브라우저 부분 증거 및 잔여 게이트

- root가 frontend 구현자와 별도 컨텍스트에서 직접 실행·관측한 브라우저 증거를 정적·자동·API 증거와 분리해 기록한다. 가입→초기 설정 3칩→자기 blog slug 이동과 전체 reload/session 복원, 하위 카테고리 1·2 생성, 하위 2의 ArrowUp 순서 PUT/GET 및 성공 notice, 하위 1 inline rename/성공 notice, 동일 부모의 중복 이름 생성 거부와 기존 목록 보존, 새 탭 재조회 후 순서·이름·세션 복원이 확인됐다. 이 목록은 QA가 해당 브라우저를 직접 조작한 결과가 아니라 root의 독립 실행 보고다.
- root가 전달한 1440px native screenshot에서는 단일 카드·들여쓰기·행 선·타이포, DEFAULT의 disabled `이름 변경`/`삭제`, 카드 아래 `미분류` 안내가 확인됐다. 카피는 ADR-0005:34·REQUIREMENTS FR-CAT-04:519 override와 맞는다. 따라서 위 사용자 흐름과 1440px 화면 항목은 root 독립 실행 evidence로 현재 게이트를 닫되, QA가 screenshot의 computed 수치나 loading/error 상태를 직접 측정한 것으로 과장하지 않는다.
- **잔여 브라우저 게이트**: CUA mouse drag가 핸들 focus만 만들고 HTML5 dragstart/dragover/drop lifecycle을 완료하지 못해 실제 마우스 DnD의 순서 PUT·성공 notice는 검증되지 않았다. 코드의 `dataTransfer` 설정과 FE 회귀 PASS를 이 실측으로 대체하지 않는다. LOCKED 경고 문구는 접근성 트리에서 확인됐지만 confirm 수락이 도구 timeout/`No dialog is showing`으로 끝나 저장·후속 목록은 검증하지 않았다. 따라서 이 LOCKED 브라우저 사례도 PASS로 기록하지 않는다.
- 판정: 현재 정적 FE 패치와 parent 자동/API 및 root 독립 브라우저 증거에서 새 제품 결함은 발견되지 않았다. 다만 CUA mouse-DnD lifecycle과 LOCKED confirm 수락이 남아 있어 독립 QA의 최종 acceptance는 **보류**다. 수동 HTML5 DnD와 LOCKED confirm 수락을 실제로 완료하거나 도구 한계를 명시한 대체 증거를 받기 전 M3 최종 PASS/APPROVE 및 dev 머지 완료를 주장하지 않는다. M3 마감 후 M4는 착수하지 않는다.

## 2026-09-08 09:39 M3 종료 재개·완료 주장 감사

- 기준 source는 `08239e0514b6a1a78f090c3b0961e8fd4a60403e`이며 working tree는 깨끗하다. `git diff --name-status 4fc9ae2..HEAD -- src frontend`와 `git diff --name-status 067cd11..HEAD -- src`는 출력이 없어 제품 source/test는 각 기준 이후 변경되지 않았다. 따라서 기존 BE/FE 증거의 source 기준은 유효하다.
- 기존 산출물을 다시 읽어 `build/m3-root-full-build.log`의 `BUILD SUCCESSFUL in 10m 24s`, `build/test-results/test/TEST-*.xml` 56 files/370 tests/failures·errors·skips 0/0/0, `build/m3-final-frontend-tests.log`의 23 files/273 tests PASS를 확인했다. FE log와 lint/build는 parent 실행 참고이며 QA가 npm을 재실행한 결과가 아니다. 검증 JAR `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar` SHA-256은 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`로 기존 기록과 일치한다.
- 설치 상태를 변경하지 않고 `frontend/package.json`, `frontend/package-lock.json`, 직접 `node_modules` manifest를 비교했다. 17개 직접 의존성의 lock version과 설치 version은 모두 일치한다. `vite`는 선언 범위 `^8.1.1`, lock/설치 버전 `8.1.5`이며 `build/m2-fe-final-build.log`·`build/m3-vite.log`도 `vite v8.1.5`를 기록한다. `npm ls --depth=0`는 exit 0이고 @emnapi 계열·`tslib` 6개 extraneous만 보고한다. 이는 Vite mismatch나 M3 제품 결함으로 판정하지 않으며 install/update/정리는 수행하지 않았다.
- 전달용 PR body(`build/m3-pr-body.md`)의 “기존 사용자 소유의 project-lead 스킬 이전 변경은 이 PR에서 제외” 문구는 실제 HEAD `08239e0`가 `.agents/skills/project-lead/**`와 관련 운영 문서를 포함하는 사실과 모순된다. 이는 root가 PR body를 실제 head 파일 목록에 맞춰 교정할 delivery metadata 문제이며, QA 소유 제품 acceptance 판정을 바꾸지 않는다.
- 새 브라우저 증거는 아직 도착하지 않았다. root의 이전 독립 evidence로 signup/setup/self-blog/full-refresh/session·keyboard/rename/duplicate/new-tab·1440px screenshot 범위는 유지하지만, 실제 HTML5 mouse-DnD의 drop→order PUT→성공 notice와 LOCKED confirm 수락→불변 상태 재조회는 여전히 미검증이다. 두 gate 또는 지원 도구 한계를 입증하는 대체 증거 전에는 M3 최종 PASS/APPROVE·dev merge를 주장하지 않는다. M4는 시작하지 않는다.

## 2026-09-08 09:53 M3 native DnD 실측·LOCKED 잔여 gate

- root가 frontend 구현자와 분리된 Chrome 컨텍스트에서 `/blog/m3-close-20260908`의 카테고리 순서를 확인한 뒤 CUA pointer drag `[515,281] → [515,223]`로 `프론트엔드`를 `백엔드` 위로 이동했다. 직후 접근성 트리에서 `미분류/프론트엔드/백엔드/회고` 순서와 정확한 `카테고리 순서를 저장했습니다.` notice를 확인했다. 이는 QA 직접 조작이 아닌 root 독립 실행 evidence다.
- 같은 시점의 DB 읽기 전용 재조회에서 active IDs/orders `19 DEFAULT order=0`, `21 GENERAL order=1`, `20 GENERAL order=2`, `22 GENERAL order=3`이 확인됐다. blog ID는 parent report에 명시되지 않았으므로 추정하지 않는다. 현재 `SettingsPostsPage.tsx:193–224`의 `persistOrder`는 reorder await 후 GET 재조회 성공 뒤 notice를 설정하므로, root의 UI notice와 DB 최종 순서가 함께 확인된 native DnD 저장 gate는 **PASS( root 독립 evidence )**로 승격한다.
- 1440 viewport override 요청은 브라우저 zoom 90%로 `innerWidth=1600`, `dpr=0.9`가 관측됐다. 이 동작을 1440 CSS px 실측으로 주장하지 않으며, 기존 1440 native screenshot의 단일 카드·들여쓰기·행·타이포·disabled·미분류 안내 evidence만 유지한다.
- root는 `회고`의 type을 `LOCKED`로 선택해 `잠금 카테고리로 저장하면 이름·타입·순서를 되돌릴 수 없습니다. 저장할까요?` 경고와 AX confirm/OK focus를 확인했지만, `getJsDialog.accept`가 30초 timeout 후 세션 reset됐고 후속 `getTab`도 timeout됐다. DB read-only 결과에서 id 22는 여전히 GENERAL이므로 저장·재조회는 **미검증**이다. 사용자의 직접 OK 클릭 또는 지원되는 대체 evidence 전에는 LOCKED gate를 PASS로 올리지 않는다.
- 새 critical/high finding은 없다. native DnD gate는 닫혔고 현재 남은 UI gate는 LOCKED confirm 수락 후 `LOCKED` 타입·불변 disabled 상태와 DB/GET 재조회다. 이 증거 전 M3 최종 PASS/APPROVE·dev merge를 주장하지 않으며 M4는 시작하지 않는다. PR body의 project-lead 공개 범위 문구는 root가 remote에서 `포함`으로 정정했다고 보고했으나, QA가 GitHub API를 직접 재확인하지 못했으므로 delivery metadata는 root 소유 evidence로만 반영한다.

## 2026-09-08 10:05 M3 최종 LOCKED 실측·acceptance 판정

- root가 frontend 구현자와 분리된 IAB tab 1에서 `http://localhost:5173/settings/posts`를 열고 새 합성 slug `m3-iab-close-20260908`로 가입→초기 설정→자기 블로그 이동 후 카테고리 관리를 수행했다. `회고` 타입 select를 native click→Down→Return으로 `LOCKED`로 바꾸고, 경고 `잠금 카테고리로 저장하면 이름·타입·순서를 되돌릴 수 없습니다. 저장할까요?`의 `confirm`을 `getJsDialog.accept` 성공으로 수락했다.
- 저장 직후 AX에서 `회고 0 개의 글 LOCKED`, 회고의 순서 이동·이름 변경·삭제·타입 조작이 모두 disabled, notice `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.`를 확인했다. full reload 후 session 복원과 fresh AX에서도 같은 `LOCKED` 및 4개 조작 disabled 상태를 확인했다. 이는 QA 직접 조작이 아니라 root 독립 실행 evidence지만, 이전 미검증 UI gate를 닫는 실측이다.
- 10:05 KST DB 읽기 결과는 exit 0이며 blog 7의 active categories `23 미분류 DEFAULT order=0`, `24 백엔드 GENERAL order=1`, `25 프론트엔드 GENERAL order=2`, `26 회고 LOCKED order=3`이다. 이전 Chrome fixture blog 6/category 22가 GENERAL인 상태는 별도 데이터로 보존되며 이번 성공 결과와 혼합하지 않는다.
- 기존 근거와 함께 Q1~Q4 및 FR-CAT-01~05/FR-SETTINGS-04의 M3 gate를 모두 대조했다: BE full 370 tests/XML 0 failure·error·skip, FE 273 tests 및 lint/build, JAR SHA-256 일치, loopback API smoke, V1→V2/SQL count·move/권한/OpenAPI, root 독립 signup/setup/reload/session·keyboard·rename·duplicate·new-tab·1440px 시각 evidence, native mouse-DnD 저장/notice/DB 재조회 PASS가 유지된다. 새 critical/high finding은 없다.
 - 오전 Chrome native DnD 시점에 1440 viewport override 요청 후 browser zoom 90%로 `innerWidth=1600`, `dpr=0.9`가 관측됐으나, IAB에는 viewport override를 설정하지 않았고 치수도 측정하지 않았다. 따라서 이 값을 IAB 동작 또는 1440 CSS px computed PASS로 연결하지 않으며, 기존 1440 native screenshot의 단일 카드·들여쓰기·행·타이포·DEFAULT disabled·미분류 안내 evidence만 시각 gate 근거로 유지한다.
- root는 remote PR body의 project-lead 공개 범위를 `포함`으로 정정했고 PR `OPEN/Draft/head=08239e0`를 재확인했다고 보고했다. QA가 GitHub API를 직접 확인한 결과는 아니므로 이 delivery metadata는 root evidence로 구분한다. 제품 source/test 변경은 없으며 QA 소유 파일만 갱신했다.
- **최종 판정: APPROVE.** LOCKED confirm→저장 notice→reload/session→disabled 상태→DB/GET 결과가 모두 확인되어 이전 유일한 UI blocker가 해소됐다. root가 `dev` merge와 M3 `[머지]` 마감 기록을 수행할 수 있으며, 이 QA 판정은 merge 완료를 의미하지 않는다. M3 마감 후 M4는 시작하지 않는다.
