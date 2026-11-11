# M9 — 관리자 독립 QA 준비 및 인수 기준

> **현재 상태: A-v2 APPROVED / audit-2 FIX2 HARNESS UPDATED / ACCEPTANCE NOT RUN**
>
> 사용자의 `A-v2 전체 승인`으로 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이 현재 계약이 됐다. 승인은 제품 PASS가 아니며, 구현 freeze·격리 runtime·실제 HTTP/Chrome/DB 증거 전에는 인수 완료를 주장하지 않는다.

## 1. 검토 경계와 정본

| 항목 | 기준 |
|---|---|
| 현재 기준 | `feature/M9-admin`, 기준 `dev=848d272`; M8은 PR #14 `merged=true`로 완료 |
| 요구사항 | `docs/REQUIREMENTS.md` §6.13-A 및 원 FR-ADMIN-01~05, NFR-01/04/09 |
| 실행 명세 | `docs/PRD.md` §5.13, §7 관리자 화면, §8.3, §9-D/K/U, §10 M9, §11~§12 |
| 시각 정본 | `docs/design/DESIGN-SYSTEM.md` §8.10~8.11, `docs/design/ZeroVerse Pages.dc.html` 관리자 원본 |
| 승인 정본 | `docs/governance/meetings/M9-20261111-admin.md` §5·§10·§10.1·§11, `docs/governance/decisions/ADR-0008-admin-contract.md` |
| 실행 harness | `qa/m9-acceptance.mjs`; API `127.0.0.1:18089`, FE `127.0.0.1:5179`, synthetic MySQL `127.0.0.1:13319` |
| 데이터 경계 | 합성 관리자 계정과 격리 DB만 허용. 사용자 DB `13307`·기존 업로드·`.env.local` 접근 금지 |

## 2. 승인·판정 규칙

- A-v1의 미확정 표시는 과거 심의 입력 이력이다. 현재는 A-v2 승인 계약을 적용한다.
- 사용자 soft delete는 `users.deleted_at`만 설정하고 하위 blog/post/comment/universe/notification/upload/refresh 이력과 파일을 보존한다. 삭제 카피는 `계정 접근이 차단되고 블로그와 게시글이 공개되지 않습니다. 데이터는 보존됩니다.`를 정확히 확인한다.
- self ADMIN 회수·self SUSPENDED·self delete는 모두 `ADMIN_002/400`과 승인 메시지로 거부한다. self ADMIN/ACTIVE 유지 요청은 성공한다.
- `lastAccessAt`은 수집하지 않으며 DTO `null`과 화면 `마지막 접속 기록 없음`만 허용한다. summary·post/comment/universe 집계는 §6.13-A 정의와 동일해야 한다.
- 제품 bootstrap·실사용 DB 승격·새 migration·전역 refresh revoke·M10은 범위 밖이다. 합성 runtime의 SQL seed만 허용한다.
- 구현 역할의 자체 테스트는 기능 준비 증거일 뿐 최종 QA PASS가 아니다. 원작성자와 다른 컨텍스트의 diff·정본·실측 대조가 필요하다.
- 테스트·브라우저·DB·runtime이 시작되지 않은 행은 모두 `NOT RUN`으로 남긴다. `skip`이나 placeholder로 통과시키지 않는다.

