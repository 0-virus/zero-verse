# M2 독립 QA 리뷰

- 기준: `feature/M2-settings`의 2026-09-06 종료 검증 작업 트리. 원래 기준 HEAD는 `ed8f2c1`, PR #8 원격 HEAD는 `403eb31`이다.
- 범위: FR-SETTINGS-01~04, FR-BLOG-01, NFR-04·09, PRD §7·§9-J/K·§10~§12, 디자인 정본, ADR-0004.
- 판정: **APPROVE (최종 독립 QA, confidence 96/100)**. 최신 FE/BE 전체 gate, stale mutation, 새 JAR OpenAPI smoke 및 리더의 실제 가입→설정→공개·1440px smoke가 모두 통과했다. 배포 전 운영 위험과 문서 정합성 후속은 남지만 M2 소스·계약·테스트에 blocking finding은 없다.

## 독립 확인 증거

| 영역 | 직접 확인한 근거 | 결과 |
| --- | --- | --- |
| BE 전체 | `build/test-results/test` 최신 53 XML 직접 합산 + backend 보존 로그 | 348 tests / 0 failures / 0 errors / 0 skipped. `cleanTest test`와 bootJar/build exit 0, JAR SHA-256 `5F9724EC…A69B` 일치. |
| BE OpenAPI | 새 JAR PID 24560(05:33:05 기동)의 `GET /v3/api-docs`·`/swagger-ui.html` 직접 조회 | `bearerAuth` scheme, M2 보호 6개 `bearerAuth`, public blog/auth 4개 `security=[]`, DTO/error envelope refs·401 codes 확인; swagger 302. |
| FE 전체 | `build/m2-fe-final-test.log`, `m2-fe-final-lint.log`, `m2-fe-final-build.log` | 21 files / 250 tests pass, failures·skips 0, lint/build exit 0. |
| 실제 smoke | 새 JAR·별도 MySQL/Vite의 guest/auth/browser/API 기록 | guest login/My Blog→signin, 로그인 nickname/title/slug 카드, settings 분리 저장·복구, avatar90·공개 image, 이전 slug 404, retry, 1440px을 재확인. |

## 발견 및 판정

### F-M2-01 — stale slug 회귀 테스트가 fake-pass 가능 (해소 확인)

- `frontend/src/test/settingsBehavior.test.tsx:782–843`에서 A 요청 handler는 `aResolved!()`를 호출한 뒤 `return res`한다(`:795–800`). 테스트는 A의 실제 `response.text()`/`parseEnvelope()`/React state continuation이 끝났다는 보장 없이 `await aDone` 후 B hero를 확인한다(`:835–843`).
- 이 순서에서는 `cancelled` guard를 제거해도 A의 늦은 성공이 반영되기 전 B assertion이 통과할 수 있다. 같은 이유로 늦은 실패 경로도 독립적으로 증명하지 않는다.
- 코드 자체의 guard는 `frontend/src/pages/BlogPage.tsx:31,42,46,57–69`에서 성공·실패·로딩 상태를 `cancelled`로 차단하는 구조다. 그러나 NFR-09와 PRD §12는 실효성 있는 회귀 증거를 요구한다.
- 필수 조치: 요청 진입/완료를 관측하고 `response.text()` 또는 최종 DOM 상태까지 `act`/`waitFor`로 flush한 뒤, A 늦은 성공과 A 늦은 실패 각각에서 B hero·오류·재시도 상태가 보존되는지 확인한다. FE 소유자가 테스트를 수정하고 최신 전체 FE gate에서 재실행해야 한다.
- 해소: 최신 테스트는 body read 관측·실제 요청 진입·`act`/flush와 늦은 성공·404 실패 케이스를 포함한다. 임시 transform으로 guard 3곳을 제거한 mutation에서 원본 SHA-256은 불변이고 두 선택 테스트가 모두 기대대로 exit 1이었다(`build/m2-stale-mutation.log`). 최신 FE 전체 21 files/250 tests도 통과했다.

### F-M2-02 — BLOG_004 복구 테스트의 slug 경계가 약함 (해소 확인)

