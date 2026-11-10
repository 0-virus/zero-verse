# M8 — 알림 독립 QA 인수 준비

> **현재 상태: A-v2 APPROVED / IMPLEMENTATION IN PROGRESS / ACCEPTANCE PREP — NOT RUN**
>
> 이 문서는 M8 Task 5의 독립 인수 기준과 실행 경계를 고정한다. 현재는 backend Task 2와 frontend 구현이 시작된 단계이며, 실제 HTTP·MySQL·Chrome·자동검증·소스/JAR 일치 결과가 없다. 아래의 `NOT RUN`은 실패가 아니라 아직 실행하지 않은 gate다. PASS는 immutable artifact와 독립 실측 뒤에만 기록한다.

## 1. 검토 경계와 정본

| 항목 | 정본·현재 기준 |
|---|---|
| 승인·기준 | `feature/M8-notifications`, base `b80ede0`; 사용자 A-v2 전체 승인 (`docs/governance/meetings/M8-20261110-notifications.md` §2, §9, §10) |
| 요구사항 | `docs/REQUIREMENTS.md` §6.11 FR-NOT-01~05, **§6.11-A 확정 세부 계약**, NFR-04/09 |
| 실행 명세 | `docs/PRD.md` §5.11, §7.2/§7.3, §9.11, §10 M8, §11~§12 |
| 구체 계약 | `docs/governance/meetings/M8-20261110-notifications.md` §9 A-v2, `docs/REQUIREMENTS.md` §6.11-A, `docs/governance/decisions/ADR-0007-notifications-contract.md`; §9가 보완한 범위와 §5에서 상속한 DTO/API를 함께 적용 |
| 시각 정본 | `docs/design/DESIGN-SYSTEM.md` §6.1/§6.2/§8.1/§8.9, `ZeroVerse Pages.dc.html` 알림 센터, `ZeroVerse Main Feed v2.dc.html` 최근 알림/드롭다운 |
| 마일스톤 기록 | `docs/worklog/M8-notifications.md` Task 1~6 및 승인 후 인수 매트릭스 |
| 결정 기록 | `docs/governance/decisions/ADR-0007-notifications-contract.md`(ACCEPTED)와 M8 회의 §2/§5/§9/§10; M7 읽기 snapshot/no-side-effect는 `ADR-0006` 및 M7 최종 증거에서 유지 |
| QA 산출물 | `qa/M8-review.md`, `qa/m8-acceptance.mjs`; 제품 `src/**`, `frontend/**`, 타 역할 테스트는 수정하지 않음 |

## 2. 실행·보안 경계

- 실제 인수는 root가 준비하는 합성 MySQL/API/FE에서만 실행한다. 사용자 DB `13307`, 기존 M5~M7 DB, `.env.local`, `.local-data/`, 업로드에는 접근하지 않는다.
- 기본 runtime은 DB `zeroverse_m8`/`127.0.0.1:13318`, API `http://localhost:18088`, FE `http://localhost:5178`, system Chrome 1440×1000이다. 이 값은 `M8_*` 환경변수로만 덮어쓴다.
- register/signin access token/password/email/본문 marker는 process memory에만 둔다. seed/evidence/screenshot/log에는 합성 ID·check 이름·파일명만 남긴다. 만료 토큰은 `M8_EXPIRED_TOKEN`으로 받아 검증하고 절대 출력하지 않는다.
- harness는 HTTP로 합성 계정·블로그·게시글·댓글·좋아요·관계를 만들고, DB는 읽기 전용 snapshot/count/fingerprint 관측에만 `build/m8-runtime.py sql`을 사용한다. 운영 행을 찾거나 임의로 수정하지 않는다.
- 40일 초과 알림 timestamp와 SUSPENDED/deleted 상태는 harness가 직접 SQL을 실행하지 않는다. seed 이후 root runner가 seed의 정확한 ID만 사용해 SQL을 적용하고 `status-ready.json`을 기록해야 한다. 누락 시 전체 실행은 `NOT RUN`으로 남긴다.