## 3. API·공통 응답·인증 인수 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| 공통 envelope | 모든 admin 성공/실패 응답의 `success/data/error/timestamp` 검사 | `ApiResponse` 형식과 오류 payload 일치 | NOT RUN |
| 목록 endpoint | `GET /api/v1/admin/users` | ADMIN만 접근, `PageResponse` offset 필드와 summary 계약 | NOT RUN |
| 상세 endpoint | `GET /api/v1/admin/users/{id}` | ADMIN만 접근, canonical 상세 DTO | NOT RUN |
| role mutation | `PUT /api/v1/admin/users/{id}/role` | body enum 검증, 성공 canonical 상세 | NOT RUN |
| status mutation | `PUT /api/v1/admin/users/{id}/status` | `ACTIVE/SUSPENDED`, 성공 canonical 상세 | NOT RUN |
| soft delete | `DELETE /api/v1/admin/users/{id}` | `users.deleted_at`만 설정, empty success, 하위 이력·파일 보존 | NOT RUN |
| missing auth | 5 admin method에 Authorization 없음/빈 값 | `401 AUTH_004`, private/no-store, envelope | NOT RUN |
| malformed auth | Basic, malformed Bearer, bad signature, wrong token type | `401 AUTH_004`, private/no-store, envelope | NOT RUN |
| expired access | 서명된 만료 ACCESS | `401 AUTH_002`; refresh 재시도 여부는 기존 apiClient 계약대로 | NOT RUN |
| non-admin | 유효 USER ACCESS로 모든 endpoint 호출 | `403 ADMIN_001`; 목록·상세·mutation 데이터 미노출 | NOT RUN |
| current role 재평가 | ADMIN token 발급 후 DB role을 USER로 변경하고 재호출 | 기존 claim만으로 통과하지 않고 `403 ADMIN_001`; 반대로 current ADMIN 승격은 허용 | NOT RUN |
| current status/delete 재평가 | actor SUSPENDED 또는 deleted 후 기존 ACCESS 재호출 | 승인된 `USER_003`/`USER_001` 경계, 쓰기·관리 API 차단 | NOT RUN |
| validation | 음수 page, size 0/101, 100/101자 keyword, 잘못된 role/status body·enum/id/query | `400 VALIDATION_001`, no-store, 내부 SQL/stacktrace 미노출; 100자는 허용 | NOT RUN |
| OpenAPI | `/v3/api-docs`와 Swagger | 5 endpoint method/security/request/response/error 문서 | NOT RUN |
| cache policy | 성공·401·403·404·400 모두 header 검사 | `Cache-Control: private, no-store` | NOT RUN |
| signed claim shape | root synthetic runtime의 `M9_MISSING_ROLE_TOKEN`·`M9_MISSING_SUBJECT_TOKEN` 및 invalid role/subject | 5개 method 모두 `AUTH_004/401`·private/no-store, 정상 인증 계약 보존 | NOT RUN |

## 4. 목록·summary·상세 데이터 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| 검색 범위 | email/nickname/name keyword trim/max100·대소문자·`%_\\` | OR 부분검색, wildcard literal, 응답 사용자 집합 정확 | NOT RUN |
| role/status 조건 | 승인된 `role`, `status` query를 조합 | 조건과 count/list가 일치. 화면에는 디자인대로 상태 filter를 만들지 않음 | NOT RUN |
| offset paging | 0/1/큰 offset, size 경계, 마지막 빈 page | 정상 `totalElements/totalPages/hasNext`, 중복·누락 없음 | NOT RUN |
| 안정 정렬 | 동일 createdAt fixture | 승인된 deterministic sort(기본 createdAt DESC,id DESC) | NOT RUN |
| deleted 제외 | soft-deleted 대상과 활성 대상 혼합 | 목록·summary·통계에서 deleted 계정 제외 | NOT RUN |
| 전체 사용자 카드 | 검색·페이지와 무관한 summary 대조 | 삭제 사용자 제외 생존 전체 기준 `totalUsers` | NOT RUN |
| 이번 주 가입 | Asia/Seoul 월요일 00:00 주경계 fixture, 요청시각 단일 Clock | UTC audit 변환 후 `joinedThisWeek` 정확 | NOT RUN |
| 정지 계정 | ACTIVE/SUSPENDED/deleted 혼합 | deleted 제외, SUSPENDED count 정확 | NOT RUN |
| 목록 privacy | 응답 JSON과 SQL projection 대조 | password/hash/token·민감 본문·승인되지 않은 birthDate 미노출 | NOT RUN |
| blog/post count | 다중 blog·draft·deleted blog/post fixture | 생존 blog/post, draft·모든 visibility 포함, 기본 blog는 활성 최소 id | NOT RUN |
| 상세 기본 정보 | 활성·정지·없는·삭제 대상 요청 | 승인된 상태/삭제 오류와 개인정보 최소 DTO | NOT RUN |
| 상세 통계 | post/comment/universe fixture | post `total=published+draft`, 본인 미삭제 댓글/답글, 생존 상대 ACCEPTED 방향 distinct·교집합 distinct | NOT RUN |
| last access | 응답·화면·source 대조 | 수집하지 않은 `null`/`마지막 접속 기록 없음`; `updatedAt` 대용 금지 | NOT RUN |

