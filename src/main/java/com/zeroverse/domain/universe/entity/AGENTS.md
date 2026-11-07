# Universe entity 경계

V1 스키마의 상태 enum(PENDING/ACCEPTED/BLOCKED)과 `(from_user_id,to_user_id)` unique를 유지한다.
거절·취소·관계 삭제·차단 해제는 서비스에서 hard delete한다. 상태 전이는 entity 메서드로 검증한다.
