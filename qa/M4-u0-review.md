# M4 U0 로컬 업로드 안전성 독립 QA

검토 시각: 2026-09-09 12:38 KST

검토자: Codex 독립 QA (이번 U0 구현에는 참여하지 않음)

## 범위와 결론

사용자 승인 범위인 `docs/governance/meetings/M4-20260908-posts.md` §12.3·§14의 U0만 검토했다. `FR-UPLOAD-01~04`, `PRD` §5.12·§13.1, 회의록 §11.4·§11.5를 기준으로 [U0Harness.java](/C:/Users/PC/Desktop/zeroverse-server/gradle/u0/src/main/java/com/zeroverse/u0/U0Harness.java), [index.html](/C:/Users/PC/Desktop/zeroverse-server/frontend/u0/index.html), [compose.localstack.yml](/C:/Users/PC/Desktop/zeroverse-server/compose.localstack.yml)의 최종 내용을 읽기 전용 대조했다. 제품 API·DB·도메인·실제 AWS·구매·U1은 검토하지 않았다.

**판정: BLOCKED (U0 PASS 아님), 확신도 99/100, 위험 HIGH/기밀성 CRITICAL.**

## 사실 근거

- `compileJava`는 첫 실행에서 Gradle cache `AccessDenied`로 exit 1이었으나 권한 승인 후 동일 U0 명령이 exit 0이었다. 이는 제품 코드 실패가 아니다. 최초 `--self-check`는 bucket 생성 전 잘못된 WebP fixture로 중단됐고, fixture 보정 뒤 fresh process/bucket 실측을 다시 했다.
- root가 별도 실행한 fresh U0 세션의 실제 결과는 **19 CASE 중 18 PASS, 1 FAIL**, **28 VERIFY 중 27 PASS, 1 FAIL**이다. 정상 JPEG/PNG/WebP/GIF, 정확한 5 MiB, 크기 경계, checksum/byte/type 변조, 만료 PUT, replay overwrite(412), signed GET/변조/만료, bounded GET·HEAD·Tika와 객체 부재 검사가 통과했다.
- 실패는 `unsigned-private-get`: 실제 HTTP **200**, 기대 **403**이다. `/verify`의 `unsigned-private-get-403`도 같은 이유로 실패했다. `S3_SKIP_SIGNATURE_VALIDATION=0`, `S3_VALIDATE_SIGNATURES=1`은 compose에 있으나 IAM authorization enforcement는 활성화되지 않았다. 따라서 서명 검증과 private 권한 검증을 혼동할 수 없다.
- root의 독립 CUA 브라우저 실행도 DOM에서 `19/19 cases · 18 PASS · verify 27/28 PASS`를 재현했다. `fetch TypeError/CORS`는 없었고, unsigned private GET만 S3 HTTP 200 FAIL이었다. root의 explicit endpoint read-only 확인에서 이번 U0 고유 bucket 두 개(`zeroverse-u0-20260909033216-56be5afc5b0e4c1a9d8b8981fadfb0ce`, `zeroverse-u0-20260909033250-8112da8fbbed4d9dbc570e81e84f7f3b`)에 각 8 objects가 있었고 삭제는 없었다. signed URL/token 출력도 없었으며, helper 종료 후 harness 14567 listener는 0이고 LocalStack S3만 유지됐다. QA는 동일 key 재실행이나 새 bucket/브라우저 실험을 하지 않았다.

## 정적 대조와 경계

하네스는 S3 endpoint를 `http://127.0.0.1:14566`으로 고정하고 SDK/presigner에 정적 U0 자격증명과 endpoint override를 사용한다. bucket public-access block 4개, bucket-owner-enforced, exact harness-origin CORS, required signed headers 및 브라우저 `Host`/`Content-Length` 미설정을 검사한다. 브라우저 페이지는 raw `File`, native `atob`, 8 MiB manifest cap, 실제 5 MiB `File.size`, `credentials: omit`, redirect error, TypeError/CORS를 실패로 분류한다.

