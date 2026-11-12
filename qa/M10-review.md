# M10 — MVP 전체 테스트·마감 QA 사전 검토

> **현재 상태: PREPARING / NOT RUN**
>
> 이 문서는 M10 실제 인수 전의 독립 QA 계획과 acceptance matrix다. M9는 `dev`에 통합된 COMPLETE 상태지만, M10 새 source/테스트/런타임/브라우저 인수는 이 기록 시점에 실행되지 않았다. 아래의 역사적 PASS는 M10 PASS로 합산하지 않는다.

## 1. 현재 판정과 범위

- `$brief` read-only 대조에서 현재 branch는 `feature/M10-closeout`이다. M9 PR #15의 실제 merge SHA는 `c0df2d33d0dce5ac71f1b358672606f717e739ae`이고, M10의 base/current `dev` reference는 `ce7d037`이다. 두 ref를 혼동하지 않으며, M9의 실제 merge·tree·runtime 정리는 `docs/worklog/M9-admin.md`, `.claude/team/qa/STATE.md`, `qa/M9-review.md`와 일치한다.
- M10의 목표는 PRD §11 테스트 전략과 §12 DoD를 M0~M9 통합 제품에 적용하는 **전체 회귀·마감 gate**다. 결함이 발견되면 해당 소유 역할이 TDD로 수정하고 QA가 독립 재검증한다. QA는 제품 코드나 타 역할 테스트를 직접 수정하지 않는다.
- 이 문서의 상태값은 `NOT RUN`을 유지한다. 구현자 자체 테스트, 과거 artifact 재독, source 정적 검색만으로 M10 PASS/완료를 주장하지 않는다.
- 이번 M10 범위에서 제외하는 항목은 배포·운영 프로비저닝, S3/LocalStack/U0 전환, 실제 AWS, 실사용 사용자 DB·기존 업로드 적용/보정, 운영 관리자 bootstrap, 모바일/반응형, 운영 fan-out·SLA·peak memory, DB 장애 주입이다. 제외 항목은 DoD를 몰래 통과시킨 것으로 취급하지 않고 잔여 위험으로 보고한다.

## 2. 정본과 독립성 기준

| 축 | 직접 대조한 정본·기준 |
|---|---|
| 공통 운영 | `.claude/CONSTITUTION.md`, `AGENTS.md`, `qa/AGENTS.md`, `.claude/team/qa/CLAUDE.md`, QA `STATE.md`·`WORKLOG.md` |
| 요구사항 | `docs/REQUIREMENTS.md` FR-AUTH/SETTINGS/BLOG/CAT/POST/UPLOAD/UNI/COM/LIKE/FEED/SEARCH/NOT/ADMIN, NFR-01~09(특히 NFR-04·05·07~09) |
| 실행 명세 | `docs/PRD.md` §4~§8, §9.5/§9.7/§9.8/§9.9/§9.10/§9.11/§9.12, §10 M0~M10, §11, §12 |
| 시각 정본 | `docs/design/AGENTS.md`, `docs/design/DESIGN-SYSTEM.md`, 각 `.dc.html`; 데스크톱 CSS viewport 1440px 기준 |
| 승인 계약 | ADR-0003(인증/세션), ADR-0004(오류·slug), ADR-0005(카테고리), ADR-0006(피드·검색), ADR-0007(알림), ADR-0008(관리자)와 각 M3~M9 회의록 |
| 위험·역사 | `docs/governance/RISK-REGISTER.md`, `docs/worklog/M0-scaffold.md`~`M9-admin.md`, `qa/M2-review.md`~`qa/M9-review.md` |

적용 규칙은 정본의 우선순위와 동일하다. 사용자 결정/PRD §9, 디자인 정본, REQUIREMENTS, PRD 본문, 승인 ADR 순으로 계약을 대조한다. 역할 STATE나 커밋 제목은 완료 증거가 아니며, 실제 파일·XML/JSON 원문·HTTP/DB/Chrome 출력과 대조한다.

## 3. 역사적 증거의 provenance와 재사용 경계

M0~M9 산출물은 이미 각 마일스톤의 독립 gate를 닫은 역사 기록이지만, M10은 현재 통합 tree에 대한 새 마감 gate다. 과거 숫자를 단순 합산하지 않고, 아래처럼 **재사용 후보**와 **M10 재실행 필수**를 분리한다.

