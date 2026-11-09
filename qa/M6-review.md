# M6 — 댓글·좋아요 독립 QA 최종 검토

> **상태: 최종 독립 QA PASS — 기능/API·자동검증·Chrome 1440px 인수 완료**
> 확인일: 2026-11-09 KST
> `docs/governance/meetings/M6-20261109-comments-likes.md`의 최신 A-v2는 `APPROVED`이며 `docs/REQUIREMENTS.md` FR-LIKE-03/확정 세부 계약과 `docs/PRD.md`에 동기화됐다. 아래 판정은 저자와 분리된 독립 QA의 실제 소스·테스트 출력·HTTP·Chrome 증거를 대조한 결과다. 승인 계약이 정정되면 아래 매트릭스를 재기준화한다.

## 1. 범위와 판정 경계

- 근거 우선순위는 사용자 결정/PRD §9 → `docs/design/` → `docs/REQUIREMENTS.md` → `docs/PRD.md` → 승인된 governance다. M6 회의 A-v2는 사용자 승인된 governance다.
- 검토한 정본: `docs/REQUIREMENTS.md` §4/§6.7/§6.8 FR-COM-01~04/FR-LIKE-01~03/NFR-04/09 및 M6 확정 세부 계약; `docs/PRD.md` §5.7/5.8, §7.2, §9.2-P/§9.3-③/§9.9, §10 M6, §11/12; `docs/design/DESIGN-SYSTEM.md`, `docs/design/ZeroVerse Pages.dc.html`; M6 worklog와 승인 회의록 A-v2.
- 현재 회의 상태는 `APPROVED`·위험 `HIGH`다. 구현·테스트·실제 인수는 이 문서의 최종 증거로 완료 판정했다.
- 이번 준비에서 backend/frontend 제품 코드, 타 역할 테스트, `docs/PRD.md`, `docs/REQUIREMENTS.md`, governance, `docs/worklog/M6-comments-likes.md`, Git을 수정하지 않았다. 이 파일과 QA 역할 상태·워크로그만 QA 소유 산출물이다.
- FR 표는 backend 최종 XML/소스 대조, 실제 HTTP·Chrome, MySQL 경쟁/query-bound/no-notification 증거를 반영한다. M6 외 범위(예: M8 notification 생성, BlogPage/PostCard count)는 PASS 범위에 포함하지 않는다.

## 2. 현재 소스 baseline 읽기 결과

> 아래 baseline 표는 구현 전·부분 구현 단계에서 기록한 **당시 관측의 보존 기록**이다. 최종 source와 gate 판정은 §8~§11 및 최종 자동검증/HTTP 증거가 우선한다.

| 영역 | 현재 확인 | QA 의미 |
|---|---|---|
| DB | `src/main/resources/db/migration/V1__init.sql`에 `comments`와 `post_likes`가 이미 존재한다. `comments`는 `parent_id` FK/index와 `deleted_at`, `post_likes`는 `(post_id,user_id)` unique를 가진다. | V1 변경·신규 migration 없이 매핑할 수 있는지는 구현 diff에서 확인한다. `comments.parent_id`가 같은 post/root만 허용하도록 DB가 보장하지 않으므로 애플리케이션 경계와 실제 MySQL 검증이 필수다. |
| Backend domain | 현재 diff에는 `Comment`·`Like` entity와 `PostInteractionLockService` 초안이 보이지만, 댓글/좋아요 repository·service·controller/OpenAPI 경계는 아직 완성되지 않았다. `ErrorCode`에는 COM_001~003과 LIKE_001이 이미 있다. | 부분 구현을 최종 API 계약으로 간주하지 않는다. 신규 DTO, ACL, transaction/lock, query bound, OpenAPI를 구현 closure 후 독립 검토한다. |
| 게시글 접근 | `PostAccessPolicy.canRead`가 삭제된 post/blog/owner를 거부하고 PUBLIC·owner·ACCEPTED UNIVERSE를 판정한다. `PostService.get`은 `findByIdAndDeletedAtIsNullForUpdate` 후 조회수를 기록한다. | comments/interactions GET이 `PostService.get`을 재사용해 조회수를 올리거나, 접근 판정 전에 상태를 노출하지 않는지 확인한다. |
| 잠금 | 현재 `BlogRepository.findByIdAndDeletedAtIsNullForUpdate`는 `JOIN FETCH b.user`이고, `PostRepository.findByIdAndDeletedAtIsNullForUpdate`도 blog/user를 fetch한다. `PostService.get`은 post row를 직접 잠근다. | A안의 `blog scalar lock → 관련 user ID 오름차순 → post → comment/like`와 현재 코드의 실제 lock 순서 사이 차이를 구현 diff·MySQL 경쟁에서 확인한다. deadlock 회피를 가정하지 않는다. |
| Security/API | `SecurityConfig`는 숫자 post 상세 GET만 공개 permitAll이며 comments/interactions GET 경로는 아직 없다. `PostController`는 잘못된 Bearer를 `rejectInvalidBearer`로 거부한다. | A안의 정확한 숫자 postId GET만 optional auth로 공개하고, 잘못된 Bearer를 guest로 downgrade하지 않는지 검증한다. |
| Frontend | 현재 diff에는 `features/comment/**`·`features/like/**` API/types/컴포넌트와 `PostDetailPage` 연결 초안·M6 단위 테스트가 보인다. | 부분 구현을 최종 기능으로 간주하지 않는다. 실제 backend API와 연결된 최종 diff 후에만 1440px 화면·계정 전환·rollback·stale response를 검증한다. mock/feature-level 테스트만으로 PASS하지 않는다. |
| 디자인 | Pages 원본 게시글 상세는 `max-width:980px`, 본문 아래 댓글 카드, `■ 댓글 N`, 34px avatar, 입력 placeholder `따뜻한 신호를 남겨주세요…`, `전송`, `♥ 좋아요 N`을 그린다. PRD §9.3-③은 1단계 답글 들여쓰기+구분선을 요구한다. | 실제 Chrome 1440px에서 원본과 제품을 나란히 대조하되, 원본의 평면 예시는 PRD의 1단계 답글 요구를 override하지 않는다. |

