# X-deployment — 배포 후속 독립 QA 검토

## 현재 판정

- 상태: **FE LOW·HIGH REVIEW PASS / T5 SYNTHETIC DEV AWS·BROWSER FINAL REVIEW PASS / PACKAGED PROD SMOKE PASS(운영 전) / PRODUCTION GATES NOT RUN**
- 기준 안건: [`X-20261113-deployment`](../docs/governance/meetings/X-20261113-deployment.md) §10 A-v2, §11 LOW
- 검토자: Codex QA 독립 컨텍스트
- 범위: 코드 준비·격리 검증·개발용 synthetic AWS 검증과 실제 운영 인수의 경계를 정의한다.
- 이 문서는 개발 synthetic AWS/browser 인수 증거와 production 미실행 경계를 함께 기록한다. 실제 운영 DB, 사용자 데이터, 기존 업로드, 배포 서버와 production HTTPS는 사용하지 않았다.

M10의 로컬 MVP QA PASS는 아래 배포 gate를 충족하지 않는다. `qa/M10-review.md:12`, `docs/PRD.md §10 M10`에서 배포·S3/AWS·실사용 DB·운영 SLA를 제외한 범위를 확인했다.

## 검증 매트릭스

| ID | 검증 대상 | 합격 기준 | 필요한 증거·실행 | 현재 상태 / 사유 |
|---|---|---|---|---|
| X-PLAN-01 | A-v2 승인·정본 동기화 | 사용자 승인 후 REQUIREMENTS/PRD/ADR·worklog가 §10 계약과 일치 | 회의 상태·diff·문서 교차 대조 | **PASS (계약 문서 범위)** — 회의 `APPROVED`, 신규 Aiven/기존 local 보존, A-v2 계약과 PRD §13.1/REQUIREMENTS/ADR 정합을 확인; 실제 운영 적용은 별도 |
| X-BE-01 | prod fail-fast | prod에서 localhost/default secret/local storage fallback 없이 필수 설정·UTC·HTTPS origin 검증 | Spring context 기동/실패 로그, 비밀값 미출력 | **PASS (final JAR synthetic smoke)** — `prod-invalid-*`, `prod-cleanup-non-utc`, `prod-local-row` 및 valid smoke의 17 receipts가 final JAR `a7fb8fe0…`에 결속되고 `pass=true`; prod-valid `200/UP/no-store`, generated-password marker 없음. 실제 운영 host/secret은 별도 |
| X-BE-02 | V7 additive migration | V1~V6 불변, `storage_backend`/location/checksum/`upload_intents` 제약·index가 entity와 일치. SHA-256은 CHAR(44) canonical base64(ASCII 대소문자 구분), `(object_bucket,object_key)` 복합 unique, LOCAL 위치/checksum NULL·S3 non-null check | fresh/upgrade/backup-restore MySQL, Flyway history, rollback 재검증 | **PARTIAL PASS** — `V7__s3_upload_intents.sql`·entity와 root 제공 targeted MySQL logs의 V7/LOCAL/S3 controller 계약 PASS; 전체 fresh/upgrade/Flyway/rollback evidence는 별도 |
| X-API-01 | presigned intent | owner/purpose/size/type/checksum 검증, 서버 UUID·prefix 고정, 5분 URL. presigned URL에 포함되는 bucket/object key 자체는 비밀·권한 근거로 취급하지 않으며 access secret·app Bearer를 S3/로그에 노출하지 않음 | 실제 HTTP 201/400/401/403 및 OpenAPI, 로그 secret scan | **PARTIAL PASS** — T5 receipt가 owner presign/prefix·oversize·MIME/checksum·secret-free browser flow를 PASS; 전체 invalid/auth matrix와 OpenAPI 독립 대조는 남음 |
| X-API-02 | complete 상태·오류 계약 | PENDING→READY 원자성, **명확한 404/NoSuchKey일 때만** object 없음 409/`UPLOAD_005`; ListBucket 없는 HeadObject의 403/존재 여부 불명은 `UPLOAD_008`/503, 만료 410/`UPLOAD_006`, checksum 400/`UPLOAD_007` | HTTP body/status + DB row/state snapshot, 403 ambiguity case | **PARTIAL PASS (final T5)** — final reviewed T5가 success/idempotency·pending binding·403 ambiguity·MIME/checksum·양 endpoint malformed 6건 `VALIDATION_001/400`·canonical missing 2건 `UPLOAD_003/404`를 PASS; expiry/DB row snapshot/운영 transaction은 별도 |
| X-API-03 | 멱등·동시성 | 동일 owner complete 재호출은 같은 metadata, 중복 image/binding 없음; UTC clock 초정밀도, SDK attempt 5초·전체 call 10초·transaction 30초, 실제 lock 해제·timeout 복구 확인 | 동시 complete/DB unique/rollback/lock-release 증거 | **PARTIAL PASS** — duplicate complete 및 4 concurrent 5MiB reads PASS; concurrent complete/lock timeout 독립 증거는 남음 |
| X-API-04 | cleanup | dry-run 기본, explicit apply만 `expires_at >= 24h AND signed URL expiry 경과 AND image metadata 부재`를 모두 재확인해 DELETING→DELETED; READY/BOUND/기존 local은 보존 | 0/1/100/101 batch, 실패 재시도, tombstone 증거 | **PARTIAL PASS (final JAR synthetic CLI)** — final JAR `a7fb8fe0…` dry-run/apply/empty/non-UTC/local-row receipts와 tombstone/READY/image 보존 evidence가 PASS; 100/101 batch·실패 retry 독립 CLI matrix는 남음 |
| X-S3-01 | 실제 AWS PUT | private bucket, exact size/checksum/content type, conditional write/replay 차단 | named dev identity·`zeroverse/dev/deployment-<run-uuid>/` synthetic object, raw browser PUT | **PASS (synthetic dev gate)** — final reviewed T5 receipt에서 small/exact 5MiB/browser PUT·complete·If-None-Match PASS; production/other prefixes 미검증 |
| X-S3-02 | AWS negative/security | anonymous GET 거부, prefix 밖 접근 거부, public policy 없음, CORS가 필요한 헤더만 허용 | AWS CLI read-only 확인 + 브라우저 status/headers | **PARTIAL PASS** — CORS exact preflight·unsigned GET 403·browser no app auth/tamper PASS; bucket policy/PAB read는 AccessDenied, prefix 밖 GET/정책 독립 증거는 남음 |
| X-S3-03 | complete/read bytes | HEAD와 bounded GET(5MiB+1)로 실제 MIME/길이/checksum 검증; 명확한 404만 `UPLOAD_005`, 403/존재 여부 불명은 `UPLOAD_008`; mismatch는 metadata 생성·binding 금지 | 성공/0/5MiB/5MiB+1/MIME mismatch/checksum mismatch/403 ambiguity | **PARTIAL PASS** — T5 complete/read exact 5MiB·4 concurrent reads·MIME/checksum·403 ambiguity PASS; 0/5MiB+1 object and DB snapshot remain |
| X-ACL-01 | 이미지 ACL | profile/post의 현재 owner·PUBLIC/UNIVERSE/PRIVATE/draft predicate 유지; 권한 상실 후 404 | 두 계정 HTTP matrix, invalid bearer, no-store/nosniff | **PARTIAL PASS** — T5 unbound canonical owner/friend/anonymous matrix and browser profile binding path PASS; PUBLIC/UNIVERSE/post revoke matrix remains |
| X-FE-LOW-01 | production API base 기본값 | production에서 env 미지정 시 상대 `/api/...`; dev/test는 `http://localhost:8080` 유지 | Vite production build/static assertion + dev unit | **PASS (LOW scope)** — 독립 focused/build와 source/static guard PASS |
| X-FE-LOW-02 | managed image 빈 base | 빈 API base를 현재 browser origin으로 해석하고 canonical path를 fetch | `resolveManagedImage` unit/jsdom + same-origin request assertion | **PASS (LOW scope)** — 독립 resolver 회귀 PASS |
| X-FE-LOW-03 | FE URL 보안 경계 | cross-origin, query/hash/userinfo, 외부·blob·data URL을 계속 거부 | uploadApi/ManagedImage 회귀 | **PASS (LOW scope)** — URL 경계 회귀 PASS |
| X-FE-LOW-04 | LOW 회귀/범위 | FE test/lint/build PASS; API shape·DB·인증·디자인·dependency diff 없음 | focused/full FE output, `git diff` ownership review | **PASS (LOW scope)** — 구현자 full/lint/build 및 QA 독립 focused/build PASS; 운영 gate 제외 |
| X-FE-HIGH-01 | direct PUT UI | presigned→S3 PUT→complete, account switch/abort/retry/stale response가 canonical state를 보존 | 실제 API + browser, no app Authorization to S3 | **PARTIAL PASS** — FE source/independent 40-test review와 final T5 synthetic `uploadImage` browser path/no-app-auth/oversize gate PASS; account switch/abort/stale runtime scenarios remain |
| X-OPS-01 | same-origin HTTPS | Caddy가 `/` 정적 파일·`/api` reverse proxy·SPA deep link을 분리, Spring loopback | 실제 HTTPS domain smoke, `/api` JSON vs HTML content-type | **NOT RUN** — isolated Caddy route subset PASS; 실제 domain/TLS/server는 미실행 |
| X-OPS-02 | refresh cookie | Secure/HttpOnly/SameSite=Strict/Path 유지, rotation·logout replay·재시작 복구 | HTTPS browser cookie headers + protected HTTP | **NOT RUN** — 운영 HTTPS 없음 |
| X-OPS-03 | readiness | prod-only `/api/v1/health/readiness`가 DB SELECT 1·preflight 성공 시 200, 실패 시 503, no-store | DB down/misconfig/healthy HTTP | **PARTIAL PASS (final JAR synthetic smoke)** — final JAR `a7fb8fe0…` valid receipt 200/`UP`/`no-store`와 invalid/preflight negative binding receipts가 PASS; 실제 Aiven/운영 HTTPS에서 healthy·503 전환은 미실행 |
| X-OPS-04 | release/rollback | artifact checksum·FE/BE same release 기록; rollback은 S3 지원·migration 호환 release 사이에서만 | release manifest + stop/start + rollback rehearsal | **PARTIAL PASS (final artifact)** — final archive verify가 11 files/archive SHA `9b38074b…`와 JAR `a7fb8fe0…`를 확인하고 FE/BE package paths가 일치; 실제 rollback rehearsal는 미실행 |
| X-DB-01 | fresh/upgrade/restore | 신규 Aiven DB migration과 백업 복원이 데이터·audit·image metadata를 보존 | 격리 MySQL dump/restore + Flyway report | **PARTIAL PASS (synthetic)** — final backend 118/670 `0/0/0`와 synthetic TLS VERIFY_IDENTITY·암호화 dump/restore receipt PASS; 실제 Aiven fresh/upgrade/운영 restore는 미실행 |
| X-DB-02 | 기존 데이터 경계 | 기존 DB/.local-data 자동 이관·삭제 없음; 기존 데이터가 있으면 별도 이관 승인 전 배포 차단 | target inventory + explicit migration decision | **NOT RUN** — 신규 Aiven 사용·기존 local 보존 정책은 A-v2로 확정됐으나 실제 target 접속값/inventory와 운영 적용은 미실행 |
| X-REC-01 | restart durability | 앱 재시작/서버 교체 후 Aiven rows·S3 bytes·canonical refs 유지 | process restart/instance replacement smoke | **PARTIAL PASS (synthetic runtime)** — restart receipt 200, same SHA-256/no-store/nosniff; 실제 Aiven/서버 교체·운영 restart는 미실행 |
| X-REC-02 | backup/restore | 앱 IAM과 backup IAM 분리, 암호화 dump를 별도 위치에 보관하고 복원 1회 성공 | dump checksum/restore evidence, secret scan | **PARTIAL PASS** — root receipt가 synthetic MySQL TLS VERIFY_IDENTITY·CMS AES-GCM dump/restore/hash 일치를 기록; production backup IAM/schedule/location은 미실행 |
| X-T6-01 | 실제 운영 인수 | signup→login→refresh→post/image→notification→logout, anonymous S3 deny, ACL revoke, restart, rollback 전부 PASS | 실제 domain/Lightsail/Aiven CA/prod identity | **NOT RUN** — T5와 실제 대상 선행 필요 |

