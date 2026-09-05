# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-06 05:42 KST

## 현재 단계

- 기준 브랜치: `dev` (PR #8 `feature/M2-settings` 머지 완료, merge SHA `4c129e20f58a6ccb9c61246d103934702516c295`, 2026-09-06 05:41:12 KST).
- M0 스캐폴딩과 M1 인증은 `dev` 머지 기록이 있다.
- M2 설정·블로그 화면은 구현·다회 리뷰·QA 최종 승인 후 PR #8로 `dev`에 머지됐다.
- FE 자동 검증과 리더의 1440px 실제 브라우저·실제 API smoke가 통과했고, 리더 확인 기준 BE 348개와 QA M2 최종 APPROVE96도 완료됐다.

## 진행 중

- `ScreenPanel` 설정 메뉴 카피를 정본(`프로필 · 계정`/`카테고리 관리`)에 맞춰 교정하고 회귀 기대값을 갱신했다.
- `SettingsProfilePage` 아바타를 정본 90px/#ffe9c9/42px로 맞추고 URL 이미지·이모지 fallback, `변경` 포커스/스크롤, intrinsic 저장 버튼을 연결했다.
- `BlogPage`가 `ApiRequestError.status===404`만 전용 문구로 표시하고, 500/네트워크 오류에는 재시도 UI를 제공하도록 보강했다.
- `AppRoutes`의 기존 `useAuth()` 사용자 상태를 `AppShell` props로 전달해 `TopBar` 닉네임, `SideNav` 기본 블로그 slug, `RightPanel` 내 블로그 정보를 같은 공급원으로 연결했다. 비로그인 `TopBar`는 `로그인`/`/signin`, `SideNav`의 My Blog는 `/signin`으로 안전하게 연결한다. 선택적 인증 훅은 추가하지 않았다.
- `npm test` 21 files/250 tests, `npm run lint`, `npm run build`를 최신 소스에서 통과했다.
- 늦은 slug 성공·404 오류 모두 실제 응답 본문 파싱과 React continuation 이후 stale guard를 검증했다. BLOG_004의 `/blog/already-done` pathname도 단정한다. 부모가 05:26 `build/m2-stale-mutation.mjs`(Vitest/Vite 메모리 변환)로 두 mutation을 expected failure(exit 1)로 확인했고, 원본 `BlogPage.tsx` SHA256 전후 동일 및 이후 원본 전체 250 통과를 확인했다.
- Vite 개발 서버를 `http://127.0.0.1:5173/`에 hidden으로 기동했다(launcher PID 29744, intermediate PID 20908, listener PID 16332; HTTP 200, 866 bytes).
- 현재 제품 코드 외 변경은 보존한다.
- M2 FE 제품 소스는 머지 시점 기준으로 동결했다. M3 사용자 결정 전 추가 수정은 하지 않는다.

## 다음 작업

1. M3 Q1~Q4 사용자 결정과 계약 승인을 기다린다(`USER_DECISION_REQUIRED`).
2. 승인 후 기존 `SettingsPostsPage`/`/settings/posts` 라우트와 `ScreenPanel` 카테고리 패널을 실제 카테고리 API·트리·정렬 UI로 확장한다.

## 차단 요인

- M3 Q1~Q4 사용자 승인 전에는 카테고리 FE 작업을 시작하지 않는다(`USER_DECISION_REQUIRED`).
- 운영 HTTPS refresh-cookie smoke는 배포 전 `RISK-0005` 후속 게이트로 유지한다.

## 주요 산출물

- `frontend/src/**`
- `frontend/package.json`, `frontend/package-lock.json`

## M3 FE 인계 메모

- 선행: M2 PR #8 머지(`4c129e20f58a6ccb9c61246d103934702516c295`)는 완료됐다. 이제 Q1~Q4 카테고리 계약 승인과 사용자 결정이 필요하며, M3 시작 전 `docs/governance/meetings/M3-20260906-categories.md`의 미확정 결정을 확인한다.
- 재사용 자산: `SettingsPostsPage`는 현재 `PageScaffold` placeholder, `routes/router.tsx`는 `/settings/posts` 보호 라우트, `ScreenPanel`은 카테고리·통계 패널 경계만 렌더한다.
- 구현 시 정본: `DEFAULT/GENERAL/LOCKED`, 루트+1단계, DEFAULT/LOCKED 불변, soft delete/cascade·글의 DEFAULT 이동, 전체 order 배열 계약과 1440px 디자인을 백엔드 API 확정 후 반영한다.
