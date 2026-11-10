# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-11-10 13:45 KST

## 현재 단계

- M0~M5는 완료·`dev` 통합됐고, M6 댓글·좋아요와 M7 피드·검색도 구현·자동검증·독립 QA·실제 HTTP/Chrome 1440px 인수·`dev` 통합까지 완료됐다.
- M7 최종 통합은 PR #13 merge SHA `08863207b8385177b619091410bbbd43e52a337c` 및 통합 tree `dc68037477c6fed6b77d537235d79093803a983e`로 끝났고, root docs-only closeout 커밋 `b80ede08c3e54ff3f3d6742b58c401d869574331`이 이미 `dev`/`origin/dev`에 반영됐다. 현재 docs-only closeout 완료 상태다.
- 현재 단계는 **M8 사용자 제품 정책 9개·§9 A-v2 전체 승인·독립심의 `APPROVE_WITH_CHANGES/HIGH` 완료·Task1~6 구현/독립 QA/실제 인수/`dev` 통합 실행 중**이다. 기준 branch는 `feature/M8-notifications`이며, 제품 구현·검증 완료는 아직 주장하지 않는다.

## 진행 중

- `docs/PRD.md`에 §9.11 M8 제품 정책과 §5.11·§7.2·§10·§13.4 정합화를 반영했다. 전체 이력(디자인 30일 문구 override), 답글 `COMMENT`/`REPLY` 이중수신 및 동일 수신자 `REPLY` 우선 dedupe, 실제 재행동 재알림·동일요청 비중복, eligible 발행 전이, 원인 취소 후 이력 보존, 권한상실 generic/차단 숨김, 클릭 읽음·전체 범위 read-all·공유 unread·최근 3 all, 주기 자동 갱신 없이 진입/open/복귀 갱신·소급 없음만 확정했다. 읽음·삭제 뒤 서버 재조회로 자체 상태를 동기화하는 동작은 유지한다.
- M8 회의 상태는 `APPROVED`다. 사용자 `A-v2 전체 승인`으로 개별 read의 현재 권한 재평가 canonical `NotificationItem`, `SUSPENDED` GET read-only, dynamic unavailable masking·권한 복구 재평가, 원행위·알림 same-transaction rollback, sorted locks·current candidate recheck·총 3회 attempt, V6 인덱스 2개를 구현 정본으로 확정했다. 구현·자동검증·독립 QA·실제 인수·`dev` 통합은 별도 완료 gate다. §5 A-v1은 심의 입력 이력으로만 보존한다.
- root가 `b80ede0`에서 `feature/M8-notifications`를 생성했고 M8 Task1~6 실행을 시작한 상태다. PM은 실제 backend/frontend/qa 산출물·Git·worklog 증거가 갱신될 때만 완료를 반영한다. M7은 모두 PASS/통합 완료이며 증거는 backend `91 suites/538 tests`, frontend `41 files/405 tests`·lint/build exit 0, 실제 HTTP·Chrome run4 `259 PASS/0 FAIL/0 NOT_RUN`·`failures=[]`다.

## 다음 작업

1. root/backend/frontend/qa가 승인된 회의 §9 A-v2와 M8 worklog Task1~6에 따라 구현·자동검증·독립 QA·실제 인수·`dev` 통합을 진행한다. PM은 산출물·Git·worklog의 실제 증거를 교차 대조한다.
2. 각 gate의 실제 결과가 확인되기 전에는 M8 완료·PASS·`dev` 통합 완료를 주장하지 않고, M9로 자동 확장하지 않는다.

## 차단 요인

- M7 차단 요인은 없다. `HEAD=b80ede0` docs-only closeout까지 완료됐고 M7 완료 판정은 유지한다.
- M8 정책·§9 A-v2 계약 승인 blocker는 해소됐다. 남은 것은 구현·자동검증·독립 QA·실제 인수·`dev` 통합의 완료 gate이며, 결과가 없는 항목은 완료로 기록하지 않는다.
- 사용자 미추적 `package.json`, `package-lock.json`, `run-backend.sh`, `run-frontend.sh`, `.env.local`, `.local-data/`, 기존 DB·업로드 및 root 소유 M8 회의/worklog 변경은 보존한다.

## 주요 산출물

- `docs/PRD.md` §5.11, §7.2, §9.11, §10 M8, §13 item 10/§13.4
- `docs/governance/meetings/M8-20261110-notifications.md` §2(사용자 확정 정책)·§6(독립심의)·§9(A-v2 구현 계약)·§10(사용자 전체 승인·실행)
- `docs/worklog/M8-notifications.md` 정책 선택·심의·Task1~6 실행 기록
- backend/frontend/qa `STATE.md` 및 M7 worklog/QA 최종 증거
- `b80ede08c3e54ff3f3d6742b58c401d869574331` (`docs: close out M7 delivery and verification`)
