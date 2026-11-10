# ADR-0007 — M8 알림 계약

- 상태: ACCEPTED
- 결정일: 2026-11-10 KST
- 결정 주체: 사용자 `A-v2 전체 승인`
- 정본: [M8 회의 §2/§5/§9/§10](../meetings/M8-20261110-notifications.md), [M8 실행계획](../../worklog/M8-notifications.md), REQUIREMENTS §6.11-A, PRD §9.11

## 배경

M4 게시글, M5 관계, M6 댓글·좋아요, M7 피드·검색은 dev에 통합됐다. 기존 V1 notifications 테이블을 이용해 알림 생성과 전체 이력·읽음·삭제, 센터·메인·상단바를 연결한다. 사용자 정책 9개를 먼저 확정한 뒤 독립 3인 심의의 필수 보완을 A-v2에 반영해 전체 승인을 받았다.

## 결정

- 전체 이력, 실제 재행동 재알림, 답글의 두 수신자와 REPLY 우선 중복 제거, eligible 발행 전이, 소급 없음, 원인 취소 이력 보존을 적용한다.
- 차단은 목록/건수에서 제외하고 콘텐츠 권한 상실은 비민감 고정 안내로 바꾼다. 권한·차단 복구는 직접 삭제되지 않은 이력을 현재 상태로 다시 평가한다.
- 알림과 원행위는 동기 transaction으로 저장한다. POST는 scalar blog→정렬 users→post로 잠그고 current 후보를 다시 검사한다. 미잠금 후보 발견 시 rollback하고 새 transaction으로 재시도하며 총3attempt로 제한한다. 알림 실패는 원행위도 rollback한다.
- 4개 API, NotificationPage/Item, ACTIVE 관리·SUSPENDED 읽기전용, private/no-store·RR목록·조건부읽음/삭제·세대별FE상태는 회의 §9를 그대로 따른다.
- V6은 승인된 인덱스2개만 추가하고 기존행/audit를 보존한다. batch100·1001명·querybound14·실제경쟁·업그레이드/복구·HTTP/1440px Chrome·독립QA가 완료 조건이다.

## 대안과 결과

durable outbox는 원행위 지연을 줄이지만 schema/worker/idempotency/운영 계약을 추가하므로 현재 범위에서는 채택하지 않았다. best-effort async는 유실 위험 때문에 제외했다. 동기 fan-out의 경합·지연은 실제 검증해야 하며 구조적 문제로 outbox가 필요해지면 별도 심의한다. 사용자 승인은 검증 PASS를 대신하지 않는다.

## 복구·범위

사용자 DB와 업로드를 임의 변경하지 않는다. 기존행/인덱스를 자동 drop/undo하지 않고 합성 DB에서 백업·복원 및 M7 앱 호환을 검증한다. 구버전 앱 기간 활동은 재도입 후 소급하지 않는다. M9·S3·외부 알림·실시간·새 의존성은 제외한다.
