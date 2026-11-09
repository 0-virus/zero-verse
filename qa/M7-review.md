# M7 — 피드·검색 독립 QA 최종 검토

> **현재 상태: A-v2 APPROVED / FINAL QA PASS / DEV INTEGRATED**
>
> 최종 판정은 제품 구현자와 분리된 QA 컨텍스트에서, source 동결본·immutable JAR·격리 MySQL·실제 HTTP·system Chrome 1440px 결과를 대조해 내렸다. M7은 독립 QA PASS 후 PR #13으로 `dev`에 통합됐다.

## 1. 검토 경계와 정본

직접 대조한 정본과 산출물은 다음과 같다.

- `docs/REQUIREMENTS.md` — FR-FEED-01, FR-FEED-02, FR-SEARCH-01, NFR-04/09 및 §6.10-A
- `docs/PRD.md` — §5.9/§5.10, §7 MainPage/SearchPage, §9.10, §10~§12
- `docs/design/DESIGN-SYSTEM.md` §8.1/§8.8 및 `docs/design/ZeroVerse Main Feed v2.dc.html`, `docs/design/ZeroVerse Pages.dc.html`
- `docs/governance/meetings/M7-20261109-feed-search.md` §6 A-v2 (사용자 전체 승인 및 이후 실행 재개 기록 포함)
- `docs/worklog/M7-feed-search.md` Task 1~5 및 최신 구현·검증 기록
- 최종 제품 source commit `0b3008a`, acceptance/test 기록을 포함한 tested head `2b984b6`, QA 소유 `qa/m7-acceptance.mjs`

회의록의 최초 `실행 보류` 표현은 당시 승인 체크포인트 이력이다. 이후 사용자의 `승인된 작업 시작해.` 지시와 회의록/PRD의 재개 기록을 현재 계약·실행 기준으로 삼았다.

## 2. 최종 실측 증거

| 게이트 | 독립 대조 결과 | 증거 |
|---|---|---|
| Backend 전체 | **PASS** — 91 XML suites, 538 tests, failures 0, errors 0, skipped 0; `BUILD SUCCESSFUL` | `build/m7-evidence/backend-final-1/summary.json`, `backend-final-1/test-results/` |
| Backend M7 핵심 | **PASS** — Feed/Discovery query-bound, Search query/snapshot, M7 HTTP/ACL/snapshot coverage, text extractor/cache, migration/maintenance XML 모두 0/0/0 | 위 XML의 `DiscoveryQueryBoundMySqlTest`, `FeedQueryMySqlTest`, `M7DiscoveryHttpContractMySqlTest`, `M7DiscoverySnapshotCoverageMySqlTest`, `SearchQueryMySqlTest`, `SearchSnapshotMySqlTest`, `PostTextExtractorTest`, `PostTextCacheTest`, `PostSearchTextMigrationMySqlTest`, `PostSearchTextMaintenanceMySqlTest` |
| Migration/복구 | **PASS** — V4/V5 DDL·history, 201행 keyset(100+100+1), 후반 invalid 전체 rollback, V3 복원 후 재이관, stale verify/rebuild·updated_at/audit 보존, malformed 원문 비노출 | `PostSearchTextMigrationMySqlTest.xml` 5 tests, `PostSearchTextMaintenanceMySqlTest.xml` 4 tests, `migration-snapshot-fix2/summary.json` 11 tests |
| Frontend 전체 | **PASS** — 41 files, 405 tests; lint exit 0; build exit 0 | `build/m7-evidence/frontend-final-tests.log`, `frontend-final-lint.log`, `frontend-final-build.log` |
| 실제 HTTP/DB/Chrome | **PASS** — 259 checks PASS, 0 FAIL, 0 NOT_RUN, `failures=[]`; 8개 합성 계정/관계와 합성 DB만 사용 | `build/m7-acceptance-20261109-232636/evidence.json`, `build/m7-evidence/acceptance-4.log` |
| source/runtime 고정 | **PASS** — manifest 354 files를 각 SHA-256과 path\\0hash\\n aggregate로 독립 재계산; source aggregate `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c` 일치; immutable JAR `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672` 일치 | `build/m7-evidence/tested-version.json`, `build/m7-api-data/runtime.jar`, `source-runtime-sha` check |

