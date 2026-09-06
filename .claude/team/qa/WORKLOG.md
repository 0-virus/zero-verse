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
