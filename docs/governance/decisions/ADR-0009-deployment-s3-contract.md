# ADR-0009 — 첫 운영 배포와 private S3 계약

- 상태: ACCEPTED
- 결정일: 2026-11-13 KST
- 결정 주체: 사용자 “A-v2 전체 승인 (권장)”
- 상세 계약: [배포 회의 §5·§10·§12](../meetings/X-20261113-deployment.md), REQUIREMENTS §6.12-A

## 결정

브라우저는 인증된 API에서 발급받은 5분 presigned PUT으로 private S3에 직접 전송한다. 서버는 24시간 유효한 durable intent의 소유자·용도·크기·MIME·SHA-256을 확인하고 bounded GET/Tika로 실제 bytes를 검증한 뒤에만 canonical image metadata를 생성한다. PUT은 exact length/checksum/Content-Type/If-None-Match를 서명에 고정한다. 실제 browser/AWS 검증이 실패하면 기준을 완화하지 않는다.

기존 `/api/v1/uploads/{uuid}/content` 참조와 최초 binding을 보존한다. GET은 현재 글/프로필 권한을 검사하고 검증된 bytes를 제공한다. public S3, 영구 signed URL 저장, signed GET redirect는 사용하지 않는다. 이미 전달된 bytes의 회수를 보장하지 않는다.

V7은 기존 V1~V6를 고치지 않고 LOCAL/S3 discriminator·고정된 object 위치/checksum 및 upload_intents를 추가한다. 기존 row는 LOCAL이다. complete는 user→intent→image lock, READY 재호출 멱등. cleanup은 24시간 만료 AND 서명 만료 AND 완료 image 부재인 PENDING만 DELETING→DELETED로 처리하며 dry-run 기본/명시 apply/최대100건이다. READY·BOUND·탈착·기존 local 파일은 자동 삭제하지 않는다. 정확한 상태/컬럼/오류/시간 경계는 회의 §10을 따른다.

prod는 private S3와 신규 Aiven DB를 사용한다. 기존 local DB/files는 보존하고 자동 이관하지 않는다. UTC·JDBC TLS VERIFY_IDENTITY/CA·HTTPS origin·보안 cookie·필수 secrets와 S3 위치를 검증하고 local fallback을 막는다. Caddy 동일 origin `/api`/SPA 분리, Spring loopback, Java21/systemd/JVM 상한, 외부 env, artifact manifest와 S3 호환 release rollback을 준비한다.

## 대안과 비용

서버 multipart→S3(B)는 단순하지만 direct upload 방향을 변경한다. S3 URL 자체 저장/resolver(C)는 콘텐츠/검색/FE 참조와 권한 철회 의미를 크게 바꾼다. A는 업로드 intent와 브라우저 검증 비용을 수용해 기존 참조·현재 권한을 유지한다. bytes read proxy의 서버 메모리/egress와 S3 검증 중 user lock 대기는 bounded I/O 및 동시성 시험으로 관리한다.

## 검증과 실행 경계

코드 준비, 실제 개발용 AWS 통합, 실제 운영 인수를 별도로 판정한다. local 회귀·fresh/upgrade MySQL·동시 complete/cleanup/timeout·FE 계정전환·실제 S3 headers/5MiB/checksum/CORS/ACL·backup restore/restart/rollback을 검증한다. 독립 QA와 root의 실측 확인이 필요하다.

승인된 AWS 쓰기는 이번 실행의 `zero-verse-s3/zeroverse/dev/deployment-<run-uuid>/` synthetic 객체에 한한다. IAM/bucket 설정 변경, 유료 서버/도메인 생성, 실사용 DB 적용·승격·기존 데이터 이관/삭제와 실제 배포 T6는 별도다. SDK Java2 S3/BOM 2.49.6 제품 편입과 기존 Tika 재사용은 승인에 포함한다.
