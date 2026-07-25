package com.zeroverse.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * DB 통합 테스트 공통 컨테이너.
 *
 * <p>MySQL 8.4를 수용 기준으로 고정한다(REQUIREMENTS §2가 MySQL 8.x). 로컬에 설치된 다른 버전으로
 * 대체하거나 Docker 미가용을 이유로 skip하지 않는다 — PRD §9.4-Y의 M0 완료 하드 게이트다.
 *
 * <p>컨테이너는 JVM당 한 번만 시작하고 테스트 클래스 간 재사용한다.
 */
public abstract class MySqlTestSupport {

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("zeroverse")
                    .withUsername("zeroverse")
                    .withPassword("zeroverse");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
