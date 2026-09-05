# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-06 KST (팀 운영 도입)

## 현재 단계

- 기준 브랜치: `feature/M2-settings`.
- M0 스캐폴딩과 M1 인증은 `dev` 머지 기록이 있다.
- M2 설정·블로그 화면은 구현과 다회 리뷰 후 아직 미머지다.

## 진행 중

- AppShell, Hero, 공개 BlogPage와 settings behavior 테스트의 리뷰 반영 변경이 워킹트리에 있다.
- 사용자 작업 중 변경은 보존한다.

## 다음 작업

1. M2 최신 리뷰 지적과 현재 frontend diff를 대조한다.
2. 관련 테스트, 전체 frontend 테스트, lint, build와 1440px 시각 대조를 실행한다.
3. 결과를 M2 worklog와 이 역할 로그에 기록한다.

## 차단 요인

- M2 최종 검증과 머지가 완료되지 않았다.
- 운영 HTTPS refresh-cookie smoke는 `RISK-0005` 후속 게이트다.

## 주요 산출물

- `frontend/src/**`
- `frontend/package.json`, `frontend/package-lock.json`
