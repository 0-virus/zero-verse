# Planning Review: M6 댓글·좋아요 계약

- 회의 ID: `M6-20261109-comments-likes`
- 관련 마일스톤: M6
- 상태: `APPROVED` — 2026-11-09 사용자 응답 `A-v2 전체 승인`
- 소집 사유: 공개 API·접근제어 구체화, backend/frontend 동시 변경
- 생성: 2026-11-09 KST / 진행자 Codex root
- worklog: [M6-comments-likes](../../worklog/M6-comments-likes.md)

## 1. 결정할 질문

기존 스키마·게시글 권한을 유지하면서 댓글의 제한된 페이지 조회, soft delete, 멱등 좋아요와 화면 상태 조회를 아래 A안으로 구현할 것인가?

## 2. 배경과 범위

- FR-COM-01~04/FR-LIKE-01~02가 댓글 1단계·권한·삭제·멱등성을 정하지만 DTO, 댓글 길이, 부모 삭제 후 답글, 페이지 단위와 viewer 좋아요 상태 공급원은 구체화되지 않았다.
- V1 `comments`/`post_likes`와 unique가 이미 존재한다. 해당 제품 entity/service/controller 및 FE 댓글·좋아요는 없다.
- 포함: 해당 FR 전체, OpenAPI, 실제 MySQL 경쟁/ACL 검증, PostDetailPage API 연결·1440px 디자인 인수.
- 제외: M7 feed/search, M8 notification row 생성, M9 관리 기능, S3/U0, 새 라이브러리·V1 변경·DB migration. 새로운 DB 필요성이 입증되면 재심의한다.

## 3. 근거 문서

| 근거 | 내용 |
|---|---|
| REQUIREMENTS §4 Comment/Like, §5, §6.7/6.8, FR-UNI-09, NFR-03/04/09 | 데이터·공통 paging·권한·오류·테스트·차단의 댓글/좋아요 제한 |
| PRD §5.7/5.8, §7.2 PostDetailPage, §9.2-P/§9.3-③, §10 M6, §11/12 | 화면·1단계 답글·낙관적 반응·범위·DoD |
| DESIGN-SYSTEM §8.3 및 ZeroVerse Pages.dc.html | 980px 본문 아래 댓글 카드·액션바·카피 |
| V1__init.sql comments/post_likes, PostAccessPolicy, PostService | 기존 unique와 owner/PUBLIC/UNIVERSE/draft predicate·잠금 |
| PostDetailPage.tsx, post/types.ts | 현재 본문·공유·수정/삭제·인접글만 구현 |

## 4. 선택지

| 선택지 | 이점 | 비용·위험 | 복귀 방법 |
|---|---|---|---|
| A — 기존 테이블+bounded 댓글 트리·상태 GET(권고) | 모든 답글 도달 가능, 사용자별 상태를 명시적으로 재조회, V1 유지 | 조회 계약 구체화와 1개 상태 endpoint 필요 | M6 API/FE 제거, DB 테이블 유지 |
| B — 루트만 페이징하고 답글은 전부 포함 | 단순한 중첩 응답 | 한 댓글의 답글이 늘면 응답·쿼리 무제한 | A 방식으로 후속 변경 |
| C — 전체 댓글 flat paging | 단순 DB 조회 | 부모와 답글이 분리돼 1단계 트리 표시·삭제 placeholder 복원 복잡 | 트리 계약으로 후속 변경 |

## 5. 독립 검토

제품성·기술 실현성·전달/위험 검토자는 동일 A안과 근거를 직접 읽고 상호 결과 공유 없이 검토한다. 각 검토는 read-only이며 제품 테스트를 실행한 결과가 아니다.

### Architecture — `/root/m6_architecture_review`