## 3. `M8_*` 실행 계약

| 변수 | 기본값 / 의미 |
|---|---|
| `M8_API_BASE_URL` | `http://localhost:18088` |
| `M8_FRONTEND_URL` | `http://localhost:5178` |
| `M8_PLAYWRIGHT_MODULE` | root가 제공하는 Playwright 모듈 경로 |
| `M8_CHROME_EXECUTABLE` | `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` |
| `M8_OUTPUT_DIR` | `build/m8-acceptance-<unique>` 권장; 기존 산출물 덮어쓰기 금지 |
| `M8_FIXTURE_SEED_FILE` / `M8_STATUS_READY_FILE` | output 하위 `fixture-seed.json` / `status-ready.json` |
| `M8_VERSION_MANIFEST` | `build/m8-evidence/tested-version.json` |
| `M8_RUNTIME_SQL_HELPER` | `build/m8-runtime.py`; `sql` stdin 경로만 사용 |
| `M8_REQUIRE_FULL` | 기본 `1`; NOT RUN을 실패 gate로 취급 |
| `M8_REQUIRE_STATUS_FIXTURES` | 기본 `1`; SUSPENDED/deleted/old-history 상태 없이는 진행하지 않음 |
| `M8_WAIT_FOR_STATUS_FIXTURES` / `M8_STATUS_WAIT_MS` | root가 seed 후 준비할 때 `1`/기본 180000ms |
| `M8_EXPIRED_TOKEN` | root가 합성 runtime JWT로 주입하는 exact expired token; evidence 미기록 |
| `M8_SCREENSHOTS` / `M8_HEADLESS` | 기본 screenshot/headless 실행; `0`은 시각 gate를 NOT RUN으로 남김 |

예상 실행 명령은 root가 immutable JAR/source manifest와 격리 runtime을 준비한 뒤의 `python3 build/m8-runtime.py acceptance`다. 이 wrapper가 `M8_API_BASE_URL`, `M8_FRONTEND_URL`, `M8_PLAYWRIGHT_MODULE`, `M8_OUTPUT_DIR`, `M8_FIXTURE_SEED_FILE`, `M8_STATUS_READY_FILE`, `M8_VERSION_MANIFEST`, `M8_RUNTIME_SQL_HELPER`, `M8_EXPIRED_TOKEN` 및 source/JAR SHA를 주입하고 `node qa/m8-acceptance.mjs`를 child로 실행한다. root가 동일 계약의 env와 status-ready를 이미 제공하는 경우에 한해 child를 직접 `node qa/m8-acceptance.mjs`로 재현할 수 있다. 둘 다 현재는 실행하지 않는다.

`build/m8-runtime.py acceptance`는 위 계약을 주입하고 child runner를 실행한다. seed가 생성되면 root는 다음 범위만 수행한다.

1. `suspendedUserId`, `deletedUserId`에 각각 `status='SUSPENDED'`, `deleted_at=UTC_TIMESTAMP()` 적용.
2. `oldNotificationIds`에 포함된 합성 알림 ID를 `created_at=UTC_TIMESTAMP()-INTERVAL 41 DAY`, `updated_at`도 같은 시점으로 변경. 기존 `message`, actor/target, read/deleted 상태와 ID는 보존.
3. seed의 ID가 현재 run의 예상 값과 정확히 일치하는지 확인하고, 위 값과 `oldNotificationIds`를 포함한 `status-ready.json`을 작성. SQL 실패·ID 불일치·부분 적용은 ready로 표시하지 않음.

필요 SQL(값은 seed에서 읽은 양의 정수만 사용):