## 5. 변경·삭제·권한 상실 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| role USER→ADMIN | ADMIN actor가 대상 role 변경 | 다음 인증/관리 요청에서 DB current role 반영 | NOT RUN |
| role ADMIN→USER | 대상이 다른 ADMIN인 경우 | 승인된 정책대로 변경, 기존 target token 권한 재평가 | NOT RUN |
| self ADMIN 회수 | actor==target, ADMIN→USER | `ADMIN_002`(기존 ErrorCode HTTP 400) 또는 승인된 코드, role 유지 | NOT RUN |
| self delete | actor==target DELETE | 자기 삭제 거부, 계정·refresh·기존 데이터 보존 | NOT RUN |
| self suspend | actor==target, `ACTIVE→SUSPENDED` | `ADMIN_002/400`, 승인 메시지, 상태 불변 | NOT RUN |
| status ACTIVE↔SUSPENDED | 대상 로그인·기존 access·각 작성 mutation을 상태 전후 호출 | 로그인/새 글·댓글·좋아요·유니버스 신청 차단, 기존 PUBLIC 가시성은 FR 정책과 일치 | NOT RUN |
| repeated status/role | 동일 body를 반복 호출 | 승인된 idempotence, 불필요 audit/updatedAt 변경 없음 | NOT RUN |
| target missing/deleted | 없는 ID·이미 deleted ID·race 대상 | `USER_001` 등 승인 오류, 다른 사용자 변경 없음 | NOT RUN |
| soft delete effect | 삭제 전후 root helper fingerprint/API/기존 공개 글·댓글·알림·이미지 ACL 대조 | `deleted_at`만 설정, 하위 행·파일 보존, blog/post/image 차단·댓글 placeholder·알림 actor/target masking | NOT RUN |
| failed mutation | validation/권한/DB 오류 주입 | actor·target·role/status/deleted_at 부분 변경 없음 | NOT RUN |
| canonical refresh | 성공 mutation 뒤 목록·상세 재조회 | 서버 현재 상태로 UI 동기화, stale 이전 값 복귀 없음 | NOT RUN |
| audit/privacy | 응답·로그·DB diff 검사 | raw token/password/hash 및 불필요 개인정보 미출력 | NOT RUN |

## 6. 동시성·선형화 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| actor/target lock | 두 ADMIN이 상호 대상 role/status/delete mutation을 MySQL barrier로 실행 | deleted 포함 actor/target ID 오름차순 lock, lock 후 current 재검증, 권한 우회 없음 | NOT RUN |
| actor demotion race | A request와 B의 A role/status 변경을 root barrier로 동시 시작 | DB current actor 검증 시점에 따른 성공/거부, 후행 무권한 작업 차단 | NOT RUN |
| target mutation race | status↔delete, role↔delete 동시 실행 | 한 선형화 결과, partial field/복구 불가 상태 없음 | NOT RUN |
| stale ACCESS | role/status/delete 후 기존 JWT를 동시에 사용 | JWT claim만으로 admin/쓰기 권한 유지되지 않음 | NOT RUN |
| list snapshot | summary/list/detail 동시 mutation | 명시 RR 동일 snapshot, count/list 불일치·dirty read 없음 | NOT RUN |
| query bound | synthetic query-count helper/sql, size 1/100, 14 SELECT bound | 실제 HTTP 200 request 전후 counter, 양쪽 1~14·delta 0, N+1 없음 | NOT RUN |

