# M3 — 카테고리

- 범위: FR-CAT-01~05, PRD §5.4·§7·§9-H/R·§10.
- 단일 기록 담당: 리더. 구현은 backend/frontend, 독립 검토는 QA·리더.
- 현재 단계: **Draft PR #9, BE370·최종 FE273/lint/build 통과. 실제 가입·설정·새로고침·키보드/수정·오류·시각 확인 후 마우스 DnD 수동 확인과 독립 최종 QA 대기. 이번 실행은 M3 머지·마감 시 종료한다.** 과거 승인 대기와 중간 실패 기록은 아래에 보존한다.

## [계획]

### 2026-09-06 · M2 완료 후 다음 마일스톤 인계

- 선행 완료: M2 PR #8이 dev에 머지됐다(`4c129e20f58a6ccb9c61246d103934702516c295`, GitHub 05:41:12 KST). 루트가 검증 HEAD와 dev 제품 tree 일치를 확인했다. M2 BE348/FE250, 독립 QA 및 실제 smoke는 M2 로그에 기록돼 있다.
- `$brief` 대조: BE/FE 종료 검증, QA 승인, PM의 M3/M4 준비가 완료됐다. 역할의 미머지 스냅샷을 실제 Git과 일치시키며 다음 구현 병목은 사용자 결정이다.
- 준비안: [M3 카테고리 심의](../governance/meetings/M3-20260906-categories.md). Product/Architecture 조건부 찬성, Delivery & Risk 선행조건 보류, 최종 HIGH. Q1~Q4를 사용자에게 각각 제시했으며 승인으로 간주하지 않는다.
- 승인 후 순서(권고): ① BE V2·도메인·실제 MySQL 무결성/경쟁 검증 → ② 공개/보호 API·count/삭제 이동·OpenAPI → ③ FE 단일 리스트/인라인·타입·순서·공개 패널/시작 칩 API 연동 → ④ QA 교차 계약·실제 1440px·전체 회귀 → ⑤ 커밋/PR/리뷰/머지 후 M4 즉시 연결.
- 병렬 경계: 승인된 계약 아래 독립 테스트·디자인 대조 준비는 병렬 가능하다. FE의 실제 연동은 BE 계약/검증 결과를 소비하며 같은 파일을 공유 작성하지 않는다. Git/마일스톤 로그/governance는 리더만 작성한다.
- 최소 구현: 기존 Category/Blog·PageResponse·apiClient·화면 자산을 재사용한다. 새 DnD 라이브러리, Post/Universe CRUD, 0 count stub, setup API 확장, 낙관 버전 필드는 권고안에 포함하지 않는다.
- 필수 확인: active-only unique migration과 재사용/복구, 잠금 우회·같은 blog/부모·전체 형제 배열, 실제 posts 이동, root 페이지 경계, 조회자별 count·관계 방향·draft 비노출, setup 부분 실패 복구. 상세 오류/DTO는 승인된 회의 계약을 그대로 정본에 반영한다.
- M4 인계: category_id 쓰기는 같은 blog lock·활성 재조회에 참여해야 한다. Post 기능을 M3로 앞당기지 않으며 M4 시작 전 승인된 카테고리 접근 predicate와 bulk 갱신의 영속성 컨텍스트 처리를 대조한다. 준비 자료 `docs/PM-M4-readiness.md`는 로컬 참고이고 승인 정본이 아니다.

## [개발 기록]

- 제품 구현 없음. 사용자 승인 후 dev에서 `feature/M3-categories`를 분기하고 실제 변경·테스트를 timestamp와 함께 기록한다.

## [이슈·결정]

- [회의록 M3-20260906-categories](../governance/meetings/M3-20260906-categories.md): **USER_DECISION_REQUIRED**.
- 미확정 Q1 활성 unique/재사용·forward 복구, Q2 불변 타입·삭제·last-write-wins, Q3 공개 count/DTO/오류·미분류 이동, Q4 시작 칩 부분 성공 복구. 승인 전 REQUIREMENTS/PRD의 새 계약을 확정하지 않는다.
- 과거 승인 ADR-0002/0003/0004와 의미가 겹치는 기존 오류/보안 규칙은 유지한다. M3 신규 ADR은 실제 결정 뒤 작성한다.

