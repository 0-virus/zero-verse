# Planning Review: 첫 운영 배포를 위한 제품 코드와 실행 도구

- 회의 ID: `GOV-20261113-DEPLOY`
- 관련 범위: M10 이후 배포 후속 작업 `X-deployment`
- 상태: `APPROVED` (2026-11-13 사용자 A-v2 전체 승인; 구현·검증 진행)
- 생성일: 2026-11-13 (Asia/Seoul)
- 진행자·이 문서 단일 작성자: Codex root
- 소집 사유: 외부 서비스, 새 제품 의존성, 업로드 공개 API·DB·운영 보안 설정 변경
- 요청: 사용자 “배포 완료를 위해 필요한 코드 수정을 계획하여 진행해줄래?”
- 승인 경계: §10 A-v2와 T0~T5 사용자 승인 확인. 실제 운영 자원·DB 적용 T6는 별도다.

## 1. 결정할 질문

기존 콘텐츠와 이미지 권한을 유지하면서 아래 A안을 구현·검증하고, 실제 운영 적용은 환경 값과 검증 결과가 갖춰진 뒤 별도 진행할 것인가?

## 2. 배경과 범위

### 확인된 현재 상태

- 시작 Git: `main`, HEAD `8c29753`, 작업 트리 clean. `dev`도 같은 HEAD.
- M10은 로컬 MVP closeout이며 배포/S3/실사용 DB/운영 ADMIN bootstrap은 제외했다. 과거 M10 테스트 PASS 기록은 이번 배포 코드의 검증 결과가 아니다.
- 배포 HTML의 목표는 Lightsail 서울 2GB + Aiven MySQL + private S3 + 단일 HTTPS origin이다. 문서의 금액은 이번에 재검증하지 않았다.
- 사용자 제공 버킷 `zero-verse-s3`, 리전 `ap-northeast-2`. 앞선 세션의 개발 profile은 `zeroverse-s3-dev`, 범위는 `zeroverse/dev/`. 운영 identity/prefix 승인이 아니며 이번에는 AWS 변경을 수행하지 않았다.
- 현재 upload controller는 local/test 한정이고 UploadService가 LocalImageStore를 직접 사용한다. 저장된 `/api/v1/uploads/{uuid}/content` 참조는 글 정화·검색 추출·binding·FE가 함께 사용한다.
- V1/V2/V3/V4/V6 SQL 및 V5 Java migration을 보존한다. 새 스키마는 forward migration으로 추가한다.
- 운영 프로필·웹 서버·서비스·배포 도구는 아직 준비되지 않았다. FE의 API 기본 주소는 localhost다.

### 포함 범위

S3 전송/검증·현재 권한으로 이미지 읽기, 운영 설정, 동일 origin FE 빌드, 정적 파일/API 배포 패키지, 환경 검증, health/readiness, 롤백 안내, 백업·복구 절차와 격리 검증.

### 이번 코드 작업의 제외 범위

유료 리소스 생성·도메인 구매·계정 권한 확대·실사용 DB migration/승격·기존 파일 이관/삭제·자동 운영 배포는 실행하지 않는다. 필요한 설정 예시와 도구는 준비하되 실제 값과 대상이 확인되기 전에는 실행하지 않는다. HA·CDN·새 제품 기능·디자인 변경은 포함하지 않는다.

## 3. 근거

| 근거 | 적용 내용 |
|---|---|
| `zeroverse-deployment-plan.html` §2/6/7/8 | 데이터 외부 보존, private S3, 동일 origin HTTPS, TLS DB, S3→배포→운영 smoke |
| `docs/PRD.md` §9.7/§13.1 | 현행 local 계약과 후속 direct S3 upload 방향 구분, 상세 미승인 |
| `docs/REQUIREMENTS.md` §6.12 FR-UPLOAD-01~04 | 실제 바이트 1..5,242,880, JPEG/PNG/WebP/GIF, owner/purpose/binding/현재 열람권한 |
| `docs/REQUIREMENTS.md` NFR-01~09 | 인증/정화/공통 오류/Swagger/audit/Flyway/테스트 |
| `docs/worklog/M10-closeout.md` | 기존 로컬 완료 범위와 검증 기준선 |
| `docs/governance/RISK-REGISTER.md` RISK-0021 | 기존 DATETIME 기록 환경 불명확, 실데이터 자동 보정 금지 |
| `.claude/CONSTITUTION.md` 제3조, governance README §2~5 | 독립 3인 심의, HIGH 구체 사용자 승인 |

공식 기술 근거(2026-11-13 확인): [S3 conditional write](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html), [presigned URL/checksum](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html), [Java SDK presign](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html), [Connector/J TLS](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html). 공식 기능 지원과 실제 브라우저/선택 SDK의 상호운용 검증은 별도다.

## 4. 선택지

| 안 | 내용 | 이점 | 비용·위험 | 되돌리기 |
|---|---|---|---|---|
| A (권고·미확정) | 브라우저 signed PUT→서버 검증 완료, canonical 관리 URL·매 요청 권한 확인 후 서버 bytes 제공 유지 | 기존 글·FE 이미지 참조/접근권한 보존, direct upload 방향 충족 | 신규 intent/API/DB/SDK, 읽기 bytes는 서버를 경유 | additive migration 유지, S3 데이터 보존, S3 지원 release 내 rollback |
| B | 기존 multipart API가 bytes를 받은 뒤 S3에 저장 | 변경량 작음, 기존 MIME 검증 재사용 | 브라우저 direct upload 결정 변경 필요, 업로드도 서버 경유 | local/S3 명시 분리 |
| C | 저장 URL까지 S3로 바꾸고 signed GET resolver 도입 | 서버 image egress 감소 | 콘텐츠·검색·프로필·에디터 동시 변경, URL 만료 동안 권한 철회 지연 | 기존 자료 이관과 복원 필요 |

