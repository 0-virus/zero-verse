package com.zeroverse.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * DB 통합 테스트 공통 컨테이너.
 *
 * <p>MySQL 8.4를 수용 기준으로 고정한다(REQUIREMENTS §2가 MySQL 8.x). 로컬에 설치된 다른 버전으로
 * 대체하거나 Docker 미가용을 이유로 skip하지 않는다 — PRD §9.4-Y의 M0 완료 하드 게이트다.
 *
 * <p>컨테이너는 JVM당 한 번만 시작하고 테스트 클래스 간 재사용한다. 재사용하므로 데이터가
 * 남아 클래스 간 간섭이 생기며, 이를 막기 위해 <b>각 테스트 전에 도메인 테이블을 비운다</b>.
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

    @Autowired(required = false)
    private DatabaseCleaner databaseCleaner;

    /**
     * 각 테스트 전에 DB를 비운다.
     *
     * <p>{@code @Transactional} 롤백에 기대지 않는 이유: 동시성 테스트처럼 트랜잭션을 공유하면
     * 안 되는 경우가 있고, MockMvc 통합 테스트는 커밋된 데이터를 남긴다.
     */
    @BeforeEach
    void cleanDatabase() {
        if (databaseCleaner != null) {
            databaseCleaner.clear();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