```sql
UPDATE users SET status='SUSPENDED' WHERE id=<suspendedUserId> AND deleted_at IS NULL;
UPDATE users SET deleted_at=UTC_TIMESTAMP() WHERE id=<deletedUserId> AND deleted_at IS NULL;
UPDATE notifications
SET created_at=UTC_TIMESTAMP()-INTERVAL 41 DAY,
    updated_at=UTC_TIMESTAMP()-INTERVAL 41 DAY
WHERE id IN (<oldNotificationIds>)
  AND receiver_user_id=<ownerUserId>;
```

이 SQL은 root 소유 `build/m8-runtime.py sql` 경로로만 실행하며 QA runner가 직접 호출해 변경하지 않는다. snapshot SQL은 `COUNT(*)`와 정렬된 `id,receiver,actor,type,target,created_at,read_at,deleted_at` fingerprint 읽기만 수행한다.

### 3.1 seed/status-ready 정확한 JSON 키

아래 숫자는 형식 예시일 뿐 실제 run 값이 아니다. runner가 생성하는 seed는 `accounts.suspended.userId`/`accounts.deleted.userId`를 포함하고, root가 바로 읽을 수 있도록 동일한 상태 대상 키를 top-level에도 쓴다. email/password/token/본문은 없다.

```json
{
  "milestone": "M8",
  "createdAt": "2026-11-10T00:00:00.000Z",
  "accounts": {
    "owner": { "userId": 101, "blogId": 201, "postIds": { "public": 301, "transition": 305 } },
    "suspended": { "userId": 106, "blogId": 206, "postIds": { "public": 306 } },
    "deleted": { "userId": 107, "blogId": 207, "postIds": { "public": 307 } }
  },
  "comments": { "blocked": 8001, "deletedActor": 8002, "deletedComment": 8003 },
  "notifications": { "blocked": 9001, "accessLoss": 9002, "suspendedReceiver": 9003, "relationshipDeleted": 9005 },
  "statusTargets": {
    "suspendedUserId": 106,
    "deletedUserId": 107,
    "suspendedPostId": 306,
    "deletedPostId": 307,
    "ownerUserId": 101,
    "oldNotificationIds": [9004]
  },
  "suspendedUserId": 106,
  "deletedUserId": 107,
  "suspendedPostId": 306,
  "deletedPostId": 307,
  "ownerUserId": 101,
  "oldNotificationIds": [9004]
}
```

`status-ready.json`은 root가 동일 ID·receiver·update count를 검증한 뒤 다음 direct keys로 쓴다. `ready`가 true이고 `oldNotificationIds`와 `oldNotificationUpdatedCount`가 seed/기대 count와 정확히 같을 때만 harness가 계속한다.

```json
{
  "ready": true,
  "suspendedUserId": 106,
  "deletedUserId": 107,
  "suspendedPostId": 306,
  "deletedPostId": 307,
  "ownerUserId": 101,
  "oldNotificationIds": [9004],
  "oldNotificationUpdatedCount": 1
}
```

## 4. 합성 fixture 설계

runner는 `owner`, `actor`, `parent`, `friend`, `blocked`, `suspended`, `deleted`, `unrelated` 8개 합성 계정을 만들고 기본 블로그를 초기 설정한다. 모든 fixture는 run별 무작위 suffix를 사용한다.

- owner의 PUBLIC/UNIVERSE/PRIVATE/draft 및 발행 전이용 글, suspended/deleted 계정의 PUBLIC 글을 만든다.
- `owner↔friend`는 ACCEPTED로 만들어 POST 수신자, `owner↔blocked`는 알림을 만든 뒤 차단/해제 검증 대상으로 사용한다.
- `actor→parent` 신청·취소·재신청·수락으로 NEIGHBOR 최초/재행동·취소 이력·수락 이력을 만든 뒤 관계 hard-delete로 같은 이력을 unavailable 재평가한다.
- owner 글에서 owner 자기 root → actor reply(동일 수신자 REPLY 우선), parent root → actor reply(글 작성자 COMMENT + 부모 REPLY), actor의 25개 이상 root 댓글(21+ page), LIKE→중복 LIKE→unlike→relike, blocked/deleted actor 댓글을 만든다.
- 발행 전이 글에서 draft→PUBLIC, PUBLIC 본문 수정, PUBLIC↔UNIVERSE, PRIVATE→UNIVERSE를 각각 구분하고 당시 ACCEPTED 관계에만 POST가 생기는지 확인한다.
- seed에는 계정/블로그/게시글/댓글/관계/알림의 합성 ID만 기록한다. owner 알림 중 하나 이상을 `oldNotificationIds`로 handoff하여 40일 초과 전체 이력 gate를 구성한다.

