# frontend 작업 기록

> append-only. 기존 항목을 수정·삭제하지 않는다.

## 2026-09-06 — 팀 상태 기준선 생성

- 한 일: 기존 M0~M2 worklog와 현재 `feature/M2-settings` 워킹트리를 기준으로 frontend 역할 상태를 초기화했다.
- 산출물: `.claude/team/frontend/CLAUDE.md`, `STATE.md`, 이 파일.
- 검증: 리더가 역할 파일 존재와 Git/worklog 참조를 재확인한다.
- 미해결: M2 frontend 변경의 테스트·시각 검증·최종 리뷰·머지.

## 2026-09-06 04:52 KST — M2 종료 검증

- 한 일: 헌법·역할 지침·디자인/PRD/REQUIREMENTS·M2 worklog·ADR-0004를 재확인하고, 설정 메뉴 카피를 정본에 맞춰 `프로필 · 계정`/`카테고리 관리`로 교정했다. 테스트 기대값도 같은 변경으로 갱신했다.
- 산출물: `frontend/src/components/layout/ScreenPanel.tsx`, `frontend/src/test/shell-regions.test.tsx`.
- 검증: `npm test` — 21 files, 244 tests passed; `npm run lint` — exit 0; `npm run build` — exit 0, Vite 63 modules. `git diff --check -- frontend` 이상 없음. skipped/deprecated old-token/rounded-class 검색에서 제품 코드 위반 없음(정본 언급 주석만 존재). Vite `http://127.0.0.1:5173/` HTTP 200, launcher PID 29744/listener PID 16332.
- 미해결: 리더의 1440px 실제 브라우저 대조 대기. 정적 대조상 `SettingsProfilePage` 프로필 아바타가 96px(`w-24 h-24`)로 원본 90px과 다르고, `BlogPage`가 비-404 API 오류도 404 문구로 표시한다. RISK-0005 운영 HTTPS refresh-cookie smoke는 후속 배포 게이트다.

## 2026-09-06 04:53 KST — M2 검증 재실행 관찰

- 한 일: 라벨 교정 후 plain `npm test`를 재실행했다. 병렬 부하에서 1회 4/244 실패(설정 성공문구의 3초 자동 숨김이 테스트 상호작용 중 만료된 1건, `authForms` 5초 timeout 3건)가 있었고, 관련 파일 단독 실행(`shell-regions + settingsBehavior` 41/41, `authForms` 10/10) 및 plain 재실행은 통과했다.
- 산출물: 제품 코드 추가 변경 없음.
- 검증: 최종 plain `npm test` 21 files/244 tests passed, lint/build도 직전 성공을 재확인했다.
- 미해결: CI/자원 부하가 큰 환경에서 테스트 timeout 또는 3초 성공문구 타이밍 의존성이 재발할 수 있다. 기능 결함으로 단정하지 않았으며, 필요 시 테스트 대기/타이머만 안정화하는 최소 수정이 제안된다.

## 2026-09-06 05:12 KST — M2 잔여 갭 보정 및 QA 인계

- 한 일: `BlogPage`가 `ApiRequestError`의 404만 `블로그를 찾을 수 없습니다.`로 표시하고, 500/네트워크 오류에는 `잠시 후 다시 시도해 주세요.`와 재시도 버튼을 제공하도록 수정했다. retry key와 기존 effect cleanup guard를 유지했다.
- 한 일: `SettingsProfilePage`를 디자인 원본 `ZeroVerse Pages.dc.html` 292~299에 맞춰 90px/#ffe9c9/42px 아바타, URL 이미지·이모지 fallback, 정본 `변경` 버튼 스타일과 URL 입력 포커스/스크롤, 우측 열 intrinsic 저장 버튼(`self-start`, `px-[22px]`)으로 보정했다.
- 테스트: `settingsBehavior.test.tsx`에 API 404/500·재시도, 늦은 성공·늦은 404 오류(stale guard), 프로필 pathname(`/blog/already-done`), 아바타 URL/fallback/크기, 변경 포커스와 저장 폭 회귀를 추가·강화했다.
- 검증: `npm test` — 21 files / 247 tests passed (0 failures, 0 skipped); `npm test -- --run src/test/settingsBehavior.test.tsx` — 1 file / 29 tests passed; `npm run lint` — exit 0; `npm run build` — exit 0, Vite 63 modules, JS 276.42 kB (gzip 85.86 kB). `git diff --check -- frontend .claude/team/frontend`는 줄바꿈 경고만 있고 오류 없음.
- 실제 확인: 리더가 최신 `/blog/m2-smoke-renamed` 1440px에서 hero title/description/owner fallback, paper 배경, scrollWidth 1440, radius 0 및 settings 각 카드 저장·새로고침 보존을 확인했다. Vite hidden `http://127.0.0.1:5173/` HTTP 200 유지(launcher PID 29744, listener PID 16332; 종료 시 해당 프로세스 트리 사용).
- 인계: M3 FE는 현재 `SettingsPostsPage`/`/settings/posts` 보호 라우트와 `ScreenPanel` 카테고리 패널 경계를 재사용하고, Q1~Q4 계약 승인 후 실제 카테고리 API·트리·정렬 UI를 구현한다. `DEFAULT/GENERAL/LOCKED`, 루트+1단계, DEFAULT/LOCKED 불변, soft delete/cascade·글 DEFAULT 이동, 전체 order 배열을 선행 확인한다.
- 미검증: FE 소유 범위 밖인 최신 BE 전체 test/build/OpenAPI 및 운영 HTTPS refresh-cookie(`RISK-0005`)는 리더/QA 게이트다. `docs/worklog/M2-settings.md`는 리더 소유라 수정하지 않았다.

