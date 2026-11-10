# M8 — 알림 독립 QA 인수 결과

> **현재 상태: A-v2 APPROVED / IMPLEMENTED / ACCEPTANCE PASS / DEV INTEGRATED**
>
> 이 문서는 M8 Task 5의 독립 인수 기준과 실행 결과를 고정한다. Run3 immutable runtime에서 실제 HTTP·MySQL·Chrome·자동검증·소스/JAR 일치 gate를 충족했고, §10의 독립 최종 audit에서 Critical/Important finding 없음과 Ready to merge를 확인했다. PR14가 `dev`에 병합되어 현재 M8은 **DEV INTEGRATED**다. 아래의 과거 `NOT RUN` 표기는 실행 전 이력이며 현재 판정을 대체하지 않는다.

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
| actual bearer query-bound | `performance_schema` app account counter, 순차 size=1→100 | `USER='zeroverse_m8'`에서 size=1·size=100 각각 `1..14`, 두 비용 차이 `0`; unavailable counter는 NOT RUN | NOT RUN |

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

## 9. 2026-11-10 KST — whole-branch review QA finding closure

독립 whole-branch review가 지적한 인수 도구의 실행 오류·거짓 PASS 가능성을 `qa/m8-acceptance.mjs`에서 닫았다. 변경 범위는 QA 도구와 QA 기록뿐이며 실제 제품 코드·타 역할 테스트·runtime DB는 수정하지 않았다.

- source manifest 경로 정렬을 `build/m8-runtime.py`와 같은 ordinal comparator로 맞췄다. `localeCompare`에 의한 host locale 차이로 aggregate hash가 잘못 실패하지 않으며 개별 파일/JAR hash gate는 그대로 유지한다.
- 발행 전이 건수는 PRIVATE masking으로 `targetId`가 null이어도 보존되는 notification ID 집합의 신규 차이로 계산하고, PRIVATE 상태의 실제 masking을 별도 검사한다. SUSPENDED receiver COMMENT fixture는 post ID가 아니라 comment ID로 찾는다. 삭제된 comment의 masking은 실제 DELETE 뒤에 검사한다.
- size=1/100 query bound는 각 요청의 독립 SELECT 비용을 기록하고 둘 다 `1..14`, 차이 `0`을 요구한다. counter 미가용·0 관측을 PASS로 완화하지 않는다.
- Chrome 검사는 원본 Main `body` locator, 실제 센터 `role=button[tabindex=0]`/최근 알림 `<button>` 행, API 순서·표시 시각을 이용한다. 없는 `data-notification-id`, 첫 duplicate message 부모 추정, article/li 추정을 제거했다.
- USER_001 상태 비움은 실제 센터 행·상단 unread badge를 확인한다. 계정 전환 stale 검사는 full-page reload가 아니라 React Router SPA sign-in/navigation으로 살아 있는 Provider 세대 경계를 유지한다. unavailable row는 실제 `PUT read` 미호출과 URL 불변을 확인한다.
- wrong-type JWT는 runtime이 제공한 정상 서명 `M8_WRONG_TYPE_TOKEN`만 사용하며, expired token header 변조는 제거했다. env가 없으면 해당 endpoint들은 NOT RUN이다.
- DB fingerprint는 `GROUP_CONCAT`/`CONCAT_WS`를 사용하지 않고 receiver의 모든 notification scalar 필드를 `HEX(JSON_ARRAY(...))` 행별로 id 정렬 조회한 뒤 Node에서 null 보존 canonical JSON SHA-256을 계산한다. SQL은 read-only다.

정적 검증: `node --check qa/m8-acceptance.mjs` exit 0, QA 파일 `trailing-whitespace: clean`, `git diff --check -- qa/m8-acceptance.mjs` exit 0. 실제 HTTP/DB/Chrome acceptance는 root가 새 immutable source/JAR manifest로 이어서 실행하며, 이 수정만으로 M8 PASS를 주장하지 않는다.

### 9.1 root spot-check 반영

- ACTIVE 계정의 unavailable row는 `PUT read` 자체는 허용될 수 있으므로 no-read를 요구하지 않는다. 실제 row click 후 read 응답 `200`, `available=false`, `href=null`과 URL 불변을 함께 확인한다.
- account-switch stale 검사는 owner center가 준비된 뒤 `visibilitychange`로 발생한 실제 `size=20` 응답을 hold하고, SPA sign-in으로 friend 계정으로 전환·센터 로딩을 완료한 뒤 원 응답을 release한다. hold 관측·owner row 존재·200 응답·friend 화면의 old unique row 부재를 각각 확인한다.