## 3. 승인된 A-v2의 독립 검증 주의점

아래 계약은 `REQUIREMENTS.md`와 `PRD.md`에 동기화된 기준이다. QA는 구현이 이 기준과 실제 diff/응답/DB/브라우저 증거에 일치하는지 확인하며, 임의로 완화하지 않는다.

| ID | 발견한 정합성 쟁점 | 영향 |
|---|---|---|
| M6-CONTRACT-01 | 승인 계약은 M6에서 notification row를 만들지 않고 M8에서 FR-NOT-05를 연결한다. | 댓글/답글/좋아요 mutation 전후 notifications row가 늘지 않는지 확인하고, M8 범위를 M6 PASS에 섞지 않는다. |
| M6-CONTRACT-02 | 승인된 FR-LIKE-03은 `GET /api/v1/posts/{postId}/interactions`와 상태 DTO, optional auth, no-store, 조회수 불변을 정본에 추가했다. | OpenAPI·실제 HTTP·guest/ACL·invalid Bearer·조회수/headers를 모두 대조한다. |
| M6-CONTRACT-03 | 승인된 세부 계약은 댓글 1~2000 UTF-16 plain text, `CommentItem` flags/replies, 삭제·탈퇴 placeholder, root/reply PageResponse와 count 규칙이다. | 구현 DTO, validation, escaped DOM, deleted root GET/create 분리, count SQL을 정본과 대조한다. |
| M6-CONTRACT-04 | 승인된 lock order는 scalar blog → 관련 user IDs 오름차순 → post → comment/like이며 기존 `PostService.get`도 기존 ACL/view 정책을 보존한 채 순서를 맞춘다. | 실제 source lock trace와 MySQL 교차 경쟁에서 deadlock/500/1차 캐시 stale를 재현·배제한다. |
| M6-CONTRACT-05 | 승인된 차단/like 정책은 양방향 BLOCKED의 새 like 거부, 읽기 가능한 PUBLIC의 기존 like unlike 허용, ACL 상실 시 row 보존·unlike 거부·권한 회복 후 재취소다. | block/unblock·UNIVERSE 철회·PRIVATE 전환의 HTTP 상태와 DB row/count를 확인한다. |
| M6-CONTRACT-06 | 승인된 삭제 root의 기존 reply GET은 허용하되 새 reply는 거부하고, count는 살아 있는 root+reply 중 삭제/탈퇴 placeholder를 제외한다. M6 시각 인수는 PostDetailPage에 한정하며 BlogPage/PostCard count는 후속이다. | 삭제 parent 조회/create, count, PostDetail 시각 범위, BlogPage 비변경을 각각 독립 확인한다. |

## 4. FR별 독립 인수 매트릭스

| FR | 승인 후 확인할 계약 | 필수 독립 증거 | 상태 |
|---|---|---|---|
| FR-COM-01 목록 | `GET /api/v1/posts/{postId}/comments`; 접근 가능한 PUBLIC은 guest도 조회, PRIVATE/UNIVERSE/draft guest는 AUTH_004, 인증 viewer ACL 불가는 POST_002, root+1단계 reply, deleted comment는 `content=null,deleted=true`. 삭제된 root는 tree 위치와 기존 reply 보존을 위해 GET 가능하되 새 create parent는 금지한다. 승인 A-v2의 root `createdAt DESC,id DESC`, root page/child page, size 최대100, `private,no-store`, optional auth/invalid Bearer 규칙을 적용한다. | 실제 HTTP guest query/page/parentId·invalid Bearer·no-store·Chrome page2; `CommentBehaviorTest`/`CommentBatchTest`의 deleted-root, count, 100-root/20-reply bound; 최종 BE XML 490/0/0/0. | **CLOSED — backend source/MockMvc/MySQL 및 실제 HTTP/Chrome 증거 일치** |
| FR-COM-02 작성 | `POST /api/v1/posts/{postId}/comments`; 로그인·ACTIVE actor·현재 post read 권한·차단 없음. root/reply 생성, 같은 post의 살아 있는 root만 parent, 삭제 root create는 거부, parent의 parent는 COM_002. 승인 A-v2의 1~2000 UTF-16/trim/HTML literal을 적용한다. | 실제 Chrome root/reply create; `CommentBehaviorTest`의 blocked/suspended/depth/length/parent ACL; `CommentControllerTest` validation/envelope; 경쟁·rollback·no-notification XML. | **CLOSED — backend source/tests 및 실제 Chrome 증거 일치** |
| FR-COM-03 수정 | `PUT /api/v1/comments/{commentId}`; 작성자만, 삭제 comment는 수정 불가, post ACL 유지. | 실제 Chrome edit; `CommentBehaviorTest` author/삭제·SUSPENDED 경계, 공통 lock/cross-race 및 full BE XML. | **CLOSED — backend source/tests 및 실제 Chrome 증거 일치** |
| FR-COM-04 삭제 | `DELETE /api/v1/comments/{commentId}`; comment author 또는 post owner, soft delete, 자식 reply 보존. 승인 A-v2의 idempotent 재삭제·삭제 root 신규 reply 금지를 적용한다. | 실제 Chrome soft-delete/placeholder/reply preservation; `CommentBehaviorTest` 삭제 root/reply와 `M6InteractionCrossRaceMySqlTest` post/root 경쟁, no-notification XML. | **CLOSED — backend source/tests 및 실제 Chrome 증거 일치** |
| FR-LIKE-01 추가 | `POST /api/v1/posts/{postId}/likes`; 로그인·post read ACL·(post,user) unique·중복 POST idempotent. 승인 A-v2는 양방향 BLOCKED에서 새 like를 거부하고, SUSPENDED·삭제/비공개 경계는 기존 ACL을 따른다. | 실제 Chrome like/rollback; `LikeControllerTest`, `LikeConcurrencyTest`, `M6InteractionConcurrencyMySqlTest`/`CrossRace`의 duplicate·block·suspended·deleted·UNIVERSE·competition·no-notification; OpenAPI tests. | **CLOSED — backend source/tests 및 실제 HTTP/Chrome 증거 일치** |
| FR-LIKE-02 취소 | `DELETE /api/v1/posts/{postId}/likes`; row가 없어도 성공, 있으면 hard delete. 승인 A-v2는 읽기 가능한 PUBLIC의 기존 like unlike는 허용하고, ACL 상실 시 row를 보존한 채 unlike를 거부한다. | 실제 Chrome unlike/refresh/rollback 및 access-loss; `LikeControllerTest` row preservation/ACL/block/SUSPENDED, concurrency/cross-race XML. | **CLOSED — backend source/tests 및 실제 HTTP/Chrome 증거 일치** |
| FR-LIKE-03 상태 GET | `GET /api/v1/posts/{postId}/interactions`의 `postId,commentCount,likeCount,liked,canComment,canLike,canUnlike`; guest liked/flags false, no-store, 조회수 불변. | 실제 guest/invalid Bearer/no-store HTTP·Chrome refresh/access-loss; `LikeControllerTest` guest/UNIVERSE/deleted-root/view-count; OpenAPIConfig 5 tests; full BE XML. | **CLOSED — backend source/tests 및 실제 HTTP/Chrome 증거 일치** |