| 마일스톤 | 보존된 역사 증거 | M10에서의 취급 |
|---|---|---|
| M0 | `docs/worklog/M0-scaffold.md`의 BE 36/FE 152, build·lint·1440px 기록 | 공통 기반의 참고 후보. 현재 통합 tree에서 전체 BE/FE gate 재실행 필수 |
| M1 | `docs/worklog/M1-auth.md`의 BE 138/FE 186, refresh/session 회귀 | 인증 계약의 역사 근거. 현재 JWT/쿠키/API client와 전체 회귀 재실행 필수 |
| M2 | `qa/M2-review.md`, `docs/worklog/M2-settings.md`의 BE 348/FE 250, OpenAPI·1440px | source/JAR identity가 동일할 때만 보조 evidence; M10 fresh build/Swagger 필요 |
| M3 | `qa/M3-review.md`의 BE 370/FE 273, migration/category HTTP·1440px | category는 fresh XML·DB contract와 통합 흐름으로 재확인 |
| M4 | `qa/M4-review.md` 로컬 M4 PASS와 `qa/M4-u0-review.md` U0 BLOCKED | 로컬 파일 저장 범위만 재사용 후보. S3/U0 BLOCKED를 제품 PASS로 바꾸지 않음 |
| M5 | `qa/M5-review.md` 최종 BE 457/FE 338, 1440px 관계·hero | Universe 신청→수락→UNIVERSE 조회를 M10 E2E로 다시 실행 |
| M6 | `qa/M6-review.md` BE 77 suites/490, FE 34/369, 52 checks | 댓글·좋아요 및 M8 알림 연결을 현재 통합 runtime에서 다시 확인 |
| M7 | `qa/M7-review.md` BE 91/538, FE 41/405, 259 checks, source/JAR hash | feed/search read-side-effect·migration·URL/stale를 fresh provenance와 연결 |
| M8 | `qa/M8-review.md` BE 102/599, FE 47/439, 323 checks | 알림 생성·읽음·masking·동시성 및 세 UI를 현재 통합 runtime에서 다시 확인 |
| M9 | `qa/M9-review.md` BE 109/637, FE 51/465, run7 396/0/0 | run7은 M1 수정 전 FE464/source hash의 역사 evidence다. 현재 FE465와 합산하지 않고 M10 acceptance를 재실행 |

M10 결과는 다음 provenance 묶음 없이는 PASS가 될 수 없다.

1. 테스트한 source manifest의 파일별 SHA-256과 aggregate, 테스트 시작·종료 시점, 현재 tree/commit 정보.
2. `bootJar` 또는 직접 기동한 runtime JAR의 SHA-256과 source/JAR 연결, `/v3/api-docs`가 같은 JAR에서 나온 사실.
3. backend JUnit XML 원문 집계와 frontend test/lint/build 원문, acceptance JSON/log/screenshot의 artifact hash.
4. 격리 MySQL schema/fixture run ID, JVM `UTC` 실행과 DB 연결, 실제 HTTP status/body/header/SQL bound, Chrome CSS viewport.
5. 합성 계정 ID만 기록하고 credential/password/access·refresh token·cookie 원문은 stdout/evidence에 기록하지 않는 증거.

source manifest와 JAR가 서로 다르거나, 결과 JSON이 덮어써졌거나, HTTP가 실제 200/오류 body를 전달하지 못하거나, 필수 check가 `skip`/`pending`/`NOT RUN`이면 해당 gate는 FAIL 또는 NOT RUN으로 fail-closed 처리한다. 과거 성공 artifact의 hash만 현재 결과로 재라벨링하지 않는다.

## 4. M10 전체 acceptance matrix 초안

상태는 사전 점검 시점의 상태이며 모두 `NOT RUN`이다. `과거 참고`는 이전 마일스톤 evidence의 위치일 뿐 현재 M10 PASS를 의미하지 않는다.

