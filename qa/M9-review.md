# M9 — 관리자 QA 인수 증거와 독립 검토 상태

> **현재 상태: A-v2 APPROVED / M9 독립 QA PASS · READY TO MERGE / PR·dev 통합 대기**
>
> 사용자의 `A-v2 전체 승인`으로 회의 §5·§10·§10.1·§11, REQUIREMENTS §6.13-A, ADR-0008이 현재 계약이 됐다. run7의 396/0/0 실제 HTTP·Chrome·격리 DB 증거는 M1 수정 전 source에 대한 역사 증거로 보존한다. `final-review.md`의 Minor M1은 `5df2db0`에서 수정됐고, root fresh FE 51 files/465 tests·lint/build exit0와 독립 scoped 재검토 `CLEAN / READY TO MERGE`로 닫혔다. 현재 source manifest는 424 files / `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`, backend JAR SHA는 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`로 불변이다. PR/dev merge는 아직 하지 않았다.

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

§3~8의 check ID는 `build/m9-acceptance-20261111-174413/evidence.json`의 실제 PASS를 가리킨다. 이 run7 매트릭스의 396 checks는 M1 수정 전 source에 대한 역사적 runtime evidence다. `AUTO PASS`는 관측된 자동 검사 범위이며 현재 M1 closure나 merge 완료를 뜻하지 않는다. M1은 FE 검색 query serialization만 바꿨고, 현재 판정은 아래 최신 FE 검증과 독립 scoped 재검토를 함께 대조한다. 세부 BE/FE source·testcase·artifact hash 연결은 같은 JSON의 `fixture.testArtifacts`에 보존한다.

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| 공통 envelope | 모든 admin 성공/실패 응답의 `success/data/error/timestamp` 검사 | `ApiResponse` 형식과 오류 payload 일치 | AUTO PASS — `http-admin-list-envelope`, `http-admin-detail-envelope`, `auth-missing-admin-endpoints`, `validation-admin-bodies` |
| 목록 endpoint | `GET /api/v1/admin/users` | ADMIN만 접근, `PageResponse` offset 필드와 summary 계약 | AUTO PASS — `http-admin-list-status`, `http-admin-list-shape` |
| 상세 endpoint | `GET /api/v1/admin/users/{id}` | ADMIN만 접근, canonical 상세 DTO | AUTO PASS — `http-admin-detail-status`, `http-admin-detail-shape` |
| role mutation | `PUT /api/v1/admin/users/{id}/role` | body enum 검증, 성공 canonical 상세 | AUTO PASS — `mutation-role-canonical`, `validation-admin-bodies` |
| status mutation | `PUT /api/v1/admin/users/{id}/status` | `ACTIVE/SUSPENDED`, 성공 canonical 상세 | AUTO PASS — `mutation-status-canonical`, `validation-admin-bodies` |
| soft delete | `DELETE /api/v1/admin/users/{id}` | `users.deleted_at`만 설정, empty success, 하위 이력·파일 보존 | AUTO PASS — `mutation-soft-delete`, `post-mutation-subgraph-fingerprint`, `http-deleted-image-preservation` |
| missing auth | 5 admin method에 Authorization 없음/빈 값 | `401 AUTH_004`, private/no-store, envelope | AUTO PASS — `auth-missing-admin-endpoints` |
| malformed auth | Basic, malformed Bearer, bad signature, wrong token type | `401 AUTH_004`, private/no-store, envelope | AUTO PASS — `auth-malformed-admin-endpoints`, `auth-bad-signature-admin-endpoints`, `auth-wrong-type-admin-endpoints` |
| expired access | 서명된 만료 ACCESS | `401 AUTH_002`; refresh 재시도 여부는 기존 apiClient 계약대로 | AUTO PASS — `auth-expired-admin-endpoints`, `browser-auth-final-failure-boundary` |
| non-admin | 유효 USER ACCESS로 모든 endpoint 호출 | `403 ADMIN_001`; 목록·상세·mutation 데이터 미노출 | AUTO PASS — `auth-user-admin-endpoints` |
| current role 재평가 | ADMIN token 발급 후 DB role을 USER로 변경하고 재호출 | 기존 claim만으로 통과하지 않고 `403 ADMIN_001`; 반대로 current ADMIN 승격은 허용 | AUTO PASS — `mutation-role-promote-current-token`, `mutation-role-demote`, `backend-state-policy-evidence` |
| current status/delete 재평가 | actor SUSPENDED 또는 deleted 후 기존 ACCESS 재호출 | 승인된 `USER_003`/`USER_001` 경계, 쓰기·관리 API 차단 | AUTO PASS — `auth-suspended-admin-endpoints`, `auth-deleted-admin-endpoints`, `mutation-deleted-admin-claim-recheck` |
| validation | 음수 page, size 0/101, 100/101자 keyword, 잘못된 role/status body·enum/id/query | `400 VALIDATION_001`, no-store, 내부 SQL/stacktrace 미노출; 100자는 허용 | AUTO PASS — `validation-admin-queries`, `validation-admin-keyword-boundary`, `validation-admin-bodies` |
| OpenAPI | `/v3/api-docs`와 Swagger | 5 endpoint method/security/request/response/error 문서 | AUTO PASS — `runtime-api-openapi`, `openapi-m9-paths`; method 노출 확인, final review 추가 finding 없음 |
| cache policy | 성공·401·403·404·400 모두 header 검사 | `Cache-Control: private, no-store` | AUTO PASS — `http-admin-list-no-store`, `http-admin-detail-no-store`, `auth-user-admin-endpoints`, `validation-admin-queries`, `mutation-missing-target` |
| signed claim shape | root synthetic runtime의 `M9_MISSING_ROLE_TOKEN`·`M9_MISSING_SUBJECT_TOKEN` 및 invalid role/subject | 5개 method 모두 `AUTH_004/401`·private/no-store, 정상 인증 계약 보존 | AUTO PASS — `auth-missing-role-admin-endpoints`, `auth-missing-subject-admin-endpoints`, `auth-invalid-role-admin-endpoints`, `auth-invalid-subject-admin-endpoints` |

## 4. 목록·summary·상세 데이터 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| 검색 범위 | email/nickname/name keyword trim/max100·대소문자·`%_\\` | OR 부분검색, wildcard literal, 응답 사용자 집합 정확 | AUTO PASS — `list-email-search-exact`, `list-nickname-search-exact`, `list-name-search-exact`, `list-literal-wildcard-search-exact`, `validation-admin-keyword-boundary` |
| role/status 조건 | 승인된 `role`, `status` query를 조합 | 조건과 count/list가 일치. 화면에는 디자인대로 상태 filter를 만들지 않음 | AUTO PASS — `list-role-filter-exact`, `list-status-filter-exact` |
| offset paging | 0/1/큰 offset, size 경계, 마지막 빈 page | 정상 `totalElements/totalPages/hasNext`, 중복·누락 없음 | AUTO PASS — `list-page-shape`, `list-page-size-boundary`, `list-same-created-at-pages`, `list-large-offset-empty-exact` |
| 안정 정렬 | 동일 createdAt fixture | 승인된 deterministic sort(기본 createdAt DESC,id DESC) | AUTO PASS — `list-stable-sort`, `list-same-created-at-global-order` |
| deleted 제외 | soft-deleted 대상과 활성 대상 혼합 | 목록·summary·통계에서 deleted 계정 제외 | AUTO PASS — `list-live-id-set-exact`, `list-summary-no-deleted` |
| 전체 사용자 카드 | 검색·페이지와 무관한 summary 대조 | 삭제 사용자 제외 생존 전체 기준 `totalUsers` | AUTO PASS — `list-summary-exact` |
| 이번 주 가입 | Asia/Seoul 월요일 00:00 주경계 fixture, 요청시각 단일 Clock | UTC audit 변환 후 `joinedThisWeek` 정확 | AUTO PASS — `http-admin-summary-kst-boundary` |
| 정지 계정 | ACTIVE/SUSPENDED/deleted 혼합 | deleted 제외, SUSPENDED count 정확 | AUTO PASS — `list-summary-exact`, `list-status-filter-exact` |
| 목록 privacy | 응답 JSON과 SQL projection 대조 | password/hash/token·민감 본문·승인되지 않은 birthDate 미노출 | AUTO PASS — `http-admin-list-privacy` |
| blog/post count | 다중 blog·draft·deleted blog/post fixture | 생존 blog/post, draft·모든 visibility 포함, 기본 blog는 활성 최소 id | AUTO PASS — `fixture-native-subgraph`, `http-admin-detail-exact-aggregates` |
| 상세 기본 정보 | 활성·정지·없는·삭제 대상 요청 | 승인된 상태/삭제 오류와 개인정보 최소 DTO | AUTO PASS — `http-admin-detail-shape`, `http-admin-detail-privacy`, `mutation-missing-target` |
| 상세 통계 | post/comment/universe fixture | post `total=published+draft`, 본인 미삭제 댓글/답글, 생존 상대 ACCEPTED 방향 distinct·교집합 distinct | AUTO PASS — `http-admin-detail-post-stats`, `http-admin-detail-exact-aggregates` |
| last access | 응답·화면·source 대조 | 수집하지 않은 `null`/`마지막 접속 기록 없음`; `updatedAt` 대용 금지 | AUTO PASS — `http-admin-detail-last-access-null`, `browser-admin-detail-last-access` |

## 5. 변경·삭제·권한 상실 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| role USER→ADMIN | ADMIN actor가 대상 role 변경 | 다음 인증/관리 요청에서 DB current role 반영 | AUTO PASS — `mutation-role-promote-current-token`, `mutation-role-canonical` |
| role ADMIN→USER | 대상이 다른 ADMIN인 경우 | 승인된 정책대로 변경, 기존 target token 권한 재평가 | AUTO PASS — `mutation-role-demote`, `mutation-role-demote-canonical` |
| self ADMIN 회수 | actor==target, ADMIN→USER | `ADMIN_002`(기존 ErrorCode HTTP 400) 또는 승인된 코드, role 유지 | AUTO PASS — `mutation-self-protect`, `backend-noop-audit-evidence` |
| self delete | actor==target DELETE | 자기 삭제 거부, 계정·refresh·기존 데이터 보존 | AUTO PASS — `mutation-self-protect`, `backend-noop-audit-evidence` |
| self suspend | actor==target, `ACTIVE→SUSPENDED` | `ADMIN_002/400`, 승인 메시지, 상태 불변 | AUTO PASS — `mutation-self-protect`, `backend-noop-audit-evidence` |
| status ACTIVE↔SUSPENDED | 대상 로그인·기존 access·각 작성 mutation을 상태 전후 호출 | 로그인/새 글·댓글·좋아요·유니버스 신청 차단, 기존 PUBLIC 가시성은 FR 정책과 일치 | AUTO PASS — `mutation-suspend-boundary`, `mutation-suspended-signin-and-writes`, `refresh-reactivated-existing-accepted`, `backend-state-policy-evidence` |
| repeated status/role | 동일 body를 반복 호출 | 승인된 idempotence, 불필요 audit/updatedAt 변경 없음 | AUTO PASS — `mutation-same-value-audit-immutable`, `mutation-status-idempotence`, `backend-noop-audit-evidence` |
| target missing/deleted | 없는 ID·이미 deleted ID·race 대상 | `USER_001` 등 승인 오류, 다른 사용자 변경 없음 | AUTO PASS — `mutation-missing-target`, `mutation-delete-idempotence-contract` |
| soft delete effect | 삭제 전후 root helper fingerprint/API/기존 공개 글·댓글·알림·이미지 ACL 대조 | `deleted_at`만 설정, 하위 행·파일 보존, blog/post/image 차단·댓글 placeholder·알림 actor/target masking | AUTO PASS — `post-mutation-subgraph-fingerprint`, `http-target-image-after-delete`, `http-deleted-post-acl`, `http-deleted-blog-acl`, `http-target-comment-placeholder`, `http-target-reply-placeholder`, `http-target-actor-notification-mask` |
| failed mutation | validation/권한/DB 오류 주입 | actor·target·role/status/deleted_at 부분 변경 없음 | AUTO PASS — `validation-admin-bodies`, `mutation-self-protect`, `backend-noop-audit-evidence`, `browser-mutation-error-retry`; DB 장애 주입 실측은 주장하지 않음 |
| canonical refresh | 성공 mutation 뒤 목록·상세 재조회 | 서버 현재 상태로 UI 동기화, stale 이전 값 복귀 없음 | AUTO PASS — `browser-role-confirm-canonical`, `browser-status-confirm-canonical`, `browser-delete-confirm-canonical` |
| audit/privacy | 응답·로그·DB diff 검사 | raw token/password/hash 및 불필요 개인정보 미출력 | AUTO PASS — `http-admin-list-privacy`, `http-admin-detail-privacy`, `mutation-same-value-audit-immutable`; final review 추가 finding 없음 |

## 6. 동시성·선형화 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| actor/target lock | 두 ADMIN이 상호 대상 role/status/delete mutation을 MySQL barrier로 실행 | deleted 포함 actor/target ID 오름차순 lock, lock 후 current 재검증, 권한 우회 없음 | AUTO PASS — `concurrency-physical-barrier-evidence` |
| actor demotion race | A request와 B의 A role/status 변경을 root barrier로 동시 시작 | DB current actor 검증 시점에 따른 성공/거부, 후행 무권한 작업 차단 | AUTO PASS — `concurrency-actor-demotion-exact`, `concurrency-actor-demotion-race`, `concurrency-physical-barrier-evidence` |
| target mutation race | status↔delete, role↔delete 동시 실행 | 한 선형화 결과, partial field/복구 불가 상태 없음 | AUTO PASS — `concurrency-target-mutation-exact`, `concurrency-target-mutation-race` |
| stale ACCESS | role/status/delete 후 기존 JWT를 동시에 사용 | JWT claim만으로 admin/쓰기 권한 유지되지 않음 | AUTO PASS — `concurrency-stale-access`, `backend-state-policy-evidence` |
| list snapshot | summary/list/detail 동시 mutation | 명시 RR 동일 snapshot, count/list 불일치·dirty read 없음 | AUTO PASS — `backend-rr-writer-evidence` |
| query bound | synthetic query-count helper/sql, size 1/100, 14 SELECT bound | 실제 HTTP 200 request 전후 counter, 양쪽 1~14·delta 0, N+1 없음 | AUTO PASS — `admin-query-bound` |

## 7. Frontend·1440px 브라우저 매트릭스

| 기준 | 독립 확인 방법 | 기대 증거 | 상태 |
|---|---|---|---|
| AdminRoute loading | auth restore/current admin probe 지연 | 판단 전 대기, pending 중 민감 화면·보호 데이터 미표시 | AUTO PASS — `browser-admin-list-pending-sensitive`, `browser-stale-list-pending-sensitive`, `browser-stale-detail-pending-sensitive`, `frontend-matrix-evidence` |
| guest guard | 비로그인 `/admin`, `/admin/users/:id` | `/signin` 이동, admin API 미호출 | AUTO PASS — `browser-guest-admin-guard`, `browser-guest-detail-guard` |
| USER guard | 로그인 USER가 직접 URL 접근 | 승인된 비관리자 화면/403 처리, admin 데이터 미표시 | AUTO PASS — `browser-user-admin-guard` |
| ADMIN entry | 프로필 메뉴에서 관리자 진입 | ADMIN에게만 링크, 상단 전용 버튼/사이드바 Admin 항목 없음 | AUTO PASS — `browser-admin-entry-menu` |
| list layout | 1440×1000 screenshot | 1100px 단일 컬럼, `ADMIN CONSOLE`, `ROLE: ADMIN`, 검색, 통계 3카드, 테이블 정본 일치 | AUTO PASS — `browser-admin-list-width`, `browser-admin-heading`, `browser-admin-role-label`, `browser-screenshot-evidence`, `design-admin-reference`; final review visual 추가 finding 없음 |
| detail layout | 1440×1000 screenshot | 860px, breadcrumb, header/stats/actions/copy/token 정본 일치 | AUTO PASS — `browser-admin-detail-width`, `browser-admin-detail-last-access`, `browser-screenshot-evidence`, `design-admin-reference`; final review visual 추가 finding 없음 |
| loading/empty/error | 목록·상세 200/empty/500/403/404 | 빈 상태·오류·retry가 명확하고 기존 행/입력 보존 | AUTO PASS — `browser-list-empty-state`, `browser-list-transient-retry-result`, `browser-detail-transient-retry-result`, `browser-target-404`, `browser-admin-denied-reentry` |
| search/paging URL | keyword/query/page/back/reload | URL 및 결과 복원, 오래된 검색 응답이 최신 결과를 덮지 않음 | AUTO PASS — `browser-search-url-state`, `browser-search-paging-history`, `frontend-matrix-evidence` |
| role mutation UI | select→confirm→success/error | canonical 재조회, 실패 시 선택/행 보존, 중복 클릭 dedupe | AUTO PASS — `browser-role-confirm-canonical`, `browser-mutation-duplicate-submit`, `browser-mutation-error-retry` |
| status UI | suspend/activate confirm→success/error | warning 상태·캡션과 API 결과 일치, 자기 대상 정책 표시 | AUTO PASS — `browser-status-confirm-canonical`, `frontend-matrix-evidence` |
| delete UI | danger confirm→success/error | 승인 카피·대상/행동 확인, 성공 후 목록 복귀, 실패 retry | AUTO PASS — `browser-delete-copy`, `browser-delete-confirm-canonical`, `frontend-matrix-evidence` |
| account/session stale | 실제 SPA ADMIN A→USER B 전환 중 list/detail/mutation 지연 | A 데이터·retry·navigation이 B 화면에 복귀하지 않음, current probe로 재평가 | AUTO PASS — `browser-stale-list`, `browser-stale-detail`, `browser-stale-mutation`, `browser-account-switch-stale` |
| auth final failure | 401→refresh 실패 | 민감한 목록·상세·mutation retry 제거, signin 경계 | AUTO PASS — `browser-auth-final-failure-boundary` |
| visual tokens | screenshot/source direct comparison | radius 0, ink/paper/accent/warning/danger, hard shadow, 폰트·카피 일치 | AUTO PASS — `browser-screenshot-evidence`, `design-admin-reference`; final review visual 추가 finding 없음 |

## 8. DB·회귀·artifact gate

| Gate | 기준 | 상태 |
|---|---|---|
| backend unit/controller | role/status/self-protect/validation/error/no-store 테스트 | PASS — `backend-final-2/test-results/`의 AdminControllerTest 및 `backend-state-policy-evidence` |
| repository/JPA | aggregate count, soft delete, auditing, lock, paging projection | PASS — `backend-final-2/test-results/`의 AdminQuery/MySql·Mutation·Concurrency XML, `backend-noop-audit-evidence` |
| backend full | Gradle `test bootJar`, failures/errors/skips `0/0/0` | PASS — `build/m9-evidence/backend-final-2/summary.json`: 109 suites / 637 tests, failure/error/skip 0, exit0 |
| frontend full | test(`--maxWorkers=1`), lint, build | PASS — M1 수정 후 root fresh revalidation: 51 files / 465 tests; lint/build exit0 (`task-4-final-m1-report.md`, `final-m1-rereview.md`) |
| migration | 신규 migration 여부 확인. 기존 V1~V6/schema/data fingerprint 보존 | 기존 migration 회귀 PASS — final BE XML 및 whole-branch review 대조; merged tree 재확인은 PR/dev 통합 후 |
| M4~M8 regression | auth/refresh, posts/ACL, universe, comments/likes, feed/search, notifications | PASS — backend 637 및 M1 수정 후 FE 51 files/465 tests; run7 FE464는 수정 전 역사 수치로 합산하지 않음 |
| source/JAR identity | tested source manifest와 immutable runtime JAR hash 일치 | PASS — 최신 source manifest 424 files / `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`; backend JAR `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52` 불변. run7의 `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`는 역사 manifest |
| HTTP/API | 격리 MySQL runtime, OpenAPI, 모든 auth/status/delete matrix | AUTO PASS — run7 `evidence.json`의 역사 runtime evidence, `required-matrix-complete`; M1 수정 후 API 재실행은 하지 않음 |
| Chrome | 1440px 목록·상세·guest/USER/ADMIN/stale/error screenshots | AUTO PASS — run7 browser checks·1440px screenshot 2개라는 역사 evidence; M1은 시각 변경이 없어 Chrome 재실행은 하지 않음 |
| independent review | 구현자와 다른 컨텍스트에서 diff·정본·출력 대조 | PASS — `final-m1-rereview.md`: Critical/Important/Minor 0, `CLEAN / READY TO MERGE`; PR/dev merge는 별도 gate |

## 9. A-v2 실행 순서와 잔여 위험

1. backend/frontend source freeze와 source manifest/JAR가 root에서 준비되기 전에는 acceptance를 실행하지 않는다.
2. 합성 계정은 signup/signin API로만 만들고, ADMIN/status/deleted/동일시각/주경계/image fixture는 root synthetic runtime helper가 seed/status handshake로 적용한다.
3. QA는 실제 API·격리 MySQL read-only fingerprint/query-count·system Chrome 1440px를 독립 실행하고, finding은 원 소유자 수정 후 scoped re-review한다.
4. 사용자 DB `13307`, 기존 업로드, `.env.local`, 제품 코드/타 역할 테스트는 읽거나 수정하지 않는다.
5. 최대 fan-out/운영 SLA를 측정하지 않았다면 PASS로 표현하지 않는다. `lastAccessAt` 수집·제품 bootstrap·전역 refresh revoke·새 migration·M10은 검사 대상이 아니다.

run7은 M1 수정 전 source에서 current auth, lock/refresh 경합, 하위 데이터 보존·ACL masking, RR snapshot, query bound, 1440px/SPA stale 및 source/JAR identity 자동 gate를 통과했다. 이후 Minor M1은 FE focused/full 검증과 독립 scoped 재검토에서 닫혔고, 현재 QA 판정은 **M9 독립 QA PASS / READY TO MERGE**다. 남은 gate는 리더의 PR/dev 통합과 merged tree 대조다. 최대 fan-out·운영 SLA 및 DB 장애 주입까지 검증했다는 주장은 하지 않는다.

## 10. 현재 판정

- **M9 독립 QA PASS / READY TO MERGE.** `final-review.md`의 Minor M1 1건은 `5df2db0`에서 검색어 양끝 trim-only 및 내부 whitespace 보존으로 수정됐다. `final-m1-rereview.md`는 Critical/Important/Minor 각 0, `CLEAN / READY TO MERGE`다.
- 최신 FE 근거는 focused `adminApi.test.ts` 1 file/4 tests PASS, root fresh full 51 files/465 tests PASS, lint/build exit0다. backend는 109 suites/637 tests, failure/error/skip 0이며 JAR SHA `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`가 유지됐다.
- 최신 source manifest는 424 files / SHA-256 `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`다. run7의 396 acceptance checks / 0 FAIL / 0 NOT RUN, FE 464 및 source SHA `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`는 수정 전 source에 대한 역사 evidence이며 현재 FE465와 합산하지 않는다.
- 실제 실행자는 root, harness 보완·artifact 대조는 QA `/root/m9_qa_fix5`, 최종 review와 scoped re-review는 구현자와 분리된 컨텍스트였다. 초기 실패/NOT RUN run과 run7 원시 증거는 삭제하지 않았다.
- 현재 판정에서 남은 외부 gate는 root의 PR/dev 통합·merged tree/source identity 대조뿐이다. 이번 M1 수정 뒤 API/DB/Chrome acceptance를 다시 실행했다는 주장은 하지 않으며, 해당 runtime 증거는 run7 역사 evidence로 한정한다.
- 현재 기록 갱신은 QA 문서만 변경했다. main harness/helper·제품·runtime·사용자 DB/환경/업로드·Git mutation은 수행하지 않았다.

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

위 명령은 runtime handoff 계약이다. 현재는 root의 run7 실행과 실제 artifact 대조가 완료되어 §3~8에 결과를 연결했다. 아래 과거 정적 보완 기록의 NOT RUN은 해당 시점의 판정이며 최신 상태를 뜻하지 않는다.

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

## 14. Audit-3 R1~R5 fix3 정적 보완 — 실제 인수 미실행

`acceptance-audit-3.md`의 Important R1~R5만 현재 harness와 대조해 수정했다. 새 matrix나 helper 파일은 만들거나 고치지 않았고, 제품/API/DB/브라우저/Git은 실행·수정하지 않았다.

| finding | fix3 보완 | 현재 판정 |
|---|---|---|
| R1 | 기존 정지 경계의 동일 boolean 결과를 필수 `mutation-suspended-signin-and-writes` ID에도 연결하고 기존 `mutation-suspend-boundary` coverage는 유지 | STATIC FIXED / HTTP NOT RUN |
| R2 | `관리자 사용자 목록` table의 row locator로 pending/empty/denied/re-entry/재시도/stale 행 검사를 scope하고 `waitForAdminReady`의 expected row도 table 안으로 제한해 TopBar nickname을 행으로 세지 않음 | STATIC FIXED / Chrome NOT RUN |
| R3 | transient 500→retry keyword를 실제 `adminKeeper.email`로 바꿔 canonical 200에서 keeper row를 정확히 요구하고 empty query는 별도 no-match로 유지 | STATIC FIXED / Chrome NOT RUN |
| R4 | stale list hold predicate를 실제 기본 `size=20`으로 맞추고, paging 복귀 URL을 `size=20`으로 고정해 bulk 21개를 ID DESC 20+1 slice로 page0/page1/back/reload에서 검증 | STATIC FIXED / Chrome NOT RUN |
| R5 | 공개 deleted signin은 기존 인증 계약인 `AUTH_001/401`로 검증하고 authenticated deleted post/comment/like/universe는 기존 `USER_001/404` 경계를 유지 | STATIC FIXED / HTTP NOT RUN |

### 14.1 검증·경계

- `node --check qa/m9-acceptance.mjs` exit 0.
- `git diff --check -- qa/m9-acceptance.mjs` exit 0; 대상 harness trailing whitespace clean.
- 실제 signup/signin·API `18089`·FE `5179`·MySQL `13319`·status/fingerprint/query-count·backend barrier·Chrome/1440px는 실행하지 않았다. R1~R5는 **정적 보완 완료 / ACCEPTANCE NOT RUN**이며 PASS·merge-ready를 주장하지 않는다.
- helper `qa/m9-test-evidence.mjs` 및 `qa/m9-test-evidence.test.mjs`는 audit-3에서 수정하지 않고 동결 보존했다. root가 명시 QA 파일을 커밋하고 scoped re-review 후 실제 harness를 실행한다.

## 15. Audit-4 N1 fix4 — 상세/mutation stale 검사 복원

- 근거: `acceptance-audit-4.md` N1, REQUIREMENTS §6.13-A의 계정/request/mutation stale 폐기·retry 제거, PRD §7 관리자 상세의 늦은 응답·retry·이동 폐기.
- 상세와 mutation 최종 검사는 실제 `section[aria-label="사용자 상세"]`, 대상 email(실제 DOM의 가입일과 결합된 텍스트 포함), `UID #NNNN`, `다시 시도` 버튼이 모두 없는지 확인한다. 목록은 기존 table/row helper를 유지하며 B stable session/home/same-document/held response release 검사는 변경하지 않았다.
- upload 실패 진단에는 HTTP status와 형식이 제한된 domain error code만 기록한다. 응답 body·token·credentials는 기록하지 않으며 upload 성공 조건은 201과 실제 content URL로 유지한다.
- `node --check qa/m9-acceptance.mjs` exit 0, scoped `git diff --check` exit 0. helper 두 파일·제품·Git·runtime·DB·브라우저는 변경/실행하지 않았다. 새 matrix·refactor·광역 재검토는 하지 않았다.
- 현재 실제 판정: `build/m9-acceptance-20261111-163214/evidence.json`에서 진단1의 `fixture-image-upload` FAIL 및 runner 중단을 확인했다. root 인계에 따르면 404는 로컬/test profile 누락이며 root가 runtime만 보정했다. 제품 결함 판정이나 요구 검사 완화 근거로 삼지 않는다. **RUN FAILED / 미도달 required matrix NOT RUN / fix4 STATIC-FROZEN**이며 root commit·scoped 재검토·진단2·live provenance·HTTP/DB/Chrome/시각 최종 대조 전 PASS·merge-ready를 주장하지 않는다.