## 2026-09-06 05:24 KST — M2 공유 사용자 데이터 연결 및 최종 FE gate

- 한 일: 선택적 `useOptionalAuth`를 제거하고 기존 `AuthProvider`/`useAuth()` 공급원을 `AppRoutes → AppShell → TopBar·SideNav·RightPanel` props로 전달했다. 로그인 사용자는 `nickname`·`defaultBlog.urlSlug`·블로그 제목/slug를 세 소비자가 공유하고, 비로그인은 TopBar `로그인`/`/signin`, SideNav My Blog `/signin`으로 연결한다.
- 테스트: `settingsBehavior.test.tsx` 내 AppRoutes 통합 회귀로 초기 slug/닉네임/우측 패널과 `refreshUser()` 이후 변경된 slug·닉네임·제목을 확인했다. TopBar 게스트 링크 `/signin`, SideNav 게스트 fallback `/signin` 및 로그인 slug 테스트를 갱신했다.
- 검증: 집중 `npm test -- --run src/test/settingsBehavior.test.tsx src/test/TopBar.test.tsx src/test/SideNav.test.tsx src/test/router.test.tsx` — 4 files / 61 tests passed; plain `npm test` — 21 files / 250 tests passed, 0 failures/0 skipped; `npm run lint` — exit 0; `npm run build` — exit 0, Vite 63 modules transformed, `dist/assets/index-BnmL5og_.js` 277.26 kB (gzip 86.02 kB). `useOptionalAuth`, `/blog/me`, `제로별` 잔존 검색 결과 없음.
- 실행 환경: Vite hidden `http://127.0.0.1:5173/` HTTP 200, 866 bytes; launcher PID 29744, intermediate PID 20908, listener PID 16332를 유지한다. 종료 시 이 프로세스 트리를 대상으로 한다.
- 인계/미검증: 리더가 1440px 실제 브라우저 및 API smoke를 담당하며, FE gate는 통과했다. QA 독립 재검토와 리더의 BE 전체 test/build/OpenAPI/운영 HTTPS refresh-cookie(`RISK-0005`) 게이트는 FE 소유 범위 밖이다. M3 FE는 M2 머지·Q1~Q4 계약 승인 후 기존 `SettingsPostsPage`·`/settings/posts`·`ScreenPanel` 경계를 재사용한다. `docs/worklog/M2-settings.md`는 수정하지 않았다.

## 2026-09-06 05:38 KST — M2 최종 gate 인계 및 FE 소스 동결

- 상태: 부모 확인 기준 FE 전체 `npm test` 21 files/250 tests, lint/build, Vite 및 1440px 실제 UI/API smoke가 통과했다. BE 전체 348개 통과와 QA M2 최종 `APPROVE96`도 완료되어 FE 역할의 검증·진단 범위는 종료했다.
- 인계: 로그인 후 My Blog가 실제 `defaultBlog.urlSlug`로 이동하고, 게스트 My Blog는 `/signin`으로 이동한다. 로그인 후 nickname·블로그 title/slug·settings 복구 동작은 부모의 실제 smoke에서 확인됐다.
- 독립 검증: stale guard 제거 mutation 증거는 부모가 설치된 Vitest/Vite 메모리 변환으로 원본을 건드리지 않고 별도 수행한다. frontend 역할에서는 제품 소스 동결 후 해당 변형을 수행하지 않았으며, 결과는 부모 검증 기록을 따른다.
- 동결/제약: 제품 코드와 Git 조작은 추가하지 않았다. `STATE.md`/이 기록만 갱신했다. QA finding 배정 또는 새 부모 범위 지시 전에는 FE 소스를 수정하지 않는다. 운영 HTTPS refresh-cookie는 배포 전 `RISK-0005` 위험으로 유지한다.
- 다음 마일스톤: M2 실제 머지 후 사용자 Q1~Q4 승인 대기. 승인되면 기존 `SettingsPostsPage`, `/settings/posts`, `ScreenPanel` 경계를 기반으로 M3 카테고리 API·트리·정렬 UI를 시작하며 `DEFAULT/GENERAL/LOCKED` 계약을 먼저 확인한다. `docs/worklog/M2-settings.md`는 리더 소유라 수정하지 않았다.

