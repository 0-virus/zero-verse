# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M7 피드·검색은 A-v2 승인·구현·독립 최종 QA·dev 통합까지 완료**했다. `qa/M7-review.md`의 최종 판정은 **PASS / merge-ready: Yes / dev 통합 완료**다. 최종 source `0b3008a`, tested head `2b984b6`, backend 91 suites/538 tests·failures/errors/skips `0/0/0`, frontend 41 files/405 tests·lint/build exit 0, 실제 격리 HTTP/DB/Chrome run4 `259 PASS / 0 FAIL / 0 NOT_RUN`, 원본·제품 1440px screenshot 4개 직접 대조와 source/JAR SHA 독립 검증을 모두 확인했다. PR #13 merge SHA `08863207b8385177b619091410bbbd43e52a337c`, 통합 tree `dc68037477c6fed6b77d537235d79093803a983e`는 tested head tree와 일치한다.
- **M5 독립 최종 인수·dev 통합은 완료** 상태다. M5 finding BE-001/BE-002/QA-001/QA-002/DOC-001/V-001/V-002는 이전 최종 기록대로 CLOSED다.
- **M6 댓글·좋아요 최종 독립 QA는 PASS**다. `docs/governance/meetings/M6-20261109-comments-likes.md`의 보완 A-v2는 `APPROVED`·HIGH이며 `REQUIREMENTS` FR-LIKE-03/확정 세부 계약과 `PRD`에 동기화됐다.
- 최종 source 동결본을 backend/FE 독립 검토하고, backend 77 suites/490 tests, FE 34 files/369 tests·lint/build, 실제 HTTP/Chrome 1440px 52 checks, MySQL 경쟁/query-bound/no-notification 증거를 대조했다. 모든 필수 M6 gate가 CLOSED이며 PR #12가 `dev`에 통합됐다(merge `4399f73acf19d66d96fafd2c4784c6a03fcfa282`).

## 확인 이력 및 경계

