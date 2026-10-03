# backend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-03 13:37 KST

## 현재 단계

- 기준 브랜치: `feature/M4-posts`. M3 PR #9는 사용자 승인 후 `dev`에 실제 merge됐고 merge commit은 `59badfe42d539a33091a387b9ee6119d838190b8`(2026-09-09 09:08:47 KST)이다. 현재는 사용자 승인된 SeaweedFS U0-ALT 실측이 완료됐으나 browser negative 한 건과 PAB capability gap으로 전체 U0가 BLOCKED인 상태다. 상세 API·DB·보안 계약, 제품 연결·실제 AWS는 여전히 승인 범위 밖이며, Git 조작은 이 역할에서 수행하지 않는다.
- U0 범위: 독립 `gradle/u0/**`만 추가했고 매 실행 고유 `zeroverse-u0-...` private bucket·public access block·ownership·정확한 CORS, SDK Java2 presigned PUT/GET, Tika bounded verification, loopback `127.0.0.1:14567` `/`·`/cases`·`/verify`를 유지한다. 기본 LocalStack endpoint `127.0.0.1:14566` 외에 `--seaweedfs`일 때만 고정 SeaweedFS endpoint `127.0.0.1:14568`을 선택하며 임의 endpoint 입력은 허용하지 않는다. 기존 bucket 조회/삭제·실제 AWS·제품 `src/**`는 건드리지 않았다.
- M3 category 계약은 [ADR-0005](../../../docs/governance/decisions/ADR-0005-categories-contract.md) `ACCEPTED`, M3 회의록 `APPROVED`, 사용자 `시작` 승인에 근거한다. REQUIREMENTS FR-CAT01~05/NFR04~09와 PRD §5.4/§9.5/§10~12를 함께 따른다.
- M3 구현 커밋은 `067cd1174aeec1452b6c74402e750fdad75c3c05`이며 독립 QA 최종 판정과 root 최종 승인은 완료됐다. M3 기록 공개와 `dev` merge도 완료됐고, 현재는 M4 계약 준비 단계다.

## 진행 중