## 2026-09-06 05:39 KST — stale mutation 검증 완료 사실 정정

- 정정: stale guard 제거 mutation은 예정 상태가 아니라 부모가 05:26에 실제 완료했다. `build/m2-stale-mutation.mjs`의 Vitest/Vite 메모리 변환과 `build/m2-stale-mutation.log`에서 늦은 성공·늦은 실패 두 변형이 각각 expected failure(exit 1)로 드러났고, 원본 보호를 확인했다.
- 보존 증거: 원본 `frontend/src/pages/BlogPage.tsx` SHA256가 mutation 전후 동일했으며, 검증 후 원본 전체 FE gate `npm test` 21 files/250 tests passed를 재확인했다. 해당 독립 검증 결과는 부모의 최종 기록을 따른다.
- 동결: 이 정정은 역할 기록만 변경했다. 제품 코드, Git, `docs/worklog/M2-settings.md`는 변경하지 않았고 FE 소스 동결 상태를 유지한다.

## 2026-09-06 05:42 KST — M2 머지 완료 및 M3 결정 대기

- 상태: 부모가 PR #8을 `dev`에 fast-forward 머지했다(merge SHA `4c129e20f58a6ccb9c61246d103934702516c295`, GitHub mergedAt `2026-09-05T20:41:12Z` = 2026-09-06 05:41:12 KST). 공유 checkout도 `dev`로 동기화됐고 머지 전 검증 제품 tree와 동일함을 확인했다.
- M2 판정: FE 21 files/250 tests, lint/build, Vite, 1440px·실제 API smoke, BE 348개, QA `APPROVE96` 기록을 인계받아 M2 FE 범위를 완료 처리한다.
- M3 상태: `USER_DECISION_REQUIRED`. Q1~Q4 카테고리 계약에 대한 사용자 결정·승인 전에는 제품 변경이나 추가 테스트를 시작하지 않는다.
- 동결: 이번 갱신은 [STATE.md](STATE.md)와 이 WORKLOG append만 수행했다. frontend 제품 코드, 추가 테스트, Git 조작, `docs/worklog/M2-settings.md` 수정은 없으며, 부모가 M2 merge 기록과 M3 계획을 문서화한다. 운영 HTTPS refresh-cookie `RISK-0005`는 배포 전 위험으로 유지한다.
- 사실 정정: PR #8은 `gh pr merge --merge`로 merge commit `4c129e20f58a6ccb9c61246d103934702516c295`를 생성했으며, 이후 로컬 `dev`를 `origin/dev`에 fast-forward 동기화했다.

## 2026-09-06 06:05 KST — M3 FE 계약·디자인 사전 대조

- 한 일: 헌법→루트 지침→frontend 역할 지침/상태를 순서대로 재확인하고, `docs/design/AGENTS.md`·`DESIGN-SYSTEM.md` 전체와 `ZeroVerse Pages.dc.html`의 `/blog/setup`·`/blog/:slug`·`/settings/posts` 원본 스타일, PRD §5.4·§6·§7·§9·§10~§12, REQUIREMENTS FR-CAT-01~05/FR-BLOG-02/FR-SETTINGS-04, M3 회의록·ADR-0005를 읽었다. `SettingsPostsPage`, `ScreenPanel`, `BlogPage`, `BlogInitialSetupPage`, `settingsApi`/`blogApi`, `apiClient`/`AuthContext`, 공통 UI와 기존 테스트를 읽어 재사용 경계를 확인했다.
- 산출물: 제품 코드 변경 없음. FE 구현 대상은 기존 `/settings/posts` 보호 라우트·`SettingsPostsPage` placeholder·`ScreenPanel` 카테고리 경계이며, 새 DnD/상태관리 의존성은 추가하지 않는다.
- 검증: `git status --short`로 공유 작업자의 docs/governance 변경만 확인했고, 현재 backend에는 M3 category controller/service/DTO가 아직 없어 실제 응답 shape를 확정할 수 없음을 확인했다. 디자인·PRD·REQUIREMENTS 계약의 핵심은 전체 root 페이지를 읽어 children을 합친 뒤에만 write/reorder를 허용하고, setup은 initial-setup 성공 후 GET→누락 루트 GENERAL 순차 POST→GET 재확인으로 복구하는 것이다.
- 미해결: 리더/BE의 계약 검증 전달 전에는 `frontend/**` 제품 파일을 수정하지 않는다. 실제 BE DTO·오류 코드·페이지 응답·LOCKED 순서/삭제 결과를 받은 뒤 타입/API와 행동 테스트를 구현한다.