## [리뷰]

- 제품 리뷰 전. 독립 기획 심의 완료가 제품 DoD 승인을 뜻하지 않는다.

## [머지]

- 없음. M3 미완료이며 후속 M4 구현도 시작하지 않았다.

## [이슈·결정] — 2026-09-06 12:28 KST 재개 체크포인트

- 리더 `/root`가 `$project-lead`와 읽기 전용 brief를 수행했다. 현재 `dev` HEAD `1690731`의 제품 tree는 M2 검증 HEAD `13d8deb`와 동일하고, GitHub PR #8 MERGED 및 열린 PR 없음도 다시 확인했다.
- 실행 상태는 **대기**다. 기존 독립 심의·M3/M4 준비는 완료됐지만 Q1~Q4 사용자 응답은 아직 없다. 호출 자체를 정책 승인으로 처리하지 않으며 제품 변경·새 분기·중복 심의는 하지 않았다.
- 재개 조건은 회의록 §4 Q1~Q4의 사용자 결정이다. 이후 승인된 정본/ADR 반영, M3 backend 구현·검증, frontend 연동, 독립 QA 순서로 이어간다. 이번 기록은 구현 완료나 승인·머지 기록이 아니다.

## [계획] — 2026-09-06 사용자 승인 후 착수

- 사용자 응답 원문은 **"시작"**이다. 바로 앞에서 리더가 Q1~Q4의 구체 권고와 회의 §4를 제시하고 결정을 기다렸으므로 해당 권고안으로 진행하라는 승인으로 기록했다. 과거 project-lead 호출 자체를 승인으로 해석하지 않았다. 상세 근거와 범위는 [회의 §10](../governance/meetings/M3-20260906-categories.md), [ADR-0005](../governance/decisions/ADR-0005-categories-contract.md)에 보존했다.
- 리더가 dev HEAD `1690731`에서 `feature/M3-categories`를 분기했다. 기존 미커밋 운영 변경은 그대로 보존한다. 실행 상태는 **진행**이며, 이번 마일스톤 Git/공통 기록/REQUIREMENTS/governance의 단일 작성자는 `/root`다.
- 배정: `/root/m3_pm`은 PRD·PM readiness/역할 기록, `/root/m3_backend`는 src/Gradle/backend 기록, `/root/m3_frontend`는 frontend/역할 기록, `/root/m3_qa`는 qa/M3-review.md와 QA 기록이다. 같은 파일을 공유 작성하지 않는다.
- 선행 순서: PM·리더 정본 동기화 → backend 카테고리 DB/API 구현과 관련 MySQL·HTTP 검증 → frontend 실제 연동 → QA 독립 검증과 리더 실제 1440px/API 대조. FE·QA는 선행 결과 대기 중 읽기·검증 준비를 병행한다.
- 문서 반영: REQUIREMENTS의 과거 SERIES/is_default 서술을 기존 §9-H DEFAULT enum 결정에 맞추고 FR-CAT/SETTINGS·NFR-04/08에 승인 계약을 추가했다. 부모 수정·삭제 카피의 PRD 정합화는 PM 소유다. 과거 심의 반론·판단과 승인 대기 이력은 보존했으며 신규 ADR·RISK-0008/0009를 연결했다.

## [리뷰] — 2026-09-06 14:13 KST 구현 중 사전 대조

- 리더가 PM 최종 PRD §5.4/9.5를 직접 재독해 공개 PUBLIC 및 방향성 ACCEPTED UNIVERSE count, DEFAULT 직접 삭제 CAT_003, LOCKED 순서·CAT_006/007, 페이징과 setup 복구 계약을 확인했다. QA는 qa/M3-review.md에 독립 acceptance matrix를 작성했고 아직 제품 통과를 판정하지 않았다.
- backend 초안 직접 확인에서 LOCKED를 요청 배열에서 제외해 잘못된 순서 요청을 조용히 성공시키는 흐름, 삭제된 소유자 확인 누락, trim 전 길이 검증, root parentId 누락 및 OpenAPI 오류 상태 문제를 발견해 원 구현자에게 수정·회귀를 배정했다. 이는 수정 중 산출물의 사전 검토이며 최종 FAIL/APPROVE가 아니다.
- Docker Desktop 미기동을 확인해 설치된 앱을 Hidden으로 기동했다. 이후 외부 샌드박스 docker ps exit 0(컨테이너 없음)으로 Testcontainers 실행 환경을 확인했다. 현재 소스/테스트 결과는 backend가 검증 중이고 FE 구현 gate는 BE 계약 증거 대기다.

