# M9 — 관리자

> 2026-11-11 KST: 사용자 `m9 완료를 목표로 착수해줘`에 따라 계획·독립 심의에 착수했다. 실행 상태: 진행. 구체 HIGH 계약은 심의 후 사용자 승인 대상으로 제시한다. M10·배포·사용자 DB 적용은 포함하지 않는다.

## [계획]

- 기준: `main=dev=848d272`; `feature/M9-admin` 생성. 기존 `.gitignore` 사용자 변경은 보존한다.
- 근거: REQUIREMENTS §6.13 FR-ADMIN-01~05/NFR-01·04·09, PRD §5.13·§7 관리자 화면·§9-D/K/U·§10, 디자인 Pages 관리자 2화면, 헌법과 governance 승인 절차.
- Goal: 관리자 사용자 목록·상세·역할/상태 변경·soft delete, 현재 DB 기준 관리자 권한, AdminRoute와 디자인 일치 화면을 구현하고 독립 QA·실제 인수·dev 통합한다.
- Task1(root/PM): 구체 계약·독립 3인 심의·사용자 승인·정본 동기화. 현재 진행.
- Task2(backend): admin DTO/API/조회·통계·현재 권한·정렬 잠금·변경/삭제 정책과 자동 테스트.
- Task3(frontend): AdminRoute·프로필 진입·목록/검색/페이징·상세/actions·오류/계정/권한 stale 방어와 테스트.
- Task4(QA/root): 독립 source review, BE test/bootJar·FE test/lint/build, 격리 MySQL/API/Chrome 1440px 인수 및 source/JAR 증거 대조.
- Task5(root/역할/PM): 기록 동기화·명시 파일 커밋·dev PR/merge·검증본 tree 대조·전용 runtime 종료.
- 역할 파일 소유권은 헌법을 따른다. worklog 단일 작성자는 root. backend/frontend 준비 조사는 제품 편집 없이 역할 기록만 허용한다.

## [개발 기록]

- 2026-11-11 KST: `m9_backend_prep`, `m9_frontend_prep`에 기존 소스/정본/디자인 조사 배정. 제품 구현은 구체 계약 승인 이후다.

## [이슈·결정]

- M8 완료 및 PR14 merged=true를 직전 brief에서 확인했다. 현 HEAD는 M8 당시 merge SHA의 후속 선형화 이력이며 제품 src/frontend는 보존돼 있다.
- 관리자 삭제 디자인의 `블로그·게시글·댓글이 모두 삭제됩니다. 복구 불가.`는 FR-ADMIN-05 사용자만 soft delete와 충돌한다. 정확한 효과와 안내 카피를 사용자 결정 대상으로 포함한다.

## [리뷰]

- 독립 기획 심의 준비 중. 제품 검증은 미실행.

### 2026-11-11 KST — 준비 조사·A-v1 독립 검토

- frontend가 원본 관리자 화면과 scaffold/AuthContext/TopBar를 대조했다. 마지막 접속 공급원 부재, summary/댓글 통계·삭제 카피 충돌을 안건 §5에 반영했다. FE baseline test/lint/build를 실행했으며 신규 M9 구현 PASS로 계산하지 않는다.
- backend 준비·root 소스 대조에서 UserSettings 일부·BlogSettings·Category 쓰기의 기존 상태표가 Post/Comment/Like/Universe와 다름을 확인했다. 정지 계정의 모든 쓰기를 새로 금지하지 않고 기존 정책/잠금 경계를 명시하도록 A-v1을 보완했다. ADMIN_002는 기존 HTTP400을 유지한다.
- Product `m9_product_council`, Architecture `m9_architecture_council`, Delivery `m9_delivery_council`에 같은 A-v1과 근거를 전달했다. 서로 중간결과를 공유하지 않는다. QA `m9_qa_prep`는 미확정 표시 인수 매트릭스만 작성한다.

### 2026-11-11 KST — 독립 심의 완료·A-v2 승인 요청