## 2026-11-11 KST — Task4 fixround5 image fixture 계약 보완·소스 동결

- 담당: 새 QA 구현 컨텍스트 `/root/m9_qa_fix5`, 기준 HEAD `d409d28`. 범위는 실제 `fixture-target-image-bind` 실패 하나다.
- RED: `build/m9-acceptance-20261111-163859/evidence.json`의 upload PASS(201)/bind FAIL/runner 중단, root exact 재현 `build/m9-evidence/image-bind-diagnostic-response.json`의 HTTP400 `UPLOAD_004`(본문 이미지와 이미지 목록 불일치). 진단2 top-level은 NOT RUN이며 미도달 matrix를 PASS로 바꾸지 않는다.
- 근거: REQUIREMENTS FR-POST-02/NFR-02, PRD §5.5·§9.7, PostDtos.UpdatePostRequest, PostController.update, PostService.updateInTransaction/updateImages/validateImageSnapshot을 대조했다. FE PostEditor.test.tsx의 draft/image 저장 사례와 BE PostServiceIntegrationTest의 image node/snapshot 검증, PostContentService image attrs 및 UploadService.bindPostImages의 owner/purpose/최초 binding을 읽었다. images-only API는 기존 본문 image URL 집합과 일치해야 하므로 plain-text 글에 snapshot만 추가한 harness가 계약을 위반했다.
- 변경: full PUT /posts/{id}에 기존 canonical title/category/thumbnail/tag 2개·marker 문단을 보존하고 image node·동일 images snapshot·PUBLIC·publish:true를 함께 전송한다. canonical 응답의 동일 id/PUBLIC/기존 publishedAt/tag 2개/본문 deep equality/image URL·alt·order를 확인하고 target snapshot을 갱신한다. node:util의 deep equality로 JSON object key 순서에는 의존하지 않는다. bind 진단은 status와 제한된 errorCode만 남기며 body/token/credentials를 출력하지 않는다. 기존 child row/file preservation 및 stats 검사는 보존했다.
- 검증: `node --check qa/m9-acceptance.mjs` exit 0, `git diff --check -- qa/m9-acceptance.mjs qa/M9-review.md .claude/team/qa/STATE.md .claude/team/qa/WORKLOG.md .superpowers/sdd/M9-admin/task-4-fix1-report.md` exit 0; 수정 파일 재독·scoped diff 확인. API/DB/Chrome/Gradle 및 동결 helper suite는 실행하지 않았다. Git mutation·제품 코드·제품 source/JAR·사용자 데이터 변경은 없다.
- 미해결: **FIX5 STATIC-FROZEN / 실제 진단2 RUN FAILED / 최종 인수 미완료**. root만 실패 run 보존·fresh 격리 runtime 재실행을 수행한다. 실제 GREEN·최종 PASS·merge-ready는 재실행 및 독립 최종 gate 전 주장하지 않는다.

