# qa 작업 기록

> append-only. 기존 항목을 수정·삭제하지 않는다.

## 2026-09-06 — 팀 상태 기준선 생성

- 한 일: 기존 M0~M2 리뷰·심의 기록으로 QA 역할 상태를 초기화했다.
- 산출물: `.claude/team/qa/CLAUDE.md`, `STATE.md`, 이 파일.
- 검증: 리더가 M2 `[머지]` 미기록과 기존 governance 정본 보존을 확인한다.
- 미해결: M2 최종 독립 검증.

## 2026-09-06 — M2 독립 QA closing pass 및 M3 Delivery & Risk 검토

- 한 일: 헌법·루트/역할 지침·REQUIREMENTS FR-SETTINGS-01~04/FR-BLOG-01/NFR-04·09·PRD §7/§9/§10~§12·디자인 정본·ADR-0004·M2 worklog와 M3 심의 문서/AGENT-BRIEFS를 실제 코드·테스트·V1 스키마와 교차 대조했다.
- 독립 검증: BE 전체 실행 보고는 53 XML, 347 tests, failures/errors/skipped 0(04:53 리더 산출물)로 확인했다. OpenAPI 교정 후 `OpenApiConfigTest` XML은 3/3 통과(0/0/0, 05:04:20)였고, FE M2 집중 Vitest 두 파일은 35 tests 통과(04:59:38)했다.
- 발견: `settingsBehavior.test.tsx:782–843` stale 응답 테스트는 응답 파싱·React state 반영 전 완료 신호를 기다려 fake-pass 가능하다(blocking 테스트 증거). BLOG_004 복구 테스트 `:140–163`은 정확한 slug path를 고정하지 않는다(non-blocking). 공개 owner는 name/email/password 없이 id/nickname/profileImageUrl/bio이며 bio는 현재 계약·테스트상 위반 확정이 아니므로 privacy 확인 위험으로 남겼다.
- 교정 확인: BlogPage 404/비404 오류 분기와 비404 재시도, settings 카드 dirty/revision/generation·로드 전 잠금, ScreenPanel 정본 라벨, 아바타 90px을 읽기 검증했다. BE OpenAPI 보안/오류 문서 교정과 회귀 테스트는 targeted 통과했으나 전체 재검증은 미해결이다.
- 산출물: `qa/AGENTS.md`, `qa/M2-review.md`.
- M3 검토: 회의 `M3-20260906-categories.md`는 공개 API·DB unique/soft delete·잠금/순서·공개 글 수·초기 설정 연계 Q1~Q4가 미확정이고 상태가 `REVIEWING`/`PROPOSED`로 불일치한다. 실제 V1 categories/posts/universes와 Category/SecurityConfig/ErrorCode를 근거로 사용자 승인·정본/ADR 반영 전 구현 보류를 권고한다.
- 미해결: 최신 수정 후 FE 전체 test/lint/build, BE 전체 test/build 및 새 JAR `/v3/api-docs`, 리더 1440px/API smoke, M2 worklog `[머지]`와 PR #8 `dev` 머지. RISK-0005 HTTPS 쿠키 smoke와 RISK-0007 slug link break는 배포 전 위험이다.

## 2026-09-06 — M2 최신 홈 셸·문서 정합성 독립 재검토

- 한 일: 최신 FE diff에서 `AppRoutes → AppShell(user) → TopBar/SideNav/RightPanel` props 흐름, guest 경로, stale/BLOG_004 회귀 보강을 제품 원본 읽기 전용으로 재검토했다. 부모의 1440px 실측(내 블로그 클릭→새 slug hero, 우측 패널 제목/slug·관리/작성 링크, avatar90·URL focus·공개 이미지)도 소스·테스트와 대조했다.
- 교정 확인: `/blog/me` fallback은 `/signin`, 비로그인 TopBar는 `로그인`→`/signin`, 로그인은 실제 nickname→`/settings`; `useOptionalAuth`는 제거되고 router가 AuthUser를 AppShell에 전달한다. 이전 F-M2-07은 blocking 없이 닫힌 것으로 기록하되 최종 전체 FE 출력은 별도 확인한다.
- 문서 감사: 헌법 diff 없음, 과거 worklog/governance 결론 보존, `git diff --check` clean. 사용자 승인 범위와 M3 승인 전 구현 보류가 문서에 공존하며 M2 최종완료 허위 주장은 확인하지 않았다.
- 리더 보고 finding(non-blocking): `AGENTS.md:79`·팀 README `:35`의 연속 진행 문구는 “승인 전 준비·독립 검토만”으로 좁히면 안전하고, M3 meeting `:5`/`:113`의 `REVIEWING`/`PROPOSED` 상태가 충돌하며, M2 worklog `:935` 뒤 새 `[개발 기록]` append가 고정 섹션 순서와 어긋난다. 문서 소유권 때문에 수정하지 않았다.
- 검증: FE/BE 프로세스는 부모가 전체 gate를 실행한 뒤 종료했으나 이 컨텍스트에서 실제 exit/output을 보유하지 못했고, 현재 `build/test-results/test` XML 0개·기존 JAR 시각 04:54로 성공을 추정하지 않았다.
- 미해결: 부모의 최신 전체 FE test/lint/build와 BE `cleanTest test`/build·새 JAR `/v3/api-docs` 출력, M2 worklog `[머지]` 및 PR #8 머지 상태. M3 Q1~Q4 승인·정본/ADR 반영 전 구현 보류.

## 2026-09-06 — FE 최종 게이트 및 셸 결함 종결 대조

- 한 일: FE 제품 원본 동결 후 최신 `AppRoutes`·`AppShell`·`TopBar`·`SideNav`·`RightPanel`과 해당 테스트를 재독했다. 부모 최종 보고(21 files / 250 tests / failures·skips 0 / lint·build exit 0)를 독립 소스·계약 대조 근거로 기록했다.
- 교정 판정: stale 성공·404 실패는 response body read 이후 `act`/flush와 실제 요청 진입을 기다리도록 강화되었고, BLOG_004 복구는 `/blog/already-done` pathname을 고정했다. guest `/signin`, 로그인 nickname/defaultBlog slug, 실제 AppRoutes 셸 연결 모두 정합하여 FE 관련 blocking finding은 없다.
- 검증: 부모 1440px smoke의 내 블로그 클릭→새 slug hero, 우측 패널 title/slug·관리/작성 링크, avatar90·URL focus·공개 이미지 흐름을 소스·테스트와 대조했다. `useOptionalAuth`는 제거되었다.
- 독립 mutation 대조: `build/m2-stale-mutation.mjs`가 임시 transform에서 guard 3곳을 제거하고 원본 SHA-256 불변을 확인했으며, 늦은 성공·실패 2개 모두 의도된 exit 1을 냈다. 28개 filtered skip은 전체 FE gate skip이 아니다.
- 미해결: BE 전체 `cleanTest test`/build는 53 XML·348 tests·0/0/0으로 확인했고, 새 JAR `/v3/api-docs`는 JAR가 05:27:49에 갱신된 뒤에도 PID 8264(05:04:35 기동) 구 프로세스가 살아 있어 재기동 후 확인해야 한다. 리더 최종 smoke/worklog `[머지]`·PR #8 머지도 남았다. 문서 감사 비차단 finding 3건은 별도 보고를 유지한다.

## 2026-09-06 — M2 최종 독립 QA 승인

- 한 일: 새 JAR SHA-256 `5F9724EC3D38B7B3D9F41B00C6917B744937876A2423DCAA028390D76CE2A69B`와 PID 24560(05:33:05 기동)을 확인하고 `/v3/api-docs`·`/swagger-ui.html`을 직접 조회했다.
- 검증: M2 보호 6개 operation은 `bearerAuth`, public blog/auth register·signin·refresh·signout은 `security=[]`, 200 concrete DTO refs·오류 20개 application/json envelope refs·401 `AUTH_002`+`AUTH_004`가 모두 확인됐다. swagger는 302로 접근 가능했다. BE 53 XML/348 tests/0 failures/0 errors/0 skipped, bootJar/build exit 0, FE 21 files/250 tests 및 lint/build exit 0, stale mutation·1440px browser/API smoke도 통과했다.
- 판정: **APPROVE (confidence 96/100)**. F-M2-01 stale fake-pass, F-M2-02 BLOG_004 path, F-M2-03 OpenAPI, F-M2-07 guest/home shell finding을 모두 해소했다. M2 source/evidence blocking finding 없음.
- 잔여: RISK-0005 운영 HTTPS Secure/Strict 쿠키와 RISK-0007 slug 변경 후 이전 URL 단절은 배포 전 별도 gate다. 문서 감사 비차단 3건(연속 진행 오독 가능성, M3 `REVIEWING`/`PROPOSED`, worklog 고정 섹션 순서)은 리더 정정 기록 후 merge한다.
- 다음: M3 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현하지 않는다. 리더의 M2 worklog `[리뷰]`/`[머지]`, PR #8 `dev` 머지 및 문서 정정 기록을 확인한다.
- 추가 문서 확인: 최신 `docs/governance/README.md`는 Claude 전용 구현/분기 표현을 구현 역할·리더 배정으로 동기화했지만, 독립 3인 심의·`LOW` 외 사용자 명시 승인·과거 회의/ADR 보존 규칙은 그대로 유지한다.

## 2026-09-06 — QA 역할 진입점·M3 상태 혼재 종결 확인

- `.claude/team/qa/CLAUDE.md`에 `qa/AGENTS.md` 하위 지침 진입점을 추가했다. QA 소유 범위 밖 파일은 수정하지 않았다.
- 리더 최신 파일을 재확인했다. `docs/governance/meetings/M3-20260906-categories.md` 상단과 §7은 모두 `USER_DECISION_REQUIRED`이고 §10에 정정 사유가 보존되어 있다. M2 worklog `[리뷰]` continuation에는 과거 섹션 보존 및 최신 기준이 명시되어 문서 감사의 3개 finding은 해소되었다.
- `STATE.md`의 오래된 `REVIEWING`/`PROPOSED` 혼재 차단 문구를 제거했다. M3 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현·승인 금지와 `docs/PM-M3-readiness.md`의 준비 문서 한정은 유지한다.

## 2026-09-06 — M2 PR #8 dev 머지 및 M3 인계 기록 독립 확인

- 리더 최신 `docs/worklog/M2-settings.md`의 `[머지]`를 읽어 PR #8이 base `dev`에 **MERGED**임을 확인했다. GitHub mergedAt는 `2026-09-05T20:41:12Z`(2026-09-06 05:41:12 KST), merge commit은 `4c129e20f58a6ccb9c61246d103934702516c295`, 검증 head는 `13d8debbd8c6a9396291bdf72b248610d62a29a8`이다. 과거 `[머지] 아직 없음`은 기록 보존 목적의 과거 항목으로 남아 있다.
- `.claude/team/JOURNAL.md` 최신 append, `docs/worklog/M3-categories.md`, `docs/governance/DECISION-REGISTER.md`를 독립 확인했다. M3는 계획·독립 심의 완료지만 Q1~Q4 사용자 승인 대기이며, 결정 레지스터도 `USER_DECISION_REQUIRED`다. 제품 구현·M3 승인·M4 착수 주장은 없다.
- `docs/PM-M3-readiness.md`는 M2 Git/PR 종료를 반영하고, worklog continuation·사용자 승인·정본/ADR 동기화를 M3 선행조건으로 유지한다. 하단 체크리스트의 미완료 표기는 PM 소유 준비 문서의 후속 갱신 범위이며 M3 승인으로 오인할 근거가 아니다.
- QA 판정은 변경되지 않는다: M2 **APPROVE (96/100)**, source/evidence blocking 없음. M3는 사용자 Q1~Q4 응답과 정본/ADR 반영 전 구현하지 않는다.

## 2026-09-06 — M3 승인 계약 독립 대조 및 acceptance 준비

- 한 일: 사용자의 구체 Q1~Q4 안내 후 원문 `"시작"` 승인 맥락을 회의록 §10, ADR-0005, 결정 레지스터, REQUIREMENTS §6.4/NFR-04·08, PRD §9.5·§10~§12, M3 worklog와 교차 대조했다. 회의 현재 상태 `APPROVED`, ADR-0005 `ACCEPTED`, 사용자 결정 주체가 일치하며 과거 `USER_DECISION_REQUIRED`는 역사 기록으로 보존됨을 확인했다.
- 산출물: `qa/M3-review.md`에 M3-DB/API/CAT/ORDER/DELETE/COUNT/SETUP/FE/DOC/M4 독립 acceptance matrix, 오류 경계, 기존 테스트 자산, 실행 전제조건을 기록했다. CAT_006은 LOCKED 숫자 순서·불변 변경/잠금 subtree, CAT_007은 displayOrder 중복·부적합 전체 ID 배열로 분리했다. `.claude/team/qa/STATE.md`를 현재 승인·준비 상태로 갱신했다.
- 검증: `Test-Path docs/governance/decisions/ADR-0005-categories-contract.md`가 `True`; ADR/회의록/레지스터/REQUIREMENTS/PRD 검색에서 승인·Q1~Q4·active_key·last-write-wins·includeDrafts·CAT_006/007 근거를 확인했다. `git status --short`로 리더/타 역할 변경을 보존했고 QA 소유 파일만 편집했다.
- 문서 후속: 회의록 상단 `관련 ADR` 목록의 ADR-0005 누락과 §9의 승인 전 문장을 발견했다. §7·§10 및 ADR/레지스터가 현재 승인 상태를 명확히 하므로 비차단이지만 리더의 정정 기록이 필요하다.
- 미해결: backend/frontend 제품 구현, V2 실제 MySQL migration·동시성·posts 이동/count, 공개 보안·Swagger, setup 부분 실패/응답 유실 복구, FE 1440px 검증은 아직 실행하지 않았다. PM PRD 산출물 최종본과 구현 버전 고정 후 targeted acceptance를 실행한다. M3 PASS/APPROVE를 주장하지 않는다.

## 2026-09-06 — M3 BE 초안 회귀 후보 acceptance 반영

- 한 일: 리더가 전달한 BE 초안 후보를 실제 현재 소스와 대조해 `qa/M3-review.md`에 M3-API-03, M3-CAT-03, M3-ORDER-03, M3-DOC-02와 재검증 항목으로 추가했다. 대상은 LOCKED 위치 변경 요청의 no-op 허용 가능성, 삭제 owner GET, trim 전 `@Size`, root `parentId=null` 보존, GET CAT_004 HTTP 문서 상태, DELETE/order OpenAPI 노출이다.
- 정본 확인: 회의록 상단 승인된 ADR-0005 링크와 §9 현재 상태 갱신을 재독했다. 과거 준비 문구는 역사 기록으로 한정되어 기존 비차단 문서 finding은 해소됐다.
- 검증: 제품 테스트·빌드·별도 acceptance는 실행하지 않았다. `CategoryService`, `CategoryDtos`, `CategoryController`, `ErrorCode`, `SecurityConfig`를 읽기 전용으로 확인했으며 현재 소스는 수정 중이므로 최종 FAIL/APPROVE를 내리지 않았다.
- 미해결: backend 수정 후 V2/MySQL·MockMvc/OpenAPI 실제 출력 및 frontend 산출물 도착을 기다린다.

## 2026-09-06 — M3 loopback API smoke 도구 작성 및 parser-only 검증

- 한 일: QA 소유 `qa/m3-api-smoke.ps1`를 추가했다. `localhost`/`127.0.0.1`의 http(s) authority만 허용하고, 실행마다 합성 계정 2개를 생성해 실제 register/signin/auth-me/initial-setup/blog-settings DTO를 호출한다. 이후 anonymous GET/root `parentId=null`·owner draft GET·non-owner draft/mutation 403·anonymous write 401·trim/duplicate·GENERAL→LOCKED·LOCKED 숫자 순서 CAT_006·DEFAULT 보호·subtree delete/recreate·full-ID reorder·stale set CAT_007을 공통 envelope와 HTTP 상태/오류코드로 확인한다.
- 안전 경계: 토큰·비밀번호는 메모리에만 두고 출력하지 않는다. fixture 결과는 랜덤 prefix만 출력한다. `HttpClientHandler` 쿠키/자동 redirect를 끄고, loopback 외 BaseUri·관리자/계정삭제·M4 Post CRUD는 다루지 않는다. posts SQL 이동/count와 101-root 페이지 경계는 별도 BE 증거로 남긴다.
- 교정: Windows PowerShell 5.1의 `System.Net.Http` 로딩을 위해 assembly를 명시하고, request body는 `ConvertTo-Json -InputObject`로 배열 형태를 보존하며, response도 dispose한다. 실행 비밀번호는 매회 GUID로 생성한다. UTF-8 BOM을 유지해 Windows PowerShell 파일 parser가 한글 계약값을 오독하지 않게 했다.
- 검증 명령: `$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'qa/m3-api-smoke.ps1').Path, [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.Message }; exit 1 }; 'M3_API_SMOKE_PARSE_EXIT=0'` → `M3_API_SMOKE_PARSE_EXIT=0` (exit 0).
- 미실행·다음 gate: HTTP smoke, 제품 테스트/build, Swagger/JAR, DB/count/move/101 경계는 실행하지 않았다. API/JAR 준비 및 리더 지시 뒤에만 smoke를 실행하고 실제 출력·fixture prefix·실패 원인을 별도 기록한다.

## 2026-09-06 — M3 smoke 경계 보강 재검증

- 추가 확인: trim 전 `@Size` 회귀를 놓치지 않도록 앞뒤 공백을 붙인 100자 카테고리 생성·trim 결과 assertion을 추가했고, 비소유자 POST mutation 403/CAT_004도 포함했다. 기존 HTTP 실행 보류와 SQL/count/101-root 별도 증거 경계는 유지한다.
- 재검증: 동일 `Parser::ParseFile` 명령을 재실행해 `M3_API_SMOKE_PARSE_EXIT=0`(exit 0)을 확인했다. HTTP smoke·제품 테스트·build는 실행하지 않았다.

## 2026-09-06 — M3 backend 승인 계약 정적 대조

- 한 일: 현재 `CategoryService`, `CategoryRepository`, `CategoryController`, `CategoryDtos`, `BlogRepository`, `SecurityConfig`, `ErrorCode`, V2 migration 및 추가 category/migration 테스트를 승인 ADR-0005·REQUIREMENTS §6.4/NFR-04·PRD §5.4와 읽기 전용으로 대조했다. blog lock 이후 category 재조회, LOCKED numeric slot/full sibling ID, owner soft-delete query, 실제 posts 이동·visibility/direction count SQL을 추적했다.
- 신규 blocking finding: `ErrorCode.java:56`의 CAT_004 message가 정본의 `카테고리에 대한 권한이 없습니다.`가 아닌 `카테고리 관리 권한이 없습니다.`다. 또한 `GlobalExceptionHandler.java:34–65`는 `MethodArgumentNotValidException`만 VALIDATION_001로 매핑하고 Jackson invalid enum/body 및 query type binding을 포괄 COMMON_500으로 보내므로, M3가 요구하는 형식 오류 400/VALIDATION_001 계약을 보장하지 않는다. 재현 입력과 파일/라인은 `qa/M3-review.md` F-M3-ERR-01/02에 기록했다.
- 중복/현재 상태: 기존 root 후보(LOCKED 필터 no-op, deleted owner, trim 전 길이, root parentId, GET CAT_004 문서)는 현재 소스에서 각각 조기 LOCKED 비교, owner `deletedAt` 조건, DTO trim/ALWAYS, 403 annotation으로 수정된 흔적을 확인했다. 실제 MySQL/MockMvc/JAR이 없어 최종 PASS로 올리지 않았다.
- 증거 공백: `CategoryMigrationTest`는 fresh schema만 확인해 V1 기존행 forward 적용을 증명하지 않는다. category 테스트에도 same-ID LWW 경쟁, owner soft-delete endpoint, malformed binding/message, duplicate/missing/cross-parent order CAT_007 증거가 없다. 제품 소스/테스트/Gradle/HTTP는 실행하지 않았다.
- 다음 gate: BE가 두 error contract를 수정한 뒤 invalid enum/body/query와 CAT_004 message를 targeted MockMvc/OpenAPI에서 확인하고, 별도 MySQL migration/lock/count/move/owner-soft-delete 증거를 수집한다. M3 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 BE 최신 정적 재대조·wrapper 검토

- 한 일: 최신 `CategoryService`/`CategoryRepository`/`BlogRepository`/`CategoryController`/DTO/`JacksonConfig`/`GlobalExceptionHandler`와 category·migration 테스트를 ADR-0005 Q1~Q3 및 REQUIREMENTS §6.4/NFR-04·08에 읽기 전용으로 대조했다. 제품 코드·제품 테스트·Gradle 파일은 수정하지 않았다.
- 정적 해소: CAT_004 고정 message는 `ErrorCode.java:56`에서 정본과 일치했고, `GlobalExceptionHandler.java:49–58`은 malformed JSON/enum/query binding을 `VALIDATION_001`로 보낼 경로를 추가했다. V1 target→V2 forward 기존행/삭제 이력·재사용 테스트(`CategoryMigrationTest.java:42–99`)도 이전 fresh-schema 공백을 보강했다.
- 신규 blocker: `JacksonConfig.java:13–17`은 scalar coercion/float-to-int만 차단하고 `FAIL_ON_NUMBERS_FOR_ENUMS`를 설정하지 않는다. 승인 계약상 `type:1`은 `VALIDATION_001`이어야 하나 enum ordinal 수용 위험이 있어 `F-M3-ERR-03`을 `qa/M3-review.md`에 기록했다. `CategoryControllerMySqlTest.java:121–126`의 숫자 enum 회귀 케이스는 마지막 실행 후 추가되어 미실행이다.
- 계약 경계 대조: blog/user soft-delete query, 실제 posts count/move SQL, flush→bulk→clear, root 101 page/children, full sibling ID/LOCKED slot 검사, 두 barrier 경쟁 테스트의 방향은 승인 계약과 일치한다. 동시성은 same-ID LWW 최종 행을 확인하지만 create 경쟁·락 대기·commit 순서를 계측하지 않아 중간 이상 증거로만 분류했다. DEFAULT/LOCKED 필드별 PUT·DELETE·LOCKED child subtree 거부와 owner soft-delete HTTP는 테스트 증거 공백이다.
- 실행 참고: parent 전달 targeted 결과는 CategoryService 10/10, CategoryController 5/5, CategoryMigration 1/1 통과다. QA가 재실행한 결과가 아니며, `GlobalExceptionHandlerTest` XML에는 교정 전 고정문구 assertion 1건 실패가 남아 있어 현재 소스 수정 후 재실행이 필요하다. 전체 test/build/bootJar·HTTP smoke·Swagger·FE/1440px은 실행하지 않았다.
- wrapper: `gradlew.bat:33` 초기 `ERROR_CODE=1`, `:79` `%ERRORLEVEL%` 캡처를 정적으로 확인했다. parent의 invalid `JAVA_HOME` 직접 확인은 exit 1이며, 미정의 `ERRORCODE`로 실패가 exit 0이 되던 원본 회귀의 수정 방향은 타당하다. wrapper 수정은 QA가 하지 않았다.
- 산출물: `qa/M3-review.md`, `.claude/team/qa/STATE.md`만 갱신했다. `git diff --check -- qa/M3-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md`를 다음 기록 검증으로 수행한다.
- 다음 gate: 숫자 enum 거부 설정 및 고정문구 테스트 재실행, V1→V2/동시성/SQL count·move/owner HTTP/불변 subtree 독립 증거, FE 전체 로드 전 쓰기 잠금·setup 복구·Swagger/1440px을 순서대로 확인한다. M3 최종 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 FE 승인 계약 정적 대조

- 한 일: `frontend/src/features/category/categoryApi.ts`, `SettingsPostsPage.tsx`, `ScreenPanel.tsx`, `BlogInitialSetupPage.tsx`, routes/관련 FE 테스트를 ADR-0005 Q2~Q4·REQUIREMENTS FR-CAT-01~05/FR-SETTINGS-04·PRD §7/§9.5·DESIGN-SYSTEM §6.2/§8.2/§8.6~§8.7과 읽기 전용으로 대조했다. 제품 소스·제품 테스트는 수정하지 않았고 npm/Vitest/브라우저를 실행하지 않았다.
- 정적 정합: category API는 root page를 `size=100`으로 `totalPages`까지 순차 로드하고, 설정 화면은 전체 로드 전 mutation을 막으며 full sibling ID와 LOCKED numeric slot을 검사한다. 공개 `ScreenPanel`은 `includeDrafts=false`와 서버 direct `postCount`를 사용하고, blog 전환 cleanup으로 늦은 응답을 폐기한다. setup은 initial-setup 성공 후 누락 root GENERAL을 순차 POST하고 partial/lost POST를 GET으로 확인하며 같은 mount에서 initial-setup 재호출을 피한다.
- 신규 FE 후보: (1) `SettingsPostsPage`의 blog 전환 중 오래된 mutation/load callback이 공유 `loadGeneration`을 탈취해 현재 blog에 이전 categories·notice·isMutating을 반영할 수 있는 F-M3-FE-01, (2) mutation 후 reload 실패가 내부 resolve되어 성공 notice가 남고 retry 뒤 stale success가 노출되는 F-M3-FE-02, (3) category partial failure에서 `refreshUser` 없이 setup 세션이 미완료로 남아 안내한 `/settings/posts`를 SetupGuard가 다시 setup으로 보내는 F-M3-FE-03, (4) `ScreenPanel` blog 전환 중 이전 blog totalCount를 잠시 표시하는 F-M3-FE-04를 `qa/M3-review.md`에 파일/라인·재현 흐름과 함께 append했다.
- 기존 CAT_001 FE mapping은 이름 validation이 아니라 category not-found로 현재 승인 오류 의미와 맞음을 확인했다. monthly/UNIVERSE 통계의 em dash는 M3 category count API stub으로 세지 않았으며, 1440px 시각 검증은 미실행이다.
- 증거 공백: 구현자 FE 결과(23 files/261 tests, lint/build exit 0)는 parent 전달 참고로만 취급하며 QA가 재실행하지 않았다. auth/defaultBlog 전환, mutation 후 reload failure/retry, setup refresh/guard 경로와 실제 1440px은 아직 미검증이다. BE F-M3-ERR-03 숫자 enum 및 API/JAR/DB/Swagger gate도 유지한다.
- 다음 gate: FE가 F-M3-FE-01~03을 보완한 뒤 static re-review, 독립 targeted FE race/reload/setup 테스트, API/JAR smoke와 1440px 브라우저 실측을 순서대로 수행한다. M3 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 BE 최신 변경 정적 재대조

