# notification 도메인 경계

M8 알림 이력·조회·관리 API를 소유한다. V1 `notifications` 테이블과 V6 조회 인덱스를
재사용하며, `deleted_at` soft delete와 actor/target 현재 권한 재평가를 유지한다. 이벤트
생성 연계는 Task3가 담당하고 이 경계는 조회·관리와 생성기가 사용할 저장 계약만 제공한다.
