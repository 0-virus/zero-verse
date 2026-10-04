# backend 현재 상태

## 2026-10-03 18:16 KST — root final runtime gate 반영

- root가 최종 D2B5 artifact로 runtime/OpenAPI를 독립 확인했다: `POST /api/v1/posts` 201, `POST /api/v1/uploads` 201, public `content` GET, 기존 image 200 재조회 및 동일 hash, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`가 모두 통과했다.
- whole independent review는 Ready to merge `Yes`, 잔여 `0`을 전달했다. 제품 backend gate와 runtime smoke는 해소됐지만 Git stage/commit/push/merge는 아직 대기 중이며 완료로 표시하지 않는다.

## 2026-10-03 18:13 KST — M4 Task1~3 최종 backend 증거

- `feature/M4-posts`의 Task1~3 구현·통합과 root/독립 QA source closure를 완료했다. upload 병렬 담당자의 변경도 통합된 현재 소스 기준으로 backend 기록을 갱신한다.
- `PostRepository` 목록·인접글 SQL predicate는 soft-delete 부모, publish/visibility, owner/accepted Universe, category/tag를 먼저 필터하고 `Pageable`로 bounded 조회한다. JPA int offset을 넘는 범위는 DB total 확인 후 정상 empty page를 반환한다.
- `PostService`는 CRUD/권한/태그/image snapshot/URL dedupe/24시간 view ledger/48시간 bounded cleanup/ACL 후 previous-next를 적용한다. `PostContentService`는 root `doc`, UTF-8 JSON·HTML 1MiB, depth32/node10000, TipTap node/attrs/mark allowlist, ordered-list/table span 보존, canonical local image URL, OWASP sanitizer를 적용한다.
- `UserSettingsService`는 managed canonical profile URL의 최초 binding·clear·재연결을 binding service로 검증하고 unchanged legacy external URL만 호환한다. 신규 external/blob/data/malformed 변경은 `UPLOAD_004`다.
- R3/R8 targeted 로그 `build/m4-post-r3-r8-targeted-20261003-1810.log` 및 혼합 ACL pagination `build/m4-post-pagination-acl-green-20261003-1820.log`가 `BUILD SUCCESSFUL`이다. upload/profile/V3 targeted는 `build/m4-upload-profile-v3-targeted-20261003-1840.log`로 통과했다.
- 최종 full `build/m4-final-backend-full-20261003-1900.log`: `BUILD SUCCESSFUL`, 13m49s, exit 0. 보존 XML `build/m4-final-junit-20261003-1915/test-results`: 66 suites, 426 tests, failures/errors/skips `0/0/0`.
- 최종 bootJar `build/m4-final-bootjar-20261003-1920.log`: `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`, 66,354,802 bytes, SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`.
- root의 최종 artifact OpenAPI 및 HTTP/image/browser smoke gate는 해소됐다. Windows 8080 예약 포트는 제품 변경 없이 root가 18080 runtime으로 우회했다. Git stage/commit/push/merge는 아직 대기 중이며 root가 수행한다.

마지막 갱신: 2026-10-03 18:16 KST

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
- 제품 backend gate는 해소됐다. root의 최종 artifact HTTP/OpenAPI/image/browser smoke와 독립 QA closure만 외부 gate로 남아 있다.
- root runtime의 8080은 Windows 예약 포트로 바인딩되지 않아 root가 18080에서 별도 검증 중이다. 제품 코드 변경이 아니다.

## 다음 작업

1. root의 최종 artifact HTTP/OpenAPI/image/browser smoke 결과를 root worklog와 closure 기록에 반영한다.
2. 독립 QA/root closure 후 root가 Git stage/commit/push/merge를 수행한다. 이 역할은 Git 조작을 하지 않는다.
3. 이후 결함은 새 RED→GREEN 증거를 남긴 뒤에만 수정한다.
