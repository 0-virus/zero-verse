# backend 현재 상태

## 2026-11-09 23:31 KST — M7 backend 최종 검증·closeout 인계

- 현재 단계: M7 backend Task1(immutable text extractor, V4/V5 backfill·rollback·backup/restore, offline maintenance/readiness)과 Task2 통합 후 최종 backend 검증이 완료됐다. Task1은 이 역할이 작성했고, Task2 feed/search/QueryDSL/ACL/OpenAPI 소스는 `/root/m7_discovery_impl`, 추가 HTTP/RR 계약 테스트는 `/root/m7_discovery_contract_tests`가 작성했다. 이 역할이 Task2 저자였다고 기록하지 않는다.
- 구현 사실: Task1의 canonical V1 extractor, `Post.contentText`/content service 연계, V4 nullable cache, V5 keyset 100·단일 DML transaction·전체 rollback·원래 `updated_at` 보존·ID-only 오류, strict full-input JSON parse, offline verify/rebuild guard와 startup NULL readiness가 유지된다. 독립 review에서 발견한 malformed token 비노출, 후반 invalid 전체 rollback, 실제 격리 V3 backup/history 복원, FK session, `DEFAULT_GENERATED` audit timestamp 복사, 정확 202 insert−1 gap=201(100+100+1) 보강 이력은 WORKLOG에 모두 append되어 있다.
- 최종 backend 실측: `build/m7-evidence/backend-final-1.log`의 root 실행 `test bootJar`는 `BUILD SUCCESSFUL in 2m 16s`였다. `build/m7-evidence/backend-final-1/test-results/` XML 직접 집계는 `91 suites / 538 tests / failures 0 / errors 0 / skipped 0`; `build/m7-evidence/backend-final-1/summary.json`의 bootJar SHA-256은 `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`이다.
- 실제 인수 실측: `build/m7-acceptance-20261109-232636/evidence.json`은 `259 PASS / 0 FAIL / 0 NOT_RUN`, `filesChecked=354`이며 source SHA `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c`와 JAR SHA `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`의 manifest/actual 값이 각각 일치한다. 제품 구현 commit은 Task1 `5d7ede2`, Task2 `059ceed`, FE `0b3008a`로 저자를 분리해 기록한다.
- 진행 중: 독립 QA의 시각 최종 판정과 root의 M7 문서 종합·`dev` 통합은 아직 진행 중이다. backend 소스는 freeze 상태이며 이 closeout에서는 제품 소스·테스트·Gradle·Git을 변경하지 않았다.
- 다음 작업: root가 독립 QA 최종 판정 및 Git 통합 결과를 기록한 뒤, finding이 생길 때만 해당 소유자가 재현·수정·재검증한다. 정상적으로는 이 역할의 M7 backend 구현·검증 작업은 종료한다.
- 차단 요인: backend 자체 Critical/Important 독립 review finding은 없다. 전체 M7의 최종 완료·merge 판정은 QA 시각 검토와 root의 `dev` 통합에 의존하며, 이 역할이 임의로 완료·merge를 선언하지 않는다.
- 갱신 시각: 2026-11-09 23:31 KST

## 2026-11-09 — M6 backend 구현·dev 통합 완료