## 5. A안 구체 계약 초안 (미승인)

### 5.1 저장 참조와 이미지 접근

1. DB/TipTap/HTML/프로필/대표 이미지에는 기존 canonical 관리 URL만 저장한다. signed URL·AWS key를 제품 참조로 저장하지 않는다. 최초 binding 불변, 재바인딩 금지, 기존 owner/글/프로필 권한을 보존한다.
2. `GET /api/v1/uploads/{id}/content`는 매 요청 현재 권한을 검사하고 local 또는 S3 bytes를 제공한다. 기존 no-store/nosniff와 JSON envelope 예외를 유지한다. signed GET/redirect는 도입하지 않는다. PRD §13.1의 만료 가능한 GET 방향을 이 매 요청 권한 방식으로 명시 보완하는 승인 대상이다.
3. 기존 `storage_key=id` 불변식을 유지한다. 신규 storage discriminator와 S3 위치 metadata로 provider를 구분한다. 기존 row는 LOCAL이며 S3로 자동 재분류하지 않는다. 운영 prod는 LOCAL row/로컬 fallback을 허용하지 않고 사전 점검에서 막는다. 기존 자료 이관은 별도 명시 작업이다.
4. prod와 local profile 동시 활성화를 차단한다. 로컬 multipart는 계속 local/test 전용이다. S3 모드는 자격증명·리전·버킷·prefix가 명시돼야 하며 실패 시 로컬 디스크로 저장하지 않는다.

### 5.2 직접 업로드와 완료

1. 인증된 `POST /api/v1/uploads/presigned-url`: `fileName`, `contentType`, `size`, `purpose`, base64 SHA-256 checksum. 서버 발급 UUID와 owner/purpose/크기/MIME/checksum/만료를 durable upload intent에 기록한다. 사용자 입력 key/bucket/URL은 받지 않는다.
2. 응답은 공통 envelope 안 `id`, `uploadUrl`, `method=PUT`, 서명에 필요한 `headers`, `expiresAt`. 제품에서 사용할 `imageUrl`은 검증 완료 응답에서 제공한다. URL TTL 5분. raw file bytes로 PUT하고 앱 Authorization/cookie는 S3로 보내지 않는다.
3. PUT은 exact length·content type·checksum·`If-None-Match: *`를 서명에 묶어 기존 객체 덮어쓰기를 막는다. browser-controlled Content-Length를 JS로 설정하지 않고, 실제 서명/브라우저/S3 상호운용을 먼저 검증한다. 길이 제한·불변성 gate가 실패하면 계약을 완화하지 않고 차단 원인을 보고한다.
4. `POST /api/v1/uploads/{id}/complete`: 현재 인증 사용자와 intent owner를 확인하고 S3 HEAD 및 최대 5MiB+1 bounded GET으로 actual bytes/길이/checksum/Tika MIME을 검증한다. 성공 시에만 기존 image_uploads의 검증 완료 row를 만들고 `{id,imageUrl,contentType,size,purpose}`를 반환한다. 이 전에는 조회/게시글 binding이 불가하다.
5. 완료는 소유자에 한해 멱등이다. 응답 소실·PUT 재시도 412/네트워크 모호성 시 같은 intent의 complete로 복구한다. 검증 실패는 성공 metadata를 남기지 않는다. 현재 계정 정지/삭제·기존 auth 정책을 동일 적용한다.
6. intent 수명은 발급 후 24시간. 만료 후 complete/bind 불가. 완료/cleanup은 같은 intent lock과 상태 전이로 직렬화한다. storage timeout은 bounded이고 AWS 상세/키/서명/비밀은 API·로그에 노출하지 않는다. 정확한 오류표와 잠금 순서는 구현 전에 심의 보완본에 고정한다.
7. 실패·미완료 intent만 정리 대상으로 삼는다. 기본은 dry-run 관리 도구이며 explicit apply 시 24시간 경과·서명 만료·검증 완료 row 부재를 재확인하고 terminal 상태로 먼저 전환한 다음 객체 삭제를 재시도 가능하게 처리한다. 성공 업로드/BOUND/탈착/기존 local 파일은 자동 삭제하지 않는다. 버킷 전체 scan/delete와 자동 lifecycle은 도입하지 않는다.
8. 개발 AWS 검증은 `zero-verse-s3/zeroverse/dev/`의 이번 실행 UUID 하위 synthetic objects만 사용한다. 운영 prefix·identity는 별도 설정이며 기존 개발 자격증명을 무조건 운영에 복사하지 않는다.

### 5.3 운영 설정과 배포 도구

