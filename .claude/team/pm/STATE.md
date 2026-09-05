# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 프로젝트 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-09-06 KST (M2 최종 기록·M3 정책 승인 대기 상태 동기화)

## 현재 단계

- PRD §10 기준 M0·M1은 완료·`dev` 머지 기록이 있다.
- M2 설정은 공유 checkout `dev`의 merge commit `4c129e20f58a6ccb9c61246d103934702516c295`로 머지됐고, PR #8은 GitHub에서 `MERGED`(`mergedAt=2026-09-05T20:41:12Z`, 2026-09-06 05:41:12 KST)다. M2 worklog의 최종 `[머지]` 기록과 QA 확인도 완료됐다.
- M3 카테고리는 `docs/governance/meetings/M3-20260906-categories.md`가 `USER_DECISION_REQUIRED`이고 독립 심의·worklog 기록은 완료됐으나 사용자 승인·정본 반영 전이다. M3 구현은 시작하지 않았다.
- M4는 구현 전 선행조건·계약·S3 입력을 `docs/PM-M4-readiness.md`에 조사해 기록했다.

## 진행 중

- M2 merge로 M3 착수의 merge gate는 해소됐지만, M3 사용자 승인·정본 반영 전에는 구현하지 않는다. M3에서 넘겨받을 category ID/type/parent/order, 동일 블로그 검증, DEFAULT 이동, 공개 count predicate, 공개 GET/security 계약을 readiness에 분리했다.
- M3 §4의 `active_key`, 동일 ID 집합 `last-write-wins`, M4 Post `category_id` 경로의 동일 `blog_id` lock 참여는 아직 미승인 권고이며 `docs/PM-M3-readiness.md`와 `docs/PM-M4-readiness.md`의 전달 목록에 연결했다.
- PRD §10/§7/§9/§13.1과 REQUIREMENTS FR-POST/FR-UPLOAD/NFR, 디자인 정본, V1 schema, 실제 Category/SecurityConfig/ErrorCode/FE placeholder를 대조했다.
- LocalStack·S3 직접 URL·5MB·환경변수/`application-local.yml` 주입은 기존 확정 범위로 정리했고, 실제 bucket/region/IAM은 아직 제공되지 않은 사용자 입력으로 남겼다. 새 서비스·스택·API·schema를 확정하지 않았다.
- `docs/PM-M3-readiness.md` 출처 두 경로를 실제 소스인 `com/zeroverse/config/SecurityConfig.java`, `com/zeroverse/common/exception/ErrorCode.java`로 정정했다. 기존 결정·권고는 변경하지 않았다.

## 다음 작업

1. M3 Q1~Q4 사용자 승인·정본/ADR 반영 및 M3 완료를 확인한다.
2. 그 뒤 승인된 M3 계약과 사용자 제공 S3 값을 `docs/PM-M4-readiness.md`의 미확정 항목과 다시 대조하고 M4 착수 조건을 판정한다.

## 차단 요인

- M3의 Q1~Q4(공개 GET/security·글 수 predicate·soft-delete `active_key`·DEFAULT/LOCKED 순서·부모·잠금 subtree·오류·초기 설정 후속 CRUD·동시성·M4 lock 참여)가 사용자 승인 전이다.
- M4의 `UNIVERSE` 접근을 M5 관계 구현과 어떻게 분리할지, nullable `category_id`/빈 draft 검증, TipTap toolbar와 sanitizer, PostImage soft-delete unique, blogId/slug 목록 경로·thumbnail 표시 위치가 정본에서 모두 고정되지 않았다. readiness에는 모두 `권고(미확정)`으로 기록했다.
- M4는 M3 완료 후에만 착수한다. 실제 S3 bucket name·region·IAM과 운영 bucket policy/CORS는 M4 착수 시 제공·검증이 필요하다. 값은 문서/Git에 기록하지 않는다.

## 주요 산출물

- `docs/PM-M3-readiness.md`
- `docs/PM-M4-readiness.md`
- `.claude/team/pm/WORKLOG.md`
- `docs/PRD.md`
