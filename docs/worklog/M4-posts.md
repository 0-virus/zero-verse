# M4 — 게시글 CRUD · 에디터 · 이미지

## [계획]

### 2026-09-08 KST — project-lead M4 준비 재개

- 사용자 `$project-lead m4`에 따라 이전 M3 종료 경계를 넘어 M4 준비를 시작한다. 리더 `/root`가 brief·PRD 상세·요구사항·디자인·실제 Git/소스를 대조했다.
- 기준은 로컬 `feature/M3-categories` `822b4ce`, 원격/PR #9 `08239e0`; PR은 OPEN/Draft이며 dev 미머지다. M3 검증·독립 QA APPROVE는 완료됐지만 기존 기록 11개 공개 게시 승인은 미결이다. 이를 m4 지시로 승인받은 것으로 간주하지 않는다.
- 현재는 **문서 준비·독립 심의만 진행**. M4 브랜치는 M3 실제 dev merge 후 만들며 제품 구현은 관련 승인 후 배정한다. 기존 M3 미커밋 변경은 보존한다.
- 근거: REQUIREMENTS FR-POST-01~08·FR-UPLOAD-01~04·NFR-02~09, PRD §3~5·§7·§9.3·§10~13, 디자인 §8.2~8.4, ADR-0005.
- 안건/선택/검증은 [M4 기획 심의](../governance/meetings/M4-20260908-posts.md) §4가 v1 정본이다. 미확정 제안을 실행 명세로 취급하지 않는다.
- 승인 후 순서(권고): A 글·접근제어·DB → B 에디터·상세 → C LocalStack 업로드·종합 인수. BE는 src/Gradle, FE는 frontend, QA는 qa, PM은 PRD/PM 문서, 리더는 REQUIREMENTS/governance/worklog/JOURNAL/README. 계약이 독립인 작업만 병렬화한다.
- 실제 AWS 값 확보 전 LocalStack 사용은 기존 PRD §13.1의 확정 범위다. 외부 공개 게시·AWS 인프라 변경·새 보안 계약은 별도 승인 경계를 유지한다.

## [개발 기록]

제품 개발 미착수. 리더가 준비 문서만 작성했다.

## [이슈·결정]

- 2026-09-08: [M4 회의](../governance/meetings/M4-20260908-posts.md) `REVIEWING`. DB/API/보안/새 의존성/디자인 충돌로 독립 3인 심의 필요. Q1~Q5는 모두 권고(미확정).
- M3 공개 게시 승인·머지와 M4 계약 승인은 서로 다른 조건이다. 둘 중 하나를 다른 하나의 승인으로 대신하지 않는다.

## [리뷰]

독립 기획 검토 예정. 제품 QA/리더 최종 승인은 미실행.

## [머지]

미실행. M4 브랜치·PR도 아직 없다.

## [리뷰] — 2026-09-08 10:53 KST 독립 기획 심의 종합

- Product/Delivery는 APPROVE_WITH_CHANGES·HIGH(확신도88/91), Architecture는 A 운영 방식만 조건부 권고하고 신규 계약 전체는 BLOCKED(92)로 보고했다. 리더가 실제 FR-BLOG-02/FR-POST-04/PRD API 목록을 확인해 slug-list 생략 권고를 철회했다. 기존 두 목록 경로를 유지하는 것이 정본이다.
- 최종 회의 상태는 [BLOCKED](../governance/meetings/M4-20260908-posts.md). A 단계별 진행은 승인되지 않았고 이미지 접근 정책·조회수 ledger·정확한 콘텐츠/API/업로드 보장 계약은 미결이다. 독립 결과와 가장 강한 반론·필수 보완을 회의 §5~6에 기록했다.
- 사용자에게 비공개 S3+권한 확인 후 만료 URL vs 기존 공개 URL 위험 수용의 선택 요청을 남겼다. 선택은 계약 보완의 입력이며 미제출 기본 선택은 승인이 아니다. 기존 M3 게시 승인은 재요청·재시도하지 않고 별도 대기로 유지한다.
- 구현·새 의존성·DB·분기·commit/push/merge는 미실행. 준비 문서 링크 확인과 `git diff --check` exit0; source/Gradle/FE diff 없음. M4 제품 테스트 통과나 QA 완료를 주장하지 않는다.

