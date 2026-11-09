# M6 — 댓글·좋아요

> 현재 실행: **COMPLETE / 구현·독립 QA·dev 통합 완료**. 사용자 `A-v2 전체 승인`(2026-11-09), 승인 계약은 회의 §6 및 PRD §9.9다. 아래 착수·대기 기록은 시간순 이력이며 제품 검증·완료를 의미하지 않는다.

## [계획]

### 2026-11-09 — 착수·기준 확인

- 사용자 목표: `m6 구현 완료를 목표로 시작해줘`. 이번 실행 범위는 M6 구현·독립 검증·dev 통합이며 M7 이후는 포함하지 않는다.
- 기준: `dev` / `c46a26d`, M5 PR #11 통합 및 독립 인수 완료. 시작 시 작업 트리는 clean이다. 리더가 `feature/M6-comments-likes` 브랜치를 생성했다.
- 정본: REQUIREMENTS §4 Comment/Like, FR-COM-01~04, FR-LIKE-01~02, NFR-04/09; PRD §5.7/5.8, §7.2 PostDetailPage, §9.2-P/§9.3-③, §10 M6, §11/12; DESIGN-SYSTEM 게시글 상세 및 Pages 원본.
- 현재: V1에 `comments`, `post_likes` 테이블과 `(post_id,user_id)` unique가 있다. 제품 댓글·좋아요 서비스/API와 PostDetailPage의 댓글·좋아요 UI는 미구현이다. 기존 PostAccessPolicy와 공통 응답·PageResponse·오류 코드를 재사용한다.
- 소유권: 리더는 이 worklog의 단일 작성자 및 governance/JOURNAL/Git 담당. backend는 src/Gradle 및 역할 기록, frontend는 frontend 및 역할 기록, QA는 qa 및 역할 기록, PM은 PRD 및 역할 기록 담당이다.
- 조사 배정: `/root/m5_backend_impl`, `/root/m5_frontend_impl`을 재사용해 각각 실제 코드·계약 및 디자인·UI 의존성을 읽기 전용 확인한다. 기존 작업자는 모두 완료 상태임을 확인했으며 중복 구현은 시작하지 않았다.
- 심의 조건: 공개 API 구체화, BE/FE 동시 변경, 접근제어가 포함돼 독립 3인 심의를 소집한다. 승인 전 제품 구현은 보류하고 계획·검토·환경 확인을 진행한다.

### 실행계획 — A-v2 승인 후 실행

설계·계약 정본: [M6 심의 §6 A-v2](../governance/meetings/M6-20261109-comments-likes.md), PRD §9.9, REQUIREMENTS FR-COM-01~04/FR-LIKE-01~03. 아래 Task는 역할 기반 subagent 실행 및 저자와 다른 QA 검토를 사용한다. 공통 제약은 V1 불변·신규 의존성 없음·ApiResponse/PageResponse·원본 디자인·M6 한정이다.

#### Task 1 — backend 댓글 조회·쓰기

- 소유: backend. 신규 `src/main/java/com/zeroverse/domain/comment/{entity,repository,service,dto,controller}/`, 해당 `src/test/java/com/zeroverse/domain/comment/`, 도메인 AGENTS. 공통 `config/SecurityConfig.java`에는 신규 GET의 선택 인증만 추가한다.
- 입력: PostAccessPolicy, PostRepository, BlogRepository, UserRepository, UniverseRepository, ApiResponse/PageResponse, 기존 COM/POST/AUTH/USER 코드.
- 출력: `CommentService.list(postId,viewerId,parentId,page,size)`, `create(actorId,postId,request)`, `update(actorId,commentId,request)`, `delete(actorId,commentId)`와 승인 DTO/6.2 오류 계약.
- [ ] root/reply paging, 삭제 placeholder, 동일 post/root 제약, 길이 0/1/2000/2001·공백·HTML literal, author/post owner 삭제 권한을 검증하는 unit/controller/MySQL 실패 테스트 작성.
- [ ] targeted 테스트 실패 원인을 확인한 뒤 entity/repository/service/controller를 구현하고 동일 테스트 GREEN 확인.
- [ ] root 삭제 후 자식 보존, 삭제 parent reply 거부, 댓글 타인 수정 거부, 잘못된 Bearer의 guest downgrade 방지를 검증.