## 2026-11-11 KST — Task4 Step3 진단3 auth fixture 통합 보완

- 담당: QA `/root/m9_qa_fix5`. 초기 정적 loop 5회는 acceptance-audit-6.md CLEAN으로 닫혔으며 이번은 계획된 Step3 runtime 통합이다. post-loop diff는 최종 whole-branch 검토 대상이고 광역 정적 audit를 재개하지 않는다.
- 실제 근거: `build/m9-acceptance-20261111-164556/evidence.json`은 image/native fixture를 통과한 뒤 auth 실패가 발생했다. root exact `signature-append-diagnostic-response.json`은 append-A 서명에서 decoded bytes 불변/HTTP200, `auth-matrix-diagnostic-response.json`은 current USER GET403/ADMIN_001 대비 본문 없는 PUT400/VALIDATION_001을 기록한다. 원래 잘못된 서명으로 보낸 DELETE에서 target이 삭제됐다는 root 인계를 유지하며 이후 집계/집합 기대를 완화하지 않는다.
- 수정: tamperJwt는 base64url signature를 Buffer로 디코딩하고 첫 바이트 XOR1 뒤 재인코딩한다. header/payload와 signature byte length는 보존한다. bad-signature GET이 AUTH_004/401이 아니면 PUT/DELETE를 보내기 전에 fail-fast하며 통과 후에도 기존 다섯 메서드를 모두 검사한다. role/status PUT은 각각 유효한 USER/ACTIVE 본문을 모든 auth 경로에서 전송한다. 잘못된 DTO에 대해 current actor 오류가 우선한다는 새 제품 계약을 만들지 않는다.
- 진단: 실패한 auth 행은 method/path/status·형식 제한 errorCode·envelope·cachePrivate/cacheNoStore boolean만 출력한다. body/token/credentials는 기록하지 않는다. helper 두 파일·제품 source/JAR·global set/summary/query 기대는 그대로다.
- 순수 검증: 기존 append 분기는 48-byte 0xff signature에서 decoded bytes 변경 assertion이 실패(RED exit1)했다. 수정된 실제 함수 definition을 vm으로 읽어 32/48/64-byte × 0/255 총6 cases에서 decoded bytes 변경, 첫 바이트 XOR1, 나머지 바이트·길이·header/payload 보존을 확인했다(GREEN exit0). 기존 append-A가 같은 48 bytes로 decode되는 것도 별도 assert했다. 최초 64-byte 재현은 변화가 있어 결함을 재현하지 못했으며 48-byte 재현으로 바로잡았다.
- 검증 명령: `node --check qa/m9-acceptance.mjs` exit0, QA 4개 파일 및 기존 report에 대한 `git diff --check` exit0; 변경 파일 재독·diff 확인. API/DB/Chrome/Gradle/helper suite·Git mutation은 실행하지 않았다. 실제 인수 GREEN/PASS는 이 순수 검사로 주장하지 않는다.
- 남은 일: root가 진단3을 보존하고 fresh run4를 수행한다. 실제 auth/current-state 5-method 결과와 이후 matrix, live provenance 및 최종 독립 whole-branch/시각 gate는 미완료다.