## 2026-09-06 15:57 KST — M3 승인·FE 구현 게이트 해제 정정

- 정정: 위 06:05 KST 기록은 당시 실제 시각을 확인하지 않고 작성한 사전 대조 기록이다. `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 실제 출력 `2026-09-06 15:57:24 +09:00`을 기준으로 현재 기록 시각을 정정한다. 과거 항목은 append-only 규칙에 따라 보존한다.
- 승인 상태: 사용자 "시작" 후 M3 회의록 상태 `APPROVED`, ADR-0005 상태 `ACCEPTED`를 확인했다. Q1~Q4 승인 대기 문구는 과거 기록이며 현재 FE 구현을 차단하지 않는다.
- 게이트: 리더가 MySQL CategoryService/Controller 테스트와 실패·skip 0, DTO/경로/trim/OpenAPI 기본 계약을 확인해 FE 게이트를 해제했다. API 형태는 backend 최종 동시성·추가 입력·migration 검증 중에도 유지된다.
- 다음: 실제 `categoryApi`·타입·SettingsPostsPage·ScreenPanel·BlogPage·BlogInitialSetupPage 연동과 필수 행동 테스트를 frontend 소유 경로에서 진행한다.

## 2026-09-06 16:25 KST — M3 FE 구현·검증 완료 인계

- 구현: `frontend/src/features/category/categoryApi.ts`에 BE PageResponse/CategoryType(`DEFAULT/GENERAL/LOCKED`)과 GET 전체 root page 병합, 생성·수정·삭제·sibling 전체 ID 순서 API를 추가했다. `categoryApi.test.ts`에서 `includeDrafts`, 101개 루트 2페이지, body/path 계약을 검증했다.
- 구현: `SettingsPostsPage`를 단일 카드 관리 화면으로 교체했다. 전체 root page 로드 완료 전 mutation/reorder를 잠그고, root+하위 1단계 생성, inline GENERAL 이름 수정, DEFAULT/LOCKED 불변, GENERAL→LOCKED 및 신규 LOCKED 되돌릴 수 없음 확인, 삭제 시 ‘미분류’ 안내, native DnD·ArrowUp/Down 순서와 LOCKED numeric slot 보호, CAT_007 재조회/error 표시를 연결했다.
- 구현: 공개 `ScreenPanel`은 hero context가 채운 blog id로 `includeDrafts=false` category tree/count를 실제 조회하고, 글 목록·필터는 M4 범위로 만들지 않았다. `BlogPage` stale/auth 동작은 유지했다.
- 구현: `BlogInitialSetupPage`는 initial-setup 성공(또는 GET 확인 가능한 응답 유실/ BLOG_004 복구) 뒤에만 전체 category GET → 누락 root GENERAL 순차 POST를 수행한다. POST 부분 실패·응답 유실은 GET으로 이미 저장된 항목을 보존하고 남은 항목만 재시도하며 initial-setup을 재호출하지 않는다. 완료된 category 확인 뒤 `refreshUser()`와 blog 이동을 수행한다.
- 테스트: `categoryBehavior.test.tsx`에서 공개 tree/count, 전체 페이지 로드, child 생성, inline rename, lock/default 보호, keyboard/native DnD, CAT_007 재조회를 실제 행동으로 검증했다. `BlogInitialSetupPage.test.tsx`에 partial failure·lost response·initial-setup 1회 검증을 추가했고 기존 M2 setup 응답도 갱신했다.
- 검증 명령/핵심 출력: `npm.cmd test` → 23 files / 261 tests passed, 0 failures, 0 skipped; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules; `git diff --check -- frontend .claude/team/frontend` → 오류 없음(공유 checkout의 LF→CRLF 경고만).
- 현재 환경: checkout `feature/M3-categories`, HEAD `1690731`; root가 관리하는 Vite `http://localhost:5173` launcher PID 24196을 재사용했다. BE API/JAR가 아직 최종 연결되지 않아 1440px 실제 API smoke는 리더·독립 QA 인계로 남겼다.
- 미해결/위험: BE 최종 동시성·추가 잘못된 입력·migration 검증과 root의 실제 API 브라우저 smoke, 독립 QA diff review 대기. API 형태는 변경하지 않았다.

