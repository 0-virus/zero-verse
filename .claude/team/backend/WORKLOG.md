# backend 작업 기록

> append-only. 기존 항목을 수정·삭제하지 않는다.

## 2026-09-06 — 팀 상태 기준선 생성

- 한 일: 기존 M0~M2 worklog와 현재 `feature/M2-settings` 워킹트리를 기준으로 backend 역할 상태를 초기화했다.
- 산출물: `.claude/team/backend/CLAUDE.md`, `STATE.md`, 이 파일.
- 검증: 리더가 역할 파일 존재와 Git/worklog 참조를 재확인한다.
- 미해결: M2 backend 변경의 최종 테스트·리뷰·머지.

## 2026-09-06 05:00 KST — M2 종료 검증

- 한 일: `feature/M2-settings` HEAD `ed8f2c1`의 최신 `BlogSettingsDtos.UpdateBlogRequest` slug 검증 변경과 관련 HTTP 회귀를 재독하고, M2 HTTP 계약·보안 allowlist·동시성 경로를 소스 및 실행 산출물과 대조했다. 제품 코드·Gradle 파일은 수정하지 않았다.
- 산출물: `build/test-results/test/`에 전체 테스트 XML 53개, `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`(64,558,914 bytes, 04:54:02 생성).
- 검증: Docker Server `29.3.1` 및 Testcontainers `mysql:8.4`/Ryuk 기동 확인. `./gradlew.bat cleanTest test` 대신 PowerShell에서 `./gradlew.bat cleanTest test`를 실제 실행해 `BUILD SUCCESSFUL in 12m 18s`; XML 직접 집계 `files=53, tests=347, failures=0, errors=0, skipped=0`. `./gradlew.bat bootJar` `BUILD SUCCESSFUL in 9s`; `./gradlew.bat build -x test` `BUILD SUCCESSFUL in 6s`. M2 HTTP·동시성 suite(Blog/User settings controller, public controller, SettingsFlow, SecurityAccessControl, ConcurrentSetup, ConcurrentUpdate)는 모두 0 failure/error/skip.
- 발견: 실행 중인 최신 JAR의 `GET /v3/api-docs`에서 정의 scheme은 `bearerAuth` 하나인데 M2 보호 operation 6개가 미정의 `bearer`를 참조한다. root `security`가 `bearerAuth`라 공개 Blog GET 및 auth register/signin/refresh/signout 5개가 문서상 Bearer를 상속한다. M2 7개 operation responses가 모두 `200`만 포함해 실제 400/401/404/409 오류 계약도 문서화되지 않았다. 부모에게 정확한 경로·수정안(Controller scheme명 정합, 공개 operation `security=[]`, 실제 오류 응답 `@ApiResponses` 및 JSON smoke)을 보고했으며 승인 전 수정은 보류했다.
- 미해결: OpenAPI 문서 gap의 최소 수정·독립 QA 재검토·M2 최종 승인/머지. RISK-0005 실제 HTTPS Refresh-cookie smoke는 최초 배포 전 OPEN. 타 역할의 동시 워킹트리 변경은 보존했다.

## 2026-09-06 05:02 KST — 검증 명령 표기 정정

- 정정: 직전 항목의 Gradle 명령 설명은 중복 표기였다. 실제 실행 명령은 PowerShell의 `.\gradlew.bat cleanTest test`, `.\gradlew.bat bootJar`, `.\gradlew.bat build -x test`이며 모두 exit code 0이다.

## 2026-09-06 05:30 KST — M2 OpenAPI 최소 보완 및 전체 재검증