공통으로 모든 성공/실패는 `success/data/error/timestamp` envelope, 승인된 HTTP status, NFR-04 error code를 확인한다. 인증 endpoint가 아닌 공개 GET의 잘못된 Bearer는 AUTH_004로 거부되어 guest 응답으로 바뀌지 않아야 한다.

## 5. 필수 실제 HTTP·Chrome 1440px 시나리오

### 5.1 실제 API 기본 흐름

1. 합성 계정 A(owner), B(active), C(accepted UNIVERSE), D(blocked), S(SUSPENDED)와 PUBLIC/UNIVERSE/PRIVATE/draft/deleted post를 준비한다. 기존 사용자·DB를 삭제하지 않고 격리 fixture만 사용한다.
2. guest가 PUBLIC PostDetail을 연다. 본문과 댓글 목록/interactions를 읽고, 좋아요·댓글 입력은 로그인 유도로 제한되는지 확인한다. PRIVATE/UNIVERSE/draft guest는 기존 AUTH_004/ACL 계약을 확인한다.
3. A/B가 root 댓글을 만들고 B가 A root에 reply를 만든다. root page와 `parentId` reply page를 각각 읽고, A가 자기 댓글을 수정·삭제하며, post owner가 타인 댓글을 삭제한다.
4. 삭제 root를 다시 읽어 placeholder와 기존 reply 보존을 확인하고, 삭제 root의 기존 reply GET은 허용하되 삭제 root를 parent로 새 reply하는 요청은 거부되는지 확인한다. reply의 parent 지정은 COM_002다. interactions count는 살아 있는 root+reply만 세고 삭제/탈퇴 placeholder는 제외한다.
5. A/B가 like POST/DELETE를 반복하고 새로고침 후 서버 canonical count/liked를 확인한다. 양방향 BLOCKED의 새 like 거부, 읽을 수 있는 PUBLIC의 기존 like unlike 허용, ACL 상실 시 row 보존·unlike 거부를 각각 확인한다. M6 승인 계약상 notifications row는 증가하지 않아야 하며 FR-NOT-05 생성은 M8 범위다.
6. A→B account switch 중 comments/interactions/list 응답이 늦게 도착하도록 지연시킨다. 이전 계정의 댓글·liked·can* 상태가 새 계정 화면에 섞이지 않아야 한다. post route A→다른 post 전환, logout→guest 전환도 같은 방식으로 확인한다.

### 5.3 self-contained 합성 acceptance runner

- `qa/m6-acceptance.mjs`는 기존 DB를 청소하거나 알려진 사용자 데이터를 건드리지 않고, random email/password·slug를 메모리에서 만들어 register→signin→initial-setup→PUBLIC post 2개 생성 후 synthetic 대상 ID만 evidence에 기록한다.
- runner는 API로 root 댓글 21개와 한 root의 답글 21개를 seed한 뒤, 시스템 Chrome 1440px에서 실제 로그인·root 댓글 입력/전송·답글 입력/전송·root page 2·reply page 2·댓글 수정·삭제·좋아요·취소·새로고침을 수행한다. POST/PUT/DELETE response와 DOM canonical 상태를 함께 확인하며 실패 like 응답을 주입한 rollback check도 포함한다.
- actor browser flow 끝에는 owner token을 메모리에만 사용해 같은 합성 PUBLIC 글의 전체 `UpdatePostRequest`를 `PUT`으로 `PRIVATE`로 바꾼다. 열린 actor 화면에서 stale 좋아요를 다시 눌러 `LIKE_001`을 확인하고, 후속 canonical interactions GET `POST_002` 및 좋아요 count/댓글·댓글 action 비노출을 확인하는 읽기 권한 상실 시나리오를 실행했다.
- 계정·글·댓글·좋아요 생성은 이 random synthetic 범위에서만 허용한다. credentials, password, access token은 stdout/evidence에 기록하지 않으며, HTTP denied/no-store/invalid Bearer와 notification row 불변은 backend 독립 증거와 조합한다. 최종 immutable runtime runner는 **52/52 PASS**했고 fixture ID만 JSON에 남겼다.

### 5.2 1440px 원본 대조