- Product89/Architecture93/Delivery88, 전원 APPROVE_WITH_CHANGES/HIGH. 소스 대조와 검토 필수 보완을 회의 §10/10.1에 반영했다. soft delete·카피/통계/현재권한/self보호/refresh·상태표·no-store·정렬 deleted포함 lock·RR 상세·signed malformed claim 검증을 명시했다.
- QA 인수 매트릭스는 `qa/M9-review.md`, PM/BE/FE/QA STATE와 WORKLOG는 준비완료·미승인 상태다. 제품 src/frontend/migration/사용자 DB 변경 없다. 기록 diff check exit0.
- 현재 실행 상태: 대기(사용자 A-v2 구체 승인). Task1~5 및 회의 §5+§10/10.1 구현·독립QA·실제인수·dev통합의 권한을 요청한다. 승인 응답 전에는 해당 계약을 구현하지 않는다.

- 승인 요청 후 외부 작업으로 `e5c38cd`(`chore: gitignore 수정`)가 현재 M9 branch HEAD에 추가됐음을 확인했다. `.gitignore` 사용자 변경이 해당 커밋에 들어갔으며 그대로 보존한다. base dev848d272는 동일하고 제품 변경0이다. 이후 검증/커밋은 e5c38cd를 포함한 실제 HEAD 기준으로 수행한다.

## [머지]

- 미실행.

## [계획] A-v2 Implementation Plan — 2026-11-11 사용자 전체 승인

> **For agentic workers:** Use superpowers:subagent-driven-development. 프로젝트 역할별 소유권과 독립 병렬 작업/리더 Git 원칙이 범용 스킬 기본값보다 우선한다.

**Goal:** 승인된 M9 관리자 API와 두 화면을 구현·독립검증·dev 통합한다.
**Architecture:** current DB admin scalar guard와 sorted users mutation, RR batch 조회. FE current probe와 account/request/mutation 세대격리.
**Tech Stack:** 기존 Spring Boot/Java21/JPA/MySQL/React19/TypeScript/Vite, 새 의존성 없음.
**Spec:** REQUIREMENTS §6.13-A, 회의 §5+§10/10.1+§11, ADR-0008.

### Global Constraints

- V1~V6/schema/사용자DB·업로드 불변, 제품 bootstrap 없음. shared checkout `feature/M9-admin`, HEAD e5c38cd 보존.
- ApiResponse/PageResponse, no-store 성공·오류, DB현재 ACTIVE ADMIN, deleted 포함 ID정렬 lock/current재검증.
- list/detail RR, SELECT14이하·size1→100 증가0, source LONGTEXT 미적재, Clock/KST→UTC 경계.
- user-only soft delete/하위이력·파일보존, self권한회수/정지/삭제400, lastAccess null, 승인카피.
- 1440px/radius0/크림페이퍼/1100·860px, M10·배포 제외. Git은 root만, 자기소유파일만 편집, 원작성자/독립검토자 분리.

### Review Focus

- 과거 USER claim 승격/ADMIN claim 강등과 signed malformed role/subject를 401/403/current DB기준으로 검증(Task2).
- 상대가 방금 삭제된 actor/target의 current lock과 자기보호·동시 상호강등·refresh경합(Task2).
- 정확한 주경계·동일시각21행·literal `%_\\`·큰offset·RR count/items 경쟁과 query bound(Task2).
- 세션/계정전환·403 후 민감정보/retry/이동 잔존, target404와 actor404 구분(Task3).
- 하위 데이터보존과 삭제후 blog/post/image/댓글/알림 투영, 정지 PUBLIC 유지(Task4).

### Task 1: 계약·정본 동기화 (root/PM)

Files: root REQUIREMENTS §6.13-A, governance meeting/ADR-0008/registers, AGENTS/CLAUDE/worklog/JOURNAL. PM PRD §5.13/§7/§9/§10/§11/§13 및 PM 역할기록.
Interfaces: 아래 DTO와 승인 회의가 BE/FE 공통계약. Task1은 제품코드 편집 없음.
- [x] 사용자 `A-v2 전체 승인` 확인, 회의APPROVED/ADR/REQUIREMENTS 반영.
- [ ] PM PRD 동기화·역할기록 갱신.
- [ ] 파일재독/FR·ADR·잔존참조 검색/diff check. root가 명시문서만 커밋(추후).

### Task 2: backend 관리자 API·권한·잠금·테스트