## 2026-11-11 KST — Task4 Step3 진단4 상태·상세 브라우저 harness 보완

- 범위: source `753d0c6`의 실제 진단4 `build/m9-acceptance-20261111-165511/evidence.json`에서 실행된 refresh 4행/deleted write/detail retry/lastAccess 오류 및 root의 accounts 미정의 중단 인계를 대조했다. 초기 정적 loop 재개·새 matrix·제품 변경은 없다.
- 원인 대조: root `build/m9-evidence/refresh-deleted-write-diagnostic-response.json`은 refresh401/AUTH_003 및 deleted own-blog post404/BLOG_001, Spring 기본 no-cache/no-store 헤더를 보였다. AuthController.refresh는 admin 전용 private 헤더를 설정하지 않으며 NotificationPublicationLock.lock은 삭제된 owner를 BLOG_001로 거부한다. frontend main.tsx StrictMode, AdminUserPage detail useEffect의 cleanup/cancel 및 재요청·retryKey, 실제 header의 email/가입일/lastAccess 결합 텍스트를 읽었다.
- 수정: target404 probe에 expectedKeeperId를 명시 전달한다. refresh 4행은 기존 no-store와 exact status/code/envelope를 요구하고 admin privateNoStore는 그대로 둔다. deleted post creation만 BLOG_001/404로 고치며 signin AUTH_001/401과 다른 authenticated writes USER_001/404를 유지한다. 실패 state행은 response별 status/제한 errorCode/envelope/cache boolean만 기록한다.
- 브라우저: lastAccess는 canonical target ID/email/null을 검증하고 상세 header의 target email visible을 기다린 뒤 부분 텍스트로 검사한다. transient detail은 retry 확인까지 모든 matching initial GET을500으로 유지하고 handler 제거 후 실제 retry를 클릭해200/canonical ID/email 및 header 표시를 확인한다. StrictMode 두 번째 초기 요청이500을 우회하는 one-shot 가정을 제거했다.
- 검증: `node --check qa/m9-acceptance.mjs` exit0; scoped `git diff --check` exit0. 기존 oxlint `-c /private/tmp/m9-qa-no-undef-config.json -A all -D no-undef qa/m9-acceptance.mjs`는 Node/browser env를 둔 임시 config로 RED(accounts만 미정의, exit1)→GREEN(exit0)을 확인했다. config 없는 최초 lint의 Node/DOM globals 경고는 환경 설정 누락으로 제외했으며 새 제품 의존성은 없다. 코드·문서 재독과 diff 확인을 마쳤다.
- 경계: 제품·helper 두 파일·API/DB/browser/Gradle·Git mutation은 변경/실행하지 않았다. 진단4에서 이미 PASS한 global set/query/auth 기대는 완화하지 않았다. root가 실패 run을 보존하고 fresh 재실행해야 하며 실제 브라우저 GREEN·전체 인수 PASS·최종 whole-branch/시각 gate는 남아 있다.

