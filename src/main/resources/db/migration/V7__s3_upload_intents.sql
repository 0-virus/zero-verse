-- Deployment A-v2: additive S3 upload metadata and durable upload intents.
-- Existing image rows remain local; no bytes are moved by this migration.

ALTER TABLE image_uploads
    ADD COLUMN storage_backend VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN object_bucket VARCHAR(255),
    ADD COLUMN object_region VARCHAR(64),
    ADD COLUMN object_key VARCHAR(500),
    ADD COLUMN content_sha256 CHAR(44) CHARACTER SET ascii COLLATE ascii_bin,
    ADD UNIQUE KEY uk_image_uploads_s3_object (object_bucket, object_key),
    ADD CONSTRAINT ck_image_uploads_storage_backend
        CHECK (storage_backend IN ('LOCAL', 'S3')),
    ADD CONSTRAINT ck_image_uploads_storage_location
        CHECK (
            (storage_backend = 'LOCAL'
                AND object_bucket IS NULL
                AND object_region IS NULL
                AND object_key IS NULL
                AND content_sha256 IS NULL)
            OR
            (storage_backend = 'S3'
                AND object_bucket IS NOT NULL
                AND object_region IS NOT NULL
                AND object_key IS NOT NULL
                AND content_sha256 IS NOT NULL
                AND CHAR_LENGTH(content_sha256) = 44)
        );

CREATE TABLE upload_intents (
  id CHAR(36) PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  purpose VARCHAR(30) NOT NULL,
  expected_size BIGINT NOT NULL,
  expected_content_type VARCHAR(100) NOT NULL,
  expected_sha256 CHAR(44) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  object_bucket VARCHAR(255) NOT NULL,
  object_region VARCHAR(64) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  upload_expires_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  cleanup_attempts INT NOT NULL DEFAULT 0,
  last_cleanup_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_upload_intents_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT ck_upload_intents_purpose
      CHECK (purpose IN ('PROFILE_IMAGE', 'POST_THUMBNAIL', 'POST_IMAGE')),
  CONSTRAINT ck_upload_intents_size CHECK (expected_size BETWEEN 1 AND 5242880),
  CONSTRAINT ck_upload_intents_checksum_length CHECK (CHAR_LENGTH(expected_sha256) = 44),
  CONSTRAINT ck_upload_intents_status
      CHECK (status IN ('PENDING', 'READY', 'DELETING', 'DELETED')),
  UNIQUE KEY uk_upload_intents_s3_object (object_bucket, object_key),
  INDEX idx_upload_intents_owner (owner_user_id),
  INDEX idx_upload_intents_status_expiry (status, expires_at, upload_expires_at),
  INDEX idx_upload_intents_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