## 2026-09-06 16:28 KST — 카테고리 단일 조회 기본 페이지 크기 정합화

- 정합화: 서버 계약의 단일 목록 기본값(`page=0`, `size=20`, `includeDrafts=false`)에 맞춰 `getCategories`의 선택적 `size` 기본값을 20으로 조정했다. 관리·공개 전체 로더는 모든 루트 페이지를 읽기 위해 기존처럼 최대 허용값 `size=100`을 명시한다.
- 검증: 실제 시각 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-06 16:28:53 +09:00`을 사용했다. `npm.cmd test` → 23 files / 261 tests passed, 0 failures, 0 skipped; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules.

## 2026-09-06 16:45 KST — QA F-M3-FE-01~04 수정·회귀 검증

- F-M3-FE-01 수정: `SettingsPostsPage`에 현재 blog ID와 operation generation 경계를 추가해 blog A→B/null→A 전환 중 늦은 load/mutation/finally가 현재 상태·notice를 덮지 않게 했다. null 전환 시 loading도 해제하고 mutation controls를 초기화한다.
- F-M3-FE-02 수정: mutation 후 `loadCategories()`의 성공 여부를 확인한 뒤에만 성공 notice를 남긴다. reload 실패는 미확인 결과로 표시하고, CAT_001/CAT_007 문구도 실제 재조회 성공을 주장하지 않도록 바꿨다. retry는 이전 성공 notice를 지운다.
- F-M3-FE-03 수정: `BlogInitialSetupPage`에 `completedBlog`를 보존하는 `카테고리 관리에서 이어서 하기` 경로를 추가했다. location state로 `/settings/posts` 의도를 보존한 뒤 `refreshUser()`를 수행하고, `SetupGuard`가 완료 세션을 해당 고정 경로로 넘긴다. initial-setup 재호출은 하지 않는다.
- F-M3-FE-04 수정: 공개 `ScreenPanel`이 `useOptionalAuth().user.id`와 blog identity를 함께 감시하고 요청 시작 시 categories를 비운다. 통계는 로딩 중 `…`, 실패 시 `—`를 표시해 이전 count/미확인 0을 노출하지 않는다.
- 회귀 테스트: `categoryBehavior.test.tsx`에 mutation 성공→reload 실패→retry, A mutation pending→B/null→A, 공개 blog 전환 loading/failure count, viewer identity 전환 count를 추가했다. `BlogInitialSetupPage.test.tsx`에 partial setup→refreshUser→SetupGuard→관리 경로와 initial-setup 1회 유지를 추가했다.
- 검증: 실제 시각 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-06 16:45:40 +09:00` 기준. `npm.cmd test` → 23 files / 266 tests passed, 0 failures, 0 skipped; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules; `git diff --check -- frontend .claude/team/frontend` → 오류 없음(LF→CRLF 및 공유 git ignore 권한 경고만).
- 남은 범위: 독립 QA·root diff 재검토와 BE/JAR 연결 후 실제 API·1440px 브라우저 smoke 대기. 이번 라운드에서 API 형태·Post/Universe CRUD·새 의존성은 추가하지 않았다.

## 2026-09-06 17:25 KST — QA F-M3-FE-05~07 수정·회귀 검증