| ID | 요구사항·검증 축 | M10 필수 증거·기대 결과 | 상태 |
|---|---|---|---|
| M10-BASE-01 | source provenance | 현재 통합 source manifest·파일 hash·aggregate·tree/commit을 before/after 비교; 사용자 변경·QA harness 변경을 제품 source로 혼입하지 않음 | NOT RUN |
| M10-BASE-02 | BE 전체 회귀 | 현재 source의 `test`/XML을 직접 집계; failure/error/skipped 모두 0, exit 0, `bootJar` 성공 | NOT RUN |
| M10-BASE-03 | FE 전체 회귀 | 현재 source의 test files/tests와 failure/pending를 직접 집계; test·lint·build exit 0 | NOT RUN |
| M10-BASE-04 | artifact identity | acceptance가 사용하는 JAR/source/FE build가 같은 manifest와 연결되고 전후 hash가 불변 | NOT RUN |
| M10-BASE-05 | skip/placeholder/stub | `@Disabled`, `skip`, `pending`, `xit`, false-pass guard, TODO/stub/placeholder UI·fixture·`|| 0` 대체를 required path에서 검색; 발견 시 통과 금지 | NOT RUN |
| M10-BASE-06 | secret/privacy | tracked source, logs, JSON, screenshots에서 비밀·token·password·cookie 원문과 과잉 개인정보가 0; synthetic-only guard | NOT RUN |
| M10-API-01 | 공통 응답 | 성공/400/401/403/404/409의 `success/data/error/timestamp`, error code·message·details가 계약과 일치 | NOT RUN |
| M10-API-02 | 페이징 | 모든 목록의 offset `page/size/totalElements/totalPages/hasNext/hasPrevious/items`와 size boundary·큰 offset을 확인; fake count 금지 | NOT RUN |
| M10-API-03 | OpenAPI | 같은 runtime `/v3/api-docs`와 Swagger에서 모든 operation·bearerAuth·request/response/error status/code/security를 실제 경로와 대조 | NOT RUN |
| M10-API-04 | cache/security header | 보호·민감 GET/성공·오류에 `Cache-Control: private, no-store`, invalid bearer를 guest로 강등하지 않음 | NOT RUN |
| M10-AUTH-01 | 가입→로그인→설정 | synthetic A가 register 후 기본 blog/DEFAULT를 얻고 signin, initial-setup 및 설정 완료 guard를 통과; duplicate/validation 경계 확인 | NOT RUN |
| M10-AUTH-02 | refresh rotation | access 만료→refresh rotation→새 access 성공, 이전 refresh 재사용 폐기; cookie HttpOnly/SameSite/Path/Max-Age 확인 | NOT RUN |
| M10-AUTH-03 | logout boundary | signout 후 refresh 재사용 실패, protected API 401, frontend apiClient 401 single-flight·최종 실패 redirect 확인 | NOT RUN |
| M10-AUTH-04 | 인증 오류 | missing/malformed/wrong type/bad signature/expired/deleted/suspended actor의 `AUTH_004`/`AUTH_002`/사용자 상태 code가 계약과 일치 | NOT RUN |
| M10-SETUP-01 | blog/category/post 기본 흐름 | setup 뒤 category 저장·post draft 작성·publish 전환·owner 재조회가 같은 blog/category에 연결; 응답 유실/부분 재시도는 canonical GET으로 복구 | NOT RUN |
| M10-ACL-01 | 글 ACL | guest/owner/UNIVERSE friend/non-friend/PRIVATE/draft/suspended/deleted/block 상태의 detail/list/previous-next/댓글·좋아요·이미지 권한을 교차 확인 | NOT RUN |
| M10-ACL-02 | 콘텐츠 보안 | HTML sanitizer·JSON/HTML 동등성·external image 제한·local image canonical URL·no-store·실제 5MiB/MIME 경계를 확인 | NOT RUN |
| M10-CAT-01 | category contract | DEFAULT/GENERAL/LOCKED, 깊이·subtree soft delete·post DEFAULT 이동·전체 sibling order·active unique·CAT_001~007, V1→V2 보존을 재실행 | NOT RUN |
| M10-UNI-01 | 관계 lifecycle | A→B request→accept/reject/cancel/delete/block/unblock, pair lock·양방향 reread·snapshot/requestId·UNI_004/005·SUSPENDED/deleted를 확인 | NOT RUN |
| M10-UNI-02 | Universe E2E | B의 UNIVERSE post가 수락 전 A에게 거부되고 수락 후 읽히며, 차단 후 접근·상호작용·관계 CTA가 거부되고 관계 제거 후 canonical 상태가 반영 | NOT RUN |
| M10-COM-01 | 댓글 | root/reply page·bounded batch·입력/HTML literal/삭제 placeholder·owner/ACL/block·stale response·동시 mutation을 확인 | NOT RUN |
| M10-LIKE-01 | 좋아요 | POST/DELETE idempotence·duplicate race·count/liked canonical·ACL 상실·view increment 없음·optimistic rollback을 확인 | NOT RUN |
| M10-NOT-01 | 알림 생성 | comment/reply/like/neighbor/post 정책의 수신자·중복·발행전이·차단·권한상실 masking·동일 transaction rollback을 확인 | NOT RUN |
| M10-NOT-02 | 알림 lifecycle | 전체 history offset·unread count·read/read-all/delete idempotence·SUSPENDED read-only·30일 cutoff 없음·no-store를 확인 | NOT RUN |
| M10-FE-01 | AuthContext/apiClient | login/logout/restore/refresh failure, concurrent 401 single-flight, token memory-only, 인증 전 guest API 선호출 없음 | NOT RUN |
| M10-FE-02 | route guards | guest/USER/ADMIN/SUSPENDED/deleted/setup incomplete의 route guard가 민감 화면·데이터를 pending 동안 노출하지 않고 canonical probe를 사용 | NOT RUN |
| M10-FE-03 | domain screens | setup/blog/post/write/edit/settings/universe/category/search/notification의 loading/empty/error/retry, URL/back/reload/account stale를 실제 API와 확인 | NOT RUN |
| M10-FE-04 | admin | current ADMIN probe, list/detail/search/page/actions/role/status/delete, self protection·승인 삭제 카피·lastAccess null·stale/retry/target 404를 확인 | NOT RUN |
| M10-FE-05 | FE API boundary | 모든 API client가 common envelope, Bearer/credentials, FormData content type, binary error, 401 retry 후 state cancellation을 준수 | NOT RUN |
| M10-FE-06 | PostEditor publish validation | publish일 때 빈 제목과 빈 text-or-image 본문을 client에서 차단하고, draft 저장에서는 빈 제목/본문을 허용하는 실제 editor 행동·payload 검증; 서버 validation과 구분 | NOT RUN |
| M10-FE-07 | Modal keyboard evidence | **Chrome hard:** runner의 `keyboard-modal-observation`이 synthetic friend detail의 실제 status trigger를 route-specific selector로 찾아 `Tab`/reverse focus, Enter open, Escape 관찰, 문서화된 `취소` close 및 visible dialog를 확인한다. `focusReturnedAfterClose`와 Escape가 닫았는지는 detail-only다. **기존 coverage:** `frontend/src/test/components.test.tsx`의 Modal aria/dialog·닫기 버튼, `frontend/src/test/adminPages.test.tsx`의 상태/삭제 확인 호출. **관찰/잔여:** focus-trap·initial-focus·restore와 실제 mutation confirm은 새 hard contract가 아니며 runtime admin evidence/API가 담당한다. | NOT RUN |
| M10-FE-08 | DraftPicker keyboard evidence | **Chrome hard:** runner는 실제 `/write` DraftPicker trigger를 열고 `Tab`/reverse focus, dialog·문서화된 닫기 버튼을 확인한다. **기존 coverage:** `frontend/src/features/post/PostEditor.test.tsx`의 “draft picker와 이미지 업로드…” 및 “20개를 넘는 초안으로 페이지를 넘기고 선택…” 테스트와 `frontend/src/pages/WritePage.tsx:83`의 `onDraftSelect → /edit/:id` 매핑이 항목 선택·paging·edit 이동을 대조한다. **관찰/잔여:** 실제 Chrome 항목 선택→`/edit/:id`은 이 prep runner의 hard gate로 강제하지 않고 fresh runtime에서 별도 재실행할 때만 기록한다. Escape/focus-trap 부재는 observation-only다. | NOT RUN |
| M10-FE-09 | TopBar keyboard evidence | **Chrome hard:** runner의 `keyboard-topbar-observation`은 실제 알림 link에서 `Tab`/reverse focus, Enter로 preview open, Escape close를 확인한다. **기존 coverage:** `frontend/src/test/notificationShellBehavior.test.tsx`의 TopBar preview open/read/canonical href와 `frontend/src/test/searchBehavior.test.tsx:153`의 TopBar submit URL, `frontend/src/components/layout/TopBar.tsx`의 profile menu route를 대조한다. **관찰/잔여:** profile/search 별도 Chrome mutation과 focus return은 이 runner의 hard gate가 아니며 fresh browser에서 관찰 시 detail로 남긴다. focus-trap/restore 부재는 FAIL이 아니다. | NOT RUN |
| M10-FE-10 | category keyboard evidence | **Chrome hard:** runner의 `keyboard-category-observation`은 실제 enabled GENERAL rename control에서 `Tab`/reverse focus, Enter inline edit, 문서화된 Escape cancel을 확인한다. **기존 coverage:** `frontend/src/test/categoryBehavior.test.tsx`의 “하위 카테고리를 생성하고 GENERAL 이름을 인라인 수정한다”, “GENERAL을 LOCKED로 바꿀 때 경고하고 DEFAULT·LOCKED 조작을 막는다”, “키보드와 native DnD…”가 create/delete/confirm/reorder와 disabled DEFAULT/LOCKED 경계를 대조한다. **관찰/잔여:** 실제 Chrome create→delete·Arrow reorder 영속 mutation은 fixture를 남기지 않기 위해 prep runner hard gate로 강제하지 않고 unit/integration 및 API/runtime evidence로 닫는다. focus-trap 부재는 observation-only다. | NOT RUN |
| M10-FE-11 | admin keyboard evidence | **Chrome hard:** runner의 `keyboard-modal-observation`은 실제 admin detail status trigger의 `Tab`/reverse focus·Enter open·Escape 관찰·취소 close를, `keyboard-admin-observation`은 label-linked search form Enter와 query/visible list를 확인한다. **기존 coverage:** `frontend/src/test/adminPages.test.tsx`의 “상세는 lastAccess 미기록을 표시하고 역할·상태·삭제 확인을 제공한다”, “상태 변경은 canonical 상세를 다시 읽고 삭제 확인은 승인 카피 뒤 delete를 호출한다”, transient pagination 보존; `frontend/src/test/Pagination.test.tsx`의 이전/다음/aria-current; `frontend/src/test/adminApi.test.ts`의 role/status/delete endpoint를 대조한다. **관찰/잔여:** 실제 Chrome select→confirm mutation과 다중 페이지 이동은 prep runner에서 durable fixture를 만들지 않으며 unit/API/runtime evidence로 확인한다. initial-focus/restore·focus-trap 부재는 새 hard blocker가 아니다. | NOT RUN |
| M10-REPO-01 | Repository/JPA | unique·auditing·soft-delete exclusion·연관 저장/조회·실제 V1~V6 migration/rollback/history를 synthetic isolated DB에서 확인 | NOT RUN |
| M10-DB-01 | audit/time | JVM non-UTC 기동 차단, UTC 신규 가입 instant/`joinedThisWeek`, `DATETIME(0)` truncate/future 제외를 확인; 역사 DATETIME 자동 보정은 수행하지 않음 | NOT RUN |
| M10-CONC-01 | 선형화 | category/universe/comment/like/notification/admin 경쟁과 rollback·deadlock retry·physical barrier를 확인; 순차 실행을 concurrent PASS로 대체하지 않음 | NOT RUN |
| M10-ADMIN-01 | 관리자 E2E | synthetic ADMIN이 list→detail→role/status→delete를 수행; DB current ACTIVE ADMIN/non-deleted guard, stale claim, sorted deleted lock, child/file 보존, query bound를 확인 | NOT RUN |
| M10-ADMIN-02 | 관리자 privacy | password/hash/token/body/birthDate 미노출, `lastAccessAt=null`, `private,no-store`, 자기 권한회수·정지·삭제 금지와 exact copy 확인 | NOT RUN |
| M10-DESIGN-01 | 1440px visual | visible design reference와 제품 screenshot을 직접 대조. `/`, auth, setup, blog, post, write/edit, settings 3종, search, notifications, admin/list·detail의 layout/copy/token/radius0/shadow를 확인 | NOT RUN |
| M10-DESIGN-02 | desktop boundary | CSS viewport `1440×1000` 또는 화면별 명시 viewport, `min-width:1440px`; hidden screen/switcher/placeholder screenshot을 PASS로 사용하지 않음 | NOT RUN |
| M10-OPS-01 | evidence/cleanup | runtime·isolated DB·synthetic uploads는 종료 후 보존/정리 경계를 기록하고 사용자 DB `13307`·기존 업로드·`.env.local` 미접근을 확인 | NOT RUN |
| M10-DOD-01 | 최종 DoD | 위 required 행에 FAIL/NOT RUN/skip가 없고, source/JAR/HTTP/DB/Chrome provenance와 독립 review가 모두 연결될 때만 M10 PASS | NOT RUN |