## 7. Frontend·1440px 브라우저 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| AdminRoute loading | auth restore/current admin probe 지연 | 판단 전 대기, pending 중 민감 화면·보호 데이터 미표시 | NOT RUN |
| guest guard | 비로그인 `/admin`, `/admin/users/:id` | `/signin` 이동, admin API 미호출 | NOT RUN |
| USER guard | 로그인 USER가 직접 URL 접근 | 승인된 비관리자 화면/403 처리, admin 데이터 미표시 | NOT RUN |
| ADMIN entry | 프로필 메뉴에서 관리자 진입 | ADMIN에게만 링크, 상단 전용 버튼/사이드바 Admin 항목 없음 | NOT RUN |
| list layout | 1440×1000 screenshot | 1100px 단일 컬럼, `ADMIN CONSOLE`, `ROLE: ADMIN`, 검색, 통계 3카드, 테이블 정본 일치 | NOT RUN |
| detail layout | 1440×1000 screenshot | 860px, breadcrumb, header/stats/actions/copy/token 정본 일치 | NOT RUN |
| loading/empty/error | 목록·상세 200/empty/500/403/404 | 빈 상태·오류·retry가 명확하고 기존 행/입력 보존 | NOT RUN |
| search/paging URL | keyword/query/page/back/reload | URL 및 결과 복원, 오래된 검색 응답이 최신 결과를 덮지 않음 | NOT RUN |
| role mutation UI | select→confirm→success/error | canonical 재조회, 실패 시 선택/행 보존, 중복 클릭 dedupe | NOT RUN |
| status UI | suspend/activate confirm→success/error | warning 상태·캡션과 API 결과 일치, 자기 대상 정책 표시 | NOT RUN |
| delete UI | danger confirm→success/error | 승인 카피·대상/행동 확인, 성공 후 목록 복귀, 실패 retry | NOT RUN |
| account/session stale | 실제 SPA ADMIN A→USER B 전환 중 list/detail/mutation 지연 | A 데이터·retry·navigation이 B 화면에 복귀하지 않음, current probe로 재평가 | NOT RUN |
| auth final failure | 401→refresh 실패 | 민감한 목록·상세·mutation retry 제거, signin 경계 | NOT RUN |
| visual tokens | screenshot/source direct comparison | radius 0, ink/paper/accent/warning/danger, hard shadow, 폰트·카피 일치 | NOT RUN |

## 8. DB·회귀·artifact gate

| Gate | 기준 | 상태 |
|---|---|---|
| backend unit/controller | role/status/self-protect/validation/error/no-store 테스트 | NOT RUN |
| repository/JPA | aggregate count, soft delete, auditing, lock, paging projection | NOT RUN |
| backend full | Gradle `test bootJar`, failures/errors/skips `0/0/0` | NOT RUN |
| frontend full | test(`--maxWorkers=1`), lint, build | NOT RUN |
| migration | 신규 migration 여부 확인. 기존 V1~V6/schema/data fingerprint 보존 | NOT RUN |
| M4~M8 regression | auth/refresh, posts/ACL, universe, comments/likes, feed/search, notifications | NOT RUN |
| source/JAR identity | tested source manifest와 immutable runtime JAR hash 일치 | NOT RUN |
| HTTP/API | 격리 MySQL runtime, OpenAPI, 모든 auth/status/delete matrix | NOT RUN |
| Chrome | 1440px 목록·상세·guest/USER/ADMIN/stale/error screenshots | NOT RUN |
| independent review | 구현자와 다른 컨텍스트에서 diff·정본·출력 대조 | NOT RUN |

## 9. A-v2 실행 순서와 잔여 위험

1. backend/frontend source freeze와 source manifest/JAR가 root에서 준비되기 전에는 acceptance를 실행하지 않는다.
2. 합성 계정은 signup/signin API로만 만들고, ADMIN/status/deleted/동일시각/주경계/image fixture는 root synthetic runtime helper가 seed/status handshake로 적용한다.
3. QA는 실제 API·격리 MySQL read-only fingerprint/query-count·system Chrome 1440px를 독립 실행하고, finding은 원 소유자 수정 후 scoped re-review한다.
4. 사용자 DB `13307`, 기존 업로드, `.env.local`, 제품 코드/타 역할 테스트는 읽거나 수정하지 않는다.
5. 최대 fan-out/운영 SLA를 측정하지 않았다면 PASS로 표현하지 않는다. `lastAccessAt` 수집·제품 bootstrap·전역 refresh revoke·새 migration·M10은 검사 대상이 아니다.

