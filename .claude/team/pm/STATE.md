# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-11-09 13:17 KST

## 현재 단계

- M0~M4는 완료·`dev` 통합됐고, M5 A-v2 구현·독립 QA·1440px 인수·PR #11 `dev` merge까지 완료됐다. M5의 승인·결론은 유지하며 M6은 별도 마일스톤이다.
- M6 `comments/likes`는 사용자 목표에 따라 심의·계약 확정을 거쳤다. 독립 Product/Architecture/Delivery 3인은 모두 `APPROVE_WITH_CHANGES / HIGH`를 반환했고, 사용자가 2026-11-09 A-v2 전체를 승인했다. — `docs/governance/meetings/M6-20261109-comments-likes.md` §5~§6
- 현재 checkout은 `feature/M6-comments-likes`이며 M6 기준 HEAD는 `c46a26d`다. 승인 후 초기 구현 산출물(`frontend/src/features/comment|like` API/types/tests, backend comment controller targeted test)이 작업 트리에 생성됐지만, M6 제품 구현·제품 테스트·실제 HTTP/MySQL/Chrome 인수·PR/merge는 아직 완료로 주장하지 않는다.

## 진행 중

- PM 상태는 **APPROVED / M6 구현 착수**다. PM 소유 `docs/PRD.md`에 사용자 승인 계약을 §9.9와 §5.7/§5.8, §7.2, §10, §11, §13.2로 동기화했다. 원본 계약은 [M6 회의 §6](../../../docs/governance/meetings/M6-20261109-comments-likes.md#6-a-v2--사용자-승인-대상-구체-계약)을 가리키며 새 제품 결정을 추가하지 않았다.
- 승인된 M6 정책은 V1 comments/post_likes·unique 유지, bounded root/reply paging, soft delete·삭제/탈퇴 placeholder, interactions 상태 GET, 1~2000 UTF-16 plain text, ACTIVE/ACL/block/SUSPENDED 경계, 양방향 block-like 금지, 읽기권한 상실 후 unlike 거부·기존 like 보존, scalar blog→users→post→comment/like lock 및 PostService.get 정렬, PostDetailPage 1440px 인수, M6 notification row 미생성과 M8 알림 분리다. — 회의 §6; `docs/PRD.md` §9.9, §13.2
- root 소유 REQUIREMENTS/governance 상태 동기화는 root가 수행하며 PM은 해당 파일을 수정하지 않았다. 실제 source에는 FE API/types/tests와 BE targeted test가 먼저 생성됐고, BE domain implementation·FE PostDetail 연결·full 검증은 후속이다. 타 역할 STATE의 승인 대기 문구는 소유자가 갱신할 사항으로 PM이 되돌리거나 수정하지 않는다. — `.claude/team/backend/STATE.md`, `.claude/team/frontend/STATE.md`, `.claude/team/qa/STATE.md`

## 다음 작업

1. backend가 `docs/worklog/M6-comments-likes.md` Task1→Task2(댓글 CRUD/paging/soft delete → 좋아요/interactions/경쟁)를 순차 구현·검증한다.
2. frontend가 Task3→Task4(API/types/좋아요 상태 → PostDetail 댓글·답글 UI)를 승인 DTO에 맞춰 구현·검증한다.
3. QA가 Task5의 독립 API/MySQL/Chrome 1440px 인수와 finding closure를 수행하고, root가 Task6의 diff·commit/PR/merge 기록을 담당한다.
4. PM은 구현 diff·검증 결과가 승인된 §9.9/회의 §6과 일치하는지 대조해 STATE/WORKLOG를 후속 갱신한다. M7 이후는 자동 착수하지 않는다.

## 차단 요인

- 사용자 승인 blocker는 해소됐다. 다만 M6 최종 acceptance는 backend/frontend 구현, RED→GREEN·MySQL 경쟁·OpenAPI, 독립 QA·1440px 시각 검증, root Git 통합 전까지 닫히지 않는다.
- 실제 제품 source에는 M6 backend entity/service/controller와 PostDetail 연결이 아직 없고 FE API/types 및 targeted tests만 초기 생성됐다. full test/실제 검증은 미실행이다. — `frontend/src/features/comment/`, `frontend/src/features/like/`, `src/test/java/com/zeroverse/domain/comment/`, `docs/worklog/M6-comments-likes.md` Task1~5
- M6 notification row는 생성하지 않으며, 댓글·좋아요 알림 생성은 M8 인계 범위다. M6에서 알림 PASS를 주장하지 않는다. — `docs/PRD.md` §5.11, §10 M8, §13.2

## 주요 산출물

- `docs/PRD.md` §9.9, §5.7~§5.8, §7.2, §10 M6, §11 M6 검증, §13.2
- `docs/governance/meetings/M6-20261109-comments-likes.md` §6
- `docs/worklog/M6-comments-likes.md` Task1~6
- `.claude/team/backend/STATE.md`, `.claude/team/frontend/STATE.md`, `.claude/team/qa/STATE.md`
- `.claude/team/pm/WORKLOG.md`