- backend 제품 `src/**`, `src/test/**`, root Gradle에는 이번 U0에서 변경이 없다. 기존 `UserSettingsService`는 `findByIdAndDeletedAtIsNull` 후 `profileImageUrl`을 그대로 저장하고, `UserProfileResponse`·`AuthService.getMe`·`BlogSettingsService` 계열 응답도 raw URL을 반환한다. JWT 필터는 claims만 검증하므로 M4 쓰기에서 `User`의 ACTIVE/soft-delete를 재조회해야 한다.
- U0 독립 산출물은 `gradle/u0/settings.gradle`, `gradle/u0/build.gradle`, `gradle/u0/src/main/java/com/zeroverse/u0/U0Harness.java`다. AWS SDK BOM/S3 `2.49.6`, Tika core `3.3.2`는 각각 AWS 공식 release/Gradle 안내와 Apache 공식 download 지원 3.x line을 확인해 고정했다. `/cases`는 첫 요청 시에만 서명 URL을 만들고 signed query의 Content-Length를 assert하며 브라우저 headers에는 Host/Content-Length를 넣지 않는다. `--seaweedfs`는 provider 라벨·고정 endpoint 선택만 추가했고 signed Content-Length/type/checksum/If-None-Match와 reject 기대는 완화하지 않았다.
- 이번 변경 compile은 기본 Gradle cache 생성 실패(exit 1), workspace cache의 `retries-spi-2.49.6.jar` `AccessDeniedException`(exit 1) 뒤 동일 U0 `compileJava` 권한 승인 실행이 `BUILD SUCCESSFUL in 11s`, `U0_COMPILE_ESCALATED_EXIT=0`으로 끝났다. root의 SeaweedFS 4.47 실측에서 PAB setup HTTP501로 constructor가 중단된 사실을 반영해 SeaweedFS에 한해 PAB/OwnershipControls setup HTTP501을 `CAPABILITY ... UNSUPPORTED`로 출력하고 core CASE를 계속하도록 보완했으며, 501 이외 오류와 LocalStack 동작은 그대로 throw한다. 보완 compile은 `BUILD SUCCESSFUL in 17s`, `U0_COMPILE_PAB_OWNERSHIP_EXIT=0`이다. 이어서 VERIFY read-side S3Exception도 SeaweedFS HTTP501 capability 및 기타 실제 HTTP status를 detail에 표시하도록 보완했다. root의 최신 Java/browser 실측은 완료됐고 이 역할은 추가 runtime을 실행하지 않는다. 이전 LocalStack self-check는 19 case 중 18 PASS, 28 verify 중 27 PASS이며 unsigned private GET 200만 실패했다.
- root 첫 self-check는 `buildFixtures`의 WebP Base64 padding 오류(`Input byte array has wrong 4-byte ending unit`)로 bucket 생성 전 exit 1이었다. `U0Harness`의 synthetic WebP 문자열을 올바른 `==` padding으로 교체하고 filename hint 없는 native Base64 decode 및 `Tika.detect(byte[]) == image/webp` guard를 추가한 뒤 동일 실측에서 재현되지 않았다.
- `src/main/java/com/zeroverse/domain/category/`에 entity·DTO·repository·service·controller와 실제 MySQL count/visibility/order/delete 규칙이 구현되어 있다.
- `src/main/resources/db/migration/V2__category_active_unique.sql`은 V1을 수정하지 않고 active-key unique를 추가하는 forward migration이다. 활성 name/order 제약, soft-delete 후 재사용, parent 경로 및 blog owner 삭제 검증을 포함한다.
- `BlogRepository`의 category 경로는 active blog와 active owner를 확인하고, 쓰기는 blog lock 후 재조회한다. `ErrorCode`의 CAT_004 고정 문구, `GlobalExceptionHandler`의 request binding 400 경계, `JacksonConfig`의 strict numeric/enum 입력 정책이 반영되어 있다.
- category HTTP/OpenAPI 테스트에는 공개 root `parentId: null`, trim-before-size, CAT_004/AUTH 오류, malformed JSON/numeric/enum 입력, 삭제 owner 경계 및 GET `security: []` 구조 검증이 있다. controller 6번째 테스트와 LWW assertion 변경도 최신 full suite에 포함됐다.
- 이미지 계약 초기 권고(조건부·미승인): 기존 `users.profile_image_url`, `posts.thumbnail_url`, `post_images.image_url`의 `VARCHAR(500)`을 canonical URL(쿼리·fragment 없는 configured bucket/region URL)로만 저장하고, 읽기 DTO의 signed GET 분리는 root의 §11.1 상세안과 정합화가 필요하다. 업로드 key는 서버 생성 `users/{ownerId}/{purpose}/{uuid}.{ext}`로 목적별 격리한다. 연결 시 blog-scoped post는 기존 M3 lock 뒤 owner `FOR UPDATE`, profile은 owner `FOR UPDATE`를 잡고 canonical URL·owner/purpose·S3 `HeadObject`·기존 세 컬럼의 cross-resource 참조를 한 트랜잭션에서 검사한다. 이 방식은 현재 참조가 동시에 겹치는 것만 막으며, thumbnail/profile 교체로 과거 URL이 컬럼에서 사라진 뒤에도 영구 재사용 금지를 보장하지 못한다. detached key 재사용을 허용한다는 명시 결정이 없으면 작은 `image_uploads` 상태/이력 테이블(또는 동등한 영구 tombstone)이 필요하다. postId 선행 draft는 만들지 않는다.
- 저장 lock 순서: 실제 `CategoryService`는 `BlogRepository.findByIdAndDeletedAtIsNullForUpdate`의 `SELECT b JOIN FETCH b.user ...`를 사용한다. M4 post/image 경로는 `blog → user → post` 순서를 따르고 `user → blog` 역순을 만들지 않아야 한다. SQL join의 물리 lock 순서는 DB 계획에 좌우될 수 있으므로 하나의 join fetch만 믿지 말고 blog lock 뒤 명시적인 user lock이 필요하면 그 순서로 획득한다.
- orphan 경쟁: cleaner와 binder 모두 owner lock 아래 DB 참조를 재조회한다. LIST→check→DELETE를 DB 트랜잭션이 S3 외부 I/O와 함께 수행하므로 lock 점유·장애 재시도 비용이 제약이며, 24시간 grace와 bounded batch를 둔다. binder가 먼저 lock을 잡으면 참조 후 cleaner가 건너뛰고, cleaner가 먼저 삭제하면 binder의 후속 HEAD가 실패해야 한다.
- 이미지 읽기 권한은 Post의 공개 predicate와 동일하게 평가하고, public 글은 익명에게도 API가 signed GET을 발급하되 private/universe·프로필은 resource 소유/접근 검증 후에만 발급한다. canonical URL을 임의 fetch하지 않고 configured S3 client로 key를 조회하며, 모든 body image는 활성 PostImage 바인딩을 요구한다.

