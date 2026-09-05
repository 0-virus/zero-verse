# ZeroVerse Codex 에이전트 팀 운영 가이드

이 문서는 Claude 팀과 같은 역할·소유권·상태 기록을 Codex custom agents로 운영하는 방법이다.

## 1. 공동 정본

| 목적 | Claude | Codex | 공동 정본 |
| --- | --- | --- | --- |
| 진입점 | CLAUDE.md | AGENTS.md | 헌법과 프로젝트 정본 |
| 역할 정의 | .claude/agents/*.md | .codex/agents/*.toml | 역할 CLAUDE.md |
| 상태·이력 | 역할 STATE·WORKLOG | 같은 파일 직접 사용 | .claude/team/** |
| 통합 이력 | JOURNAL | 같은 파일 직접 사용 | .claude/team/JOURNAL.md |
| 브리핑 | /brief | $brief | 같은 판정 절차 |

Codex 전용 STATE나 WORKLOG를 만들지 않는다.

## 2. 시작 예시

    $brief를 사용해 backend, frontend, qa, pm의 상태와 최우선 다음 행동을 보고해.

    ZeroVerse 에이전트 팀으로 이 작업을 수행해. backend, frontend, qa에 소유 영역이
    겹치지 않게 배정하고 독립 작업만 병렬 실행해. 모든 결과를 기다린 뒤 실제 파일과
    검증 출력을 다시 확인해 종합해: <작업>

    zeroverse-backend custom agent에게 <작업>을 맡겨. 헌법, 역할 지침, STATE와
    관련 정본 절을 먼저 읽게 하고 검증 근거까지 받은 뒤 직접 확인해.

## 3. 역할 실행 순서

1. .claude/CONSTITUTION.md
2. AGENTS.md
3. .claude/team/{역할}/CLAUDE.md
4. .claude/team/{역할}/STATE.md
5. 관련 REQUIREMENTS·PRD·design·ADR·마일스톤 worklog
6. 과거 근거가 필요할 때만 역할 WORKLOG와 JOURNAL

작업 후에는 소유 파일만 변경하고 재독·검증한 뒤 STATE를 갱신하고 WORKLOG에 append한다.

## 4. 분할 기준

- 단일 역할의 작은 작업: 주 에이전트 직접 수행 가능
- 두 역할 이상: 역할별 custom agent로 분할
- 독립 조사·검증: 병렬
- 같은 파일·선행 의존: 순차
- pm: 종합이나 사용자 결정 요청이 있을 때만

.codex/config.toml의 동시 thread 상한은 4다. 환경의 상위 제한이 더 작으면 역할을 순차 실행한다.

## 5. 전달과 검증

Codex에서는 주 에이전트가 역할 간 전달 허브다. 타 역할 파일 변경 요청을 해당 소유자에게 후속 작업으로 보낸다. 주 에이전트는 모든 thread 결과를 기다린 뒤 파일과 핵심 명령 출력을 직접 재검증한다.

문서·설정은 내용 검색, 코드는 관련 테스트·빌드·동작, 이름 변경은 옛 참조 전역 검색으로 검증한다. 사용자 승인 없는 Git 작업과 외부 상태 변경은 하지 않는다.

## 6. 파일별 책임

| 파일 | 책임 |
| --- | --- |
| AGENTS.md | Codex 저장소 전체 지침 |
| .codex/config.toml | custom agent 활성화·모델·동시 실행 상한 |
| .codex/agents/zeroverse-*.toml | 네 역할 정의 |
| .agents/skills/brief/SKILL.md | 읽기 전용 현재 상태 종합 |
| .agents/skills/brief/agents/openai.yaml | $brief UI 정보 |

## 7. 빠른 자체 점검

    Get-Content -Raw -Encoding UTF8 AGENTS.md
    Get-Content -Raw -Encoding UTF8 .codex/config.toml
    Get-ChildItem .codex/agents/*.toml | Select-Object Name
    Get-Content -Raw -Encoding UTF8 .agents/skills/brief/SKILL.md

새 세션에서 $brief와 zeroverse-backend, zeroverse-frontend, zeroverse-qa, zeroverse-pm이 발견되면 구성이 완료된 것이다.
