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

## [계획]·[이슈·결정] — 2026-10-03 14:22 KST 로컬 M4 완료 지시

- 사용자 원문 `$brief 로컬에서 검증할 수 있는 수준이면 되니까 localstack 등은 제쳐 두고 m4 완료까지 달려`. LocalStack/SeaweedFS/AWS 호환성 검증을 로컬 M4 제품 구현의 선행 gate에서 제외한다. 기존 U0 실패를 PASS로 변경하지 않는다. M4까지가 이번 실행 범위이며 M5 이후는 포함하지 않는다.
- root brief 기준 `feature/M4-posts`/`97567d9`, 시작 시 clean, M3 dev merge `59badfe`. 제품 글 API/엔티티·에디터/상세 기능은 미구현이다. 회의 §20에 로컬 구현 계약 초안을 append하고 독립 제품/기술/전달 검토를 배정했다. PM은 최신 지시와 과거 상태의 충돌만 정합화한다.
- 실제 로컬 파일 업로드로 S3 전송 API를 대체하는 선택은 사용자에게 별도 질문했다. §20의 새 세부 계약은 검토·결정 전까지 초안이며 제품 소스/의존성/DB 변경은 아직 없다. 과거 승인 범위를 소급해 넓히지 않는다.
- 환경 확인: JDK21.0.10, Node22.21.1. Docker 엔진 미기동을 확인하고 기존 Docker Desktop을 시작했다. 다음은 독립 검토 필수 보완·로컬 이미지 방식 결정 반영, 역할별 상세 계획·구현·MySQL/브라우저 통합 검증이다.

### 2026-10-03 — 로컬 계약 독립 검토 결과·결정 대기

- 독립3인 보고 수령: Product/Delivery는 APPROVE_WITH_CHANGES·HIGH(91/93), Architecture는 전체 새 계약 BLOCKED(91). 리더가 기존 S3 요구 계약, 실제 미구현 upload, FE 최신 273개 통과 기록을 직접 대조했다. 회의 §20.5~20.6에 필수 보완·반론·실패 경로·권고를 기록했다.
- U0는 더 이상 선행 gate가 아니며 파일 기반 multipart/content API와 그 로컬 인수 계약만 사용자 결정이 필요하다. 최종 로컬 M4 검증과 S3/presigned 후속 이행을 분리한다. 새 제품 코드/DB/의존성은 아직 변경하지 않았다. Docker 엔진은 기동 후 `docker ps` exit0(실행 컨테이너 없음)으로 확인했다.
- 현재 실행 상태는 새 저장 방식 결정 대기다. 같은 U0 재시험·중복 승인 요청을 수행하지 않는다. 사용자 선택 후 §20 계약을 정본에 반영하고 BE/FE 구현 계획·독립 QA 순서로 재개한다.

## [계획] — 2026-10-03 로컬 M4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 저장소의 역할별 소유권·독립 QA·기존 기록 위치가 범용 스킬의 기본 경로와 Git 절차보다 우선한다.

**Goal:** 로컬 MySQL·Spring API·React 브라우저에서 글 작성부터 이미지 포함 발행·권한별 조회·수정·삭제까지 M4를 완성한다.

**Architecture:** 기존 도메인별 controller/service/repository와 공통 응답을 재사용한다. 콘텐츠는 검증된 TipTap JSON을 원본으로 하고 서버가 HTML을 생성·정화한다. 업로드 바이트는 Git 제외 로컬 폴더, 소유·연결 metadata와 24h 조회 ledger는 MySQL에 보관한다.

**Tech Stack:** 기존 Java21/Spring Boot3.5.15/MySQL8.4/Flyway, React19/TypeScript/Vite. 새 기능에는 TipTap3 동버전 확장, OWASP Java HTML Sanitizer, Tika core를 사용하며 설치 직전 공식 버전·호환성을 확인해 고정한다.

**Spec:** [M4 회의 §20.1~20.7](../governance/meetings/M4-20260908-posts.md), REQUIREMENTS FR-BLOG-02·FR-POST-01~08·FR-UPLOAD-01~04·NFR-02/04/06~09, PRD §4~7·§9.7·§10~12, 디자인 §8.2~8.4, ADR-0005.

**Decision:** 사용자 `로컬 파일 저장으로 M4 완성`으로 로컬 저장·업로드 API·권한/5MB 유지·S3 후속 분리는 승인됐다. 이 계획은 그 선택의 구현 순서와 상세 인수 기준이며, 제품 구현 시작 전 사용자 검토를 받는다.

### Global Constraints

- `feature/M4-posts`/기준 `97567d9`의 기존 checkout·미커밋 기록을 보존한다. 다른 역할의 파일을 되돌리지 않는다. V1/V2 수정·기존 데이터 삭제·LocalStack 재시험 없음.
- BE 소유: `src/**`, root Gradle, `.claude/team/backend/**`. FE 소유: `frontend/**`, `.claude/team/frontend/**`. QA: `qa/**`, 자기 역할 기록. PM: PRD/PM 문서와 자기 역할 기록. root: REQUIREMENTS/governance/README/.gitignore/루트 지침/JOURNAL/이 worklog.
- `com.zeroverse`, DB snake_case, Java camelCase, JPA wrapper 타입, ApiResponse/PageResponse, method별 Security allowlist, 블로그 잠금 이후 활성 사용자·카테고리 재검사를 유지한다.
- `PUBLIC` 발행은 익명, `UNIVERSE`는 viewer→owner ACCEPTED, `PRIVATE`와 draft는 작성자만. 삭제된 post/blog/owner 제외. SUSPENDED owner의 공개 글은 남기되 쓰기는 금지한다.
- 실제 파일 `1..5,242,880` bytes, JPEG/PNG/WebP/GIF만. no-store/nosniff. 외부 URL 다운로드·S3 signer 모방·가짜 성공 금지.
- 데스크톱 `min-width:1440px`, `border-radius:0`, paper `#f6ead8`, ink `#2b1b3d`, accent `#e85d75`, hard shadow `#d8c7b0`. 에디터1060px·상세980px, 친구 라벨 유지.
- M5 관계 CRUD와 M6 좋아요/댓글은 구현하지 않는다. 관계 읽기 권한은 실제 DB fixture로 검증한다. S3 후속 미완료를 로컬 M4 결과와 별도 표시한다.

### Review Focus

1. 이미지 업로드 성공 뒤 글 저장 실패·재시작: 입력과 성공 업로드는 보존하고 미커밋 파일만 정리한다(Task3/5/7).
2. 로그아웃 또는 계정 변경 중 늦은 글·이미지 응답: 이전 비공개 내용·Blob이 새 세션에 나타나지 않는다(Task4/5/6).
3. 카테고리 삭제와 글 저장, 동일 태그 생성, 24h 동시 조회: DB 잠금·unique·원자적 증가로 유효한 상태만 남는다(Task1).
4. JSON/HTML/images 불일치와 악성 Markdown/링크: 저장·복원·출력 모두 안전하고 검증 실패 시 사용자 입력이 사라지지 않는다(Task2/3/5).
5. 발행→draft 또는 PRIVATE 변경 후 기존 이미지 주소/이전다음/목록: 모든 읽기 경로에 변경된 권한이 적용된다(Task1/3/6/7).

