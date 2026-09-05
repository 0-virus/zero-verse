<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-01 | Updated: 2026-09-06 -->

# docs

## Purpose

ZeroVerse Blog MVP의 요구사항·설계·디자인·계획 문서 모음. 스펙 원본은 `.gitignore`로 추적 제외되며 로컬에만 존재한다. `worklog/`, `governance/`, Claude·Codex 에이전트 팀 운영 가이드는 Git 추적 대상이다. 관련 구현 전에 반드시 정본 문서를 읽는다.

## Key Files

| File | Description |
|------|--------------|
| `REQUIREMENTS.md` | MVP 요구사항 명세 v2.1 — 도메인 모델, API 계약, FR-*/NFR-*, 프론트 라우트, 엔드포인트 목록. **데이터·규칙 정본**(단, `PRD.md` §9 사용자 결정이 override하는 부분 제외). |
| `PRD.md` | 요구사항 + 디자인 정본 + 사용자 결정을 통합한 구현 실행 명세(v2.0). 아키텍처, 화면-API 매핑, 디자인 토큰(§6), 화면 명세(§7), 결정 로그(§9), 마일스톤(§10), 테스트/DoD. 구현 로드맵. |
| `design/` | **시각 디자인 정본**. Claude Design 원본 `.dc.html` + `DESIGN-SYSTEM.md`. 프론트엔드 작업 전 필독. `design/AGENTS.md` 참고. |
| `log.md` | 과거 개발 학습/작업 로그(JPA·Security·JWT 메모). 현재 소스와 동기화 보장 없음(참고용). |
| `에이전트-팀-운영-가이드.md` | Claude 역할 팀 실행·상태·검증 방법. |
| `Codex-에이전트-팀-운영-가이드.md` | Codex custom agent와 공동 STATE·WORKLOG 운영 방법. |

## For AI Agents

- 우선순위: **사용자 결정(PRD §9) > `design/`(시각·레이아웃·카피) > REQUIREMENTS.md(데이터·규칙) > PRD 본문**. 상세는 루트 `../AGENTS.md`의 "소스 오브 트루스 우선순위" 참조.
- **2026-07-24 디자인 소스 교체**: Figma 와이어프레임(`GdWn01gB35uT1mebKXGIx6`)과 다크 네온 팔레트는 폐기. `design/`의 크림 페이퍼 팔레트가 정본이며, 충돌 시 **무조건 디자인이 이긴다**(PRD §9.0).
- 디자인에 안 그려진 요구사항 필드를 그 이유만으로 삭제하지 않는다. 데이터 축 충돌은 PRD **§9.3 확인 대기 항목**에 모아두었다.
- 요구사항이 갱신되면 `PRD.md`의 대응 섹션과 §9 결정 로그를 함께 업데이트한다.
- `PRD.md` §13.1: S3/이미지 업로드는 로컬 **LocalStack** + **S3 직접 URL**로 확정. 실제 버킷·리전 값만 M4 착수 시 제공 필요.

<!-- MANUAL: -->