잔여 위험은 계약 미확정이 아니라 구현·실측 증거 부재다: DB current auth와 JWT claim 불일치, sorted lock/refresh 경합, 하위 데이터 보존과 ACL masking, RR count/list snapshot, 14 SELECT bound, 1440px/SPA stale, source/JAR identity가 아직 실제로 닫히지 않았다.

## 10. 현재 판정

- **판정: NOT RUN / A-v2 APPROVED / HARNESS PREPARED / WAITING FOR SOURCE FREEZE + ISOLATED RUNTIME**
- `qa/m9-acceptance.mjs`는 실제 합성 HTTP·Chrome runner로 준비됐지만, 현재 턴에서는 `node --check`와 정적 검증만 수행한다.
- 현재 증거가 없는 항목은 전부 `NOT RUN`이다. 구현자의 자체 테스트·승인 기록·환경변수만으로 PASS를 완화하지 않는다.
- 제품 코드·타 역할 테스트·사용자 DB `13307`·업로드·Git은 이 준비에서 수정하지 않았다.

## 11. A-v2 runtime handoff — root exact contract

### 11.1 Fixture seed → status handshake

`qa/m9-acceptance.mjs`가 signup/signin/initial-setup과 합성 post/comment/image를 끝낸 뒤 `M9_FIXTURE_SEED_FILE`에 아래 **비밀 없는** JSON을 쓴다. email/password/access token/body marker는 쓰지 않는다.

```json
{
  "schema": "zeroverse.m9.fixture-seed.v1",
  "milestone": "M9",
  "contract": "A-v2",
  "runId": "m9-<opaque-run-id>",
  "accountIds": {"admin": 1, "adminPeer": 2, "adminKeeper": 3, "target": 4, "suspended": 5, "deleted": 6, "race": 7},
  "blogIds": {"target": 11},
  "postIds": {"targetPublic": 21, "adminPublic": 22},
  "commentIds": {"deletedActorComment": 31},
  "imageIds": {"target": "<UUID>"},
  "bulkLabels": ["bulk01", "...", "bulk21"],
  "weekBoundaryLabels": ["weekBefore", "weekStart", "weekAfter"],
  "preservedImageIds": ["<UUID>"],
  "nativeFixtureRequest": {"extraBlogs": true, "softDeletedPost": true},
  "requiredStatusFixture": {"baseline": {"admin": {"role": "ADMIN", "status": "ACTIVE"}, "suspended": {"role": "USER", "status": "SUSPENDED"}, "deleted": {"role": "USER", "deletedAt": "non-null"}}}
}
```

root helper `apply_seed`는 **사용자 DB가 아닌 MySQL `127.0.0.1:13319`**에서만 seed IDs를 사용해 baseline 상태·동일 `created_at` 21행·`weekBoundaryLabels`의 KST 월요일 직전/시작/직후 시각·선택적 image fixture를 적용하고, `nativeFixtureRequest:{extraBlogs:true,softDeletedPost:true}`가 있을 때 target의 추가 활성 blog 1개·삭제 blog 1개·삭제 blog post 1개·기본 blog 삭제 post 1개를 기존 스키마로 생성한다. `M9_STATUS_READY_FILE`에는 `zeroverse.m9.status-ready.v1` JSON을 원자적으로 쓴다. ready에는 `runId`, `phase=baseline`, 각 필수 계정의 id/role/status/deletedAt, `sameCreatedAt.ids` 21개, seed의 `preservedImageIds`, `time.{requestNowUtc,weekStartUtc,weekEndUtc}`, `summaryExpectations.{totalUsers,joinedThisWeek,suspendedUsers}`, `nativeFixtures.{activeBlogId,deletedBlogId,deletedBlogPostId,deletedPostId}`, `targetDetailExpectations.{defaultBlogId,blogCount,postCount,postStats:{total,published,draft},commentCount,universeStats:{outgoingCount,incomingCount,connectionCount}}`를 포함한다. 누락·불일치 시 harness는 fail-closed한다.