## [개발 기록] — 2026-09-06 14:48 KST 구현 인계·통합 점검 준비

- 리더가 응답·검증 보고 지연 중인 `/root/m3_backend`를 중단하고 쓰기 권한을 회수했다. 초안을 그대로 보존하고 `/root/m3_backend_resume`에 backend 영역을 단독 인계했다. category/migration 테스트 파일이 추가된 것을 확인했지만 실행 결과가 없어 아직 완료 판정하지 않는다.
- QA 소유 `qa/m3-api-smoke.ps1`은 loopback HTTP 합성 계정·초기 설정·권한·CRUD·타입·순서·이름 재사용·stale 요청을 점검한다. QA Parser 검증 exit 0, 리더의 범위/직렬화/자원 정리 확인까지 완료했고 실제 API 실행은 보류 중이다. SQL post 이동/count 및 101-root 경계는 별도 BE 검증 대상이다.
- 리더가 기존 M2 합성 MySQL 컨테이너를 시작해 업그레이드 기준을 직접 읽었다: Flyway V1/success=1, users=1, blogs=1, categories=1, posts=0. 아직 M3 JAR은 적용하지 않았다. 기존 데이터나 다른 컨테이너 삭제는 없다.
- 실행 캐시 `.gradle-home/`와 `.gradle-home2/`를 `.gitignore`에 한정 추가했다. 생성 캐시를 보존하며 PR 산출물에서 제외하기 위한 변경이고 `git check-ignore` exit 0을 확인했다.

## [개발 기록]·[리뷰] — 2026-09-06 15:58 KST 직접 테스트·FE 연동 개시

- 단일 소유권 조정: `/root/m3_backend`는 `CategoryMigrationTest.java`만 편집하도록 제한 재개했고 `/root/m3_backend_resume`가 나머지 backend를 소유한다. Gradle 실행은 리더가 인수했다. migration 테스트는 독립 MySQL 8.4에 V1 데이터를 넣은 뒤 V2를 적용하며 공유 fixture를 삭제하지 않는다.
- 리더 실행 결과: CategoryServiceMySqlTest 6, CategoryControllerMySqlTest 4 통과(failure/error/skip 0). CategoryMigrationTest 1 실패: 삭제행과 새 활성행이 같은 이름일 때 테스트 helper가 두 행을 읽는 오류다. 원본 결과 `build/m3-root-category-tests.log`, `build/test-results/test/TEST-*.xml`. 총 11/실패 1이며 외부 PowerShell 종료 코드 0만으로 통과라고 판단하지 않았다.
- helper 재조회에 active 필터를 추가하고 V1 시점의 삭제행 ID/deleted_at/active_key 보존·재사용 검증을 추가했다. 이 수정은 앞선 테스트 컴파일 이후이므로 아직 통과 증거가 없다.
- API의 기본 CRUD·공개/인증·DTO·trim·OpenAPI 실측을 확인해 FE 연동 gate를 해제했다. frontend가 실제 SettingsPosts/블로그 패널/setup 복구를 구현하며, backend 추가 회귀·동시성·전체 build와 독립 QA는 병행한다. M3 최종 DoD는 아직 미충족이다.
- QA가 CAT_004 고정 문구와 자료형/binding 오류의 공통 400 누락을 지적했다. backend 수정 후 리더가 Jackson coercion 및 HandlerMethodValidationException의 반환값 오류(서버 500) 경계를 추가 확인 중이다. 새 의존성이나 API 응답 구조 변경은 없다.

## [리뷰]·[이슈·결정] — 2026-09-06 16:19 KST 재회귀와 검증 환경