### 공유 HTTP 인터페이스

- 모든 경로는 `/api/v1`, JSON 응답은 기존 ApiResponse. 목록 data는 기존 PageResponse의 `items,page,size,totalElements,totalPages,hasNext,hasPrevious`.
- `POST /posts`: `{blogId,title,contentJson,contentHtml,categoryId,visibility,publish,thumbnailUrl,tagNames,images}`. `PUT /posts/{id}`는 blogId 없이 동일 전체 snapshot. nullable 생략→null, 배열 생략→빈 배열, title/contentJson/visibility/publish는 필수. draft title은 빈 문자열 가능.
- `PostImageInput={imageUrl:string,altText:string|null,displayOrder:number}`; `PUT /posts/{id}/images` 요청은 `{images:PostImageInput[]}`. 본문 JSON 이미지 집합과 일치해야 하므로 본문 제거는 Post PUT에서 함께 저장한다.
- `PostDetail={id,blogId,blogSlug,blogTitle,author:{id,nickname,profileImageUrl},category:{id,name},title,contentJson,contentHtml,thumbnailUrl,visibility,viewCount,publishedAt,createdAt,updatedAt,tags:string[],images:PostImageInput[],previous:{id,title,blogSlug}|null,next:{id,title,blogSlug}|null}`. 시간은 기존 ISO 형식, nullable 시간은 null.
- `PostSummary`는 PostDetail에서 contentJson/contentHtml/images/previous/next를 제외하고 `excerpt:string`을 추가한다. POST/PUT은 PostDetail, image PUT은 images 배열, DELETE는 기존 null 성공 envelope. 상세 조회만 조회수를 증가시킨다.
- `GET /blogs/{blogId}/posts`, `/blogs/slug/{urlSlug}/posts`: `page=0,size=20,sort=latest|popular,categoryId,tag,visibility,publish` optional. 기본 published만, owner의 publish=false만 draft. `GET /posts/drafts`는 인증·updatedAt/id desc. `GET /tags/{tagName}/posts`는 같은 공개 predicate와 page/size/sort.
- `GET /posts/{id}`는 PostDetail. previous/next는 같은 블로그에서 접근 가능한 발행 글의 publishedAt/id 순서이며 자기 자신은 제외한다.
- `POST /uploads`: multipart `file`,`purpose=PROFILE_IMAGE|POST_THUMBNAIL|POST_IMAGE`, data `{id:string,imageUrl:string,contentType:string,size:number,purpose:string}`. canonical imageUrl은 `/api/v1/uploads/{uuid}/content` 상대 경로, 조회는 bytes 응답이다. 로컬 API origin은 기존 apiClient 설정에서 해석하며 외부 origin에 Bearer를 전송하지 않는다.
- UPLOAD_003은 404(없거나 권한 없는 파일), UPLOAD_004는 400(purpose/바인딩 위반)으로 정본 등록 후 사용한다. 인증 미제공·잘못됨 AUTH_004, 만료 AUTH_002. 다운로드의 정상 binary만 JSON envelope 예외다.

### Task 1 — 글 영속성·CRUD·접근제어·조회수 (backend)

**Files:** 생성 `src/main/java/com/zeroverse/domain/post/{entity,repository,dto,service,controller}/`의 Post/Visibility/PostImage/PostViewRecord/PostDtos/PostRepository/PostService/PostAccessPolicy/PostController, `domain/tag/{entity,repository}/`의 Tag/PostTag/TagRepository; migration `src/main/resources/db/migration/V3__posts_local_uploads.sql`; 테스트 `src/test/java/com/zeroverse/domain/post/{PostServiceIntegrationTest,PostControllerTest,PostConcurrencyTest}.java`, `migration/PostMigrationTest.java`. 수정 BlogRepository, SecurityConfig, 테스트 DatabaseCleaner. 세부 파일은 도메인 경로에 AGENTS.md로 책임을 기록한다.

**Interfaces:** 기존 blog lock·UserRepository·CategoryRepository를 소비한다. `PostService.create(Long userId, CreatePostRequest):PostDetail`, `update(Long userId,Long postId,UpdatePostRequest):PostDetail`, `delete(Long userId,Long postId):void`, `get(Long postId,Long viewerId,String anonymousKey):PostDetail`와 공유 HTTP 계약을 제공한다. 콘텐츠 검증은 Task2, 이미지 연결은 Task3에서 연결하며 미완성 상태를 전체 통과로 표시하지 않는다.

- [ ] 실패 테스트: draft→publish→publish 유지→draft의 publishedAt; 타인쓰기403; 익명/owner/정방향·역방향 관계의 PUBLIC/UNIVERSE/PRIVATE/draft 조회; 삭제된 post/blog/owner404; 두 목록 경로·태그·drafts 인증을 assert한다.
- [ ] 위 테스트를 `.\gradlew.bat --gradle-user-home .gradle-home2 --no-daemon --max-workers=1 test --tests '*Post*Test'`로 실행해 기능 부재 실패를 확인한다.
- [ ] V3에 PostImage active unique, `(post_id,viewer_key)` unique ledger와 image_uploads metadata를 추가한다. V1 적용 데이터와 soft-delete 순서 이력 보존을 테스트한다. 태그 동시 생성은 MySQL 원자 upsert/재조회로 처리해 rollback-only 트랜잭션을 재사용하지 않는다.
- [ ] 권한 검증과 blog→user→post 잠금 순서로 CRUD/snapshot을 구현한다. Clock 주입·서버 secret HMAC IP+UA로 rolling24h 증가를 원자 처리하고 원문을 저장·로그하지 않는다. 48h 초과 ledger는 bounded 삭제하며 읽기 요청마다 전체 스캔하지 않는다.
- [ ] 동시10조회→증가1, 23:59:59재조회→0, 24h→1; category삭제/저장 경쟁→활성DEFAULT 또는 유효category; 동시tag1개; imageorder이력 보존을 실제 MySQL로 검증한다. API OpenAPI·정지 사용자 쓰기·이전다음 정보 누출 회귀도 통과시킨다.
- [ ] Task2/3 연결 뒤 관련 테스트 PASS·명령/건수/skip0를 역할 WORKLOG에 기록하고 독립 QA에 인계한다. 커밋은 리더 검토 후 명시 파일만 수행한다.

### Task 2 — JSON 원본·HTML 정화 (backend)

**Files:** 생성 `domain/post/service/PostContentService.java`, `src/test/java/com/zeroverse/domain/post/PostContentServiceTest.java`; 수정 build.gradle, PostService.

