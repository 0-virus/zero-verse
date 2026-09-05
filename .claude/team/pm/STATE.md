# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 프로젝트 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-09-06 KST (M4 준비도 조사)

## 현재 단계

- PRD §10 기준 M0·M1은 완료·`dev` 머지 기록이 있다.
- M2 설정은 `feature/M2-settings` local HEAD `ed8f2c1`, PR #8 원격 HEAD `403eb31`, 상태 `OPEN`이며 아직 미머지다. `docs/worklog/M2-settings.md`의 `[머지]`도 비어 있다.
- M3 카테고리는 `docs/governance/meetings/M3-20260906-categories.md`가 `REVIEWING`이고 사용자 승인·정본 반영·머지 전이다. M3 구현은 시작하지 않았다.
- M4는 구현 전 선행조건·계약·S3 입력을 `docs/PM-M4-readiness.md`에 조사해 기록했다.

## 진행 중

- M4는 M2·M3 이후라는 PRD §10 게이트를 유지한다. M3에서 넘겨받을 category ID/type/parent/order, 동일 블로그 검증, DEFAULT 이동, 공개 count predicate, 공개 GET/security 계약을 readiness에 분리했다.
- PRD §10/§7/§9/§13.1과 REQUIREMENTS FR-POST/FR-UPLOAD/NFR, 디자인 정본, V1 schema, 실제 Category/SecurityConfig/ErrorCode/FE placeholder를 대조했다.
- LocalStack·S3 직접 URL·5MB·환경변수/`application-local.yml` 주입은 기존 확정 범위로 정리했고, 실제 bucket/region/IAM은 아직 제공되지 않은 사용자 입력으로 남겼다. 새 서비스·스택·API·schema를 확정하지 않았다.
- `docs/PM-M3-readiness.md` 출처 두 경로를 실제 소스인 `com/zeroverse/config/SecurityConfig.java`, `com/zeroverse/common/exception/ErrorCode.java`로 정정했다. 기존 결정·권고는 변경하지 않았다.

## 다음 작업

1. M2 검증·독립 리뷰·PR #8 머지와 M2 `[머지]` 기록을 확인한다.
2. M3 심의·사용자 승인·정본/ADR 반영 및 M3 완료를 확인한다.
3. 그 뒤 승인된 M3 계약과 사용자 제공 S3 값을 `docs/PM-M4-readiness.md`의 미확정 항목과 다시 대조하고, 리더가 정한 구현 순서에 따라 M4 착수 조건을 판정한다.

## 차단 요인

- M2 PR #8이 `OPEN`이고 `[머지]` 기록이 없어 M3·M4 착수 게이트가 닫혀 있다.
- M3의 공개 GET/security·글 수 predicate·soft-delete unique·DEFAULT/LOCKED 순서·부모·잠금 subtree·오류·초기 설정 후속 CRUD가 승인 전이다.
- M4의 `UNIVERSE` 접근을 M5 관계 구현과 어떻게 분리할지, nullable `category_id`/빈 draft 검증, TipTap toolbar와 sanitizer, PostImage soft-delete unique, blogId/slug 목록 경로·thumbnail 표시 위치가 정본에서 모두 고정되지 않았다. readiness에는 모두 `권고(미확정)`으로 기록했다.
- 실제 S3 bucket name·region·IAM과 운영 bucket policy/CORS는 M4 착수 시 제공·검증이 필요하다. 값은 문서/Git에 기록하지 않는다.

## 주요 산출물

- `docs/PM-M3-readiness.md`
- `docs/PM-M4-readiness.md`
- `.claude/team/pm/WORKLOG.md`
- `docs/PRD.md`
