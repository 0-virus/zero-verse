# Planning Review: M8 알림 계약

- 회의 ID: `M8-20261110-notifications`
- 관련 마일스톤: M8
- 상태: `APPROVED` — 사용자 `A-v2 전체 승인`, §9 계약·구현·독립 QA·dev 통합 실행 중
- 생성: 2026-11-10 KST / 진행자: Codex root
- 소집: 신규 공개 API, 권한·개인정보, BE/FE 및 post/comment/like/universe 도메인 변경
- 관련 worklog: [M8](../../worklog/M8-notifications.md)
- 소유권: 본 회의·worklog·JOURNAL·레지스터는 root. PRD 와 PM 기록은 PM. 심의 검토자는 읽기 전용.

## 1. 결정할 질문

사용자가 선택한 아래 9 개 제품 정책을 구현하는 구체 API·생성·권한·실행계획을 승인할 것인가?

## 2. 사용자 확정 정책과 범위

사용자 원문: `모두 권고안으로 진행하자` (2026-11-10). 직전 질문의 1A~6A, 7 번 권고, 8A, 9A 를 선택한 것이다. 정책 승인은 확정이며 다시 묻지 않는다. 직전 안내대로 구체 계약·실행계획은 심의 후 별도 구현 승인 대상으로 제시한다.

| 번호 | 확정 정책 |
|---|---|
| 1 | 사용자가 삭제하기 전까지 전체 이력, offset paging. 디자인의 30 일 안내는 실제 전체 이력에 맞게 변경. 자동 만료·purge 없음. |
| 2 | root 댓글→글 작성자 COMMENT. 답글→글 작성자 COMMENT + 부모 댓글 작성자 REPLY. 같은 수신자는 REPLY 우선 한 건, 행위자 본인 제외. |
| 3 | 실제 재좋아요·재신청이면 새 알림. 이미 성립한 같은 상태의 반복 요청/재시도에는 중복 알림 없음. |
| 4 | 최초 PUBLIC/UNIVERSE 발행, draft→PUBLIC/UNIVERSE 발행, 발행 PRIVATE→PUBLIC/UNIVERSE 전환마다 POST. 발행된 PUBLIC↔UNIVERSE 나 본문 수정에는 없음. 당시 작성자를 발견한 수락 관계(친구 포함)에만, 과거 글 소급 없음. |
| 5 | 댓글 삭제·좋아요 취소·신청 취소 후에도 이력 보존. 더 이상 유효하지 않은 동작은 막음. |
| 6 | 콘텐츠 접근 상실·삭제는 `더 이상 확인할 수 없는 알림입니다`로 대체하고 제목·본문·이동 링크 숨김. 양방향 차단된 행위자의 기존 알림은 목록과 미읽음 수에서 숨김. |
| 7 | 센터/드롭다운 열기만으로 읽지 않음. 클릭 시 한 건 읽음 후 이동. 모두 읽음은 현재 페이지가 아닌 목록 대상 전체. 세 UI 의 전체 미읽음 수 일치, 최근 3 건은 읽음과 무관한 최신 3 건. |
| 8 | 화면 진입·드롭다운 열기·브라우저 복귀 시 갱신. 주기 polling·SSE·WebSocket 없음. |
| 9 | M8 적용 후 발생한 활동부터 생성. 기존 활동 소급 생성 없음. |

포함: 5 종 알림 생성, 목록/개별읽음/전체읽음/soft delete, 센터 860px·메인 최근 3·상단 배지와 330px dropdown, 실제 MySQL/HTTP/Chrome1440px 독립 인수.
제외: M9 관리자, 이메일·푸시·실시간 전송·알림 설정·일괄삭제·자동보관기간·새 외부 서비스·S3, 과거 이벤트 이관, 댓글 deep-link 전용 새 조회 API.
사용자 root 미추적 package/lock/run scripts, `.env.local`, `.local-data/`, 기존 DB·업로드는 보존. 검증은 별도 합성 환경. 현재 dev `b80ede08c3e54ff3f3d6742b58c401d869574331`, M7 PR13 merge 확인. 브랜치/제품/DB/runtime 변경 없음.

## 3. 근거

