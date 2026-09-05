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
