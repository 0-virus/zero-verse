# frontend/src

## Purpose

ZeroVerse Blog MVP 프론트엔드. React 19 + TypeScript + Vite + React Router(SPA) + TailwindCSS v4. **MVP 데스크톱 전용**.

## 소스 오브 트루스

**`docs/design/`가 시각의 최종 권위다.** 색·그림자·타이포·간격·패널 배치·버튼 라벨이 다른 문서와 충돌하면 무조건 디자인이 이긴다(PRD §9.0). 정확한 값은 `docs/design/DESIGN-SYSTEM.md`와 `.dc.html` 원본의 인라인 `style`.

## 절대 규칙

- **`border-radius: 0` — 예외 없음.** 전역 리셋이 `styles/index.css`에 있다.
- **`min-width: 1440px`** 데스크톱 전용(PRD §9-I). 반응형 분기를 만들지 않는다.
- **`Press Start 2P`는 로고와 짧은 영문 대문자 라벨 전용 — 한글에 절대 쓰지 않는다.** 그 외 전부 `IBM Plex Sans KR`.
- 보더는 `3px solid #2b1b3d`(주) / `2px`(보조), 그림자는 blur 0 우하단 오프셋 `#d8c7b0`.
- 우주 그라디언트·픽셀 별(`steps(2)`)·픽셀 로켓은 **다크 영역(히어로·온보딩)에만**.
- **폐기**: 다크 네온 팔레트(`#02020b`/`#22d3ee`/`#a855f7`/`#fb7185`), 골드 `#fde047` 관리자 강조, 상단바 관리자 버튼, 사이드바 Admin 항목.
- 공개범위 enum은 `UNIVERSE`이나 **화면 표기는 "친구"**(PRD §9-B). `Badge` 컴포넌트가 이 매핑을 담당한다.

## 구조

```text
src/
├─ main.tsx, App.tsx
├─ routes/router.tsx        # 15개 라우트 + not-found
├─ pages/                   # 라우트별 화면
├─ components/layout/       # AppShell, TopBar, SideNav
├─ components/ui/           # 공용 컴포넌트 14종
├─ features/ lib/ types/    # 도메인 로직·API 클라이언트·타입 (M1 이후)
├─ styles/index.css         # @theme 디자인 토큰
└─ test/                    # Vitest + RTL
```

## 레이아웃 규칙

- **앱 셸 = 상단바.** 전 화면 공통(PRD §9-L).
- **`SideNav`는 `/`에서만** 렌더한다(PRD §6.7). 블로그·설정의 240px 패널은 별도 화면 전용 컴포넌트로 만든다.
- 온보딩(`/signin`, `/signup`, `/blog/setup`)은 다크 레이아웃이며 사이드바가 없다.

## M0 범위와 이후

M0는 **골격까지만**이다 — 디자인 토큰, 라우터 경계, AppShell, 공용 컴포넌트. 페이지는 제목·레이아웃·빈 상태만 렌더하며 **샘플 사용자나 가짜 API 데이터를 넣지 않는다**.

- AuthContext, apiClient(401 자동 갱신), ProtectedRoute/GuestOnlyRoute/SetupGuard/AdminRoute → **M1**
- TipTap 에디터, 이미지 업로드 → **M4**

## 테스트

Vitest + RTL + jsdom. `npx vitest run`. `skip`, snapshot만으로 끝나는 테스트, 가짜 성공 응답은 금지한다(PRD §11~§12).

CSS 픽셀 일치는 jsdom으로 보증되지 않는다. `/`, `/signin`, `/settings`를 개발 서버에서 **1440px 기준으로 정본과 육안 대조**한다(PRD §12.4).
