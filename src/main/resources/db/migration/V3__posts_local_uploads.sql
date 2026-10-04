-- M4 forward migration. V1/V2 are immutable after shared environments.

-- V1's unique order included soft-deleted rows. Keep the history while allowing
-- the active snapshot to reuse an order slot.
ALTER TABLE post_images
    DROP INDEX uk_post_images_post_order,
    ADD COLUMN active_key TINYINT AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_post_images_post_order_active (post_id, display_order, active_key);

CREATE TABLE post_view_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  viewer_key VARCHAR(128) NOT NULL,
  last_viewed_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_post_view_records_post FOREIGN KEY (post_id) REFERENCES posts(id),
  UNIQUE KEY uk_post_view_records_post_viewer (post_id, viewer_key),
  INDEX idx_post_view_records_last_viewed_at (last_viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE image_uploads (
  id CHAR(36) PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  purpose VARCHAR(30) NOT NULL,
  storage_key VARCHAR(500) NOT NULL UNIQUE,
  image_url VARCHAR(500) NOT NULL UNIQUE,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  bound_post_id BIGINT,
  bound_resource_type VARCHAR(30),
  bound_resource_id VARCHAR(100),
  bound_at DATETIME,
  detached_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_image_uploads_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT fk_image_uploads_post FOREIGN KEY (bound_post_id) REFERENCES posts(id),
  CONSTRAINT ck_image_uploads_purpose CHECK (purpose IN ('PROFILE_IMAGE', 'POST_THUMBNAIL', 'POST_IMAGE')),
  CONSTRAINT ck_image_uploads_size CHECK (file_size BETWEEN 1 AND 5242880),
  INDEX idx_image_uploads_owner (owner_user_id),
  INDEX idx_image_uploads_post (bound_post_id),
  INDEX idx_image_uploads_bound (bound_resource_type, bound_resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