## 검증 사실

- root가 남긴 기존 full 검증 산출물은 `build/m3-root-full-build.log`의 exit 0, `BUILD SUCCESSFUL in 10m 24s`, XML 56 suites/370 tests, failures 0/errors 0/skips 0이다. `CategoryServiceMySqlTest` 14, `CategoryControllerMySqlTest` 6, `CategoryMigrationTest` 1, `GlobalExceptionHandlerTest` 5를 포함한다.
- 기존 산출 JAR는 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`이며 2026-09-06 17:13:54 KST 생성됐다. SHA-256은 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`다. root가 이를 PID 8712로 `127.0.0.1:8080`에 기동했다.
- root의 기존 실행 기록에서 M3 합성 DB(13306) 재기동 후 Flyway V1/V2 success=1/1, 기동 시 `users/blogs/categories/posts = 5/5/18/0` 보존을 확인했다. 이후 새 UI 합성 계정이 추가됐으며 fixture는 삭제하지 않는다. Swagger 실제 JSON의 category GET `security=[]`, POST `bearerAuth`와 QA HTTP script exit 0도 기존 evidence이며 오늘 이 상태 턴에 Swagger/full test를 재실행하지 않았다.
- 2026-09-23 provider 선택 변경 후 U0 독립 compile 명령 `& .\gradlew.bat --gradle-user-home .gradle-home2 -p gradle/u0 --no-daemon --max-workers=1 compileJava`를 권한 승인으로 실행해 `BUILD SUCCESSFUL in 11s`, `1 actionable task: 1 executed`, `U0_COMPILE_ESCALATED_EXIT=0`을 확인했다. 기본 wrapper cache 생성 실패와 workspace cache `retries-spi-2.49.6.jar` AccessDenied 실패는 이전 시도 결과로 함께 보존한다. PAB/OwnershipControls provider-specific 501 처리 보완 후 같은 compile 명령은 `BUILD SUCCESSFUL in 17s`, `1 actionable task: 1 executed`, `U0_COMPILE_PAB_OWNERSHIP_EXIT=0`이었다.
- root의 SeaweedFS core self-check는 `build/u0-seaweedfs-core-self-check.log`에서 `19/19 CASE PASS`, `27/28 VERIFY PASS`, exit1로 확인됐다. `bucket-public-access-block`만 FAIL이고 `bucket-ownership-enforced`는 PASS였다. 최신 Java fresh run도 `19/19 CASE`, `27/28 VERIFY`, exit1이다. root 직접 관측한 `build/u0-seaweedfs-browser-evidence.md`의 browser run은 `18/19 CASE`, `27/28 VERIFY`; `over-5mb-signed-at-max`만 fetch TypeError/CORS로 HTTP status가 노출되지 않아 CASE FAIL이지만 object absence는 PASS다. PAB만 VERIFY FAIL, OwnershipControls PASS, unsigned private GET 403이다. TypeError/CORS 원인은 미확정이며 전체 U0 BLOCKED다.
- SeaweedFS native server는 loopback으로 기동했고 `volume.max4 → 16` 조정으로 새 bucket volume 소진 준비 실패를 해소했다. 데이터는 보존됐으며 현재 root 임시 harness/weed는 종료되어 `14567/14568` listener가 0이다. 합성 bucket 4개와 object count `0/8/0/8`은 보존된다.
- frontend 구현자와 분리된 root CUA 실행에서 `프론트엔드`를 `백엔드` 위로 native mouse DnD했다. UI 성공 notice `카테고리 순서를 저장했습니다.`와 DB active ID/order `19:0, 21:1, 20:2, 22:3`을 확인해 DnD gate는 root 독립 evidence로 PASS다. QA 직접 조작으로 표기하지 않는다.
- 2026-09-08 기존 Chrome fixture(ID 22)의 LOCKED 시도는 경고·확인창·OK focus를 관측했으나 `accept`가 timeout 후 kernel reset으로 끝났고 이어진 `getTab`도 timeout됐다. DB에서 ID 22가 계속 GENERAL인 과거 실패로 보존하며 새 IAB 결과와 섞지 않는다.
- 2026-09-07 과거 재시도에서는 확인창 도구가 `No dialog is showing`을 반환했고 새 탭의 타입이 GENERAL이었다. 이 과거 evidence도 LOCKED 저장 PASS로 승격하지 않는다.
- 2026-09-08 새 IAB fixture(blog ID 7)에서 category ID 26 `LOCKED`, `displayOrder=3` 저장·재조회와 새로고침 후 복원을 확인했다. `회고`의 순서 이동·이름 변경·삭제·타입 선택이 disabled이고 `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.` notice가 표시됐다. 이는 root 독립 실제 UI·DB evidence로 LOCKED gate PASS이며 QA 직접 조작으로 표기하지 않는다.
- 이전 25-test snapshot과 numeric enum 21-test 실패는 진행 중간의 역사 기록으로 `WORKLOG.md`에 보존한다. 최신 full 결과가 이를 대체한다.
- 이번 기술 입력에서 실제로 `REQUIREMENTS.md` FR-POST-01~08/FR-UPLOAD-01~04/NFR-01~09, `PRD.md` §4/§5.12/§13.1, M4 회의록 §6/§10, `UserSettingsService`·DTO·`SecurityConfig`·JWT filter·V1 schema를 재독했다. 제품 테스트·Gradle·DB·S3 실측은 실행하지 않았다.
- 이번 U0 실행 준비에서 health `http://127.0.0.1:14566/_localstack/health`는 `Reachable=True`, `s3=running`, `version=2026.8.1`이었다. `docker ps`는 Docker named pipe 권한 거부였다. `gradlew.bat -p gradle/u0 --no-daemon --max-workers=1 clean test`의 초기 cache 권한/cleanup 실패는 root escalated compile exit 0으로 분리됐다. root는 이후 self-check와 fresh-process CUA 브라우저를 실행했으며 상세 결과는 2026-09-09 12:36 기록과 같다.
- LocalStack 현재 compose에는 `S3_SKIP_SIGNATURE_VALIDATION=0`, `S3_VALIDATE_SIGNATURES=1`만 있고 `ENFORCE_IAM=1`은 없다. 따라서 unsigned private GET 200은 PASS로 처리하지 않고 U0 실패로 기록해야 한다. 컨테이너 재기동·compose 수정·권한 우회는 이 역할에서 하지 않는다.
- 2026-09-09 root 실측: `run --args=--self-check`는 compile 성공·run exit 1, 19 case 중 18 PASS/`unsigned-private-get`만 expected 403 대신 200, verify 28 중 27 PASS였다. fresh process와 실제 CUA 브라우저도 동일하게 18/19·27/28을 재현했다. jpeg/png/webp/gif·5MiB는 200, over/empty/expired PUT은 403, byte tamper는 400, type/checksum/missing은 403, replay는 412, signed GET positive/invalid/expired는 200/403/403, CORS positive/negative는 200/403, HEAD checksum·bounded GET·Tika·negative object 부재가 PASS였다. `ENFORCE_IAM` 부재로 unsigned private GET 200 보안 gate는 BLOCKED이며 제품 U1/전체 M4 승인으로 승격하지 않는다. root README와 M4 회의록 §15에 기록됐고 독립 QA가 진행 중이다.
- AWS 공식 문서상 presigned request는 bearer 성격의 유한 서명이고 최대 7일이며, conditional `If-None-Match: *`는 기존 key에 `412`, 동시 충돌에 `409`를 낸다. S3Presigner의 PUT/GET은 공식 API지만 presigned POST 발급은 이번 계약에서 전제하지 않는다. `Content-Length`/`Content-Type`와 `If-None-Match: *`를 signed header로 발급하고 실제 browser File PUT·HEAD size/type·MIME magic negative smoke를 구현 첫 gate로 삼아야 하며, HEAD metadata만으로 바이트 MIME를 검증했다고 보고하지 않는다. 근거: AWS [conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html), [S3Presigner](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html), [presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html).
- 추가 반례: private A의 thumbnail X를 제거하면 현재 `posts.thumbnail_url` 검색만으로는 X가 과거 A에 bound였다는 사실이 남지 않는다. 이후 public B가 X를 연결하면 권한이 B로 바뀐다. 따라서 “현재 resource 간 동시 공유만 금지·detached 후 소유자 재사용 허용”은 별도 제품 정책으로 명시해야 하고, “한 번 bound된 key는 다른 resource에 영구 재사용 금지”는 기존 세 URL 컬럼만으로는 불가능하다. 후자는 최소 `image_uploads` 상태/이력 1개(또는 key 이동으로 bound tombstone을 보존하는 동등 방식)가 필요하다.
- 추가 lock 사실: M3 category의 `JOIN FETCH b.user` 비관적 잠금과 새 경로의 `user → blog` 순서를 섞으면 T1(blog→user)·T2(user→blog) 사이의 논리적 cycle이 생길 수 있다. 기존 blog lock을 선행하고 모든 blog-scoped path를 `blog → user → post`로 통일하는 것이 최소 공통 규칙이다. 이번 추가 검토에서도 제품 테스트·Gradle·DB·S3 실측은 실행하지 않았다.