### QA harness

- `qa/m10-acceptance.mjs`는 source manifest/JAR/FE dist/Git commit·tree identity, generated OpenAPI, synthetic-only HTTP/MySQL/Chrome, 공통 envelope/private+no-store/page, refresh/logout, draft/publish matrix, GENERAL fixture, 실제 UNIVERSE pre-accept deny/post-accept allow, accepted-post comment/like/notification, 13 canonical screenshots와 keyboard observation을 하나의 unique evidence directory에 기록한다. admin stale/self/mutation/deleted/child-row, upload 5MiB/MIME, restart는 `M10_RUNTIME_EVIDENCE`의 현재 source/JAR/FE/harness hash와 명명된 fresh runtime 결과가 없으면 required `NOT RUN`이며 env 변수 존재만으로 통과하지 않는다. runtime evidence gate가 유효하지 않으면 request factory/API context 생성 자체를 중단한다.
- runtime bundle 입력은 `zeroverse.m10.runtime-evidence.v1` exact shape(`runtime.mysql=127.0.0.1:13320`, database `zeroverse_m10`, API `18090` UTC, FE `5180`, provenance의 Git/source/JAR/frontendDist/harness SHA-256, admin/upload/restart named IDs·`PASS`)만 인정한다. full run의 `M10_RUN_ID`와 bundle `runId`는 exact match여야 하며, bundle은 repository-local attestation 파일 path/SHA-256을 가리켜야 한다. attestation은 `zeroverse.m10.runtime-attestation.v1`, 동일 runId, 현재 bound `observedAt`, 8개 per-check `ok=true`/observedAt/artifactPaths 및 실제 raw artifact SHA-256을 검증한다. `M10_MYSQL_HOST`와 `M10_MYSQL_PORT`는 full run에 필수이고 loopback `127.0.0.1:13320`만 허용하며 사용자 DB `13307`과 다른 port는 거부한다. credentials/access·refresh token·cookie/password/raw email은 메모리에서만 사용하고 evidence/log에는 scrub된 오류·합성 ID·hash만 남긴다.
- refresh는 owner/friend shared jar가 아닌 격리 request context에서 cookie rotation·old-cookie replay·logout reuse를 확인하고, rotated access token으로 보호 `/auth/me`를 호출한다. `Set-Cookie`의 HttpOnly/SameSite=Strict/Path `/api/v1/auth`/positive Max-Age를 원문 없이 검증한다. notification은 request/comment/like 각각 actor/type/target ID를 식별한 뒤 해당 ID만 read/delete한다.
- generated OpenAPI는 61 operations, unique operationId, 정확히 분류된 public `security:[]` operations, bearerAuth/security, request/response schema, 대표 400/401/403/404/409 response schema를 대조한다. HTTP matrix는 실제 400/401/403/404/409와 canonical `VALIDATION_001`/`AUTH_004`/`POST_002`/`POST_001`/`CAT_005`를 exact status+`error.code`로 확인하고, 공통 실패 envelope(`success=false`, `data=null`, 구조화된 `error.code/message/details`, 유효한 `timestamp`)까지 동시에 요구한다. PageResponse 전체 필드와 size bound, PostEditor draft-empty/publish-empty/image-only도 확인한다.
- image-only publish는 실제 synthetic multipart `POST_IMAGE` upload의 canonical `/api/v1/uploads/{UUID}/content` URL을 contentJson과 images snapshot에 사용하고 thumbnail은 null로 둔다. upload metadata와 publish response의 target ID/body/image URL을 모두 대조한다.
- screenshots는 disable 경로를 허용하지 않으며 HTTP 2xx·expected pathname·route-specific visible selector 및 정확히 13개 고유 PNG의 nonzero bytes/SHA-256/`1440×1000` dimension을 모두 요구한다. keyboard 5행은 각 route-specific matched control에서 실제 `page.keyboard.press('Tab')`/reverse focus와 open/close·query outcome을 관찰하고 close 후 `document.activeElement`가 trigger로 돌아오는지 detail에 기록한다. Modal/DraftPicker Escape·focus return 부재는 detail-only이고 documented cancel/close·명시된 route behavior만 hard check다. control 미존재는 required `NOT RUN`; 정본에 없는 focus-trap/initial-focus/restore 부재는 새 hard blocker로 만들지 않는다. 선택·category mutation·admin select/confirm/paging은 기존 frontend unit/integration/API coverage와 fresh runtime evidence로 정확히 매핑하며 prep runner가 durable fixture를 만들지 않는다.
- `node qa/m10-acceptance.mjs --self-check`는 runtime을 호출하지 않고 exactly 13 route, known stale false-pass/disabled screenshot/runtime-evidence guards, negative fixture validation, 그리고 `src/main`·`src/test`·`frontend/src` product static scan을 확인한다. 현재 self-check는 PASS이며 실제 runtime/API/DB/Chrome acceptance는 **NOT RUN**이다.
- full runner는 final source/JAR/FE manifest·runtime bundle이 없거나 불일치하면 fail-closed `NOT RUN`으로 끝난다. 이전 M7~M9 evidence를 재라벨링하거나 hidden/mock/switcher screenshot을 PASS로 세지 않으며, full run의 `M10_SCREENSHOTS=0`도 거부한다.

