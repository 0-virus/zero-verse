# backend 현재 상태

## 현재 단계

- X-deployment T1/T2 backend 구현과 storage-mode/readiness/prod-preflight 경계를 완료하고 root의 immutable JAR·실제 AWS/browser gate 인계를 기다리는 상태다.
- A-v2는 사용자 승인되어 `APPROVED`; 실제 운영 DB·AWS 자원 적용·기존 데이터 이관은 이번 범위에 포함하지 않았다.

## 완료한 작업

- AWS SDK Java2/BOM `2.49.6` S3 provider를 추가하고, `V7__s3_upload_intents.sql`로 LOCAL/S3 discriminator·고정 bucket/region/key/checksum·intent 상태/unique/index/check 제약을 additive 도입했다. 기존 V1~V6와 local multipart/canonical URL은 수정하지 않았다.
- `POST /api/v1/uploads/presigned-url` → 5분 PUT intent → `POST /api/v1/uploads/{id}/complete`의 owner/user→intent 잠금, HEAD + 최대 5MiB+1 bounded GET/Tika/SHA-256 검증, READY 멱등 응답과 `GET .../content` 현재 ACL 검증을 구현했다. S3 403/권한상 부재는 UPLOAD_008, 명확한 404/NoSuchKey만 UPLOAD_005로 매핑한다.
- S3 signed headers는 SDK `signedHeaders()`에서 추출해 Content-Type/checksum/If-None-Match를 반환하고, browser-controlled host/content-length는 제외한다. S3 client/presigner는 5초 attempt/10초 call timeout, row에 저장된 region별 client, shutdown close를 사용한다.
- 24시간 만료 AND 5분 PUT 만료 AND image row 부재 조건의 dry-run 기본/명시 apply·최대100건 cleanup CLI와 DELETING 재시도/tombstone, prod fail-fast(UTC/HTTPS/single-origin/TLS CA/secrets/S3/LOCAL row/profile mix), loopback readiness, bootJar local-secret 제외를 추가했다.
- S3 direct controller는 이제 `zeroverse.upload.backend=s3` 조건으로 prod뿐 아니라 격리된 local/test S3 synthetic runtime에서도 활성화되고, 기존 multipart controller는 local/test 프로필에서 `backend=local`(기본값 포함)일 때만 활성화된다. prod readiness는 `ApplicationAvailability`가 `ACCEPTING_TRAFFIC`이고 `SELECT 1`도 성공한 경우에만 `UP`을 반환한다.
- `ProdConfigurationInitializer`를 Boot `spring.factories`에 등록해 prod raw environment를 context refresh 이전에 검증한다. HTTPS 단일 origin의 path/query/userinfo·local host, S3 endpoint/path-style/prefix, TLS truststore/secrets, UTC/loopback, JDBC URL의 중복·반대 `sslMode`/legacy TLS/timezone/force-session 옵션을 구조적으로 검사하며, 기존 component `@PostConstruct` 검증은 defense-in-depth로 남겼다.
- 독립 review의 잔여 계약을 반영했다. S3 attempt/api timeout은 각각 `>0`, `<=5s`/`<=10s`, attempt `<=` api를 prod pre-context와 bound validator에서 검사한다. malformed/non-canonical UUID는 complete/content 모두 `VALIDATION_001`(400)으로 매핑하고 회귀를 추가했다. cleanup CLI도 공통 UTC guard를 거치며 Gradle maintenance task가 UTC JVM을 명시한다. prod profile은 생성된 기본 security password 로그를 막도록 `UserDetailsServiceAutoConfiguration`을 제외한다.

## 검증과 다음 단계

- 집중 회귀(`S3UploadControllerTest`, `ProdConfigurationValidatorTest`, `UploadIntentCleanupTest`)는 `BUILD SUCCESSFUL`로 통과했다. Testcontainers/Colima 소켓을 명시한 단독 `./gradlew test --rerun-tasks`는 `build/x-deployment-backend-20261113/full-final-review4-docker.log` 기준 `BUILD SUCCESSFUL in 3m 11s`; JUnit XML 직접 집계는 `118 suites / 670 tests / failures 0 / errors 0 / skipped 0`이다. 첫 전체 실행의 Docker 소켓 누락 결과(`full-final-review4.log`, 428 infrastructure failures)는 제품 판정에 사용하지 않는다.
- `./gradlew bootJar verifyBootJarNoLocalSecrets --rerun-tasks`는 `BUILD SUCCESSFUL`이며 `build/x-deployment-backend-20261113/artifact-review4.log`에 보존했다. 최신 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar` SHA-256은 `a7fb8fe0f8ab3561a997bc8a4678ad109466b0a78f52462b87700943d4bc1f84`이고 `application-local.yml`은 archive에 없다. 별도 worker의 S3/MySQL 계약 통합 테스트와 root의 synthetic DB/AWS/browser·운영 readiness/CLI runtime gate는 각각 인계·독립 검토 범위로 남긴다.

## 차단 요인

- 운영 bucket/IAM/CORS/identity/CA/domain 및 신규 Aiven DB 적용은 별도 T6 승인 범위다. 이번 구현은 AWS 호출·운영 DB 변경·기존 local 파일/DB 이관을 수행하지 않았다.
- presigned PUT의 실제 브라우저·S3 conditional/checksum 상호운용, 실제 403/404 권한 표, complete lock timeout/cleanup 경쟁 및 CLI runtime의 최종 acceptance는 root/QA가 최신 JAR로 확인해야 한다. backend source/test/build 검증과 artifact 생성은 완료했지만 이를 실제 운영 자원 적용 완료로 확대하지 않는다.

## 갱신 시각

2026-11-13 16:35 KST · X-deployment review 잔여 4건 반영, 집중·전체 회귀 및 최신 artifact 검증 후