### 2.1 최종 acceptance fixture 경계

run4는 owner/actor/friend/one-way/unrelated/blocked/suspended/deleted 8개 합성 계정과 합성 blog/post/relation만 사용했다. actor→owner, actor↔friend, actor→one-way/suspended/deleted, block으로 제거된 actor→blocked 관계가 준비됐고, owner PUBLIC/UNIVERSE/PRIVATE/draft, rich text·inline mark·entity·wildcard·21+ paging post가 생성됐다. credentials/token은 memory-only이며 evidence에는 합성 ID만 남았다. 사용자 DB(기존 M5 DB 포함)는 접근하지 않았다.

## 3. FR/NFR 인수 매트릭스

| 요구사항/축 | 최종 검증 내용 | 판정 |
|---|---|---|
| FR-FEED-01 유니버스 피드 | `all/friends/following`의 방향·ACCEPTED 관계 predicate, PUBLIC/UNIVERSE ACL, PRIVATE/draft·삭제 제외, SUSPENDED author 기존 공개글 포함, deleted author 제외, 차단 후 universe 제외, 21+ page/disjoint, invalid relation/size `VALIDATION_001`을 HTTP/격리 MySQL로 확인 | **PASS** |
| FR-FEED-02 공개 피드 | guest 및 authenticated public feed, blocked가 PUBLIC 읽기를 막지 않음, SUSPENDED author 공개글 포함, deleted author 제외, owner PUBLIC/UNIVERSE/PRIVATE/draft 경계, PageResponse/envelope/no-store를 확인 | **PASS** |
| FR-SEARCH-01 요청/ACL | keyword 정규화·blank/missing validation, `ALL|POST|BLOG|USER|TAG`, owner PRIVATE/draft, non-owner 보호글 제외, deleted parent 제외, SUSPENDED public 포함, malformed/expired/deleted auth의 exact error와 no-store를 확인 | **PASS** |
| 검색 distinct/facet/count | multi-field 중복을 post ID로 distinct, canonical `matchedTypes` 순서, snapshot counts, multi-tag 중복 방지, wildcard `%`/`_` literal, 160 code-point snippet, 21+ paging을 HTTP·MySQL로 확인 | **PASS** |
| 본문 cache/migration | canonical JSON text node 경계, inline mark 연결, block/hardBreak 공백, image/link/mark/node 이름 제외, 실제 `&`·emoji 보존, empty/image-only·malformed/unknown 처리, V4/V5 rollback/restore/rerun/offline verify-rebuild와 old-writer stale 경계를 backend XML로 확인 | **PASS** |
| NFR-04 공통 응답/보안 | OpenAPI path, PageResponse/ApiResponse, exact status/error code, `Cache-Control: private, no-store`, optional-auth의 invalid/expired/deleted/suspended 정책, guest universe `AUTH_004`를 실제 HTTP로 확인 | **PASS** |
| NFR-09 자동검증 | backend 91/538 및 frontend 41/405에서 failure/error/skip 0; placeholder/skip 없는 최종 XML/FE 결과를 확인 | **PASS** |
| side effect/snapshot | feed/search 전후 viewCount와 synthetic notification COUNT/fingerprint 불변, repeated read ID set 양방향 diff 0, relation/tag/interaction RR snapshot 보존을 확인 | **PASS** |

## 4. FE·브라우저 인수 매트릭스

run4는 실제 API에 연결된 system Chrome에서 CSS viewport 1440px로 실행했다. mock-only 렌더나 빈 switcher를 근거로 삼지 않았다.