## 5. 필수 end-to-end 시나리오

아래 흐름은 단위·마일스톤별 과거 PASS를 대체하지 않으며, M10 격리 runtime에서 실제 HTTP와 필요한 Chrome 증거로 다시 닫아야 한다.

### E2E-1 가입 → 설정 → 글 → 공개 조회

1. synthetic A가 register한다. 기본 blog와 `DEFAULT` category가 자동 생성되고 secret/credential은 메모리에만 둔다.
2. signin 후 initial setup으로 title/slug/bio와 GENERAL 시작 category를 저장한다. 중복/응답 유실/부분 실패에서 GET 재조회로 canonical 상태를 확인한다.
3. A가 category를 선택해 draft post를 작성하고, content JSON/HTML·tag·image·visibility를 저장한다. PRIVATE/draft에서 owner만 접근하는지 확인한 뒤 PUBLIC으로 전환한다.
4. guest와 authenticated reader가 public blog/post를 읽고 envelope/PageResponse/조회수 dedup·no-store·canonical image bytes를 확인한다. 실제 파일 선택 자동화는 제외하지만 API multipart, 파일 저장, 표시, 5MiB/MIME는 유지한다.

### E2E-2 refresh → logout → refresh 실패

1. access 만료 또는 합성 expired access로 refresh rotation을 실행해 새 access와 이전 refresh 폐기를 확인한다.
2. signout 후 refresh endpoint가 실패하고 보호 API가 401이 되는지 확인한다. 브라우저에서는 apiClient의 동시 401 한 번 refresh, refresh 실패 후 민감 화면 clear/redirect를 확인한다.

