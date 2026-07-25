-- 테스트 전용 테이블. 운영 마이그레이션(classpath:db/migration)에 포함되지 않으며,
-- BaseEntityAuditingTest가 spring.flyway.locations에 이 경로를 추가할 때만 생성된다.
CREATE TABLE test_auditing_probe (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  -- DATETIME(6): updatedAt이 실제로 갱신됐는지 밀리초 단위로 구분해 검증하기 위함.
  -- 운영 테이블은 REQUIREMENTS §4대로 DATETIME(초 정밀도)를 쓴다.
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