- `frontend/src/test/settingsBehavior.test.tsx:140–163`은 BLOG_004 후 `/blogs/me` 조회와 `navigate` 결과로 “도착: 블로그 페이지”가 나타나는지만 확인한다. `already-done` slug가 실제 URL에 사용됐는지 assertion이 없어, 잘못된 slug navigation도 통과할 수 있다.
- 소스 `frontend/src/pages/BlogInitialSetupPage.tsx`의 복구 경로는 `refreshUser()` 후 `getBlog()`와 slug 기반 이동을 수행하는 방향으로 확인됐다. `FR-SETTINGS-04`, PRD §7.2, ADR-0004가 요구하는 자기 블로그 복구의 최종 주소를 테스트에서 고정해야 한다.
- 필수 조치는 아니지만 `useLocation()`/라우트 probe로 `/blog/already-done`을 단정하는 것이 바람직하다. 복구 조회 실패 테스트와 함께 정확한 error boundary도 유지한다.
- 해소: `PathProbe`가 최종 pathname `/blog/already-done`을 단정하고, 전체 FE gate가 통과했다.

### F-M2-03 — OpenAPI 보안·오류 문서 결함 (해소 확인)

- 리더의 실제 `/v3/api-docs` 확인에서 등록 scheme `bearerAuth`와 M2 보호 operation의 `bearer` 참조 불일치, 공개 Blog GET의 root bearer 상속, M2 오류 responses 누락이 확인됐다. 이는 REQUIREMENTS NFR-05, PRD §12의 Swagger/보안 DoD 위반이었다.
- 현재 작업 트리의 `AuthController`, `BlogPublicController`, `BlogSettingsController`, `UserSettingsController`는 `@SecurityRequirements`, `bearerAuth`, M2 `@ApiResponses`를 추가했고, `OpenApiConfigTest`는 보호 operation·공개/인증 발급 operation·JSON envelope 응답을 실제 `/v3/api-docs`에서 검사한다.
- 해소: 최신 BE full 53 XML/348 tests/0/0/0, bootJar/build exit 0 및 새 JAR PID 24560의 `/v3/api-docs` 직접 확인으로 등록 `bearerAuth`, M2 보호 6개 명시 참조, public blog/auth 4개 `security=[]`, 200 DTO refs·오류20개 application/json envelope refs·401 `AUTH_002`+`AUTH_004`를 검증했다. 이전 PID 8264 구 JAR 결과는 폐기했고, 새 JAR 재기동 결과만 증거로 사용한다.
- 재확인 이력: 구 PID 8264는 05:04:35 기동되어 05:27:49 갱신 전 JAR를 사용했으므로 그때의 `/v3/api-docs` 결과는 폐기했다. 새 JAR class annotation에는 `bearerAuth`가 포함됨을 `javap`로 확인했고, 재기동 PID 24560(05:33:05)의 결과를 최종 증거로 사용한다.

### F-M2-04 — 설정 카드 데이터 손실·복구 경로는 현재 결함 미확인 (non-blocking)

- `frontend/src/pages/SettingsProfilePage.tsx:78–123`의 dirty/revision/generation 분리와 `:327`, `:452`의 카드별 loading 전 `fieldset` 잠금은 전체 PUT이 로드 전 빈 값을 덮는 위험을 줄인다. 저장 중 입력 변경도 revision 비교로 오래된 응답을 버리는 구조다.
- 집중 테스트에는 로드 전 입력/저장 차단, 카드별 지연 GET, 저장 중 입력 보존 및 실패 독립성이 포함되어 35개가 통과했다. 실제 smoke에서도 프로필과 블로그를 별도 저장·새로고침 후 복구한 경로가 보고됐다.
- stale 회귀·계정/지연 응답·실패 조합은 최신 FE 전체 gate와 mutation 증거로 보강되었다. 배포 전 RISK-0005/RISK-0007은 별도 운영 위험이다.

### F-M2-05 — 공개 owner `bio`는 개인정보 최소화 확인 필요 (non-blocking, 위반 확정 아님)

- `PublicBlogResponse.OwnerInfo` 및 `BlogSettingsService.getPublicBlog`는 `id`, `nickname`, `profileImageUrl`, `bio`를 반환한다. `BlogPublicControllerTest`는 password/email/name 비노출을 확인하며 실제 공개 smoke도 email/name/birthDate 미포함을 확인했다.
- FR-BLOG-01은 “소유자 기본 정보”만 정하고 필드 목록은 고정하지 않았고, FE 타입/테스트는 `bio`를 현재 계약으로 사용한다. 따라서 현재는 명백한 요구사항 위반으로 판정하지 않는다.
- 다만 디자인 정본의 Blog hero는 avatar/title/description/slug 중심이며 우측 owner panel이 없다(PRD §7.2, §9-O). 외부 공개 전 `bio`를 기본정보로 유지할지 제품 결정 또는 개인정보 최소화 검토를 남겨야 한다.