comments/reply, target actor comment notification, accepted universe directions (target→user, user→target, target→special, adminKeeper→target, target→deleted-opponent), post like와 target post tags는 root SQL seed가 아니라 harness가 인증된 실제 API로 생성한다. target 본인의 soft-deleted comment도 API로 생성·삭제해 active aggregate에는 제외되고 history/fingerprint에는 남는 반례를 만든다. target post fixture는 PUBLIC·UNIVERSE·PRIVATE draft를 모두 포함한다. 해당 합성 ID는 seed `commentIds`/evidence memory에만 보관하며 raw credential/token은 쓰지 않는다. target의 기대 detail은 root SQL ground truth와 API fixture가 합쳐진 정확한 수치 `{blogCount:2,postCount:4,postStats:{total:4,published:3,draft:1},commentCount:3,universeStats:{outgoingCount:2,incomingCount:2,connectionCount:1}}`와 일치해야 한다.

`runId`·각 ID·필수 role/status/deleted 상태·21개 bulk ID·UUID image ID가 맞지 않으면 harness는 실행을 계속하지 않고 `NOT RUN`/실패로 종료한다. helper는 기존 사용자 DB·업로드를 조회하거나 수정하지 않는다.

### 11.2 Fingerprint/query-count handshake

#### Fingerprint

harness는 root의 `python3 build/m9-runtime.py fingerprint`를 호출하고 stdin에 다음 요청을 보낸다. DB credential은 helper의 환경/메모리에만 있고 stdout/evidence에는 나오지 않는다.

```json
{
  "schema": "zeroverse.m9.helper-request.v1",
  "milestone": "M9",
  "mysql": {"host": "127.0.0.1", "port": 13319},
  "payload": {"phase": "before-target-delete", "accountIds": {"target": 4}}
}
```

응답은 `zeroverse.m9.fingerprint.v1`이며 `childFingerprints`, `tableCounts`, `fingerprint`, `users[targetId].{id,deletedAt,preservedSha256}`를 포함한다. target user의 role/status/deletedAt/updatedAt 허용 변경을 제외한 `preservedSha256`와 모든 child fingerprint(`blogs`, `categories`, `posts`, `post_images`, `post_tags`, `comments`, `post_likes`, `universes`, `notifications`, `image_uploads`, `refresh_tokens`, `uploadFiles`)가 삭제 전후 같아야 한다. 실제 image content는 `/api/v1/uploads/{uuid}/content`에서 삭제 전 200+byte SHA-256, 삭제 후 정확한 `404 UPLOAD_003`로 별도 확인한다.

#### Query count

query-count는 별도 추상화/토큰 전달 없이 기존 root `sql` CLI로 performance schema scalar를 삭제 전후 읽는다. harness가 **실제 Bearer를 메모리에서 사용한 HTTP 200** list size=1, list size=100, detail 요청을 각각 수행하고, 각 요청 전후 `statement/sql/select` count 차이를 기록한다. 양쪽 list/detail 모두 `1..14`, list size1→size100 delta `0`이어야 한다. performance_schema 비활성·0·request non-200은 PASS가 아니다.

root acceptance가 메모리/env로 제공하는 `M9_EXPIRED_ACCESS_TOKEN`, `M9_WRONG_TYPE_TOKEN`, `M9_MISSING_ROLE_TOKEN`, `M9_MISSING_SUBJECT_TOKEN`, `M9_INVALID_ROLE_TOKEN`, `M9_INVALID_SUBJECT_TOKEN`은 raw token을 evidence/log에 쓰지 않고 5개 admin method의 AUTH_002/AUTH_004·no-store만 판정한다.

### 11.3 실행·증거 경계

실제 실행 명령(제품 freeze 후 root가 수행):

```sh
M9_API_BASE_URL=http://127.0.0.1:18089 \
M9_FRONTEND_URL=http://127.0.0.1:5179 \
M9_MYSQL_PORT=13319 \
M9_WAIT_FOR_STATUS_FIXTURES=1 \
M9_RUNTIME_SQL_HELPER=build/m9-runtime.py \
node qa/m9-acceptance.mjs
```

현재 준비 명령은 `node --check qa/m9-acceptance.mjs`뿐이다. 실제 HTTP·DB·Chrome·source/JAR acceptance는 아직 실행하지 않았고, 본 문서의 모든 매트릭스 행은 `NOT RUN`이다.

