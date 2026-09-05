# ZeroVerse 에이전트 팀 운영

Claude와 Codex가 같은 역할 지침·상태·이력을 공유한다.

## 계층

```text
.claude/CONSTITUTION.md
  └─ .claude/team/{role}/CLAUDE.md
       ├─ STATE.md       현재 스냅샷(덮어쓰기)
       └─ WORKLOG.md     시간순 기록(append-only)
.claude/team/JOURNAL.md  역할을 가로지르는 리더 기록(append-only)
```

충돌 시 헌법이 우선한다. 제품·기술 정본은 `AGENTS.md`의 소스 오브 트루스 순서를 따른다.

## 역할과 소유권

| 역할 | 책임 | 주요 소유 경로 |
| --- | --- | --- |
| backend | Spring Boot, DB, 인증·인가, API 구현 | `src/**`, Gradle 파일 |
| frontend | React, 라우팅, UI/UX, API client | `frontend/**` |
| qa | 독립 검증, 보안, 계약·추적성 | `qa/**` |
| pm | 제품 정합성, 범위, 결정 요청 종합 | `docs/PRD.md`, `docs/PM-*.md` |

정확한 경계는 헌법 제1조가 정본이다. `docs/design/**`는 읽기 전용 시각 정본이고, `docs/REQUIREMENTS.md`, `docs/worklog/**`, `docs/governance/**`는 리더가 조정한다.

독립 QA 기록은 `qa/AGENTS.md`에 따라 `qa/`에 보관한다. PM의 `docs/PM-*.md`는 로컬 준비 자료이며 최종 승인·마일스톤 판정은 기존 governance/worklog 정본을 대체하지 않는다.

## 실행 원칙

- 단일 역할의 작은 작업은 주 에이전트가 직접 처리할 수 있다.
- 두 역할 이상에 걸리면 해당 custom agent에 나누고 독립 작업만 병렬 실행한다.
- backend·frontend·qa가 기본 구성이다. pm은 종합·결정 요청이 있을 때만 실행한다.
- 2026-09-06 사용자 승인: Codex backend/frontend가 구현하고 별도 컨텍스트의 QA·리더가 최종 검토한다. 마일스톤 완료 후 다음 마일스톤으로 계속 진행한다. 새로운 개별 승인 안건은 독립 작업을 진행하면서 요청한다. 승인 대기 중인 안건 자체는 준비·독립 검토만 진행하며 구현하지 않는다.
- 같은 파일을 둘 이상에게 맡기지 않는다. 선행 산출물 의존 작업은 순차 실행한다.
- 각 팀원에게 헌법 → 역할 지침 → 역할 상태 → 작업 정본 경로 순으로 읽게 한다.
- 타 역할 변경 필요는 직접 처리하지 않고 리더에게 정확한 경로·제안·근거로 보고한다.
- 의미 있는 작업은 `STATE.md` 갱신과 `WORKLOG.md` append까지 완료해야 끝난다.
- 세션 재개 시 새 역할 에이전트가 공통 상태 파일에서 이어받는다.

## 완료 보고 규약

근거 없는 완료 보고는 접수하지 않는다.

1. 편집한 파일을 다시 열어 실제 저장 내용을 확인한다.
2. 문서·설정은 변경 문구와 옛 참조를 `rg`로 검색한다.
3. 코드는 관련 테스트·타입체크·빌드와 요청 동작을 확인한다.
4. 테스트 실패나 미실행은 그대로 적고, 부분 완료는 남은 범위와 이유를 적는다.
5. 데이터 공급원이 없어 영원히 비는 UI, 사용되지 않는 타입·필드, 계약에 없는 값 생성 같은 구조적 공백을 확인한다.
6. 리더는 중요한 산출물과 출력의 핵심을 직접 재검증하고 결과를 `JOURNAL.md`에 남긴다.

## 기존 마일스톤·심의 기록

`docs/worklog/**`와 `docs/governance/**`는 이 팀 체계 도입 전후를 잇는 프로젝트 정본이다. 이동·요약·초기화하지 않는다. 새 역할 로그는 누가 무엇을 했는지 기록하고, 마일스톤 로그는 계획→개발→리뷰→머지 흐름을 계속 기록한다.

## Claude/Codex 대응

| 목적 | Claude | Codex | 공동 정본 |
| --- | --- | --- | --- |
| 저장소 진입점 | `CLAUDE.md` | `AGENTS.md` | 헌법·프로젝트 정본 |
| 역할 정의 | `.claude/agents/*.md` | `.codex/agents/*.toml` | 역할 `CLAUDE.md` |
| 브리핑 | `/brief` | `$brief` | 역할 `STATE.md`·`WORKLOG.md`, `JOURNAL.md` |

실행기별 상세는 `docs/에이전트-팀-운영-가이드.md`와 `docs/Codex-에이전트-팀-운영-가이드.md`를 본다.
