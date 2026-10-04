# ZeroVerse

개인 블로그 플랫폼 ZeroVerse의 Spring Boot + React MVP 저장소다.

로컬 테스트 서버와 웹 화면 실행 방법은 리더 소유의 루트 `README.md`를 따른다.

## 시작 전 필독

1. `.claude/CONSTITUTION.md` — 모든 에이전트에 우선하는 팀 헌법
2. `AGENTS.md` — 프로젝트 정본, 마일스톤, 설계·테스트 규칙
3. 팀원이라면 `.claude/team/{역할}/CLAUDE.md` → `STATE.md`

Claude와 Codex는 역할별 `STATE.md`·`WORKLOG.md`와 통합 `JOURNAL.md`를 공동 사용한다. Claude 역할 정의는 `.claude/agents/`, Codex 역할 정의는 `.codex/agents/`에 있다. 운영 상세는 `.claude/team/README.md`, 사용자용 실행법은 `docs/에이전트-팀-운영-가이드.md`와 `docs/Codex-에이전트-팀-운영-가이드.md`를 따른다.

완료 보고에는 편집한 파일의 재독, 관련 테스트·빌드·검색 명령과 실제 출력이 필요하다. 사용자가 명시하지 않은 commit, branch 변경, merge, push는 하지 않는다.

2026-09-06 사용자 승인에 따라 현재 연속 개발은 Codex backend/frontend 구현과 독립 QA·리더 검토로 진행한다. 마일스톤 종료 후 다음 단계로 이어가며 실행·Git 배정은 `AGENTS.md`의 최신 파이프라인과 리더 JOURNAL을 따른다.

2026-10-03 사용자는 로컬 파일 저장 기반 M4 실행계획을 승인했다. 현재 범위는 M4까지이며 S3 연동은 후속이다. LocalStack/U0는 제품 구현의 선행 gate가 아니다. 구체 계약·역할 배정·실측은 `docs/worklog/M4-posts.md`와 회의 §20.8, PRD §9.7을 따른다. `.local-data/` 영속 파일은 Git 제외하며 임의 삭제하지 않는다.