Files create: `src/main/java/com/zeroverse/domain/admin/{controller/AdminController.java,dto/AdminDtos.java,service/AdminService.java,repository/AdminQueryRepository.java}` 및 하위 AGENTS. 필요시 단일책임 AdminAccessService/AdminMutationService로 분리 가능. `src/main/java/com/zeroverse/security/AdminCacheControlFilter.java`.
Files modify: SecurityConfig, JwtAuthenticationFilter, ErrorCode.ADMIN_002, User entity/Repository(admin role/status/softdelete current lock), 관련 소유 AGENTS. 기존 타도메인 동작은 유지.
Tests create: `src/test/java/com/zeroverse/domain/admin/{AdminControllerTest,AdminQueryMySqlTest,AdminMutationMySqlTest,AdminConcurrencyMySqlTest,AdminQueryBoundMySqlTest}.java`와 지원 fixture; malformed JWT 회귀는 기존 security 테스트 확장. backend 역할기록.
Interfaces: GET `/api/v1/admin/users` data `{users:PageResponse<AdminUserSummary>,summary:{totalUsers,joinedThisWeek,suspendedUsers}}`, GET `/users/{id}` AdminUserDetail, PUT `/role` `{role}` 및 `/status` `{status}`→AdminUserDetail, DELETE→empty. 정확 필드는 REQUIREMENTS §6.13-A를 그대로 따른다. service signature `list(Long actorId,String keyword,UserRole role,UserStatus status,int page,int size)`, `get(Long actorId,Long targetId)`, `changeRole(Long actorId,Long targetId,UserRole role)`, `changeStatus(Long actorId,Long targetId,UserStatus status)`, `delete(Long actorId,Long targetId)`.
- [ ] Step1 RED: 미구현 endpoint/no-store/currentrole·self보호 테스트 작성, targeted 실행 실패 증거 보존.
- [ ] Step2 GREEN: strict DTO/validation/RR list/detail/query·auth/state/error/lock/user-only delete·malformedJWT 구현. 상태×endpoint/lock공유 표를 보고서에 작성.
- [ ] Step3 RED/GREEN: 실제 MySQL actor-target 상호role/status/delete·refresh·source writes 경쟁 barrier, 동시current auth, idempotent audit·fingerprint·RR/querybound/시간경계 테스트.
- [ ] Step4 `test --tests '*admin*'`(실제 package filter 사용) 및 security 관련 targeted·bootJar 실행. 최초 full은 root sourcefreeze 후 단일실행, 자체 전체 반복 금지. raw log/XML·명령/exit와 RED/GREEN 보존.
- [ ] Step5 self-review/STATE·WORKLOG 및 SDD task2 report, Git하지않고 freeze 인계. root 커밋 후 별도컨텍스트 task review.

### Task 3: frontend 관리자 guard·목록·상세

Files create: `frontend/src/features/admin/{types.ts,adminApi.ts,AdminAccessProvider.tsx}`(작은 상태helper 허용) 및 AGENTS; tests `frontend/src/test/{adminApi,adminAccess,adminPages,adminRaces}.test.ts(x)`.
Files modify: `routes/guards.tsx`, `routes/router.tsx`, `App.tsx` provider 위치, `components/layout/TopBar.tsx` 프로필메뉴, `pages/AdminPage.tsx`, `pages/AdminUserPage.tsx`, 관련 tests/AGENTS·frontend 역할기록.
Interfaces: Task2 DTO/endpoint 정확히 소비, TypeScript AdminUserSummary/AdminUserDetail/AdminUserPage 타입은 REQUIREMENTS §6.13-A와 일치. `adminApi.list/get/changeRole/changeStatus/delete` names. AuthProvider 아래 current admin access probe(목록size1 재사용), 기존 Auth API payload는 변경하지 않음.
- [ ] Step1 RED: AdminRoute loading/guest/currentrole, 승인DTO API, 목록/통계/URL검색·paging, 상세 role/status/delete confirm tests.
- [ ] Step2 GREEN: 실제 endpoint연결·정본1100/860px·통계/표·프로필메뉴/자기보호·승인 삭제카피/lastAccess기록없음·오류/empty/pending/retry·mutationcanonical재조회 구현.
- [ ] Step3 RED/GREEN: A→B account switch·동일계정 out-of-order 검색/GET·권한상실/canonical failure·late mutation retry/navigation, target404 대 actor404·USER001/USER003 구분, stale USER claim 승격 probe 테스트.
- [ ] Step4 targeted test→전체 `npm test -- --run --maxWorkers=1`, lint/build. 증거파일 보존, 새변경 없으면 전체반복하지않음.
- [ ] Step5 self-review/STATE·WORKLOG/SDD task3 report, root에게 freeze 인계. Git하지않고 root commit→독립task review 대기.

