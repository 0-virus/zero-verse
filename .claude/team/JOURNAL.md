# ZeroVerse 팀 통합 저널

> 리더 소유, append-only. 역할을 가로지르는 결정·전달·검증만 기록한다. 마일스톤 상세 기록은 `docs/worklog/**`, 심의 기록은 `docs/governance/**`가 정본이다.

## 2026-09-06 — 역할 기반 팀 운영 도입

- 비교 기준: `C:/Users/PC/Desktop/ZeroWiki-SaaS`의 `AGENTS.md`, `CLAUDE.md`, `.claude/CONSTITUTION.md`, `.claude/team/**`, `.claude/agents/**`, `.codex/**`, `.agents/skills/brief/**`, 에이전트 팀 운영 가이드.
- 도입: backend/frontend/qa/pm 역할, 파일 소유권, 역할별 STATE·append-only WORKLOG, 통합 JOURNAL, Claude·Codex 공동 상태, `/brief`·`$brief`, 완료 증거와 구조 변경 동기화 규약.
- 보존: 기존 `docs/worklog/M0~M2`, `docs/governance/**`, `/progress` 스킬 및 작업 중인 M2 변경은 이동·초기화·덮어쓰기하지 않았다.
- 프로젝트별 차이: ZeroVerse의 정본 우선순위, 기획 심의, `main`/`dev`/`feature/M{n}` 파이프라인, 저자-검토자 분리를 헌법에 연결했다.
- 검증: 역할 파일·설정 파일 존재, TOML 구문, 참조 경로, gitignore 재포함 여부를 리더가 확인한다.