## [계획]·[이슈·결정] — 2026-09-09 09:09 KST 선행 머지·비공개 이미지 방향 승인

- 사용자 `그렇게 해줘`는 M3 기록11개/검증 요약 공개 및 dev 머지와 M4 비공개 S3+글 열람권한에 따른 이미지 제공 방향의 세부 계약 보완을 승인했다. 세부 계약·제품 구현 일괄 승인은 아니다.
- M3 PR #9 실제 MERGED/merge `59badfe42d539a33091a387b9ee6119d838190b8`/09:08:47 KST를 확인해 선행 gate를 해소했다. 같은 origin/dev에서 `feature/M4-posts`를 생성하고 기존 모든 문서 변경을 보존했다. 제품/DB/의존성은 변경하지 않았다.
- 현재 작업은 회의 §11에 새 이미지 계약 v2를 준비하고, v1과 달라진 계약만 독립 심의하는 것이다. 권한 있는 조회 후 짧은 서명 GET, 영구 저장 참조, 프로필/대표/본문 연결, 크기·내용·재사용·정리 및 만료 UX를 구체화한다. 새 M4 준비 문서를 M3 공개 승인에 자동 포함하지 않는다.

## [계획]·[리뷰] — 2026-09-09 09:29 KST 이미지 v2 심의

- root 작성 [회의 §11](../governance/meetings/M4-20260908-posts.md)의 v2는 canonical/display 분리, 60초 bearer URL 잔여 위험, complete/read batch API, signed PUT/type/checksum 검증, image_uploads 보조 표·최초 연결/정리 및 U0→U1→U2 gate를 정리했다. 아직 미승인이며 기존 Q1/Q2/Q3/Q5 전체 계약을 대체하지 않는다.
- BE 초기 무스키마 현재참조 검색은 thumbnail 교체 후 과거 binding을 잃는 반례 때문에 영구 no-rebind 해법으로 채택하지 않았다. FE 실제 profile→form→PUT 및 Hero 소비와 늦은 응답을 함께 검토했다. 이 기술 입력 역할과 최종 심의자는 분리한다.
- 새 독립 Product/Architecture/Delivery 3인에게 동일 v2와 근거를 배포했다. 상호 연락·의견 공유·수정 금지. 제품/테스트/의존성/DB·S3 실측은 미실행, 문서 공백 검사 exit0이다. 실제 기술 검증 U0도 상세 승인 전 실행하지 않는다.

## [리뷰]·[이슈·결정] — 2026-09-09 09:52 KST 이미지 v2 심의 완료·U0 승인 요청

- Product/Architecture/Delivery 모두 APPROVE_WITH_CHANGES·HIGH(확신도88/88/90). A 조건부 권고이며 B 프록시로 자동 확장하지 않는다. 작성자와 분리된 세 결과를 모두 받은 뒤 리더가 SecurityConfig/JWT filter/apiClient/PostImage unique 및 기존 프로필 소비 근거를 직접 대조했다. 세부 결과·강한 반론·실패 경로·보완안은 [회의 §12](../governance/meetings/M4-20260908-posts.md)에 기록했다.
- M4 전체/U1은 BLOCKED 유지. U0만 USER_DECISION_REQUIRED이며 AWS SDK/Tika 검증 harness·로컬 private S3/브라우저 서명·MIME/크기/재사용 negative를 요청 범위로 제한한다. 정확한 의존성/이미지 버전은 설치 전 확인·고정한다. 제품 endpoint/DB/FE 및 실제 AWS·구매·M4 공개 게시 승인이 아니다.
- LocalStack auth token·사용 조건이 별도 실행 gate다. 알려진 세 env scope에 token 없음·읽기 전용 Docker 조회의 관련 이미지/컨테이너 없음은 확인했지만 계정 여부는 미확정이다. 사용자에게 계정 가능 여부만 요청했으며 비밀값을 대화로 받지 않는다. U0 미실행·미통과다.
- 현재 `feature/M4-posts`/HEAD `59badfe42d539a33091a387b9ee6119d838190b8`, M3 실제 dev 머지 완료, M4 PR/commit/push 없음. 과거 본문의 브랜치 미생성·M3 미머지 문구는 당시 이력이며 이 최신 기록이 현재 상태다. 제품 변경이 없어 통과한 M3 테스트를 반복하지 않는다.

