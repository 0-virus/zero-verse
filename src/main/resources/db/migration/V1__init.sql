-- ZeroVerse Blog MVP — 초기 스키마
--
-- 근거: docs/REQUIREMENTS.md §4(엔티티) · NFR-06(명명) · NFR-07(첫 마이그레이션은 MVP 전체 테이블) ·
--       NFR-08(무결성 제약), docs/PRD.md §3.1~§3.5.
--
-- 출처: origin/feature/M4a-post-backend(a6f0407)의 V1__init.sql을 기준선으로 사용하되, 아래를 변경했다.
--   - category type에 DEFAULT를 추가하고 SERIES를 제거(PRD §9-H). 기본값도 GENERAL 유지.
--   - 모든 enum 성격 컬럼에 CHECK 제약을 추가해 허용값을 DB에서 강제(계획 §2.4).
--   - is_default 컬럼은 두지 않고 type='DEFAULT'로 표현(PRD §3.2).
-- 유지한 검증 자산: categories.parent_key STORED generated column을 이용한 unique key(MySQL 8.4에서
--   표현식 unique 문법 오류를 회피한 방식), post_likes 테이블명(예약어 likes 회피), utf8mb4 설정.
--
-- 애플리케이션 레벨에서 보장하는 규칙(SQL로 강제하지 않음, NFR-08 / PRD §3.5):
--   블로그당 DEFAULT 카테고리 1개, 카테고리·댓글 최대 깊이,
--   작성자와 블로그 소유자 일치, 카테고리와 게시글의 동일 블로그 소속.
--   (자기 자신 Universe 금지는 예외적으로 DB에서 강제한다 — ck_universes_not_self.)
--
-- 변경 정책: 공유 환경 적용 전에는 빈 DB 재생성으로 검증한다. 적용 후에는 이 파일을 수정하지 않고
--   V2 이상 forward migration만 추가한다(RISK-0003).

-- 1. users
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  nickname VARCHAR(100) NOT NULL UNIQUE,
  birth_date DATE,
  bio TEXT,
  profile_image_url VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  INDEX idx_users_deleted_at (deleted_at),
  CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
  CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. blogs
CREATE TABLE blogs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  url_slug VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  is_setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_blogs_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_blogs_user_id (user_id),
  INDEX idx_blogs_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. categories
-- parent_key: parent_id가 NULL인 루트 카테고리도 unique key에 참여시키기 위한 STORED generated column.
CREATE TABLE categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  blog_id BIGINT NOT NULL,
  parent_id BIGINT,
  parent_key BIGINT AS (COALESCE(parent_id, 0)) STORED,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
  display_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_categories_blog FOREIGN KEY (blog_id) REFERENCES blogs(id),
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id),
  INDEX idx_categories_blog_id (blog_id),
  INDEX idx_categories_parent_id (parent_id),
  INDEX idx_categories_deleted_at (deleted_at),
  UNIQUE KEY uk_categories_blog_parent_name (blog_id, parent_key, name),
  UNIQUE KEY uk_categories_blog_parent_order (blog_id, parent_key, display_order),
  CONSTRAINT ck_categories_type CHECK (type IN ('DEFAULT', 'GENERAL', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. posts
-- 임시저장 = published_at IS NULL.
CREATE TABLE posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  blog_id BIGINT NOT NULL,
  category_id BIGINT,
  title VARCHAR(200) NOT NULL,
  content_json LONGTEXT NOT NULL,
  content_html LONGTEXT,
  thumbnail_url VARCHAR(500),
  visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  view_count INT NOT NULL DEFAULT 0,
  published_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_posts_blog FOREIGN KEY (blog_id) REFERENCES blogs(id),
  CONSTRAINT fk_posts_category FOREIGN KEY (category_id) REFERENCES categories(id),
  INDEX idx_posts_user_id (user_id),
  INDEX idx_posts_blog_id (blog_id),
  INDEX idx_posts_category_id (category_id),
  INDEX idx_posts_visibility (visibility),
  INDEX idx_posts_published_at (published_at),
  INDEX idx_posts_deleted_at (deleted_at),
  CONSTRAINT ck_posts_visibility CHECK (visibility IN ('PUBLIC', 'UNIVERSE', 'PRIVATE')),
  CONSTRAINT ck_posts_view_count CHECK (view_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. post_images
CREATE TABLE post_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  alt_text VARCHAR(255),
  display_order INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts(id),
  INDEX idx_post_images_post_id (post_id),
  INDEX idx_post_images_deleted_at (deleted_at),
  UNIQUE KEY uk_post_images_post_order (post_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. tags
CREATE TABLE tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. post_tags (재생성 가능한 연결 — hard delete)
CREATE TABLE post_tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts(id),
  CONSTRAINT fk_post_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id),
  INDEX idx_post_tags_post_id (post_id),
  INDEX idx_post_tags_tag_id (tag_id),
  UNIQUE KEY uk_post_tags_post_tag (post_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. universes (단방향 신청-수락 관계, 재생성 가능 — hard delete)
-- REJECTED는 저장하지 않는다(REQUIREMENTS §4 Universe 주석).
CREATE TABLE universes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_universes_from_user FOREIGN KEY (from_user_id) REFERENCES users(id),
  CONSTRAINT fk_universes_to_user FOREIGN KEY (to_user_id) REFERENCES users(id),
  INDEX idx_universes_from_user_id (from_user_id),
  INDEX idx_universes_to_user_id (to_user_id),
  INDEX idx_universes_status (status),
  UNIQUE KEY uk_universes_from_to (from_user_id, to_user_id),
  CONSTRAINT ck_universes_status CHECK (status IN ('PENDING', 'ACCEPTED', 'BLOCKED')),
  CONSTRAINT ck_universes_not_self CHECK (from_user_id <> to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. comments (1단계 대댓글)
CREATE TABLE comments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id),
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id),
  INDEX idx_comments_post_id (post_id),
  INDEX idx_comments_user_id (user_id),
  INDEX idx_comments_parent_id (parent_id),
  INDEX idx_comments_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. post_likes (예약어 회피용 테이블명. 재생성 가능 — hard delete)
CREATE TABLE post_likes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(id),
  CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_post_likes_post_id (post_id),
  INDEX idx_post_likes_user_id (user_id),
  UNIQUE KEY uk_post_likes_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. notifications
CREATE TABLE notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  receiver_user_id BIGINT NOT NULL,
  actor_user_id BIGINT,
  type VARCHAR(30) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT NOT NULL,
  message VARCHAR(500),
  read_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_user_id) REFERENCES users(id),
  CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  INDEX idx_notifications_receiver_user_id (receiver_user_id),
  INDEX idx_notifications_read_at (read_at),
  INDEX idx_notifications_deleted_at (deleted_at),
  CONSTRAINT ck_notifications_type
    CHECK (type IN ('COMMENT', 'LIKE', 'REPLY', 'NEIGHBOR', 'POST')),
  CONSTRAINT ck_notifications_target_type
    CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'UNIVERSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. refresh_tokens (rotation 대상. 해시만 저장하고 원본 토큰은 저장하지 않는다)
CREATE TABLE refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_id VARCHAR(255) NOT NULL UNIQUE,
  token_hash VARCHAR(500) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_refresh_tokens_user_id (user_id),
  INDEX idx_refresh_tokens_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
