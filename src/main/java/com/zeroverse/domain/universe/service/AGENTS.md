# Universe service 경계

모든 mutation은 두 사용자 row를 ID 오름차순 pessimistic lock하고 lock 이후 양방향 관계를 재조회한다.
상태·소유권 오류는 승인된 `UNI_004`/`UNI_005` 계약을 지키며, notification row는 M8 범위라 생성하지 않는다.

M8에서는 위 M5 당시의 미생성 경계를 대체한다. request/accept는 새 transaction executor에서 성공 전이에만
NEIGHBOR를 저장·flush한다. pair lock 이전의 managed snapshot은 clear하고 lock 후 current pair를 읽는다.
accept는 요청한 원래 request ID도 재검증하여 취소·재신청으로 교체된 관계를 수락하지 않는다.