다만 `mime-spoof-jpeg-as-png`는 raw S3 PUT이 200으로 저장된 뒤 Tika의 내용 불일치를 확인하는 **탐지 경계**이지 PUT 거부 증거가 아니다. 이는 U1 `complete`/제품 MIME 바이트 검증의 통과 근거로 사용할 수 없다. Java direct self-check의 `requestStatus`는 GET/PUT만 직접 전송하고 CORS OPTIONS는 별도 helper로 검사하지만, 현재 manifest도 GET/PUT만 포함하므로 이번 결과에는 영향이 없다.

## 가정과 권고

root가 제공한 명령 출력·CUA 관측·고유 bucket 조회를 실행 증거로 채택하되, QA가 같은 시험을 재실행했다고 가장하지 않는다. 현재 LocalStack은 `S3_*` signature flags만 확정된 상태이고 IAM 403을 보장하지 않는다고 가정한다. 따라서 권고는 U0를 BLOCKED로 유지하고, 정식 지원·허가된 IAM enforcement가 가능한 환경에서만 fresh resource로 한 번 재검증하는 것이다.

## 이점과 가장 강한 반론

고유 loopback 자원, 고정된 SDK 2.49.6/Tika 3.3.2, raw File와 자동 길이, checksum·조건부 PUT·만료·replay·CORS negative를 실제로 분리해 확인한 점은 U1 이전의 업로드 primitive 위험을 크게 줄인다. 가장 강한 반론은 LocalStack의 IAM 기능 부재가 도구 한계이므로 서명 negative만으로도 충분하다는 것이다. 그러나 §12.3의 명시적 acceptance가 무서명 private GET 403이고, 실제 canonical URL GET이 200이므로 이 반론은 U0 PASS를 정당화하지 못한다.

## 위험 완화·필수 변경

1. U0와 private-image 경로를 **PASS/완료로 표시하지 않는다**. `S3_*` 설정값만으로 보안 통과를 주장하지 않는다.
2. 정식 지원·허가된 IAM authorization enforcement가 실제로 활성화된 LocalStack 설정(또는 동등한 승인된 로컬 S3 emulator)을 확보한 뒤, fresh `zeroverse-u0-` bucket에서 무서명 GET이 403인지 재검증한다. 가입·구매·임의 구버전 pin·만료 우회·보안 완화는 하지 않는다.
3. 재검증 시 signed signature flags와 실제 negative HTTP 출력, 고유 자원/기존 데이터 보존, 비밀·signed URL 비출력을 함께 남긴다. 같은 key를 재실행하지 않는다.
4. MIME spoof 탐지 결과를 제품 complete/UPLOAD validation으로 승격하지 않는다. 현재 U0 결과에서 raw PUT의 선언 type 불일치가 거부됐다고 해석하지 않는다.

## 실패 시나리오

현재 상태에서 canonical S3 URL을 아는 임의 클라이언트가 서명 없이 객체를 읽을 수 있다. 이후 60초 signed GET을 도입해도 이 권한 우회는 해결되지 않는다. MIME spoof를 complete 거부로 오인하면 선언 type과 실제 바이트가 다른 파일이 READY/BOUND될 수 있다. 따라서 U0는 부분 증거만 확보했으며 M4 전체와 U1은 계속 차단 상태다.

## 2026-09-09 12:56 KST — U0 LocalStack IAM 라이선스 진단 후속

### 범위와 사실

사용자 후속 승인(비용·구매 없이 지원 여부 확인, 식별된 U0 bucket 2개/객체 16개를 초기화·재생성할 수 있는 범위)을 반영하되, 진단 결과 뒤 root는 초기화·재생성·설정 변경·재시험을 하지 않고 자원을 보존했다. 이번 후속은 제공된 read-only 출력과 공식 자료의 대조만 수행했으며 새 Docker/브라우저/버킷 실험은 하지 않았다.