- 최신 `JacksonConfig`, category service concurrency test, 삭제 owner 경로와 category controller/OpenAPI annotation·테스트를 ADR-0005 및 M3 계약과 읽기 전용으로 대조했다. 제품 코드·제품 테스트는 수정하지 않았고 Gradle/HTTP/JAR을 실행하지 않았다.
- **F-M3-ERR-03 유지(blocking)**: `JacksonConfig.java:15-18`이 `FAIL_ON_NUMBERS_FOR_ENUMS`를 `featuresToDisable(...)`에 넣어 numeric enum ordinal 차단 극성이 반대다. `CategoryControllerMySqlTest.java:121-126`의 `type:1` 400/`VALIDATION_001` 기대는 아직 미검증이며 수정 후 재실행이 필요하다.
- **F-M3-ORDER-02 증거 공백**: `CategoryServiceMySqlTest.java:357,363-385,582-610`은 transactional service 반환 뒤 callback 순서로 `lastCompletedOrder`를 기록한다. commit과 callback 사이 스케줄링으로 callback 순서가 실제 commit 순서를 대표하지 않아 LWW를 독립 증명하지 못하고 가짜 실패도 가능하다. 제품 FAIL 판정이 아니라 테스트 증거 강도 부족으로 기록한다.
- 삭제 owner service 직접 테스트(`CategoryServiceMySqlTest.java:512-533`)와 repository soft-delete 조건은 방향이 맞지만, anonymous/authenticated MockMvc GET에서 `BLOG_001` 및 비노출 envelope를 확인하는 HTTP 증거는 없다.
- `CategoryController.java:45-46`의 public GET `@SecurityRequirements`, write `bearerAuth` 및 `CategoryControllerMySqlTest.java:180-208`의 security/응답 문서는 정적 방향이 맞다. 생성 `/v3/api-docs`와 최종 JAR 실행은 보류한다.
- 명령: `rg` 읽기 전용 대조(exit 0). 미실행: Gradle 전체/targeted, HTTP smoke, Swagger/JAR. 다음 gate는 enum 설정 교정·400 실측, commit-order 증거, deleted-owner MockMvc, 생성 OpenAPI다. M3 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 FE 제한 수정본 독립 정적 재대조

- 범위: 최신 `SettingsPostsPage`, `ScreenPanel`, `BlogInitialSetupPage`, `SetupGuard`, `SideNav`와 관련 FE 테스트를 ADR-0005 Q2~Q4·REQUIREMENTS FR-CAT-01~05/FR-SETTINGS-04·PRD §7/§9.5·DESIGN-SYSTEM과 읽기 전용 대조했다. 제품 코드·제품 테스트는 수정하지 않았고 npm/Vitest/lint/build·브라우저를 실행하지 않았다.
- 정적 해소 확인: F-M3-FE-01~04의 주 경로(현재 blog/operation generation, mutation 후 reload 실패, in-app partial setup recovery, viewer/blog 전환 count)가 최신 소스와 테스트에 반영됐다. parent의 `266 tests/lint/build exit 0`은 구현자 자체 결과로만 기록하며 QA acceptance PASS로 승격하지 않았다.
- 잔여 후보: `SettingsPostsPage.tsx:432–441`의 F-M3-FE-05 stale create 응답이 현재 blog의 `newName`을 지울 수 있고, `:106–169` 및 `:322–375`의 F-M3-FE-06은 blog 전환 직후 passive effect 전 stale `isLoaded/categories` write window를 남긴다. `BlogInitialSetupPage.tsx:112–130`/`SetupGuard.tsx:73–89`와 `SideNav.tsx:27–31`을 대조한 F-M3-FE-07은 부분 실패 화면을 full refresh하면 recovery state가 사라져 `/`로 가며 settings를 거쳐야 하는 Q4 안내 공백이다. 세 항목 모두 runtime/browser 재현 전 제품 FAIL로 단정하지 않고 `qa/M3-review.md`에 근거를 append했다.
- 다음 gate: 준비된 `127.0.0.1:8080` fresh API에서 QA smoke를 실행한다. smoke는 HTTP envelope/보안/category 계약만 확인하며 SQL post 이동/count·101 root 페이지·1440px은 별도 증거로 유지한다. M3 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 loopback API smoke 실측

- 실행: `& 'C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe' -NoProfile -ExecutionPolicy Bypass -File '.\qa\m3-api-smoke.ps1' -BaseUri 'http://127.0.0.1:8080'; $exit=$LASTEXITCODE; Write-Output ('M3_API_SMOKE_EXIT='+$exit); exit $exit`
- 결과: `M3 API smoke passed. Fixture prefix: m3sdab71e3d. Accounts remain for cleanup.` 및 `M3_API_SMOKE_EXIT=0` (exit 0). owner/viewer 합성 계정은 남겼고 cleanup/delete하지 않았다. 실제 비밀번호·token·계정 전체 식별자는 출력하지 않았다.
- assertion 범위: register/signin/auth-me·initial-setup/blog settings DTO, 공통 envelope/timestamp, anonymous/owner/non-owner GET·write 보안(401/403), root null parent/owner drafts, trim·trim 후 100자·duplicate, child/tree, GENERAL→LOCKED·idempotent update, LOCKED/DEFAULT 보호 및 CAT_006, full-ID reorder, subtree delete 후 name/order 재사용, stale CAT_007이 통과했다.
- 범위 제한: 이 스크립트는 posts SQL 이동/count·101 root pagination·V1→V2·same-ID commit-order·deleted-owner HTTP·numeric enum 400·생성 Swagger·FE/1440px을 검증하지 않는다. smoke PASS를 M3 전체 PASS/APPROVE로 확대하지 않는다.

## 2026-09-06 — M3 backend full build/test XML 독립 대조

- 재실행 없이 `build/m3-root-full-build.log`, `build/test-results/test/TEST-*.xml`, 관련 source/test를 읽었다. build log `:38`은 `BUILD SUCCESSFUL in 10m 24s`; parent 전달 wrapper exit는 0이다. XML 56개를 파싱해 370 tests, failures/errors/skips 0/0/0을 확인했다.
- 계약 연결: CategoryService 14/14가 101 roots/children, full sibling ID와 LOCKED slot, DEFAULT·LOCKED 불변·GENERAL→LOCKED, LOCKED subtree 보호, live/deleted post DEFAULT 이동, owner draft/direct count·UNIVERSE 방향, create/reorder/delete 경쟁을 통과했다. CategoryController 6/6이 owner/non-owner/anonymous security, deleted-owner BLOG_001 HTTP, root null/children, trim 100, malformed enum/body/query `VALIDATION_001`, generated OpenAPI security/status를 통과했다. CategoryMigration 1/1이 V1→V2 기존행/삭제 이력/active_key/범위 unique·재사용을 통과했고, GlobalExceptionHandler 5/5와 BlogPublicController deleted-owner 404도 통과했다.
- F-M3-ERR-03은 현재 `JacksonConfig.java:15–18` `featuresToEnable(FAIL_ON_NUMBERS_FOR_ENUMS)` 및 `CategoryControllerMySqlTest.java:128–175` numeric enum 400 case의 full XML 6/6 통과로 현재 열린 결함이 아님을 기록했다. 과거 `featuresToDisable` finding은 역사로 유지한다.
- F-M3-ORDER-02는 product FAIL이 아닌 증거 공백으로 유지한다. `CategoryServiceMySqlTest.java:607–610` callback 기록이 `TransactionTemplate` commit 전에 실행되어 실제 commit-order LWW를 독립 증명하지 않는다. 소스의 blog lock/full sibling/flush와 concurrent test 자체는 통과했다.
- 다음: frontend 마지막 FE05~07 제한 패치 뒤 closure 재독해, 실제 브라우저 full-refresh/1440px gate. M3 전체 PASS/APPROVE는 보류한다.

## 2026-09-06 — M3 FE05~07 최종 제한 패치 정적 closure

- 최신 `SettingsPostsPage.tsx`, `BlogInitialSetupPage.tsx`, `SetupGuard` 경로와 관련 회귀 소스를 F-M3-FE-05~07 범위로만 재독해했다. 제품 파일·테스트는 수정하지 않았고 npm/Vitest/browser는 실행하지 않았다.
- F05 closure: create response 뒤 current operation/`isReadyForBlog` 확인 후 `setNewName`을 실행하며, delayed A create→B 입력 보존·B POST 없음 회귀가 `categoryBehavior.test.tsx:357–440`에 있다. F06 closure: `loadedBlogId`/`isReadyForBlog`가 전환 직후 stale 목록 쓰기를 막고 모든 mutation/control guard에 연결되며, delayed B GET 중 disabled/no POST 회귀가 `:442–478`에 있다.
- F07 closure: `markCategoryRecovery`가 partial failure 즉시 history state를 보존하고, normal retry/BLOG_004 success는 `flushSync`로 state를 먼저 정리한 뒤 `refreshUser` 및 목적지 이동을 수행한다(`BlogInitialSetupPage.tsx:100–107,178–216`). `BlogInitialSetupPage.test.tsx:295–327`이 retry-success blog destination과 remount/full-refresh management path를 확인한다.
- parent의 FE 269 tests/lint/build exit 0은 참고 자체 결과로 기록했고 QA 독립 PASS로 승격하지 않았다. F-M3-FE-05~07은 정적 closure지만 실제 브라우저 full-refresh/1440px layout/copy/loading/error는 미검증이다. LWW commit-order evidence gap과 함께 M3 최종 PASS/APPROVE는 보류한다.

## 2026-09-06 — F-M3-ORDER-02 LWW 증거 재판정 정정

- 최신 실제 source/test를 재독해해 기존 “precommit callback이라 불충분” 기록을 정정했다. `CategoryServiceMySqlTest.java:603–610`은 각 worker의 outer `TransactionTemplate` 안에서 `categoryService.reorder`(REQUIRED)에 합류한 뒤 callback을 기록한다.
- `CategoryService.java:179–181,269–275`와 `BlogRepository.java:26–29`의 `PESSIMISTIC_WRITE` blog row lock은 outer transaction commit까지 유지된다. T1 callback 후 commit 전에는 T2가 같은 blog lock을 획득해 callback할 수 없다. callback 순서가 성공 commit 순서와 일치하며, `firstResult.get()`/`secondResult.get()` null(`CategoryServiceMySqlTest.java:375–376`)이 outer commit 성공을 확인한다. rollback/commit 실패 callback이 성공 결과로 남는 반례도 없다.
- 따라서 `CategoryServiceMySqlTest.xml` same-ID concurrent reorder 통과는 lock-serialized successful commit의 last-write-wins 범위를 증명한다. 구체 반례는 발견하지 못했고 F-M3-ORDER-02는 현재 제품 결함·증거 blocker가 아님으로 QA 정정 append했다. 브라우저 1440px/full-refresh는 별도 미검증 gate다.

## 2026-09-06 — FE completion-intent 최종 제한 패치 독립 대조

- 최신 `BlogInitialSetupPage.tsx`, `guards.tsx`, `BlogInitialSetupPage.test.tsx`, `guards.test.tsx` 및 F05/F06 async 회귀 소스를 읽기 전용으로 대조했다. parent가 전달한 관련 68 tests 및 FE 269/lint/build exit 0은 재실행하지 않았다.
- 성공 경계: setup 완료 후 `/blog/setup`에 `setupCompletionTo:'blog'`를 history로 먼저 커밋하고 effect에서 `refreshUser()` 후 완료 blog slug로 이동한다(`BlogInitialSetupPage.tsx:102–150,220–254`). refresh 실패는 recovery state/관리 재시도로 전환한다. `SetupGuard.tsx:73–100`은 인증된 `defaultBlog.urlSlug`로 own-blog 목적지를 고정하고 recovery fallback을 유지한다.
- 실제 route 회귀: `BlogInitialSetupPage.test.tsx:81–140,301–350`의 `/blog/setup`·`/settings/posts`·`/blog/myblog` route 모델에서 partial retry 성공, completion refresh 실패, remount history recovery를 확인한다. `guards.test.tsx:172–182`는 completion intent slug를 확인한다. F05/F06 delayed response는 `categoryBehavior.test.tsx:346–354,426–433`에서 `act` drain 후 assert한다.
- 최신 intent 구현/관련 테스트에는 timer/`flushSync` 실험이 남아 있지 않다. 새 critical 실제 결함은 발견하지 못했고 FE 정적/자동/API 범위 검토를 완료로 정리한다. 실제 browser 1440px/full-refresh만 pending이며 M3 최종 승인은 보류한다.

## 2026-09-07 — M3 FE 시각·native DnD 최종 패치 독립 재대조

- 작업: 동결된 `BlogInitialSetupPage`, `SettingsPostsPage`와 최신 관련 회귀 테스트를 ADR-0005 Q2~Q4, REQUIREMENTS FR-CAT/FR-BLOG-02/FR-SETTINGS-04, PRD §7·§9.5, `DESIGN-SYSTEM.md` §3·§7.8·§8.6~§8.7과 읽기 전용 대조했다. 제품 소스·제품 테스트·npm·브라우저는 실행/수정하지 않았다.
- 결과: 시작 칩/`+ 추가` 점선 스타일, category row spacing/typography, native drag `text/plain` ID와 `effectAllowed=move`, DEFAULT/LOCKED rename/delete `inert+disabled`, 카드 밖 삭제 안내를 정본과 연결했다. 삭제 안내 `미분류`는 디자인 원문의 `전체`와 다르지만 ADR-0005:34·REQUIREMENTS FR-CAT-04:519의 승인 정책 override이므로 finding이 아니다. 신규 critical/high 결함은 발견하지 않았다.
- 회귀/실행: `BlogInitialSetupPage.test.tsx:267–279`, `categoryBehavior.test.tsx:657–734`의 보강 assertion을 확인했다. `Get-Content build/m3-final-frontend-tests.log`에서 parent 결과 `23 files/273 tests pass`, exit 0을 읽었으며, parent가 보고한 lint·tsc+Vite build exit 0은 참고 증거로만 기록한다. QA는 FE 명령을 재실행하지 않았다. BE full 370 tests/0 failure·error·skip 및 LWW closure는 기존 기록을 유지한다.
- 잔여/다음 gate: 실제 브라우저 1440px computed layout·카피·loading/error/disabled 상태와 OS full-refresh/session 복원은 상세 증거 도착 전 pending이다. 해당 gate 전 M3 전체 PASS/APPROVE 및 dev 머지 완료를 주장하지 않으며, M3 마감 후 M4는 시작하지 않는다.

## 2026-09-07 — M3 브라우저 부분 증거 재판정

- parent 전달 중간 증거: 가입→setup→자기 blog slug, 새로고침/session 복원, 자식 카테고리 생성 및 ArrowUp 순서 PUT/GET·notice, inline rename·notice, 중복 거부·기존 목록 보존, 새 탭 재조회와 native screenshot의 단일 카드/들여쓰기/행/타이포/DEFAULT disabled/미분류 안내. 이는 QA가 독립 조작한 결과가 아닌 parent 실행 보고로 기록한다.
- 잔여 실패/미실행: CUA mouse drag가 핸들 focus만 만들고 HTML5 dragstart/dragover/drop lifecycle을 완료하지 않아 실제 마우스 DnD PUT/notice는 미검증이다. LOCKED 경고는 AX에서 확인됐으나 confirm 수락은 timeout/`No dialog is showing`으로 저장되지 않았다. 코드·회귀 테스트 통과를 이 두 브라우저 실측으로 대체하지 않는다.
- 판정/다음 gate: 정적 FE 패치와 parent 자동/API/부분 브라우저 경로에서 신규 critical/high finding은 없지만 M3 독립 최종 acceptance는 보류한다. 수동 DnD와 LOCKED confirm 수락, 독립 1440px computed layout/copy/loading/error 대조 뒤에만 최종 판단하며, M3 마감 후 M4는 착수하지 않는다.

## 2026-09-07 — 브라우저 evidence 상태 정정

- frontend 작성자와 별도 컨텍스트인 root의 직접 실행·관측으로 signup/setup/self-blog/full-refresh/session, keyboard reorder, rename, duplicate rejection, new-tab persistence 및 1440px native screenshot(단일 카드·들여쓰기·행·타이포·DEFAULT disabled·미분류 안내)을 완료 근거로 반영한다. 이를 QA 직접 실행으로 표기하지 않으며, root 독립 검토 evidence로 대조했다.
- 남은 게이트는 CUA mouse-DnD가 HTML5 lifecycle을 완료하지 못한 실제 마우스 순서 PUT/notice와 LOCKED confirm 수락 후 불변 상태뿐이다. confirm은 timeout/`No dialog is showing`으로 저장되지 않았다. 사용자 확인/도구 제약을 해소하기 전 M3 최종 acceptance와 dev 머지는 보류한다.

## 2026-09-08 09:39 KST — M3 종료 재개·완료 주장 및 설치 상태 감사