### F-M2-06 — 이미지 변경·공개 slug 정책은 범위 정합 (non-blocking)

- 프로필 `변경` 버튼은 파일 업로드를 가장하지 않고 URL 입력으로 포커스를 이동한다(`SettingsProfilePage.tsx:331–345`). 실제 S3/presigned 업로드는 M4 범위이므로 M2 blocking stub으로 보지 않는다.
- slug 변경은 `BlogPage`와 backend 테스트가 이전 주소 404/새 주소 성공을 확인하는 ADR-0004·PRD §9-J 정책이다. alias/redirect를 M2에 추가하지 않는 RISK-0007은 외부 공개 전 남은 운영 위험이다.

## M2 종료 조건

1. **통과:** F-M2-01 늦은 성공·실패 회귀는 실제 body read/React flush와 mutation으로 검증되었고 FE 전체 gate가 통과했다.
2. **통과:** BE 전체 53 XML/348 tests/0/0/0·bootJar/build 및 새 JAR `/v3/api-docs`에서 보호 `bearerAuth`, 공개 GET·auth4 `security=[]`, M2 공통 envelope 오류/DTO를 확인했다.
3. **통과:** 리더가 최신 산출물로 가입→initial-setup→settings 각 카드 저장/복구→slug 변경→공개 블로그 및 1440px 레이아웃과 guest/auth 셸을 재확인했다.
4. **리더 절차:** `docs/worklog/M2-settings.md`에 실제 최신 검증과 `[머지]` 기록을 남기고 PR #8의 `dev` 머지 상태를 확인한다. QA는 해당 leader-owned 파일을 수정하지 않는다.
5. RISK-0005(운영 HTTPS Secure/Strict 쿠키 smoke)는 M2 완료와 별개로 배포 전 닫아야 한다.

## M3 acceptance 핵심 선행조건

- M2 종료·PR 머지·worklog 증거와 Q1~Q4 사용자 승인 전에는 M3 구현을 시작하지 않는다. 현재 `docs/governance/meetings/M3-20260906-categories.md`는 심의 중이며 계약 제안은 미확정이다.
- FR-CAT-01~05와 PRD §5.4·§7·§9-H/R·§10에 맞춰 owner write/public read, 루트+1단계, `DEFAULT/GENERAL/LOCKED`, DEFAULT/LOCKED 불변, soft delete·하위 cascade·실제 posts의 DEFAULT 이동·같은 부모 전체 order 배열을 먼저 고정한다.
- V1의 categories name/order unique가 deleted_at을 포함하지 않고 posts/universes 테이블만 존재한다(`V1__init.sql:57–108,148–165`). Q1 forward migration/동시성, Q2 잠금·부모·순서, Q3 공개 글 수와 FR-BLOG-02 접근범위, Q4 setup 시작 카테고리 부분 실패 복구를 승인된 계약·실제 MySQL 테스트로 검증해야 한다.
- M3 DoD는 가짜 글 수·stub·부분 목록 reorder를 허용하지 않으며, BE repository/controller/integration + FE Testing Library/1440px 독립 검증 및 복구/rollback 시나리오를 포함해야 한다.

## 검토한 정본

`.claude/CONSTITUTION.md`, `AGENTS.md`, `CLAUDE.md`, `.claude/team/qa/CLAUDE.md`, `.claude/team/qa/STATE.md`, `docs/REQUIREMENTS.md`, `docs/PRD.md`, `docs/design/AGENTS.md`, `docs/design/DESIGN-SYSTEM.md`, `docs/governance/README.md`, `docs/governance/AGENT-BRIEFS.md`, `docs/governance/decisions/ADR-0004-error-codes-and-slug-policy.md`, `docs/governance/meetings/M3-20260906-categories.md`, `docs/worklog/M2-settings.md`, `src/main/resources/db/migration/V1__init.sql`, 현재 Category/SecurityConfig/ErrorCode 및 M2 source/test.

## 2026-09-06 추가 독립 확인 — 홈 셸·문서 정합성

### F-M2-07 — 홈 셸의 기본 블로그·비로그인 경로 (교정 확인)

