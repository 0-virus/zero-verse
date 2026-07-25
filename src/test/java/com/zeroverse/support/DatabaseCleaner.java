package com.zeroverse.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 간 DB 정리.
 *
 * <p>Testcontainers MySQL은 JVM당 하나를 재사용하므로 <b>테스트 클래스 사이에 데이터가 남는다</b>.
 * 한 클래스가 넣은 이메일이 다른 클래스의 unique 제약을 깨뜨리는 식으로, 실행 순서에 따라
 * 결과가 달라지는 flaky 테스트가 된다.
 *
 * <p>Flyway 이력({@code flyway_schema_history})은 지우지 않는다 — 지우면 다음 컨텍스트가
 * 마이그레이션을 다시 돌리려다 실패한다.
 */
@Component
public class DatabaseCleaner {

    /** 자식 → 부모 순서. FK 제약을 끄더라도 순서를 지켜 의도를 드러낸다. */
    private static final List<String> TABLES = List.of(
            "post_tags",
            "post_likes",
            "post_images",
            "comments",
            "notifications",
            "refresh_tokens",
            "universes",
            "posts",
            "categories",
            "tags",
            "blogs",
            "users");

    @PersistenceContext private EntityManager entityManager;

    /** 도메인 테이블을 모두 비우고 auto increment를 초기화한다. */
    @Transactional
    public void clear() {
        entityManager.flush();
        entityManager.clear();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        for (String table : TABLES) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate();
        }
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