## [개발 기록]·[이슈·결정] — 2026-09-09 11:39 KST LocalStack 기동만 승인·완료

- 구현자 root. 사용자 `응 그렇게 해줘`로 승인한 loopback 14566 로컬 기동 오류 해결만 수행했다. [회의 §13](../governance/meetings/M4-20260908-posts.md)에 원인·승인 경계·실측을 기록했다. root 소유 Compose/README/AGENTS에 고정 이미지·단일 포트·시작/종료·비밀값/영속성 주의사항을 남겼다. ponytail 원칙에 따라 기존 Docker와 다운로드 이미지를 재사용했다.
- Compose 문법/기동 exit0, healthy, S3 running·라이선스 활성화, 실제 publish 14566 단일 loopback, 읽기 전용 버킷 수 조회 0/exit0. 기존 DB·OS 설정·제품 코드·실제 AWS·버킷/object는 변경하지 않았다. U0 harness/의존성 설치·보안 검증·새 Git commit/push는 미실행이다.
- 독립 `/root/localstack_startup_review`에 설정·일회성 로컬 helper·README의 읽기 전용 검토를 배정했다. 로컬 환경 gate만 해소됐고 U0는 별도 승인 대기·미통과, 전체 M4/U1은 BLOCKED 유지한다.
- [리뷰] 독립 QA는 위 기동 설정 범위만 PASS 판정했다. PowerShell parser 오류 0·문서 diff check exit0을 별도 확인했으며 컨테이너 변경/U0 실험은 하지 않았다. helper의 기존 프로세스 토큰 우선 사용과 Docker 네트워크 내부 접근은 비차단 주의로 남겼다. 리더 실측과 모순이 없으며 추가 credential hardening/네트워크 설계로 범위를 확장하지 않는다.

## [계획]·[이슈·결정] — 2026-09-09 12:00 KST U0 승인·배정

- 사용자 `승인`을 [회의 §14](../governance/meetings/M4-20260908-posts.md)에 좁은 U0 범위로 반영했다. 전체 M4/U1은 BLOCKED 유지한다. HEAD `59badfe`·기존 미커밋 산출물 보존, 제품 코드/DB 불변 기준에서 시작한다.
- `/root/m4_u0_backend`: `gradle/u0/**` standalone SDK/Tika 검증 도구, `/root/m4_u0_frontend`: `frontend/u0/**` 실제 File PUT 페이지, `/root/m4_u0_pm`: PRD·PM 준비 상태만 담당한다. root는 실행 환경·공식 버전 근거·실측 결과를 대조하고 별도 QA에 최종 검토를 배정한다. 아직 U0 통과/완료로 기록하지 않는다.

## [개발 기록]·[리뷰]·[이슈·결정] — 2026-09-09 12:40 KST U0 실측·보안 gate 차단

