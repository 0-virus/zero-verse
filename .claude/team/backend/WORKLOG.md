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