**Interfaces:** `PostContentService.validateAndRender(JsonNode contentJson,String untrustedHtml,boolean publish):ValidatedContent` → `ValidatedContent(JsonNode json,String html,String excerpt,Set<String> imageUrls)`; Task1/3가 소비한다.

- [ ] 테스트 `rejectsOversizedOrDeepDocument`, `rejectsUnsafeLinksAndNodes`, `roundTripsSupportedToolbarContent`, `ignoresConflictingClientHtml`에 각각 UTF8 1MiB/depth32/nodes10000, javascript/data/style/event, 모든 toolbar 노드, JSON만 공개 원본임을 assert하고 RED를 확인한다.
- [ ] 허용 TipTap 노드/mark/attrs만 검증·렌더하고 OWASP 정책으로 HTML을 정화한다. draft 빈 doc 허용, 발행은 trim1..200제목과 텍스트 또는 검증 이미지가 필요하다. 임의 sanitizer/Markdown parser는 작성하지 않는다.
- [ ] `... test --tests '*PostContentServiceTest'` PASS 후 JSON/HTML 크기경계·XSS·이미지 URL 집합 통합 테스트를 실행한다. StarterKit/표/이미지의 FE 실제 JSON fixture와 교차 확인한다.

### Task 3 — 로컬 업로드·권한 있는 이미지 읽기 (backend)

**Files:** 생성 `domain/upload/{entity,repository,dto,service,controller}/`의 ImageUpload/UploadPurpose/UploadDtos/ImageUploadRepository/UploadService/LocalImageStore/UploadController, `config/UploadProperties.java`; 테스트 `domain/upload/{UploadServiceTest,UploadControllerIntegrationTest,LocalImageStoreTest}.java`. Task1에서 생성한 image_uploads 스키마를 소비하며 이미 적용한 V3는 수정하지 않는다. 수정 UserSettingsService, PostService, ErrorCode, GlobalExceptionHandler, application.yml 및 local example. 루트 .gitignore/README/REQUIREMENTS는 리더가 수정한다.

**Interfaces:** `UploadService.upload(Long userId,MultipartFile file,UploadPurpose purpose):UploadResponse`; `read(UUID id,Long viewerId):ImageContent`; `bindPostImages(Long userId,Post post,Set<String> bodyUrls,String thumbnailUrl):void`; `bindProfileImage(Long userId,String imageUrl):void`. metadata와 현재 도메인 연결을 읽어 권한을 판단하며 파일 경로는 응답에 노출하지 않는다.

- [ ] RED: 정확5MiB/한바이트초과/빈파일/MIME위장/4종정상, 타인/purpose/최초binding 재사용, 미연결owneronly, 삭제·탈착·draft전환 후 제3자GET거부를 실제HTTP로 assert한다.
- [ ] `zeroverse.upload.directory`의 local 기본 `.local-data/uploads`를 사용한다. UUID/CREATE_NEW·containment·symlink 차단, bounded read+Tika 검증, 파일·DB 실패 시 이번 요청의 임시파일만 정리한다. 로컬 profile 외에 무의식적으로 파일 제공이 켜지지 않도록 구성 경계를 명시한다.
- [ ] JSON/images/thumbnail과 현재 프로필 연결을 검증하고 영구 최초 binding을 보존한다. 이미지 GET은 매번 DB권한·명시된토큰오류를 검증하고 no-store/nosniff를 반환한다. multipart예외도 공통 오류로 변환한다.
- [ ] `... test --tests '*Upload*Test' --tests '*LocalImageStoreTest'` PASS, 파일쓰기/DB실패·symlink·재시작영속성 테스트를 통과시킨다. S3 U0 harness에는 손대지 않는다.

### Task 4 — FE 계약·인증 이미지 기반 (frontend)

**Files:** 생성 `frontend/src/features/post/{types.ts,postApi.ts,postApi.test.ts}`, `features/upload/{uploadApi.ts,uploadApi.test.ts,ManagedImage.tsx,ManagedImage.test.tsx}`; 수정 `lib/apiClient.ts`, `test/apiClient.test.ts`, `components/ui/Avatar.tsx`, `PostCard.tsx`, 프로필 설정의 이미지 소비 지점.

**Interfaces:** `createPost/updatePost/getPost/listBlogPosts/listDrafts/listTagPosts/deletePost/updatePostImages`는 위 HTTP DTO를 그대로 사용한다. `uploadImage(file:File,purpose:UploadPurpose):Promise<UploadResponse>`, `ManagedImage`는 canonical src를 받아 origin검사→Bearer fetch→Blob 렌더링한다. 기존 apiClient의 401 single-flight·session generation 계약을 유지한다.

- [ ] RED: FormData에 JSON Content-Type을 강제로 붙이지 않음; 만료tokenrefresh1회; 외부URL에Bearer없음; 계정전환/언마운트후 늦은Blob 무효화·revoke; canonical을 POST하고 Blob을 저장하지 않음을 assert한다.
- [ ] 기존 JSON 호출과 분리된 multipart/binary 응답 처리를 최소 확장한다. UI공용이미지는 기존 외부 profile URL 읽기를 보존하되 새 관리 URL은 인증 경로를 사용한다.
- [ ] `npm.cmd test -- src/features/post/postApi.test.ts src/features/upload src/test/apiClient.test.ts` PASS. API/DTO 확정 전에는 Task5/6의 통합 완료를 선언하지 않는다.

### Task 5 — TipTap 작성·수정·draft·이미지 (frontend)

**Files:** 생성 `features/post/{PostEditor.tsx,EditorToolbar.tsx,DraftPicker.tsx,PostEditor.test.tsx}`; 수정 WritePage.tsx/EditPage.tsx/SettingsProfilePage.tsx/package.json/lock/styles/index.css. 디자인 원본은 읽기 전용이다.

**Interfaces:** PostEditor는 create/edit mode, initial PostDetail과 Task4 API를 소비하고 서버 savedAt만 표시한다. image node에는 canonical src를 저장하고 ManagedImage와 같은 인증표시 수명관리를 사용한다.

- [ ] RED: 실제 에디터 bold/italic/underline/strike/H1~3/quote/code/hr/list/link/table/image JSON 저장·복원, Markdown 붙여넣기, draft popup→edit, draft↔publish, 실패입력보존, 중복클릭차단을 assert한다.
- [ ] 호환되는 TipTap3 React/PM/StarterKit/Image/Table/Markdown을 같은 버전으로 고정한다. StarterKit의 Link/Underline 중복등록은 하지 않는다. 에디터1060px·하단두카드·thumbnail카드·최대5MB를 구현한다.
- [ ] 이미지업로드중발행차단, 실패재시도, category null→DEFAULT, tag정규화/10개 제한, 원래 글 slug와 요청id검증을 연결한다. 계정변경시 이전개인글·늦은저장응답이 새세션으로 가지 않게 한다.
- [ ] `npm.cmd test -- src/features/post/PostEditor.test.tsx` PASS, `npm.cmd run lint`, `npm.cmd run build` exit0. backend의 JSON검증 fixture와 실제 toolbar round-trip을 확인한다.