## 5. FR/NFR·A-v2 인수 매트릭스

모든 행의 초기 상태는 `NOT RUN`이다. `qa/m8-acceptance.mjs`의 check 이름과 evidence를 동일한 식별자로 연결한다.

### 5.1 API·조회·공통 응답

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| FR-NOT-01 목록 기본 | `GET /api/v1/notifications?read=all&page=0&size=100` 및 생략 read | `items,totalElements,totalPages,page,size,hasNext,hasPrevious,unreadCount,canManage`와 공통 envelope | NOT RUN |
| 전체 이력·40일 초과 | root SQL handoff 알림을 41일 전으로 변경 후 `all` 목록/마지막 page | old row가 보존·최신순에 포함; 30일 cutoff/purge 없음; 마지막 footer는 30일 문구가 아님 | NOT RUN |
| filter | `read=all|unread|read`, 경계 `read=bad` | all/unread/read 결과 집합·unreadCount 일치; 잘못된 값 `400 VALIDATION_001` | NOT RUN |
| 정렬·offset | 동일 timestamp ID 역순 fixture, page 0/1/21·size 1/20/100 및 큰 offset | `createdAt DESC,id DESC`, filter/count 이전 hidden 적용, 정상 empty page | NOT RUN |
| 5 type DTO | COMMENT/REPLY/LIKE/NEIGHBOR/POST row | type·targetType·targetId·actor·message·createdAt·readAt가 계약과 일치; unsupported legacy는 unavailable | NOT RUN |
| unread count | center/preview/filter/page 조합 | filter·page와 무관한 visible unread count; blocked/self-deleted row 제외 | NOT RUN |
| read | `PUT /{id}/read` 반복·현재 ACL 재평가 | 첫 readAt 보존, canonical Item 반환, 반복 read 멱등, no-store | NOT RUN |
| read-all | 페이지 밖 포함 + 이후 새 INSERT | 단일 conditional update 의미; 대상 전체만 read; 이후 새 row는 unread; FE는 0 추정 금지 | NOT RUN |
| delete | 소유 row delete 반복·read 후 delete | soft delete, 첫 deletedAt 보존, 목록 제외, read `404 NOT_001`, 반복 delete `200 empty` | NOT RUN |
| masking | post PRIVATE/comment delete/actor 탈퇴/content ACL loss | `available=false`, 고정 `더 이상 확인할 수 없는 알림입니다`, actor/targetId/href/message 원문 숨김 | NOT RUN |
| restore | post PUBLIC 복구·차단 해제·관계 복구 | 직접 삭제하지 않은 row만 현재 상태로 다시 표시하고 restored read projection도 available; 수신자 delete row는 복구 금지 | NOT RUN |
| block | 어느 방향이든 BLOCKED 전후 list/count/read | page/count/unread에서 사전 제외; 직접 read는 `404 NOT_001`; unblock 후 보존 row와 read projection 재표시 | NOT RUN |
| 관계 삭제·actor 탈퇴/null | ACCEPTED 관계 hard-delete·deleted actor projection | NEIGHBOR 이력은 row를 보존하고 `available=false`, 고정 masking, actor/targetId/href null; deleted actor도 동일 null projection | NOT RUN |
| ACL·상태 | 타인 소유/없는 ID/SUSPENDED/deleted receiver | `403 NOT_002`, `404 NOT_001`, SUSPENDED GET 200 `canManage=false` + 모든 mutation `403 USER_003`, deleted principal 모든 endpoint `404 USER_001` | NOT RUN |
| auth | 모든 4 endpoint의 header 없음/빈 값/Basic/잘못된 bearer/서명/type/만료 | endpoint별 `401 AUTH_004` 또는 exact expired `401 AUTH_002`; deleted principal/SUSPENDED 오류와 private/no-store 포함 | NOT RUN |
| validation/no-store | page<0,size 0/101, 잘못된 id/read | `400 VALIDATION_001`; 성공·오류 모두 `Cache-Control: private, no-store` + envelope | NOT RUN |
| OpenAPI | `/v3/api-docs` | list/read/read-all/delete path·method 노출 | NOT RUN |
| actual bearer query-bound | `performance_schema` app account counter, 순차 size=1→100 | `USER='zeroverse_m8'` SELECT delta, size=1 총 `<=14`, size=100 추가 delta `0`; unavailable counter는 NOT RUN | NOT RUN |