- 실제 API가 연결된 시스템 Chrome에서 CSS viewport `1440×1000`, device scale 1로 `/blog/{slug}/{postId}`를 연다. mock-only 화면은 시각 PASS 증거로 사용하지 않는다.
- Pages 원본의 게시글 상세와 제품을 대조한다: 단일 `980px` 컬럼, 본문 카드 아래 별도 댓글 카드, `■ 댓글 N`, 34px avatar, 13.5px 본문, `따뜻한 신호를 남겨주세요…`, `전송`, `♥ 좋아요 N`, 잉크 보더/하드 shadow, 1단계 reply 들여쓰기+구분선.
- guest/owner/other/blocked 상태에서 제한 안내·액션 표시가 달라지는지 확인한다. 화면에서 private data, 이메일, 내부 차단 방향, 삭제 사용자 개인정보가 노출되면 FAIL이다. 이 M6 시각 범위는 PostDetailPage이며 BlogPage/PostCard count 변경은 검증·완료 주장 범위가 아니다.
- 새로고침과 A↔B 계정 전환 후 count, comment page, like 상태가 실제 API canonical 값으로 재조회되는지 확인한다. 디자인 원본의 예시 댓글 수는 제품 데이터 기대값으로 사용하지 않는다.

## 6. MySQL 경쟁·rollback·query bound 시나리오

### 6.1 경쟁과 lock ordering

- 같은 B가 같은 PUBLIC post에 like POST를 N회 동시 실행(`N=20`, 경계 반복은 `N=100`): 성공은 승인된 정상/4xx만 허용하고 `post_likes`는 1행, count는 1 증가여야 한다.
- 같은 like row에 DELETE N회, POST/DELETE 교차 N회: duplicate row, 음수/과다 count, COMMON_500, deadlock 미복구를 허용하지 않는다. 최종 상태는 응답 순서가 아니라 commit 후 DB canonical 상태로 판정한다.
- 서로 다른 사용자들의 root comment 동시 생성과 같은 root에 reply 동시 생성: id가 중복되지 않고 모두 같은 post에 속해야 한다. reply의 parent parent는 항상 거부한다.
- root delete↔reply create, comment edit↔delete, post delete↔comment/like, Universe block/unblock↔comment/like를 동시에 실행한다. 결과는 정의된 성공 또는 4xx만 허용하고, 삭제 후 새 reply/like, soft-delete row 복원, post 접근 우회, 500을 금지한다.
- lock trace/SQL log와 DB 결과로 `blog scalar → user IDs ascending → post → comment/like`(승인된 A안이 유지될 경우)를 확인한다. 두 사용자가 actor/owner를 역순으로 제공해도 같은 순서로 잠겨야 하며, 테스트가 우연히 한 스레드씩 실행된 경우 PASS로 취급하지 않는다.

### 6.2 query bound와 전체 도달성

- root 100개 이상, 각 root reply 21개 이상을 만든다. `size=20` root page는 root 20개와 각 root의 최초 bounded replies만 반환하고, `parentId` page를 넘겨 reply 전체를 도달할 수 있어야 한다. `size=101`, 음수 page/size, 큰 offset은 승인된 VALIDATION/empty-page 계약을 확인한다.
- A-v2가 유지되면 root100에서 MySQL `ROW_NUMBER() OVER(PARTITION BY parent_id ORDER BY created_at DESC,id DESC)`·`COUNT(*) OVER(PARTITION BY parent_id)` 기반으로 최대 2000개 최초 reply와 total을 batch로 공급하는지 확인한다. exact SQL은 구현자 선택이지만 per-root repository 호출은 허용하지 않는다.
- SQL statement count를 실제 MySQL에서 측정한다. root 개수 N을 20→100으로 늘렸을 때 child 조회 수가 N에 비례하면 N+1 FAIL이다. root page와 reply batch/count 쿼리는 고정 상수 범위여야 하며, exact query 수는 구현 방식에 따라 기록하되 per-row finder를 허용하지 않는다.
- `commentCount`는 승인된 A안 기준 살아 있는 root+reply와 탈퇴 사용자 제외 여부를 SQL row와 대조한다. `likeCount`는 살아 있는 사용자 like만 포함하는지 확인한다. 삭제 comment/like row를 읽기 요청이 변경하지 않아야 한다.
- `interactions`/comments GET이 `PostService.get` 경로를 호출해 view count/view ledger를 증가시키지 않는지 전후 DB를 비교한다.

### 6.3 transaction rollback

- validation, ACL, blocked, deleted parent, unique race, post deleted 등 모든 실패 요청 후 comment/like/notification row와 count가 부분 반영되지 않아야 한다. M6 승인 계약상 notifications row는 처음부터 증가하지 않는다.
- DB exception/deadlock 재시도 또는 실패를 유도해 transaction 경계 밖의 orphan comment, like count drift, 삭제 상태 되돌림이 없는지 확인한다. 실패 응답은 COMMON_500이 아닌 정의된 4xx가 가능한 경우 4xx를 우선한다.
- FE에서 like POST/DELETE 4xx·network failure를 가로채 optimistic count/color가 이전 canonical 값으로 rollback되고 중복 클릭이 잠기며, comment create/update/delete 실패 시 입력값과 기존 행이 보존되는지 확인한다. 성공 뒤에는 server canonical reload를 확인한다.

## 7. account stale·개인정보·공통 보안

- AuthContext 계정 A에서 댓글 목록/interactions 요청을 보낸 뒤 B로 로그인 전환한다. A 응답이 B 화면에 반영되면 FAIL이다. `postId`, `blogSlug`, page, `parentId`가 바뀐 뒤 이전 요청의 성공/실패도 무시해야 한다.
- guest→signin, signin→logout, 만료/잘못된 Bearer, SUSPENDED actor를 각각 실제 HTTP로 확인한다. 잘못된 Bearer는 공개 GET guest 결과로 강등하지 않고 AUTH_004다.
- 댓글 author DTO는 승인 계약의 최소 필드만 제공하고 삭제 사용자 author/content placeholder가 개인정보를 재노출하지 않아야 한다. blocked 방향·이유, email/name/birthDate/bio는 응답·DOM·OpenAPI example에 없어야 한다.
- comments/interactions 성공 응답은 승인된 경우 `Cache-Control: private, no-store`; 게시글 조회수·anonymous ledger와 분리된다. OpenAPI에는 bearer/optional auth, 201/200, error envelope와 DTO가 실제 경로별로 문서화되어야 한다.