- 현재 단계: 사용자 `A-v2 전체 승인` 후 `feature/M6-comments-likes`에서 Task1 댓글 CRUD/paging/soft-delete와 Task2 likes/interactions를 구현했고, PR #12가 `dev`에 통합됐다. 구현 commit `55aedea966a45dc0e88a6b0d8926e709b366050`, merge commit `4399f73acf19d66d96fafd2c4784c6a03fcfa282`, 통합 tree `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`는 root가 실제 Git 대조를 완료했다. M6 이후 backend 후속 마일스톤은 별도 범위이며 아직 착수하지 않았다.
- 구현 사실: V1 `comments`/`post_likes`와 `(post_id,user_id)` unique를 유지한다. 댓글은 plain text trim/UTF-16 1~2000, root/reply PageResponse, 삭제 placeholder, 1단계 reply, COM 오류·ACTIVE/ACL/block 정책을 적용한다. root page의 최초 replies는 MySQL `ROW_NUMBER()` batch(최대 root100×reply20)와 author ID batch이며 큰 offset은 page-zero count 후 empty PageResponse다.
- 구현 사실: 좋아요는 멱등 POST/DELETE, hard delete, 대상 Like row current locking과 READ_COMMITTED post-scoped aggregate count, `InteractionState(postId,commentCount,likeCount,liked,canComment,canLike,canUnlike)`, PUBLIC/UNIVERSE ACL·양방향 BLOCKED·SUSPENDED/보존 like 규칙을 적용한다. interactions/comment GET은 optional auth, malformed Bearer 거부, `private,no-store`다.
- 잠금/snapshot: user join 없는 scalar blog lock → 관련 user ID 오름차순 lock → post → comment/like 순서를 공통 helper로 사용한다. M6 mutation과 `PostService.get`은 `READ_COMMITTED` 및 lock 후 current ACL/pair read를 사용해 RR stale snapshot을 줄였다. `PostService.get`의 기존 view/ACL 의미는 유지한다.
- Swagger/security: 숫자 postId comments/interactions GET query-string 공개 matcher를 추가했고, M6 7 operation의 개별 성공·400/401/403/404 응답 및 bearer/optional security를 OpenAPI 테스트로 고정했다.
- TDD/검증: RED에서 미구현 댓글 3건이 `3 tests completed, 3 failed`였고 이후 최소 구현 GREEN으로 전환했다. 최신 본체 targeted는 `CommentBatch 1 + CommentBehavior 9 + CommentController 3 + LikeController 10 + LikeConcurrency 1 + OpenApiConfig 5 = 29 tests`, 모두 failures/errors/skips `0/0/0`; 기존 `PostServiceIntegrationTest` 8건도 별도 재실행해 `0/0/0`이다. CommentBatch는 guest와 ACTIVE viewer 모두에서 1 root 대비 100 roots의 Hibernate prepare-statement 상한과 100×21→20 bounded replies를 실제 MySQL에서 고정하고, 댓글/좋아요 no-notification count도 검증한다. 최신 표준 full은 두 helper 포함 `77 XML files / 490 tests / failures-errors-skips 0/0/0`, `BUILD SUCCESSFUL`이다.
- 경쟁 검증: `M6InteractionConcurrencyMySqlTest` 3건과 `M6InteractionCrossRaceMySqlTest` 5건이 모두 통과했다. 첫 helper 실행의 SpringData spy latch timeout은 helper 소유자가 실제 service 호출·performance_schema transaction wait 관측으로 수정했고, 재실행에서 `8/8` PASS로 해소됐다. CrossRace는 block↔comment/like, root-delete↔reply, post-delete↔comment/like를 고정했다.
- 실행 환경: JDK `/private/tmp/zeroverse-jdk21/jdk-21.0.13+9/Contents/Home`, Gradle cache `/private/tmp/zeroverse-gradle-user-home`, MySQL 8.4 Testcontainers Colima socket `unix:///private/tmp/zeroverse-colima/default/docker.sock`, `TESTCONTAINERS_RYUK_DISABLED=true`, `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp/zeroverse-junit-tmp`.
- 남은 단계/위험: M6 backend source/full/bootJar, FE, 실제 HTTP, 독립 QA 및 dev 통합이 모두 PASS/완료됐다. 현재 backend 차단은 없으며 후속 마일스톤은 별도 승인·배정 범위다.
- 갱신 시각: 2026-11-09 KST

## 2026-10-04 — M4 로컬 인수·Git 통합 완료

- root가 최종 D2B5 artifact로 runtime/OpenAPI를 독립 확인했다: `POST /api/v1/posts` 201, `POST /api/v1/uploads` 201, public `content` GET, 기존 image 200 재조회 및 동일 hash, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`가 모두 통과했다.
- whole independent review는 Ready to merge `Yes`, 잔여 `0`, 최종 QA는 로컬 M4 PASS다. 구현 커밋 `4404f1e`를 푸시했고 PR #10이 `dev`에 머지됐다(`c34ff4e979bd6efffc3a2aea28c2be307ccac3b5`).
- 사용자 승인으로 리더가 상태 문구만 갱신했다. 제품 코드·요구사항·기존 역할 WORKLOG는 변경하지 않았다.

## 2026-10-03 18:13 KST — M4 Task1~3 최종 backend 증거

- `feature/M4-posts`의 Task1~3 구현·통합과 root/독립 QA source closure를 완료했다. upload 병렬 담당자의 변경도 통합된 현재 소스 기준으로 backend 기록을 갱신한다.
- `PostRepository` 목록·인접글 SQL predicate는 soft-delete 부모, publish/visibility, owner/accepted Universe, category/tag를 먼저 필터하고 `Pageable`로 bounded 조회한다. JPA int offset을 넘는 범위는 DB total 확인 후 정상 empty page를 반환한다.
- `PostService`는 CRUD/권한/태그/image snapshot/URL dedupe/24시간 view ledger/48시간 bounded cleanup/ACL 후 previous-next를 적용한다. `PostContentService`는 root `doc`, UTF-8 JSON·HTML 1MiB, depth32/node10000, TipTap node/attrs/mark allowlist, ordered-list/table span 보존, canonical local image URL, OWASP sanitizer를 적용한다.
- `UserSettingsService`는 managed canonical profile URL의 최초 binding·clear·재연결을 binding service로 검증하고 unchanged legacy external URL만 호환한다. 신규 external/blob/data/malformed 변경은 `UPLOAD_004`다.
- R3/R8 targeted 로그 `build/m4-post-r3-r8-targeted-20261003-1810.log` 및 혼합 ACL pagination `build/m4-post-pagination-acl-green-20261003-1820.log`가 `BUILD SUCCESSFUL`이다. upload/profile/V3 targeted는 `build/m4-upload-profile-v3-targeted-20261003-1840.log`로 통과했다.
- 최종 full `build/m4-final-backend-full-20261003-1900.log`: `BUILD SUCCESSFUL`, 13m49s, exit 0. 보존 XML `build/m4-final-junit-20261003-1915/test-results`: 66 suites, 426 tests, failures/errors/skips `0/0/0`.
- 최종 bootJar `build/m4-final-bootjar-20261003-1920.log`: `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`, 66,354,802 bytes, SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`.
- root의 최종 artifact OpenAPI 및 HTTP/image/browser smoke gate는 해소됐다. Windows 8080 예약 포트는 제품 변경 없이 root가 18080 runtime으로 우회했다. Git 통합 결과는 위 최종 상태를 따른다.