- 최신 FE diff에서 `AppRoutes`가 `useAuth()`의 실제 `user`를 `AppShell`에 전달하고, `AppShell.tsx:39,70,102,105`가 이를 `TopBar`, `SideNav`, `RightPanel`에 props로 전달한다. `useOptionalAuth`를 제거해 공개 화면의 선택적 컨텍스트 의존도 없앴다.
- `SideNav.tsx:25`의 slug 없는 My Blog는 `/signin`으로, `TopBar.tsx:19–22,82`의 비로그인 프로필은 `로그인`→`/signin`, 로그인 사용자는 실제 `nickname`→`/settings`로 연결된다. 종전 `/blog/me`·`제로별` 고정 경로는 최신 셸 diff에서 제거됐다.
- `SideNav.test.tsx:22–45`, `TopBar.test.tsx:14–22`, `settingsBehavior.test.tsx:1090–1141`이 guest fallback, 로그인 사용자 slug, 실제 `AppRoutes`의 초기→갱신 slug 및 우측 패널 제목을 검증한다. 부모가 전달한 1440px smoke에서도 `내 블로그` 클릭 후 새 slug hero, 우측 패널 제목/slug·관리/작성 링크, avatar 90px·URL focus·공개 `profileImageUrl` 렌더를 확인했다.
- 판정: 이전 `/blog/me` 결함과 허위 guest 프로필 표시는 **교정 확인**, 최신 전체 FE gate도 통과했다. 알림·Universe 패널은 기존 M8/M5 placeholder 범위를 유지하며 새 API·정책을 추가하지 않았다.

### 문서·운영 기록 독립 감사

- `.claude/CONSTITUTION.md`는 `git diff`상 변경이 없고, 기존 `docs/worklog/**`·`docs/governance/**` 결론과 과거 위임 승인/정정 기록은 삭제·덮어쓰기되지 않았다. `git diff --check`도 clean이었다.
- 최신 `docs/governance/README.md` diff는 Claude 전용 구현/분기 표현을 “구현 역할·리더 배정”으로 동기화한 운영 문구뿐이며, 독립 3인 심의·`LOW` 외 사용자 명시 승인·과거 회의/ADR 담당 기록 보존을 명시한다. 제품 계약·승인 등급을 새로 만들거나 우회하지 않는다.
- `AGENTS.md:77–79`, `CLAUDE.md:15`, `.claude/team/README.md:28,35`, `docs/Codex-에이전트-팀-운영-가이드.md:17`의 신규 문구는 사용자의 명시 승인(“Codex backend/frontend 구현 + 독립 QA/리더 검토, 연속 마일스톤”)을 그대로 반영한다. 동시에 `AGENTS.md:92`, 팀 README 완료 규약, `docs/governance/README.md:84–87`의 `MEDIUM/HIGH/BLOCKED` 사용자 승인 규칙과 M2 worklog:965의 M3 구현 보류도 유지된다. 현재 문서에 M2 최종 승인·M3 구현 완료라는 허위 주장은 없다.
- 비차단 정합성 위험 1: `AGENTS.md:79`·팀 README `:35`의 “연속 진행/새 승인 안건은 진행하면서 요청”은 인접한 승인 전 구현 보류 문구와 함께 읽어야 하며, M3 구현 전역 허가로 오독되지 않게 다음 문구에서 “승인 전에는 준비·독립 검토만”이라고 한정하는 편이 안전하다. 문서 소유자인 리더에게 보고만 하고 수정하지 않았다.
- 비차단 정합성 위험 2(해소): 이전 감사 시점에는 `docs/governance/meetings/M3-20260906-categories.md` 상단과 §7 상태가 혼재했으나, 리더가 현재 두 곳을 `USER_DECISION_REQUIRED`로 통일하고 §10 정정 기록을 append했다. 승인 증거로 오인되지 않도록 미확정·사용자 승인 대기 문구도 유지된다.
- 비차단 절차 위험 3: `docs/worklog/M2-settings.md:935`의 기존 `[머지]`(“아직 없음”) 뒤에 새 `[개발 기록]`이 append되어 고정 섹션 순서(`AGENTS.md:106`)와 어긋난다. 과거 기록 보존을 위해 재배열하지 말고, 리더가 최종 최신 검증을 기존 `[리뷰]` continuation 또는 별도 명시된 종료 검증 항목으로 append할지 결정해야 한다.
- 판정: 문서 변경은 사용자 승인 범위·역할 소유권 안에 있고, `qa/**`·`qa STATE/WORKLOG` 외 제품/리더 문서는 수정하지 않았다. 위 3건은 현재 M2 기능/보안 blocking이 아니며 리더의 정정 기록으로 모두 해소되었다. `docs/PM-M3-readiness.md`의 준비 문구에 남은 과거 `PROPOSED` 참조는 승인 문서가 아니라는 한정과 함께 별도 비차단 정합성 후속으로 남긴다.