## 2026-11-11 KST — Task4 Step3 진단5 브라우저 route 프로토콜 보완

- 실제 RED: `build/m9-evidence/acceptance-diagnostic-5.log`에서 HTTP·상태·이미지보존·detail retry/lastAccess PASS 이후 paging/target404 list/mutation retry/delete canonical/stale detail 실패와 unhandled `Route is already handled!`를 읽었다. `build/m9-acceptance-20261111-170234`에는 최종 evidence.json이 없으므로 raw log의 부분 PASS를 전체 인수 PASS로 취급하지 않는다.
- root 독립 관측: `build/m9-evidence/browser-paging-detail-diagnostic.json`에서 bulk page0 IDs33..14/21 rows, page1 ID13/2 rows, back/reload 정상과 exact nickname0/header partial email1을 확인했다. main은 검색 이전 페이지와 같은 첫 행만 기다려 이전 결과를 readiness로 오인할 수 있어 각 keyword/size20/page의 실제200 응답과 정확 ID slice를 기다린다. page0는 absent/0 모두 의미상0으로 판정하며 기존20+1 기대를 유지한다. 실패하면 boolean·URL·row count·응답 ID를 기록한다. 초기 실패의 개별 boolean은 당시 artifact가 없으므로 추정과 확정을 구분한다.
- 관측된 selector/response 구분 수정: target404 뒤 목록은 size1 권한 probe가 아닌 size100/page0 canonical 목록을 기다린다. mutation retry는 header email로 행 보존을 확인하고 실제 mutation200/detail200·target ID/role·rendered role을 대조하며 안전한 상태 metadata를 남긴다. delete는 pathname /admin·정확 keyword와 canonical empty DTO로 확인한다.
- route 원인: 기존 held 여부는 fetch 이후 설정되어 중복 callback이 진입했고 shared body를 덮어쓸 수 있었다. dispose는 fulfillment를 기다리지 않았다. 이제 비동기 fetch 전 A Authorization을 메모리에 예약하고 중복 A 요청별 response/body/완료를 소유한다. release는 각 실제 browser response event·finished/body 일치를 기다리고, dispose는 release→handler 제거→bounded drain을 거친다. 취소/timeout/callback 오류는 failure와 delivery false로 기록한다. stale PASS는 delivery/disposal 성공·upstream200·B stable·same document·민감 fragment/retry 부재를 모두 요구한다.
- expiry는 one-shot 대신 captured A Authorization에만 지속401을 적용한다. refresh 실패 주입은 실제 signin 경계가 관측된 뒤 B signin 전에 제거하며 B header는 만료 주입을 통과한다. A access/refresh 실패 전달 횟수와 route error 여부를 확인한다. 원문 Authorization/body는 evidence에 남기지 않는다.
- 증거 보존: fixture/HTTP/각 browser stage 및 route failure에서 top-level NOT RUN checkpoint를 저장한다. JSON 쓰기는 직렬화된 temp→rename으로 보존하며 마지막 전체 gate만 PASS를 결정한다. 추가 required matrix나 제품 정책은 없다.
- 검증: 실제 함수를 읽는 순수 `node /private/tmp/m9-route-protocol-focused.mjs` exit0 — 동시2개 body 격리/각1회 fulfillment, A 반복401/B pass, browser delivery completion, idempotent finite disposal, cancellation failure/checkpoint 및 stuck-fetch disposal timeout/no unhandled를 확인했다. `node --check qa/m9-acceptance.mjs`, 기존 oxlint Node/browser 환경의 scoped no-undef, scoped git diff --check 모두 exit0. 기존 Playwright 로컬 types의 unroute(handler)/waitForResponse/finished 계약과 수정 파일을 재독했다.
- 경계: 위 순수 fake-page/route 검사는 실제 Chrome GREEN이 아니다. 이번 QA는 API/DB/browser/Gradle/helper suite·Git mutation을 실행하지 않았고 제품/helper/source/JAR은 변경하지 않았다. root fresh runtime·최종 whole-branch/시각/실제 artifact gate가 남았다.

