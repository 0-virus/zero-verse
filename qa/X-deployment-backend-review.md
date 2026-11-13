# X-deployment A-v2 — 독립 Backend QA 검토

> **현재 상태: CONDITIONAL / 재리뷰 대기**  
> 2026-11-13 KST. 이 문서는 `deployment_backend` 구현에 대한 독립 정적 검토와 root가 제공한 격리·AWS/runtime 증거를 기록한다. 제품 코드와 타 역할 테스트는 수정하지 않았다. 아래 open finding을 반영한 새 source/JAR와 fresh 회귀 출력 없이는 backend 승인으로 승격하지 않는다.

## 1. 범위와 정본

- 검토 범위: `src/main`의 S3 presign/complete/read, upload intent와 cleanup, V7 migration/entity/repository, prod validator/initializer/readiness, cleanup CLI, `build.gradle` 및 관련 backend tests.
- 직접 대조한 정본: `docs/governance/meetings/X-20261113-deployment.md` §10·§12, `docs/governance/decisions/ADR-0009-deployment-s3-contract.md`, `docs/REQUIREMENTS.md` §6.12-A, `docs/PRD.md` §9.13, root/QA 지침.
- 승인 경계: 사용자 A-v2 T0~T5 승인. 실제 운영 DB·기존 데이터·T6는 범위 밖이며 PASS로 주장하지 않는다.
- 독립성: 구현 source/타 역할 테스트/공유 QA `STATE.md`·`WORKLOG.md`는 읽기 전용으로 대조했다. 이 산출물만 root의 명시 배정으로 작성한다.

## 2. Open findings — 수정 후 재검증 필수

### BE-X-001 — malformed UUID가 `UPLOAD_003/404`로 잘못 매핑됨 (Important)

근거:

- `src/main/java/com/zeroverse/domain/upload/controller/S3UploadController.java:98-106`의 `parseId`가 `UUID.fromString` 실패와 non-canonical UUID를 모두 `BusinessException(ErrorCode.UPLOAD_003)`으로 변환한다.
- A-v2 오류표 `docs/governance/meetings/X-20261113-deployment.md:191-194`는 잘못된 DTO/UUID/checksum 형식을 `VALIDATION_001/400`, 실제로 없는 intent/image를 `UPLOAD_003/404`로 분리한다.
- `src/test/java/com/zeroverse/domain/upload/S3DeploymentContractIntegrationTest.java`와 현재 upload controller test에는 S3 `/complete`·`/content`의 malformed/non-canonical UUID 회귀가 없다.

영향: 공격자/클라이언트 입력 오류가 존재하지 않는 이미지와 동일한 404로 처리되어 승인된 API 오류 계약을 위반한다. S3 complete와 canonical content GET 모두 영향받는다.

필요 조치: S3 controller의 parse 실패를 `VALIDATION_001/400`으로 분리하고, 정상 canonical UUID지만 없는 리소스는 service의 `UPLOAD_003/404`로 유지한다. 두 endpoint에 malformed, non-canonical, missing-resource 테스트를 추가한 뒤 MockMvc/실제 HTTP에서 envelope를 확인한다.

### BE-X-002 — prod S3 SDK timeout이 계약보다 크게 설정될 수 있음 (Important 후보)

근거:

- `src/main/resources/application-prod.yml:45-46`은 `S3_ATTEMPT_TIMEOUT`·`S3_API_CALL_TIMEOUT`으로 임의 duration을 허용한다.
- `src/main/java/com/zeroverse/domain/upload/service/S3ObjectStore.java:241-243`은 0/음수만 기본값으로 되돌리고 상한을 강제하지 않는다.
- `src/main/java/com/zeroverse/config/ProdConfigurationValidator.java:165-180`에는 timeout 검증이 없다.
- 승인 계약 `docs/governance/meetings/X-20261113-deployment.md:183`은 SDK attempt 5초, 각 API call 10초, transaction 30초를 고정한다. complete는 user/intent lock을 외부 S3 I/O 동안 보유한다.

영향: 운영자가 API call timeout을 30초 이상으로 설정하면 transaction timeout과 외부 I/O 경계가 어긋나 user/intent lock 점유가 길어지고 동시 complete/cleanup의 장애 표면이 커진다.

