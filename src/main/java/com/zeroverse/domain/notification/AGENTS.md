# notification 도메인 경계

M8 알림 이력·조회·관리 API를 소유한다. V1 `notifications` 테이블과 V6 조회 인덱스를
재사용하며, `deleted_at` soft delete와 actor/target 현재 권한 재평가를 유지한다. 이벤트
생성 연계는 Task3가 담당하고 이 경계는 조회·관리와 생성기가 사용할 저장 계약만 제공한다.

M8 Task3의 `NotificationTransactionExecutor`, `NotificationPublicationLock`, `NotificationEventService`는
원행위와 알림의 원자적 저장·전체 후보 정렬 잠금·종류별 수신자 정책을 담당한다. 공개 DTO/조회 계약은
그대로 유지한다. 확정 rollback 1213/1205/후보 충돌만 총 3 attempt이며 commit 불명확 오류는 재시도하지 않는다.