## 12. Audit-1 I1~I9 fix1 closure (역사 기록; §13 fix2가 현재 기준)

| Audit finding | 정적 보완 근거 | 현재 상태 |
|---|---|---|
| I1 | label별 20자 이내 고유 nickname, email/nickname/blogSlug 사전 unique check, seed 전 중복 fail | STATIC CLOSED / RUNTIME NOT RUN |
| I2 | `registerAndSetup` 반환 account에 email/password를 memory-only로 연결하고 SPA signin helper가 사용 | STATIC CLOSED / RUNTIME NOT RUN |
| I3 | 실제 DOM의 `ADMIN CONSOLE` span·`searchbox/관리자 사용자 검색`·`[data-layout="app"]` 외곽 폭, HTTP 200+fixture-row readiness로 수정 | STATIC CLOSED / FE freeze 후 runtime 재대조 필요 |
| I4 | 동일 SPA Provider에서 list/detail/mutation 실제 upstream 200/body hold → access/refresh 401 → B signin ready → release; logout/goto fallback 제거 | STATIC CLOSED / Chrome NOT RUN |
| I5 | race의 정확 허용 status/error·최종 detail state·복구를 검사하고, HTTP race와 별도로 `AdminConcurrencyMySqlTest` XML marker를 물리 barrier 증거로 요구 | STATIC CLOSED / BE XML·runtime NOT RUN |
| I6 | root `nativeFixtureRequest`/`nativeFixtures`/`targetDetailExpectations` 계약, API comments/reply/actor notification/universe/like/tags, non-empty fingerprint child counts·정확 detail·masking checks | STATIC CLOSED / root helper handoff·runtime NOT RUN |
| I7 | role/status/literal/summary exact ID 집합, 21개 page0/page1 union/intersection/metadata, large offset empty metadata와 envelope/status 검사 | STATIC CLOSED / HTTP NOT RUN |
| I8 | seed 후 admin/adminPeer/adminKeeper와 target promotion 후 별도 signin으로 실제 ADMIN ACCESS claim 발급, pre-seed USER claim 보존, 강등/정지/삭제 후 5-method 재검증 | STATIC CLOSED / HTTP NOT RUN |
| I9 | 고정 `REQUIRED_EVIDENCE_IDS`와 `ensureRequiredEvidenceChecks()` fail-closed, auth/bad-signature/refresh/read-only/write/UI confirm/canonical/retry/guard/stale evidence IDs 추가 | STATIC CLOSED / 전체 acceptance NOT RUN |

Fix1 범위의 정적 검증은 `node --check qa/m9-acceptance.mjs`와 whitespace/diff 점검뿐이다. 실제 합성 signup/signin, API, isolated MySQL, fingerprint/query-count, backend XML, Playwright/Chrome 1440px, source/JAR provenance는 실행하지 않았으므로 이 closure는 M9 PASS가 아니다. root가 source/JAR freeze와 위 native/status/BE XML handoff를 마친 뒤 동일 harness를 독립 실행하고, 결과를 각 matrix 행에 evidence ID로 연결해야 한다.

## 13. Audit-2 fix2 정적 보완 — 실제 인수 미실행

`acceptance-audit-2.md` F1~F9 원문과 root의 final artifact handoff를 다시 대조했다. 아래 보완은 QA harness·QA 기록 범위에 한정하며 제품/API/DB 계약을 새로 확정하지 않는다.

