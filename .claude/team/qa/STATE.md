# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M8 알림 Task5 focused black-box coverage audit**다. 사용자 A-v2 전체 승인, `feature/M8-notifications` base `b80ede0`; backend Task2와 frontend fix1이 진행 중이다.
- `qa/M8-review.md`에 FR-NOT-01~05/§6.11-A·ADR-0007·회의 A-v2 기반 인수 매트릭스와 관계삭제·전 endpoint auth/no-store·FE retry/stale/query-bound gate를 반영했다. 현재 모든 M8 자동/HTTP/DB/Chrome/시각 gate는 **NOT RUN**이며 PASS·merge-ready·dev 통합을 주장하지 않는다.
- `qa/m8-acceptance.mjs`는 합성 HTTP/Playwright harness다. 관계 hard-delete masking, 네 endpoint 인증 행렬, FE fix1 black-box probes, performance_schema SELECT delta 관측을 추가했으며 합성 ID만 seed/evidence에 남기고 credentials/token/marker는 memory/env에서만 사용한다.

## 진행 중

- root runtime 계약(DB `zeroverse_m8`/`13318`, API `18088`, FE `5178`, `build/m8-runtime.py`)과 handoff 중이다. root 제공 V5 legacy240rows/EXPLAIN/M7 JAR API baseline은 후속 runtime evidence로 취급하고, V6-after/restart/M7 compatibility는 root 소유 gate다.
- runner가 쓰는 seed direct keys: `suspendedUserId`, `deletedUserId`, `suspendedPostId`, `deletedPostId`, `ownerUserId`, `oldNotificationIds`; `accounts.<label>.userId` 및 `statusTargets`도 함께 보존한다.
- root는 seed의 정확한 합성 ID만 사용해 상태·41일 timestamp를 한 transaction으로 적용하고, `status-ready.json`의 `ready`, 동일 ID, `oldNotificationUpdatedCount`를 검증한 뒤 작성해야 한다. runner는 ready 전 실행을 진행하지 않는다.
- root가 `USER='zeroverse_m8'` performance_schema SELECT counter 가용성을 확인했지만, 실제 size1→size100 delta는 아직 측정하지 않았다.
- focused audit 후 `node --check qa/m8-acceptance.mjs` 및 QA 산출물 trailing-whitespace 검사를 통과했다.

## 다음 작업

1. 후속 정적 `node --check`만 수행하고 backend/FE 구현·root immutable artifact/status-ready를 기다린다.
2. root 실행 지시가 오면 unique output으로 `qa/m8-acceptance.mjs`를 실행하고, `evidence.json`·SQL fingerprint·performance_schema delta·screenshots·자동검증 결과를 독립 재집계한다.
3. 구현 finding은 제품 소유자 수정 후 독립 재리뷰한다. M8 실제 증거가 닫히기 전에는 최종 판정하지 않는다.

## 차단 요인

- 현재 blocker는 구현·immutable artifact·isolated runtime/status-ready 미준비와 실제 실행 금지 지시다. 이는 root/backend/frontend의 선행 작업이며 QA가 제품 코드나 사용자/운영 DB를 대신 수정하지 않는다.
- 실제 HTTP/DB/Chrome, performance_schema delta, Gradle `test bootJar`, FE test/lint/build, source/JAR hash, query-bound/lockwait/fan-out/rollback 및 1440px 시각 gate는 모두 미실행이다.
- 사용자 DB `13307`, 기존 DB/업로드, `.env.local`에는 접근하지 않았다. Git 조작·환경 기동도 하지 않았다.

## 산출물

- `qa/M8-review.md`
- `qa/m8-acceptance.mjs`
- `.superpowers/sdd/M8-notifications/task-5-prep-report.md` focused audit append

## 갱신 시각

- 2026-11-10 KST — M8 Task5 focused black-box audit 완료. 실제 인수 시작 신호와 immutable artifact를 기다린다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`와 root가 명시 허용한 `.superpowers/sdd/M8-notifications/task-5-prep-report.md` append만 수정했다. 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance/worklog 원본·Git은 수정하지 않았다.