## 2026-09-06 FE 최종 게이트 독립 대조

- 부모 실행 보고: FE 21 files / 250 tests, failures 0 / skipped 0, lint exit 0, build exit 0. 이 컨텍스트에서는 동일 전체 실행을 중복하지 않고 보고된 명령 결과와 최신 소스·테스트 diff를 독립 대조했다.
- F-M2-01: `settingsBehavior.test.tsx`의 성공·실패 stale 경로가 A 요청 진입을 기다리고 `Response.text()` read 후 `act`/microtask flush를 거친다. guard를 제거하면 B hero·오류 보존 assertion이 실패하도록 설계되어 fake-pass 우려가 해소되었다.
- 독립 mutation: `build/m2-stale-mutation.mjs`가 임시 Vite transform에서만 cancellation guard 3곳을 제거했고 원본 SHA-256 전후가 동일했다. 선택한 늦은 성공·늦은 실패 2개가 각각 exit 1(기대된 A hero/없음 오염)로 실패했으며, 28개 filtered skip은 mutation 선택 실행의 skip이지 전체 FE gate skip이 아니다(`build/m2-stale-mutation.log`).
- F-M2-02: initial-setup `BLOG_004` 복구 테스트가 `PathProbe`로 최종 `/blog/already-done` pathname을 고정한다.
- F-M2-07: `AppRoutes`가 인증 `user`를 `AppShell`에 전달하고 `TopBar`·`SideNav`·`RightPanel`이 이를 소비한다. guest는 `로그인`/`/signin`, authenticated는 실제 nickname/defaultBlog slug로 연결되며 부모 1440px 실측도 동일 흐름을 확인했다.
- FE 독립 판정: M2 FE 관련 blocking finding 없음. BE 전체 test/build·신규 JAR `/v3/api-docs`·리더 최종 smoke도 후속 확인되어 M2 source/evidence gate가 종결됐다.

## M2 최종 독립 QA 판정

- 권고: **APPROVE**, confidence **96/100**, 잔여 위험 **MEDIUM(배포 전 운영·문서 후속)**. M2 소스·공개 API·오류 계약·회귀 테스트·FE/BE gate에 blocking finding은 없다.
- 사실: FE 21 files/250 tests pass, lint/build exit 0; BE 53 XML/348 tests/0 failures/0 errors/0 skipped, bootJar/build exit 0; JAR SHA-256 `5F9724EC3D38B7B3D9F41B00C6917B744937876A2423DCAA028390D76CE2A69B` 일치; PID 24560 새 JAR `/v3/api-docs` 보안·응답 smoke pass; stale guard mutation 원본 SHA 보존 및 2개 의도된 실패; 부모 브라우저/API smoke pass.
- 가정·범위: QA는 FE/BE 전체 실행을 중복하지 않았고, FE/BE 명령 출력은 부모가 보존한 log와 현재 XML/JAR/API를 교차 확인했다. RISK-0005(운영 HTTPS Secure/Strict 쿠키)·RISK-0007(slug 변경 후 이전 URL 단절)은 M2 source approval과 별개로 배포 전에 닫는다.
- 가장 강한 반론과 판단: 문서·운영 기록의 연속 진행 문구와 M2 worklog 섹션 순서가 정리되지 않은 채 merge될 수 있다는 우려가 있었고, M3 상태(`REVIEWING`/`PROPOSED`)도 초기에는 혼재했다. 리더의 승인대기 제한 문구·M3 단일 상태 정정·`[리뷰]` continuation 기록으로 현재는 해소되었다. 이는 기능·보안 결함이 아니며, 이를 이유로 이미 통과한 M2 실행 증거를 뒤집을 사유는 아니다.
- 실패 시나리오: 신규 JAR가 아닌 구 프로세스의 Swagger 결과를 잘못 인용할 위험은 PID 기동 시각을 대조해 배제했다. 운영 HTTPS에서 쿠키가 차단되거나 slug 변경 후 외부 링크가 단절되는 위험은 배포 전 별도 gate로 남는다.
- 필수 후속: 리더는 최신 M2 검증·`[리뷰]`/`[머지]` 및 PR #8 `dev` 머지를 기록한다. M3는 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현·승인하지 않는다. `docs/PM-M3-readiness.md`의 과거 `PROPOSED` 참조는 준비 문서라는 성격을 유지한 채 후속 정합성 점검 대상으로 둔다.