| finding | fix2 보완 | 현재 판정 |
|---|---|---|
| F1 / I8 | promotion 뒤 pre-promotion USER claim도 current ACTIVE DB ADMIN이면 `200`/envelope/no-store로 확인하고, fresh ADMIN claim·demotion stale ADMIN 거부를 별도 유지 | STATIC HARNESS UPDATED / HTTP NOT RUN |
| F2 / I5 | `qa/m9-test-evidence.mjs`가 final backend XML을 Python stdlib XML로 구조 파싱하고 suite totals, exact testcase/classname, testcase failure/error/skipped, source method/assertion 연결을 검증한다. AdminConcurrency 7 cases와 AdminQueryBound 3 cases의 RR/lock evidence를 substring marker로 대체하지 않는다 | HELPER 13/13 FOCUSED PASS / FINAL RUNTIME NOT RUN |
| F3 / I4 | admin route glob을 slash/query를 포함하는 정규식으로 교체하고, expiry 1회 소비·ready/release 유한 timeout·same-document marker·B 안정화 후 release·finally cleanup을 사용한다. list/detail/mutation 각각 real upstream body hold를 유지한다 | STATIC HARNESS UPDATED / Chrome NOT RUN |
| F4 / I9 | signin 실제 accessible name `접속하기 ✦`, modal `role=dialog`/`aria-label` title, delete copy dialog scope를 사용한다 | STATIC HARNESS UPDATED / Chrome NOT RUN |
| F5 | guest/USER/ADMIN 일반 browser journey를 별도 BrowserContext로 격리하고, same-Provider account switch만 stale probe context에서 수행한다 | STATIC HARNESS UPDATED / Chrome NOT RUN |
| F6 / I6 | 삭제 거부용 refresh probe를 `before-target-delete` fingerprint 이전에 만들고 전후 fingerprint 사이 target signin을 금지한다 | STATIC HARNESS UPDATED / DB NOT RUN |
| F7 / I9 | invalid role/subject env, no-op audit, suspended/deleted signin·post/comment/like/universe write, suspended refresh 재사용·reactivation, pending/empty/transient/retry/duplicate-submit/deny re-entry rows를 required ID로 고정했다. helper의 mapped-only PASS와 direct caller evidence를 합치며, required NOT RUN이 있으면 `M9_REQUIRE_FULL=0`이어도 top-level `PASS`가 되지 않는다 | STATIC MATRIX UPDATED / runtime NOT RUN |
| F8 / I6 | target→soft-deleted opponent ACCEPTED relation, target own soft-deleted comment, PUBLIC/UNIVERSE/PRIVATE-draft rows를 실제 API fixture로 만들고 active aggregate에서 제외·history/fingerprint에서 보존되는지 확인한다 | STATIC FIXTURE UPDATED / API/DB NOT RUN |
| F9 / I7 | email/nickname/name 독립 keyword fixture, exact ID set, same-createdAt global DESC ID page slices를 추가했다 | STATIC MATRIX UPDATED / HTTP NOT RUN |

### 13.1 Evidence helper handoff

`qa/m9-test-evidence.mjs`의 `collectTestEvidence({root, backendDir, backendSummary, frontendJson})`는 다음 다섯 결과를 반환한다: `concurrency-physical-barrier-evidence`, `backend-rr-writer-evidence`, `backend-noop-audit-evidence`, `backend-state-policy-evidence`, `frontend-matrix-evidence`. `mappings.backend/frontend`, `uncoveredFrontendRows`, `uncoveredStatePolicyRows`, `artifactHashes`는 raw token/log 없이 보존한다. helper의 현재 13개 focused test는 artifact 변조·deceptive comment marker·wrong class·summary mismatch·FE failed assertion/source comment 위장을 거부한다. mapped named coverage가 전체 browser/HTTP matrix를 대신하지 않으며, direct harness rows가 모두 실제 PASS일 때만 caller가 uncovered row를 닫는다.

### 13.2 Fix2 정적 검증과 경계

검증 명령은 `node --check qa/m9-acceptance.mjs` exit 0, `node --check qa/m9-test-evidence.mjs` exit 0, `node --check qa/m9-test-evidence.test.mjs` exit 0, `node --test qa/m9-test-evidence.test.mjs` exit 0 (13/13), `git diff --check -- qa/m9-acceptance.mjs qa/m9-test-evidence.mjs qa/m9-test-evidence.test.mjs` exit 0이다. 이 명령들은 저장된 artifact parser와 JavaScript syntax만 검사한다. signup/signin, API, MySQL 13319, fingerprint/query-count, backend final runtime, Playwright/Chrome, source/JAR live provenance는 실행하지 않았으므로 현재 M9 판정은 **NOT RUN / A-v2 APPROVED / HARNESS UPDATED**이며 PASS·완료·merge-ready를 주장하지 않는다.