- `docs/REQUIREMENTS.md` FR-COM-01~04/FR-LIKE-01~03, NFR-04/09, `docs/PRD.md` §5.7/5.8/§7.2/§9.2-P/§9.3-③/§9.9/§10~12, 디자인 원본, M6 worklog/회의록과 최종 source를 읽었다.
- V1 `comments`/`post_likes` 테이블·unique/index와 최종 backend `Comment`·`Like` repository/service/controller/OpenAPI, frontend feature/PostDetail 연결을 승인 A-v2와 대조했다. 초기 baseline의 “부분 구현·closure 대기” 표현은 과거 관측이며, 최종 backend/FE gate는 §최종 판정과 WORKLOG 최신 항목을 따른다.
- 승인된 A-v2와 정본의 M6 no-notification, FR-LIKE-03 interactions endpoint, 삭제 root GET/create, count 제외, DTO/길이/차단·unlike/lock 계약을 `M6-CONTRACT-01~06` 검증 주의점으로 정리했다.
- FE round1에서 FE-001~004를 닫았고, round2/round3에서 FE-005/006 및 WR-01/02/03까지 재검토해 모두 **CLOSED / Important 또는 Minor**로 갱신했다. root fresh FE는 `34 files / 369 tests`, lint/build PASS다.
- M6-BE-03 query-string 공개 matcher는 실제 immutable runtime HTTP로 `comments?page=0&size=20`, `parentId` reply page, guest interactions, malformed Bearer AUTH_004, private/no-store를 확인해 **CLOSED (실제 HTTP 경계)**로 갱신했다. `.param` 추정이 아닌 `build/m6-acceptance/evidence.json`의 실제 URL 호출 증거다.
- whole review의 `FE-005` root page 왕복 stale reply loading과 `FE-006` canonical GET 권한 거부 상태 잔류는 round2/round3 source·unit 재검토에서 **CLOSED / Important**로 갱신됐다. WR-01/02/03은 ADDRESSED이며 새 Critical/Important finding이 없다.
- `qa/m6-acceptance.mjs`의 읽기 권한 상실 browser 시나리오를 최종 실행했다. actor 화면을 열린 채 owner의 전체 `UpdatePostRequest`로 동일 합성 PUBLIC 글을 PRIVATE로 전환하고, stale like `LIKE_001`→canonical interactions `POST_002` 및 좋아요 count/댓글/action clear를 확인했다. 최종 evidence는 기존 46개에 6개를 더한 52/52 PASS다.
- M7 준비에서 `feature/M7-feed-search` HEAD `bc99cba`를 read-only 확인하고 A-v2 exact contract를 재대조했다. PRD `/universe/summary`는 기존 세 universe list total로 동기화 대상이며, runtime 미가동은 승인 blocker가 아니라 root의 실제 인수 전 실행 선행조건으로 구분했다. 이 항목은 run4에서 실제 relation counts와 canonical URL까지 검증해 종료했다.
- `qa/m7-acceptance.mjs`는 owner/actor/friend/one-way/unrelated/blocked/suspended/deleted 합성 계정과 PUBLIC/UNIVERSE/PRIVATE/draft·rich text·wildcard·21+ paging fixture를 준비하고, status-ready 파일이 합성 DB에서만 확인된 뒤 HTTP/1440px UI를 검증하도록 작성했다. credentials/token은 메모리 전용이며 seed/evidence에는 ID만 남긴다.
- M7 harness는 `/notifications` API를 side-effect 근거로 사용하지 않고 root `build/m7-runtime.py sql`로 합성 actor의 notifications COUNT/fingerprint를 전후 비교한다. `status-ready.json`은 `ready:true`와 4개 expected ID의 정확 일치가 필요하며, `build/m7-evidence/tested-version.json`의 source file hashes/aggregate와 runtime JAR SHA-256은 harness가 직접 다시 계산한다.
- M7 harness의 `read-snapshot-no-mutation`은 Set 직렬화가 아닌 정렬된 ID 배열과 양방향 diff를 비교하고, harness self-check로 missing/unexpected 양쪽 검출을 확인했다. malformed/expired/deleted/validation 오류 응답에도 `private, no-store`와 공통 envelope 검사를 적용했으며, run4 실제 API/DB에서 전체 side-effect와 snapshot gate를 PASS했다.
- (과거 run1 이력) owner/actor/friend fixture까지 통과했으나 `oneWay-initial-setup`에서 400 `BLOG_003`으로 중단됐다. API log의 실제 요청 slug가 `m7-oneWay-...` 대문자를 포함한 harness fixture 오류였고 제품 계약 완화 없이 slug 생성만 lowercase로 보정했다. run1은 최종 판정에 포함하지 않는 QA-only FAIL이다.
- (과거 harness 보정 이력) 독립 리뷰에서 발견한 M7 browser harness false-positive를 보정했다. Main Feed는 owner PUBLIC blog title이 정상 노출될 수 있으므로 PRIVATE/draft 카드의 `article > h2`만 검사하고, Search 결과·account switch·stale는 검색 input/heading이 아닌 실제 result card title을 검사한다. 검색 API response 완료를 기다리며 5 facet 탭의 URL/type/count, 21+ pagination/back, empty state와 일회성 HTTP 500→retry 상호작용을 추가했다. 이 보정은 run4에서 재실행·검증됐다.
- run2 직전 안정성 보강으로 Search `waitForSearchReady`가 로딩 indicator 제거를 조건 대기하고, 각 facet count가 API snapshot 값으로 실제 렌더될 때까지 `waitForFunction`으로 기다리도록 했다. 고정 sleep은 추가하지 않았다.
- (과거 run2 이력) 합성 fixture·HTTP/ACL/query/no-view/no-notification·status matrix와 source/JAR manifest 검증까지 통과했으나 TopBar/feed 두 searchbox의 harness strict selector로 browser 시작 직후 중단됐다. evidence는 203 PASS/0 FAIL/0 NOT RUN이지만 runner failure가 있어 당시 최종 PASS가 아니었다. TopBar는 `header` scope, SearchPage input은 `main` scope로 exact selector를 보정했고 후속 run4에서 전체 경로를 PASS했다.
- DOM 재확인 결과 SearchPage keyword 결과 상태에는 main 검색 input이 없고 TopBar만 `aria-label="검색"`을 유지하므로 `searchInputValue`도 `header` scope로 정정했다. `SearchPage`의 keyword 입력은 keyword 없는 상태에서만 `label="검색어"`다.
- (과거 run3 이력) HTTP/manifest 및 browser 상호작용 대부분을 실행했지만 `browser-search-result`, retry-success, account-switch-actor-result가 오래된 `owner.multi`를 기본 page0 카드로 기대해 FAIL했다. run3 screenshot의 실제 page0에는 최신 `owner.page[20]`가 있고 owner.multi는 page1이므로 제품 결함이 아닌 fixture expectation 오류로 분류했으며 세 기대값을 실제 API `items[0]` 기반으로 보정했다. run3는 보정 전 3 failures로 최종 PASS가 아니며 run4에서 해소됐다.
- run3 fixture 기대값은 더 강화해 실제 초기/재시도/계정전환 Search HTTP `response.data.items[0]`의 ID·title·publishedAt/author/blogSlug로 예상 canonical href를 계산하고 렌더된 `article > h2`와 anchor를 직접 대조하도록 수정했다. `owner.multi`/고정 page 정렬 가정은 제거했다. run3의 3 FAIL은 보정 전 QA-only 증거로 보존한다.
- run4 `build/m7-acceptance-20261109-232636/evidence.json`을 독립 집계해 **259 PASS / 0 FAIL / 0 NOT_RUN / failures=[]**를 확인했다. `statusFixtureReady=true`, 8개 합성 계정·관계, HTTP auth/ACL/facet/paging/no-store/no-view/no-notification, 1440px Chrome의 relation URL·Search 5탭·pagination/back·empty/retry·account switch/stale/guest no-call을 모두 PASS했다.
- run4의 `tested-version.json` manifest 354개 파일을 QA가 각 SHA-256과 codepoint path aggregate로 재계산해 source `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c` 일치를 확인했고, immutable JAR `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`도 직접 일치시켰다. 원본 Main Feed/Pages Search와 제품 4개 PNG를 직접 열어 M7 시각 범위를 PASS했다.
- A-v2 owner draft `/edit/:id`는 `searchBehavior.test.tsx` FE 행동 테스트, `SearchPage.tsx` source invariant, run4 owner draft HTTP ACL 및 harness canonical href 계산으로 확인했다. run4 Chrome에서 owner draft 카드를 직접 클릭하는 별도 check는 실행하지 않았으므로 browser-only 증거가 필요한 정책이면 후속 micro-check 대상이며, 현재 제품 finding으로 분류하지 않는다.