## 2026-11-11 KST — Task4 Step3 진단6 잔여 두 검사 보완

- 실제 상태: `build/m9-acceptance-20261111-171732/evidence.json`을 읽어 394 PASS / 2 FAIL / 0 NOT RUN을 확인했다. 실패는 browser-stale-list-pending-sensitive와 design-admin-reference뿐이다. 기존 route 오류/미도달 없이 종료됐고 나머지 HTTP·controls·stale detail/mutation·helper gate는 통과했다. 최종 상태는 NOT RUN이며 아직 M9 PASS가 아니다.
- stale list 원인: special row는 기본 목록에도 있어 기존 visibility-only readiness가 special 검색 완료를 증명하지 못했다. 또한 bulk query 실제 응답에 없는 keeper를 검사해 held response와 민감 행 anchor가 연결되지 않았다. special keyword/size20/page0의 실제200 및 정확 special ID 1개·렌더1행을 먼저 확인하고 clear keyword의 real200을 보류한다. held body에서 숫자 userIds만 읽고 baseline에 없는 실제 합성 계정을 anchor로 선택한다. pending/final 검사와 evidence는 동일 anchor ID에 연결하며 이전에 허용된 baseline 행의 유지는 금지하지 않는다.
- 디자인 원인: 기본 디자인 화면의 body.innerText에서 숨겨진 admin 화면을 찾았다. root `build/m9-design-reference.mjs` 및 원본 switcher/data-screen-label을 대조해 관리자와 관리자·상세를 각각 클릭한 뒤 visible 화면 1개·innerText의 ADMIN CONSOLE/사용자·양수 bounds를 검증한다. 숨긴 textContent 존재로 PASS하지 않는다. 화면별 safe metadata는 evidence.designReference에 보존한다.
- 보존: holder 변경은 ready metadata의 숫자 ID 추출뿐이며 actual delivery/expiry/dispose/B stable/same-document/late-response 부재 gate를 유지한다. stdout 마지막 출력만 경로·건수로 줄이고 전체 fixture/hash/evidence 데이터는 JSON에 남긴다.
- 검증: `node --check qa/m9-acceptance.mjs`, Node/browser 환경의 기존 oxlint scoped no-undef, scoped git diff --check 모두 exit0. `node /private/tmp/m9-route-protocol-focused.mjs`는 actual 함수의 numeric IDs 필터·중복body/expiry/delivery/disposal/failure 경로를 재검증해 exit0이었다. 변경 파일·diff·기록을 재독했다.
- 경계: QA는 API/browser/DB/Gradle/Git mutation 및 frozen helper를 실행·변경하지 않았다. 제품 source/JAR·디자인 정본도 변경하지 않았다. root fresh run7와 최종 whole-branch/시각/실제 artifact gate가 남아 있으며 순수 검사로 실제 GREEN/PASS를 주장하지 않는다.