## 8. 최종 QA gate 결과

최종 source 동결본, backend/frontend 자동검증, MySQL 경쟁/query-bound/no-notification 증거, 실제 HTTP·Chrome 1440px 실행을 모두 대조했다. 이전 46-check 중간 실행은 보존하되, 최종 판정에는 FE round3와 access-loss를 포함한 52-check 실행만 사용한다.

### 현재 판정

- 제품 구현: **최종 source 동결본 독립 대조 완료**. FE WR-01/02/03은 whole-review 재검토에서 ADDRESSED로 확인됐고 새 Critical/Important finding은 없었다.
- 자동 테스트: backend **77 suites / 490 tests / failures 0 / errors 0 / skipped 0**, frontend **34 files / 369 tests / failures·errors·skips 0**, lint/build exit 0. source 연결 hash와 built/runtime JAR SHA가 `docs/worklog/M6-comments-likes.md` 최종 자동 검증 증거와 일치한다.
- MySQL 경쟁/query bound/rollback/no-notification/ACL: `CommentBatchTest`, `LikeControllerTest`, `LikeConcurrencyTest`, `M6InteractionConcurrencyMySqlTest`, `M6InteractionCrossRaceMySqlTest` 및 전체 XML을 직접 대조해 **CLOSED**.
- 실제 HTTP/Chrome: final immutable runtime `52 PASS / 0 FAIL`, 1440×1000 CSS viewport, guest/invalid Bearer/no-store/query string/root·reply page2/mutation/refresh/rollback/access-loss를 확인해 **CLOSED**.
- M6 기능/API/시각 최종 인수: **PASS**. BlogPage/PostCard count 후속 범위와 M8 notification 생성은 M6 판정에서 제외한다.
- QA blocker: **없음**. Git 통합·마일스톤 closeout은 root 소유 후속 단계다.

## 9. FE 독립 source review — 2026-11-09 (round1 재검토)

검토 기준은 `dev c46a26d` 대비 `frontend/**` diff와 untracked M6 feature/test 파일, 승인 회의 §6 A-v2, REQUIREMENTS §6.7~§6.8/FR-LIKE-03, PRD §9.9/§7.2/§11~§12, `docs/design/ZeroVerse Pages.dc.html`·`DESIGN-SYSTEM.md`다. 이 절의 최초/round1 수치는 당시 관측을 보존하며, 최신 FE `34 files / 369 tests / lint / build`, round2/round3 재검토 및 최종 독립 판정은 §8·§10·§11이 덮어쓴다. 아래 세부 finding은 최초 근거를 보존하고 각 항목의 후속 closure를 연결한다.

### FE-001 — Important — mutation 성공 뒤 canonical GET 실패가 성공 결과를 되돌림 (초기 finding; CLOSED after round1)

- **근거:** `frontend/src/features/comment/CommentSection.tsx:228-242`의 `refreshCanonical`은 root/reply 또는 `onRefreshInteractions()` GET 실패를 throw한다. 그런데 create `:314-328`, edit `:357-367`, delete `:390-399`의 넓은 `catch`가 서버 mutation 이후에도 `beforeRoot`/`beforeReplies`를 복원하고 실패 안내를 표시한다.
- **재현:** 댓글 create/edit/delete 또는 reply가 201/200으로 커밋된 직후 comments/interactions canonical GET을 network error/5xx로 지연·실패시킨다. API mutation은 성공했지만 화면은 이전 댓글/본문 상태로 돌아가고, create는 입력을 지운 뒤 실패처럼 보인다. 사용자가 재시도하면 중복 create 또는 이미 삭제된 row에 대한 혼란이 생긴다.
- **계약 영향:** PRD §9.9/REQUIREMENTS M6는 mutation 성공 후 canonical 재조회가 느리거나 실패해도 서버가 수락한 canonical 결과를 잃지 않고, optimistic 임시 id를 서버 id로 교체해야 한다. 현재 `:317-323` 주석의 의도와 실제 catch가 모순된다. 기존 `m6PostDetailBehavior.test.tsx:291-305`는 이 실패를 “댓글 작성 실패”로 받아들이고 서버 canonical 결과가 유지되는지 증명하지 않는다.
- **필요 조치:** mutation API 성공과 후속 재조회 실패를 분리한다. 성공 응답 DTO/soft-delete 상태는 유지하고, GET 오류만 별도 stale/error·재시도 상태로 표시하며 accepted mutation을 rollback하지 않아야 한다. 동일 패턴을 root/reply create, edit, delete 모두 검증한다.
- **round1 재검토:** 현재 `CommentSection.tsx:335-369,389-421,428-463`는 mutation API 호출 실패에만 `beforeRoot`/`beforeReplies` rollback을 수행하고, 성공 후 `refreshCanonical` 실패는 `canonicalReloadTarget`·재시도 안내만 남긴다. create는 서버 `created` DTO로 임시 ID를 즉시 교체하고 edit/delete도 accepted 상태를 유지한다. `m6PostDetailBehavior.test.tsx:298-344`가 create/edit/delete 각각 canonical GET 실패 후 상태 보존·재시도를 확인한다. **source/unit 기준 CLOSED.**

### FE-002 — Important — post/account 세대 전환 뒤 좋아요 busy 상태가 영구 고착될 수 있음 (초기 finding; CLOSED after round1)