- 한 일: 부모 승인 범위에 따라 runtime endpoint·DB·보안 정책은 바꾸지 않고 Controller annotation만 보완했다. `UserSettingsController`와 `BlogSettingsController`의 보호 operation은 등록된 `bearerAuth`를 참조하고, `BlogPublicController`의 공개 Blog GET과 `AuthController`의 register/signin/refresh/signout은 `security=[]`를 명시했다. M2 operation의 실제 400/401/404/409(해당 operation별) ErrorCode와 JSON `ApiResponse` envelope를 `@ApiResponses`로 문서화했다. 401에는 기존 계약의 만료 `AUTH_002`와 미인증/무효 토큰 `AUTH_004`를 함께 표기했다.
- 발견·수정: 처음 `@ApiResponses`만 추가했을 때 Springdoc의 자동 200 응답이 대체되어 `get /api/v1/blogs/me`의 성공 schema `$ref`가 빈 값이 되는 실패를 확인했다. 성공 응답에 `200 + useReturnTypeSchema=true`를 모든 M2 operation과 공개 Blog GET에 추가해 기존 concrete `ApiResponse<...>` return schema를 보존했다. 테스트는 Springdoc 기본 `*/*` media type과 JSON media type 모두를 다룰 수 있도록 content 하위 schema `$ref`를 직접 순회하되, concrete `$ref`가 없으면 실패한다.
- 변경 파일: `src/main/java/com/zeroverse/domain/user/controller/UserSettingsController.java`, `src/main/java/com/zeroverse/domain/blog/controller/BlogSettingsController.java`, `src/main/java/com/zeroverse/domain/blog/controller/BlogPublicController.java`, `src/main/java/com/zeroverse/domain/auth/controller/AuthController.java`, `src/test/java/com/zeroverse/config/OpenApiConfigTest.java`.
- 실패 증거: 수정 중간 단계 `.\gradlew.bat test --tests com.zeroverse.config.OpenApiConfigTest`는 `3 tests completed, 1 failed`, `[get /api/v1/blogs/me의 성공 DTO schema] Expecting actual: "" to contain: "ApiResponse"`로 종료했다. 이는 새 회귀 테스트가 200 schema 소실을 차단한 결과이며, 테스트 assertion 완화로 숨기지 않고 `useReturnTypeSchema=true`로 annotation을 고쳤다.
- 검증: `.\gradlew.bat test --tests com.zeroverse.config.OpenApiConfigTest` 최종 `BUILD SUCCESSFUL in 2m 14s`, XML `tests=3, failures=0, errors=0, skipped=0`. `.\gradlew.bat cleanTest test` 최종 `BUILD SUCCESSFUL in 7m 50s`. `build/test-results/test/` XML 53개를 직접 집계해 `files=53, tests=348, failures=0, errors=0, skipped=0`, 실패 case 0을 확인했다(기존 347 + OpenAPI regression 1). `.\gradlew.bat bootJar` `BUILD SUCCESSFUL in 8s`; `.\gradlew.bat build -x test` `BUILD SUCCESSFUL in 5s`.
- 산출물: `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`, 64,560,071 bytes, 2026-09-06 05:27:49 KST, SHA-256 `5F9724EC3D38B7B3D9F41B00C6917B744937876A2423DCAA028390D76CE2A69B`. `git diff --check`는 오류 없이 완료했다. Git stage/commit/push/branch 조작은 하지 않았다.
- 미해결: 부모 smoke 앱이 새 JAR로 재기동된 뒤 `/v3/api-docs` 네트워크 JSON에서 concrete 200 schema, `bearerAuth`, 공개 `security=[]`, 오류 응답을 확인해야 한다. 독립 QA 최종 검토와 M2 승인/머지 전이다. RISK-0005의 실제 HTTPS Refresh-cookie smoke는 최초 배포 전 OPEN이다.

## 2026-09-06 05:38 KST — M2 최종 인계 및 M3 Architecture 결과 동기화

- 루트·QA가 전체 XML 53개/348 tests/failures 0/errors 0/skipped 0, 새 JAR SHA-256, 실제 `/v3/api-docs` JSON을 독립 대조해 M2 최종 `APPROVE`를 확정했다. OpenAPI 보호·공개 보안 표기, concrete 성공 schema, M2 오류 응답 문서화가 runtime smoke와 일치함을 확인했다.
- M3 Architecture 독립 검토 상세본을 `docs/governance/AGENT-BRIEFS.md` 공통 형식으로 제출했다. 권고 `APPROVE_WITH_CHANGES`, 확신도 92/100, 위험 HIGH. blog write lock은 동시 mutation 직렬화에는 유효하지만 전체 ID 집합 검증만으로 stale reorder를 거부할 수 없으므로 revision/ETag/snapshot precondition 또는 last-write-wins를 별도 확정해야 한다. M4 Post의 모든 `category_id` 변경 경로가 동일 blog lock에 참여해야 삭제 후 dangling category reference를 막을 수 있다는 인계 조건을 명시했다.
- 진행자가 M3 Architecture 결과를 회의록에 취합했고 Q1~Q4 사용자 승인을 요청했다. 승인 전 M3 backend 구현은 시작하지 않는다.
- 상태: M2 실제 Git stage/commit/merge와 M3 Q1~Q4 사용자 승인이 남은 유일한 현재 gate다. 부모가 이후 backend 소유 경로와 역할 기록을 명시적으로 stage/commit한다. 이 인계 후 backend STATE/WORKLOG와 M2 변경 파일을 동결한다. 제품 코드 추가 수정과 Git 조작은 하지 않았다.

## 2026-09-06 05:42 KST — M2 merge 완료 및 M3 사용자 결정 대기