## 다음 작업

1. 최신 SeaweedFS U0 결과를 `build/u0-seaweedfs-core-self-check.log` 및 `build/u0-seaweedfs-browser-evidence.md` 기준으로 root/QA가 BLOCKED 판정에 반영한다. `over-5mb-signed-at-max`의 fetch TypeError/CORS 원인은 별도 원인 확인 전까지 추정하지 않는다.
2. PAB HTTP501은 provider-specific capability gap으로, OwnershipControls PASS·unsigned private GET 403·core CASE 결과와 분리해 보고한다. U0 전체 PASS로 승격하지 않는다.
3. U0 evidence 이후에도 detached key 정책·image_uploads/DB/API/보안 상세 계약은 별도 승인 전 구현하지 않는다. 구현 시 orphan/lock 경쟁은 후속 U1 gate로 남긴다.

## 차단 요인

- M3 공개 게시와 `dev` merge는 사용자 승인 및 실제 merge SHA로 해소됐다. M4 상세 API/DB/보안 계약은 새 HIGH 변경이므로 독립 심의와 사용자 승인 전에는 구현할 수 없다.
- 2026-09-09 LocalStack fresh process·독립 CUA 브라우저에서 presigned PUT의 signed `Content-Length`·`If-None-Match`, 크기·header/checksum 변조, overwrite 및 MIME 위장 탐지를 실측했다. AWS 실서비스는 아직 미실측이며, SeaweedFS 대체 실측도 browser over-size TypeError/CORS·PAB capability gap으로 전체 U0 BLOCKED를 유지한다.
- 이 역할의 초기 Gradle compiler cache JAR cleanup `AccessDeniedException`은 root의 escalated 동일 명령 compile exit 0으로 해소됐다. U0 보안 gate는 별도 unsigned private GET 200 결과 때문에 여전히 BLOCKED다.
- 현재 compose의 `ENFORCE_IAM` 부재로 LocalStack unsigned private GET은 403이 아닐 수 있다. 실제 응답이 200이면 설정값을 완화하거나 PASS 처리하지 않고, 지원 capability/재기동 여부를 root가 별도 승인 범위에서 판단한다.
- 60초 signed GET은 권한 변경 후 즉시 회수할 수 없다. 즉시 폐기가 필요하면 인증 프록시/추가 revoke 상태가 필요하며, 현재 최소 권고는 이 60초 창을 계약에 명시하는 것이다.
- 현재 참조 검색만으로 영구 no-rebind를 주장할 수 없다. thumbnail/profile 교체 이력 손실 반례를 제품 정책으로 결정하거나 persistent tombstone을 승인해야 한다.
- 기존 M3 `blog JOIN FETCH user FOR UPDATE`와 M4 `user → blog` 순서를 혼용하면 교착 가능성이 있다. 공통 lock 순서는 `blog → user → post`로 고정해야 한다.
- SeaweedFS U0는 최신 Java `19/19 CASE·27/28 VERIFY·exit1`, browser `18/19 CASE·27/28 VERIFY`로 최종 PASS가 아니다. browser의 over-5MB TypeError/CORS는 원인 미확정이고, PAB setup/read HTTP501 capability gap은 AWS 전용 보안 의미를 대체하지 않는다. root 임시 runtime은 종료됐으며 합성 데이터는 보존 중이다.
- 2026-09-23 15:34 KST 공식 source bounded 조사(이전 링크 좌표 정정): [SeaweedFS 최신 정식 release](https://github.com/seaweedfs/seaweedfs/releases/latest)는 4.47이며, immutable [4.47 raw PAB handler L424-L435](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_policy_handlers.go#L424-L435)에서 Get/Put/Delete가 모두 `ErrNotImplemented`를 무조건 반환한다. 이전 `github.com/.../blob/4.47#L1900-L1919`와 `master#L424-L435` 표기는 GitHub HTML 페이지 좌표였으므로 source line 좌표가 아니다. 최신 release 확인과 PAB 미구현 판정 자체는 유지한다.
- 같은 immutable 4.47 raw source에서 [PAB route L882-L885](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_server.go#L882-L885)는 `?publicAccessBlock`을 `ACTION_ADMIN`으로 등록한다. [ACL/policy/object ingress L789-L836](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_server.go#L789-L836)와 [AuthWithPublicRead·PutBucketAcl L834-L972](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_handlers.go#L834-L972)는 기존 ACL/public-policy enforcement 경로다. [4.47 raw BucketConfig](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_config.go)에는 PAB 상태가 없으므로, 올바른 구현은 handler XML 저장·조회만이 아니라 bucket-config 영속화/cache/meta-event와 ACL·bucket-policy·anonymous-read call-site를 함께 갱신해야 한다. AWS는 설정 활성화가 기존 ACL/policy를 삭제하지 않지만 effective permission 및 GetAcl 응답을 바꾼다고 명시한다([AWS PAB semantics](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html#access-control-block-public-access-options)). 이는 구현하지 않은 영향범위 기록이며 SeaweedFS source/vendor patch는 수행하지 않았다.
- SeaweedFS PAB의 현재 차단은 설정·harness 조정으로 해결할 수 없다. full U0를 통과시키려면 upstream에 PAB 상태 저장/조회와 ACL·bucket-policy·anonymous/cross-account enforcement를 함께 구현하고 별도 회귀검증해야 하며, 그 전까지는 PAB capability FAIL과 전체 U0 BLOCKED를 유지한다. 새 provider 설치·source patch·proxy·검증 기준 완화는 이 배정 범위가 아니다.
- 2026-09-23 15:42 KST root 최종 보존 상태: browser `18/19 CASE·27/28 VERIFY`; same-file XHR도 error 0으로 HTTP 오류가 노출되지 않은 채 실패가 계속됐다. 전체 U0는 미해결/BLOCKED이며, 여섯 합성 bucket object count `0/8/0/8/8/8`과 `14567/14568` listener 0을 보존한다.

## 역사 요약

- M0 스캐폴딩과 M1 인증은 `dev`에 머지됐다.
- M2 backend 검증·OpenAPI 보완은 XML 53개/348 tests, failures/errors/skips 0과 JAR/API 문서 smoke를 root·QA가 독립 대조해 완료했고, M2 PR #8은 merge됐다. 상세 명령·SHA·시각은 기존 `WORKLOG.md` 항목을 보존한다.
- M3 이전 상태의 Q1~Q4 결정 대기 기록과 중간 25/21-test 기록은 역사로만 남기며, 현재 구현 판단은 ACCEPTED ADR-0005와 최신 full 검증 증거를 따른다.

## 주요 소유 경로

- `gradle/u0/**` (M4 U0 독립 검증 harness)
- `src/main/java/com/zeroverse/**`
- `src/test/java/com/zeroverse/**`
- `src/main/resources/db/migration/**`
- `build.gradle`, `settings.gradle`, Gradle wrapper
