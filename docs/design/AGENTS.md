# docs/design

## Purpose

ZeroVerse 프론트엔드의 **시각 디자인 정본**. Claude Design 프로젝트 "우주 블로그 웹사이트 레이아웃"(`f1af0ea2-421d-44aa-9871-4273726f53fe`)에서 가져온 원본과, 거기서 추출한 디자인 시스템 문서가 들어있다.

**이 폴더의 디자인이 시각·레이아웃·카피에 관한 한 다른 모든 문서를 override한다.** 폐기된 Figma 와이어프레임(`GdWn01gB35uT1mebKXGIx6`)의 다크 네온 토큰은 더 이상 사용하지 않는다.

## Key Files

| File | Description |
|------|--------------|
| `DESIGN-SYSTEM.md` | **먼저 읽는다.** 색·그림자·타이포·레이아웃 토큰 + 컴포넌트 레시피 + 11개 화면 스펙. |
| `ZeroVerse Pages.dc.html` | 구현 대상 원본. 12화면(블로그·게시글·글쓰기·로그인·초기설정·설정 3종·검색·알림·관리자 2종). |
| `ZeroVerse Main Feed v2.dc.html` | `/` 메인 피드 원본. Pages에 없는 유일한 화면. |
| `support.js` | `.dc.html` 렌더링용 Design Canvas 런타임(생성물). **편집 금지.** |

## For AI Agents

- 정확한 값이 필요하면 `DESIGN-SYSTEM.md`를 먼저 보고, 부족하면 해당 `.dc.html`의 **인라인 `style` 속성**을 직접 읽는다. 요약본보다 원본이 우선이다.
- `.dc.html`은 Design Canvas 템플릿 문법을 쓴다 — `<x-dc>` 루트, `<sc-if value="{{ x }}">` 조건, `<sc-for list="{{ xs }}" as="i">` 반복, `{{ }}` 바인딩, `style-hover` 속성(hover 스타일), 하단 `<script type="text/x-dc">`의 `class Component extends DCLogic { renderVals() {...} }`가 데이터/상태를 공급한다. `hint-placeholder-count`/`hint-placeholder-val`은 편집기 미리보기용 힌트이며 스펙이 아니다.
- `ZeroVerse Pages.dc.html` 최상단의 **SCREEN SWITCHER 스트립은 리뷰 도구**다. 구현 대상이 아니다.
- `Main Feed v2`의 `data-props`에 선언된 값(히어로 변형·보더 두께·그림자 오프셋·밀도 등)은 **선언된 `default`가 확정 스펙**이다. 코드의 `?? 값` 폴백이 아니라 `data-props`의 `default`를 따른다(예: `heroVariant: dusk`, `heroHeight: 240`, `borderWidth: 3`, `shadowOffset: 6`, `shadowColor: #d8c7b0`, `showClouds: false`, `paperGrain: false`).
- `.dc.html`을 React로 그대로 옮기지 않는다. 인라인 스타일은 참조값일 뿐이고, 구현은 TailwindCSS v4 유틸리티 + `@theme` 토큰으로 다시 작성한다.
- **디자인에 없는 요구사항 필드/규칙은 디자인이 부정한 것이 아니다.** 접근제어·카테고리 타입·필수 입력 등 데이터 규칙은 `../REQUIREMENTS.md`가 계속 권위를 가진다. 시각 표현만 이 폴더를 따른다. 둘이 정면충돌하면 `../PRD.md` §9 표에 기록하고 사용자에게 확인한다.
- **미리보기**: `ZeroVerse Pages.dc.html`을 브라우저에서 그냥 열면 된다(같은 폴더의 `support.js`가 상대경로로 로드됨). `support.js`가 React 18.3.1 / ReactDOM / Babel standalone을 **unpkg CDN에서 받아오므로 인터넷 연결이 필요**하다. 오프라인이면 빈 화면이 뜨고 콘솔에 `dc-runtime: window.React is not available yet`이 남는다.
- 원본을 다시 받거나 갱신하려면 `DesignSync`(claude_design MCP)의 `list_files`/`get_file`에 위 projectId를 쓴다. 이 폴더의 파일을 손으로 고쳐서 원본과 어긋나게 만들지 않는다.
- 디자인 원본과 `DESIGN-SYSTEM.md`는 `docs/*` 규칙으로 **git 추적 제외**이며 로컬에만 존재한다. 이 `AGENTS.md`만 에이전트 지침으로 추적한다.