- `GET /_aws/iam/config`가 `404 NoSuchBucket`인 사실만으로 기능 미지원이라고 단정하지 않는다. 반면 설치된 `localstack_pro_core-2026.8.1.dist-info/entry_points.txt`에는 `iam-enforcement -> localstack.pro.core.services.iam.plugins:IamEnforcementPlugin`이 있어 플러그인 코드는 설치되어 있음을 확인했다.
- 기존 정상 오프라인 license를 메모리에서 검증한 root 진단(`get_licensed_environment()._try_activate_offline(require_valid_credentials())`, `ProductEntitlements(...).has_entitlement`, exit 0; 온라인 activate/request-new-license 호출·파일/라이선스 변경 없음)은 `cached_license_valid=true`, `iam_basic_allowed=true (localstack.aws.provider/iam:default)`, **`iam_enforcement_allowed=false (localstack.platform.plugin/iam-enforcement)`**를 반환했다. 비밀값·license 원문·고객 식별자는 출력하지 않았다.
- 공식 [LocalStack Plans](https://docs.localstack.cloud/aws/licensing/)는 IAM Policy Enforcement를 Hobby에서 미제공, Base/Ultimate 이상에서 제공하는 기능으로 표기한다. 공식 [IAM Policy Enforcement](https://docs.localstack.cloud/aws/developer-tools/security-testing/iam-policy-enforcement/)도 이 기능은 Base/Ultimate 대상이며 `ENFORCE_IAM=1`이 필요하고 기본값은 비활성화되어 API가 인증 없이 접근된다고 명시한다.

### 독립 판단과 갱신 결론

위 사실을 함께 보면 현재 환경의 정확한 차단 원인은 “플러그인 미설치”가 아니라 **현재 cached license에서 IAM enforcement entitlement가 false인 것**이다. IAM 기본 API entitlement가 true라는 사실은 policy enforcement entitlement가 true라는 뜻이 아니다. 이는 앞선 unsigned GET 200 실측과 일치하며, signature flags(`S3_SKIP_SIGNATURE_VALIDATION=0`, `S3_VALIDATE_SIGNATURES=1`)만으로 private 403을 만들 수 없다는 판단을 강화한다. 다만 QA는 이 메모리 진단을 직접 재실행하지 않았으므로 라이선스 원문·계정 tier 자체를 독립 확정한 것은 아니다.

**판정 유지: BLOCKED (U0 PASS 아님), 확신도 99/100, 위험 HIGH/기밀성 CRITICAL.** 구매·새 license 발급·우회 플래그·임의 구버전·`ENFORCE_IAM=1` 재기동을 근거 없이 수행하지 않는다. U0를 재개하려면 IAM enforcement entitlement가 실제 허가된 환경에서 활성화되고, fresh `zeroverse-u0-` 자원으로 무서명 private GET 403을 재현하는 증거가 필요하다. 그 전까지 기존 두 bucket/16 objects와 LocalStack 환경을 보존하고 §16 회의에서만 후속 결정을 다룬다.

## 2026-09-23 — SeaweedFS U0-ALT 최종 독립 QA

### 범위·증거의 출처

회의 §17의 비용 없는 대체 검증 승인만 검토했다. 제품 API·DB·U1·실제 AWS·구매·Git은 범위 밖이며 QA가 SeaweedFS를 설치하거나 서버/브라우저를 실행하지 않았다. 아래 실행 결과는 root가 별도 컨텍스트에서 수행한 결과를 읽기 전용으로 대조한 것이다. 브라우저 결과는 [root 관측 요약](../build/u0-seaweedfs-browser-evidence.md), Java 결과는 [core self-check 로그](../build/u0-seaweedfs-core-self-check.log)를 근거로 삼고, 소스/harness는 현재 파일을 다시 읽었다.

- 공식 native SeaweedFS `4.47` Windows ZIP의 SHA-256은 `8809359079e62fcd60574ff661449160899622c52072f3f569d346669079efe9`; `weed version`은 `4.47 c507336... windows amd64`다. [공식 4.47 S3 구현](https://github.com/seaweedfs/seaweedfs/blob/4.47/weed/s3api/s3api_server.go?plain=1)은 해당 release의 API 표면을 기준으로 보며, 현재 문서/최신 branch의 기능을 4.47에 소급하지 않았다.
- S3는 `127.0.0.1:14568`, harness는 `127.0.0.1:14567`에만 묶였고 root가 8개 listener 모두 loopback임을 확인했다. `s3.json`에는 anonymous identity 없이 합성 identity 1개가 있고 `Admin/Read/Write/List/Tagging`을 허용한다. 이는 인증 on/anonymous deny 실험에는 충분하지만 least-privilege/IAM 정책 수용 증거는 아니다.
- 현재 검증 입력 해시는 Java `EA8D0D21161159638683D5CD7871F391578641F743AD9D12F45572DE98D75F6D`, HTML `B98C583B82C49F87EB08C30550624EADADF58DDE1F8F6CB86DF07EA014442EDE`다. harness는 SeaweedFS `HTTP 501` setup만 capability line으로 기록하고, VERIFY의 PAB/OwnershipControls 결과를 false로 남기며 전체 분모 28과 exit 1을 유지한다. browser 허용 origin도 `14566`·`14568` 두 개로 고정되고 expected status 완화나 wildcard CORS는 없다.
- core self-check는 `19/19 CASE PASS`, `27/28 VERIFY PASS`, `exit 1`이다. unsigned private GET은 실제 `403`, signed GET은 `200/403/403`, OwnershipControls는 `BUCKET_OWNER_ENFORCED`, exact/wrong origin preflight는 `200/403`, checksum/HEAD/ranged GET/Tika/negative object absence는 통과했다. 당시 로그는 HTTP-detail-only 소스 변경 전이라 PAB VERIFY 상세가 일반 `S3Exception`으로 남았고, 최신 소스는 동일한 false 결과를 `CAPABILITY ... PublicAccessBlock UNSUPPORTED HTTP501`로 명시한다. 이는 진단 보강이지 acceptance 완화가 아니다.
- root의 fresh browser 실행은 UI self-check를 PASS로 완료했으나 `19/19 cases · 18 PASS · verify 27/28 PASS`였다. 18개는 실제 S3 HTTP 응답이며 unsigned private GET도 `403`이다. 유일한 browser CASE 실패 `over-5mb-signed-at-max`는 `fetch TypeError/CORS`, HTTP status 미노출이다. `/verify` 직접 조회의 유일한 VERIFY 실패는 `PublicAccessBlock UNSUPPORTED HTTP501`이다. 객체 부재만으로 browser의 초과 크기 HTTP `403`을 대체하지 않는다. TypeError 원인은 현재 미확정이므로 PASS로 승격하지 않는다.
- 첫 browser 실행의 free-volume 고갈/500과 setup PAB 501은 실패 이력으로 보존됐다. 이후 `volume.max`만 4→16으로 늘려 fresh browser를 실행했으며 인증·기대 status·검사 기준은 바꾸지 않았다. 검증 종료 후 harness/SeaweedFS listener는 0이고 data는 보존됐다.

### acceptance matrix

| 위협·계약 | SeaweedFS 실측 | 판정 | AWS 보장으로 해석 가능한가 |
|---|---|---|---|
| anonymous/private canonical GET | Java `403`; browser `403` | PASS (SeaweedFS identity 경로) | 아니오. AWS IAM/Bucket Policy semantics 별도 |
| 정상/변조/만료 signed GET·PUT | core 정상·변조·만료 `200/403`; browser도 status 노출 케이스 통과 | PASS (provider-neutral primitive) | 아니오. SigV4/S3 호환 동작을 AWS 동등성으로 확대하지 않음 |
| 크기·빈 입력·MIME·checksum·byte tamper | core 경계/변조/부재/Tika 통과 | PASS (Java core) | 아니오. 제품 complete/MIME 정책·AWS 저장 의미 별도 |
| browser raw File signed PUT, 초과 5 MiB status | 정상 File PUT은 통과; 초과 케이스는 TypeError/CORS로 status 미확인 | **FAIL / 미해결** | 아니오. 재현 원인과 실제 `400/403`을 확인해야 함 |
| replay/conditional overwrite | `200 → 412` 통과 | PASS (SeaweedFS) | 아니오. AWS conditional semantics 별도 gate |
| exact/wrong-origin CORS | `200/403` 통과 | PASS (configured emulator) | 아니오. AWS CORS rule evaluation 별도 |
| BucketOwnerEnforced | `BUCKET_OWNER_ENFORCED` 통과 | PASS (SeaweedFS endpoint) | 아니오. AWS ownership semantics 동등성 아님 |
| Public Access Block 4 flags | SeaweedFS 4.47 setup/read가 `HTTP501` | **FAIL / UNSUPPORTED** | 아니오. 실제 AWS 또는 지원 emulator gate 필수 |
| least-privilege IAM, anonymous 정책, deny precedence | broad synthetic identity만 사용; 미검증 | **미실행** | 실제 AWS/허가된 IAM enforcement gate |
| TLS/외부 네트워크·durability/lifecycle·STS/SSE/control-plane hardening | loopback data-plane만 실행. STS signing-key 오류와 unauthenticated filer gRPC 경고가 로그에 남음 | **범위 밖 잔여 위험** | 실제 운영/AWS gate |
| 제품 U1 vertical slice | API/DB/FE 연결 미실행 | **미실행** | U1은 실제 AWS가 아닌 제품 도메인·FE gate |

### 최종 판정·최소 권고

**최종 판정: BLOCKED (U0 전체 PASS 아님), 확신도 99/100, 위험 HIGH/기밀성 CRITICAL.** SeaweedFS 4.47은 비용·계정 없이 LocalStack의 핵심 데이터-plane 보안 공백(무서명 private GET 200)을 `403`으로 보완하는 유효한 **부분 대체**다. 그러나 PAB API가 4.47에서 지원되지 않아 회의 §17의 “미지원은 명시하고 전체 PASS를 만들지 않는다” 조건을 충족할 수 없고, browser 초과 크기 status도 미확인이다. 따라서 이를 AWS acceptance나 U0 완료로 보고할 수 없다.

최소 권고는 대체 provider를 더 늘리지 않고 SeaweedFS 4.47을 `U0-ALT/core`로 고정하는 것이다. 현재 결과를 다음처럼 분리해 사용한다.

1. **허용 가능한 local core gate:** `19/19 CASE`의 Java 결과, unsigned private GET `403`, signed auth negative, checksum/conditional/CORS/Tika/object absence를 SeaweedFS provider 결과로 기록한다.
2. **필수 FAIL을 유지:** PAB `HTTP501`을 false/분모 28/exit 1로 유지하고, browser `over-5mb-signed-at-max` TypeError/CORS를 재현 원인과 실제 HTTP status가 확인될 때까지 FAIL로 둔다.
3. **잔여 AWS gate:** 실제 AWS 또는 PAB·IAM policy enforcement를 정식 지원하는 승인 환경에서 PAB 4 flags, least-privilege/deny precedence, CORS·SigV4·checksum·conditional·size boundary를 별도로 확인한다. 무료 대체가 이 gate를 면제하지 않는다.
4. **잔여 U1 gate:** U1은 제품 도메인/FE 최소 vertical slice(실제 AWS가 아님)이며 업로드 endpoint/DB binding/권한·상태·cleanup·브라우저 제품 연결을 별도로 검증한다.

SeaweedFS의 broad synthetic identity, STS signing-key/SSE 및 filer gRPC 경고를 제품/운영 보안 통과로 해석하지 않는다. 새 proxy, 임의 vendor patch, 기준 완화, 다른 emulator 추가 설치는 이번 판정에 필요하지 않으며 승인 범위에도 없다.

## 2026-09-23 — 잔여 두 문제 독립 재검토 및 XHR 대조

root의 후속 fresh harness/browser 실측과 진단 증거를 읽기 전용으로 대조했다. QA는 서버·harness·브라우저를 실행하지 않았고, 제품 코드·타 역할 소유 파일·provider·Git을 변경하지 않았다. 근거는 [root 진단 증거](../build/u0-seaweedfs-diagnostic-evidence.md), 공식 SeaweedFS 4.47 소스, Go `net/http` 공식 소스다.

- **PAB 원인 정정:** SeaweedFS 4.47은 route 자체가 없는 것이 아니다. 공식 `s3api_server.go` L882–885가 `?publicAccessBlock`의 GET/PUT/DELETE 세 route를 등록하지만, 공식 `s3api_bucket_policy_handlers.go` L424–435의 세 handler가 각각 `ErrNotImplemented`를 반환한다. 따라서 관측 `HTTP501`은 route 부재가 아니라 release의 기능 stub/미구현 경계다. OwnershipControls route(L904–910)와 그 PASS는 PAB PASS로 대체할 수 없다. [route source](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_server.go) · [handler source](https://raw.githubusercontent.com/seaweedfs/seaweedfs/4.47/weed/s3api/s3api_bucket_policy_handlers.go)
- **PAB 판정:** 네 flag의 저장·조회·effective deny semantics가 구현·실측되지 않았으므로 `PAB UNSUPPORTED HTTP501`은 계속 **FAIL**, VERIFY는 27/28·exit 1이다. route가 있다는 사실이나 metadata-only 응답을 AWS Public Access Block 보장으로 해석하지 않는다. 네 독립 설정과 account/bucket/access-point 적용을 요구하는 [AWS 공식 PAB 의미](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html#access-control-block-public-access-options)는 실제 AWS 또는 이를 정식 지원하는 승인 환경에서 별도 gate로 남긴다.
- **XHR 대조:** fresh harness `81971`, loopback-only SeaweedFS `25292`, 동일 manifest의 동일 `over-5mb-signed-at-max` File 5,242,881 bytes와 기존 signed headers를 사용했다. presign freshness guard 통과 후 native XHR을 한 번 실행했으며, 결과는 `XHR status 0 · FAIL · event error · headers 미수신 · U0 판정 미변경`이다. Run U0 자체도 다시 `18/19 CASE`, `27/28 VERIFY`였다. fetch를 XHR로 바꿔도 응답 status를 읽지 못했으므로 API 교체 해결안은 기각한다.
- **브라우저 FAIL 경계:** raw HTTP에서는 같은 signed-length mismatch가 `403 SignatureDoesNotMatch`·정확한 ACAO·`Connection: close`였고, body 0 TCP 진단에도 즉시 같은 403이 나왔다. 이는 서버의 본문 소비 전 reject와 조기 연결 종료 가설을 강하게 지지한다. Go 공식 `net/http`의 큰 미소비 body drain/connection-close 규칙과도 맞지만, 브라우저 TCP reset/error code를 직접 확보하지 못했으므로 exact root cause를 확정하지 않는다. XHR의 `ACAO 미관측`은 CORS failure 증거가 아니다. ACAO/Connection은 CORS-safelisted/exposed response header가 아니므로 JS에서 읽히지 않을 수 있다. [Go source](https://go.dev/src/net/http/server.go) · [XHR standard](https://xhr.spec.whatwg.org/) · [Fetch CORS headers](https://fetch.spec.whatwg.org/)
- **수용 기준:** browser `fetch`가 실제 `Response`로 expected `400/403`을 반환하고 object absence까지 확인하기 전에는 oversized case를 PASS로 바꾸지 않는다. XHR status 0/error, raw HTTP 403, object absence를 browser HTTP status의 대체 증거로 쓰지 않는다. expected status·manifest·negative 정책·Content-Length signed contract를 완화하거나 fetch fallback을 추가할 근거가 없다.

### 갱신 판정

두 문제 모두 해결되지 않았다. **최종 QA 판정은 BLOCKED (U0 전체 PASS 아님)**을 유지한다. SeaweedFS는 unsigned private GET 403을 보완하는 `U0-ALT/core` 부분 gate일 뿐이며, PAB four-flag gate와 browser oversized readable reject는 미충족이다. 최소 권고는 추가 provider/proxy/vendor patch/비용 전환 없이 현재 결과를 부분 gate로 고정하는 것이다. 전체 U0를 열려면 PAB를 정식 지원하는 승인 환경의 fresh 4-flag/deny 검증과, 공식 지원 서버·프로토콜 또는 별도 승인된 vendor/환경 변경에서 browser `400/403` readable 결과를 새로 확보해야 한다. 제품 U1(도메인·FE vertical slice)과 실제 AWS gate는 서로 별도로 남긴다.

## 2026-10-03 13:47 KST — line 68 사실 표현 append-only 정정

- 기존 line 68의 `VERIFY의 PAB/OwnershipControls 결과를 false로 남기며` 표현은 폐기·정정한다. PAB와 OwnershipControls 모두 false로 읽힐 수 있어 line 69 및 최신 실측과 충돌한다. 기존 줄과 이력은 append-only 원칙에 따라 보존한다.
- 최신 사실은 **PAB=false (PublicAccessBlock HTTP501 FAIL), OwnershipControls=true (`BUCKET_OWNER_ENFORCED` PASS), VERIFY 27/28 PASS·1 FAIL, exit 1**이다. SeaweedFS Java core의 유일한 VERIFY 실패는 PAB이며, OwnershipControls는 통과했다. fresh browser의 `18/19 CASE`, `27/28 VERIFY`와 전체 U0 **BLOCKED** 판정은 변경되지 않는다.