- APPROVE_WITH_CHANGES / HIGH / 확신도86. V1 재사용과 bounded 트리는 타당하다. 신규 endpoint·length·탈퇴 placeholder·count·삭제 parent 정책은 명시 승인과 정본 동기화가 필요하다.
- 필수 보완: 신규 GET만 공개하는 security matcher 및 invalid Bearer 검사, batch reply 쿼리 상한, unique 예외로 rollback-only가 되지 않는 like 직렬화, mutation별 실제 잠금 집합, M8 알림 인계. §6 및 실행계획에 반영했다.
- 반대 논거: 상태 GET·nested reply·탈퇴 표현까지 한 번에 고정하면 기대가 다를 때 BE/FE 재작업 비용이 생긴다.
- 실패 사례: guest comments가401, malformed Bearer 익명 강등, 동시 like500, root별 reply N+1, 삭제/차단과 쓰기 교착, count와 placeholder 불일치.
- 추가 실제 SQL 검토: BlogRepository의 join-fetch user lock과 PostService.get의 joined post 선점이 M6 순서와 반전될 수 있다. retry만 넣기보다 scalar blog lock과 기존 get의 순서 정렬을 먼저 적용하도록 권고했다. 이 최소 선행 수정은 §6.3에 반영하고 실제 MySQL 교차 경쟁으로 검증한다.

### Delivery & Risk — `/root/m6_delivery_review`

- APPROVE_WITH_CHANGES / HIGH / 확신도90. 공개 API·보안 계약은 사용자 승인 대상이며 V1 index로 시작할 수 있다. 실제 병목 증거 없이 migration을 추가하지 않는다.
- 필수 보완: 승인 후 정본/레지스터 동기화, backend Task1→2 단일 소유 순차 실행, guest private 접근401 구체화, 실행 명령·JVM temp·포트·XML·검증 SHA 기록, 차단 중 unlike 정책 명시. §6 및 worklog에 반영했다.
- 반대 논거: 기존 요구사항이 큰 범위를 이미 정한 상태에서 심의가 지연을 만들지만, 새로운 공개 동작을 사후 수정하는 비용이 더 크다.
- 실패 사례: 신규 GET 보안 누락, 잠금 순서 반전, block 후 like 생성, root별 N+1, M8과 중복 알림.
- count 관련 해석 주의: 답글은 제외하지 않는다. §6.1대로 살아 있는 root+reply를 포함하고 삭제/탈퇴 작성자 댓글만 제외한다.

### Product — `/root/m5_product_review`

- APPROVE_WITH_CHANGES / HIGH / 확신도89. M6 사용자 가치, V1 유지, bounded 탐색·복귀, 최소 author DTO는 적절하다. M5 승인과 M6 상세계약은 별개다.
- 필수 사용자 결정: FR-UNI-09의 차단당한 사람 제한을 양방향 차단 관계로 구체화할지, 접근권한 상실 후 unlike를 거부하고 기존 like를 보존할지. 이 두 선택을 사용자가 확정하기 전 해당 계약은 BLOCKED라고 판단했다. 진행자는 §6.3에 양쪽 결과를 명시해 A-v2 전체 승인 대상으로 올린다.
- 필수 보완: 삭제 root의 reply GET 허용·create 거부 분리, 살아 있는 root+reply count와 placeholder 제외, PostDetail에 한정한 시각 인수 및 BlogPage count 미구현 범위, M8 알림 정책은 폐기 아닌 후속 연결. §6과 worklog에 반영했다.
- 반대 논거: 보수적인 양방향 차단/ACL 기반 취소 제한은 일관되고 가역적이며, M8 분리는 중간 알림 상태를 피한다. 다만 실제 기대가 방향성 차단이라면 차단자도 like를 못 하는 동작이 오작동처럼 보일 수 있다.
- 실패 사례: 차단자 자신도 like 거부, UNIVERSE 관계 철회 후 취소 못 한 like가 PUBLIC 전환 시 다시 집계, 삭제 root21번째 답글 도달 실패, 댓글0과 placeholder 혼동, 목록 카드별 상태 GET waterfall.

## 6. A-v2 — 사용자 승인 대상 구체 계약

세 독립 검토의 공통 권고는 기존 V1 재사용·bounded 트리·별도 상태 조회다. 정본 대조로 차단 좋아요 누락을 바로잡고, 보안 matcher·삭제 root 답글 조회·실제 잠금 순서·단일 backend 소유권·검증 환경을 보완했다. **신규 endpoint, 2000자 제한, 양방향 차단, 접근권한 상실 후 unlike 거부, 탈퇴 placeholder/count 정의는 사용자 승인이 필요한 제안이며 아직 정본을 바꾸지 않았다.**