- 두 번째 리더 실행: 21 tests, failure 1/error 0/skip 0. 서비스 10/컨트롤러 5/migration 1은 통과했고 공통 handler 테스트의 고정 문구 기대 오타만 실패했다. 테스트 기대값을 기존 정본에 맞췄고 숫자 enum 회귀를 추가했다. 이 두 수정의 재실행 및 전체 BE test/build는 /root/m3_backend_resume에 단독 인계했다.
- 검증 실행기 결함도 수정했다. gradlew.bat은 ERRORCODE 오타로 Gradle 실패를 exit 0으로 반환했다. /root/m3_backend가 초기 ERROR_CODE=1 및 ERRORLEVEL 캡처 두 줄을 수정하고 실패 task=1/help=0/invalid JAVA_HOME=1을 확인했다. 리더는 파일과 invalid JAVA_HOME=1을 별도로 직접 확인했다. 빌드 도구 버전·의존성·캐시 삭제는 없다.
- 실제 화면 gate: Vite localhost:5173 ready지만 연결된 browser가 없고 iab도 사용할 수 없다. 사용자에게 브라우저 연결을 요청했다. 실제 1440px 디자인·상호작용은 미검증이며 자동 테스트를 그 대체 증거로 삼지 않는다. API는 새 JAR 준비 후 기존 합성 V1 DB에 적용해 검증한다.

## [리뷰] — 2026-09-06 16:37 KST FE 직접 회귀·복구 경로 수정 배정

- 리더가 frontend 구현자와 별도 실행으로 `npm.cmd test` 23 files/261 tests PASS, `npm.cmd run lint` exit 0, `npm.cmd run build` exit 0(Vite 64 modules)을 확인했다. 이는 1440px 실제 화면이나 최종 M3 승인 증거를 대신하지 않는다.
- 독립 QA가 오래된 blog mutation 응답의 상태 덮어쓰기, mutation 뒤 재조회 실패의 성공 문구, setup 부분 실패 후 SetupGuard에 막히는 관리 경로, blog 전환 중 이전 count 표시를 발견했다. 리더는 해당 소스를 직접 대조했고 동일 blog의 로그인/로그아웃 때 공개 패널 count 재조회 누락과 실패 시 미확인 0 표시도 확인했다. `/root/m3_frontend`에 관련 경로·회귀 테스트·역할 기록만 재배정했으며 다른 역할의 파일은 수정하지 않는다.
- backend 관련 재실행은 21개 중 숫자 enum 입력 회귀 1개가 실패했다(`build/m3-backend-related.log`). FAIL_ON_NUMBERS_FOR_ENUMS 설정 방향을 수정하고 LWW 테스트의 commit 밖 callback 경쟁을 제거하는 작업은 `/root/m3_backend_resume` 단독 소유다. 전체 test/build/bootJar는 아직 통과하지 않았고 기존 JAR은 M2 산출물이다.
- 실행 상태는 **진행**. QA는 최신 BE 변경을 독립 재검토하고, 리더는 새 JAR 이후 V1 데이터 보존·HTTP/OpenAPI 검증을 이어간다. 브라우저 연결 요청에는 아직 응답이 없어 시각 검증만 대기한다.

## [개발 기록]·[리뷰] — 2026-09-06 17:50 KST 전체 BE·실제 API 검증