### 9.2 Run2 브라우저 실패 원인과 final-fix handoff

- Run2에서 HTTP는 통과했으나 `browser-center-width`, `browser-shared-unread-center-topbar`, `browser-right-panel-recent`, `browser-shared-unread-right-panel`이 실패했다. 원인은 제품 레이아웃이 아니라 하네스의 inner-list 폭 측정, `■` 접두어가 있는 panel 제목의 exact text locator, unread 0에서 badge가 없는 정상 상태, Main 화면에서 center unread를 읽던 scope 오류였다. outer `[data-layout="app"]` 폭, scoped `h2 hasText`, 0 fallback, right-panel scope로 교정했다.
- stale 검사는 owner 준비 전 첫 GET을 지연시키고 SPA 계정 전환에 product logout UI를 기대해 signin timeout을 만들던 경로를 제거했다. owner 준비 후 notification `route.fetch()`의 실제 upstream status 200을 보류하고, 기존 `apiClient`에 auth `/me`·`/refresh` 401만 합성 주입해 auth-expired handler를 실행한 뒤 same-Provider SPA friend signin·center ready 후 held response를 release한다. 제품 코드·logout UI·React internals는 추가하지 않았다.
- TopBar dropdown이 열린 상태의 `topbar-notifications.png` 캡처를 추가했다. 정적 검증은 `node --check qa/m8-acceptance.mjs` exit 0, `git diff --check` exit 0, trailing whitespace clean이다. Run3 실제 evidence 전까지 M8 독립 QA 판정은 계속 **NOT RUN**이다.

## 10. 2026-11-10 KST — 독립 최종 audit / m8_final_review

검토자 `m8_final_review`는 제품·QA 구현자와 다른 컨텍스트에서 whole-branch package와 두 final-fix package를 읽고, 실제 출력과 시각 증거를 대조했다. 이 절은 위의 준비 시점 NOT RUN 판정을 갱신한다. 검토 중 제품·테스트·Git 상태 변경이나 테스트 재실행은 하지 않았으며, 리더가 단독 소유권을 배정한 이 최종 기록만 append했다.

### Strengths

- A-v2의 snapshot 조회, hidden-before-count, 현재 접근권한에 따른 안전한 projection, 원 이벤트와 알림의 동일 트랜잭션, 정렬된 잠금·최종 후보 확인·허용된 rollback만의 새 트랜잭션 재시도 계약을 코드와 기존 통합 증거로 확인했다.
- 프론트는 공통 unread 상태와 분리된 목록 cache, viewer/request/mutation/session 세대 검사를 사용한다. 최신 브라우저 검증은 실제 owner 200 응답을 보류하고 같은 Provider에서 friend 로그인 이후 완료시켜 이전 행이 복구되지 않는 것을 확인한다.
- 최초 QA 도구의 7개 Important 그룹과 후속 locator·세션 전환 문제를 수정 코드와 실제 Run3 결과로 닫았다. 정렬/hash gate, null 보존 전체 행 fingerprint, 올바른 fixture·삭제 순서, 독립 SQL 비용, 정상 서명 wrong-type token, 실제 DOM/URL/응답 관측을 유지한다.

### Evidence