### 6.1 조회·응답

- 기존 6개 FR endpoint를 구현한다. 모든 성공 응답은 ApiResponse, page는 공통 PageResponse다. 댓글 create=201, 나머지 mutation=200이며 like POST 재호출도 200이다.
- `GET /posts/{postId}/comments?page=0&size=20`은 최상위 댓글 page다. 정렬은 공통 최신순을 구체화한 `createdAt DESC,id DESC`, size 최대100이다. 삭제된 root도 포함해 tree 위치를 보존한다.
- 각 root의 `replies`는 같은 PageResponse의 첫 페이지(size20), leaf는 `replies=null`이다. `GET /posts/{postId}/comments?parentId={rootId}&page=0&size=20`으로 해당 root의 답글 page를 조회한다. 답글도 같은 최신순이며 모두 순회 가능해야 한다. root page별 최초 답글을 bounded batch 조회해 N+1과 무제한 load를 피한다.
- 최초 replies는 MySQL8의 `ROW_NUMBER() OVER(PARTITION BY parent_id ORDER BY created_at DESC,id DESC)`와 `COUNT(*) OVER(PARTITION BY parent_id)`를 사용한 root ID batch로 제한한다. author는 LEFT JOIN 또는 ID batch로 조회한다. root100에서도 최대2000 replies와 고정 개수 SQL로 제한하며 per-root repository 호출을 금지한다. `parentId` 조회는 삭제 root도 허용해 보존된 답글에 도달할 수 있지만, 다른 post의 root나 reply ID는 COM_001/COM_002 규칙으로 거부한다.
- `CommentItem={id,postId,parentId,author,content,deleted,createdAt,updatedAt,canEdit,canDelete,canReply,replies}`. author는 `{id,nickname,profileImageUrl}`만 제공한다. 삭제된 사용자 author는 null/화면 `탈퇴한 사용자`; 삭제된 comment는 반드시 content=null, deleted=true다. canReply는 살아 있는 root에만, 로그인·ACTIVE·현재 ACL·게시글/부모 작성자 차단 검사를 만족할 때 true다.
- `GET /posts/{postId}/interactions`를 추가한다: `{postId,commentCount,likeCount,liked,canComment,canLike,canUnlike}`. commentCount는 살아 있는 root+reply 총수(탈퇴 사용자 댓글 제외), likeCount는 살아 있는 사용자의 like 총수. guest liked=false 및 mutation flags=false다. canLike는 새 like 허용, canUnlike는 기존 like 취소 허용을 뜻한다.
- 모든 신규 GET은 optional auth를 지원하되 잘못된 Bearer를 guest로 취급하지 않는다. SecurityConfig에는 숫자 postId의 comments/interactions 정확한 GET 경로만 permitAll로 열고 Controller에서 기존 rejectInvalidBearer와 동일 검사를 적용한다. 게시글 ACL을 먼저 확인하고 응답은 `Cache-Control: private, no-store`다. 상태 재조회 때문에 게시글 조회수를 올리지 않는다.

### 6.2 댓글 mutation

- create body `{content,parentId?}`, update body `{content}`. plain text로 trim 후 1~2000 UTF-16 code units, 공백만/초과는 VALIDATION_001(400). FE는 HTML로 삽입하지 않고 escaped text+줄바꿈으로 렌더한다. 링크/HTML/markdown 변환 기능은 없다.
- 모든 mutation은 로그인·ACTIVE actor·현재 게시글 canRead를 확인한다. owner가 읽을 수 있는 PRIVATE/draft도 기존 접근정책대로 처리한다. 삭제된 post/blog/owner에서는 모두 거부한다.
- create는 actor↔게시글 작성자의 양방향 BLOCKED를 거부한다. reply는 actor↔부모 댓글 작성자의 BLOCKED도 거부한다. 차단 이유/상대 방향을 DTO로 공개하지 않는다.
- parent는 같은 post의 살아 있는 root여야 한다. 타 post/부재/삭제 parent는 COM_001(404), reply를 parent로 지정하면 COM_002(400). 삭제 root의 기존 replies는 보존하지만 새 reply는 금지한다.
- edit는 살아 있는 댓글 작성자만, delete는 댓글 작성자 또는 게시글 작성자만. 차단은 새 댓글 작성에 적용하고 기존 댓글 수정·삭제에는 게시글 ACL과 소유권을 적용한다.
- 삭제는 soft delete이며 자식을 연쇄 삭제하지 않는다. 권한 있는 동일 delete 재호출은 200. 삭제 댓글 edit는 COM_001. 비소유 mutation은 COM_003(403), 차단된 create도 COM_003으로 일반화한다.
- 탈퇴 사용자 댓글은 삭제 placeholder(content=null,deleted=true,author=null)로 보이게 하며 후손은 보존한다. 저장 row를 읽기 작업에서 변경하지 않는다.
- create/update는 canonical CommentItem, delete는 empty success를 반환한다. FE는 성공 후 해당 root/reply page와 interactions를 재조회한다.