- 부모가 M2 PR #8을 실제 merge했다. GitHub mergedAt은 `2026-09-05T20:41:12Z`(KST `2026-09-06 05:41:12`), merge SHA는 `4c129e20f58a6ccb9c61246d103934702516c295`다. 공유 checkout은 `dev`로 fast-forward 동기화됐고 검증 완료 제품 tree와 동일함을 확인했다.
- M2 backend 작업은 검증·독립 QA·실제 smoke·OpenAPI 대조·merge까지 완료됐다. 이전 항목의 XML 53개/348 tests/실패·오류·skip 0과 JAR SHA 증거를 최종 근거로 유지한다. 이번 마감 인계에서는 추가 테스트나 제품 수정 없이 상태만 동기화했다.
- M3 상태를 `USER_DECISION_REQUIRED`로 전환했다. Architecture 독립 검토(`APPROVE_WITH_CHANGES`, 92/100, HIGH)는 상세 제출·회의록 취합 완료이며, Q1~Q4 사용자 승인 전에는 제품 구현을 시작하지 않는다.
- 이후 backend 소유 파일·STATE/WORKLOG는 동결한다. 부모가 M2 merge 기록과 M3 계획 기록을 포함한 문서 stage/commit을 수행한다. 본 항목 이후 Git 조작·제품 코드 변경·추가 테스트는 하지 않는다.

## 2026-09-06 17:15 KST — M3 구현 상태·지침 갱신

- 한 일: 현재 `feature/M3-categories` HEAD 표기 `1690731`의 M3 구현 현실을 확인하고 `src/main/java/com/zeroverse/AGENTS.md`, backend `STATE.md`를 M3 category 계약·Jackson strict input·검증 경계에 맞춰 갱신했다. 기존 M2 이력은 역사 요약으로 보존했다.
- 저자/소유: 초안 `m3_backend`, 후속 `m3_backend_resume`, 제한 migration/wrapper/policy 보완 `m3_backend`; 독립 QA 최종 검토와 root 최종 승인은 미완료다. CategoryMigrationTest와 wrapper의 별도 작업자 소유 이력을 덮어쓰지 않았다.
- 구현 근거: ADR-0005 `ACCEPTED`, M3 회의 `APPROVED`, 사용자 `시작` 승인. category service/controller/repository, V2 forward migration, CAT_004·binding 400·strict numeric 설정의 현재 경로를 source와 대조했다.
- 검증 사실: root XML 최신 snapshot은 service14/controller5/migration1/common5 = 25 tests, failures/errors/skips 0, `BUILD SUCCESSFUL` 2m55s다. 이후 controller 6번째 HTTP 경계와 LWW assertion 변경은 그 snapshot에 포함되지 않아 통과로 기록하지 않는다. 별도 관련 실행은 `build/m3-backend-related.log`에 21 tests/1 failure(numeric enum), exit 1로 남아 있다.
- 미실행/대기: 이 문서 전용 턴에는 테스트·Gradle·Git을 실행하지 않았다. 최신 변경을 포함한 full backend test/build/bootJar, JAR/API smoke, QA 최종 검토는 root 인계 상태로 결과 대기다. API 8080은 미기동이며 Vite 5173과 합성 DB 13306은 준비 상태다.

## 2026-09-06 17:49 KST — M3 full 검증·smoke 결과 동기화

- 한 일: root가 전달한 최종 실행 증거를 backend `STATE.md`에 반영하고, 기존 25-test snapshot과 21-test numeric-enum 실패를 중간 역사로 유지했다. 제품 소스·테스트·Gradle·Git은 이 문서 턴에서 수정하지 않았다.
- 검증: `build/m3-root-full-build.log` 기준 full build exit 0, `BUILD SUCCESSFUL in 10m 24s`. XML 직접 집계 56 suites/370 tests, failures 0, errors 0, skipped 0. service14/controller6/migration1/common5가 포함되어 owner HTTP 경계와 LWW 변경까지 통과했다.
- 산출물·smoke: `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`가 2026-09-06 17:13:54 KST 생성됐다. PID 4904 local API(127.0.0.1:8080) 기동 후 V1/V2 success=1/1, users/blogs/categories/posts 행 수 1/1/1/1/0 보존을 확인했다. 기동 전 V1 success=1도 확인했다.
- API/QA: 실제 Swagger JSON에서 category GET `security=[]`, POST `bearerAuth`를 확인했고 QA HTTP script는 exit 0이었다. smoke 중 2계정 추가로 행 수가 증가했으며 해당 데이터는 삭제하지 않는다.
- 남은 gate: 브라우저 FE 검증과 QA/root 독립 최종 검토 전 M3 최종 승인·merge는 보류한다.

## 2026-09-06 17:50 KST — smoke 행수 표기 정정

- 정정: 직전 항목의 네 테이블 행수는 `users/blogs/categories/posts = 1/1/1/0`이다. V1/V2 Flyway success 값은 별도이며, 기존 `1/1/1/1/0` 표기는 오타다.