필요 조치: prod에서 정확한 5s/10s 또는 승인된 상한 이하만 허용하고, raw environment initializer와 bound properties 양쪽 중 최소 startup 이전 경계에서 fail-fast한다. timeout 초과·0·음수·malformed 값을 포함한 hostile validator tests와 packaged prod smoke를 추가한다. local/test의 유연성은 별도로 유지할 수 있다.

### BE-X-003 — cleanup CLI가 UTC JVM을 자체 보장하지 않음 (Important 후보)

근거:

- `src/main/java/com/zeroverse/tools/UploadIntentCleanup.java:16-25`에는 `ZeroverseServerApplication.main`과 같은 UTC guard가 없다.
- `build.gradle:114-124`의 `cleanupUploadIntents` JavaExec에도 `systemProperty 'user.timezone', 'UTC'`가 없다.
- cleanup 비교용 `Clock`은 `src/main/java/com/zeroverse/domain/post/config/PostConfig.java:10-13`에서 UTC이지만, JPA auditing provider `src/main/java/com/zeroverse/common/entity/SecondPrecisionAuditTimeProvider.java:14-24`는 `LocalDateTime.now()` 시스템 시간대를 사용한다. apply 실행이 `updated_at`을 갱신하므로 문서 명령의 `-Duser.timezone=UTC`를 누락하면 audit 시각이 계약과 어긋날 수 있다.

필요 조치: CLI 진입점에서 non-UTC를 fail-fast하거나 Gradle task에 UTC를 고정하고, non-UTC CLI dry-run/apply 회귀를 추가한다. `deploy/README.md:58-65`의 직접 Java 명령이 UTC를 지정하는 사실만으로 다른 CLI invocation을 안전하다고 승격하지 않는다.

### BE-X-004 — production web 기동 로그에 Spring generated security password가 출력됨 (운영 보안 잔여)

근거:

- `build/x-deployment-runtime-20261113/prod-valid.log:45-47`에 `Using generated security password`와 개발용 비밀번호 경고가 기록된다.
- `src/main/java/com/zeroverse/config/SecurityConfig.java`는 form/basic를 비활성화하지만 `UserDetailsServiceAutoConfiguration`의 generated user가 production context에 생성되는 것을 막지 않는다.

현재 JWT API에는 즉시 사용되는 Basic/form login 경로가 없어 직접 API 인증 우회로 확인된 것은 아니다. 그러나 production 로그에 불필요한 credential이 노출되고 Spring Boot가 운영을 개발 기본값으로 경고하는 잔여 위험이다.

필요 조치: prod에서 사용하지 않는 `UserDetailsServiceAutoConfiguration`을 명시적으로 제외하거나 동등한 no-user provider 경계를 두고, 새 JAR 로그에 generated password가 없음을 확인한다. 기존 local/test 인증 동작은 바꾸지 않는다.

## 3. 확인된 구현·실측 증거

- 실제 AWS/browser acceptance receipt `build/x-deployment-s3-acceptance-mwemwm8j32086f7b43/evidence.json`의 required checks는 PASS였다. private prefix, exact 5MiB, signed headers/checksum/If-None-Match, owner/friend/anonymous ACL, no app auth/cookie/referrer to S3, CORS·oversize, complete idempotence를 확인했다. root가 별도 fresh browser repeat에서 32 required checks·4 concurrent 5MiB reads·restart same SHA/no-store/nosniff·RSS 699MiB를 재확인했다.
- 격리 MySQL S3 계약의 latest XML `build/test-results/test/TEST-com.zeroverse.domain.upload.S3DeploymentContractIntegrationTest.xml`은 11 tests, skipped/failures/errors `0/0/0`이다. timeout lock release, rollback/retry, concurrent complete, ACL, cleanup 상태 전이를 포함한다.
- backend targeted log `build/x-deployment-backend-contract-20261113/timeout-lock-acquisition-rerun.log`는 `BUILD SUCCESSFUL`이며, root가 제공한 actual MySQL lock reacquisition 결과도 PASS다.
- packaged CLI의 기존 `HttpSecurity` failure는 `build/x-deployment-runtime-20261113/prod-cleanup.log`의 historical evidence로 남긴다. source의 `SecurityConfig.filterChain` servlet-only 조건 보완 후 root의 새 JAR에서 dry-run, hostile flyway/ddl args, no HTTP/no Flyway/no schema, real-S3 apply 4 `DELETED`/READY 보존/empty DB 결과는 PASS였다.
- prod initializer invalid origin/duplicate TLS/CORS path가 Hikari/DataSource 이전에 실패하고, valid readiness가 200 `UP`/no-store인 증거를 확인했다. 이는 위 open findings를 대체하지 않는다.