### Task 6 — 블로그 목록·상세·관리 동작 (frontend)

**Files:** 수정 BlogPage.tsx/PostDetailPage.tsx/SettingsPostsPage.tsx/components/layout/ScreenPanel.tsx/components/ui/Prose.tsx/PostCard.tsx; 생성 `frontend/src/test/postBehavior.test.tsx`.

**Interfaces:** Task4 list/get/delete, 기존 blog/hero/category context를 소비한다. category/tag/sort/page 필터는 URL query로 공유하고 권한 변경 시 이전 응답을 무효화한다.

- [ ] RED: 실제 pagination/filter/sort, 401/403/404, PRIVATE→PUBLIC 계정전환, slug불일치, owner수정/삭제후이동, previous/next없음, 실제공유URL, 목록이미지실패를 assert한다.
- [ ] 빈목록scaffold를 실제 PostSummary로 바꾸고 상세980px/정본Prose/조회수/읽기시간/태그/관리동작을 연결한다. 미구현M6 좋아요/댓글의 가짜숫자·성공버튼은 표시하지 않는다.
- [ ] `npm.cmd test -- src/test/postBehavior.test.tsx` 및 전체 `npm.cmd test`, lint/build PASS. 기존 AuthContext/설정/카테고리 회귀를 보존한다.

### Task 7 — 독립 통합 인수·문서·Git (qa/root/pm)

**Files:** QA 생성 `qa/M4-review.md`, `qa/m4-api-smoke.ps1`; 역할 STATE/WORKLOG, root README/.gitignore/AGENTS/CLAUDE/REQUIREMENTS/governance/이worklog/JOURNAL, PM PRD/준비문서. 루트·역할 지침에 새 도메인 위치와 실행법을 동기화한다.

- [ ] QA가 실제 diff/명세/원출력을 검토하고 BE/FE 단계별 발견사항을 소유자에게 전달한다. 작성자가 자기 산출물을 최종 승인하지 않는다.
- [ ] root가 전체 `gradlew ... test bootJar`, FE test/lint/build 출력과 JUnit 실패/skip0를 직접 확인한다. 실제 MySQL migration 및 합성 owner/viewer/reverse/unrelated 관계 fixture로 글·이미지 권한, 24h경계,두목록경로,삭제/탈착/재시작을 검증한다.
- [ ] 실제 브라우저1440px에서 로그인→글/표/본문이미지/대표이미지 작성→draft복원→발행→공개조회→친구/비공개차단→수정/삭제와 프로필 이미지 교체를 검증한다. 테스트 계정·합성이미지만 사용한다. 디자인 정본과 비교해 발견을 기록한다.
- [ ] README에 로컬 데이터 경로/백업/재시작/기동/종료/환경변수와 S3 미완료를 기록한다. `.local-data/`를 Git 제외한다. 사용자 파일·기존U0 데이터를 삭제하지 않는다.
- [ ] 모든 필수 발견 해결 후 독립 QA와 root가 로컬M4완료를 판정한다. 기록을 같은 버전에 맞추고 명시파일만 원자적커밋, `feature/M4-posts` push 및 `dev` base PR/리뷰/머지는 실제 권한·결과를 확인한 뒤 수행한다. GitHub 인증이 막히면 로컬완료와 원격절차미완료를 분리한다.

### 실행 순서·자가 검토

- backend Task1→2→3은 단일 backend 작성자로 공유 파일 충돌을 막는다. frontend Task4→5→6은 단일 frontend 작성자이며 HTTP 계약을 먼저 확인한다. 두 역할의 독립 파일 작업은 병렬화하고 합의된 DTO를 바꾸려면 root와 상대 역할에 먼저 알린다. QA는 읽기/인수계획을 병렬 수행한다.
- FR-BLOG-02와 FR-POST-01~08은 Task1/6, 업로드와 프로필 연결은 Task3/4/5, NFR 콘텐츠 보안은 Task2/4/5, 접근·DB·동시성은 Task1/3, 실제 디자인과 종단 인수는 Task7에 연결했다. Review Focus5개 모두 해당 테스트 단계에 포함했다.
- 이전 기록의 저장 방식 선택 대기는 해소됐다. 현재는 위 구현 계획 검토 대기이며 제품코드·DB·의존성은 아직 변경하지 않았다. 실행 방식은 프로젝트 규칙대로 backend/frontend 역할 분담과 독립 QA를 유지한다.
- 계획 자가 검토에서 Task3의 V3 재편집 가능 문구를 제거했다. image_uploads까지 Task1에서 정의하고 이후는 소비하므로 적용된 migration 불변 원칙을 유지한다. root의 편집분 재독 및 `git diff --check`는 exit0이며 PM 결정 동기화도 직접 확인했다.

## [개발 기록] — 2026-10-03 실행 승인·사전 대조

- 사용자 `yes`로 위 계획과 backend/frontend 병렬 구현·독립 QA가 승인됐다. 실행 상태 **진행**. 기준 HEAD `97567d9`, 기존 공유 `feature/M4-posts` checkout과 문서 변경을 유지한다. 계획의 단일 역할 작성자·기존 기록 위치를 따라 별도 worktree/SDD 로그 디렉터리를 만들지 않는다.
- `subagent-driven-development`의 구현/독립 검토/수정 루프를 사용하되 승인된 프로젝트 역할별 소유권과 실행계획이 범용 기본의 직렬 배정·새 보고파일보다 우선한다. 역할 보고는 기존 WORKLOG, 리더 진행표는 이 파일에 남긴다.

| 사전 대조 | 생산→소비 / 파일 관계 | 판정 |
| --- | --- | --- |
| Task1 | CRUD·predicate·ledger, 테스트와 구현 범위 | 일치; Task2/3 통합 전 전체 완료 아님 |
| Task2 | JSON→검증HTML/imageUrls | 일치; JSON이 원본 |
| Task3 | 파일/metadata→권한GET·binding | 일치; 적용V3 불변 |
| Task4 | HTTP계약→FE API/ManagedImage | 일치; binary 성공만 envelope 예외 |
| Task5 | 에디터→snapshot저장 | 일치; 실제JSON 교차검증 필요 |
| Task6 | summary/detail→목록/상세/관리 | 일치; M6 제외 |
| Task7 | 전체산출물→독립QA/실측/기록 | 일치; 저자와 검토자 분리 |
| Task1↔2↔3 | PostService/V3/이미지참조 | backend 단일 작성자로 직렬 통합 |
| Task1/3→4 | 공유HTTP DTO/경로/오류 | 계약을 동일하게 소비, 변경 시 리더 조정 |
| Task4→5/6 | API/인증이미지/타입 | frontend 단일 작성자, API 테스트 선행 |
| Task5↔6 | PostCard/Prose/공유스타일 | frontend 단일 작성자 |
| Task1~6→7 | 실제소스·테스트출력·브라우저 | QA 소유 제품변경 없음 |