1. `prod` profile: DB 주소/계정·JWT/HMAC·HTTPS public origin·S3 위치 필수, cookie Secure/HttpOnly/Strict 고정, localhost 기본값 의존 금지. UTC JVM·DB 세션 검사 유지. Swagger 운영 비공개/비활성.
2. Aiven JDBC는 `sslMode=VERIFY_IDENTITY`, CA truststore를 외부 주입하고 인증서/호스트 검증을 끄지 않는다. Flyway 기존 migrations 수정 금지. prod 시작 전 backup/migration 계획 확인, Hibernate validate 유지.
3. FE production 기본 API는 same-origin 상대 URL이다. dev는 기존 localhost 동작을 보존한다. production의 localhost/HTTP API 설정은 빌드 또는 설정 검사에서 거부한다. 업로드 전송만 mode별로 분리하고 이미지 소비 UI/디자인은 유지한다.
4. Caddy 설정, Java21 systemd service, 비밀값 없는 env example, 로컬 release 패키지 생성 및 배포/rollback 가이드를 제공한다. 서버 포트 loopback, UTC, JVM memory cap, 서버에서 Gradle/npm build하지 않는다. artifact checksum과 frontend/backend 같은 release를 기록한다.
5. 최소 readiness는 익명 개인정보 없는 상태만 제공하고 DB 연결 실패를 success로 반환하지 않는다. 외부 reverse proxy 공개 범위와 운영 점검 방법을 고정한다. S3 쓰기 probe는 매 요청 실행하지 않는다.
6. DB dump는 암호화해 별도 backup 위치에 보관하는 실행 절차와 복원 검증을 준비한다. 애플리케이션 IAM에 backup/admin 권한을 합치지 않는다. 보관/스케줄 실제 활성화는 대상과 저장 비용을 확인 후 수행한다.
7. 운영 admin bootstrap은 필요할 때 명시된 사용자 1명과 offline 절차로 실행한다. 기본 admin/공개 bootstrap API/자동 승격을 만들지 않는다.

### 5.4 완료 기준과 실행 gate

- **코드 준비 PASS:** BE/FE focused+full 회귀, lint/build, 격리 MySQL migration, local 회귀, S3 adapter/intent/실패·경쟁 테스트, 배포 설정·패키지 검증, 저자와 다른 QA 리뷰.
- **AWS 통합 PASS:** 실제 named dev identity와 private bucket에서 signed header/5MiB/초과/MIME/checksum/replay/CORS/complete retry/owner ACL/cleanup 경쟁을 검증. mock/에뮬레이터만으로 주장하지 않는다. synthetic object 쓰기·삭제는 실행 범위 확인 후 수행.
- **운영 배포 PASS:** 실제 도메인/서버/DB CA/운영 identity가 갖춰진 환경에서 HTTPS signup→login→refresh→게시글/이미지→알림→signout, S3 anonymous deny/권한 철회, 프로세스 재시작 보존, backup restore, frontend deep link/API 404 분리·rollback을 확인한다.
- 신규 운영 DB와 기존 로컬 데이터 이관은 별개다. 기존 DB 기록 시간대가 확인되지 않으면 자동 보정/실사용 migration을 하지 않는다. 코드 완료를 실제 운영 배포 완료라고 보고하지 않는다.

## 6. 독립 검토

동일 초안을 3개 독립 컨텍스트에 전달했다. 검토자끼리 의견·중간 결과를 공유하지 않았다. 아래는 공통 형식의 손실 없는 핵심 요약이며 reviewer 최종 응답이 원문이다.

### Product — deployment_frontend

- APPROVE_WITH_CHANGES, 확신도 88, HIGH. 지정 FE 부분은 LOW.
- 사실: local MVP와 운영 배포는 별개다. canonical 참조·현재 ACL을 보존하는 A안은 FR-UPLOAD/배포 HTML과 정합적이다. 세부 TTL/API는 기존 승인사항이 아니다.
- 가정: 신규 운영 DB, canonical API read 기존 semantics, browser-only Vite.
- 이점: 기존 콘텐츠 보존, 현재 권한 반영, local/prod 분리.
- 위험·완화: 기존 LOCAL row는 prod에서 깨지므로 신규 DB 또는 별도 이관 gate; browser CORS/checksum은 실AWS 검증; 서버 proxy 동시 이미지 메모리/egress 점검; proxy가 /api를 HTML로 응답하지 않는지 smoke.
- 가장 강한 반론: A안은 첫 베타에 비해 복잡하고 B가 작다. 다만 B는 기존 direct-upload 방향 변경이 필요하다.
- 실패 시나리오: localhost 요청, proxy HTML fallback, PUT CORS/서명 실패, complete 응답 소실로 orphan, LOCAL row 404.
- 필수 보완: 신규/기존 DB 선택, canonical read 명시, 상태·오류·멱등 계약 고정. 공개 프로필 raw img는 401이라고 단정할 수 없어 별도 수정 제외.

### Architecture — deployment_backend

- APPROVE_WITH_CHANGES, 확신도 89, HIGH. 지정 FE 부분은 LOW.
- 사실: local/test controller·LocalImageStore 결합, canonical validator, V3 metadata가 있어 additive migration 필요.
- 가정: 서버 UUID key, 개발/운영 identity 분리, local 자동이관 없음. 객체 임의 overwrite/delete를 운영 IAM으로 통제해야 한다.
- 이점: canonical/ACL 재사용, provider 명시, conditional PUT+actual checksum 검증.
- 위험·완화: HEAD는 힌트이고 bounded GET이 최종 진실; exact-length 브라우저 검증; provider location을 row에 고정; complete/cleanup lock·멱등 상태 고정; Delete 실패 재시도 상태 보존; timeout/비밀 노출 통제.
- 가장 강한 반론: upload만 직접 S3, read는 proxy라 비용 이점은 제한적이다. B는 API/intent/CORS가 없어 작은 실패 표면이다.
- 실패 시나리오: signed header mismatch, MIME/초과 거부 후 orphan, double complete, cleanup이 READY를 삭제, LOCAL row prod 접속, 빈 API base 이미지 해석 실패.
- 필수 보완: 컬럼/인덱스·오류표·트랜잭션/잠금 순서·cleanup retry 고정, 실제 AWS gate, 운영 DB 및 위치 선택. FE LOW는 API shape/보안/디자인 변화 없이 가능.