마지막 갱신: 2026-10-04 KST

## 현재 단계

- 브랜치 `feature/M4-posts`에서 M4 Task1(글 CRUD/권한/목록/태그/조회수), Task2(JSON 원본·HTML sanitizer), Task3 로컬 업로드 연동을 구현했다.
- 업로드 전용 구현 경로는 병렬 담당자의 변경을 통합했으며 현재 backend 통합·검증 기록과 root 인계를 이 역할이 관리한다.
- 공유 경계는 `PostService.bindPostImages`, `UploadService.bindProfileImage`, `UploadService.read`와 `zeroverse.upload.viewer-hmac-secret`, `zeroverse.upload.directory`이다.

## 구현 사실

- `PostService`는 blog→user→post 잠금, ACTIVE 쓰기 재검증, PUBLIC/UNIVERSE/PRIVATE/draft ACL, publish 필터, tag 정규화, image snapshot 검증/중복 URL dedupe, 24시간 ledger와 48시간 bounded cleanup, HMAC anonymous key, ACL 후 previous/next 선택을 적용한다.
- `PostContentService`는 JSON root `doc`, UTF-8 JSON/HTML 1MiB, depth32/node10000, TipTap toolbar attrs allowlist, canonical local image URL, deterministic HTML 및 OWASP sanitizer를 적용한다. client HTML은 저장 원본이 아니다.
- `UserSettingsService`는 managed canonical profile URL을 `UploadService.bindProfileImage`로 최초 binding/재연결 검증하고, 기존 외부 profile URL은 호환 유지하며 managed URL detach/clear를 처리한다.
- V3 migration은 post image active unique key, view ledger, local image metadata를 추가한다. V1/V2는 수정하지 않았다.
- 공통 multipart 설정은 `max-file-size: 5MB`, `max-request-size: 6MB`이며 초과는 `UPLOAD_002` envelope으로 변환한다. `UPLOAD_003/004` 계약도 반영했다.

## 검증 사실

- `& .\gradlew.bat --no-daemon --max-workers=1 compileJava --gradle-user-home .gradle-home2`: `BUILD SUCCESSFUL in 47s` (UserSettingsController/OpenAPI 설정 포함).
- 16:40 snapshot bootJar 기록은 과거 증거로 보존한다. 최종 artifact는 위 18:13 증거 항목의 `build/m4-final-bootjar-20261003-1920.log`와 SHA-256을 따른다.
- 최종 full과 targeted 테스트의 현재 수치·로그·JUnit 보존 경로는 위 18:13 증거 항목을 따른다.

## 현재 차단·위험

- 과거 `LocalImageStoreTest` 누락 import 오류는 upload 담당자의 통합 변경으로 해소됐으며 최종 full에서 compile/test가 통과했다.
- 제품 backend·runtime·독립 QA·Git 통합 gate는 모두 해소됐다. 로컬 M4 완료를 막는 미해결 항목은 없다.
- 8080 예약 포트 문제는 검증용 18080 사용으로 대응했다. S3/U0·운영 배포는 별도 후속 범위다.

## 다음 작업

1. M4 추가 구현 작업은 없다. 실제 머지·검증 근거는 `docs/worklog/M4-posts.md`의 최종 [리뷰]·[머지]를 따른다.
2. M5/S3 후속은 별도 사용자 지시 전 착수하지 않는다. 이후 결함 수정은 새 RED→GREEN 증거와 함께 진행한다.