- REQUIREMENTS §4 Notification, §6.11 FR-NOT-01~05, NFR-03/04/09; PRD §5.11, §7 notifications/Main, §9-T, §10 M8, §11/12.
- DESIGN-SYSTEM §8.1/8.9, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`; 30 일 문구는 위 사용자 결정이 override.
- V1 notifications table(id/receiver/actor/type/target_type/target_id/message500/read_at/created_at/updated_at/deleted_at), FK receiver/actor; V1~V5 불변.
- `PostService.create/update`, `PostInteractionLockService`, `CommentService.create`, `LikeService.like`, `UniverseService.request/accept`의 transaction 과 잠금; M5 pair ID 오름차순, M6 scalar blog→users sorted→post→comment/like.
- `PostAccessPolicy`, `DiscoveryViewerResolver`는 기존 ACL/인증 참고. M7 전용 resolver 의 기존 용도를 임의 전역 변경하지 않음.
- M5/M6 no-notification tests 는 당시 마일스톤 경계이며 M8 성공 생성·실패 미생성 테스트로 대체해야 함. M7 feed/search 무부작용은 계속 유지.

## 4. 기술 선택지 (권고·미확정)

| 안 | 방법 | 장점 | 비용/위험 |
|---|---|---|---|
| A | 기존 DB 에서 원행위와 같은 transaction 으로 동기 알림 생성 | 성공한 원행위와 알림 원자성, 신규 인프라·worker·outbox 없음 | 발행 fan-out 지연, FK user 잠금과 기존 잠금 순서 결합, 대량 수신자 검증 필수 |
| B | durable DB outbox 와 비동기 소비·재시도 | 원행위 응답과 대량 생성 분리 | schema·worker·중복키·재처리/운영/지연 UI 계약 추가 필요 |

권고는 A. 성공 응답 뒤 알림을 best-effort 비동기로 저장하는 방식은 유실되므로 제외한다. A 의 잠금·대량 fan-out 가능성은 Architecture 심의로 검증하고 입증 불가하면 사용자에게 대안을 제시한다. 라이브러리 추가 없음. 제품 코드는 아직 작성하지 않는다.

## 5. A-v1 기술 계약 초안 (심의 대상·미승인)

### 5.1 API 와 상태

- `GET /api/v1/notifications?read=all|unread|read&page=0&size=20`: 최대 100, 정렬 `createdAt DESC,id DESC` 고정. 기존 PageResponse 필드에 `unreadCount`(선택 filter/page 와 무관한 전체 visible 미읽음 수), `canManage`를 더한 NotificationPage. list/total/unread/대상정보는 REPEATABLE_READ 같은 snapshot.
- `NotificationItem`: `id,type,message,actor:{id,nickname,profileImageUrl}|null,targetType,targetId|null,href|null,available,readAt,createdAt`. href 는 서버가 현재 slug/권한으로 만든 내부 경로만. unavailable 은 actor/targetId/href=null, 고정 message, type/id/시각/읽음만 유지하며 저장 message 를 그대로 노출하지 않음.
- 최근 3/배지는 동일 GET page0,size3 을 공유. 센터는 page size20. frontend store/provider 가 viewer·세대별 최신 상태를 관리하며 한 화면 내 중복 호출을 합친다. 새로운 unread-count API 는 만들지 않음.
- `PUT /{id}/read`: ACTIVE 소유자만, 최초 readAt 보존·멱등 200 empty success. `PUT /read-all`: ACTIVE 소유자의 visible 미읽음 전체를 한 UPDATE statement 시점으로 읽음, 이후 새 알림은 미읽음. `DELETE /{id}`: ACTIVE 소유자 soft delete, 자기 이미삭제 ID 는 멱등 200. mutation 후 필요한 list/preview 를 재조회하여 서버 count 동기화.
- missing/malformed bearer401 AUTH_004, expired401 AUTH_002, 유효토큰+없는/탈퇴사용자 404 USER_001. 생존 SUSPENDED 는 GET 허용, mutation403 USER_003, canManage=false. 기존 다른 도메인 인증 계약은 변경하지 않음.
- 잘못된 page/size/read/id 는 400 VALIDATION_001. 존재하지 않는 알림/삭제된 알림 read/차단으로 숨긴 알림은 404 NOT_001, 타인 소유 알림은 403 NOT_002. 삭제멱등 예외는 위와 같음. ApiResponse·PageResponse·Swagger·보호 API·`Cache-Control: private, no-store`(오류도 포함).
- int offset 초과는 total 확인 후 정상 empty page, totalPages 공통 의미 유지. hidden-by-block 은 pagination/count 보다 먼저 DB predicate 로 제외. unavailable 은 이력으로 포함하고 읽음/삭제 가능.

### 5.2 생성·중복·수신자

- root/reply 는 성공 INSERT 한 번에 생성. 같은 수신자 REPLY 우선 dedupe 와 self 제외를 적용. 댓글 수정·삭제는 생성하지 않음. 댓글 POST 자체에 없는 idempotency key 를 M8 에서 새로 도입하지 않으며 서로 다른 성공 comment INSERT 는 서로 다른 사건.
- like 는 Like row 가 없어서 INSERT 한 분기에서만 생성. unlike 는 생성/기존알림 삭제 없음. 재 like 의 새 row 는 새 알림. Universe 는 신규 request 성공 및 PENDING→ACCEPTED 성공에만 생성; request 는 target 에게, accept 는 최초 requester 에게. 기존 중복/거부 오류는 유지.
- POST `beforeEligible = publishedAt!=null && visibility in(PUBLIC,UNIVERSE)`에서 `afterEligible=true && beforeEligible=false`일 때만 생성. create 는 before=false. 공개범위·draft 조건은 위 사용자 정책과 동일.
- 수신자 `receiver→author ACCEPTED`, 살아 있는 사용자, self 제외, actor↔receiver 양방향 block 제외, 해당 글 현재 읽기권한 충족. SUSPENDED receiver 는 생존 사용자이므로 수신 가능(행위자 mutation 금지는 유지). 댓글/답글도 수신 시 읽을 수 있는 post 만 생성.
- COMMENT/REPLY 의 target_type=COMMENT,target_id=생성된 comment.id; LIKE/POST 는 POST/post.id; NEIGHBOR 는 UNIVERSE/해당 relationship.id. NEIGHBOR request/accept 메시지는 서버가 생성시 고정한 안전한 텍스트로 구분하며 DB message 에는 사용자 이름·글 제목·댓글 본문을 저장하지 않음. message 를 파싱해 권한/행위를 결정하지 않음.
- 화면 메시지는 현재 허용된 actor nickname+안전한 종류별 문장으로 생성. 댓글 본문/글 제목을 알림 메시지에 넣지 않아 과거 콘텐츠 snapshot 유출을 줄임. 원인 취소 시 row 보존, COMMENT 삭제·actor 탈퇴·대상 관계 삭제는 unavailable 고정 안내. LIKE 취소는 post 가 읽히면 과거 좋아요 사건으로 유지.
- 초기 구현은 원행위와 같은 transaction 참여, 저장 실패 시 원행위도 전체 rollback. 성공 이전 상태에 따라 생성하며 read/refresh/feed/search·자기행위·거부·중복요청은 0 건. 과거 행 backfill 과 M8 이전 code path 생성은 없음.

### 5.3 권한·경쟁·성능

- 현재 차단은 DB 에서 숨김, 해제 시 soft-deleted 되지 않은 과거 이력은 다시 나타남. 콘텐츠 권한 복구 시 같은 row 를 다시 available 로 표시. 수신자가 직접 삭제한 row 는 되살리지 않음. 승인 6 번에 대한 기술적 해석이며 최종 계약에 명시.
- 글/댓글/작성자/blog 삭제·권한 상실이면 unavailable. NEIGHBOR 는 살아 있는 actor/관계만 settings/universe 로 이동; 해당 행에서 accept/reject 버튼은 만들지 않음. 댓글 알림은 현재 post canonical URL 로 이동하고 특정 댓글 자동 페이지 탐색은 범위 밖.
- 목록은 read-only snapshot 에서 ACL/actor/target batch projection, post LONGTEXT 로딩·카드당 개별 GET 금지. size1→100 에서 query count 상한이 유지되는 테스트. 정확 상한은 구현 쿼리 설계에서 기록.
- 전체읽음은 DB conditional update 라 삭제/개별읽음 경쟁에서 deletedAt/readAt 을 되살리거나 readAt 을 덮지 않음. 차단 필터 동일 적용. 동시 INSERT 와 read-all 의 statement 선후를 테스트.
- 생성은 기존 잠금 순서를 보존. POST fan-out 은 receiver FK 가 암묵적으로 user lock 을 잡으므로 단순 author-lock 후 수신자 INSERT 를 안전하다고 가정하지 않음. 후보수신자 확보·전체 user ID 정렬 잠금·최신 관계 재검증·경쟁시 transaction 재시도 방법 또는 동등한 안전한 방법을 심의에서 확정해야 함. 무한 재시도·수신자 임의 절단 금지.
- V1~V5 불변. 필요한 인덱스는 V6 forward migration 제안으로 한정하고 기존 행·audit 유지. 현재 후보 `(receiver_user_id,deleted_at,created_at,id)` 및 `(receiver_user_id,deleted_at,read_at)`; 실제 EXPLAIN 과 읽기/쓰기 비용에 따라 최종 심의에서 확정.

### 5.4 FE 와 인수

- 센터 860px, 필터탭·일괄삭제 없음, 전체 이력 마지막 page footer `알림을 모두 확인했어요 ✦`. loading/error/retry/empty/out-of-range paging. unavailable row 도 읽음/삭제는 가능하고 이동은 없음.
- TopBar330px dropdown·badge, RightPanel 최근 3 건·센터링크, 센터의 global unread 는 같은 store 를 사용. 인증 준비/guest 는 보호 GET 미호출. logout/account change/auth 최종실패 즉시 이전 목록·숫자 제거, 지연된 이전 응답 폐기.
- 진입/open/hidden→visible 복귀에 갱신, 중복 in-flight 합치기. read/delete 중 버튼 중복 클릭 방지. 읽음 저장 실패면 이동하지 않고 오류·retry; 삭제 실패면 기존 목록 보존. 타브라우저 실시간 동기화는 없음.
- 독립 QA: 5 종 생성/recipient/self/block/반복 요청/재행동/발행전이, 삭제·권한변경·복구·차단 해제·탈퇴·suspend, 모든 API401/403/404/400 및 no-store, paging/count/querybound, transaction rollback·fanout·readall 경쟁·회귀, FE stale/race/error/state3 곳 동기화, 실제 HTTP+MySQL+Chrome1440·디자인 대조.

## 6. 독립 검토

같은 최초 A-v1 에 대해 서로 독립된 컨텍스트에서 읽기 전용 검토했다. 검토자끼리 중간 의견을 공유하지 않았으며 아래는 결과의 핵심을 보존한 요약이다. 제품 구현/테스트 PASS 판정은 아니다.

### Product — `/root/m8_product_review`

- APPROVE_WITH_CHANGES, 확신도 90, HIGH. 근거: FR-NOT-01~05, PRD §7/§9-T, DESIGN-SYSTEM §8.1/8.9 및 Pages/Main 원본, 회의 §2.
- 사실/가정: 9 개 정책은 확정, 실제 ACL·원자성·공유 store 구현은 아직 가정/미실측. 사용자 정책을 재질문하지 않음.
- 이점: 원행위/알림 원자성, masking 과 비민감 메시지, 공유 store/lifecycle refresh, 실제재행동·반복 요청 구분.
- 위험/완화: 전체 이력과 30 일문구 충돌(높음)→override; 차단 후 배지 stale(중간)→동일 세션 invalidate; 복구·삭제상태(중간)→상태표; 정지 계정반복 403(중간)→read-only; 원행위 rollback/FE 낙관상태(중간)→실패 복구; readall 동시 INSERT(중간)→canonical 재조회; 무기한이력(중간)→인덱스/측정.
- 가장 강한 반대: 추가 상태표가 일정을 늘리지만 사용자 확정정책의 외부 결과이므로 구현 재량으로 남기면 안 됨.
- 실패: 40 일전 알림에 30 일 footer, 차단후배지 1/센터 0, 삭제댓글내용노출, readall 후신규알림을 FE0 으로덮음, 원행위 rollback 인데화면성공, SUSPENDED403 루프.
- 필수 보완: read 기본 all, 상태표, 3UI 동기화·정지 UX, canonical post 이동, footer override. root 는 A-v1 §5.4 가 이미 전체 이력 문구를 제안했음을 재확인했다. 디자인 원본의 30 일문구는 편집하지 않고 PRD§9.11 사용자결정으로 override 한다.

### Architecture — `/root/m8_architecture_review`

- APPROVE_WITH_CHANGES, 확신도 89, HIGH. 현재스키마·M5/M6 잠금모델에서 A 는 가능하나 원안그대로 안전하다고 판정하지 않음.
- 사실: `PostInteractionLockService` scalar blog→sorted users→post 와 달리 `PostService.lockBlogForWrite`는 blog join/owner 를 먼저 잡음. V1 notifications receiver/actor FK 가 있음. author10 을 선잠근뒤 receiver5 INSERT 는 Universe5→10 과 교착 가능. `UniverseService.lockPair`가 모든관계 mutation 에서 sortedusers 를 잡는 것이 A 의 전제.
- 이점: 원자성, 동적 masking, source 전이기반중복 방지, outbox/worker 불필요.
- 위험/완화: 선잠금 FK 교착·후보변동·대량 fanout(높음)→정렬 잠금/current 재검증/새 transaction 유한 retry/batchINSERT·1000+측정; block 조건불일치·masking(높음)→공통 predicate; N+1(중간)→batch; legacy 행(중간)→보존·안전 masking.
- 필수안: scalarblog→전체 sortedusers→post, 최종 current 후보 재검증에서 미잠금 ID 발견시 out-of-order 추가잠금 금지/전체 rollback 재시도, final 재검증후원행위 직전 수신자확정, transaction 밖 executor 가 최대 3 회 전체 attempt, 소진 COMMON_500, RR 조회/sharedhidden 조건/readallconditionalUPDATE/no-store.
- 가장 강한 반대/가정: 대규모팬아웃에 낮은응답 SLA 를 요구하거나 commit 시점의별도엄격한수신자정의를 요구하면 outbox/serialization 대안을 재심의해야함. 현재관계 writer 가잠금 계약을지킨다는 전제, 실제 FK 경쟁·성능은미실측.
- 실패: 낮은 IDreceiver 교착, 후보뒤 ACCEPTED 누락, blockcount 누출, source 커밋뒤 best-effort 유실, unavailable 의 actor/내용/href 누출, hidden 까지 readall.

### Delivery & Risk — `/root/m8_delivery_review`

- APPROVE_WITH_CHANGES, 확신도 92, HIGH. 근거: FR-NOT/NFR-09, V1/Notification 골격, M5/M6no-notification, M7snapshot 회귀, Post/Universe 잠금 및 DiscoveryCacheControlFilter.
- 가정/이점: 동기생성안의잠금·규모·지연을실제입증, V1 불변·격리합성환경, 원자성및기존 MySQL/ACL/stale 패턴활용.
- 위험/완화: 팬아웃교착→global 순서/실제경쟁; 전체 이력 count 비용→EXPLAIN/인덱스/size1/100; count0 단순삭제→성공/반복/거부기대값으로교체; 오류캐시·stale→HTTP/no-store/계정세대; migration 복구→forward-only/백업복원/구앱호환.
- 가장 강한 반대: 동기 fanout 지연/경합이검증 bound 를충족하지못하면 outbox 대안을별도 심의해야하며, 정책을낮춰 PASS 시키지않음.
- 실패: publish 와 accept 교착, readall 이새 INSERT/readAt/deletedAt 오염, block 을 page 뒤필터링해 count 과대, A 응답이 B 화면에렌더, 오류 no-store 누락, V6 이후구버전앱복귀실패/audit 변경.
- 필수 보완: 실제 MySQL 경쟁/partial 실패 rollback/readall, M5/M6 기대전환·M7 불변유지, querybound·V6/복구, 인증전상태/no-store, FE3UI/stale·Chrome1440, JDK21/Node/MySQL8.4/Chrome/performance_schema 사전확인. 테스트매트릭스는 M8worklog 에 추가.

root 종합: 합의는 A 의 조건부 실현가능성·정렬 잠금·원자성·비민감 projection·실제 검증. 동기대량쓰기 비용은 남는 tradeoff 이며 아직 성능 PASS 를 주장하지 않는다. LOW 자동승인 조건은 공개 API/DB/권한변경으로 충족하지 못한다. 아래 A-v2 가 필수 보완을 명시한 유일한 현행 승인 대상이며 A-v1 은 심의입력 이력으로 보존한다.

## 7. 결정

- 제품 정책 9 개: 사용자 승인 완료.
- 구체 기술 계약·실행계획: 독립 심의 완료, §9 A-v2 HIGH 사용자 승인 대기. 승인 전 코드 착수 없음.
- 코드·migration·테스트 수정/실행·branch/commit/push/merge 는 미착수.

## 8. 후속 작업

1. 독립 3 인 결과를 root 가 실제 정본·source 와 대조해 A-v2 를 작성한다.
2. PM 은 확정된 제품 정책만 PRD §9 에 기록하고 기술안은 미확정으로 분리한다.
3. root 는 결정·위험 레지스터와 M8 worklog 를 연결하고 구체 승인 요청을 제출한다.
4. 승인 후 정본/API 동기화→backend→frontend→독립 QA→실제 인수→dev 통합. M9 자동확장 없음.

## 9. A-v2 — 구체 계약·실행 승인 대상

2026-11-10 root 보완. **§2의 제품 정책 9개는 이미 승인됐다.** 아래는 §5 A-v1의 기술안을 보완한 현행 승인 대상이다. 명시하지 않은 ApiResponse·DTO·범위는 §5를 유지한다. 실제 구현과 검증은 아직 수행하지 않았다.

### 9.1 API·조회·권한·이동

- 목록은 `GET /api/v1/notifications?read=all&page=0&size=20`이다. read는 생략 시 all이며 all/unread/read만 허용한다. 최대 size는 100, 정렬은 createdAt DESC, id DESC다. 기존 PageResponse 7개 필드에 `unreadCount:Long, canManage:Boolean`을 추가한다. unreadCount는 필터·페이지와 무관한 전체 visible 미읽음 수다.
- items·total·unreadCount·actor·target projection은 하나의 REPEATABLE_READ snapshot에서 조회한다. 차단 조건은 page/count 이전에 적용하고, 큰 offset은 전체 건수를 확인한 뒤 정상 empty page를 반환한다.
- 관리 API는 `PUT /notifications/{id}/read`, `PUT /notifications/read-all`, `DELETE /notifications/{id}`다. 모두 /api/v1 접두사를 사용한다. **개별 read는 현재 권한으로 재평가한 NotificationItem을 반환**한다(A-v1의 empty 응답 대체). 이미 읽었어도 최초 readAt을 보존한다. read-all/delete는 200 empty success다.
- 모든 성공·오류 응답에 private, no-store를 적용한다. 기존 M7 전용 인증 정책을 전역 변경하지 않고 M8 보호 경로를 명시한다. header 없음·빈 값·잘못된 scheme/서명/type은 401 AUTH_004, 만료는 401 AUTH_002, 유효 principal의 사용자가 없거나 탈퇴했다면 404 USER_001이다.
- ACTIVE만 관리 API를 사용할 수 있다. SUSPENDED는 GET 200과 canManage=false, 관리 API는 403 USER_003이다. 잘못된 입력은 400 VALIDATION_001, 타인 소유는 403 NOT_002, 자기 알림의 차단 숨김·없는 ID는 404 NOT_001이다. 자기 soft-deleted 알림은 read 404, 반복 delete 200이다. 인증→사용자 생존/상태→소유권→숨김/삭제 순서를 검증한다.
- unavailable 항목은 id/type/targetType/readAt/createdAt만 보존하고 actor/targetId/href=null, available=false로 반환한다. message는 `더 이상 확인할 수 없는 알림입니다`다. 저장된 제목·본문·actor snapshot을 노출하지 않는다. 실제 이동 대상 API도 권한을 다시 검사한다.

| 현재 상태 | 목록·건수 | 메시지·링크 | 관리 |
|---|---|---|---|
| 정상이며 대상 열람 가능 | 포함 | 현재 actor와 안전한 종류별 문구, 현재 canonical href | ACTIVE 가능 |
| 좋아요 취소, post 열람 가능 | 과거 사건으로 포함 | 과거 좋아요 문구와 글 링크 | ACTIVE 가능 |
| 댓글·관계 삭제, post/blog/author 삭제, actor 탈퇴/null, 콘텐츠 ACL 상실 | unavailable로 포함 | 고정 안내, 민감 필드 null | ACTIVE 가능 |
| receiver↔actor 중 어느 방향이든 BLOCKED | page/total/unread에서 제외 | 직접 접근 NOT_001 | 불가 |
| 차단 해제·콘텐츠 권한 복구 | 직접 삭제하지 않은 이력 재표시 | 현재 상태로 재평가 | ACTIVE 가능 |
| 수신자가 직접 soft delete | 제외 | 복구하지 않음 | read 404, delete 멱등 200 |

- NEIGHBOR에는 inline accept/reject 버튼을 만들지 않는다. 살아 있는 관계·actor라면 /settings/universe로 이동하고 관계가 hard delete됐다면 고정 안내를 표시한다. COMMENT/REPLY는 canonical post로 이동하며 특정 댓글을 찾는 자동 page 탐색은 제외한다.
- legacy row의 ID·audit·message는 보존한다. 지원하지 않는 type-target 조합, 끊긴 대상, 알 수 없는 NEIGHBOR 문구는 unavailable로 처리한다. 원문을 그대로 반환하거나 자동 수정·삭제하지 않는다. NEIGHBOR 문구는 `유니버스 신청을 받았습니다.`와 `유니버스 신청이 수락되었습니다.`만 허용한다. 문구를 파싱해 권한이나 행위를 결정하지 않는다.

### 9.2 원자성·수신자 확정·잠금

- 적용 범위는 Post create/update, Comment create, Like add, Universe request/accept와 알림 관리다. 모든 기존 API에 전역 retry를 붙이지 않는다. 원행위와 알림은 같은 transaction에서 저장하고 반환 전 flush한다. 실패하면 모두 rollback한다. commit 이후 best-effort/@Async 생성은 금지한다.
- POST create/update의 기존 blog join→owner 선잠금 경로를 보완한다. 순서는 **scalar blog FOR UPDATE → owner와 후보 receiver를 포함한 user ID 오름차순 FOR UPDATE → 기존 post FOR UPDATE → 원행위 변경·notification INSERT**다. create는 기존 post 잠금이 없다. category/image/content_text/발행 전이/audit/권한 의미는 유지한다.
- 후보는 receiver→author ACCEPTED 관계에서 self·탈퇴·양방향 block을 제외한 사용자다. 후보 ID를 확보하고 owner를 포함한 users를 정렬해 잠근 뒤, author 잠금 상태에서 native current relation-only 조회로 후보·block을 재검증한다. 이 조회가 JOIN으로 아직 잠그지 않은 user를 암묵적으로 잠그지 않는지 확인한다.
- 새 receiver ID가 발견되면 순서를 깨며 추가 잠그지 않고 전체 transaction을 rollback한 뒤 새 후보로 재시도한다. 사라진 후보는 제외한다. **최종 재검증 완료 후 원행위 변경 직전이 발행 수신자 집합의 확정 지점**이다. 이후 관계에 과거 알림을 소급하지 않는다. 모든 Universe writer가 양쪽 user를 정렬해 잠근다는 전제이며 직접 writer 추가 시 재심의한다.
- 댓글·좋아요는 기존 blog→sorted users→post→comment/like 순서를 따른다. 행위자·글 작성자·부모 작성자 중 필요한 users를 이미 잠근 상태에서만 알림을 저장한다. Universe는 기존 pair lock 후 성공 전이에서만 생성한다. 이 단계에서 post/blog 잠금을 역순으로 추가하지 않는다.
- transaction 바깥 executor가 **총 3회 attempt(최초 1회+재시도 최대 2회)**를 수행한다. 각 attempt는 새 transaction이다. 확실하게 rollback한 deadlock(SQL 1213), lock timeout(1205), 후보 집합 충돌만 재시도한다. rollback-only transaction은 재사용하지 않는다. 유효성·ACL·업무 unique 오류·알림 validation 실패·commit 여부가 불명확한 통신 오류는 자동 재시도하지 않는다.
- 재시도 소진은 500 COMMON_500이며 원행위도 반영되지 않는다. 로그에는 종류·attempt·건수·시간만 남기고 본문·토큰·자격증명은 기록하지 않는다.
- Like가 이미 존재하거나 Universe가 이미 전이됐다면 알림을 다시 생성하지 않는다. 댓글/글 POST의 commit 이후 응답 유실까지 보장하는 새 idempotency key는 도입하지 않는다. 별개의 성공 INSERT는 별개 사건이며 내부 rollback 재시도는 최종 성공 한 번만 반영한다.
- fan-out은 수신자 수를 임의로 자르지 않고 정렬한 전체에 batch 100개씩 INSERT한다. 모두 같은 transaction이며 ID별 saveAndFlush 반복으로 대체하지 않는다. 0/1/100/101/1001명과 후반 batch 실패를 검증하고 메모리·query 수·시간·lock 대기·attempt를 기록한다.
- 운영 규모·SLA는 아직 실측하지 않았다. 구조적 교착·지연이 발견되면 승인 범위에서 수정·재검증한다. 동기 방식을 포기하거나 outbox를 도입하려면 별도 심의한다.

### 9.3 읽음·삭제와 동시성

- 알림 관리 API는 receiver user를 잠그고 ACTIVE를 재검증한 뒤 notification을 조건부 변경한다. 이후 다른 user/post/blog를 잠그지 않는다. 대상·ACL 조회는 안전한 projection으로 수행한다. 개별 read는 최초 readAt, 반복 delete는 최초 deletedAt을 보존한다.
- read-all은 receiver 일치, deletedAt IS NULL, readAt IS NULL, 양방향 BLOCKED 부재 조건으로 단일 conditional UPDATE한다. receiver 잠금 확보 뒤 해당 statement에 포함된 알림만 읽고 이후 생성된 알림은 미읽음이다. 생성기도 receiver 잠금에 참여하므로 실제 경쟁 테스트로 선후를 검증한다.
- FE는 read-all 성공을 unreadCount=0으로 추정하지 않고 서버를 재조회한다. unavailable 항목도 ACTIVE의 read/delete를 허용한다. SUSPENDED는 읽기 전용 안내와 관리 버튼 비활성화를 적용하며, 읽음 mutation 없이 안전한 링크 열람만 허용한다.

### 9.4 DB·조회 비용·복구

- **V6__notification_indexes.sql**은 인덱스 두 개만 추가한다: `idx_notifications_receiver_active_created(receiver_user_id,deleted_at,created_at DESC,id DESC)`, `idx_notifications_receiver_active_read(receiver_user_id,deleted_at,read_at)`. V1~V5·column·check·FK·원문 row는 변경하지 않으며 backfill도 없다.
- migration 전후 EXPLAIN으로 목록·미읽음 조회 비용을 확인한다. 목록 SQL은 인증 user 조회를 포함해 최대 14 statement, size 1→100에서 증가 없음, 빈 ID batch 미호출, LONGTEXT 미적재를 검증 기준으로 삼는다. 미충족은 리뷰 finding이며 상한을 조용히 올리지 않는다.
- 빈 DB 최신 migration과 V5 legacy 합성 데이터→V6을 검증한다. ID·내용·actor·target·readAt·deletedAt·createdAt·updatedAt·건수는 모두 보존돼야 한다. 재기동, 별도 합성 DB에 백업 복원·재이관, 구버전 M7 앱의 schema validate와 기존 API 호환을 확인한다.
- 실패 시 row/index를 임의 drop/undo하지 않는다. 알림 쓰기 중단·호환 앱 복귀 또는 forward repair를 검토한다. 사용자 DB 적용·복구는 이번 합성 검증과 별도다. 구버전 앱으로 운영한 동안의 활동은 재도입 후 소급하지 않으며 기존 알림 이력은 보존한다. M7 content_text의 구버전 writer 복구 규칙도 유지한다.

### 9.5 클라이언트·디자인·실행

- store는 viewer/account 세대와 mutation 세대를 모두 추적한다. 오래된 GET 응답을 폐기하고 서로 다른 query의 응답이 최신 unreadCount를 덮지 않도록 공통 summary 요청 순서도 관리한다. preview와 센터 page는 별도 cache로 유지한다.
- 진입·dropdown open·visible 복귀 요청은 진행 중 요청을 합친다. read/delete/read-all 및 같은 세션의 block/unblock 성공 시 store를 즉시 무효화하고 필요한 목록과 preview를 재조회한다. logout·계정 전환·인증 최종 실패 시 즉시 지운다. 조회 오류를 0건으로 표시하지 않는다.
- read 성공 응답의 현재 available/href로 이동한다. 저장 실패 시 이동하지 않고 오류와 재시도를 제공한다. 삭제 실패 시 기존 목록을 보존한다. 타 브라우저 실시간 동기화는 없다.
- 전체 이력 footer는 `알림을 모두 확인했어요 ✦`이며 마지막 page에만 표시한다. 디자인 원본을 수정하지 않고 PRD §9.11의 사용자 결정을 적용한다. radius 0·크림 토큰·센터 860px·dropdown 330px·1440px 실측 기준을 유지한다.
- 구현 순서는 [M8 worklog](../../worklog/M8-notifications.md)의 Task 1~6과 인수 매트릭스를 따른다. Backend는 도메인/API→생성 연계를 순차 수행하고 frontend는 승인 DTO를 기준으로 병렬 작업한다. 독립 QA·실제 검증·source/JAR 대조 후 dev PR·머지·기록까지 수행한다. M9는 제외한다.

### 9.6 승인 범위와 남은 검증

- 승인 요청은 A-v2의 공개 API·DTO, SUSPENDED 읽기 전용, 권한 masking·복구, 동기 원자성·총 3회 retry, 수신자 확정 지점, V6 인덱스 두 개, M8 구현·독립 QA·dev 통합이다.
- 이미 승인된 제품 정책 9개는 다시 묻지 않는다. 구현·브라우저·성능 검증은 아직 수행하지 않았으며 승인이 검증 통과를 대신하지 않는다.
- 위험 등급은 HIGH다. 공개 API·DB·권한·기존 Post 쓰기 잠금에 영향을 주므로 `docs/governance/README.md` §5에 따라 사용자 명시 승인을 받은 뒤 구현한다.

## 10. 2026-11-10 사용자 승인·실행

- 사용자 원문: `A-v2 전체 승인`. §9 구체 계약과 M8 worklog Task 1~6의 구현·독립 QA·실제 인수·dev 통합까지 승인했다. 기존 정책 9개의 승인도 유지한다.
- §7/§9의 승인대기 문구는 요청 당시 이력이다. 현재 상태는 APPROVED이며 동일 계약을 다시 승인받지 않는다. 구현 검증은 별도 완료 gate다.
- root가 dev b80ede0에서 `feature/M8-notifications`를 생성했다. 승인된 공유 체크아웃과 역할 소유권을 유지한다. 사용자 미추적 파일·기존 DB·업로드는 보존한다. M9는 제외다.