## 개발 S3 read-only preflight — 2026-11-13 KST

- 호출 경계: 모든 AWS 호출에 `--profile zeroverse-s3-dev --region ap-northeast-2 --bucket zero-verse-s3`를 명시했다. AWS CLI `2.40.0`을 확인했고, 객체 PUT/GET/DELETE·bucket/IAM 설정 변경은 수행하지 않았다.
- STS 현재 identity: `arn:aws:iam::555634208316:user/zeroverse-s3-dev` (계정 ID는 식별에 필요한 최소 범위로만 기록). 자격증명 파일·키·토큰·presigned URL은 출력하지 않았다.
- `get-bucket-location`: **PASS**, `LocationConstraint=ap-northeast-2`.
- `get-public-access-block`, `get-bucket-ownership-controls`, `get-bucket-encryption`, `get-bucket-cors`, `get-bucket-policy-status`, `get-bucket-policy`: 모두 **NOT RUN / AccessDenied**. 개발 identity에 각 bucket-level `s3:Get*` 권한이 없어 상태를 읽지 못했다. 이는 설정이 안전하다는 증거도, 설정이 없다는 증거도 아니다.
- `head-bucket`: HTTP 403. `list-objects-v2 --prefix zeroverse/dev/ --max-keys 1`: `s3:ListBucket` AccessDenied. bucket-level List/Head 권한을 앱 identity에 임의로 추가하지 않았고, 객체 목록·기존 객체 상태를 추정하지 않는다.
- 읽기 전용 CORS preflight: `OPTIONS https://zero-verse-s3.s3.ap-northeast-2.amazonaws.com/zeroverse/dev/deployment-preflight`에 `Origin: http://localhost:5173`, `Access-Control-Request-Method: PUT`, `Access-Control-Request-Headers: content-type,if-none-match,x-amz-checksum-sha256`를 보냈다. 응답은 **HTTP 403 Forbidden**이었고 `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`는 응답에 없었다. 따라서 이 요청은 CORS preflight를 충족하지 않는다. 403만으로 CORS 미구성과 origin/method/header 불일치를 구분하지 않으며, 버킷 정책·객체 존재·권한 상태를 추정하지 않는다.
- 당시 결론: S3 실제 AWS gate(`X-S3-01~03`)는 **NOT RUN**이었다. 이후 T5 synthetic AWS/browser 실제 인수 section이 개발 prefix 범위의 최신 evidence를 기록한다. bucket-level PAB/ownership/encryption/CORS/policy 상태는 여전히 bucket owner/admin read context 또는 명시된 read-only 권한으로 별도 확인해야 한다.