### E2E-3 관계 → UNIVERSE 글 → 상호작용

1. A가 B에게 Universe 신청을 보내고, 수락 전 B의 UNIVERSE 글·snapshot·CTA를 확인한다.
2. B가 수락한 뒤 A가 글을 읽고, 댓글/root·reply·좋아요를 실행한다. 차단 또는 관계 제거 후 글·댓글·좋아요·이미지 접근이 즉시 canonical ACL로 닫히는지 확인한다.
3. 신청/수락/차단 등 M5 mutation 자체는 알림을 만들지 않는 경계를, M8 연결 후에는 comment/reply/like/neighbor/post 알림이 승인된 recipient에게 정확히 생성되는 경계를 각각 확인한다.

### E2E-4 댓글/좋아요 → 알림 읽음

1. A가 B의 공개 글에 comment/reply/like를 생성하고 B 알림 목록에서 같은 사건의 type/actor/target/message/unread를 확인한다. self/duplicate/block/권한 상실은 제외된다.
2. 알림 센터·TopBar·Main 최근 3건이 같은 unread count를 사용하고, 행 클릭 read·read-all·delete가 서버 재조회와 일치하는지 확인한다. unavailable 행은 고정 masking 문구와 이동 불가만 허용한다.

### E2E-5 관리자 목록 → 상세 → 변경 → soft delete