## 4. 미실행·잔여 경계

- QA는 shared build 경합 지침 때문에 Gradle을 직접 실행하지 않았다. root가 보고한 full suite의 test-pool-starvation 진단은 fresh source 수정 후 재실행·원인/종료 출력이 필요하다. targeted 11-test XML PASS만 전체 회귀 PASS로 승격하지 않는다.
- malformed UUID, timeout configuration boundary, non-UTC cleanup, generated-password absence는 현재 source 기준으로 fresh regression이 없다. 위 네 항목 수정 후 새 source/JAR identity를 실제 파일과 대조해야 한다.
- 실제 운영 Lightsail/Aiven/도메인/TLS/운영 IAM·DB 적용, 기존 사용자 DB/업로드 이관, T6는 실행하지 않았고 실행해서도 안 된다.
- AWS/browser PASS는 승인된 개발 synthetic prefix에 한정한다. 제품 self-test만으로 production approval을 주장하지 않는다.

## 5. 재리뷰 판정

현재 독립 backend 결과는 **CONDITIONAL — approval withheld**다. BE-X-001은 명시된 오류 계약 위반으로 반드시 수정해야 하며, BE-X-002~004도 root가 primary backend에 전달한 수정 대상이다. 수정된 source, 새 JAR, malformed/timeout/UTC/security-log 회귀 및 full test 결과를 root가 직접 확인한 뒤 이 문서에 최종 superseding status를 append한다. 기존 실패·NOT RUN·역사 증거는 삭제하거나 PASS로 재라벨링하지 않는다.

## 6. 최종 superseding 재리뷰 — 2026-11-13 16:40 KST

최종 source와 독립 증거를 다시 대조한 결과, backend의 A-v2 T0~T5 구현 판정은 **PASS / 승인**이다. 이 판정은 실제 운영 자원·기존 DB·기존 업로드 적용(T6)을 포함하지 않는다.

### 수정 확인

- BE-X-001 closed: `S3UploadController.parseId`가 malformed·non-canonical UUID를 `VALIDATION_001/400`으로 매핑한다(`src/main/java/com/zeroverse/domain/upload/controller/S3UploadController.java:98-106`). canonical하지만 없는 intent/image는 service의 `UPLOAD_003/404`로 유지된다. `S3UploadControllerTest`의 complete/content 회귀와 final browser/API evidence가 두 경계를 확인한다.
- BE-X-002 closed: prod raw initializer와 bound component가 S3 timeout을 검증한다(`ProdConfigurationValidator.java:174-187, 291-312`). 최종 smoke는 6개 hostile 값(5초/10초 상한 초과, call timeout 초과, 0, 음수, malformed, attempt>call)을 모두 Hikari 이전에 거부했다.
- BE-X-003 closed: application와 cleanup CLI가 UTC JVM을 fail-fast하고(`ZeroverseServerApplication.java:22-35`, `UploadIntentCleanup.java:16-20`), Gradle cleanup task도 UTC를 고정한다(`build.gradle:119`). non-UTC CLI는 DB/Hikari 이전에 exit 1이었다.
- BE-X-004 closed: prod profile에서 `UserDetailsServiceAutoConfiguration`을 제외한다(`src/main/resources/application-prod.yml:6-10`). final JAR의 valid prod receipt는 readiness 200 `{"status":"UP"}`, `Cache-Control: no-store`, `generatedPasswordLogged=false`다. servlet filter chain은 servlet web application에만 생성된다(`SecurityConfig.java:64-66`).