- Task1~3: backend 구현 배정. Task4~6: frontend 구현 배정. Task7: 독립 QA 인수계획부터 배정. PM은 승인 정본 동기화, root는 공통지침·요구사항·runtime·검증·최종 기록을 맡는다. 제품 작성자의 자기승인·stub/skip·가짜 성공은 허용하지 않는다.

## [개발 기록] — 2026-10-03 15:46 KST 로컬 인수 준비·조기 리뷰

- root가 새 합성 DB `zeroverse-m4-smoke-20261003-v2`의 mysqladmin 응답을 확인했다. 기존 DB·파일은 변경하지 않았다. Git 제외 `build/m4-start-local-api.ps1`와 `build/m4-run-http-acceptance.ps1`을 준비해 비밀값을 메모리에만 유지하며 loopback API 기동·4계정·방향성 관계 fixture·QA HTTP 검사를 연결한다. 두 보조 스크립트는 PowerShell 구문 검사만 통과했으며 API 기동/인수 실행은 아직 하지 않았다.
- frontend Vite를 `127.0.0.1:5173`/API `127.0.0.1:8080`으로 시작하고 실제 Chrome에서 첫 화면 로딩을 확인했다. M4 화면의 완료·종단 PASS를 의미하지 않는다.
- QA의 `qa/M4-review.md`와 `qa/m4-api-smoke.ps1` 초안을 확인했다. root는 새로 생성하는 private post 1건의 soft delete 검증만 배정했으며 기존 fixture/파일 삭제는 하지 않는다.
- `/root/m4_qa_acceptance`에 생성된 post/tag/universe/V3 및 대응 테스트의 Task1/2 조기 독립 리뷰를 배정했다. backend가 계속 구현 중이므로 최종 PASS와 분리하고 검토 시점·잔여 변경을 추적한다. root도 본문 attrs/크기/표 셀 병합 의미와 인증 이미지의 이전 계정 노출 경계를 작성자에게 전달했다.

### Task3 소유권 분할 — 2026-10-03 15:50 KST

- `dispatching-parallel-agents`의 독립 파일 분리 원칙에 따라 root가 기존 backend의 쓰기를 잠시 중단하고 upload 경로가 AGENTS만 존재함을 직접 확인했다. `/root/m4_backend_upload`에 `src/main/java/com/zeroverse/domain/upload/**` 및 `src/test/java/com/zeroverse/domain/upload/**`를 독점 배정한 뒤 `/root/m4_backend_impl`의 Task1/2·통합 작업을 즉시 재개했다. 기능·계약·검증 범위는 변경하지 않는다.
- 기존 backend는 PostService/UserSettingsService·공통 설정/오류·Gradle 및 backend STATE/WORKLOG 단일 작성자를 유지한다. 새 구현자는 upload 결과를 원 작성자와 root에 인계하며 역할 기록 파일을 동시에 수정하지 않는다. 동일 Gradle build 출력은 두 구현자가 실행 순서를 조율한다. 최종 QA는 별도 컨텍스트다.

## [리뷰] — 2026-10-03 Task1/2 조기 독립 검토

- `/root/m4_qa_acceptance`가 작성한 `qa/M4-review.md`의 조기 리뷰를 root가 실제 읽었다. 구현 진행 중 스냅샷이므로 최종 판정은 아니며 익명 상세/draft 필터 오류, 이미지 displayOrder 검증, 48h ledger 정리, 접근 불가 20개 이후 인접 공개 글 탐색, HMAC 고정 기본값 및 동시성/migration 증거를 원 backend에 수정·재검증 요청했다.
- root는 `receiving-code-review` 원칙에 따라 정본과 기존 코드를 대조했다. 생성 성공 상태의 미명시 부분은 기존 AuthController/CategoryController 및 REQUIREMENTS 카테고리 생성 계약의 201 패턴으로 통일한다: 신규 POST `/posts`, POST `/uploads`는 201 envelope, PUT/DELETE는 200. frontend 요청 처리와 QA 기대는 동일 계약을 소비한다.
- `publish=false`는 특정 blog 목록에서 실제 Blog 소유자를 검사한다(익명 AUTH_004, 인증 비소유 POST_002). 전역 tag 목록은 인증 요청자 소유의 해당 tag draft만 반환하는 owner-only 접근 정책을 적용한다. 타인 draft를 열거나 M5 기능을 추가하는 변경이 아니다.
- 먼저 전달한 ContentService 크기/root/attrs/span 보완의 정적 반영은 확인했지만 정상 TipTap JSON·실제 HTTP 검증은 남아 있다. 테스트 실행 전 PASS 표시는 하지 않는다.

## [리뷰] — 2026-10-03 16:21 KST 첫 실제 기동

- root가 16:18 생성 JAR(SHA256 `A5595EB9D4673ADED5FF5A852030A790725D7CE89BA7C12EBD67838687C7FB55`)을 local 프로필·loopback8080·전용합성DB13308로 시작했다. 비밀값은 메모리로만 주입했다. 실행 로그는 Git 제외 `build/m4-api-20261003-161850.stdout.log`다.
- 실제 MySQL의 V1→V2→V3 migration 및 Hibernate schema validation은 성공했다. root의 별도 SQL readback은 `1/init/1`, `2/category active unique/1`, `3/posts local uploads/1`을 반환했다(exit0). 이 환경에 V3가 적용됐으므로 기존 V3 수정은 금지한다.
- API는 이후 `LocalImageStore`의 두 생성자에 주입 선택이 지정되지 않아 `NoSuchMethodException: LocalImageStore.<init>()`로 기동 실패했다. PID28068은 종료됐으며 HTTP 인수는 미실행이다. root가 원 upload 작성자에게 원인·로그·Spring context 회귀를 요청하고 통합 작성자에게 재빌드를 요청했다.
- Windows JAR 잠금과 재빌드 간섭을 피하도록 이후 root 기동 보조 도구는 새 고유 이름으로 복사한 빌드 산출물 snapshot을 실행한다. 원본 JAR·기존 runtime 파일·사용자 파일은 덮어쓰거나 삭제하지 않는다. 최종 인수는 최종 snapshot hash와 연결해 기록한다.

## [리뷰] — 2026-10-03 실제 HTTP·브라우저 중간 검증