### Task 4: 독립 QA·전체 검증·실제 인수

Files: QA `qa/M9-review.md`, `qa/m9-acceptance.mjs`, 역할기록. root ignored `build/m9-*` helper/manifest/evidence/JAR. 제품 fix는 원소유자만.
Interfaces: Tasks2/3 freeze와 exact source manifest/JAR, existing M4~M8 fixtures/patterns는 읽기 재사용. 합성계정 ADMIN/USER/SUSPENDED/deleted·0ADMIN/preseed 및 다중블로그/draft/private/동일시각/주경계.
- [ ] Step1 승인계약으로 QA 매트릭스 갱신·harness 정적검증, 준비를 PASS로 표시하지않음.
- [ ] Step2 root 단일 backend full test/bootJar·frontend final test/lint/build, XML 직접집계/최종artifact hash. 동시 Gradle runner 금지.
- [ ] Step3 격리MySQL/API/FE/Chrome1440px 실제 목록·상세·검색/paging/actions·guest/USER/승격/강등/정지/삭제/stale·실제 공개ACL/댓글/알림/이미지·하위fingerprint 검증. 사용자DB/env/upload 접근금지.
- [ ] Step4 독립 whole-branch/시각 검토·finding 원소유자수정→scoped re-review·영향검증, Critical/Important 종료 후만 PASS.

### Task 5: 통합·기록·runtime 정리

Files: 기존 각역할 기록/PM PRD, root worklog/JOURNAL/registers/README. Git root 단독.
- [ ] Step1 최종 diff/정본/로그 재독, 역할 상태·승인계약·실측을 동기화.
- [ ] Step2 정확파일만 원자commit/push·dev PR·독립QA결과 포함. github connector/설치CLI 중 가용경로 사용.
- [ ] Step3 dev merge·검증head/merge tree및 source identity 확인, closeout기록.
- [ ] Step4 M9 runtime만 종료·합성데이터/증거/사용자데이터보존. M9완료 후 새범위 추가없이 사용자입력 대기.

## [개발 기록] 2026-11-11 KST — A-v2 승인·Task1 완료·구현 배정

- 사용자 비동기 `A-v2 전체 승인`으로 진행 재개. root가 회의APPROVED/ADR-0008/REQUIREMENTS §6.13-A/지침/레지스터를 갱신했고 PM이 PRD §5.13/§7/§9.12/§10/§11/§13.5와 기록을 동기화했다. root 재독·참조검색·diffcheck exit0. Task1완료.
- `m9_backend_impl` Task2 src/security/user·backend기록/Gradle단일실행, `m9_frontend_impl` Task3 frontend·역할기록, QA `m9_qa_prep` Task4 승인매트릭스/인수도구, PM `m9_pm_prep` Task1PRD 소유. 전원 타역할 파일변경금지·Git root단독·독립review별도.
- SDD ledger `.superpowers/sdd/M9-admin/progress.md`, exact task brief/report로 추적. 기존 shared checkout/독립소유병렬·root Git은 프로젝트규칙에 따라 범용기본값보다 우선한다.
- root는 전용 Docker서버29.5.2 상태확인, M9runtime helper를 build/m9-runtime.py로 준비했다. API18089/FE5179/MySQL13319 합성환경만 사용. 제품/실제인수 PASS는 아직 없다.

### 2026-11-11 KST — Task3 자체검증·독립 리뷰 착수