1. 격리 DB의 synthetic ADMIN이 current ACTIVE 상태로 list/detail을 조회한다. summary는 page/filter와 무관한 생존 사용자 기준이며 100자·literal `%_\\`·동일 createdAt·large offset을 확인한다.
2. 다른 synthetic 사용자의 role/status를 변경하고 성공 응답이 canonical detail인지, same-value audit가 변하지 않는지, stale claim이 current DB guard를 우회하지 않는지 확인한다.
3. self ADMIN 회수/정지/삭제는 승인된 `ADMIN_002/400`과 exact copy로 거부한다. 다른 사용자를 삭제하면 `users.deleted_at`만 바뀌고 하위 row/audit/file은 보존되며, 삭제 사용자 blog/post/image/댓글 placeholder/알림 masking/refresh 경계가 적용된다.
4. 관리자 화면은 1100px 목록·860px 상세·검색/paging/retry/계정전환/stale 응답/lastAccess 미기록을 실제 visible DOM과 screenshot으로 확인한다.

## 6. fail-closed 판정 규칙

- 필수 test가 skip/pending/disabled, 실제 실행되지 않은 check, 빈 fixture로 얻은 200, 고정 문자열만 확인하는 fake assertion, `NOT RUN`을 성공으로 세는 집계가 하나라도 있으면 해당 gate는 FAIL/NOT RUN이다.
- 테스트 수는 backend XML suite/testcase, frontend test reporter, acceptance check를 서로 합치지 않는다. 예를 들어 M9의 FE464/run7 396과 현재 FE465를 더해 “829 tests”라고 보고하지 않는다.
- source/JAR/FE bundle/acceptance manifest가 불일치하거나 실행 후 source가 변경되면 전체 acceptance는 NOT RUN으로 되돌린다. runtime JAR가 오래된 PID에 연결된 경우 `/v3/api-docs`·HTTP 결과를 현재 source evidence로 인정하지 않는다.
- OpenAPI annotation만 읽고 실제 generated spec을 통과시킬 수 없다. `/v3/api-docs`의 method/security/response/error와 실제 HTTP status/header/body를 같은 runtime에서 맞춘다.
- screenshot은 실제 visible DOM만 대상으로 하며 숨겨진 디자인 보드, switcher, mock-only, 빈 placeholder, 확대/축소로 1440 CSS px를 가장한 결과를 PASS로 삼지 않는다. `border-radius:0`, cream/paper/ink/accent, 3px/2px border, hard shadow, Press Start 2P/IBM Plex Sans KR, 화면별 폭·카피를 정본과 비교한다.
- 인증서·비밀번호·access/refresh token·cookie·실사용 사용자 row가 로그/JSON/스크린샷에 나타나면 증거를 폐기하고 새 synthetic run을 수행한다.
- 실제 runtime/DB/Chrome가 기동할 수 없거나 사용자 DB 접근 가능성이 있으면 억지 대체·기준 완화를 하지 않고 `NOT RUN`으로 남긴다.

## 7. M10에서 숨기지 않을 기존 위험