- **근거:** `frontend/src/pages/PostDetailPage.tsx:75-112`는 post/account 변화 때 `pageGeneration`을 증가시키고 interaction을 초기화하지만 `isLikeBusy`를 초기화하지 않는다. 좋아요 mutation의 `:183-193` finally는 세대가 다르면 `setIsLikeBusy(false)`를 건너뛴다. 버튼은 `:281-289`에서 `isLikeBusy`가 true이면 계속 disabled다.
- **재현:** A 계정에서 like POST/DELETE를 pending으로 만든 뒤 다른 post로 이동하거나 B 계정으로 전환한다. 새 세대의 interactions는 정상으로 로드되어도 이전 요청의 finally가 세대 불일치로 skip되므로 새 화면 좋아요 버튼이 계속 disabled된다. invalid route 전환도 같은 reset 누락을 가진다.
- **계약 영향:** PRD §9.9의 post/account stale guard는 늦은 응답을 무시하면서 새 화면의 mutation이 정상 동작해야 한다. 현재 테스트 `frontend/src/test/m6PostDetailBehavior.test.tsx:231-248`은 늦은 응답이 새 화면을 덮지 않는지만 확인하고 새 버튼이 다시 enabled인지 확인하지 않는다.
- **필요 조치:** 세대 전환 시 busy를 명시적으로 false로 reset하거나 mutation busy를 post/account generation에 귀속해 새 세대가 독립적으로 동작하게 한다. stale response를 무시하는 기존 guard는 유지한다.
- **round1 재검토:** 현재 `PostDetailPage.tsx:76-108`의 post/account 변경 effect가 interaction 초기화와 함께 `setIsLikeBusy(false)`를 수행하고, `m6PostDetailBehavior.test.tsx:234-258`가 계정 전환 뒤 새 like 버튼 enabled 및 새 mutation 가능을 확인한다. 늦은 응답은 기존 generation guard로 무시된다. **source/unit 기준 CLOSED.**

### FE-003 — Important — root page 전환 중 오래된 reply GET이 새 root page에 재주입될 수 있음 (초기 finding; CLOSED after round1)

- **근거:** `frontend/src/features/comment/CommentSection.tsx:201-226`의 reply request guard는 root별 `replyRequestRef[rootId]`와 component generation만 검사한다. root page 전환 `:174-195`은 root request id만 증가시키고, 페이지를 떠나는 root의 reply request id를 무효화하지 않는다. 늦은 reply 응답은 `:209-214`에서 현재 `rootPage`와 `replyPages`에 다시 반영될 수 있다.
- **재현:** root page 0의 root A에 대해 reply page 1 GET을 지연시킨다. 응답 전에 root page 1로 이동한 뒤 다시 page 0으로 이동한다. component/account/post 세대는 동일하고 A의 reply request id도 증가하지 않으므로, 이전 page 전환 전에 시작한 응답이 현재 A 카드에 stale하게 덮어쓸 수 있다.
- **계약 영향:** A-v2는 root/reply를 독립적으로 순회하되 account/post/root/reply page stale 응답이 새 화면을 덮지 않아야 한다. 현재 테스트 `m6PostDetailBehavior.test.tsx:206-229`는 순차 page 2 호출만 증명하고 page 전환 중 지연 응답 race는 증명하지 않는다.
- **필요 조치:** root page generation/key를 reply 요청 guard에 포함하거나 root page 변경 시 해당 root들의 pending request id를 폐기하고, 현재 화면의 root/page와 일치할 때만 `setReplyPages`/`setRootPage`를 적용한다.
- **round1 재검토:** 현재 `CommentSection.tsx:151-226`에 `rootPageGenerationRef`가 있고 root page load마다 generation을 증가시켜 reply 응답 guard가 component generation·root page generation·root별 request ID를 모두 확인한다. `m6PostDetailBehavior.test.tsx:465-509`가 root page 0→1→0 중 지연 reply 응답이 재주입되지 않음을 확인한다. **source/unit 기준 CLOSED.**

### FE-004 — Minor — server capability/placeholder 테스트 범위가 부족함 (초기 finding; CLOSED after round1)

- **정적 확인:** source는 server flags를 대체로 존중한다. `PostDetailPage.tsx:273-289`는 guest를 `/signin` link로 보내고 `canLike/canUnlike`·busy를 적용하며, `CommentSection.tsx:405-430`은 `canReply/canEdit/canDelete`를 서버 DTO에서만 사용한다. `CommentRow.tsx:43-60`은 `author=null`, `content=null`, `deleted=true`를 `탈퇴한 사용자`·`삭제된 댓글` placeholder로 표시하고 후손을 렌더한다.
- **증거 공백:** `m6PostDetailBehavior.test.tsx:183-204`는 삭제 placeholder/후손을 확인하지만 deleted root의 reply action 비노출, reply의 `canReply=false`, 비작성자 `canEdit/canDelete=false` action 비노출을 함께 검증하지 않는다. owner와 author가 다른 댓글 delete 경계 및 server flags가 stale하게 바뀌는 경우도 없다.
- **판정:** source 정적 방향은 PASS 후보지만, 실제 backend DTO와의 통합 전에는 capability/placeholder gate를 닫지 않는다. FE unit test 보강 또는 실제 API/브라우저 증거가 필요하다.
- **round1 재검토:** `m6PostDetailBehavior.test.tsx:183-204,347-430`에 deleted root/reply action 비노출, `canReply/canEdit/canDelete=false` guard, 서버 `canDelete=true`인 비작성자 댓글의 owner-side delete UI 경계, guest CTA가 추가됐다. **source/unit 기준 CLOSED; 실제 backend DTO와의 통합 gate는 최종 acceptance에서 별도 확인한다.**

### FE-005 — Important — root page 왕복 뒤 stale reply의 loading 상태가 영구 잔류할 수 있음 (초기 finding; CLOSED after round2)

