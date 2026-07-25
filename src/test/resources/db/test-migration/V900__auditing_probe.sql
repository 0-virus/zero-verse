-- 테스트 전용 테이블. 운영 마이그레이션(classpath:db/migration)에 포함되지 않으며,
-- BaseEntityAuditingTest가 spring.flyway.locations에 이 경로를 추가할 때만 생성된다.
CREATE TABLE test_auditing_probe (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
