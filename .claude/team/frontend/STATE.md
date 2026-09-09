# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-08 10:08:36 KST

## 현재 단계

- M2 기준 브랜치: `dev` (PR #8 `feature/M2-settings` 머지 완료, merge SHA `4c129e20f58a6ccb9c61246d103934702516c295`, 2026-09-06 05:41:12 KST).
- 현재 FE checkout: `feature/M3-categories`, HEAD `08239e0` (공유 checkout; Git 조작은 리더 소유). 제품 tree는 `4fc9ae2` 이후 불변이다.
- M0 스캐폴딩과 M1 인증은 `dev` 머지 기록이 있다.
- M2 설정·블로그 화면은 구현·다회 리뷰·QA 최종 승인 후 PR #8로 `dev`에 머지됐다.
- FE 자동 검증과 리더의 1440px 실제 브라우저·실제 API smoke가 통과했고, 리더 확인 기준 BE 348개와 QA M2 최종 APPROVE96도 완료됐다.

## 진행 중

- M3 FE 구현·QA·시각 보완 완료: `docs/design/DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`의 `/blog/setup`·`/blog/:slug`·`/settings/posts`, PRD §5.4·§6·§7·§9·§10~§12, REQUIREMENTS FR-CAT-01~05/FR-BLOG-02/FR-SETTINGS-04 및 M3 회의록을 대조하고 FE 계약 연동·QA F-M3-FE-01~07·정본 시각 경계를 완료했다.
- M3 Q1~Q4는 사용자 "시작"으로 승인됐고, 회의록은 `APPROVED`, ADR-0005는 `ACCEPTED`다. 리더가 BE CategoryService/Controller 테스트와 API 형태를 확인해 FE 구현 게이트를 해제했다.
- 실제 BE 변경에서 확인한 FE 계약은 GET 루트 페이지+children/`includeDrafts`, 모든 루트 페이지 로드 후 mutation/reorder 활성화, `DEFAULT/GENERAL/LOCKED`, 전체 sibling ID 배열 reorder, 순차 setup category 복구다.
- 구현: `categoryApi`/타입·페이지 병합과 API 테스트, `SettingsPostsPage` 단일 카드 CRUD/인라인 수정/GENERAL→LOCKED 경고/미분류 삭제 안내/native DnD·키보드 reorder, `ScreenPanel` 공개 tree/count, `BlogInitialSetupPage` 초기 설정 후 누락 루트 순차 보정 및 부분·응답손실 재확인을 연결했다.
- 시각 보완: `SettingsPostsPage`의 행을 정본 gap12/padding13px 20px/1px line/14px 이름·12px count·15px handle로 정합화하고, DEFAULT/LOCKED 이름 변경·삭제 버튼을 inert+disabled로 항상 렌더했다. 하단 form은 정본 gap10/padding16px 20px로 맞추고 삭제 안내를 카드 밖 캡션으로 이동했다. `BlogInitialSetupPage` 시작 칩을 12px·padding5px 12px, 추가 버튼을 2px dashed 경계로 정합화했으며 slug availability API는 추가하지 않았다.
- native DnD 보완: ready한 draggable 행의 `dragstart`에 `text/plain` category ID와 `effectAllowed='move'`를 기록하고, drop 판정은 기존 내부 `draggedId`·동일 sibling·LOCKED slot 검증을 유지한다. 외부 dataTransfer 값으로 이동 대상을 결정하지 않는다.

- `ScreenPanel` 설정 메뉴 카피를 정본(`프로필 · 계정`/`카테고리 관리`)에 맞춰 교정하고 회귀 기대값을 갱신했다.
- `SettingsProfilePage` 아바타를 정본 90px/#ffe9c9/42px로 맞추고 URL 이미지·이모지 fallback, `변경` 포커스/스크롤, intrinsic 저장 버튼을 연결했다.
- `BlogPage`가 `ApiRequestError.status===404`만 전용 문구로 표시하고, 500/네트워크 오류에는 재시도 UI를 제공하도록 보강했다.
- `AppRoutes`의 기존 `useAuth()` 사용자 상태를 `AppShell` props로 전달해 `TopBar` 닉네임, `SideNav` 기본 블로그 slug, `RightPanel` 내 블로그 정보를 같은 공급원으로 연결했다. 비로그인 `TopBar`는 `로그인`/`/signin`, `SideNav`의 My Blog는 `/signin`으로 안전하게 연결한다. 공개 `ScreenPanel`은 독립 테스트 렌더도 유지하도록 `useOptionalAuth()`의 viewer id를 blog identity와 함께 감시한다.
- M2 이력 검증: `npm test` 21 files/250 tests, `npm run lint`, `npm run build` 및 리더의 1440px smoke를 머지 시점에 통과했다. M3 F-M3-FE-01~04 직전 전체 기준은 `npm test` 23 files/266 tests(0 failures/0 skipped), `npm run lint`/`npm run build` exit 0이었다. F-M3-FE-05~07·시각·DnD 보완 관련 기준은 2 files/27 tests(0 failures/0 skipped), lint/build exit 0이었다. 최신 root 독립 전체 검증은 273 tests, lint/build exit 0이다.
- 늦은 slug 성공·404 오류 모두 실제 응답 본문 파싱과 React continuation 이후 stale guard를 검증했다. BLOG_004의 `/blog/already-done` pathname도 단정한다. 부모가 05:26 `build/m2-stale-mutation.mjs`(Vitest/Vite 메모리 변환)로 두 mutation을 expected failure(exit 1)로 확인했고, 원본 `BlogPage.tsx` SHA256 전후 동일 및 이후 원본 전체 250 통과를 확인했다.
- M2 이력 Vite PID(29744/20908/16332)와 이전 launcher PID 24196은 과거 기록으로 보존한다. 현재 리더가 API PID 8712와 Vite PID 34144를 재기동했으며 기존 M3 합성 DB는 보존한다.
- 현재 제품 코드 외 변경은 보존한다.
- M2 FE 제품 소스는 머지 시점 기준으로 동결했고, M3 변경은 승인된 카테고리 계약 범위에서 진행한다. `SettingsPostsPage`의 blog/operation 세대 guard·실제 loaded blog ID 경계·reload 확인, setup 관리 복구 경로·새로고침 가능한 SetupGuard history state handoff, 공개 패널의 blog/viewer 전환·미확인 count 표시를 보강했다.
- 최신 수정: `BlogInitialSetupPage`가 `/blog/setup`에 `setupCompletionTo: 'blog'` intent를 먼저 커밋한 뒤 location effect에서만 `refreshUser()`를 시작하고, `SetupGuard`가 완료 세션의 `defaultBlog.urlSlug`로 자기 블로그 목적지를 계산한다. 타이머/`flushSync` 실험은 제품에서 제거했다. completion intent 후 refreshUser 실패→recovery/관리 재시도 회귀를 추가했고, 시각·DnD 보완 후 관련 2 files/27 tests와 lint/build exit 0을 확인했다. 리더는 1440px에서 signup/setup/self blog/reload, 루트·하위 keyboard, rename·duplicate·재조회 및 시각 대조를 확인했다.
- 2026-09-08 상태 감사: 제품 source/test tree는 `4fc9ae2` 이후 변경이 없고, 기존 FE 기준 `23 files/273 tests`, lint/build exit 0이다. 직접 의존성 17개는 `package.json`·lockfile·설치 manifest가 exact match이며 Vite 실행 버전은 `8.1.5`; `npm ls --depth=0`도 exit 0이다(보고된 extraneous 6개는 정리하지 않음).
- native mouse DnD gate: root가 frontend 구현자와 분리된 Chrome에서 합성 블로그의 실제 pointer drag(`[515,281]`→`[515,223]`) 후 AX 순서 `미분류/프론트엔드/백엔드/회고`, 성공 notice `카테고리 순서를 저장했습니다.`, DB active ID/order `19/0, 21/1, 20/2, 22/3`을 확인해 PASS로 승격했다. viewport override 상태는 browser zoom 90%로 `innerWidth=1600`이므로 이번 관측을 1440 CSS px 실측으로 주장하지 않으며 기존 1440px 시각 검증은 유지한다.
- 과거 Chrome LOCKED gate 기록: `회고`를 LOCKED로 선택한 뒤 경고·AX confirm·OK focus는 확인했으나 dialog accept timeout 후 세션을 reset했고, 당시 회고 id `22`는 GENERAL이었다. 이는 새 IAB 실측과 구분되는 과거 사실이다.
- LOCKED gate 해소: root가 frontend 구현자와 분리된 인앱 브라우저에서 새 합성 blog ID `7`/category ID `26`의 `회고`를 LOCKED로 저장하고, notice `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.`, order `3`, `회고 0개의 글 LOCKED`를 확인했다. full reload 뒤에도 LOCKED/order 3와 순서 이동·이름 변경·삭제·타입 선택 4개 조작의 disabled 상태를 재확인했다. 이는 root 독립 UI·DB evidence이며 QA 직접 조작으로 표기하지 않는다. 이로써 제품 UI gate는 해소됐고 M4는 시작하지 않는다.

## 다음 작업

1. 독립 QA의 최종 acceptance 판정 후 리더가 M3 최종 diff·계약·PR #9를 `dev`에 반영하고 마감 기록을 남긴다.
2. M4 착수 전까지 FE는 M3 산출물을 유지하며 새 API·관계 CRUD를 추가하지 않는다.

## 차단 요인

- M3 FE 구현·관련 테스트·lint/build, native mouse DnD 저장, IAB LOCKED 저장·reload·disabled gate는 완료됐다. 리더의 1440px signup/setup/self blog/reload, 루트·하위 keyboard, rename·duplicate·재조회·시각 대조는 유지하되 이번 browser zoom 90% 관측은 1440 CSS px로 세지 않는다. 남은 차단은 독립 QA 최종 acceptance와 PR #9 `dev` merge·마감이며, FE는 확정 API 형태를 유지한다.
- 운영 HTTPS refresh-cookie smoke는 배포 전 `RISK-0005` 후속 게이트로 유지한다.

## 주요 산출물

- `frontend/src/**`
- `frontend/package.json`, `frontend/package-lock.json`

## M3 FE 인계 메모

- 선행: M2 PR #8 머지(`4c129e20f58a6ccb9c61246d103934702516c295`)와 M3 Q1~Q4 사용자 승인은 완료됐다. 회의록 `APPROVED`, ADR-0005 `ACCEPTED` 및 BE 계약 검증 결과를 기준으로 구현한다.
- 현재 산출물: `SettingsPostsPage`는 category API 기반 단일 관리 카드와 blog/operation stale guard, `routes/router.tsx`/`guards.tsx`는 setup recovery state를 포함한 기존 `/settings/posts` 보호 라우트, `ScreenPanel`은 공개 category tree/count와 M4 미제공 통계를 viewer identity별로 렌더한다.
- 구현 정본: `DEFAULT/GENERAL/LOCKED`, 루트+1단계, DEFAULT/LOCKED 불변, soft delete/cascade·글의 DEFAULT 이동, 전체 order 배열 계약과 1440px 디자인을 반영한다.