## 2026-11-11 KST — Task4 Step3 run7 실제 자동 인수 PASS / 독립 최종 gate 대기

- 실제 증거: `build/m9-acceptance-20261111-174413/evidence.json` status PASS, **396 acceptance checks PASS / 0 FAIL / 0 NOT RUN**. `build/m9-evidence/acceptance-diagnostic-7.log`의 마지막 summary도 396/0/0, screenshot 2개이며 root 실행 exit0 인계와 일치한다. 초기 실패 run은 보존했고 당시 부분 PASS를 최종 결과로 바꾸지 않았다.
- source/JAR: source `3a2752e`, `source-after.json`의 424 files/match=true, `runner-provenance.json`과 `runner-provenance-after.json` 117개 동등 및 현재 파일 SHA-256 전부 일치를 QA가 직접 확인했다. source manifest SHA-256 `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`, immutable runtime JAR SHA-256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`.
- SQL 실측: 실제 HTTP200 list size1=5 SELECT, size100=5 SELECT, detail=3 SELECT. list 증분0이며 세 요청 모두 승인 bound 1..14 이내다. 하위 row/file fingerprint·이미지 bytes/삭제후 UPLOAD_003·canonical 집계·current actor·동시성·SPA stale gate가 모두 PASS다.
- 별도 제품 회귀: `build/m9-evidence/backend-final-2/summary.json` 109 suites/637 tests, failure/error/skip0, exit0; `frontend-final-result.json` 및 `frontend-final-test.log` 51 files/464 tests, 실패/skip0. 396 acceptance checks와 합산하지 않는다. `qa-helper-final.log`는 parser focused 13/13 PASS이며 `route-protocol-focused.log`는 body 격리·한번 fulfillment·A만 expiry/B 통과·실제 delivery·유한 cleanup·취소 failure/noUnhandled의 순수 검사 PASS다.
- helper closure: physical barrier 7 mappings, RR writer 2, no-op/audit 4, state policy 6, FE 25가 실제 named test/source와 연결됐다. 다섯 helper check의 missing/unresolved는 모두 빈 배열이다. `fixture.testArtifacts`에 backend19/frontend25 mappings 및 artifact hashes123개를 보존했다. FE uncovered6/state uncovered3은 아래 direct PASS로 닫혔다.
  - FE pending: `browser-admin-list-pending-sensitive`; 같은 Provider 전환: `browser-stale-list`, `browser-stale-detail`, `browser-stale-mutation`, `browser-account-switch-stale`; 복귀 probe 상태: `browser-admin-focus-suspended`, `browser-admin-visible-deleted`, `browser-admin-denied-reentry`.
  - FE empty/error/retry: `browser-list-empty-state`, `browser-list-transient-retry`, `browser-list-transient-retry-result`, `browser-detail-transient-retry`, `browser-detail-transient-retry-result`, `browser-mutation-error-retry`; 중복 제출: `browser-mutation-duplicate-submit`; URL/실측: `browser-admin-list-width`, `browser-admin-detail-width`, `browser-search-url-state`, `browser-search-paging-history`, `browser-screenshot-evidence`, `browser-admin-entry-menu`, `browser-admin-heading`, `browser-admin-role-label`.
  - 상태정책: `mutation-suspend-boundary`, `mutation-deleted-signin-and-writes`, `refresh-reactivated-existing-accepted`. required matrix도 `required-matrix-complete` PASS이며 미실행/실패 required ID가 없다.
- 화면 증거: run7 `admin-list-1440.png`/`admin-detail-1440.png`, 실제 visible 디자인 관리자/상세 폭1100/860, 목록·상세·retry·삭제카피·stale 자동 검사 PASS. 디자인 소스 선택과 크기 검사는 최종 시각 정합성 심사를 대신하지 않는다.
- 저자/판정 경계: QA harness 보완은 `/root/m9_qa_fix5`, 실제 runtime 실행은 root, 최종 whole-branch/시각 검토는 별도 `/root/m9_final_review`다. 현재는 **자동 인수 PASS / 독립 최종 verdict 및 PR/dev merge 대기**다. QA 매트릭스의 최신 상태를 실제 check ID로 연결했고 과거 append-only 실패 기록은 보존했다. 이번 변경은 QA 기록만이며 main/helper/product/runtime/Git은 변경하지 않았다.
- 기록 검증: QA 4개 문서 대상 `git diff --check` exit0, 최신 매트릭스71행의 실제 PASS ID115개 대조·미실행 상태 잔존 없음, WORKLOG/report append 동일성·117개 frozen provenance 재대조 PASS. 기존 report는 Git 제외 경로이므로 별도 append 내용/후행 공백도 검사했다. main/helper/product/runtime 재실행은 하지 않았다.

## 16. 2026-11-12 KST — final-review Minor M1 수정·독립 scoped 재검토 종료

- `final-review.md`는 Critical 0 / Important 0 / Minor 1로, `frontend/src/features/admin/adminApi.ts`의 keyword 내부 whitespace 축약을 Minor M1로 지적했다. `task-4-final-m1-brief.md`의 계약과 REQUIREMENTS §6.13-A·ADR-0008을 다시 대조했고, `5df2db0`에서 양끝 `trim`만 적용하고 내부 whitespace를 보존하는 수정과 회귀를 확인했다. URL wire 표현과 literal `+`·`%_\\` 의미 보존도 보고·재검토 원문에 연결된다.
- TDD/검증 근거는 `task-4-final-m1-report.md`와 `final-m1-rereview.md`에 있다: 수정 전 focused RED(1 failed/3 passed), 수정 후 focused 1 file/4 tests PASS, root fresh full FE 51 files/465 tests PASS, `npm run lint`·`npm run build` exit0. 기존 build chunk warning은 비차단 경고로 분리됐다.
- 최종 source manifest는 424 files / SHA-256 `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`이며 backend 109 suites/637 tests와 immutable JAR SHA-256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`는 변하지 않았다. run7의 source SHA `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`, FE464 및 396/0/0 acceptance는 M1 수정 전 역사 evidence다.
- 독립 `final-m1-rereview.md`는 Critical/Important/Minor 0, **CLEAN / READY TO MERGE**로 판정했다. 이 재검토는 제품 2개 파일·frontend 기록 diff와 focused 경로를 범위로 하며, 기존 run7 API/DB/Chrome evidence를 수정 후 재실행한 결과로 표현하지 않는다.
- 현재 QA 판정은 **M9 독립 QA PASS / READY TO MERGE**다. 미해결 gate는 root의 PR/dev 통합·merged tree/source identity 대조이며, 아직 PR/dev merge는 수행되지 않았다. 과거 실패·NOT RUN 기록과 run7 실제 396 evidence는 append-only로 보존한다.
