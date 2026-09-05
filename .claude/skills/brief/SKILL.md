---
name: brief
description: 리더가 ZeroVerse의 backend, frontend, qa, pm STATE와 마일스톤·심의 기록을 Git 현실과 교차 대조해 현재 위치와 다음 행동을 보고한다. 읽기 전용이며 팀원을 스폰하지 않는다.
trigger: /brief
---

# /brief

리더가 직접 수행하는 읽기 전용 절차다. 팀원을 스폰하거나 파일을 수정하지 않는다.

.agents/skills/brief/SKILL.md와 같은 판정 절차를 쓴다.

1. git branch --show-current, git status --short, git log --oneline -10.
2. 대상 역할의 CLAUDE.md → STATE.md → 최근 WORKLOG.md.
3. 전원 브리핑이면 헌법, 통합 JOURNAL, 현재 마일스톤 worklog, 결정·위험 레지스터, PRD §9·§10.
4. 역할별 즉시 작업, 차단 주체, STATE/현실 차이, 역할 의존, 우선순위 하나.
5. 역할 간 의존 사슬·계약 모순·사용자 결정 묶음.
6. 실제 파일과 출력으로 검증한 최우선 다음 행동 하나를 권고한다.

STATE나 commit 메시지를 완료 근거로 단독 사용하지 않는다. worklog [머지] 본문, 실제 산출물, 테스트·빌드 결과를 대조한다. 깊은 조사나 구현은 별도 팀 작업으로 분리한다.