### 6.3 좋아요·권한·오류

- 좋아요 POST/DELETE는 기존 `(post_id,user_id)` unique를 유지하고 존재 유무와 무관하게 200을 반환한다. DELETE는 hard delete다. 응답은 interactions와 같은 상태 DTO다.
- 두 mutation 모두 로그인·ACTIVE·게시글 canRead가 필요하다. FR-UNI-09에 따라 차단 관계에서는 PUBLIC 글에도 새 좋아요를 허용하지 않는다. actor↔게시글 작성자의 양방향 BLOCKED면 POST like는 LIKE_001(403), canLike=false다. DELETE like는 신규 상호작용이 아닌 기존 행 제거이므로 block 자체로 막지 않는다. 접근권한이 사라진 글의 like 취소는 거부한다.
- 위 양방향 차단은 차단한 사람도 상대 PUBLIC 글에 새 like를 못 하는 것으로 명시 확정 요청한다. 읽을 수 있는 PUBLIC 글의 기존 like 취소는 가능하다. UNIVERSE 연결 철회나 PRIVATE 전환으로 읽기권한이 사라지면 기존 like row는 유지하고 취소 요청은 LIKE_001로 거부한다. 이후 접근권한이 회복되면 liked=true로 다시 보이고 취소할 수 있으며, 활성 사용자의 보존 like는 총수에 계속 포함된다. 권한 상실 시 FE는 기존 interaction 화면 상태를 폐기하며 거부된 GET에서 flags/count를 노출하지 않는다.
- 댓글 조회/쓰기 및 interactions GET의 인증 viewer 게시글 접근불가는 POST_002(403), guest가 비공개/친구/draft에 접근하면 기존 PostService와 같은 AUTH_004(401)다. 좋아요 mutation 접근불가는 LIKE_001(403). post/blog/owner가 삭제/부재면 POST_001(404). 인증 AUTH_004(401), SUSPENDED USER_003(403), 입력 검증 VALIDATION_001을 재사용한다. 신규 오류 코드는 만들지 않는다.
- 댓글 mutation과 좋아요 mutation은 같은 post 단위로 직렬화하고, 관련 사용자 잠금을 오름차순으로 잡아 M5의 block/accept/delete와 충돌을 제어한다. `blog 단독 scalar lock→관련 users ID 정렬→post→comment/like` 순서를 지킨다. 관련 user 집합은 actor·post owner 및 reply create 시 parent author의 중복 제거 집합이다. 본인 글은 user 1행만 잠근다.
- 기존 BlogRepository의 JOIN FETCH user 잠금은 owner를 먼저 선점할 수 있어 M6에서 그대로 사용하지 않는다. owner를 join하지 않는 scalar blog lock을 추가한다. 기존 PostService.get의 joined post 선점도 동일 blog→owner→post 순서로 정렬한다. 이는 M6의 교착 방지를 위한 선행 최소 수정이며 기존 조회수·응답·ACL 의미는 바꾸지 않는다. 다른 M4 API의 제품 기능을 확장하지 않는다.
- PostService.get 전용 read-lock helper는 owner-only/ACTIVE 쓰기 제한을 적용하지 않는다. guest PUBLIC 읽기와 SUSPENDED 작성자의 공개 글 읽기 정책을 유지한다. SUSPENDED actor의 M6 mutation은 edit/delete/unlike를 포함해 USER_003으로 거부하며 조회는 기존 인증 흐름을 따른다.
- lock 전에는 식별자 projection만 탐색하고 관리 entity를 미리 적재하지 않는다. lock 후 post/blog/user/comment 상태를 다시 읽고, Universe는 같은 사용자 잠금 아래 current read로 재조회해 REPEATABLE_READ snapshot/1차 캐시를 재사용하지 않는다. 권한은 이 최종 상태로 판정한다.
- 동시 중복 like, like/unlike, 댓글 edit/delete, root delete/reply, block/comment·like, post 삭제/interaction 경쟁은 정상 성공 또는 정의된 4xx만 반환해야 하며 500·중복 row·삭제 후 새 reply·차단 후 새 댓글/좋아요는 허용하지 않는다.