### Delivery & Risk — deployment_qa

- APPROVE_WITH_CHANGES, 확신도 91, HIGH. 지정 FE 부분은 LOW.
- 사실: M10은 배포 미검증, 운영 설정/산출물 부재, S3 세부 미확정, FE localhost/빈 base 문제.
- 가정: 운영 DB·도메인·CA·서버 미제공, 개발 identity는 운영 재사용 아님.
- 이점: 참조 보존·additive migration·단계별 코드/AWS/실운영 gate.
- 위험·완화: 실제 browser/aws 시험 필수, S3 지원 release만 rollback, fresh/upgrade/restore 격리 검증, 앱/backup IAM 분리, readiness 실패503, artifact/log 비밀 검사.
- 가장 강한 반론: pre-S3 JAR로 복귀하면 S3 row를 읽지 못한다. 과거 U0 BLOCKED를 코드 mock PASS로 해결했다고 주장할 수 없다.
- 실패 시나리오: /api HTML 응답, 잘못된 bytes를 complete가 수용, pre-S3 rollback으로 image404, CA/JWT 누락인데 readiness200.
- 필수 보완: A 상세 승인, 운영 데이터 선택, prefix/CA/domain/서버 값, S3 지원 release rollback 고정. LOW FE는 실제 배포 완료와 구분하여 진행 가능.

## 7. 진행자 종합 및 결정

- 잠정 등급: HIGH. 공개 API·DB·새 의존성·운영 설정·이미지 제공 계약에 영향.
- 상태: APPROVED. 사용자가 §10 A-v2 전체를 승인했다. §12 사용자 결정 이후 T0~T5 구현·검증을 진행한다.
- 선택 SDK: 기존 U0의 Java2 AWS SDK 2.49.6은 후보이며 제품 편입은 이번 승인·의존성 확인 대상이다. Tika는 기존 승인 버전을 재사용한다.
- 작업 브랜치 생성/commit/PR/merge는 아직 수행하지 않았다.

## 8. 실행계획 및 소유권