- Windows 예약 포트 범위 8010–8109가 8080 바인딩을 막는 것을 확인했다. OS 예약을 변경하지 않고 전용 API를 `127.0.0.1:18080`으로 기동하고 Vite/README를 같은 포트로 맞췄다. 실행 snapshot SHA256은 `0E90FCBF51AEB1B4B91C81BBB519FCD944A35CB47D6E8916FC0CB1E8DBC6CEB5`이며 후속 소스 수정 전 버전이다. Swagger 경로와 정상 API 응답을 확인했으나 최종 버전 인수를 대체하지 않는다.
- HTTP 중간 검사에서 4개 MIME 업로드, 익명 401, 빈 파일 UPLOAD_002, MIME 불일치 UPLOAD_001, 정확히 5MiB 성공 및 1byte 초과 거부를 확인했다. PowerShell fixture의 byte 배열·빈 배열 바인딩과 raw displayOrder 기대 오류를 QA에 돌려보내 수정 중이며 전체 smoke PASS는 아직 아니다.
- 실제 Chrome/1440 viewport에서 신규 합성 계정·블로그 설정 → 제목/태그/굵은 본문/2x2표 작성 → 임시저장 → 임시저장 목록에서 재개 → 공개 발행을 확인했다. 브라우저 파일 업로드는 확장 프로그램의 파일 URL 접근 권한이 필요해 사용자 선택을 요청했고 임의로 권한을 확대하지 않았다. API 이미지 검증은 계속한다.
- 실제 발행 화면의 UTC 오프셋 누락에 따른 9시간 시각 차이를 backend에 수정 요청했다. 새 외부 프로필 URL 쓰기 거부·기존 값 유지 계약 및 FE 전체 Vitest 메모리 오류도 원 작성자 수정/검증 단계다. 테스트 skip이나 일부 성공을 전체 완료로 기록하지 않는다.
- 임시 Gradle cache 세 경로는 Git 제외만 추가하고 삭제하지 않았다. 기존 업로드 파일·합성 DB·사용자 데이터는 보존했다.

## [리뷰] — 2026-10-03 17:20 KST HTTP 통과·독립 전체 리뷰 진행