- **근거:** `CommentSection.tsx:209-235`의 `loadReplyPage`는 요청 시작 때 `replyLoading[rootId]=true`를 기록하지만, root page가 바뀌면 `rootPageGenerationRef`가 증가한다. stale 응답의 `finally`는 `rootPageGeneration` 불일치로 `setReplyLoading(...false)`를 건너뛴다.
- **재현:** root page 0의 root A에서 reply page 요청을 지연시킨 뒤 root page 1로 이동하고 다시 page 0으로 돌아온다. A가 다시 화면에 나타난 상태에서 이전 reply 요청이 완료되면 stale payload는 버려지지만 `replyLoading[A]`는 true로 남을 수 있다. 이후 A의 reply 영역이 `답글을 불러오는 중...`에 고착되고 reply pagination/error/action이 가려진다.
- **증거 공백:** `m6PostDetailBehavior.test.tsx:471-507`는 stale reply가 DOM에 재주입되지 않는지만 확인하고 `replyLoading` 해제·spinner 부재·재시도 가능성을 확인하지 않는다.
- **필요 조치:** stale request도 현재 root/page에 영향을 주지 않는 범위에서 loading 슬롯을 안전하게 해제하거나 generation별 loading map을 사용한다. root page 왕복 후 해당 root reply spinner가 사라지고 page 이동이 가능함을 회귀 테스트로 고정한다.
- **round2 재검토:** `loadRootPage` 시작 시 `replyRequestRef`, `replyLoading`, `replyErrors`를 세대와 함께 초기화하고(`CommentSection.tsx:212-218`), post/account reset도 같은 map을 비운다(`:189-201`). 따라서 root 전환으로 폐기된 stale request의 `finally`가 현재 loading 슬롯을 건드리지 않아도 잔류 슬롯을 만들 수 없다. round2 행동 테스트와 whole-review WR-01 재검토, 최종 369-test 회귀를 대조해 **CLOSED / source·unit 기준**으로 갱신한다.

### FE-006 — Important — canonical 권한 거부 시 이전 interaction/comments 상태를 계속 노출함 (초기 finding; CLOSED after round3)

- **근거:** `PostDetailPage.tsx:141-155`의 `refreshInteractions` catch는 `interactionError`만 설정하고 기존 `interaction`을 폐기하지 않는다. `CommentSection.tsx:181-203`의 `loadRootPage(..., { reportError:false })`도 `401/AUTH_004`, `403/POST_002`, `404/POST_001`에서 기존 `rootPage`·`replyPages`를 유지한 채 null만 반환한다.
- **재현:** mutation 성공 뒤 canonical interactions/comments GET이 `401/AUTH_004`(로그아웃/무효 viewer), `403/POST_002`(ACL 상실), `404/POST_001`(post/blog 삭제)로 응답하도록 한다. 화면은 이전 계정의 liked/count/can* 및 댓글 목록/액션을 남긴 채 오류만 표시할 수 있다.
- **계약 영향:** 회의 §6.3/PRD §9.9는 권한 상실 시 FE가 기존 interaction 화면 상태를 폐기하고 거부된 GET의 flags/count를 노출하지 않도록 한다. 이전 comments도 현재 post/ACL이 거부된 뒤 노출되어서는 안 된다. 이는 단순 network/5xx stale 보존 정책과 분리해야 한다.
- **필요 조치:** 권한·리소스 거부 분류(`401/AUTH_004`, `403/POST_002`, `404/POST_001`)에서는 interaction과 comments/replies를 명시적으로 clear하고 제한/오류 경계를 렌더한다. network/5xx는 accepted mutation 보존 정책을 유지하되, 각 분기를 테스트한다.
- **round3 재검토:** `PostDetailPage.tsx`의 초기/재조회/mutation access-denied 경로가 interaction을 폐기하고, `CommentSection.tsx`의 `clearReadState`가 root/reply/loading를 비운다. stale callback도 generation guard를 거친다. whole-review WR-02/03 ADDRESSED, FE 369 tests와 최종 browser에서 `LIKE_001 → POST_002` access-loss를 직접 확인했으므로 **CLOSED / source·unit·browser 기준**이다.

### FE API·payload 확인

- `frontend/src/features/comment/commentApi.ts:10-16,19-54`는 root/reply 모두 `page=0,size=20`, size 최대100, optional `parentId`와 `/posts/{postId}/comments`, `/comments/{commentId}` 경로를 사용한다. `frontend/src/features/like/likeApi.ts:9-26`은 `/interactions`, `/likes` GET/POST/DELETE 경로와 `InteractionState` canonical DTO를 사용한다.
- `CommentSection.tsx:292-333,342-371,373-403`은 trim·UTF-16 기반 JS length/2000 제한, root/reply parent 전달, plain-text 렌더(`CommentRow.tsx:19-25`)와 server action flags를 적용한다. FE-001~004는 현재 source/unit 기준 closure됐지만 API 경로·payload와 실제 DTO/브라우저 동작은 최종 acceptance에서 확인해야 한다.

### FE 독립 판정

- **FE-001/FE-002/FE-003: CLOSED / Important (source·unit 재검토).**
- **FE-004: CLOSED / Minor (source·unit 재검토).** 실제 backend DTO와의 통합은 최종 acceptance gate로 남긴다.
- **FE-005/FE-006: CLOSED / Important.** FE-005는 round2 세대 초기화와 WR-01 재검토, FE-006은 round3 권한 폐기·세대 guard와 WR-02/03 재검토로 닫혔다.
- FE 전체 자동검증 `34 files / 369 tests`, lint, build는 root fresh 실측 PASS다. 최종 browser/API/rollback/access-loss도 §10의 52-check evidence에 포함된다.

## 10. 독립 acceptance 실행·최종 closure — 2026-11-09 KST

- `qa/m6-acceptance.mjs`를 random synthetic fixture·Chrome 1440px·`M6_REQUIRE_FULL=1`로 실행했다. credentials/token은 stdout·artifact에 기록되지 않도록 설정된 runner를 사용했다.
- 첫 실행 당시 API는 listen 상태였으나 실행 중 원본 JAR가 재빌드 산출물로 교체되어 lazy class loading 오류가 발생했고, 그 결과 첫 `POST /api/v1/auth/register`가 30초 timeout으로 중단됐다. 이는 제품 계약 결함이 아닌 runtime 교체 경합으로 root 로그에서 교차 확인했다. 합성 계정·게시글·댓글·좋아요 생성과 브라우저 단계에는 진입하지 않았다.
- 당시 실패 실행은 `build/m6-acceptance/evidence.json`을 최종 실행이 덮어쓰기 전의 별도 중단 상태였으며, fixture/browser 단계에 진입하지 않았다. 이 실행은 FE/API PASS가 아니며 **runtime 준비 전 미실행/중단 이력**으로 분리한다.