- 리더가 Gradle 단일 실행을 인수해 `./gradlew.bat --no-daemon --max-workers=1 build bootJar --gradle-user-home .gradle-home2`를 완료했다. `build/m3-root-full-build.log`: `BUILD SUCCESSFUL in 10m 24s`, `M3_FULL_BUILD_EXIT=0`. XML 직접 집계는 56 suites/370 tests, failures/errors/skips 모두 0이다. CategoryService 14/Controller 6/V1→V2 migration 1/Common handler 5를 포함하며 숫자 enum, 삭제 owner HTTP, lock 내 LWW 기대값 기록의 최신 보완도 포함된다. 중간 21개/실패 1 및 25개 통과 기록을 최신 전체 결과와 혼동하지 않는다.
- 실제 구현자: category 초안과 제한 migration/wrapper/policy 보완 `/root/m3_backend`, service/API/입력 경계 후속 및 HTTP 회귀 `/root/m3_backend_resume`, FE 연동/복구·테스트 `/root/m3_frontend`. 리더는 실행·검토를 수행했고 제품 코드의 작성자로 기재하지 않는다.
- JAR `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`는 17:13:54 KST 생성, SHA256 `422f7216e6b70a8bc533c9f84605c9e3368b961c0f0ac1f58a6b9113819fe369`다. 리더가 local profile/loopback API PID 4904로 기동했다(17:15:29 ready). 테스트 자격증명/JWT 키는 프로세스 메모리에서만 설정했고 로그는 `build/m3-api.log`/`m3-api.err.log`다.
- 기동 직전 기존 합성 DB는 V1/success=1 및 users/blogs/categories/posts=1/1/1/0이었다. 기동 직후 V1/V2 success=1/1과 동일 행수 보존을 직접 확인했다. 생성 Swagger의 category GET에는 실제 `security: []`, POST에는 bearerAuth, GET 오류에는 400/403/404가 있다.
- QA가 `qa/m3-api-smoke.ps1 -BaseUri http://127.0.0.1:8080`을 독립 실행해 exit 0을 확인했다. 합성 prefix `m3sdab71e3d` 두 계정은 검증용으로 남았으며 삭제하지 않았다. HTTP 검증과 SQL count/이동·101-root·동시성 테스트의 증거 경계를 `qa/M3-review.md`에 구분했다.
- 리더 FE 재실행은 23 files/269 tests와 lint/build가 통과했다. 그 후 부분 실패→재시도 성공의 history state 정리 시점을 refresh 앞에 두고 지연 응답 회귀의 완료 시점을 엄밀히 확인하는 제한 수정이 진행 중이므로, 해당 변경의 최종 관련 재검증은 별도로 남긴다.
- 남은 gate: 최종 FE 제한 변경의 독립 재검토, 브라우저 연결 후 1440px 실제 layout·DnD/키보드·setup 새로고침/API 연동, 최종 QA·PR/머지. 현재 실행 상태는 **진행**, 제품 전체 승인·머지는 하지 않았다.

## [리뷰] — 2026-09-06 18:16 KST 최종 관련 FE 회귀·Draft 준비

- 추가 Guard 회귀가 부분 실패 후 성공한 재시도를 자기 블로그가 아닌 관리 화면으로 보내는 경합을 재현했다. fixture를 실제 앱과 같은 setup 보호/public blog 구조로 바꿔도 실패해 제품 흐름을 보완했다. 타이머/flushSync 실험은 제거했고, frontend가 고정 completion intent를 Router에서 관측한 뒤 세션을 갱신하도록 수정했다. Guard와 완료 continuation은 동일한 자기 블로그 목적지를 사용하며, 임의 외부 URL은 받지 않는다. 기존 management recovery 및 intent 없는 완료 사용자의 기본 이동은 유지한다.
- 리더 직접 최종 관련 명령 `npm.cmd test -- src/test/BlogInitialSetupPage.test.tsx src/test/categoryBehavior.test.tsx src/test/guards.test.tsx src/test/settingsBehavior.test.tsx`: 4 files/68 tests PASS, exit 0(18:14:48 시작). 최종 lint/build도 exit 0, Vite 64 modules다. 앞선 전체 269개 통과 이후 변경된 경로의 회귀이며 전체 269개를 새 소스에서 다시 실행했다고 주장하지 않는다.
- QA는 latest backend XML과 실제 코드의 transaction 경계를 직접 대조했다. outer TransactionTemplate 안에서 service REQUIRED와 blog PESSIMISTIC_WRITE lock을 공유하고, callback 기록 후 commit 전에는 다른 요청이 같은 lock을 획득할 수 없으며 두 Future의 성공도 확인한다. 이에 따라 과거 LWW 증거 부족 판단은 정정·closure됐다. 최종 FE intent 패치의 독립 재검토는 QA에 배정했다.
- 리더는 QA smoke prefix의 실제 DB 잔여 상태도 읽었다: 합성 users=2/blogs=2, active DEFAULT=2/GENERAL=1/LOCKED=1 및 deleted GENERAL=3. 이는 삭제·재사용 검증의 합성 데이터이며 추가 삭제는 하지 않는다.
- 검토용 Draft PR을 준비한다. M3 관련 파일만 명시적으로 stage하며, 기존 사용자 project-lead 스킬 이전의 AGENTS/JOURNAL 부분 변경과 팀 README·운영 가이드·스킬 파일은 제외한다. 실제 1440px/브라우저 검증 전에는 최종 승인·머지하지 않는다.

