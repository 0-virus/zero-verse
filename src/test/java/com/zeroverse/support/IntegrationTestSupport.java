package com.zeroverse.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합/JPA 테스트 공용 베이스. MySQL 8.4 Testcontainer를 JVM당 1회만 기동해
 * 모든 하위 테스트가 재사용한다(컨테이너 시작 비용 절감). datasource 속성은
 * {@link DynamicPropertySource}로 런타임 주입하므로 application-test.yml의
 * localhost datasource 값을 덮어쓴다. 컨테이너는 Testcontainers Ryuk이 JVM 종료 시 정리한다.
 */
public abstract class IntegrationTestSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("zeroverse_test")
        .withUsername("test")
        .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