- root가 `3890657091DDDD7927DC53CBB4E14891125F568F51739B69F32793053723B36A` JAR을 별도 snapshot으로 실행했다(API18080, PID18916). 기존 owned PID24580만 명령행 identity 확인 후 종료했으며 DB/파일은 보존했다. Swagger readiness 200, 정상 기동을 확인했다. 해당 버전은 전체 리뷰 수정 전이므로 최종 인수와 구분한다.
- `build/m4-run-http-acceptance.ps1` → `qa/m4-api-smoke.ps1` 6차 실행 exit0: `M4 API smoke passed for the executed HTTP gates.` 공개 이미지 익명 조회·친구 방향성·private/draft·5MiB 경계·MIME·snapshot/dedupe/order·발행 전환·삭제 후 이미지 차단 등 script 범위가 통과했다. 이전 fixture 오류들은 제품 기대를 완화하지 않고 byte/empty/nullable/optional parameter와 유효 title 전환을 보정했다.
- post1 HTTP 시각은 `publishedAt=2026-10-03T07:50:23Z`, `createdAt=2026-10-03T07:48:03Z`, `updatedAt=2026-10-03T08:03:21Z`로 UTC offset이 명시된다. 실제 새 이미지 글의 Chrome 표시는 KST 17:19로 일치했다.
- 실제 PNG fixture(post23, 96×64, 395 bytes)를 API로 저장한 후 비로그인 Chrome에서 `complete=true`, `naturalWidth=96`, `naturalHeight=64`, managed Blob 렌더를 확인했다. 원본·익명 HTTP 응답·`.local-data/uploads/ac0be62f-ebc1-4d50-b20b-a5865570a904` 파일의 SHA256은 모두 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`이다. 응답은200/image/png/no-store. 이 fixture는 다음 재시작 보존 검증에 사용한다. 브라우저 파일 선택창 자체는 확장 권한 대기로 미실행이다.
- upload JUnit XML 3개는 root 직접 집계로20 tests/0failure/0error/0skip. root가 별도 readonly mount·network none·tmpfs Docker에서 실제 JAR `FEB988DF...`와 `LocalImageStoreSymlinkCheck`를 실행해 target/root/ancestor symlink 모두 read UPLOAD_003/write COMMON_500/outside marker unchanged 및 fixture cleanup을 확인했다(exit0). 호스트 파일은 삭제하지 않았다.
- FE 작성자가 전체28 files/302 tests PASS, lint/build exit0를 인계했다. root는 Vitest cache 28개 모두 failed=false와 변경 소스/기록을 직접 확인했다. OOM은 provider 없는 테스트의 unstable fallback callback 반복 effect였고 stable context/query dependency 및 실제 route fixture로 수정됐다. 기존 성공을 반복 실행하지 않는다.
- `/root/m4_whole_review`(독립 전체 리뷰)에 작업트리·미추적 제품 파일과 승인 계약을 인계했다. review package의 local dev ref가 M2 시점1690731임을 발견했으므로 실제 비교 기준은 `origin/dev` 및 merge-base인 명시 hash `59badfe42d539a33091a387b9ee6119d838190b8`로 정정했다. 공유 checkout의 branch/ref는 변경하지 않았다.
- 전체 리뷰 중 markdown paste의 기존 본문 교체, edit 화면 draft 선택 연결 누락, leaf JSON 검증·FE 방어, DB 페이징/overflow, 삭제된 부모 상세 오류를 검토 중이다. root는 새 미저장 합성 초안에서 markdown 버튼이 기존 본문을 공유 링크 하나로 교체함을 실제 재현했다. 사용자 저장 글은 변경하지 않았다. 최종 발견 목록을 원 소유자에게 배정하며 전체 M4 완료/커밋/푸시는 아직 아니다.

### 전체 리뷰 판정·수정 배정 — 2026-10-03

- 독립 검토자 `/root/m4_whole_review`는 Critical0, Important7, Minor1로 `Ready to merge: No`, spec 미충족/quality 보류를 보고했다. root는 아래 전체 목록을 backend/frontend 소유자별 한 묶음으로 전달했다. 사용자 역할 헌법에 따라 서로 다른 경로는 분리하며 generic skill의 단일 수정자 권고보다 단일 파일 소유권을 우선한다.

| ID | 발견·필수 회귀 | 수정 소유자 |
| --- | --- | --- |
| R1 | 일반/버튼 Markdown paste가 전체 문서를 교체. 선택 위치 삽입과 앞뒤 본문 보존 | frontend |
| R2 | leaf node의 숨은 비배열 content가 BE 검증 우회 후 FE map 오류. 재귀 schema 검증 및 방어 렌더 | backend + frontend(별도 파일) |
| R3 | 전체 글 로드 후 Java 페이징/large page overflow. SQL ACL·정렬·offset/limit과 page 단위 매핑, 큰 page 비500 | backend |
| R4 | edit 화면 draft 선택 callback 누락. edit/A→draftB 전환/복원 | frontend |
| R5 | DEFAULT가 첫 옵션이 아닐 때 null state와 표시 카테고리 불일치. 초기 표시/payload/응답 일치 | frontend |
| R6 | draft picker/관리 화면 page0 고정. 21개 이후 초안 접근 | frontend |
| R7 | ol start/type 및 표 span 출력 속성 유실. JSON/HTML/상세/편집 의미 보존 | backend + frontend(별도 파일) |
| R8 | 삭제 blog/owner 상세의 401/403 대신 POST_001/404 계약 유지 | backend |

- 마이그레이션 증거 지적은 리뷰 중 추가된 `PostV3MigrationTest.java`와 성공 로그를 검토자가 아직 반영하지 못한 부분이 있어 해당 새 파일만 후속 확인을 요청했다. 기존 V3 파일 변경은 금지한다.
- root는 large page에 새 임의 상한을 추가하기보다 long offset과 total 비교로 정상 빈 페이지를 돌려주는 계약 보존 방향을 전달했다. 최종 수정 diff·관련 회귀·전체 테스트·실제 최신 runtime 및 독립 재리뷰 전에는 완료/머지하지 않는다.

### 브라우저 파일 선택 검증 범위 — 2026-10-03 사용자 결정

- 사용자 응답: `브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해`.
- Chrome 확장의 파일 URL 접근 권한을 확대하지 않는다. 실제 브라우저 파일 선택/전송만 인수 범위에서 제외하고 multipart API·FE 파일 입력/업로드 자동 테스트로 검증한다. 이미지 권한·5MiB·실제 파일 저장·이미지 브라우저 표시·재시작 보존은 유지한다. 미실행 파일 선택을 브라우저 PASS로 표기하지 않는다.

### 수정 회귀 진행 — 2026-10-03 17:43 KST

- backend 첫 전체 실행은419 tests/1 failure였으며 기존 `SecurityAccessControlTest`가 M4 공개 상세 `/api/v1/posts/1`까지401로 기대한 낡은 fixture였다. 공개 상세 없음404와 draft/쓰기 보호를 분리해 보정하도록 원 작성자에게 배정했다. 전체 통과로 기록하지 않으며 다음 전체 결과의 JUnit XML은 별도 보존한다.
- R2/R7의 `PostContentService.java`와 대응 단위 테스트만 `/root/m4_backend_upload`에 잠시 단독 배정해 leaf content/unknown field 검증과 ol start/type 정화를 수정했다. root의 실제 HTTP RED는 malformed leaf201, ol 속성보존 false였다. 수정 인계 후 해당 두 파일 소유권을 원 backend에 반환했으며 최종 JAR GREEN 확인은 남아 있다.
- root Chrome 실측에서 R1 버튼 Markdown 및 일반 Ctrl+V가 기존 본문을 보존하고 새 H1/H2를 삽입했다. 새 합성 draft26을 저장한 뒤 edit1의 임시저장 선택창에서 draft26을 선택해 URL `/edit/26` 및 제목/본문 복원을 확인했다(R4 GREEN). R5 fresh write 초기 DEFAULT 표시는 수정 확인 대기다.
- `/root/m4_qa_closeout`에 기존 QA 조기 기록과 최신 증거의 정합성 갱신을 배정했다. 최종 whole-review 재검토와 최신 runtime 인수는 아직 pending이며 M4 완료/커밋/푸시를 선언하지 않는다. README와 AGENTS의 과거 U0 전용 Tika 설명은 승인된 제품 Tika/정화 의존성 예외와 구분했다.

### 최신 JAR 인수·재시작 보존 — 2026-10-03 17:57 KST

- root는 preflight bootJar 성공22s와 SHA256 `F11E4A183234630ADE346437A897990C6A7B55B44297C004F1A02493AEA9DA1F`를 확인한 뒤 owned PID18916의 정확한 runtime 경로를 대조하고 종료했다. 새 snapshot `build/m4-runtime-20261003-175458.jar`/PID19060은 같은 전용DB·업로드폴더·API18080에서 정상 기동했다. DB·파일·사용자 서비스는 삭제/종료하지 않았다.
- 최신 artifact의 HTTP smoke7차는 exit0, `M4 API smoke passed for the executed HTTP gates.`였다. 별도 실제 HTTP negative/정화 fixture는 `MALFORMED_LEAF_STATUS=400;CODE=VALIDATION_001`, `ORDERED_LIST_START_PRESERVED=True;TYPE_PRESERVED=True`, exit0으로 이전 RED를 해소했다. `page=2147483647&size=20`도 정상응답의 items0/total1로 overflow500이 사라졌다.
- 재시작 전에 만든 post23은 동일 image URL·발행시각을 유지했다. 기존 file·익명HTTP395bytes/image/png/200의 SHA256 모두 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`로 재시작 전 원본과 일치했다. Chrome에서도 비로그인 상태로 같은 post23 이미지 complete=true/natural96×64를 다시 확인했다.
- 새 post33은 orderedList start5/typea의 실제 Chrome marker가 `e.`이고 computed list-style-type이 lower-alpha였다. 이미지 natural96×64도 확인했다. viewport1440 검사 후 임시 override를 reset했다. 파일 선택/전송은 사용자 제외 결정대로 실행하지 않았다.
- root 직접 집계: FE 전체 보존 JSON28files/311passed/0failed·pending 및 R7 CSS 후속 영향 JSON2files/40passed/0failed·pending. 독립 `/root/m4_whole_review`는 FE R1/R2방어/R4/R5/R6/R7과 BE R2/R3/R7/R8 소스 지적을 모두 종료 가능으로 판정했다. 추가 Critical/Important 회귀 없음이며 전체 merge 판정은 BE 최종 suite 대기다.
- 실제 OpenAPI는 POST posts201 정상, POST uploads는200만 표기해 문서 불일치를 원 backend에 보완 요청했다(실제 HTTP201은 통과). 최종 docs 반영 artifact/OpenAPI와 전체BE XML·QA 승인은 아직 남아 있다.

### 최종 회귀·OpenAPI — 2026-10-03 18:15 KST

- backend 전체 명령의 로그 `build/m4-final-backend-full-20261003-1900.log`는 `BUILD SUCCESSFUL in 13m 49s`, exit0이다. root가 보존 archive `build/m4-final-junit-20261003-1915`를 직접 파싱해66 XML/426 tests/0failure/0error/0skip을 집계했다. PostConcurrency3, Content10, ServiceIntegration8, UploadService8, ProfileBinding6, V3Migration1, SecurityAccessControl24가 포함된다. init-script는 JVM별 독립 MySQL로 maxParallelForks2만 적용했고 테스트 제외/skip은 없다. 로그 파일명 숫자는 실제 종료시각이 아니며 실제 full 종료는18:12 KST다.
- upload/profile/V3 targeted는4m7s, 최종 bootJar는18s 모두 성공했다. 최종 SHA256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`를 root가 확인했다. F11E와 최종 JAR의136개 BOOT-INF/classes 파일 hash를 비교한 차이는 OpenAPI201 annotation을 추가한 UploadController.class 하나이고 삭제는 없다. symlink 검증 JAR과 F11E의 LocalImageStore.class도 동일했다.
- owned PID19060 identity 확인 후 최종 snapshot `build/m4-runtime-20261003-181423.jar`/PID4588로 교체했다. 실제 OpenAPI는 posts201/400/401/403, uploads201, contentGET 존재로 PASS다. 같은 기존 이미지 HTTP200/395bytes/SHA256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`/no-store/nosniff를 최종 artifact에서도 확인했다. DB/파일은 계속 보존한다.
- `/root/m4_whole_review`는 전체XML·로그·최종hash를 직접 확인했고 마지막 Swagger 조건까지 root가 인계했다. `/root/m4_qa_closeout`의 최종 독립 인수 기록과 PM 상태 동기화 이후 명시112파일 목록(소스/테스트/역할기록/승인문서)을 stage한다. 개인설정·로컬데이터·build로그는 ignore 확인, 아직 commit/push/merge 전이다.

