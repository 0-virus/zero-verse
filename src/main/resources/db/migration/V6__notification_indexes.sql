CREATE INDEX idx_notifications_receiver_active_created
    ON notifications (receiver_user_id, deleted_at, created_at DESC, id DESC);

CREATE INDEX idx_notifications_receiver_active_read
    ON notifications (receiver_user_id, deleted_at, read_at);