- 실제 구현자는 `/root/m4_u0_backend`(독립 Gradle/Java harness)와 `/root/m4_u0_frontend`(단일 정적 File PUT 페이지)다. PM은 승인·실측 상태를 동기화했다. root는 빌드/자체 실행과 fresh bucket 실제 브라우저 검증을 직접 수행했고, 구현에 참여하지 않은 기존 `/root/m4_image_v2_product_review`를 재사용해 독립 QA를 받았다. source와 output을 직접 대조한 결과 **U0 BLOCKED, 전체 M4/U1 미승인 유지**다.
- 최종 Java 파일 SHA-256 `832ACAC45874DF15BB3765E71861120C2C0C2CC9D85290353FC4CFCBCCC85E81`, HTML `14067244C7E801108FA281DBD1AAB7B7DB5E16C71580E6A33A607FC786D5000F`. 검증 전 common Origin 검사·required URL 변환·negative PUT/CORS와 WebP padding 오류를 원 소유자가 수정했다. ponytail 원칙에 따라 기존 wrapper·JDK HttpServer·native File/atob를 재사용하고 제품 앱/추가 FE 패키지를 만들지 않았다.
- 명령과 19개 요청/28개 검사의 경계는 [회의 §15](../governance/meetings/M4-20260908-posts.md)에 기록했다. root compile은 cache 권한 실패 후 동일 명령 승인 재실행 exit0, fixture 오류 수정 후 자체 검증은 **18/19 CASE·27/28 VERIFY, exit1**. 실제 브라우저 self-check PASS 및 File PUT도 **18/19·27/28**, TypeError/CORS 없음. 유일 실패는 **무서명 private GET HTTP200(expected403)**이며 signature·CORS·크기·checksum·replay·Tika primitive 통과로 이를 상쇄하지 않는다.
- 독립 [U0 QA](../../qa/M4-u0-review.md)는 동일 실패를 근거로 BLOCKED를 판정했다. MIME spoof는 PUT200 후 탐지한 것이므로 제품 complete 거부로 주장하지 않는다. FE method finding은 root가 실제 fetch 구현을 대조해 QA에 사실 정정을 요청했고, 현재 manifest에 영향 없는 Java 자체검사 GET/PUT 범위로 정정됐다.
- FE 구현자가 실행한 lint/build는 exit0, 제품 전체 테스트는 270/273 passed·3 failed였다. 이 실행만으로 U0 인과나 기존 결함을 단정하지 않는다. 제품 코드 변경이 없으므로 root는 M3 전체 테스트를 반복하거나 이전 검증 결과를 소급 폐기하지 않았다. 1440px 제품 시각 QA·U1 API/DB 테스트는 수행하지 않았다.
- 고유 U0 버킷 2개/각 8객체는 보존했다. root의 임시 harness만 종료해 14567 listener 0을 확인했고 LocalStack S3는 running이다. README에 독립 실행·한 프로세스 한 번 Run·현재 실패·개인 데이터 업로드 금지를 추가했다. 재생성·데이터 초기화·구매·실제 AWS·제품 변경·commit/push/PR는 하지 않았다.
- 재개 조건: 현재 라이선스에서 공식 IAM enforcement를 지원하는지 확인하고, 식별된 합성 테스트 데이터 초기화를 포함한 LocalStack 재생성/재검증 방식에 대한 사용자 선택. 권고는 비용/가입/구매 없이 지원 범위만 사용하고 지원 불가 시 중단하는 것이다. 같은 실패 시험이나 동일 승인 요청을 변화 없이 반복하지 않는다.

## [리뷰]·[이슈·결정] — 2026-09-09 13:02 KST IAM 기능 지원 확인·재생성 보류

- 사용자 `응 진행해`로 비용 없는 지원 확인 및 지원 시 두 U0 버킷/16개 합성 객체 초기화·재검증이 승인됐다. root 사전 오프라인 진단(12:55:48 KST, exit0)은 유효한 캐시 라이선스·기본 IAM 허용 true, 정확한 `localstack.platform.plugin/iam-enforcement` 허용 false를 확인했다. [회의 §16](../governance/meetings/M4-20260908-posts.md)에 승인·방법·공식 근거를 기록했다.
- 정상 라이선스 검증을 사용했으며 온라인 활성화·구매·비밀 출력·검사 우회는 없었다. entitlement 제한 때문에 컨테이너·Compose·기존 합성 자원을 보존하고 재생성/재시험하지 않았다. 제품·U0 도구·DB·실제 AWS·Git 게시도 불변이다. ponytail 원칙에 따라 문제를 해소하지 못할 설정/코드를 추가하지 않았다.
- 독립 `/root/m4_image_v2_product_review`의 후속 QA BLOCKED와 PM 동기화 산출물을 root가 직접 확인했다. U0 미통과/전체 M4·U1 미승인 유지. 기존 초기화 승인은 해소됐고, 새 결정은 무료 대체 검증 전략 또는 정식 지원 기능 확보 방향이다. 동일 진단/시험/승인 질문은 변화 없이 반복하지 않는다.
- 마감 읽기 전용 확인: `git diff --check` exit0, 제품 경로 diff 없음, Java/HTML/독립 Gradle 2파일의 SHA-256이 이전 실측본과 일치한다. health의 S3 running·14567 listener0을 확인했다. 기존 heartbeat 실제 update ACTIVE로 최신 기능 미지원·새 방향 대기를 반영했다.

