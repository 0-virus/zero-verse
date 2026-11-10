# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-11-10 17:47:12 KST

## 현재 단계

- M0~M5는 완료·`dev` 통합됐고, M6 댓글·좋아요와 M7 피드·검색도 구현·자동검증·독립 QA·실제 HTTP/Chrome 1440px 인수·`dev` 통합까지 완료됐다.
- M7 최종 통합은 PR #13 merge SHA `08863207b8385177b619091410bbbd43e52a337c` 및 통합 tree `dc68037477c6fed6b77d537235d79093803a983e`로 끝났고, root docs-only closeout 커밋 `b80ede08c3e54ff3f3d6742b58c401d869574331`이 이미 `dev`/`origin/dev`에 반영됐다. 현재 docs-only closeout 완료 상태다.
- 현재 단계는 **M8 사용자 제품 정책 9개·§9 A-v2 전체 승인·독립심의 `APPROVE_WITH_CHANGES/HIGH` 완료, Task1~6 구현·자동검증·독립 QA·실제 인수·`dev` 통합 완료**다. M8 최종 PASS/merge-ready 상태이며 M9는 별도 사용자 지시를 대기한다.

## 진행 중

- `docs/PRD.md`에 §9.11 M8 제품 정책과 §5.11·§7.2·§10·§13.4 정합화를 반영했다. 전체 이력(디자인 30일 문구 override), 답글 `COMMENT`/`REPLY` 이중수신 및 동일 수신자 `REPLY` 우선 dedupe, 실제 재행동 재알림·동일요청 비중복, eligible 발행 전이, 원인 취소 후 이력 보존, 권한상실 generic/차단 숨김, 클릭 읽음·전체 범위 read-all·공유 unread·최근 3 all, 주기 자동 갱신 없이 진입/open/복귀 갱신·소급 없음만 확정했다. 읽음·삭제 뒤 서버 재조회로 자체 상태를 동기화하는 동작은 유지한다.
- M8 회의 상태는 `APPROVED`다. 사용자 `A-v2 전체 승인`으로 개별 read의 현재 권한 재평가 canonical `NotificationItem`, `SUSPENDED` GET read-only, dynamic unavailable masking·권한 복구 재평가, 원행위·알림 same-transaction rollback, sorted locks·current candidate recheck·총 3회 attempt, V6 인덱스 2개를 구현 정본으로 확정했다. 구현·자동검증·독립 QA·실제 인수·`dev` 통합은 별도 완료 gate다. §5 A-v1은 심의 입력 이력으로만 보존한다.
- backend 최신 전체 검증은 `102 suites / 599 tests / failures 0 / errors 0 / skipped 0`, `bootJar BUILD SUCCESSFUL`이다. frontend 최신 전체 검증은 `47 files / 439 tests PASS`, lint/build exit 0이다. DELETE `400 VALIDATION_001` OpenAPI 문서와 SUSPENDED preview 읽기 전용 안내는 관련 backend controller 5 tests/bootJar·frontend shell 8 tests/lint/build를 통과했고 `12d7ab5`에 보존됐다.
- 독립 전체 리뷰는 신규 제품 Critical/Important 차단 결함 없이 `With fixes`를 판정했고, QA 도구 보완 후 run3 실제 인수가 통과했다. `build/m8-acceptance-20261110-173900/evidence.json`은 `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`다. source hash `108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47`와 immutable JAR hash `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5`가 manifest와 일치하고, 1440×1000 screenshot 5개와 query bound(`size=1 SELECT 4`, `size=100 SELECT 4`, 차이 `0`)을 확인했다. `qa/M8-review.md` §10 독립 QA는 `Ready to merge: Yes`로 판정했다.
- PR #14(`https://github.com/0-virus/zero-verse/pull/14`)는 merge SHA `b9fcb0469f42077447ce8a8842ce22f24bc94af5`로 `dev`에 통합됐다. tested head `201cd181ec8332e85f2cc985c9560d947b2833f4`와 통합 tree `9da6077a89a4528df3cb55c13556ed28300cfd28`가 일치하고 local `dev` fast-forward도 확인됐다.

## 다음 작업

1. root가 post-merge docs-only closeout 기록과 최종 PRD/STATE/WORKLOG 정합성을 확인한다.
2. M9는 이번 실행 범위가 아니며 별도 사용자 지시 전 자동 확장하지 않는다.

## 차단 요인

- M7 차단 요인은 없다. `HEAD=b80ede0` docs-only closeout까지 완료됐고 M7 완료 판정은 유지한다.
- M8 승인·구현·자동검증·실제 인수·독립 QA·PR/`dev` 통합 blocker는 모두 해소됐다. 남은 것은 root의 docs-only closeout 기록 정합성 확인뿐이다.
- 사용자 미추적 `package.json`, `package-lock.json`, `run-backend.sh`, `run-frontend.sh`, `.env.local`, `.local-data/`, 기존 DB·업로드 및 root 소유 M8 회의/worklog 변경은 보존한다.

## 주요 산출물

- `docs/PRD.md` §5.11, §7.2, §9.11, §10 M8, §13 item 10/§13.4
- `docs/governance/meetings/M8-20261110-notifications.md` §2(사용자 확정 정책)·§6(독립심의)·§9(A-v2 구현 계약)·§10(사용자 전체 승인·실행)
- `docs/worklog/M8-notifications.md` 정책 선택·심의·Task1~6 실행 기록
- 최신 제품 근거: `build/m8-evidence/backend-final-2/summary.json`, `build/m8-evidence/frontend-final-2-test.log`, `build/m8-acceptance-20261110-173900/evidence.json`
- 최신 QA/리뷰 근거: `.superpowers/sdd/M8-notifications/final-review-findings.md`, `qa/M8-review.md`, `.superpowers/sdd/M8-notifications/qa-final-fix.md`
- Git 통합 근거: PR #14 merge SHA `b9fcb0469f42077447ce8a8842ce22f24bc94af5`, tested head/tree 일치 및 local `dev` fast-forward
- `b80ede08c3e54ff3f3d6742b58c401d869574331` (`docs: close out M7 delivery and verification`)