### CORS 최소 초안 (적용하지 않음)

현재 CORS를 읽지 못했고 운영 origin도 정해지지 않았으므로 설정 변경은 하지 않는다. 개발 브라우저가 실제로 `http://localhost:5173`에서 presigned PUT을 수행하고, 서버가 complete/read를 담당하는 A-v2 경계를 전제로 한 최소 초안은 다음과 같다.

```json
[
  {
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["content-type", "if-none-match", "x-amz-checksum-sha256"],
    "ExposeHeaders": [],
    "MaxAgeSeconds": 300
  }
]
```

- 이번 read-only OPTIONS preflight는 위 초안과 같은 origin/method/header 조합에서도 403과 CORS allow 헤더 부재를 반환했다. 버킷 owner/admin read context에서 실제 CORS를 먼저 확인하고, 아래 초안은 적용 전 검토안으로만 둔다.
- `http://127.0.0.1:5173`은 별도 origin이므로 실제 harness가 그 주소를 쓸 때만 별도 추가한다.
- SDK presign 응답의 실제 `SignedHeaders`에 `x-amz-content-sha256` 등 추가 항목이 있으면 그 헤더만 최소 목록에 보완한다. `AllowedHeaders: *`, public GET, 운영 도메인 추정은 사용하지 않는다.
- 운영 origin은 domain/TLS가 확정된 뒤 별도 CORS diff와 실제 preflight/browser PUT으로 검증한다.

### CORS OPTIONS 재검증 — 규칙 추가 후 2026-11-13 KST

- 동일 endpoint와 요청 조합을 규칙 추가 후 다시 호출했다. 응답은 **HTTP 200 OK**였고 `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Methods: PUT`, `Access-Control-Allow-Headers: content-type, if-none-match, x-amz-checksum-sha256`가 반환됐다.
- 함께 관측된 응답은 `Access-Control-Expose-Headers: ETag`, `Access-Control-Max-Age: 300`, `Access-Control-Allow-Credentials: true`, `Vary: Origin, Access-Control-Request-Headers, Access-Control-Request-Method`였다. 이 요청에서는 allow origin/method/header가 일치한다.
- 당시 판정: 개발 origin의 지정 preflight는 **부분 PASS**였다. 이 OPTIONS만으로 private bucket, anonymous GET 거부, prefix ACL, signed PUT/5MiB/checksum 및 실제 브라우저 PUT을 증명하지 못했으며, 이후 T5 synthetic AWS/browser 실제 인수 section에서 별도 검증했다. 실제 CORS 설정 JSON은 bucket owner/admin read context에서 여전히 확인하지 못했다.