### 6.4 화면·검증

- 980px 단일 열·본문 아래 26px 간격 댓글 카드, 34px avatar, 13.5px 본문, `■ 댓글 N`, `따뜻한 신호를 남겨주세요…`, `전송`, `♥ 좋아요 N` 및 정본 border/shadow/color를 적용한다.
- 시각 인수 범위는 PostDetailPage다. 같은 화면의 기존 헤더/액션바도 원본 수치에 정렬하되 owner 수정/삭제·공유·인접글을 유지한다. BlogPage/PostCard 목록 count 집계는 M6에 확장하지 않으며 현재 미구현인 후속 항목으로 남긴다. 카드마다 interactions를 호출하지 않는다. 댓글 헤더 수는 삭제 placeholder 행 수가 아닌 살아 있는 root+reply 수다.
- 1단계 답글은 PRD §9.3-③대로 들여쓰기+구분선, root/reply 각각 paging, canEdit/canDelete만 액션 표시. guest는 로그인 유도, 작성 불가 상태는 제한 안내다.
- 낙관적 like/comment 표시와 실패 rollback, 중복 클릭 방지, 실패 시 입력 보존, canonical reload 및 post/account 변경 시 늦은 응답 무시를 구현한다. 임시 댓글은 서버 ID를 받기 전 답글/수정/삭제할 수 없다.
- RED→GREEN 단위·controller·MySQL integration, FE 행동·lint/build, 실제 HTTP/OpenAPI·브라우저1440px 검증과 독립 QA를 완료 gate로 삼는다. notification row는 M6에서 생성하지 않는다고 별도 검증한다.

## 7. 결정

- 상태: APPROVED. 사용자 2026-11-09 응답 `A-v2 전체 승인`으로 §6 A-v2 및 worklog Task1~6 구현·독립 QA·dev 통합을 승인했다. §6의 승인 전 제안 표현은 준비 이력이며 이 결정으로 확정한다.
- 위험 등급 HIGH: 공개 API·보안·요구사항의 세부 계약 확정이 필요하다. LOW 자동 승인 조건을 충족하지 않는다.
- 진행자 권고: A-v2. 무제한 답글/flat tree 대안보다 탐색성과 응답 상한을 유지하며, 기존 ACL과 테이블을 재사용한다. 두 제품 정책 선택의 결과를 위에 공개해 함께 승인받는다.
- 사실/가정: 기존 테이블·코드·디자인 존재와 잠금 query를 확인했다. batch/lock 수정의 실행 가능성은 backend 코드 조사 결과이며 아직 구현/실행 검증이 아니다.

## 8. 후속 작업

- 독립 3인 검토 → 누락 계약 보완 → 사용자 승인 → REQUIREMENTS/PRD 정본 동기화 → backend/frontend 구현 → 독립 QA/실제 검증 → PR/dev 통합.
- 아직 구현/테스트 통과/완료를 주장하지 않는다.

## 9. 승인 기록

- 사용자에게 2000자·1단계 paging·삭제 내용 숨김·상태 API·양방향 차단like·ACL 상실 시 기존like 보존/취소거부·V1 유지·잠금 순서 최소수정과 구현/독립QA/dev 통합 범위를 제시했다.
- 사용자 원문: **A-v2 전체 승인**. 이 시점 이후 §6 계약은 확정이며 추가 변경은 정정 기록으로 남긴다.