- F-M3-FE-05 수정: `SettingsPostsPage`의 create 응답이 `isCurrentOperation` 확인 전에 `newName`을 비우던 순서를 뒤집었다. A blog create가 pending인 동안 B로 전환해 입력한 이름이 늦은 A 응답 때문에 사라지지 않도록 현재 operation과 loaded blog 경계를 먼저 확인한다.
- F-M3-FE-06 수정: 실제 성공한 category GET의 blog ID를 `loadedBlogId` state로 보존하고 현재 blog ID와 일치할 때만 reorder/create/rename/type/delete/DnD를 허용한다. blog 전환 렌더와 passive effect 사이에도 이전 목록으로 mutation을 시작하지 않으며, 입력 handler와 조작 UI도 같은 준비 상태를 사용한다.
- F-M3-FE-07 수정: category 부분 실패가 확인되는 즉시 `/blog/setup`의 `setupRecoveryTo: '/settings/posts'` history state를 기록한다. 버튼 클릭 전 새로고침에서도 `SetupGuard`가 관리 화면으로 보낼 수 있고, 전체 성공 시에는 기존 blog 완료 이동과 state 정리를 유지한다. 새 storage/API는 추가하지 않았다.
- 회귀 테스트: `categoryBehavior.test.tsx`에 delayed create→B 새 입력 보존과 B GET pending 중 이전 loaded 목록 mutation 차단을 추가했다. `BlogInitialSetupPage.test.tsx`에 부분 실패→AuthProvider 재마운트(브라우저 새로고침 동등)→history state 기반 관리 진입과 initial-setup 1회 유지를 추가했다.
- 검증: 실제 시각 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-06 17:25:22 +09:00` 기준. `npm.cmd test -- src/test/categoryBehavior.test.tsx src/test/BlogInitialSetupPage.test.tsx` → 2 files / 24 tests passed, 0 failures/0 skipped; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules. 초기 build에서 테스트 callback narrowing 오류 2건이 발생했으나 명시적 callback cast로 보정 후 재빌드 통과했다.
- 남은 범위: root의 전체 FE test 재실행·diff 독립 검토, BE/JAR 연결 후 실제 API 및 1440px 브라우저 smoke. 제품 파일은 이번 수정 범위에서 동결한다.

## 2026-09-06 18:08 KST — setup 완료 navigation intent 경합 수정·관련 회귀

- 수정: `BlogInitialSetupPage`가 완료 블로그 ref를 유지한 채 `/blog/setup`에 `setupCompletionTo: 'blog'` history state를 먼저 커밋한다. location effect가 해당 intent의 실제 commit과 완료 ref를 확인한 뒤 한 번만 `refreshUser()`를 시작하고, cleanup 시 늦은 continuation을 무시한다. 부분 실패 재시도와 BLOG_004 복구 성공에도 같은 경로를 사용하며, 관리 버튼의 `setupRecoveryTo` 경로는 유지했다.
- 수정: `SetupGuard`는 완료 사용자이면서 유효한 completion intent일 때 임의 state URL이 아니라 `user.defaultBlog.urlSlug`에서 `/blog/{slug}`를 계산한다. intent가 없으면 기존 `/settings/posts` recovery 또는 `/` 기본 정책을 유지한다.
- 테스트: 실제 앱과 같은 `/blog/setup`만 Guard, `/blog/myblog`는 공개 목적지인 fixture에서 partial 실패→동일 화면 retry→자기 블로그 이동을 통과시켰고, Guard intent slug 회귀를 추가했다. 초기 설정 1회, 관리 버튼, 새로고침 recovery, 응답 유실/부분 실패, 기존 stale continuation drain을 보존했다. 완료 세션 fixture의 slug도 서버 응답과 일치하도록 정합화했다.
- 검증: `npm.cmd test -- --run src/test/BlogInitialSetupPage.test.tsx` → 1 file / 13 tests passed; `npm.cmd test -- --run src/test/BlogInitialSetupPage.test.tsx src/test/guards.test.tsx` → 2 files / 25 tests passed; `npm.cmd test -- --run src/test/categoryBehavior.test.tsx src/test/settingsBehavior.test.tsx` → 2 files / 42 tests passed; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules, JS 296.60 kB (gzip 90.75 kB). 실제 시각은 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-06 18:08:23 +09:00` 기준이다.
- 실험 정리: plain clear→refresh→navigate는 stale recovery state를 Guard가 먼저 소비해 관리/홈으로 빠졌고, `flushSync`와 `setTimeout(0)` 실험도 제품에서 제거했다. 최종 구현은 명시 intent commit 경로만 남겼다. 전체 FE 269 재실행과 실제 API/1440px 브라우저 smoke는 root/리더 검증으로 남는다.

## 2026-09-06 18:10 KST — refreshUser 실패 회귀 보강·최종 관련 검증