## [리뷰]·[이슈·결정] — 2026-09-06 18:43 KST Draft PR·브라우저 대기

- QA가 마지막 FE intent/Guard staged 패치와 독립 검토본의 일치 및 PR 증거 표현을 재확인했다. 신규 critical 없음, `git diff --cached --check` exit 0. 통과한 테스트를 변경 없이 반복 실행하지 않았다.
- M3 구현 커밋 `067cd1174aeec1452b6c74402e750fdad75c3c05`(47 files, +5553/-117)을 생성하고 `origin/feature/M3-categories`에 push했다. 실제 구현 역할은 위 기록대로이며 리더가 커밋·통합 검증을 담당했다. 인덱스 문서 두 개의 BOM은 커밋 전에 제거했고, 기존 사용자 운영 변경은 작업 트리에 그대로 보존했다.
- [PR #9](https://github.com/0-virus/zero-verse/pull/9) 생성 후 GitHub 조회로 OPEN / isDraft=true / base=dev / head=feature/M3-categories / head SHA=067cd117을 확인했다. 최종 QA 승인·Ready 전환·머지는 하지 않았다.
- 실행 상태 **대기**. 현재 브라우저 inventory apps=[]/browsers=[]이며 요청한 Chrome/Edge 연결 응답은 없다. 재개 조건은 연결된 브라우저에서 1440px 디자인, 키보드/DnD, 실제 API, setup 새로고침 검증 후 독립 최종 검토다. API 127.0.0.1:8080 및 Vite localhost:5173와 합성 fixture는 보존한다.
- M4 준비 자료는 이미 존재하며 미결 정책·외부 입력을 승인으로 간주하지 않는다. 새로운 기능·중복 검증을 만들지 않고 `$project-lead`의 실제 대기 기능으로 입력·연결 변화를 기다린다.

## [개발 기록]·[리뷰]·[이슈·결정] — 2026-09-07 22:45 KST README·실제 브라우저 검증 재개

- 사용자 요청으로 리더가 루트 README.md를 작성했다. Windows PowerShell/JDK21/Node22.x, 기존 테스트 DB 재사용 또는 신규 loopback13307 MySQL, 비밀값 비출력 환경변수, local cookie/Origin, bootRun/JAR, frontend, 별도 Testcontainers, smoke 데이터 잔류 및 비파괴 종료를 설명한다. README 소유권·진입점을 AGENTS/CLAUDE에 동기화했다. 기존 문서를 삭제·대체하지 않았고 새 제품 스크립트/설정/의존성은 없다.
- 리더 검증: 11개 PowerShell block의 Parser 오류0, 상대 링크 존재, 랜덤 키의 Base64 decode32바이트, 기존 MySQL `mysqld is alive`, OpenAPI/FE HTTP200. QA도 별도 읽기 전용 문법/실제 설정 대조에서 실행 불가·과장 문제 없음을 확인했다. README의 신규 DB·서버 명령을 전부 새로 실행했다고 주장하지 않는다.
- Chrome 연결을 확인해 1440 CSS px에서 실제 가입/initial-setup/시작 칩 저장/자기 블로그 이동/전체 reload 세션 복구를 수행했다. 신규 합성 계정·블로그 `m3-ui-20260907`은 검증 데이터로 남긴다. 비밀값은 기록하지 않는다.
- frontend의 정본 재대조와 root의 실제 소스 확인에서 관리 행 경계/폰트, 삭제 안내 위치, 보호 항목 버튼 미표시, setup 칩 스타일의 차이를 발견했다. 해당 두 FE 화면과 최소 회귀만 원 구현자에게 재배정했으며 변경 후 관련 테스트·lint/build·1440px 재확인과 독립 QA가 필요하다.
- 사용자 명시로 **M3 완료(검증·PR dev머지·기록)를 이번 작업의 종료점**으로 변경했다. M4는 시작하지 않는다. PR #9는 최종 게이트 통과 전 Draft로 유지한다.

## [리뷰]·[이슈·결정] — 2026-09-07 23:04 KST 최종 회귀·브라우저 증거 경계

- 리더가 최종 FE 소스에서 `npm.cmd test`를 실행했다. `build/m3-final-frontend-tests.log`: 23 files/273 tests PASS, `M3_FINAL_FE_EXIT=0`(22:52:29 시작, 24.29초). `npm.cmd run lint` 및 `npm.cmd run build`도 exit 0, Vite 64 modules다. BE는 370개 전체 통과 이후 소스 변경이 없어 중복 실행하지 않았다.
- 실제 합성 블로그에서 루트/하위 카테고리 생성, 루트와 하위의 키보드 정렬 후 저장 notice, 인라인 이름 변경 후 notice, 동일 부모 중복 이름 오류와 기존 목록 보존을 확인했다. 새 탭에서도 세션과 변경된 순서·이름이 복구됐다. native screenshot으로 단일 카드·하위 들여쓰기·보호 버튼 disabled·카드 아래 삭제 캡션을 확인했다.
- native draggable의 payload 누락을 보완했다. [MDN HTML Drag and Drop API](https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API)에 따라 dragstart에 text/plain과 move 효과를 설정했고 외부 payload를 신뢰하지 않는 내부 ID/전체 sibling/LOCKED 검증은 유지했다. 새 라이브러리는 없다. 다만 보완 후에도 CUA pointer drag는 focus만 바꾸고 drop 저장을 완료하지 않아 payload 누락을 실측 원인으로 확정하지 않는다.
- 실제 마우스 DnD는 미검증이다. 구현자 읽기 전용 검토와 자동 회귀에서는 결함을 찾지 못했지만 도구 lifecycle 제약이라는 판단도 추정이다. 사용자에게 `/settings/posts`에서 프론트엔드를 화면검증 위로 이동해 순서와 저장 notice를 확인하도록 요청했다. 응답 전에는 DnD PASS나 M3 최종 승인을 기재하지 않는다.
- LOCKED 전환 경고 문구는 실제 AX에서 확인했으나 확인창 처리 도구가 timeout/No dialog is showing을 반환했고 새 탭의 타입은 GENERAL이었다. 이 브라우저 저장 사례는 PASS가 아니며 기존 HTTP/자동 회귀와 구분한다. 사용자 데이터·검증 fixture를 삭제하지 않았고 서버도 재시작하지 않았다.
- 최신 FE diff·273개 결과·README·브라우저 확인 및 한계를 독립 QA에 전달했다. GitHub PR #9는 OPEN/Draft/MERGEABLE/CLEAN, base dev, CI checks 없음으로 조회됐다. M3 종료 조건과 기존 사용자 미커밋 변경 보존은 유지한다.

## [리뷰]·[이슈·결정] — 2026-09-07 23:10 KST 최종 패치·README push와 수동 확인 대기

- 커밋 `4fc9ae22701c99db031af03fd7ddf120fe74ae92`를 push했다(14 files, +408/-46). README·최신 FE와 회귀·QA 기록이 PR #9에 반영됐고 GitHub head 일치, OPEN/Draft/MERGEABLE/CLEAN/checks=[]를 확인했다. 사용자 기존 운영 변경은 제외·보존했다.
- 독립 QA는 신규 critical/high 없음, root의 실제 화면·setup/full-refresh/keyboard/rename/duplicate 증거 반영을 확인했지만 mouse-DnD와 LOCKED confirm 수락 후 저장 미검증으로 최종 acceptance를 보류했다. 실제 사용자 조작 확인 또는 해당 경로를 검증할 수 있는 지원된 도구가 필요하다. 코드·회귀 PASS를 실제 mouse/confirm PASS로 대신하지 않는다.
- 다음은 두 조작 확인→필요한 제한 수정/회귀→QA/리더 최종 승인→PR dev 머지·마감 후 작업 종료다. M4를 시작하지 않으며 아직 `[머지]` 완료 기록을 남기지 않는다. 실행 중 에이전트/테스트는 없고 서버·DB는 보존, 임시 viewport는 복원했다.