- 실제 실행: `build/m8-evidence/acceptance-run3.log`, `build/m8-acceptance-20261110-173900/evidence.json`. JSON의 checks를 독립 재집계한 결과 **323 PASS / 0 FAIL / 0 NOT RUN**, failures `[]`다.
- 파일 396개 검증 및 source aggregate expected/actual `108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47`, runtime JAR expected/actual `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5` 일치. JAR 경로는 `build/m8-api-data/runtime.jar`다.
- performance_schema 실제 SELECT 비용: size 1 = 4, size 100 = 4, 차이 0. 관측값 0을 통과시키지 않는 수정된 gate다.
- Backend full 검증은 `build/m8-evidence/backend-final-2/` XML **102 suites / 599 tests / failures 0 / errors 0 / skipped 0** 및 bootJar다. 최종 DELETE 400 문서 수정 후 별도 covering **5 tests**와 bootJar를 `backend-final-doc.log`/summary에서 확인했다.
- Frontend full 검증은 `frontend-final-2-test.log`의 **47 files / 439 tests PASS**다. 최종 read-only 설명 추가 후 별도 covering **8 tests**(새 테스트 2개 포함), lint/build를 `frontend/.m8-evidence/raw/frontend-final-fix-readonly-*`에서 확인했다. 최종 전체 441개 실행을 주장하지 않는다.
- 계정 전환 브라우저 probe는 `/src/lib/apiClient.ts`의 기존 apiClient 요청에 `/auth/me`·`/auth/refresh` 401을 합성 주입한 진단이다. 실제 로그아웃 버튼 검증으로 표현하지 않는다. 같은 Provider, 실제 signin form, upstream owner 200 hold/release, friend 화면의 이전 고유 행 부재를 각각 관측했다.

### Visual approval

1440px PNG 5장(`design-notifications.png`, `design-main-notifications.png`, `notifications-owner.png`, `topbar-notifications.png`, `main-owner-notifications.png`)을 모두 직접 열어 비교했다. `docs/design/DESIGN-SYSTEM.md` §6·8.1·8.9와 Pages 원본 395–415행, Main Feed v2 원본 44–50·146–154행을 함께 대조했다.

알림 센터 outer 860px / 좌우 padding 40px / 실제 목록 780px, 크림 배경·잉크 보더·각진 모서리·하드 그림자, 미읽음 강조와 read-all/delete 배치, TopBar 330px dropdown·최신 3개·센터 링크, Main 최근 알림 3행을 승인한다. unavailable 고정 문구와 전체 이력 pagination은 승인된 A-v2 계약을 적용한다. 이 시각 승인은 M8 알림 영역이며 기존 Main 전체를 재설계 검토한 것은 아니다.

### Issues

- **Critical: 없음.**
- **Important: 없음.** 기존 QA Important와 잔여 false-PASS/false-failure 지적은 종료했다.
- **Minor:** DELETE 400 OpenAPI 누락과 TopBar/RightPanel SUSPENDED read-only 설명 누락은 수정·covering 검증으로 종료했다. 기존 500kB 초과 chunk 경고와 새 SpyBean 제거 예정 경고는 리더의 기존 비차단 보류 판정을 유지한다. M8에서 의존성 변경은 권고하지 않는다.

### Declined to judge

- 운영 최대 용량·SLA·peak memory: 승인된 로컬 비용/경합 검증을 넘어서는 운영 성능 보증은 하지 않는다.
- 물리 JDBC batching 보장: 승인된 100개 logical batch와 실제 SQL 수를 확인했으며 JDBC batch 보장으로 바꾸어 주장하지 않는다.
- commit 후 응답 유실의 idempotency key: 승인 계약에서 제외되었으며 수동 재실행은 별도 이벤트다.
- 30일 보존·자동 purge: 사용자 전체 이력 결정이 디자인의 30일 footer를 대체한다.

위 네 항목은 `.superpowers/sdd/M8-notifications/final-review-findings.md`의 리더 ruling을 유지한다. 추가로 조용히 제외한 동작은 없다.

### Assessment

**Ready to merge: Yes.** M8 승인 범위에 남은 제품·검증 blocking finding은 없고 실제 HTTP/DB/Chrome 및 시각 gate가 닫혔다. 이는 독립 검토 승인으로, dev 통합이 이미 실행되었다는 뜻은 아니다. 리더의 기록 정리·PR·dev 통합 단계로 진행할 수 있다. M9는 포함하지 않는다.

## 11. 2026-11-10 KST — PR14 dev 통합 증거

- PR14가 `dev`에 병합됐다: `https://github.com/0-virus/zero-verse/pull/14`, merge commit `b9fcb0469f42077447ce8a8842ce22f24bc94af5`.
- 병합 후 local `dev`는 `ff` 상태이며, tested head `201cd181ec8332e85f2cc985c9560d947b2833f4`와 merge tree `9da6077a89a4528df3cb55c13556ed28300cfd28`가 동일하다.
- 이 증거로 M8은 독립 QA PASS·Ready to merge를 거쳐 **DEV INTEGRATED**로 갱신한다. M9는 이번 기록과 범위에 포함하지 않으며 사용자 지시를 기다린다.