- `BlogInitialSetupPage.test.tsx`에 completion intent 이후 첫 `refreshUser()`만 실패하는 fixture를 추가했다. 완료 블로그로 잘못 이동하지 않고 recovery/관리 재시도로 이어지며 initial-setup 호출이 1회인 것을 확인한다.
- 최종 관련 실행: `npm.cmd test -- --run src/test/BlogInitialSetupPage.test.tsx src/test/guards.test.tsx src/test/categoryBehavior.test.tsx src/test/settingsBehavior.test.tsx` → 4 files / 68 tests passed, 0 failures/0 skipped; `npm.cmd run lint` → exit 0; `npm.cmd run build` → exit 0, Vite 64 modules, JS 296.60 kB (gzip 90.75 kB). 실제 시각은 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-06 18:10:56 +09:00` 기준이다.

## 2026-09-07 22:49:08 KST — M3 정본 시각 보완·immutable 접근성 회귀

- 한 일: 리더의 제한 배정에 따라 `SettingsPostsPage` 행을 정본(`gap:12px`, `padding:13px 20px`, `1px solid #eadbc4`, 이름 14px/700, count 12px, handle 15px/#d8c7b0)에 맞췄다. DEFAULT/LOCKED의 `이름 변경`·`삭제` 버튼을 숨기지 않고 `inert` variant + `disabled`로 렌더했으며, 기존 handler guard와 GENERAL 동작은 유지했다.
- 한 일: 카테고리 관리 하단 form을 정본 `gap:10px; padding:16px 20px`로 조정하고 불필요한 raise 배경·3px 상단 보더를 제거했다. 삭제 안내를 카드 밖 12px muted/4px padding 캡션으로 이동했다. `BlogInitialSetupPage`의 시작 칩은 12px/700·padding 5px 12px, `+ 추가`는 2px dashed shadow 경계로 맞췄고 입력·Enter 추가 흐름은 유지했다. slug availability API·새 의존성·다른 영역은 추가하지 않았다.
- 테스트: `categoryBehavior.test.tsx`에 immutable 버튼 disabled와 row/form/caption class boundary를 추가하고, `BlogInitialSetupPage.test.tsx`에 setup chip/add class 및 click 추가 회귀를 추가했다. 첫 실행의 테스트 선택자 오류(outer wrapper와 중복 heading)를 수정한 뒤 관련 2 files / 27 tests passed, 0 failures/0 skipped.
- 검증: `npm test ...`는 PowerShell execution policy로 `npm.ps1`가 차단됐고, 동일 테스트를 `npm.cmd test -- --run src/test/categoryBehavior.test.tsx src/test/BlogInitialSetupPage.test.tsx`로 재실행해 exit 0을 확인했다. `npm.cmd run lint` exit 0, `npm.cmd run build` exit 0(Vite 64 modules)이다. 실제 시각은 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-07 22:49:08 +09:00`을 사용했다.
- 미해결: 리더의 실제 1440px 브라우저 재확인은 리더가 수행한다. M3 마감·PR/dev 반영 이후 M4는 시작하지 않는다.

## 2026-09-07 22:51:10 KST — native DnD payload 보완·회귀 검증

- 한 일: 리더의 CUA 재현에서 확인된 native DnD 미이동 원인(dataTransfer payload 부재)을 `SettingsPostsPage`의 기존 `onDragStart`에 한정해 보완했다. ready·reorder 가능 행에서만 `dataTransfer.setData('text/plain', String(category.id))`와 `effectAllowed = 'move'`를 설정한다. drop 대상 ID는 계속 내부 `draggedId`를 사용하고, 동일 부모 sibling·전체 ID·LOCKED numeric slot 검증은 변경하지 않았다.
- 테스트: `categoryBehavior.test.tsx` native DnD 회귀에서 `setData('text/plain', '2')`와 `effectAllowed === 'move'`를 확인하고 LOCKED drop 보호를 유지했다. `npm.cmd test -- --run src/test/categoryBehavior.test.tsx src/test/BlogInitialSetupPage.test.tsx` → 2 files / 27 tests passed, 0 failures/0 skipped.
- 검증: `npm.cmd run lint` exit 0, `npm.cmd run build` exit 0(Vite 64 modules; JS 296.76 kB, gzip 90.81 kB). 실제 시각은 `Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'` 출력 `2026-09-07 22:51:10 +09:00`을 사용했다.
- 미해결: 실제 native drag 재검증은 리더가 공유 브라우저에서 수행한다. M3 종료 범위이며 M4·DnD 의존성은 추가하지 않는다.

## 2026-09-07 23:04:00 KST — STATE/WORKLOG 사실관계 정정