### 최종 검증 증거

- fresh full backend run `build/x-deployment-backend-20261113/full-final-review4-docker.log`: `BUILD SUCCESSFUL in 3m 11s`. root가 복사한 final XML 118 suites / 670 tests를 직접 집계해 failures=0, errors=0, skipped=0을 확인했다. 핵심 suite도 S3 contract 11, controller 2, prod validator 9, cleanup CLI 1, runtime timezone 3 모두 0/0/0이다.
- final immutable JAR SHA-256 `a7fb8fe0f8ab3561a997bc8a4678ad109466b0a78f52462b87700943d4bc1f84`는 `prod-smoke-app.jar`와 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`에서 동일하다. prod config 안의 generated-user auto-configuration 제외와 prod-only 설정도 JAR 내용으로 확인했다.
- `build/x-deployment-runtime-20261113/final-reviewed-browser/evidence.json`은 `status: PASS`; UUID 8개 경계(잘못된 6종=VALIDATION_001/400, canonical missing 2종=UPLOAD_003/404), headers/no-store·ACL·bounded 5MiB/browser PUT·private S3·secret-free evidence를 모두 확인했다.
- final JAR receipts `prod-*-receipt.json` 중 valid, six hostile timeout, invalid origin/TLS/profile/CA, non-UTC CLI, dry-run, hostile Flyway/DDL, local-row preflight는 모두 `pass: true`이며 SHA가 위 final JAR와 일치한다. local-row fixture는 preflight failure 뒤 제거되어 최종 local count=0이다. dry-run은 웹 서버·Flyway migration 없이 종료했고 empty DB hostile 실행은 schema를 만들지 않고 실패했다.
- 기존 synthetic AWS cleanup apply의 실제 receipt `build/x-deployment-runtime-20261113/cleanup-final-receipt.json`은 candidates 4를 `DELETED`로 처리하고 READY 3/image 3을 보존했다. 단, 그 apply receipt가 참조하는 `prod-cleanup-apply-receipt.json`의 JAR SHA는 이전 `6066357c...`다. 따라서 삭제 동작은 역사적 synthetic evidence로, UTC guard를 포함한 final JAR의 apply 실행으로 과장하지 않는다. 이는 현재 source 결함이 아니라 증거 identity 잔여 경계이며, final JAR에서는 dry-run/hostile/empty 경계와 full integration이 PASS다.

### 잔여 경계와 최종 결정

- T6(실제 운영 Lightsail/Aiven/domain/TLS/IAM, 운영 DB 적용, 기존 데이터·업로드 이관)는 실행하지 않았고 승인하지 않는다.
- 기존 `CONDITIONAL` 기록, 초기 `HttpSecurity` CLI 실패, shared pool-starvation 실패 및 NOT RUN 기록은 역사 증거로 보존했다. fresh final run의 0/0/0 결과가 최종 source에 대해 이를 supersede한다.
- 결론: **A-v2 backend T0~T5 독립 QA PASS**. root는 위 final JAR와 증거 identity를 함께 기록해야 하며, 실제 운영 배포 승인은 T6 별도 판정이다.

## 7. 최종 cleanup apply 증거 보강 — 2026-11-13 16:41 KST

root가 final JAR에서 cleanup apply를 재실행했고, 이전 §6의 JAR identity caveat를 supersede한다.

- `build/x-deployment-runtime-20261113/prod-cleanup-apply-receipt.json`: final JAR SHA `a7fb8fe0f8ab3561a997bc8a4678ad109466b0a78f52462b87700943d4bc1f84`, exit 0, candidates 4 / transitioned 4 / deleted 4 / retried 0 / skipped 0.
- `build/x-deployment-runtime-20261113/final-cleanup-review-receipt.json`: 같은 SHA, PASS; DELETED tombstones 8, 만료 전 PENDING 4 보존, READY intent 9 및 image row 9 보존, empty DB tables 0.
- 따라서 cleanup apply도 final-a7 artifact identity로 PASS이며, §6에 기록한 `6066357c...` receipt는 역사 증거로만 남긴다. 최종 backend T0~T5 PASS 판정에는 변경이 없다.