| 화면/행동 | 증거 | 판정 |
|---|---|---|
| MainPage shell | 1440px viewport, hero `유니버스 새 소식`, 전체/친구/내가 발견한 3탭, actor에게 owner PRIVATE/draft 카드 비노출 | **PASS** |
| 관계 URL/API | 친구→`relation=friends`, 내가 발견한→`relation=following`; right rail counts는 기존 세 목록 endpoint totals와 대조 | **PASS** |
| Search route/state | TopBar → `/search`, post-centric 설명, 5 facet 탭, keyword/type/page URL, refresh 및 input 복원 | **PASS** |
| 실제 검색 결과 | API response의 실제 첫 item ID/title/author/blogSlug로 canonical href를 계산해 `article > h2`와 anchor를 대조; 고정 fixture 정렬·heading 문자열 검사는 사용하지 않음 | **PASS** |
| owner draft link | `searchBehavior.test.tsx`의 실제 PostCard 행동 테스트와 `SearchPage.tsx` source가 owner `publishedAt=null` 결과를 `/edit/:id`로 연결하고, run4 HTTP가 owner draft를 허용함을 확인 | **PASS — FE/HTTP/source** |
| facet/pagination | POST/BLOG/USER/TAG/ALL 실제 클릭, URL/type/response snapshot count 일치, 21+ page 0/1 disjoint 및 browser back 복원 | **PASS** |
| empty/error/retry | unique keyword empty에서 API total 0·result card 없음, 일회성 HTTP 500에서 retryable error, 다시 시도 후 실제 API 첫 item/card 복귀 | **PASS** |
| account/stale/guest | owner PRIVATE 노출 후 actor 전환 시 total/card clear, actor ACL 결과 복귀, 늦은 owner 응답이 actor 결과를 오염하지 않음, guest 보호 탭 login prompt 및 universe API 미호출 | **PASS** |
| 디자인 원본 | 원본 Main Feed/Pages Search를 실제 browser로 render하고, 제품 4개 캡처를 직접 열어 1440px 폭·cream/ink/purple 토큰·hero·tabs·single-column cards·badge/meta/title/snippet·border/shadow·pagination을 비교 | **PASS (M7 범위)** |

### 4.1 시각 범위 분리

- `design-main-feed.png` (1440×1445), `design-search.png` (1440×1007), `main-actor.png` (1440×4114), `search-actor.png` (1440×3755)는 `build/m7-acceptance-20261109-232636/`에 보존됐다.
- Pages 원본의 상단 `SCREENS` 디자인 보드 chrome은 제품 shell의 요구사항이 아니므로 불일치로 판정하지 않았다. 제품 Search의 5탭, 단일 컬럼, 카드 및 결과 count는 원본 본문과 대조해 PASS했다.
- 제품의 hero 하단 pixel staircase는 M4/M5 기존 baseline으로 이번 M7 diff가 아니다. M7 시각 gate에서 결함으로 승격하지 않았으며, M7 승인 기능인 Main 검색 입력은 원본 정적 샘플에 없더라도 의도된 추가 범위로 분리했다.
- A-v2 §6.6의 owner draft `/edit/:id`는 FE 405 자동검증(`searchBehavior.test.tsx`), `SearchPage.tsx` source invariant, run4 owner HTTP ACL 및 harness의 canonical href 계산으로 확인했다. **run4 Chrome에서는 owner draft 카드를 직접 클릭·이동하는 별도 check를 실행하지 않았다.** 따라서 이 한 항목은 브라우저 직접 증거가 아닌 FE 행동 테스트+source+HTTP 증거로 판정했으며, browser-only 증거가 필요한 운영 gate라면 후속 캡처 대상이다.

## 5. Finding 및 수정 이력

### 5.1 최종 제품 finding

최종 source review, backend/FE 자동검증, run4 HTTP/DB/Chrome에서 **Critical/Important/Minor 제품 finding 없음**. M7 QA finding은 모두 CLOSED이며 제품 코드 수정 없이 QA harness 기대값/selector만 보정됐다.

### 5.2 run1~run3 보존 이력 (제품 결함 아님)