#### Task 2 — backend 좋아요·상태·경쟁

- 소유: backend. 신규 `src/main/java/com/zeroverse/domain/like/{entity,repository,service,dto,controller}/`, 해당 테스트 및 AGENTS. 댓글 서비스와 공통 interaction 상태 계산 경계는 backend 단일 작성자가 결정한다.
- 출력: POST/DELETE likes의 200 멱등 응답, GET interactions의 count/liked/권한 flags와 private,no-store.
- 선행 최소 수정: `domain/blog/repository/BlogRepository.java`에 user join 없는 scalar blog lock, `domain/post/repository/PostRepository.java`에 ID projection, `PostService.get`에 읽기 전용 정책을 보존하는 blog→owner→post 잠금 helper를 추가한다. 기존 쓰기 helper의 owner/ACTIVE 제약을 읽기에 적용하지 않는다. M6 mutation은 actor/owner/reply-parent-author 중복 제거 ID 오름차순 사용자 lock 및 lock 후 current state 재검증을 적용한다.
- reply batch는 MySQL8 ROW_NUMBER/COUNT window를 사용해 root별 첫20개와 total을 한 번에 읽고 author를 batch/LEFT JOIN으로 공급한다. 댓글 query/permission lookup에도 per-root N+1을 만들지 않는다.
- [ ] 같은 사용자 동시 like N회→row1, unlike N회→row0, 교차 mutation→정의된 최종 상태 테스트를 RED→GREEN 실행.
- [ ] guest/PUBLIC/UNIVERSE accepted·취소·차단/PRIVATE/draft/삭제 post·blog·owner/SUSPENDED 매트릭스의 상태 조회와 mutation을 검증.
- [ ] block/comment, root delete/reply, post delete/mutation, edit/delete를 실제 MySQL로 경쟁시키고 lock 이후 재검증·500 없음·원자성 검증.
- [ ] 기존 M4 상세 GET·post delete·M6 댓글/like·M5 block 교차 경쟁으로 joined-lock 역전 회귀를 검증한다. guest PUBLIC·SUSPENDED 작성자의 공개 글·24h 조회수 dedupe도 회귀 확인한다.
- [ ] root100×reply20 상한과 batch 조회를 검증하고 reply21+ 전체 순회·큰 offset을 검증. M6에서 notification row가 늘지 않음을 확인.
- [ ] backend 전체 test 및 bootJar 성공, 실패/오류/skip 0, OpenAPI에 인증·오류·201/200·DTO를 명시.

#### Task 3 — frontend API·좋아요

- 소유: frontend. 신규 `frontend/src/features/comment/`, `frontend/src/features/like/`의 API/types/components와 테스트·AGENTS; 수정 `frontend/src/pages/PostDetailPage.tsx` 및 관련 기존 테스트.
- 입력: 승인된 CommentItem/InteractionState/PageResponse와 apiClient/AuthContext. 댓글 create/update/delete/list, like/unlike/getInteractions 함수를 정확한 경로·method로 제공한다.
- [ ] API 계약·size/parentId와 guest/인증 전환 테스트를 RED→GREEN 구현.
- [ ] 좋아요 최초 상태·낙관적 수치/색상·실패 rollback·중복 클릭 방지·서버 canonical 값 재반영·계정/글 전환 중 늦은 응답 무시를 테스트하고 버튼을 연결.

#### Task 4 — frontend 댓글·답글

- 소유: frontend. `CommentSection`, `CommentItem` 등 작은 컴포넌트로 root/reply 독립 page와 폼 상태를 분리한다.
- [ ] 읽기/빈 목록/부분 오류/재시도/guest·제한 안내, 자기 댓글 수정, 글 주인 삭제, 삭제 부모의 자식 보존을 RED→GREEN 검증.
- [ ] 낙관적 임시 댓글·실패 입력 보존·성공 canonical 교체, 업데이트/삭제 rollback, 중복 submit 방지, root/reply 페이지 이동과 상위 글/계정 변경 경계를 검증.
- [ ] 정본 980px·26px·34px·13.5px·하드 보더/그림자와 카피를 직접 대조. 답글은 1단계 들여쓰기+구분선, textarea는 escaped plain text와 줄바꿈 사용.
- [ ] `npm test -- --run --maxWorkers=1`, `npm run lint`, `npm run build` 성공 확인.

