-- Users table
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  name VARCHAR(100),
  nickname VARCHAR(100) NOT NULL UNIQUE,
  birth_date DATE,
  bio TEXT,
  profile_image_url VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  INDEX idx_email (email),
  INDEX idx_nickname (nickname),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Blogs table
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
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_user_id (user_id),
  INDEX idx_url_slug (url_slug),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Categories table
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
  FOREIGN KEY (blog_id) REFERENCES blogs(id),
  FOREIGN KEY (parent_id) REFERENCES categories(id),
  INDEX idx_blog_id (blog_id),
  INDEX idx_parent_id (parent_id),
  INDEX idx_type (type),
  INDEX idx_deleted_at (deleted_at),
  UNIQUE KEY uk_blog_parent_name (blog_id, parent_key, name),
  UNIQUE KEY uk_blog_parent_order (blog_id, parent_key, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Posts table
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
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (blog_id) REFERENCES blogs(id),
  FOREIGN KEY (category_id) REFERENCES categories(id),
  INDEX idx_user_id (user_id),
  INDEX idx_blog_id (blog_id),
  INDEX idx_category_id (category_id),
  INDEX idx_visibility (visibility),
  INDEX idx_published_at (published_at),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Post Images table
CREATE TABLE post_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  alt_text VARCHAR(255),
  display_order INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  FOREIGN KEY (post_id) REFERENCES posts(id),
  INDEX idx_post_id (post_id),
  INDEX idx_deleted_at (deleted_at),
  UNIQUE KEY uk_post_display_order (post_id, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tags table
CREATE TABLE tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_normalized_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Post Tags table
CREATE TABLE post_tags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (tag_id) REFERENCES tags(id),
  INDEX idx_post_id (post_id),
  INDEX idx_tag_id (tag_id),
  UNIQUE KEY uk_post_tag (post_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Universe table
CREATE TABLE universes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (from_user_id) REFERENCES users(id),
  FOREIGN KEY (to_user_id) REFERENCES users(id),
  INDEX idx_from_user_id (from_user_id),
  INDEX idx_to_user_id (to_user_id),
  INDEX idx_status (status),
  UNIQUE KEY uk_from_to_user (from_user_id, to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Comments table
CREATE TABLE comments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  content TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME,
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (parent_id) REFERENCES comments(id),
  INDEX idx_post_id (post_id),
  INDEX idx_user_id (user_id),
  INDEX idx_parent_id (parent_id),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Post Likes table (renamed from likes to avoid SQL keyword)
CREATE TABLE post_likes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (post_id) REFERENCES posts(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_post_id (post_id),
  INDEX idx_user_id (user_id),
  UNIQUE KEY uk_post_user_like (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notifications table
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
  deleted_at DATETIME,
  FOREIGN KEY (receiver_user_id) REFERENCES users(id),
  FOREIGN KEY (actor_user_id) REFERENCES users(id),
  INDEX idx_receiver_user_id (receiver_user_id),
  INDEX idx_actor_user_id (actor_user_id),
  INDEX idx_type (type),
  INDEX idx_target_type (target_type),
  INDEX idx_read_at (read_at),
  INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Refresh Tokens table
CREATE TABLE refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_id VARCHAR(255) NOT NULL UNIQUE,
  token_hash VARCHAR(500) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_user_id (user_id),
  INDEX idx_expires_at (expires_at),
  INDEX idx_revoked_at (revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
