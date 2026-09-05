# ZeroVerse

개인 블로그 플랫폼 ZeroVerse의 Spring Boot + React MVP 저장소다.

## 시작 전 필독

1. `.claude/CONSTITUTION.md` — 모든 에이전트에 우선하는 팀 헌법
2. `AGENTS.md` — 프로젝트 정본, 마일스톤, 설계·테스트 규칙
3. 팀원이라면 `.claude/team/{역할}/CLAUDE.md` → `STATE.md`

Claude와 Codex는 역할별 `STATE.md`·`WORKLOG.md`와 통합 `JOURNAL.md`를 공동 사용한다. Claude 역할 정의는 `.claude/agents/`, Codex 역할 정의는 `.codex/agents/`에 있다. 운영 상세는 `.claude/team/README.md`, 사용자용 실행법은 `docs/에이전트-팀-운영-가이드.md`와 `docs/Codex-에이전트-팀-운영-가이드.md`를 따른다.

완료 보고에는 편집한 파일의 재독, 관련 테스트·빌드·검색 명령과 실제 출력이 필요하다. 사용자가 명시하지 않은 commit, branch 변경, merge, push는 하지 않는다.