## 배포 산출물 격리 검증 — 2026-11-13 KST

- QA 소유 harness [`qa/deployment-artifacts-test.py`](deployment-artifacts-test.py)를 추가해 실제 `deploy/Caddyfile`을 읽고, `deploy/test_release.py`를 실행한 뒤 지정 digest `caddy@sha256:78bd35025c7ba85961dd149ae670315129954e77abe2383ac47c89106b35ade9`에서 검증했다. Caddyfile 검증은 stdin, fixture config/static 주입은 `docker cp`를 사용했으며 repository bind mount는 사용하지 않았다.
- 최종 receipt: `build/x-deployment-artifacts-20261113/20261113T064150Z-9813d8d2/receipt.json`. source-contract, release unit tests(5), Docker/image, 실제 Caddyfile validate, `127.0.0.1:18198` 가용성, fixture validate, container start, HTTP route checks가 모두 **PASS**다.
- HTTP 관찰: `/deep/spa` `200 text/html` + `Cache-Control: no-store`; `/assets/app.js` `200`; `/assets/missing.js` `404` 비HTML; `/api/v1/feed/public` dummy upstream JSON `404` 보존; `/api/v1/health`, `/swagger-ui/index.html`, `/v3/api-docs` 외부 `404` 차단. 임시 container는 receipt 생성 전에 force remove했고 남은 named container가 없는 것을 확인했다.
- 경계: 이는 격리 HTTP/Caddy routing과 release unit test 증거다. 실제 HTTPS certificate/domain, Spring 운영 upstream, Aiven DB, S3 private ACL/presigned PUT/5MiB/checksum, restart/rollback을 검증하지 않으므로 `X-OPS-01`, `X-T6-01`, AWS gate를 운영 PASS로 승격하지 않는다.

## 운영 cleanup CLI 문서 검토 — 2026-11-13 KST

- `deploy/README.md:56–67`의 새 설명은 packaged JAR를 `systemd-run --wait --pipe --collect --uid=zeroverse`와 `EnvironmentFile=/etc/zeroverse/zeroverse.env`, 고정 `WorkingDirectory`, `-Duser.timezone=UTC`, `-Dloader.main=com.zeroverse.tools.UploadIntentCleanup`, `PropertiesLauncher`로 실행하도록 한다. env 파일을 셸에서 출력/source하지 않으며 기본 dry-run 뒤 후보·대상 확인 후 `--upload.cleanup.apply=true`를 명시한다.
- 문서의 안전 경계(최대 100건, `expires_at`와 signed PUT URL 만료 동시 충족, image metadata 없음, READY/바인딩/탈착/LOCAL 보존, 실패 시 DELETING 재시도, 자동 스케줄 없음)는 현재 `UploadIntentCleanup`/`UploadIntentCleanupService`의 source shape와 일치한다. 이는 문서·소스 정합성 관찰이지 packaged artifact 실행 증거가 아니다.
- 판정: `X-API-04` cleanup의 실제 dry-run/apply, Flyway/DDL 억제, TLS Aiven DB, failure retry와 packaged JAR `PropertiesLauncher` invocation은 **NOT RUN**이다. root/BE가 synthetic TLS DB에서 packaged JAR command receipt를 제공한 뒤 독립 재검토해야 하며, 운영 DB·사용자 데이터에는 접근하지 않는다.

## FE HIGH 독립 검토 — 2026-11-13 KST

- 검토 대상: `frontend/src/lib/apiClient.ts`, `frontend/src/features/upload/uploadApi.ts`, `uploadTransport.ts`, `vite.config.ts`와 해당 테스트 diff. 구현자 r2 로그는 source 변경 시각(최대 15:41:24 KST) 뒤인 15:42에 focused/full/lint/build를 실행했다.
- 구현자 evidence: focused `3 files / 40 tests PASS`, full `54 files / 499 tests PASS`, lint/build exit 0, production에서 `VITE_UPLOAD_TRANSPORT=local`을 거부하는 staging build expected exit 1. QA 독립 실행도 동일 focused `3 files / 40 tests PASS`, exit 0(15:45:22 KST)이다.
- 독립 소스 판정: normal 401 refresh는 `tokenVersion`만 회전하고 `authSessionVersion`은 유지한다. 실제 계정 변경/로그아웃·만료는 session generation을 바꾸고 늦은 refresh/complete를 폐기한다. S3 direct PUT은 `credentials: omit`, `redirect: error`, `referrerPolicy: no-referrer`이며 app Authorization/Cookie를 signed headers에서 거부한다. complete는 intent id와 요청 MIME을 재확인하고, 412/ambiguous network/503·UPLOAD_008 재시도 경계를 분리한다.
- 결과: **FE HIGH source/replay review에서 Critical/Important finding 0, PASS(코드 범위 한정)**. 이 판정은 실제 S3 CORS/PUT/5MiB/checksum/ACL/브라우저 계정 전환을 대신하지 않는다. 제품 E2E는 아래 T5 harness 실행과 실제 runtime evidence가 필요하다.

## T5 S3 acceptance harness 준비 — 2026-11-13 KST

