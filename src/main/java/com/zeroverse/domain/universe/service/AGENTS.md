# Universe service 경계

모든 mutation은 두 사용자 row를 ID 오름차순 pessimistic lock하고 lock 이후 양방향 관계를 재조회한다.
상태·소유권 오류는 승인된 `UNI_004`/`UNI_005` 계약을 지키며, notification row는 M8 범위라 생성하지 않는다.