| 순서 | 담당/소유 경로 | 산출물·완료 조건 | 선행조건 |
|---|---|---|---|
| T0 | root: 이 회의록·governance·JOURNAL / PM: PRD·역할 기록 | 독립 심의 보완본·구체 승인, 승인 뒤 REQUIREMENTS/PRD/ADR 동기화 | 지금 수행 |
| T1 | backend: src/main/**, src/test/**, build.gradle | prod fail-fast·storage 분리·forward migration·기존 local 회귀 | 승인 |
| T2 | backend: 동일 경로 | S3 intent→PUT→complete→read·실패 정리·Swagger·테스트 | T1/API 계약 확정 |
| T3 | frontend: frontend/** | same-origin 설정·직접 업로드 완료 흐름·계정 전환/abort/retry 회귀 | 승인/API 고정; T2와 병렬 가능 |
| T4 | root: 신규 deploy/**·README / backend: prod 설정 | Caddy/systemd/env 예제·release 패키지/rollback·backup/restore 안내 | 승인; T1/T3와 독립 부분 병렬 |
| T5 | QA: qa/**·역할 기록 / root: 실측 검토 | 독립 전체 계약 검토·자동 회귀·격리 인수·AWS 기술 gate | 구현 완료·AWS 검증 범위 확정 |
| T6 | root·사용자 | 실제 운영값 반영·자원/DB 적용·운영 인수 | T5 PASS·실제 대상/실행 승인 |

신규 `deploy/**`는 root 소유로 배정한다. 이번 작업은 기존 M10 완료 결과를 덮어쓰지 않고 이 회의록에 실행 로그를 이어 기록한다. 구현 역할은 자기 STATE/WORKLOG를 갱신하고 root는 JOURNAL을 갱신한다. 기존 `.local-data/`와 사용자 DB는 보존한다.

## 9. 실행 기록

- 2026-11-13: root가 Git·기존 문서·코드와 배포 HTML 대조. backend/frontend/QA 읽기 전용 조사를 배정했다. 제품 수정·AWS 변경·DB 변경·Git 쓰기는 아직 수행하지 않았다.

## 10. A-v2 — 사용자 승인 대상 보완본

§5는 아래 명시 사항으로 보완한다. 이번 승인 대상은 T0~T5 코드·격리 검증·개발용 synthetic AWS 검증이며 T6의 실제 자원/DB/운영 배포 실행은 별도다.

### 운영 데이터·저장 위치

- 첫 운영 배포는 **신규 Aiven DB**를 대상으로 준비한다. 기존 사용자 DB와 `.local-data/`는 그대로 보존하고 자동 이관하지 않는다. 사용자가 기존 데이터를 원하면 이관 안건을 먼저 확정한다. 이 선택은 아직 권고이며 A-v2 승인 시 확정된다.
- `image_uploads`에 `storage_backend`(LOCAL/S3, 기존 row LOCAL), `object_bucket`, `object_region`, `object_key`, `content_sha256`을 additive V7로 추가한다. LOCAL은 기존 동작, S3는 위치/검증 checksum 필수. key는 발급 시 설정 prefix+서버 UUID이고 row/intent에 전체 위치를 고정하여 설정 변경이 과거 객체 위치를 바꾸지 않는다. image와 intent 각각 `(object_bucket, object_key)` unique를 보장한다. DB check로 LOCAL은 S3 위치/checksum NULL, S3는 모두 NOT NULL을 강제한다.
- `upload_intents`: UUID PK, owner FK, purpose, expected_size/type/sha256, bucket/region/object_key, status, upload_expires_at(5분), expires_at(24시간), cleanup_attempts/last_cleanup_at, created_at/updated_at. 상태/만료 index와 owner index를 둔다. 검증 완료 image row id는 intent id와 같고 PENDING은 image_uploads에 없다. 값 범위/NULL 제약을 entity와 migration에서 검증한다. 기존 Flyway 파일은 수정하지 않는다.
- checksum은 API/DB 모두 표준 padded base64 SHA-256, ASCII 대소문자 구분 `CHAR(44)`로 고정한다. 정확히 32 bytes로 decode 후 같은 canonical base64로 재인코딩되는 값만 허용하고 공백 제거/대소문자 변환으로 유효화하지 않는다. 상태 DB check는 PENDING/READY/DELETING/DELETED만 허용하고 READY↔동일 id의 image row 존재 불변식은 service transaction과 실제 DB 테스트로 보장한다. 시간은 UTC·초 단위, injectable Clock으로 경계를 검증한다.
- AWS SDK Java2 S3/BOM `2.49.6` 제품 편입을 승인 대상으로 삼고, 기존 Tika `3.3.2` 재사용. 새 불필요한 FE SDK는 추가하지 않는다.

### 상태·경쟁·실패 계약

| 현재 상태 | 동작/조건 | 결과 |
|---|---|---|
| PENDING | 소유자 complete, 만료 전, bounded bytes 검증 성공 | 같은 transaction에서 image row 생성 + READY; canonical 응답 |
| READY | 활성 소유자 complete 재호출 | 업로드 intent 만료와 무관하게 기존 동일 metadata 응답; 객체 재작성/재바인딩 없음 |
| PENDING | object 부재가 S3 404/NoSuchKey로 확인됨 | 409; 상태 유지, complete 재시도 가능 |
| PENDING | MIME/size/checksum 불일치 | 400; metadata 생성 없음, 24시간 후 cleanup 대상 |
| PENDING | 24시간 이후 | complete 410; dry-run/apply cleanup 후보 |
| PENDING | cleanup apply, now >= expires_at(24h) AND upload_expires_at 만료 AND 완료 metadata 부재 | intent lock 아래 DELETING으로 commit |
| DELETING | DeleteObject 성공 또는 객체 부재 | DELETED; tombstone 보존 |
| DELETING | storage 실패 | DELETING 유지 + 실패 횟수/시간 기록, 다음 실행 재시도 |
| DELETING/DELETED | complete | 410; 새 metadata 생성 금지 |

- complete 잠금 순서: 현재 user row → intent row → 새 image row. owner ACTIVE·비삭제를 lock 후 재검증한다. 같은 intent에 대한 S3 HEAD/GET 및 검증·image 생성·READY 전이를 한 transaction/intent lock 안에서 수행한다. beta의 단순한 일관성을 선택하며 네트워크 때문에 user lock이 길어지는 비용을 수용한다. SDK attempt timeout 5초/각 API call timeout 10초, transaction timeout 30초를 설정한다. transaction annotation이 외부 I/O/DB lock wait를 강제 중단한다고 가정하지 않으며 실제 MySQL 경쟁·timeout 후 lock 해제를 검증한다. 검증 bytes를 최종 진실로 삼는다.
- cleanup은 intent row만 잠그고 사용자 row를 뒤늦게 얻지 않는다. READY는 절대 삭제하지 않으며 image row 존재 시 정리를 중단한다. DELETING commit 이후 외부 delete 후 재잠금하여 결과를 기록한다. DB commit 실패 시 성공 metadata를 만들지 않고 같은 intent 재시도로 복구한다.
- cleanup batch 기본/최대 100건, DB intent 목록만 조회(ListBucket 불필요), dry-run 기본, 명시 apply만 쓰기. tombstone 자동 삭제와 정상 업로드·탈착 이미지 회수는 이번 범위 밖이다. 유효 PUT URL이 남은 객체를 먼저 삭제하지 않으며 5분 만료만으로 정리하지 않는다.
- read는 현재 권한 검사 후 bounded GET bytes/길이/MIME/저장 checksum을 검증한다. 기존 권한 판정 시점의 의미를 유지하며 이미 전달된 bytes를 회수한다고 주장하지 않는다. S3 임의 객체 교체는 checksum 불일치로 숨기고 내부 진단은 민감값 없이 기록한다.

| 상황 | 외부 응답 |
|---|---|
| 인증 실패/정지·삭제 | 기존 AUTH/USER 계약 유지 |
| 잘못된 DTO/UUID/checksum 형식 | 기존 VALIDATION_001/400 |
| MIME 불일치/지원안함 | UPLOAD_001/400 |
| 실제/신고 길이 범위 위반·길이 불일치 | UPLOAD_002/400 |
| 타 owner/없는 intent, 읽기권한 없음/없는 이미지 | UPLOAD_003/404 |
| purpose/기존 binding 위반 | UPLOAD_004/400 |
| complete의 객체 미존재 | 신규 UPLOAD_005/409, “업로드된 파일을 아직 확인할 수 없습니다.” |
| intent 만료/DELETING/DELETED | 신규 UPLOAD_006/410, “업로드 요청이 만료되었습니다.” |
| 실제 checksum 불일치 | 신규 UPLOAD_007/400, “업로드 파일 검증에 실패했습니다.” |
| S3 timeout/일시 실패/권한상 객체 존재 여부 불명 | 신규 UPLOAD_008/503, “이미지 저장소에 일시적으로 연결할 수 없습니다.” |
| 예기치 않은 DB 무결성·설정 불일치 | 기존 COMMON_500, 내부 정보 숨김 |

DB lock 이후 READY 중복 완료는 정상 멱등 성공이며 DB unique 예외를 일반 재시도 성공으로 가장하지 않는다. S3 API 응답·URL·bucket/key·토큰은 그대로 사용자 오류에 넣지 않는다.

[AWS HeadObject 문서](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html)에 따라 ListBucket 없는 identity의 missing object 응답은 403일 수 있다. 403을 404 부재로 단정하지 않는다. 명확한 404/NoSuchKey만 UPLOAD_005, 존재 여부 불명/권한 오류는 UPLOAD_008로 다루며 상태를 유지한다. 운영 검증에서 정상 객체 권한도 따로 확인한다. 객체 부재 여부를 알기 위해 버킷 전체 List 권한을 추가하지 않는다.

### 운영·검증 보완

- `GET /api/v1/health/readiness`는 prod에서만 최소 `{status:UP/DOWN}`로 200/503, no-store. DB SELECT 1 성공과 prod 설정/preflight가 기준이며 S3 연속 쓰기를 하지 않는다. 실제 S3 readiness는 별도 배포 smoke로 증명한다. Caddy는 외부 health 경로를 차단하고 loopback 운영 점검으로만 사용한다. 필요 이상의 Actuator 의존성을 추가하지 않는다.
- prod 기동 전 LOCAL row count 검사·필수값/HTTPS origin·TLS/UTC·location 설정 검증. 비밀은 server 외부 env file, artifact와 Git에는 예시만 포함한다. 기존 프로필 동작을 바꾸는 설정은 prod에 국한한다.
- rollback은 **S3를 지원하고 현재 migration과 호환되는 release 사이**에서만 허용한다. 첫 배포 실패 시 서비스 중단 후 수정/복원하며 M10 JAR로 데이터 호환 rollback을 주장하지 않는다. DB 역 migration/자동 데이터 삭제 없음.
- 개발 AWS 검증은 `AWS_PROFILE=zeroverse-s3-dev`, `ap-northeast-2`, `zero-verse-s3/zeroverse/dev/deployment-<run-uuid>/`의 이번 synthetic 객체 발급/업로드/조회/정리만 승인 대상이다. 다른 prefix·기존 객체·bucket/IAM 설정 변경 없음. CORS 수정이 필요하면 정확한 JSON과 영향 범위를 별도로 제시한다. 자격증명은 출력하지 않는다.
- 성공/최대 크기/초과/헤더·checksum mismatch/동시complete/rollback/cleanup경합/owner ACL 및 실제 browser PUT은 gate다. 기존 U0 BLOCKED를 소급 PASS로 바꾸지 않고 새 증거로 이번 제품 gate를 판정한다. 시간·비용 절약을 위해 모의 성공만으로 생략하지 않는다.
- 이미지 proxy는 최대 5MiB bounded buffer를 유지하여 검증 전 bytes를 노출하지 않는다. 스트리밍으로 검증을 생략하지 않는다. 합성 동시 이미지 4개 read를 2GB/JVM 한도 내에서 점검하고 대규모 부하 SLA로 확대하지 않는다.

## 11. 독립 LOW — FE same-origin 기본값

- 상태: AUTO_APPROVED, 2026-11-13 root. 세 검토자 모두 공개 API/DB/인증 정책/디자인/외부 의존성 변화가 없는 독립 LOW로 판정했다.
- 범위: production 기본 API base `''`, dev/test 기존 localhost 유지, 명시 환경값 호환, managed image 빈 base→현재 origin 해석, 기존 cross-origin/query/hash/userinfo 거부 유지, 운영 HTTP/localhost API 설정 검사 및 회귀.
- 제외: presigned API, S3 SDK, DB, 새 쿠키/권한 정책, 일반 img 4경로 수정, 디자인.
- 배정: deployment_frontend가 frontend/**와 자기 STATE/WORKLOG 작성. QA는 별도 컨텍스트에서 diff와 테스트 근거를 검토한다. root는 실제 파일/출력을 직접 확인한다.

## 12. 2026-11-13 사용자 승인 및 구현 배정

- 사용자 async 답변 원문: **“A-v2 전체 승인 (권장)”**. 질문은 §10의 direct private S3/서버 검증/canonical 권한 유지/운영 설정·배포 도구/신규 DB 및 개발용 테스트 객체 생성·조회·정리를 명시했고 실제 서버·운영 DB 적용은 별도로 구분했다.
- 승인: §5를 §10으로 보완한 A-v2 전체와 T0~T5. 신규 Aiven DB를 기준으로 준비하고 기존 로컬 데이터를 보존한다. 실제 운영 적용 T6는 자동 실행하지 않는다.
- root: REQUIREMENTS/ADR/governance/README/deploy 산출물, 실제 출력 확인 및 통합.
- deployment_backend: T1/T2·prod 설정/health·V7/S3/cleanup·BE 테스트. deployment_frontend: LOW QA 뒤 T3. deployment_qa: LOW 독립 리뷰 뒤 T5. deployment_pm: PRD·역할 기록 정합성.
- LOW 자체 검증: 53 files/485 tests, lint/build PASS 로그를 root가 직접 읽었다. 단, 마지막 소스와 로그 시점의 동일성 및 독립 QA는 별도 확인한다. S3/운영 완료로 승격하지 않는다.

## 13. 2026-11-13 구현·격리 검증 중간 증거

- 사용자 CORS 추가 완료 후 QA가 `localhost:5173` PUT preflight 200 및 지정 origin/headers를 확인했다. bucket/IAM 변경은 사용자가 콘솔에서 수행했다. 실제 객체 PUT/브라우저 인수는 별도 gate다.
- FE T3는 정상 refresh와 계정 전환을 별도 generation으로 구분하는 수정 후 54 files/499 tests·lint/build 자체 PASS다. 독립 HIGH 리뷰와 실제 S3 브라우저 인수는 진행 중이다.
- backend 실제 MySQL 계약 검사 첫 7개는 PASS(`build/x-deployment-backend-contract-20261113/targeted-green-final.log`). V7 제약/LOCAL 유지·S3 controller 선택·완료 멱등·동시 완료·profile/post ACL·cleanup 실패 재시도를 확인했다. rollback/cleanup 경쟁·timeout lock 해제 보완을 추가 배정했다.
- QA 배포 도구 검증 receipt `build/x-deployment-artifacts-20261113/20261113T064150Z-9813d8d2/receipt.json`: Caddy validate·release 단위 5개·실제 HTTP SPA/deep route/API JSON/assets/차단 경로 PASS. mock upstream이며 운영 TLS/Spring/Aiven 인수가 아니다.
- root backup 검증 receipt `build/x-deployment-runtime-20261113/backup-restore-receipt.json`: 소유한 합성 MySQL8.4에 TLS VERIFY_IDENTITY 접속으로 실제 mysqldump→CMS AES-GCM 암호화→인증 복호화→별도 빈 fixture DB 복원 후 2개 row의 전체 필드 hash 일치 PASS. 사용자 DB/Aiven에 접속하지 않았다. 복원 평문 파일은 검증 후 제거했다.
- 실제 S3 인수를 위해 새 합성 DB `zeroverse_deploy`/loopback13337와 전용 volume을 준비했다. API18092·FE5173·개발 prefix `zeroverse/dev/deployment-20261113-79bcf001/`만 사용한다. 비밀은 Git 제외 build 내부 0600 파일이며 보고/로그에 출력하지 않는다. API/FE 기동 및 실제 S3 객체 인수는 아직 진행 전이다.
- T6 운영 적용, Git commit/PR/merge는 실행하지 않았다. 기존 DB와 `.local-data/`는 보존한다.

### 실제 개발 S3·운영 후보 기동 추가 증거

- QA 실제 개발 AWS/browser receipt `build/x-deployment-s3-acceptance-mwemwm8j32086f7b43/evidence.json`: 33 checks 중 required 32/32 PASS, failures0. named dev identity·이번 prefix에서 실제 조건부 PUT/5MiB/동시4read/서명헤더·MIME·checksum 거부/owner ACL/unsigned S3 deny/제품 FE profile 저장을 확인했다. CORS 규칙으로만 해결했으며 공개 접근을 허용하지 않았다.
- root는 동일 receipt와 원본 harness를 직접 읽었다. 기존 U0 결과와 별개인 새 제품 gate 증거다. 운영 origin/identity/Aiven/실제 서버의 T6 결과가 아니다.
- 운영 후보 JAR `964c6957...`는 잘못된 HTTP origin·중복 TLS URL·CORS 경로·prod+local 혼용을 DataSource/Flyway 전에 차단했고, 테스트 CA가 있는 실제 MySQL TLS 연결에서 readiness200/UP/no-store PASS다. 없는 CA는 JDBC TLS handshake 실패로 종료했다. 초기 검증기의 오류 문자열 조건이 좁았던 CA receipt는 원본 로그의 FileNotFound/TLS 실패 증거로 정정했다.
- 같은 후보의 packaged cleanup은 웹 없는 context에서 `HttpSecurity` bean 부재로 실패했다. backend가 filterChain에 servlet 조건을 추가한 후보 `6066357c...`로 재검증하여 dry-run·apply PASS. 빈 합성 DB에서 Flyway=true/ddl=create 인수를 주어도 스키마 생성 없이 실패하고 실제 table count0을 보존했다.
- 실제 서명 URL이 모두 만료된 뒤 이번 합성 PENDING4건의 intent 만료 시각만 앞당겼다. packaged dry-run은 candidates4/transition0/delete0, 명시 apply는 transition4/delete4, DB는 DELETED4·READY3·image3으로 정상 완료 데이터를 보존했다. 근거 `build/x-deployment-runtime-20261113/cleanup-final-receipt.json`. 다른 prefix/기존 객체에는 접근하지 않았다.
- 첫 최종 full regression은 새 경쟁 테스트의 connection 획득/latch 실패2건으로 FAIL이다. 개별11tests PASS와 전체PASS를 혼동하지 않으며, 공유 MySQL context pool 원인 진단을 원 작성자에게 재배정했다. 최신 전체 회귀·독립 backend 리뷰·최종 artifact 동일성은 아직 gate다.

## 14. 최종 구현·독립 검증 판정 — 2026-11-13

**코드/개발 S3 검증 T0~T5 PASS. 실제 운영 적용 T6는 미실행이다.**

- backend 독립 리뷰의 UUID 오류 계약·S3 timeout 상한·CLI UTC·불필요한 generated security password 4건을 수정했다. 최종 판정은 `qa/X-deployment-backend-review.md` §6~7의 PASS다. FE와 통합 산출물 검토는 `qa/X-deployment-review.md`를 따른다.
- 최종 backend 전체: **118 suites / 670 tests / failures0 / errors0 / skipped0**. 로그 `build/x-deployment-backend-20261113/full-final-review4-docker.log`; root 보존 XML `build/x-deployment-runtime-20261113/final-backend-test-results/`. 기존 전체 실패는 MySQL shared context pool 고갈, 겹친 Gradle 보고서 쓰기, sandbox Docker 접근 실패였으며 각각 증거를 보존했다. test 전용 pool 한도/idle0과 V7 fixture cleanup을 보완하고 단일 실행으로 최종 PASS했다. 제품 운영 pool 설정을 임의로 바꾸지 않았다.
- 최종 frontend: **54 files / 499 tests**, lint/build PASS. `build/x-deployment-fe-high-20261113/*-r2.log`와 QA focused 독립 검토를 root가 읽었다. 이후 FE 제품 변경은 없다.
- 최종 JAR SHA-256: `a7fb8fe0f8ab3561a997bc8a4678ad109466b0a78f52462b87700943d4bc1f84`. main 클래스277개·운영 설정·initializer 등록이 현재 컴파일/소스와 일치한다. 원본452개 hash는 `build/x-deployment-runtime-20261113/final-source-manifest.json`에 보존했다.
- 최종 개발 AWS/browser: `build/x-deployment-runtime-20261113/final-reviewed-browser/evidence.json`, **41 checks / required40 PASS / failures0**. 실제 full headers를 `allHeaders()`로 읽어 auth/cookie/referrer가 S3로 전송되지 않음을 검증했다. 잘못된/non-canonical UUID400, canonical missing404, 5MiB·4회 동시 read·MIME/checksum/header·조건부 replay·owner/anonymous ACL·browser profile 저장을 확인했다. [Playwright 공식 Request 문서](https://playwright.dev/docs/api/class-request#request-all-headers)에 맞춰 헤더 수집을 보완했으며 초기 headers()만 사용한 증거의 한계를 최종 실행으로 해소했다.
- 최종 API JVM은 768MiB heap/256MiB metaspace/128MiB direct 한도에서 인수 전체107회 RSS 표본의 peak **594480 KiB(약581MiB)**였다. 이는 macOS 프로세스 측정이며 실제 2GB 운영 호스트 전체 부하 SLA가 아니다.
- final-a7 JAR의 prod/CLI **17개 성공·실패 시나리오 PASS**: 올바른 TLS DB/readiness, 잘못된 origin/TLS 중복/CORS path/profile 혼용/CA, 여섯 timeout 경계, non-UTC CLI, 기본 dry-run, 명시 apply, 빈 DB에 hostile Flyway/DDL 인수, LOCAL row 기동 차단. final prod 로그에 generated password 없음. 임시 LOCAL fixture는 제거했다.
- final-a7 cleanup apply는 실제 서명 URL 만료 이후 준비한 이번 합성 미완료4건만 처리했다. `final-cleanup-review-receipt.json`: DELETED8(누적), 아직 만료되지 않은 PENDING4·READY9·image9 보존, 빈 fixture DB tables0. 기존 완료/탈착/local 데이터 회수 기능을 추가한 것이 아니다.
- 재시작 후 기존 synthetic S3 profile의 바이트 SHA, no-store/nosniff 응답이 같았다. 암호화된 실제 MySQL dump→별도 빈 DB 복원, Caddy HTTP/API/SPA 분리, release 변조/경로 안전 검증도 기존 §13 증거를 유지한다.
- 최종 release: `build/releases/zeroverse-deployment-20261113-a7fb8fe.tar.gz`, 파일11개, SHA-256 **`9b38074bb2e6488634d5e9dd9356ab23a7b7f4db754a46ccd5f7b6f69086d542`**. 서버 JAR·production FE·운영 템플릿/도구만 포함한다. manifest는 packaging identity이며 QA 승인 증거를 대신하지 않는다. 이전 `6066357c` 후보 archive는 최종 release가 아니다.
- 운영 가이드 `deploy/README.md`: 실제 domain/Lightsail/Aiven CA·접속값·운영 identity/prefix를 넣고 T6 승인/운영 인수 후 적용한다. 신규 Aiven/기존 local 보존 결정은 확정됐지만 실제 운영 접속값은 제공되지 않았다. Git commit/PR/merge, 유료 자원 생성, 실제 운영 DB/server 적용은 수행하지 않았다.

### 테스트 자원 종료·보존

- 최종 실제 서명 URL 만료를 확인한 뒤 DB의 이번 합성 `t5-owner/t5-friend @example.test` 소유와 정확한 승인 prefix를 교차 검사했다. 제품 cleanup 처리분 외 남은 테스트 객체 키13개에 DeleteObject 성공 응답13/실패0을 받았다. 이는 이번 합성 fixture의 명시적 teardown이며 READY 이미지 자동 정리 정책을 만든 것이 아니다. bucket listing이나 다른 prefix 접근은 하지 않았다.
- `build/x-deployment-runtime-20261113/synthetic-s3-teardown.json`과 `runtime-shutdown.json`에 근거를 남겼다. root가 시작한 API18092·FE5173 포트는 닫혔고 전용 MySQL 컨테이너는 중지했다. 테스트 DB volume·증거·암호화 백업은 보존했다. 사용자 DB·기존 `.local-data/`·기존 S3 객체·IAM/bucket 설정은 변경하지 않았다.