### immutable runtime 중간 재실행 결과

- immutable JAR 기동 후 동일 harness를 재실행해 `46 PASS / 0 FAIL`, screenshot 3개(`design-post-detail.png`, `owner-initial.png`, `actor-final.png`)를 생성했다. HTTP guest/invalid Bearer/no-store, root/reply page2, root/reply 작성, edit, soft-delete, like/unlike, refresh, optimistic like rollback, 1440px viewport를 모두 확인했다.
- 결과는 `build/m6-acceptance/evidence.json`에 저장됐고 fixture ID만 기록됐다. 이는 FE-005/FE-006 수정 전의 FE/API 및 browser 중간 runtime 증거이며, 새 finding을 닫았다는 증거가 아니다. MySQL 경쟁/query bound/no-notification/ACL 전체 gate도 대체하지 않는다.

### 최종 immutable runtime 재실행 결과

- 최종 source/JAR 고정본(`runtime.jar` SHA-256 `f0cbf9c2fd027510b03a00ce752f2f39040f5870bafaef360895343856d056ab`)에서 `M6_REQUIRE_FULL=1`로 재실행해 **52 checks PASS / failures[]**를 얻었다. 기존 46개에 access-loss 6개가 추가됐다.
- 실제 HTTP는 guest comments/interactions, query string, malformed Bearer `AUTH_004`, `private,no-store`, authenticated `parentId` reply page를 확인했다. 실제 Chrome은 owner/actor 로그인, root/reply page2, root/reply create, edit, soft-delete, like/unlike, refresh, optimistic rollback, 1440px viewport를 확인했다.
- 추가 access-loss는 owner 전체 `UpdatePostRequest`로 PUBLIC→PRIVATE 전환 후 열린 actor 화면에서 `LIKE_001`, canonical `POST_002`, like count/action·comments/composer·comment actions clear를 모두 PASS했다. `build/m6-acceptance/evidence.json`에는 synthetic ID와 check 결과만 있으며 credentials/token은 없다.

### 시각 대조

- `design-post-detail.png`, `owner-initial.png`, `actor-final.png`를 직접 열어 Pages 원본과 제품을 1440px 기준 대조했다. 980px 단일 컬럼, 본문/댓글 카드 분리, 잉크 보더·하드 shadow, 댓글 헤더/입력 placeholder/전송, 1단계 reply 들여쓰기가 확인되어 **M6 PostDetail 시각 범위 PASS**다. PNG는 full-page 캡처라 높이는 내용에 따라 다르며 CSS viewport는 1440×1000이다.

### M6-BE-03 query-string 공개 matcher closure

- 기존 source finding은 query string이 포함된 실제 URL에서 RegexRequestMatcher가 comments GET을 보호 경로로 떨어뜨릴 수 있다는 것이었다. `.param`/MockMvc matcher 추정이 아니라, immutable runtime에 실제 HTTP URL을 호출해 closure를 확인했다.
- evidence의 `http-guest-comments`는 `GET /api/v1/posts/{postId}/comments?page=0&size=20` guest 200 및 공통 envelope/items를, `http-reply-page`는 인증 token으로 `GET /api/v1/posts/{postId}/comments?page=1&size=20&parentId={syntheticRootId}`의 page=1 응답을 PASS했다. `http-guest-interactions`도 guest interactions 200을 PASS했다.
- `http-invalid-bearer-comments`·`http-invalid-bearer-interactions`는 동일 실제 GET에 malformed Bearer가 guest downgrade되지 않고 AUTH_004 401임을 PASS했고, comments/interactions `private,no-store`도 각각 PASS했다. 따라서 **M6-BE-03: CLOSED (실제 query-string guest/page/parentId HTTP 증거)**로 기록한다. 나머지 backend 경쟁/ACL/query-bound/no-notification gate도 §8·§11의 최종 XML/소스 대조로 CLOSED다.

## 11. 최종 독립 판정

| 구분 | 판정 | 근거 |
|---|---|---|
| 기능/API 계약 | **PASS** | FR-COM-01~04/FR-LIKE-01~03 매트릭스 모두 CLOSED; backend source·MockMvc/MySQL·OpenAPI와 실제 HTTP/Chrome 대조 |
| 자동 검증 | **PASS** | BE 77 suites/490 tests, FE 34 files/369 tests, failures·errors·skips 0; FE lint/build exit 0 |
| 경쟁/query bound/rollback/no-notification | **PASS** | 100-root bounded replies, actual MySQL lock/race 8 tests, like concurrency, rollback/access-loss, notification 불변 증거 |
| FE stale/권한 경계 | **PASS** | FE-001~006 모두 CLOSED; WR-01/02/03 ADDRESSED, 최종 52-check access-loss 포함 |
| 시각 gate | **PASS (M6 범위)** | 원본·제품 full-page PNG 직접 대조, CSS viewport 1440×1000; 범위는 PostDetail만 |
| 전체 M6 최종 acceptance | **PASS / merge-ready: Yes** | 위 게이트 모두 종료. Git 통합·closeout은 root 후속 |

M6 범위 밖으로 명시된 BlogPage/PostCard count 변경과 M8 notification 생성은 이번 PASS에 포함하지 않는다. 합성 fixture는 임시 계정·글·댓글·좋아요만 사용했고 비밀 자격 증명은 산출물에 남기지 않았다.

증거 경계도 명확히 한다. 최종 Chrome/API runner가 직접 호출한 ACL 경계는 합성 PUBLIC 글의 guest·invalid Bearer·PRIVATE 전환 access-loss이며, PRIVATE/UNIVERSE/draft·SUSPENDED·BLOCKED·삭제 경계의 나머지 조합은 최종 backend MockMvc/service/MySQL 테스트와 source 대조로 닫았다. 따라서 “모든 조합을 브라우저에서 반복했다”는 의미의 판정은 아니다.
