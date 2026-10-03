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

## 2026-09-08 09:55 KST — M3 backend 상태·브라우저 게이트 정합화

- 한 일: 헌법→루트 지침→backend 지침/STATE 순서와 ADR-0005, REQUIREMENTS §6.4/FR-CAT-01~05, PRD §5.4/§9.5/§10~§12, `docs/worklog/M3-categories.md`, `qa/M3-review.md`, QA 최신 STATE를 직접 대조했다. 기존 STATE의 M3 기준 HEAD `1690731`과 “브라우저 전체 대기” 표현을 현재 산출물에 맞게 `STATE.md`에 갱신했다. 제품 source/test, Gradle, 서버·DB, Git 상태는 변경하지 않았다.
- 현재 근거: 실제 Git 읽기에서 branch는 `feature/M3-categories`, HEAD는 `08239e0`이며 `067cd11..08239e0`의 backend `src/**`·Gradle 경로 diff는 없다. M3 backend 구현 커밋은 `067cd1174aeec1452b6c74402e750fdad75c3c05`다. `build/m3-root-full-build.log`/XML의 기존 결과는 `BUILD SUCCESSFUL in 10m 24s`, 56 suites/370 tests, failures/errors/skips `0/0/0`; JAR SHA-256은 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`다.
- 최신 실행 기록 반영: root API PID 8712(`127.0.0.1:8080`)와 기존 M3 합성 DB 13306 재기동, Flyway V1/V2 success `1/1`, 기동 시 `users/blogs/categories/posts = 5/5/18/0` 보존 및 이후 UI 합성 계정 추가를 기록했다. QA HTTP smoke exit 0도 유지했다.
- 브라우저 상태: root가 frontend 구현자와 분리된 CUA에서 native mouse DnD로 `프론트엔드`를 `백엔드` 위로 이동했고 UI notice `카테고리 순서를 저장했습니다.`와 DB active ID/order `19:0, 21:1, 20:2, 22:3`을 확인했다. 이 gate는 root 독립 evidence로 PASS다. `회고` LOCKED 전환 경고·확인창은 보였지만 accept가 timeout/`No dialog is showing`으로 끝났고 DB ID 22가 GENERAL이라 LOCKED 저장·불변 상태는 미검증이다.
- 검증: 상태·로그·정본 재독 후 `STATE.md`를 재독하고 `git diff --check -- .claude/team/backend/STATE.md .claude/team/backend/WORKLOG.md`를 실행해 exit 0을 확인했다. 이 턴에는 Gradle/test/JAR/API/DB를 재실행하지 않았으며, LOCKED 확인 수락 후 QA/root 최종 acceptance와 PR #9 `dev` merge가 남은 gate다. M3 마감 전 M4는 시작하지 않는다.

## 2026-09-08 10:01 KST — LOCKED 확인 결과 시점·실행 종료 경계 정정

- 정정: 앞선 09:55 항목의 `timeout/No dialog is showing` 병기는 두 시점을 압축한 표현이다. 2026-09-08 현재 LOCKED accept 시도는 `timeout → kernel reset`, 이어진 `getTab`도 timeout으로 끝났다. `No dialog is showing`은 2026-09-07 과거 재시도의 결과이며 현재 시도 결과에 포함하지 않는다.
- 종료 경계: 사용자 지시에 따라 M3 검증, PR #9의 `dev` merge, M3 마감 기록 완료 후 이번 실행을 종료한다. M4는 이번 실행에서 착수하지 않으며, 향후 별도 착수 시 동일 blog lock/category 재검증을 참고 인계로만 남긴다.
- 검증 범위: 오늘 상태 정합화 턴에는 Swagger·전체 backend test/build·JAR/API/DB를 재실행하지 않았다. full XML/JAR/Swagger/HTTP와 browser DnD 결과는 기존 root/QA evidence로 표시하고, LOCKED 저장·후속 GET·보호 상태는 미검증으로 유지한다.
- 미해결: 사용자의 OK 직접 조작 또는 지원되는 대체 evidence, QA/root 최종 acceptance, `dev` merge 및 `[머지]` 기록.

## 2026-09-08 10:05 KST — LOCKED 실측 완료 상태 반영

- 한 일: 최신 `docs/worklog/M3-categories.md` 10:05 항목을 읽고 backend STATE의 잔여 UI gate를 정정했다. 기존 Chrome fixture category ID 22 `GENERAL` 및 accept timeout→kernel reset/getTab timeout은 과거 실패로 보존하고, 2026-09-07 `No dialog is showing`도 별도 과거 evidence로 유지했다.
- 현재 근거: root가 frontend 구현자와 분리된 IAB에서 새 blog ID 7을 만들고 category ID 26을 `LOCKED`, `displayOrder=3`으로 저장·재조회했다. 새로고침 후에도 LOCKED가 복원되고 순서 이동·이름 변경·삭제·타입 선택 4개 조작이 disabled, 잠금 저장 성공 notice가 표시됐다. 새 결과는 root 독립 실제 UI·DB evidence로 LOCKED gate PASS이며 QA 직접 조작으로 표기하지 않는다.
- 상태/종료: 차단은 LOCKED가 아니라 독립 QA 최종 acceptance와 PR #9 `dev` merge·M3 마감 기록이다. M3 검증→merge→마감 기록 완료 후 이번 실행을 종료하며, M4는 이번 실행에서 착수하지 않는다. 향후 별도 M4 착수 시 참고할 category lock 인계만 유지한다.
- 검증 범위: 제품 source/test/UI/서버/DB/Git 실행 변경은 없고, 이 상태 정합화 턴에는 테스트·빌드·Swagger·API를 재실행하지 않았다. 위 IAB·DB와 기존 full XML/JAR/Swagger/HTTP는 각각 root 또는 기존 evidence로 구분한다.

## 2026-09-08 10:11 KST — M3 최종 승인·공개 게시 승인 대기 체크포인트

- 한 일: 최신 `docs/worklog/M3-categories.md` 10:11 항목 기준으로 backend STATE를 갱신했다. 독립 QA `/root/m3_final_qa`의 최종 `APPROVE`와 root 최종 승인을 반영하고, 제품 소스는 불변으로 유지했다.
- 현재 근거: 로컬 문서 커밋 `822b4ce1d6c78695e87ac7883e8577b066e03aea`가 생성됐고 `origin/feature/M3-categories`는 `08239e0` 그대로이며 ahead/behind `1/0`이다. push 시도는 STATE/QA/JOURNAL 등 11개 문서 공개 전송에 대한 구체적 사용자 승인 부족으로 자동 승인 검토에서 거절됐다.
- 상태/순서: 검증·QA/root 승인은 완료됐다. 사용자 공개 게시 승인이 오기 전에는 push·PR #9 Ready 전환·merge를 하지 않는다. 승인 후 push→PR Ready→`dev` merge→M3 마감 기록 순으로 진행하고, 마감 기록 완료 후 이번 실행을 종료한다.
- 범위: 제품·테스트·외부/Git 실행은 이 상태 정합화 턴에서 변경하지 않았다. M4는 이번 실행에서 착수하지 않으며, 향후 별도 착수 시 필요한 category lock 인계만 참고로 남긴다.

## 2026-09-09 09:20 KST — M4 비공개 이미지 계약 기술 입력

- 한 일: `REQUIREMENTS.md` FR-POST-01~08/FR-UPLOAD-01~04/NFR-01~09, `PRD.md` §4/§5.12/§13.1, M4 회의록 §6/§10과 실제 `UserSettingsService`·`UserSettingsDtos`·`SecurityConfig`·`JwtAuthenticationFilter`·V1 schema를 대조했다. 사용자 `그렇게 해줘`의 M3 merge 완료 및 M4 비공개 S3 방향 승인 범위에 맞춰 제품 구현 없이 저장/연결 기술안을 작성했다.
- 산출물: 기존 `VARCHAR(500)` URL 컬럼을 canonical private S3 URL 저장소로 유지하고, read DTO에는 60초 signed GET만 반환하는 분리안. key는 `users/{ownerId}/{purpose}/{uuid}.{ext}`로 서버 생성한다. profile/thumbnail/body 연결은 같은 목적의 소유자 resource에만 허용하고, 다른 글·프로필로의 key 재사용은 `User` row `FOR UPDATE` 아래 기존 URL 참조를 재조회해 거부한다. postId 선행 draft나 새 asset table은 만들지 않는 최소 권고다.
- 검증: `UserSettingsService`는 현재 raw `profileImageUrl`을 저장하고, 여러 read mapper가 raw URL을 반환하며, JWT filter는 claims만 검증한다는 실제 상태를 확인했다. AWS 공식 [conditional write](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html)는 signed `If-None-Match: *`의 기존 key `412`·동시 충돌 `409`를 설명하고, [S3Presigner](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html)는 PUT/GET 서명과 browser compatibility 확인을 제공한다. 이번 턴은 문서·코드 read-only 조사라 Gradle/test/S3 실측은 실행하지 않았다.
- 미해결: signed `Content-Length`·`Content-Type`·`If-None-Match`의 실제 browser File PUT, post-upload HEAD size/type, MIME magic negative, overwrite 및 orphan-cleaner 경쟁은 구현 첫 acceptance gate다. HEAD metadata만으로 바이트 MIME 검증 완료를 주장하지 않는다. 60초 signed GET은 권한 변경 후 즉시 회수되지 않는다. 상세 API/DB/error code 계약과 구현은 independent review 및 사용자 승인 후다.

## 2026-09-09 09:27 KST — 이미지 key 이력·lock 순서 반례 추가

- 한 일: 현재 URL 컬럼/PostImage 참조만으로는 thumbnail/profile 교체 시 과거 binding을 보존하지 못한다는 반례와 M3 `BlogRepository.findByIdAndDeletedAtIsNullForUpdate`의 `JOIN FETCH b.user` 잠금 경계를 재확인했다. 제품 구현·스키마 변경은 하지 않았다.
- 산출물: 정책을 두 갈래로 분리했다. (A) detached key의 소유자 재사용을 명시 허용하면 기존 URL 컬럼 + owner/purpose 검증 + 현재 참조 검색으로 동시 공유만 차단한다. (B) 한 번 bound된 key의 타 resource 영구 재사용 금지는 기존 컬럼만으로 불가능하며, 최소 `image_uploads` 상태/이력 tombstone 1개 또는 동등한 S3 key-state 보존이 필요하다. postId 선행 draft는 두 안 모두 요구하지 않는다.
- 검증: M3 category의 실제 lock query가 `SELECT b JOIN FETCH b.user ... @PESSIMISTIC_WRITE`임을 확인했다. M4 blog-scoped binder는 `blog → user → post`, profile/cleaner는 `user` 순서만 사용해야 하며 `user → blog` 역순을 만들면 논리적 deadlock cycle이 가능하다. cleaner의 LIST→check→DELETE와 binder는 동일 owner lock으로 재조회해야 하지만 S3 외부 I/O를 DB transaction에 포함하는 lock 점유·장애 재시도 비용이 남는다. 이번 검토는 `git diff --check -- .claude/team/backend/STATE.md .claude/team/backend/WORKLOG.md` 외 제품 테스트/Gradle/DB/S3 실측을 실행하지 않았다.
- 미해결: detached key 재사용 허용 여부, 영구 no-rebind를 선택할 경우 새 상태표/동등 key-state 방식, 실제 DB lock·S3 I/O 경쟁 acceptance를 root가 상세 계약과 사용자 승인 안건으로 분리해야 한다.

## 2026-09-09 12:28 KST — M4 U0 독립 LocalStack 검증 harness 구현·root 인계

- 한 일: 사용자 승인 범위인 M4 회의록 §12.3 U0만 구현했다. 제품 `src/**`, API/DB/보안 계약, root Gradle은 변경하지 않고 독립 `gradle/u0` 프로젝트를 추가했다. `U0Harness`는 고정 `http://127.0.0.1:14566` loopback endpoint·Region `us-east-1`·StaticCredentialsProvider의 `test/test`만 사용하고, 매 실행 `zeroverse-u0-...` private bucket 하나를 생성한 뒤 public access block 4종, `BUCKET_OWNER_ENFORCED`, origin `http://127.0.0.1:14567` 단일 CORS를 설정한다. 기존 bucket 조회/삭제와 실제 AWS endpoint는 사용하지 않는다.
- 산출물: `gradle/u0/settings.gradle`, `gradle/u0/build.gradle`, `gradle/u0/src/main/java/com/zeroverse/u0/U0Harness.java`. `/`는 고정 `frontend/u0/index.html`만 읽고, `/cases`·`/verify`는 Host/Origin/Sec-Fetch-Site 경계를 확인하며 `no-store`를 붙인다. `/cases` 서명 URL은 첫 요청 시 메모리에서 생성하고 JSON 계약 `{cases:[{id,method,url,headers,bodyBase64,contentType,expectedStatuses:[...],waitMs?:number}]}`으로만 전달한다. 브라우저 전송 headers에는 Host/Content-Length를 넣지 않으며 SDK query에 signed Content-Length 포함을 assert한다.
- 검증 설계: jpeg/png/webp/gif 작은 실제 fixture, empty/5,242,880/5,242,881 경계, same-length byte tamper, MIME spoof, signed type/checksum tamper·missing, replay/overwrite, signed GET positive/invalid/expiry, unsigned private GET, exact/wrong-origin CORS preflight, bounded range GET(최대 5,242,881)·HEAD `ChecksumMode.ENABLED`·Tika content detection을 포함한다. `ENFORCE_IAM=1`이 현재 compose에 없으므로 unsigned GET 200은 설정값으로 PASS하지 않고 명시적 실패로 남긴다. expired PUT도 signed GET expiry와 별도로 포함한다.
- 의존성 근거: AWS 공식 SDK Java v2 release `2.49.6`(https://github.com/aws/aws-sdk-java-v2/releases/tag/2.49.6) 및 AWS Gradle BOM 안내(https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-gradle.html), Apache Tika 공식 다운로드의 지원 3.x line `3.3.2`(https://tika.apache.org/download.html)를 설치 전 확인했다. 독립 Gradle에는 AWS SDK BOM/S3와 `org.apache.tika:tika-core:3.3.2`만 선언했으며 Jackson·framework·제품 abstraction은 추가하지 않았다.
- 실행 명령/실제 출력: `.\gradlew.bat -p gradle/u0 --no-daemon --max-workers=1 clean test`(GRADLE_USER_HOME=`build/u0/gradle-home`)는 `:clean FAILED` — 실행 중 cache를 clean 대상 `build/u0` 아래에 둔 경로 충돌. cache를 `build/u0-gradle-home`로 분리한 동일 명령은 `:compileJava FAILED` — `Fatal Error: Cannot close compiler resources`, `AccessDeniedException ... sdk-core-2.49.6.jar`. `compileJava --stacktrace`도 동일한 Windows `ZipFileSystem` cleanup 오류. `options.release` 제거 후 Temp cache(`%TEMP%\zeroverse-u0-gradle-home`)로 재시도했으나 `http-auth-aws-2.49.6.jar`에서 같은 `AccessDeniedException`; 모두 exit 1이고 U0 self-check/서버 기동은 실행하지 않았다. Java 21.0.12로 별도 wrapper 다운로드 세션을 시도했지만 root 인계 요청에 따라 다운로드 완료 직후 중단(exit 1)했다.
- 환경 확인: `Invoke-RestMethod http://127.0.0.1:14566/_localstack/health`는 `Reachable=True`, `s3=running`, `version=2026.8.1`; `docker ps`는 Docker named pipe 권한 거부로 조회하지 못했다. 이 역할은 컨테이너 재기동/compose 수정/ENFORCE_IAM 추가를 하지 않는다. root가 compile·self-check·server/browser 실행을 인계했다.
- 미해결: root의 독립 compile/test 및 LocalStack U0 실측, FE CUA 실제 raw File PUT/CORS 결과, unsigned GET 403 여부. signed URL·토큰 원문은 출력·로그에 남기지 않았다. 이 harness 통과도 M4 U1 제품 API/DB/FE 구현 또는 전체 M4 완료를 의미하지 않는다.

## 2026-09-09 12:32 KST — M4 U0 WebP fixture self-check 수정

- root가 독립 compile exit 0 후 `--self-check`를 실행했으나 S3 bucket 생성 전 `buildFixtures`에서 WebP Base64의 잘못된 4-byte ending unit으로 exit 1이었다. 해당 fixture 문자열만 올바른 `==` padding으로 교체했다.
- 같은 fixture를 filename hint 없이 `Base64.getDecoder().decode(...)`하고 `new Tika().detect(webp)`가 정확히 `image/webp`인지 확인하는 최소 guard를 추가했다. signed URL·토큰 원문은 출력하지 않는다.
- root가 Gradle/runtime 재실행을 맡았으므로 이 역할은 별도 Gradle·서버·bucket을 실행하지 않았다. 결과는 root 재시도 후 갱신한다.

## 2026-09-09 12:36 KST — M4 U0 root 실측 결과 동기화

- root의 Gradle cache 권한으로 인한 첫 compile 실패 뒤 동일 명령의 escalated compile은 exit 0이었다. WebP fixture 수정 후 `run --args=--self-check`는 run exit 1, 19 case 중 18 PASS, 28 verify 중 27 PASS였다.
- 유일한 실패는 `unsigned-private-get`: private bucket의 unsigned GET이 expected 403 대신 HTTP 200이었다. fresh process와 실제 CUA 브라우저에서도 동일하게 재현됐다. 이는 현재 compose에 `ENFORCE_IAM=1`이 없는 LocalStack 권한 enforcement 상태와 일치하므로 PASS/완화하지 않고 보안 gate BLOCKED로 보존한다.
- 나머지 실측은 4 MIME 및 5MiB 200, over/empty/expired PUT 403, same-length byte tamper 400, signed type/checksum/missing 403, replay 412, signed GET 200/403/403, CORS positive/negative 200/403, HEAD checksum·bounded GET·Tika·negative object 부재 PASS였다. root README와 M4 회의록 §15에 기록됐고 독립 QA가 진행 중이다.
- 이 동기화에서는 제품 코드·검증 harness·compose를 변경하지 않았으며, 실행·삭제·구매를 수행하지 않았다. 제품 U1 또는 전체 M4 완료로 해석하지 않는다.

## 2026-09-23 13:24 KST — SeaweedFS U0-ALT provider 선택 구현·compile 인계

- 한 일: 사용자 `승인. 내가 할 일을 알려줘.` 후 root가 준비한 native SeaweedFS 4.47 runtime에 맞춰 `gradle/u0/src/main/java/com/zeroverse/u0/U0Harness.java`만 최소 수정했다. 기본 실행은 기존 LocalStack `http://127.0.0.1:14566`, `--seaweedfs` 실행만 고정 SeaweedFS `http://127.0.0.1:14568`을 선택하며, 임의 endpoint 입력은 허용하지 않는다. provider 라벨을 시작 출력과 unsigned private GET 실패 detail에 포함했다.
- 산출물: 기존 AWS SDK BOM/S3 `2.49.6`, Tika core `3.3.2`, Region `us-east-1`, synthetic `test/test`, path-style access, `127.0.0.1:14567` harness server를 그대로 재사용했다. 19 CASE/28 VERIFY, signed Content-Length/Content-Type/checksum/If-None-Match assertion, reject expected status, PAB/OwnershipControls 호출은 변경·완화하지 않았다.
- 검증 명령/실제 출력: 기본 wrapper cache 명령 `& .\gradlew.bat -p gradle/u0 --no-daemon --max-workers=1 compileJava`는 `Could not create parent directory for lock file C:\.gradle\wrapper\...` 및 `U0_COMPILE_EXIT=1`. workspace cache 동일 compile은 `:compileJava FAILED`, `AccessDeniedException ... .gradle-home2\caches\...\retries-spi-2.49.6.jar`, `U0_COMPILE_EXIT=1`. 권한 승인으로 재실행한 `& .\gradlew.bat --gradle-user-home .gradle-home2 -p gradle/u0 --no-daemon --max-workers=1 compileJava`는 `:compileJava`, `BUILD SUCCESSFUL in 11s`, `1 actionable task: 1 executed`, `U0_COMPILE_ESCALATED_EXIT=0`.
- 실행 경계: 이 역할은 compile만 수행했다. SeaweedFS native 기동, `--seaweedfs --self-check`, 서버/버킷/객체 생성, DB·제품 runtime, 외부 설치, Git 조작은 수행하지 않았다. root가 14568 서버와 동시 버킷/port 충돌을 관리한 뒤 19/28 및 PAB/OwnershipControls/unsigned GET 403을 독립 실측한다.
- 미해결: SeaweedFS 선택 릴리스의 PAB/OwnershipControls 지원 및 static `test/test` identity에서 무서명 private GET 403 여부는 root runtime evidence 전까지 미판정이다. 미지원이면 조용히 skip하지 않고 provider-specific 결과로 분리 보고한다. 기존 LocalStack 18/19·27/28 및 unsigned GET 200 BLOCKED evidence는 대체하지 않는다.

## 2026-09-23 13:28 KST — SeaweedFS PAB/OwnershipControls HTTP501 provider-specific 보완

- 한 일: root의 `build/u0-seaweedfs-self-check.log`에서 SeaweedFS 4.47 `putPublicAccessBlock`이 HTTP501 NotImplemented로 `createPrivateBucket`에서 중단된 실측을 반영했다. `U0Harness.createPrivateBucket()`에서 SeaweedFS provider에 한해 PAB setup 및 OwnershipControls setup 각각 `S3Exception.statusCode()==501`만 catch하고 `CAPABILITY provider=SeaweedFS feature=PublicAccessBlock UNSUPPORTED HTTP501` 또는 `CAPABILITY provider=SeaweedFS feature=OwnershipControls UNSUPPORTED HTTP501`을 출력한 뒤 CORS/core CASE로 진행하도록 했다.
- 경계: 501 이외 오류는 그대로 throw한다. LocalStack은 기존 동작을 그대로 유지한다. `verifyPublicAccessBlock()`·`verifyOwnershipControls()`는 변경하지 않아 read 501/불일치를 `VERIFY ... FAIL`로 남기며, 28 VERIFY 분모와 전체 exit1을 유지한다. signed Content-Length/Content-Type/checksum/If-None-Match, reject 기대, 19 CASE는 완화하지 않았다.
- 검증 명령/실제 출력: `& .\gradlew.bat --gradle-user-home .gradle-home2 -p gradle/u0 --no-daemon --max-workers=1 compileJava`를 권한 승인으로 실행했다. 출력은 `> Task :compileJava`, `BUILD SUCCESSFUL in 17s`, `1 actionable task: 1 executed`, `U0_COMPILE_PAB_OWNERSHIP_EXIT=0`이다.
- 실행 경계: 이 역할은 runtime/self-check/서버/버킷/객체/DB/Git/외부 설치를 실행하지 않았다. root가 동일 SeaweedFS 14568 server에서 `--seaweedfs --self-check`를 재실행해 capability 출력, 19 CASE, 28 VERIFY와 전체 exit를 기록한다.
- 미해결: PAB/OwnershipControls setup은 SeaweedFS provider-specific UNSUPPORTED로 분리되지만, read-side 2 VERIFY는 실패로 남을 수 있다. unsigned private GET 403 및 나머지 core CASE/VERIFY 실제 결과는 root 재실측 전까지 미판정이다.

## 2026-09-23 13:34 KST — SeaweedFS capability VERIFY status detail 보완

- 한 일: QA 요청에 따라 `verifyPublicAccessBlock()`과 `verifyOwnershipControls()`의 `S3Exception` catch를 최소 보완했다. SeaweedFS의 HTTP501만 각각 `CAPABILITY provider=SeaweedFS feature=PublicAccessBlock UNSUPPORTED HTTP501`/`OwnershipControls UNSUPPORTED HTTP501`로 표시하고, 그 외 S3 예외는 `configuration read failed: HTTP<status>`로 실제 status를 남긴다. 일반 예외 fallback은 유지했다.
- 경계: `Check.passed=false`, check ID, 28 VERIFY 분모, 19 CASE, signed/core 검증과 LocalStack 동작은 변경하지 않았다. root의 `build/u0-seaweedfs-core-self-check.log` 기존 결과 `19/19 CASE PASS`, `27/28 VERIFY PASS`(PAB만 FAIL, OwnershipControls PASS)를 보존한다.
- 검증/실행 경계: root browser 실행을 지연하지 않기 위해 compile·runtime·self-check를 이 수정 후 재실행하지 않았다. 소스 readback으로 두 메서드의 status 분기와 fallback을 확인했으며, root가 최신 소스로 browser/self-check를 수행한다.
- 미해결: 최신 browser output에 capability/status detail이 반영되는지와 unsigned private GET 403 결과는 root 실측 대기다.

## 2026-09-23 13:38 KST — SeaweedFS U0-ALT 최종 root 실측 상태 반영

- 한 일: root 최신 실측과 `build/u0-seaweedfs-browser-evidence.md` 직접 관측 요약을 backend STATE/WORKLOG에 반영했다. 코드·Gradle·compile·runtime은 추가 실행하지 않았다.
- 검증 결과: Java fresh self-check는 `19/19 CASE PASS`, `27/28 VERIFY PASS`, exit1이다. browser run은 `18/19 CASE`, `27/28 VERIFY`; `over-5mb-signed-at-max`만 fetch TypeError/CORS로 HTTP status가 노출되지 않아 CASE FAIL이지만 객체 부재는 PASS다. PAB만 VERIFY FAIL(HTTP501 capability), OwnershipControls PASS, unsigned private GET 403이다. 전체 U0는 BLOCKED이며 TypeError/CORS 원인은 미확정이다.
- 환경/보존: native SeaweedFS server는 loopback으로 기동했고 `volume.max4 → 16` 조정으로 새 bucket volume 소진 준비 실패를 해소했다. 데이터는 보존했으며 root 임시 harness/weed를 종료해 `14567/14568` listener가 0이다. 합성 bucket 4개와 object count `0/8/0/8`은 보존된다.
- 판정 경계: PAB HTTP501은 SeaweedFS provider-specific capability gap으로 기록하고, OwnershipControls PASS·unsigned private GET 403·core CASE 결과와 동등하게 합산하지 않는다. signed header/reject 계약과 전체 U0 BLOCKED 판정을 유지한다. 실제 AWS semantics 또는 M4/U1 승인으로 승격하지 않는다.
- 미해결: `over-5mb-signed-at-max` browser fetch TypeError/CORS의 원인 및 HTTP status 미노출 경계는 별도 원인 분석 전까지 미확정이다. 동일 조건 반복 실행이나 소스 추가 변경은 root/QA 결정 전 수행하지 않는다.

## 2026-09-23 15:34 KST — SeaweedFS 최신 release PAB 상태·최소 구현 영향범위 조사

- 한 일: 사용자/root가 요청한 bounded 조사로 공식 SeaweedFS 최신 정식 release와 4.47 및 current `master` source만 확인했다. [최신 release](https://github.com/seaweedfs/seaweedfs/releases/latest)는 4.47로 확인됐고, [4.47 PAB handler](https://github.com/seaweedfs/seaweedfs/blob/4.47/weed/s3api/s3api_bucket_policy_handlers.go#L1900-L1919)의 `GetPublicAccessBlockHandler`·`PutPublicAccessBlockHandler`·`DeletePublicAccessBlockHandler`는 각각 `s3err.ErrNotImplemented`만 반환한다. [master](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_bucket_policy_handlers.go#L424-L435)도 동일하다. 따라서 latest upgrade, SDK request shape, path-style, endpoint 설정으로 HTTP501을 해결할 수 없다.
- 현재 route/call-site: [PAB route](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_server.go#L3834-L3842)는 `?publicAccessBlock`와 `ACTION_ADMIN`을 이미 올바르게 연결한다. 같은 router에는 [PutObjectAcl/GetObjectAcl, PutObject, PutBucketAcl, PutBucketPolicy](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_server.go#L3675-L3749)가 별도 ingress로 존재한다. [PutBucketAcl 및 AuthWithPublicRead](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_bucket_handlers.go#L798-L971)는 ACL public-read 캐시를 저장하고 익명 GET/HEAD 경로에서 ACL과 bucket policy를 평가한다. [BucketConfig](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_bucket_config.go#L26-L57)에는 PAB 상태가 없고, [entry mapping/write patch](https://github.com/seaweedfs/seaweedfs/blob/master/weed/s3api/s3api_bucket_config.go#L362-L527)와 [bucket-config serialization 설계](https://github.com/seaweedfs/seaweedfs/blob/master/design-bucket-config-serialization.md#L196-L215)까지 함께 갱신해야 일관된 저장·cache·metadata event가 된다.
- 최소 영향범위(구현하지 않음): (1) 네 boolean의 bucket-level 영속화·Get/Put/Delete·cache/event, (2) `BlockPublicAcls`의 PutBucket/Create·PutObject 및 PutBucketAcl/PutObjectAcl public ACL 거부, (3) `BlockPublicPolicy`의 PutBucketPolicy 저장 전 public-policy 판정, (4) `IgnorePublicAcls`·`RestrictPublicBuckets`의 AuthWithPublicRead/policy evaluation 및 GetAcl effective response 반영이다. AWS 공식 의미상 기존 ACL/policy를 지우지 않고 effective access만 제한하므로 저장/조회 handler만 채우면 PAB 의미를 증명할 수 없다: [AWS Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html#access-control-block-public-access-options).
- 판정/미해결: SeaweedFS 4.47 및 최신 master 모두 PAB 자체가 미구현이다. 현재 U0의 Java `19/19 CASE·27/28 VERIFY·exit1`, browser `18/19 CASE·27/28 VERIFY`와 PAB capability FAIL은 유지한다. 이번 턴에는 source/vendor patch, harness/runtime, 설치, compile/test, Git 조작을 하지 않았다. 새 provider를 추가해 결과를 합산하거나 PAB failure를 PASS로 바꾸지 않는다.

## 2026-09-23 15:42 KST — SeaweedFS 4.47 raw source 좌표·최종 실측 정정

- 참조 정정: 직전 15:34 기록의 GitHub blob `#L1900-L1919` 및 `#L3834...` 표기는 GitHub HTML 페이지 좌표이며 source line이 아니다. 기존 WORKLOG 이력은 수정하지 않고, immutable [SeaweedFS 4.47 raw PAB handler L424-L435](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_policy_handlers.go#L424-L435)를 정본으로 삼는다. 이 raw 파일은 실제 436 lines이고 PAB Get/Put/Delete handler block은 L424-L435에서 `s3err.ErrNotImplemented`를 반환한다.
- route/call-site 좌표도 immutable [4.47 raw router PAB L882-L885](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_server.go#L882-L885)로 정정한다. 같은 raw router의 ACL/policy/object ingress는 L789-L836에 있고, [4.47 raw bucket handlers L834-L972](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_handlers.go#L834-L972)는 `AuthWithPublicRead` 및 `PutBucketAcl` 경로를 포함한다. [4.47 raw BucketConfig](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_config.go)에는 PAB 상태가 없으므로 PAB 구현은 handler 저장·조회만이 아니라 bucket-config 저장/cache/meta-event와 ACL·bucket-policy·anonymous-read enforcement call-site까지 포함해야 한다. 이 영향범위를 기록할 뿐 구현하지 않는다.
- 최신 root 실측을 동기화한다: browser `18/19 CASE·27/28 VERIFY`; same-file XHR도 error 0으로 HTTP 오류가 노출되지 않은 채 동일 실패가 계속됐다. 전체 U0는 미해결/BLOCKED이고, 여섯 합성 bucket object count `0/8/0/8/8/8`, `14567/14568` listener 0을 보존한다. PAB capability FAIL과 browser oversize 경계는 PASS로 완화하지 않는다.
- 작업 경계: 이 정정은 STATE/WORKLOG 문서만 append/update했다. 소스·harness·설정·compile·runtime·설치·Git 작업은 수행하지 않았다.

## 2026-10-03 13:37 KST — M4 U0 상태 stale 검토·정정

- 한 일: `.claude/CONSTITUTION.md`·`AGENTS.md`·backend 지침/STATE, REQUIREMENTS FR-UPLOAD-01~04·PRD §5.12/§9.6/§13.1, M4 회의 §14~§19와 `docs/worklog/M4-posts.md`를 대조했다. STATE의 기존 “LocalStack/AWS 미실측” 차단 문구가 2026-09-09 LocalStack fresh process·독립 CUA browser 실측과 충돌함을 확인해 STATE 해당 한 줄만 현재 증거에 맞게 정정하고 갱신 시각을 변경했다.
- 검토 결과: `gradle/u0`의 `U0Harness`는 고정 `127.0.0.1:14566` LocalStack/`--seaweedfs` 전용 `127.0.0.1:14568`, harness `127.0.0.1:14567`, `us-east-1`, synthetic `test/test`, 고유 private bucket을 유지한다. 19 CASE/28 VERIFY, signed `Content-Length`·`Content-Type`·checksum·`If-None-Match`, Host/Content-Length 브라우저 header 제외, reject 기대와 unsigned private GET 403 기준을 완화하지 않았다.
- 검증: `git diff --check -- .claude/team/backend/STATE.md .claude/team/backend/WORKLOG.md gradle/u0/settings.gradle gradle/u0/build.gradle gradle/u0/src/main/java/com/zeroverse/u0/U0Harness.java` exit 0. `& .\gradlew.bat --gradle-user-home .gradle-home2 -p gradle/u0 --no-daemon --max-workers=1 compileJava`는 `BUILD SUCCESSFUL in 26s`, `U0_REVIEW_COMPILE_EXIT=0`; 동일 `test`는 `BUILD SUCCESSFUL in 27s`, `NO-SOURCE`, `U0_REVIEW_TEST_EXIT=0`이다.
- 미해결: AWS 실서비스는 여전히 미실측이다. SeaweedFS 대체 실측은 browser over-size TypeError/CORS와 PAB capability gap으로 전체 U0 BLOCKED이며, 제품 `src/**`, 실제 AWS, U1/M4 상세 계약 및 Git 조작은 수행하지 않았다.