### 5.2 생성·중복·원자성·동시성

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| COMMENT/REPLY | root/reply 성공, 자기 root, 같은 수신자 reply | root→COMMENT; reply→글 작성자 COMMENT+부모 REPLY; 동일 수신자는 REPLY 1건; self 제외 | NOT RUN |
| LIKE | add→retry→cancel→re-like | 최초 +1, 동일 active retry +0, unlike 후 row 보존, re-like +1 새 사건 | NOT RUN |
| NEIGHBOR | request→cancel→request→accept | request target/accept requester에 새 row; cancel 후 이력 보존; 기존 상태 retry/거부 0 | NOT RUN |
| POST transition | create draft/PRIVATE/PUBLIC/UNIVERSE와 publish/update | 최초·draft→eligible·PRIVATE→eligible만 +1; PUBLIC↔UNIVERSE/body edit/과거 글 소급 없음 | NOT RUN |
| 수신자/차단 | ACCEPTED·self·양방향 block·현재 post ACL | 살아 있는 ACCEPTED만, self/block/읽기권한 상실 제외; SUSPENDED receiver는 row 수신 가능 | NOT RUN |
| rollback | Task 3의 후반 batch/validation/failure injection 증거 대조 | 원행위·모든 recipient notification이 함께 0; partial fan-out/commit 후 best-effort 없음 | NOT RUN |
| lock/retry | backend MySQL barrier/1213·1205·candidate conflict | 정렬 lock·최종 후보 재검증·총 3 attempt·소진 `COMMON_500`; 무한 재시도 없음 | NOT RUN |
| fan-out | backend 0/1/100/101/1001 recipient와 batch100 | 임의 절단 없음; query count·elapsed·lockwait·recipient 수 실측, 미측정 SLA 통과 주장 금지 | NOT RUN |
| snapshot side effect | list/preview/feed/search 전후 SQL COUNT/fingerprint | GET이 notifications/view/관계/like를 변경하지 않음; M7 read fingerprint 0 diff | NOT RUN |