## 산출물

- `qa/M6-review.md`: 승인 상태 경계, source baseline, FR별 인수 매트릭스, 실제 HTTP/Chrome 1440px, MySQL 경쟁·query bound·rollback·account stale 시나리오.
- `qa/m6-visual-qa.mjs`: 승인 계약에 맞춘 API/Chrome 1440px PostDetail 검증 harness(최종 acceptance 증거와 함께 검토 완료).
- `qa/m6-acceptance.mjs`: random synthetic fixture 생성·seed와 실제 브라우저 mutation/access-loss acceptance harness(최종 실행 52/52 PASS). credentials/token은 메모리 전용이다.
- `build/m6-acceptance/evidence.json`: 최종 immutable runtime 결과 `52 PASS / 0 FAIL`, 1440px screenshot 3개 및 synthetic fixture ID만 기록(credential/token 없음).
- `qa/M7-review.md`: M7 A-v2 정본 대조, FR-FEED-01/02·FR-SEARCH-01 인수 매트릭스, run1~3 QA-only 실패 이력, 최종 run4 증거와 **PASS / merge-ready / DEV INTEGRATED** 판정.
- `qa/m7-acceptance.mjs`: M7 승인 후 QA-only synthetic fixture·HTTP/API/OpenAPI·ACL/facet/text-cache/no-side-effect·Playwright 1440px Main/Search/account-stale harness. 최종 run4 evidence는 259/259 PASS이며 credentials/token은 남기지 않았다.

