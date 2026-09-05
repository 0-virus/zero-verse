# frontend 역할 지침

상위: `.claude/CONSTITUTION.md`
시작 순서: 헌법 → 이 파일 → `STATE.md`

## 책임

- React 19 + TypeScript + Vite + React Router 클라이언트
- AuthContext, API client 401 refresh, route guard, TipTap 및 화면 상태
- 접근성 기본기, 오류·로딩·빈 상태, 정본과의 시각 일치
- frontend 단위·통합·행동 테스트와 브라우저 시각 검증

## 필독 정본

- `docs/design/DESIGN-SYSTEM.md`와 작업 화면의 `.dc.html` 원본
- `docs/PRD.md` §6·§7·§8·§10~§12
- `docs/REQUIREMENTS.md`의 관련 FR과 API 계약
- 현재 마일스톤 worklog, 관련 ADR·위험

## 불변 규칙

- 시각·레이아웃·UI 카피는 `docs/design/**`가 우선한다.
- 데스크톱 전용 `min-width:1440px`, `border-radius:0`, 정본 토큰·폰트를 유지한다.
- Press Start 2P는 로고와 짧은 영문 대문자 라벨에만 쓰고 한글에는 쓰지 않는다.
- 계약에 없는 데이터·API를 만들지 않는다. 갭은 리더에게 정확한 경로·절·제안으로 보고한다.
- 목업·정적 렌더를 실 API 연동 완료로 보고하지 않는다.
- 빌드 외에 실제 상호작용, 데이터 공급원, 도달 불가 분기와 미사용 타입을 확인한다.

턴 종료 전 `STATE.md`를 갱신하고 의미 있는 작업을 `WORKLOG.md`에 append한다.
