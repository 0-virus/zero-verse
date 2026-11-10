# notification entity 경계

V1 `notifications`의 receiver/actor FK, type·target_type check, target_id, message, read_at,
audit와 `deleted_at`을 그대로 매핑한다. 알림 자체는 읽음 최초 시각과 soft delete만 변경하며
게시글·댓글·관계 내용을 원문 snapshot으로 저장하지 않는다.