### 5.3 FE·Chrome 1440px

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| center layout | `/notifications`, 1440×1000 screenshot | `max-width:860`, cream/ink/purple 토큰, radius 0, 3px border/8px shadow, filter tab 없음 | NOT RUN |
| center data | API 실제 첫 item과 DOM row 대조 | actor/message/time/read state/× 삭제, unavailable row의 고정 안내와 no link | NOT RUN |
| recent 3 | MainPage RightPanel | 읽음 여부와 무관한 최신 3건, center와 same unreadCount, center link | NOT RUN |
| top dropdown | TopBar `알림` open | width 330px, latest 3, open-only no read mutation, center link | NOT RUN |
| shared unread | center/topbar/right panel same viewer | 한 API snapshot의 unreadCount와 3 UI가 일치, query response 역순에도 stale count 불가 | NOT RUN |
| click read/navigation | available row click + 500 read injection | `PUT read` 성공 후 응답 href로 이동; 실패 시 row 보존, error/retry 후 재시도 성공 시 이동 | NOT RUN |
| read-all | center button + page 밖 row | 서버 재조회 후 count 0; read-all 전후 새 notification은 unread 유지 | NOT RUN |
| delete | 성공 × 및 500 주입 실패 | 성공 시 row 제거·count sync; 실패 시 기존 목록 보존·error/retry | NOT RUN |
| unavailable/block | masking row, block/unblock session | row는 read/delete 가능·이동 불가; block 성공 후 count/list/preview invalidate, unblock 복구 | NOT RUN |
| transient list/auth failure | list 500·USER_001 주입 | transient list가 빈 상태로 대체되지 않고 retry로 복구; USER_001 시 rows/unread/canManage clear | NOT RUN |
| auth lifecycle | guest/auth loading/logout/refresh final failure | 보호 GET 미호출 또는 signin 경계, 계정 변경 즉시 이전 list/count clear, late response 폐기 | NOT RUN |
| refresh lifecycle | route re-entry/dropdown open/hidden→visible | 주기 polling 없음; 진입/open/focus 복귀 갱신, in-flight 합치기 | NOT RUN |
| same-account stale GET | preview 응답을 hold한 뒤 새 알림·center page 요청 | 같은 viewer의 오래된 preview 응답이 최신 unread summary를 덮지 않음 | NOT RUN |
| design original | Pages 알림 원본 + Main 원본 screenshot 직접 대조 | 원본과 제품 1440px 캡처·center/recent/dropdown의 폭·카피·토큰 일치 | NOT RUN |

## 6. 자동검증·artifact gate

| Gate | 기준 | 상태 |
|---|---|---|
| Backend | `./gradlew test bootJar`; M8 생성/조회/읽음/삭제/rollback/lock/migration/query-bound XML에서 failures/errors/skips `0/0/0` | NOT RUN |
| Frontend | `npm test -- --run --maxWorkers=1`, lint, build; M8 store/API/behavior/auth-race와 기존 회귀 | NOT RUN |
| Runtime | immutable JAR로 API 실행, `/v3/api-docs`, API/FE health 및 DB migration/재기동 | NOT RUN |
| Source/JAR identity | `tested-version.json` 각 `files[]` SHA-256·path\0hash\n aggregate·runtime JAR SHA를 runner가 재계산 | NOT RUN |
| HTTP/DB/Chrome | `qa/m8-acceptance.mjs` unique output의 `evidence.json`, SQL fingerprint, performance_schema query-bound delta, 1440px screenshots | NOT RUN |
| 독립 review | 구현자와 다른 컨텍스트에서 diff·정본·실제 출력 재독; finding은 원소유자 수정 후 재검토 | NOT RUN |

## 7. 잔여 gate·실행 순서

1. backend/FE 구현·자체 검증과 root의 immutable JAR/source manifest 준비를 기다린다.
2. root가 seed ID만 사용해 suspended/deleted/old timestamp SQL을 적용하고 `status-ready.json`을 만든다.
3. root가 `M8_*` 환경을 주입해 unique output으로 runner를 한 번 실행한다. runner는 실패/미실행을 숨기지 않고 evidence를 남긴다.
4. QA가 실제 output·자동검증 XML·source/JAR hash·screenshots를 독립 재집계하고, 필요한 경우 원소유자 수정→재리뷰한다.
5. 모든 필수 gate가 실제 증거로 닫히기 전에는 M8 PASS, merge-ready, dev 통합을 주장하지 않는다. M9는 범위 밖이다.

## 8. 현재 판정

**M8 독립 QA: NOT RUN. Merge-ready: No claim. Dev 통합: 미실행.**

준비 산출물은 `qa/M8-review.md`와 `qa/m8-acceptance.mjs`이며, 현재 시점에는 제품 구현·실제 runtime·브라우저·DB·자동검증 결과를 근거로 한 PASS가 없다.
