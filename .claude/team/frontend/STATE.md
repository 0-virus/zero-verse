# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-09 23:28:41 KST

## 현재 단계

- M7 A-v2 사용자 승인 후 frontend Task3(MainPage·RightPanel)와 Task4(TopBar·SearchPage) 구현 및 자동 검증을 완료했다. root의 fresh FE 검증과 실제 acceptance run도 통과했고, full fresh review의 제품·QA blocking은 CLOSED다.
- 작업 브랜치는 `feature/M7-feed-search`이며 backend Task1~2와 병렬이다. backend/rootdocs/QA 경로, Git, 의존성, `.env.local`은 수정하지 않았다.
- 실제 HTTP/acceptance는 root가 통과 판정했으며 독립 시각 QA 판정과 dev merge가 아직 남아 있다. source freeze를 유지하고 merge SHA 확인 후 최종 closeout을 추가 인계한다.

## M7 Task3 — 메인 피드·관계 현황

- `MainPage`는 URL의 `relation/keyword/page/size/sort`를 기준으로 guest `/api/v1/feed/public`, 인증 `/api/v1/feed/universe`를 호출한다. `friend` URL은 canonical `friends`로 치환하고, 전체/친구/내가 발견한 3탭·검색·page0 전환·서버 `totalElements`·페이지네이션을 연결했다. 결과 total은 있으나 요청 page가 범위를 넘은 빈 응답도 안내와 유효 페이지 복귀용 pagination을 유지한다.
- 인증 준비 중에는 guest 조회를 보류하고, 관계 보호 탭 guest는 로그인 안내를 표시한다. URL/계정/세대가 바뀌면 이전 카드·count를 즉시 숨기고 늦은 응답을 폐기한다. 확정 AUTH_002/AUTH_004/USER_001 및 transient 실패는 결과를 재사용하지 않고 오류·재시도 상태로 분리한다.
- following 피드에는 `친구를 제외한 내가 발견한 사람의 글입니다.`를 표시한다. `RightPanel`은 기존 friends/following/followers 목록을 각각 `page=0,size=1`로 조회해 `totalElements`를 표시하고, following 현황은 친구 포함 설명을 함께 표시한다. 오류를 0으로 대체하지 않으며 M8 최근 알림은 빈 상태를 유지한다.

## M7 Task4 — 검색·전역 검색

- `TopBar` 검색 submit은 trim·연속 공백 정규화 및 UTF-16 100자 제한을 적용해 `/search?keyword=...`로 이동하며 blank는 호출하지 않는다.
- `SearchPage`는 URL의 `keyword/type/page/size/sort`를 복원·canonicalize하고 5개 서버 facet count, `matchedTypes`, 게시글 중심 카드·보조 문구·페이지네이션을 렌더한다. counts를 클라이언트에서 합산하지 않는다.
- 결과 링크는 발행글 `/blog/{blogSlug}/{id}`, viewer 소유 draft는 `임시저장` 표시와 `/edit/{postId}`다. 검색 keyword/계정/page 세대 전환 즉시 기존 결과·counts를 숨기고 stale 응답을 무시하며 오류 재시도를 제공한다.
- 새 `features/feed`·`features/search` API/types 경계와 `PostCard`의 실제 href/키보드 접근성을 추가했으며 기존 BlogPage/PostCard 계약과 M8 범위를 보존했다.

## M7 FE fix round1

- `AuthContext`에 `sessionExpired`를 추가해 apiClient refresh 최종 실패(`AUTH_002/004`)로 `user=null`이 된 상태와 명시 로그아웃을 구분한다. Main/Search는 만료 상태에서 guest public/search 자동 재조회를 막고 빈 개인 결과와 만료 오류를 유지하며, 명시 로그아웃은 guest 전환을 허용한다. 실제 `AuthProvider`·apiClient 통합 회귀로 고정했다.
- `RightPanel`은 `statsViewerId`가 현재 viewer와 일치할 때만 통계를 그린다. A→B 첫 렌더에서 이전 숫자가 노출되지 않고 loading placeholder를 표시한다.
- Search `PostCard variant="search"`에 Pages 원본의 `padding 16px 20px`, title `16.5px`, snippet `13px/1.6/#5c4a72`를 적용했다. 결과가 있으나 요청 page가 범위를 넘은 빈 응답에서도 안내와 유효 페이지 복귀용 pagination을 유지한다.
- stale 계정/탭/page의 실제 resolve/reject, Main keyword·탭 page0 reset 및 AUTH_002/004·USER_001 retry, Search auth loading/account/retry/back 복원, AuthProvider refresh 실패·명시 로그아웃 회귀 테스트를 추가했다.

## 검증·변경 경계

- TDD RED→GREEN: fix 전 refresh 실패 통합 2개, RightPanel 계정 전환 1개, Search variant 1개, out-of-range pagination 1개가 의도대로 실패했다. 각 원인 수정 후 GREEN을 확인했다.
- M7 targeted: `npm test -- --run src/test/mainFeedBehavior.test.tsx src/test/searchBehavior.test.tsx src/test/m7AuthExpiryIntegration.test.tsx src/test/m7FeedStateBehavior.test.tsx src/test/m7SearchStateBehavior.test.tsx --maxWorkers=1` → 5 files/29 tests passed.
- M7 fix2 targeted: `npm test -- --run src/test/mainFeedBehavior.test.tsx --maxWorkers=1` (frontend cwd) → 1 file/8 tests passed. stdout은 ignored `frontend/.m7-evidence/fe-fix2-main.log`에 보존했다.
- 구현 역할의 이전 자동 검증: `npm test -- --run --maxWorkers=1` → 41 files/404 tests passed(실패·오류·skip 0); `npm run lint` exit 0; `npm run build` exit 0(`tsc -b`, Vite 143 modules, 기존 >500KB chunk warning만).
- root 최종 fresh 직접 실측: FE 41 files/405 tests, failures 0; lint exit 0; build exit 0. 관련 stdout은 `build/m7-evidence/frontend-final-*.log`에 root가 보존했다. BE는 538 tests / failures 0 / errors 0 / skipped 0 (91 suites)다.
- root acceptance run4: `build/m7-acceptance-20261109-232636/evidence.json` → 259 PASS / 0 FAIL / 0 NOT RUN / exit 0, source 354와 JAR hash 일치. full fresh review 및 제품/QA fix blocking은 CLOSED이며 독립 시각 QA 판정만 진행 중이다.
- `git diff --check -- frontend .claude/team/frontend` 오류 없음. dev merge는 아직 없고, 최종 통합 전 source freeze를 유지한다.
- 이번 wave에서 편집한 제품 경계는 `frontend/**`, 상태/로그는 `.claude/team/frontend/**`뿐이다. root untracked package/lock/run scripts 및 타 역할 변경은 열거나 수정·실행하지 않았다.

## 다음 작업 / 차단 요인

- 제품 차단 없음. full fresh review와 실제 acceptance의 blocking은 닫혔고 독립 시각 QA 판정 및 dev merge SHA 확인을 기다린다.
- source freeze 중에는 제품/테스트/Git을 수정하지 않는다. merge SHA가 전달되면 frontend closeout history만 추가한다.

## M7 closeout gate (root 전달 기준)

- 제품 커밋(통합 전): `5d7ede2`, `059ceed`, `0b3008a`가 root 기록에 있다. 이 역할은 Git을 조작하지 않았고 dev merge를 주장하지 않는다.
- root 최종 실측 로그와 acceptance evidence는 root 소유 경로를 참조만 한다. README/PRD/QA/rootdocs는 수정하지 않는다.