- 도구: [`qa/x-deployment-s3-acceptance.mjs`](x-deployment-s3-acceptance.mjs). `T5_RUNTIME_PUBLIC_CONFIG`(기본 `build/x-deployment-runtime-20261113/public.json`)만 읽으며, private runtime config·비밀·DB SQL은 사용하지 않는다. 정확한 `localhost:18092` API, `localhost:5173` FE, synthetic MySQL `13337`, profile/bucket/region/prefix를 fail-closed로 확인한다. 사용자 DB `13307`은 금지 상수로 방어한다.
- 실제 실행 전제: root가 API/FE와 synthetic DB를 기동하고 `T5_AWS_WRITE_APPROVED=1`을 명시해야 한다. 승인 flag나 정확한 runtime config가 없으면 API/browser/AWS를 호출하지 않고 `NOT RUN` receipt만 남긴다.
- 포함 gate: synthetic owner/friend register·initial setup, 공개 feed 성공 envelope를 이용한 **local application availability**(prod readiness 아님), S3 CORS OPTIONS, 서버 prefix-bound presign, raw valid PNG PUT, exact 5MiB, exact 5MiB canonical object 4회 동시 read, >5MiB rejection, signed content-type header tamper denial, MIME/checksum mismatch, `If-None-Match` replay, complete success/idempotency, pending complete/binding block, owner/friend/anonymous canonical read ACL, unsigned direct S3 deny, 실제 FE `uploadImage`를 synthetic `File`로 실행하는 profile upload, app auth/cookie/referrer 없는 direct PUT, browser CORS, browser >5MiB no-presign.
- 403/NoList 경계: pending/mismatch complete에서 403을 404로 추정하지 않는다. `UPLOAD_008/503` ambiguity를 허용하고 `UPLOAD_005/409`는 명확한 missing 증거가 있을 때만 허용하도록 기록한다. presigned URL·토큰·cookie·credentials·object key는 log/evidence에 저장하지 않는다.
- 준비 검증: `node --check qa/x-deployment-s3-acceptance.mjs` PASS; `--self-check`는 `SELF-CHECK PASS`; 승인 flag를 제거한 정상 실행은 API/AWS를 호출하지 않고 `NOT RUN` receipt를 생성했다.

## T5 synthetic AWS·browser 실제 인수 — 2026-11-13 KST

- root가 승인한 runtime에서만 `T5_AWS_WRITE_APPROVED=1 node qa/x-deployment-s3-acceptance.mjs`를 실행했다. 첫 sandbox 실행은 API/AWS/browser 전에 local TCP `127.0.0.1:13337` 연결 `EPERM`으로 중단됐고, 동일 명령을 제한 해제한 뒤 재실행했다. 첫 실패 receipt는 `build/x-deployment-s3-acceptance-mwemwdjw47c17eb1d2/evidence.json`이며 제품 실패로 해석하지 않는다.
- 최종 receipt: [`build/x-deployment-s3-acceptance-mwemwm8j32086f7b43/evidence.json`](../build/x-deployment-s3-acceptance-mwemwm8j32086f7b43/evidence.json). status `PASS`, checks `33`개 중 required `32/32 PASS`, failures `0`. runtime은 API `http://localhost:18092`, FE `http://localhost:5173`, synthetic MySQL `13337`, bucket `zero-verse-s3`, region `ap-northeast-2`, prefix `zeroverse/dev/deployment-20261113-79bcf001/`로 public config와 결속됐다.
- PASS 범위: owner/friend synthetic register·setup, S3 CORS preflight, server prefix-bound presign, small PUT, exact 5MiB PUT/complete, 같은 객체 4회 동시 5MiB read, If-None-Match replay(412), complete 멱등성, pending binding 차단, HeadObject 403 ambiguity를 `UPLOAD_008/503`으로 유지, MIME/checksum mismatch metadata 차단, signed content-type tamper 거부, >5MiB API/browser no-presign, owner canonical read, friend/anonymous API read 거부, unsigned S3 GET 403, 실제 FE `uploadImage` profile binding, browser direct PUT의 app Authorization/cookie/referrer 부재를 포함한다.
- receipt는 access token/password/cookie/presigned URL/object key를 기록하지 않았고 secret guard가 PASS했다. 생성된 synthetic object/metadata는 승인된 prefix에 남아 있으며 이 harness는 자동 삭제하지 않는다. 정확한 prefix cleanup과 artifact/database 보존 확인은 root가 별도 수행한다.
- 경계: 이는 개발 named identity와 synthetic runtime의 AWS/browser PASS다. bucket-level PAB/ownership/encryption/policy 조회는 권한 부족으로 미실행이며 prefix 밖 접근, production HTTPS/Caddy/domain, Aiven 운영 DB, refresh cookie, restart/rollback, T6 운영 인수로 승격하지 않는다.

## packaged prod·cleanup·restart·memory 사전 최종 증거 검토(역사) — 2026-11-13 KST

> 이 section은 final JAR `6066357c…` 이전의 중간 증거다. 아래 `final reviewed T5·backend·prod smoke·release` section이 현재 판정을 supersede한다.