- 정정: 공유 checkout의 현재 FE 상태는 `feature/M3-categories`, HEAD `067cd117`이다. 최신 root 독립 FE 검증은 전체 273 tests, lint/build exit 0이다. 기존 22:51 기록은 보존하며 덮어쓰지 않았다.
- 브라우저 범위: 리더가 1440px에서 실제 signup/setup/self blog/reload, 루트·하위 keyboard 순서 저장, rename, duplicate·재조회 및 시각 대조를 확인했다. 이 검증과 native mouse DnD는 별도 항목으로 구분한다.
- DnD 원인 정정: 22:51의 `dataTransfer payload 부재`는 원인 확정이 아니다. payload/effectAllowed 보완 후에도 CUA drag에서 AX 순서·notice 변화가 없었고, 소스 handler 및 관련 회귀는 정상이다. 현재는 CUA가 HTML5 `dragstart`→`dragover`→`drop` lifecycle을 완성하지 못한 도구 한계로 추정하며, 사용자 수동 native mouse 확인을 대기한다.
- 미해결/범위: 제품 변경은 없었다. M3 마감·PR/dev 반영을 기다리며 M4는 시작하지 않는다.

## 2026-09-08 09:56:46 KST — M3 종료 게이트 상태 정합화

- 한 일: 헌법·루트/역할 지침·frontend `STATE.md`, 디자인 정본과 `/blog/setup`·`/settings/posts` 원본, PRD §5.4·§7·§9.5·§10~§12, REQUIREMENTS FR-CAT-01~05/FR-SETTINGS-04, M3 worklog·QA 최신 기록을 재독했다. 현재 FE checkout과 native DnD/LOCKED 잔여 게이트를 최신 root 증거에 맞췄다.
- 산출물: `.claude/team/frontend/STATE.md`의 HEAD를 `08239e0`로, 제품 tree 불변 기준을 `4fc9ae2`로 갱신했다. native mouse DnD 저장 gate는 PASS로, LOCKED confirm 후 저장·GET·disabled 확인은 pending으로 정리했다. 이 `WORKLOG.md`와 `STATE.md`만 수정했으며 frontend source/test/deps, Git, UI, 서버는 변경하지 않았다.
- 검증: `git branch --show-current`=`feature/M3-categories`, `git rev-parse HEAD`=`08239e0514b6a1a78f090c3b0961e8fd4a60403e`; `git diff --name-status 4fc9ae2..HEAD -- src frontend` 출력 없음. 기존 FE `23 files/273 tests`, lint/build exit 0, 직접 의존성 17개 lock/설치 exact match, Vite `8.1.5`, `npm ls --depth=0` exit 0 증거를 재확인했다. root의 분리 Chrome CUA drag(`[515,281]`→`[515,223]`) 후 AX 순서·성공 notice·DB `19/0, 21/1, 20/2, 22/3` 결과를 native DnD PASS 근거로 기록했다.
- 미해결: viewport override는 browser zoom 90%로 `innerWidth=1600`이어서 이번 관측을 1440 CSS px 실측으로 세지 않으며 기존 1440px 시각 검증은 유지한다. `회고` id `22`는 DB에서 아직 GENERAL이고 dialog accept timeout 후 세션 reset됐으므로 사용자 직접 `OK` 클릭 뒤 LOCKED 저장·GET 재조회·rename/delete disabled를 확인해야 한다. QA 최종 승인·`dev` merge 전 M3는 미완료이며 M4는 시작하지 않는다.

## 2026-09-08 10:08:36 KST — LOCKED 실제 저장·reload gate 해소 반영

- 한 일: M3 worklog `2026-09-08 10:05 KST` 항목을 읽고, 새 인앱 브라우저 실측을 frontend 상태에 반영했다. 기존 Chrome fixture id `22`의 GENERAL 결과는 과거 사실로 보존하고 새 evidence와 구분했다.
- 산출물: `.claude/team/frontend/STATE.md`의 현재 상태·다음 작업·차단을 LOCKED gate 해소 및 QA 최종 판정/PR #9 `dev` merge 대기로 최소 정정했다. 이 `WORKLOG.md`와 `STATE.md`만 수정했으며 제품 source/test/UI/deps/server/Git는 변경하지 않았다.
- 검증 근거: root 독립 인앱 브라우저에서 새 합성 blog ID `7`/category ID `26`의 `회고`를 LOCKED로 저장하고 order `3`, `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.`, `회고 0개의 글 LOCKED`를 확인했다. full reload 후에도 LOCKED/order `3` 및 순서 이동·이름 변경·삭제·타입 선택 4개 조작의 disabled 상태를 확인했다. QA 직접 조작으로 표기하지 않는다.
- 미해결: 남은 단계는 독립 QA 최종 acceptance와 리더의 PR #9 `dev` merge·M3 마감이다. 이번 IAB 관측은 1440 CSS px 실측으로 확대하지 않고 기존 1440px 시각 evidence를 유지한다. M4는 시작하지 않는다.