## [리뷰] — 2026-10-03 최종 로컬 M4 승인

- 구현: Codex backend `/root/m4_backend_impl`·upload 분담 `/root/m4_backend_upload`, frontend `/root/m4_frontend_impl`. 최종 검토: 별도 컨텍스트 `/root/m4_whole_review`·독립 QA `/root/m4_qa_closeout`, root의 실제 runtime/Chrome 검증. 작성자 자기승인으로 대체하지 않았다.
- `/root/m4_whole_review` 최종 `Ready to merge: Yes`, spec/quality 승인 가능, 잔여 Critical/Important/Minor0. R1~R8 및 R7 CSS 후속을 모두 종료했다. QA의 `qa/M4-review.md` 18:18 최종 판정은 **로컬 M4 PASS**다.
- Task1~3은 실제 MySQL·426개 무실패/무skip 테스트·최종 JAR·HTTP와 파일 보존으로, Task4~6은 FE311개 및 R7영향40개·lint/build·실제Chrome으로, Task7은 독립리뷰/QA와 위 실측 묶음으로 인수했다. 최초 계획의 체크박스는 계획 당시 이력이며 이 최종 판정이 실행 결과다.
- 브라우저 파일 선택/전송만 사용자 승인 예외로 제외했다. S3/U0/AWS 정책 동등성, M5 이후, 운영 배포·미연결 파일 자동정리는 완료로 보고하지 않는다. 기존 합성DB/파일은 보존한다. 개발 검증 로그/테스트 archive는 로컬 build 아래이며 Git에는 코드·테스트·검증 기록만 포함한다.
- 다음 Git 단계는 승인된 feature/M4-posts→dev PR 통합이다. 실제 커밋/푸시/머지 결과는 실행 뒤 [머지]에 별도 기록한다.

### 재개 점검 — 2026-10-04 14:58 KST

- 사용자 `계속`에 따라 brief로 Git·네 역할 STATE·최종 QA·PRD/결정 기록을 재대조했다. HEAD97567d9와 원격feature97567d9/dev59badfe는 그대로이며 M4 PR은 아직 없다. 최종 제품 검증 이후 src/frontend/build.gradle 파일의 추가 수정은 없고 JAR hashD2B5, 보존 BE426/0failure·error·skip, FE311 및 후속40 PASS를 다시 읽어 확인했다. 소스 변경 없이 동일 전체 테스트를 반복하지 않는다.
- QA18:18 로컬M4 PASS는 완료되어 있다. PM STATE의 QA 문서화 대기, backend STATE 하단의 이미 해소된 runtime/QA 대기, frontend 기록의 Chrome orderedList `type=A` 표기는 오래되거나 부정확한 상태 문구다. 실제 Chrome fixture는 `type=a`/lower-alpha/marker e.이고 type=A는 자동 회귀다. 최신 판정은 이 worklog와 QA 최종 항목을 따른다.
- PM 마지막 후속 실행이 역할 모델 사용량 제한으로 종료됐다. 제한 우회/추가 제품 수정은 하지 않으며 리더 소유 Git·검증 기록을 계속한다. 역할별 STATE/PRD의 상태 문구만 리더가 직접 동기화하는 권한은 사용자에게 별도로 요청했다. 제품 인수 PASS 및 기존 독립 리뷰 승인은 변하지 않는다.

## [머지] — 2026-10-04 로컬 M4 완료

- 리더가 검토된112파일만 명시 목록으로 stage하고 staged diff/check 및 일반 비밀 패턴 검사를 수행했다(금지 데이터/개인설정0, 비밀 패턴0). 구현 커밋은 `4404f1e1fd518602d2c777bdeec11cde7ec2ca27` (`feat(M4): complete posts and local image uploads`)이다.
- 최초 push/PR 요청은 원격 목적지 명시 확인을 요구한 자동 승인 검사에서 실행 전 거부됐다. 리더는 우회하지 않고 공개 저장소 `0-virus/zero-verse` 전송·PR·머지를 질문했고, 사용자 **`해당 저장소 푸시·PR·머지 승인`** 이후 실행했다. 로컬 이미지·개인설정·build 검증 로그는 전송하지 않았다.
- 구현 커밋 push 성공 후 [PR #10](https://github.com/0-virus/zero-verse/pull/10)을 `feature/M4-posts → dev`로 생성했다. head4404f1e/base59badfe, MERGEABLE/CLEAN 및 대기 체크 없음(원격 CI 등록 없음)을 확인하고 정확한 head hash 조건으로 merge했다. 독립 검토·로컬 전체 테스트를 원격 CI 실행으로 오인하지 않는다.
- GitHub `MERGED`, merge commit **`c34ff4e979bd6efffc3a2aea28c2be307ccac3b5`**, 시각 **2026-10-04 15:07:52 KST**를 실제 조회했다. merge tree와 검증된 feature tree의 `git diff --quiet`가 exit0이다. 깨끗한 로컬 dev도 ancestor 확인 후 fast-forward했다. 브랜치 삭제/force push/데이터 삭제는 하지 않았다.
- 사용자 **`리더가 상태 문구만 갱신`** 승인에 따라 네 역할 STATE와 로컬 PRD의 완료 상태를 리더가 직접 동기화했다. 기존 역할 WORKLOG는 편집하지 않고 이 리더 기록으로 변경 주체·사유를 남긴다. backend의 stale runtime 대기, PM의 stale QA 대기, frontend의 Chrome type=A 오표기를 상태 문구에서 바로잡았다(type=A는 자동 회귀, Chrome은 type=a).
- **최종 판정: 승인된 로컬 M4 구현·인수·PR 통합 완료.** BE426/0failure·error·skip, FE311 및 R7영향40, lint/build, API·실제 이미지·재시작 보존·독립QA/리뷰 근거는 위 최종 [리뷰]를 따른다. 이후 변경은 상태 문서뿐이며 제품 재수정/중복 전체 테스트는 없다. S3/U0·브라우저 파일 선택 예외·운영 배포·M5 이후는 별도 경계를 유지한다.