- frontend m9_frontend_impl 구현을 a176049에 동결했다. 원본 frontend-final-test-2.log는51files/456tests PASS, lint/build exit0(기존500kB chunkwarning). 최초full의프로필링크1fail은수정후최종full재실행으로해소했다.
- root가보고서/실제diff/원본출력을직접확인하고 별도 m9_frontend_review에8e9559c..a176049 task범위독립검토를배정했다. 아직Task3독립승인/전체M9완료아님.
- 전용MySQL13319 ready, FE5179은build/m9-vite.mjs(envDir:false)로실행. API18089은최종backendartifact이후기동한다. 외부사용자 e5c38cd와기존영속데이터보존.

### 2026-11-11 KST — Task3 독립 review1 수정 배정

- 독립검토Needsfixes: Important4건(F1진입/복귀probe, F2target404재검증unmountloop, F3oldtargetmutation/retry/navigation, F4transient목록행숨김). root가중요코드경로확인후원작성자fix1에RED→GREEN 및실provider/guard/page조합회귀를배정했다.
- MinorF5테이블열배치/spacing은최종1440px시각gate에서추적한다. src/backend구현은계속별도소유로진행하며, Task3완료판정은독립재검토뒤다.

### 2026-11-11 KST — Task2/3 검증 중간 결과·UTC 결함

- Task3 F1~F4는7f61a98, F5는3d34468에서 수정했고 별도 frontend 검토자가 모두 ADDRESSED/Approved로 판정했다. root 최종 FE 자동검증은51files/464tests PASS, lint/build exit0. 실제 API/1440px 제품 화면은 아직 Task4 gate다.
- Task2 구현9a4d03f의 admin33/보안62 targeted와 root 전체107suites/632tests/0fail/0error/0skip+bootJar exit0를 확인했다. 이는 후속 UTC 수정 전 baseline이며 현재 최종 통과를 뜻하지 않는다.
- 추가 실제 HTTP 가입 테스트에서 KST JVM audit가 UTC보다9시간 앞서는3assertion RED를 재현했다. 독립검토M1 Important로 Task2를 Needs fixes로 전환했다. 이어 UTC 실행에서도 DATETIME(0) 반올림에 의한 즉시통계 누락을 재현했다. 회의 §12/ADR 보완처럼 UTC 실행 전제와 audit 초 정밀도 수정을 원 backend에 배정했다. 비UTC JAR 차단·지원UTC 가입/즉시통계·audit 경계 및 전체재검증 전에는 닫지 않는다.
- QA 도구 독립감사 I1~I9(유일fixture·실제ADMIN claim·DOM·stale계정격리·정확한경쟁/검색/집계·비어있지않은삭제보존·필수행누락방지)도 원QA가 fix1중이다. root는 합성 DB native blog/post fixture·전체row/file fingerprint·실행 전후 source/JAR provenance를 보강한다. 사용자 DB/업로드 변경 없음.

## [리뷰] 2026-11-11 KST — Task2/3 종료·Task4 보완 중

- Task2 수정6a8763d: 독립 backend `task-2-review2.md`는 M1 시간대·DATETIME(0) 반올림 모두 ADDRESSED, Critical/Important/Minor0, Approved. root가 source7files hash 및15suites79tests targeted 원문을 대조했다. 새 전체 `build/m9-evidence/backend-final-2/summary.json`은109suites637tests/0failure/0error/0skip/exit0+bootJar. JAR SHA256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`.
- Task3 root 최종464PASS를 testcase 단위로 인계하기 위해 F7 증거 누락 우려에 한해 구조화 reporter를 실행했다. `build/m9-evidence/frontend-final-result.json` success=true/testResults51/464passed/0failed/0pending. source frontend는3d34468후불변이며 과거456과 합산하지 않는다. envDir:false로 사용자 env를 읽지 않았다.
- Task4 QA fix1(01d8582)은 audit2에서 CHANGES REQUIRED. I1~I3 ADDRESSED, I4~I9 잔여/F1~F9 Important는 원QA fixround2로 모두 배정했다. M1 실제runtime provenance는 최종 whole-branch gate로 남긴다. actual acceptance는 NOT RUN이다.
- root는 provisional API28555만 종료하고 최종검증JAR API36756/18089를 UTC로기동했다. Swagger실제GET에서 admin4paths/5methods등록확인. 전용MySQL13319·FE5179·업로드 build/m9-api-data만사용하며 사용자자료불변. tested-version manifest는424files/sourceSHA63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c/JAR위hash다.