## [이슈·결정] — 2026-09-09 13:55 KST 사용자 정지

- 사용자 `일단 중지`로 실행 상태를 **사용자 정지**로 전환했다. heartbeat `zeroverse-m3` 실제 PAUSED, 하위 배정 모두 completed를 확인했다. U0 BLOCKED와 기존 산출물·LocalStack 데이터는 보존하며 대안 설치/전환은 미실행이다. 명시 재개 전 새 배정·시험·Git 변경·자동 재개를 하지 않는다.

## [계획]·[리뷰]·[이슈·결정] — 2026-09-23 KST 무료 U0 대체안 조사

- 사용자 `재개한다. 비용 없이 U0 검증을 대체할 방법을 찾아 보고한다.`로 읽기 전용 조사를 재개했다. backend/frontend/qa/pm 네 역할이 서로의 결론을 공유하지 않고 독립 검토했으며, root가 현재 Git·U0 18/19 CASE·27/28 VERIFY·LocalStack IAM entitlement 미지원 상태와 공식 자료를 교차 확인했다. 설치·전환·재시험·제품/DB/Git 전송은 하지 않았다.
- **1순위 권고는 SeaweedFS 4.47.0을 U0 전용 로컬 검증기로 고정해 기존 AWS SDK/Tika harness를 재사용하는 최소안**이다. Apache-2.0이고 단일 `weed mini` 실행, S3/IAM/CORS/checksum/presigned URL 지원을 공식 문서가 명시한다. 반드시 access/secret identity를 설정해 인증을 켜고, 무자격 실행의 allow-all 모드는 금지한다. 제품 저장소나 운영 AWS 대체로 채택하지 않는다.
- 검증 계약은 공급자 중립 핵심 보안 항목(무서명 GET 403, 서명 GET/PUT, 변조·만료·재사용, CORS, checksum, 크기·MIME)을 전부 재실측한다. LocalStack에서 이미 확인한 AWS 전용 Public Access Block/Ownership Controls와 SeaweedFS의 IAM을 동등하다고 주장하지 않으며, provider-specific 항목은 결과에 분리 표기한다. 실제 AWS smoke(U1)도 면제하지 않는다.
- RustFS 1.0.1은 Public Access Block·presigned GET/PUT·IAM/policy·CORS 근거가 있는 2순위다. 다만 공식 호환표에서 Ownership Controls가 planned라 1순위 실패 때만 검토한다. MinIO는 공식 저장소가 archived/source-only, Garage는 ACL/policy 호환 부족, AWS Free Tier는 신규 계정·기간/크레딧 조건 때문에 `비용 0` 보장이 없어 제외했다. 자체 프록시는 저장소 인증이 아니라 프록시만 시험하므로 제외했다.
- 상태는 **대안 권고 완료, 실행은 사용자 승인 대기**다. SeaweedFS 자체를 아직 통과로 보지 않으며, 고정 release/digest와 전체 U0 결과가 있어야 판정한다. 기존 U0와 전체 M4/U1은 BLOCKED 유지한다.

## [계획]·[이슈·결정] — 2026-09-23 KST U0-ALT 실행 승인