#### Task 5 — 독립 QA·실제 인수

- 소유: QA(`qa/M6-review.md`, 필요 시 `qa/m6-*.mjs`, 역할 기록); 리더는 실행환경·중요 diff/검증 출력 직접 확인.
- [ ] FR-COM01~04/LIKE01~02, 승인 계약의 실패·경계·경쟁을 테스트/실제 구현과 추적. stub/skip/공급원 없는 UI 금지.
- [ ] 실제 API+Chrome 1440px에서 guest 로그인 유도, 두 계정 댓글/답글/수정/삭제, 좋아요 토글·새로고침·계정 전환, page2/답글21+, 권한 철회 및 원본 디자인 대조.
- [ ] 독립 검토 finding을 원 소유자에 반환→수정→재검증. 모든 필수 finding 해소 후 인수 판정.

#### Task 6 — 기록·Git 통합

- 소유: 리더. PM은 승인 시 PRD와 자신의 상태만 동기화하며 다른 역할 상태는 해당 소유자가 갱신한다.
- [ ] 최종 검증 버전과 diff 일치, `git diff --check`, 비밀/원본/로컬 데이터 미포함 확인.
- [ ] 명시 파일만 commit/push→dev 대상 PR→독립 최종 검토→merge. 실제 SHA/PR/검증 출력을 [머지]에 기록.
- [ ] 이번 실행의 임시 runtime 정상 종료 및 데이터 보존. M7 이후는 별도 범위로 남긴다.

#### Review Focus

1. 차단·관계 삭제가 댓글/좋아요 검증과 경쟁하는 경우 — Task2 실제 MySQL 경쟁.
2. 삭제 root/탈퇴 작성자 뒤 답글 노출·count 일관성 — Task1/2와 Task4.
3. 100개 root·각 21개 이상 reply 및 큰 offset — Task2 query 상한과 Task4 전체 도달성.
4. 느린 응답 중 계정/게시글/페이지 변경 — Task3/4 stale response 및 rollback.
5. escaped HTML·Unicode·공백·길이 경계 및 중복 submit — Task1/3/4 입력 검증과 실제 Task5 인수.

## [개발 기록]

아직 제품 구현을 시작하지 않았다.

## [이슈·결정]