| 실행 | 관측 | 원인/조치 | 최종 상태 |
|---|---|---|---|
| run1 `230615` | oneWay initial setup에서 400 `BLOG_003` 후 중단 | QA fixture slug가 camel-case였음. slug lowercase 계약을 완화하지 않고 harness 생성식만 lowercase로 보정 | **CLOSED — QA-only** |
| run2 `231741` | HTTP/manifest 203 PASS 뒤 browser strict-mode 중단 | TopBar와 Main feed searchbox의 비정확 role selector 충돌. header scope + exact selector로 보정 | **CLOSED — QA-only** |
| run3 `232138` | 256 PASS, 3 FAIL | page0 최신 정렬인데 harness가 오래된 `owner.multi`를 기대. 실제 API `items[0]`의 ID/title/href를 렌더 card와 대조하도록 보정 | **CLOSED — QA-only** |
| run4 `232636` | 259 PASS, 0 FAIL, 0 NOT_RUN | 위 보정 후 새 합성 fixture로 전체 재실행 | **FINAL PASS** |

## 6. 증거·재현 경로

- 최종 JSON: `build/m7-acceptance-20261109-232636/evidence.json`
- 최종 log: `build/m7-evidence/acceptance-4.log`
- 원본/제품 캡처: `build/m7-acceptance-20261109-232636/*.png`
- backend XML/summary: `build/m7-evidence/backend-final-1/`
- frontend logs: `build/m7-evidence/frontend-final-{tests,lint,build}.log`
- version manifest/JAR: `build/m7-evidence/tested-version.json`, `build/m7-api-data/runtime.jar`

최종 QA harness는 `node --check qa/m7-acceptance.mjs`를 통과했고 run4 evidence에 NOT_RUN이 없다. credentials/token은 로그·artifact에 기록하지 않았다.

## 7. 잔여 gate와 경계

- **QA gate:** 기능/API/DB/자동검증/실제 브라우저/1440px 시각 인수는 PASS다. 단, owner draft `/edit/:id` 직접 Chrome 클릭은 별도 실행하지 않았고 FE 행동 테스트·source·HTTP로 대체 증명했다(브라우저-only 정책이면 후속 micro-check).
- **통합 gate:** **CLOSED** — PR #13이 `dev`에 merge됐다. merge SHA는 `08863207b8385177b619091410bbbd43e52a337c`, 통합 tree는 `dc68037477c6fed6b77d537235d79093803a983e`이며 tested head `2b984b6`의 tree와 일치한다. QA는 merge 후 제품 수정·재실행을 하지 않았다.
- **범위 제외:** M8 notification 생성, S3, 관리자, 기존 BlogPage 목록 count 확장은 M7 A-v2 범위가 아니다. M7 read side-effect가 없음을 확인했으며 M8 notification 기능 PASS를 주장하지 않는다.

## 8. 최종 판정

**M7 독립 QA: PASS. Merge-ready: Yes. Dev 통합: 완료.**

근거는 최종 source/runtime SHA 일치, backend 91 suites/538 tests 0/0/0, frontend 41 files/405 tests·lint/build exit 0, 실제 격리 HTTP/DB/Chrome run4 `259 PASS / 0 FAIL / 0 NOT_RUN`, 4개 원본·제품 screenshot 직접 대조 PASS다. owner draft 직접 Chrome click만 별도 실행하지 않았다는 증거 경계를 보존하며, 해당 route는 FE 행동 테스트·source·HTTP로 닫혔다. PR #13 merge 후 통합 tree가 tested head와 동일함을 확인했으며, QA는 추가 제품 수정이나 테스트 반복을 요구하지 않는다.

## 9. Dev 통합 closeout

- PR #13: <https://github.com/0-virus/zero-verse/pull/13>
- merge commit: `08863207b8385177b619091410bbbd43e52a337c`
- tested head: `2b984b6a2fc3aafd6d650c6ccb1cd8213a528545`
- 통합 tree: `dc68037477c6fed6b77d537235d79093803a983e` (tested head tree와 동일)
- 현재 브랜치: `dev`; M7 QA evidence·시각 판정·merge-ready 상태는 통합 후에도 유지된다.