- final browser receipt [`build/x-deployment-runtime-20261113/final-browser/evidence.json`](../build/x-deployment-runtime-20261113/final-browser/evidence.json)는 동일 runtime/prefix의 T5 status `PASS`, 33 checks, required `32/32`, failures `0`을 재확인한다. final browser 실행 JAR SHA-256은 [`final-browser-memory.json`](../build/x-deployment-runtime-20261113/final-browser-memory.json)의 `6066357c093b0032741ce5bb275474f493f3457639ed8e065d5d244203cf7738`이며 현재 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar` hash와 일치한다.
- `prod-valid-receipt.json`은 같은 final JAR에서 prod readiness `200`, body `UP`, `Cache-Control: no-store`를 기록한다. `prod-cleanup-receipt.json`은 dry-run candidates 4/transitioned 0/deleted 0, `prod-cleanup-apply-receipt.json`은 apply candidates 4/transitioned 4/deleted 4/retried 0/skipped 0, `prod-cleanup-empty-receipt.json`은 hostile flags를 거부한 exit 1을 `pass=true`로 기록한다. [`cleanup-final-receipt.json`](../build/x-deployment-runtime-20261113/cleanup-final-receipt.json)은 deleted tombstones 4, READY intents 3 보존, image rows 3 보존, empty DB tables 0을 기록한다.
- `deploy/README.md:59–64`의 cleanup classpath `-cp /opt/zeroverse/current/backend/app.jar org.springframework.boot.loader.launch.PropertiesLauncher`는 `deploy/release.py:64`의 packaged member `backend/app.jar`와 일치한다. 운영 env를 shell source하지 않는 `systemd-run` 절차와 dry-run/apply 경계도 확인했다.
- [`restart-receipt.json`](../build/x-deployment-runtime-20261113/restart-receipt.json)은 HTTP 200, 동일 content SHA-256, `no-store`, `nosniff`를 기록한다. [`final-browser-memory.json`](../build/x-deployment-runtime-20261113/final-browser-memory.json)은 final JAR browser/S3 run의 4×5MiB read 구간에서 76 samples, peak RSS `715712 KiB`(약 699 MiB), process RSS 2GiB 이내를 기록한다. 이는 macOS API JVM process 관찰이며 2GiB production host load/SLA PASS가 아니다.
- 중요한 결속 한계: `prod-invalid-early/duplicate-tls/mixed-profile/origin-path/ca-receipt.json`의 negative fail-fast 실행 hash는 `964c6957…`로 final JAR `6066357c…`와 다르다. 따라서 final immutable release의 invalid-config fail-fast는 재실행·hash 결속 전까지 PASS로 주장하지 않는다. 전체 BE full의 connection-pool 실패도 원저자 수정·재검증 전 최종 코드 READY로 승격하지 않는다.

## 후보 release 독립 검증·브라우저 헤더 증거 보강(역사) — 2026-11-13 KST

> 이 section은 후보 release `6066357c…`와 allHeaders 재실행 전 상태를 보존한다. 현재 final artifact는 아래 `a7fb8fe…` section을 따른다.

- 후보 archive [`build/releases/zeroverse-deployment-20261113-6066357.tar.gz`](../build/releases/zeroverse-deployment-20261113-6066357.tar.gz)를 독립 검증했다. `python3 deploy/release.py verify`는 exit 0으로 11 files와 archive SHA-256 `c0d07972b74ab7585ea5df450021e2e9490d2c6f535cd9ab58e4216cf50725aa`를 확인했고, `manifest.json`의 `releaseId`, 파일 수, 각 SHA/size가 archive와 일치한다. packaged `backend/app.jar` SHA-256은 final `6066357c093b0032741ce5bb275474f493f3457639ed8e065d5d244203cf7738`이다.
- archive member는 backend JAR, production FE dist 3개, Caddy/README/backup/release/CORS/env-example/service 운영 파일로 제한된다. 금지 경로(`.env`, `.aws`, credential, keystore, `.p12`, `.jks`)는 없었다. JAR에는 `application-prod.yml`, `PropertiesLauncher`, cleanup class가 있고, 허용된 `application-local.example.yml`만 포함되어 실제 local 설정 파일/keystore와 혼동하지 않았다. manifest의 `sourceDirty=true`는 후보 산출물 provenance이며 운영 배포 승인으로 해석하지 않는다.
- final JAR 결속 receipt는 prod valid `200/UP/no-store`, cleanup dry-run/apply/empty, restart `200/no-store/nosniff`, final browser 33 checks/required 32/32, memory 4×5MiB peak RSS `715712 KiB`를 기록한다. cleanup receipt의 synthetic 범위는 tombstone 4 삭제·READY intent 3/image row 3 보존·empty DB 0 tables다. RSS는 macOS API JVM 관찰이지 2GiB production host load/SLA 증거가 아니다. 이전 negative fail-fast receipts는 JAR `964c6957…`이므로 final `6066357c…` 결속 PASS가 아니며, BE full connection-pool 2 failures도 원저자 수정·재검증 전 READY가 아니다.
- 브라우저 S3 요청 증거의 독립성을 보강하기 위해 [`qa/x-deployment-s3-acceptance.mjs`](x-deployment-s3-acceptance.mjs)의 direct PUT 감시를 동기 `request.headers()`에서 async `request.allHeaders()`로 바꾸고, 모든 pending header task를 앱 인증/쿠키/referrer 판정 전에 `Promise.all`로 기다리게 했다. header read 실패는 `auth/cookie/referer=true`로 fail-closed하며 값 자체는 저장하지 않는다. `node --check`와 `--self-check`는 PASS(`build/x-deployment-s3-acceptance-mwenklj462bfcb68ee/evidence.json`)다.
- 위 패치 후 실제 T5 AWS/browser 재실행은 아직 하지 않았다. 따라서 기존 `final-browser/evidence.json`의 browser direct PUT header 판정은 패치 전 실행 증거이며, root가 승인한 final memory wrapper 재실행 후에만 allHeaders 기반 증거로 갱신한다. 현재 AWS/DB/운영 서버에는 추가 변경이 없다.

## final reviewed T5·backend·prod smoke·release — 2026-11-13 KST

- 최신 [`final-reviewed-browser/evidence.json`](../build/x-deployment-runtime-20261113/final-reviewed-browser/evidence.json)은 final harness source 기준 status `PASS`, 총 41 checks, required `40/40`, failures `0`이다. 승인 runtime은 API `18092`, FE `5173`, synthetic MySQL `13337`, S3 prefix `zeroverse/dev/deployment-20261113-79bcf001/`이며, `request.allHeaders()`를 await한 direct PUT에서 Authorization/cookie/referrer 없음이 PASS다.
- 새 UUID contract checks도 실제로 PASS했다. `/complete`와 `/content` 각각 `not-a-uuid`, shortened, uppercase UUID가 `VALIDATION_001/400`(6 checks), canonical-but-missing UUID가 `UPLOAD_003/404`(2 checks)이다. 이 receipt는 malformed path 호출이 S3 object operation 없이 API에서 끝났음을 전제로 한다.
- [`final-reviewed-browser-memory.json`](../build/x-deployment-runtime-20261113/final-reviewed-browser-memory.json)은 JAR SHA `a7fb8fe0f8ab3561a997bc8a4678ad109466b0a78f52462b87700943d4bc1f84`, 107 samples, peak RSS `594480 KiB`를 기록한다. 범위는 macOS API JVM과 4×5MiB read 구간이며 production host 2GiB load/SLA 증거는 아니다.
- [`final-backend-test-results/summary.json`](../build/x-deployment-runtime-20261113/final-backend-test-results/summary.json)은 118 suites/670 tests, failures/errors/skipped `0/0/0`이다. `qa/X-deployment-backend-review.md`의 2차 독립 reviewer 판정은 별도 artifact·소유권으로 유지하며, 이 QA 문서가 그 결과를 덮어쓰거나 대신 승인하지 않는다.
- 현재 `build/x-deployment-runtime-20261113/prod-*-receipt.json` 17개는 모두 `pass=true`이며 JAR SHA `a7fb8fe0…`로 결속된다. valid `200/UP/no-store`, invalid origin/TLS/profile/S3 timeout·zero/malformed/order, non-UTC, local-row guard와 generated-password marker 부재를 포함한다.
- 최종 archive [`build/releases/zeroverse-deployment-20261113-a7fb8fe.tar.gz`](../build/releases/zeroverse-deployment-20261113-a7fb8fe.tar.gz)는 `deploy/release.py verify` exit 0, 11 files, archive SHA-256 `9b38074bb2e6488634d5e9dd9356ab23a7b7f4db754a46ccd5f7b6f69086d542`, packaged JAR SHA `a7fb8fe0…`이다. member/path에는 금지된 `.env`, `.aws`, credential, keystore, `.p12`, `.jks`가 없고 JAR에는 허용된 `application-local.example.yml`만 포함된다. `sourceDirty=true`는 후보 provenance이며 운영 승인 자체가 아니다.
- [`final-source-manifest.json`](../build/x-deployment-runtime-20261113/final-source-manifest.json)은 compiled classes 277개·resources 차이 0개·source files 452개 hash와 final JAR `a7fb8fe0…`를 결속한다.
- teardown/shutdown: [`synthetic-s3-teardown.json`](../build/x-deployment-runtime-20261113/synthetic-s3-teardown.json)은 signed URL 만료 확인 후 이 실행의 synthetic 13 keys를 명시적으로 삭제(`ack=13`, failures=0)했다. 이는 synthetic READY fixture를 포함한 테스트 정리이며 제품 cleanup 정책의 자동 동작 증거가 아니다. [`runtime-shutdown.json`](../build/x-deployment-runtime-20261113/runtime-shutdown.json)은 API `18092`·FE `5173` 종료, MySQL container 중지, volume 보존, 사용자 DB/local uploads 미변경을 기록한다.
- 남은 경계: 실제 domain/Lightsail/Aiven/HTTPS refresh-cookie·rollback/T6는 미실행이며, S3 prefix 밖 정책·bucket-level PAB/ownership/encryption/policy는 권한/운영 gate로 남는다. 따라서 이번 판정은 T0~T5 코드·합성 인수 closeout이지 실제 배포 완료가 아니다.

## FE LOW 독립 리뷰 슬롯

회의 §11은 이 부분만 `AUTO_APPROVED`로 분리했다. 아래 슬롯 표는 구현 전 계획 시점의 기준을 보존하며, 구현 후 독립 판정은 다음 `FE LOW 독립 검토 결과` section에 기록했다.

| 슬롯 | 독립 확인 내용 | 판정 규칙 |
|---|---|---|
| LOW-R1 | `apiClient` production default가 `''`이고 dev/test localhost fallback을 유지하는지 확인 | production request URL은 상대 `/api`; localhost/HTTP를 production 기본값으로 허용하면 FAIL |
| LOW-R2 | `resolveManagedImage`가 빈 base에서 `window.location.origin`을 사용하고 canonical path를 유지하는지 확인 | same-origin canonical은 PASS, 외부·query/hash/userinfo·blob/data는 기존처럼 거부 |
| LOW-R3 | 명시 `VITE_API_BASE_URL` 호환성과 existing API client tests 확인 | credentials/Bearer/401 single-flight/API response shape 변경 시 LOW 범위 이탈 |
| LOW-R4 | FE focused/full test, lint, build와 diff scope 확인 | `frontend/**`만 변경, 새 dependency/API/DB/security/design 변경 없음 |
| LOW-R5 | 운영 배포 대체가 아님을 확인 | FE PASS만으로 S3·HTTPS·proxy·운영 인수 PASS로 승격 금지 |

예상 독립 명령(구현 후에만 실행):

```text
cd frontend && npm test -- --run --maxWorkers=1
cd frontend && npm run lint
cd frontend && npm run build
git diff -- frontend/src/lib/apiClient.ts frontend/src/features/upload/uploadApi.ts frontend/src/test frontend/src/features/upload
```

위 명령 중 LOW 대상 focused test와 production build는 아래 독립 검토 결과로 실행했다. backend 전체·reverse proxy·실제 HTTPS는 아직 실행하지 않았고 X-OPS-01에서 따로 검증한다.

## FE LOW 독립 검토 결과 — 2026-11-13 KST

- 검토 대상은 `frontend/src/lib/apiBase.ts`, `apiClient.ts`, `uploadApi.ts`, `vite.config.ts`와 관련 테스트다. S3 API, DB, 인증 cookie 정책, 디자인, 새 dependency 변경은 diff에서 확인되지 않았다.
- 구현자 로그의 source 시각은 `apiBase.ts`/환경 테스트 `15:12:06`, focused GREEN `15:12:11`, production build `15:12:53`, lint `15:12:46`으로 source 변경 뒤에 실행됐다. 구현자 기록의 focused GREEN은 4 files/34 tests, full FE는 53 files/485 tests, lint/build exit 0이다. production bundle 검사 `NO_LOCAL_API_ORIGIN`, HTTP/loopback staging 설정은 expected exit 1로 기록됐다.
- QA 독립 focused 실행: `cd frontend && npm test -- --run src/test/apiBase.test.ts src/test/apiClientEnvironment.test.ts src/features/upload/uploadApi.test.ts src/features/upload/ManagedImage.test.tsx --maxWorkers=1` → **4 files / 27 tests PASS, exit 0** (15:16:51 KST).
- QA 독립 production build: `cd frontend && npm run build` → **150 modules, exit 0**. Vite chunk 958.59 kB warning만 남았으며 build failure는 없다.
- QA 독립 정적 확인: `frontend/dist/assets/index-*.js`에서 `http://localhost:8080`·`127.0.0.1:18090`이 발견되지 않았다(`NO_FORBIDDEN_BASE_ORIGIN`). `git diff --check`도 무출력 exit 0이다.
- 확인된 경계: production 기본값은 빈 base여서 `/api/...` 상대 요청을 만들고, dev/test 기본값은 `http://localhost:8080`이다. explicit trailing slash는 제거되고 `//api`가 생기지 않는다. `localhost`, `localhost.`, `.localhost`, IPv4/IPv6 loopback과 HTTP production base는 build guard에서 거부된다. managed image 빈 base는 현재 browser origin을 사용하며 query/hash/userinfo/external origin은 계속 거부한다.
- 판정: **LOW-R1~R5 APPROVE**. 이 판정은 FE 설정·resolver·회귀 범위에 한정하며 S3, HTTPS proxy, backend, 운영 배포 PASS를 의미하지 않는다.

## 운영 완료와 코드 준비의 분리

- 코드 준비 PASS는 backend/frontend 회귀, 격리 MySQL, S3 adapter/intent 테스트, 배포 설정 검증을 의미한다.
- AWS 통합 PASS는 실제 named dev identity와 private bucket의 브라우저 PUT, checksum/5MiB/CORS/ACL/complete retry를 의미한다.
- 운영 배포 PASS는 실제 domain/Lightsail/Aiven CA/prod identity에서 HTTPS·refresh·이미지·알림·restart·backup restore·rollback을 의미한다.
- 어느 단계도 다음 단계의 PASS를 대신하지 않는다. 과거 LocalStack/SeaweedFS U0 `BLOCKED` 기록을 이번 제품·AWS PASS로 재라벨링하지 않는다.

## 현재 미실행·차단 정보

- 사용자 A-v2 전체 승인 메시지는 도착했고 FE LOW/HIGH 독립 검토, final reviewed T5 synthetic AWS/browser, final backend 독립 reviewer PASS를 확보했다. 회의 상태·PRD/REQUIREMENTS/ADR 정본과 신규 Aiven/기존 local 보존 결정도 동기화됐다. production 운영 gate는 여전히 별도 범위다.
- 신규 Aiven 사용과 기존 local 데이터 보존은 확정됐다. 실제 Aiven 접속값/CA, domain/Lightsail, prod S3 identity/prefix와 운영 실행 승인은 아직 제공되지 않았다.
- final JAR `a7fb8fe0…`의 prod valid/readiness, 17개 prod smoke receipt, cleanup apply/empty/non-UTC/local-row, final reviewed browser 41/40, bounded-memory peak `594480 KiB`, final backend 118 suites/670 tests `0/0/0`, final source manifest compiled class/resource diff `0`를 확인했다. final archive `a7fb8fe`도 독립 verify PASS다.
- `qa/X-deployment-backend-review.md`의 별도 독립 reviewer가 BE-X-001~004를 최종 PASS로 승인했으며 QA는 그 문서를 덮어쓰지 않았다. synthetic prefix teardown/shutdown도 완료됐고, 실제 domain/Lightsail/Aiven/HTTPS/rollback/T6는 운영 gate로 미실행이다.
- 개발 S3 preflight는 location/identity와 지정 CORS OPTIONS를 확인했지만 bucket-level 보안·CORS·policy 조회는 권한 부족으로 미실행이다. T5는 root 승인 synthetic prefix에만 객체를 생성했으며 운영 bucket/prefix·운영 DB·사용자 데이터·운영 서버는 변경하지 않았다. QA 소유 review·상태·worklog와 FE 검증 산출물만 갱신됐다.

## 독립 QA 검토 결론

현재 배포 QA 결론은 **T0~T5 FINAL QA CLOSEOUT PASS** — FE LOW·HIGH 독립 검토, final T5 synthetic dev AWS/browser 40/40, final backend 독립 reviewer 및 118/670 회귀, final artifact·prod smoke·cleanup, synthetic teardown/shutdown을 모두 독립 대조했다. final archive와 final JAR identity도 검증했다. 이는 실제 배포 완료나 production 운영 승인으로 해석할 수 없다. 실제 domain/Lightsail/Aiven/HTTPS refresh-cookie·rollback/T6, bucket-level/prefix 밖 정책은 별도 운영 gate다.