- 댓글 페이지 구조·삭제된 부모 처리·viewer별 좋아요/권한 상태·동시성 경계는 구체 계획과 심의로 확정한다. 기존 정본의 1단계 대댓글·soft delete·멱등 좋아요는 유지한다.
- 2026-11-09: [독립 심의 A-v2](../governance/meetings/M6-20261109-comments-likes.md#6-a-v2--사용자-승인-대상-구체-계약)를 작성했다. 제품/기술/전달 3인 모두 APPROVE_WITH_CHANGES·HIGH. 보완을 반영했고 사용자 승인 전 제품 구현을 시작하지 않는다. 승인 후 REQUIREMENTS와 PRD를 소유자별로 동기화한다.
- M6 시각 인수는 PostDetailPage로 한정한다. BlogPage/PostCard count 집계 미구현은 별도 후속 범위이며 M6 완료로 그 부분까지 완료됐다고 주장하지 않는다. M8 댓글/좋아요 알림 정책은 유지하되 row 생성은 M8에서 연결한다.

## [리뷰]

미실행. 구현자와 다른 QA 컨텍스트에서 정본·diff·실제 검증 출력을 대조한다.

## [머지]

미실행. 완료 기준과 독립 인수 이후 리더가 명시 파일만 commit/push하고 dev 대상 PR·merge를 수행한다.

### 2026-11-09 — 실행환경 확인·검증 명령 계획

- Java21 `/private/tmp/zeroverse-jdk21/jdk-21.0.13+9/Contents/Home`, Gradle cache `/private/tmp/zeroverse-gradle-user-home`, Colima/Docker `/private/tmp/zeroverse-container-runtime/bin`, frontend/node_modules의 실제 존재를 확인했다. 현재 runtime을 기동하거나 제품 테스트를 실행한 것은 아니다.
- 구현 후 BE 실행: `JAVA_HOME=/private/tmp/zeroverse-jdk21/jdk-21.0.13+9/Contents/Home GRADLE_USER_HOME=/private/tmp/zeroverse-gradle-user-home JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp/zeroverse-junit-tmp DOCKER_HOST=unix:///private/tmp/zeroverse-colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true /private/tmp/zeroverse-jdk21/jdk-21.0.13+9/Contents/Home/bin/java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --max-workers=1 test bootJar`. JAVA_HOME/bin은 실행 PATH에도 추가한다.
- 기존 macOS symlink temp 문제를 피해 지정 temp를 사용한다. Ryuk disable은 M5 sidecar 실패 대응을 유지하므로 생성된 테스트 container ID를 기록하고 테스트 종료 후 이번 생성 자원만 정리한다. 사용자 DB/container/영속 파일은 삭제하지 않는다.
- BE XML 집계는 `build/test-results/test/TEST-*.xml`, FE는 `frontend`에서 `npm test -- --run --maxWorkers=1`, `npm run lint`, `npm run build`. 실제 exit/count/log 및 검증 SHA는 실행 후 기록한다.
- 실제 인수는 API127.0.0.1:18080/FE localhost:5173, 기존 분리 합성 MySQL127.0.0.1:13307과 시스템 Chrome을 사용하되 포트 점유와 기존 container 상태를 실행 직전 확인한다. 운영 배포·외부 자원 추가는 없다.
- backend Task1→Task2는 같은 backend 작성자가 순차 수행하며 interaction DTO/계산을 단독 소유한다. FE는 승인 DTO에 따라 병렬 개발 가능하고 실제 API 인수는 양쪽 통합 뒤 진행한다.

### [이슈·결정 / 개발 기록] 2026-11-09 — 사용자 승인·개발 착수

- 사용자 원문 `A-v2 전체 승인`으로 회의 §6와 Task1~6 실행을 승인했다. 이전 승인 대기 문구는 당시 이력이며 현재는 구현 진행이다.
- root는 REQUIREMENTS §6.8 FR-LIKE03/확정 계약/API 목록을 반영했고 PM이 PRD를 동기화한다. backend Task1→2, frontend Task3→4, QA 준비를 위 소유권대로 배정했다. source/test 구현과 실제 검증 결과는 후속 기록에서 확인한다.

### [개발 기록·환경] 2026-11-09 13:13 KST

- Colima 기존 VM 재기동 exit0. 기존 합성 MySQL `d78f404f9136`/`zeroverse-m5-mysql84`를 재기동했고 연결 `SELECT 1` 및 Flyway 성공 버전1/2/3을 확인했다. DB credential은 출력하지 않았다. runtime Docker socket은 sandbox 밖 연결 권한으로 사용한다.
- API launcher는 리더 소유 ignored `build/m6-api-launch.py`로 준비해 문법 확인했다. 기존 container credential은 메모리에서 읽고 JWT/view HMAC 키를 매 실행 메모리에서 생성한다. upload 경로는 ignored `build/m6-api-data/uploads`, API loopback18080/FE5173이다. 아직 M6 JAR/API/FE를 기동하지 않았다. 사용자 .env.local·.local-data는 수정하지 않았다.
- backend RED XML `CommentControllerTest`는 API 미구현 시점3tests/3failures/0errors/0skips를 기록했다. 이는 실패 테스트 확인이며 M6 PASS가 아니다.

### [개발 기록·리더 검증] 2026-11-09 13:39 KST — FE Task3/4 자체 완료

- 구현자 `/root/m5_frontend_impl`: 댓글/좋아요 API·DTO·CommentSection/Row·PostDetail UI, optimistic/canonical/rollback, root/reply 독립 페이지, canEdit/Delete/Reply·guest 상태, account/post 세대 guard와 await-chain guard를 구현했다. create 성공 후 canonical GET이 실패해도 임시 음수ID를 남기지 않는 회귀를 포함한다.
- 리더 직접 확인: API targeted2files/3tests PASS, behavior11tests PASS, 최종 fresh 전체 `npm test -- --run --maxWorkers=1` **34files/353tests PASS**(15.31s,exit0). `npm run lint` exit0, `npm run build` exit0(Vite141modules, 기존500KB chunk warning). `git diff --check` exit0.
- 이 결과는 FE 자동검증이다. QA 독립 source 검토를 배정했으며 실제 backend HTTP/권한 및 원본1440px 인수는 미실행·대기다. M6 전체 완료를 의미하지 않는다.

### [리뷰] 2026-11-09 — 독립 backend 중간 source gate

- 검토자 `/root/m6_backend_code_review`(구현자와 다른 컨텍스트)가 read-only 검토했고 root가 실제 소스와 대조했다. 아직 backend 구현·테스트 진행 중이며 최종 인수 판정이 아니다.
- **M6-BE-01 Important OPEN**: RR scalar prelookup 후 일반 like find/exists/count가 stale snapshot을 써 동시 like unique500 또는 오래된 unlike/state를 만들 수 있다. current row와 응답snapshot을 함께 수정하고 강제순서 MySQL 경쟁 검증 요청.
- **M6-BE-02 Important OPEN**: readOnly interactions GET이 canReadCurrent/FOR UPDATE를 호출해 nonowner UNIVERSE 조회가 실패할 수 있다. ordinary GET ACL과 mutation current ACL을 분리하고 실제 accepted UNIVERSE GET 회귀 요청.
- **M6-BE-03 Important OPEN**: RegexRequestMatcher가 queryString을 포함해 매칭하므로 실제 guest comments?page=0&size=20이 보호 경로로 떨어진다. 리뷰어가 설치된 SpringSecurity jar bytecode를 확인했다. queryParam/URL literal 및 실제HTTP를 사용해 GET·numeric path를 유지하는 수정 요청.
- 모두 backend 원소유자에게 전달했으며 실제 수정·검증 이후에만 CLOSED로 변경한다.

### [리뷰 보완] 2026-11-09 — backend source gate 최종·경쟁 테스트 분담

- 독립 source gate 최종은 수정 필요다. 추가 **M6-BE-04 Important OPEN**(탈퇴 root 작성자 검사 누락으로 새 reply 허용), **M6-BE-05 Minor OPEN**(COM002 OpenAPI404 설명 오류), **M6-BE-06 Important OPEN**(M4 get의 current 본문+RR 과거 tags/images 응답 혼합), **M6-BE-07 OPEN**(SUSPENDED의 보존 liked와 ACTIVE 쓰기flags 혼합)을 원 구현자에게 반환했다. 실제 재현/회귀 이후 closure한다.
- 경쟁 검증은 `/root/m6_concurrency_tests`에 새 `src/test/java/com/zeroverse/domain/post/M6InteractionConcurrencyMySqlTest.java` 한 파일을 단독 배정했다. 기존 backend는 해당 파일을 편집하지 않는다. helper는 src제품·다른test·공용support·역할기록을 변경하지 않으며 주backend가 역할기록 단일 작성자로 결과를 반영한다.
- helper 목표는 단순 동시 출발이 아닌 snapshot→lock대기→타tx commit 순서를 강제하는 MySQL 검증(duplicate like/unlike, Universe 철회, post update/get 응답 일관성)이다. Gradle 실행은 경합 방지를 위해 root가 조정한다. 기존 backend는 source수정/행동/OpenAPI/full 검증을 계속한다.

### [리뷰] 2026-11-09 — FE 독립 source gate·수정 1차

- `/root/m5_independent_qa`가 qa/M6-review.md에 FE-001~004를 기록했다. root가 현재코드를 대조해 Important3건의 수정 필요를 확인했다.
- FE-001: mutation 성공 뒤 canonical GET실패를 저장실패와 같은 catch에서 rollback. FE-002: account/post 세대변경시 likebusy 미초기화로 새버튼 고착. FE-003: rootpage 이동시 reply요청세대 미무효화로 stale 재주입. FE-004 Minor: capability/placeholder 검증범위 부족.
- Task3/4 fix round1을 원 frontend 구현자에게 배정했다. accepted mutation과 재조회실패 분리, 새세대 busyreset, rootpage replyguard를 RED→GREEN으로 검증하고 독립 QA에 재리뷰한다. 앞선353tests 통과는 이 결함을 잡지 못한 이전증거이며 수정후 검증으로 대체해야 한다.

### [리뷰·검증] 2026-11-09 14:10 KST — 수정 후 중간 인수

- frontend 수정1차 자체 전체34files/357tests·lint/build 통과 보고를 받았고 root는 변경된 성공DTO/재조회실패 분리·busyreset·rootpage세대 코드를 직접 확인했다. root targeted `m6PostDetailBehavior.test.tsx` 16tests PASS(14:06,exit0), diff-check PASS. QA 독립 재리뷰와 실제 브라우저 인수를 재배정했다.
- root 직접 BE XML74suite/478tests failure/error/skip0 및 14:01 bootJar를 확인해 API loopback18080을 기동했다. 새 helper 경쟁 테스트는 아직 이 수치에 포함되지 않으며 최종source 변경 후 검증은 별도다. 실제 `/v3/api-docs`200과 comments GET/POST, comments/{id} PUT/DELETE, likes POST/DELETE, interactions GET 및 각 응답code를 확인했다.
- `/root/m6_backend_code_review` 재검토에서 기존 지적의 소스 수정과 aggregate JOIN FOR UPDATE 제거를 확인했다. 보존된 finding 번호는 앞선 기록을 따른다(재리뷰 보고서의 후반번호가 재정렬됐으므로 제목으로 대조). RR/current상태·querystring GET·M4 응답일관성은 실제 경쟁/HTTP 증거 이후에만 최종 CLOSED다.
- helper 경쟁테스트의 외부 RR TransactionTemplate/managed Post preload가 실제 service RC진입을 덮는 문제를 root가 발견해 원소유자에게 반환했다. 테스트를 통과시키기 위한 제품변경 없이 실제 호출경로에서 대기순서를 관찰하도록 수정 중이다.

### [이슈·결정] 2026-11-09 14:10 KST — 중간 runtime 빌드 경합 해소

- 첫 실제 인수는 POST timeout/일부500으로 중단됐다. root가 서버 오류를 확인한 결과 14:05:31 실행한 원본JAR를14:05:52 backend bootJar가 덮어써 JVM lazy classload `NoClassDefFoundError(ThrowableProxy)`가 발생했다. 제품 API 결함이나 localhost 정책 문제로 판정하지 않는다.
- root ignored launcher가 JAR를 `build/m6-api-data/runtime.jar`로 복사해 실행하도록 변경했고 이전서버 종료후14:09 재기동했다. localhost OpenAPI200 복구를 확인해 QA 인수를 재개했다. 이후 최종source/JAR 동일성 확인과 필요시최종재기동은 유지한다.

### [리뷰] 2026-11-09 — 최종 전체 검토·FE 수정 2차

- 독립 `/root/m6_whole_review`(fresh context,gpt-6-astra high)에 base c46a26d 대비 tracked/untracked 전체 변경을 read-only 배정했다. 기존 부분검토와 별개로 실제 정본·diff·신규파일을 대조한다.
- **M6-FINAL-FE-01 Important OPEN**: rootpage세대 무효화 후 이전reply finally가 loading=false를 못 쓰는데 새root조회가 replyLoading을 초기화하지 않아 페이지왕복시 영구로딩. stale text부재만 검증한 이전FE003보다 행동검증을 보강한다.
- **M6-FINAL-FE-02 Important OPEN**: canonical interactions/comments GET의 확정적401/POST001/POST002 거부도 일시적네트워크실패와 같이 기존상태를 유지한다. 승인회의§6.3의 권한상실시기존화면상태폐기와 충돌하므로 root가수정필요판정했다. LIKE001은PUBLIC block과ACL상실을구별하지못하므로재조회등으로실제권한을구분한다.
- 원frontend작성자에게 fix round2를 배정했고 수정후원reviewer가해당범위재검토한다. 앞선46HTTP/브라우저PASS는기본통합동작의증거로보존하며이경계결함을닫는증거가아니다.

### [검증 분담] 2026-11-09 — 교차 경쟁·조회 상한 보강

- 같은 helper `/root/m6_concurrency_tests`에 신규 `src/test/java/com/zeroverse/domain/post/M6InteractionCrossRaceMySqlTest.java` 단독소유를 배정했고5개 강제순서시나리오(block→comment/like,rootdelete→reply,postdelete→comment/like)를 작성했다. 제품소스·공용support·기존test·역할기록변경은없다. mainbackend가Gradle실행을단독조정하며 실제통과전까지미검증이다.
- 기존helper테스트3건중2건PASS 후 실패1건은 testuser의performance_schema SELECT권한오류였다. helper파일내에서격리된Testcontainer root연결로잠금관측만수행하도록수정했고제품계정·grant·기존합성DB는변경하지않았다. 수정후실행결과대기다.
- mainbackend는100root×20reply cap뿐아니라Hibernate SQL statement수상한과notification불변을테스트로보강중이다. 실제증거는최종XML/실행출력으로확인한다.

### [리뷰] 2026-11-09 14:26 KST — FE round2 결과·round3 착수

- root가전체FE `npm test -- --run --maxWorkers=1`을직접실행해34files/363tests PASS(14.83s,exit0;ignored build/m6-frontend-final-tests.log)를확인했다. 이파일명은실행용명칭이며최종완료판정이아니다.
- scoped reviewer는 WR01(앞선 M6-FINAL-FE01) ADDRESSED, WR02(권한폐기) PARTIAL/NOTADDRESSED로판정했다. LIKE001 mutation실패뒤GET재확인이없어실제ACL상실과읽기가능PUBLIC차단을구분하지못한다. 테스트가계약에없는interactionsGET LIKE001을주입한점도수정대상이다.
- **M6-WR-03 Important OPEN**: canonical interactions GET실패catch에서세대확인없이parent상태폐기callback을호출하여옛계정의늦은POST002가새계정/글화면을지울수있다. root가실제코드대조후원FE작성자에게round3로WR02/03수정을배정했다. GET200canonical/확정권한거부/transient분기및전환후새화면유지회귀가필수다.
- backend 실제14:25XML: CrossRace5tests PASS(0/0/0), 기존Concurrency3중2PASS/1FAIL이다. 실패는getSnapshot의projection관측latch미도달이며helper가future실패원인과SpringData spy를진단중이다. 제품수정으로테스트를우회하지않고실제DB대기순서검증을유지한다.

### [검증] 2026-11-09 14:29 KST — 실제 MySQL 경쟁 8건 PASS

- root가현재JUnit XML을직접파싱했다. `M6InteractionConcurrencyMySqlTest`3tests, `M6InteractionCrossRaceMySqlTest`5tests 모두failure/error/skip0. XML2개를ignored `build/m6-evidence/concurrency-1429/`에보존했다.
- 앞선getSnapshot관측실패는SpringData repositoryspy를제거하고실제DB의distinct requesting transaction2개로update/get대기단계를확인하도록교정했다. 외부RR/managed entity주입없이실제PostService.get RC진입을검증한다. 조회결과본문·tags·images최신일치및중복like/unlike/UNIVERSE철회/차단/삭제경쟁이모두통과했다.
- BE독립reviewer에게기존stale snapshot지적의증거closure를배정했다. 최종전체suite와마지막source/JAR일치, FE round3/독립인수는별도남아있다.

### [리뷰] 2026-11-09 14:30 KST — backend 경쟁 finding CLOSED

- `/root/m6_backend_code_review`가보존XML2suites/8tests와수정된실제test/source를직접대조해 **M6-BE-01(RR stale), M6-BE-06(상세GET snapshot혼합) CLOSED** 판정했다. 외부RR·managedentity주입없는actualget의2개DBwaiter와updatepostlock관측, 최신제목/JSON/HTML/tags/images일치를근거로확인했다.
- BE02(readOnlyGET), BE04(탈퇴parent), BE05(OpenAPI), BE07(SUSPENDEDliked)은이전소스수정/회귀로해소됐고 BE03은actualquerystringHTTP로닫혔다. 독립검토신규Important없음. 최종전체suite/bootJar/source동일성게이트는여전히남아있다.

### [리뷰·최종 자동 검증] 2026-11-09 14:38 KST

- 원 frontend 작성자의 fix round3 후 독립 `/root/m6_whole_review`가 **WR-01/02/03 모두 ADDRESSED, 새 Critical/Important 없음, source merge-ready Yes**로 판정했다. reviewer도 M6 행동 테스트 28/28을 직접 실행해 통과했다. `LIKE_001 → 상태 GET`의 canonical/접근 거부/transient 분기, 직접 mutation의 확정 읽기 거부, 옛 canonical 오류의 세대 guard와 새 화면 사용 가능 여부를 확인했다.
- root fresh FE 전체: `npm test -- --run --maxWorkers=1` → **34files/369tests PASS**, exit0, 14:37 시작·15.83초. `npm run lint && npm run build` exit0, Vite141modules. 기존 500KB chunk warning은 남지만 빌드 오류는 없다. `git diff --check` PASS.
- root fresh BE 증거 대조: **77suites/490tests, failure/error/skip 0**, helper 경쟁8건과 guest/ACTIVE query-bound, no-notification 포함. 전체 XML은 ignored `build/m6-evidence/backend-final/`에 보존했다.
- 검증 소스(`src/main`, `src/test`, `frontend/src`의 path-NUL-content-NUL 정렬 연결) 289files SHA256 `daef7ce61593a22c61c7416bf54c16fae3064ce743a720c988530022523aede7`. built/runtime JAR 둘 모두 `f0cbf9c2fd027510b03a00ce752f2f39040f5870bafaef360895343856d056ab`로 일치한다. ignored `build/m6-evidence/tested-version.json`에 저장했다.
- 최종 실제 HTTP/Chrome 인수(추가 PRIVATE 전환 시나리오 포함)만 남아 있으며 결과 확인 후 Git 통합한다. 제품·테스트 소스를 동결했다.

### [리뷰·실제 인수] 2026-11-09 14:39 KST

- 최종 immutable JAR와 FE round3로 `qa/m6-acceptance.mjs`를 실행한 evidence를 root가 직접 확인했다. **52checks PASS / 0 FAIL**, 누락 항목 없음. 실제 Chrome1440px에서 댓글/답글 작성·독립 페이지·수정·soft-delete·좋아요/취소·새로고침·실패 rollback 및 원본 화면 대조를 수행했다.
- 추가 권한 인수: 합성 owner가 글을 PUBLIC→PRIVATE로 수정한 뒤 actor의 기존 화면에서 like가 LIKE001, 후속 interactions GET이 POST002를 반환했다. 좋아요 count/버튼·댓글·댓글 action이 폐기되는 6개 검증이 모두 통과했다. 합성 fixture 외 사용자 데이터는 변경하지 않았다.
- artifact는 ignored `build/m6-acceptance/evidence.json` 및 screenshot3개다. 앞선46건은 이전 실행 증거이며 이52건이 최종 실제 인수 증거다. 독립 QA의 최종 기록 반영 후 명시 파일 commit/PR/dev merge를 진행한다.

### [머지·완료] 2026-11-09 14:52 KST

- 독립 최종 QA는 **M6 PASS / merge-ready Yes**로 판정했고 필수 gate와 finding을 모두 닫았다. 구현 커밋 `55aedea966a45dc0e88a6b0d8926e709b366050e`를 push하고 [PR #12](https://github.com/0-virus/zero-verse/pull/12)를 `dev` 대상으로 생성했다.
- GitHub의 mergeable=true, head SHA 일치를 확인한 뒤 expected-head 조건으로 병합했다. 결과 `merged=true`, merge SHA `4399f73acf19d66d96fafd2c4784c6a03fcfa282`. 설정된 commit status는 빈 목록이며 실제 판정은 위 로컬/독립 검증 증거를 따른다.
- root가 origin/dev를 fetch하고 로컬 dev를 fast-forward했다. 구현 SHA와 merge SHA의 diff가 비었고 두 tree가 `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`로 일치한다. 검증 뒤 제품 소스 변경은 없다.
- API·FE 검증 프로세스와 합성 MySQL 컨테이너를 중지했고 Colima VM 정상 종료(exit0)를 확인했다. 새 테스트 컨테이너 잔여는 없으며 기존 합성 DB·업로드·증거 파일·사용자 .local-data/.env.local은 보존했다.
- README의 M5 이후 미포함이라는 오래된 실행 안내를 현재 댓글·답글·좋아요 사용 위치와 M4/M5/M6 검증 기록 링크로 갱신했다. 역할 STATE/WORKLOG 및 PRD 현황은 소유자별 closeout으로 동기화한다. 이번 실행의 M6 Task1~6은 완료이며 M7 이후 구현은 포함하지 않는다.
