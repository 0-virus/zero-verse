# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 프로젝트 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-09-06 KST (M3 Q1~Q4 승인 계약·PRD/readiness 정합화)

## 현재 단계

- PRD §10 기준 M0·M1은 완료·`dev` 머지 기록이 있다.
- M2 설정은 `dev`에 merge commit `4c129e20f58a6ccb9c61246d103934702516c295`로 머지됐고, PR #8은 GitHub에서 `MERGED`(`mergedAt=2026-09-05T20:41:12Z`, 2026-09-06 05:41:12 KST)다. 현재 checkout은 `feature/M3-categories`, HEAD는 `1690731`이며 M3 제품 구현 중이다. M2 worklog의 최종 `[머지]` 기록과 QA 확인도 완료됐다.
- M3 카테고리는 회의록 §4 Q1~Q4가 사용자 원문 `시작`으로 승인됐고 회의록은 `APPROVED`, ADR-0005는 `ACCEPTED`다. PRD·REQUIREMENTS·ADR·PM readiness 정합화는 완료됐으며 제품 구현·DoD 검증은 아직이다.
- M4는 M3 승인 계약을 인계받았고, M3 구현·검증 및 실제 S3 bucket/region/IAM 입력 전까지 착수 대기다.

## 진행 중

- M2 merge로 M3의 선행 gate는 해소됐고, Q1~Q4 승인 범위는 `active_key`, root page+children/CategoryResponse, 조회자별 count와 `includeDrafts`, `CAT_004~007`, 생성 시 parent 선택·DEFAULT/LOCKED 정책, setup 부분 복구, `last-write-wins`, M4 동일 `blog_id` lock이다.
- `docs/PRD.md` §3.5·§4.2·§4.4·§5.2·§5.4·§7·§9.5·§10~12와 `docs/PM-M3-readiness.md`/`docs/PM-M4-readiness.md`에 위 계약을 반영하고, 승인 전 권고·반론·상태 이력은 별도 역사 문맥으로 보존했다.
- PRD §10/§7/§9/§13.1과 REQUIREMENTS FR-POST/FR-UPLOAD/NFR, 디자인 정본, V1 schema, 실제 Category/SecurityConfig/ErrorCode/FE placeholder를 대조했다.
- LocalStack·S3 직접 URL·5MB·환경변수/`application-local.yml` 주입은 기존 확정 범위로 정리했고, 실제 bucket/region/IAM은 아직 제공되지 않은 사용자 입력으로 남겼다. 새 서비스·스택·API·schema를 확정하지 않았다.
- `docs/PM-M3-readiness.md` 출처는 실제 소스인 `com/zeroverse/config/SecurityConfig.java`, `com/zeroverse/common/exception/ErrorCode.java`와 ADR-0005를 가리킨다. PM은 제품 코드·governance·Git을 편집하지 않았다.

## 다음 작업

1. backend가 승인 계약을 조사·구현하기 전에 PRD/ADR/REQUIREMENTS의 동일 범위와 `feature/M3-categories` 산출물을 대조한다.
2. backend/frontend 구현과 독립 QA 결과를 확인해 M3 DoD를 판정한다.
3. M3 완료 후 사용자 제공 S3 값과 M4 readiness의 미확정 항목을 다시 대조해 M4 착수 조건을 판정한다.

## 차단 요인

- M3 제품 구현·실제 MySQL/권한/부분 실패 검증과 BE·FE·QA 공통 계약 확인이 아직 남았다. 승인된 범위를 넘어 새 API/schema/version을 추가하지 않는다.
- M4의 `UNIVERSE` 접근을 M5 관계 구현과 어떻게 분리할지, nullable `category_id`/빈 draft 검증, TipTap toolbar와 sanitizer, PostImage soft-delete unique, blogId/slug 목록 경로·thumbnail 표시 위치가 정본에서 모두 고정되지 않았다. readiness에는 모두 `권고(미확정)`으로 기록했다.
- M4는 M3 완료 후에만 착수한다. 실제 S3 bucket name·region·IAM과 운영 bucket policy/CORS는 M4 착수 시 제공·검증이 필요하다. 값은 문서/Git에 기록하지 않는다.

## 주요 산출물

- `docs/PM-M3-readiness.md`
- `docs/PM-M4-readiness.md`
- `.claude/team/pm/WORKLOG.md`
- `docs/PRD.md`