| 위험 | 현재 상태 | M10 처리·판정 |
|---|---|---|
| RISK-0001 | OPEN: V1과 후속 entity nullability/enum/관계 불일치 가능 | fresh Flyway/JPA/MySQL 회귀로 확인; 실사용 DB 적용은 별도 |
| RISK-0003 | OPEN: 공유 환경 Flyway checksum/schema 분기 | V1 불변·forward migration/isolated upgrade만 확인; 운영 복구는 제외 |
| RISK-0004 | OPEN: 공용 UI API 조기 고정 재작업 | 전체 FE 소비처·실제 화면 대조에서 placeholder 여부 확인 |
| RISK-0005 | OPEN: 운영 scheme/host에서 SameSite=Strict cookie 위험 | local/synthetic browser만으로 닫지 않으며 배포 전 OPEN 유지 |
| RISK-0006 | OPEN: 폐기 refresh token 재사용 시 전역 세션 폐기 미지원 | rotation/reuse reject만 확인; 전역 revoke는 범위 밖 |
| RISK-0007 | OPEN: slug 변경 후 기존 URL 단절 | 새 slug 동작만 확인; redirect/alias는 운영 전 별도 결정 |
| RISK-0008 | OPEN: V2 active unique 후 downgrade/복구 위험 | migration history/재사용만 확인; 운영 downgrade 금지 |
| RISK-0009 | OPEN: Post category write와 M3 blog lock 결합 위험 | 현재 category/post 동시성·ACL을 fresh DB에서 확인 |
| RISK-0010 | OPEN: S3 공개 URL/PRIVATE·UNIVERSE 기밀성 | 로컬 canonical file ACL만 확인; S3/AWS는 M10 PASS 대상 아님 |
| RISK-0011 | OPEN: 24h view ledger 수명/HMAC rotation/cleanup | local bounded cleanup·동시성만 재확인; 운영 보존정책 미확정 |
| RISK-0012 | OPEN/BLOCKED: LocalStack IAM·SeaweedFS PAB/browser status 공백 | U0 BLOCKED를 유지하고 제품 local M4 PASS와 분리 |
| RISK-0013 | OPEN: DB와 local file 별도 보관/백업 불일치 | synthetic restart/bytes 보존만 확인; 운영 backup 보증 금지 |
| RISK-0017 | MITIGATED: notification fan-out 경쟁 | 승인된 local 경쟁/1001 범위는 재확인하되 운영 SLA/peak는 미측정 |
| RISK-0019 | CLOSED for M9: stale admin claim/FE state | M10 regression으로 current guard를 확인; 운영 침투·다중 인스턴스는 제외 |
| RISK-0020 | MITIGATED: user soft delete/cascade/ACL | synthetic fingerprint만 재확인; 실사용 DB 적용은 제외 |
| RISK-0021 | OPEN: 역사 DATETIME provenance·기존 DB 시간대/ADMIN bootstrap | UTC 신규행·비UTC fail-fast만 확인; 자동 ±9h 보정·실사용 적용 금지 |

이 위험들은 M10 local MVP 마감의 명시된 범위와 별개로 남는다. 특히 RISK-0010/0012/0013/0021을 닫지 않고 “배포 준비 완료” 또는 “실사용 DB 안전”이라고 보고하지 않는다.

## 8. 실행 순서와 다음 gate

1. root가 현재 source/JAR/tree/isolated runtime의 provenance를 고정하고 사용자 DB·기존 업로드 접근이 없는지 확인한다.
2. backend 역할이 UTC 고정으로 full `test`/XML 집계와 `bootJar`를 실행하고, frontend 역할이 full test/lint/build를 실행한다. QA는 원문 출력과 현재 source identity를 독립 대조한다.
3. QA acceptance runner가 synthetic-only MySQL/JAR/API/Chrome 1440px에서 §5 E2E와 §4 matrix를 실행한다. 실패 run은 삭제하지 않고, 미도달 필수 행은 top-level `NOT RUN`으로 남긴다.
4. backend/frontend의 수정이 발생하면 source/JAR manifest를 새로 만들고 영향을 받은 unit/controller/repository/integration/FE/HTTP/visual 행을 처음부터 재검증한다.
5. 마지막으로 QA가 정본·diff·test XML·OpenAPI·HTTP/DB·screenshots·risk 경계를 직접 대조한 뒤에만 M10 verdict를 갱신한다. 모든 필수 gate가 닫히기 전 `PASS`, `COMPLETE`, `release-ready`를 쓰지 않는다.

## 9. 현재 사전 판정

**M10 QA: PREPARING / NOT RUN.**

현재 확보된 것은 정본·역사 evidence·재실행 matrix뿐이다. 남은 필수 증거는 현재 M10 source의 BE/FE fresh 회귀, generated OpenAPI, synthetic HTTP/MySQL E2E, 알림·관리자·refresh/logout 연결, source/JAR provenance, 실제 visible 1440px screenshots, skip/placeholder/secret scan이다. 이 항목들이 도착하기 전에는 M10 완료나 제품 결함 부재를 주장하지 않는다.
