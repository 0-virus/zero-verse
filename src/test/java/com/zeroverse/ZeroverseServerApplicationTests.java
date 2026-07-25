package com.zeroverse;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.support.MySqlTestSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 애플리케이션 컨텍스트가 실제 MySQL 8.4 + Flyway 위에서 기동하는지 확인한다(PRD §11~§12). */
@SpringBootTest
@ActiveProfiles("test")
class ZeroverseServerApplicationTests extends MySqlTestSupport {

    @Autowired private ApplicationContext context;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("컨텍스트가 로드되고 공통 빈이 등록된다")
    void contextLoads() {
        assertThat(context.containsBean("zeroverseOpenAPI")).isTrue();
        assertThat(context.containsBean("corsConfigurationSource")).isTrue();
        assertThat(context.containsBean("passwordEncoder")).isTrue();
    }

    @Test
    @DisplayName("연결된 DB는 MySQL 8.4다")
    void runsOnMySql84() {
        String version = new JdbcTemplate(dataSource)
                .queryForObject("SELECT VERSION()", String.class);

        assertThat(version).startsWith("8.4");
    }
}