- 한 일: 헌법·root/QA 지침·STATE를 순서대로 읽고, ADR-0005 Q1~Q4, REQUIREMENTS FR-CAT-01~05/FR-SETTINGS-04·NFR-04·08·09, PRD §5.4·§7·§9.5·§10~§12, M3 worklog와 `qa/M3-review.md`를 현재 HEAD와 교차 대조했다. 제품 source/test와 타 역할 파일은 변경하지 않았다.
- 검증: HEAD `08239e0514b6a1a78f090c3b0961e8fd4a60403e`, working tree clean, `4fc9ae2..HEAD`의 `src`·`frontend` diff 없음. 기존 `build/m3-root-full-build.log`/XML은 `BUILD SUCCESSFUL in 10m 24s`, 56 files/370 tests/failures·errors·skips 0/0/0이며, FE log는 23 files/273 tests PASS다. FE/BE 실행 자체는 이번 턴에 재실행하지 않았다. 검증 JAR SHA-256 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`도 기존 기록과 일치한다.
- 설치 상태: `package.json`·`package-lock.json`·직접 `node_modules` manifest를 읽기 전용 비교해 17개 직접 의존성 lock/설치 version exact match를 확인했다. Vite는 선언 `^8.1.1`, lock/설치/실행 로그 `8.1.5`로 mismatch가 아니다. `npm ls --depth=0` exit 0, @emnapi 계열·`tslib` 6개 extraneous만 보고했으며 install/update/정리는 하지 않았다.
- delivery metadata: local PR body의 “project-lead 스킬 이전 변경은 이 PR에서 제외” 문구는 HEAD `08239e0`가 `.agents/skills/project-lead/**`와 운영 문서를 포함한 실제 파일 목록과 모순된다. PR/Git는 root 소유이므로 수정하지 않고 root 교정 대상으로 보고한다. 제품 acceptance에는 영향이 없다.
- 미해결: root의 이전 독립 브라우저 evidence(가입/setup·reload/session·keyboard·rename·duplicate·new-tab·1440px screenshot)는 유지하되, HTML5 mouse-DnD drop→순서 PUT→성공 notice 및 LOCKED confirm 수락→불변 상태 재조회는 새 증거 전까지 pending이다. 이 gate 전 M3 최종 PASS/APPROVE·dev merge를 주장하지 않으며 M4는 시작하지 않는다.

## 2026-09-08 09:53 KST — native DnD 실측 PASS·LOCKED confirm 잔여 gate

- 한 일: root가 frontend 구현자와 분리된 Chrome 컨텍스트에서 `/blog/m3-close-20260908`의 실제 CUA pointer drag `[515,281] → [515,223]`를 수행해 `프론트엔드`를 `백엔드` 위로 이동했다는 증거를 검토했다. 직후 AX에서 `미분류/프론트엔드/백엔드/회고`와 정확한 `카테고리 순서를 저장했습니다.` notice가 보였고, DB read-only 재조회에서 active IDs/orders `19 DEFAULT=0`, `21 GENERAL=1`, `20 GENERAL=2`, `22 GENERAL=3`이 확인됐다.
- 판정: `SettingsPostsPage.tsx:193–224`의 `persistOrder`가 reorder await→GET 재조회 성공→notice 순서인 현재 source와 root의 UI/DB 결과가 일치한다. QA가 직접 브라우저를 조작한 결과는 아니므로 root 독립 evidence로 native mouse-DnD 저장 gate를 PASS로 승격했으며 새 critical/high finding은 없다.
 - 범위 경계: 오전 Chrome native DnD 시 viewport override 요청 후 브라우저 zoom 90%로 `innerWidth=1600`, `dpr=0.9`가 관측되어 해당 동작을 1440 CSS px 실측으로 주장하지 않는다. 이후 임시 viewport는 reset됐다. 기존 1440 screenshot의 단일 카드·들여쓰기·행·타이포·disabled·미분류 안내 evidence는 유지한다.
- 미해결: `회고` type을 LOCKED로 선택한 뒤 경고 `잠금 카테고리로 저장하면 이름·타입·순서를 되돌릴 수 없습니다. 저장할까요?`와 AX confirm/OK focus는 확인했으나 `getJsDialog.accept` timeout/세션 reset으로 저장이 완료되지 않았다. DB에서 id 22는 여전히 GENERAL이므로 LOCKED 저장·GET 재조회·불변 disabled 상태는 미검증이며 현재 유일한 UI gate다. 사용자 직접 OK 클릭 또는 지원 대체 evidence 전 최종 M3 APPROVE/dev merge는 보류한다.
- 전달: root가 remote PR body의 project-lead 공개 범위를 `제외`에서 `포함`으로 정정해 gh 성공했다고 보고했다. GitHub API 인증 실패로 QA는 직접 확인하지 않았고, PR/Git·마일스톤 기록은 root 소유로 유지한다. M4는 시작하지 않는다.

## 2026-09-08 10:05 KST — M3 최종 LOCKED 실측·QA APPROVE

- 한 일: root가 frontend 구현자와 분리된 IAB tab 1에서 `http://localhost:5173/settings/posts`를 열고 합성 slug `m3-iab-close-20260908`로 가입→초기 설정→자기 블로그 이동 후 `회고` 타입을 native click→Down→Return으로 `LOCKED`로 변경했다. confirm 경고 `잠금 카테고리로 저장하면 이름·타입·순서를 되돌릴 수 없습니다. 저장할까요?`에 `getJsDialog.accept`가 성공했다.
- 검증: 저장 후 AX에서 `회고 0 개의 글 LOCKED`, 순서 이동·이름 변경·삭제·타입 4개 조작 disabled, notice `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.`를 확인했다. reload 후 session 복원과 fresh AX에서도 동일 LOCKED/disabled 상태가 유지됐다. 10:05 KST DB read-only exit 0에서 blog 7/category 23 `미분류 DEFAULT order=0`, 24 `백엔드 GENERAL order=1`, 25 `프론트엔드 GENERAL order=2`, 26 `회고 LOCKED order=3`을 확인했다.
 - 경계: 이 브라우저 조작은 QA가 직접 수행한 것이 아니라 root 독립 evidence다. 이전 Chrome fixture blog 6/category 22가 GENERAL인 상태는 별도 fixture이며 성공 결과에 혼합하지 않았다. 오전 Chrome native DnD 시점의 zoom 90%(`innerWidth=1600`, `dpr=0.9`) 관측은 IAB 동작에 해당하지 않는다. IAB에는 viewport override를 설정하지 않았고 치수도 측정하지 않았으므로 1440 CSS 수치로 주장하지 않으며, 기존 native 1440 screenshot evidence만 유지한다.
- 종합: 기존 BE 370 tests/XML 0 failure·error·skip, FE 273 tests/lint/build, JAR SHA-256, loopback smoke, V1→V2/SQL count·move/권한/OpenAPI, signup/setup/reload/session·keyboard/rename/duplicate/new-tab·1440px·native DnD 저장/notice/DB 재조회 근거와 이번 LOCKED confirm 저장/재조회 근거를 승인 정본 ADR-0005 Q1~Q4 및 FR-CAT-01~05/FR-SETTINGS-04에 대조했다. 새 critical/high finding은 없다.
- 판정: **QA 최종 APPROVE.** M3 acceptance의 마지막 UI gate가 해소됐으며 root가 PR `OPEN/Draft/head=08239e0`의 remote scope 문장 `포함` 정정도 완료했다고 보고했다. root의 dev merge와 M3 `[머지]` 마감은 별도 후속이며, merge 완료로 기록하지 않는다. M4는 시작하지 않는다.

## 2026-09-08 10:14 KST — M3 QA 완료·공개 게시 승인 대기

- 상태 갱신: QA 최종 **APPROVE**와 기존 LOCKED/native DnD acceptance 근거는 유지한다. root 보고에 따라 제품 불변의 로컬 문서 커밋 `822b4ce1d6c78695e87ac7883e8577b066e03aea`가 생성됐고, 제품 기준 feature 원격 HEAD는 `08239e0`이다(local `ahead 1/behind 0`).
- 잔여 delivery gate: 11개 문서 공개 전송을 포함한 `git push`가 사용자 승인 부족으로 거절됐으며, 현재 PR은 `OPEN/Draft`이고 push·ready·merge는 수행되지 않았다. 공개 게시 승인 후 root가 push·PR 상태 갱신·dev merge 및 M3 `[머지]` 마감 기록을 이어간다.
- 범위: QA는 새 UI 조작·테스트·서버 검증·Git 조작을 수행하지 않았고 타인 변경을 보존했다. M4는 시작하지 않는다.

## 2026-09-09 09:20 KST — M3 공개 게시·dev merge 독립 마감 감사

- 한 일: 헌법·QA 지침과 현재 STATE를 읽은 뒤 ADR-0005 `ACCEPTED`, REQUIREMENTS §6.4의 FR-CAT-01~05·FR-SETTINGS-04·M3 공통 계약, PRD §5.4·§10~§12, M3 worklog `[머지]`, `qa/M3-review.md` 최종 `APPROVE`를 직접 재대조했다. 사용자 `그렇게 해줘`로 승인된 M3 공개 게시·PR 병합 범위만 감사했으며 M4 제품 구현·새 계약 검토·테스트 재실행은 하지 않았다.
- 검증: `git show --format=fuller --stat --summary 822b4ce1d6c78695e87ac7883e8577b066e03aea` exit 0, 11 files `+233/-46`; `git diff-tree --no-commit-id --name-status -r 822b4ce1d6c78695e87ac7883e8577b066e03aea` exit 0, 고정된 팀 상태/로그·QA review·M3 worklog 11개만 포함. `git diff --quiet 4fc9ae2 59badfe42d539a33091a387b9ee6119d838190b8 -- src frontend` exit 0, 출력 없음으로 승인 제품 tree 불변을 확인했다. merge tree에서 `M4|M4-posts|20260908-posts` 경로는 발견되지 않았다.
- 원격 증거: `git ls-remote origin refs/heads/dev refs/heads/feature/M3-categories` exit 0에서 `dev=59badfe42d539a33091a387b9ee6119d838190b8`, feature=`822b4ce1d6c78695e87ac7883e8577b066e03aea`; `git ls-remote origin refs/pull/9/head refs/pull/9/merge` exit 0에서 PR head=`822b4ce1d6c78695e87ac7883e8577b066e03aea`; `git cat-file -t 59badfe42d539a33091a387b9ee6119d838190b8`는 `commit`을 반환했다. 현재 브랜치는 `feature/M4-posts`이고 기존 M4/운영 미커밋 변경은 보존되어 있다.
- PR 증거: 공개 [PR #9](https://github.com/0-virus/zero-verse/pull/9) 페이지에서 `Merged`, `dev` ← `feature/M3-categories`, 2026-09-09 병합, Ready timeline과 merge commit `59badfe`를 확인했다. PR 본문도 11개 기록·검증 요약 게시와 M4 준비 문서 제외를 명시한다. `gh pr view` 직접 API는 인증 오류 `HTTP 401`(exit 1)이어서 API JSON을 읽었다고 주장하지 않고 공개 페이지·Git ref를 사용했다.
- 산출물: `qa/M3-review.md`에 공개 payload/PR 상태/base-head/merge/tree 불변의 독립 마감 감사와 **PASS**를 append했다. `.claude/team/qa/STATE.md`를 `M3 MERGED / M4 계약 준비 중 / QA 제품 미배정`으로 갱신했다. 제품 코드·제품 테스트·타 역할 파일·Git ref는 변경하지 않았다.
- 미해결: M3 차단 요인은 없다. M4 세부 계약 승인과 QA 명시 배정 전에는 제품 검증을 시작하지 않는다. loopback 합성 계정·브라우저 fixture는 정리하지 않았다.

## 2026-09-09 12:37 KST — M4 U0 독립 QA 최종 검토

- 한 일: 사용자 승인 §12.3·§14의 U0 범위만 대상으로 FR-UPLOAD-01~04, PRD §5.12·§13.1, 회의 §11.4·§11.5와 `gradle/u0/**`, `frontend/u0/**`, `compose.localstack.yml`을 읽기 전용 대조했다. 이번 U0 구현에는 참여하지 않았고 제품 API·DB·도메인·실제 AWS·구매는 다루지 않았다.
- 검증 근거: root가 별도 실행한 fresh process/bucket과 CUA 브라우저의 결과를 대조했다. `compileJava`는 cache ACL 문제를 권한 승인 후 exit 0으로 통과했고, fixture 보정 후 **19 CASE 18 PASS/1 FAIL**, **28 VERIFY 27 PASS/1 FAIL**이었다. 정상 네 MIME·정확 5 MiB·경계·checksum/byte/type 변조·만료·replay 412·signed GET negative·bounded GET/HEAD/Tika·객체 부재·CORS negative는 통과했다.
- 실패: `unsigned-private-get`가 실제 200(기대 403), `/verify`의 `unsigned-private-get-403`도 실패했다. `S3_SKIP_SIGNATURE_VALIDATION=0` 및 `S3_VALIDATE_SIGNATURES=1` 설정은 있으나 IAM authorization enforcement가 없어 signature validation과 private 권한이 분리되지 않았다. 브라우저 DOM도 `19/19 cases · 18 PASS · verify 27/28 PASS`를 재현했고 CORS TypeError는 없었다.
- 정적 경계: MIME spoof는 raw PUT 거부가 아닌 Tika 탐지 경계이므로 제품 `complete`/UPLOAD validation 증거가 아니다. Java direct self-check의 `requestStatus`는 GET/PUT만 전송하고 CORS OPTIONS는 별도 helper로 검사하며 현재 manifest도 GET/PUT만 포함한다. 고유 `zeroverse-u0-` 자원, 기존 데이터 미삭제, signed URL/token 비출력은 root 보고와 최종 파일에서 확인했으며 QA는 재실행하지 않았다.
- 판정/미해결: **BLOCKED (U0 PASS 아님), 확신도 99/100, 위험 HIGH/기밀성 CRITICAL.** 정식 지원·허가된 IAM authorization enforcement가 실제로 403을 증명하기 전까지 U0 완료·M4/U1 착수·보안 완화를 주장하지 않는다. 가입·구매·임의 구버전 pin·만료 우회·같은 key 재실행은 하지 않는다. 상세 근거와 필수 gate는 `qa/M4-u0-review.md`에 기록했다.

## 2026-09-09 12:57 KST — U0 IAM 라이선스 진단 후속 독립 검토

- 한 일: root가 제공한 12:55:48 read-only license 진단과 공식 [LocalStack Plans](https://docs.localstack.cloud/aws/licensing/), [IAM Policy Enforcement](https://docs.localstack.cloud/aws/developer-tools/security-testing/iam-policy-enforcement/)를 대조했다. 새 Docker/버킷/브라우저 실험, 설정·라이선스 파일 변경, 온라인 activate/request-new-license, 구매는 하지 않았다.
- 사실: `GET /_aws/iam/config` 404만으로 미지원이라 단정하지 않는다. 설치된 `localstack_pro_core-2026.8.1.dist-info/entry_points.txt`의 `iam-enforcement` plugin entrypoint는 확인됐다. 메모리 오프라인 검증 exit 0 결과는 `cached_license_valid=true`, `iam_basic_allowed=true`, `iam_enforcement_allowed=false`였고 비밀·라이선스 원문·고객 식별자는 출력하지 않았다.
- 판단: 공식 문서는 IAM Policy Enforcement가 기본 비활성이고 `ENFORCE_IAM=1`이 필요하며 Hobby에는 없고 Base/Ultimate 이상에 있다고 명시한다. 따라서 현재 차단은 플러그인 미설치가 아니라 **현재 cached license의 enforcement entitlement 미허용**으로 구체화된다. IAM 기본 API 허용은 enforcement 허용을 뜻하지 않는다. 계정 tier/license 원문은 독립 확정하지 않았다.
- 범위/결론: 사용자 후속 승인에도 root는 U0 bucket 2개/객체 16개를 초기화·재생성하지 않고 보존했다. 기존 18/19·27/28 실측과 브라우저 결과 외 추가 시험은 없다. U0 판정은 **BLOCKED (PASS 아님), 확신도 99/100, HIGH/기밀성 CRITICAL** 유지다. IAM entitlement가 허가된 환경에서 fresh 자원 무서명 GET 403을 재현하기 전까지 U0/M4/U1 PASS·보안 완화·구매·우회 주장을 금지한다. 세부 후속은 `qa/M4-u0-review.md`에 append했다.

## 2026-09-23 KST — SeaweedFS U0-ALT 최종 독립 QA

- 한 일: 헌법·AGENTS·QA 지침·현재 STATE를 순서대로 다시 읽고, 사용자 명시 `$brief` 절차로 정본·회의 §17·M4 worklog·현재 U0 harness/page를 읽기 전용 대조했다. QA가 SeaweedFS 설치·서버/브라우저 실행·Git·제품 수정은 하지 않았고 root의 별도 실행/관측 산출물만 독립 검토했다.
- 증거: 공식 native SeaweedFS 4.47 ZIP SHA-256 `8809359079e62fcd60574ff661449160899622c52072f3f569d346669079efe9`, Java `EA8D0D21161159638683D5CD7871F391578641F743AD9D12F45572DE98D75F6D`, HTML `B98C583B82C49F87EB08C30550624EADADF58DDE1F8F6CB86DF07EA014442EDE`를 파일 hash/관측 요약과 대조했다. `build/u0-seaweedfs-core-self-check.log`는 `19/19 CASE PASS`, `27/28 VERIFY PASS`, exit1이다.
- SeaweedFS identity가 anonymous private GET `403`, signed GET `200/403/403`, OwnershipControls `BUCKET_OWNER_ENFORCED`, exact/wrong CORS `200/403`, checksum·HEAD/range/Tika·negative object absence를 통과시켜 LocalStack의 private GET 200 공백을 provider-local로 보완했다. broad `Admin/Read/Write/List/Tagging` synthetic identity이므로 least privilege 증거로 확대하지 않았다.
- root browser evidence는 fresh bucket에서 `19/19 cases · 18 PASS · verify 27/28 PASS`다. unsigned private GET은 실제 `403`이나 `over-5mb-signed-at-max`는 `fetch TypeError/CORS`로 status 미노출이라 FAIL이다. `/verify` 유일 실패는 PAB `UNSUPPORTED HTTP501`이며, 객체 부재를 HTTP403 증거로 대체하지 않았다. TypeError 원인은 미확정이다.
- 현재 harness는 SeaweedFS setup PAB/OwnershipControls의 `501`만 capability line으로 기록하고 VERIFY false/분모28/exit1을 유지한다. browser origin은 `14566`·`14568`만 허용하고 expected status/wildcard CORS는 완화하지 않았다. core log의 generic PAB detail은 최신 detail-only 소스 변경 전 출력이며 acceptance 변경이 아니다.
- 잔여 위험: PAB 미지원으로 AWS Public Access Block을 증명하지 못하고, SeaweedFS emulator 결과를 IAM policy semantics·TLS·durability·lifecycle·STS/SSE/control-plane 또는 실제 AWS 보장으로 해석하지 않는다. server-v2 stderr의 STS signing-key/SSE/filer gRPC 경고도 운영 통과로 주장하지 않는다. U1은 실제 AWS가 아닌 제품 domain/FE vertical slice 별도 gate다.
- 산출물: `qa/M4-u0-review.md`에 SeaweedFS acceptance matrix·필수 PASS/FAIL·AWS/U1 잔여 gate를 append하고, STATE를 최종 **U0 BLOCKED** 스냅샷으로 갱신했다. 최종 권고는 대체 provider를 더 늘리지 않고 SeaweedFS 4.47을 `U0-ALT/core` 부분 gate로 고정하는 것; PAB와 browser oversized status를 FAIL로 유지하며 실제 AWS/정식 지원 PAB·IAM 환경과 U1 검증을 별도 수행하는 것이다.

## 2026-09-23 15:43 KST — 잔여 두 문제 후속 독립 QA

- root의 `build/u0-seaweedfs-diagnostic-evidence.md`와 fresh harness/browser 결과를 읽기 전용으로 대조했다. 동일 manifest의 fresh Run U0는 다시 `18/19 CASE`, `27/28 VERIFY`였고, native XHR 비교는 `status 0 · event error · headers 미수신 · U0 판정 미변경`이었다. fetch를 XHR로 교체해도 readable reject status가 나오지 않으므로 API 교체 해결안은 기각한다.
- 브라우저 증거 경계: raw HTTP의 `403 SignatureDoesNotMatch`·정확한 ACAO·`Connection: close`와 body 0 TCP 즉시 403은 서버 조기 서명 거절/연결 종료 가설을 강화한다. Go `net/http`의 큰 미소비 body close 규칙과도 부합하지만 browser TCP reset/error code는 확보되지 않아 exact root cause는 미확정이다. XHR의 ACAO/Connection 미관측은 CORS exposure 제한 때문에 헤더 부재를 뜻하지 않는다.
- PAB 공식 소스를 동일 4.47 tag로 재확인했다. `s3api_server.go` L882–885에 route는 있으나 `s3api_bucket_policy_handlers.go` L424–435의 각 handler가 `ErrNotImplemented`를 반환한다. 이전 중간 보고의 route 부재 표현은 정정하며, 정확한 판정은 **route 존재·handler stub·HTTP501**이다. PAB VERIFY false/분모28/exit1은 유지한다.
- 판정: 두 문제 모두 미해결, **U0 전체 BLOCKED**. raw 403·object absence·XHR status0은 browser PASS로 대체하지 않으며, expected status/manifest/negative 기준 완화·provider 합산·proxy/vendor patch/비용 전환을 하지 않는다. 전체 U0 재개 조건은 PAB 4 flag와 effective deny를 정식 지원하는 승인 환경 및 browser에서 readable `400/403`을 새로 확보하는 것이다. U1은 제품 도메인/FE gate, 실제 AWS는 별도 gate다.
- 검증·보존: temporary XHR HTML hash `4E25BBC4E140F2F705899985896A0FDCE4D50825AE283CB879D7B58889AEB7C9`는 root 증거에 기록됐고, root가 진단 후 XHR 코드를 제거했다. Java harness hash `EA8D0D...`는 변하지 않았으며 서버/listener와 합성 data 보존 상태는 root가 확인했다. QA는 제품·타 역할 파일·runtime·Git을 변경하지 않았다.

## 2026-10-03 13:43 KST — M3/M4 QA 산출물 commit-review 독립 대조

- 범위: `qa/M3-review.md`, `qa/M4-u0-review.md`, QA `STATE/WORKLOG`, ADR-0005, `docs/worklog/M3-categories.md`, `docs/worklog/M4-posts.md`, M4 회의 §14~§19와 U0 core/browser/diagnostic evidence를 읽기 전용으로 대조했다. 제품 코드·타 역할 파일·Git ref는 변경하지 않았다.
- M3 merge 증거는 `59badfe42d539a33091a387b9ee6119d838190b8`의 PR #9 merge commit/부모(`822b4ce`), 원격 `dev`, `feature/M3-categories`, PR head ref와 일치했고 `4fc9ae2..59badfe`의 `src`·`frontend` diff는 exit 0, merge tree의 M4 경로 match는 0이었다. ADR-0005 `ACCEPTED` 및 M3 `[머지]` 기록과 QA review의 PASS가 모순되지 않는다. M3 worklog 상단의 과거 `Draft PR #9` 문구는 append-only 역사 기록으로 보존한다.
- M4 수치는 정본과 일치한다. LocalStack은 `18/19 CASE`, `27/28 VERIFY`; SeaweedFS Java core는 `19/19 CASE PASS`, `27/28 VERIFY PASS`, exit 1; fresh browser는 `18/19 CASE`, `27/28 VERIFY`; PAB는 `HTTP501`; native XHR은 `status 0/event error`; 최종 U0 판정은 모든 문서에서 `BLOCKED`다. 실제 core log는 19/19 CASE, 27/28 VERIFY·1 FAIL과 non-zero exit 1을 재확인했고 OwnershipControls는 PASS, PAB만 FAIL이다.
- 정정 필요성이 명백한 STATE 사실은 발견하지 않아 판정·차단·잔여 위험은 유지했다. 다만 `qa/M4-u0-review.md:68`의 `PAB/OwnershipControls 결과를 false` 표현은 실제 log·STATE·worklog의 OwnershipControls PASS와 모호하게 충돌하므로 원문 수정은 담당자 판단으로 남긴다. `qa/M4-u0-review.md:3`의 Markdown hard-break trailing spaces 1건은 staged `diff --check`에서 확인할 항목이다.
- 검증: M3 merge/tree/ref 확인 명령 exit 0, SeaweedFS core 집계 `19/19`, `27/28·1 FAIL`, exit marker 1, browser evidence `18/19·27/28`, diagnostic `XHR status 0`, tracked QA `git diff --check` exit 0. 제품 테스트·U0 재실행·browser 재조작·commit/push는 실행하지 않았다.

## 2026-10-03 15:30 KST — M4 로컬 독립 인수계획·HTTP smoke 준비

- 작업 전 `.claude/CONSTITUTION.md` → `AGENTS.md` → `.claude/team/qa/CLAUDE.md` → `.claude/team/qa/STATE.md` → `qa/AGENTS.md`를 순서대로 읽었다. `$brief` 절차로 branch/status/log, 네 역할 STATE·최근 WORKLOG, M4 worklog·회의 §20.1~§20.8, REQUIREMENTS FR-BLOG-02/FR-POST-01~08/FR-UPLOAD-01~04/NFR-02/04/06~09, PRD §4~7·§9.7·§10~§12, ADR-0005를 직접 대조했다.
- 사용자 `로컬 파일 저장으로 M4 완성`과 worklog Implementation Plan에 대한 `yes`가 로컬 글/콘텐츠/DB/업로드/화면/QA 실행 기준임을 확인했다. S3/presigned/U0·실제 AWS/M5 이후는 별도 gate이고, M4 제품 구현·최종 PASS는 아직 미완료다. 기존 dirty `JOURNAL.md`, PM STATE/WORKLOG, M4 worklog/회의록은 변경하지 않았다.
- `qa/M4-review.md`를 생성해 공유 HTTP DTO/envelope, owner·정방향 ACCEPTED·역방향 ACCEPTED·unrelated·anonymous 권한 매트릭스, draft/publish/soft delete/previous-next/tag/view ledger, XSS/JSON-image equality, local multipart/5MiB/MIME/purpose/rebind/detach/restart, FE/Swagger/build/design/README gate를 FR/NFR/회의/Task와 실제 증거 유형에 매핑했다. 24h 경계·동시성·재시작·늦은 Blob 응답은 API smoke가 대체하지 않는 미검증 항목으로 명시했다.
- `qa/m4-api-smoke.ps1`를 생성했다. 기존 fixture 계정 credential만 메모리로 사용하고 M5 관계 mutation·SQL·runtime 기동/종료·cleanup을 하지 않는다. 실제 multipart PNG/JPEG/WebP/GIF, 정확 5MiB/초과/빈/위장, shared post DTO, XSS, 권한별 detail/list/tag/draft, image binary headers, purpose/rebind/external/detach/delete, 동일 anonymous view dedup을 검증하도록 작성했다.
- 검증: PowerShell parser `PS_PARSE_ERRORS=0`, `git diff --check -- qa/M4-review.md qa/m4-api-smoke.ps1` exit 0, 산출물 파일 존재/핵심 marker 확인 exit 0. 제품 runtime/DB/API/FE 테스트는 제품 미완성 및 root 소유 경계에 따라 실행하지 않았다. 따라서 smoke PASS·M4 완료를 주장하지 않으며 다음은 구현 완료 스냅샷·fixture·실측 출력 수령 후 독립 실행이다.

## 2026-10-03 15:36 KST — 공유 계약 대조 보정

- root의 독립 대조에서 M4 회의 §20.1의 익명 비허용 상세 오류(`AUTH_004`)와 초기 fixture 표의 `POST_002`가 충돌함을 확인했다. `qa/M4-review.md`와 smoke의 익명 UNIVERSE 상세 기대값을 HTTP 401 `AUTH_004`로 수정했다. 잘못/만료 Bearer를 익명 강등하지 않는 보완 계약도 유지한다.
- 이미지 snapshot의 중복·비연속 `displayOrder`는 입력 형식 검증 `VALIDATION_001`, owner/purpose/최초 binding/참조 불일치는 `UPLOAD_004`로 분리했다. smoke에 두 관리 이미지의 동일 order를 실제 400 `VALIDATION_001` gate로 추가했다.
- 보정 후 PowerShell parser `PS_PARSE_ERRORS=0`, QA 소유 파일 `git diff --check` exit 0을 재확인했다. 제품 HTTP smoke는 아직 실행하지 않았고 M4 PASS를 주장하지 않는다.

## 2026-10-03 15:40 KST — 생성 post soft-delete negative 경계 명시

- root fixture 계획과 대조해 smoke의 삭제 범위를 명확히 했다. 기존 계정·기존 post·기존 파일·fixture에는 삭제를 수행하지 않는다. smoke가 스스로 만든 private post 1건만 승인된 `DELETE` soft-delete negative로 사용하고, detail 404 및 연결 image 제3자/owner 404를 확인한다. 물리 파일 삭제나 전체 cleanup은 수행하지 않는다.
- 스크립트 주석과 `M4-review.md` 실행 경계를 위 내용으로 보정했다. 최신 parser `PS_PARSE_ERRORS=0`과 QA diff check exit 0은 다음 독립 실행 전 다시 확인 대상이며, 현재 제품 runtime/DB/API는 실행하지 않았다.
- 최종 보정 후 fresh PowerShell parser `PS_PARSE_ERRORS=0`, `git diff --check` exit 0, 계약 marker(`AUTH_004`, `VALIDATION_001`, `UPLOAD_004`, `no-store`) 확인 exit 0을 기록했다. 이는 도구 정적 준비 증거일 뿐 제품 API smoke 실행이나 M4 완료 증거가 아니다.

## 2026-10-03 15:54 KST — M4 Task1/2 조기 독립 코드리뷰

- 한 일: 제품 소유권을 지켜 `src/main/java/com/zeroverse/domain/post/**`, `tag/**`, `universe/**`, `V3__posts_local_uploads.sql`, 대응 post/migration test만 읽었다. 기준은 `feature/M4-posts` HEAD `97567d9cae51869b1ad1f896bce3dc5be4f2c42c`였고, backend 생성 파일은 untracked 상태였다. 주요 파일 읽기 시각/mtime은 `qa/M4-review.md` 조기 리뷰 절에 고정했다. 제품 코드·타 역할 test·runtime·DB·Git은 수정/기동/조작하지 않았다.
- 산출물: `qa/M4-review.md`에 Task1/2 조기 검토와 file:line/영향/수정방향/재판정 조건을 append했다. 정본 §20.1/§20.2·worklog Task1/2와 직접 대조해 (1) 익명 비공개 상세 `AUTH_004` 분기 부재, (2) 익명 `publish=false` 목록의 200 빈 결과, (3) 입력 `displayOrder` 중복/음수/비연속의 위치값 덮어쓰기, (4) 48h view cleanup 부재, (5) 접근 filter 전에 previous/next 20건 한정, (6) HMAC secret 고정 fallback을 Important 조기 FAIL/미구현으로 기록했다.
- 알려진 backend 초안인 ContentService HTML 1MiB/root doc/attrs allowlist/table span/external image code는 리뷰 중 `PostContentService.java`가 재갱신된 최신 source readback에서도 반영을 확인했다. `PostContentServiceTest.java`가 의도를 담고 있으나 제품 테스트/HTTP는 실행하지 않았다. `PostConcurrencyTest.java` 부재와 `PostMigrationTest.java`의 schema 존재성 위주 검증은 독립 증거 부족으로 기록했다.
- 검증: source 정적 readback과 `rg` 전체 검색만 수행했다. backend test/Gradle/API smoke/DB/browser는 제품 미완성 및 root 소유 경계로 실행하지 않았다. QA 산출물 변경 후 PowerShell parser 및 `git diff --check`는 다음 fresh 검증에서 재실행한다. 따라서 조기 FAIL/미검증 혼합이며 M4 PASS를 주장하지 않는다.
- 미해결: backend 수정 후 fresh snapshot 재리뷰, root의 random runtime HMAC 주입 및 4계정 fixture, 실제 MySQL/HTTP/FE evidence가 필요하다. root 조율로 POST `/posts`·`/uploads` 생성은 201, PUT/DELETE는 200으로 확정했지만 현재 PostController/OpenAPI의 POST 200은 backend 수정 후 fresh JAR에서 확인해야 한다. 최종 acceptance는 별도 배정 전까지 보류한다.

## 2026-10-03 16:01 KST — M4 Task1/2 최신 source 재확인

- 최신 읽기 범위: backend가 계속 수정 중인 `PostService.java`(UTC mtime 06:57:56), `PostContentService.java`(06:56:13), `PostController.java`(06:38:39), 대응 post/migration test를 다시 읽었다. 원래 조기 보고한 ACL 익명 오류·draft owner filter·image order 검증·48h cleanup·previous/next 접근 탐색·HMAC fallback 7건은 현재 source에 정적으로 반영된 것을 확인했으며, 제품 test/Gradle/API/DB는 실행하지 않았다.
- 잔여 Important: `PostService.java:389-400`은 동일 image URL을 `VALIDATION_001`로 거부하지만 회의 §20.1은 URL 중복 제거 후 최종 order를 연속 재번호하는 계약이다(`M4-EARLY-IMG-02`). 서로 다른 URL의 중복/음수/비연속 order를 `VALIDATION_001`로 구분하는 현재 방향은 유지하되, 동일 URL dedupe와 HTTP fixture가 필요하다.
- 계약·증거 공백: root 조율상 POST `/posts`·`/uploads`는 201, PUT/DELETE는 200이어야 하나 현재 `PostController` POST 반환/OpenAPI는 200이다. 익명 private stale controller test, `PostConcurrencyTest` 부재, `PostMigrationTest`의 table/index 존재성 한정 검증이 남았다. 원래 `PostServiceIntegrationTest` 2 fail은 backend 조사 중이라는 root 보고 이후 QA가 재실행하지 않았다.
- ContentService의 root doc/HTML 1MiB/attrs allowlist/table span/외부 image `UPLOAD_004`는 최신 source와 의도 test에서 정적 반영됐으나 실행 미검증이다. 조기 판정은 **정적 수정 확인 + residual FAIL + runtime 미검증 혼합**, M4 PASS 주장이 아니다.
- 산출물 검증: QA 소유 문서·smoke만 수정했고 제품·runtime·DB·Git은 건드리지 않았다. 다음 fresh parser/diff/marker 확인 후 root에 최신 상태를 전달한다.

## 2026-10-03 16:05 KST — M4 최신 변경 재독 정정

- backend가 조기 리뷰 후 추가 생성·수정한 `PostConcurrencyTest.java`를 확인했다. concurrent 10→1, 24h boundary, 48h batch cleanup, adjacent access fixture가 있어 기존 “파일 부재” 판정을 철회하고, 실제 실행·DB 결과 미검증 및 `PostMigrationTest` 보존 검증 부족으로 `M4-EARLY-TEST-01`을 부분 보완 상태로 정정했다.
- `PostController.java:51-63` 실제 POST `/posts` response는 201로 수정됐다. 다만 OpenAPI annotation은 `responseCode = "200"`으로 남아 있어 root 조율 계약(POST `/posts`·`/uploads` 201, PUT/DELETE 200)과 문서 정합성은 아직 미완료다. `PostControllerTest.java` 익명 private 기대값은 최신 source에서 `AUTH_004`로 맞음을 확인했다.
- 제품 test/Gradle/API/DB는 실행하지 않았다. QA 소유 산출물만 갱신했고 M4 판정은 여전히 **정적 수정 확인 + URL dedupe residual FAIL + 실행 미검증**, PASS 아님이다.

## 2026-10-03 16:08 KST — QA 산출물 최신 정적 검증

- `qa/m4-api-smoke.ps1` PowerShell parser 결과 `PS_PARSE_ERRORS=0`이다. 생성·업로드 기본 기대 201, PUT/DELETE 200, 익명 비공개 `401/AUTH_004`, 서로 다른 URL의 동일 `displayOrder` `400/VALIDATION_001`, binding/purpose/external `400/UPLOAD_004`, 동일 URL dedupe 201·image 1개·order 0 기대가 문서·스크립트에 함께 존재하는지 marker 검사 `MISSING_MARKERS=0`으로 확인했다.
- QA 소유 파일 `qa/M4-review.md`, `qa/m4-api-smoke.ps1`, `.claude/team/qa/STATE.md`, `.claude/team/qa/WORKLOG.md`에 대해 `git diff --check` exit 0을 확인했다(줄바꿈 경고만 출력). 제품 test/Gradle/API/DB/browser/runtime은 실행하지 않았다.
- 최신 제품 source mtime은 `PostService.java 07:03:37.960Z`, `PostController.java 06:58:41.504Z`, 새 `PostConcurrencyTest.java 07:00:04.421Z`, `PostMigrationTest.java 06:45:12.474Z`이다. source는 계속 변할 수 있으므로 이 결과는 조기 재확인 스냅샷이며 M4 PASS가 아니다.

## 2026-10-03 16:22 KST — M4 Task4 프론트 API·인증 이미지 조기 독립 코드리뷰

- 범위: frontend 구현자가 계속 수정 중인 기준 HEAD `97567d9` + untracked snapshot에서 `lib/apiClient.ts`, post DTO/API/tests, `features/upload/**`, `apiClient.test.ts`, Avatar/PostCard/Prose와 auth identity·프로필·본문·thumbnail 소비처를 읽기 전용 대조했다. Task5 editor 전체 UI/디자인, backend 재리뷰, 제품 test/runtime/browser, Git 조작은 하지 않았다. 주요 UTC mtime과 범위는 `qa/M4-review.md` Task4 절에 고정했다.
- 정본: worklog Task4, 회의 §20.3·§20.5, REQUIREMENTS 인증/업로드 canonical 계약을 적용했다. `apiClient.ts:106-236`의 FormData no-JSON-header·Bearer/credentials·401 single-flight·binary error, `ManagedImage.tsx:28-78`의 `src/userId` identity/늦은 Blob 무효화/revoke를 정적으로 확인했다. 전체 frontend `rg`에서 실제 `<img>`는 ManagedImage 한 곳이고 Prose/PostCard/설정/상세/Hero/PostEditor 소비처가 공통 경로를 사용한다. 최신 `uploadApi.ts:34-53`에서 기존 prefix resolver 문제도 해소됐다.
- Important `M4-FE-EARLY-CANON-01`: `uploadApi.ts:24-31`이 upload response `imageUrl`을 canonical 검증 없이 반환하고 PostEditor(`:186-207`)·Settings(`:222-228`)가 저장한다. Settings 자유 `profileImageUrl` input(`SettingsProfilePage.tsx:479-485`)도 update payload(`:310-316`)로 임의 external/blob/data/비정상 URL 쓰기를 막지 않아 §20.3의 canonical-only·legacy external read-only 경계가 FE에서 강제되지 않는다. 응답 malformed negative 및 자유 입력 테스트도 없다. canonical validator, 자유 입력 제거/읽기 전용·managed upload-only, negative tests와 실제 HTTP가 필요하다. 실제 서버 거부 여부는 미실행이다.
- 판정: Critical 발견 없음. ManagedImage stale Blob/userId guard·revoke, external URL no-Bearer, FormData/401 계약은 정적 수정 확인이지만 실행 증거가 아니다. 제품 FE test/lint/build·backend/API/DB/browser는 실행하지 않았고 M4 PASS를 주장하지 않는다. `STATE.md`와 `qa/M4-review.md`에 동일 판정과 다음 재검증 조건을 반영했다.

## 2026-10-03 16:34 KST — HTTP smoke New-ImageBytes primitive-array harness 수정

- root `build/m4-run-http-acceptance.ps1` 첫 HTTP acceptance가 합성 4계정·관계·signin/default category 뒤 `New-ImageBytes`의 `[Buffer]::BlockCopy`에서 제품 HTTP 단계 전에 중단됐다. QA는 동일 `switch` 자료 흐름을 Windows PowerShell 5.1에서 재현해 `byte[]`가 `System.Object[]`로 열거되고 primitive-array 예외가 나는 원인을 확정했다.
- QA 소유 `qa/m4-api-smoke.ps1:316-329`만 수정했다: 빈 byte[] 단일 반환, `[byte[]]$seed` 명시 캐스팅, 큰 결과 `return ,$bytes` 단일 반환. 제품 소스·runtime·DB·root harness·기존 합성 데이터는 변경하지 않았다.
- AST로 해당 순수 함수만 추출해 4 MIME/128 bytes·0·정확 5MiB·5MiB+1을 검증했다. `PURE_IMAGE_CASES=7`, `PURE_IMAGE_BAD=0`, 모두 `System.Byte[]`·기대 길이였고 parser `PS_PARSE_ERRORS=0`, smoke diff check exit 0이었다. HTTP/API PASS는 아니다.

## 2026-10-03 16:36 KST — HTTP smoke 빈 byte[] parameter binding harness 수정

- root 2회차가 4종 정상 업로드·익명 거부 후 빈 파일 검증에서 `Upload-Image`의 `[Parameter(Mandatory)][byte[]]$Bytes` binding 오류로 중단됐다. `[AllowEmptyCollection()]`이 없는 mandatory byte[]가 길이 0 배열을 거부하는 최소 재현을 확인했다. 하위 `Invoke-Request`의 기존 `[AllowNull()][byte[]]$FileBytes`는 별도 binding 재현에서 통과했다.
- QA 소유 `qa/m4-api-smoke.ps1:396`의 `Upload-Image.Bytes`에 `[AllowEmptyCollection()]`만 추가했다. 실제 `Upload-Image` AST를 HTTP stub과 함께 호출해 `UPLOAD_EMPTY_BINDING=PASS`를 확인했고, 앞선 7개 primitive/size 경계도 `PURE_IMAGE_BAD=0`으로 재검증했다. 전체 parser `PS_PARSE_ERRORS=0`, smoke diff check exit 0이다.
- 제품 HTTP/DB/runtime은 재실행하지 않았다. root가 다음 단일 HTTP smoke에서 제품 빈 파일 `400/UPLOAD_002`까지 도달하고 이후 전체 결과를 새로 제공해야 하며, 현재 M4 PASS가 아니다.

## 2026-10-03 16:52 KST — HTTP smoke duplicate URL fixture order 분리

- root 3회차는 5MiB 정확/초과·4 MIME·서로 다른 URL의 duplicate order `400/VALIDATION_001`까지 실제 HTTP로 통과했지만, dedupe fixture가 동일 URL의 raw `displayOrder=7,12`를 보내 정본의 연속 raw-order 입력을 위반해 `400/VALIDATION_001`을 받았다. 이는 제품 dedupe 실패가 아니라 QA fixture 순서 오류다.
- `qa/m4-api-smoke.ps1:651-652`를 동일 URL의 유효 `displayOrder=0,1`로 수정하고 `:659-664`에 서로 다른 URL raw `7,12 → 400/VALIDATION_001` negative를 별도 추가했다. URL first-occurrence dedupe와 raw-order 검증을 분리했으며 backend acceptance를 완화하지 않았다.
- PowerShell 5.1 pure test에서 `New-Post` body image 배열 count/index가 `2/0,1`로 유지되고, 단일 JSON image의 `Get-PostImages`도 `System.Object[]`, count 1, index0 order0이 되도록 `:533-535`를 단일 배열 반환으로 보정했다. 제품 HTTP/DB/runtime는 수정 후 실행하지 않았다.
- 전체 parser와 QA diff check는 다음 fresh check에서 재확인해야 하며, root가 수정 보고 후 `127.0.0.1:18080` 단일 smoke를 재실행해 dedupe `201/1개/order0` 및 raw `7,12 → 400`을 확보해야 한다. 현재 M4 PASS가 아니다.

## 2026-10-03 16:58 KST — HTTP smoke helper 빈 배열 binding 일괄 보정

- root 4회차가 제품 HTTP 이전 이미지 없는 글의 `@()` 입력에서 `Images` empty-array binding 오류로 중단됐다. 최소 재현에서 `New-ContentJsonWithImages.ImageUrls @()`도 동일 오류를 냈다. 이미지 없는 글/본문/스냅샷의 빈 배열은 승인된 제품 계약이다.
- QA smoke AST의 배열 parameter 8개를 전수 audit하고 `[AllowEmptyCollection()]`을 적용했다: `Invoke-Request.FileBytes`, `New-ContentJsonWithImages.ImageUrls`, `New-Post.TagNames/Images`, `Assert-ContainsId/Assert-ExcludesId.Items`, `Update-PostSnapshot.Images`. `Upload-Image.Bytes`는 이전 회차 수정으로 이미 포함돼 있다.
- PowerShell 5.1 pure 검증에서 New-Post body tags/images 0·1·2, content image nodes 0·1·2, update images 0·1·2가 통과했다. `ARRAY_PARAMETER_COUNT=8`, `ARRAY_PARAMETER_MISSING_ALLOW_EMPTY=0`, `PURE_EMPTY_ARRAY_CASES=PASS`. 제품 HTTP/runtime/DB는 실행하지 않았다.
- parser/diff fresh check 후 root가 다음 단일 smoke를 실행해야 한다. 이미지 없는 글 API 결과, dedupe `201/1개/order0`, raw non-contiguous `7,12 → 400`은 아직 미실측이며 M4 PASS가 아니다.

## 2026-10-03 17:49 KST — M4 중간 인수 증거 재대조·최종 게이트 보류

- 한 일: `.claude/CONSTITUTION.md` → `AGENTS.md` → `.claude/team/qa/CLAUDE.md` → `STATE.md`를 순서대로 읽고, `qa/AGENTS.md`, PRD §9.7, 회의 §20.8, `docs/worklog/M4-posts.md`, REQUIREMENTS의 M4 FR/NFR, ADR-0005와 기존 `qa/M4-review.md`를 직접 대조했다. 기존 기록은 삭제·덮어쓰지 않고 최신 addendum만 append했다. 제품 코드·타 역할 테스트·runtime/DB/browser fixture·Git은 변경하지 않았다.
- 사용자 결정 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”**를 인수 범위에 반영했다. Chrome 파일 URL 권한을 확대하지 않으며 실제 파일 선택·전송 자동화만 제외한다. multipart API/FE 자동 업로드, 로컬 파일 저장, 이미지 접근권한·5MiB, 브라우저 이미지 표시·재시작 보존은 유지하고, 실행하지 않은 파일 선택을 PASS로 기록하지 않는다.
- `build/m4-frontend-final-results.json`을 직접 `ConvertFrom-Json`으로 집계했다: `success=true`, 89 suites/311 tests, passed 89/311, failed 0, pending 0. root가 전달한 frontend WORKLOG의 최종 lint/build exit 0과 함께 기록했으나 backend 통합·독립 재리뷰 전 FE 전체 M4 PASS로 확대하지 않았다.
- root가 제공한 이전 runtime `3890657091DDDD7927DC53CBB4E14891125F568F51739B69F32793053723B36A`의 6차 `qa/m4-api-smoke.ps1` exit0, API 생성 PNG의 비로그인 Chrome 표시와 로컬/HTTP 응답 SHA 동일, `LocalImageStoreSymlinkCheck` target/root/ancestor 차단 결과를 root 관측으로 `qa/M4-review.md`에 기록했다. 브라우저 파일 선택창 증거와 혼동하지 않았고, 최종 수정 JAR 재실행·재시작 보존 대체로 취급하지 않았다.
- `build/m4-security-time-green-20261003-1720.log`와 `build/m4-post-v3-migration-20261003-1730.log`의 BUILD SUCCESSFUL을 직접 읽었다. 후자는 root가 보고한 target2→legacy active/deleted fixture→target3 보존 테스트와 연결했으나 최종 full suite/최종 artifact 재검증은 남겼다.
- `build/m4-backend-full-test-20261003-1750.log`를 직접 읽어 `419 tests completed, 1 failed`, `SecurityAccessControlTest`의 `path=/api/v1/posts/1` fixture 기대 불일치와 BUILD FAILED를 확인했다. 따라서 현재 독립 QA 판정은 **중간 증거 통과 + 최종 게이트 보류 / M4 PASS 아님**이다.
- `qa/M4-review.md`에 R1~R8 수정 추적을 갱신했다. R1/R4/R5/R6/R7 FE 자동 증거와 R1/R4 root UI 관측, R5 fresh `/write`의 실제 `미분류` selected는 중간 GREEN으로 기록했으며, R2 최종 malformed/ordered-list HTTP, R3 SQL ACL/page overflow, R8 deleted-parent `POST_001/404`, backend full green, 최신 HTTP/bootJar/restart/image preservation/final independent rereview는 pending으로 남겼다.

### 검증 명령·출력

- `Get-Content build/m4-frontend-final-results.json | ConvertFrom-Json` — `89/89 suites`, `311/311 tests`, failed/pending `0`, `success=True`.
- `Get-Content build/m4-backend-full-test-20261003-1750.log` — `419 tests completed, 1 failed`, `BUILD FAILED`.
- `Get-Content build/m4-security-time-green-20261003-1720.log`, `build/m4-post-v3-migration-20261003-1730.log` — 각각 `BUILD SUCCESSFUL`(2m18s/1m10s).
- `Get-Content`로 `docs/PRD.md` §9.7, M4 worklog 최신 root 기록, governance §20.8 및 QA 산출물을 재대조했다. QA는 API/DB/browser를 새로 실행하지 않았다.

### 미해결

- backend full suite failure 수정 및 fresh JUnit archive/bootJar hash 필요.
- 최종 JAR에서 API smoke와 malformed content/ordered-list attrs, HMAC/24h·동시성, 최신 PNG URL/metadata/bytes 재시작 전후 보존 필요.
- OpenAPI 실제 response code, README, R1~R8 독립 재리뷰 및 최종 root 판정 필요.
- S3/LocalStack/U0와 M5 이후 기능은 PRD §9.7에 따라 별도 후속이며, 기존 U0 `BLOCKED`를 로컬 M4 PASS로 변경하지 않는다.

## 2026-10-03 18:18 KST — 최종 로컬 M4 독립 QA PASS

- 최종 판정 범위를 PRD §9.7·회의 §20.8의 로컬 파일 저장, 인증 multipart/content API, 글·이미지 권한, 5MiB, API/자동 테스트로 고정했다. 사용자 결정 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”**에 따라 파일 선택창·브라우저 파일 전송 자동화는 실행하지 않았고, multipart API/FE 자동 업로드·실제 저장·권한·이미지 표시·재시작 보존은 유지했다.
- backend 최종 로그 `build/m4-final-backend-full-20261003-1900.log`를 직접 읽어 `BUILD SUCCESSFUL in 13m 49s`, exit 0을 확인했다. 보존 archive `build/m4-final-junit-20261003-1915`의 `TEST-*.xml` 66개를 PowerShell `[xml]`로 직접 집계한 결과 `426 tests / 0 failures / 0 errors / 0 skipped`였다. `PostConcurrencyTest=3`, `PostContentServiceTest=10`, `PostServiceIntegrationTest=8`, `UploadServiceTest=8`, `PostV3MigrationTest=1`, `SecurityAccessControlTest=24`를 포함한다.
- 최종 bootJar 로그 `build/m4-final-bootjar-20261003-1920.log`는 `BUILD SUCCESSFUL in 18s`다. `Get-FileHash build/m4-runtime-20261003-181423.jar -Algorithm SHA256` 결과는 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`다. preflight F11E와 최종 JAR zip class hash를 직접 비교해 `UploadController.class` 1개만 변경되고 `LocalImageStore.class`는 동일함을 확인했다.
- FE report를 직접 집계했다. `build/m4-frontend-final-results.json`: `28 testResults / 311 tests / 311 passed / 0 failed / 0 pending / success=true`; `build/m4-frontend-r7-results.json`: `2 files / 40 tests / 40 passed / 0 failed / 0 pending / success=true`. frontend WORKLOG 최종 lint/build exit 0도 직접 읽었다. 이전 기록의 `89 suites`는 nested suite 수이므로 최종 기준은 **28 files/311 tests**로 정정했다.
- root가 최종 runtime F11E에서 7차 HTTP smoke exit0, malformed leaf `400/VALIDATION_001`, ordered-list start/type `True/True`, `page=2147483647&size=20`의 정상 empty page를 관측했다. 공개/친구 방향성/PRIVATE/draft, MIME/1~5MiB, purpose/rebind/detach/delete/view dedup/snapshot/order를 포함한다.
- root가 최종 D2B5 runtime에서 실제 OpenAPI posts `201`, uploads `201`, content GET operation과 오류 response를 확인했다. 이전 uploads 200 annotation 잔여는 `UploadController.class` 변경으로 해소됐다. 같은 최종 artifact에서 post23 image `200/395 bytes/image/png/no-store/nosniff`와 원본·파일·HTTP SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67` 일치, 비로그인 Chrome `complete=true/natural96×64`를 재확인했다.
- root의 Chrome 1440px ordered-list/image 표시, R1 Markdown 삽입, R4 draft 전환, R5 DEFAULT 선택과 root whole-review의 FE R1/R2방어/R4/R5/R6/R7·BE R2/R3/R7/R8 source closure를 대조했다. 추가 Critical/Important 회귀 없음으로 독립 reviewer 결과를 기록했다. symlink proof의 target/root/ancestor read `UPLOAD_003`, write `COMMON_500`, marker `UNCHANGED`도 root 실행 증거로 유지했다.
- README를 직접 읽어 `.local-data/uploads`, DB+파일 동시 백업/재시작, local/test profile, static exposure 금지, 5MB와 S3 후속 범위를 확인했다. 로컬 M4 판정과 S3/U0/M5 범위를 섞지 않았다.

### 최종 QA 판정

**로컬 M4 PASS.** backend/FE full evidence, HTTP 권한·업로드·콘텐츠·페이징·OpenAPI, migration/동시성/HMAC/조회수, 파일 경계, 재시작 보존, 브라우저 표시와 독립 R1~R8 재리뷰를 확인했다. 이 판정은 commit/push/merge/release 완료가 아니다.

### 비차단 잔여 범위

- S3 presigned/실제 AWS/LocalStack/SeaweedFS U0는 PRD §9.7 후속 범위이며 기존 U0 `BLOCKED`를 변경하지 않는다.
- 브라우저 파일 선택·전송 자동화는 사용자 결정으로 제외했다. API/FE 자동 업로드 및 실제 이미지 표시로 검증했다.
- M5 관계 CRUD·댓글·좋아요 및 운영 배포 hardening은 이번 M4 판정 범위가 아니다.
- QA는 제품 코드·타 역할 테스트·runtime/DB/browser fixture·Git을 변경하지 않았으며, root가 후속 stage/commit/push/merge를 진행한다.

### 검증 명령·출력

- `Get-Content build/m4-final-backend-full-20261003-1900.log` → `BUILD SUCCESSFUL in 13m 49s`.
- `Get-ChildItem build/m4-final-junit-20261003-1915 -Recurse -Filter TEST-*.xml` + XML 합산 → `66 / 426 / 0 / 0 / 0`(files/tests/failures/errors/skipped).
- `Get-Content build/m4-final-bootjar-20261003-1920.log` → `BUILD SUCCESSFUL in 18s`; `Get-FileHash` → D2B5 hash.
- `ConvertFrom-Json build/m4-frontend-final-results.json` 및 `m4-frontend-r7-results.json` → 각각 `28/311/0/0`, `2/40/0/0`.
- ZIP class hash comparison → final/preflight 차이 `UploadController.class` 1개, `LocalImageStore.class` 동일.
- README/PRD §9.7/M4 worklog/whole-review 결과를 직접 대조했다. QA는 최종 runtime/API를 재기동하지 않았다.

## 2026-10-03 18:20 KST — R7 브라우저·자동 테스트 증거 귀속 정정

- 앞선 최종 QA 기록의 `R7 ordered-list/table 의미 보존` 표현을 정정했다. root Chrome 직접 검증은 ordered-list `start=5/type=a`의 marker `e.` 및 computed `lower-alpha`, 이미지 표시와 일반 2×2 표 작성·발행 흐름이다.
- `rowspan/colspan` 보존은 Chrome 직접 증거로 주장하지 않고, backend `PostContentServiceTest` 및 FE R7 추가 회귀 JSON `2 files/40 tests`, 실패·pending 0의 자동 테스트 증거로 귀속한다.
- 귀속 정정이며 R7 PASS, 로컬 M4 PASS, 테스트 결과와 범위는 변경하지 않는다. QA는 제품 코드·타 역할 테스트·runtime/DB/browser fixture·Git을 변경하지 않았다.

## 2026-11-07 KST — M5 독립 QA 1차 최종 판정

### 한 일

- `.claude/CONSTITUTION.md` → `AGENTS.md` → `.claude/team/qa/CLAUDE.md` → `STATE.md`와 `qa/AGENTS.md`를 다시 읽고, REQUIREMENTS §6.6 FR-UNI-01~12/NFR-04/09, PRD §4/§5.6/§7.1/§7.6/§9.8/§10~12, M5 worklog와 승인 회의록을 실제 소스와 대조했다.
- `qa/M5-review.md`에 검증 매트릭스, severity별 finding, positive evidence, 잔여 gate를 작성했다. QA 소유 파일 외에는 수정하지 않았고 commit/push/PR/merge를 하지 않았다.

### 검증

- Fresh frontend `npm test -- --run --maxWorkers=1` → `31 files / 336 tests passed`; `npm run lint` exit 0; `npm run build` exit 0(Vite chunk warning만 존재).
- 승인된 임시 Temurin21/Colima MySQL8.4 환경에서 backend M5 대상 test를 직접 실행 → `BUILD SUCCESSFUL in 14s`, `6 files / 28 tests / 0 failures / 0 errors / 0 skipped`.
- source inspection으로 `lockPair` ID 오름차순, block 양방향 정리/반대 BLOCKED 보존, 목록 DB filter/search/sort/PageResponse, snapshot direction/requestId/availability/no-store, FE stale guard/action mapping을 확인했다.
- 테스트 source에서 `@Disabled`, `it.skip`, `describe.skip`, `test.skip`, `xit`을 찾지 못했다. QA 문서 작성 전후 `git diff --check -- qa .claude/team/qa` exit 0.

### 산출물·판정

- `qa/M5-review.md`: **조건부 통과이나 최종 인수 차단**. Important는 BE-001(모든 mutation의 lock 후 양방향 Universe reread 누락), BE-002(received/sent per-row blog slug N+1), QA-001(M4 신청→수락→UNIVERSE GET 직접 통합/HTTP 증거 부재)다. Minor는 snapshot 직접 무토큰 테스트와 endpoint별 OpenAPI error 문서 증거다.
- 디자인 원본 `DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`, `support.js`가 없고 `docs/design/AGENTS.md`만 있으므로 1440px 시각 DoD는 BLOCKED다. 따라서 M5 완료·merge-ready를 주장하지 않는다.

### 미해결

- root가 BE-001/BE-002 수정 루프, 실제 MySQL/HTTP M4 positive/revocation, snapshot 401/OpenAPI 보강, 디자인 원본 복구 후 1440px 검증을 수행해야 한다.

## 2026-11-07 KST — M5 수정 루프 독립 재검토

### 한 일

- 수정된 실제 diff와 정본(REQUIREMENTS §6.6/NFR-04/09, PRD §5.6/§10~12, 승인 회의 A-v2, M5 worklog)을 다시 대조했다. 제품 코드·타 역할 파일은 수정하지 않았다.
- BE-001은 `UniverseService.pairRelation()`의 `findPair()` 양방향 reread가 accept/reject/cancel/deleteFollowing/unblock 모두의 pair lock 후 경로에 적용된 것을 확인했다.
- BE-002는 received/sent가 `requestPage→blogSlugs→findByUserIdInAndDeletedAtIsNullOrdered` batch map으로 전환됐고 빈 page early return이 있는 것을 확인했다.
- QA-001은 실제 MySQL 테스트에서 post 생성→request/accept→service 및 MockMvc UNIVERSE GET 200, delete/block 후 service `POST_002`와 HTTP 403, notification count 0을 확인하도록 보강된 것을 대조했다.
- QA-002 snapshot 직접 무토큰 경로와 DOC-001 14개 Universe operation bearer/status/schema 순회 검사를 대조했다.

### 검증

- Fresh backend 전체 `GradleWrapperMain test` → `BUILD SUCCESSFUL in 1m 27s`; XML 직접 집계 `70 files / 457 tests / 0 failures / 0 errors / 0 skipped`.
- Fresh backend targeted Universe/PostConcurrency/OpenAPI/Security → `BUILD SUCCESSFUL in 15s`; `7 files / 61 tests / 0 failures / 0 errors / 0 skipped`.
- Fresh backend `bootJar` → `BUILD SUCCESSFUL in 958ms`(UP-TO-DATE).
- Fresh frontend `npm test -- --run --maxWorkers=1` → `31 files / 336 tests passed`; `npm run lint` exit 0; `npm run build` exit 0(Vite chunk warning만 존재).
- `@Disabled|it.skip|describe.skip|test.skip|xit` 검색 결과 없음. `git diff --check -- qa .claude/team/qa` exit 0.
- `find docs/design -maxdepth 1 -type f -print` → `docs/design/AGENTS.md`만 존재.

### 최종 closure와 판정

- **CLOSED:** BE-001 pair reread, BE-002 request-list N+1, QA-001 M4 UNIVERSE 접근 회귀/철회·알림 불변, QA-002 snapshot 401, DOC-001 14개 OpenAPI operation 문서화.
- **OPEN/BLOCKER:** V-001 디자인 원본 부재. 기능/API/자동검증 범위는 **PASS·merge-ready YES**로 올렸지만, 1440px 시각 DoD가 닫히지 않아 전체 M5 final acceptance는 **BLOCKED·merge-ready NO**다.
- `qa/M5-review.md`에 1차 판정과 수정 루프 closure matrix를 기록하고, `STATE.md`를 현재 상태로 갱신했다. 디자인 원본 복구 후 브라우저 시각 검증만 남겼다.

## 2026-11-07 KST — M5 V-001 최종 실제 브라우저 인수

### 한 일

- 복구된 `docs/design/` 원본을 실제 Playwright + 시스템 Chrome에서 1440×1000 CSS viewport로 렌더하고 Blog/Settings Universe 화면의 visible assertion 후 기준 PNG를 만들었다. 빈 switcher/placeholder는 통과시키지 않았다.
- root runtime `http://localhost:5173`/API `http://localhost:18080`과 backend-owned `build/m5-visual-fixture-runner.mjs`의 새 합성 A~H 계정을 사용했다. runner는 기존 데이터를 삭제하지 않고, 비밀값을 출력하지 않았다.
- QA harness `qa/m5-visual-qa.mjs`를 추가·보완했다. 실제 API 계정 로그인, Settings 6개 카드, refresh, Blog owner/7 hero 상태, 두 번째 계정 전환, fresh guest context, RSS 제외 및 CTA href를 기록한다. `FAIL` 또는 final required matrix/account-switch 누락은 exit 1이 된다. `node --check`와 `git diff --check`를 통과했다.
- 첫 브라우저 캡처에서 `유니버스 신청 ✦`와 원본 `✦ 유니버스 신청`의 순서 차이를 V-002로 보고했다. frontend 수정 후 현재 소스·FE 테스트·최종 matrix가 prefix 카피로 일치한다. 중간 guest 실패는 동일 page의 in-memory auth 상태를 사용한 harness 결함으로 분리했고 fresh BrowserContext로 수정했다.

### 검증

- 최종 명령: `M5_PLAYWRIGHT_MODULE=/Users/zerovirus/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright M5_HEADLESS=1 node build/m5-visual-fixture-runner.mjs` → fixture setup 성공, QA child exit `0`.
- 최종 `build/m5-visual-qa/evidence.json`: viewport `1440×1000`, 14 screenshots, `failures=[]`; 실제 API 기준 Settings 6 cards/refresh, owner blog hero, incoming/outgoing/friend/follower/NONE/BLOCKED_BY_ME/UNAVAILABLE 7상태, second account, guest `✦ 유니버스 신청` + `/signin` href, no RSS 모두 PASS.
- 실제 API 계정 A의 Settings main rect `x=404,width=896`, root `min-width=1440px`, body `rgb(246,234,216)`, Blog hero `190px`를 확인했다. 원본 기준 PNG와 제품 PNG를 root가 직접 view_image로 대조했다.
- 보조 Tab 검증: guest Blog에서 Tab 28회 중 검색 input(`aria-label=검색`)과 hero `/signin` link에 포커스 도달. 반복 순환은 보조 증거로만 기록했다.
- root 최신 회귀 교차확인: backend `70 XML suites/457 tests/0/0/0`, bootJar 성공; frontend `31 files/338 tests`, lint/build exit 0. 전체 회귀는 제품 코드 변경 후 root가 재실행했으므로 QA가 중복 실행하지 않았다.

### 최종 판정

- **V-001 CLOSED.** 실제 디자인 원본 복구 및 1440px 브라우저 대조가 완료됐다.
- **V-002 CLOSED.** Blog guest/follower/NONE CTA prefix 카피와 `/signin` 유도 href가 승인 정본·현재 소스·브라우저 증거에 일치한다.
- **M5 전체 final acceptance PASS / merge-ready YES.** 기능/API/자동검증/시각 gate와 잔여 QA finding이 없다. QA는 commit/push/PR/merge를 수행하지 않으며 root의 Git 마감만 남는다.

### 산출물 경계

- QA가 수정한 것은 `qa/**`, `.claude/team/qa/**`뿐이다. PNG/JSON은 Git 무시 `build/m5-visual-qa/`에 있고 비밀값을 포함하지 않는다. 제품 코드·backend/frontend 역할 기록·governance·마일스톤 worklog·Git은 수정하지 않았다.

## 2026-11-07 16:53:41 KST — M5 PR #11 dev 통합 closeout

- root가 M5 PR #11의 실제 통합을 확인했다. 구현 커밋은 `060bbcf15d54025a8e098627be279143e4e3094a`, merge SHA는 `30a863000a918075e9e1530ec6627e2f9167989a`, 대상은 `dev`다.
- root 확인 origin/dev tree `ae40db51c5de9f9d262347802962579775ee7c77`가 QA가 최종 검증한 커밋 트리와 동일하다. QA는 read-only로 HEAD/base 및 clean tree를 확인했으며 추가 테스트를 반복하지 않았다.
- M5 독립 QA 최종 판정(PASS / merge-ready YES)과 BE-001/BE-002/QA-001/QA-002/DOC-001/V-001/V-002 CLOSED 상태는 dev 통합 후에도 유지된다.
- QA 소유 STATE만 closeout/dev 통합 상태로 갱신했다. `qa/M5-review.md`는 최종 PASS 기록을 유지하며, 제품/테스트/Git은 변경하지 않았다.

## 2026-11-09 KST — M6 QA 사전 검토·계획 대기

### 한 일

- `.claude/CONSTITUTION.md` → `AGENTS.md` → `.claude/team/qa/CLAUDE.md` → `STATE.md` → `qa/AGENTS.md` 순서로 읽고 `$brief` 절차에 따라 branch/status/log와 QA 이력을 확인했다.
- `docs/worklog/M6-comments-likes.md`, `docs/governance/meetings/M6-20261109-comments-likes.md` 최신 A안, REQUIREMENTS FR-COM-01~04/FR-LIKE-01~02/NFR-04/09, PRD §5.7/5.8/§7.2/§9.2-P/§9.3-③/§10~12, 디자인 원본과 기존 backend/frontend source를 읽기 전용 대조했다.
- 독립 3인 보완 A-v2가 `AWAITING_USER_APPROVAL`·HIGH로 사용자 승인 질문에 올라갔고 worklog에 제품 구현 미착수라고 기록된 것을 확인했다. source에서도 V1 `comments`/`post_likes`만 있고 제품 comment/like domain·API·PostDetail UI는 아직 없음을 확인했다.

### 산출물

- `qa/M6-review.md`에 승인 전 판정 경계, 현재 source baseline, `M6-CONTRACT-01~06` 정합성 쟁점, FR별 인수 매트릭스와 실제 HTTP/Chrome 1440px·MySQL 경쟁·query bound·transaction rollback·account stale 시나리오를 작성했다. 삭제 root reply GET/create, count 제외, 양방향 block/unlike, guest ACL, scalar lock 및 PostDetail 시각 범위를 A-v2 후보로 반영했다.
- 모든 매트릭스 항목은 `NOT RUN — 승인 대기`로 기록했다. PASS, 제품 테스트 실행, runtime 기동, 브라우저 캡처, DB mutation은 수행하지 않았다.

### 확인된 미해결

- A-v2 §6.4의 M6 notification row 불생성과 REQUIREMENTS FR-NOT-05/PRD §5.11·§11/12의 댓글·좋아요 알림 생성 요구가 충돌한다.
- A-v2 신규 interactions endpoint, 삭제 root reply GET/create, count 제외, 댓글 길이/DTO/placeholder, 양방향 block/unlike, blog/user/post lock 순서는 기존 정본에 완전히 확정되지 않았다. 사용자 승인 또는 정본/ADR 반영 전 QA 기준으로 고정하지 않는다.

### 검증·경계

- 실행한 read-only 명령: `git branch --show-current`(`feature/M6-comments-likes`), `git status --short`(root의 JOURNAL/M6 정본 변경만 확인), `git log --oneline -10`, source/doc `cat`·`sed`·`rg`·`find` 대조.
- backend/frontend 제품 테스트·lint/build·실제 HTTP/MySQL/Chrome은 실행하지 않았다. QA는 제품 코드·타 역할 테스트·PRD·governance·M6 worklog·Git을 수정하지 않았고, `qa/M6-review.md`와 QA STATE/WORKLOG만 편집했다.

## 2026-11-09 KST — M6 A-v2 승인·QA harness 준비

### 한 일

- root가 전달한 사용자 `A-v2 전체 승인`과 `docs/governance/meetings/M6-20261109-comments-likes.md` `APPROVED` 상태를 확인했다. `docs/REQUIREMENTS.md` FR-LIKE-03·M6 확정 세부 계약 및 `docs/PRD.md` PostDetail/M6 범위 동기화를 다시 읽었다.
- 승인된 기준으로 QA 문서의 상태·매트릭스를 `NOT RUN — 구현 대기`로 정합화했다. M6는 notification row를 생성하지 않고 FR-NOT-05는 M8 범위라는 계약, 양방향 block like 거부·PUBLIC 기존 like unlike·ACL 상실 row 보존/취소 거부, 삭제 root reply GET/create 분리, 살아 있는 root+reply count, PostDetail 시각 범위를 반영했다.
- 실제 API/Chrome 1440px 검증 harness `qa/m6-visual-qa.mjs`를 준비했다. synthetic fixture runner가 제공하는 계정·slug·post ID를 환경으로 받아 실제 FE/API를 확인하며, 비밀값을 로그·artifact에 기록하지 않고 결과는 ignored `build/m6-visual-qa/`에 둔다.

### 현재 경계

- source 구현·backend/frontend 테스트·runtime 기동·실제 HTTP/MySQL 경쟁·Chrome 캡처는 아직 실행하지 않았다. 제품 구현 완료와 root의 fixture/runtime 제공 후에만 harness를 실행한다.
- QA 판정은 보류다. 이 기록은 승인된 계약의 독립 검증 준비이며 PASS/merge-ready 주장이 아니다.

### 검증·소유권

- 승인 회의·REQUIREMENTS·PRD·기존 source를 read-only로 재대조하고 QA 문서/상태를 다시 읽었다. `git diff --check`는 QA 소유 변경에 대해 종료 시 확인한다.
- QA가 수정한 것은 `qa/**`, `.claude/team/qa/**`뿐이다. 제품 코드·src/frontend 테스트·governance/worklog 원본·Git은 수정하지 않았다.

## 2026-11-09 KST — M6 QA harness 정합성 보정

### 한 일

- 현재 source를 다시 확인해 baseline을 보정했다. backend 댓글/좋아요 domain·service·controller는 아직 없고, frontend `features/comment/**`·`features/like/**`의 API/types·M6 단위 테스트 초안은 존재하지만 `PostDetailPage` 실연결은 아직 없다. 이 부분 구현을 M6 최종 PASS로 해석하지 않는다.
- `qa/m6-visual-qa.mjs`에 실제 API envelope/status/cache-control/invalid Bearer와 guest/authenticated/second-account PostDetail, Pages 원본 `게시글 상세`, 새로고침, no-mutation passive capture, 선택적 delayed-route/account-switch stale probe를 추가했다. 이메일·비밀번호·access token은 evidence/stdout에 기록하지 않는다.

### 검증·경계

- `node --check qa/m6-visual-qa.mjs` 및 `git diff --check -- qa .claude/team/qa`가 성공했다.
- M6 backend/frontend 제품 테스트, lint/build, 실제 HTTP/MySQL 경쟁·rollback/query bound, Chrome 캡처는 **실행하지 않았다**. runtime fixture와 구현 closure가 제공된 뒤 root가 지정한 독립 QA 단계에서 실행한다.
- QA는 `qa/**`, `.claude/team/qa/**`만 수정했으며 제품 코드·타 역할 테스트·정본·Git은 수정하지 않았다.

## 2026-11-09 KST — M6 FE round1 재검토 및 immutable runtime acceptance

### FE 재검토

- FE 담당 round1 수정 후 `PostDetailPage.tsx`, `CommentSection.tsx`, `CommentRow.tsx`, comment/like API와 `m6PostDetailBehavior.test.tsx`를 다시 읽었다. FE-001은 mutation API 실패와 canonical GET 실패를 분리해 accepted DTO/placeholder를 유지하고 재시도 상태만 표시한다. FE-002는 post/account effect에서 `isLikeBusy`를 초기화하고 계정 전환 후 새 like 동작을 테스트한다. FE-003은 `rootPageGenerationRef`를 reply guard에 포함하고 root page 왕복 stale 응답 테스트를 추가했다. FE-004는 deleted root/reply action, capability flags, guest CTA, server `canDelete` 경계를 테스트한다.
- FE-001~004를 **source/unit 기준 CLOSED**로 `qa/M6-review.md`와 STATE에 갱신했다. 이는 backend DTO와 실제 browser/API 통합 PASS가 아니다. root 제공 FE `34 files / 357 tests`, lint/build PASS는 교차 참고로만 사용했다.

### acceptance 실행 경계

- 첫 실행은 API listen 상태에서 원본 JAR 재빌드 산출물 교체로 lazy class loading 오류가 난 runtime 경합 때문에 `POST /api/v1/auth/register`가 timeout됐다. root 로그로 제품 결함이 아닌 환경 원인을 확인했고, 해당 실행은 fixture/browser 단계에 진입하지 않았다.
- immutable JAR 기동 후 `M6_PLAYWRIGHT_MODULE=.../playwright`, system Chrome, `M6_HEADLESS=1`, `M6_REQUIRE_FULL=1`로 `qa/m6-acceptance.mjs`를 재실행했다. synthetic owner/actor register·signin·initial setup, PUBLIC post 2개, root/reply 21+ seed, guest/invalid Bearer/no-store, 실제 Chrome 1440px root/reply create·edit·soft-delete·like/unlike·refresh·rollback까지 **46 PASS / 0 FAIL**, screenshot 3개를 얻었다. credentials/token은 출력·artifact에 기록하지 않았다.
- 두 번째 실행 중 발견한 harness strict locator(`댓글 {rootId} 답글`가 pagination label과 substring 충돌)는 QA 소유 `qa/m6-acceptance.mjs`에서 `exact: true`로만 보정했으며 제품 파일은 수정하지 않았다. 보정 후 acceptance가 완료됐다.

### 검증

- `build/m6-acceptance/evidence.json`에 fixture ID·check·screenshot만 기록됐고 비밀값은 없다. `node --check qa/m6-acceptance.mjs`, `node --check qa/m6-visual-qa.mjs`, `git diff --check -- qa .claude/team/qa`를 재확인한다.
- 이 결과는 FE/API·browser 중간 acceptance 증거다. MySQL 경쟁/query bound/rollback/no-notification/ACL 전체 gate와 backend 최종 full test는 별도 증거와 대조해야 한다.
- M6-BE-03 query-string 공개 matcher는 `.param` 추정이 아니라 실제 immutable runtime URL `comments?page=0&size=20`, `parentId` reply page, guest interactions와 malformed Bearer 401/no-store를 evidence에서 확인해 **CLOSED (실제 HTTP 경계)**로 갱신했다. 나머지 backend 경쟁/ACL/query bound/no-notification gate는 대기한다.

## 2026-11-09 KST — M6 whole review 추가 FE Important findings

- whole review에서 FE round1 closure 이후 현재 source를 다시 대조했다. `FE-005`는 `CommentSection.tsx:209-235`에서 root page generation 변경으로 stale reply 응답의 `finally`가 loading 해제를 건너뛰어 root page 왕복 후 `replyLoading[rootId]`가 true로 고착될 수 있는 문제다. 기존 stale payload 비주입 테스트만으로 spinner/pagination 회복을 증명하지 못한다.
- `FE-006`은 `PostDetailPage.tsx:141-155`의 canonical interactions GET 실패와 `CommentSection.tsx:181-203`의 canonical comments GET 실패가 `401/AUTH_004`, `403/POST_002`, `404/POST_001` 권한·리소스 거부에서 이전 interaction/comments 상태를 유지하는 문제다. 회의 §6.3의 권한 상실 시 개인화 상태 폐기·flags/count 비노출 계약과 어긋날 수 있다. network/5xx accepted-state 보존과 거부 응답 clear를 분리해야 한다.
- `FE-005`·`FE-006`을 **OPEN / Important**로 `qa/M6-review.md`와 STATE에 기록하고 frontend round2 수정·독립 재리뷰를 요청했다. 기존 FE-001~004 CLOSED와 M6-BE-03 CLOSED는 유지한다.
- 이전 immutable runtime `46 PASS / 0 FAIL` 및 screenshot 3개는 round2 finding 발견 전 중간 증거이며 M6 final PASS가 아니다. backend 보강·helper·whole review가 끝난 뒤 수정 소스 기준 acceptance를 재실행한다.
- 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance·Git은 수정하지 않았다.

## 2026-11-09 KST — M6 진행 중 source 재확인

- 동시 구현 변경을 다시 읽어 현재 상태를 보정했다. backend에는 `Comment`·`Like` entity와 `PostInteractionLockService` 초안이 추가됐고, frontend에는 comment/like feature 및 `PostDetailPage` 연결 초안이 추가됐다. 그러나 repository·service·controller/OpenAPI와 최종 runtime/test closure는 아직 QA 인수 전 단계다.
- 따라서 QA STATE/`M6-review.md`의 표현을 “미착수”에서 “부분 구현 진행·인수 대기”로 정정했다. 실제 HTTP/MySQL/Chrome 및 제품 테스트는 계속 **미실행**으로 유지한다.

## 2026-11-09 KST — M6 synthetic mutation acceptance harness 준비

### 한 일

- root 요청에 따라 `qa/m6-acceptance.mjs`를 추가했다. runner는 random synthetic owner/actor를 register→signin→initial-setup하고 PUBLIC 글 2개를 만든 뒤 API로 root 댓글 21개와 한 root의 답글 21개를 seed한다. 기존 데이터 조회·삭제·청소는 하지 않는다.
- Playwright Chrome 1440px에서 실제 계정 로그인과 PostDetail을 열고, root 댓글 작성·답글 작성·root/reply page 2 이동·댓글 수정·soft-delete·좋아요·취소·새로고침을 사용자 label 기반으로 수행한다. 실패 like 응답을 주입해 optimistic rollback도 확인한다. 설계 원본 PostDetail 캡처와 PUBLIC guest/invalid Bearer/no-store HTTP 경계도 함께 기록한다.
- evidence에는 synthetic 대상 user/blog/post/comment ID와 PASS/FAIL/check 이름만 남기며, random email/password/access token/본문 marker는 stdout·artifact에 저장하지 않는다.

### 검증·경계

- `node --check qa/m6-acceptance.mjs` 및 `git diff --check -- qa .claude/team/qa`가 성공했다.
- 이 runner와 기존 `qa/m6-visual-qa.mjs`는 아직 실행하지 않았다. backend API/service/controller/OpenAPI 구현 closure와 runtime 준비 후 실제 HTTP/MySQL/Chrome 인수에서 실행한다. notification row 불변·suspended/deleted/block ACL 및 경쟁/query bound는 backend 독립 증거와 조합한다.

## 2026-11-09 KST — M6 FE 독립 source review

### 검토 범위

- `dev c46a26d` 대비 `frontend/src/pages/PostDetailPage.tsx`, untracked `features/comment/**`, `features/like/**`, `CommentSection/CommentRow`, M6 FE tests를 읽었다. 승인 회의 §6 A-v2, REQUIREMENTS FR-COM-01~04/FR-LIKE-01~03, PRD §9.9/§7.2/§11~§12, 디자인 원본과 대조했다.
- root의 FE `34 files / 353 tests / lint / build PASS`는 참고 증거로만 사용했고 FE 회귀를 중복 실행하지 않았다. 제품/테스트 파일은 수정하지 않았다.

### Findings

- `FE-001` Important OPEN: `CommentSection.tsx:228-242` canonical follow-up GET 실패가 create/edit/delete `catch(:324-328,362-367,394-399)`의 before-state rollback으로 이어져, 이미 서버에 커밋된 mutation을 UI에서 실패처럼 되돌린다. create 재시도 중복·edit/delete stale가 가능하다. 테스트 `m6PostDetailBehavior.test.tsx:291-305`도 이 잘못된 동작을 검증한다.
- `FE-002` Important OPEN: `PostDetailPage.tsx:75-112,183-193,281-289`에서 세대 전환 시 `isLikeBusy` reset이 없고 stale mutation finally가 skip되어 post/account 전환 뒤 좋아요 버튼이 영구 disabled될 수 있다. 기존 늦은 응답 테스트는 enabled 복귀를 확인하지 않는다.
- `FE-003` Important OPEN: `CommentSection.tsx:201-226` reply guard가 component generation과 root ID별 request id만 보며 root page 전환을 무효화하지 않아, page 0→1→0 중 오래된 reply 응답이 현재 root card에 재주입될 수 있다. 순차 page 테스트만 있고 지연 race 테스트는 없다.
- `FE-004` Minor OPEN: server `canReply/canEdit/canDelete`와 삭제/탈퇴 placeholder 렌더 방향은 맞지만 deleted root/reply action 비노출·비작성자 action guard·owner delete 경계의 FE 테스트 증거가 부족하다.

### 판정·경계

- FE-001~003은 독립 source review 기준 Important OPEN이며, 수정 루프와 재검토 전 FE PASS 또는 M6 final acceptance를 주장하지 않는다. FE-004는 Minor test gap이다.
- 실제 API/브라우저/rollback은 backend runtime 준비 후 별도 실행한다. QA는 `qa/**`, `.claude/team/qa/**`만 수정했다.
- QA는 `qa/**`, `.claude/team/qa/**`만 수정했으며 제품 코드·타 역할 테스트·정본·Git은 수정하지 않았다.

## 2026-11-09 KST — M6 읽기 권한 상실 독립 acceptance 시나리오 보강

### 한 일

- `qa/m6-acceptance.mjs`의 기존 actor browser flow 끝에 QA 전용 synthetic access-loss 시나리오를 추가했다. owner token은 메모리에만 보관하고, REQUIREMENTS FR-POST-02의 전체 `UpdatePostRequest` 필드로 같은 합성 PUBLIC 글을 `PRIVATE`로 전환한다.
- actor의 열린 PostDetail에서 stale like를 다시 눌러 `POST /likes`의 `LIKE_001`을 받고, FE가 호출하는 canonical interactions GET의 `POST_002`를 실제 브라우저 응답으로 확인하도록 구성했다. 이후 좋아요 action/count, 댓글 heading/composer, 댓글 action button이 제거되는지 확인한다.
- 기존 46-check 경로와 1440px screenshot 흐름은 보존했고, 이 시나리오는 최종 FE round3·immutable backend runtime 고정 뒤 실행한다. 기존 `build/m6-acceptance/evidence.json` 46/46에는 아직 반영되지 않았다.

### 검증·경계

- `node --check qa/m6-acceptance.mjs` 및 `git diff --check -- qa/m6-acceptance.mjs` 성공.
- 제품 코드·타 역할 테스트·정본·Git은 수정하지 않았다. 실제 access-loss HTTP/Chrome 결과와 M6 최종 PASS는 아직 주장하지 않는다.

## 2026-11-09 KST — M6 최종 독립 QA closure

### 최종 backend 대조

- root가 보존한 immutable final XML `build/m6-evidence/backend-final/` 77 suites를 직접 파싱해 **490 tests / failures 0 / errors 0 / skipped 0**을 확인했다. `CommentBehaviorTest`, `CommentBatchTest`, `CommentControllerTest`, `LikeControllerTest`, `LikeConcurrencyTest`, `M6InteractionConcurrencyMySqlTest`, `M6InteractionCrossRaceMySqlTest`, `OpenApiConfigTest`의 소스·테스트 이름·XML 결과를 승인 A-v2와 대조했다.
- 100-root reply bounded/query statement bound, no-notification, deleted root/reply, BLOCKED/UNIVERSE/PRIVATE ACL, SUSPENDED actor/viewer, lock 경쟁·stale snapshot·rollback과 OpenAPI 경계가 최종 XML 및 source에 반영됐다. M6-BE-01~07은 CLOSED로 기록한다.

### FE 및 실제 HTTP/Chrome closure

- FE whole-review의 WR-01/02/03이 ADDRESSED·새 Critical/Important 없음으로 재검토됐고, root fresh `34 files / 369 tests`, lint/build exit0를 인계받아 `qa/M6-review.md`의 FE-001~006을 모두 CLOSED로 갱신했다. FE-005는 `loadRootPage` 세대 시작 시 reply map/request를 초기화하는 round2 source invariant와 WR-01 증거를 연결했고, FE-006은 권한 거부 시 interaction/comments clear와 세대 guard를 WR-02/03에 연결했다.
- 최종 immutable runtime(JAR SHA-256 `f0cbf9c2fd027510b03a00ce752f2f39040f5870bafaef360895343856d056ab`)에서 `M6_REQUIRE_FULL=1 M6_HEADLESS=1`로 `qa/m6-acceptance.mjs`를 실행했다. **52 PASS / 0 FAIL**, fixture ID만 evidence에 남았다. 기존 46개에 owner PUBLIC→PRIVATE 전환, actor stale like `LIKE_001`, canonical `POST_002`, like count/comments/comment actions clear 6개를 추가로 확인했다.
- 실제 HTTP guest comments/interactions, query string, malformed Bearer AUTH_004, private/no-store, authenticated parentId reply page와 Chrome 1440px owner/actor root·reply page2, create/edit/delete, like/unlike, refresh, optimistic rollback을 확인했다. `design-post-detail.png`, `owner-initial.png`, `actor-final.png`를 직접 열어 Pages 원본과 PostDetail 레이아웃/카드/댓글/reply 시각을 대조해 M6 시각 범위 PASS로 판정했다.

### 최종 판정 및 경계

- FR-COM-01~04/FR-LIKE-01~03, 자동검증, 경쟁/query-bound/rollback/no-notification, FE stale·권한, PostDetail 시각 gate를 **PASS / merge-ready: Yes**로 판정했다. QA blocker는 없다.
- BlogPage/PostCard count 변경과 M8 notification 생성은 승인 범위상 이번 M6 PASS에서 제외한다. Git 통합·마일스톤 closeout은 root 소유 후속이다.
- QA는 `qa/**`, `.claude/team/qa/**`만 수정했고 제품 코드·타 역할 테스트·정본·Git은 수정하지 않았다.

## 2026-11-09 KST — M6 dev 통합 및 QA closeout 확인

- root 보고의 PR #12(`https://github.com/0-virus/zero-verse/pull/12`)를 기준으로 M6 구현 commit `55aedea966a45dc0e88a6b0d8926e709b366050e`가 `dev`에 merge된 것을 확인했다. merge SHA는 `4399f73acf19d66d96fafd2c4784c6a03fcfa282`이며, root가 확인한 통합 tree는 `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`다.
- QA 최종 판정(PASS / merge-ready Yes), backend 77 suites/490 tests 0/0/0, FE 34 files/369 tests·lint/build PASS, immutable runtime 52/52 PASS 및 1440px PostDetail 시각 PASS는 dev 통합 후에도 유지된다. M6 QA blocker는 없고 QA closeout을 완료했다.
- QA는 제품 코드·타 역할 테스트·Git을 수정하지 않았으며, 이 closeout은 `.claude/team/qa/STATE.md`와 이 append-only WORKLOG만 갱신했다.

## 2026-11-09 KST — M7 피드·검색 승인 전 독립 QA 준비

### 범위와 정본 대조

- 헌법·루트/QA 지침·QA `STATE.md`를 먼저 읽고, `docs/worklog/M7-feed-search.md`, `docs/governance/meetings/M7-20261109-feed-search.md`, `docs/REQUIREMENTS.md` FR-FEED-01~02/FR-SEARCH-01/NFR-04/09, `docs/PRD.md` §5.9/5.10·§7·§9-G/N/S·§10~12, `docs/design/DESIGN-SYSTEM.md` §8.1/8.8 및 Main Feed v2/Pages 원본을 직접 대조했다.
- M7 회의는 현재 `REVIEWING`이고 A안은 사용자 승인 전 미확정이다. 따라서 이 항목은 계획·인수 준비만 수행하고 제품 구현, 제품/타 역할 테스트 수정, runtime 기동, M7 acceptance 실행 또는 PASS 판정을 하지 않았다.
- `feature/M7-feed-search` HEAD `bc99cba`를 read-only 확인했다. `MainPage`/`SearchPage`/`TopBar`는 골격이며 M7 feed/search 서비스·검색용 `content_text` 구현은 아직 없다. `SecurityConfig`의 public matcher와 기존 security 테스트는 실제 API 구현의 증거로 세지 않았다.

### 준비 산출물과 위험

- `qa/M7-review.md`에 FR-FEED-01/02·FR-SEARCH-01별 ACL/visibility, DB filter-before-page, deterministic sort, count/facet, text cache/migration, error/no-store, stale account/keyword, 1440px browser 인수 매트릭스를 기록했다. 모든 항목은 `NOT RUN`이다.
- M6 `qa/m6-acceptance.mjs`의 memory-only fixture·실제 HTTP·Playwright Chrome 패턴을 재사용 후보로 확인했지만 M7 전용 harness는 만들지 않았다. M6 PostDetail visual harness는 M7 근거로 직접 재사용하지 않는다.
- 승인 blocker로 A안 미확정(PLAN-001), PRD §7.1의 `/universe/summary`와 A안 §6.5의 세 universe list endpoint count 공급원 불일치(PLAN-002), REQUIREMENTS/PRD의 HTML text와 A안 §6.4 canonical `content_text` 계약 동기화 필요(PLAN-003), matcher와 실제 API 경계(PLAN-004)를 분리 기록했다.
- 실제 최종 검증은 격리 MySQL/API/FE와 1440px Chrome에서 수행해야 한다. Java 21, Node, `frontend/node_modules`, system Chrome와 기존 Playwright 모듈은 존재하지만 현재 Colima socket이 없어 runtime은 가동하지 않았다.

### 검증·소유권

- read-only 명령으로 branch/HEAD/status, `qa/**` 파일 목록, M7 endpoint/source marker, Java/Node/Chrome/Playwright/Colima 상태를 확인했다. 확인 결과 branch=`feature/M7-feed-search`, HEAD=`bc99cba`, Colima socket absent, Java 21/Node 26/Chrome/Playwright present다.
- M7 backend/FE 테스트, migration, HTTP, MySQL query-bound/rollback/no-notification, Chrome screenshot, `node --check`는 실행하지 않았다. M7 evidence/credential/token artifact도 생성하지 않았다.
- 수정 파일은 `qa/M7-review.md`, `.claude/team/qa/STATE.md`, 이 QA WORKLOG뿐이다. 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance/worklog 원본·Git 및 사용자 미추적 파일은 수정하지 않았다.

## 2026-11-09 KST — M7 A-v2 승인 후 acceptance harness 준비

### 범위와 설계

- 사용자의 `승인된 작업 시작해.` 지시와 A-v2 전체 승인, `REQUIREMENTS.md` §6.10-A 및 회의 §6을 다시 읽고 승인 전 `NOT RUN` 문서 경계를 `APPROVED / IMPLEMENTATION IN PROGRESS / ACCEPTANCE PREP`로 정합화했다. PRD route map `/universe/summary`는 기존 friends/following/followers page0-size1 total로 동기화될 대상이며, runtime 중지는 승인 blocker가 아닌 실제 인수 전 실행 선행조건으로 기록했다.
- `qa/m7-acceptance.mjs`를 추가했다. runner는 owner/actor/friend/one-way/unrelated/blocked/suspended/deleted 합성 계정·블로그를 HTTP로 만들고, actor→owner/one-way/suspended/deleted, actor↔friend, actor→blocked 후 block 관계를 구성한다. owner PUBLIC/UNIVERSE/PRIVATE/draft, rich inline-mark/entity/literal wildcard, 21개 page fixture와 decoy 글을 생성한다.
- 상태 변경 endpoint가 없는 SUSPENDED/deleted author는 기존 사용자 DB에 접근하지 않는다. runner가 ID만 포함한 `build/m7-acceptance/fixture-seed.json`을 쓴 뒤, root가 합성 M7 DB에서만 상태를 변경하고 `status-ready.json`을 만들면 `M7_WAIT_FOR_STATUS_FIXTURES=1`로 이어서 실행한다. 상태 fixture가 없으면 full runner는 NOT RUN failure로 멈춘다.

### 검증 범위

- 실제 HTTP: public/universe/search ACL·관계(`all/friends/following`)·deleted/suspended/blocked·PUBLIC/UNIVERSE/PRIVATE/draft, guest/malformed/expired/deleted-user 인증 표, 21+ page/tie/query params, wildcard literal, inline mark/`&`/`<tag>`/entity 오탐, ALL distinct/matchedTypes/counts snapshot, owner draft ACL, batch count DTO/no-store/envelope/OpenAPI, viewCount·notification side-effect 불변을 확인하도록 구성했다.
- migration/maintenance gate는 source/test와 MySQL 증거를 조합한다. V4/V5 keyset100, empty doc/image-only, malformed/non-doc/unknown node, 100/101/201/ID gap/late-invalid, 전체 DML transaction rollback/Flyway history, repair/re-run/backup-restore, versioned `PostTextExtractorV1`, offline verify/rebuild와 old-writer 복귀 경계를 `qa/M7-review.md`에 명시했다.
- 실제 Chrome 1440x1000: Main Feed 원본/제품 screenshot, 3 관계 탭의 canonical `friends`/`following`, guest protected-tab no-call/login prompt, TopBar→`/search`, 5 facet tabs/post-centric copy, URL/reload/back, owner private→actor clear, delayed stale account response, real API result 및 viewport를 확인하도록 구성했다.
- source revision/runtime source manifest/immutable JAR SHA-256은 environment로 주입해 서로 다른 산출물이 섞이지 않았음을 확인한다. source revision과 JAR SHA-256은 서로 다른 값일 수 있어 runtime source manifest와 source revision을 비교한다.

### 실행·보안 경계

- 기본 대상은 API `http://localhost:18087`, FE `http://localhost:5177`, Playwright system Chrome이며, root가 immutable final JAR/FE/isolated MySQL를 고정한 뒤에만 실행한다. 현재는 runtime을 호출하지 않았다.
- `M7_EXPIRED_TOKEN`은 exact `AUTH_002` 검증용으로만 memory/env에서 받아 evidence/stdout에 출력하지 않는다. register/signin token·password·email·content marker는 모두 memory-only다. seed/evidence에는 합성 ID·check·screenshot 파일명만 남긴다.
- 사용자 `13307` DB, 기존 M5 data, `.env.local`, root untracked files에 접근·수정하지 않았다. 제품 코드·backend/frontend 테스트·root helper/Git은 수정하지 않았다.

### 검증 결과와 미해결

- `node --check qa/m7-acceptance.mjs` 성공. 실제 backend/frontend/API/DB/Chrome와 M7 full tests는 아직 실행하지 않았다.
- 최종 실행 전 root가 status-ready 합성 fixture, expired token, API/FE runtime, source/runtime SHA를 전달해야 한다. 실행 신호 전 harness를 호출하지 않는다.

## 2026-11-09 KST — M7 harness 실행 전 독립 증거 보강

### 보강 내용

- `/notifications` GET은 M8 미구현/범위 의존이라 side-effect gate에서 제거했다. `qa/m7-acceptance.mjs`는 root 소유 `build/m7-runtime.py sql`을 child process stdio로 호출해 합성 actor의 `notifications` COUNT와 ID/receiver/actor/type/target/timestamp/read/delete fingerprint를 전후 읽고 비교한다. SQL 결과·자격증명·원문은 출력/evidence에 남기지 않는다. helper 실패는 available=false로 기록되어 required full gate를 통과하지 못한다.
- `status-ready.json`은 `ready: true`와 `suspendedUserId`, `deletedUserId`, `suspendedPostId`, `deletedPostId` 네 값이 현재 seed ID와 정확히 일치해야 한다. 빈 JSON/누락/불일치는 ready로 인정하지 않는다.
- source/runtime 검증은 환경값 존재만으로 통과시키지 않는다. 기본 `build/m7-evidence/tested-version.json`(`M7_VERSION_MANIFEST` override 가능)의 `files[]`를 repository-relative로 다시 SHA-256하고 path\0hash\n aggregate를 계산해 `sourceSha256`과 비교한다. manifest `jarPath`의 immutable JAR도 harness가 직접 SHA-256해 `jarSha256`과 비교한다. path escape, 누락 파일, manifest/hash 불일치는 FAIL/NOT RUN이다.

### 검증·경계

- `node --check qa/m7-acceptance.mjs`, trailing whitespace 검사, `git diff --check -- qa .claude/team/qa`를 재실행한다.
- API/FE/JAR가 아직 최종 준비되지 않았으므로 실제 API 호출, SQL helper 호출, Chrome, DB, M7 full tests는 실행하지 않았다. expired token은 root가 합성 runtime key로 발급해 `M7_EXPIRED_TOKEN` memory/env로 주입할 때만 `AUTH_002`를 확인한다.
- 수정 파일은 `qa/m7-acceptance.mjs`, `qa/M7-review.md`, `.claude/team/qa/STATE.md`, 이 WORKLOG뿐이다. 제품 코드·root helper·타 역할 테스트·Git은 수정하지 않았다.

## 2026-11-09 KST — M7 harness 오류 응답·snapshot 비교 보강

### 한 일

- `qa/m7-acceptance.mjs`의 `read-snapshot-no-mutation`을 `JSON.stringify(Set)` 비교에서 정렬된 ID 배열 기반 양방향 diff로 교체했다. `missing`과 `unexpected`를 모두 evidence detail에 남기며, 고정 synthetic Set을 이용한 QA self-check로 두 방향 검출을 먼저 확인한다.
- malformed/missing/expired/deleted 인증, feed validation, search blank/missing validation의 각 오류 HTTP 응답에 `Cache-Control: private, no-store`와 공통 envelope 검사를 추가했다. 기존 성공 응답 검사는 유지한다.

### 검증·경계

- `node --check qa/m7-acceptance.mjs` 성공. QA harness·STATE·WORKLOG의 trailing whitespace 검사와 추적 QA 기록의 `git diff --check`도 성공했다.
- API/DB/SQL helper/Chrome 및 M7 full acceptance는 runtime 준비 신호 전이므로 실행하지 않았다. 제품 코드·타 역할 테스트·root helper·정본·Git은 수정하지 않았다.

## 2026-11-09 KST — M7 acceptance run1 실패 진단 및 fixture 보정

### 관측·진단

- root가 실행한 고유 output `build/m7-acceptance-20261109-230615`의 `evidence.json`과 `build/m7-evidence/acceptance-1.log`를 읽었다. OpenAPI, owner/actor/friend register/signin/initial-setup까지 PASS했지만 `oneWay-initial-setup`에서 중단되어 screenshot·status fixture·HTTP matrix·Chrome evidence는 생성되지 않았다.
- `build/m7-evidence/api-runtime.log`에서 실제 `PUT /api/v1/blogs/me/initial-setup` 응답이 400 `BLOG_003`(주소 형식 오류)이며 request `urlSlug=m7-oneWay-...`의 camel-case가 원인임을 확인했다. 요구사항의 slug lowercase 계약을 완화하지 않고 QA fixture 생성식만 `.toLowerCase()`로 보정했다.

### 검증·경계

- `node --check qa/m7-acceptance.mjs`와 trailing-whitespace 검사를 보정 후 재실행해 성공했다.
- run1은 실패로 유지한다. 제품 버그·PASS·시각 gate 완료로 기록하지 않으며, root가 새 고유 run2를 실행한 뒤 결과를 다시 독립 검토해야 한다. credentials/token은 로그/evidence에 기록하지 않았다.

## 2026-11-09 KST — M7 browser acceptance false-positive 보정 및 상호작용 gate 확장

### 한 일

- Main Feed의 owner PUBLIC blog title은 actor의 following feed에서 정상 노출될 수 있으므로 `browser-main-no-private`를 blog title body 문자열 검사에서 owner PRIVATE/draft의 실제 `article > h2` 카드 검사로 좁혔다.
- Search의 result/owner-private/account-switch/stale 검사를 query heading/input 문자열이 아닌 실제 `article > h2` PostCard로 바꾸고, `waitForSearchResponse`/`navigateSearchAndWait`로 API 응답 완료를 기다리도록 했다. 고정 250/500ms 대기 의존을 제거했다.
- Chrome 1440px flow에 5개 facet 탭 실제 클릭과 URL/type/count 응답 대조, 21+ 검색 pagination page0/page1·disjoint·back 복원, 고유 keyword empty state·무카드, 일회성 intercepted HTTP 500·재시도 후 실제 API/card 복귀를 추가했다. guest feed도 public API 응답 완료 후 protected-tab no-call을 판정한다.

### 검증·경계

- `node --check qa/m7-acceptance.mjs`, QA 파일 trailing-whitespace 검사 및 추적 QA 기록 `git diff --check` 성공.
- run1의 setup 실패는 그대로 FAIL이며 browser flow는 아직 실행되지 않았다. 제품 코드/테스트·root helper·Git은 수정하지 않았고, 새 고유 run2 결과와 screenshot을 받은 뒤 독립 판정한다.

## 2026-11-09 KST — M7 run2 직전 렌더 안정성 보강

- `waitForSearchReady`가 `검색 결과를 불러오는 중...` indicator가 숨겨질 때까지 조건 대기하도록 했고, facet 탭은 API snapshot count가 실제 `[role=tab]` label에 반영될 때까지 `page.waitForFunction`으로 기다리도록 했다. fixed sleep은 추가하지 않았다.
- `node --check qa/m7-acceptance.mjs`, trailing-whitespace 검사, 추적 QA 기록 `git diff --check` 성공. run2 및 Chrome/screenshot evidence는 아직 실행 전이다.

## 2026-11-09 KST — M7 acceptance run2 HTTP 통과·browser selector 중단

### 관측·분류

- `build/m7-acceptance-20261109-231741/evidence.json`과 `build/m7-evidence/acceptance-2.log`를 독립 집계했다. synthetic fixture/status-ready, public/universe/search ACL·pagination·facet/text/no-view/no-notification, suspended/deleted auth와 source manifest 354개 파일/JAR SHA 재계산까지 **203 PASS / 0 FAIL / 0 NOT RUN**이었다.
- runner는 browser 시작 후 TopBar 검색과 Main Feed 피드 검색이 모두 `getByRole('searchbox', {name:'검색'})`에 매칭되어 strict-mode exception으로 중단됐다. 이는 제품 결함이 아닌 QA selector 결함이다. 원본 Main Feed와 제품 `main-actor.png` 및 source SHA는 생성됐지만 Search 제품 screenshot·후속 browser matrix는 생성되지 않았다.
- `searchInput`은 `header` scope, reload input sync용 `searchInputValue`는 `main` scope의 exact role selector로 보정했다. run2는 runner failure가 있으므로 M7 PASS로 기록하지 않는다.

### 검증·경계

- selector 보정 후 `node --check qa/m7-acceptance.mjs`, trailing-whitespace 검사와 추적 QA 기록 `git diff --check` 성공.
- 새 고유 run3에서 browser 전체 flow·5 facet/pagination/back/empty/retry/account stale/guest protected tab 및 Search screenshot을 재실행해야 한다.

## 2026-11-09 KST — M7 run3 직전 Search input scope 정정

- SearchPage source를 재확인해 keyword 결과 상태에는 main 검색 input이 없고, keyword 없는 상태의 label은 `검색어`이며 TopBar만 `aria-label="검색"`임을 확인했다. `searchInputValue`를 `header` scope로 정정했다.
- `node --check qa/m7-acceptance.mjs`와 trailing-whitespace 검사를 통과했다. 제품 source는 수정하지 않았다.

## 2026-11-09 KST — M7 run3 결과·Search page0 기대값 독립 보정

### 관측·분류

- run3 output `build/m7-acceptance-20261109-232138`은 **256 PASS / 3 FAIL / 0 NOT RUN**으로 종료됐다. 실패는 `browser-search-result`, `browser-search-retry-success`, `browser-account-switch-actor-result`이며, screenshot에서 기본 size20 page0가 최신 `owner.page[20]`부터 시작하고 오래된 `owner.multi`는 page1인 것이 확인됐다. HTTP/API/제품 search 결과 결함이 아니라 harness가 고정 fixture title을 기대한 오류다.
- run3는 browser screenshot 4개를 생성했지만 runner failures가 있어 최종 PASS가 아니다. 실제 Search response page0은 30 total·20 items·2 pages로 보였고 facet/pagination/back/empty/retry-error/stale/guest checks는 통과했다.

### 보정·검증

- 초기/재시도/계정전환 Search response의 실제 `data.items[0]`을 읽어 ID/title/publishedAt/author/blogSlug 기반 canonical `/blog/{slug}/{id}` 또는 own draft `/edit/{id}`를 계산하고, 렌더 `article > h2` 및 anchor href와 대조하도록 변경했다. 고정 page 정렬 가정과 임의 heading/any-text 검사를 사용하지 않는다.
- `node --check qa/m7-acceptance.mjs` 및 trailing-whitespace 검사 성공. 새 고유 run4에서 재실행 전이며 제품 source는 수정하지 않았다.

## 2026-11-09 KST — M7 run4 최종 독립 QA·시각 판정 및 dev 통합 전 closeout

### 최종 증거 재검증

- root가 실행한 고유 output `build/m7-acceptance-20261109-232636`의 `evidence.json`과 `build/m7-evidence/acceptance-4.log`를 QA가 다시 집계했다. **259 PASS / 0 FAIL / 0 NOT_RUN / `failures=[]`**, `statusFixtureReady=true`이며, run1~3 실패 후 보정한 동일 QA harness의 전체 HTTP·DB·Chrome 경로가 종료됐다.
- run4는 8개 합성 계정과 합성 MySQL만 사용했다. public/universe/search ACL·정확한 `AUTH_004`/`AUTH_002`/`USER_001`, no-store/envelope, relation/facet/count/paging, wildcard·본문 extractor 경계, view/notification side-effect 불변, RR snapshot, 5 facet 탭·pagination/back·empty·500 retry·owner/actor 전환·stale·guest protected no-call을 실제 API/브라우저로 확인했다. credentials/token은 출력·artifact에 남기지 않았다.
- `build/m7-evidence/tested-version.json`의 354 source file hash를 각 파일에서 독립 재계산하고 path\0hash\n aggregate를 비교해 source `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c` 일치를 확인했다. `build/m7-api-data/runtime.jar`의 SHA-256 `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`도 manifest/JAR와 일치했다.
- 보존 XML에서 backend 91 suites/538 tests, failures/errors/skips 0/0/0을 확인했고, Feed/Discovery query-bound·Search query/snapshot·M7 HTTP/ACL/snapshot·PostTextExtractor/Cache·PostSearchTextMigration 5·Maintenance 4가 모두 통과했다. `migration-snapshot-fix2/summary.json` 11 tests도 0/0/0이다. FE log는 41 files/405 tests, lint/build exit0다.

### 독립 시각 판정

- `design-main-feed.png`(1440×1445), `design-search.png`(1440×1007), `main-actor.png`(1440×4114), `search-actor.png`(1440×3755)를 직접 열어 원본 Main Feed/Pages Search와 제품을 비교했다. 1440px 폭, cream/ink/purple 토큰, hero·탭·단일 컬럼 카드·배지/meta/title/snippet·border/shadow·pagination은 M7 범위에서 **PASS**다.
- Pages 원본의 `SCREENS` 디자인 보드 chrome은 제품 shell 요구사항이 아니므로 차이로 세지 않았다. hero 하단 pixel staircase는 M4/M5 기존 baseline, Main 검색 입력은 승인된 M7 추가 범위로 분리했다. 신규 시각 blocker는 없다.

### 최종 판정·소유권

- run1 slug, run2 searchbox selector, run3 page0 고정 item 기대는 모두 QA-only finding으로 CLOSED했으며 제품 Critical/Important/Minor finding은 없다.
- `qa/M7-review.md`를 최종 FR/NFR·FE/브라우저 매트릭스와 evidence 경로로 갱신하고, QA STATE를 **M7 QA PASS / merge-ready Yes (dev 통합 전)**으로 갱신했다. `dev` 통합·PR·root closeout은 QA 소유가 아니며 후속 gate로 남겼다.
- A-v2 owner draft `/edit/:id`는 FE `searchBehavior.test.tsx` 행동 테스트·`SearchPage.tsx` source·run4 owner HTTP ACL·harness href 계산으로 확인했다. run4 Chrome에서 owner draft 카드를 직접 클릭하는 별도 check는 실행하지 않았음을 문서에 명시했으며, browser-only 운영 정책이 있으면 후속 micro-check 대상이다. 이는 제품 finding이 아니다.
- QA는 `qa/**`, `.claude/team/qa/**`만 수정했으며 제품 코드·타 역할 테스트·정본·Git은 수정하지 않았다.

## 2026-11-09 KST — M7 PR #13 dev 통합 후 QA closeout

- root 보고의 PR #13(`https://github.com/0-virus/zero-verse/pull/13`) merge를 read-only 대조했다. merge SHA는 `08863207b8385177b619091410bbbd43e52a337c`, tested head는 `2b984b6a2fc3aafd6d650c6ccb1cd8213a528545`, 현재 브랜치는 `dev`다.
- 현재 `dev` tree `dc68037477c6fed6b77d537235d79093803a983e`와 tested head tree가 동일함을 확인했다. 기존 backend 538 tests, frontend 405 tests/lint/build, 실제 HTTP+Chrome `259 PASS / 0 FAIL / 0 NOT_RUN`, 독립 1440px 시각 PASS 및 owner draft 증거 경계는 유지된다.
- `qa/M7-review.md` 상태를 **DEV INTEGRATED**로 갱신하고 PR/tree 정보를 §9에 기록했다. QA는 추가 테스트·제품 수정·Git 조작을 하지 않았으며 M8은 착수하지 않는다.

## 2026-11-10 KST — M8 Task5 acceptance matrix·HTTP/Chrome harness 준비

### 한 일

- `.claude/CONSTITUTION.md` → `AGENTS.md`/`CLAUDE.md` → QA `CLAUDE.md`/`STATE.md` → `qa/AGENTS.md` 순서로 읽고, M8 Task5 brief, `docs/worklog/M8-notifications.md` 인수 매트릭스, 회의 `M8-20261110-notifications.md` §2/§5/§9/§10, `REQUIREMENTS` §6.11/§6.11-A, `PRD` §5.11/§7/§9.11/§10~§12, 디자인 알림 원본, `ADR-0007-notifications-contract.md`를 직접 대조했다.
- `qa/M8-review.md`를 추가해 A-v2 승인·구현 진행·실제 인수 미실행 경계를 명시했다. FR-NOT-01~05, 전체 이력/40일 초과/21+ paging/read/read-all/delete, 5 type·재행동·발행 전이·masking/block/restore/suspend/deleted, auth/error/no-store/OpenAPI, FE 3 surface/stale/failure/1440px 디자인, rollback/fan-out/lock/query-bound/source-JAR gate를 모두 초기 `NOT RUN`으로 매트릭스화했다.
- `qa/m8-acceptance.mjs`를 추가했다. 8개 합성 계정과 관계·게시글·댓글·좋아요를 HTTP로 생성하고, 5 type 생성·동일 reply dedupe·self 제외·like retry/unlike/re-like·request cancel/re-request/accept·eligible POST 전이·21+ 목록을 준비한다. 실제 실행 시 API envelope/no-store/error/auth/ACL/read/delete/read-all/masking/block 복구와 MySQL read-only fingerprint, M7 discovery no-notification, Chrome 1440px center/330px dropdown/right-panel/recent3/shared unread/stale/guest guard/삭제 실패 보존/원본 디자인 캡처를 검증한다.
- root 소유 `build/m8-runtime.py`와의 handoff를 고정했다. seed는 합성 ID만 포함하고 `accounts.<label>.userId`, top-level `suspendedUserId/deletedUserId/suspendedPostId/deletedPostId/ownerUserId/oldNotificationIds`, `statusTargets`를 쓴다. root는 동일 receiver의 `oldNotificationIds`에 41일 timestamp와 두 user 상태를 한 transaction으로 적용하고 `status-ready.json`의 `ready`, 동일 IDs, `oldNotificationUpdatedCount`를 검증한 뒤 작성한다. runner는 이 ready가 없으면 계속하지 않는다.

### 산출물

- `qa/M8-review.md`
- `qa/m8-acceptance.mjs`

### 검증·경계

- `node --check qa/m8-acceptance.mjs` 성공.
- `rg -n '[[:blank:]]+$' qa/M8-review.md qa/m8-acceptance.mjs .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md` 결과 `trailing-whitespace: clean`.
- 실제 backend/FE/API/MySQL/SQL helper/Chrome, Gradle/FE 자동검증, source manifest/JAR hash, screenshot, acceptance runner는 root의 실행 지시와 immutable artifact 전까지 **실행하지 않았다**. PASS/merge-ready/dev 통합을 주장하지 않는다.
- 제품 `src/**`, `frontend/**` 및 타 역할 테스트, governance/worklog 원본, Git은 수정하지 않았다.

## 2026-11-10 KST — root runtime seed handoff 반영 확인

- root가 `seed.statusTargets`/top-level 상태 키, 8개 합성 계정 ID의 positive·unique 검증, owner receiver guard, SUSPENDED/deleted/41일 timestamp의 단일 transaction 적용, `oldNotificationUpdatedCount`를 포함한 `status-ready.json` 검증을 runtime helper에 반영했다고 인계했다.
- QA harness와 acceptance matrix는 변경 없이 유지한다. root 실행 지시와 immutable artifact가 오기 전 실제 HTTP·DB·Chrome·자동검증은 시작하지 않으며, M8 판정은 계속 **NOT RUN**이다.

## 2026-11-10 KST — M8 Task5 focused black-box coverage audit

### 한 일

- 기존 harness를 A-v2 §9.1/§9.3 및 Task2 독립 리뷰의 남은 actual gate와 대조했다. 차단해제·ACL복구·deleted actor/null masking·41일 보존은 기존 검증을 유지하고 직접 read assertion만 보강했다.
- `actor→parent` ACCEPTED 관계를 hard-delete한 뒤 NEIGHBOR 이력의 unavailable projection과 read를 확인하도록 fixture/HTTP probe를 추가했다.
- list/read/read-all/delete 네 endpoint 각각에 missing·empty·Basic/bad bearer·expired·wrong JWT type·deleted principal·SUSPENDED 행렬과 `private, no-store` 검사를 추가했다.
- FE fix1 대응 black-box 조건으로 route re-entry refresh, read 500→retry/navigation, transient list 500 보존·retry, USER_001 state clear, same-account stale preview summary 보호를 추가했다.
- `performance_schema.events_statements_summary_by_account_by_event_name`의 `USER='zeroverse_m8'` SELECT counter를 read-only helper로 관측해 actual bearer size=1→size=100 query-bound를 측정하도록 추가했다. root가 격리 DB에서 counter 가용성을 확인했지만 실행 전이므로 결과는 없다.

### 산출물·검증·미해결

- 변경: `qa/m8-acceptance.mjs`, `qa/M8-review.md`; QA 상태/이력과 root 지정 prep report도 append했다.
- 실제 HTTP/DB/Chrome/Playwright/performance_schema/Gradle/FE 명령은 실행하지 않는다. `node --check qa/m8-acceptance.mjs`만 후속 수행한다.
- query-bound delta, runtime V6/restart/M7 compatibility 및 실제 acceptance evidence는 root 실행·artifact 이후 독립 재집계 대상이다. PASS/merge-ready는 주장하지 않는다.

## 2026-11-10 KST — focused audit 정적 검증

- `node --check qa/m8-acceptance.mjs` 통과.
- `rg -n '[[:blank:]]+$'`로 `qa/M8-review.md`, `qa/m8-acceptance.mjs`, QA STATE/WORKLOG 및 prep report를 검사했고 `trailing-whitespace: clean`이다.
- 실제 runtime/API/DB/Chrome/performance_schema 측정은 실행하지 않았으며 M8 판정은 계속 **NOT RUN**이다.

## 2026-11-10 KST — whole-branch review QA final-fix wave

### 한 일

- 독립 whole-branch review의 QA Important 7개를 `qa/m8-acceptance.mjs`에서 수정했다. manifest는 ordinal path sort를 사용하고, POST transition은 PRIVATE masking에도 안정적인 notification ID 집합으로 계산하며, suspended receiver fixture는 comment ID를 참조한다.
- query-bound는 size=1·size=100의 독립 SELECT 비용을 각각 기록하고 양쪽 `1..14` 및 비용 차이 `0`을 요구한다. wrong-type auth는 root runtime이 주입하는 정상 서명 `M8_WRONG_TYPE_TOKEN`만 사용한다.
- Chrome locator는 실제 center role row/recent button과 API 순서·표시 시각을 조합해 duplicate message를 구분한다. USER_001 state clear, SPA account switch stale response, unavailable no-read/no-navigation을 실제 DOM/URL로 판정한다.
- DB fingerprint는 `HEX(JSON_ARRAY(...))` 행별 read-only SQL로 모든 notification scalar/NULL/audit fields를 보존한 뒤 Node SHA-256을 계산한다. `GROUP_CONCAT`/`CONCAT_WS` truncation/null-loss 경로를 제거했다.
- `qa/M8-review.md`의 query-bound 기대값과 final-fix closure를 갱신하고 `.superpowers/sdd/M8-notifications/qa-final-fix.md`를 기록했다.

### 검증·경계

- `node --check qa/m8-acceptance.mjs` exit 0.
- QA harness trailing whitespace clean, `git diff --check -- qa/m8-acceptance.mjs` exit 0.
- 제품 코드·타 역할 테스트·runtime DB·사용자 DB·Git은 수정하지 않았다.
- 실제 HTTP/DB/Chrome acceptance는 root가 새 immutable source/JAR manifest로 실행할 다음 gate이며, 이번 파동에서는 실행하지 않았다. M8 PASS·merge-ready는 주장하지 않는다.

## 2026-11-10 KST — root spot-check correction

- ACTIVE 계정의 unavailable 알림은 read mutation 자체를 거부하지 않을 수 있다는 제품 동작을 확인해, harness를 `PUT read` 완료 후 `200 + available=false + href=null + URL 불변` 검증으로 교정했다.
- stale account 검사는 초기 navigation을 지연시키지 않는다. owner center가 준비된 뒤 `visibilitychange`가 만든 실제 size=20 응답을 hold하고 SPA sign-in으로 friend 계정 전환·friend center 로딩을 끝낸 뒤 response를 release한다. hold 관측, owner unique row 존재, 실제 200 응답, friend 화면의 old row 부재를 각각 확인한다.
- `node --check qa/m8-acceptance.mjs`, trailing whitespace, `git diff --check`를 다시 통과했다. 실제 acceptance는 여전히 root 실행 전이며 M8 PASS를 주장하지 않는다.

## 2026-11-10 KST — Run2 browser-failure final harness handoff

### 한 일

- root Run2의 `browser-center-width`, `browser-shared-unread-center-topbar`, `browser-right-panel-recent`, `browser-shared-unread-right-panel` 실패를 실제 DOM/source와 대조했다. center 폭은 내부 목록이 아니라 `[data-layout="app"]` 외곽(설계상 860px)을 측정하고, `■ 최근 알림` h2를 scoped locator로 찾으며, zero unread에서 의도적으로 없는 badge를 0으로 비교하고, Main right panel unread를 해당 panel 범위로 한정했다.
- stale 계정 시나리오는 owner 화면 준비 후 `route.fetch()`로 실제 `size=20` 응답을 upstream 200 확인과 함께 보류한다. 제품 logout UI를 추가하지 않고, 기존 Vite `apiClient` 모듈의 `/api/v1/auth/me`·`/refresh`에만 401을 주입해 기존 auth-expired handler를 통과시킨 뒤 same-Provider SPA friend signin·센터 ready 후 held response를 release하도록 교정했다.
- 열린 TopBar notification dropdown 상태에서 `topbar-notifications.png` 캡처를 추가해 center/Main recent와 함께 세 표면의 독립 시각 증거를 남기도록 했다.

### 검증·미해결

- `node --check qa/m8-acceptance.mjs` exit 0.
- `git diff --check -- qa/m8-acceptance.mjs` exit 0; QA harness trailing whitespace clean.
- 실제 HTTP/DB/Chrome acceptance는 root의 Run3가 수행할 단계다. Run2의 310 PASS/4 browser FAIL과 stale timeout은 재현 원인·하네스 수정까지 확인했지만, Run3 evidence 전에는 M8 PASS를 주장하지 않는다.

## 2026-11-10 KST — Run3 actual acceptance evidence handoff

### 한 일

- root가 실행한 `build/m8-acceptance-20261110-173900/evidence.json`과 `build/m8-evidence/acceptance-run3.log`를 read-only 대조했다. 결과는 `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`, runner exit 0이다.
- evidence에 source/JAR identity 일치, status fixture ready, 실제 bearer query-bound `sizeOneSelectDelta=4`, `size100SelectDelta=4`, cost difference `0`, HTTP/DB/Chrome checks와 5개 screenshot(`topbar-notifications.png` 포함)이 있음을 확인했다. source/JAR 및 runner provenance는 root 보고대로 unchanged다.
- 이 결과를 **실제 runtime acceptance PASS**로 QA STATE에 반영했다. 단, `qa/m8-acceptance.mjs`와 `qa/M8-review.md`는 freeze하고, `qa/M8-review.md` 최종 audit는 `m8_final_review` 단독 작성으로 이관했다.

### 검증·미해결

- 본 항목은 Run3 execution evidence의 독립 read-only 확인이다. 독립 최종 visual/audit, merge-ready 및 dev 통합은 아직 판정하지 않았다.
- 새 finding이 나오면 해당 소유자 수정 후 재검토한다. 현재 evidence만으로 M8 최종 PASS나 merge-ready를 주장하지 않는다.

## 2026-11-10 KST — 독립 최종 audit 완료·merge-ready handoff

### 한 일

- `m8_final_review`가 `qa/M8-review.md` §10에 독립 최종 audit을 append했고, Run3 evidence·source/JAR provenance·backend/frontend covering 검증·1440px screenshot 5장을 구현자와 다른 컨텍스트에서 대조했다.
- 최종 review 결과 Critical 0, Important 0, 기존 QA finding 종료, **Ready to merge Yes**다. DELETE 400 OpenAPI와 SUSPENDED read-only 설명 누락은 covering 검증으로 종료됐다. 운영 SLA·최대 용량과 물리 JDBC batching은 판단 범위 밖으로 명시됐다.
- `qa/M8-review.md`의 현재 header/status를 `IMPLEMENTED / ACCEPTANCE PASS / READY TO MERGE`로 갱신했다. 과거 섹션의 `NOT RUN`은 실행 전 이력으로 보존했고, `qa/m8-acceptance.mjs`는 freeze했다.

### 검증·미해결

- QA STATE를 **M8 독립 QA PASS / merge-ready Yes (dev 통합 pending)**으로 갱신했다.
- 남은 단계는 root의 dev 통합·PR/merge 기록뿐이다. QA는 dev 통합 완료를 주장하지 않으며, 통합 후 회귀나 새 finding이 생길 때만 재검토한다.

## 2026-11-10 KST — PR14 dev 통합·M8 closeout

- PR14(`https://github.com/0-virus/zero-verse/pull/14`)가 `dev`에 병합됐다. merge commit은 `b9fcb0469f42077447ce8a8842ce22f24bc94af5`이며 local `dev`는 ff 상태다.
- tested head `201cd181ec8332e85f2cc985c9560d947b2833f4`와 merge tree `9da6077a89a4528df3cb55c13556ed28300cfd28` 동일 증거를 반영했다. M8은 독립 QA PASS·Ready to merge를 거쳐 **DEV INTEGRATED / 완료**로 기록했다.
- `qa/M8-review.md` 현재 header와 §11 merge proof, QA STATE를 갱신했다. 독립 §10 review와 `qa/m8-acceptance.mjs`는 수정하지 않았다. M9는 범위 밖이며 사용자 지시 대기다.

## 2026-11-11 KST — M9 독립 QA 준비

### 한 일

- `.claude/CONSTITUTION.md` → `AGENTS.md`/`CLAUDE.md` → QA `CLAUDE.md`/`STATE.md` → `qa/AGENTS.md` 순서로 읽고, M9 `docs/worklog/M9-admin.md`, `docs/governance/meetings/M9-20261111-admin.md` §5 A-v1, REQUIREMENTS §6.13 FR-ADMIN-01~05/NFR-01·04·09, PRD §5.13/§7/§8.3/§9/§10~§12, 디자인 관리자 원본을 대조했다.
- `qa/M9-review.md`를 작성했다. A-v1을 승인 계약으로 취급하지 않고 API·공통 응답·인증/현재 권한·목록/summary/상세·role/status/delete·soft delete·동시성·FE AdminRoute/stale·1440px·DB/회귀·source/JAR gate를 모두 초기 `NOT RUN`으로 고정했다.
- 디자인의 “블로그·게시글·댓글이 모두 삭제됩니다. 복구 불가.”와 FR-ADMIN-05의 `deleted_at` soft delete가 충돌하고, `lastAccessAt` 저장 필드가 현재 없다. self SUSPENDED, summary 기준, 통계 범위, bootstrap도 사용자 결정 또는 구체 계약이 필요하다고 기록했다.

### 산출물·검증·미해결

- 산출물: `qa/M9-review.md`, QA `STATE.md` 갱신.
- 검증: 편집 파일 재독 및 `rg`/`git diff --check`는 부모가 최종 확인할 수 있도록 남겼다. 제품 자동검증·DB·runtime·Chrome은 실행하지 않았다.
- 판정: **NOT RUN / BLOCKED ON CONTRACT APPROVAL**. 사용자 승인 전에는 A-v1의 제안값을 제품 구현·QA PASS로 올리지 않는다.

## 2026-11-11 KST — M9 A-v2 승인 계약·acceptance harness 준비

### 한 일

- M9 회의 §11, REQUIREMENTS §6.13-A, ADR-0008, worklog Task4를 재독하고 A-v2를 현재 승인 정본으로 반영했다. A-v1의 미확정 표시는 과거 심의 이력으로 남기고 QA 기준은 user-only soft delete/하위 이력 보존, DB current ACTIVE ADMIN, self 보호, `lastAccessAt=null`, 승인 삭제 카피, RR/query-bound, FE probe/stale, 1440px로 갱신했다.
- `qa/m9-acceptance.mjs`를 새로 준비했다. API `18089`, FE `5179`, synthetic MySQL `13319`만 대상으로 signup/signin API 합성 계정, 실제 multipart POST_IMAGE 업로드·bind, fixture seed/status handshake, root fingerprint JSON, performance_schema scalar query-count, HTTP auth/status/role/delete/ACL/notification/image, Playwright Chrome 1440px와 SPA account-switch stale probe를 포함한다.
- raw email/password/access/refresh/wrong-claim token은 memory/env에만 두고 evidence에는 합성 ID·hash·status·count·screenshot 이름만 남긴다. SQL은 고정 scalar SELECT 또는 root `fingerprint --json` helper만 사용하며 사용자 입력을 SQL에 조합하지 않는다. 사용자 DB `13307`·`.env.local`·기존 업로드에는 접근하지 않는다.
- target deleted-actor notification ID는 soft delete 전에 캡처하고, 삭제 후 actor/target/href null masking을 ID 기준으로 확인하도록 했다. image ID는 UUID 문자열이며 실제 `/api/v1/uploads/{uuid}/content` byte hash와 삭제 후 `404 UPLOAD_003`를 확인하도록 했다.

### 산출물·검증·미해결

- 산출물: `qa/M9-review.md`, `qa/m9-acceptance.mjs`, QA `STATE.md` 갱신.
- 정적 검증: `node --check qa/m9-acceptance.mjs` exit 0; `git diff --check -- qa/M9-review.md qa/m9-acceptance.mjs` exit 0; 대상 파일 trailing whitespace 없음(`rg` exit 1, no matches).
- 실제 backend/frontend test·bootJar·HTTP·MySQL·fingerprint/query-count·Chrome·1440px acceptance는 실행하지 않았다. 현재 판정은 **NOT RUN / A-v2 APPROVED / HARNESS PREPARED / SOURCE FREEZE·격리 RUNTIME 대기**다.
- root handoff: `fixture-seed.v1`/`status-ready.v1`의 runId·account IDs·baseline role/status/deleted·bulk 21 IDs·KST week-boundary 3 IDs·summary 기대값·preserved image UUID, `fingerprint.v1`의 child/uploadFiles/preservedSha256, 실제 HTTP 200 전후 query count 1~14·delta0 및 env claim tokens를 exact contract로 문서화했다. root helper의 status-ready가 `time`/`summaryExpectations`를 제공하지 않으면 KST summary gate는 PASS로 완화하지 않는다.

### 2026-11-11 KST — 정적 source/helper 대조 및 harness 보정

- `build/m9-runtime.py`를 읽기 전용으로 대조한 결과 `fingerprint.v1`/synthetic `sql`은 인계 계약과 맞지만, 현재 `apply_seed` source는 `weekBoundaryLabels`를 적용하지 않고 status-ready에 `time`·`summaryExpectations`를 쓰지 않는다. 이 차이는 root가 보완해야 하며 QA가 제품/런타임 helper를 수정하지 않는다.
- `qa/m9-acceptance.mjs`의 삭제 actor 알림 검사는 실제 수신자인 `accounts.admin` 토큰으로 조회하도록 보정했다. 승인된 keyword 100자 허용/101자 `VALIDATION_001`, role/status invalid body와 private/no-store 검사도 추가했다.
- 후속 정적 검증 전까지 실제 API/DB/Chrome 실행은 계속 `NOT RUN`이며, 사용자 DB `13307`·업로드·Git은 접근/변경하지 않았다.

### 2026-11-11 KST — 후속 정적 검증

- `node --check qa/m9-acceptance.mjs` exit 0.
- tracked QA 상태/이력은 `git diff --check` exit 0, 신규 QA 문서·harness는 no-index whitespace check에 whitespace 출력이 없었고 `rg -n '[[:blank:]]+$'` 결과 `trailing-whitespace: clean`이다.
- 현재 QA 소유 변경은 `qa/M9-review.md`, `qa/m9-acceptance.mjs`, `.claude/team/qa/STATE.md`, `.claude/team/qa/WORKLOG.md`뿐이다. 실제 backend/frontend/API/MySQL/Chrome 및 M9 acceptance는 실행하지 않았다.

## 2026-11-11 KST — M9 audit-1 I1~I9 fix1 정적 closure

### 한 일

- `.superpowers/sdd/M9-admin/acceptance-audit-1.md` 원문 I1~I9를 다시 읽고 현재 source/DTO/DOM과 대조했다. 합성 nickname/email/slug 사전 unique gate와 memory-only browser credentials를 보강했다.
- browser DOM을 실제 `ADMIN CONSOLE` visible span, `searchbox` label `관리자 사용자 검색`, `[data-layout="app"]` 외곽 1100/860px로 맞췄다. 목록·상세 readiness는 실제 HTTP 200과 fixture row/ID를 함께 요구한다. USER direct guard, profile-menu ADMIN entry, role/status/delete 확인창·canonical 재조회·500 retry·승인 삭제 카피를 추가했다.
- stale 검사는 실제 upstream `route.fetch()` 200/body를 list/detail/mutation 각각 보류하고, 같은 SPA Provider에서 access/refresh 실패 → B signin form ready → held response release 순서로 검증하도록 바꿨다. 제품에 없는 logout button이나 document `goto` account switch를 사용하지 않는다.
- I5 race는 Promise.all을 MySQL barrier로 주장하지 않고 허용 status/error 집합·최종 detail state·actor race 복구를 확인한다. 별도 `M9_BACKEND_TEST_XML`/기본 XML에 `AdminConcurrencyMySqlTest` 승인 testcase marker가 있어야 physical barrier evidence가 된다.
- root와 합의한 native fixture handoff로 seed에 `nativeFixtureRequest:{extraBlogs:true,softDeletedPost:true}`를 추가하고, ready의 `nativeFixtures`·`targetDetailExpectations`를 fail-closed로 검증한다. target comments/reply/actor notification, 4개 accepted universe 방향, like/tags는 harness 실제 API로 만든다. deletion 전 ID 캡처, child table non-empty count, comment/reply/notification masking, exact detail aggregate를 확인하도록 했다.
- I7 exact ID/filter/summary/paging metadata/page0·page1 union/intersection/large-offset empty를 보강했고, I8 seed 후 실제 ADMIN ACCESS 재발급 및 기존 USER claim 보존, current role/status/deleted 5-method 재검증, refresh ACTIVE/SUSPENDED/deleted 경계를 추가했다. I9 고정 `REQUIRED_EVIDENCE_IDS`/fail-closed gate를 넣었다.

### 검증·미해결

- `node --check qa/m9-acceptance.mjs` exit 0.
- `git diff --check -- qa/M9-review.md qa/m9-acceptance.mjs .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md` exit 0; 대상 QA/report files `rg -n '[[:blank:]]+$'` exit 1 (no matches).
- 실제 API/signup/signin, isolated MySQL/status/fingerprint/query-count, backend XML, Chrome/Playwright, source/JAR provenance는 이번 배정에서 실행하지 않았다. 현재 판정은 **I1~I9 STATICALLY CLOSED / M9 ACCEPTANCE NOT RUN**이다.
- root handoff 잔여: `nativeFixtureRequest` 처리와 ready `nativeFixtures`/`targetDetailExpectations`, backend physical concurrency XML, source/JAR+FE freeze provenance. 제품/타 역할 tests/DB/runtime/Git는 수정·실행하지 않았다.
- root feedback 반영: native ready의 auto-increment ID distinct 검사는 blog table 내부 2개와 post table 내부 2개로 분리했다. 서로 다른 테이블의 숫자 ID 겹침은 유효하다.

## 2026-11-11 KST — M9 audit-2 fix2 정적 보완

### 한 일

- `acceptance-audit-2.md` F1~F9 원문과 현재 QA harness를 재대조했다. promotion 뒤 pre-promotion USER claim은 current ACTIVE DB ADMIN이면 200이어야 하므로 기대값을 수정했고, demotion 뒤 fresh ADMIN claim 재평가를 유지했다.
- `qa/m9-acceptance.mjs`의 stale probe를 실제 `/api/v1/admin/users(?:/|?|$)` 정규식과 one-shot expiry, 유한 ready/release timeout, same-document marker, B 안정화 후 release, finally cleanup으로 보완했다. guest/USER/ADMIN 일반 시나리오는 별도 BrowserContext로 격리했다.
- 실제 accessible DOM 계약에 맞춰 signin `접속하기 ✦`, modal `role=dialog`/`aria-label`, scoped delete copy를 사용했다. list/detail empty·transient retry, duplicate confirm click, focus/visible denied/re-entry, 1440px screenshot evidence를 required matrix에 추가했다.
- target native API fixture에 PUBLIC/UNIVERSE/PRIVATE-draft, target→deleted-opponent accepted relation, target own soft-deleted comment, suspended/deleted signin·post/comment/like/universe write, reactivation 후 기존 refresh 재사용을 연결했다. 삭제 전 refresh probe가 fingerprint를 오염하지 않도록 순서를 이동했다.
- email/nickname/name independent search와 same-createdAt global ID slice 검사를 추가했다. required NOT RUN은 `M9_REQUIRE_FULL=0`이어도 top-level PASS가 되지 않도록 finish gate를 고쳤다.
- 별도 QA evidence helper `qa/m9-test-evidence.mjs`를 main harness에 연결했다. backend XML/summary와 FE JSON을 구조 파싱하고 exact testcase/class/source/assertion 및 zero failure/skip을 확인하며, mapped-only PASS와 uncovered rows를 분리한다.

### 검증·미실행

- `node --check qa/m9-acceptance.mjs` exit 0.
- `node --check qa/m9-test-evidence.mjs` exit 0.
- `node --check qa/m9-test-evidence.test.mjs` exit 0.
- `node --test qa/m9-test-evidence.test.mjs` exit 0 — 13 tests / 13 passed / 0 failed.
- `git diff --check -- qa/m9-acceptance.mjs qa/m9-test-evidence.mjs qa/m9-test-evidence.test.mjs` exit 0.
- 실제 signup/signin·API·MySQL 13319·status/fingerprint/query-count·backend runtime·Chrome/Playwright·source/JAR live provenance는 실행하지 않았다. 현재 M9는 **NOT RUN**이며 helper focused artifact 검증은 전체 acceptance PASS가 아니다.

### 잔여 handoff

- root는 final source/JAR/runtime provenance와 isolated API/FE/MySQL를 동결한 뒤 `qa/m9-acceptance.mjs`를 단일 실행해야 한다.
- helper의 `uncoveredFrontendRows`/`uncoveredStatePolicyRows`는 direct browser/HTTP evidence가 모두 PASS일 때만 main이 닫는다. raw token/credential/log는 evidence에 쓰지 않는다.
- 제품 코드·타 역할 테스트·사용자 DB/업로드·Git은 이 fix2에서 수정하지 않았다.

### fix2 후속 정적 보정

- stale list/detail probe는 SPA 관리자 진입과 target link 전환을 먼저 완료한 뒤 provider document marker를 설정하도록 재배치했다. baseline `page.goto`가 marker를 지우는 false failure를 제거했으며, A hold→B signin/home→release 구간만 same-document로 판정한다.
- browser direct matrix에 list/detail transient retry 결과, successful empty state, duplicate submit 1회, focus/visibility denied/re-entry, same-createdAt 검색 page/back/reload와 list/detail screenshot rows를 추가했다.
- 후속 `node --check qa/m9-acceptance.mjs` exit 0 및 `git diff --check -- qa/m9-acceptance.mjs` exit 0을 확인했다. actual acceptance는 여전히 NOT RUN이다.

## 2026-11-11 KST — M9 fix2 정적 최종 확인·QA 파일 동결 인계

### 검증

- `node --check qa/m9-acceptance.mjs` exit 0.
- `node --check qa/m9-test-evidence.mjs` exit 0.
- `node --check qa/m9-test-evidence.test.mjs` exit 0.
- `node --test qa/m9-test-evidence.test.mjs` exit 0 — 13 tests / 13 passed / 0 failed / 0 skipped.
- `git diff --check -- qa/m9-acceptance.mjs qa/m9-test-evidence.mjs qa/m9-test-evidence.test.mjs qa/M9-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md` exit 0; 대상 파일 trailing whitespace clean.
- helper SHA-256은 `qa/m9-test-evidence.mjs`=`b02a2922bc22a271448b69e35f5440473c8d270128d82bba77cec4719bcd8f6e`, `qa/m9-test-evidence.test.mjs`=`b4abdcd0e0173b62f61c490ed85ff6973751f8d930b654d6cc53eae107ad59ec`로 확인했다.
- `git diff --name-only 6a8763d..HEAD`에는 제품 `src/**`/`frontend/**`가 없고 문서·PM 상태만 있어, QA 작업 트리의 제품 소스 무변경을 read-only로 확인했다. live JAR/FE provenance는 root가 별도 확인할 대상이다.

### 판정·인계

- QA harness 및 구조화 helper는 fix2 정적 동결 상태다. helper의 mapped-only PASS는 전체 인수 PASS가 아니며 uncovered direct rows와 required matrix는 실제 실행 전까지 닫지 않는다.
- 실제 signup/signin·API `18089`·FE `5179`·MySQL `13319`·status/fingerprint/query-count·backend barrier·Chrome/1440px는 실행하지 않았다. M9 판정은 계속 **A-v2 APPROVED / HARNESS STATIC-FROZEN / ACCEPTANCE NOT RUN**이다.
- root가 명시 QA 6개 파일을 커밋하고 동일 독립 auditor의 scoped re-review를 거친 뒤, immutable source/JAR/live runtime provenance가 준비되면 단일 실제 acceptance를 실행한다. 제품·DB·runtime·Git은 이 확인에서 수정하지 않았다.

## 2026-11-11 KST — M9 audit-3 R1~R5 fix3 정적 보완·동결 인계

### 변경

- R1: 기존 SUSPENDED signin/write/read 경계의 동일 boolean을 필수 `mutation-suspended-signin-and-writes` evidence ID에도 연결하고 기존 `mutation-suspend-boundary` 결과는 보존했다.
- R2: 관리자 사용자 table의 row locator를 추가해 pending/empty/denied/re-entry/transient/stale 행 검사를 scoped로 바꿨고, `waitForAdminReady`의 expected row도 table 내부로 제한했다. TopBar nickname은 행 결과로 세지 않는다.
- R3: transient 500→retry 검색어를 실제 `adminKeeper.email`로 변경해 canonical retry 결과를 실제 keeper row로 검증하고, 별도 no-match 검색은 empty 상태로 검증한다.
- R4: stale list hold를 실제 기본 `size=20`으로 맞췄다. transient helper 복귀·paging을 `size=20`으로 고정하고 bulk 21개를 API ID DESC 기준 page0 20개/page1 1개로 back/reload까지 비교한다. keeper가 첫 기본 페이지에 없을 수 있는 경로의 불필요한 readiness 기대도 제거했다.
- R5: deleted signin은 `AUTH_001/401`, authenticated deleted post/comment/like/universe writes는 기존 `USER_001/404`로 분리했다. generic signin boundary가 401을 캡처하도록 허용 범위만 넓혔다.

### 검증·경계

- `node --check qa/m9-acceptance.mjs` exit 0.
- `git diff --check -- qa/m9-acceptance.mjs qa/M9-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md` exit 0; harness/기록 trailing whitespace clean.
- helper 두 파일은 audit-3에서 수정하지 않았다. 실제 signup/signin·API·DB·runtime·Chrome/Playwright·Git 실행은 하지 않았고, M9 판정은 **A-v2 APPROVED / audit-3 R1~R5 FIX3 STATIC-FROZEN / ACCEPTANCE NOT RUN**이다.
- root가 이 보정 뒤 명시 QA 파일을 커밋하고 동일 독립 auditor의 scoped re-review를 수행한다. 제품 코드·타 역할 테스트·DB·runtime·Git은 수정하지 않았다.

## 2026-11-11 KST — M9 audit-4 N1 fix4 정적 보완·동결

- 한 일: 새 QA 컨텍스트 `/root/m9_qa_fix4`가 N1만 보정했다. 상세/mutation stale 최종 부재 검사를 목록 row에서 실제 사용자 상세 section·target email/표시 UID·retry 버튼으로 복원했다. 목록 table helper와 B stable session/home/document/held release/navigation 검사는 유지했다. upload 진단에는 HTTP status와 제한된 error code만 기록한다.
- 산출물: `qa/m9-acceptance.mjs`, `qa/M9-review.md`, QA STATE 및 기존 Task4 report append. helper 두 파일·제품·타 역할 파일·Git·runtime·사용자 데이터는 변경하지 않았다.
- 검증: `node --check qa/m9-acceptance.mjs` exit 0; scoped `git diff --check` exit 0; 수정 파일 재독. helper suite·HTTP/API/DB/Gradle/브라우저는 이번 라운드에서 실행하지 않았다.
- 실제 상태: root 진단1 `build/m9-acceptance-20261111-163214/evidence.json`의 fixture-image-upload FAIL/runner 중단을 읽었다. root는 404 원인을 로컬/test profile 누락으로 진단하고 runtime만 보정했다고 인계했다. 환경 원인을 제품 결함으로 판정하거나 required check를 완화하지 않는다.
- 미해결: **진단1 RUN FAILED / 미도달 matrix NOT RUN**. fix4 정적 동결 후 root commit·scoped re-review·실제 진단2 및 최종 provenance/인수 gate가 남아 있으며 PASS·merge-ready를 주장하지 않는다.


## 2026-11-11 KST — Task4 fixround5 image fixture 계약 보완·소스 동결

- 담당: 새 QA 구현 컨텍스트 `/root/m9_qa_fix5`, 기준 HEAD `d409d28`. 범위는 실제 `fixture-target-image-bind` 실패 하나다.
- RED: `build/m9-acceptance-20261111-163859/evidence.json`의 upload PASS(201)/bind FAIL/runner 중단, root exact 재현 `build/m9-evidence/image-bind-diagnostic-response.json`의 HTTP400 `UPLOAD_004`(본문 이미지와 이미지 목록 불일치). 진단2 top-level은 NOT RUN이며 미도달 matrix를 PASS로 바꾸지 않는다.
- 근거: REQUIREMENTS FR-POST-02/NFR-02, PRD §5.5·§9.7, PostDtos.UpdatePostRequest, PostController.update, PostService.updateInTransaction/updateImages/validateImageSnapshot을 대조했다. FE PostEditor.test.tsx의 draft/image 저장 사례와 BE PostServiceIntegrationTest의 image node/snapshot 검증, PostContentService image attrs 및 UploadService.bindPostImages의 owner/purpose/최초 binding을 읽었다. images-only API는 기존 본문 image URL 집합과 일치해야 하므로 plain-text 글에 snapshot만 추가한 harness가 계약을 위반했다.
- 변경: full PUT /posts/{id}에 기존 canonical title/category/thumbnail/tag 2개·marker 문단을 보존하고 image node·동일 images snapshot·PUBLIC·publish:true를 함께 전송한다. canonical 응답의 동일 id/PUBLIC/기존 publishedAt/tag 2개/본문 deep equality/image URL·alt·order를 확인하고 target snapshot을 갱신한다. node:util의 deep equality로 JSON object key 순서에는 의존하지 않는다. bind 진단은 status와 제한된 errorCode만 남기며 body/token/credentials를 출력하지 않는다. 기존 child row/file preservation 및 stats 검사는 보존했다.
- 검증: `node --check qa/m9-acceptance.mjs` exit 0, `git diff --check -- qa/m9-acceptance.mjs qa/M9-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md .superpowers/sdd/M9-admin/task-4-fix1-report.md` exit 0; 수정 파일 재독·scoped diff 확인. API/DB/Chrome/Gradle 및 동결 helper suite는 실행하지 않았다. Git mutation·제품 코드·제품 source/JAR·사용자 데이터 변경은 없다.
- 미해결: **FIX5 STATIC-FROZEN / 실제 진단2 RUN FAILED / 최종 인수 미완료**. root만 실패 run 보존·fresh 격리 runtime 재실행을 수행한다. 실제 GREEN·최종 PASS·merge-ready는 재실행 및 독립 최종 gate 전 주장하지 않는다.

## 2026-11-11 KST — Task4 Step3 진단3 auth fixture 통합 보완

- 담당: QA `/root/m9_qa_fix5`. 초기 정적 loop 5회는 acceptance-audit-6.md CLEAN으로 닫혔으며 이번은 계획된 Step3 runtime 통합이다. post-loop diff는 최종 whole-branch 검토 대상이고 광역 정적 audit를 재개하지 않는다.
- 실제 근거: `build/m9-acceptance-20261111-164556/evidence.json`은 image/native fixture를 통과한 뒤 auth 실패가 발생했다. root exact `signature-append-diagnostic-response.json`은 append-A 서명에서 decoded bytes 불변/HTTP200, `auth-matrix-diagnostic-response.json`은 current USER GET403/ADMIN_001 대비 본문 없는 PUT400/VALIDATION_001을 기록한다. 원래 잘못된 서명으로 보낸 DELETE에서 target이 삭제됐다는 root 인계를 유지하며 이후 집계/집합 기대를 완화하지 않는다.
- 수정: tamperJwt는 base64url signature를 Buffer로 디코딩하고 첫 바이트 XOR1 뒤 재인코딩한다. header/payload와 signature byte length는 보존한다. bad-signature GET이 AUTH_004/401이 아니면 PUT/DELETE를 보내기 전에 fail-fast하며 통과 후에도 기존 다섯 메서드를 모두 검사한다. role/status PUT은 각각 유효한 USER/ACTIVE 본문을 모든 auth 경로에서 전송한다. 잘못된 DTO에 대해 current actor 오류가 우선한다는 새 제품 계약을 만들지 않는다.
- 진단: 실패한 auth 행은 method/path/status·형식 제한 errorCode·envelope·cachePrivate/cacheNoStore boolean만 출력한다. body/token/credentials는 기록하지 않는다. helper 두 파일·제품 source/JAR·global set/summary/query 기대는 그대로다.
- 순수 검증: 기존 append 분기는 48-byte 0xff signature에서 decoded bytes 변경 assertion이 실패(RED exit1)했다. 수정된 실제 함수 definition을 vm으로 읽어 32/48/64-byte × 0/255 총6 cases에서 decoded bytes 변경, 첫 바이트 XOR1, 나머지 바이트·길이·header/payload 보존을 확인했다(GREEN exit0). 기존 append-A가 같은 48 bytes로 decode되는 것도 별도 assert했다. 최초 64-byte 재현은 변화가 있어 결함을 재현하지 못했으며 48-byte 재현으로 바로잡았다.
- 검증 명령: `node --check qa/m9-acceptance.mjs` exit0, QA 4개 파일 및 기존 report에 대한 `git diff --check` exit0; 변경 파일 재독·diff 확인. API/DB/Chrome/Gradle/helper suite·Git mutation은 실행하지 않았다. 실제 인수 GREEN/PASS는 이 순수 검사로 주장하지 않는다.
- 남은 일: root가 진단3을 보존하고 fresh run4를 수행한다. 실제 auth/current-state 5-method 결과와 이후 matrix, live provenance 및 최종 독립 whole-branch/시각 gate는 미완료다.

## 2026-11-11 KST — Task4 Step3 진단4 상태·상세 브라우저 harness 보완

- 범위: source `753d0c6`의 실제 진단4 `build/m9-acceptance-20261111-165511/evidence.json`에서 실행된 refresh 4행/deleted write/detail retry/lastAccess 오류 및 root의 accounts 미정의 중단 인계를 대조했다. 초기 정적 loop 재개·새 matrix·제품 변경은 없다.
- 원인 대조: root `build/m9-evidence/refresh-deleted-write-diagnostic-response.json`은 refresh401/AUTH_003 및 deleted own-blog post404/BLOG_001, Spring 기본 no-cache/no-store 헤더를 보였다. AuthController.refresh는 admin 전용 private 헤더를 설정하지 않으며 NotificationPublicationLock.lock은 삭제된 owner를 BLOG_001로 거부한다. frontend main.tsx StrictMode, AdminUserPage detail useEffect의 cleanup/cancel 및 재요청·retryKey, 실제 header의 email/가입일/lastAccess 결합 텍스트를 읽었다.
- 수정: target404 probe에 expectedKeeperId를 명시 전달한다. refresh 4행은 기존 no-store와 exact status/code/envelope를 요구하고 admin privateNoStore는 그대로 둔다. deleted post creation만 BLOG_001/404로 고치며 signin AUTH_001/401과 다른 authenticated writes USER_001/404를 유지한다. 실패 state행은 response별 status/제한 errorCode/envelope/cache boolean만 기록한다.
- 브라우저: lastAccess는 canonical target ID/email/null을 검증하고 상세 header의 target email visible을 기다린 뒤 부분 텍스트로 검사한다. transient detail은 retry 확인까지 모든 matching initial GET을500으로 유지하고 handler 제거 후 실제 retry를 클릭해200/canonical ID/email 및 header 표시를 확인한다. StrictMode 두 번째 초기 요청이500을 우회하는 one-shot 가정을 제거했다.
- 검증: `node --check qa/m9-acceptance.mjs` exit0; scoped `git diff --check` exit0. 기존 oxlint `-c /private/tmp/m9-qa-no-undef-config.json -A all -D no-undef qa/m9-acceptance.mjs`는 Node/browser env를 둔 임시 config로 RED(accounts만 미정의, exit1)→GREEN(exit0)을 확인했다. config 없는 최초 lint의 Node/DOM globals 경고는 환경 설정 누락으로 제외했으며 새 제품 의존성은 없다. 코드·문서 재독과 diff 확인을 마쳤다.
- 경계: 제품·helper 두 파일·API/DB/browser/Gradle·Git mutation은 변경/실행하지 않았다. 진단4에서 이미 PASS한 global set/query/auth 기대는 완화하지 않았다. root가 실패 run을 보존하고 fresh 재실행해야 하며 실제 브라우저 GREEN·전체 인수 PASS·최종 whole-branch/시각 gate는 남아 있다.

## 2026-11-11 KST — Task4 Step3 진단5 브라우저 route 프로토콜 보완

- 실제 RED: `build/m9-evidence/acceptance-diagnostic-5.log`에서 HTTP·상태·이미지보존·detail retry/lastAccess PASS 이후 paging/target404 list/mutation retry/delete canonical/stale detail 실패와 unhandled `Route is already handled!`를 읽었다. `build/m9-acceptance-20261111-170234`에는 최종 evidence.json이 없으므로 raw log의 부분 PASS를 전체 인수 PASS로 취급하지 않는다.
- root 독립 관측: `build/m9-evidence/browser-paging-detail-diagnostic.json`에서 bulk page0 IDs33..14/21 rows, page1 ID13/2 rows, back/reload 정상과 exact nickname0/header partial email1을 확인했다. main은 검색 이전 페이지와 같은 첫 행만 기다려 이전 결과를 readiness로 오인할 수 있어 각 keyword/size20/page의 실제200 응답과 정확 ID slice를 기다린다. page0는 absent/0 모두 의미상0으로 판정하며 기존20+1 기대를 유지한다. 실패하면 boolean·URL·row count·응답 ID를 기록한다. 초기 실패의 개별 boolean은 당시 artifact가 없으므로 추정과 확정을 구분한다.
- 관측된 selector/response 구분 수정: target404 뒤 목록은 size1 권한 probe가 아닌 size100/page0 canonical 목록을 기다린다. mutation retry는 header email로 행 보존을 확인하고 실제 mutation200/detail200·target ID/role·rendered role을 대조하며 안전한 상태 metadata를 남긴다. delete는 pathname /admin·정확 keyword와 canonical empty DTO로 확인한다.
- route 원인: 기존 held 여부는 fetch 이후 설정되어 중복 callback이 진입했고 shared body를 덮어쓸 수 있었다. dispose는 fulfillment를 기다리지 않았다. 이제 비동기 fetch 전 A Authorization을 메모리에 예약하고 중복 A 요청별 response/body/완료를 소유한다. release는 각 실제 browser response event·finished/body 일치를 기다리고, dispose는 release→handler 제거→bounded drain을 거친다. 취소/timeout/callback 오류는 failure와 delivery false로 기록한다. stale PASS는 delivery/disposal 성공·upstream200·B stable·same document·민감 fragment/retry 부재를 모두 요구한다.
- expiry는 one-shot 대신 captured A Authorization에만 지속401을 적용한다. refresh 실패 주입은 실제 signin 경계가 관측된 뒤 B signin 전에 제거하며 B header는 만료 주입을 통과한다. A access/refresh 실패 전달 횟수와 route error 여부를 확인한다. 원문 Authorization/body는 evidence에 남기지 않는다.
- 증거 보존: fixture/HTTP/각 browser stage 및 route failure에서 top-level NOT RUN checkpoint를 저장한다. JSON 쓰기는 직렬화된 temp→rename으로 보존하며 마지막 전체 gate만 PASS를 결정한다. 추가 required matrix나 제품 정책은 없다.
- 검증: 실제 함수를 읽는 순수 `node /private/tmp/m9-route-protocol-focused.mjs` exit0 — 동시2개 body 격리/각1회 fulfillment, A 반복401/B pass, browser delivery completion, idempotent finite disposal, cancellation failure/checkpoint 및 stuck-fetch disposal timeout/no unhandled를 확인했다. `node --check qa/m9-acceptance.mjs`, 기존 oxlint Node/browser 환경의 scoped no-undef, scoped git diff --check 모두 exit0. 기존 Playwright 로컬 types의 unroute(handler)/waitForResponse/finished 계약과 수정 파일을 재독했다.
- 경계: 위 순수 fake-page/route 검사는 실제 Chrome GREEN이 아니다. 이번 QA는 API/DB/browser/Gradle/helper suite·Git mutation을 실행하지 않았고 제품/helper/source/JAR은 변경하지 않았다. root fresh runtime·최종 whole-branch/시각/실제 artifact gate가 남았다.

## 2026-11-11 KST — Task4 Step3 진단6 잔여 두 검사 보완

- 실제 상태: `build/m9-acceptance-20261111-171732/evidence.json`을 읽어 394 PASS / 2 FAIL / 0 NOT RUN을 확인했다. 실패는 browser-stale-list-pending-sensitive와 design-admin-reference뿐이다. 기존 route 오류/미도달 없이 종료됐고 나머지 HTTP·controls·stale detail/mutation·helper gate는 통과했다. 최종 상태는 NOT RUN이며 아직 M9 PASS가 아니다.
- stale list 원인: special row는 기본 목록에도 있어 기존 visibility-only readiness가 special 검색 완료를 증명하지 못했다. 또한 bulk query 실제 응답에 없는 keeper를 검사해 held response와 민감 행 anchor가 연결되지 않았다. special keyword/size20/page0의 실제200 및 정확 special ID 1개·렌더1행을 먼저 확인하고 clear keyword의 real200을 보류한다. held body에서 숫자 userIds만 읽고 baseline에 없는 실제 합성 계정을 anchor로 선택한다. pending/final 검사와 evidence는 동일 anchor ID에 연결하며 이전에 허용된 baseline 행의 유지는 금지하지 않는다.
- 디자인 원인: 기본 디자인 화면의 body.innerText에서 숨겨진 admin 화면을 찾았다. root `build/m9-design-reference.mjs` 및 원본 switcher/data-screen-label을 대조해 관리자와 관리자·상세를 각각 클릭한 뒤 visible 화면 1개·innerText의 ADMIN CONSOLE/사용자·양수 bounds를 검증한다. 숨긴 textContent 존재로 PASS하지 않는다. 화면별 safe metadata는 evidence.designReference에 보존한다.
- 보존: holder 변경은 ready metadata의 숫자 ID 추출뿐이며 actual delivery/expiry/dispose/B stable/same-document/late-response 부재 gate를 유지한다. stdout 마지막 출력만 경로·건수로 줄이고 전체 fixture/hash/evidence 데이터는 JSON에 남긴다.
- 검증: `node --check qa/m9-acceptance.mjs`, Node/browser 환경의 기존 oxlint scoped no-undef, scoped git diff --check 모두 exit0. `node /private/tmp/m9-route-protocol-focused.mjs`는 actual 함수의 numeric IDs 필터·중복body/expiry/delivery/disposal/failure 경로를 재검증해 exit0이었다. 변경 파일·diff·기록을 재독했다.
- 경계: QA는 API/browser/DB/Gradle/Git mutation 및 frozen helper를 실행·변경하지 않았다. 제품 source/JAR·디자인 정본도 변경하지 않았다. root fresh run7와 최종 whole-branch/시각/실제 artifact gate가 남아 있으며 순수 검사로 실제 GREEN/PASS를 주장하지 않는다.

## 2026-11-11 KST — Task4 Step3 run7 실제 자동 인수 PASS / 독립 최종 gate 대기

- 실제 증거: `build/m9-acceptance-20261111-174413/evidence.json` status PASS, **396 acceptance checks PASS / 0 FAIL / 0 NOT RUN**. `build/m9-evidence/acceptance-diagnostic-7.log`의 마지막 summary도 396/0/0, screenshot 2개이며 root 실행 exit0 인계와 일치한다. 초기 실패 run은 보존했고 당시 부분 PASS를 최종 결과로 바꾸지 않았다.
- source/JAR: source `3a2752e`, `source-after.json`의 424 files/match=true, `runner-provenance.json`과 `runner-provenance-after.json` 117개 동등 및 현재 파일 SHA-256 전부 일치를 QA가 직접 확인했다. source manifest SHA-256 `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`, immutable runtime JAR SHA-256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`.
- SQL 실측: 실제 HTTP200 list size1=5 SELECT, size100=5 SELECT, detail=3 SELECT. list 증분0이며 세 요청 모두 승인 bound 1..14 이내다. 하위 row/file fingerprint·이미지 bytes/삭제후 UPLOAD_003·canonical 집계·current actor·동시성·SPA stale gate가 모두 PASS다.
- 별도 제품 회귀: `build/m9-evidence/backend-final-2/summary.json` 109 suites/637 tests, failure/error/skip0, exit0; `frontend-final-result.json` 및 `frontend-final-test.log` 51 files/464 tests, 실패/skip0. 396 acceptance checks와 합산하지 않는다. `qa-helper-final.log`는 parser focused 13/13 PASS이며 `route-protocol-focused.log`는 body 격리·한번 fulfillment·A만 expiry/B 통과·실제 delivery·유한 cleanup·취소 failure/noUnhandled의 순수 검사 PASS다.
- helper closure: physical barrier 7 mappings, RR writer 2, no-op/audit 4, state policy 6, FE 25가 실제 named test/source와 연결됐다. 다섯 helper check의 missing/unresolved는 모두 빈 배열이다. `fixture.testArtifacts`에 backend19/frontend25 mappings 및 artifact hashes123개를 보존했다. FE uncovered6/state uncovered3은 아래 direct PASS로 닫혔다.
  - FE pending: `browser-admin-list-pending-sensitive`; 같은 Provider 전환: `browser-stale-list`, `browser-stale-detail`, `browser-stale-mutation`, `browser-account-switch-stale`; 복귀 probe 상태: `browser-admin-focus-suspended`, `browser-admin-visible-deleted`, `browser-admin-denied-reentry`.
  - FE empty/error/retry: `browser-list-empty-state`, `browser-list-transient-retry`, `browser-list-transient-retry-result`, `browser-detail-transient-retry`, `browser-detail-transient-retry-result`, `browser-mutation-error-retry`; 중복 제출: `browser-mutation-duplicate-submit`; URL/실측: `browser-admin-list-width`, `browser-admin-detail-width`, `browser-search-url-state`, `browser-search-paging-history`, `browser-screenshot-evidence`, `browser-admin-entry-menu`, `browser-admin-heading`, `browser-admin-role-label`.
  - 상태정책: `mutation-suspend-boundary`, `mutation-deleted-signin-and-writes`, `refresh-reactivated-existing-accepted`. required matrix도 `required-matrix-complete` PASS이며 미실행/실패 required ID가 없다.
- 화면 증거: run7 `admin-list-1440.png`/`admin-detail-1440.png`, 실제 visible 디자인 관리자/상세 폭1100/860, 목록·상세·retry·삭제카피·stale 자동 검사 PASS. 디자인 소스 선택과 크기 검사는 최종 시각 정합성 심사를 대신하지 않는다.
- 저자/판정 경계: QA harness 보완은 `/root/m9_qa_fix5`, 실제 runtime 실행은 root, 최종 whole-branch/시각 검토는 별도 `/root/m9_final_review`다. 현재는 **자동 인수 PASS / 독립 최종 verdict 및 PR/dev merge 대기**다. QA 매트릭스의 최신 상태를 실제 check ID로 연결했고 과거 append-only 실패 기록은 보존했다. 이번 변경은 QA 기록만이며 main/helper/product/runtime/Git은 변경하지 않았다.
- 기록 검증: QA 4개 문서 대상 `git diff --check` exit0, 최신 매트릭스71행의 실제 PASS ID115개 대조·미실행 상태 잔존 없음, WORKLOG/report append 동일성·117개 frozen provenance 재대조 PASS. 기존 report는 Git 제외 경로이므로 별도 append 내용/후행 공백도 검사했다. main/helper/product/runtime 재실행은 하지 않았다.

## 2026-11-12 KST — M9 Task4 final-review Minor M1 closure 및 QA 판정 동기화

- 한 일: `final-review.md`, `task-4-final-m1-brief.md`, `task-4-final-m1-report.md`, `final-m1-rereview.md`, `docs/worklog/M9-admin.md`, REQUIREMENTS §6.13-A, ADR-0008 및 현재 HEAD `5df2db0`을 직접 대조했다. 최초 final review의 Minor M1(관리자 keyword 내부 whitespace 축약)이 `5df2db0`에서 trim-only·내부 whitespace 보존으로 수정됐음을 확인하고, QA 최신 판정을 **M9 독립 QA PASS / READY TO MERGE**로 갱신했다.
- 산출물: `qa/M9-review.md`에 M1 finding→수정→독립 scoped clean 흐름, 최신 source manifest 424 files/SHA-256 `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`, FE 51 files/465 tests 및 BE637/JAR 불변을 기록했다. `STATE.md`는 PR/dev 통합·merged tree 대조만 남은 상태로 덮어썼다. 기존 run7 396/0/0·FE464·source SHA `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`와 과거 실패/NOT RUN은 삭제하지 않고 역사 경계로 보존했다.
- 검증: `git rev-parse HEAD` → `5df2db03866a1257ac69e6bf7f8109487b01a1ef`; 편집 파일 재독(`sed -n`/`tail`), `git diff --check -- qa/M9-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md` exit0, 핵심 문자열 검색에서 `M9 독립 QA PASS`, `READY TO MERGE`, `465`, `ababaa1c...`, `CLEAN / READY TO MERGE`, `PR/dev` 경계를 확인했다.
- 실행 경계: M1 수정 후 root 보고의 focused 1 file/4 tests, full FE 51/465, lint/build exit0와 독립 `final-m1-rereview.md`의 Critical/Important/Minor 0을 근거로 삼았다. QA는 이번 동기화에서 runtime/API/DB/Chrome/Gradle 테스트를 재실행하지 않았고 Git commit/branch/push/merge도 하지 않았다.
- 미해결: root의 PR/dev 통합·merged tree/source identity 대조만 대기한다. M9 완료·배포·M10·실사용 DB 적용은 주장하지 않는다.

## 2026-11-12 KST — M9 PR #15 dev 통합 closeout

- 한 일: root가 전달한 PR #15(`https://github.com/0-virus/zero-verse/pull/15`) merge handoff를 QA 상태에 반영했다. merge SHA는 `c0df2d33d0dce5ac71f1b358672606f717e739ae`, tested head는 `e3efa6b`, tested head tree와 merge tree는 `409c084b2189f13fb5f44a5ffb375726475becaf`로 기록했다.
- 근거: backend 109 suites/637 tests, failure/error/skip/exit 0/0/0/0 및 frontend 51 files/465 tests + lint/build PASS, run7 396/0/0 역사 evidence, final independent `CLEAN / READY TO MERGE`, M9 runtime stopped/preserved handoff를 대조했다.
- 산출물: `.claude/team/qa/STATE.md`를 **DEV INTEGRATED / COMPLETE**로 갱신했다. M9 QA PASS/READY TO MERGE, Minor M1 closure, source/JAR 및 과거 run7 경계를 유지하고 PR/dev 통합 gate를 종료했다.
- 검증: 편집 후 `sed -n`으로 STATE/WORKLOG를 재독하고 `rg -n`으로 `DEV INTEGRATED`, `COMPLETE`, PR #15, merge SHA, tested tree, BE637, FE465, `396/0/0`, `CLEAN / READY TO MERGE`, `runtime stopped/preserved` 핵심 문자열을 확인했다. 이번 closeout에서는 Git 명령을 금지받아 Git diff/status/check와 테스트/runtime 재실행을 하지 않았다.
- 미해결: M9 QA 차단 요인은 없다. M10·배포·실사용 DB 적용 및 운영 범위는 이번 완료에 포함하지 않는다.