- 사용자 `승인. 내가 할 일을 알려줘.`는 직전 SeaweedFS U0-ALT 설치·재시험 제안의 승인이다. 추가 계정/결제/사용자 설정 없이 리더가 실행한다. native 공식 release tag는 `4.47`이며 이미지 태그 `4.47.0`과 구분한다. [공식 Windows ZIP](https://github.com/seaweedfs/seaweedfs/releases/download/4.47/windows_amd64.zip)의 GitHub API SHA-256 `8809359079e62fcd60574ff661449160899622c52072f3f569d346669079efe9`와 다운로드 파일 일치를 확인했다. `weed version`은 `4.47 c5073360007d28385a33426a42ac3e4ec504c5a3 windows amd64`다.
- 기존 LocalStack을 보존하기 위해 SeaweedFS S3는 loopback `14568`, 검증 페이지는 기존 `14567`을 사용한다. backend는 고정 profile 선택만, frontend는 정확한 두 S3 origin 허용 및 카피만 수정한다. core 보안 검증의 기대값은 완화하지 않는다. root가 환경/실측과 worklog/JOURNAL/governance를, PM이 승인 정본을, 별도 QA가 최종 판정을 맡는다.
- 실행 상태는 **진행**, U0-ALT 아직 미통과다. 제품 구현·DB·실제 AWS·유료 서비스·Git 게시를 이 승인으로 확장하지 않는다.

### 실행 중 확인·정정

- `mini -admin.ui=false`도 관리 gRPC를 `::33646`에 열어 root가 해당 프로세스를 종료했다. 같은 공식 바이너리의 `server -master -volume -filer -s3`와 명시 loopback bind로 전환한 뒤 8개 listener 모두 `127.0.0.1`임을 확인했다. 합성 데이터는 보존했다.
- 첫 자체검증은 `putPublicAccessBlock` HTTP501로 CASE 실행 전 exit1이었다(`build/u0-seaweedfs-self-check.log`). SeaweedFS profile에서 AWS 전용 setup501만 명시 기록 후 핵심 검사를 이어가도록 원 소유자에 보완 요청했다. 관련 VERIFY는 false·분모28·전체exit1을 유지하므로 미지원이 PASS로 바뀌지 않는다.
- 앞선 조사 보고의 `실제 AWS smoke(U1)`/`실제 AWS U1` 표현을 정정한다. 회의 §11.5/12의 U1은 제품 도메인·FE 연결의 최소 vertical slice이며 실제 AWS 검증과 별개다. PM이 PRD §9.6/13.1에도 정확한 구분을 반영했다.

## [개발 기록]·[리뷰] — 2026-09-23 KST SeaweedFS 실측 완료·전체 U0 차단 유지

- backend가 두 고정 provider 선택 및 SeaweedFS AWS 전용 setup501의 명시 기록을 구현했다. GET 검증도 실제 HTTP 오류를 표시한다. frontend는 정확한 두 origin 허용과 설명/self-check만 변경했다. core19CASE/28VERIFY 기대값·실패 판정·SDK/Tika 버전은 유지했다. root가 실제 파일/출력 및 브라우저를 직접 대조했다.
- 공식 ZIP SHA 검증 후 바이너리 SHA-256은 `082A8A9754CBFF8351C83F0B6271F5247FC09BB2BFCCBB0CA5128F6C91858D6E`. 최종 Java SHA는 `EA8D0D21161159638683D5CD7871F391578641F743AD9D12F45572DE98D75F6D`, HTML SHA는 `B98C583B82C49F87EB08C30550624EADADF58DDE1F8F6CB86DF07EA014442EDE`다. 마지막 Java 변경은 오류 detail만이며 root의 fresh browser 프로세스에서 재compile됐다.
- Java `run --args="--seaweedfs --self-check"`: **19/19 CASE,27/28 VERIFY,exit1**(`build/u0-seaweedfs-core-self-check.log`). 유일 VERIFY 실패는 PublicAccessBlock HTTP501이다. OwnershipControls 설정/조회는 실제 PASS이며, 앞선 가능성 추정과 구분한다. 이 설정 왕복은 AWS policy 동등성 증명이 아니다.
- 실제 CUA 브라우저에서 self-check PASS 후 Run U0를 한 번 실행했다: **18/19 CASE,27/28 VERIFY**. 무서명 private GET403, 정상/불량/만료GET200/403/403, 정상4MIME·5MiB200, 빈파일/만료/타입/checksum·byte negative 및 replay412를 확인했다. 유일 CASE 실패는 `over-5mb-signed-at-max`의 `fetch TypeError/CORS`이며 HTTP status가 브라우저에 노출되지 않았다. 해당 객체 부재 VERIFY는 PASS지만 이것으로 HTTP403이라고 추정하거나 CASE를 통과로 바꾸지 않는다. CORS 정책/연결 종료 중 정확 원인은 미확정이다. [root 관측 요약](../../build/u0-seaweedfs-browser-evidence.md)은 로컬 ignored 증거이며 QA 직접 브라우저 조작으로 주장하지 않는다.
- 브라우저 준비 첫 시도는 `volume.max=4` 소진으로 preseedPUT500이었다(`build/u0-seaweedfs-browser-server.log`). 같은 data를 보존하고 최대 볼륨 수16으로 재시작한 뒤 위 fresh browser를 실행했다(`build/u0-seaweedfs-browser-v2-server.log`). 8개 listener 모두 loopback을 재확인했다. STS 서명키 및 로컬 관리 plane 경고는 별도이며 broad synthetic identity는 최소권한 증거가 아니다.
- 판정: 승인된 설치·재시험은 완료했으나 **전체 U0 BLOCKED**. 이전 LocalStack의 익명GET200 문제는 이 provider에서403으로 개선됐고, 무료 완전 대체 통과는 아직 증명하지 못했다. PAB 미지원과 브라우저 초과파일 HTTP 관측 실패를 남긴다. U1은 제품 최소연결 단계로 별도 미승인, 실제 AWS도 미실행이다.
- root가 임시 harness와 이번 SeaweedFS 프로세스를 종료하고 `14567/14568 listener0`, data 존재True를 확인했다. 새 합성버킷4개/객체0·8·0·8을 보존했으며 기존 LocalStack·DB·제품 소스·Git 게시는 변경하지 않았다. 사용자에게 요구할 계정·키·결제·수동설정은 없다. 독립 `/root/u0_alt_qa` 최종 기록은 `qa/M4-u0-review.md`에 남긴다.
- 독립 QA의 최종 BLOCKED 보고와 네 역할의 상태·기록을 모두 수령했고, root가 실제 문서·핵심 소스·실측 출력과 일치함을 확인했다. 최종 `git diff --check` exit0, 제품 경로 diff 없음. 승인된 설치·재시험 결과 보고는 완료했으며, 동일 실패의 자동 반복 없이 후속 방향을 기다린다.

### 2026-09-23 KST — U0 잔여 두 문제 해결 재개 (진행 중)

- 사용자 `두 문제 해결을 먼저 수행해줄래?`로 PAB501 및 browser oversized TypeError 원인 조사·최소 수정·재검증을 재개했다. 회의 §19 및 JOURNAL에 범위를 기록했다. backend는 공식 PAB 구현/지원 경로, frontend는 전송 오류, root는 실제 loopback runtime/CUA를 맡는다. 기존 실패 기준과 합성 데이터는 보존한다.
- 동일 native4.47 설정으로 재기동한 root PID26068의 LISTENING8개는 모두127.0.0.1이다. fresh browser는 여전히 CASE18/19·VERIFY27/28이며, dev error/warn 로그는 빈 배열이다.
- 같은 oversized manifest를 메모리에서만 사용하는 Origin 포함 HTTP 진단은 실제 body5242881에 HTTP403 SignatureDoesNotMatch·exactACAO·Connection:close를 반환했다. TCP headers-only 요청에서도 body0 상태에 같은403을 받았다. 큰 upload의 body를 읽기 전 signature reject가 발생함을 입증하나, 이것은 browser status403 또는 TCP reset 직접 관측을 의미하지 않는다.
- root 상세 관측은 ignored `build/u0-seaweedfs-diagnostic-evidence.md`, 서버 로그는 `build/u0-seaweedfs-4.47/diagnostic-stderr.log`다. Go net/http의256KiB unread-body connection-close 경로를 원인 후보로 비교 중이다. U0 BLOCKED 유지; 제품U1/실제AWS/비용/계정/검증완화/공개Git/기존데이터삭제 미실행.

### 2026-09-23 KST — 잔여 문제 진단 결과, 수정 완료 아님

- PAB는 공식4.47의 `s3api_server.go` L882–885에 route가 있으나 `s3api_bucket_policy_handlers.go` L424–435의 Get/Put/Delete가 모두 ErrNotImplemented를 반환한다. SDK/endpoint 설정 오류가 아니다. backend의 공식 최신 release 조사도4.47이며 설정·업그레이드로 해결할 지원 구현을 찾지 못했다. 실제 PAB 구현은 네 flag의 저장/조회뿐 아니라 ACL·bucket-policy 쓰기 거부와 읽기 권한 평가에 대한 enforcement가 필요하다. 자체 저장소 보안 구현 또는 검증 환경 변경은 별도 결정 범위다.
- frontend가 별도 native XHR 진단 버튼을 임시 추가했으며 root가 직접 실행했다. 새 manifest에서 기존 fetch는 CASE18/19·VERIFY27/28, 이어 같은 초과 File·signed headers로 XHR 전송은 **status0 / event error / headers 미수신**이었다. XHR 교체로 해결되지 않았다. ACAO/Connection의 JS 미관측은 expose-headers 제한과 구분한다. 서버 early reject/connection close는 실측했으나 browser TCP reset 오류코드는 직접 관측하지 못했다.
- root는 native binary/data를 유지한 채 가득 찬 volume.max16을24로 늘려 fresh manifest용 서버 PID25292를 띄웠고 8개 loopback listener를 확인했다. 새 합성 bucket2개는 각각8객체, 기존4개 포함 총6개 보존. `build/u0-seaweedfs-diagnostic-evidence.md`에 명령·관측·소스·버킷·해시를 기록했다. 일회성 XHR 코드는 증거 확보 후 frontend 소유자가 제거하고 기존19/28 harness를 보존한다.
- root가 harness81971/weed25292를 종료했다. 후속 조회에서14567/14568 LISTENING0, 두 weed PID26068/25292 없음, data존재True; CUA 임시 탭도 닫았다. 제품 경로·실제AWS·비용·계정·vendor patch/proxy·신규provider·기존데이터삭제·Git게시 변경 없음.
- **결론: 두 문제는 아직 해결되지 않았으며 전체U0 BLOCKED.** 원인 범위는 좁혔지만 현재 승인 환경/설정/클라이언트 수정으로 완료할 수 있는 해결책을 확보하지 못했다. 같은 실패 재시험이나 불완전 provider 교체를 반복하지 않는다.

- 최종 정리 검증: frontend가 일회성 진단 추가분을 역패치로 제거했다. 현재 HTML hash는 `FEBE910EA47F526AF901C86502EE774E51874E417FC51D914EC8353E23691DDE`이며 과거 B98C와 byte 일치는 확인하지 못했다. 추정 편집을 중단하고 root가 실제 페이지를 설치된 jsdom으로 실행하여 self-check PASS, Run U0 버튼1개·활성, XHR 버튼없음을 assert했다(`U0_CLEAN_PAGE_SELF_CHECK=PASS`, exit0). 함수 위치와 기존 fetch/19-case/28-verify 경계도 확인했다. 임시 XHR 실측 입력hash4E25와 현재clean파일을 구분하며 현재 파일을 과거 browser 실측의 byte동일 입력이라고 주장하지 않는다.
- backend/frontend/QA/PM 최종 보고를 수령하고 root가 실제 소유 문서·관측·소스를 대조했다. 독립 QA도 두 문제 미해결/BLOCKED다. 최종 `git diff --check` exit0, 제품 `src`/`frontend/src`/root build/settings/package diff없음,14567/14568 LISTENING0. 기존 heartbeat를 최신 실패 근거·총6bucket보존·후속결정대기로 갱신했으며 같은 시험이나 승인 요청을 반복하지 않는다.