## 다음 작업

1. M7 PR #13의 `dev` 통합·tree 동일성을 확인했으며 QA closeout을 유지한다.
2. 다음 마일스톤은 M8 승인·계획 절차에 따라 별도 착수한다. M6 QA 및 dev 통합 closeout은 완료 상태이며, M8 notification 생성/BlogPage count 확장은 M7 범위가 아니었다.

## 차단 요인

- M7 QA blocker는 없다. source/test closure, 합성 status-ready, exact expired token, API/FE runtime, source/runtime SHA, 실제 HTTP/DB/Chrome 및 시각 gate를 모두 run4와 최종 XML에서 확인했고, PR #13 `dev` 통합과 tree 동일성도 확인했다.
- M7의 hero 하단 pixel staircase는 M4/M5 기존 baseline으로 분류했고, Main 검색 입력은 승인된 M7 추가 범위로 분리했다. 이는 최종 M7 finding이 아니다.
- M6 QA blocker는 없다. backend/FE 최종 source·자동검증·MySQL 경쟁/query-bound/no-notification/ACL·HTTP/Chrome evidence가 모두 closure됐다.
- M6 승인 계약상 notification row는 생성하지 않고 FR-NOT-05는 M8 범위다. 최종 backend 증거에서 M6 mutation 전후 notification 불변을 확인했으며, M8 생성 자체는 이번 판정 범위가 아니다.

## 갱신 시각

- 2026-11-09 KST — M7 최종 독립 QA closeout. `qa/M7-review.md`에 source commit `0b3008a`, backend 91 suites/538 tests 0/0/0, FE 41 files/405 tests·lint/build PASS, run4 실제 acceptance 259/259 PASS·0 NOT_RUN, source/JAR SHA 독립 일치, 원본/제품 1440px PNG 4개 직접 대조 결과를 기록했다. run1 slug·run2 selector·run3 page-order 실패는 QA-only 이력으로 CLOSED했고 제품 Critical/Important/Minor finding은 없다. 최종 판정은 **M7 QA PASS / merge-ready Yes (dev 통합 전)**이며 root의 PR/`dev` 통합/closeout만 잔여다. M6 최종 독립 QA PASS 및 dev 통합 완료 상태(M6 FE-001~006 CLOSED, WR-01/02/03 ADDRESSED, backend 77 suites/490 tests 0/0/0, FE 34 files/369 tests·lint/build PASS, final immutable runtime 52/52 PASS, PR #12 merge SHA `4399f73acf19d66d96fafd2c4784c6a03fcfa282`, 통합 tree `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`)는 유지된다.
- 2026-11-09 KST — M7 dev 통합 closeout. PR #13 merge SHA `08863207b8385177b619091410bbbd43e52a337c`, tested head `2b984b6a2fc3aafd6d650c6ccb1cd8213a528545`, 통합 tree `dc68037477c6fed6b77d537235d79093803a983e`를 read-only 대조했고 tested head tree와 일치했다. 기존 독립 시각 포함 QA PASS·merge-ready 판정을 유지하며, `qa/M7-review.md`를 **DEV INTEGRATED**로 갱신했다. 추가 테스트·제품 수정·Git 조작은 하지 않았다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance·M6 worklog 원본·Git은 수정하지 않았다. 기존 root의 `.claude/team/JOURNAL.md` 및 M6 정본 untracked 변경은 보존했다.
